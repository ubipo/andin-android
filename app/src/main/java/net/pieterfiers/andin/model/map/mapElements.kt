package net.pieterfiers.andin.model.map

import android.graphics.Color
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Point
import org.locationtech.jts.geom.Polygon
import org.osmdroid.util.GeoPoint
import java.io.Serializable
import java.util.*

open class MapElement(val uuid: UUID, val geom: Polygon): Serializable {
    fun getOverlay(): MapElementOsmdroidOverlayPolygon {
        val overlay =
            MapElementOsmdroidOverlayPolygon(this)
        overlay.points = geom.coordinates.map { GeoPoint(it.y, it.x) }
        overlay.fillPaint.color = Color.YELLOW
        return overlay
    }

    fun getMapboxPolygon(): com.mapbox.geojson.Polygon {
        val points = geom.coordinates.map { Point.fromLngLat(it.x, it.y) }
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

    open val labelText: String? get() = null
}


class MapElementOsmdroidOverlayPolygon(val mapElement: MapElement) : org.osmdroid.views.overlay.Polygon()

class Room(uuid: UUID, geom: Polygon, val level: Int, val name: String?, val ref: String?) : MapElement(uuid, geom) {
    override fun toDataJson(): JsonObject {
        val obj = super.toDataJson()
        obj.add("level", JsonPrimitive(level))
        obj.add("type", JsonPrimitive("room"))
        return obj
    }

    override val typeName: String get() {
        return "Room"
    }

    override val labelText: String? get() = name ?: ref
}

open class Building(uuid: UUID, geom: Polygon, val name: String?, val address: Address?) : MapElement(uuid, geom) {
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

class CompleteBuilding(uuid: UUID, geom: Polygon, val rooms: List<Room>, name: String?, address : Address?) : Building(uuid, geom, name, address)
