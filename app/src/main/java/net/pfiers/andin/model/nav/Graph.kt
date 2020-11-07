package net.pfiers.andin.model.nav

import net.pfiers.andin.model.map.LevelRange
import org.jgrapht.graph.AbstractBaseGraph
import org.jgrapht.graph.DefaultGraphType
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.util.SupplierUtil
import org.locationtech.jts.geom.Coordinate


open class LvldCoordVertex(val coordinate: Coordinate, val levelRange: LevelRange)

class PoiVertex(coordinate: Coordinate, levelRange: LevelRange, val poiId: Int) : LvldCoordVertex(coordinate, levelRange)

class WeightedLineGraph: AbstractBaseGraph<LvldCoordVertex, DefaultWeightedEdge>(
    null,
    SupplierUtil.createSupplier(DefaultWeightedEdge::class.java),
    DefaultGraphType.Builder()
        .undirected().allowMultipleEdges(false).allowSelfLoops(true).weighted(true)
        .build()
)
