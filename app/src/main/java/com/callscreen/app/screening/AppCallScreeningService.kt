package com.callscreen.app.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import com.callscreen.app.interactor.CheckNumberTrusted
import com.callscreen.app.interactor.SendMathChallenge
import com.callscreen.app.model.PendingScreenedMessage
import com.callscreen.app.repository.ScreeningRepository
import com.callscreen.app.util.Preferences
import dagger.android.AndroidInjection
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Screens incoming calls. Trusted callers ring through; unknown callers are
 * rejected and sent an SMS challenge.
 *
 * Uses Dagger injection for interactors and repositories.
 * Fail-open: on any error, allows the call through.
 */
class AppCallScreeningService : CallScreeningService() {

    @Inject lateinit var checkNumberTrusted: CheckNumberTrusted
    @Inject lateinit var sendMathChallenge: SendMathChallenge
    @Inject lateinit var screeningRepository: ScreeningRepository
    @Inject lateinit var prefs: Preferences

    private val disposables = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart

        if (number.isNullOrBlank()) {
            Timber.w("No caller number — letting call through")
            respondToCall(callDetails, ALLOW_RESPONSE)
            return
        }

        if (!prefs.callScreeningEnabled.get()) {
            Timber.d("Call screening disabled — allowing call from %s", number)
            respondToCall(callDetails, ALLOW_RESPONSE)
            return
        }

        Timber.d("Screening call from %s", number)

        disposables += checkNumberTrusted.buildObservable(CheckNumberTrusted.Params(number))
            .subscribeOn(Schedulers.io())
            .timeout(TRUST_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .firstOrError()
            .subscribe({ trusted ->
                if (trusted) {
                    Timber.d("Trusted — allowing call from %s", number)
                    respondToCall(callDetails, ALLOW_RESPONSE)
                } else {
                    Timber.d("Untrusted — rejecting call from %s", number)
                    // Perf: respond immediately with pre-built response to minimize ring latency
                    respondToCall(callDetails, REJECT_RESPONSE)

                    // Insert pending message record for the screened call
                    screeningRepository.insertPendingMessage(
                        phoneNumber = number,
                        body = SCREENED_CALL_BODY,
                        type = PendingScreenedMessage.MessageType.CALL
                    )

                    // Send math challenge if enabled
                    if (prefs.mathChallengeEnabled.get()) {
                        sendMathChallenge.execute(SendMathChallenge.Params(number)) {
                            Timber.d("Challenge sent for %s", number)
                        }
                    }
                }
            }, { error ->
                Timber.e(error, "Error screening call from %s — fail-open", number)
                respondToCall(callDetails, ALLOW_RESPONSE)
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    companion object {
        private const val TRUST_CHECK_TIMEOUT_MS = 5_000L
        private const val SCREENED_CALL_BODY = "[Screened call]"

        // Perf: pre-build immutable CallResponse objects to avoid allocation per call
        private val ALLOW_RESPONSE: CallResponse = CallResponse.Builder().build()
        private val REJECT_RESPONSE: CallResponse = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSilenceCall(true)
            .setSkipNotification(false)
            .build()
    }
}
