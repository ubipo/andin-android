package net.pfiers.andin.model.nav

import net.pfiers.andin.model.geospatial.GEO
import net.pfiers.andin.model.geospatial.distanceGeo
import org.jgrapht.GraphPath
import org.jgrapht.graph.DefaultWeightedEdge


const val WALKING_SPEED = 1.4 // m/s

fun GraphPath<LvldCoordVertex, DefaultWeightedEdge>.distanceGeo(): Double {
    var distance = 0.0

    var prevVertex = vertexList.first()
    for (vertex in vertexList.subList(1, vertexList.size)) {
        distance += prevVertex.coordinate.distanceGeo(GEO, vertex.coordinate)
        prevVertex = vertex
    }

    return distance
}

fun GraphPath<LvldCoordVertex, DefaultWeightedEdge>.time(): Double {
    return distanceGeo() * (1 / WALKING_SPEED)
}

fun distanceStr(path: GraphPath<LvldCoordVertex, DefaultWeightedEdge>?): String? {
    if (path == null)
        return null

    val distanceFormatted = String.format("%.2f", path.distanceGeo())
    return "${distanceFormatted}m"
}

fun timeStr(path: GraphPath<LvldCoordVertex, DefaultWeightedEdge>?): String? {
    if (path == null)
        return null

    val timeFormatted = String.format("%.2f", path.time() + 1.0)
    return "${timeFormatted}s"
}
