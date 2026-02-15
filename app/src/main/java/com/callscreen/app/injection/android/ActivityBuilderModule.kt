/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.callscreen.app.injection.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import com.callscreen.app.feature.backup.BackupActivity
import com.callscreen.app.feature.blocking.BlockingActivity
import com.callscreen.app.feature.compose.ComposeActivity
import com.callscreen.app.feature.debug.DebugActivity
import com.callscreen.app.feature.screening.ScreeningActivity
import com.callscreen.app.feature.settings.crypto.CryptoSettingsActivity
import com.callscreen.app.feature.settings.screening.CallScreeningSettingsActivity
import com.callscreen.app.feature.compose.ComposeActivityModule
import com.callscreen.app.feature.contacts.ContactsActivity
import com.callscreen.app.feature.contacts.ContactsActivityModule
import com.callscreen.app.feature.conversationinfo.ConversationInfoActivity
import com.callscreen.app.feature.gallery.GalleryActivity
import com.callscreen.app.feature.gallery.GalleryActivityModule
import com.callscreen.app.feature.main.MainActivity
import com.callscreen.app.feature.main.MainActivityModule
import com.callscreen.app.feature.messageutils.MessageUtilsActivity
import com.callscreen.app.feature.notificationprefs.NotificationPrefsActivity
import com.callscreen.app.feature.notificationprefs.NotificationPrefsActivityModule
import com.callscreen.app.feature.plus.PlusActivity
import com.callscreen.app.feature.plus.PlusActivityModule
import com.callscreen.app.feature.qkreply.QkReplyActivity
import com.callscreen.app.feature.qkreply.QkReplyActivityModule
import com.callscreen.app.feature.scheduled.ScheduledActivity
import com.callscreen.app.feature.scheduled.ScheduledActivityModule
import com.callscreen.app.feature.settings.SettingsActivity
import com.callscreen.app.injection.scope.ActivityScope

@Module
abstract class ActivityBuilderModule {

    @ActivityScope
    @ContributesAndroidInjector(modules = [MainActivityModule::class])
    abstract fun bindMainActivity(): MainActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [PlusActivityModule::class])
    abstract fun bindPlusActivity(): PlusActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindBackupActivity(): BackupActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ComposeActivityModule::class])
    abstract fun bindComposeActivity(): ComposeActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ContactsActivityModule::class])
    abstract fun bindContactsActivity(): ContactsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindConversationInfoActivity(): ConversationInfoActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [GalleryActivityModule::class])
    abstract fun bindGalleryActivity(): GalleryActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [NotificationPrefsActivityModule::class])
    abstract fun bindNotificationPrefsActivity(): NotificationPrefsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [QkReplyActivityModule::class])
    abstract fun bindQkReplyActivity(): QkReplyActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ScheduledActivityModule::class])
    abstract fun bindScheduledActivity(): ScheduledActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindMessageUtilsActivity(): MessageUtilsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindSettingsActivity(): SettingsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindBlockingActivity(): BlockingActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindScreeningActivity(): ScreeningActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindDebugActivity(): DebugActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindCryptoSettingsActivity(): CryptoSettingsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindCallScreeningSettingsActivity(): CallScreeningSettingsActivity

}
