package com.ucbps.app.privacy

import com.ucbps.app.BuildConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Only keyed hashes of numbers ever leave the device. NOTE: phone-number space is small, so a keyed hash is
 * pseudonymous, not anonymous. That is why uploads are opt-in, minimised and erasable.
 */
object Privacy {
    fun normalize(n: String): String =
        if (n.any { it.isLetter() }) n.trim().uppercase() else n.filter { it.isDigit() || it == '+' }

    fun hash(number: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(BuildConfig.PEPPER.toByteArray(), "HmacSHA256"))
        return mac.doFinal(normalize(number).toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
