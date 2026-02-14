package com.callscreen.app.feature.screening

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callscreen.app.R
import com.callscreen.app.model.ChallengeState
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter
import java.text.DateFormat
import java.util.Date

class ChallengeAdapter(
    data: OrderedRealmCollection<ChallengeState>
) : RealmRecyclerViewAdapter<ChallengeState, ChallengeAdapter.ViewHolder>(data, true) {

    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_active_challenge, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
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
