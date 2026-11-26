package dev.maplesu.manager.liteweb

import android.content.Context
import java.security.MessageDigest

object LiteWebProxyBase {
    var url: String? = null
    var calls = 0
    fun current(context: Context): String? { calls++; return url }
}
object LiteWebToken {
    fun isValid(token: String?): Boolean = token != null && token.matches(Regex("[0-9a-f]{64}"))
    fun matches(expected: String?, provided: String?): Boolean = expected != null && provided != null &&
        MessageDigest.isEqual(expected.toByteArray(), provided.toByteArray())
}
