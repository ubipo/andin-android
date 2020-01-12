package net.pieterfiers.andin.model.geospatial

import net.sf.geographiclib.Geodesic
import net.sf.geographiclib.PolygonArea
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Polygon
import kotlin.math.absoluteValue

val GEO = Geodesic.WGS84 // Web mercator

val Polygon.areaSqm: Double get() {
    val p = PolygonArea(GEO, false);
    for (point in this.coordinates) {
        p.AddPoint(point.y, point.x)
    }
    val res = p.Compute()
    return res.area.absoluteValue
}

fun Envelope.expandByRatio(ratio: Double) {
    this.expandBy(width * ratio, height * ratio)
}

fun Envelope.expandByMeters(meters: Double) {
    val widthRatio = meters / widthMeters
    val heightRatio = meters / heightMeters
    this.expandBy(widthRatio * width, heightRatio * height)
}

val Envelope.widthMeters: Double get() {
    val avgX = (minX + maxX) / 2
    val widthL = PolygonArea(GEO, true);
    widthL.AddPoint(minY, avgX)
    widthL.AddPoint(maxY, avgX)
    return widthL.Compute(false, false).perimeter
}

val Envelope.heightMeters: Double get() {
    val avgY = (minY + maxY) / 2
    val heightL = PolygonArea(GEO, true);
    heightL.AddPoint(avgY, minX)
    heightL.AddPoint(avgY, maxX)
    return heightL.Compute(false, false).perimeter
}
