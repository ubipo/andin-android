package net.pfiers.andin.model.geospatial

import net.pfiers.andin.domain.geometry.TAU
import net.pfiers.andin.domain.geometry.degrees
import net.pfiers.andin.domain.geometry.positiveAngle
import net.pfiers.andin.domain.geometry.radians
import net.sf.geographiclib.Geodesic
import net.sf.geographiclib.GeodesicLine
import net.sf.geographiclib.PolygonArea
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.geom.Polygon
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.pow

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

fun LineSegment.geoRectangleAround(geodesic: Geodesic, distance: Double) {
    p0.perpendicularGeo(geodesic, p1, distance, false)
    p0.perpendicularGeo(geodesic, p1, distance, true)
}

fun Coordinate.distanceGeo(geodesic: Geodesic, other: Coordinate): Double {
    val distancePoly = PolygonArea(geodesic, true);
    distancePoly.AddPoint(this.y, this.x)
    distancePoly.AddPoint(other.y, other.x)
    return distancePoly.Compute(false, false).perimeter
}

fun Coordinate.projectedGeo(geodesic: Geodesic, distance: Double, azimuth: Double): Coordinate {
    val pData = GeodesicLine(geodesic, this.y, this.x, positiveAngle(azimuth).degrees).Position(distance)
    return Coordinate(pData.lon2, pData.lat2)
}

fun Coordinate.roughProjectedDistance(geodesic: Geodesic, distance: Double): Double {
    val projected = projectedGeo(geodesic, distance, 45.0)
    return (
            (x - projected.x).absoluteValue.pow(2) +
                    (y - projected.y).absoluteValue.pow(2)
            ).pow(0.5)
}

fun Coordinate.azimuthGeo(geodesic: Geodesic, other: Coordinate): Double {
    val dat = geodesic.Inverse(this.y, this.x, other.y, other.x)
    return positiveAngle(dat.azi1.radians)
}

fun Coordinate.angleBisectorGeo(
    geodesic: Geodesic,
    a: Coordinate,
    c: Coordinate,
    distance: Double,
    invertAngle: Boolean = false
): Coordinate {
    val azimuthA = azimuthGeo(geodesic, a)
    var azimuthC = azimuthGeo(geodesic, c)
    if (azimuthC < azimuthA)
        azimuthC += TAU
    var angleD = positiveAngle(azimuthA + ((azimuthC - azimuthA) / 2))
    if (invertAngle)
        angleD += PI
    return projectedGeo(geodesic, distance, angleD)
}

fun Coordinate.perpendicularGeo(
    geodesic: Geodesic,
    other: Coordinate,
    distance: Double,
    invertAngle: Boolean = false
): Coordinate {
    var angleP = this.azimuthGeo(geodesic, other) + 90
    if (invertAngle)
        angleP += 180
    return projectedGeo(geodesic, distance, angleP + 90)
}

fun LineSegment.lengthGeo(geodesic: Geodesic): Double {
    return p0.distanceGeo(geodesic, p1)
}

fun LineSegment.azimuth(geodesic: Geodesic): Double = p0.azimuthGeo(GEO, p1)

//fun circleCentersGeo(geodesic: Geodesic, circumferenceP1: Coordinate, circumferenceP2: Coordinate, r: Double): Pair<Coordinate, Coordinate> {
//    val distP1P2 = circumferenceP1.distanceGeo(geodesic, circumferenceP2)
//    val azimuthP1P2 = circumferenceP1.azimuthGeo(geodesic, circumferenceP2)
//    val midPoint = Coordinate(
//        (circumferenceP1.x + circumferenceP2.x) / 2,
//        (circumferenceP1.y + circumferenceP2.y) / 2
//    )
//    // pythagorean
//    val distMidCenters = sqrt(r.sqrd - (distP1P2 / 2).sqrd)
//    return Pair(
//        midPoint.projectedGeo(geodesic, distMidCenters, azimuthP1P2 + PI / 2),
//        midPoint.projectedGeo(geodesic, distMidCenters, azimuthP1P2 - PI / 2)
//    )
//}

enum class IntersectionLineType {
    LINE, // Extending to infinity to both ends
    RAY, // Extending to infinity towards p1 of the LineSegment
    SEGMENT // Treat as a LineSegment (bounded on both sides)
}

//fun lineSegmentLineIntersection(
//    segment: LineSegment,
//    line: LineSegment,
//    intersectionLineType: IntersectionLineType
//): Coordinate? {
//    val intersection = segment.lineIntersection(line) ?: return null
//
//    if (!intersection.projectionWithin(segment))
//        return null
//
//    val projectionFactor = line.projectionFactor(intersection)
//    val projectionFactorOk = when (intersectionLineType) {
//        IntersectionLineType.RAY -> projectionFactor > 0
//        IntersectionLineType.SEGMENT -> (0.0..1.0).contains(projectionFactor)
//        IntersectionLineType.LINE ->  true
//    }
//
//    if (!projectionFactorOk)
//        return null
//
//    return intersection
//}
//
//fun LineSegment.closestPointGeo(geodesic: Geodesic, p: Coordinate): Coordinate {
//    val azimuth = p0.azimuthGeo(geodesic, p1)
//    val pProjected = p.projectedGeo(geodesic, 1.0, azimuth + PI / 2)
//    val intersection = LineSegment(p, pProjected).lineIntersection(this)
//    val factor = projectionFactor(intersection)
//    return when {
//        factor < 0 -> p0
//        factor > 1 -> p1
//        else -> intersection
//    }
//}
