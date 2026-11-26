# Ultra 网页管理器（H2）服务端

Ultra 模式（A）的网页管理器：由 libsu `RootService` 长驻 root 进程托管 HTTP 服务，
App 进程退出不影响；不依赖 ksud/Ultra。

## 入口与鉴权

- 只绑定 `127.0.0.1`，端口候选 `18427/18437/18447/18457`（`LiteWebConstants.PORT_CANDIDATES`）。
- 路径前缀固定 `/x7k9f/<64hex token>/`；token 由 root 进程生成，写
  `/data/adb/maplesu/webadmin.conf`（0600），App 只在内存持有。
- Host 头只接受 `127.0.0.1` / `localhost`（可带端口）；只允许 GET/POST；
  token 不进日志（`LiteWebRedaction`）；请求头 16 KiB、请求体 1 MiB 上限；静态路径防穿越。

## API（第一版，路由与 7K webadmin 同名）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ping` | 存活探测 |
| GET | `/api/status` | `{mode,root,moduleCount,hidden,uapi,serverVersion}`（`uapi="lite"`） |
| GET | `/api/modules` | Ultra 模块列表（JSON 数组，只读） |
| GET | `/api/superusers` | 已授权 uid 数组（只读，前端 `Array.isArray` 契约） |
| GET | `/api/sulog` | Ultra 无日志源：`{available:false,reason,lines:[]}` |
| GET | `/api/hidden` | `{hidden,webuiPort,recovery:{apkBackupPresent,apkShaValid,daemonRunning,bootPersistence},steps}` |
| POST | `/api/hidden` | 仅取消隐藏 `{enable:"0"\|false}`；`{enable:"1"/true}` → 409 `USE_ACTIVATE_API`（隐藏必须走 activate） |
| POST | `/api/activate` | 一键隐藏事务 `{port?/webuiPort?, confirm:true}`：备份 sha→应用端口→startDaemon→ping→写 ON→复核；失败逆序回滚，不半隐藏 |
| POST | `/api/restore` | 一键恢复 `{action:"full"\|"unhide"}`：sha 校验→`pm install -r`→回读包→写 OFF→恢复组件→验证；幂等 fail-closed |
| GET/POST | `/api/config/port` | WebUI 端口 `{webuiPort}`（默认 18427，1024–65535；旧别名 `recoveryPort` 兼容）；POST 重启监听并回读 |
| POST | `/api/config/token/reset` | token 轮换（旧链接失效）；未接线 501 |
| GET/POST | `/api/settings` | 白名单 UI 项（风格/主题/下雪/壁纸）；根/安全项 403 |
| GET | `/api/diagnostics` | 只读：probe/rootBackend/webuiPort/version/mode + apkBackupPresent/apkShaValid/daemonUptimeSeconds/bootPersistence |
| GET | `/api/workmode` | 当前模式/能否切换（只读） |
| POST | `/api/module/toggle` | 模块启停 `{id,enabled}` JSON；后端失败 409/503 |
| POST | `/api/module/action` | 模块动作脚本 `{id,action}`；后端不可用 501 |
| GET | 其余静态路径 | `assets/liteweb/`（`/` → `index.html`） |

其它 `POST /api/<any>` 一律 501 + 原因（诚实降级，不伪造成功）。
所有写接口需 POST + JSON + token + Host 校验；无用户可见的"演练/恢复自检"字段——
恢复通道检查在 activate 事务内部完成（备份 sha、daemon ping、bootPersistence 判定）。

WebUI 配置：`/data/adb/maplesu/webui.conf`（root 进程 0600，`schema=2`，含 token + webuiPort；
旧 `webadmin.conf` 只读迁移）。端口仅 loopback（127.0.0.1）。

## 未实现项

- **`/modweb` 模块 WebUI 代理：未实现**。当前返回 `501 MODWEB_PROXY_UNIMPLEMENTED`
  （前端入口已隐藏；`modbridge.js` 仅保留）。后续若实现，需要先有模块目录映射与路径穿越
  防护的服务端代理。
- 上传安装、卸载、动作脚本（`/api/module/action` 依赖 LiteScriptModuleBackend 动作实现）、
  授权修改等 KernelSU 写接口：后端未接线时 501。
- sulog：Ultra 不记录 su 日志，返回 `available:false`。
- activate/restore 的 wm-core 端口（FullHideTransaction/RestoreTransaction/RecoveryBundle、
  daemon 启动、APK 备份/重装）未接线时显式 501（`*_BACKEND_UNAVAILABLE`），不假装成功。

## 测试

`manager/src/test/java/dev/maplesu/manager/liteweb/` 为纯 JVM 用例
（`LiteWebAuthTest` / `LiteWebHandlerTest` / `LiteWebServerTest` / `LiteWebHiddenCountTest` /
`LiteWebActivateTest`），覆盖鉴权、Host、token、脱敏、解析上限、端口回退、真实 loopback 请求、
超时、隐藏 GET/POST、activate 事务成功/各步失败回滚/前置拒绝、restore 幂等与失败、端口配置、
settings 白名单/403、diagnostics 形状、modweb 501 与 webuiPort 命名。
