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
import com.callscreen.app.model.WhitelistedContact
import com.callscreen.app.repository.ScreeningRepository
import com.callscreen.app.util.ScreenLog
import dagger.android.support.AndroidSupportInjection
import io.realm.RealmResults
import javax.inject.Inject

class WhitelistedContactsFragment : Fragment() {

    @Inject lateinit var screeningRepository: ScreeningRepository

    private var contacts: RealmResults<WhitelistedContact>? = null
    private var adapter: WhitelistedContactAdapter? = null

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
        val view = inflater.inflate(R.layout.fragment_whitelisted_contacts, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val emptyView = view.findViewById<TextView>(R.id.emptyView)

        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE

        if (!::screeningRepository.isInitialized) {
            ScreenLog.w(TAG, "screeningRepository not initialized")
            return view
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = WhitelistedContactAdapter()
        recyclerView.adapter = adapter

        contacts = screeningRepository.getWhitelistedContacts()
        ScreenLog.d(TAG, "Realm query created, adding change listener")
        contacts?.addChangeListener { results ->
            ScreenLog.d(TAG, "Change listener: ${results.size} results, loaded=${results.isLoaded}, valid=${results.isValid}")
            if (results.isLoaded && results.isNotEmpty()) {
                try {
                    val copied = results.realm.copyFromRealm(results)
                    ScreenLog.d(TAG, "Copied ${copied.size} items from Realm")
                    copied.forEachIndexed { i, c ->
                        ScreenLog.d(TAG, "  [$i] phone=${c.phoneNumber} source=${c.source}")
                    }
                    adapter?.updateData(copied)
                    recyclerView.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                    ScreenLog.d(TAG, "RecyclerView VISIBLE, adapter count=${adapter?.itemCount}")
                } catch (e: Exception) {
                    ScreenLog.e(TAG, "copyFromRealm FAILED: ${e.message}", e)
                }
            } else {
                adapter?.updateData(emptyList())
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                ScreenLog.d(TAG, "Empty state shown")
            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        contacts?.removeAllChangeListeners()
    }

    companion object {
        private const val TAG = "Whitelist"
    }
}
