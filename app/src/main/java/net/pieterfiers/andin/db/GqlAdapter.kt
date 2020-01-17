package net.pieterfiers.andin.db

import net.pieterfiers.andin.GqlException
import net.pieterfiers.andin.MapViewModel
import net.pieterfiers.andin.fetchRoom
import net.pieterfiers.andin.model.map.Building
import net.pieterfiers.andin.model.map.Room
import java.util.*
import kotlin.collections.ArrayList

fun getOrDownload(uuid: UUID, viewModel: MapViewModel, onResult: (Room) -> Unit, onError: (Exception) -> Unit) {
    val handler = { e: Exception ->
        if (e !is GqlException)
            throw e

        val builder = StringBuilder(e.message ?: "")
        var cause = e.cause
        while (cause != null) {
            builder.append(":\n${cause.message}")
            cause = cause.cause
        }
        onError(GqlException("Error getting info for room (UUID: ${uuid}): \n ${builder.toString()}"))
    }
    val existingRoom = viewModel.mapData.rooms.filter { room ->
        room.uuid == uuid
    }.firstOrNull()

    var room: Room
    if (existingRoom == null) {
        fetchRoom(
            viewModel.apolloClient,
            uuid,
            handler
        ) { building ->
            viewModel.mapData.addBuildings(listOf(building))
            room = viewModel.mapData.rooms.firstOrNull { room ->
                room.uuid == uuid
            } ?: throw RuntimeException("Room wasn't properly fetched")
            onResult(room)
        }
    } else {
        onResult(existingRoom)
    }
}

fun getOrDownloadAll(uuids: List<UUID>, viewModel: MapViewModel, onResult: (List<Room>) -> Unit, onError: (Exception) -> Unit) {
    val rooms = ArrayList<Room>(uuids.size)
    var error = false
    uuids.forEach {uuid ->
        getOrDownload(uuid, viewModel, {room ->
            rooms.add(room)
            if (rooms.size == uuids.size) {
                onResult(rooms)
            }
        }, {e ->
            if (!error) {
                error = true
                onError(e)
            }
        })
    }
}
