package com.callscreen.app.feature.screening

import android.os.Bundle
import com.google.android.material.tabs.TabLayoutMediator
import dagger.android.AndroidInjection
import com.callscreen.app.R
import com.callscreen.app.common.base.QkThemedActivity
import com.callscreen.app.databinding.ScreeningActivityBinding

class ScreeningActivity : QkThemedActivity() {

    private lateinit var binding: ScreeningActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = ScreeningActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.screening_title)

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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
