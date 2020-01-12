package net.pieterfiers.andin.view.fragments.support


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.pieterfiers.andin.databinding.FragmentShortRoomInfoBinding
import net.pieterfiers.andin.model.map.Room

const val ARG_ROOM = "room"

class ShortRoomInfoFragment() : Fragment() {
    private var room: Room? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val roomArg = it.getSerializable(ARG_ROOM)
            room = roomArg as Room?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentShortRoomInfoBinding.inflate(inflater)
        binding.room = room
        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance(room: Room? = null) =
            ShortRoomInfoFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_ROOM, room)
                }
            }
    }
}
