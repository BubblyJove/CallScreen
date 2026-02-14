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
package com.callscreen.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.android.AndroidInjection
import com.callscreen.app.crypto.CryptoPriceOracle
import com.callscreen.app.interactor.CheckNumberTrusted
import com.callscreen.app.interactor.SendCryptoChallenge
import com.callscreen.app.interactor.SendMathChallenge
import com.callscreen.app.interactor.ValidateChallengeResponse
import com.callscreen.app.model.PendingScreenedMessage
import com.callscreen.app.repository.CryptoRepository
import com.callscreen.app.repository.MessageRepository
import com.callscreen.app.repository.ScreeningRepository
import com.callscreen.app.worker.ReceiveSmsWorker
import com.callscreen.app.worker.ReceiveSmsWorker.Companion.INPUT_DATA_KEY_MESSAGE_ID
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class SmsReceivedReceiver : BroadcastReceiver() {
    @Inject lateinit var messageRepo: MessageRepository
    @Inject lateinit var screeningRepository: ScreeningRepository
    @Inject lateinit var cryptoRepository: CryptoRepository
    @Inject lateinit var checkNumberTrusted: CheckNumberTrusted
    @Inject lateinit var sendMathChallenge: SendMathChallenge
    @Inject lateinit var sendCryptoChallenge: SendCryptoChallenge
    @Inject lateinit var cryptoPriceOracle: CryptoPriceOracle
    @Inject lateinit var validateChallengeResponse: ValidateChallengeResponse

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        val pendingResult = goAsync()

        Sms.Intents.getMessagesFromIntent(intent)?.takeIf { it.isNotEmpty() }?.let { messages ->
            Single.just(messages)
                .observeOn(Schedulers.io())
                .map {
                    val address = messages[0].displayOriginatingAddress ?: ""
                    if (address.isBlank()) {
                        Timber.w("SMS received with blank address, skipping screening")
                        return@map 0L
                    }
                    // Perf: use StringBuilder to concatenate multi-part SMS bodies
                    // instead of reduce() which creates intermediate String objects
                    val body = if (messages.size == 1) {
                        messages[0].displayMessageBody ?: ""
                    } else {
                        val sb = StringBuilder(messages.size * 160)
                        for (msg in messages) {
                            msg.displayMessageBody?.let { sb.append(it) }
                        }
                        sb.toString()
                    }
                    val subId = intent.extras?.getInt("subscription", -1) ?: -1
                    val timestamp = messages[0].timestampMillis

                    Timber.v("SMS received from $address: ${body.take(50)}")

                    try {
                        // Step 1: Check if this is a challenge response
                        val challenge = screeningRepository.getChallengeForNumber(address)
                        if (challenge != null && !challenge.isExpired()) {
                            Timber.d("Potential challenge response from $address")
                            // Perf: all branches insert normally — validate then insert once
                            validateChallengeResponse
                                .buildObservable(ValidateChallengeResponse.Params(address, body))
                                .blockingFirst()
                            return@map insertAndEnqueue(context, subId, address, body, timestamp)
                        }

                        // Step 2: Check if sender is trusted
                        val trusted = checkNumberTrusted
                            .buildObservable(CheckNumberTrusted.Params(address))
                            .blockingFirst()

                        if (trusted) {
                            Timber.d("Trusted sender $address — inserting to Quik normally")
                            return@map insertAndEnqueue(context, subId, address, body, timestamp)
                        }

                        // Step 3: Untrusted — hold message and send challenge
                        Timber.d("Untrusted sender $address — holding message, sending challenge")
                        screeningRepository.insertPendingMessage(
                            phoneNumber = address,
                            body = body,
                            type = PendingScreenedMessage.MessageType.SMS
                        )

                        // Don't insert to Quik DB — screened messages stay hidden
                        // until the sender passes verification

                        // Send challenge (don't fail if this errors)
                        try {
                            if (cryptoRepository.isCryptoChallengeEnabled()) {
                                val ethPrice = cryptoPriceOracle.getEthPriceUsd()
                                if (ethPrice != null) {
                                    sendCryptoChallenge.buildObservable(
                                        SendCryptoChallenge.Params(address, ethPrice)
                                    ).blockingFirst()
                                } else {
                                    // Fallback to math if price fetch fails
                                    sendMathChallenge.buildObservable(SendMathChallenge.Params(address)).blockingFirst()
                                }
                            } else {
                                sendMathChallenge.buildObservable(SendMathChallenge.Params(address)).blockingFirst()
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to send challenge to $address")
                        }

                        0L
                    } catch (e: Exception) {
                        // Fail-open: on any screening error, insert to Quik normally
                        Timber.w(e, "Screening error for $address — fail-open, inserting normally")
                        insertAndEnqueue(context, subId, address, body, timestamp)
                    }
                }
                .subscribe({ messageId ->
                    // Enqueue worker for the message if not already done
                    if (messageId > 0) {
                        WorkManager.getInstance(context).enqueue(
                            OneTimeWorkRequestBuilder<ReceiveSmsWorker>()
                                .setInputData(workDataOf(INPUT_DATA_KEY_MESSAGE_ID to messageId))
                                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                                .build()
                        )
                    }
                    pendingResult.finish()
                }, { error ->
                    Timber.e(error, "Fatal error in SmsReceivedReceiver")
                    pendingResult.finish()
                })
        } ?: pendingResult.finish()
    }

    /**
     * Insert message into Quik's DB and return the message ID.
     */
    private fun insertAndEnqueue(
        context: Context,
        subId: Int,
        address: String,
        body: String,
        timestamp: Long
    ): Long {
        return messageRepo.insertReceivedSms(subId, address, body, timestamp).id
    }
}
