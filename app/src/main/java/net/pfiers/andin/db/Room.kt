package net.pfiers.andin.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*


@Entity(
    tableName = "room",
    indices = [Index (
        value = ["uuid"],
        unique = true
    )]
)
class Room (
    @PrimaryKey(autoGenerate = true)
    var roomId: Long = 0L,

    @ColumnInfo(name = "uuid")
    var uuid: UUID,

    var name: String,

    var levelFrom: 
)
