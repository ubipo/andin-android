package net.pfiers.andin;

import android.app.Application;
import net.pfiers.andin.model.map.MapData

class AndinApplication : Application() {
    lateinit var dataRepo: DataRepo

    override fun onCreate() {
        super.onCreate()
        val mapData = MapData()
        dataRepo = DataRepo(mapData, this)
    }
}
