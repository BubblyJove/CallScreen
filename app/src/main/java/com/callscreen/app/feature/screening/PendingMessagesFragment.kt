package com.callscreen.app.feature.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.callscreen.app.R
import com.callscreen.app.model.PendingScreenedMessage
import com.callscreen.app.repository.ScreeningRepository
import dagger.android.support.AndroidSupportInjection
import io.realm.RealmResults
import javax.inject.Inject

class PendingMessagesFragment : Fragment() {

    @Inject lateinit var screeningRepository: ScreeningRepository

    private var pendingMessages: RealmResults<PendingScreenedMessage>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidSupportInjection.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_pending_messages, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val emptyView = view.findViewById<TextView>(R.id.emptyView)

        recyclerView.layoutManager = LinearLayoutManager(context)

        pendingMessages = screeningRepository.getPendingMessages()
        pendingMessages?.addChangeListener { results ->
            if (results.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingMessages?.removeAllChangeListeners()
    }
}
