package net.pieterfiers.andin.view

import android.app.AlertDialog
import android.app.Dialog
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.andin_api.type.CustomType
import com.apollographql.apollo.ApolloClient
import com.google.android.material.snackbar.Snackbar
import com.mapbox.mapboxsdk.maps.MapView
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import net.pieterfiers.andin.*
import net.pieterfiers.andin.databinding.ActivityMainBinding
import net.pieterfiers.andin.db.AndinDb
import net.pieterfiers.andin.db.getOrDownload
import net.pieterfiers.andin.model.map.Room
import net.pieterfiers.andin.view.fragments.MapFragment
import net.pieterfiers.andin.view.fragments.support.SlippymapMapboxFragment
import okhttp3.HttpUrl
import java.util.*
import kotlin.coroutines.resume


const val ACTION_ROOM = "net.pieterfiers.andin.ROOM"
const val EXTRA_ROOM_UUID = "net.pieterfiers.andin.view.EXTRA_ROOM_UUID"

private const val DEFAULT_HOSTNAME = "home.ubipo.net"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var view: View
    lateinit var map: MapView
    lateinit var viewModel: MapViewModel

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Log.v("AAA","Intent: ${intent.action}")
        when(intent.action) {
            Intent.ACTION_SEARCH -> handleSearchIntent(intent)
            Intent.ACTION_MAIN -> {}
            Intent.ACTION_VIEW -> handleViewIntent(intent)
            null -> handleImplicitIntent(intent)
            else -> {
                Log.e("AAA", "Received unknown intent!")
                Log.e("AAA", "e: ${intent.extras} d: ${intent.data}")
            }
        }
        this.intent = null // Prevents triggering handleIntent() again next time
    }

    private fun handleSearchIntent(intent: Intent) {
        intent.getStringExtra(SearchManager.QUERY)?.also { query ->
            handleSearch(query)
        }
    }

    private fun handleViewIntent(intent: Intent) {
        val query = intent.getStringExtra(SearchManager.QUERY)
        if (query != null) {
            handleSearch(query)
        }
    }

    private var searchIntentHandlerJob: Job? = null
    private val searchHandlerMutex = Mutex()
    private fun handleSearch(query: String) {
        val context = this
        GlobalScope.launch {
            searchHandlerMutex.lock() // Wait until prev launched
            searchIntentHandlerJob?.cancel() // Kill prev :)
            searchIntentHandlerJob = launch {
                val scope = this
                searchHandlerMutex.unlock()
                viewModel.searchResults.set(null)
                viewModel.query.set(null)
                viewModel.query.set(query)
                SearchRecentSuggestions(
                    context,
                    MainSearchSuggestionProvider.AUTHORITY,
                    MainSearchSuggestionProvider.MODE
                ).saveRecentQuery(query, null)
                val curr = viewModel.navController.currentDestination?.id
                if (curr != R.id.searchResultsFragment) {
                    val destination: Int
                    if (curr == R.id.mapFragment) {
                        destination = R.id.action_mapFragment_to_searchResultsFragment
                    } else if (curr == R.id.searchResultsMapFragment) {
                        destination =
                            R.id.action_searchResultsMapFragment_to_searchResultsFragment
                    } else if (curr == R.id.favoritesMapFragment) {
                        destination = R.id.action_favoritesMapFragment_to_searchResultsFragment
                    } else {
                        throw RuntimeException("No idea how to get to the results fragment from this destination")
                    }
                    lifecycleScope.launch {
                        if (scope.isActive) {
                            viewModel.navController.navigate(destination)
                        } else {
                            Log.e("AAA", "Navigate after cancel")
                        }
                    }
                }
                val errorHandler = { e: Exception ->
                    if (e !is GqlException)
                        throw e

                    val builder = StringBuilder(e.message ?: "")
                    var cause = e.cause
                    while (cause != null) {
                        builder.append(":\n${cause.message}")
                        cause = cause.cause
                    }
                    dialog("Error searching for rooms", builder.toString())
                }
                val rooms = suspendCancellableCoroutine<List<Room>> { cont ->
                    fetchRooms(
                        viewModel.apolloClient,
                        query,
                        errorHandler
                    ) { rooms ->
                        cont.resume(rooms)
                    }
                }
                if (scope.isActive)
                    viewModel.searchResults.set(rooms)
            }
        }
    }

    private fun handleImplicitIntent(intent: Intent) {
        val roomUUID = intent.getStringExtra(EXTRA_ROOM_UUID)
        if (roomUUID != null) {
            handleRoomIntent(UUID.fromString(roomUUID))
        }
    }

    private fun handleRoomIntent(roomUUID: UUID) {
        val handler = { e: Exception ->
            if (e !is GqlException)
                throw e

            val builder = StringBuilder(e.message?: "")
            var cause = e.cause
            while (cause != null) {
                builder.append(":\n${cause.message}")
                cause = cause.cause
            }
            dialog("Error getting info for room (UUID: $roomUUID)", builder.toString())
        }
        fetchRoom(
            viewModel.apolloClient,
            roomUUID,
            handler
        ) { building ->
            viewModel.mapData.addBuildings(listOf(building))
            val room = viewModel.mapData.rooms.filter { room ->
                room.uuid == roomUUID
            }.firstOrNull() ?: throw RuntimeException("Room wasn't properly fetched")
            viewModel.selectedMapElement.set(room)
            viewModel.desiredLevel.set(room.level)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (viewModel.navController.currentDestination?.id == R.id.sharedRoomFragment) {
            finish()
            return true
        }
        return viewModel.navController.navigateUp()
    }

    override fun onBackPressed() {
        if (viewModel.navController.currentDestination?.id == R.id.sharedRoomFragment) {
            finish()
            return
        }
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        val backStackEntryCount = navHostFragment?.childFragmentManager?.backStackEntryCount
        if (backStackEntryCount == 0) {
            return super.onBackPressed()
        }
        viewModel.navController.navigateUp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProviders.of(this)[MapViewModel::class.java]
        binding = DataBindingUtil.setContentView(this,
            R.layout.activity_main
        )
        view = binding.root

        viewModel.dao = AndinDb.getInstance(application).dao

        val d = viewModel.dao

        val all = d.getAll()
        viewModel.favorites = all

        viewModel.navController = findNavController(R.id.navHostFragment)

        if (!viewModel.apolloClientInitialized) {
            val sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)
            val urlBuilder = HttpUrl.Builder()
                .port(41533)
                .addPathSegment("graphql")
                .scheme("http")
            val prefHost = sharedPreferences.getString("api_hostname",null)
            val host = if (!prefHost.isNullOrBlank()) prefHost else DEFAULT_HOSTNAME
            try {
                urlBuilder.host(host)
            } catch (ex: IllegalArgumentException) {
                dialog("Bad hostname", "Bad hostname (\"$host\"), fell back to default (\"$DEFAULT_HOSTNAME\")")
                urlBuilder.host(DEFAULT_HOSTNAME)
            }
            val url = urlBuilder.build()
            Log.v("AAA", "Url: $url")
            // 10.0.2.2
            // 192.168.1.22
            // 192.168.1.26
            val apolloClient = ApolloClient.builder()
                .serverUrl(url)
                .okHttpClient(okHttpClient)
                .addCustomTypeAdapter(CustomType.UUID,
                    uuidCustomTypeAdapter
                )
                .addCustomTypeAdapter(CustomType.B64WKB,
                    b64WkbCustomTypeAdapter
                )
                //            .addCustomTypeAdapter(CustomType.WKT, wktCustomTypeAdapter)
                .build()
            viewModel.apolloClient = apolloClient
        }

        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the main has a writable location for the main cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string

//        main = binding.main
//
//        fun onMapChange(mapboxMap: MapboxMap) {
//            val zoom = mapboxMap.cameraPosition.zoom
//            val bounds = mapboxMap.projection.visibleRegion.latLngBounds
//            val envelope = toEnvelope(bounds)
//            scrollHandler(zoom, envelope)
//            updateLevels(envelope)
//        }
//
//        main.onCreate(savedInstanceState)
//        main.getMapAsync { mapboxMap ->
//            mapboxMap.setStyle(Style.DARK) {
//                fillManager = FillManager(main, mapboxMap, it)
//                mapData.buildings.addOnMapChangedCallback(object : ObservableMap.OnMapChangedCallback<ObservableMap<UUID, Building>, UUID, Building>() {
//                    override fun onMapChanged(sender: ObservableMap<UUID, Building>?, uuid: UUID?) {
//                        val building = mapData.buildings.get(uuid)
//                        if (building != null) {
//                            Log.v("AAA", "Added building ${building.geom.areaSqm}");
//                            val polygon = building.getMapboxPolygon();
//                            lifecycleScope.launch {
//                                val fillOptions = FillOptions()
//                                    .withGeometry(polygon)
//                                    .withFillColor(fillColor("#3bb2d0").value)
//                                val fill = fillManager.create(fillOptions)
//                                fill.data = building.toDataJson()
//                                mapElementIds.append(fill.id, building)
//                                if (building is CompleteBuilding) {
//                                    println("Adding rooms ${building.rooms.size}")
//                                    for (room in building.rooms) {
//                                        val roomPolygon = room.getMapboxPolygon()
//                                        val roomFillOptions = FillOptions()
//                                            .withGeometry(roomPolygon)
//                                            .withFillOutlineColor(fillColor(Color.BLACK).value)
//                                            .withFillColor(fillColor("#357266").value)
//                                        val roomFill = fillManager.create(roomFillOptions)
//                                        roomFill.data = room.toDataJson()
//                                        mapElementIds.append(roomFill.id, room)
//                                    }
//                                    val bounds = mapboxMap.projection.visibleRegion.latLngBounds
//                                    val viewEnvelope = toEnvelope(bounds)
//                                    updateLevels(viewEnvelope)
//                                }
//                            }
////                        main.overlayManager.add(building.getOverlay())
////                        if (building is CompleteBuilding) {
////                            println("Adding rooms ${building.rooms.size}")
////                            for (room in building.rooms) {
////                                main.overlayManager.add(room.getOverlay())
////                            }
////                            println("Added rooms")
////                        }
//                        } else {
//                            Log.v("AAA", "Remove")
//                        }
//                    }
//                })
//                fillManager.addClickListener {fill ->
//                    Log.v("AAA", "${fill.data}")
//                    val mapElement = mapElementIds.get(fill.id)
//                    val name = when (mapElement) {
//                        is Building -> "Building"
//                        is Room -> "Room"
//                        else -> "Unknown"
//                    }
////                    binding.text.setText("$name ${mapElement.uuid}")
//                }
//                val dialog = MapElementDialogFragment()
//                val man = supportFragmentManager
//                dialog.show(man, null)
//                // Trigger at start
//            }
//            main.addOnCameraDidChangeListener {
//                onMapChange(mapboxMap)
//            }
//        }


//        initOsmDroid(main, mapData) {
//            scrollHandler(getzoom, getenveloppe)
//        }
        handleIntent(intent)
    }

//    private fun updateLevels(envelope: Envelope) {
//        Envelope(envelope).expandByMeters(5.0)
//        val newLevels = mapData.levelsInEnvelope(envelope)
//        if (newLevels != levels) {
//            levels = newLevels
//            for (i in 0 until layersIds.size) {
//                binding.speedDial.clearActionItems()
//                for (level in newLevels) {
//                    if (level >= -2 && level <= 5) {
//                        val arrIndex = level + 2 // + b1 + b2
//                        binding.speedDial.addActionItem(SpeedDialActionItem.Builder(
//                            layersIds[arrIndex], layersIcs[arrIndex]
//                        ).create())
//                    }
//                }
//            }
//        }
//    }

//    private fun fetchAndDrawBuildings() {
//        val center = main.mapCenter
//        val buildingQuery = BuildingQuery(2000, center.latitude, center.longitude)
//
//        apolloClient.query(buildingQuery).enqueue(
//            object : ApolloCall.Callback<BuildingQuery.Data>() {
//                override fun onFailure(e: ApolloException) {
//                    lifecycleScope.launch {
//                        dialog("Apollo exception", e.message)
//                    }
//                }
//
//                override fun onResponse(response: Response<BuildingQuery.Data>) {
//                    val data = response.data() ?: throw RuntimeException("No data returned")
//                    val buildings = fromBuildingQueryData(data)
//                    main.overlays.clear()
//                    main.overlays.addAll(polygons);
//                }
//            }
//        );
//    }
//
//    private fun fetchAndDrawBuildingsAndRooms() {
//        val center = main.mapCenter
//        val buildingQuery = BuildingAndRoomsQuery(1000, center.latitude, center.longitude)
//
//        apolloClient.query(buildingQuery).enqueue(
//            object : ApolloCall.Callback<BuildingAndRoomsQuery.Data>() {
//                override fun onFailure(e: ApolloException) {
//                    lifecycleScope.launch {
//                        dialog("Apollo exception", e.message)
//                    }
//                }
//
//                override fun onResponse(response: Response<BuildingAndRoomsQuery.Data>) {
//                    val data = response.data()
//                    if (data != null) {
//                        val buildings = data.buildings
//                        val polygons = LinkedList<Polygon>()
//                        if (buildings != null) {
//                            for (building in buildings) {
//                                val geom = building?.building?.geometry
//                                if (geom != null) {
//                                    val factory = JtsSpatialContextFactory();
//                                    factory.normWrapLongitude = true;
//                                    val sctx = factory.newSpatialContext();
//                                    val POLY_SHAPE = wkt(sctx, geom) as JtsGeometry
//                                    val jtsgeom = POLY_SHAPE.geom
//                                    if (jtsgeom is org.locationtech.jts.geom.Polygon) {
//                                        polygons.add(toOsmDroidPolygon(jtsgeom, Color.GRAY))
//                                    }
//                                }
//                                val rooms = building?.building?.rooms
//                                if (rooms != null) {
//                                    for (room in rooms) {
//                                        val roomGeom = room?.geometry
//                                        if (roomGeom != null) {
//                                            val factory = JtsSpatialContextFactory();
//                                            factory.normWrapLongitude = true;
//                                            val sctx = factory.newSpatialContext();
//                                            val POLY_SHAPE = wkt(sctx, roomGeom) as JtsGeometry
//                                            val jtsgeom = POLY_SHAPE.geom
//                                            if (jtsgeom is org.locationtech.jts.geom.Polygon) {
//                                                polygons.add(toOsmDroidPolygon(jtsgeom, Color.GREEN))
//                                            }
//                                        }
//                                    }
//                                } else {
//                                    dialog("GraphQL error", "No rooms")
//                                }
//                            }
//                            main.overlays.clear()
//                            main.overlays.addAll(polygons);
//                        } else {
//                            dialog("GraphQL error", "No buildings")
//                        }
//                    } else {
//                        dialog("GraphQL error", "No data")
//                    }
//                }
//            }
//        );
//    }

    var openedDialogs = LinkedList<Dialog>()
    fun dialog(title: String, message: String?) {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle(title)
        builder.setMessage(message)
        lifecycleScope.launch {
            val dialog: AlertDialog = builder.create()
            dialog.show()
            openedDialogs.add(dialog)
        }
    }

    override fun onPause() {
        super.onPause()
        openedDialogs.forEach {
            it.dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        openedDialogs.forEach {
            it.dismiss()
        }
    }
}
