# maplesu-ghostlock — P0 host-side contract layer

**NOT FUNCTIONAL / NOT FOR DEVICE.**
**本目录是 P0 宿主机契约层：只做纯函数与文本，不产生任何运行期行为，不参与 APK 打包，不注册 CMake 目标，不执行任何设备/内核动作。**

- 设计依据：`docs/patching/skroot-pro-logic-port-design-2026-10-29.md`（§3 架构、§5 P0 验收门、§6 风险、附录 B）。
- 上位规格：`docs/patching/skroot-pro-based-jailbreak-root-integration-dev-manual-2026-10-28.md`（C1–C10 硬约束）。
- 内核侧依赖：`docs/patching/maplesu-kernel-entry-design-2026-10-29.md`（D-1…D-9；本目录只定义交接契约，不实现内核侧）。
- P0 报告：`docs/patching/skroot-pro-logic-port-P0-2026-10-29.md`（构建内容、门禁证据、偏差、未决项）。

---

## 1. 这是什么

把参考实现 Ghostlock 免解链里**可移植的"形状"**落成 MapleSU 自己的接口与纯函数：

| 层 | 内容 | 状态 |
|---|---|---|
| 规格载体 MSK1 | 13 个必需键、无编译期默认值、精确 release 绑定、严格错误码、SHA-256 内容摘要 | **已实现** |
| 跳序计划 | 9 个有序步骤 / 8 个 hop，每步带判据种类与有界重试预算；计划摘要可校验 | **已实现** |
| 一次 attempt 状态机 | 显式 method/backend + 一次性标记 + 一次 boot 一次；失败进入强制恢复；无恢复不成功 | **已实现** |
| 交接脚本渲染 | 唯一一段脚本字节：恢复体 + 状态帧 + 单发交接；容量契约、注入安全、恢复读回门 | **已实现（只产字节）** |
| 就绪 token 解析 | 只接受内核侧产生的 magic + boot 绑定；**故意不提供格式化器** | **已实现（accept-only）** |
| 失败分类 | 与 D-2 形状一致的"是否已经发生"效果判定 + 是否需要恢复 | **已实现** |
| 设备绑定 | `struct msk_ops` 四个回调；P0 只校验完整性，无任何调用点 | **未实现（P1/P2）** |
| 内核侧 token 产生 | 属 L3，未开始 | **未实现（L3）** |
| 恢复体 / consumer 参数 | 由调用方提供；P2 把现有恢复脚本适配成读回行协议 | **未实现（P2）** |
| 构建集成 | 不进 `manager/build.py`、不进 CMake（P2 再议） | **未实现（P2）** |

一句话边界：**本目录能在链接期证明自己碰不到设备**（G-P0-2/G-P0-5），它产出的是
"计划和脚本字节"，不是"root"。

## 2. 目录与文件

```
README.md                       本文件
include/msk_digest.h            SHA-256（纯函数，freestanding）
include/msk_spec.h              MSK1：schema、错误码、解析/校验/摘要、失败分类
include/msk_plan.h              跳序计划 + 一次 attempt 状态机 + 设备绑定缝
include/msk_handoff.h           交接脚本契约 + 就绪 token 语法
src/msk_digest.c                SHA-256 实现
src/msk_spec.c                  schema 驱动解析器 + 校验 + 摘要
src/msk_plan.c                  固定步骤表 + 计划校验 + 状态机
src/msk_handoff.c               渲染器 + token 解析 + 状态→失败映射
tests/msk_tests.c               宿主机 C 单测（531 条断言）
tests/test_msk_static.py        源码形状/边界静态测试
tests/test_msk_host.py          C 单测驱动器 + Python 独立交叉校验
gates/toolchain.py              编译器/NDK 发现与受限命令执行
gates/run_gates.py              G-P0-1…G-P0-5 门禁运行器
gates/out/                      门禁证据（日志 + summary.json，可删除后重跑）
```

`src/` 只包含 4 个 `.c`、`include/` 只包含 4 个 `.h`，**没有** `open/read/write/ioctl/
socket/fork/exec*/mount/module` 调用，**没有** `/dev/*`、`/proc/*`、`/sys/*` 路径，
**没有** 动态分配，**没有** stdio。允许的未定义外部符号只有
`memcpy / memset / strlen`（实际残留为 0，见 G-P0-2）。

## 3. MSK1 规格（我们自己的 schema）

一行一条 `key=value`；`#` 开头为整行注释；空行忽略；**不允许行内注释**；
数字为十进制无符号（唯一有符号键 `waiter_shift` 允许前导 `-`）；
每个键必须出现且只出现一次；未知键直接拒绝；`release` 必须与运行内核
`uname -r` **逐字节相等**（不做主版本模糊匹配）。

<!-- MSK1-SCHEMA-KEYS -->
```text
msk_spec_version
release
kernel_phys_load
kernel_phys_offset
page_offset
selinux_enforcing_off
init_cred_off
task_real_cred_off
task_cred_off
task_seccomp_off
task_comm_off
waiter_shift
kernelsnitch_collisions
```

约束（解析期即拒）：`page_offset` 低 16 位为 0；`kernel_phys_load` 与
`kernel_phys_offset` 页对齐且非 0；两个镜像偏移 < 64 MiB；任务内偏移 < 4096 且按
指针/4 字节对齐；`task_cred_off == task_real_cred_off + 8`（W2 一次覆盖相邻指针对）；
`waiter_shift ∈ [-8, 8]`；`kernelsnitch_collisions ∈ [0, 64]`。

**没有编译期默认值**：缺键返回 `MSK_EINCOMPLETE`，不会用任何"内置值"补齐；
解析失败时输出结构体逐字节保持调用方传入的内容（先用局部结构体，成功才写回）。

### 3.1 错误码（append-only）

| 码 | 值 | 含义 |
|---|---|---|
| `MSK_OK` | 0 | 成功 |
| `MSK_EINVAL` | -1 | 参数/语法错误（非 `key=value`、空值、非法数字） |
| `MSK_EINCOMPLETE` | -2 | 缺少必需键（`diag.missing_mask` 给出精确位） |
| `MSK_EUNSUPPORTED` | -3 | release 不匹配（零内核动作前拒绝） |
| `MSK_ERANGE` | -4 | 数值越界 |
| `MSK_EOVERFLOW` | -5 | 输出缓冲不足（`used` 给出所需容量） |
| `MSK_EUNKNOWNKEY` | -6 | 未知键（**不忽略**） |
| `MSK_EDUPLICATE` | -7 | 重复键（`diag.duplicate_mask`） |
| `MSK_ETOOLARGE` | -8 | 输入/行/渲染结果超过固定上限 |
| `MSK_EBADVERSION` | -9 | `msk_spec_version` 不受支持 |
| `MSK_ERELATION` | -10 | 两个键违反文档化关系 |
| `MSK_ENOTIMPL` | -11 | 设备绑定未安装（P1/P2）；P0 无调用点 |

另有 `MSK_PLAN_*`（-40…）、`MSK_SM_*`（-60…）、`MSK_HANDOFF_*`（-80…）、
`MSK_READY_*`（-90…），全部互不重叠（静态测试校验唯一性）。

### 3.2 摘要（内容标识，不是签名）

`msk_spec_digest` = SHA-256(`"MSK1-BINDING/v1"` ‖ u32le version ‖ u16le release 长度 ‖
release ‖ 9×u64le（phys_load, phys_offset, page_offset, selinux_enforcing_off,
init_cred_off, task_real_cred_off, task_cred_off, task_seccomp_off, task_comm_off）‖
i32le waiter_shift ‖ u32le kernelsnitch_collisions)。
`msk_spec_schema_digest` 覆盖 schema 本身（名字、顺序、必需性、类型、上下界）。
两者都**没有密钥材料**，只回答"是不是同一份字节"，不回答"谁批准了它"。

## 4. 跳序计划（9 步 / 8 hop）

| # | hop | 判据 (`msk_verify`) | 轮数 | 间隔 | 依据 |
|---|---|---|---|---|---|
| 1 | eligibility | precondition（精确 release、显式 method/backend、一次性标记） | 1 | 0 | H-0 |
| 2 | primitive | provider 状态帧 | 1 | 0 | H-2（原语不移植，已有同源实现） |
| 3 | selinux | 读回 enforcing 节点 | 15 | 100 ms | H-3 |
| 4 | cred | 子进程 uid == 0 | 15 | 100 ms | H-4a |
| 5 | seccomp | 子进程无 filter | 18 | 1 ms | H-4b（3 链 × 6 次） |
| 6 | seccomp | 模块装载探针（只看 errno） | 1 | 0 | H-4b / 映射 #11 |
| 7 | **restore** | **恢复值读回** | 1 | 0 | **C5 / D-8** |
| 8 | handover | 单发交接前置（计划/脚本/一次性载体一致） | 1 | 0 | H-4c |
| 9 | ready | 内核往返 magic | 30 | 100 ms | H-5（3 s 窗口） |

设计原文把 7 个 hop 里的恢复折叠在 H-3；P0 把它提升为**独立且带读回判据的步骤**，
因为"跑过恢复脚本"不等于"恢复被读回验证"（C5/D-8）。**没有任何一步允许"没崩就算过"**：
`msk_plan_validate` 拒绝 `MSK_VERIFY_NONE`、拒绝缺 restore 步、拒绝被改动的步骤表，
并重算计划摘要。

## 5. 一次 attempt 状态机

- **显式输入**：`method` 必须恰为 `ghostlock`；`backend` 必须恰为 `hook`（D-6 第三值）；
  `one_shot_marker` 必须恰为 1；`boot_token` 非 0。其它值在**任何动作之前**拒绝，
  防止"未知 backend 静默落回 lease 路径"（D-6 指出的缺陷）。
- **一次 boot 一次**：同一 `boot_token` 只能 arm 一次，失败也不可复用；
  只有终态（done/aborted）才能 `msk_machine_reset` 到**不同**的 boot token。
- **无恢复不成功**：`DONE` 只能经由 handover → ready，而 handover 只能由
  `RESTORE_OK` 进入；`RESTORE_OK` 置 `MSK_SM_F_RESTORE_VERIFIED` 标志。
- **失败即恢复**：W1 之后任何一步失败都进入 `RESTORE`（rollback），不允许"先记着"；
  恢复失败置 `MSK_SM_F_RESTORE_FAILED`，保留原始失败原因，并以非成功终态结束。
- **token 只能被接受**：`READY_OK` 要求调用方已用 `msk_ready_parse` 验证过 token
  （本层再校验 magic 非 0 与 boot 绑定）；本目录**没有**生成 token 的 API。
- `msk_machine_effect` 从证据标志推导"是否已发生"：`NONE` → `KERNEL_WINDOW` →
  `HANDED_OVER` → `READY`，与失败分类的 `msk_failure_effect` 一起回答 D-2 形状的问题。

## 6. 交接与恢复契约

`msk_handoff_render` 只产字节（不写文件、不执行）。渲染模板固定为：

1. `#!` + 调用方给的绝对解释器路径；
2. 头部注释（attempt id + 计划摘要前 8 字节 + 规格摘要前 8 字节）；
3. `HOME_DIR` / 两个 baseline / `EXPECT_ENFORCE`；
4. `RESTORE_OUT="$( <restore body> )"`，随后 `RESTORE_RC=$?`；
5. 从读回行解析 `ENF` / `CRP`；`READBACK_OK` 只在形如 `[012u]:[012u]` **且**
   `ENF == EXPECT_ENFORCE` 时为 1，否则 `RESTORE_RC=79` 并 **不交接**；
6. `FRAME="r${RESTORE_RC}:${ENF:-u}:${CRP:-u}:<o|d>"`，打印一行状态帧；
7. `RESTORE_RC != 0` ⇒ `exit 80`（**先恢复、后交接；恢复不成立就不交接**）；
8. `exec <consumer> <args…> "$FRAME"` —— 唯一一次交接，帧作为最后一个参数。

恢复体（restore body）必须按顺序声明 6 个标记行：

```
# MSK-RESTORE:begin
# MSK-RESTORE:baseline
# MSK-RESTORE:policy
# MSK-RESTORE:enforce
# MSK-RESTORE:readback
# MSK-RESTORE:end
```

`readback` 阶段必须向 **stdout** 打一行 `<enforcing>:<checkreqprot>`（各为 `0/1/2/u`），
诊断信息走 stderr；stdout 是证据通道。缺标记、乱序、重复、截断、夹带二进制字节都会被
`MSK_HANDOFF_ERESTORE_WEAK` / `MSK_HANDOFF_EINVAL` 拒绝——**参考实现那种"只跑一段脚本、
不读回"的形态在本层无法表达**。

> 与已有 adapter 的差异（P2 要做的适配）：现有 `ghostlock/adapter/handoff.cpp` 在恢复体
> 之后用 `cat` 读节点并直接拼帧。P0 契约要求恢复体自己产出读回行，因此 P2 需要给恢复体
> 末尾加一行 `printf '%s:%s\n' "$ENF" "$CRP"`（或等价实现），其余形状保持一致。

就绪 token 语法（accept-only，**magic 是我们的值**，不是参考实现的 `0x12345`）：

```
MSK-READY/1 <magic:16 hex> <boot_tag:16 hex>[\n|\r\n]
              magic = 0x4D534B52 ("MSKR")
```

`boot_tag` 必须等于本次 attempt 的 boot token，否则 `MSK_READY_WRONG_BOOT`；
magic 不对（含参考实现的值）→ `MSK_READY_WRONG_MAGIC`；没有 token → `MSK_READY_ABSENT`。

## 7. 跑 P0 门禁

```bash
PY="${MAPLESU_PYTHON:-python}"
cd <MapleSU checkout>

# 一次跑完 G-P0-1…G-P0-5；日志与 summary.json 落在 gates/out/
"$PY" jailbreak-providers/maplesu-ghostlock/gates/run_gates.py --verbose

# 只跑单测（C harness 由 test_msk_host.py 自动编译并驱动）
"$PY" -m unittest discover -s jailbreak-providers/maplesu-ghostlock/tests -v

# 生产门禁（必须仍是 26，且本目录不得修改它）
"$PY" jailbreak-providers/check_traceless_invariants.py
```

预期输出（逐字）：

```
SUMMARY PASS (5/5 gates)  evidence: jailbreak-providers/skroot-ghostlock/gates/out/summary.json
TRACELESS INVARIANTS OK checks=26 points=19
```

门禁含义与证据文件：

| 门 | 判据 | 证据 |
|---|---|---|
| G-P0-1 | NDK clang `--target=aarch64-linux-android29 -fsyntax-only` + 宿主机 `-pedantic` 均通过；不引内核头 | `gates/out/G-P0-1.*.log` |
| G-P0-2 | 归档的**外部**未定义符号 ⊆ {`memcpy`,`memset`,`strlen`}；`ld.lld -r` 合并对象的未定义符号为空；公开 API 符号齐全 | `gates/out/G-P0-2.*.log` |
| G-P0-3 | `python -m unittest discover` 全绿，≥12 个测试方法；C harness 531 条断言 0 失败 | `gates/out/G-P0-3.unittest.log` |
| G-P0-4 | `check_traceless_invariants.py` 退出 0 且输出 `checks=26`；记录该文件 sha256 | `gates/out/G-P0-4.invariants.log` |
| G-P0-5 | 全目录 0 处设备/内核动作串、0 处参考实现标识；扫描器自检通过 | `gates/out/G-P0-5.surface.log` |

G-P0-5 的扫描模式在 `gates/run_gates.py` 里用片段拼接，**文件本身不含任何禁词字面量**，
因此对目录做 `grep` 也不会自命中；扫描前先跑一次合成样本自检，证明规则确实能命中。

## 8. 硬边界（本目录永远不做）

| 禁止 | 原因 |
|---|---|
| 不打包/不 `dlopen`/不 `exec` 任何参考实现二进制 | 专有软件；本目录零字节来自它（R-1） |
| 不实现授权密钥、一机一码、在线激活、授权码 | R-2；鉴权走 lease + attempt + Manager |
| 不写 boot/分区、不做 `install.mode=boot`、不做离线补丁表 | C4（越狱线零 boot 写入） |
| 不实现持久 SELinux 修补、不删 `/dev` 下的 `df` 节点、不改 exec 路径拦截 | C5/C6、映射 #8/#20/#21 |
| 不实现调试控制协议、不新增 Provider method、不新增第 4 个 backend | 附录 B #7/#8 |
| 不修改生产门禁、不把"行为等价"当验收 | C7；验收只认 C1–C10 / D1–D9 |

## 9. 未决项（带设计编号）

| 编号 | 内容 | 状态 |
|---|---|---|
| H-4 | 外部安装器"到底装什么"（补丁包本体不在参考包内） | **UNKNOWN**；本目录只负责把控制权交出去 |
| §7.2 Q1–Q5 | 设备侧可读性/`initialized`/seccomp 值/装载探针 errno/读回稳定性 | **需上机**（P1） |
| §7.2 Q6 / R-9 | 内核侧能返回什么作为 readiness token | **需 L3**；P0 只固定"形状 + 我们的 magic"，不承诺值语义 |
| D-2 | 内核侧返回码契约（0 已移交 / -ENODEV 未 exec / -EINVAL 符号解析失败） | 由 L2 实现；本目录用 `msk_failure_effect` 保留同形状的用户态映射 |
| R-3 | 每 release 的 MSK1 数值必须从 BTF/kallsyms 重派生 | **未派生**；测试里的数字是合成 fixture，不是设备值 |
| MSK1 演进 | 新增键 = 新增 schema 行 + 迁移说明 | 版本键为 1；变更加版本 |

## 10. 来源与许可

- 本目录代码：MapleSU 项目自有实现。
- **未**包含参考实现的任何代码、二进制、密钥、偏移数值或反编译文本。
- 接口事实（"规格必须精确绑定 release"、"就绪判据是内核往返值"、"载体一次性"、
  "恢复必须读回"）来自本仓设计文档记录的公开可观测行为，不构成对其实现的复制。
- 提权原语**不在本目录**：它来自 `jailbreak-providers/ghostlock/`（pinned Apache-2.0 upstream）。
