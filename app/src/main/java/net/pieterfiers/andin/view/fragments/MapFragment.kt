package net.pieterfiers.andin.view.fragments


import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.Observable
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.navigation.ui.NavigationUI
import com.leinardi.android.speeddial.SpeedDialActionItem
import kotlinx.coroutines.launch
import net.pieterfiers.andin.MapViewModel
import net.pieterfiers.andin.R
import net.pieterfiers.andin.databinding.FragmentMapBinding
import net.pieterfiers.andin.layersIcs
import net.pieterfiers.andin.layersIds
import net.pieterfiers.andin.view.fragments.support.MapElementDialogFragment


class MapFragment : Fragment() {
    private lateinit var binding: FragmentMapBinding
    private lateinit var viewModel: MapViewModel

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map, menu)

        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchItem = menu.findItem(R.id.app_bar_search)

        val searchView = searchItem.actionView as android.widget.SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))
        searchView.isIconifiedByDefault = false
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                item?.actionView?.clearFocus()
                Context.INPUT_METHOD_SERVICE
                val imm: InputMethodManager = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
                return true
            }

            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                item.actionView.requestFocus()
                val imm: InputMethodManager = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.preferences -> {
                viewModel.navController.navigate(R.id.preferencesFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val levelsChangeCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            binding.layersSpeedDial.clearActionItems()
            val levels = viewModel.levels.get() ?: throw RuntimeException("fix me")
            for (level in levels) {
                if (level >= -2 && level <= 5) {
                    val arrIndex = level + 2 // + b1 + b2
                    binding.layersSpeedDial.addActionItem(
                        SpeedDialActionItem.Builder(
                            layersIds[arrIndex], layersIcs[arrIndex]
                        ).create())
                }
            }
        }
    }

    private val currentLevelChangeCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            currentLevelChangeHandler()
        }
    }

    private fun currentLevelChangeHandler() {
        val level = viewModel.currentLevel.get()
        val shown = binding.layersSpeedDial.isShown
        if (level != null) {
            if (!shown) {
                lifecycleScope.launch {
                    binding.layersSpeedDial.show()
                }
            }
        } else {
            if (shown) {
                lifecycleScope.launch {
                    binding.layersSpeedDial.hide()
                }
            }
        }
    }

    private val selectedMapElementCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            selectedMapElementHandler()
        }
    }

    private fun selectedMapElementHandler() {
        val elem = viewModel.selectedMapElement.get()
        if (elem != null) {
            val man = requireFragmentManager()
            val existing = man.fragments.filterIsInstance<MapElementDialogFragment>().firstOrNull()
            if (existing == null) {
                val mapElementDialog = MapElementDialogFragment.newInstance(elem)
                mapElementDialog.show(requireFragmentManager(), null)
                mapElementDialog.onCancelListener = {
                    viewModel.selectedMapElement.set(null)
                }
            } else {
                existing.onCancelListener = {
                    viewModel.selectedMapElement.set(null)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = activity?.run {
            ViewModelProviders.of(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMapBinding.inflate(inflater)
        binding.vm = viewModel

        if (viewModel.currentLevel.get() == null) {
            binding.layersSpeedDial.hide()
        }

        val toolbar = binding.myToolbar
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(toolbar)
        NavigationUI.setupActionBarWithNavController(activity, viewModel.navController)

        viewModel.levels.addOnPropertyChangedCallback(levelsChangeCallback)
        viewModel.currentLevel.addOnPropertyChangedCallback(currentLevelChangeCallback)
        viewModel.selectedMapElement.addOnPropertyChangedCallback(selectedMapElementCallback)
        selectedMapElementHandler()

        binding.layersSpeedDial.setOnActionSelectedListener {item ->
            val layersIdI = layersIds.indexOf(item.id)
            if (layersIdI == -1) {
                throw RuntimeException("Non-existing level selected")
            }
            val level = layersIdI - 2
            viewModel.currentLevel.set(level)
            true
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()

        viewModel.levels.removeOnPropertyChangedCallback(levelsChangeCallback)
        viewModel.currentLevel.removeOnPropertyChangedCallback(currentLevelChangeCallback)
        viewModel.selectedMapElement.removeOnPropertyChangedCallback(selectedMapElementCallback)
    }
}
