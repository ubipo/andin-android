package net.pfiers.andin.model.mapbox

import com.mapbox.mapboxsdk.plugins.annotation.LineOptions
import com.mapbox.mapboxsdk.style.layers.Property
import org.locationtech.jts.geom.GeometryFactory


fun createLineOptions(rangeSection: SameLevelRangeNavSection): LineOptions? {
    val (levelRange, section) = rangeSection
    val geom = GeometryFactory().createLineString(section.map { it.coordinate }.toTypedArray()).mapbox
    return LineOptions()
        .withGeometry(geom)
        .withLineWidth(2.5F)
        .withLineColor("blue")
        .withLineJoin(Property.LINE_JOIN_ROUND)
        .withData(levelRange.asJsonObject())
}
