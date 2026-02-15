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
import com.callscreen.app.feature.widget.WidgetProvider
import com.callscreen.app.injection.scope.ActivityScope
import com.callscreen.app.receiver.BlockThreadReceiver
import com.callscreen.app.receiver.BootReceiver
import com.callscreen.app.receiver.DefaultSmsChangedReceiver
import com.callscreen.app.receiver.DeleteMessagesReceiver
import com.callscreen.app.receiver.MmsReceivedReceiver
import com.callscreen.app.receiver.MmsWapPushReceiver
import com.callscreen.app.receiver.NightModeReceiver
import com.callscreen.app.receiver.RemoteMessagingReceiver
import com.callscreen.app.receiver.SendScheduledMessageReceiver
import com.callscreen.app.receiver.MessageDeliveredReceiver
import com.callscreen.app.receiver.SmsProviderChangedReceiver
import com.callscreen.app.receiver.SmsReceivedReceiver
import com.callscreen.app.receiver.MessageMarkReceiver
import com.callscreen.app.receiver.MessageSentReceiver
import com.callscreen.app.receiver.ResendMessageReceiver
import com.callscreen.app.receiver.SendDelayedMessageReceiver
import com.callscreen.app.receiver.SpeakThreadsReceiver
import com.callscreen.app.receiver.StartActivityFromWidgetReceiver

@Module
abstract class BroadcastReceiverBuilderModule {

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindBlockThreadReceiver(): BlockThreadReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindBootReceiver(): BootReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindDefaultSmsChangedReceiver(): DefaultSmsChangedReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindDeleteMessagesReceiver(): DeleteMessagesReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindSpeakThreadsReceiver(): SpeakThreadsReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindStartActivityFromWidgetReceiver(): StartActivityFromWidgetReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindMmsReceivedReceiver(): MmsReceivedReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindMmsWapPushReceiver(): MmsWapPushReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindNightModeReceiver(): NightModeReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindRemoteMessagingReceiver(): RemoteMessagingReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindResendMessageReceiver(): ResendMessageReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindSendScheduledMessageReceiver(): SendScheduledMessageReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindSendDelayedMessageReceiver(): SendDelayedMessageReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindMessageDeliveredReceiver(): MessageDeliveredReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindSmsProviderChangedReceiver(): SmsProviderChangedReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindSmsReceivedReceiver(): SmsReceivedReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindMessageSentReceiver(): MessageSentReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindMessageMarkReceiver(): MessageMarkReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindWidgetProvider(): WidgetProvider

}