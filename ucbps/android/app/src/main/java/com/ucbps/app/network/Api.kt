package com.ucbps.app.network

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal blocking HTTP client. Always call from a background thread. */
object Api {
    class ApiException(val code: Int, message: String) : Exception(message)

    private fun call(base: String, method: String, path: String, body: JSONObject? = null, token: String? = null): String {
        val c = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 45_000          // free hosts need time to wake up
            c.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw ApiException(code, "HTTP $code")
            return text
        } finally {
            c.disconnect()
        }
    }

    fun auth(base: String, register: Boolean, email: String, password: String, country: String, consent: Boolean): String {
        val body = JSONObject().put("email", email).put("password", password)
        if (register) body.put("country", country).put("reportingConsent", consent)
        val res = call(base, "POST", if (register) "/api/v1/auth/register" else "/api/v1/auth/login", body)
        return JSONObject(res).getString("token")
    }

    fun report(base: String, token: String, hash: String, country: String, type: String, risk: Double) {
        val body = JSONObject().put("numberHash", hash).put("country", country).put("type", type).put("riskScore", risk)
        call(base, "POST", "/api/v1/reports", body, token)
    }

    fun setConsent(base: String, token: String, granted: Boolean) {
        call(base, "PUT", "/api/v1/me/consent", JSONObject().put("granted", granted), token)
    }

    fun feed(base: String, token: String): Set<String> {
        val arr = JSONArray(call(base, "GET", "/api/v1/intel/feed", null, token))
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("numberHash") }.toSet()
    }

    fun deleteAccount(base: String, token: String) {
        call(base, "DELETE", "/api/v1/me/data", null, token)
    }
}
