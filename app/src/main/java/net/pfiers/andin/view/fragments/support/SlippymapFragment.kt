package net.pfiers.andin.view.fragments.support


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.Observable
import androidx.databinding.ObservableBoolean
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.leinardi.android.speeddial.SpeedDialActionItem
import kotlinx.coroutines.launch
import net.pfiers.andin.MapViewModel
import net.pfiers.andin.R
import net.pfiers.andin.databinding.FragmentSlippymapBinding
import net.pfiers.andin.layersIds
import net.pfiers.andin.model.map.LevelRange
import net.pfiers.andin.model.onPropertyChangedCallback
import net.pfiers.andin.view.LevelTextDrawable
import net.pfiers.andin.view.SubTextDrawable
import net.pfiers.andin.view.fragments.MapFragment
import kotlin.math.ceil
import kotlin.math.floor


class SlippymapFragment : Fragment() {
    private lateinit var binding: FragmentSlippymapBinding
    private lateinit var viewModel: MapViewModel

    val highlightFavorites = ObservableBoolean(false)
    val highlightSearchResults = ObservableBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            highlightFavorites.set(it.getBoolean(MapFragment.ARG_HIGHLIGHT_FAVORITES, false))
            highlightSearchResults.set(it.getBoolean(MapFragment.ARG_HIGHLIGHT_SEARCH_RESULTS, false))
        }
        viewModel = activity?.run {
            ViewModelProvider(this)[MapViewModel::class.java]
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

        binding.layersSpeedDial.setOnActionSelectedListener { item ->
            val layersIdI = layersIds.indexOf(item.id)
            if (layersIdI == -1) {
                throw RuntimeException("Non-existing level selected")
            }
            val level = layersIdI - 2
            viewModel.desiredLevel.set(level.toDouble())
            true
        }

        binding.navButton.setOnClickListener {
            val existing = parentFragmentManager.fragments.filterIsInstance<NavInfoBsFragment>().firstOrNull()
            if (existing == null) {
                val mapElementDialog = NavInfoBsFragment.newInstance()
                mapElementDialog.show(parentFragmentManager, null)
            }
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
            val levels = viewModel.levels.get() ?: LevelRange(0.0)
            val from = floor(levels.from).toInt()
            val to = levels.to?.let { ceil(it).toInt() }
            val levelsRange = if (to != null) from..to else from..from
            val currentLevel = viewModel.currentLevel.get()?.let { ceil(it).toInt() }
            for (level in levelsRange) {
                if (level >= -2 && level <= 5) {
                    val arrIndex = level + 2 // + b1 + b2
                    val isCurrentLevel = currentLevel == level
                    val itemBuilder = SpeedDialActionItem.Builder(
                        layersIds[arrIndex], LevelTextDrawable(level.toString())
                    )
                    if (isCurrentLevel) {
                        val bgColorId = if (isCurrentLevel) R.color.colorPrimaryDark else R.color.colorPrimary
                        val bgColor = context?.let {
                            ContextCompat.getColor(
                                it,
                                bgColorId
                            )
                        } ?: error("Couldn't get color")
                        itemBuilder.setFabBackgroundColor(bgColor)
                        itemBuilder.setLabelClickable(false)
                        itemBuilder.setFabSize(FloatingActionButton.SIZE_MINI)
                    }
                    binding.layersSpeedDial.addActionItem(itemBuilder.create())
                }
            }
            val layersIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_layers_white_24, null) ?: error("Couldn't load layers icon")
            val closedDrawable = if (currentLevel == null) {
                layersIcon
            } else {
                LevelTextDrawable(currentLevel.toString())
            }
            binding.layersSpeedDial.setMainFabClosedDrawable(closedDrawable)
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

    private val selectedMapElementCallback = onPropertyChangedCallback(this::selectedMapElementHandler)

    private var dialogShown = false

    private fun selectedMapElementHandler() {
        val elem = viewModel.selectedMapElement.get()
        if (elem != null) {
            if (dialogShown)
                return

            val existing = parentFragmentManager.findFragmentByTag("TEST_TAG") as MapElementDialogFragment?
            if (existing == null) {
                val mapElementDialog = MapElementDialogFragment.newInstance(elem)
                mapElementDialog.show(parentFragmentManager, "TEST_TAG")
                mapElementDialog.onCancelListener = {
                    viewModel.selectedMapElement.set(null)
                    dialogShown = false
                }
                dialogShown = true
            } else {
                existing.onCancelListener = {
                    viewModel.selectedMapElement.set(null)
                    dialogShown = false
                }
                dialogShown = false
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
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
