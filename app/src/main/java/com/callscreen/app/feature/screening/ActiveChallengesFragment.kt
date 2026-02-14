package com.callscreen.app.feature.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.callscreen.app.R
import dagger.android.support.AndroidSupportInjection
import timber.log.Timber

class ActiveChallengesFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            AndroidSupportInjection.inject(this)
        } catch (e: Exception) {
            Timber.e(e, "Failed to inject ActiveChallengesFragment")
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_active_challenges, container, false)
        val emptyView = view.findViewById<TextView>(R.id.emptyView)
        emptyView.visibility = View.VISIBLE
        emptyView.text = getString(R.string.screening_no_challenges)
        return view
    }
}
