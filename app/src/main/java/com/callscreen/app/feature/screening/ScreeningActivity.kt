package com.callscreen.app.feature.screening

import android.os.Bundle
import com.google.android.material.tabs.TabLayoutMediator
import dagger.android.AndroidInjection
import com.callscreen.app.R
import com.callscreen.app.common.base.QkActivity
import com.callscreen.app.databinding.ScreeningActivityBinding

class ScreeningActivity : QkActivity() {

    private lateinit var binding: ScreeningActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        binding = ScreeningActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showBackButton(true)
        title = getString(R.string.screening_title)

        val adapter = ScreeningPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.screening_tab_pending)
                1 -> getString(R.string.screening_tab_whitelist)
                2 -> getString(R.string.screening_tab_challenges)
                else -> ""
            }
        }.attach()
    }
}
