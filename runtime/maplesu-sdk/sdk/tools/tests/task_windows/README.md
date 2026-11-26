# task 本地扫描窗口

```powershell
py runtime/maplesu-sdk/sdk/tools/tests/task_windows/run.py --out C:/Temp/MapleSU-task-windows --ndk
```

runner 先保存当前完整 `task_struct.cpp`、测试、错误码头和构建清单，再按函数边界提取 `get_task_struct_stack_offset()` 与 `get_task_struct_mm_offset()` 的完整原文，生成实际被编译的 `extracted.cpp`；收据记录生产文件及两个片段的 SHA256、行号和命令。没有另写一份扫描实现。

夹具只替代缓存、comm 偏移来源、符号、版本分支、内核读取和 mm shellcode 执行边界。mm emitter 的错误返回顺序仍由实际被提取函数驱动；本测试不执行 AArch64 指令，不验收 `get_task_mm/mmput` 的实际内核语义。函数自己的向量分配、8B 复制、循环、匹配、错误返回和缓存提交均来自生产函数。

覆盖 stack/mm 的 0/1/4/7/8/12/16/20B 无匹配窗口、最后完整窗口匹配、偏移0旧语义、缓存成功/读取失败/写入失败、现代/旧内核符号路径、comm/符号/读取及 mm emitter/执行/当前任务错误。每例独立进程；同一固定容器批量运行，开启 ASan/UBSan，逐例保存日志与退出码。使用零长度窗口时读边界允许 size=0，不人为添加缓冲区填充。

`--ndk` 使用当前 m001 manifest 的实际 profile/extraFlags，对完整生产 TU 做 Android NDK 语法检查。输出只写指定目录，不构建正式 SDK、helper、APK，不访问设备。缓存及 offset0 行为按原函数保留；这项测试仅证明本地窗口边界和分支合同，不证明原有值匹配扫描的字段语义。
