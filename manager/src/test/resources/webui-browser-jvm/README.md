# 外部浏览器 WebUI 接线回归

本回归使用未修改的固定 KernelSU JS API，覆盖真实 HTTP handler、外部文档会话、注解分派、文件对象、PM、命令和下载实现；同时穿过 daemon 的认证与转发层。

这是 host 测试，不是 Android 设备或实际浏览器验收。Android、libsu shell、PackageManager binder 结果与 Node DOM 为明确夹具；文件读写使用实际临时文件。测试不会调用 Gradle、ADB、刷写、SU 或修改设备状态。

## 运行

前置：PowerShell 7、JDK 17+、Node.js 18+、可运行的 `curl.exe`。项目所用 Kotlin 编译器及测试依赖须由调用方传入，脚本不下载依赖。

在仓库根目录调用 `manager/src/test/resources/webui-browser-jvm/run.ps1`，传入：

| 参数 | 内容 |
| --- | --- |
| `Java` | JDK 的 `java.exe` 完整路径 |
| `CompilerClassPath` | Kotlin 编译器与编译器依赖，分号分隔 |
| `DependencyClassPath` | Android SDK、Kotlin stdlib、coroutines、JSON、JUnit、libsu、AndroidX 及 Manager 外部依赖，分号分隔 |
| `ProductionClassPath` | 已有同代 Manager Kotlin/Java class 目录及资源 class jar；目录作为 Kotlin friend paths |
| `OutputDirectory` | 全新的证据目录，已存在则退出 |
| `Node` | 可选 Node executable，默认 `node` |

依赖参数与项目其他 `webui-*-jvm/run.ps1` 保持一致。生产类用于本专项之外的依赖；本专项 WebUI、HTTP 和 daemon 转发源码会重新快照和窄编译，不依靠旧 WebUI class 实现通过。

执行过程：

1. 快照并 SHA-256 绑定所选生产源码及夹具。
2. 窄编译；运行 9 个 JUnit 用例。
3. 通过确定性即时 RPC 响应，验证固定 KSU `spawn()` 调用方安装监听后才接收事件。
4. 启动本机临时 HTTP 服务，执行未修改 KSU JS → 完整生产 `modbridge.js` → 生产 Kotlin 对象调用链。
5. 再以 daemon wire handler 执行同一调用链，使用历史非 64hex 外部 token 验证外部链接与内部认证 token 的区分。
6. 关闭夹具服务；产出参数、日志、输入哈希和 `RESULT.json`；若运行期间输入改变则报告真实失败。

## 覆盖

- 模块 HTML 的桥注入先于模块首脚本，保留 doctype、CSP、同源 Referer 与真实外部 token。
- `ksu.io()` 全类型接线：独立 File、输入流、输出流与追加、RAF；跨文档 stream handle 隔离。
- `ksu.pm()` 复用真实实现，保留系统/用户过滤及缺包错误；context-free PM 源分别验证 int/long flags 和显式 Android user。
- 同步 exec、同步 options、两参数 callback、三参数 callback、spawn stdout/exit、命令关闭取消。
- 独立 `ksu_download`：chunk 下载、legacy 下载、超过 HTTP 1 MiB 上限的 legacy 数据分块、取消与缓存回收。
- 模块提交、模式 activation、源 generation、源退役、模块 disable、页面 close 的会话退役。
- Origin、注解方法白名单、真实错误、资源回收；一页退役不影响另一页。

## 边界

未验证真实 Android WebView/Chrome popup、fullscreen、下载交互、MediaStore、真实 app_process 启动、root provider 和 PackageManager binder、月虹/Zygisk-Next 模块原包，或卸载 Manager 后的整机冷启。上述项目需由主线在冻结源码构建后上机验收。管理器原生 document-start proof 守卫另由原有 WebUI 回归覆盖；本夹具不替代该验收。
