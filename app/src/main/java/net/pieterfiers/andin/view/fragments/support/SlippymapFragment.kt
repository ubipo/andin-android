package net.pieterfiers.andin.view.fragments.support


import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.Observable
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import com.leinardi.android.speeddial.SpeedDialActionItem
import kotlinx.coroutines.launch
import net.pieterfiers.andin.MapViewModel

import net.pieterfiers.andin.R
import net.pieterfiers.andin.databinding.FragmentSlippymapBinding
import net.pieterfiers.andin.layersIcs
import net.pieterfiers.andin.layersIds
import net.pieterfiers.andin.view.fragments.MapFragment

class SlippymapFragment : Fragment() {
    private lateinit var binding: FragmentSlippymapBinding
    private lateinit var viewModel: MapViewModel

    val highlightFavorites = ObservableBoolean(false)
    val highlightSearchResults = ObservableBoolean(false)

    fun test() {
        Log.v("AAA", "Test called!")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            highlightFavorites.set(it.getBoolean(MapFragment.ARG_HIGHLIGHT_FAVORITES, false))
            highlightSearchResults.set(it.getBoolean(MapFragment.ARG_HIGHLIGHT_SEARCH_RESULTS, false))
        }
        viewModel = activity?.run {
            ViewModelProviders.of(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSlippymapBinding.inflate(inflater, container, false)

        if (viewModel.currentLevel.get() == null) {
            binding.layersSpeedDial.hide()
        }

        val slippymapFragment = childFragmentManager.findFragmentById(R.id.slippymapFragment) as SlippymapMapboxFragment
        slippymapFragment.highlightFavorites.set(highlightFavorites.get())
        slippymapFragment.highlightSearchResults.set(highlightSearchResults.get())
        highlightFavorites.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                slippymapFragment.highlightFavorites.set(highlightFavorites.get())
            }
        })
        highlightSearchResults.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                slippymapFragment.highlightSearchResults.set(highlightSearchResults.get())
            }
        })


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
            viewModel.desiredLevel.set(level)
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

    companion object {
        const val ARG_HIGHLIGHT_FAVORITES = "highlight_favorites"
        const val ARG_HIGHLIGHT_SEARCH_RESULTS = "highlight_search_results"

        @JvmStatic
        fun newInstance(highlightFavorites: Boolean = false, highlightSearchResults: Boolean = false) =
            SlippymapFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_HIGHLIGHT_FAVORITES, highlightFavorites)
                    putBoolean(ARG_HIGHLIGHT_SEARCH_RESULTS, highlightSearchResults)
                }
            }
    }
}
