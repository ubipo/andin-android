package net.pieterfiers.andin.view.fragments.support


import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.pieterfiers.andin.MapViewModel
import net.pieterfiers.andin.databinding.FragmentShortRoomInfoBinding
import net.pieterfiers.andin.db.FavoriteRoom
import net.pieterfiers.andin.model.map.Room

const val ARG_ROOM = "room"

class ShortRoomInfoFragment() : Fragment() {
    lateinit var favoriteRoom: LiveData<FavoriteRoom?>
    lateinit var viewModel: MapViewModel

    private var room: Room? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val roomArg = it.getSerializable(ARG_ROOM)
            room = roomArg as Room?
        }
        viewModel = activity?.run {
            ViewModelProviders.of(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentShortRoomInfoBinding.inflate(inflater)
        binding.lifecycleOwner = this

        binding.room = room
        val lRoom = room
        Log.v("AAA","${lRoom}")
        if (lRoom != null) {
            favoriteRoom = viewModel.dao.get(lRoom.uuid)
            binding.favoriteRoom = favoriteRoom
            binding.favoriteToggle.setOnClickListener {
                if (favoriteRoom.value == null) {
                    GlobalScope.launch {
                        if (viewModel.dao.get(lRoom.uuid).value == null) {
                            try {
                                viewModel.dao.insert(FavoriteRoom(uuid = lRoom.uuid))
                            } catch (e: SQLiteConstraintException) {
                                // Pass
                            }
                        }
                    }
                } else {
                    GlobalScope.launch {
                        viewModel.dao.delete(lRoom.uuid)
                    }
                }
            }
            binding.shareButton.setOnClickListener {
                val share = Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "${lRoom.labelText}")
                    putExtra(Intent.EXTRA_SUBJECT, "Andin room")
                    putExtra(Intent.EXTRA_TEXT, "https://andin.pieterfiers.net/room/${lRoom.uuid}")
                }, "Share room")
                startActivity(share)
            }
        }
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
