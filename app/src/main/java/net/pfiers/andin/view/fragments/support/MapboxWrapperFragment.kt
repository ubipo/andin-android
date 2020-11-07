package net.pfiers.andin.view.fragments.support

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.*
import androidx.databinding.Observable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.utils.BitmapUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import net.pfiers.andin.*
import net.pfiers.andin.R
import net.pfiers.andin.databinding.FragmentMapboxWrapperBinding
import net.pfiers.andin.db.FavoriteRoom
import net.pfiers.andin.model.geospatial.areaSqm
import net.pfiers.andin.model.geospatial.expandByMeters
import net.pfiers.andin.model.geospatial.expandByRatio
import net.pfiers.andin.model.map.*
import net.pfiers.andin.model.mapbox.*
import net.pfiers.andin.model.nav.PoiVertex
import net.pfiers.andin.model.onPropertyChangedCallback
import net.pfiers.andin.view.MainActivity
import net.pfiers.andin.view.fragments.MapFragment
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.absoluteValue
import kotlin.random.Random


class SlippymapMapboxFragment : Fragment() {
    val highlightSearchResults = ObservableBoolean(false)
    val highlightFavorites = ObservableBoolean(false)

    private var lastFetch = Instant.EPOCH
    private var fetchScheduled = false
    private var fetchMutex = Mutex()

    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    private lateinit var viewModel: MapViewModel
    private lateinit var symbolManager: SymbolManager
    private var navSymbols: Set<Symbol> = emptySet()
    private lateinit var fillManager: FillManager
    private lateinit var lineManager: LineManager
    private lateinit var circleManager: CircleManager
    private lateinit var mapElementFills: HashMap<UUID, Long>
    private lateinit var mapElementSymbols: HashMap<UUID, Long>
    private lateinit var mapElementCircles: HashMap<UUID, Long>
    private var lastSelected: MapElement? = null
    private val uuidWaitingList = ObservableArrayList<UUID>()

    private val currentLevelChangeCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            levelChangeHandler()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.v("AAA", "View created")
        Log.v("AAA", ">>>> RANGE ${LevelRange(1.0, 2.0).intersect(LevelRange(1.0, null))}")
        super.onViewCreated(view, savedInstanceState)
    }

    private fun levelChangeHandler() {
        val level = viewModel.currentLevel.get()
        if (level != null) {
            val levelExp = Expression.any(
                Expression.eq(
                    Expression.get("type", udExpr()),
                    Expression.literal("building")
                ),
                levelWithinRangeExpr(level)
            )
            val zoomExp = Expression.gte(
                Expression.zoom(),
                Expression.literal(17)
            )
            lifecycleScope.launch {
                fillManager.setFilter(levelExp)
                symbolManager.setFilter(
                    Expression.all(levelExp, zoomExp)
                )
                circleManager.setFilter(
                    Expression.all(
                        levelExp,
                        Expression.gte(
                            Expression.zoom(),
                            Expression.literal(19)
                        )
                    )
                )
                lineManager.setFilter(
                    Expression.all(levelExp, zoomExp)
                )
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

    private val departureDestinationChangeCallback = onPropertyChangedCallback(this::onDepartureDestinationChange)

    private fun onDepartureDestinationChange() {
        if (viewModel.path.get() != null)
            return

        val newNavSymbols = HashSet<Symbol>()
        val departure = viewModel.departure.get()
        if (departure != null) {
            val startSymbol = symbolManager.create(
                createSymbolOptions(LevelRange(departure.level), departure.coordinate, R.drawable.ic_nav_start)
            )
            newNavSymbols.add(startSymbol)
        }
        val destination = viewModel.destination.get()
        if (destination != null) {
            val finishSymbol = symbolManager.create(
                createSymbolOptions(LevelRange(destination.level), destination.coordinate, R.drawable.ic_nav_finish)
            )
            newNavSymbols.add(finishSymbol)
        }

        lineManager.deleteAll()
        symbolManager.delete(navSymbols.toList())
        navSymbols = newNavSymbols
        onPathChange()
    }

    private val pathChangeCallback = onPropertyChangedCallback(this::onPathChange)

    private fun onPathChange() {
        val path = viewModel.path.get() ?: return

        val sameLevelSections = ArrayList<SameLevelRangeNavSection>()
        var prevLevelRange = path.vertexList.first().levelRange
        var prevVertex = path.vertexList.first()
        var currentSection = mutableListOf(path.vertexList.first())
        for (vertex in path.vertexList.subList(1, path.vertexList.size)) {
            Log.v("AAA", "Overlaps: ${vertex.levelRange.overlaps(prevLevelRange)}")
            if (!vertex.levelRange.overlaps(prevLevelRange)) {
                prevLevelRange = vertex.levelRange
                val levelRange = currentSection.map { it.levelRange }.reduce { acc, levelRange ->
                    acc.intersect(levelRange) ?: error("$sameLevelSections $currentSection ${currentSection.map { it.levelRange }} ${currentSection.filterIsInstance<PoiVertex>().map { it.poiId }}")
                }
                sameLevelSections.add(Pair(levelRange, currentSection))
                currentSection = mutableListOf(prevVertex, vertex)
            } else {
                currentSection.add(vertex)
            }
            prevVertex = vertex
        }
        val lastLevelRange = currentSection.map { it.levelRange }.reduce { acc, levelRange -> acc.intersect(levelRange)!! }
        sameLevelSections.add(Pair(lastLevelRange, currentSection))
        val sectionsLevels = sameLevelSections.map { it.second.map { it.levelRange } }
        Log.v("AAA", "Path sections: ${sectionsLevels}")
        val newNavSymbols = HashSet<Symbol>()
        lineManager.deleteAll()
        for ((sectionI, rangeSection) in sameLevelSections.withIndex()) {
            val (_, section) = rangeSection
            if (section.size > 1) {
                lineManager.create(createLineOptions(rangeSection))
                val prevRangeSection = if (sectionI == 0) null else sameLevelSections[sectionI - 1]
                val startSymbol = symbolManager.create(
                    createSymbolOptionsStart(rangeSection, prevRangeSection)
                )
                newNavSymbols.add(startSymbol)
            }
            val nextRangeSection = if (sectionI == sameLevelSections.size - 1) null else sameLevelSections[sectionI + 1]
            val finishSymbol = symbolManager.create(
                createSymbolOptionsFinish(rangeSection, nextRangeSection)
            )
            newNavSymbols.add(finishSymbol)
        }
        symbolManager.delete(navSymbols.toList())
        navSymbols = newNavSymbols
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
                    val fillId = mapElementFills[lLastSelected.uuid]
                        ?: throw RuntimeException("Selected mapElement doesn't have an associated fill")
                    val lastSelectedFill = fillManager.annotations[fillId]
                        ?: throw RuntimeException("Selected mapElement doesn't have an associated fill")
                    lastSelectedFill.updateStyle(lLastSelected)
                    fillManager.update(lastSelectedFill)
                })
            }
            val elem = viewModel.selectedMapElement.get()
            if (elem != null) {
                val bounds = elem.geometry.envelopeInternal
                bounds.expandByRatio(0.2)
                val latLng = bounds.latLngBounds
                var fillId = mapElementFills[elem.uuid]
                if (fillId == null) {
                    if (!uuidWaitingList.contains(elem.uuid))
                        uuidWaitingList.add(elem.uuid)
                    suspendCoroutine<Unit> { cont ->
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
                    val pos = map.getCameraForLatLngBounds(latLng)
                        ?: throw RuntimeException("Couldn't get fitting latLngBounds for selected mapElement")
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
                        val labelId = mapElementSymbols[room.uuid]
                            ?: throw RuntimeException("Previous search result mapElement doesn't have an associated label")
                        val label = symbolManager.annotations[labelId]
                            ?: throw RuntimeException("Previous search result mapElement doesn't have an associated label")
                        label.updateHighlight(context, isSearchResult = false)
                        symbolManager.update(label)
                    }
                })
                prevSearchResultsList = null
            }
            val searchResults = viewModel.searchResults.get()
            if (searchResults != null) {
                if (highlightSearchResults.get()) {
                    prevSearchResultsList = searchResults
                    for (room in searchResults) {
                        var labelId = mapElementSymbols[room.uuid]
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
                                if (mapElementSymbols.contains(room.uuid))
                                    cont.resume(Unit)
                            }
                        }
                        labelId = mapElementSymbols[room.uuid]
                            ?: throw RuntimeException("Search result mapElement doesn't have an associated label (should never occur)")
                        val label = symbolManager.annotations[labelId]
                            ?: throw RuntimeException("Search result mapElement doesn't have an associated label (should never occur)")
                        uiJobs.add(lifecycleScope.launch {
                            label.updateHighlight(context, isSearchResult = true)
                            symbolManager.update(label)
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

    private val favoritesChangedCallback = Observer { _: List<FavoriteRoom> ->
        favoritesChangedHandler()
    }

    private var prevFavoritesList: List<FavoriteRoom>? = null
    private val favoritesChangedHandlerMutex = Mutex()
    private fun favoritesChangedHandler() {
        Log.v(
            "AAA",
            "Favorites chaged: ${viewModel.favorites.value?.size}, prev: ${prevFavoritesList?.size}"
        )
        if (!favoritesChangedHandlerMutex.tryLock()) {
            Log.v("AAA", "Favorites mutex was locked!")
            return
        }
        val context = requireContext()
        GlobalScope.launch {
            val uiJobs = LinkedList<Job>()
            prevFavoritesList?.let {
                for (room in it) {
                    val labelId = mapElementSymbols[room.uuid]?:
                    throw RuntimeException("Previous favorite mapElement doesn't have an associated label")
                    val label = symbolManager.annotations[labelId]?:
                    throw RuntimeException("Previous favorite mapElement doesn't have an associated label")
                    uiJobs.add(lifecycleScope.launch {
                        label.updateHighlight(context)
                        symbolManager.update(label)
                    })
                }
                prevFavoritesList = null
            }
            val favorites = viewModel.favorites.value
            Log.v("AAA", "New favorites: ${favorites?.size}")
            if (favorites != null) {
                Log.v("AAA", "Highlight favorites: ${highlightFavorites.get()}")
                if (highlightFavorites.get()) {
                    val addedFavorites = ArrayList<FavoriteRoom>()
                    for (favorite in favorites) {
                        var labelId = mapElementSymbols[favorite.uuid]
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
                            labelId = mapElementSymbols[favorite.uuid]
                                ?: throw RuntimeException("Favorite mapElement doesn't have an associated label (should never occur)")
                            val label = symbolManager.annotations[labelId]
                                ?: throw RuntimeException("Favorite mapElement doesn't have an associated label (should never occur)")
                            uiJobs.add(lifecycleScope.launch {
                                Log.v("AAA", "Marking Favorite")
                                label.updateHighlight(context, isFavorite = true)
                                Log.v("AAA", "Marking update")
                                symbolManager.update(label)
                                Log.v("AAA", "Marking done")
                            })
                            addedFavorites.add(favorite)
                        }
                    }
                    prevFavoritesList = addedFavorites
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
            highlightSearchResults.set(
                it.getBoolean(
                    MapFragment.ARG_HIGHLIGHT_SEARCH_RESULTS,
                    false
                )
            )
        }
        viewModel = activity?.run {
            ViewModelProvider(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        val context = activity?.applicationContext ?: throw RuntimeException("No context")
        Mapbox.getInstance(context, getString(R.string.mapbox_access_token))
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

    private fun addStyleDrawables(style: Style) {
        val prefix = "ic-andin"
        val icons = listOf(
            R.drawable.ic_map_coffee,
            R.drawable.ic_map_toilet_male,
            R.drawable.ic_map_toilet_female,
            R.drawable.ic_map_fire_extinguisher,
            R.drawable.ic_map_fire_hose,
            R.drawable.ic_map_door,
            R.drawable.ic_nav_start,
            R.drawable.ic_nav_up,
            R.drawable.ic_nav_down,
            R.drawable.ic_nav_finish,
            R.drawable.ic_map_first_aid
        )
        icons.forEach { id ->
            val name = prefix + String.format("%06X", Random.nextInt(0xFFFFFF + 1))
            iconNames.putIfAbsent(id, name)
        }
        val act = activity ?: error("No activity")
        for ((id, name) in iconNames) {
            style.addImage(name, BitmapUtils.getBitmapFromDrawable(
                ContextCompat.getDrawable(act, id)
            ) ?: error("Drawable for $name not found"))
        }
    }

    private fun setStyle(styleStr: String) {
        map.setStyle(styleStr) { style ->
            addStyleDrawables(style)
            fillManager = FillManager(mapView, map, style)
            mapElementFills = HashMap()
            lineManager = LineManager(mapView, map, style)
            circleManager = CircleManager(mapView, map, style)
            mapElementCircles = HashMap()
            symbolManager = SymbolManager(mapView, map, style)
            mapElementSymbols = HashMap()

            viewModel.mapData.elements.addOnMapChangedCallback(buildingsChangeCallback)
            fillManager.addClickListener { fill ->
                val data = fill.data ?: throw RuntimeException("Fill is missing data")
                val obj = data.asJsonObject ?: throw RuntimeException("Fill data isn't an object")
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
            viewModel.favorites.observe(viewLifecycleOwner, favoritesChangedCallback)
            favoritesChangedHandler()

            viewModel.path.addOnPropertyChangedCallback(pathChangeCallback)
            onPathChange()

            viewModel.departure.addOnPropertyChangedCallback(departureDestinationChangeCallback)
            viewModel.destination.addOnPropertyChangedCallback(departureDestinationChangeCallback)
            onDepartureDestinationChange()

            highlightSearchResults.addOnPropertyChangedCallback(
                highlightSearchResultsChangedCallback
            )
            highlightSearchResultsHandler()
            highlightFavorites.addOnPropertyChangedCallback(highlightFavoritesChangedCallback)
            highlightFavoritesHandler()
        }
    }

    private lateinit var sharedPreferences: SharedPreferences

    private fun allowRotateFromPrefs() {
        val allowRotate = sharedPreferences.getBoolean(PREF_ALLOW_ROTATE, true)
        map.uiSettings.isRotateGesturesEnabled = allowRotate
    }

    private fun lightMapFromPrefs() {
        val lightMap = sharedPreferences.getBoolean(PREF_LIGHT_MAP, true)
        val styleStr = if (lightMap) Style.LIGHT else Style.DARK
        setStyle(styleStr)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentMapboxWrapperBinding.inflate(inflater)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

        mapView = binding.map
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { mapboxMap ->
            map = mapboxMap

            val prevPos = viewModel.cameraPosition
            if (prevPos != null) {
                map.cameraPosition = prevPos
            }

            sharedPreferences.registerOnSharedPreferenceChangeListener { _, s ->
                when (s) {
                    PREF_ALLOW_ROTATE -> allowRotateFromPrefs()
                    PREF_LIGHT_MAP -> lightMapFromPrefs()
                }
            }
            allowRotateFromPrefs()
            lightMapFromPrefs()

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
        viewModel.path.removeOnPropertyChangedCallback(pathChangeCallback)
        viewModel.departure.removeOnPropertyChangedCallback(departureDestinationChangeCallback)
        viewModel.destination.removeOnPropertyChangedCallback(departureDestinationChangeCallback)
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

    private fun updateLevels() {
        lifecycleScope.launch {
            val envelope = Envelope(viewEnvelope)
            envelope.expandByMeters(5.0)
            val newLevels = viewModel.mapData.levelRangeInEnvelope(envelope)
            if (newLevels == null) {
                viewModel.currentLevel.set(null)
            } else {
                val desired = viewModel.desiredLevel.get()
                if (desired == null) {
                    viewModel.currentLevel.set(newLevels.from)
                } else if (newLevels.contains(desired)) {
                    viewModel.currentLevel.set(desired)
                } else {
                    viewModel.currentLevel.set(newLevels.closestMatch(desired))
                }
            }
            viewModel.levels.set(newLevels ?: LevelRange(0.0))
        }
    }

    private fun onMapChange() {
        scrollHandler()
        updateLevels()
        viewModel.cameraPosition = map.cameraPosition
    }

    private fun addBuilding(building: Building) {
        Log.v("AAA", "Adding building ${building.geometry.areaSqm}")
        val context = requireContext()
        lifecycleScope.launch {
            val label = symbolManager.create(createSymbolOptions(building))
            symbolManager.update(label)
            mapElementSymbols[building.uuid] = label.id
            val fill = fillManager.create(createFillOptions(building))
            fill.updateStyle(building)
            fillManager.update(fill)
            mapElementFills[building.uuid] = fill.id
            if (building is CompleteBuilding) {
                println("Adding rooms ${building.indoorElements.rooms.size}")
                val fills = LinkedList<Fill>()
                val labels = LinkedList<Symbol>()
                val circles = LinkedList<Circle>()
                for (room in building.indoorElements.rooms) {
                    val roomSymbol = symbolManager.create(createSymbolOptions(room))
                    val isFavorite = !viewModel.favorites.value?.filter { r -> r.uuid == room.uuid }.isNullOrEmpty() && highlightFavorites.get()
                    val isSearchResult = !viewModel.searchResults.get()?.filter { r -> r.uuid == room.uuid }.isNullOrEmpty() && highlightSearchResults.get()
                    val isSelected = viewModel.selectedMapElement.get()?.uuid == room.uuid
                    roomSymbol.updateHighlight(
                        context,
                        isSelected,
                        isSearchResult,
                        isFavorite
                    )
                    mapElementSymbols[room.uuid] = roomSymbol.id
                    val roomFill = fillManager.create(createFillOptions(room))
                    roomFill.updateStyle(room, isSelected)
                    fills.add(roomFill)
                    mapElementFills[room.uuid] = roomFill.id
                    uuidWaitingList.remove(room.uuid)
                }
                for (corridor in building.indoorElements.corridors) {
                    val corridorFill = fillManager.create(createFillOptions(corridor))
                    val isSelected = viewModel.selectedMapElement.get()?.uuid == corridor.uuid
                    corridorFill.updateStyle(corridor, isSelected)
                    fills.add(corridorFill)
                    mapElementFills[corridor.uuid] = corridorFill.id
                    uuidWaitingList.remove(corridor.uuid)
                }
                for (entrance in building.indoorElements.entrances) {
                    val entranceCircle = circleManager.create(createCircleOptions(entrance))
                    entranceCircle.updateStyle(entrance)
                    circles.add(entranceCircle)
                    mapElementCircles[entrance.uuid] = entranceCircle.id
                    uuidWaitingList.remove(entrance.uuid)
                }
                for (fireSuppressionTool in building.indoorElements.fireSuppressionTools) {
                    val fireSuppressionToolSymbol = symbolManager.create(createSymbolOptions(fireSuppressionTool))
                    uuidWaitingList.remove(fireSuppressionTool.uuid)
                }
//                for (fireSuppressionTool in building.indoorElements.fireSuppressionTools) {
//                    val roomLabel = symbolManager.create(createSymbolOptions(room))
//                    val fav = !viewModel.favorites.value?.filter { r -> r.uuid == room.uuid }.isNullOrEmpty() && highlightFavorites.get()
//                    val search = !viewModel.searchResults.get()?.filter { r -> r.uuid == room.uuid }.isNullOrEmpty() && highlightSearchResults.get()
//                    roomLabel.updateStyle(
//                        context,
//                        room,
//                        false,
//                        isSearchResult = search,
//                        isFavorite = fav
//                    )
//                    mapElementLabels[room.uuid] = roomLabel.id
//                    val roomFill = fillManager.create(createFillOptions(room))
//                    val selected = viewModel.selectedMapElement.get()?.uuid == room.uuid
//                    roomFill.updateStyle(room, selected = selected)
//                    fills.add(roomFill)
//                    mapElementFills[room.uuid] = roomFill.id
//                    uuidWaitingList.remove(room.uuid)
//                }
                fillManager.update(fills)
                symbolManager.update(labels)
                circleManager.update(circles)
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

            val builder = StringBuilder(e.message ?: "")
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

    companion object {
        val iconNames: HashMap<Int, String> = HashMap()

        const val ARG_PARAM1 = "param1"
        const val ARG_PARAM2 = "param2"

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
}
