package com.ucbps.app

import com.ucbps.app.detection.Action
import com.ucbps.app.detection.Decision
import com.ucbps.app.detection.Detector
import com.ucbps.app.detection.SmsClassifier
import org.junit.Assert.*
import org.junit.Test

class DetectorTest {
    private val clock = 1_000_000L

    @Test fun otpBombingFromManySendersIsBlocked() {
        val d = Detector(now = { clock })
        var last: Decision? = null
        for (round in 1..2) for (s in 1..8) last = d.onSms("SVC$s", "Your OTP is 48291$round. Do not share it.")
        assertEquals(Action.BLOCK, last!!.action)
        assertEquals("OTP_FLOOD", last.category)
    }

    @Test fun legitimateOtpRetriesWhileExpectingAreAllowed() {
        val d = Detector(now = { clock }, expectingOtp = { true })
        var last: Decision? = null
        repeat(16) { last = d.onSms("BANK", "Your OTP is 123456") }
        assertEquals(Action.ALLOW, last!!.action)
    }

    @Test fun sameRetriesWithoutExpectingOnlyAlert() {
        val d = Detector(now = { clock })
        var last: Decision? = null
        repeat(16) { last = d.onSms("BANK", "Your OTP is 123456") }
        assertNotEquals(Action.ALLOW, last!!.action)
        assertNotEquals(Action.BLOCK, last!!.action)
    }

    @Test fun frequencyAloneNeverBlocksSms() {
        val d = Detector(now = { clock })
        var last: Decision? = null
        repeat(20) { last = d.onSms("SHOP", "Your order has shipped") }
        assertNotEquals(Action.BLOCK, last!!.action)
    }

    @Test fun contactsAreNeverFlagged() {
        val d = Detector(now = { clock }, isTrusted = { true })
        repeat(30) { assertEquals(Action.ALLOW, d.onSms("MOM", "hi").action) }
        repeat(30) { assertEquals(Action.ALLOW, d.onCall("+911234567890").action) }
    }

    @Test fun repeatedCallsFromUnknownNumberAreRejected() {
        val d = Detector(now = { clock })
        assertEquals(Action.ALLOW, d.onCall("+15550100001").action)
        var last: Decision? = null
        repeat(4) { last = d.onCall("+15550100001") }
        assertEquals(Action.BLOCK, last!!.action)
    }

    @Test fun blocklistAndIntelFeedAreHonoured() {
        val d = Detector(now = { clock }, isBlocklisted = { it == "+1999" }, isIntelFlagged = { it == "+1888" })
        assertEquals(Action.BLOCK, d.onCall("+1999").action)
        assertEquals(Action.BLOCK, d.onCall("+1888").action)
    }

    @Test fun classifierSeparatesOtpFromSpam() {
        assertTrue(SmsClassifier.isOtp("Your OTP is 123456"))
        assertTrue(SmsClassifier.spamScore("URGENT! You are a winner, claim your free prize http://bit.ly/x") >= 0.5)
        assertTrue(SmsClassifier.spamScore("Lunch at 1?") < 0.2)
    }
}
