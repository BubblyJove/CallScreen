package com.callscreen.app.feature.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.callscreen.app.R
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import com.callscreen.app.repository.ScreeningRepository

class ActiveChallengesFragment : Fragment() {

    @Inject lateinit var screeningRepository: ScreeningRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidSupportInjection.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_active_challenges, container, false)
        val emptyView = view.findViewById<TextView>(R.id.emptyView)
        emptyView.text = getString(R.string.screening_no_challenges)
        return view
    }
}
