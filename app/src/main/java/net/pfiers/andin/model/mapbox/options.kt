package net.pfiers.andin.model.mapbox

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.pfiers.andin.model.map.LevelRange

fun LevelRange.asJsonObject(): JsonObject {
    val obj = JsonObject()
    obj.add("levelFrom", JsonPrimitive(from))
    val levelTo = to
    if (levelTo != null)
        obj.add("levelTo", JsonPrimitive(levelTo))
    return obj
}
