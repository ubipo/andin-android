package net.pfiers.andin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import net.pfiers.andin.databinding.ViewFavoriteRoomsItemBinding
import net.pfiers.andin.model.map.Room

class FavoriteRoomRecyclerAdapter(val onClick: (room: Room) -> Unit) : RecyclerView.Adapter<FavoriteRoomRecyclerAdapter.Holder>() {
    var data = listOf<Room>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(data[position], onClick)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ViewFavoriteRoomsItemBinding.inflate(layoutInflater, parent, false)
        return Holder(binding)
    }

    inner class Holder(private val binding: ViewFavoriteRoomsItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(room: Room, onClick: (room: Room) -> Unit) {
            binding.room = room
            itemView.setOnClickListener {
                onClick(room)
            }
        }
    }
}