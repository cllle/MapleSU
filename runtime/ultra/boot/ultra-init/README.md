# Ultra 静态早期入口候选

## 当前源码构建

当前生产候选读取`/.maplesu_ultra/credential`的48字节字母数字材料，通过正常`finit_module`参数传入桥；ko字节/签名保持，非编译期默认Key。读取后尝试删除文件并清空传参buffer，真实错误保留，失败仍交原init；缺该条目的旧embedded包仍传空参数。新源码/24个全OS模拟场景待本批集中验证，旧R23及上一轮静态产物不覆盖这次改动。Windows测试显式OSfixture只建模调用边界，Android构建仍使用真实系统头。

`build.py` 使用显式指定的现有 NDK、CMake 和 Ninja，在新建的独立输出目录编译本目录实际 C 源并生成 `ULTRA-INIT-BUILD.json`；已存在目录保持原样并报错，避免失败后遗留旧成功回执。输出仍是静态第一阶段入口，不包含内核桥或设备资格。

```powershell
python runtime/ultra/boot/ultra-init/build.py --ndk "$env:ANDROID_HOME/ndk/26.1.10909125" --cmake "$env:ANDROID_HOME/cmake/3.22.1/bin/cmake.exe" --ninja "$env:ANDROID_HOME/cmake/3.22.1/bin/ninja.exe" --build-dir .tmp/ultra-init-build
```

产物 `maplesu-ultra-init` 经同一入口检查 ELF64/AArch64、静态依赖、文件/内存边界、入口、不可执行栈、W^X 及 4K/16K/64K 映射；这些检查不执行目标程序。主 runtime CMake 显式提供同名 `EXCLUDE_FROM_ALL` 子目标，默认 SDK/APK 构建不自动打包它。ramdisk producer 必须同时取得正常加载的同代桥与真实目标合同，才可使用该入口。

当前失败诊断增加原 init `rename` 的真实 errno，并在后续加载/close/exec 错误前保留其顺序；rename 失败后的交接路径和原 argv/envp 保持。历史十场景见下文；新增组合错误场景需使用当前源码单独验证，不能继承 R23 运行结论。

本目录是独立、未打包的第一方源码目标。当前容器候选已消费同一路径的入口与桥文件，Manager 候选已增加对应生产调用；它们尚未由主线采用。桥、SDK 与冷启动资格分别验收。

入口仅供 PID1：原 init 备份应为根目录 `/init.maplesu.orig`，bridge 应为 `/.maplesu_ultra/bridge.ko`。备份与 `/init` 同一 parent，保留相对 symlink 解析。入口尝试原子还原 init，正常 `finit_module` 加载一次，无论加载成功、失败或文件缺失都用原 argv/envp 交接；rename 失败直接 exec 原备份，不递归执行 shim。open/加载/close 的真实错误写继承 stderr，先保存各自 errno，close 错误不覆盖加载首错；成功无额外日志。

它不挂载文件系统、不读 kallsyms、不写 sysctl/SELinux、不调整模块版本或符号，不依赖 `/system` 或 `/data`。实际 bridge 必须满足目标内核的正常加载合同。交接保证只覆盖原 init 有效且模块系统调用返回的两个分支；bridge 内核挂起/Panic、原 init 缺失/损坏不属此范围。永久 exec 失败向继承 stderr 记录 errno 和实际原 init 路径，返回126；PID1 终止会触发内核失败保护，本入口不重启或无限等待。

rename 失败时备份与 `/init` 仍同处根目录，原 symlink 的 parent 保持；但 `/init` 此时仍可能为 shim。直接 exec 备份只完成一次同 PID1 交接，不能据此认定后续再次使用 `/init` 路径的启动语义完整保全；需要真实原 init 冷启动单独验证。

## 历史 R23 验证范围

`WORKSPACE/` 链接指向开发者保留的历史证据目录，未随公开源码分发。

以下只记录 R23 当时的源与产物，当前修改另取本轮验证结果。

Android ARM64 的独立 CMake 项目提供 `EXCLUDE_FROM_ALL` 目标 `maplesu-ultra-init`，链接选项为 `-static -Wl,--no-undefined -Wl,-z,max-page-size=65536`。R23 使用 CMake 3.22.1、NDK 26.1.10909125、API31 构建过该独立目标。初代 16K/64K 对齐缺陷经 linker flag 修正后仅重链接，两份 C 对象未变；最终 ELF 无 PT_INTERP/DT_NEEDED，四个 PT_LOAD 均为 65536 对齐，三种页大小的同余、映射区间和 W^X 检查通过。这是静态格式证据，实际 loader 和各页大小设备启动仍待验证。未 strip 产物 3,179,928 B，strip-only 参考产物 436,064 B；后者未进入 ramdisk，不代表最终压缩占用。R23 时主 runtime、Manager、ksud 和镜像 producer 均未接入此目录，配套桥、打包和冷启动尚未闭合。

最少 Linux host seam 已提供，可显式设置 `MAPLESU_ULTRA_INIT_HOST_TESTS=ON` 后构建并运行 `maplesu-ultra-init-handoff-test`。R23 实际使用对应选项的直接 cc 命令编译/运行一次，没有执行 host CMake 目标。十个场景覆盖正常、模块缺失、加载错误、close 错误、双错 errno/诊断顺序、rename 失败、exec EINTR、两条路径的永久 exec 失败和非 PID1，验证 argv/envp 原指针与内容。所有生产 OS 调用由 wrapper 接管，没有实际改 `/init`、加载模块或执行程序；CMake 页对齐修正后没有重复 host 测试。

已归档证据：[R23 验证报告](WORKSPACE/docs/dual-mode/impl/_work/2026-11-16/evidence/source-continuation-r23/REPORT.md)、[最终摘要](WORKSPACE/docs/dual-mode/impl/_work/2026-11-16/evidence/source-continuation-r23/FINAL-SUMMARY.json)。静态运行库/头输入记录不是每个归档成员的完整 link map。当前桥来源、目标正常加载合同和 producer 集成缺口见 [R26 闭包评估](WORKSPACE/docs/dual-mode/impl/_work/2026-11-16/evidence/source-continuation-r25-r26/ultra-init-closure/REPORT.md)；R26 只更新文档，没有追加编测或设备证据。

参考材料与方案：[R23 计划](WORKSPACE/.tmp/p2-ultra-init-entry-r23/PLAN.md)。仅复用固定 ksuinit 的同进程交接模型，未修改或链接它的通用模块加载器。
