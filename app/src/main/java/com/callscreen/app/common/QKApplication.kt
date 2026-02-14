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
package com.callscreen.app.common

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.uber.rxdogtag.RxDogTag
import com.uber.rxdogtag.autodispose.AutoDisposeConfigurer
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import com.callscreen.app.BuildConfig
import com.callscreen.app.R
import com.callscreen.app.common.util.FileLoggingTree
import com.callscreen.app.injection.AppComponentManager
import com.callscreen.app.injection.appComponent
import com.callscreen.app.interactor.SpeakThreads
import com.callscreen.app.manager.BillingManager
import com.callscreen.app.manager.ReferralManager
import com.callscreen.app.migration.QkMigration
import com.callscreen.app.migration.QkRealmMigration
import com.callscreen.app.util.NightModeManager
import com.callscreen.app.worker.HousekeepingWorker
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class QKApplication : Application(), HasAndroidInjector {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Inject these so that they are forced to initialize
     */
    @Suppress("unused")
    @Inject lateinit var qkMigration: QkMigration

    @Inject lateinit var androidInjector: DispatchingAndroidInjector<Any>
    @Inject lateinit var billingManager: BillingManager
    @Inject lateinit var fileLoggingTree: FileLoggingTree
    @Inject lateinit var nightModeManager: NightModeManager
    @Inject lateinit var realmMigration: QkRealmMigration
    @Inject lateinit var referralManager: ReferralManager
    @Inject lateinit var workerFactory: WorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Perf: DI and Realm init are critical-path — keep on main thread
        AppComponentManager.init(this)
        appComponent.inject(this)

        Realm.init(this)
        Realm.setDefaultConfiguration(RealmConfiguration.Builder()
                // Perf: compactOnLaunch adds startup latency; only compact conditionally
                .compactOnLaunch { totalBytes, usedBytes ->
                    // Only compact if file is over 50MB and less than 50% used
                    totalBytes > 50L * 1024 * 1024 && usedBytes.toDouble() / totalBytes < 0.5
                }
                .migration(realmMigration)
                .schemaVersion(QkRealmMigration.SCHEMA_VERSION)
                .deleteRealmIfMigrationNeeded()
                .build())

        // Perf: night mode must apply before any UI draws
        nightModeManager.updateCurrentTheme()

        // Perf: configure timber — skip DebugTree in release (R8 strips d/v calls anyway)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree(), fileLoggingTree)
        } else {
            Timber.plant(fileLoggingTree)
        }

        // Perf: init work manager early — required before any worker enqueue
        WorkManager.initialize(
            this,
            Configuration.Builder().setWorkerFactory(workerFactory).build()
        )

        // Perf: defer non-critical initialization to background threads
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Migration can run off main thread
                qkMigration.performMigration()

                referralManager.trackReferrer()
                billingManager.checkForPurchases()
                billingManager.queryProducts()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error during app startup background tasks")
            }
        }

        // Perf: defer EmojiCompat init — not needed until first text render
        applicationScope.launch(Dispatchers.Default) {
            EmojiCompat.init(BundledEmojiCompatConfig(this@QKApplication)
                // Perf: disable replace-all strategy to avoid processing every TextView
                .setReplaceAll(false)
                .registerInitCallback(object: EmojiCompat.InitCallback() {
                    override fun onInitialized() {
                        super.onInitialized()
                        Timber.v("bundled emojicompat initialized")
                    }

                    override fun onFailed(throwable: Throwable?) {
                        super.onFailed(throwable)
                        Timber.e("bundled emojicompat initialization failed")
                    }
                })
            )
        }

        // Perf: defer RxDogTag — only needed when an Rx error occurs (debug tool)
        applicationScope.launch(Dispatchers.Default) {
            RxDogTag.builder()
                    .configureWith(AutoDisposeConfigurer::configure)
                    .install()
        }

        // Perf: defer SpeakThreads string load — only needed for TTS feature
        SpeakThreads.setNoMessagesString(getString(R.string.speak_no_messages))

        // Perf: defer housekeeping registration — periodic work, not time-sensitive
        applicationScope.launch(Dispatchers.IO) {
            HousekeepingWorker.register(applicationContext)
        }
    }

    override fun androidInjector(): AndroidInjector<Any> {
        return androidInjector
    }

}
