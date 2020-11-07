package net.pfiers.andin.view.fragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.ui.NavigationUI
import androidx.recyclerview.widget.DividerItemDecoration
import kotlinx.coroutines.launch
import net.pfiers.andin.FavoriteRoomRecyclerAdapter
import net.pfiers.andin.MapViewModel
import net.pfiers.andin.R
import net.pfiers.andin.databinding.FragmentFavoriteRoomsBinding
import net.pfiers.andin.db.getOrDownload
import net.pfiers.andin.db.getOrDownloadAll
import net.pfiers.andin.model.map.Room
import net.pfiers.andin.view.MainActivity

class FavoriteRoomsFragment : Fragment() {

    private lateinit var binding: FragmentFavoriteRoomsBinding
    private lateinit var adapter: FavoriteRoomRecyclerAdapter
    private lateinit var viewModel: MapViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = activity?.run {
            ViewModelProvider(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    private fun roomSelectedHandler(searchResultRoom: Room) {
        val setSelected = { room: Room ->
            viewModel.desiredLevel.set(room.levelRange.from)
            viewModel.selectedMapElement.set(room)
            lifecycleScope.launch {
                if (viewModel.navController.currentDestination?.id != R.id.mapFragment) {
                    viewModel.navController.navigate(R.id.action_favoriteRoomsFragment_to_favoritesMapFragment)
                }
            }
        }

        getOrDownload(searchResultRoom.uuid, viewModel, {room ->
            setSelected(room)
        }, {e ->
            dialog("Error getting info for selected room", e.message)
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentFavoriteRoomsBinding.inflate(inflater)
        binding.lifecycleOwner = this

        binding.vm = viewModel

        val toolbar = binding.toolbar
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(toolbar)
        NavigationUI.setupActionBarWithNavController(activity, viewModel.navController)

        Log.v("AAA", "NavUI set up!")

        adapter = FavoriteRoomRecyclerAdapter { room ->
            roomSelectedHandler(room)
        }
        binding.list.adapter = adapter

        val layoutManager = LinearLayoutManager(context);
        binding.list.layoutManager = layoutManager

        val dividerItemDecoration = DividerItemDecoration(
            binding.list.context,
            layoutManager.orientation
        )
        binding.list.addItemDecoration(dividerItemDecoration)

        val favorites = viewModel.dao.getAll()

        val allFavorites = favorites.value

        if (allFavorites != null) {
            val uuids = allFavorites.map {fav ->
                fav.uuid
            }

            getOrDownloadAll(uuids, viewModel, {rooms ->
                adapter.data = rooms
            }, {e ->
                dialog("Error getting info for rooms: ", e.message)
            })
        }

        favorites.observeForever {lst ->
            val uuids = lst.map {fav ->
                fav.uuid
            }

            getOrDownloadAll(uuids, viewModel, {rooms ->
                lifecycleScope.launch {
                    Log.v("AAA","${rooms[0].labelText}")
                    adapter.data = rooms
                }
            }, {e ->
                dialog("Error getting info for rooms: ", e.message)
            })
        }

        return binding.root
    }

    private fun dialog(title: String, message: String?) {
        val act = activity ?: throw RuntimeException("no app")
        (act as MainActivity).dialog(title, message)
    }


    companion object {
        @JvmStatic
        fun newInstance(columnCount: Int) = FavoriteRoomsFragment()
    }
}
