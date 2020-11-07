package net.pfiers.andin.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*


@Entity(
    tableName = "favorite_room",
    indices = [Index (
        value = ["uuid"],
        unique = true
    )]
)
data class FavoriteRoom (
    @PrimaryKey(autoGenerate = true)
    var roomId: Long = 0L,

    @ColumnInfo(name = "uuid")
    var uuid: UUID
)
