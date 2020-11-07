package net.pfiers.andin.model.nav

import android.util.Log
import net.pfiers.andin.model.map.LevelRange
import net.pfiers.andin.model.addEdges
import net.pfiers.andin.model.addVertices
import org.jgrapht.graph.AbstractBaseGraph
import org.jgrapht.graph.DefaultGraphType
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.nio.AttributeType
import org.jgrapht.nio.json.JSONImporter
import org.jgrapht.util.SupplierUtil
import org.locationtech.jts.geom.Coordinate
import kotlin.properties.Delegates


open class TempLvldCoordVertex {
    var coordinateX by Delegates.notNull<Double>()
    var coordinateY by Delegates.notNull<Double>()
    var levelFrom by Delegates.notNull<Double>()
    var levelTo: Double? = null
    var poiId: Int? = null
}

open class TempEdge {
    var weight by Delegates.notNull<Double>()
}

class TempWeightedLineGraph: AbstractBaseGraph<TempLvldCoordVertex, DefaultWeightedEdge>(
    { TempLvldCoordVertex() },
    SupplierUtil.createSupplier(DefaultWeightedEdge::class.java),
    DefaultGraphType.Builder()
        .undirected().allowMultipleEdges(false).allowSelfLoops(true).weighted(true)
        .build()
)

fun json2weightedLineGraph(json: String): WeightedLineGraph {
    Log.v("ABA", ">>>>>>>>> Parsing JSON graph")
    val jsonImporter = JSONImporter<TempLvldCoordVertex, DefaultWeightedEdge>()
    jsonImporter.addVertexAttributeConsumer { pair, attribute ->
        val vertex = pair.first
        val key = pair.second
        when {
            attribute.type == AttributeType.DOUBLE && key == "coordinatex" -> {
                vertex.coordinateX = attribute.value.toDouble()
            }
            attribute.type == AttributeType.DOUBLE && key == "coordinatey" -> {
                vertex.coordinateY = attribute.value.toDouble()
            }
            attribute.type == AttributeType.DOUBLE && key == "level_from" -> {
                vertex.levelFrom = attribute.value.toDouble()
            }
            attribute.type == AttributeType.DOUBLE && key == "level_to" -> {
                vertex.levelTo = attribute.value.toDouble()
            }
            attribute.type == AttributeType.INT && key == "poi_id" -> {
                vertex.poiId = attribute.value.toInt()
            }
            else -> Log.v("ABA", "Unknown navgraph attr ${pair.second} ${attribute.type}")
        }
    }
    val tempGraph = TempWeightedLineGraph()
    jsonImporter.importGraph(tempGraph, json.toByteArray().inputStream())

    val navGraph = WeightedLineGraph()
    val vertexMap = tempGraph.vertexSet().map {
        val coordinate = Coordinate(it.coordinateX, it.coordinateY)
        val levelRange = LevelRange(it.levelFrom, it.levelTo)
        val poiId = it.poiId
        Pair(it, when (poiId) {
            null -> LvldCoordVertex(coordinate, levelRange)
            else -> PoiVertex(coordinate, levelRange, poiId)
        })
    }.toMap()
    navGraph.addVertices(vertexMap.values)
    for ((tempVertex, src) in vertexMap) {
        navGraph.addEdges(tempGraph.outgoingEdgesOf(tempVertex).map { edge ->
            val tgt = vertexMap[tempGraph.getEdgeTarget(edge)]
            val weight = tempGraph.getEdgeWeight(edge)
            Triple(src, tgt, weight)
        })
    }
    return navGraph
//    jsonImporter.setVertexAttributeProvider { vertex ->
//        val attr = mutableListOf(
//            Pair("coordinateX", DefaultAttribute.createAttribute(vertex.coordinate.x)),
//            Pair("coordinateY", DefaultAttribute.createAttribute(vertex.coordinate.y)),
//            Pair("levelFrom", DefaultAttribute.createAttribute(Klaxon().toJsonString(vertex.levelRange.from)))
//        )
//        vertex.levelRange.levelTo?.let {
//            attr.add(Pair("levelTo", DefaultAttribute.createAttribute(it)))
//        }
//        if (vertex is PoiVertex) {
//            attr.add(Pair("poiId", DefaultAttribute.createAttribute(vertex.id)))
//        }
//        attr.toMap()
//    }
//    jsonImporter.setEdgeAttributeProvider { edge ->
//        listOf(
//            Pair("weight", DefaultAttribute.createAttribute(navGraph.getEdgeWeight(edge)))
//        ).toMap()
//    }
//
//    val navGraphJsonOutputStream = ByteArrayOutputStream()
//    jsonImporter.exportGraph(navGraph, navGraphJsonOutputStream)
//    return navGraphJsonOutputStream.toString("UTF-8")
}
