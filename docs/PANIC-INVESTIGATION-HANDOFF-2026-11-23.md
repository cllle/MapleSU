# MapleSU 空闲 Panic：调查、修复及交付交接

公开快照约定：`WORKSPACE` 为源码工作区占位，`WORKSPACE_BUILD_PRESERVE` 为独立构建证据目录占位，`SERIAL` 为测试设备序列号占位；私有证据按原路径后缀定位。

更新时间：2026-11-23 UTC。工作区：`WORKSPACE`。

## 1. 接手结论与任务边界

**已定位直接致死点，根因尚未证实。** 本文是交给独立 Agent 的开发任务书，不是“已修复”报告。

用户报告设备放置不操作也会自动重启。最新完整现场证明：MapleSU 的动态代码执行通道在 `BLR X10` 后、payload 第一条 BTI 指令执行前，触发 EL1 instruction abort / level 3 permission fault，随后 Kernel panic。现有日志不足以把原因定为缺少屏障、提前释放、地址串用、厂商保护或某个上层 SDK 功能。

用户最新安排：**Panic 交由另一个 Agent 专项处理；本主线继续其余源码整合、构建与验收。** 两边共享工作区，先约定写集和输入冻结窗口；Panic Agent 不自行覆盖主线产物或并发刷机。Panic 保持独立未验收状态，其他项目通过不抵销它。

目标：绑定真实故障生产者 → 找到首个错误状态转移 → 最小源码修复 → 同路径红绿回归 → 同代产物 → 原机验证 → 可复核交付。

## 2. 必须保留的项目合同

- 先读 `WORKSPACE/AGENTS.md`，依据固定 KernelSU 实现与本项目真实调用链，不凭 README 增加架构前提。
- 保留真正启动保护、真实错误、Root Key、授权/fullUID、模块、分区备份与故障原件。清标记、假 Ready、隐藏错误、增加等待时间或关闭功能不算根因修复。
- 未请求/未授权普通应用路径不新增全局 SELinux、挂载/命名空间、系统属性或额外常驻判定进程。
- 不把修改 ROM/framework、PMS 事务屏障等新增为 SU、模块、免 Key 或无 Manager 冷启前置。
- 生产源码、实际选源、产物、宿主回归与设备效果分层记录。静态指令正确不等于硬件 Panic 已修复。
- 只改变可解释的最小范围。保留其他 Agent 修改；修改前保存当前前像和 SHA256，提交前检查并发差异。
- 不读取或在报告/日志输出 Root Key、请求包、用户凭据或无关系统秘密。调试只记录完成因果关联所需的元数据。

## 3. 最新完整故障：事实与精确身份

### 3.1 设备与故障

| 项 | 已观察值 |
|---|---|
| ADB serial / 机型 | `SERIAL` / `PLQ110` |
| 实际内核 | `6.6.89-android15-8-g7e1f3c083cc6-abogki467167594-4k` |
| 活动槽 / SELinux | 历史采集为 `_a` / Enforcing；操作前重新读当前值 |
| 最新完整 dump | `WORKSPACE/evidence/device-panic-20261123T1041Z/current-20261123T112010Z/minidump.bin` |
| dump 大小 / SHA256 | `268435456` bytes / `7902280fce38f9304218cf03aa9a913abafe86efeeacb6eeddf2827d5677408b` |
| 致死时刻 | 本次启动 uptime `168.016315` 秒，不把采集目录时刻当作精确崩溃墙钟 |
| CPU / PID / TGID | CPU5 / `18634` / `18634` |
| comm | 前 8 bytes 为 `f0f0f0f0f0f0f0f0`，不是可直接唯一定位功能的名字 |
| PC / X10 | `0xffffffd21a87f000` |
| ESR / FSC | `0x8600000f` / `0x0f`，current EL instruction abort、level 3 permission fault |
| Kernel LR | `0xffffffd21503fef4`；符号显示 `trans_pgd_copy_el2_vectors+0x52c/0x638` |
| 故障首字 | `0xd503245f`，在首条 payload 指令执行前异常 |
| handler 打印 PTE | `0x00400009bf522783`，对应物理页 `0x9bf522000` |
| fault task 年龄 | 约 `160.868 ms`；`nvcsw=0`、`nivcsw=5` |

调用栈从动态 PC 返回 `invoke_syscall → el0_svc_common → do_el0_svc → el0_svc → el0t_64_sync_handler`。保存用户 syscall number 为 `221`；用户 PC 在 libc syscall，用户 LR 为 `0x7e02d4a894`。

**重要边界：handler 读到的 PTE 是异常处理时的页表值，不是故障瞬间硬件有效翻译权限的完整快照。** 最新 dump 未覆盖故障 payload 页，也未保存其完整请求包或用户栈；不要凭首字、长度或 F0 名字猜定生成器。

### 3.2 实际调用者已关联到常驻 generation native

崩前观测的父链为 `18634 → 18607 → 4144 → 1`。前两者为 F0 名进程，4144 为 `main`。故障 PID 的真实 maps 已捕获，不只是依据地址对齐推测。

- 设备加载路径：`/data/adb/maplesu/ultra-identity/generation-89fda3b0bf6a44098f9cc74ab3caf80d/libmaplesu_ultra_bridge.so`。
- 该文件实读大小 `11598544` bytes，SHA256：`700152c6c0bf1f3f52cd10b64198f1879ee4f8ef3d043492f080b6bb0e61d745`。
- ELF load bias `0x7e02878000`；保存用户 LR 对应 ELF VA `0x4d2894`，吻合实际 `dispatch_kernel_request` 的 syscall 返回点。
- 注意 ELF VA 与文件偏移不同：该 maps 中对应文件偏移 `0x4ce894`；不要直接把 VA 当文件 offset。
- 独立副本：`WORKSPACE/.tmp/supervision-20261123-resume4/panic-evidence/installed-700152c6.so`。
- 已有 IDA 函数分析：`WORKSPACE/.tmp/supervision-20261123-resume4/panic-evidence/IDA-FOCUS.json`。反编译类型可能失真，调用参数最终以原指令和 ABI 核对。

**尚待补齐：** 4144 的哪次上层操作创建了这两级 child，最终哪一个真实 emitter 提交了失败 EXEC。`get_root`、授权刷新等是需验证的候选入口，不是当前根因结论。

### 3.3 已装 APK、SDK、boot 与当前源码存在代差

- 已装历史 APK：5.2.1 / 50201，SHA256 `5e0971c267c339124bafc2b8bc3071a10bb0f3caad3eaf60bfd74ce1ab6b6474`。
- APK SDK10 identity：`maplesu-ultra-source-7af6f0d42a440ac7`；archive SHA256 `7af6f0d42a440ac7616d555ab788d90f728dc0fc89df46e7976c5634cf3749e4`。
- 历史运行 boot_a SHA256：`73c956c597827c6bf70f2b536c12d56de55c5663f2f6013cf39de7684fb9662d`；最早明确产品写入为 r21，不把后来的相同 boot 校验称作新修复。
- 最新只读 boot：`WORKSPACE/evidence/device-panic-20261123T1041Z/boot-readback-20261123T113323Z/boot_a.img`。
- 实际 kernel/网关反汇编：同目录 `analysis/Image`、`analysis/MANIFEST.json`、`analysis/gateway.txt`、`analysis/native_permission.txt`。
- 当前累计源码修复尚未由这份旧 APK/boot 执行。**代差只能解释测试覆盖边界，不能单独证明 Panic 原因或修复有效。**

## 4. 已核对的排除项：不要重复走弯路

1. 实际 boot 网关已经检查 `set_memory_ro`、`set_memory_x` 返回值，失败分支写 `-19999`；不是无条件执行。
2. 网关已经有范围 `TLBI VAAE1IS`、DSB/ISB 和 `IC IALLUIS`。重复加同样屏障没有现成因果证据。
3. 同类完整 7e08 现场已核 TTBR1 与 swapper 页表根一致，SCTLR.WXN=0，TCR 为4K/39位VA；该次 TLBI operand、单页步进及普通 EL1 上层/叶 PXN 未见错误。这些结论只属于那一现场，最新现场按自己的证据核对。
4. 最新 handler 打印上层 descriptor 为 `pgd/pud=1000000affffe003`、`pmd=1000000895ad1003`，叶 PTE 的普通 EL1 解读同样没有直接给出 PXN 禁止。不能从“源码置了 X”或“PTE 看起来可执行”推出硬件必已生效。
5. 最新 comm 的 F0×8 与生产 Root 命名路径有关，不是 `task_comm_scan` 随机 marker；新 fork task 年龄也不证明 task_struct 自探测就是失败点。
6. 厂商 `new_slab` WARNING 后系统继续运行，不应把它直接认作致死异常。
7. 原厂压缩 dump 曾有截断；保留它们为辅助，主分析使用上述完整256MiB原件。
8. 当前没有故障时 HCR_EL2/VMID/Stage-2 证据；不要将虚拟化或厂商安全模块写成已发生原因。
9. 旧生命周期调查没有证明某个地址被两个 pending owner 重叠使用；不要先加全局锁再声称解决串用。

旧精确分析入口：

- `WORKSPACE/.tmp/main-rootpanic-resume-20261121/panic/offline-7e08-review-20261121/REPORT.md`
- `WORKSPACE/.tmp/main-rootpanic-resume-20261121/panic/offline-7e08-review-20261121/tlbi-current-gateway/REPORT.md`

## 5. 调查与实施步骤

### A. 接管及身份基线（先只读）

1. 核主线当前工作、构建输入窗口、进程与设备唯一执行者，声明精确写集。Agent 的 isolated 输出放 `WORKSPACE/.tmp/panic-agent-20261123/`；目录已存在则使用明确的新子目录，不覆盖旧证据。
2. 对 dump、boot、native、源码和已有收据重新核 SHA；原件保持。记录当前 APK/SDK/helper/generation/boot/boot_id，区分当前状态与历史现场。
3. 精读下节列出的 analysis，不重新全盘收集或扫描无关目录。已有分析能回答的先复用。
4. 从 dump 的寄存器、栈上下文与确切 ELF 反汇编证明 `ALLOC → WRITE → EXEC` 当前走哪条分支，特别核对 LegacyKey / Owner / EarlyHelper，而不是只搜源码函数名。

### B. 构造能抓到这次缺陷的反馈环

先给出一个已运行的确定性命令或一次真实场景捕获，说明它怎样对“首次取指异常/错误页状态”判失败。只做指令形状断言或模拟迁移不足以复现硬件 Panic。

若现存 dump 缺必要上下文，在**同代真实调用链**加最小、可撤销且默认关闭的边界观测，不写与问题无关的新调试系统：

- 单调时间、boot identity、PID/TID、事务序号、确切 native caller/ELF VA。
- ALLOC 返回地址与长度/页数，完整 WRITE 完成，EXEC 前/返回，失败 RELEASE 前/返回。
- payload 长度与代码摘要；只在确需区别 emitter 且内容经检查后保存独立代码原件，不记录 Key/请求包。
- 首错、权限操作实际返回、完成/未知终态。用户态 exec-return 不冒充内核 cleanup 的实测。
- 捕获机制跨 fork 保持可用，不在 fork child 首次触发不适用的锁、TLS 初始化或复杂符号解析。
- 日志在主机持续保存；设备重启也能定位最后一条未完成事务。记录采集自身开销，不把观测版时延当正式性能。

已有最小诊断实现可参考但不是可直接装的同代最终包：

- `WORKSPACE/.tmp/main-rootpanic-resume-20261121/panic/exec-lifecycle-observation/SOURCE.patch`
- 同目录 `README.md`、`RESULT.json`、`parse-boundaries.py`。
- `WORKSPACE/.tmp/main-rootpanic-resume-20261121/panic/DELIVERY.md` 记录较早 trace-root 的具体代际与行为。

**这些诊断件绑定旧 SDK/native。接当前 generation 前重新核身份/调用 ABI，不直接混装或以旧 through 收据充当新结果。** 优先复用已编译对象和现有 parser，避免为观测重建整个库。

### C. 按证据检验候选原因

按可证伪预测逐个排查；一次只改变一个变量：

| 候选 | 必须取得的决定性证据 | 证实后的最小修复方向 |
|---|---|---|
| 页/owner 生命周期交叉 | 同boot同地址 pending事务重叠，或EXEC前确有别处NX/free；不能只看正常地址复用 | 修第一个越权/提前退役/拷贝fork状态错误，保留真实在途与失败语义 |
| 权限输入或目标解析错误 | 实际请求地址/页数、helper真实入口/ABI、配置/页大小与受修改页不一致 | 修解析/参数/范围计算，保留失败拒绝与清理，不硬编码机型偏移 |
| 有效翻译/权限发布异常 | 精确过渡后、BLR前的相关寄存器/翻译与当前descriptor存在可复核矛盾，并能区别迁移和并发 | 修已经证实遗漏的权限转换或发布步骤，避免重复泛化屏障 |
| 上层重入或错误模式选择 | 实际父操作/child/线程状态进入了不该进入的协议或退役代际 | 修真实选择/失效撤销/资源所有权，不取消该功能或要求Manager常开 |
| 厂商/EL2相关限制 | 故障同次实际运行证据与最小对照证明相关路径，而不是按模块名猜测 | 只对已证目标语义做适配；涉及新增合同先说明必要性与影响 |

若候选未证实，交付“调查未闭环”和具体缺失观测，不用猜测性代码填空。若某修复只解决源码可证缺陷，但未证明为这次 Panic 根因，两种结论分别登记。

### D. 修复与源码集成

1. 先建立能对首个错误转移报红的回归，用真实 call-site/多调用者/fork生命周期接缝；浅层单函数 mock 不替代真实缺陷模式。
2. 最小修复，保留布局、协议、认证、W^X、错误回执、回滚与资源生命周期。不要把“失败返回”改成成功来掩盖未执行。
3. 向主线交精确 diff、前/后 SHA、新增选源及依赖；主线确认写集和冻结窗口后采用。
4. 确认生成器、SDK静态成员、helper/backend、bridge、JNI、Manager 与 boot 的实际打包/装载路径包含修复。仅改一个源码副本不算产品已接入。
5. 完整构建/签名/安装/刷写由主线统一调度，或由主线明确交给唯一设备执行者；避免与其余验收互相覆盖。

## 6. 代码入口及职责

| 入口 | 路径与核查点 |
|---|---|
| Image 网关生成 | `WORKSPACE/manager/src/main/java/dev/maplesu/manager/ultrakernel/UltraKernelShellcode.kt`：`UltraKernelBodies.printfn0`、分配dispatch、`drmPrintk0`；BLR/LR偏移与metadata locator有合同 |
| SDK请求/地址登记 | `WORKSPACE/runtime/maplesu-sdk/sdk/src/kernel_request/module_base_kernel_request.cpp`：`dispatch_kernel_request`、`send_kernel_request`、`legacy_alloc`、`unsafe_request_kernel_exec_mem`、fork/reset、地址范围 |
| SDK执行器 | `WORKSPACE/runtime/maplesu-sdk/sdk/src/module_base/module_base_kernel_func_executor.cpp`：`__exec_kernel_asm_func`、standard/legacy adapter、securebits及回执恢复 |
| idle长期映射/权限 | `WORKSPACE/runtime/maplesu-sdk/sdk/src/module_base/module_base_kernel_idle_mem.cpp`：临时页和长期alias、权限切换、失败保留/释放，不与staging生命周期混淆 |
| 管理协议执行 | `WORKSPACE/runtime/ultra/identity/kernel/manager_management.c` 与其实际include：按真实请求模式追踪，不先假设本次使用Owner |
| 常驻generation调用 | `WORKSPACE/manager/src/main/java/dev/maplesu/manager/runtime/ultra/UltraIdentityBroker.kt` 与实际native入口/child创建；先对齐故障进程 |
| Root命名线索 | `WORKSPACE/runtime/maplesu-sdk/sdk/src/module/idx070/module_root.cpp`：F0命名来源，不据此唯一定位emitter |
| 发布静态窄回归 | `WORKSPACE/scripts/tests/test-ultra-executor-publication.py`：17项cache发布模型；脚本自身明确不是硬件Panic验收 |

## 7. 前置条件与设备操作规约

- 工作区、确切旧原件和最新生产源可读；磁盘与构建资源按即时值核对，不把旧空闲空间作为保证。
- 使用已有 Python/NDK/JDK/DDK、原签名和构建入口；遇到工具/依赖缺失先核本地保留路径，不要求用户重复提供现存材料。
- 设备在线且由单一执行者持有；先记录boot_id、uptime、APK及native SHA、boot分区身份和模块状态。
- 原分区备份与恢复途径先核可用。保数据升级，不卸载清数据，不清保护标记/授权/模块来制造通过。
- 新Panic触发不是默认压力测试：先用既定真实产品场景与被动捕获；任何新增反复重启/刷写步骤先与主线协调。
- 同代产物完成前可以做只读调查、宿主回归和精确TU编译；不要以此预填原机通过。
- 主线可能继续生成新的APK/SDK：每份证据固定采样时身份和时间，按新结果追加，不覆盖历史。

## 8. 验收出口

### 调查出口

- [ ] 确切boot/native/SDK/source绑定；新旧现场不混合。
- [ ] 真实父操作、child、emitter与失败EXEC关联，或精确说明仍缺哪段证据。
- [ ] 首个错误转移有原件和可重放提取步骤；明确已证/推断/未知。
- [ ] 没有将旧WARNING、F0名、旧包代差或看似正确PTE写成单独根因。

### 修复出口

- [ ] 缺陷原路径红例和修后同路径绿例；必要故障/回滚负例保持。
- [ ] 受影响TU真实编译，实际选源闭包与打包身份核验。
- [ ] 默认关闭/移除临时观测，保留可重复调查脚本及原件。
- [ ] 无关普通应用路径及项目边界无新增可见改变。

### 原机出口（新产物分别记录）

- [ ] 新APK/SDK/helper/native/boot实际读回身份匹配，同时核验本地文件和设备装载内容。
- [ ] 原始触发场景：Manager不操作/空闲运行，覆盖已观察到的约168秒触发窗口，并明确实际时长和负载；单次通过只报告单次。
- [ ] 重复真实Root/SU、授权刷新及相应模块操作，观测代码页生命周期与错误回执。
- [ ] 安全模式两次有效修复与重启、Manager未打开/未解锁冷启、模块开机收尾，与主线相应验收卡协调执行。
- [ ] 报告成功次数/总次数、boot IDs、异常及新dump；“没有看到日志”不等价于“没有Panic”。
- [ ] 性能使用无观测正式版测量；不借临时日志时延结算预算。

## 9. 交付方式（给主线一个目录，不只发结论）

推荐目录：`WORKSPACE/.tmp/panic-agent-20261123/delivery/`。

1. `REPORT.md`：结论、首错、证据、排除项、根因、最小修复及覆盖边界；未闭环项显式写出。
2. `PATCH.diff`：仅本任务改动；`FILES.json` 列绝对路径、before/after SHA、大小、新增文件及实际采用状态。
3. `REPRO.md`：可复制命令、输入身份、环境、红例/绿例、负例与真实设备步骤。新证据不覆盖旧目录。
4. `RESULTS.json`：每步argv、退出码、时间、输入/输出SHA；`sourceIntegrated`、`buildPassed`、`devicePanicAccepted`分别填写。
5. `ARTIFACTS.json`：APK版本/原签名、SDK/helper/bridge/JNI/boot的精确路径和SHA，实际readback收据；未构建/未装机不冒充已交付。
6. `ROLLBACK.md`：对应设备/槽的原备份身份、回退顺序和数据保留说明；只写已验证可用的材料。
7. 原始日志、dump索引和小型分析脚本。大原件在原位置引用并校SHA，不复制多份浪费磁盘。

交回主线时先摘要：**根因是否已证实 → 哪些生产源已实际采用 → 哪些构建/设备结果真实通过 → 仍需的唯一下一步**。

## 10. 证据导航与初始校验命令

先读以下文件即可接续，不必重新审计全部需求：

- `WORKSPACE/evidence/device-panic-20261123T1041Z/current-20261123T112010Z/MANIFEST.json`
- 同目录 `analysis/FATAL-ANALYSIS.json`、`analysis/FATAL-THREAD.txt`、`analysis/KERNEL-STACK-CONTEXT.json`、`analysis/OBSERVED-CORRELATION.json`、`analysis/NATIVE-CALLCHAIN.json`。
- `WORKSPACE/evidence/device-panic-20261123T1041Z/read-20261123T114030Z-active-identity-library/stdout.txt`
- `WORKSPACE/.tmp/supervision-20261123-continuation/requirements/DEVICE-PANIC-ARTIFACT-BINDINGS.md` 及同名JSON。
- `WORKSPACE/.tmp/supervision-20261123-continuation/requirements/CURRENT-SOURCE-VS-INSTALLED-FREEZE.json`

```powershell
$root = 'WORKSPACE'
Get-FileHash -Algorithm SHA256 -LiteralPath "$root\evidence\device-panic-20261123T1041Z\current-20261123T112010Z\minidump.bin"
Get-FileHash -Algorithm SHA256 -LiteralPath "$root\.tmp\supervision-20261123-resume4\panic-evidence\installed-700152c6.so"
Get-Content -LiteralPath "$root\evidence\device-panic-20261123T1041Z\current-20261123T112010Z\analysis\FATAL-ANALYSIS.json"
Get-Content -LiteralPath "$root\evidence\device-panic-20261123T1041Z\boot-readback-20261123T113323Z\analysis\gateway.txt"
```

现有分析脚本有固定输出路径；重跑前读脚本并把输出迁到本Agent的新目录，保留原报告。`read_device.py` 会通过 `su` 发起真实设备请求，并非纯离线读取；需要设备执行者调度，不因文件名带read就并发执行。

---

### 可直接转交给修复 Agent 的任务

> 接手 `WORKSPACE` 的独立 Panic 专项。完整阅读本文和所列最新原件，先确认写集与唯一设备执行者。定位空闲重启现场中动态执行页首次取指权限异常的首个错误转移，交付证据支持的最小源码修复、红绿回归、同代产物接线与原机验证。现有直接致死点已明确，但根因未证实；不要重复已存在的TLBI/IALLUIS、凭旧包代差宣布修复或关闭功能。主线正在构建与验收其他功能，保留并发修改，冻结前协调。按本文第9节提交完整delivery目录及真实未完成项。
