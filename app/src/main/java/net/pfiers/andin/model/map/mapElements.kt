package net.pfiers.andin.model.map

import android.graphics.Color
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Point
import net.pfiers.andin.model.nav.WeightedLineGraph
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Polygon
import org.osmdroid.util.GeoPoint
import java.io.Serializable
import java.util.*

open class MapElement(val uuid: UUID, open val geometry: Geometry): Serializable {
    fun getOverlay(): MapElementOsmdroidOverlayPolygon {
        val overlay =
            MapElementOsmdroidOverlayPolygon(this)
        overlay.points = geometry.coordinates.map { GeoPoint(it.y, it.x) }
        overlay.fillPaint.color = Color.YELLOW
        return overlay
    }

    fun getMapboxPolygon(): com.mapbox.geojson.Polygon {
        val points = geometry.coordinates.map { Point.fromLngLat(it.x, it.y) }
        return com.mapbox.geojson.Polygon.fromLngLats(Arrays.asList(points))
    }

    open fun toDataJson(): JsonObject {
        val obj = JsonObject()
        obj.add("uuid", JsonPrimitive(uuid.toString()))
        return obj
    }

    open val typeName: String get() {
        return "Indoor element"
    }

    open val jsonType: String get() {
        return "indoorElement"
    }

    open val labelText: String? get() = null
}

open class IndoorMapElement(
    uuid: UUID,
    geometry: Geometry,
    val levelRange: LevelRange
) : MapElement(uuid, geometry) {
    override fun toDataJson(): JsonObject {
        val obj = super.toDataJson()
        obj.add("levelFrom", JsonPrimitive(levelRange.from))
        val levelTo = levelRange.to
        if (levelTo != null)
            obj.add("levelTo", JsonPrimitive(levelTo))
        obj.add("type", JsonPrimitive(jsonType))
        return obj
    }

    open val labelTextUnnamed: String get() = "Unnamed indoor element"
}


class MapElementOsmdroidOverlayPolygon(val mapElement: MapElement) : org.osmdroid.views.overlay.Polygon()


open class WalkableMapElement(
    uuid: UUID,
    override val geometry: Polygon,
    levelRange: LevelRange,
    val navGraphWalkableId: Int?,
) : IndoorMapElement(uuid, geometry, levelRange)


enum class RoomType {
    CLASSROOM, OFFICE
}

enum class Toilet {
    MALE, FEMALE, UNISEX, HANDICAPPED, HANDICAPPED_MALE, HANDICAPPED_FEMALE
}


class Room(
    uuid: UUID,
    geometry: Polygon,
    levelRange: LevelRange,
    navGraphWalkableId: Int?,
    val name: String?,
    val ref: String?,
    val roomType: RoomType?,
    val toilet: Toilet?,
    val drinkCoffee: Boolean?,
    val firstAidKit: Boolean?
) : WalkableMapElement(uuid, geometry, levelRange, navGraphWalkableId) {
    override val jsonType: String get() = "room"
    override val typeName: String get() = "Room"
    override val labelText: String? get() = name ?: ref

    override val labelTextUnnamed get() = "Unnamed room"
}


class Corridor(
    uuid: UUID,
    geometry: Polygon,
    levelRange: LevelRange,
    navGraphWalkableId: Int?,
) : WalkableMapElement(uuid, geometry, levelRange, navGraphWalkableId) {
    override val jsonType: String get() = "corridor"
    override val typeName: String get() = "Corridor"
}


open class PoiMapElement(
    uuid: UUID,
    geometry: Geometry,
    levelRange: LevelRange,
    val navGraphPoiId: Int?,
    val withinWalkable: List<Int>?
) : IndoorMapElement(uuid, geometry, levelRange)


enum class FsToolType {
    HOSE, EXTINGUISHER
}


class FireSuppressionTool(
    uuid: UUID,
    override val geometry: org.locationtech.jts.geom.Point,
    levelRange: LevelRange,
    navGraphPoiId: Int?,
    withinWalkable: List<Int>?,
    val toolType: FsToolType?
) : PoiMapElement(uuid, geometry, levelRange, navGraphPoiId, withinWalkable) {
    override val jsonType: String get() = "fireSuppressionTool"
    override val typeName: String get() = "Fire suppression tool"
}


class Entrance(
    uuid: UUID,
    override val geometry: org.locationtech.jts.geom.Point,
    levelRange: LevelRange,
    navGraphPoiId: Int?,
    withinWalkable: List<Int>?
) : PoiMapElement(uuid, geometry, levelRange, navGraphPoiId, withinWalkable) {
    override val jsonType: String get() = "entrance"
    override val typeName: String get() = "Entrance"
}


open class Building(
    uuid: UUID,
    override val geometry: Polygon,
    val name: String?,
    val address: Address?
) : MapElement(uuid, geometry) {
    override fun toDataJson(): JsonObject {
        val obj = super.toDataJson()
        obj.add("type", JsonPrimitive("building"))
        return obj
    }

    override val typeName: String get() {
        return "Building"
    }

    override val labelText: String? get() = name
}

open class Address(val free: String?, val locality: String, val region: String, val postcode: String, val country: String) : Serializable {
    val concatenated: String get() {
        return listOf(free, locality, postcode, region, country).joinToString(", ")
    }
}


class IndoorElements(
    val rooms: List<Room>,
    val corridors: List<Corridor>,
    val entrances: List<Entrance>,
    val fireSuppressionTools: List<FireSuppressionTool>
)


class CompleteBuilding(
    uuid: UUID,
    geom: Polygon,
    name: String?,
    address : Address?,
    val indoorElements: IndoorElements,
    val navGraph: WeightedLineGraph?
) : Building(uuid, geom, name, address)
