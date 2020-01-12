package net.pieterfiers.andin

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import net.pieterfiers.andin.model.map.Room

class TextItemViewHolder(val layout: ConstraintLayout, private var room: Room? = null): RecyclerView.ViewHolder(layout) {
    fun bind(room: Room, onClick: (room: Room) -> Unit) {
        val titleView = layout.getChildAt(0) as TextView
        titleView.text = room.labelText?: "Unknown room"
        val levelView = layout.getChildAt(1) as TextView
        levelView.text = "Level ${room.level}"
        this.room = room
        itemView.setOnClickListener {
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
        val view = layoutInflater.inflate(R.layout.search_item_view, parent, false) as ConstraintLayout

        return TextItemViewHolder(view)
    }
}