package net.pfiers.andin

import androidx.databinding.ObservableField
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.apollographql.apollo.ApolloClient
import com.mapbox.mapboxsdk.camera.CameraPosition
import net.pfiers.andin.db.AndinDao
import net.pfiers.andin.db.FavoriteRoom
import net.pfiers.andin.model.map.LevelRange
import net.pfiers.andin.model.map.Building
import net.pfiers.andin.model.map.MapData
import net.pfiers.andin.model.map.MapElement
import net.pfiers.andin.model.map.Room
import net.pfiers.andin.model.nav.LvldCoordVertex
import net.pfiers.andin.model.nav.Navigable
import org.jgrapht.GraphPath
import org.jgrapht.graph.DefaultWeightedEdge

class MapViewModel : ViewModel() {
    var cameraPosition: CameraPosition? = null
    val mapData = MapData()
    val selectedMapElement = ObservableField<MapElement?>()
    val levels: ObservableField<LevelRange> = ObservableField(LevelRange(0.0))
    val currentLevel = ObservableField<Double?>()
    val desiredLevel = ObservableField<Double?>()
    val query = ObservableField<String?>()
    val searchResults = ObservableField<List<Room>?>()
    val departure = ObservableField<Navigable?>()
    val destination = ObservableField<Navigable?>()
    val path = ObservableField<GraphPath<LvldCoordVertex, DefaultWeightedEdge>?>()
    lateinit var navController: NavController
    lateinit var apolloClient: ApolloClient
    lateinit var dao: AndinDao
    lateinit var favorites: LiveData<List<FavoriteRoom>>

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
