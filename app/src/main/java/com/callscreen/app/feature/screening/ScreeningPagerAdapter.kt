package com.callscreen.app.feature.screening

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ScreeningPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> PendingMessagesFragment()
        1 -> WhitelistedContactsFragment()
        2 -> ActiveChallengesFragment()
        else -> throw IllegalArgumentException("Invalid position: $position")
    }
}
