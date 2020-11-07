package net.pfiers.andin

import com.andin_api.BuildingAndRoomsQuery
import com.andin_api.BuildingQuery
import com.andin_api.RoomQuery
import com.andin_api.RoomsQuery
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.CustomTypeAdapter
import com.apollographql.apollo.api.CustomTypeValue
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.beust.klaxon.Klaxon
import net.pfiers.andin.model.map.*
import net.pfiers.andin.model.nav.json2weightedLineGraph
import okhttp3.OkHttpClient
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKTReader
import java.util.*


class GqlException(msg: String, cause: Exception? = null) : java.lang.Exception(msg, cause)

val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()

val uuidCustomTypeAdapter: CustomTypeAdapter<UUID> = object : CustomTypeAdapter<UUID> {
    override fun encode(value: UUID): CustomTypeValue<*> {
        return CustomTypeValue.GraphQLString(value.toString())
    }

    override fun decode(value: CustomTypeValue<*>): UUID {
        val strValue = value.value
        if (strValue !is String)
            throw GqlException("UUID underlying type must be String")

        return UUID.fromString(strValue)
    }
}

val b64WkbCustomTypeAdapter: CustomTypeAdapter<Geometry?> = object :
    CustomTypeAdapter<Geometry?> {
    override fun encode(value: Geometry?): CustomTypeValue<*> {
        throw UnsupportedOperationException("encoding B64Wkb")
    }

    override fun decode(value: CustomTypeValue<*>): Geometry? {
        val strValue = value.value
        if (strValue !is String)
            throw GqlException("B64Wkb underlying type must be String")

        val bytes = android.util.Base64.decode(strValue, android.util.Base64.DEFAULT)
        val geometry: Geometry
        val wkbReader = WKBReader()
        try {
            geometry = wkbReader.read(bytes)
        } catch (e: Exception) {
            return null
        } catch (e: OutOfMemoryError) {
            println(strValue)
            println(bytes.size)
            throw e
        }
        return geometry
    }
}


val wktReader = WKTReader()
val wktCustomTypeAdapter: CustomTypeAdapter<Polygon?> = object :
    CustomTypeAdapter<Polygon?> {
    override fun encode(value: Polygon?): CustomTypeValue<*> {
        throw UnsupportedOperationException("encoding B64Wkb")
    }

    override fun decode(value: CustomTypeValue<*>): Polygon? {
        val strValue = value.value
        if (strValue !is String)
            throw GqlException("B64Wkb underlying type must be String")

        val geometry: Polygon
        try {
            geometry = wktReader.read(strValue) as Polygon
        } catch (e: Exception) {
            return null
        } catch (e: OutOfMemoryError) {
            println(strValue)
            throw e
        }
        return geometry
    }
}

fun <T : com.apollographql.apollo.api.Operation.Data> dataFromResponse(response: Response<T>): T {
    val resErrors = response.errors
    if (resErrors != null && resErrors.isNotEmpty()) {
        val errorMsgs = resErrors.map { it.message }
        throw GqlException(errorMsgs.joinToString("\n  "))
    }

    return response.data ?: throw GqlException("No data returned")
}

fun fetchBuildings(client: ApolloClient, bbox: Envelope, onError: (Exception) -> Unit, callback: (List<Building>) -> Unit) {
    val buildingQuery = BuildingQuery(bbox.maxY, bbox.minY, bbox.maxX, bbox.minX)

    client.query(buildingQuery).enqueue(
        object : ApolloCall.Callback<BuildingQuery.Data>() {
            override fun onFailure(e: ApolloException) {
                onError(GqlException("Apollo exception executing buildingQuery", e))
            }

            override fun onResponse(response: Response<BuildingQuery.Data>) {
                val data = try {
                    dataFromResponse(response)
                } catch (e: GqlException) {
                    return onError(e)
                }
                val buildings: List<Building>
                try {
                    buildings = fromBuildingQueryData(data)
                } catch (e: java.lang.Exception) {
                    return onError(e)
                }
                callback(buildings)
            }
        }
    )
}

fun fetchBuildingsAndRooms(client: ApolloClient, bbox: Envelope, onError: (Exception) -> Unit, callback: (List<CompleteBuilding>) -> Unit) {
    val buildingQuery = BuildingAndRoomsQuery(bbox.maxY, bbox.minY, bbox.maxX, bbox.minX)

    client.query(buildingQuery).enqueue(
        object : ApolloCall.Callback<BuildingAndRoomsQuery.Data>() {
            override fun onFailure(e: ApolloException) {
                onError(GqlException("Apollo exception executing BuildingAndRoomsQuery", e))
            }

            override fun onResponse(response: Response<BuildingAndRoomsQuery.Data>) {
                val data = try {
                    dataFromResponse(response)
                } catch (e: GqlException) {
                    return onError(e)
                }
                val buildings: List<CompleteBuilding>
                try {
                    buildings = fromBuildingAndRoomsQueryData(data)
                } catch (e: java.lang.Exception) {
                    return onError(e)
                }
                callback(buildings)
            }
        }
    )
}

fun fetchRooms(client: ApolloClient, query: String, onError: (Exception) -> Unit, callback: (List<Room>) -> Unit) {
    val roomsQuery = RoomsQuery(query)

    client.query(roomsQuery).enqueue(
        object : ApolloCall.Callback<RoomsQuery.Data>() {
            override fun onFailure(e: ApolloException) {
                onError(GqlException("Apollo exception executing RoomsQuery", e))
            }

            override fun onResponse(response: Response<RoomsQuery.Data>) {
                val data = try {
                    dataFromResponse(response)
                } catch (e: GqlException) {
                    return onError(e)
                }
                val rooms: List<Room>
                try {
                    rooms = fromRoomsQueryData(data)
                } catch (e: java.lang.Exception) {
                    return onError(e)
                }
                callback(rooms)
            }
        }
    )
}

fun fetchRoom(client: ApolloClient, uuid: UUID, onError: (Exception) -> Unit, callback: (Building) -> Unit) {
    val roomQuery = RoomQuery(uuid.toString())

    client.query(roomQuery).enqueue(
        object : ApolloCall.Callback<RoomQuery.Data>() {
            override fun onFailure(e: ApolloException) {
                onError(GqlException("Apollo exception executing RoomsQuery", e))
            }

            override fun onResponse(response: Response<RoomQuery.Data>) {
                val data = try {
                    dataFromResponse(response)
                } catch (e: GqlException) {
                    return onError(e)
                }
                val building: Building
                try {
                    building = fromRoomQueryData(data)
                } catch (e: java.lang.Exception) {
                    return onError(e)
                }
                callback(building)
            }
        }
    )
}

fun fromBuildingQueryData(buildingQueryData: BuildingQuery.Data): List<Building> {
    val gqlBuildings = buildingQueryData.buildings

    val buildings = LinkedList<Building>()
    for (buildingWrapper in gqlBuildings) {
        val building = buildingWrapper.building
        val uuid = building.uid
        val polygon = building.geometry.wkb ?:
            throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
        var address: Address? = null
        if (building.address != null) {
            val gAddr = building.address
            address = Address(
                gAddr.free,
                gAddr.locality,
                gAddr.region,
                gAddr.postcode,
                gAddr.country
            )
        }
        buildings.add(
            Building(
                uuid,
                polygon as Polygon,
                building.name,
                address
            )
        )
    }
    return buildings
}

fun fromBuildingAndRoomsQueryData(roomQueryData: BuildingAndRoomsQuery.Data): List<CompleteBuilding> {
    val gqlBuildings = roomQueryData.buildings

    val buildings = LinkedList<CompleteBuilding>()
    for (buildingWrapper in gqlBuildings) {
        val building = buildingWrapper.building
        val polygon = building.geometry.wkb ?:
            throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
        val rooms = building.rooms.map { room ->
            val roomPolygon = room.geometry.wkb ?:
                throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
            val levelRange = LevelRange(room.levelFrom, room.levelTo)
            Room(
                room.uid,
                roomPolygon as Polygon,
                levelRange,
                room.navGraphWalkableId,
                room.name,
                room.ref,
                room.roomType?.name?.let { RoomType.valueOf(it) },
                room.toilet?.name?.let { Toilet.valueOf(it) },
                room.drinkCoffee,
                room.firstAidKit
            )
        }
        val corridors = building.corridors.map { corridor ->
            val corridorPolygon = corridor.geometry.wkb ?:
            throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
            val levelRange = LevelRange(corridor.levelFrom, corridor.levelTo)
            Corridor(
                corridor.uid,
                corridorPolygon as Polygon,
                levelRange,
                corridor.navGraphWalkableId
            )
        }
        val entrances = building.entrances.map { entrance ->
            val entrancePoint = entrance.geometry.wkb ?:
                throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
            val levelRange = LevelRange(entrance.levelFrom, entrance.levelTo)
            val withinWalkable = entrance.withinWalkable?.let {
                Klaxon().parseArray<Int>(it)
            }
            Entrance(
                entrance.uid,
                entrancePoint as Point,
                levelRange,
                entrance.navGraphPoiId,
                withinWalkable
            )
        }
        val fireSuppressionTools = building.fireSupressionTools.map { fireSuppressionTool ->
            val geom = fireSuppressionTool.geometry.wkb ?:
                throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
            val levelRange = LevelRange(fireSuppressionTool.levelFrom, fireSuppressionTool.levelTo)
            val withinWalkable = fireSuppressionTool.withinWalkable?.let {
                Klaxon().parseArray<Int>(it)
            }
            FireSuppressionTool(
                fireSuppressionTool.uid,
                geom as Point,
                levelRange,
                fireSuppressionTool.navGraphPoiId,
                withinWalkable,
                fireSuppressionTool.toolType?.name?.let { FsToolType.valueOf(it) }
            )
        }
        var address: Address? = null
        if (building.address != null) {
            val gAddr = building.address
            address = Address(
                gAddr.free,
                gAddr.locality,
                gAddr.region,
                gAddr.postcode,
                gAddr.country
            )
        }
        val navGraph = building.navGraph?.let { json2weightedLineGraph(it) }
        buildings.add(
            CompleteBuilding(
                building.uid,
                polygon as Polygon,
                building.name,
                address,
                IndoorElements(rooms, corridors, entrances, fireSuppressionTools),
                navGraph
            )
        )
    }
    return buildings
}

fun fromRoomsQueryData(roomQueryData: RoomsQuery.Data): List<Room> {
    val gqlRooms = roomQueryData.rooms

    val rooms = LinkedList<Room>()
    for (roomWrapper in gqlRooms) {
        val gqlRoom = roomWrapper.room
//        val uuid = gqlRoom.building?.uid
        val polygon = gqlRoom.geometry.wkb ?:
            throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
        val levelRange = LevelRange(gqlRoom.levelFrom, gqlRoom.levelTo)
        val room = Room(
            gqlRoom.uid,
            polygon as Polygon,
            levelRange,
            gqlRoom.navGraphWalkableId,
            gqlRoom.name,
            gqlRoom.ref,
            gqlRoom.roomType?.name?.let { RoomType.valueOf(it) },
            gqlRoom.toilet?.name?.let { Toilet.valueOf(it) },
            gqlRoom.drinkCoffee,
            gqlRoom.firstAidKit
        )
        rooms.add(room)
    }
    return rooms
}

fun fromRoomQueryData(roomQueryData: RoomQuery.Data): CompleteBuilding {
    val building = roomQueryData.room?.building ?:
        throw GqlException("No room for the given UUID")

    val polygon = building.geometry.wkb ?:
    throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
    val rooms = building.rooms.map { room ->
        val roomPolygon = room.geometry.wkb ?:
        throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
        val levelRange = LevelRange(room.levelFrom, room.levelTo)
        Room(
            room.uid,
            roomPolygon as Polygon,
            levelRange,
            room.navGraphWalkableId,
            room.name,
            room.ref,
            room.roomType?.name?.let { RoomType.valueOf(it) },
            room.toilet?.name?.let { Toilet.valueOf(it) },
            room.drinkCoffee,
            room.firstAidKit
        )
    }
    val corridors = building.corridors.map { corridor ->
        val corridorPolygon = corridor.geometry.wkb ?:
        throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
        val levelRange = LevelRange(corridor.levelFrom, corridor.levelTo)
        Corridor(
            corridor.uid,
            corridorPolygon as Polygon,
            levelRange,
            corridor.navGraphWalkableId
        )
    }
    val entrances = building.entrances.map { entrance ->
        val entrancePoint = entrance.geometry.wkb ?:
        throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
        val levelRange = LevelRange(entrance.levelFrom, entrance.levelTo)
        val withinWalkable = entrance.withinWalkable?.let {
            Klaxon().parseArray<Int>(it)
        }
        Entrance(
            entrance.uid,
            entrancePoint as Point,
            levelRange,
            entrance.navGraphPoiId,
            withinWalkable
        )
    }
    val fireSuppressionTools = building.fireSupressionTools.map { fireSuppressionTool ->
        val geom = fireSuppressionTool.geometry.wkb ?:
        throw GqlException("Should never occur as 'wkb: true' was specified in buildingsQuery")
        val levelRange = LevelRange(fireSuppressionTool.levelFrom, fireSuppressionTool.levelTo)
        val withinWalkable = fireSuppressionTool.withinWalkable?.let {
            Klaxon().parseArray<Int>(it)
        }
        FireSuppressionTool(
            fireSuppressionTool.uid,
            geom as Point,
            levelRange,
            fireSuppressionTool.navGraphPoiId,
            withinWalkable,
            fireSuppressionTool.toolType?.name?.let { FsToolType.valueOf(it) }
        )
    }
    var address: Address? = null
    if (building.address != null) {
        val gAddr = building.address
        address = Address(
            gAddr.free,
            gAddr.locality,
            gAddr.region,
            gAddr.postcode,
            gAddr.country
        )
    }
    val navGraph = building.navGraph?.let { json2weightedLineGraph(it) }
    return CompleteBuilding(
        building.uid,
        polygon as Polygon,
        building.name,
        address,
        IndoorElements(rooms, corridors, entrances, fireSuppressionTools),
        navGraph
    )
}
