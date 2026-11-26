package com.maplesu.core.ui.screen.install

import android.content.Context
import android.net.Uri
import com.maplesu.core.ui.util.UltraKernelFacts
import com.maplesu.core.ui.util.UltraKernelGeneration
import com.maplesu.core.ui.util.ultraKernelFacts
import com.maplesu.core.ui.util.ultraKernelReleaseOf
import com.maplesu.core.ui.util.ultraKernelStableCode
import java.io.File
import java.io.IOException

/** Target generation result projected onto the existing install controls. */
internal data class KernelTarget(
    /** 传给修补入口的资产 id（`k22` / …）；现场生成档是 [InstallBootFlow.GENERATED_VARIANT_ID]。 */
    val id: String,
    /**
     * `true` = 这个目标由本机现场生成（目录里没有它的补丁集）。
     *
     * 它只影响结果页的一句说明（"由本机生成，尚未上机验证"）：本机生成的档在长度/回读/摘要上
     * 走的是同一套 builder 门禁，但"没在设备上启动过"这件事不能让用户以为是已验证档。
     */
    val generatedOnDevice: Boolean = false,
)

/**
 * 步骤 ②→③ 的页面状态。[reading] 时是纯占位（还没识别完）；识别失败也在这同一个类型里表达，
 * 页面不做任何 `if (error == ...)` 的分支。
 */
internal data class BootFlowProfile(
    /** 来源标签（文件名 / "current"），只用于展示。 */
    val source: String = "",
    val reading: Boolean = false,
    /** 来源镜像的真实内核版本；镜像身份与写入检查始终使用它。 */
    val release: String = "",
    /** 可选的旧调用目标提示；标准页面预览只显示来源事实，实际生成在执行阶段完成。 */
    val target: KernelTarget? = null,
    val stableCode: String = "",
    val detail: String = "",
    val knownReleases: List<String> = emptyList(),
    /** 设备当前运行内核（只用来提示"镜像不是本机内核 → 只能保存不能刷入"）。 */
    val deviceRelease: String = "",
    /** 目录识别出的内核系列，仅用于显示，不参与镜像身份判定。 */
    val series: String = "",
    val hasRamdisk: Boolean? = null,
) {
    val seriesLabel: String get() = if (series.isEmpty()) "" else "$series.x"

    /** 镜像的内核与设备当前内核不同 → 写盘会被服务端拒绝，只会出镜像。 */
    val releaseMismatch: Boolean
        get() = release.isNotEmpty() && deviceRelease.isNotEmpty() && release != deviceRelease

    /** 兼容旧调用的生成结果，页面提交不以它作为前置条件。 */
    val selectable: Boolean get() = !reading && target != null
}

/**
 * 现场生成（W1b `UltraKernelPlan`）的接线端口。
 *
 * 生产装配是 [com.maplesu.core.ui.util.UltraKernelGeneration.port]：它真的会调
 * `UltraKernelFlow.generatePlan` 渲染这个内核的 plan，并把拒绝原因（`SITE_*` / `KALLSYMS` /
 * `CREDENTIAL_REQUIRED`）作为稳定码返回。这一层只决定"能不能修"，产物仍然由
 * `UltraKernelInstall` 走既有 builder 门禁生成 —— 端口本身**不建镜像、不写盘**。
 */
internal interface UltraKernelGenerationPort {
    /**
     * @param boot 将用于渲染的 boot 镜像；`null` = 调用方只有 release 字符串
     *        （"当前分区"来源没有拷贝镜像），此时延后到用户确认安装时准备凭据并生成。
     */
    fun plan(boot: File?, release: String): GenerationPlan

    sealed class GenerationPlan {
        /**
         * 已接线：能现场产出该内核的 spec。
         *
         * @param specSha256 现算的**修补后内核**摘要（不是 spec 文本摘要 —— 后者含口令明文，
         *        产品路径不落盘、不进 UI）。
         * @param patches 记录条数；[deferred] 时为 0（写入前才渲染）。
         * @param deferred `true` = 缺少镜像或修补凭据，确认安装/文件修补后再渲染。
         */
        data class Available(
            val specSha256: String,
            val patches: Int,
            val deferred: Boolean = false,
        ) : GenerationPlan()

        /** 拒绝：`code` 是稳定码（`ULTRA_KERNEL_*`），`detail` 只进日志/错误行。 */
        data class Unavailable(val code: String, val detail: String = "") : GenerationPlan()
    }
}

/** The target renderer decides availability; historical recommendations are compatibility data. */
internal object InstallBootFlow {
    /**
     * 生产装配的生成端口（`ui/util/UltraKernelInstall.kt` 的 `UltraKernelGeneration`）。
     * 测试通过这个 `var` 注入 fake（端口是纯接口，判定在 JVM 上可测）。
     */
    var generation: UltraKernelGenerationPort = UltraKernelGeneration.port

    /** 本机现场生成档的伪 id：只在 `generation` 返回 `Available` 时出现。 */
    const val GENERATED_VARIANT_ID = "generated"

    fun reading(source: String, deviceRelease: String): BootFlowProfile =
        BootFlowProfile(source = source, reading = true, deviceRelease = deviceRelease)

    /**
     * @param boot 用于现场生成的 boot 镜像；`null` = 只有 release 字符串（"当前分区"来源）。
     *        传入镜像时始终询问该目标的渲染器。
     */
    fun fromFacts(
        source: String,
        deviceRelease: String,
        facts: UltraKernelFacts,
        boot: File? = null,
    ): BootFlowProfile {
        return when (val plan = generation.plan(boot, facts.release)) {
            is UltraKernelGenerationPort.GenerationPlan.Available -> BootFlowProfile(
                source = source,
                release = facts.release,
                series = facts.series,
                target = KernelTarget(GENERATED_VARIANT_ID, generatedOnDevice = true),
                deviceRelease = deviceRelease,
            )
            is UltraKernelGenerationPort.GenerationPlan.Unavailable -> BootFlowProfile(
                source = source,
                release = facts.release,
                series = facts.series,
                target = null,
                // 端口的码优先：它比"目录里没有这个 release"更具体（缺凭据 / 站点半覆盖 /
                // kallsyms 走不通）。facts 的码只在端口没给码时兜底。
                stableCode = plan.code.ifEmpty { facts.stableCode },
                detail = plan.detail.ifEmpty {
                    facts.detail.ifEmpty { facts.rejected.joinToString(" | ") }
                },
                knownReleases = facts.knownReleases,
                deviceRelease = deviceRelease,
            )
        }
    }

    /** 识别失败（镜像读不出 release / IO 失败）：同样只是步骤 ③ 的"修不了"，不是拦截。 */
    fun failed(source: String, deviceRelease: String, stableCode: String, detail: String = ""): BootFlowProfile =
        BootFlowProfile(
            source = source,
            release = "",
            target = null,
            stableCode = stableCode,
            detail = detail,
            deviceRelease = deviceRelease,
        )
}

/** 上限与 `ImageRepository.MAX_IMAGE_BYTES` 对齐：探测也不接受超大"镜像"。 */
private const val PROBE_SIZE_LIMIT = 512L * 1024 * 1024

/**
 * 步骤 ②→③ 的只读探测：把用户选择的 boot 复制到私有缓存、读 release、查目录。
 *
 * **不写** `files/images/imported.img`（它在确认之前必须保持"现场 boot 的备份"语义，
 * 见 47 报告 R3）。识别完立刻删临时文件。
 */
internal fun probeChosenBoot(context: Context, uri: Uri, deviceRelease: String): BootFlowProfile {
    val source = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    val temp = try {
        File.createTempFile("boot-probe-", ".img", context.cacheDir)
    } catch (error: Exception) {
        return InstallBootFlow.failed(source, deviceRelease, "ULTRA_KERNEL_BASE_UNAVAILABLE", error.message.orEmpty())
    }
    return try {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return InstallBootFlow.failed(source, deviceRelease, "BOOT_SOURCE_READ_FAILED")
        stream.use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > PROBE_SIZE_LIMIT) throw IOException("BOOT_PROBE_SIZE_LIMIT")
                    output.write(buffer, 0, read)
                }
            }
        }
        val carrier = temp.inputStream().use(dev.maplesu.manager.installer.InstallPartitionPolicy::imagePartition)
        if (carrier == dev.maplesu.manager.installer.InstallPartition.INIT_BOOT) {
            return BootFlowProfile(source = source, deviceRelease = deviceRelease,
                hasRamdisk = dev.maplesu.image.BootImageInspector.inspect(temp).ramdiskSize > 0)
        }
        val release = try {
            ultraKernelReleaseOf(temp)
        } catch (error: Exception) {
            return InstallBootFlow.failed(source, deviceRelease, ultraKernelStableCode(error), error.message.orEmpty())
        }
        if (release.isEmpty()) {
            return InstallBootFlow.failed(source, deviceRelease, "ULTRA_KERNEL_BASE_RELEASE_UNREADABLE")
        }
        // Preview reads facts only; component selection and plan generation follow submission.
        BootFlowProfile(source = source, deviceRelease = deviceRelease, release = release,
            series = ultraKernelFacts(release).series,
            hasRamdisk = dev.maplesu.image.BootImageInspector.inspect(temp).ramdiskSize > 0)
    } catch (error: Exception) {
        val code = error.message.orEmpty().substringBefore(':').takeIf {
            it.startsWith("ULTRA_INIT_") || it.startsWith("ULTRA_KERNEL_")
        } ?: "BOOT_SOURCE_READ_FAILED"
        InstallBootFlow.failed(source, deviceRelease, code, error.message.orEmpty())
    } finally {
        runCatching { temp.delete() }
    }
}

/** 当前分区来源：release 直接来自 `uname`，不需要读镜像。 */
internal fun probeCurrentBoot(deviceRelease: String): BootFlowProfile {
    if (deviceRelease.isBlank()) {
        return InstallBootFlow.failed("current", deviceRelease, "ULTRA_KERNEL_BASE_RELEASE_UNREADABLE")
    }
    // 真正的源镜像、凭据与组件在 PRE 和构建阶段核实。
    return BootFlowProfile(source = "current", deviceRelease = deviceRelease, release = deviceRelease,
        series = ultraKernelFacts(deviceRelease).series)
}
