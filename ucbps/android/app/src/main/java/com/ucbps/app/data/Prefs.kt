package com.ucbps.app.data

import android.content.Context
import com.ucbps.app.BuildConfig

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ucbps", Context.MODE_PRIVATE)

    var protectionOn: Boolean
        get() = sp.getBoolean("protectionOn", true)
        set(v) = sp.edit().putBoolean("protectionOn", v).apply()
    var emergency: Boolean
        get() = sp.getBoolean("emergency", false)
        set(v) = sp.edit().putBoolean("emergency", v).apply()
    var guest: Boolean
        get() = sp.getBoolean("guest", false)
        set(v) = sp.edit().putBoolean("guest", v).apply()
    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v).apply()
    var apiBase: String
        get() = sp.getString("apiBase", BuildConfig.DEFAULT_API) ?: BuildConfig.DEFAULT_API
        set(v) = sp.edit().putString("apiBase", v.trim().trimEnd('/')).apply()
    var shareReports: Boolean
        get() = sp.getBoolean("shareReports", false)
        set(v) = sp.edit().putBoolean("shareReports", v).apply()
    var expectingUntil: Long
        get() = sp.getLong("expectingUntil", 0L)
        set(v) = sp.edit().putLong("expectingUntil", v).apply()
    var intelHashes: Set<String>
        get() = sp.getStringSet("intelHashes", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("intelHashes", v).apply()

    fun logout() { token = ""; guest = false }
}
