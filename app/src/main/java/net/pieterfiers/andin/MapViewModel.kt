package net.pieterfiers.andin

import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableField
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.apollographql.apollo.ApolloClient
import com.mapbox.mapboxsdk.camera.CameraPosition
import net.pieterfiers.andin.model.map.Building
import net.pieterfiers.andin.model.map.MapData
import net.pieterfiers.andin.model.map.MapElement
import net.pieterfiers.andin.model.map.Room
import java.util.*

class MapViewModel : ViewModel() {
    var cameraPosition: CameraPosition? = null
    val mapData = MapData()
    val selectedMapElement = ObservableField<MapElement?>()
    val levels = ObservableField(Collections.singleton(0).toSet())
    val currentLevel = ObservableField<Int?>()
    val query = ObservableField<String?>()
    val searchResults = ObservableField<List<Room>?>()
    lateinit var navController: NavController
    lateinit var apolloClient: ApolloClient

    val navControllerInitialized: Boolean get() = this::navController.isInitialized
    val apolloClientInitialized: Boolean get() = this::apolloClient.isInitialized

    val selectedMapElementDetailStr: String? get() {
        val e = selectedMapElement.get() ?: return null

        val name = when(e) {
            is Room -> "Room"
            is Building -> "Building"
            else -> "Unknown main element"
        }
        return "$name ${e.uuid}"
    }
}