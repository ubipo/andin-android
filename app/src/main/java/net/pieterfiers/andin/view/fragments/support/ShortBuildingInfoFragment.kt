package net.pieterfiers.andin.view.fragments.support


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.pieterfiers.andin.model.map.Building
import net.pieterfiers.andin.databinding.FragmentShortBuildingInfoBinding

private const val ARG_BUILDING = "building"

class ShortBuildingInfoFragment : Fragment() {
    private var building: Building? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val buildingArg = it.getSerializable(ARG_BUILDING)
            building = buildingArg as Building?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentShortBuildingInfoBinding.inflate(inflater)
        binding.building = building
        return binding.root
    }


    companion object {
        @JvmStatic
        fun newInstance(building: Building?) =
            ShortBuildingInfoFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_BUILDING, building)
                }
            }
    }
}
