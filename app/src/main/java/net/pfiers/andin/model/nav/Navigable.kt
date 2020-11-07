package net.pfiers.andin.model.nav

import net.pfiers.andin.model.map.IndoorMapElement
import net.pfiers.andin.model.map.PoiMapElement
import net.pfiers.andin.model.map.WalkableMapElement
import org.locationtech.jts.geom.Coordinate

class Navigable(val elem: IndoorMapElement? = null, val lvldCoord: Pair<Double, Coordinate>? = null) {
    fun getVertices(navGraph: WeightedLineGraph, pois: Iterable<PoiMapElement>): Set<LvldCoordVertex> {
        return when (elem) {
            is WalkableMapElement -> {
                val poisWithin = pois.filter { poi ->
                    poi.withinWalkable?.contains(elem.navGraphWalkableId) ?: false
                }.map { it.navGraphPoiId }
                poisWithin.map { poiId ->
                    navGraph.vertexSet().filterIsInstance<PoiVertex>().firstOrNull { vertex ->
                        vertex.poiId == poiId
                    } ?: error("No corresponding vertex in graph for poiId $poiId")
                }
            }
            is PoiMapElement -> {
                listOf(navGraph.vertexSet().filterIsInstance<PoiVertex>().firstOrNull { vertex ->
                    vertex.poiId == elem.navGraphPoiId
                } ?: error("No corresponding vertex in graph for poiId ${elem.navGraphPoiId}"))
            }
            else -> emptySet()
        }.toSet()
    }

    val coordinate: Coordinate get() = when (elem) {
        null -> lvldCoord?.second!!
        else -> elem.geometry.centroid.coordinate
    }

    val lvldCoordAssert get() = lvldCoord!!

    val isUnnamed: Boolean = when (elem) {
        null -> false
        else -> elem.labelText == null
    }

    val unnamedLabel = when (elem) {
        null -> error("Coord can not be unnamed")
        else -> elem.labelTextUnnamed
    }

    val label: String? = when (elem) {
        null -> lvldCoord?.second.toString()
        else -> elem.labelText
    }

    val level: Double = when (elem) {
        null -> lvldCoordAssert.first
        else -> elem.levelRange.from
    }

    init {
        if (elem == null && lvldCoord == null)
            throw IllegalArgumentException("One of elem and lvldCoord must be set")
    }
}
