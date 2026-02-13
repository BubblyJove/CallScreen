package com.callscreen.app.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager

class HeadlessSmsSendService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val uri = intent.data
            if (uri != null) {
                val number = uri.schemeSpecificPart
                val message = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!number.isNullOrBlank() && !message.isNullOrBlank()) {
                    try {
                        val smsManager = getSystemService(SmsManager::class.java)
                        smsManager.sendTextMessage(number, null, message, null, null)
                    } catch (e: Exception) {
                        android.util.Log.e("HeadlessSmsSend", "Failed to send", e)
                    }
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
