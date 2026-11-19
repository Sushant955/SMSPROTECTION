package com.ucbps.app.detection

enum class Action { ALLOW, ALERT, BLOCK, SILENCE }

data class Decision(
    val risk: Double,
    val action: Action,
    val reason: String,
    val category: String = "NONE",
    val trusted: Boolean = false,
)

private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

/** Pure-Kotlin text heuristics (no Android deps, unit-testable). */
object SmsClassifier {
    private val otpWords = Regex("(?i)\\b(otp|one[- ]time|verification|security code|passcode|login code)\\b")
    private val digits = Regex("\\b\\d{4,8}\\b")
    private val codeWords = Regex("(?i)\\b(code|pin)\\b")
    private val link = Regex("(?i)https?://|bit\\.ly|tinyurl|\\.xyz|\\.top")
    private val spamPhrases = listOf(
        "urgent", "winner", "won", "claim", "free", "prize", "lottery", "kyc", "blocked",
        "act now", "congratulations", "click", "loan approved",
    ).map { Regex("\\b" + Regex.escape(it) + "\\b") }

    fun isOtp(body: String) = otpWords.containsMatchIn(body) || (digits.containsMatchIn(body) && codeWords.containsMatchIn(body))

    fun spamScore(body: String): Double {
        val b = body.lowercase()
        val hits = spamPhrases.count { it.containsMatchIn(b) }
        return clamp01(0.3 * hits + if (link.containsMatchIn(body)) 0.2 else 0.0)
    }
}

/**
 * On-device detection engine. Sliding windows + several independent signals.
 * Frequency alone never produces a BLOCK for SMS; a BLOCK for calls is a temporary rejection while a flood is
 * in progress (it is NOT added to the permanent blocklist automatically).
 */
class Detector(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val expectingOtp: () -> Boolean = { false },
    private val isTrusted: (String) -> Boolean = { false },
    private val isBlocklisted: (String) -> Boolean = { false },
    private val isIntelFlagged: (String) -> Boolean = { false },
) {
    private class Sms(val ts: Long, val sender: String, val otp: Boolean)
    private class Call(val ts: Long, val number: String)

    private val sms = ArrayDeque<Sms>()
    private val calls = ArrayDeque<Call>()
    private val smsWindowMs = 5 * 60_000L
    private val callWindowMs = 10 * 60_000L

    @Synchronized
    fun onSms(sender: String, body: String): Decision {
        val t = now()
        while (sms.isNotEmpty() && t - sms.first().ts > smsWindowMs) sms.removeFirst()
        if (isTrusted(sender)) return Decision(0.0, Action.ALLOW, "Known contact", trusted = true)

        val otp = SmsClassifier.isOtp(body)
        val spam = SmsClassifier.spamScore(body)
        sms.addLast(Sms(t, sender, otp))

        if (isBlocklisted(sender)) return Decision(1.0, Action.BLOCK, "Sender is on your blocklist", "SPAM_SMS")
        if (isIntelFlagged(sender)) return Decision(0.85, Action.BLOCK, "Reported by several independent users", "SPAM_SMS")

        val total = sms.size
        val fromSender = sms.count { it.sender == sender }
        val otpSenders = sms.filter { it.otp }.map { it.sender }.toSet().size

        val burst = clamp01((total - 3) / 12.0)          // 15+ messages in 5 min saturates
        val diversity = clamp01((otpSenders - 1) / 5.0)  // 6+ different OTP senders saturates
        val repeat = clamp01((fromSender - 3) / 10.0)    // 13+ from one sender saturates
        val content = spam

        var risk = 0.3 * burst + 0.3 * diversity + 0.15 * repeat + 0.25 * content
        if (content >= 0.5) risk = maxOf(risk, 0.4)
        if (expectingOtp() && otpSenders <= 1) risk = minOf(risk, 0.3) // user asked for a code: retries are normal

        val signals = listOf(burst, diversity, repeat, content).count { it >= 0.5 }
        val nonFrequency = diversity >= 0.5 || content >= 0.5
        val category = when {
            diversity >= 0.5 -> "OTP_FLOOD"
            content >= 0.5 -> "SPAM_SMS"
            else -> "SMS_FLOOD"
        }
        val reason = when (category) {
            "OTP_FLOOD" -> "OTP codes from $otpSenders different senders within 5 minutes"
            "SPAM_SMS" -> "Message looks like phishing or spam"
            else -> "$total messages within 5 minutes"
        }
        val action = when {
            risk >= 0.55 && signals >= 2 && nonFrequency -> Action.BLOCK
            risk >= 0.35 && signals >= 1 -> Action.ALERT
            else -> Action.ALLOW
        }
        return Decision(risk, action, if (action == Action.ALLOW) "Normal activity" else reason, if (action == Action.ALLOW) "NONE" else category)
    }

    @Synchronized
    fun onCall(number: String): Decision {
        val t = now()
        while (calls.isNotEmpty() && t - calls.first().ts > callWindowMs) calls.removeFirst()
        if (isTrusted(number)) return Decision(0.0, Action.ALLOW, "Known contact", trusted = true)
        if (isBlocklisted(number)) return Decision(1.0, Action.BLOCK, "Number is on your blocklist", "CALL_FLOOD")

        calls.addLast(Call(t, number))
        if (isIntelFlagged(number)) return Decision(0.85, Action.BLOCK, "Reported by several independent users", "CALL_FLOOD")

        val fromNumber = calls.count { it.number == number }
        val distinct = calls.map { it.number }.toSet().size
        val repeat = clamp01((fromNumber - 1) / 4.0)   // 5 calls from one number in 10 min saturates
        val spread = clamp01((distinct - 2) / 6.0)     // 8 different unknown callers saturates
        val rate = clamp01((calls.size - 2) / 10.0)
        val risk = 0.5 * repeat + 0.3 * spread + 0.2 * rate
        val action = when {
            risk >= 0.5 -> Action.BLOCK
            risk >= 0.35 -> Action.ALERT
            else -> Action.ALLOW
        }
        val reason = if (fromNumber >= 3) "$fromNumber calls from the same number in 10 minutes" else "$distinct different unknown callers in 10 minutes"
        return Decision(risk, action, if (action == Action.ALLOW) "Normal activity" else reason, if (action == Action.ALLOW) "NONE" else "CALL_FLOOD")
    }
}
