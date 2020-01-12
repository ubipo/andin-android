package net.pieterfiers.andin

import net.pieterfiers.andin.model.map.MapData
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView


fun initOsmDroid(map: MapView, mapData: MapData, mapMoveHandler: () -> Unit) {
//    Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

    map.setTileSource(
        XYTileSource(
            "Mapnik",
            8,
            18,
            256,
            ".png",
            arrayOf("https://tile.osm.be/osmbe/"),
            "© OpenStreetMap contributors, Tiles courtesy of GEO-6",
            TileSourcePolicy(2,
                TileSourcePolicy.FLAG_NO_BULK
                        or TileSourcePolicy.FLAG_NO_PREVENTIVE
                        or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
                        or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
            )
        )
    )

    map.minZoomLevel = 8.0
    map.maxZoomLevel = 22.0
    val mapController = map.getController()
    mapController.setZoom(16.0)
    val startPoint = GeoPoint(50.8791272, 4.7143374)
    mapController.setCenter(startPoint)
    map.setMultiTouchControls(true)

    map.addMapListener(
        object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapMoveHandler()
                return true //To change body of created functions use File | Settings | File Templates.
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                mapMoveHandler()
                return true //To change body of created functions use File | Settings | File Templates.
            }
        }
    )

//    mapData.elements.addOnMapChangedCallback(
//        object :
//            ObservableMap.OnMapChangedCallback<ObservableMap<UUID, Building>, UUID, Building>() {
//
//            override fun onMapChanged(sender: ObservableMap<UUID, Building>?, uuid: UUID?) {
//                val building = mapData.elements.get(uuid)
//                if (building != null) {
//                    map.overlayManager.add(building.getOverlay())
//                    if (building is CompleteBuilding) {
//                        println("Adding rooms ${building.rooms.size}")
//                        for (room in building.rooms) {
//                            map.overlayManager.add(room.getOverlay())
//                        }
//                        println("Added rooms")
//                    }
//                } else {
//                    println("Remove")
//                }
//            }
//        }
//    )
}
