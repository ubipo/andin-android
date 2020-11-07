package net.pfiers.andin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import net.pfiers.andin.databinding.ViewFavoriteRoomsItemBinding
import net.pfiers.andin.model.map.Room

class TextItemViewHolder(val binding: ViewFavoriteRoomsItemBinding, private var room: Room? = null): RecyclerView.ViewHolder(binding.root) {
    fun bind(room: Room, onClick: (room: Room) -> Unit) {
        binding.room = room
//        binding.root.setOnCli
//        val titleView = layout.getChildAt(0) as TextView
//        titleView.text = room.labelText?: "Unknown room"
//        val levelView = layout.getChildAt(1) as TextView
//        levelView.text = "Level ${room.levelRange.from.lvlStr}"
//        this.room = room
        binding.root.setOnClickListener {
            onClick(room)
        }
    }
}

class SearchItemAdapter(val onClick: (room: Room) -> Unit) : RecyclerView.Adapter<TextItemViewHolder>() {
    var data = listOf<Room>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: TextItemViewHolder, position: Int) {
        holder.bind(data[position], onClick)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextItemViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ViewFavoriteRoomsItemBinding.inflate(layoutInflater, parent, false)
//        val view = layoutInflater.inflate(R.layout.view_search_results_item, parent, false) as ConstraintLayout

        return TextItemViewHolder(binding)
    }
}