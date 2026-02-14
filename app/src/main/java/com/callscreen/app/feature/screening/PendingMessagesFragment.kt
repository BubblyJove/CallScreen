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
import com.callscreen.app.util.ScreenLog
import dagger.android.support.AndroidSupportInjection
import io.realm.RealmResults
import javax.inject.Inject

class PendingMessagesFragment : Fragment() {

    @Inject lateinit var screeningRepository: ScreeningRepository

    private var pendingMessages: RealmResults<PendingScreenedMessage>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            AndroidSupportInjection.inject(this)
            ScreenLog.d(TAG, "Injection succeeded")
        } catch (e: Exception) {
            ScreenLog.e(TAG, "Injection FAILED", e)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_pending_messages, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val emptyView = view.findViewById<TextView>(R.id.emptyView)

        // Show empty state by default
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE

        if (!::screeningRepository.isInitialized) {
            ScreenLog.w(TAG, "screeningRepository not initialized, returning empty view")
            return view
        }

        recyclerView.layoutManager = LinearLayoutManager(context)

        pendingMessages = screeningRepository.getPendingMessages()
        recyclerView.adapter = PendingMessageAdapter(pendingMessages!!)
        ScreenLog.d(TAG, "Adapter set, initial size=${pendingMessages?.size ?: 0}")
        pendingMessages?.addChangeListener { results ->
            ScreenLog.d(TAG, "Change listener fired: ${results.size} results")
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

    companion object {
        private const val TAG = "PendingMsgs"
    }
}
