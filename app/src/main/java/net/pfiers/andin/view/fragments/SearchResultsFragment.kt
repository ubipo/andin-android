package net.pfiers.andin.view.fragments


import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.Observable
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.ui.NavigationUI
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import net.pfiers.andin.MapViewModel
import net.pfiers.andin.R
import net.pfiers.andin.SearchItemAdapter
import net.pfiers.andin.databinding.FragmentSearchResultsBinding
import net.pfiers.andin.db.getOrDownload
import net.pfiers.andin.model.map.Room
import net.pfiers.andin.model.nav.Navigable
import net.pfiers.andin.view.MainActivity
import net.pfiers.andin.view.fragments.support.ShortRoomInfoFragment
import java.util.*


class SearchResultsFragment : Fragment() {
    private lateinit var viewModel: MapViewModel
    private lateinit var adapter: SearchItemAdapter
    private var searchDeparture = false
    private var searchDestination = false

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
        arguments?.let {
            searchDeparture = it.getBoolean(ARG_SEARCH_DEPARTURE, false)
            searchDestination = it.getBoolean(ARG_SEARCH_DESTINATION, false)
        }
        setHasOptionsMenu(true)
        viewModel = activity?.run {
            ViewModelProvider(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    private fun roomSelectedHandler(searchResultRoom: Room) {
        val setSelected = { room: Room ->
            viewModel.desiredLevel.set(room.levelRange.from)
            if (searchDeparture) {
                viewModel.departure.set(Navigable(room))
                viewModel.selectedMapElement.set(null)
                viewModel.navController.navigateUp()
            } else if (searchDestination) {
                viewModel.destination.set(Navigable(room))
                viewModel.selectedMapElement.set(null)
                viewModel.navController.navigateUp()
            } else {
                viewModel.selectedMapElement.set(room)
                lifecycleScope.launch {
                    if (viewModel.navController.currentDestination?.id != R.id.searchResultsMapFragment) {
                        viewModel.navController.navigate(R.id.action_searchResultsFragment_to_searchResultsMapFragment)
                    }
                }
            }
            Unit
        }

        getOrDownload(searchResultRoom.uuid, viewModel, { room ->
            setSelected(room)
        }, { e ->
            dialog("Error getting info for selected room", e.message)
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentSearchResultsBinding.inflate(inflater)
        binding.lifecycleOwner = this

        binding.vm = viewModel

        val toolbar = binding.toolbar
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
        super.onDestroyView()
    }

    private fun dialog(title: String, message: String?) {
        val act = activity ?: throw RuntimeException("no app")
        (act as MainActivity).dialog(title, message)
    }

    companion object {
        const val ARG_SEARCH_DEPARTURE = "search_departure"
        const val ARG_SEARCH_DESTINATION = "search_destination"

        @JvmStatic
        fun newInstance(query: String? = null, searchDeparture: Boolean = false, searchDestination: Boolean = false) =
            ShortRoomInfoFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_SEARCH_DEPARTURE, searchDeparture)
                    putSerializable(ARG_SEARCH_DESTINATION, searchDestination)
                }
            }
    }
}
