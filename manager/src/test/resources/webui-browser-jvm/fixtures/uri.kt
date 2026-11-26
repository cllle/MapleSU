package android.net

class Uri private constructor(private val value: String) {
    private val parsed = java.net.URI(value)
    val scheme: String? get() = parsed.scheme
    val host: String? get() = parsed.host
    val userInfo: String? get() = parsed.userInfo
    val port: Int get() = parsed.port
    val path: String? get() = parsed.path
    override fun toString() = value
    companion object { @JvmStatic fun parse(value: String) = Uri(value) }
}
