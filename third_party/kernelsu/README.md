# KernelSU · Normal 后端

Normal 模式主要使用 [KernelSU](https://github.com/tiann/KernelSU) 的实现。管理器和用户态保留 GPL-3.0-or-later，内核部分保留 GPL-2.0-only；完整声明见原文件和 [upstream/LICENSE](upstream/LICENSE)、[upstream/kernel/LICENSE](upstream/kernel/LICENSE)。

源码基线为 `f4603d14bd7f3bf21206d6f7c622ddf1c049ade3`，选自 2026-11-17 官方成功的 [Build Manager #5545](https://github.com/tiann/KernelSU/actions/runs/44010950397)。来源、原始文件哈希、补丁与本地适配记录由 [manifest.json](manifest.json) 统一描述。

MapleSU 身份、UAPI 7 与已有功能补丁保留；`0017` 的 SELinux 回移由新上游替代，归档记录见 `patches/7k/patches.json`。2026-11-17 的 Normal 生产产物已实际构建并集成：八份 Normal LKM、六份 companion、ksuinit、maplesu_vrinit 和三份用户态/JNI，共19项。来源见 [normal-production.json](artifacts/normal-production.json)，原 LKM 收据及当前用户态差异的精确复用证明分别保留，不将旧字节重标为新构建。

`scripts/rebuild.sh --source-only` 只生成源码与 manifest；`--verify-only --source-only` 是集中验证时的源码入口。全量同代构建入口 `scripts/build_all.sh --kmi all` 先检查源码，再产出官方八组 Normal LKM、既有六组 Ultra companion、用户态与 JNI，最后更新 payload；部分 KMI 构建保留原 payload。CI 模板 `ci/build-kernel.yml` 复用同一选源矩阵；源码重建不提升已有二进制的来源代次。

已注册的远程生产入口为 [Build Normal production](../../.github/workflows/build-normal.yml)，支持手动 `workflow_dispatch` 及推送到 `main`。它重建 `applied/` 并核对固定来源，按 `ksu_common.py` 矩阵调用现有脚本，使用 `20260828` DDK、NDK r29、Rust 1.99.1。首次 run `44069942181` 的 source 与八个 LKM 成功，package 在锁文件缺边处失败；补齐 ksud → flate2 后，run `44074782699` 成功完成用户态/JNI、16份实际内嵌资产及完整 payload 门禁。第二次通过 `lkm_source_run` 复用已绿模块：同仓同 workflow、原 run/源码/每件资产身份必须吻合，全文只允许 Cargo.lock 差异，kernel 完全一致，原14份收据保原身份。实际导出经独立解压与字节核验后已集成，见[交付报告](../../docs/2026-11-17-image-patcher-delivery-report.md)。设备启动与 Root/SU 尚未验收；payload 内额外的 L3 JNI 和 gen2 标记仍保留各自历史来源，不属于本次19项构建。

## 文件目标的 KMI 选择

Normal 的 Manager 矩阵与实际八组 LKM 一致。带 kernel 的 `boot` 文件从该文件内核解析 KMI；解析失败返回 `PRODUCE_TARGET_KMI_UNAVAILABLE`，不使用运行设备 KMI。无 kernel 的 `init_boot` 保留显式目标 KMI 或 ksud 当前设备默认值。当前分区直接安装继续使用设备事实，系统 SDK 版本不在多个 KMI 间代选。

持久补丁 `0025-target-kernel-kmi.patch` 使 ksud 默认先识别替换 kernel，再识别显式 boot 文件的 kernel；显式 `--kmi`、自定义模块及 `--no-install` 的既有合同保留。该增量的首次 run `44091331327` 在真实 host 测试编译暴露既有 vendor_boot_tests 缺少 lz4 直接测试依赖；补齐已锁版本的 dev-dependency 后，run `44095205135` 已通过用户态/JNI、payload 与真实 `patch()` 专项1项并导出19资产，已独立核验19资产及16内嵌字节并正式窄集成44路径，旧材料保留本轮前像。原14份模块只有在完整 kernel 源与原构建一致、逐项收据吻合时才复用；Cargo.toml 和锁文件额外差异限定为实际必要的测试依赖/直接边。专项验证内嵌模块字节及目标内核保全；生产结果以随包来源与验收收据为准。

## 目录

| 路径 | 用途 |
| --- | --- |
| `upstream/` | 固定版本的上游源码与通知 |
| `patches/7k/` | 按 `series` 顺序应用的适配补丁 |
| `scripts/` | 补丁应用、源码与产物核对、JNI/用户态/内核构建入口 |
| `ci/` | 原生与 KMI 构建所需配置 |
| `applied/` | 本机生成的应用补丁后源码，不作为另一份手工维护的源 |
| `lkm/`、`artifacts/`、`ksu-payload.json` | 本机构建与产物身份记录，不由公开源码树携带 |

## 重建输入与原生输出

`apply_patches.py` 从固定 `upstream/` 与补丁生成 `applied/`，在应用每个补丁前检查可应用性，并校验 UAPI 与路径。已有生成目录时须先确认内容；不要叠加重复补丁或手改清单摘要来通过验证。

```shell
python third_party/kernelsu/scripts/apply_patches.py --help
```

JNI、ksuinit/ksud 与内核模块分别由 `build_jni.sh`、`build_ksud.sh` / `build_userland.sh`、`build_lkm.sh` 构建。它们需要对应 NDK、Rust 依赖和内核/DDK输入；内核模块还与 KMI 和 Manager 签名身份相关。最终产物的摘要、来源和 APK 打包身份须一致。

The complete Manager retains its native SDK, provider and tool inputs. Follow the root [build guide](../../docs/BUILD.md) and verify the source archive before release.
