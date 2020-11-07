package net.pfiers.andin.model.map

import net.pfiers.andin.model.nav.LvldCoordVertex
import net.pfiers.andin.model.nav.Navigable
import net.pfiers.andin.model.nav.WeightedLineGraph
import org.jgrapht.GraphPath
import org.jgrapht.alg.interfaces.ManyToManyShortestPathsAlgorithm
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import org.jgrapht.graph.DefaultWeightedEdge

fun getSharedBuilding(departure: Navigable, destination: Navigable, completeBuildings: Iterable<CompleteBuilding>): CompleteBuilding {
    val departureBuilding = completeBuildings.firstOrNull { building ->
        building.indoorElements.rooms.contains(departure.elem)
    } ?: error("Couldn't find building for departure")
    val destinationBuilding = completeBuildings.firstOrNull { building ->
        building.indoorElements.rooms.contains(destination.elem)
    } ?: error("Couldn't find building for destination")

    if (departureBuilding != destinationBuilding)
        error("Departure and destination buildings are different")

    return departureBuilding
}

fun <V, E> ManyToManyShortestPathsAlgorithm.ManyToManyShortestPaths<V, E>.shortest(): GraphPath<V, E>? {
    return sources.flatMap { src ->
        targets.map { tgt ->
            getPath(src, tgt)
        }
    }.minByOrNull { path ->
        path.weight
    }
}

fun dspMulti(navGraph: WeightedLineGraph, srcVertices: Set<LvldCoordVertex>, tgtVertices: Set<LvldCoordVertex>): GraphPath<LvldCoordVertex, DefaultWeightedEdge>? {
    val dsp = DijkstraManyToManyShortestPaths(navGraph)
    val paths = dsp.getManyToManyPaths(srcVertices, tgtVertices)
    return paths.shortest()
}
