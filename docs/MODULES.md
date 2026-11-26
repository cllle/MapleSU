# 模块与 WebUI 指南

先选择目标模式，再选择模块格式。脚本模块、Ultra 原生模块和主题包由不同的导入路径处理，文件扩展名相同不代表运行方式相同。

| 内容 | 接入路径 | 指南 |
| --- | --- | --- |
| KSU 脚本模块 | 根目录 `module.prop` 与生命周期脚本 | 本文及 KernelSU 模块文档 |
| Ultra 原生模块 | 根目录的 ARM64 模块共享库与静态描述信息 | [Ultra 原生模块](ULTRA_MODULES.md) |
| 应用 Root 调用 | Binder 客户端 | [SDK 接入](SDK.md) |
| `.fpt` 主题 | 主题商店和主题导入器 | [主题仓库](THEME_REPOSITORY.md) |

## KSU 脚本模块

保持 KernelSU 的标准 ZIP 布局，元信息在 ZIP 根目录：

```text
module.prop
customize.sh
post-fs-data.sh
service.sh
uninstall.sh
webroot/
```

按需要提供脚本；不为不存在的能力放置空脚本。最小元信息示例：

```properties
id=maplesu.example
name=MapleSU Example
version=1.0.0
versionCode=1
author=Example contributors
description=A minimal module metadata example.
```

`id` 以英文字母开头，后续使用字母、数字、点、下划线或连字符，至少两个字符；已发布后保持稳定。`versionCode` 用于版本比较，提供时使用有符号 32 位整数并随更新递增。

Normal 对接 KernelSU 的模块生态，按根目录 `module.prop` 识别脚本模块；包内普通原生文件交给模块使用，不作为 Ultra 原生模块入口解析。Ultra 只对经过适配的模块声明兼容；依赖特定挂载、策略、注入或启动时序的模块需要逐项验证。参考 [KernelSU 模块开发文档](https://kernelsu.org/guide/module.html)，对 MapleSU 的差异以当前源码和发行说明为准。

Normal 持久安装或模式切换在报告需要重启前部署并校验 ksud、busybox 和配套库。重启后由 init 调用 ksud 执行模块生命周期，无需打开管理器。模块仍受 KSU 的启用状态、安全模式和启动条件约束；分区挂载需要已启用的元模块。临时接管仅覆盖当次启动，跨重启运行需要完成持久安装。

## 安装前检查

Manager 会检查 ZIP 路径、重复条目、链接、完整性、体积和格式歧义。Ultra 原生入口与根目录 `module.prop` 同时出现时，不把它当成可以任选安装器的通用包。

当前生产检查器的上限为：压缩包 256 MiB、总展开 512 MiB、单条目 128 MiB、20000 条目、`module.prop` 64 KiB。Ultra 原生描述字段和 ELF 还受额外限制；静态通过不代表加载和运行验证已经完成。

Normal 的属性预检按 KSU 接纳重复属性并保留最后一个值；非整数或超出 32 位范围的 `versionCode` 保留原文，管理器的数字字段按 0 处理。Normal 安装等待脚本退出，支持显式取消；安装结束后重新读取模块列表。符号链接、重复 ZIP 条目、压缩方法和容量仍受上述预检规则约束，归档接纳范围与 KSU 并非完全等价。

## WebUI

模块可在 `webroot/` 提供页面资源。静态资源、脚本执行桥和 Ultra HTTP 回调不是同一接口；先确认目标后端开放的能力，再调用。页面只展示真实可用的设置，避免把其他实现中的专属开关作为空功能复制过来。

Normal 的标准入口是 `webroot/index.html`。纯 HTML 页面及相对路径引用的 CSS、JavaScript、图片等资源由管理器直接读取，无需模块启动 HTTP 服务或提供 `service.sh`。需要 Root 操作的页面可使用 KSU WebUI 桥；冷启动快捷方式会等待模式识别和后端就绪后再打开页面。

外部链接、执行动作、文件访问和输入输出应在模块说明中写清楚。包内附带 WebUI 对应源码及其许可，更新时保持页面与原生/脚本行为一致。

## 上传与维护

模块由贡献者上传并维护。提交包、固定版本的源码、许可证或明确的分发许可、更新说明和经过验证的设备/模式范围。不要通过改包名、删署名或重新压缩来替换原作的来源说明。

提交流程、维护者登记、版本更新和侵权删除入口见[模块仓库指南](MODULE_REPOSITORY.md)。源码和包的下载校验不替代作者权属确认。
