package dev.maplesu.manager.liteweb

/**
 * "隐藏后 WebUI 链接"预览（**不启动 daemon**）：
 * 确保 `webui.conf`（token + webuiPort）存在；token 不存在则生成一次并持久化（root 0600），
 * 返回完整 URL。只读入口 [LiteWebPreviewCoordinator.previewUrlOrNull] 供 UI 即时显示。
 *
 * 与 activate 使用同一 token/端口（同一 `webui.conf`），因此预览链接即隐藏后实际链接。
 */

data class LiteWebUrlInfo(val url: String, val port: Int)

data class LiteWebPreviewConfig(val token: String, val port: Int) {
    init {
        require(LiteWebToken.isValid(token)) { "LITEWEB_PREVIEW_TOKEN_INVALID" }
        require(port in LiteWebPortPolicy.MIN_PORT..LiteWebPortPolicy.MAX_PORT) { "LITEWEB_PREVIEW_PORT_INVALID" }
    }

    fun url(): String = LiteWebUrls.url(port, token)
}

/** Bind a root-only port change to the config that the manager actually read, without exposing its token. */
internal fun liteWebPortIdentity(port: Int, token: String): String =
    dev.maplesu.manager.workmode.Hashes.sha256Hex("$port:$token")

sealed interface LiteWebPreviewResult {
    data class Ok(val info: LiteWebUrlInfo) : LiteWebPreviewResult

    data class Failed(val code: String, val detail: String = "") : LiteWebPreviewResult
}

object LiteWebPreviewCodes {
    const val MODE_NOT_LITE = "LITEWEB_MODE_NOT_LITE"

    /** 模式未判定且没有 LITE 证据（冷启动窗口）：与"模式不是 Lite"分开，避免误导。 */
    const val MODE_UNRESOLVED = "LITEWEB_MODE_UNRESOLVED"
    const val READ_DENIED = "LITEWEB_PREVIEW_READ_DENIED"
    const val WRITE_DENIED = "LITEWEB_PREVIEW_WRITE_DENIED"
    const val READBACK_FAILED = "LITEWEB_PREVIEW_READBACK_FAILED"
    const val PORT_INVALID = LiteWebPortPolicy.CODE_RANGE
    const val CONFIG_UNAVAILABLE = "LITEWEB_PREVIEW_CONFIG_UNAVAILABLE"
}

/** 配置读写端口（生产 = root shell 读写 /data/adb/maplesu/webui.conf 0600；测试 = 内存）。 */
interface LiteWebPreviewStore {
    /** 读不到（无文件/无 root/损坏）→ null。 */
    fun read(): LiteWebPreviewConfig?

    /** 配置文件是否存在（用于区分“确实没有”与“暂时读不到”）。 */
    fun exists(): Boolean = read() != null

    /** 原子写 0600；失败（无 root/写读失败）→ false。 */
    fun write(config: LiteWebPreviewConfig): Boolean
}

object UnavailableLiteWebPreviewStore : LiteWebPreviewStore {
    override fun read(): LiteWebPreviewConfig? = null
    override fun write(config: LiteWebPreviewConfig): Boolean = false
}

/**
 * 普通预览只读/创建配置；显式改口委托已有监听的 owner，并核对持久回读。
 * 本层不写 hidden、不备份、不卸载，也不自行重启监听。
 */
class LiteWebPreviewCoordinator(
    private val store: LiteWebPreviewStore,
    private val tokenGenerator: () -> String = { LiteWebToken.generate() },
    private val defaultPort: Int = LiteWebPortPolicy.DEFAULT_PORT,
) {
    @Volatile
    private var cached: LiteWebPreviewConfig? = null

    /** Existing ports change through the live owner; only a missing config is created here. */
    fun preparePreviewUrl(
        requestedPort: Int? = null,
        applyPort: (LiteWebPreviewConfig, Int) -> LiteWebPreviewResult.Failed? = { _, _ ->
            LiteWebPreviewResult.Failed("LITEWEB_PORT_APPLY_UNAVAILABLE")
        },
    ): LiteWebPreviewResult {
        cached = null
        if (requestedPort != null && !validPort(requestedPort)) {
            return LiteWebPreviewResult.Failed(LiteWebPreviewCodes.PORT_INVALID)
        }
        val existing = runCatching { store.read() }.getOrNull()
        val config = when {
            existing != null && validPort(existing.port) -> {
                if (requestedPort == null || requestedPort == existing.port) existing else {
                    applyPort(existing, requestedPort)?.let { return it }
                    val readback = runCatching { store.read() }.getOrNull()
                    if (readback != existing.copy(port = requestedPort)) {
                        return LiteWebPreviewResult.Failed(LiteWebPreviewCodes.READBACK_FAILED)
                    }
                    readback
                }
            }
            // 配置**存在但暂时读不到**（root 通道未就绪/读失败）→ 绝不能生成新 token 覆盖它：
            // 那会让 daemon 已使用的 token 失效、恢复链接全部作废（真机实测）。
            runCatching { store.exists() }.getOrDefault(true) -> {
                return LiteWebPreviewResult.Failed(
                    LiteWebPreviewCodes.READBACK_FAILED, "webui.conf exists but unreadable",
                )
            }
            else -> {
                val created = runCatching {
                    LiteWebPreviewConfig(token = tokenGenerator(), port = requestedPort ?: defaultPort)
                }.getOrElse {
                    return LiteWebPreviewResult.Failed(
                        LiteWebPreviewCodes.CONFIG_UNAVAILABLE, it.message.orEmpty(),
                    )
                }
                if (!runCatching { store.write(created) }.getOrDefault(false)) {
                    return LiteWebPreviewResult.Failed(
                        LiteWebPreviewCodes.WRITE_DENIED, "webui.conf 0600 write failed",
                    )
                }
                val readback = runCatching { store.read() }.getOrNull()
                    ?: return LiteWebPreviewResult.Failed(
                        LiteWebPreviewCodes.READBACK_FAILED, "webui.conf readback failed",
                    )
                if (readback != created) {
                    return LiteWebPreviewResult.Failed(
                        LiteWebPreviewCodes.READBACK_FAILED, "webui.conf readback invalid",
                    )
                }
                readback
            }
        }
        cached = config
        return LiteWebPreviewResult.Ok(LiteWebUrlInfo(config.url(), config.port))
    }

    /**
     * 只读：配置读取生成 URL；**不写盘**、不启动 daemon。读不到 → null。
     *
     * 每次都**先读配置**、再退回缓存：激活/恢复会重写 `webui.conf`（新的 token），
     * 旧实现优先返回缓存 → 页面显示的链接与 daemon 实际 token 不一致（真机实测：
     * 链接打不开）。配置读取失败才用缓存兜底。
     */
    fun previewUrlOrNull(): String? {
        val config = runCatching { store.read() }.getOrNull() ?: cached ?: return null
        if (!validPort(config.port) || !LiteWebToken.isValid(config.token)) return null
        cached = config
        return config.url()
    }

    /** 丢弃缓存（token 轮换/激活后由调用方显式触发）。 */
    internal fun invalidate() {
        cached = null
    }

    internal fun cachedConfig(): LiteWebPreviewConfig? = cached

    private fun validPort(port: Int): Boolean = LiteWebPortPolicy.validate(port) == null
}
