package com.betteranki.sync

import java.security.MessageDigest
import java.util.Locale

object HashUtils {
    fun normalizeForKey(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b))
        }
        return sb.toString()
    }
}
