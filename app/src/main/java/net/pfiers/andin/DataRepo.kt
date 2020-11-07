package net.pfiers.andin

import android.util.Log
import net.pfiers.andin.model.map.MapData

class DataRepo(
    val mapData: MapData,
    val application: AndinApplication
) {
    fun prt() {
        Log.v("AAA", "logging")
    }
}