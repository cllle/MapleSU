# 五态共享状态聚焦测试

从工作区真实 `src/maplesu_core` 编译 installer、list、module_state 和 fork helper，测试不复制其函数体或另写解析器。输出目录需显式指定，例如：

```powershell
py runtime/maplesu-sdk/sdk/tools/tests/module_state/run.py --out C:\Temp\MapleSU-module-state
```

运行环境为当前 Windows 构建机、sdk_common 配置的 NDK r26b，以及已有 Docker 镜像 `sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e`（GCC 14.2）。脚本使用 `--pull never` 和关闭网络的只读容器，SDK/头文件只读挂载，`/data` 是容器临时文件系统。构建输出写到 `--out`，不部署正式产物或访问设备。

190条断言覆盖 STATE01–04：Key16五态路径、原格式/错误语义、close失败传播、真实安装/卸载 fork、list优先级及Key17单模块状态/描述覆盖。随后按真实manifest参数用NDK编译，idx56保持两阶段IR→ELF，idx55执行list+state组合，验证5个内部隐藏状态函数唯一且无未解析的状态符号。

主机替身只位于外部边界：Key getter、Root/reset/callback、Android文件标签、ZIP与ELF元数据解析。真实文件访问、目录遍历、状态变更、fork/管道由生产函数执行。主机标签失败是注入测试，ZIP/ELF格式与设备Root/SELinux没有由此验收。GCC的packed警告保留；Android ABI由NDK原头/布局断言编译验证。

boot/single编排、连续启动run-once、正式helper/APK、设备冷启动和并发验收属于后续独立批次。历史旧实现49条断言和两个已复现缺陷记录于 `notes/MODULE-STATE-INTEGRATION.md` 所引用的证据，不在当前源码中保留第二套旧实现。
