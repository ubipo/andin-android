package net.pfiers.andin.model.geospatial

import net.pfiers.andin.model.getEdgeVertices
import net.pfiers.andin.model.nav.LvldCoordVertex
import org.jgrapht.Graph
import org.locationtech.jts.geom.LineSegment

fun <E> Graph<LvldCoordVertex, E>.asLineSegment(edge: E): LineSegment {
    val (src, tgt) = getEdgeVertices(edge)
    return LineSegment(src.coordinate, tgt.coordinate)
}
