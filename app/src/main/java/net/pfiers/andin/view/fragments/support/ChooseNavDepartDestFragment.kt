package net.pfiers.andin.view.fragments.support

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.pfiers.andin.MapViewModel
import net.pfiers.andin.databinding.FragmentChooseNavDepartDestBinding
import net.pfiers.andin.model.map.IndoorMapElement
import net.pfiers.andin.model.nav.Navigable

private const val ARG_MAP_ELEMENT = "map_element"

class ChooseNavDepartDestFragment : BottomSheetDialogFragment() {
    private lateinit var viewModel: MapViewModel
    private var mapElement: IndoorMapElement? = null
    var onCancelListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val mapElementArg = it.getSerializable(ARG_MAP_ELEMENT)
            mapElement = mapElementArg as IndoorMapElement?
        }
        viewModel = activity?.run {
            ViewModelProvider(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentChooseNavDepartDestBinding.inflate(inflater)

        binding.vm = viewModel
        binding.mapElement = mapElement

        binding.departure.setOnClickListener {
            viewModel.departure.set(Navigable(mapElement ?: error("No mapElement")))
            val mapElementDialog = NavInfoBsFragment.newInstance()
            mapElementDialog.show(parentFragmentManager, null)
            dismiss()
        }

        binding.destination.setOnClickListener {
            viewModel.destination.set(Navigable(mapElement ?: error("No mapElement")))
            val mapElementDialog = NavInfoBsFragment.newInstance()
            mapElementDialog.show(parentFragmentManager, null)
            dismiss()
        }

        return binding.root
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCancelListener?.invoke()
    }

    companion object {
        @JvmStatic
        fun newInstance(mapElement: IndoorMapElement) =
            ChooseNavDepartDestFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_MAP_ELEMENT, mapElement)
                }
            }
    }
}