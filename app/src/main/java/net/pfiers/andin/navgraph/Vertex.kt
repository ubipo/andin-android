package net.pfiers.andin.navgraph

import org.jgrapht.graph.DefaultWeightedEdge
import org.locationtech.jts.geom.Coordinate
import java.io.Serializable

//abstract class Vertex(val coordinate: Coordinate) : Serializable
//
//class IntersectionVertex(coordinate: Coordinate, val intersectionEdges: Pair<DefaultWeightedEdge, DefaultWeightedEdge>? = null) : Vertex(coordinate), Serializable
//
//class BaseVertex(coordinate: Coordinate, val angle: BaseVertexAngle, val linePos: BaseVertexLinePos) : Vertex(coordinate), Serializable
//
//enum class BaseVertexAngle {
//    BISECTOR, PERPENDICULAR
//}
//
//enum class BaseVertexLinePos {
//    MIDDLE, OUTER, INTERSECTION
//}
//
//class PoiVertex(coordinate: Coordinate, val id: Int) : Vertex(coordinate)
