package com.callscreen.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS handling — for MVP, just accept all MMS without screening.
        // Full MMS screening would require parsing the PDU which is complex.
        // The call and SMS screening covers the primary use case.
    }
}
