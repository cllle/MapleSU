# 寄生模式接口与恢复契约

寄生能力属于 Ultra 侧增强。生产端口通过 Ultra root 通道调用原生扩展，Lite 与 Ultra
归档分别链接。App loader 复用隐藏模式的恢复脚本与宿主授权；su-env 使用独立 PATH
loader。两种载荷均不嵌入 root key。

## 调用链

`NativeBridge` 的 `parasitePrecheckApp`、`parasiteImplantApp`、`parasiteImplantSuEnv`
由 `runtime/ultra/bridge/native_lib_ultra.cpp` 实现，返回统一 JSON 回执。
`UltraRuntimeClient` → `ParasiteUltraApiAdapter` → `UltraLinkedParasiteSdkPort` 负责解析、
错误映射和前置检查，`productionParasiteSdkPort(context)` 装配生产端口。

当前集成使用 Ultra 5.1.0 扩展提供三项原生接口；旧库缺符号时继续返回
`MAPLESU_BRIDGE_SDK_SYMBOL_MISSING`，端口映射为 `ULTRA_API_UNLINKED`。接口存在、
环境 Running 与宿主能力的实际就绪分别验证。

## 运行前置条件

- Ultra 环境为 Running，配对凭据可用，所需原生符号已链接。
- 用户选择的宿主包、签名集合和安装实例通过系统查询核对。
- 临时模式的运行端引用属于当前 boot 与 attempt；重启后重新建立。
- 模式切换写者静默期停止接收新的寄生动作。

## 组件职责

| 组件 | 职责 |
| --- | --- |
| `ParasiteSdkPort` / `UltraLinkedParasiteSdkPort` | 参数、可用性、静默期和原生调用边界 |
| `UnavailableParasiteSdkPort` | 区分 `ULTRA_UNAVAILABLE`、`ULTRA_NOT_RUNNING`、`ULTRA_API_UNLINKED`、`ULTRA_ROOT_KEY_UNAVAILABLE` |
| `ParasiteHostRecord` | 宿主注册身份、进程名、库路径与独立结果位 |
| `ParasiteStateMachine` | N0–N6 状态跃迁及 reset；证据不足时保留失败 |
| `ParasiteJournal` | 追加并同步日志，容忍末尾半行并支持重建 |
| `ParasiteRecoveryStore` | 卸载前备份、来源与权限记录、摘要回读和恢复校验 |
| `ParasiteWebAssets` | Web 资源包、生成头和必需路由约定 |

`soLoaded`、`webReachable`、`serviceEstablished`、`rootCommandOk` 分别表示装载、网页、
服务和命令结果。`allFour` 只作展示汇总；状态跃迁仍检查对应条件，例如 N2 需要
装载证据，N4 需要网页和服务，N5 需要已校验的恢复备份。

## 验证与维护

主机测试覆盖回执、未知状态、状态机、备份与失败语义。设备验证另行记录进程匹配、
多进程宿主、真实装载、网页可达、服务、Root 命令、卸载恢复以及目标 ROM 的权限和
SELinux 标签。单项通过不代表完整宿主流程通过。

原生输入的版本、摘要与更新流程见根目录的 `docs/NATIVE_DEPENDENCIES.md`。
