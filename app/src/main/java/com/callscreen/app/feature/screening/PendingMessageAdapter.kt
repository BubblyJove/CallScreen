package com.callscreen.app.feature.screening

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callscreen.app.R
import com.callscreen.app.model.PendingScreenedMessage
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter
import java.text.DateFormat
import java.util.Date

class PendingMessageAdapter(
    data: OrderedRealmCollection<PendingScreenedMessage>
) : RealmRecyclerViewAdapter<PendingScreenedMessage, PendingMessageAdapter.ViewHolder>(data, true) {

    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.phoneNumber.text = item.phoneNumber
        holder.body.text = item.body
        holder.timestamp.text = dateFormat.format(Date(item.timestamp))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val phoneNumber: TextView = view.findViewById(R.id.phoneNumber)
        val body: TextView = view.findViewById(R.id.body)
        val timestamp: TextView = view.findViewById(R.id.timestamp)
    }
}
