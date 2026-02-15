package com.callscreen.app.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.callscreen.app.util.ScreenLog
import com.callscreen.app.util.maskPhone

/**
 * Receives the PendingIntent result from SmsManager.sendTextMessage()
 * so we know whether the SMS was actually accepted by the radio.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra(EXTRA_PHONE) ?: "unknown"
        val masked = maskPhone(phone)
        when (resultCode) {
            Activity.RESULT_OK ->
                ScreenLog.d(TAG, "SMS to $phone: SENT OK (accepted by radio)")
            SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                ScreenLog.e(TAG, "SMS to $masked: GENERIC_FAILURE (check SIM / carrier)")
            SmsManager.RESULT_ERROR_NO_SERVICE ->
                ScreenLog.e(TAG, "SMS to $masked: NO_SERVICE (no cellular signal)")
            SmsManager.RESULT_ERROR_NULL_PDU ->
                ScreenLog.e(TAG, "SMS to $masked: NULL_PDU (system error)")
            SmsManager.RESULT_ERROR_RADIO_OFF ->
                ScreenLog.e(TAG, "SMS to $masked: RADIO_OFF (airplane mode?)")
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED ->
                ScreenLog.e(TAG, "SMS to $masked: SHORT_CODE_NOT_ALLOWED")
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED ->
                ScreenLog.e(TAG, "SMS to $masked: SHORT_CODE_NEVER_ALLOWED")
            else ->
                ScreenLog.e(TAG, "SMS to $masked: UNKNOWN result code $resultCode")
        }
    }

    companion object {
        const val TAG = "SmsSent"
        const val ACTION = "com.callscreen.app.SMS_SENT"
        const val EXTRA_PHONE = "phone"
    }
}
