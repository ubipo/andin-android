package net.pfiers.andin.view.fragments


import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import net.pfiers.andin.MapViewModel
import net.pfiers.andin.R
import net.pfiers.andin.databinding.FragmentMapBinding
import net.pfiers.andin.db.FavoriteRoom
import net.pfiers.andin.view.fragments.support.ShortRoomInfoFragment
import net.pfiers.andin.view.fragments.support.SlippymapFragment


class MapFragment : Fragment() {
    private lateinit var binding: FragmentMapBinding
    private lateinit var viewModel: MapViewModel
    private lateinit var favorites: LiveData<List<FavoriteRoom>>
    private lateinit var toggle: ActionBarDrawerToggle

    private var highlightFavorites = false
    private var highlightSearchResults = false
    private var isHome = false
    private var isDrieDee = false

    private lateinit var libGdxFragment: DrieDeeFragment
    private lateinit var slippyMapFragment: SlippymapFragment

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map, menu)

        val activity = requireActivity()

        val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchItem = menu.findItem(R.id.app_bar_search)

        val searchView = searchItem.actionView as android.widget.SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.componentName))
        searchView.isIconifiedByDefault = false
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                item?.actionView?.clearFocus()
                Context.INPUT_METHOD_SERVICE
                val imm: InputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
                return true
            }

            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                item.actionView.requestFocus()
                val imm: InputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isHome) {
                    binding.drawer.openDrawer(GravityCompat.START)
                } else {
                    if (viewModel.navController.currentDestination?.id == R.id.sharedRoomFragment) {
                        activity?.finish()
                    } else {
                        viewModel.navController.navigateUp()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            isHome = it.getBoolean(ARG_HOME, false)
            isDrieDee = it.getBoolean(ARG_DRIE_DEE, false)
            highlightFavorites = it.getBoolean(ARG_HIGHLIGHT_FAVORITES, false)
            highlightSearchResults = it.getBoolean(ARG_HIGHLIGHT_SEARCH_RESULTS, false)
        }
        viewModel = activity?.run {
            ViewModelProvider(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        favorites = viewModel.dao.getAll()
    }

    private fun setCorrectFragment() {
        if (isDrieDee) {
            parentFragmentManager.beginTransaction().remove(slippyMapFragment).add(R.id.slippymapFragment, libGdxFragment).commit()
        } else {
            parentFragmentManager.beginTransaction().remove(libGdxFragment).add(R.id.slippymapFragment, slippyMapFragment).commit()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMapBinding.inflate(inflater)
        binding.vm = viewModel

        binding.navView.setupWithNavController(viewModel.navController)

        val toolbar = binding.toolbar
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(toolbar)
        NavigationUI.setupActionBarWithNavController(activity, viewModel.navController)

        libGdxFragment = DrieDeeFragment()
        slippyMapFragment = SlippymapFragment.newInstance(highlightFavorites, highlightSearchResults)

        setCorrectFragment()

        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true);

        toggle = ActionBarDrawerToggle(activity, binding.drawer, R.string.open_drawer, R.string.close_drawer)
        toggle.isDrawerIndicatorEnabled = isHome
        binding.drawer.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener {item ->
            when (item.itemId) {
                R.id.drawer_map -> {
                    if (viewModel.navController.currentDestination?.id == R.id.mapFragment) {
                        binding.drawer.closeDrawer(GravityCompat.START)
                    } else {
                        viewModel.navController.navigate(R.id.mapFragment)
                    }
                }
                R.id.drawer_preferences -> {
                    viewModel.navController.navigate(R.id.preferencesFragment)
                }
                R.id.drawer_favorites -> {
                    viewModel.navController.navigate(R.id.favoriteRoomsFragment)
                }
            }
            false
        }

        binding.drieDeeButton.setOnClickListener {
            isDrieDee = !isDrieDee
            setCorrectFragment()
        }

        return binding.root
    }

    companion object {
        const val ARG_HOME = "home"
        const val ARG_DRIE_DEE = "drie_dee"
        const val ARG_HIGHLIGHT_FAVORITES = "highlight_favorites"
        const val ARG_HIGHLIGHT_SEARCH_RESULTS = "highlight_search_results"

        @JvmStatic
        fun newInstance(highlightFavorites: Boolean = false, highlightSearchResults: Boolean = false, isHome: Boolean = false, drieDee: Boolean = false) =
            ShortRoomInfoFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_HOME, isHome)
                    putBoolean(ARG_DRIE_DEE, drieDee)
                    putBoolean(ARG_HIGHLIGHT_FAVORITES, highlightFavorites)
                    putBoolean(ARG_HIGHLIGHT_SEARCH_RESULTS, highlightSearchResults)
                }
            }
    }
}
