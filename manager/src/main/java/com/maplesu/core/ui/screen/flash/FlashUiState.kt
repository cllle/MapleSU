package com.maplesu.core.ui.screen.flash

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.maplesu.core.R
import com.maplesu.core.ui.util.FlashOutcome
import com.maplesu.core.ui.util.InstallStage

@Immutable
data class FlashUiState(
    /** 直接安装及模块操作的等宽日志；选文件结果页只显示输出路径。 */
    val text: String = "",
    /** `false` = 选文件修补，只显示结论与输出路径。 */
    val showLog: Boolean = true,
    val showRebootAction: Boolean,
    val flashingStatus: FlashingStatus,
    val showJailbreakWarning: Boolean,
    @param:StringRes val rebootLabelRes: Int,
    /**
     * S5：当前粗粒度阶段（修补中 / 写入中 / 待重启）。`null` = 该入口不报阶段（模块/还原）。
     */
    val stage: InstallStage? = null,
    /**
     * S6：结果态。`null` = 旧入口没上报，页面按 [FlashingStatus] 兜底。
     * 只有 [FlashOutcome.WRITTEN] 允许显示"已写入 boot 分区"。
     */
    val outcome: FlashOutcome? = null,
    /** 失败时那一句可读原因（已本地化；不含日志）。 */
    val message: String? = null,
    /** 失败时的稳定码（`ULTRA_KERNEL_*` / `INSTALLER_*` / 退出码），单独一行小字。 */
    val errorCode: String? = null,
    /** 本次直装成功，且提交候选的尺寸和摘要仍相符时提供「保存 boot」。 */
    val canSaveBoot: Boolean = false,
    /**
     * 这次修补的目标由**本机现场生成**（目录里没有它的补丁集）。
     *
     * 2026-11-01（用户口径）之后它**只**驱动失败/只出镜像页的一句出处说明；成功页（真的写进
     * boot 分区 / 该镜像本来就已修补）不显示它；直装成功显示日志 + 「保存 boot / 重启」。它不是"未验证"
     * 提示，也从不参与"能不能写"的判断。
     */
    val generatedOnDevice: Boolean = false,
    /** 后台确认存在安装前副本时，结果页允许发起恢复。 */
    val canRestore: Boolean = false,
    /** 成功后只保留固定在底部的保存 Boot 与重启动作。 */
    val directInstall: Boolean = false,
    val showCloseAction: Boolean = false,
    val actionInProgress: Boolean = false,
    /** Recovery always queries the original task; it does not submit another installation. */
    val canQuery: Boolean = false,
    val querying: Boolean = false,
    val canReturnToPreparation: Boolean = false,
    val recoveryMessage: String? = null,
)

@Immutable
data class FlashScreenActions(
    val onBack: () -> Unit,
    /** 直接安装及模块/还原的日志导出；文件修补结果页隐藏此入口。 */
    val onSaveLog: () -> Unit = {},
    /** 已完成分区写入，重启使其生效。 */
    val onReboot: () -> Unit,
    /** 步骤 ④ 的「保存 boot」：把修补后的 boot.img 导出到下载目录。 */
    val onSaveBoot: () -> Unit,
    val onConfirmJailbreakWarning: () -> Unit,
    val onDismissJailbreakWarning: () -> Unit,
    val onRestore: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onQueryOriginalTask: () -> Unit = {},
    val onReturnToPreparation: () -> Unit = {},
)

/**
 * 步骤 ④ 的页面标题：四步流程说「正在修补 / 修补成功 / 修补失败」，
 * 其它入口（模块安装、还原）沿用原有的「刷写中 / 刷写完成 / 刷写失败」。
 */
@get:StringRes
internal val FlashUiState.titleRes: Int
    get() = when (flashingStatus) {
        FlashingStatus.WAITING_INPUT -> R.string.work_mode_images_title
        FlashingStatus.PENDING -> R.string.module_install_pending_title
        FlashingStatus.FLASHING -> when {
            stage == InstallStage.UPGRADING_ENVIRONMENT -> R.string.ultra_env_update_running
            showLog -> R.string.flashing
            else -> R.string.install_step4_patching
        }
        FlashingStatus.SUCCESS ->
            if (outcome == FlashOutcome.ENVIRONMENT_UPDATED) R.string.ultra_env_update_complete
            else if (showLog) R.string.flash_success else R.string.install_step4_success
        FlashingStatus.FAILED -> when {
            stage == InstallStage.UPGRADING_ENVIRONMENT -> R.string.ultra_env_update_failed
            showLog -> R.string.flash_failed
            else -> R.string.install_step4_failed
        }
    }
