package com.ucbps.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ucbps.app.data.AppDb
import com.ucbps.app.data.BlockedNumber
import com.ucbps.app.data.Prefs
import com.ucbps.app.data.SecurityEvent
import com.ucbps.app.detection.Action
import com.ucbps.app.detection.Decision
import com.ucbps.app.detection.Detector
import com.ucbps.app.network.Api
import com.ucbps.app.privacy.Privacy
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Process-wide singleton wiring detector, storage, cloud reporting and notifications together. */
class Core private constructor(private val ctx: Context) {
    val prefs = Prefs(ctx)
    val dao = AppDb.get(ctx).dao()
    val exec = Executors.newSingleThreadExecutor()
    private val blocked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val detector = Detector(
        expectingOtp = { prefs.expectingUntil > System.currentTimeMillis() },
        isTrusted = { isContact(it) },
        isBlocklisted = { Privacy.normalize(it) in blocked },
        isIntelFlagged = { Privacy.hash(it) in prefs.intelHashes },
    )

    init {
        exec.execute { blocked.addAll(dao.blockedNow().map { it.number }) }
        createChannel()
    }

    // ---- blocklist (in-memory mirror so the call-screening path never touches the DB)
    fun addBlocked(number: String) {
        val n = Privacy.normalize(number); if (n.isEmpty()) return
        blocked.add(n); exec.execute { dao.addBlocked(BlockedNumber(n, System.currentTimeMillis())) }
    }
    fun removeBlocked(number: String) { blocked.remove(number); exec.execute { dao.removeBlocked(number) } }

    // ---- entry points
    fun handleSms(sender: String, body: String, done: () -> Unit = {}) {
        exec.execute {
            try {
                if (!prefs.protectionOn) return@execute
                val d = detector.onSms(sender, body)
                record("SMS", sender, d, false)
                if (d.action != Action.ALLOW) postAlert("SMS threat: ${d.action}", "$sender – ${d.reason}")
                maybeReport(sender, d)
            } finally { done() }
        }
    }

    /** Called synchronously from CallScreeningService, so it must stay fast and must not touch the DB. */
    fun handleCall(rawNumber: String): Decision {
        if (!prefs.protectionOn) return Decision(0.0, Action.ALLOW, "Protection is off")
        val number = rawNumber.ifBlank { "private" }
        var d = detector.onCall(number)
        if (prefs.emergency && !d.trusted && d.action != Action.BLOCK) {
            d = Decision(maxOf(d.risk, 0.5), Action.SILENCE, "Emergency mode: unknown caller silenced", d.category)
        }
        exec.execute {
            record("CALL", number, d, false)
            if (d.action != Action.ALLOW) postAlert("Call ${d.action.name.lowercase()}", "$number – ${d.reason}")
            maybeReport(number, d)
        }
        return d
    }

    fun record(kind: String, source: String, d: Decision, simulated: Boolean) {
        dao.insertEvent(SecurityEvent(ts = System.currentTimeMillis(), kind = kind, source = source,
            risk = d.risk, action = d.action.name, reason = d.reason, simulated = simulated))
    }

    // ---- cloud (opt-in)
    private fun maybeReport(source: String, d: Decision) {
        if (!prefs.shareReports || prefs.token.isEmpty() || d.action != Action.BLOCK || d.category == "NONE") return
        try {
            val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "ZZ"
            Api.report(prefs.apiBase, prefs.token, Privacy.hash(source), country.uppercase(), d.category, d.risk.coerceIn(0.0, 1.0))
        } catch (_: Exception) { /* best effort; never block protection on network */ }
    }

    // ---- helpers
    private fun isContact(number: String): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) { false }
    }

    private fun createChannel() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("threats", "Threat alerts", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun postAlert(title: String, text: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(ctx, "threats")
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(text)
            .setAutoCancel(true).build()
        nm.notify(System.nanoTime().toInt(), n)
    }

    companion object {
        @Volatile private var inst: Core? = null
        fun get(ctx: Context): Core = inst ?: synchronized(this) {
            inst ?: Core(ctx.applicationContext).also { inst = it }
        }
    }
}
