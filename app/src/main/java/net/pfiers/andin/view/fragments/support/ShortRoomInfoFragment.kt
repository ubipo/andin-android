package net.pfiers.andin.view.fragments.support


import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.pfiers.andin.MapViewModel
import net.pfiers.andin.databinding.FragmentShortRoomInfoBinding
import net.pfiers.andin.db.FavoriteRoom
import net.pfiers.andin.model.map.Room
import net.pfiers.andin.view.MainActivity

const val ARG_ROOM = "room"

class ShortRoomInfoFragment : Fragment() {
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
            ViewModelProvider(this)[MapViewModel::class.java]
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
            binding.navButton.setOnClickListener {
                val mapElementDialog = ChooseNavDepartDestFragment.newInstance(lRoom)
                mapElementDialog.show(parentFragmentManager, null)
//                mapElementDialog.onCancelListener = {
//                    viewModel.selectedMapElement.set(null)
//                }
//                if (viewModel.departure.get() == null)
//                    viewModel.departure.set(Navigable(lRoom))
//                else
//                    viewModel.destination.set(Navigable(lRoom))
            }
            binding.shareButton.setOnClickListener {
                val share = Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "${lRoom.labelText}")
                    putExtra(Intent.EXTRA_SUBJECT, "${lRoom.labelText} Andin room")
                    putExtra(Intent.EXTRA_TEXT, "https://andin.pfiers.net/room/${lRoom.uuid}")
                }, "Share room")
                startActivity(share)
            }
            binding.technicalInfo.setOnClickListener {
                dialog("Technical room info", """
                    UUID: ${lRoom.uuid}
                    LevelRange: ${lRoom.levelRange}
                    WalkableId: ${lRoom.navGraphWalkableId}
                    Toilet: ${lRoom.toilet}
                    Coffee: ${lRoom.drinkCoffee}
                    FirstAid: ${lRoom.firstAidKit}
                """.trimIndent())
            }
        }
        return binding.root
    }

    private fun dialog(title: String, message: String?) {
        val act = activity ?: throw RuntimeException("no app")
        (act as MainActivity).dialog(title, message)
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
