package net.pfiers.andin.view.fragments.support

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import net.pfiers.andin.R
import net.pfiers.andin.model.map.Building
import net.pfiers.andin.model.map.Room
import net.pfiers.andin.databinding.FragmentMapelementDialogBinding
import net.pfiers.andin.model.map.MapElement
import kotlin.RuntimeException

private const val ARG_MAP_ELEMENT = "map_element"

class MapElementDialogFragment : BottomSheetDialogFragment() {
    private var mapElement: MapElement? = null
    var onCancelListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val mapElementArg = it.getSerializable(ARG_MAP_ELEMENT)
            mapElement = mapElementArg as MapElement?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) ?:
            throw RuntimeException("No window")

        val binding = FragmentMapelementDialogBinding.inflate(inflater)
        binding.mapElement = mapElement

        when (val lMapElement = mapElement) {
            is Room -> {
                val detail =
                    ShortRoomInfoFragment.newInstance(lMapElement)
                childFragmentManager.beginTransaction().add(R.id.fragment_container, detail).commit()
            }
            is Building -> {
                val detail =
                    ShortBuildingInfoFragment.newInstance(lMapElement)
                childFragmentManager.beginTransaction().add(R.id.fragment_container, detail).commit()
            }
            else -> {
                Log.e("AAA","Unknown map element")
            }
        }

        return binding.root
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCancelListener?.invoke()
    }

    companion object {
        @JvmStatic
        fun newInstance(mapElement: MapElement? = null) =
            MapElementDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_MAP_ELEMENT, mapElement)
                }
            }
    }
}
