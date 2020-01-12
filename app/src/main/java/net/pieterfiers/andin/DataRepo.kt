package net.pieterfiers.andin

import android.util.Log
import net.pieterfiers.andin.model.map.MapData

class DataRepo(
    val mapData: MapData,
    val application: AndinApplication
) {
    fun prt() {
        Log.v("AAA", "logging")
    }
}