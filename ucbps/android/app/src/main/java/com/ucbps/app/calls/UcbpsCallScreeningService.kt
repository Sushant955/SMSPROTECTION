package com.ucbps.app.calls

import android.telecom.Call
import android.telecom.CallScreeningService
import com.ucbps.app.Core
import com.ucbps.app.detection.Action

/** Official Android call-screening API. The user must grant the "call screening app" role to UCBPS. */
class UcbpsCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val response = CallResponse.Builder()
        if (details.callDirection == Call.Details.DIRECTION_INCOMING) {
            val number = details.handle?.schemeSpecificPart ?: ""
            when (Core.get(this).handleCall(number).action) {
                Action.BLOCK -> response.setDisallowCall(true).setRejectCall(true).setSkipNotification(false)
                Action.SILENCE -> response.setSilenceCall(true)
                else -> Unit
            }
        }
        respondToCall(details, response.build())
    }
}
