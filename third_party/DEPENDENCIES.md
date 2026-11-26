# 第三方来源与许可

此清单说明当前源码的来源边界。版本以固定清单和实际构建输入为准；下载摘要用于完整性检查，许可与对应源码另行核对。

| 组件 | 来源与固定版本 | 使用范围与许可 |
| --- | --- | --- |
| 7kimisu | [源清单](7kimisu/source-manifest.json)，`dafd0a96ab1ad37cb4aa692b4081bf68e231d6a7` | Manager 直接派生基础；[GPL-3.0-or-later](7kimisu/LICENSE)，保留[原说明](7kimisu/UPSTREAM-SOURCE-NOTICE.md) |
| KernelSU | [清单与补丁](kernelsu/manifest.json)，`528bafc5696196f3a9500cee2e7e18497421cd32` | Normal 主要实现；[用户态/管理器 GPLv3](kernelsu/upstream/LICENSE)、[内核 GPLv2](kernelsu/upstream/kernel/LICENSE)，具体版本选择见文件头 |
| FinaSU | [官方仓库](https://github.com/LyraVoid/FinaSU) | UI/交互主要参考，主题格式兼容；直接代码来源按对应文件和管理器派生清单记录 |
| Shark | [源码与逐文件摘要](../jailbreak-providers/shark/manifest.json)，`2af72cb8959fae5f1c7f929a649db5e546334cef`（v2.2.2） | 98260 逐内核偏移表的来源；[GPL-3.0](../jailbreak-providers/shark/LICENSE)，保留[上游 NOTICE](../jailbreak-providers/shark/NOTICE.upstream.md)。**不分发上游 runner 二进制**：plant 由 [build_plants.py](../jailbreak-providers/shark/build_plants.py) 用 NDK 从保留源码编译，产物与源码摘要不一致时打包失败 |
| libsu service | [SOURCE.json](libsu-service/SOURCE.json)，6.0.0，`8c3e80ffe466b89ff875471e542d77e6a1b480c5` | [Apache-2.0](libsu-service/LICENSE)；源码调整和官方 trampoline 摘要见相邻 README |
| Apache Commons Compress | [固定工件清单](commons-compress/license-source-manifest.json)，1.28.0 | 模块 ZIP 静态检查；[LICENSE](commons-compress/LICENSE.txt) / [NOTICE](commons-compress/NOTICE.txt) |
| Magisk / libsepol | [固定来源](../module-policy/upstream/sources.json) | 策略处理；GPL-3.0 与 LGPL-2.1-or-later，保留各文件声明和对应源码 |
| ksu_props | [源码快照清单](ksu_props/SOURCE.json)，`ddb6ee7294467f7f25bad2118e9e24eee104144b` | 属性工具与原始 resetprop CLI；固定上游树未含根许可证及 crate license 字段，分发依据单独待核对 |
| Ultra native components | [Credits](../CREDITS.md) and pinned source manifests | Offline patch core, SU, environment and native module SDK; retained input identities in `release/native-inputs.json` |
| Capstone / LLVM | [来源清单](capstone/license-source-manifest.json) | 历史修补依赖的通知材料；BSD/LLVM 文本不替代所属外部核心的许可 |
| VioletToolBox | `Smart-Paocai/VioletToolBox`，`153a0d13a0ae855c3e682bf281940e691ef22f21` | AVB 回封实现参考与适配，GPL-3.0-or-later；[来源说明](../manager/src/main/assets/maplesu/avb/NOTICE) |
| AOSP boot/AVB | [mkbootimg 清单](aosp-mkbootimg/source-manifest.json)、[AVB 清单](aosp-avb/source-manifest.json) | 镜像格式与测试参考；BSD-3-Clause / Apache-2.0。公开 AVB 测试密钥见[相邻 NOTICE](../manager/src/main/assets/maplesu/avb/NOTICE) |
| magiskboot | [固定来源](magiskboot/source-manifest.json) | GPL-3.0，开发对照工具；不把参考二进制作为本项目源码构建产物 |
| BusyBox | [固定输入记录](busybox/PROVENANCE.md) | 原生运行工具；本地预编译输入的配置、完整对应源码与再分发材料仍需在发布前补齐 |
| Android/Maven 依赖 | [版本目录](../gradle/libs.versions.toml)、[Manager 依赖](../manager/build.gradle.kts) | 版本与传递依赖由 Gradle 解析；[verification-metadata.xml](../gradle/verification-metadata.xml)固定工件摘要，各库保留原许可 |

## 如何复核

ReZygisk、CSOLoader 和 PLTI 属于独立 Zygisk 模块的依赖，不进入 Manager 的构建与打包。对应模块包、源码和许可在[模块仓库](../docs/MODULE_REPOSITORY.md)按版本维护。

```shell
./gradlew :manager:dependencies --configuration releaseRuntimeClasspath
```

依赖树与摘要清单不代替逐项许可审查。完整发布还需要插件、原生工具、资源与最终包的对应关系，状态统一记录在[发布策略](../release/source-policy.json)。

主机侧 `image-core` / `installer-core` 无外部 Java 库；Android SDK、NDK、CMake、JDK 与 Rust 工具链由开发者安装，版本要求见[构建指南](../docs/BUILD.md)。
