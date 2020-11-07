package net.pfiers.andin.model.map

import androidx.databinding.ObservableArrayMap
import org.locationtech.jts.geom.*
import java.util.*

val geoFac = GeometryFactory()

fun emptyGeom(): Geometry {
    return GeometryCollection(emptyArray<Geometry>(),
        geoFac
    )
}

class MapData {
    val elements = ObservableArrayMap<UUID, MapElement>()

    fun addBuildings(buildings: List<Building>) {
        for (new in buildings) {
            val uuid = new.uuid
            val existing = this.elements[uuid]

            if (existing != null && existing is CompleteBuilding)
                return

            elements[uuid] = new
            if (new is CompleteBuilding) {
                for (room in new.indoorElements.rooms) {
                    elements[room.uuid] = room
                }
            }
        }
    }

    val rooms: List<Room>
        get() {
            return elements.values
                .filterIsInstance<Room>()
        }

    fun levelRangeInEnvelope(envelope: Envelope): LevelRange? {
        val range = rooms.asSequence().filter { room ->
            envelope.intersects(room.geometry.envelopeInternal)
        }.map { room -> room.levelRange }.reduceOrNull(LevelRange::union)
        return range
    }

    fun roomsOnLevel(level: Double): List<Room> {
        return rooms.asSequence().filter { room ->
            room.levelRange.contains(level)
        }.toList()
    }
}
