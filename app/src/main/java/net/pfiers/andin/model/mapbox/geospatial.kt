package net.pfiers.andin.model.mapbox

import com.mapbox.mapboxsdk.geometry.LatLngBounds
import org.locationtech.jts.geom.*


val Envelope.latLngBounds: LatLngBounds get() {
    return LatLngBounds.from(maxY, maxX, minY, minX)
}

val LatLngBounds.envelope: Envelope get() {
    return Envelope(lonWest, lonEast, latSouth, latNorth)
}

val Polygon.mapbox: com.mapbox.geojson.Polygon get() {
    return com.mapbox.geojson.Polygon.fromLngLats(
        listOf(exteriorRing.coordinates.map { it.mapboxPoint })
    )
}

val Coordinate.mapboxPoint: com.mapbox.geojson.Point get() {
    return com.mapbox.geojson.Point.fromLngLat(x, y)
}

val LineString.mapbox: com.mapbox.geojson.LineString get() {
    return com.mapbox.geojson.LineString.fromLngLats(
        coordinates.map { it.mapboxPoint }
    )
}
