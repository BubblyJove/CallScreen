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
import com.callscreen.app.model.ChallengeState
import com.callscreen.app.repository.ScreeningRepository
import com.callscreen.app.util.ScreenLog
import dagger.android.support.AndroidSupportInjection
import io.realm.RealmResults
import javax.inject.Inject

class ActiveChallengesFragment : Fragment() {

    @Inject lateinit var screeningRepository: ScreeningRepository

    private var challenges: RealmResults<ChallengeState>? = null
    private var adapter: ChallengeAdapter? = null

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
        val view = inflater.inflate(R.layout.fragment_active_challenges, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val emptyView = view.findViewById<TextView>(R.id.emptyView)

        // Show empty state by default
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        emptyView.text = getString(R.string.screening_no_challenges)

        if (!::screeningRepository.isInitialized) {
            ScreenLog.w(TAG, "screeningRepository not initialized, returning empty view")
            return view
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ChallengeAdapter()
        recyclerView.adapter = adapter

        challenges = screeningRepository.getActiveChallenges()
        challenges?.addChangeListener { results ->
            ScreenLog.d(TAG, "Change listener: ${results.size} results, loaded=${results.isLoaded}")
            if (results.isLoaded && results.isNotEmpty()) {
                val copied = results.realm.copyFromRealm(results)
                adapter?.updateData(copied)
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            } else {
                adapter?.updateData(emptyList())
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        challenges?.removeAllChangeListeners()
    }

    companion object {
        private const val TAG = "Challenges"
    }
}
