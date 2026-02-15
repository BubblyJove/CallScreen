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
package com.callscreen.app.injection

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkerFactory
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import com.callscreen.app.blocking.BlockingClient
import com.callscreen.app.blocking.BlockingManager
import com.callscreen.app.common.ViewModelFactory
import com.callscreen.app.common.util.BillingManagerImpl
import com.callscreen.app.common.util.NotificationManagerImpl
import com.callscreen.app.common.util.ShortcutManagerImpl
import com.callscreen.app.feature.conversationinfo.injection.ConversationInfoComponent
import com.callscreen.app.feature.themepicker.injection.ThemePickerComponent
import com.callscreen.app.listener.ContactAddedListener
import com.callscreen.app.listener.ContactAddedListenerImpl
import com.callscreen.app.manager.ActiveConversationManager
import com.callscreen.app.manager.ActiveConversationManagerImpl
import com.callscreen.app.manager.AlarmManager
import com.callscreen.app.manager.AlarmManagerImpl
import com.callscreen.app.manager.BillingManager
import com.callscreen.app.manager.ChangelogManager
import com.callscreen.app.manager.ChangelogManagerImpl
import com.callscreen.app.manager.KeyManager
import com.callscreen.app.manager.KeyManagerImpl
import com.callscreen.app.manager.NotificationManager
import com.callscreen.app.manager.PermissionManager
import com.callscreen.app.manager.PermissionManagerImpl
import com.callscreen.app.manager.RatingManager
import com.callscreen.app.manager.ReferralManager
import com.callscreen.app.manager.ReferralManagerImpl
import com.callscreen.app.manager.ShortcutManager
import com.callscreen.app.manager.WidgetManager
import com.callscreen.app.manager.WidgetManagerImpl
import com.callscreen.app.mapper.CursorToContact
import com.callscreen.app.mapper.CursorToContactGroup
import com.callscreen.app.mapper.CursorToContactGroupImpl
import com.callscreen.app.mapper.CursorToContactGroupMember
import com.callscreen.app.mapper.CursorToContactGroupMemberImpl
import com.callscreen.app.mapper.CursorToContactImpl
import com.callscreen.app.mapper.CursorToConversation
import com.callscreen.app.mapper.CursorToConversationImpl
import com.callscreen.app.mapper.CursorToMessage
import com.callscreen.app.mapper.CursorToMessageImpl
import com.callscreen.app.mapper.CursorToPart
import com.callscreen.app.mapper.CursorToPartImpl
import com.callscreen.app.mapper.CursorToRecipient
import com.callscreen.app.mapper.CursorToRecipientImpl
import com.callscreen.app.mapper.RatingManagerImpl
import com.callscreen.app.repository.BackupRepository
import com.callscreen.app.repository.BackupRepositoryImpl
import com.callscreen.app.repository.CryptoRepository
import com.callscreen.app.repository.CryptoRepositoryImpl
import com.callscreen.app.repository.ScreeningRepository
import com.callscreen.app.repository.ScreeningRepositoryImpl
import com.callscreen.app.repository.BlockingRepository
import com.callscreen.app.repository.BlockingRepositoryImpl
import com.callscreen.app.repository.ContactRepository
import com.callscreen.app.repository.ContactRepositoryImpl
import com.callscreen.app.repository.ConversationRepository
import com.callscreen.app.repository.ConversationRepositoryImpl
import com.callscreen.app.repository.EmojiReactionRepository
import com.callscreen.app.repository.EmojiReactionRepositoryImpl
import com.callscreen.app.repository.MessageContentFilterRepository
import com.callscreen.app.repository.MessageContentFilterRepositoryImpl
import com.callscreen.app.repository.MessageRepository
import com.callscreen.app.repository.MessageRepositoryImpl
import com.callscreen.app.repository.ScheduledMessageRepository
import com.callscreen.app.repository.ScheduledMessageRepositoryImpl
import com.callscreen.app.repository.SyncRepository
import com.callscreen.app.repository.SyncRepositoryImpl
import com.callscreen.app.crypto.AlchemyWebSocketService
import com.callscreen.app.crypto.Web3Service
import com.callscreen.app.worker.InjectionWorkerFactory
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module(subcomponents = [
    ConversationInfoComponent::class,
    ThemePickerComponent::class])
class AppModule(private var application: Application) {

    @Provides
    @Singleton
    fun provideContext(): Context = application

    @Provides
    fun provideContentResolver(context: Context): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun provideRxPreferences(preferences: SharedPreferences): RxSharedPreferences {
        return RxSharedPreferences.create(preferences)
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
    }

    @Provides
    fun provideViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory = factory

    // Listener

    @Provides
    fun provideContactAddedListener(listener: ContactAddedListenerImpl): ContactAddedListener = listener

    // Manager

    @Provides
    fun provideBillingManager(manager: BillingManagerImpl): BillingManager = manager

    @Provides
    fun provideActiveConversationManager(manager: ActiveConversationManagerImpl): ActiveConversationManager = manager

    @Provides
    fun provideAlarmManager(manager: AlarmManagerImpl): AlarmManager = manager

    @Provides
    fun blockingClient(manager: BlockingManager): BlockingClient = manager

    @Provides
    fun changelogManager(manager: ChangelogManagerImpl): ChangelogManager = manager

    @Provides
    fun provideKeyManager(manager: KeyManagerImpl): KeyManager = manager

    @Provides
    fun provideNotificationsManager(manager: NotificationManagerImpl): NotificationManager = manager

    @Provides
    fun providePermissionsManager(manager: PermissionManagerImpl): PermissionManager = manager

    @Provides
    fun provideRatingManager(manager: RatingManagerImpl): RatingManager = manager

    @Provides
    fun provideShortcutManager(manager: ShortcutManagerImpl): ShortcutManager = manager

    @Provides
    fun provideReferralManager(manager: ReferralManagerImpl): ReferralManager = manager

    @Provides
    fun provideWidgetManager(manager: WidgetManagerImpl): WidgetManager = manager

    // Mapper

    @Provides
    fun provideCursorToContact(mapper: CursorToContactImpl): CursorToContact = mapper

    @Provides
    fun provideCursorToContactGroup(mapper: CursorToContactGroupImpl): CursorToContactGroup = mapper

    @Provides
    fun provideCursorToContactGroupMember(mapper: CursorToContactGroupMemberImpl): CursorToContactGroupMember = mapper

    @Provides
    fun provideCursorToConversation(mapper: CursorToConversationImpl): CursorToConversation = mapper

    @Provides
    fun provideCursorToMessage(mapper: CursorToMessageImpl): CursorToMessage = mapper

    @Provides
    fun provideCursorToPart(mapper: CursorToPartImpl): CursorToPart = mapper

    @Provides
    fun provideCursorToRecipient(mapper: CursorToRecipientImpl): CursorToRecipient = mapper

    // Repository

    @Provides
    fun provideBackupRepository(repository: BackupRepositoryImpl): BackupRepository = repository

    @Provides
    fun provideBlockingRepository(repository: BlockingRepositoryImpl): BlockingRepository = repository

    @Provides
    fun provideMessageContentFilterRepository(repository: MessageContentFilterRepositoryImpl): MessageContentFilterRepository = repository

    @Provides
    fun provideContactRepository(repository: ContactRepositoryImpl): ContactRepository = repository

    @Provides
    fun provideConversationRepository(repository: ConversationRepositoryImpl): ConversationRepository = repository

    @Provides
    fun provideMessageRepository(repository: MessageRepositoryImpl): MessageRepository = repository

    @Provides
    fun provideScheduledMessagesRepository(repository: ScheduledMessageRepositoryImpl): ScheduledMessageRepository = repository

    @Provides
    fun provideSyncRepository(repository: SyncRepositoryImpl): SyncRepository = repository

    @Provides
    fun provideEmojiReactionRepository(repository: EmojiReactionRepositoryImpl): EmojiReactionRepository = repository

    // Screening

    @Provides
    fun provideScreeningRepository(repository: ScreeningRepositoryImpl): ScreeningRepository = repository

    @Provides
    fun provideCryptoRepository(repository: CryptoRepositoryImpl): CryptoRepository = repository

    // Crypto

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideWeb3Service(service: AlchemyWebSocketService): Web3Service = service

    // worker factory
    @Provides
    fun provideWorkerFactory(workerFactory: InjectionWorkerFactory): WorkerFactory = workerFactory
}