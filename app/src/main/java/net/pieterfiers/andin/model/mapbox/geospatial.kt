package net.pieterfiers.andin.model.mapbox

import com.mapbox.mapboxsdk.geometry.LatLngBounds
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import java.util.*


val Envelope.latLngBounds: LatLngBounds get() {
    return LatLngBounds.from(maxY, maxX, minY, minX)
}

val LatLngBounds.envelope: Envelope get() {
    return Envelope(lonWest, lonEast, latSouth, latNorth)
}

val Polygon.mapbox: com.mapbox.geojson.Polygon get() {
    val points = coordinates.map { com.mapbox.geojson.Point.fromLngLat(it.x, it.y) }
    return com.mapbox.geojson.Polygon.fromLngLats(listOf(points))
}

val Coordinate.mapbox: com.mapbox.geojson.Point get() {
    return com.mapbox.geojson.Point.fromLngLat(x, y)
}
