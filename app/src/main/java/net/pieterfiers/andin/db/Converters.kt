package net.pieterfiers.andin.db

import androidx.room.TypeConverter
import java.util.*

class Converters {
    @TypeConverter
    fun toUuid(value: String?): UUID? {
        return if (value == null) null else UUID.fromString(value)
    }

    @TypeConverter
    fun fromUuid(uuid: UUID?): String? {
        return uuid.toString()
    }
}