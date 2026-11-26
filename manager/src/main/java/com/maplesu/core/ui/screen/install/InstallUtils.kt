package com.maplesu.core.ui.screen.install

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.StringRes
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import com.maplesu.core.R
import dev.maplesu.manager.installer.InstallSourceKind
import dev.maplesu.manager.installer.InstallTargetMode
import dev.maplesu.manager.installer.InstallTargetRequest

/**
 * 步骤 ② 的两个 boot 来源。
 *
 * `DownloadFile` / `MigrateToSelf` / `DirectInstallToInactiveSlot` 保留类型（刷写页与旧路由
 * 仍能反序列化），但安装页**不再构造**它们：四步流程里 boot 只有"选文件"和"当前 boot"两种来源。
 */
@Parcelize
internal sealed class InstallMethod : Parcelable {
    data class SelectFile(
        val uri: Uri? = null,
        @get:StringRes override val label: Int = R.string.select_file,
        override val summary: String?
    ) : InstallMethod()

    data class DownloadFile(
        val url: String? = null,
        val partition: String? = null,
        @get:StringRes override val label: Int = R.string.download_file,
        override val summary: String?
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.direct_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.install_inactive_slot
    }

    /** W20/W21：外部 Root → 本管理器迁移。入口在首页，不在安装页（四步里没有它）。 */
    data object MigrateToSelf : InstallMethod() {
        override val label: Int
            get() = R.string.migration_entry_title
    }

    abstract val label: Int

    @IgnoredOnParcel
    open val summary: String? = null
}

/**
 * 安装方式 + 目标模式 + 目标分区 → 分派输入（**页面不自己做 if**：这里只翻译事实，结论由
 * `dev.maplesu.manager.installer.InstallTargetDispatch.decide` 给出）。
 *
 * [target] 来自步骤 ① 的选择（默认 Ultra），[partition] 是本模式下**生效**的分区
 * （按镜像头或用户选择）。它仅供页面提示；执行阶段使用实际源镜像或 PRE 事实重新裁决。
 */
internal fun installTargetRequestFor(
    method: InstallMethod?,
    target: InstallTargetMode,
    kernelRelease: String?,
    sdkInt: Int,
    partition: String?,
): InstallTargetRequest = InstallTargetRequest(
    target = target,
    source = when (method) {
        is InstallMethod.SelectFile -> InstallSourceKind.IMAGE
        is InstallMethod.DownloadFile -> InstallSourceKind.DOWNLOADED_BOOT
        InstallMethod.MigrateToSelf, InstallMethod.DirectInstall,
        InstallMethod.DirectInstallToInactiveSlot, null,
        -> InstallSourceKind.CURRENT_PARTITION
    },
    partition = partition,
    kernelRelease = kernelRelease,
    // 页面侧不 exec ksud（无 root 也要能显示本页）：内嵌目录留 null，
    // 真正的产物闸在修补入口用 `ksud boot-info supported-kmis` 再判一次（缺了硬失败）。
    availableKmis = null,
    sdkInt = sdkInt,
)
