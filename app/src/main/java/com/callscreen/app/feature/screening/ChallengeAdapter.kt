package com.callscreen.app.feature.screening

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callscreen.app.R
import com.callscreen.app.model.ChallengeState
import java.text.DateFormat
import java.util.Date

class ChallengeAdapter : RecyclerView.Adapter<ChallengeAdapter.ViewHolder>() {

    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    private var items: List<ChallengeState> = emptyList()

    fun updateData(newItems: List<ChallengeState>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_active_challenge, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.phoneNumber.text = item.phoneNumber
        holder.type.text = item.type.name
        holder.question.text = item.challengeQuestion
        holder.expiry.text = holder.itemView.context.getString(
            R.string.screening_challenge_expires,
            dateFormat.format(Date(item.expiresAt))
        )
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val phoneNumber: TextView = view.findViewById(R.id.phoneNumber)
        val type: TextView = view.findViewById(R.id.type)
        val question: TextView = view.findViewById(R.id.question)
        val expiry: TextView = view.findViewById(R.id.expiry)
    }
}
