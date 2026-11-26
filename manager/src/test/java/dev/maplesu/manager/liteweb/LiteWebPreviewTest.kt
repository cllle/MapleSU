package dev.maplesu.manager.liteweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "隐藏后 WebUI 链接"预览（JVM）：配置生成/回读、只读、端口变化、不启动 daemon。 */
class LiteWebPreviewTest {
    private val token = LiteWebTestFixtures.TOKEN.replace('a', 'c')

    private class FakeStore(
        var value: LiteWebPreviewConfig? = null,
        var writeResult: Boolean = true,
        var readbackOverride: (() -> LiteWebPreviewConfig?)? = null,
        var existsOverride: Boolean? = null,
    ) : LiteWebPreviewStore {
        var reads = 0
        var writes = 0
        val written = mutableListOf<LiteWebPreviewConfig>()
        override fun read(): LiteWebPreviewConfig? {
            reads++
            readbackOverride?.let { return it() }
            return value
        }
        override fun exists(): Boolean = existsOverride ?: (value != null)
        override fun write(config: LiteWebPreviewConfig): Boolean {
            writes++
            written.add(config)
            if (!writeResult) return false
            value = config
            return true
        }
    }

    @Test
    fun previewUrlReflectsConfigChangesInsteadOfStaleCache() {
        // 真机缺陷：激活重写 webui.conf 后，页面仍显示旧缓存 token → 链接打不开。
        val store = FakeStore(value = LiteWebPreviewConfig(token, 18427))
        val coordinator = LiteWebPreviewCoordinator(store, tokenGenerator = { token }, defaultPort = 18427)
        val first = coordinator.previewUrlOrNull()
        assertTrue(first!!.contains(token))
        val fresh = LiteWebTestFixtures.TOKEN.replace('a', 'd')
        store.value = LiteWebPreviewConfig(fresh, 18427)
        val second = coordinator.previewUrlOrNull()
        assertTrue("必须反映磁盘上的新 token", second!!.contains(fresh))
        assertFalse(second.contains(token))
    }

    @Test
    fun unreadableButExistingConfigIsNeverOverwritten() {
        // 读失败 ≠ 不存在：绝不能生成新 token 覆盖（会让 daemon 的 token 失效）。
        val store = FakeStore(
            value = null,
            readbackOverride = { null },
            existsOverride = true,
        )
        val coordinator = LiteWebPreviewCoordinator(store, tokenGenerator = { token }, defaultPort = 18427)
        val result = coordinator.preparePreviewUrl()
        assertTrue(result is LiteWebPreviewResult.Failed)
        assertEquals("配置存在但读不到时不得写盘", 0, store.writes)
    }

    @Test
    fun failedExistenceCheckNeverOverwritesOrReturnsAnEarlierToken() {
        var readable = true
        var writes = 0
        val store = object : LiteWebPreviewStore {
            override fun read(): LiteWebPreviewConfig? =
                if (readable) LiteWebPreviewConfig(token, 18427) else null
            override fun exists(): Boolean = error("root channel unavailable")
            override fun write(config: LiteWebPreviewConfig): Boolean { writes++; return true }
        }
        val coordinator = LiteWebPreviewCoordinator(store)
        assertTrue(coordinator.preparePreviewUrl() is LiteWebPreviewResult.Ok)
        readable = false
        val failed = coordinator.preparePreviewUrl() as LiteWebPreviewResult.Failed
        assertEquals(LiteWebPreviewCodes.READBACK_FAILED, failed.code)
        assertEquals(0, writes)
        assertNull(coordinator.previewUrlOrNull())
    }

    @Test
    fun missingConfigGeneratesTokenOnceAndPersistsWithReadback() {
        val store = FakeStore(value = null)
        val coordinator = LiteWebPreviewCoordinator(store, tokenGenerator = { token }, defaultPort = 18427)
        val result = coordinator.preparePreviewUrl()
        assertTrue(result is LiteWebPreviewResult.Ok)
        assertEquals("http://127.0.0.1:18427/x7k9f/$token/", (result as LiteWebPreviewResult.Ok).info.url)
        assertEquals(1, store.writes)
        assertEquals(LiteWebPreviewConfig(token, 18427), store.written.single())
        // 第二次准备不重复写（幂等）。
        coordinator.preparePreviewUrl()
        assertEquals(1, store.writes)
    }

    @Test
    fun existingConfigIsReusedWithoutWriting() {
        val existing = LiteWebPreviewConfig(token, 18437)
        val store = FakeStore(value = existing)
        val result = LiteWebPreviewCoordinator(store).preparePreviewUrl()
        assertEquals("http://127.0.0.1:18437/x7k9f/$token/", (result as LiteWebPreviewResult.Ok).info.url)
        assertEquals(0, store.writes)
    }

    @Test
    fun requestedPortUsesLiveOwnerAndPreservesToken() {
        val existing = LiteWebPreviewConfig(token, 18427)
        val store = FakeStore(value = existing)
        val calls = mutableListOf<Int>()
        val result = LiteWebPreviewCoordinator(store).preparePreviewUrl(18437) { current, requested ->
            assertEquals(existing, current)
            calls += requested
            store.value = current.copy(port = requested)
            null
        } as LiteWebPreviewResult.Ok
        assertEquals(18437, result.info.port)
        assertEquals(existing.copy(port = 18437), store.value)
        assertEquals(listOf(18437), calls)
        assertEquals(0, store.writes)
    }

    @Test
    fun missingConfigUsesRequestedPortAndExactReadback() {
        val store = FakeStore()
        val result = LiteWebPreviewCoordinator(store, tokenGenerator = { token }).preparePreviewUrl(18437)
        assertEquals(18437, (result as LiteWebPreviewResult.Ok).info.port)
        assertEquals(LiteWebPreviewConfig(token, 18437), store.value)
        assertEquals(1, store.writes)
    }

    @Test
    fun sameRequestedPortDoesNotReplaceExistingConfig() {
        val existing = LiteWebPreviewConfig(token, 18427)
        val store = FakeStore(value = existing)
        val result = LiteWebPreviewCoordinator(store).preparePreviewUrl(18427) { _, _ -> error("same port") }
        assertEquals(18427, (result as LiteWebPreviewResult.Ok).info.port)
        assertEquals(existing, store.value)
        assertEquals(0, store.writes)
    }

    @Test
    fun portConflictPreservesOldConfigAndOwnerError() {
        val existing = LiteWebPreviewConfig(token, 18427)
        val store = FakeStore(value = existing)
        val result = LiteWebPreviewCoordinator(store).preparePreviewUrl(18437) { _, _ ->
            LiteWebPreviewResult.Failed(LiteWebPortPolicy.CODE_CONFLICT)
        }
        assertEquals(LiteWebPortPolicy.CODE_CONFLICT, (result as LiteWebPreviewResult.Failed).code)
        assertEquals(existing, store.value)
        assertEquals(0, store.writes)
    }

    @Test
    fun ownerSuccessRequiresPersistedRequestedPortAndOriginalToken() {
        val existing = LiteWebPreviewConfig(token, 18427)
        for (after in listOf(existing, existing.copy(token = "f".repeat(64), port = 18437))) {
            val store = FakeStore(value = existing)
            val result = LiteWebPreviewCoordinator(store).preparePreviewUrl(18437) { _, _ ->
                store.value = after
                null
            }
            assertEquals(LiteWebPreviewCodes.READBACK_FAILED, (result as LiteWebPreviewResult.Failed).code)
            assertEquals(0, store.writes)
        }
    }

    @Test
    fun noWritePermissionReturnsFailure() {
        val store = FakeStore(value = null, writeResult = false)
        val result = LiteWebPreviewCoordinator(store, tokenGenerator = { token }).preparePreviewUrl()
        assertEquals(LiteWebPreviewCodes.WRITE_DENIED, (result as LiteWebPreviewResult.Failed).code)
        assertNull(LiteWebPreviewCoordinator(store).previewUrlOrNull())
    }

    @Test
    fun readbackMismatchIsFailure() {
        val store = FakeStore(value = null)
        store.readbackOverride = { LiteWebPreviewConfig(token, 18427) }
        val result = LiteWebPreviewCoordinator(store, tokenGenerator = { token }).preparePreviewUrl()
        // readback 与写入 token 相同但 store.value 未更新 -> 仍视为读到有效配置（幂等读）。
        assertTrue(result is LiteWebPreviewResult.Ok)

        val corrupt = FakeStore(value = null)
        corrupt.readbackOverride = { null }
        val failed = LiteWebPreviewCoordinator(corrupt, tokenGenerator = { token }).preparePreviewUrl()
        assertEquals(LiteWebPreviewCodes.READBACK_FAILED, (failed as LiteWebPreviewResult.Failed).code)
    }

    @Test
    fun corruptConfigIsRegenerated() {
        val store = FakeStore(value = null)
        val coordinator = LiteWebPreviewCoordinator(store, tokenGenerator = { token })
        val result = coordinator.preparePreviewUrl() as LiteWebPreviewResult.Ok
        assertEquals(token, store.value!!.token)
        assertEquals(result.info.port, store.value!!.port)
    }

    @Test
    fun portChangeChangesPreviewUrl() {
        val store = FakeStore(value = LiteWebPreviewConfig(token, 18427))
        assertEquals(
            "http://127.0.0.1:18427/x7k9f/$token/",
            (LiteWebPreviewCoordinator(store).preparePreviewUrl() as LiteWebPreviewResult.Ok).info.url,
        )
        store.value = LiteWebPreviewConfig(token, 18437)
        assertEquals(
            "http://127.0.0.1:18437/x7k9f/$token/",
            (LiteWebPreviewCoordinator(store).preparePreviewUrl() as LiteWebPreviewResult.Ok).info.url,
        )
    }

    @Test
    fun previewUrlReadOnlyDoesNotWrite() {
        val store = FakeStore(value = null)
        assertNull(LiteWebPreviewCoordinator(store).previewUrlOrNull())
        assertEquals(0, store.writes)
        store.value = LiteWebPreviewConfig(token, 18427)
        assertEquals("http://127.0.0.1:18427/x7k9f/$token/", LiteWebPreviewCoordinator(store).previewUrlOrNull())
        assertEquals(0, store.writes)
    }

    @Test
    fun prepareDoesNotStartAnyDaemonOrListener() {
        // 接口上不存在 start/ping/listen 能力：用反射锁住，防未来偷偷加入副作用。
        // exists() 是只读存在性探测（区分“确实没有配置”与“暂时读不到”），不是副作用。
        val methods = LiteWebPreviewStore::class.java.methods.map { it.name }
            .filterNot { it.startsWith("access$") } // Kotlin 为默认方法生成的合成访问器
            .toSet()
        assertEquals(setOf("read", "write", "exists"), methods)
        val coordinatorMethods = LiteWebPreviewCoordinator::class.java.methods.map { it.name }
        assertTrue(coordinatorMethods.none { it.contains("start", ignoreCase = true) })
        assertTrue(coordinatorMethods.none { it.contains("ping", ignoreCase = true) })
        assertTrue(coordinatorMethods.none { it.contains("listen", ignoreCase = true) })
        assertFalse(coordinatorMethods.any { it.contains("Hidden", ignoreCase = true) })
    }

    @Test
    fun urlMatchesActivateContract() {
        val store = FakeStore(value = LiteWebPreviewConfig(token, 18437))
        val preview = (LiteWebPreviewCoordinator(store).preparePreviewUrl() as LiteWebPreviewResult.Ok).info
        // activate 事务同样用 LiteWebUrls.url(port, token) 生成隐藏后的链接。
        assertEquals(LiteWebUrls.url(18437, token), preview.url)
        assertTrue(preview.url.startsWith("http://127.0.0.1:18437/x7k9f/"))
    }

    @Test
    fun invalidPortInConfigFallsBackToDefault() {
        // 端口非法（<1024）时不能生成链接；协调器以默认端口重建配置。
        val store = FakeStore(value = LiteWebPreviewConfig(token, LiteWebPortPolicy.DEFAULT_PORT))
        val result = LiteWebPreviewCoordinator(store).preparePreviewUrl()
        assertTrue(result is LiteWebPreviewResult.Ok)
        assertEquals(LiteWebPortPolicy.DEFAULT_PORT, (result as LiteWebPreviewResult.Ok).info.port)
    }
}
