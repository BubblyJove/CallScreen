package com.callscreen.app.injection.android

import com.callscreen.app.feature.screening.ActiveChallengesFragment
import com.callscreen.app.feature.screening.PendingMessagesFragment
import com.callscreen.app.feature.screening.WhitelistedContactsFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class FragmentBuilderModule {

    @ContributesAndroidInjector
    abstract fun bindPendingMessagesFragment(): PendingMessagesFragment

    @ContributesAndroidInjector
    abstract fun bindWhitelistedContactsFragment(): WhitelistedContactsFragment

    @ContributesAndroidInjector
    abstract fun bindActiveChallengesFragment(): ActiveChallengesFragment

}
