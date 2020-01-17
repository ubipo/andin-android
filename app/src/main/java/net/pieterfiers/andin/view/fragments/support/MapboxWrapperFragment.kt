package net.pieterfiers.andin.view.fragments.support

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.*
import androidx.databinding.Observable
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import net.pieterfiers.andin.*
import net.pieterfiers.andin.R
import net.pieterfiers.andin.databinding.FragmentMapboxWrapperBinding
import net.pieterfiers.andin.model.map.*
import net.pieterfiers.andin.db.FavoriteRoom
import net.pieterfiers.andin.model.geospatial.*
import net.pieterfiers.andin.model.mapbox.*
import net.pieterfiers.andin.view.MainActivity
import net.pieterfiers.andin.view.fragments.MapFragment
import org.locationtech.jts.algorithm.Centroid
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.absoluteValue

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class SlippymapMapboxFragment : Fragment() {
    val highlightSearchResults = ObservableBoolean(false)
    val highlightFavorites = ObservableBoolean(false)

    var lastFetch = Instant.EPOCH
    var fetchScheduled = false
    var fetchMutex = Mutex()

    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    private lateinit var viewModel: MapViewModel
    private lateinit var labelManager: SymbolManager
    private lateinit var fillManager: FillManager
    private lateinit var mapElementFills: HashMap<UUID, Long>
    private lateinit var mapElementLabels: HashMap<UUID, Long>
    private var lastSelected: MapElement? = null
    private val uuidWaitingList = ObservableArrayList<UUID>()

    private val currentLevelChangeCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            levelChangeHandler()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.v("AAA","View created")
        super.onViewCreated(view, savedInstanceState)
    }

    private fun levelChangeHandler() {
        val level = viewModel.currentLevel.get()
        if (level != null) {
            val levelExp = Expression.any(
                Expression.neq(
                    Expression.get(
                        "type",
                        Expression.get("custom_data")
                    ), Expression.literal("room")
                ),
                Expression.eq(
                    Expression.get(
                        "level",
                        Expression.get("custom_data")
                    ), Expression.literal(level)
                )
            )
            lifecycleScope.launch {
                fillManager.setFilter(levelExp)
                labelManager.setFilter(levelExp)
            }
        }
    }

    private val buildingsChangeCallback = object : ObservableMap.OnMapChangedCallback<ObservableMap<UUID, MapElement>, UUID, MapElement>() {
        override fun onMapChanged(sender: ObservableMap<UUID, MapElement>?, uuid: UUID?) {
            val building = viewModel.mapData.elements[uuid]
            if (building is Building) {
                addBuilding(building)
            } else {
                Log.v("AAA", "Remove")
            }
        }
    }

    private val selectedMapElementCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            selectedMapElementHandler()
        }
    }

    private val desiredLevelChangeCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            updateLevels()
        }
    }

    private fun onListChangedCallbackFactory(callback: (listener: ObservableList.OnListChangedCallback<ObservableList<UUID>>) -> Unit) : ObservableList.OnListChangedCallback<ObservableList<UUID>> {
        return object : ObservableList.OnListChangedCallback<ObservableList<UUID>>() {
            override fun onChanged(sender: ObservableList<UUID>?) {
                callback(this)
            }

            override fun onItemRangeRemoved(
                sender: ObservableList<UUID>?,
                positionStart: Int,
                itemCount: Int
            ) {
                callback(this)
            }

            override fun onItemRangeMoved(
                sender: ObservableList<UUID>?,
                fromPosition: Int,
                toPosition: Int,
                itemCount: Int
            ) {
                callback(this)
            }

            override fun onItemRangeInserted(
                sender: ObservableList<UUID>?,
                positionStart: Int,
                itemCount: Int
            ) {
                callback(this)
            }

            override fun onItemRangeChanged(
                sender: ObservableList<UUID>?,
                positionStart: Int,
                itemCount: Int
            ) {
                callback(this)
            }
        }
    }

    private val selectedMapElementHandlerMutex = Mutex()
    private fun selectedMapElementHandler() {
        if (!selectedMapElementHandlerMutex.tryLock())
            return
        GlobalScope.launch {
            val uiJobs = LinkedList<Job>()
            val lLastSelected = lastSelected
            if (lLastSelected != null) {
                uiJobs.add(lifecycleScope.launch {
                    val fillId = mapElementFills[lLastSelected.uuid]?:
                        throw RuntimeException("Selected mapElement doesn't have an associated fill")
                    val lastSelectedFill = fillManager.annotations[fillId]?:
                        throw RuntimeException("Selected mapElement doesn't have an associated fill")
                    lastSelectedFill.updateStyle(lLastSelected)
                    fillManager.update(lastSelectedFill)
                })
            }
            val elem = viewModel.selectedMapElement.get()
            if (elem != null) {
                val bounds = elem.geom.envelopeInternal
                bounds.expandByRatio(0.2)
                val latLng = bounds.latLngBounds
                var fillId = mapElementFills[elem.uuid]
                if (fillId == null) {
                    if (!uuidWaitingList.contains(elem.uuid))
                        uuidWaitingList.add(elem.uuid)
                    suspendCoroutine<Unit> {cont ->
                        val checkRemoved = { callback: ObservableList.OnListChangedCallback<ObservableList<UUID>> ->
                            if (!uuidWaitingList.contains(elem.uuid)) {
                                cont.resume(Unit)
                                uuidWaitingList.removeOnListChangedCallback(callback)
                            }
                        }
                        val callback = onListChangedCallbackFactory(checkRemoved)
                        uuidWaitingList.addOnListChangedCallback(callback)
                        checkRemoved(callback)
                        if (mapElementFills.contains(elem.uuid))
                            cont.resume(Unit)
                    }
                }
                fillId = mapElementFills[elem.uuid] ?:
                        throw RuntimeException("Selected mapElement doesn't have an associated fill (should never occur)")
                val fill = fillManager.annotations[fillId] ?:
                    throw RuntimeException("Selected mapElement doesn't have an associated fill (should never occur)")
                lastSelected = elem
                uiJobs.add(lifecycleScope.launch {
                    val pos = map.getCameraForLatLngBounds(latLng) ?:
                    throw RuntimeException("Couldn't get fitting latLngBounds for selected mapElement")
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 1000)
                    fill.updateStyle(elem, true)
//                        fill.fillColor = PropertyFactory.fillColor(Color.WHITE).value
                    fillManager.update(fill)
                })
            } else {
                lastSelected = null
            }
            uiJobs.forEach(action = { it.join() })
            selectedMapElementHandlerMutex.unlock()
        }
    }

    private val onMapChangeCallback = MapView.OnCameraDidChangeListener {
        onMapChange()
    }

    private val searchResultsChangedCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            searchResultsChangedHandler()
        }
    }

    private var prevSearchResultsList: List<Room>? = null

    private val searchResultsChangedHandlerMutex = Mutex()
    private fun searchResultsChangedHandler() {
        if (!searchResultsChangedHandlerMutex.tryLock())
            return
        val context = requireContext()
        GlobalScope.launch {
            val uiJobs = LinkedList<Job>()
            val lPrevSearchResultsList = prevSearchResultsList
            if (lPrevSearchResultsList != null) {
                uiJobs.add(lifecycleScope.launch {
                    for (room in lPrevSearchResultsList) {
                        val labelId = mapElementLabels[room.uuid]?:
                        throw RuntimeException("Previous search result mapElement doesn't have an associated label")
                        val label = labelManager.annotations[labelId]?:
                        throw RuntimeException("Previous search result mapElement doesn't have an associated label")
                        label.updateHighlight(context, isSearchResult = false)
                        labelManager.update(label)
                    }
                })
                prevSearchResultsList = null
            }
            val searchResults = viewModel.searchResults.get()
            if (searchResults != null) {
                if (highlightSearchResults.get()) {
                    prevSearchResultsList = searchResults
                    for (room in searchResults) {
                        var labelId = mapElementLabels[room.uuid]
                        if (labelId == null) {
                            if (!uuidWaitingList.contains(room.uuid))
                                uuidWaitingList.add(room.uuid)
                            suspendCoroutine<Unit> { cont ->
                                val checkRemoved =
                                    { callback: ObservableList.OnListChangedCallback<ObservableList<UUID>> ->
                                        if (!uuidWaitingList.contains(room.uuid)) {
                                            cont.resume(Unit)
                                            uuidWaitingList.removeOnListChangedCallback(callback)
                                        }
                                    }
                                val callback = onListChangedCallbackFactory(checkRemoved)
                                uuidWaitingList.addOnListChangedCallback(callback)
                                checkRemoved(callback)
                                if (mapElementLabels.contains(room.uuid))
                                    cont.resume(Unit)
                            }
                        }
                        labelId = mapElementLabels[room.uuid]
                            ?: throw RuntimeException("Search result mapElement doesn't have an associated label (should never occur)")
                        val label = labelManager.annotations[labelId]
                            ?: throw RuntimeException("Search result mapElement doesn't have an associated label (should never occur)")
                        uiJobs.add(lifecycleScope.launch {
                            label.updateHighlight(context, isSearchResult = true)
                            labelManager.update(label)
                        })
                    }
                }
            } else {
                prevSearchResultsList = null
            }
            uiJobs.forEach(action = { it.join() })
            searchResultsChangedHandlerMutex.unlock()
        }
    }

    private val favoritesChangedCallback = Observer { list: List<FavoriteRoom> ->
        favoritesChangedHandler()
    }

    private var prevFavoritesList: List<FavoriteRoom>? = null
    private val favoritesChangedHandlerMutex = Mutex()
    private fun favoritesChangedHandler() {
        Log.v("AAA", "Favorites chaged: ${viewModel.favorites.value?.size}, prev: ${prevFavoritesList?.size}")
        if (!favoritesChangedHandlerMutex.tryLock()) {
            Log.v("AAA", "Favorites mutex was locked!")
            return
        }
        val context = requireContext()
        GlobalScope.launch {
            val uiJobs = LinkedList<Job>()
            val lPrevFavoritesList = prevFavoritesList
            if (lPrevFavoritesList != null) {
                for (room in lPrevFavoritesList) {
                    val labelId = mapElementLabels[room.uuid]?:
                    throw RuntimeException("Previous favorite mapElement doesn't have an associated label")
                    val label = labelManager.annotations[labelId]?:
                    throw RuntimeException("Previous favorite mapElement doesn't have an associated label")
                    uiJobs.add(lifecycleScope.launch {
                        label.updateHighlight(context)
                        labelManager.update(label)
                    })
                }
                prevFavoritesList = null
            }
            val favorites = viewModel.favorites.value
            Log.v("AAA", "New favorites: ${favorites?.size}")
            if (favorites != null) {
                Log.v("AAA", "Highlight favorites: ${highlightFavorites.get()}")
                if (highlightFavorites.get()) {
                    val lPrevFavoritesList = ArrayList<FavoriteRoom>()
                    for (favorite in favorites) {
                        var labelId = mapElementLabels[favorite.uuid]
                        if (labelId == null) {
                            Log.v("AAA", "Adding ${favorite.uuid} to waiting list...")
//                            if (!uuidWaitingList.contains(favorite.uuid))
//                                uuidWaitingList.add(favorite.uuid)
//                            suspendCoroutine<Unit> { cont ->
//                                val checkRemoved =
//                                    { callback: ObservableList.OnListChangedCallback<ObservableList<UUID>> ->
//                                        if (!uuidWaitingList.contains(favorite.uuid)) {
//                                            cont.resume(Unit)
//                                            uuidWaitingList.removeOnListChangedCallback(callback)
//                                        }
//                                    }
//                                val callback = onListChangedCallbackFactory(checkRemoved)
//                                uuidWaitingList.addOnListChangedCallback(callback)
//                                checkRemoved(callback)
//                                Log.v("AAA", "Last contains check: ${mapElementLabels.contains(favorite.uuid)}")
//                                if (mapElementLabels.contains(favorite.uuid))
//                                    cont.resume(Unit)
//                            }
                        } else {
                            labelId = mapElementLabels[favorite.uuid]
                                ?: throw RuntimeException("Favorite mapElement doesn't have an associated label (should never occur)")
                            val label = labelManager.annotations[labelId]
                                ?: throw RuntimeException("Favorite mapElement doesn't have an associated label (should never occur)")
                            uiJobs.add(lifecycleScope.launch {
                                Log.v("AAA", "Marking Favorite")
                                label.updateHighlight(context, isFavorite = true)
                                Log.v("AAA", "Marking update")
                                labelManager.update(label)
                                Log.v("AAA", "Marking done")
                            })
                            lPrevFavoritesList.add(favorite)
                        }
                    }
                    prevFavoritesList = lPrevFavoritesList
                }
            } else {
                prevFavoritesList = null
            }
            Log.v("AAA", "Joining uijobs")
            uiJobs.forEach(action = { it.join() })
            Log.v("AAA", "Jobs joined")
            favoritesChangedHandlerMutex.unlock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            highlightFavorites.set(it.getBoolean(MapFragment.ARG_HIGHLIGHT_FAVORITES, false))
            highlightSearchResults.set(it.getBoolean(MapFragment.ARG_HIGHLIGHT_SEARCH_RESULTS, false))
        }
        viewModel = activity?.run {
            ViewModelProviders.of(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        val context = activity?.applicationContext ?: throw RuntimeException("No context")
        Mapbox.getInstance(context, getString(R.string.mapbox_access_token));
    }

    private val highlightSearchResultsChangedCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            highlightSearchResultsHandler()
        }

    }

    fun highlightSearchResultsHandler() {
        searchResultsChangedHandler()
    }

    private val highlightFavoritesChangedCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            highlightFavoritesHandler()
        }
    }

    fun highlightFavoritesHandler() {
        favoritesChangedHandler()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentMapboxWrapperBinding.inflate(inflater)

        mapView = binding.map
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { mapboxMap ->
            map = mapboxMap
            val prevPos = viewModel.cameraPosition
            if (prevPos != null) {
                map.cameraPosition = prevPos
            }
            map.setStyle(Style.DARK) {style ->
                fillManager = FillManager(mapView, mapboxMap, style)
                mapElementFills = HashMap()
                labelManager = SymbolManager(mapView, mapboxMap, style)
                mapElementLabels = HashMap()
                viewModel.mapData.elements.addOnMapChangedCallback(buildingsChangeCallback)
                fillManager.addClickListener {fill ->
                    val data = fill.data ?: throw RuntimeException("Fill is missing data")
                    val obj = data.asJsonObject ?: throw RuntimeException("Fill isn't an object")
                    val uuidElem = obj.getAsJsonPrimitive("uuid") ?: throw RuntimeException("No UUID elem in object")
                    val uuid = UUID.fromString(uuidElem.asString)
                    val mapElement = viewModel.mapData.elements[uuid]
                    viewModel.selectedMapElement.set(mapElement)
                }
                viewModel.currentLevel.addOnPropertyChangedCallback(currentLevelChangeCallback)
                levelChangeHandler()
                viewModel.desiredLevel.addOnPropertyChangedCallback(desiredLevelChangeCallback)
                updateLevels()
                viewModel.selectedMapElement.addOnPropertyChangedCallback(selectedMapElementCallback)
                selectedMapElementHandler()
                for (building in viewModel.mapData.elements.values) {
                    if (building is Building)
                        addBuilding(building)
                }
                viewModel.searchResults.addOnPropertyChangedCallback(
                    searchResultsChangedCallback
                )
                searchResultsChangedHandler()
                viewModel.favorites.observe(this, favoritesChangedCallback)
                favoritesChangedHandler()

                highlightSearchResults.addOnPropertyChangedCallback(highlightSearchResultsChangedCallback)
                highlightSearchResultsHandler()
                highlightFavorites.addOnPropertyChangedCallback(highlightFavoritesChangedCallback)
                highlightFavoritesHandler()
            }
            mapView.addOnCameraDidChangeListener(onMapChangeCallback)
            onMapChange()
            map.addOnMapClickListener {
                viewModel.selectedMapElement.set(null)
                false // Don't consume
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.currentLevel.removeOnPropertyChangedCallback(currentLevelChangeCallback)
        viewModel.mapData.elements.removeOnMapChangedCallback(buildingsChangeCallback)
        viewModel.selectedMapElement.removeOnPropertyChangedCallback(selectedMapElementCallback)
        viewModel.searchResults.removeOnPropertyChangedCallback(searchResultsChangedCallback)
        viewModel.desiredLevel.removeOnPropertyChangedCallback(desiredLevelChangeCallback)
        viewModel.favorites.removeObserver(favoritesChangedCallback)
        highlightSearchResults.removeOnPropertyChangedCallback(highlightSearchResultsChangedCallback)
        highlightFavorites.removeOnPropertyChangedCallback(highlightFavoritesChangedCallback)
        mapView.removeOnCameraDidChangeListener(onMapChangeCallback)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment SlippymapMapboxFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            SlippymapMapboxFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    private fun updateLevels() {
        lifecycleScope.launch {
            val envelope = Envelope(viewEnvelope)
            envelope.expandByMeters(5.0)
            val newLevels = viewModel.mapData.levelsInEnvelope(envelope)
            if (newLevels.isEmpty()) {
                viewModel.currentLevel.set(null)
            } else {
                val desired = viewModel.desiredLevel.get()
                if (desired == null) {
                    viewModel.currentLevel.set(newLevels.first())
                } else if (newLevels.contains(desired)) {
                    viewModel.currentLevel.set(desired)
                } else {
                    val closestMatch = newLevels
                        .minBy { abs(it - desired) }
                        ?: newLevels.first()
                    viewModel.currentLevel.set(closestMatch)
                }
            }
            viewModel.levels.set(newLevels)
        }
    }

    private fun onMapChange() {
        scrollHandler()
        updateLevels()
        viewModel.cameraPosition = map.cameraPosition
    }

    private fun addBuilding(building: Building) {
        Log.v("AAA", "Adding building ${building.geom.areaSqm}")
        val context = requireContext()
        lifecycleScope.launch {
            val polygon = building.geom
            val centroid = Centroid.getCentroid(polygon)
            val labelOptions = SymbolOptions()
                .withGeometry(centroid.mapbox)
                .withTextField(building.labelText)
                .withTextColor(PropertyFactory.textColor(Color.WHITE).value)
            val label = labelManager.create(labelOptions)
            val buildingData = building.toDataJson()
            label.data = buildingData
            label.updateStyle(context, building)
            labelManager.update(label)
            mapElementLabels[building.uuid] = label.id
            val fillOptions = FillOptions().withGeometry(polygon.mapbox)
            val fill = fillManager.create(fillOptions)
            fill.data = buildingData
            fill.updateStyle(building)
            fillManager.update(fill)
            mapElementFills[building.uuid] = fill.id
            if (building is CompleteBuilding) {
                println("Adding rooms ${building.rooms.size}")
                val fills = LinkedList<Fill>()
                val labels = LinkedList<Symbol>()
                for (room in building.rooms) {
                    val roomPolygon = room.geom
                    val roomCentroid = Centroid.getCentroid(roomPolygon)
                    val roomLabelOptions = SymbolOptions().withGeometry(roomCentroid.mapbox)
                    val roomLabel= labelManager.create(roomLabelOptions)
                    val roomData = room.toDataJson()
                    roomLabel.data = roomData
                    val fav = !viewModel.favorites.value?.filter {r -> r.uuid == room.uuid }.isNullOrEmpty()
                    val search = !viewModel.searchResults.get()?.filter {r -> r.uuid == room.uuid }.isNullOrEmpty()
                    roomLabel.updateStyle(context, room, false, isSearchResult =  search, isFavorite = fav)
                    mapElementLabels[room.uuid] = roomLabel.id
                    val roomFillOptions = FillOptions().withGeometry(roomPolygon.mapbox)
                    val roomFill = fillManager.create(roomFillOptions)
                    roomFill.data = roomData
                    val selected = viewModel.selectedMapElement.get()?.uuid == room.uuid
                    roomFill.updateStyle(room, selected = selected)
                    fills.add(roomFill)
                    mapElementFills[room.uuid] = roomFill.id
                    Log.v("AAA", "Remove ${room.uuid} from waiting list")
                    uuidWaitingList.remove(room.uuid)
                }
                fillManager.update(fills)
                labelManager.update(labels)
                updateLevels()
            }
            uuidWaitingList.remove(building.uuid)
        }
    }

    private fun scrollHandler() {
        if (!fetchMutex.tryLock()) {
            return
        }
        val runHandler = {
            fetchScheduled = false
            scrollHandlerUnlimited()
        }

        val timeToNextPossible = Duration.between(Instant.now(), lastFetch.plus(timeout))
        if (timeToNextPossible.isNegative) {
            runHandler()
            return
        }

        if (fetchScheduled) {
            fetchMutex.unlock()
            return
        }

        fetchScheduled = true
        android.os.Handler().postDelayed({
            runHandler()
        }, timeToNextPossible.toMillis().absoluteValue);
    }

    private var incompleteBuildingFetchedArea = AtomicReference(emptyGeom())
    private var buildingFetchedArea = AtomicReference(emptyGeom())

    private val zoom: Double get() {
        return map.cameraPosition.zoom
    }

    private val viewEnvelope: Envelope get() {
        return map.projection.visibleRegion.latLngBounds.envelope
    }

    val timeout = Duration.ofSeconds(4)
    private fun scrollHandlerUnlimited() {
        val runtime = Runtime.getRuntime();
        val usedMemInMB=(runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        val maxHeapSizeInMB=runtime.maxMemory() / 1048576L;
        val availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB;
        Log.v("AAA", "Fetching (${availHeapSizeInMB} MB avail), zoom: $zoom");

        val viewGeometry = GeometryFactory().toGeometry(viewEnvelope)

//        zoom = 5.0
        val handler = { e: Exception ->
            if (e !is GqlException)
                throw e

            val builder = StringBuilder(e.message?: "")
            var cause = e.cause
            while (cause != null) {
                builder.append(":\n${cause.message}")
                cause = cause.cause
            }
            dialog("Error fetching buildings", builder.toString())
            fetchMutex.unlock()
        }
        if (zoom > 17.5) {
            val newGeometry = viewGeometry.difference(incompleteBuildingFetchedArea.get())
            val env = newGeometry.envelopeInternal
            if (!env.isNull) {
                fetchBuildingsAndRooms(
                    viewModel.apolloClient,
                    viewEnvelope,
                    handler
                ) {
                    viewModel.mapData.addBuildings(it)
                    incompleteBuildingFetchedArea.set(
                        incompleteBuildingFetchedArea.get().union(
                            newGeometry
                        )
                    )
                    lastFetch = Instant.now()
                    fetchMutex.unlock()
                }
            } else {
                fetchMutex.unlock()
            }
        } else {
            val newGeometry = viewGeometry.difference(buildingFetchedArea.get())
            val env = newGeometry.envelopeInternal
            if (!env.isNull) {
                fetchBuildings(
                    viewModel.apolloClient,
                    viewEnvelope,
                    handler
                ) {
                    viewModel.mapData.addBuildings(it)
                    buildingFetchedArea.set(buildingFetchedArea.get().union(newGeometry))
                    lastFetch = Instant.now()
                    fetchMutex.unlock()
                }
            } else {
                fetchMutex.unlock()
            }
        }
    }

    private fun dialog(title: String, message: String?) {
        val act = activity
        if (act == null) {
            Log.e("AAA", "No activity to display dialog, doing nothing")
            return
        }
        (act as MainActivity).dialog(title, message)
    }
}
