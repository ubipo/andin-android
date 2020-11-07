package net.pfiers.andin.model

import net.pfiers.andin.model.nav.LvldCoordVertex
import org.jgrapht.Graph
import org.jgrapht.GraphPath
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString

fun <V, E> Graph<V, E>.getEdgeVertices(edge: E): Pair<V, V> {
    return Pair(getEdgeSource(edge)!!, getEdgeTarget(edge)!!)
}

fun <V, E> Graph<V, E>.addVertices(vertices: Iterable<V>) {
    for (vertex in vertices) {
        addVertex(vertex)
    }
}

fun <V, E> Graph<V, E>.addEdges(edges: Iterable<Triple<V, V, Double>>) {
    for ((src, tgt, weight) in edges) {
        val edge = addEdge(src, tgt)
        if (edge != null)
            setEdgeWeight(edge, weight)
    }
}

fun <E> GraphPath<LvldCoordVertex, E>.toLineString(fac: GeometryFactory): LineString {
    return fac.createLineString(vertexList.map { it.coordinate }.toTypedArray())
}

//fun <E> Graph<CoordVertex, E>.asLineSegment(edge: E): LineSegment {
//    val (src, tgt) = getEdgeVertices(edge)
//    return LineSegment(src.coordinate, tgt.coordinate)
//}
