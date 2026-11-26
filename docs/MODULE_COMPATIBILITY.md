# 模块兼容判定

模块包通过静态检查后，仍需按实际 SDK 与内核组合判断兼容性。`ProModuleCompatibilityRegistry` 保存固定记录；判定输入包括包身份、SDK、内核和请求动作，不依赖远程服务。

## 三种结果

| 结果 | 行为 |
| --- | --- |
| `KNOWN_INCOMPATIBLE` | 在宿主检查、安装或 WebUI SDK 调用前拦截本次请求，保留原因码 |
| `VERIFIED` | 仅对记录中实际验收通过的动作和设备组合标记通过 |
| `UNVERIFIED` | 保持未验证状态，不把静态识别或入口返回成功当作功能兼容 |

包身份同时比较归档 SHA-256、原生成员 SHA-256 和固定模块 ID。显示版本用于诊断，同一版本号也可能对应不同字节。

## 固定保护记录

`aah-market-1.9.3-20261031` 是现有应用自动隐藏模块的保护记录：

| 字段 | 固定值 |
| --- | --- |
| 模块 ID | `cd08ae9eef7bb885c06f80ef3aea1d5c` |
| 版本 | `1.9.3` |
| 原生成员 | `libmodule_app_auto_hider.so` |
| 归档 SHA-256 | `2fdc08482075a9865d49e2595a2487f07a37ab499aab744d8d9bb09a098d4a31` |
| 原生成员 SHA-256 | `a0835b667db5ed33a12b7fa2d2f8ba9ee58b8f7fd538250b62111cffd2a647dc` |
| SDK 记录范围 | `5.1.0` |
| 内核 release | `6.6.89-android15-8-g7e1f3c083cc6-abogki467167594-4k` |
| 记录变体 | `k22` |
| boot SHA-256 | `47aa147acbe3edf8e58ce60d334ad4c4b26cad26b5cb93aa6acb59a67fdd5f59` |

记录中的不兼容原因涉及目录回调返回约定、进程回调参数和返回类型与内核 BTF 不一致，以及 Hook 句柄回收和卸载回执缺失。`verifiedScopes` 保持为空，没有任何动作由此取得实测通过状态。

`k21` 仅作为已核对同一基础内核的变体列入推断范围，并保留 `KERNEL_VARIANT_OUT_OF_VERIFIED_SCOPE` 注意码。未知 release、SDK 或变体按现有匹配规则处理，维护者不从这一记录推定其他组合的结论。

以上是现有固定规则的公开说明。原始设备资料由维护者归档，本次文档整理未重新执行设备验收，也未扩大保护范围。完整匹配细节以注册表和 `ProModuleCompatibility` 的实际代码为准。

## 添加记录

新记录须固定原始 ZIP、库内身份与摘要，提供 SDK、内核、变体及动作的验证结果。已确认不兼容和实测通过分别记录；更新模块、SDK 或内核后重新核对。故障诊断保留首次异常、阶段和稳定错误码，公开材料去除凭据与无关设备信息。

开发与投稿入口见 [Ultra 原生模块指南](ULTRA_MODULES.md)和[模块仓库指南](MODULE_REPOSITORY.md)。
