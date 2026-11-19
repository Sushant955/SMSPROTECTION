package com.ucbps.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ucbps.app.Core

/**
 * Analyses incoming SMS on the device. Message text never leaves the phone.
 * Android only lets the *default* SMS app hide or delete messages, so UCBPS alerts and flags rather than suppresses.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = parts.firstOrNull()?.originatingAddress ?: return
        val body = parts.joinToString("") { it.messageBody ?: "" }
        val pending = goAsync()
        Core.get(context).handleSms(sender, body) { pending.finish() }
    }
}
