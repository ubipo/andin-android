package net.pieterfiers.andin.view.fragments


import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.Observable
import androidx.databinding.ObservableList
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.ui.NavigationUI
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import net.pieterfiers.andin.*
import net.pieterfiers.andin.model.map.MapElement
import net.pieterfiers.andin.model.map.Room
import net.pieterfiers.andin.databinding.FragmentSearchResultsBinding
import net.pieterfiers.andin.view.MainActivity
import net.pieterfiers.andin.view.fragments.support.ShortRoomInfoFragment
import java.util.*

class SearchResultsFragment : Fragment() {
    private lateinit var viewModel: MapViewModel
    private lateinit var adapter: SearchItemAdapter

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map, menu)

        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchItem = menu.findItem(R.id.app_bar_search)

        val searchView = searchItem.actionView as android.widget.SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))
        searchItem.expandActionView()
        searchView.onActionViewExpanded()
        searchView.setQuery(viewModel.query.get(), false)
        searchView.clearFocus()
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                viewModel.navController.popBackStack()
                return false
            }

            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                item.actionView.requestFocus()
                val imm: InputMethodManager = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { // Handle item selection
        return when (item.itemId) {
            R.id.preferences -> {
                viewModel.navController.navigate(R.id.preferencesFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val searchResultsChangedCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            searchResultsChangedHandler()
        }
    }

    private val searchResultsList: List<Room> get() = viewModel.searchResults.get() ?: Collections.emptyList()

    private fun searchResultsChangedHandler() {
        lifecycleScope.launch {
            adapter.data = searchResultsList
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = activity?.run {
            ViewModelProviders.of(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    private fun roomSelectedHandler(searchResultRoom: Room) {
        val setSelected = { room: Room ->
            viewModel.selectedMapElement.set(room)
            viewModel.currentLevel.set(room.level)
            lifecycleScope.launch {
                if (viewModel.navController.currentDestination?.id != R.id.searchResultsMapFragment) {
                    viewModel.navController.navigate(R.id.action_searchResultsFragment_to_searchResultsMapFragment)
                }
            }
        }
        val handler = { e: Exception ->
            if (e !is GqlException)
                throw e

            val builder = StringBuilder(e.message?: "")
            var cause = e.cause
            while (cause != null) {
                builder.append(":\n${cause.message}")
                cause = cause.cause
            }
            dialog("Error getting info for room (UUID: ${searchResultRoom.uuid})", builder.toString())
        }
        val existingRoom = viewModel.mapData.rooms.filter { room ->
            room.uuid == searchResultRoom.uuid
        }.firstOrNull()

        var room: Room
        if (existingRoom == null) {
            fetchRoom(
                viewModel.apolloClient,
                searchResultRoom.uuid,
                handler
            ) { building ->
                viewModel.mapData.addBuildings(listOf(building))
                room = viewModel.mapData.rooms.firstOrNull { room ->
                    room.uuid == searchResultRoom.uuid
                } ?: throw RuntimeException("Room wasn't properly fetched")
                setSelected(room)
            }
        } else {
            setSelected(existingRoom)
        }
    }

    private val navigationChangedCallback = NavController.OnDestinationChangedListener { controller, destination, arguments ->
        val id = destination.id
        if (!(id == R.id.searchResultsFragment || id == R.id.searchResultsMapFragment)) {
            Log.v("AAA","Navigating away from search, clearing results...")
            viewModel.searchResults.set(null)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentSearchResultsBinding.inflate(inflater)

        viewModel.navController.addOnDestinationChangedListener(navigationChangedCallback)

        binding.vm = viewModel

        val toolbar = binding.myToolbar
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(toolbar)
        NavigationUI.setupActionBarWithNavController(activity, viewModel.navController)

        adapter = SearchItemAdapter { searchResultRoom ->
            roomSelectedHandler(searchResultRoom)
        }
        binding.recycler.adapter = adapter

        val layoutManager = LinearLayoutManager(context);
        binding.recycler.layoutManager = layoutManager

        val dividerItemDecoration = DividerItemDecoration(
            binding.recycler.context,
            layoutManager.orientation
        )
        binding.recycler.addItemDecoration(dividerItemDecoration)

        viewModel.searchResults.addOnPropertyChangedCallback(searchResultsChangedCallback)
        searchResultsChangedHandler()

        return binding.root
    }

    override fun onDestroyView() {
        viewModel.searchResults.removeOnPropertyChangedCallback(searchResultsChangedCallback)
        viewModel.navController.removeOnDestinationChangedListener(navigationChangedCallback)
        super.onDestroyView()
    }

    private fun dialog(title: String, message: String?) {
        val act = activity ?: throw RuntimeException("no app")
        (act as MainActivity).dialog(title, message)
    }

    companion object {
        @JvmStatic
        fun newInstance(query: String? = null) = ShortRoomInfoFragment()
    }
}
