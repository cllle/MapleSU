package dev.maplesu.manager.modules.webui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「打开模块 WebUI」的纯逻辑回归：URL 组成、选路、失败分类。
 *
 * 这些用例是设备上「点了没反应/白屏」的可执行规格：
 *  - Ultra 模块只能走 SDK 端口，不能去读 `/data/adb/modules/<id>`（那里没有它）；
 *  - 端口必须先回 HTTP 才算就绪（TCP 可连 ≠ 服务就绪）；
 *  - 任何失败都要有稳定 code，不能返回 null 让界面静默。
 */
class ModuleWebUiPlanTest {

    // ------------------------------------------------------------------ ids

    @Test
    fun ultraModuleIdIsStrippedAndValidated() {
        assertEquals("abc123", ModuleWebUiPlan.moduleId32("maplesu:abc123"))
        assertEquals("abc123", ModuleWebUiPlan.moduleId32OrNull("maplesu:abc123"))
        assertTrue(ModuleWebUiPlan.isUltraModuleId("maplesu:abc123"))
        assertFalse(ModuleWebUiPlan.isUltraModuleId("abc123"))
    }

    @Test
    fun invalidModuleIdsAreRejectedInsteadOfGuessed() {
        assertNull(ModuleWebUiPlan.moduleId32OrNull("maplesu:"))
        assertNull(ModuleWebUiPlan.moduleId32OrNull("maplesu:../etc"))
        assertNull(ModuleWebUiPlan.moduleId32OrNull("maplesu:a b"))
        assertNull(ModuleWebUiPlan.moduleId32OrNull("maplesu:a/b"))
        assertNull(ModuleWebUiPlan.moduleId32OrNull("maplesu:${"x".repeat(65)}"))
        // 中文/控制字符不是合法 id32（模块 id 由宿主生成，一定是可见 ASCII）
        assertNull(ModuleWebUiPlan.moduleId32OrNull("maplesu:模块"))
    }

    // ------------------------------------------------------------------ urls

    @Test
    fun loopbackUrlOnlyAcceptsRealPortsAndRelativePaths() {
        assertEquals("http://127.0.0.1:32431/", ModuleWebUiPlan.loopbackUrl(32431))
        assertEquals("http://127.0.0.1:32431/x", ModuleWebUiPlan.loopbackUrl(32431, "/x"))
        assertNull(ModuleWebUiPlan.loopbackUrl(0))
        assertNull(ModuleWebUiPlan.loopbackUrl(65536))
        assertNull(ModuleWebUiPlan.loopbackUrl(-1))
        // 不能借 path 换成别的 host / scheme
        assertNull(ModuleWebUiPlan.loopbackUrl(32431, "//evil.example/"))
        assertNull(ModuleWebUiPlan.loopbackUrl(32431, "/../secret"))
        assertNull(ModuleWebUiPlan.loopbackUrl(32431, "http://evil.example/"))
    }

    @Test
    fun modWebUrlKeepsLitewebPrefixAndTokenSegment() {
        val base = "http://127.0.0.1:18427/x7k9f/${"a".repeat(64)}/"
        assertEquals(
            "http://127.0.0.1:18427/x7k9f/${"a".repeat(64)}/modweb/tee/index.html",
            ModuleWebUiPlan.modWebUrl(base, "tee"),
        )
        assertEquals(
            "http://127.0.0.1:18427/x7k9f/${"a".repeat(64)}/modweb/tee/a/app.js",
            ModuleWebUiPlan.modWebUrl(base, "tee", "a/app.js"),
        )
    }

    @Test
    fun modWebUrlRejectsForeignHostTraversalAndSecretPaths() {
        val base = "http://127.0.0.1:18427/x7k9f/${"a".repeat(64)}/"
        // 不是回环 → 不拼
        assertNull(ModuleWebUiPlan.modWebUrl("http://10.0.0.2:18427/x7k9f/tok/", "tee"))
        // https / 缺端口 / 缺 token 段
        assertNull(ModuleWebUiPlan.modWebUrl("https://127.0.0.1:18427/x7k9f/tok/", "tee"))
        assertNull(ModuleWebUiPlan.modWebUrl("http://127.0.0.1/x7k9f/tok/", "tee"))
        assertNull(ModuleWebUiPlan.modWebUrl("http://127.0.0.1:18427/", "tee"))
        // 穿越 / 点文件
        assertNull(ModuleWebUiPlan.modWebUrl(base, "tee", "../../etc/passwd"))
        assertNull(ModuleWebUiPlan.modWebUrl(base, "tee", "a/../../x"))
        assertNull(ModuleWebUiPlan.modWebUrl(base, "tee", ".ssh/id_rsa"))
        // 非法 id
        assertNull(ModuleWebUiPlan.modWebUrl(base, "..", "index.html"))
    }

    @Test
    fun modWebRelativePathNormalization() {
        assertEquals("index.html", ModuleWebUiPlan.normalizeModWebRel(""))
        assertEquals("index.html", ModuleWebUiPlan.normalizeModWebRel("/"))
        assertEquals("a/app.js", ModuleWebUiPlan.normalizeModWebRel("/a/app.js"))
        assertNull(ModuleWebUiPlan.normalizeModWebRel("a\\b"))
        assertNull(ModuleWebUiPlan.normalizeModWebRel("a/./b"))
        assertNull(ModuleWebUiPlan.normalizeModWebRel("a//b"))
    }

    // ------------------------------------------------------------------ routing

    @Test
    fun runningUltraEnvironmentSelectsSdkRoute() {
        assertEquals(
            WebUiRoute.ULTRA_SDK,
            ModuleWebUiPlan.routeFor(
                hasWebUi = true, isUltraModule = true, ultraEnvironmentRunning = true,
                litewebBaseUrl = "http://127.0.0.1:18427/x7k9f/${"a".repeat(64)}/",
                moduleEnabled = true,
            ),
        )
    }

    @Test
    fun nativeModuleKeepsSdkOwnershipWhileEnvironmentNeedsPreparation() {
        val base = "http://127.0.0.1:18427/x7k9f/${"a".repeat(64)}/"
        assertEquals(
            WebUiRoute.ULTRA_SDK,
            ModuleWebUiPlan.routeFor(
                hasWebUi = true, isUltraModule = true, ultraEnvironmentRunning = false,
                litewebBaseUrl = base, moduleEnabled = true,
            ),
        )
        assertEquals(
            WebUiRoute.ULTRA_SDK,
            ModuleWebUiPlan.routeFor(
                hasWebUi = true, isUltraModule = true, ultraEnvironmentRunning = false,
                litewebBaseUrl = null, moduleEnabled = true,
            ),
        )
    }

    @Test
    fun scriptModulesUseAssetWebrootAndDisabledOrDeclaredFreeModulesAreUnsupported() {
        assertEquals(
            WebUiRoute.KSU_ASSET_WEBROOT,
            ModuleWebUiPlan.routeFor(
                hasWebUi = true, isUltraModule = false, ultraEnvironmentRunning = false,
                litewebBaseUrl = null, moduleEnabled = true,
            ),
        )
        assertEquals(
            WebUiRoute.UNSUPPORTED,
            ModuleWebUiPlan.routeFor(
                hasWebUi = false, isUltraModule = false, ultraEnvironmentRunning = true,
                litewebBaseUrl = null, moduleEnabled = true,
            ),
        )
        assertEquals(
            WebUiRoute.UNSUPPORTED,
            ModuleWebUiPlan.routeFor(
                hasWebUi = true, isUltraModule = false, ultraEnvironmentRunning = false,
                litewebBaseUrl = null, moduleEnabled = false,
            ),
        )
        assertEquals(
            WebUiRoute.UNSUPPORTED,
            ModuleWebUiPlan.routeFor(
                hasWebUi = true, isUltraModule = true, ultraEnvironmentRunning = true,
                litewebBaseUrl = null, moduleEnabled = true, moduleRemoved = true,
            ),
        )
    }

    // ------------------------------------------------------------------ failures

    @Test
    fun receiptFailureSeparatesMissingRootKeyFromSdkRejection() {
        val noKey = ModuleWebUiPlan.failureFromReceipt(false, "MAPLESU_ULTRA_ROOT_KEY_ABSENT", 0)
        assertNotNull(noKey)
        assertEquals(WebUiFailureReason.ROOT_KEY_ABSENT, noKey!!.reason)
        assertEquals("ULTRA_ROOT_KEY_ABSENT", noKey.code)

        val rejected = ModuleWebUiPlan.failureFromReceipt(false, "ERR_MODULE_NOT_RUNNING", 0)
        assertEquals(WebUiFailureReason.SDK_REJECTED, rejected!!.reason)

        val unparseable = ModuleWebUiPlan.failureFromReceipt(false, "MAPLESU_BRIDGE_UNPARSEABLE", 0)
        assertEquals(WebUiFailureReason.SDK_REJECTED, unparseable!!.reason)
        assertTrue(unparseable.describe().contains("MAPLESU_BRIDGE_UNPARSEABLE").not())

        // 成功但没有端口 = 失败（异步服务没给出端口，绝不能当「已打开」）
        val noPort = ModuleWebUiPlan.failureFromReceipt(true, "OK", 0)
        assertEquals(WebUiFailureReason.PORT_MISSING, noPort!!.reason)

        assertNull(ModuleWebUiPlan.failureFromReceipt(true, "OK", 32431))
    }

    @Test
    fun probeFailureDistinguishesRefusedFromNonHttpPort() {
        assertEquals(
            WebUiFailureReason.PORT_NOT_LISTENING,
            ModuleWebUiPlan.failureFromProbe(connected = false, httpStatus = null)!!.reason,
        )
        assertEquals(
            WebUiFailureReason.NOT_HTTP,
            ModuleWebUiPlan.failureFromProbe(connected = true, httpStatus = null)!!.reason,
        )
        assertNull(ModuleWebUiPlan.failureFromProbe(connected = true, httpStatus = 200))
        assertNull(ModuleWebUiPlan.failureFromProbe(connected = true, httpStatus = 404))
    }

    @Test
    fun httpStatusFailuresAreExplicitButRedirectsAndSuccessesPass() {
        assertNull(ModuleWebUiPlan.failureFromHttpStatus(200))
        assertNull(ModuleWebUiPlan.failureFromHttpStatus(302))
        val notFound = ModuleWebUiPlan.failureFromHttpStatus(404, "http://127.0.0.1:32431/")
        assertEquals(WebUiFailureReason.HTTP_STATUS, notFound!!.reason)
        assertTrue(notFound.detail.contains("404"))
        assertEquals(WebUiFailureReason.HTTP_STATUS, ModuleWebUiPlan.failureFromHttpStatus(500)!!.reason)
    }

    @Test
    fun failureDescribeNeverLeaksAnEmptyReason() {
        assertEquals("PORT_MISSING", WebUiFailure(WebUiFailureReason.PORT_MISSING).describe())
        assertEquals(
            "SDK_REJECTED: ERR_X",
            WebUiFailure(WebUiFailureReason.SDK_REJECTED, "ERR_X").describe(),
        )
    }

    // ---------------------------------------------------- SDK 打开回执的轻量分类

    @Test
    fun sdkOpenOutcomeSeparatesAlreadyRunningFromRejection() {
        // -19918 = SERVER_RUNNING：服务已在运行，不能当普通拒绝，也不能自动重启（§5.2）。
        val running = ModuleWebUiPlan.failureFromSdkOpen(
            SdkOpenOutcome(ok = false, sdkError = "OK", sdkErrorCode = ModuleWebUiPlan.SERVER_RUNNING_SDK_ERROR),
        )
        assertEquals(WebUiFailureReason.ALREADY_RUNNING, running!!.reason)
        assertEquals("ALREADY_RUNNING", running.code)

        val runningByText = ModuleWebUiPlan.failureFromSdkOpen(
            SdkOpenOutcome(ok = false, sdkError = "ERR_SERVER_RUNNING"),
        )
        assertEquals(WebUiFailureReason.ALREADY_RUNNING, runningByText!!.reason)

        // 启动器层异常：errorCode 必须留下来，不能退化成「bridge answer was not a receipt」。
        val threw = ModuleWebUiPlan.failureFromSdkOpen(
            SdkOpenOutcome(ok = false, errorCode = "IllegalStateException"),
        )
        assertEquals(WebUiFailureReason.SDK_REJECTED, threw!!.reason)
        assertTrue(threw.detail.contains("IllegalStateException"))

        // 成功回执但没给端口，仍按 PORT_MISSING 失败。
        assertEquals(
            WebUiFailureReason.PORT_MISSING,
            ModuleWebUiPlan.failureFromSdkOpen(SdkOpenOutcome(ok = true, sdkError = "OK", port = 0))!!.reason,
        )
        assertNull(ModuleWebUiPlan.failureFromSdkOpen(SdkOpenOutcome(ok = true, port = 32431, sdkError = "OK")))
    }

    @Test
    fun sdkOpenOutcomeMapsExplicitTimeoutWithoutTouchingProbeSemantics() {
        assertEquals(
            WebUiFailureReason.TIMEOUT,
            ModuleWebUiPlan.failureFromSdkOpen(
                SdkOpenOutcome(ok = false, errorCode = "TIMEOUT"),
            )!!.reason,
        )
        // 探活预算耗尽仍由 failureFromProbe 分类（更具体），不会被归成 TIMEOUT。
        assertEquals(
            WebUiFailureReason.PORT_NOT_LISTENING,
            ModuleWebUiPlan.failureFromProbe(connected = false, httpStatus = null)!!.reason,
        )
    }
}
