package com.ucbps.app.ui

import com.ucbps.app.Core
import com.ucbps.app.detection.Decision
import com.ucbps.app.detection.Detector

/**
 * Safe, local attack simulator. Feeds SYNTHETIC events into a throw-away Detector with a fake clock.
 * It never sends SMS, never places calls and never touches the real detector windows.
 */
object Simulator {
    val scenarios = listOf("OTP bombing", "Legitimate OTP retries", "Call flood", "Phishing SMS")

    fun run(core: Core, scenario: String): Decision {
        val clock = 1_000_000L
        val expecting = scenario == "Legitimate OTP retries"
        val d = Detector(now = { clock }, expectingOtp = { expecting })
        val result: Decision = when (scenario) {
            "OTP bombing" -> {
                var last = d.onSms("SVC1", "Your OTP is 100001")
                for (round in 1..2) for (s in 1..8) last = d.onSms("SVC$s", "Your OTP is 48291$round. Do not share it.")
                last
            }
            "Legitimate OTP retries" -> {
                var last = d.onSms("BANK", "Your OTP is 123456")
                repeat(15) { last = d.onSms("BANK", "Your OTP is 123456") }
                last
            }
            "Call flood" -> {
                var last = d.onCall("+15550100001")
                repeat(5) { last = d.onCall("+15550100001") }
                last
            }
            else -> d.onSms("PROMO", "URGENT! You are a winner, claim your free prize http://bit.ly/x")
        }
        val kind = if (scenario == "Call flood") "CALL" else "SMS"
        core.exec.execute { core.record(kind, "SIMULATION: $scenario", result, true) }
        return result
    }
}
