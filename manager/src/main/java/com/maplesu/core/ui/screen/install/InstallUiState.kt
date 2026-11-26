package com.maplesu.core.ui.screen.install

import androidx.compose.runtime.Immutable
import dev.maplesu.manager.installer.InstallPartition
import dev.maplesu.manager.installer.InstallPartitionPolicy
import dev.maplesu.manager.installer.InstallTargetDecision
import dev.maplesu.manager.installer.InstallTargetMode
import dev.maplesu.manager.installer.LenovoSigningOptions

/**
 * 安装页（步骤 ①②③）的页面状态。
 *
 * 页面只承载**用户的选择**与**纯函数算出来的结论**：
 * - [targetMode] 是步骤 ① 的目标工作模式（默认 Ultra）；
 * - [partition] 是步骤 ① 的实际目标载体；更新保持已观测载体，切换进入原载体恢复事务；
 * - [bootFlow] 是 Ultra boot 的步骤 ③；init_boot 使用 [initBootKmi] 选择对应目标的内嵌组件；
 * - [normalDecision] 是 Normal 的步骤 ③（设备 KMI + 分区 → 能不能装），由纯函数
 *   `InstallTargetDispatch.decide` 算出，页面只投影。
 */
@Immutable
internal data class InstallUiState(
    val installMethod: InstallMethod?,
    /** Both sources are offered before execution requests actual Root access. */
    val installMethodOptions: List<InstallMethod>,
    /**
     * 步骤 ③（Ultra boot）：从源镜像识别可修补的内核版本。
     * init_boot 没有 kernel，组件选择与实际错误由执行链处理。
     */
    val bootFlow: BootFlowProfile? = null,
    /** 步骤 ①：目标工作模式（默认 Ultra，与 `InstallTargetMode.DEFAULT` 一致）。 */
    val targetMode: InstallTargetMode = InstallTargetMode.DEFAULT,
    /** 步骤 ①：提交时快照的实际目标分区。 */
    val partition: InstallPartition = InstallPartitionPolicy.NORMAL_DEFAULT,
    /** 按当前槽位拓扑或已选镜像头确定的分区选项。 */
    val partitionOptions: List<InstallPartition> = InstallPartitionPolicy.NORMAL_SELECTABLE,
    /** 目标模式 / 分区选择是否可操作（安装或切换进行中禁用，但仍然可见）。 */
    val targetSelectable: Boolean = true,
    /**
     * 步骤 ③（Normal）：本次直装/选文件能不能装（纯函数结论）。
     *
     * 仅作页面提示；实际源镜像、KMI 与组件在执行阶段重新核实。
     */
    val normalDecision: InstallTargetDecision? = null,
    val migrationEntry: Boolean = false,
    val managerInstalled: Boolean? = null,
    val directAccessReady: Boolean = true,
    val sourceLoading: Boolean = false,
    val sourceAccessFailed: Boolean = false,
    val sourceAccessFailure: InstallSourceAccessFailure? = null,
    val sourceUsesManagedRoot: Boolean = false,
    val restoreOriginalImage: Boolean = false,
    val originalManagers: List<OriginalRootManager> = emptyList(),
    val originalManagerPackage: String? = null,
    val advancedExpanded: Boolean = false,
    val vivoDevice: Boolean = false,
    val vivoInputs: dev.maplesu.manager.installer.VivoCompatibilityInputs = dev.maplesu.manager.installer.VivoCompatibilityInputs(),
    val lenovoSigning: LenovoSigningOptions = LenovoSigningOptions(),
    val observedCarrier: String? = null,
    val observedSlotSuffix: String? = null,
    val availableCarriers: Set<String>? = null,
    val bridgeFailure: String? = null,
    val initBootKmi: String? = null,
    val initBootKmis: List<String> = emptyList(),
) {
    val directInstallLabel: Int get() = com.maplesu.core.R.string.direct_install

    /** Ultra consumes observed partition choices; Normal retains its existing policy. */
    val partitionFixed: Boolean get() = if (targetMode == InstallTargetMode.ULTRA)
        partitionOptions.size <= 1
        else InstallPartitionPolicy.isFixed(targetMode)

    val ultraRoute: UltraInstallRoute get() = ultraInstallRoute(UltraInstallAction.AUTOMATIC, partition.wire,
        observedCarrier, availableCarriers, installMethod == InstallMethod.DirectInstall, bridgeFailure)

    val hasInstallSource: Boolean
        get() {
            if (!installMethodOptions.any { it.javaClass == installMethod?.javaClass }) return false
            return when (val method = installMethod) {
                is InstallMethod.SelectFile -> method.uri != null
                InstallMethod.DirectInstall -> directAccessReady && !sourceLoading
                else -> false
            }
        }

    val canInstall: Boolean
        get() {
            if (!targetSelectable || !hasInstallSource) return false
            if (targetMode == InstallTargetMode.ULTRA) {
                if (ultraRoute.blocker != null) return false
            }
            if (lenovoSigning.enabled && installMethod is InstallMethod.SelectFile &&
                !lenovoSigning.fileInputsReady(partition.wire)) return false
            return true
        }
}

@Immutable
internal data class InstallScreenActions(
    val onBack: () -> Unit,
    /** 步骤 ② 选了某个 boot 来源（文件来源会立刻打开系统文件选择器）。 */
    val onSelectMethod: (InstallMethod) -> Unit,
    val onSelectBootImage: () -> Unit,
    val onSelectInitBootKmi: (String?) -> Unit = {},
    /** 步骤 ① 选了目标工作模式（Ultra / Normal）。 */
    val onSelectTargetMode: (InstallTargetMode) -> Unit = {},
    /** 步骤 ① 选了实际目标分区。 */
    val onSelectPartition: (InstallPartition) -> Unit = {},
    /** 「下一步」：选文件修补并导出；直接安装修补并写入。 */
    val onNext: () -> Unit,
    val onRestoreOriginalImageChange: (Boolean) -> Unit = {},
    val onOriginalManagerChange: (String?) -> Unit = {},
    val onToggleAdvanced: () -> Unit = {},
    val onLenovoSigningChange: (Boolean) -> Unit = {},
    val onConfigureLenovoSigning: () -> Unit = {},
    val onSelectSigningImage: (String) -> Unit = {},
    val onSelectVivoImage: (String) -> Unit = {},
)
