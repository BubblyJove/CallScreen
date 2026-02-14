package com.callscreen.app.feature.screening

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callscreen.app.R
import com.callscreen.app.model.WhitelistedContact
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter
import java.text.DateFormat
import java.util.Date

class WhitelistedContactAdapter(
    data: OrderedRealmCollection<WhitelistedContact>
) : RealmRecyclerViewAdapter<WhitelistedContact, WhitelistedContactAdapter.ViewHolder>(data, true) {

    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_whitelisted_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.phoneNumber.text = item.displayName.ifBlank { item.phoneNumber }
        holder.source.text = item.source.name.replace('_', ' ').lowercase()
            .replaceFirstChar { it.uppercase() }
        holder.date.text = dateFormat.format(Date(item.whitelistedAt))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val phoneNumber: TextView = view.findViewById(R.id.phoneNumber)
        val source: TextView = view.findViewById(R.id.source)
        val date: TextView = view.findViewById(R.id.date)
    }
}
