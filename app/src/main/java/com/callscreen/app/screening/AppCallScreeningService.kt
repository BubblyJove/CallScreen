package com.callscreen.app.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import com.callscreen.app.interactor.CheckNumberTrusted
import com.callscreen.app.interactor.SendMathChallenge
import com.callscreen.app.model.PendingScreenedMessage
import com.callscreen.app.repository.ScreeningRepository
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

    private val disposables = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val number = handle?.schemeSpecificPart

        if (number.isNullOrBlank()) {
            Timber.w("No caller number — letting call through")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        Timber.d("Screening call from $number")

        disposables += checkNumberTrusted.buildObservable(CheckNumberTrusted.Params(number))
            .subscribeOn(Schedulers.io())
            .timeout(TRUST_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .firstOrError()
            .subscribe({ trusted ->
                if (trusted) {
                    Timber.d("Trusted — allowing call from $number")
                    respondToCall(callDetails, CallResponse.Builder().build())
                } else {
                    Timber.d("Untrusted — rejecting call from $number")
                    val response = CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSilenceCall(true)
                        .setSkipNotification(false)
                        .build()
                    respondToCall(callDetails, response)

                    // Insert pending message record for the screened call
                    screeningRepository.insertPendingMessage(
                        phoneNumber = number,
                        body = "[Screened call]",
                        type = PendingScreenedMessage.MessageType.CALL
                    )

                    // Send math challenge
                    sendMathChallenge.execute(SendMathChallenge.Params(number)) {
                        Timber.d("Challenge sent for $number")
                    }
                }
            }, { error ->
                Timber.e(error, "Error screening call from $number — fail-open")
                respondToCall(callDetails, CallResponse.Builder().build())
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    companion object {
        private const val TRUST_CHECK_TIMEOUT_MS = 5_000L
    }
}
