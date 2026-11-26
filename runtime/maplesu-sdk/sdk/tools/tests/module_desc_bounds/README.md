# 持久描述字段边界测试

```powershell
py runtime/maplesu-sdk/sdk/tools/tests/module_desc_bounds/run.py --out C:\Temp\MapleSU-module-desc
```

直接编译工作区真实 list、共享 state、描述 writer 三个生产 TU。既有 module_state 夹具只替代 Key getter、Root 和 Android 标签操作；ELF metadata parser 提供固定字段，便于逐字节验证描述前后的 name/version/author/id/update/webUI/最低版本。文件写入、描述覆盖读取、目录遍历和返回记录由生产函数执行。

11组输入各在 fortify 和 ASan 两种主机构建中运行：0/1/64/4095/4096/4097/8192/65536字节、两组文件内部 NUL、nullptr 恢复描述。每个案例独立进程。检查 writer 原内容/标签调用、描述容量与结尾 NUL、截断后内容/零填充、整个相邻 metadata 和状态。含内部 NUL 的持久文件由夹具直接写入；writer 的 C 字符串语义不变。

固定 GCC 镜像、无网络、只读根文件系统、SDK/公开头只读挂载，`/data` 为临时文件系统。fortify 和 ASan 的诊断分别记录，不把非零退出自动视为 ASan 报告。随后按当前 m055 manifest 的真实 profile/extraFlags 用 NDK 编译 list+state，并 `ld.lld -r` 合成，所有对象和完整命令写到显式输出目录。runner 以非零退出报告边界失败，仍保留基线对象与 RESULTS.json。

测试没有部署或修改正式 SDK/helper/APK/manifest，也没有访问设备。主机数据边界和 Android 目标编译不等同设备 Root、ELF 格式解析或模块启动验收。
