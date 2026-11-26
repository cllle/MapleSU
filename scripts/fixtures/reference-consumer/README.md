# 固定 R18 consumer 回归

本夹具仅重放历史 `R18/current20/90e7` 真实消费者边界；不是任意最新资产或最终代构建验收。Kotlin 原文件逐字节保留，不内置或生成 native/专有二进制、metadata、编译器或历史 class 目录。

## 运行

使用 Python 3.11+、显式依赖清单、原真实 claim 目录、原真实 metadata 和全新输出路径。所有输入路径由调用方提供；脚本不搜索 latest receipt，也不依赖工作区 `.tmp/supervision` 布局。先用 `--help` 查看参数；`--plan-only` 验证输入并打印 argv，不启动 Java、编译器或夹具，不创建输出目录。

必填参数：

| 参数 | 含义 |
|---|---|
| `--assets` | 外部 current20 claim 根，直接包含 `manifest.json` |
| `--assets-tree-sha256` | 整个上述目录的排序 tree identity，定义见下 |
| `--metadata` / `--metadata-sha256` | 真实 reference-abi.json 与固定 `90e7ca2424782b908cd9d82a7804222e6c6194cc0d72109ff82d54c4b1d04c0f` |
| `--dependencies` / `--dependencies-sha256` | 外部依赖 JSON 与其整文件 SHA256 |
| `--mode source` | 编译五份完整 consumer TU，再编译夹具；新 product classes 优先于支持依赖 |
| `--mode final` | 显式使用清单的 productClasspath；选用该模式不代表这些 class 已获最终代验收 |
| `--output` | 全新输出目录；现有目录及输入目录内/上级路径均拒绝 |

每次执行顺序固定：source 模式编译完整五 TU → 编译夹具 → 真实 metadata load → 379 原负例与 disabled-n 正例 → 原包 FACT_FIELDS 首红及 23 原负例。首错停止，后继 `notRun`，保留日志；末尾复核全部输入身份。运行总验收断言为 379+1+23=403；独立 load 与原包已知首红门槛另记。每份 Kotlin 内部断言及顺序保持原样。

## 显式依赖清单

JSON schema 为 `maplesu-reference-consumer-dependencies/1`。字段：

- `selectionDescription`：准确说明选择及历史/当前范围的非空文字。
- `java`：实际 Java 可执行文件 identity。
- `compilerClasspath`：按序列出 K2 compiler、stdlib、annotations、coroutines 等真实依赖 identity。
- `classpath`：按序列出实际 Manager/Java/image-core/installer-core/Android/Kotlin/serialization 支持依赖 identity。若使用历史支持类，要在 selectionDescription 明示，非当前整源码重建。
- `sources`：source 模式显式五完整文件，文件名依次集合为 UltraKernelHostDeploymentMetadata.kt、UltraKernelManagerClaimPayload.kt、UltraKernelResidentAbi.kt、UltraKernelSuComponentMetadata.kt、UltraKernelTaskProfileTarget.kt。不是抽取函数或模拟实现。
- `productClasspath`：final 模式显式的完整已编译产品依赖，位置在支持 classpath 之前；source 模式不使用它。真实最终产物尚未提供时保持空数组，选择 final 模式会报错，不回退历史类。

文件 identity 采用项目既有 `path`（绝对路径）、`bytes`、`sha256` 三字段。目录 identity 为 `path`、`fileCount`、`treeSha256`。tree 算法：递归所有普通文件，以相对 POSIX 路径排序；每项为按字段顺序 `path,bytes,sha256` 的对象；使用 `json.dumps(rows, ensure_ascii=False, separators=(',', ':'))` 编码 UTF-8 后算 SHA256。目录摘要不包含绝对根，所有文件数量及内容均受约束；不随意忽略历史 class。调用方保留自己的可信清单与摘要，不以运行时自动生成摘要充当来源证明。

## 来源与已知边界

| 对象 | 原身份 |
|---|---|
| 原夹具 | `main17-image-close/reference-consumer-r18/ReferenceConsumerLoad.kt`，110126 bytes，SHA256 `94867abcad7b847c4aa5147e57f1fe8ef9e485ec421dabf2bb4d9ad20f4500bd` |
| 原资产 manifest | SHA256 `2bc643e4fa67cb26ff3dc6d6256aa004033ae75ab7dda90374f703f79061909a` |
| 原 metadata | 23011925 bytes，SHA256 `90e7ca2424782b908cd9d82a7804222e6c6194cc0d72109ff82d54c4b1d04c0f` |
| 已绿执行 | `main10-tests/reference-consumer-r18-r18-r1/EXECUTION.json`，SHA256 `bfcb882bc3698295c093acbf6d0024225f08dd0da1c72ccf1f771f53528e47e5` |

上述相对身份原位于 `.tmp/supervision-20261121/`，仅作历史证据，不是 runner 运行依赖。永久保留不修改原收据、不重复声称403项新执行。外部真实资产和23MB metadata 应由所有者显式提供，不复制进仓库、不重造摘要事实。

403项历史通过不包括默认生产资产容器发布、全部镜像正向生命周期、目标 ABI/module load、Android UI、设备或未来最终 SDK/claim/bridge/JNI/Manager 联合门禁。`scripts/tests/test-reference-consumer-cli.py` 仅测 CLI 解析、真实输入 plan 和 source-first 命令合同；Java/夹具执行为0，不替代403项。
