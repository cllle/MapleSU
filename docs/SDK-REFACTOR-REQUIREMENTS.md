## 2026-11-21 原机源码回放与限定完整链接增量（2026-11-21T18:36:00.548204+00:00）

公开快照约定：`WORKSPACE` 为源码工作区占位，`WORKSPACE_BUILD_PRESERVE` 为独立构建证据目录占位，`SERIAL` 为测试设备序列号占位；私有证据按原路径后缀定位。

新源码已在原设备UID2000完成8原包两模式16次解析：旧源码14/16，新源码16/16，三个中文路径实际提取通过，原包SHA及前后boot `7ff19abb-1fa2-4552-b844-147eedef0e28`保持。Root、真实模块安装、脚本和生产控制写入均0。该证据为真实Java parser及extract逐字函数端口；已安装r23产品尚未变化，其实际parser14/16失败记录继续保留，不结算产品或模块生命周期通过。首轮系统Killed/exit137原因未定、空列表夹具断言纠正及原失败收据均保留。

主线已核前像采用resident条件下的 `native_imports.c/h`，采用收据为 `provider-registry-intake-main7/TYPED-IMPORT-ADOPTED.json`。新限定完整模块链接通过：44个r6冻结对象加两个新目标TU，实际模块链接、严格modpost、`.mod.o`、`.ko`及ARM64 postlink均通过；baseline裸 `__module_address` 被严格modpost准确拒绝，最终get/put为真实T定义、五个wait CRC与目标匹配。最终ko SHA `729ac2b6f8379598deeae36adde474ab809e3428ae01bc49d922bd8ca155c018`。这不是全源clean或产品Release；运行时binder、PUBLISH及原机发布未验，PUBLISH gate仍0。

SU真实batch/guard/cleanup源回放25场景605检查通过，unknown错误释放变异被捕获；未证新增Bug，生产修改0，三个关键源与r23实际选源SHA一致。真实provider发布/排空、lost-reply/unknown协调和原机SU效果仍开放。

stage当前37测试、45 failures/21 errors为主线本轮真实工具观测，完整首轮原始日志尚未落文件；共同首错缺 `preinit/runner.c`，旧前像对照未完成，尚未归因本次修改，未记全stage通过。已有私有include闭包限定绿；原审查任务已接续前像/当前真实日志与最小fixture核实。Manager完整 `compileReleaseKotlin`/`compileReleaseJavaWithJavac` 于18:33:28 UTC启动，记录时状态 `RUNNING`，沿保留r23 SDK，尚无通过结论；未构建APK或新SDK。

当前产品仍r23 safe3/`root-environment=-19999`，没有新SDK/JNI/Manager产品或生产环境repair。119完整勾选仍SDK-01/07/08、29CASE整项闭合0；全部范围、主线active/监督paused及23:30正式构建/00:00争取交付时点保持。后续出口为当前全源clean桥与同代选源、实际typed/KMI绑定、producer/root-loader v2、真实PUBLISH/DISABLE/RETIRE及在途排空，继而SDK/helper/backend/JNI/APK与指定原设备完整验收；源端口与限定链接不合并为产品通过。

证据根`.tmp/supervision-20261121/`：`main7-module-unicode/device-original8-observed2/RESULT.json`、`main7-typed-module-import/{FULL-LINK-RESULT.json,DELIVERY-full-link.md}`、`main7-su-rollback/{RESULT.json,DELIVERY.md}`、`main7-manager-compile/RESULT.json`。新文档收据：`main7-document-update/RESULT-source-device-link.json`；前三轮收据保持。

## 2026-11-21 安装测试判据与provider源码采用增量（2026-11-21T18:18:47.273570+00:00）

`RealModuleInstallAndroidTest` 的假绿判据已修并经原机验证：18:15:14–37 UTC，UID2000执行真实测试方法与外部seams，缺包、解析失败、安装错误、未列出、unknown、archiveError、未提交七种错误由旧假绿转为断言失败，成功控制仍通过；状态 `PASS_ORACLE_BEFORE_AFTER_8`。前后boot仍 `7ff19abb-1fa2-4552-b844-147eedef0e28`，Root、真实模块安装、脚本执行及生产控制写入均0。该证据证明验收判据捕错，未证明真实模块安装或生命周期通过；已安装r23原包parser结果仍为14/16，M03两模式INVALID_ARCHIVE_PATH。

主线已核对前像SHA，把native-compat `adoptOnly` 四文件采用到源码，并补 `scripts/build-ultra-manager-claim.py` 与 `runtime/ultra/boot/init-bridge/stage.py` 的provider私有include闭包；当前文件SHA与PLAN四文件后像匹配。`provider-registry-intake-main7/PLAN.json` 仍为采用计划/前像记录，完整后验收据尚未完成，本次不结算新编译通过。RESIDENT与PUBLISH仍关闭、源选择未完整、新产品尚未构建，既有候选TU通过不转记本次整合成功。

`main7-typed-module-import` 已实际ACK，仅负责独占目录内typed/KCFI单依赖候选；`main7-module-unicode` 已实际ACK，M03 Unicode最小修复实施中，baseline46项4红保留，修后结果未验收。原legacy/producer审核中断任务继续保持中断状态。119条需求、SDK-01/07/08原三项完整勾选和29CASE最终Release闭合0均保持；正式时点及其他开放项沿前文，不扩大本次文档范围。

实际证据：`.tmp/main-rootpanic-resume-20261121/module-install-oracle-main7/device-first/RESULT.json`、`provider-registry-intake-main7/PLAN.json`，及`.tmp/supervision-20261121/main7-typed-module-import/ACK.md`、`main7-module-unicode/{ACK.md,tests-before/RESULT.json,tests-before/junit.log}`。新文档收据：`main7-document-update/RESULT-oracle-intake.json`；原两轮RESULT/DELIVERY保持。

## 2026-11-21 原机归档解析增量（2026-11-21T18:11:06.678103+00:00）

用户已手动完成厂商验证，18:09:10–11 UTC 实际续接已安装r23的只读parser：8个原包两模式共16条，14条PARSED；仅M03 `TEE-AutoList_5.2.2.zip` 在两个模式均返回 `INVALID_ARCHIVE_PATH`。原8包SHA逐一保持，产品APK `3152f2be…`、调用UID10498、前后boot均 `7ff19abb-1fa2-4552-b844-147eedef0e28`；Root、模块安装、脚本执行及生产控制写入均0。本结果是当前产品实际归档解析，不结算模块安装/生命周期、产品环境恢复或29CASE完整通过。

首轮安装120秒超时与原文档交付均保留；此前“等待人工验证/16条未运行”仅为当时状态。主线独占调查M03路径拒绝来源，生产parser尚未修改；`RealModuleInstallAndroidTest` 假绿判据修复仍在进行，尚未记通过。其他安全模式、provider、正式构建与验收缺口保持，119条需求及SDK-01/07/08原完整勾选均未变。

实际设备证据：`.tmp/main-rootpanic-resume-20261121/modules-original8-r23-readonly/device-resume-after-manual/RESULT.json`。文档增量收据：`.tmp/supervision-20261121/main7-document-update/RESULT-archive-parser.json`。

## 2026-11-21 MAIN7 当前接续（2026-11-21T18:08:27.103784+00:00）

当前唯一生产采用、完整构建与原机负责人为主线（7）`01a1ff3b-8b47-7411-81b9-da494e110a3b`，goal 为 active；监督 `01a1ff24-054a-76a2-8261-a8eb9be292fd` 的连续 goal 为 paused，仅每小时 :00/:30 巡检。以下历史段落保留各自时点，以本段及 JSON 的 `main7Current20261121` 为当前状态。

- 两个实施任务均已实际 ACK：`legacy_candidate_main6` 随后被自动审核中断，`resident_producer_main6` 随后 HTTP403/session blocked，均无最终交付。保留四源候选、PROPOSED-ABI 和部分 Kotlin；旧代理状态不记为仍在运行，未通过换代理重做被拦动作。新四文件采用审查与本文档任务均已有 TASK/实际 ACK。
- r25 已于17:52:53–55在原设备 UID2000执行 Hook40、m024七场景，全部通过，前后 boot 均为 `7ff19abb-1fa2-4552-b844-147eedef0e28`。Root、gateway、kernel text 与生产控制标记写入均0；这是源函数端口回放，尚未采用到生产，也不结算内核发布或产品恢复。
- provider 已交 `DELIVERY.md/DELIVERY.json/ADOPTION.patch`，只审 `adoptOnly` 四个 management/provider 文件，同目录三份旧 SDK 副本保持冻结。registry167、scope156、共享生命周期35项源回放及真实6.6 ARM64 KCFI/PAC/SCS management TU通过；完整链接、实际 PUBLISH/原生同址共存、SU回滚/unknown及原机验收仍开放。新增 `__module_address` 在目标 Module.symvers 无模块导出，typed/KCFI绑定资格尚未验证，其余五个新增等待符号已有真实导出。接口只采用 v2，保持 slot64/binding32/现有 anchor 字节及版本。
- 产品最后可信仍为 r23：APK `3152f2be…`、SDK `maplesu-ultra-source-51f6b3a749d084d0`，原设备 PLQ110、UID10498、上述 boot、版本5.2.1、safe3、保护文件存在、enable缺失；本 boot 首错 `root-environment=-19999`、`filldir64 ERR_MODULE_TEST_CHANNEL`。Ready、SU、模块整链尚未通过；原始SDK门禁182通过/78失败/exit7原样保留，78成员差异已独立归因，完整两次 clean SDK/六族构建仍开放。
- CASE-01–20共29子项、8原包SHA和指定设备/ABI/生命周期要求完整保留，最终Release整项闭合0。8包已推到原机并逐包校验SHA；已安装r23的16次只读parser尚未运行。首轮诊断包安装120秒超时已保留，当前停厂商拼图验证，待用户在手机上手动完成；模块安装与脚本执行均0，M08原包未执行，不把解析进度记为生命周期通过。
- 本轮8份冻结mapping/报告无损压缩完成，逐文件SHA/大小/mtime保持，零删除/移动，D盘实际余量增196612096字节。18:05附近主线实测 C312414208/D1161322496字节可用，尚未证明足够正式构建。23:30 UTC（2026-11-21）启动最终正式Release、争取00:00 UTC（2026-11-22）交付的目标保留；诊断候选不改名充当正式构建。

全部P0–P8、119唯一需求、20约束、两组10项及追加要求保持；完整勾选仍仅SDK-01/07/08，本轮新增完整勾选0。源码、选源、产物、主机回放、源端口上机和实际产品效果分别记账。

证据根 `.tmp/main-rootpanic-resume-20261121/`：`safe-mode-all/native/boot-legacy-fix/device-r25-main6/RESULT.json`、`modules-original8-r23-readonly/device-first/RESULT.json`、`space-main6/round2-text/attempt2/RESULT.json`；监督交付根 `.tmp/supervision-20261121/`：`native-compat/{DELIVERY.md,DELIVERY.json,tests/resident/LINK-DEPENDENCIES.json}`、`case01-20-acceptance/RESULT.json`、`main7-document-update/{ACK.md,RESULT.json}`。

## 2026-11-21 17:25 UTC r23原机UI提交与新boot首错

- r23同代Manager构建成功，114任务30执行/84复用，输入稳定；签名、封包、SDK/JNI、bridge配对核验通过。APK `3152f2be…`，SDK `maplesu-ultra-source-51f6b3a749d084d0`。原始SDK成员门禁182通过/78失败/exit7原样保留，独立源码review不提升原始门禁。
- 已保数据安装r23，UID10498与Key私有摘要保持。真实设置→修复→确认已越过资格检查、进入生产SDK安装，随后观察到新boot `7ff19abb-1fa2-4552-b844-147eedef0e28`、boot_completed=1。只结算实际调用与重启，不据此宣称完整成功。
- 原机保护状态回读：真实版本文件`skroot.sdk.ver`=5.2.1，helper1779776字节，safe3、空保护文件存在、duplicate本boot、work/completed为空、enable缺失。实际boot_a仍`73c956c5…`；本boot helper日志明确root-environment=-19999、filldir64 ERR_MODULE_TEST_CHANNEL。首次补采用了逻辑名VERSION，已另外按真实生产路径重读纠正；首份原始记录保留。
- repair资格精确源函数已在原机UID2000回放：native12/0、ART23/0，VERSION顺序mutation精确红例被捕获；无Root或生产control写入。该结果与本次真实UI已进入SDK安装分别记账，没有放宽Unknown守卫。
- Legacy隔离候选补齐PAC/branch/身份重入核验和长期remap backing归属；主机32项Hook与6项remap通过，原始红例保留，原机源端口回放正在执行。首个before之后的显式handle SU及SELinux shared-bool仍是独立缺口；当前生产源尚未采用此候选。

证据根`.tmp/main-rootpanic-resume-20261121/`：`device-r23/{verification/APK-VERIFICATION.json,INSTALL-RESULT.json,ui-repair/RESULT.json,post-repair-identity/RESULT.json}`、`safe-mode-all/manager/fault-repair-admission/device-fixture/device-first/RESULT.json`与`safe-mode-all/native/boot-legacy-fix/`。119个唯一需求ID完整，完整勾选仍仅SDK-01/07/08；20约束及两组10条件保持。

## 2026-11-21 16:31 UTC 文档派工确认与 r23 同代 Manager 构建

- 文档派工已核实 7 份 ACK；新增 repair 资格核查和 Legacy 候选独立审查。SDK 实际交付路径已纠正为 `generation-r23-resume2/DELIVERY.json`。
- r23 SDK 完成 staging，身份 `maplesu-ultra-source-51f6b3a749d084d0`；正式 bootstrap/final 配对、源码闭包及独立 review 通过。原始成员门禁仍为182通过/78失败/exit7，首次缺符号与第二次循环抽取失败均保留。
- 主线已核验18个当前构建输入/收据摘要，启动同代 Manager 构建；APK尚未生成或安装。本代含14源修复与publisher最小选源接线；Legacy候选保持隔离，当前provider缺口尚未结算修复。
- 设备仍以原机真实 UI repair、失败回执、helper本boot日志及Refresh/SU/Ready为后续判据，保留Key/授权/模块与原备份，119项完整勾选不增加。

证据根 `.tmp/main-rootpanic-resume-20261121/`：`safe-mode-all/TASK-DISPATCH.json`、`generation-r23-resume2/DELIVERY.json`、`generation-r23-manager/{INPUT-VERIFICATION.json,COMBINED-EXECUTION.json}`。本段源码/构建进度不代表原机生产环境验收。

## 2026-11-21 16:20 UTC Normal原机回放与SDK依赖闭包修复

- 文档任务已取得5份实际ACK，送达与交付状态见 `safe-mode-all/TASK-DISPATCH.json`；不再以空消息Payload代表派工成功。gateway/Legacy兼容定位及Normal源回放已交付，SDK同源续建与Legacy内置Hook实施分别推进。
- 原设备UID2000执行Normal精确源函数：按键/锁存16场景99检查、Rust属性短路128场景384检查均0失败；Root0、生产状态写0、boot8509保持。两个Android安全模式属性实读均为空。首次query为异步stop，worker完成前仍有在途事件；实际Normal内核冷启动/输入/ioctl/模块禁用效果不由这些夹具替代。
- r23-resume1已消除原helper缺符号，但final260稳定性门禁真实exit5，捕获publisher放m053后引入payload安装器循环。已验证并采用第二个最小选源修复：共享publisher归直接调用者m055，m048/m053不重复包含。隔离实际bootstrap/final helper摘要一致 `446fcf9f…`，backend配对通过；两次正式失败均保留，后续独立resume2不放宽门禁。
- 当前root-environment首错并非Android属性，而是重构后m029全部依赖原Image未包含的自定义provider。最小兼容实施先限定原API无显式handle的builtin boot生命周期；有handle/actor/shared-bool和Owner路径保持原合同，跨CPU发布、在途资源及重复安装先验证，不以省略初始化或伪造Ready达成绿色。

证据 `.tmp/main-rootpanic-resume-20261121/` 下 `safe-mode-all/{normal/device-first/RESULT.json,normal/device-readonly/RESULT.json,native/gateway-fix/DELIVERY.md,native/legacy-compatible-path/DELIVERY.md}`、`generation-r23/resume/{ADOPTED2.json,pairing-probe2/RESULT.json}` 与 `generation-r23-resume1/EXECUTION.json`。本轮复核119个唯一需求ID仍完整，完整勾选仅SDK-01/07/08；整套安全模式修复及软件环境验收尚未完成。

## 2026-11-21 16:01 UTC 文档派工送达、r22原机结果与r23续建

- 已改用落盘任务正文＋完整上下文继承，gateway代理实际读取并写下 `safe-mode-all/native/gateway-fix/ACK.txt`；不是仅发送成功。SDK续建任务独立落在 `generation-r23/RESUME-TASK.txt`，要求单独ACK，避免空Payload继续空转。设备仍由主线统一操作。
- r22已完成同签保数据安装（UID10498、Key私有摘要不变），正式生产恢复方法在原机取得完整原核 `346d51ea…`；实际boot仍 `85092eb5-28bd-483d-bf22-e666ee059d37`。本轮UI仅完成正常冷启动/页面观察，Unknown缺版本时修复按钮隐藏，未提交SDK安装、未新增分区写入或重启；软件环境尚未完成恢复。
- 当前boot只读探针（Root0、EXEC0、publication0、Application.onCreate未运行）实测gateway descriptor存在、capabilities=7，但私有尾magic/schema/bytes全0，provider get/put均缺失。该证据与 `m070→m075→m071→m029→Legacy provider` 的-19999失败路径对应；缺provider的源修复仍由gateway代理定位，不填伪地址、不跳过ownership/失败检查。
- 已采用helper3、handoff7、Manager4共14个生产源文件。原设备限定源回放分别为：helper旧33项7失败→新33/0；安装时序旧47项27失败→新47/0；publisher旧6项2失败→新14/0；Manager ART绿色82项（旧UI2失败、失败回执14失败）。publisher使用独立临时目录和user-xattr测试seam，不冒充生产SELinux验收；ART不是实际Manager UI或SDK安装验收。
- r23首次同源SDK构建真实失败（producer exit9）：helper链接缺 `maplesu::install::publish_empty_state(char const*, char const*)`。原日志/收据保留；续建先纠正实际依赖接线，保留260门禁与独立source/provider/ABI核验。当前r22仍使用r20 SDK，14源修改尚未作为完整同代包安装。

证据根 `.tmp/main-rootpanic-resume-20261121/`：`device-r22/{deliver-RESULT.json,green-final-RESULT.json}`、`safe-mode-all/{ADOPTION/,helper/android-arm64/device-root/RESULT.json,handoff/device-ordering/RESULT.json,handoff/android-publisher/device-first/RESULT.json,manager/art/DEVICE-RESULT.json,native/passive-metadata/no-startup/device-r22/RESULT.json}`、`generation-r23/{EXECUTION.json,producer.log}`。安全模式矩阵22类入口/保留/退出及8项覆盖缺口继续逐条保留；全部119项、20条约束与两组10条件不缩减，不新增完整需求勾选。

## 2026-11-21 15:26 UTC 安全模式全链并行排查与原机证据

用户要求子代理定位并修复所有安全模式原因；已分派 helper 启动状态机、Manager 修复入口、安装提交衔接、native root-environment、入口/验收矩阵五个独立范围。生产候选保持隔离，原设备由主代理统一操作；当前 Manager r22 分类构建输入冻结。

- 原机 boot 仍为 `85092eb5-28bd-483d-bf22-e666ee059d37`、boot_completed=1。新增一次只读 JNI Root 调用收集实际 `init.log`，无分区/控制文件写入。日志曾记录 root-environment=-19999、filldir64 ERR_MODULE_TEST_CHANNEL 和 bootstrap protection retained；每行缺 boot 绑定，故不把历史失败精确归到本 boot。证据 `safe-mode-all/device/init-RESULT.json` 与 `init-decoded.log`。
- 已明确：boot_fail_protect 空文件按存在即开启；旧 safe_flag=3 可跨 boot 继承保护，提前退出会保留旧 work/completed。kernel_verified 只结算内核更新，不代表环境软件安装成功。RECOVERY_SUSPENDED 是下游保护状态；readUnknown 也可能仅因 VERSION 缺失，二者不独立证明新的安全模式根因。
- 限定缺陷验证在进行：安装失败前过早清保护标记、helper 实际 open/fsync 的 EINTR 处理、Unknown 缺版本时 UI 修复入口被隐藏。另沿真实 native filldir64 测试失败继续定位。上述候选尚未合入/上机，不提前报修复完成。
- r22 首轮 C 盘耗尽，续建 R8 报 Deflater has been closed，两次失败均保留，均未安装。自己的生成构建目录迁往 D 时遇 Windows 句柄，已通过保留原件并补齐复制形成完整 D 构建工作区，第二次续建 `generation-r22-resume2/` 进行中。当前使用既有 r20 SDK/JNI，不重写原件/Key/授权/六份备份。

随后按同源候选红绿→统一合入构建→真实 UI repair→原机启动/保护退出/Refresh/SU/Ready 验收推进。源码、主机、源测试、已安装生产效果分别结算；119 项完整勾选保持。

## 2026-11-21 14:55 UTC 内核分区与事务闭合，软件修复首个静默返回待定位

已通过生产installer service读取任务`c177e146-7edd-467d-aee1-6e3bc31caa25`：boot/_a、target LITE、state REBOOT_REQUESTED、error空、rebootObserved=true、operationActive=false。独立只读Root命令读取实际`boot_a`完整SHA `73c956c597827c6bf70f2b536c12d56de55c5663f2f6013cf39de7684fb9662d`，与candidate/partition收据相同；不只是重启时间相关。新boot仍safe_flag3、enable缺失、work/completed旧7e08、duplicate为8509，说明软件环境尚未恢复。

在新boot第二次实际UI保数据修复，预检导入的镜像已是73c956…，完整恢复仍得原核346d51…，未再报CURRENT_MISMATCH。但本次返回preflight、failure空、submittedBootId空，没有SDK提交或重启。之后真实readFacts：running/suspended/quiescing/submitted均false、installedVersion=null、bundledVersion=5.2.1、双方helper归一摘要相同、App5.2.0→5.2.1、readUnknown=true。页面同时有current_boot_protection/Fault记录；这些后读事实不冒充当次拒绝分支。

r22只给现有显式repair的state/facts/scope返回记非敏感原因，资格、超时、Key/boot/模式及一次提交守卫逐字保留，继续同代SDK/JNI构建，再设备定位首个实际返回。首次诊断readFacts的R8参数被优化，接线错误保留，按真实残留签名修正后才读得上述事实；未据测试错误改生产行为。

证据`.tmp/main-rootpanic-resume-20261121/{root/installer-status-r21/device.log,device-r21/live-state-8509/rootops-live8509.log,device-r21/ui-repair-8509/,root/repair-facts-r21-v2/device.log,generation-r22/}`。完整Refresh/SU/Ready仍未结算，119完整勾选保持。

## 2026-11-21 14:46 UTC r21生产原机绿色与内核修复重启

r21续建114 tasks（21执行/93复用）成功，首次磁盘失败及损坏executionHistory丢弃记录保留。全输入续建前后快照稳定，签名/封包/SDK四payload与bridge校验通过；SDK/JNI仍是r20同代，APK SHA `5dc20d45612834601ab3c21bca2ef626236399385d90676c964776a12d4f2e28`。原机保数据安装成功，UID10498、Key与私有摘要基线相同。

- r21正式release路径的实际生产恢复方法，对同一设备私有导入boot829e48…返回完整原核346d51…，原输入摘要未变；与r20同机CURRENT_MISMATCH形成设备红绿。该调用Root0、不写分区，不只算源测试APK通过。
- 实际设置→修复→确认后，生产状态已落`kernel_verified`、lastFailure为空、原boot5520的提交标记保留。设备随后进入boot `85092eb5-28bd-483d-bf22-e666ee059d37`，sys.boot.reason=`reboot,`、boot_completed=1；未用shell直接写分区或手改control。保护安装事务的独立状态/分区身份回读继续进行，不仅凭发生重启结算完整成功。
- 重启后正常Application+MainActivity实际启动，仍`ready=false / RECOVERY_SUSPENDED / STATE_Fault`，环境版本尚缺。故当前只结算内核恢复识别与kernel_verified阶段，不称完整工作环境/Refresh/Ready通过；接着走真实软件环境修复与回读。未放宽超时或伪造就绪。

证据根`.tmp/main-rootpanic-resume-20261121/`：`device-r21/{deliver-RESULT.json,verification/APK-VERIFICATION.json,device-recovery-RESULT.json,ui-repair-5520/,post-repair-local.log,startup-8509/}`、`root/recovery-production-red-r20/device.log`及`recovery-migration-r21/NTFS-CONTENT-PRESERVATION.json`。原119项完整勾选保持。

## 2026-11-21 14:32 UTC 原机ART回归32/0

恢复迁移相关32项测试已在原机SERIAL的Android ART执行，32/0、UID10524、Root调用0；覆盖确切归档优先于旧嵌入inverse、额外kernel改动拒绝、未知镜像继续严格失败、元数据损坏拒绝、原输入不变及现有恢复/升级识别。此为当前生产源码编译的源测试APK，不冒充已安装Manager或完整内核写入/Ready验收。r20原生产恢复方法也在同机直接复现CURRENT_MISMATCH，设备红例保留。

初轮r21 release在Gradle持久缓存写入遇D盘空间耗尽，原raw失败及wrapper收据写入errno28（空文件）保留；未通过产物不安装。空间已回升，复用已编译输出继续同源构建，内核更新将继续走生产事务。新JUnit用例首轮有目标输出预先存在的测试接线错误，已定向修正；独立当前源码主机32/0及旧排序红例通过。源绑定与首次失败均单独记账。

证据`.tmp/main-rootpanic-resume-20261121/recovery-migration-r21/{art/DEVICE-RESULT.json,HOST-SOURCE-BINDING.json,host-final/RESULT.json,BUILD-RESUME.json}`；后继构建`generation-r21-resume/`。119项完整勾选不变。

## 2026-11-21 14:20 UTC 原机前台首错闭合与恢复迁移修复

- 用户误报判定后的继续执行保持active。r20原包已恢复安装，原机UID10498、Key与原备份保留。约13:55 UTC设备重连进入boot `55204512-ba37-455f-86ca-2cc46e2453d3`；未主动发起该次重启，具体原因尚待核对，不归为已证Panic。
- 后台准备无终态时，独立只读observer捕获线程`do_freezer_trap`，恢复后同次部署成功；8次纯只读Root命令全返回成功。该证据不支持继续修改pipe/重试/锁或延长超时。早期辅助monitor栈和跳过Application初始化造成的Activity异常，均与生产prepare首错分开记账。
- 真实Application+MainActivity前台调用生产Context Refresh一次，准备阶段`ALREADY_BACKED_UP;IDENTITY_UPGRADE_PREPARED`通过；SDK约11.463秒返回`ERR_MODULE_NOT_KADDR/-19987`、submissionUnknown=true。默认verified字段不代表完整环境通过。当前版本仍缺、enable缺失、boot-session仍旧；未手补control或恢复旧授权，精确授权失败phase仍待动态确认。
- 实际UI“修复工作环境”在preflight失败：`ULTRA_KERNEL_RECOVERY_CURRENT_MISMATCH`，submittedBootId为空，无SDK提交或预期重启。新设备私有导入镜像SHA `829e4866dbdd38c92a73edaecb16611dcbe7a05291d5714b06619e7622694896`，与历史executor诊断镜像逐字节身份一致。
- 已证其嵌入inverse仍描述诊断前镜像：146范围中仅第2项(kernel offset261696/432B)摘要失配；历史3指令改动位于该范围，52B trampoline还在旧inverse之外。逐项逆转历史增量后完整boot恢复至原source SHA，再由全部原inverse验证恢复原kernel SHA `346d51ea1f63413ffa5d99c5c426806291af3573f0a1d66a7621c388e835880e`。新增逆记录只含原字节与当前范围摘要，不含replacement或Key。
- 生产最小改动：复用既有整kernel摘要归档通道，在嵌入inverse前优先验证确切归档；explicit repair对已验证归档旧图进入既有内核更新流程。没有全局吞异常、版本猜测或降低未知/损坏镜像门禁。r21复用r20 SDK/JNI同代构建中，尚未安装，不计完整设备验收通过。

证据根`.tmp/main-rootpanic-resume-20261121/`：`root/preparation-passive-observer-r20/device-first/`、`root/rootcmd-series-r20/device-first/`、`root/refresh-production-r20-foreground/device-first/RESULT.json`、`device-r20/ui-repair-5520/`、`root/recovery-image-identity-r20/device.log`、`recovery-migration-r21/ARCHIVE-PROOF.json`。原119项、20约束与两组10项保持，不新增整项勾选。

# MapleSU SDK 接入与后端重构总需求

当前执行指令（2026-11-21 13:21 UTC）：用户最新已将此前暂停事项判定为误报并明确要求继续上机实测；历史现场与失败证据保留，旧暂停字段不再作为当前执行停止条件。当前目标active，保原数据/Key/授权/六份备份，核对boot368b5b24、UID10498及仍安装r18后，执行同签r20安装、当前boot环境回读、精确绑定后的真实Refresh。尚无新增整项验收通过。

接续状态（2026-11-21 13:15 UTC）：存储存在性错误分类源码已修，真实函数体在原机7项旧1失败→新7/0，观测版同7项通过；r20同代SDK/helper/四payload/JNI/签名APK已构建并通过封包、签名、SDK/bridge配对校验，尚未安装，准备SDK-Key通道的完整原场景仍待实测。新Panic已完成离线任务/调用点分析，缺崩前maps及故障页，根因未闭合，相关触发保持暂停；原机当前仍r18诊断包/boot368b5b24。SDK Refresh实际存储失败阶段及新boot部分环境运行状态尚待对齐。119项、20约束及两组10项保持，源码/产物/宿主/设备分别记账，不把局部夹具通过算完整Ready、Panic或正式5.2.1验收。详见`本次进度`（开发证据未随公开快照分发：`SDK-REFACTOR-PROGRESS.md`）与`.tmp/main-rootpanic-resume-20261121/resume-20261121/STATUS.json`。

11:33 UTC追加：su发布后按原路径重复改权限的canonical迁移竞态已在原机隔离夹具红绿复现，源码已修、真实Android TU通过，尚未编入后继产品。10次夹具结果与完整生产安装验收分开，不将该竞态自动认定为当前Refresh失败的已证唯一原因。

## 2026-11-21 10:04 UTC 快速迭代：激活恢复已采用、Panic 新现场

按原119项与追加需求继续执行，发现问题即记录/修复/复验；无全项目重审。源码分析使用隔离写集，设备由主线独占，共享构建窗口冻结。

- 源码完成：`ModeBootstrap` 在真实 `Activated(LITE)` 后向已有串行合并恢复队列投递 fresh-pass，修复旧scope撤销后缺恢复事件的问题；全部原mode/boot/owner/key守卫保留。`UltraAutoPair` 的临时r13分类日志已清理。主机旧9项3失败→候选9/0，已精确采用，证据 `root/manager-ready-continuation/mode-activation/delivery/DELIVERY.json`、`generation-r14/SOURCE-ADOPTION.json`。
- 进入产品：r14沿已验证r12 SDK/helper/backend/四payload启动受影响Manager release构建；此时尚无r14构建成功或设备通过声明。运行中旧helper/su/resident仍单独记账。
- 已确认旧su：三份设备su完整SHA `975139fdc844df0a2415b2eab6a742e2e9076dcb848f14bfd572fb3a560f1266`，与历史218120B产物按原发布算法patch key+NUL后精确相同；仅摘要落盘。当前源码握手10场景已过，但部署迁移尚未执行，标准su仍失败。正在准备保持key与授权的同版本刷新候选。
- Panic：被动发现09:52:17 UTC新NX，boot `7e086c29-3f81-408a-9297-3295941d16ed`、`DefaultDispatch/invoke_syscall+0x58`、地址 `ffffffd67bfe6000`。先保存新完整268435456B dump，SHA `ca044e29a4ed54b214cfa21b82cca1bcbec437016aacff5c23fdea01366367b2`，原件复制前后元数据及摘要一致；当前旧broker4017经cmdline/maps匹配后SIGSTOP，状态T回读。未主动reboot、未压力触发、未写分区/授权。与09:39的90a6及08:48的c956分开记录；故障路径功能触发暂停，独立源码/主机验证继续。
- 主机新增：当前生产WebUI owner-scope 15/15、script-outcome 4/4通过，证据 `acceptance-fast-iteration/HOST-EXECUTION.json`。只结算覆盖的宿主语义，不提升完整模块/设备项。旧模块移除文本提取脚本出现7失败，已记录并改用完整当前Kotlin生成shell复验，尚无对应生产故障结论。

证据路径均相对 `.tmp/main-rootpanic-resume-20261121/`。正式5.2.1、1000ms完整Ready/五冷进、目标应用授权/标准su、修补升级、模块生命周期和Panic设备项继续按实际结果结算。

## 2026-11-21 09:48 UTC r13 定向修复与新增 Panic 现场

r13 已完成 release 构建、签名和保数据安装，APK SHA256 `5a819ef08498b85c848689783efb1a6c2f1ec66d11106efc660c162a238f7011`，UID 保持10498。仅 Manager 两成员受影响重建，使用同一 r12 SDK `maplesu-ultra-source-915c61c220e3b244`；JNI SHA 与 r12 相同。构建前后 Manager src、选定 SDK 全文件摘要稳定。设备 helper/su/resident 的安装代际另账，未宣称已升级。

- 源码/主机：SU 五候选改为共享一次总预算，6项主机检查通过；AutoPair 增加临时 `[DEBUG-r13-scope]` 首失效守卫分类，原短路顺序及撤销语义保留，9项检查通过。真实 ProcessSuExec 宿主8项检查通过，700ms挂起约709ms返回，未证实固定500/750ms追加，未作推测性收尾修改。
- 设备：跳过 Application.onCreate 的诊断只调用实际生产 helper JNI 一次，198476us完成1586520B，RUID/EUID0、complete/rootVerified/receiptComplete/streamEof/childStatus0，无超时。排除独立应用预热；此前本boot已有Root操作，故不算五次完全冷进。Legacy replyObserved=false仍与完整流事实分开。
- Manager 首错：有效 r13 原场景观测 live-observation1449ms、manager-su2718ms、terminal ready=false/pass4168ms；首失效明确是 `mode-activation`，未放宽守卫。前3次脚本在候选PID筛选/已退出子进程读取处提前退出，只计诊断脚本错误，未计功能复验。后续源码发现 Activated/LITE 缺向既有串行恢复队列投递fresh-pass事件，候选主机验证中，尚未进入产品/设备。
- 标准su：独立调用实际 r13 ProcessSuExec 的主候选 `/system/bin/su -c id -u`，102666us、exit1、`su: root handshake failed (errno=2): No such file or directory`、非超时。尚未通过Manager自身SU，更未结算目标应用授权或标准su整项。
- Panic：09:39:48 UTC新增NX，boot `90a6b520-e7f4-4584-9ea8-01652cd97bd0`，地址 `ffffffe280d45000`、stackhash与既有场次相同。单次su结束09:38:38时仍为c956，之后至本次发现未发主动重启/压力测试，先后关系不作因果结论。已暂停功能复测、force-stop Manager；新boot实际加载旧桥的身份broker4093已SIGSTOP，保留原授权/config。完整新268435456B minidump已保存，SHA256 `04501ef35be2b316913082a3236ed67a2f76d30944527a46fb37b45ff68a111d`，vendor原件复制前后元数据及副本摘要匹配。新boot maps 与崩前maps严格区分，Panic仍未修复结算。

证据：`.tmp/main-rootpanic-resume-20261121/generation-r13/COMBINED-EXECUTION.json`、`device-r13/helper-once-first-RESULT.json`、`device-r13/manager-once-observed-RESULT.json`、`device-r13/production-su-primary-RESULT.json`、`panic/panic-90a6b520/FULL-CAPTURE.json`。完整现场：`[private-evidence]/maplesu-rootpanic-resume-product-20261121/panic-90a6b520/`。正式5.2.1、完整Ready、五次冷进、目标应用SU及其余设备验收继续未完成；本轮不新增父项勾选。

## 2026-11-21 08:54 UTC r12 原机 Root 回读通过与新 Panic 现场

Root accessor 与 caps 偏移复用已进入真实 r12 同代 SDK/helper/backend/四段 payload/JNI/APK；SDK `maplesu-ultra-source-915c61c220e3b244`，APK SHA256 `d794d1c322f9b1b3604e1689313fe622721b59b439273f6dbc8956ef0b5a5dcd`。r12 采用逐编译命令/当前依赖内容/对象摘要校验后的 r11 输出复用：14次编译、293对象复用，并重新组合及链接，明确不是第二次全干净可复现构建。原 raw exit7/78成员差异、25 partial及其行为边界保留，来源审阅通过不冒充原始门禁全通过。

- 源码/主机：resolver `447cbc52384964b58945492c4a6ace8f6f8d2d021ab3d21e91ff709afaa5ca23`，patch_current `93a0790ccdb47136441fc226ebc1252a46b61cbb6075a17e819d63a79e595b4e`；前轮16场景及35检查红绿证据对应当前同一源码。2627个选源输入无漂移；六份分区压缩/解压摘要及原授权/设置备份重新校验。
- 产品/设备：release构建成功，同签 `adb install -r` 保留UID10498及数据，设备安装包摘要与候选一致。原生产 `NativeBridge.readInstalledHelper` 首次完成266319us（Java调用266767us）、1586520B、rootVerified/complete/receiptComplete/streamEof=true、ruid/euid0、childStatus0、无超时。Legacy模式 root.replyObserved=false 与 owner协议回执分别解释；真实Root由UID及完整流证明。该测试复用无SDK自带的通用Java测试入口，实际加载r12 JNI；Application启动仍可能产生独立后台warmup，不将本结果记作五次冷进或纯隔离时延。
- 尚未通过：现成r12后台启动日志 live-observation776–937ms、manager-su2132–2301ms、terminal ready=false/ULTRA_KEY_CHANGED。未执行五次冷进或目标应用SU验收；安装APK不等于已替换设备旧helper/su/resident。
- Panic：08:48:02 UTC再次NX重启至 `c956cf6c-6c69-469a-a3c1-48fc54fde859`，先保存日志并暂停功能触发。随后一次既定r12 Root返回用于旧broker SIGSTOP和现场/代码保全，未修改授权/config；后来PID4081消失，退出原因未证。实际旧broker加载 `libmaplesu_pro_bridge.so` SHA `0c27249e24132948ef802bb6a6ed0c754c83fbf406f4784fedd0bf15f07e6446`，配对APK SHA `ab9f827497321ddcf5fa5240b09ed0d3a553aaf4e64578dea035014d3e814f42`。新完整268435456B dump的用户LR精确匹配该实际桥dispatch返回点；这是生产者绑定，不是NX根因已修。旧07:21完整dump、07:38/08:22日志与本次完整现场分开保留。

证据：`.tmp/main-rootpanic-resume-20261121/device-r12/`、`generation-r12/CONTINUATION-INPUT-CHECK.json`、`panic/panic-c956cf6c/FULL-CAPTURE.json`；实际新代码和dump位于 `[private-evidence]/maplesu-rootpanic-resume-product-20261121/panic-c956cf6c/`。SU总预算候选已真实Kotlin红绿（旧6检查1失败→候选6/0），尚未合入。继续按真实失败链修复；正式5.2.1与其余设备父项未结算，未新增完整勾选。

## 2026-11-21 08:20 UTC Root accessor 接续实测（r11）

已按固定基线精确采用 Root accessor 候选，生产 resolver SHA256 `447cbc52384964b58945492c4a6ace8f6f8d2d021ab3d21e91ff709afaa5ca23`。受限 sysfs 的五字段 Root 入口不先搬运完整内核 BTF；可读 sysfs、已有有效内存 BTF 及通用消费者的权威/错误语义保留。当前生产源码重新编译的16场景 ASan/UBSan 通过，见 `.tmp/main-rootpanic-resume-20261121/generation-r11/direct-validation/RESULT.json`。

- 源码/产品：resolver 已采用；r11 SDK `maplesu-ultra-source-cbff87f0b5ab5332` 已由完整 producer/review/stage 产出，helper/backend/payload 同代；APK 正在既有生产 Gradle release 构建。原 raw member 比较 exit7 与25 partial仍保原边界，未记成正式全部通过。
- 设备：原机 SERIAL/6.6.89/4KB/_a 保原数据。单成员隔离诊断首次仍302220us超时；定向共享函数计数一次297802us完整回读、UID/EUID0，READ高层请求29次/354B。该诊断有边界计数，不作为最终时延验收。
- 后继修复：定位到 `PatchCurrent::set_current_caps` 重复获取已由 PatchBase 携带的 cap_inheritable 偏移，重新进入通用BTF解析。隔离候选复用该输入，真实 m072 的35检查原像5失败/后像全过，NDK通过；两成员隔离无计数实测219556us完整回读1586520B、rootVerified/receiptComplete/streamEof为true、childStatus0。候选尚未进入r11 APK，不把隔离结果算Manager完整Ready或目标应用SU通过。
- 现场/备份：六镜像位于 `[private-evidence]/maplesu-rootpanic-resume-product-20261121/device-pre-191c120f/`，压缩与解压摘要/长度均重验；原授权/设置备份沿 `device-r8/AUTH-BACKUP.json` 重新验证，未输出内容。此次boot仍30145d80，未发重启/分区写。旧9490完整dump与07:38仅日志现场分开，Panic未修复结算。

当前证据入口：`.tmp/main-rootpanic-resume-20261121/ROOT-ACCESSOR-ADOPTED.json`、`generation-r11/COMBINED-EXECUTION.json`、`root/caps-offset-reuse/SOURCE.json`、`root/helper-native-caps-reuse-r11/device-once/RESULT.json`。Manager1000ms完整就绪/五冷进、实际应用授权/标准su、镜像/模块生命周期及Panic设备项继续未验收；未新增父项勾选。

## 2026-11-18 MapleSU / Ultra 当前源码统一接入

当前生产入口为 `runtime/maplesu-sdk/`、`runtime/ultra/` 及 Manager `ultrakernel` / `runtime.ultra`，共享构建、JNI、模块 ABI 和持久输入已成套接线。1945 条当前路径、1995 条演进事件及 40 处尾部修正见`本域交付`（开发证据未随公开快照分发：`parallel/2026-11-18/MAPLESU-ULTRA-SOURCE-INTEGRATION.md`）。集中受影响验证通过：43/43 文件测试、39 源 Kotlin 编译、47 对 JNI及模块 ABI 对照、恢复状态行为；完整同代发布包和设备回归尚待唯一主线执行。活动内部旧命名和相关备注已清除；历史来源/身份、冻结输入、原样本和第三方许可保持真实，原模块/持久资料兼容保全。未新增整个阶段完成勾选。

## 2026-11-18 P2 resident 源码接点与本轮验证

现有镜像加载链已实际接入 boot-owned 64B publication slot、唯一 retained host、CAS 初始化/READY 发布、失败清理与 quarantine，以及 P7 签名/fullUID/epoch/watch 和 A R02 QUERY/ECHO64/READ producer。slot 来源是目标 Image memory 范围内经完整 kallsyms 证明的 BSS→init_pg_dir 对齐间隙，由 kernel_init_freeable 包装器在本 boot 初始化；96B anchor 继续只作不可变来源描述。boot 后目标页表 RW/NX 尚无实际读取证明，链接布局核验与页权限实测分别记账。源码、宿主来源与生命周期详见 `P2 接点`（开发证据未随公开快照分发：`parallel/2026-11-17/P2-RESIDENT-OWNER-CONTACTS.md`）。

最终六 family native 编译及三份 support 构建成功；6.6/6.12 reference ABI、目标布局/函数及 KCFI 有相应证据，其他 family 的 BTF/ABI/legacy CFI/6.18 reference 缺口继续阻止资格提升。最终组合使用 resident v2 专用 bootstrap1452B、permissions788B、materializer1968B；旧正式 v1 assets 与 Key/v2 wire 保留。15/15 受影响镜像实际通过生成、独立完整 kernel 回读、同版幂等、自有记录恢复及 BlockDeviceTransaction commit→readback→restore（14 个 Boot_Research boot 加 1 个匹配设备历史 base）。26 行总账中的 11 个前置失败明确复用上一轮收据：4 个 reference 不完整、6 个独立 init_boot 缺 bridge producer、1 个 NGKI family 缺 producer；不是本次新增通过。源码与全部输入前后相同。

设备 SERIAL 重连后的固定只读采集成功，Manager 摘要前后一致、独立 probe 已恢复；该机未提供 kernel_page_tables/PTDUMP，未刷入或启动新 resident kernel。本机基线候选已生成不等于 OEM 原厂真实性、AVB 启动资格或硬件救援已证。完整发布还需页权限/生命周期/安装撤销实机证据、同代 SDK/helper/APK、三模式真实 SU 和 150ms/300ms 验收；PMS 内存提交早于 VFS 事件的撤销窗口及资格迁移仍留账。当前正式 Pro assets 未提升，TRUST-02 不新增完整勾选。用户已撤销旧停止记录作为本轮停止依据；历史事件保留，本轮没有新增工具拒绝。

本轮精确源码、实际选源及验证收据见 `持久交付账`（开发证据未随公开快照分发：`parallel/2026-11-17/evidence/mainline-integration/trust02-p2-resident-20261118/DELIVERY.json`）；P5–P7 据此继续消费者、完整免 Key 和兼容资格迁移。以下各时点记录保留其原范围。

## 当前逐项审计与实机验收（2026-11-17）



`119项完整源码审计`（开发证据未随公开快照分发：`parallel/2026-11-17/evidence/mainline-integration/requirement-source-audit/AUDIT.md`）已完成：3项沿用原完整验收、33项已实现待设备验证、9项尚未实施、74项存在具体依赖或源码缺口。该分布是13:58审计快照，不代表工程完成百分比；本次13路径源改后的动态状态见下文逐行及EXECUTION.json，全部局部实施和已验证范围保留。原需求文字、阶段/验收条件、20条约束和SDK-01/07/08三个勾选保持，本次审计不新增勾选。116个未终验ID已唯一分配到9项去重任务，原10组问题与镜像10项最终条件逐项列有真实入口、所需设备/原样本及证据缺口。



依用户最新指令，由原唯一设备执行方逐项实测；满足对应整项条件后及时勾选，未完成项按首错/源码依赖继续实施。源码未齐、同代产物未齐、缺原失败设备及实际审核停止分别记账；历史r9结果保留其范围。源/产物/设备当前接点见`主线集成`（开发证据未随公开快照分发：`SDK-REFACTOR-INTEGRATION.md`），历史追记按各自时点阅读。


后续指令更新：用户已撤销此前保留停止范围的要求，并明确P2/TRUST-02由另一对话负责，本对话继续源码收口、集中验收与逐项勾选。原需求文字、119个ID、两组10项及20条约束的历史快照保留；涉及旧停止的现状按当前交付与实际工具结果更新，旧审核事件不伪写为新事件。完整UID/传统SU新增源未验，不将Manager r9 UID0或普通主机通过计作新SDK、去Key或整项SU通过。


## 历史：2026-11-17 目标 KMI 与首页签名增量（验证前）

当前正在集中修正 Normal 八组 KMI 矩阵、boot 文件按目标内核选源及首页模式切换签名接线。新增源码尚未完成本轮统一验证；本地已集成资产仍为下文 run44074782699 的上一代，尚未登记为新增 Rust 修改对应产物。新用户态/JNI构建与正式资产替换完成前，源码和资产状态分开记录。已通过的18份普通离线镜像结果保留原范围，本轮只追加实际受影响项与签名验证，不重写历史首错。全部119项、原3项勾选及具体停止边界保持。


## 历史：2026-11-17 Normal生产资产正式集成（run44074782699）

Normal run44074782699的source与package成功，原run44069942181的八组LKM不重编。19项实际资产、16份真实内嵌资产、ARM64/16KB/JNI导出及双来源已由唯一验证负责人独立核验；现已正式窄合工作区44路径（资产、checksum、原构建收据、payload与来源JSON），旧材料完整备份。源码固定官方成功CI #5545的f4603d14，最终复核仍为最新成功主线项。新payload SHA256为6b15979b7d5efb5d4b5ed39cf53e7b1bca22060536abf2e28248084ca592bd8d，原ko保cf60/首run来源，用户态/JNI保3dc391/第二run来源；仅Cargo.lock差异和完整kernel相同证明随包保留。额外L3 JNI/gen2行仍属历史资产，不计本次19项重建。正式Pro r9、共享HEAD/暂存区及119项原3勾保持。

最终本地门禁和设备覆盖按`集中实录`（开发证据未随公开快照分发：`parallel/2026-11-17/evidence/mainline-integration/image-patcher-validation/REPORT.md`）及其收据登记；Normal构建/正式资产集成不代表新APK、独立Ultra桥、候选OEM签名或实际Root/SU启动通过。当前逐图18份生成基线恢复（14直接输入、4历史内核恢复后基线）、6组init_boot记录恢复和25组容器范围不扩大。全交付及停止交点见`交付报告`（开发证据未随公开快照分发：`2026-11-17-image-patcher-delivery-report.md`），精确44路径和备份见`Normal集成收据`（开发证据未随公开快照分发：`../.tmp/mainline-source-integration/normal-production-integration/RECEIPT.json`）。下文旧Normal待构建、库存和源码数量段保留各自历史时点。


需求来源：本次会话完整需求及 SDK 源码附件；固化日期：2026-11-15 UTC。

本文是持续实施的需求账本。P0总体评估已完成；P1 r9源码接入验收已通过，实际源码产物已进入正式默认SDK，本台五项调用和固定single、默认五消费者及来源/身份校验通过，范围与残留见`P1接入验收`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p1/P1-INTEGRATION-ACCEPTANCE-R9.md`）。当前继续P2–P4，P5–P7由用户新对话按`并行边界`（开发证据未随公开快照分发：`SDK-REFACTOR-PARALLEL-BOUNDARIES.md`）推进。完整功能保全、重复完整自举、旧资产清退与跨设备/开机/发布验收仍分别记账，进度见`P1记录`（开发证据未随公开快照分发：`SDK-REFACTOR-P1.md`）。完整总体评估见`P0评估`（开发证据未随公开快照分发：`SDK-REFACTOR-P0.md`）。旧还原文档中的非目标、旧行号和完成声明仅作历史材料；与本次总需求冲突时以本文为准。

## 2026-11-17 镜像集中验证当前状态

本节更新IMG-01/02/03/04/08/09/10、QA-05/09与CASE-06c/07b的普通离线证据；全部119项、20条约束和追加验收条件保留，完整勾选仍仅SDK-01/07/08。下文历史库存、未执行及旧矩阵数字继续保留原时点，不覆盖本节的实际结果。

- 已验证：本轮统一发现60文件路径、26镜像路径、25内容组，保留隐藏目录、out和别名；容器解析25/25、含内核编码完整往返19/19。18份非NGKI boot经实际`prepareTarget`完成源码生成、完整kernel独立回读、同版幂等、无本地回执重修补、换Key、raw及精确生成基线整图恢复18/18；其中14份基线为直接原输入，4份历史已修补图先经`ARCHIVED_KERNEL_REVERSE`恢复内核再生成，`originalContainerProven=false`，整图比较只覆盖恢复后的生成基线，原历史整图/OEM原厂真实性未证。输入、普通源码、assets及runtime jar执行前后保持一致；换Key不作为跨SDK升级。
- 已验证（普通文件模拟范围）：同18份实际候选分别通过直接写、短IO、部分写失败、回读损坏、journal失败和取消；另9个既有MultiPartition方法覆盖双分区中断与补偿。6组独立init_boot仅ramdisk/记录载体恢复通过；独立Ultra桥、真实SDK初始化与启动仍存在阻塞。
- 签名边界：strict普通恢复22/25，3份旧图仍以`INVALID_AVB_FOOTER`拒绝；实际生产显式路径随后通过，strict未放宽。候选只经过Builder，未消费OEM/key签名链；输入结构观察不等于候选密码学签名、metadata或启动可信。NGKI 5.4.61只验普通容器/codec/opaque恢复，原缺失入口停止保留且未运行generate。目录无5.15、6.18与Linux4.x镜像，对应覆盖、32位及页大小组合待验。
- 存在阻塞：复用2026-11-17T10:42:50Z的ADB只读结果为0设备；没有本轮刷写、冷启动、Manager或Root/SU验收，没有新正式Pro SDK/helper/backend/APK。

证据：`集中实录`（开发证据未随公开快照分发：`parallel/2026-11-17/evidence/mainline-integration/image-patcher-validation/REPORT.md`）、`逐镜像执行结果`（开发证据未随公开快照分发：`parallel/2026-11-17/evidence/mainline-integration/image-patcher-validation/PER-IMAGE-RESULTS.json`）、`签名范围`（开发证据未随公开快照分发：`parallel/2026-11-17/evidence/mainline-integration/image-patcher-validation/SIGNATURE-SCOPE.json`）。Normal生产另账：run44069942181的source及8个LKM任务成功，实际14份ko已产出；首次package在`cargo fetch --locked`退出101，缺少ksud直连flate2锁边。隔离锁仅补该依赖边后`cargo metadata --locked`通过，正式patch/锁与package复验仍在推进，未将部分CI成功记为19产物最终交付或设备通过。

## 2026-11-17 接续执行状态（镜像集中验证前）

接续目标完整保留P0–P8、119个ID、20条约束及原停止范围；旧主聊天暂停。Normal f4603d14源码/补丁/applied与公共接口已接入，保Normal8/Ultra6及旧产物原身份；ModuleScreen非模态等待/取消、完整模块身份元数据、双形状回调等必要普通接线已完成。源索引673路径/514条有实际写入收据，包含测试与来源，不当功能数。

唯一验证负责人已完成本次普通组：当前Manager接口编译及HTTP身份修后增量、137方法首135过/2旧契约修后定向过、installer1、Ramdisk3287检查、WebUI34、Ultra9、moduleInfo1、callback4/Chromium、LocalSave2；未变B43证据复用。产物代际构建入口最后3处边界仍在修正及纯工具验证，已过普通产品组不重跑。完整勾选仍SDK-01/07/08三项；当前无新SDK/native/APK，Boot26路径/25内容组未真实修补或模拟刷写，设备0。四条关键链/m073 IR及最终交付继续存在阻塞，具体范围和源/产物/设备分账见`当前主线集成`（开发证据未随公开快照分发：`SDK-REFACTOR-INTEGRATION.md`）及其`集中验证`（开发证据未随公开快照分发：`parallel/2026-11-17/evidence/mainline-integration/continuation-validation/REPORT.md`）。下文历史零编测或旧选源统计保留历史时点，不覆盖本节。

## 当前进度快照（2026-11-17 UTC）

最新统筹与实际源合入见`主线集成与阶段出口`（开发证据未随公开快照分发：`SDK-REFACTOR-INTEGRATION.md`）：已将普通SDK模块/安装、helper、typed授权bridge/JNI、Manager环境/模块/WebUI及普通状态消费者窄合原树，保留P2改动。初始411路径快照中232条有实际写入收据，当前按同版增量合并后的436条索引中276条具有实际写入收据；不以字节相同或文件存在视作功能完成。真实slot/retained host/producer未交付；本轮其实施调用被自动审核以“possible cybersecurity risk”拒绝，具体动作记录阻塞且不换入口重试。独立Ultra init_boot桥与P3/P4核心仍未闭合。Boot_Research26路径/25唯一镜像仅库存，本轮无构建、测试或设备；正式r9及3/119完整勾选保持。

**P1源码接入里程碑已验证，整个重构尚未完成。** 完整验收保持3/119项（SDK-01/07/08），其余116项未终验；P0评估交付另计9/9。这个计数不是开发工作量完成率。完整阶段、镜像、设备、候选与剩余工作见`进展评估`（开发证据未随公开快照分发：`SDK-REFACTOR-PROGRESS.md`）。

本次集中修改8条源码/清单/现有用例路径，修复3个迁移安装器漏选、对齐14个普通成员JSONL及helper实际来源，并窄合最后2处普通结果传播。当前m073仍以IR为构建输入，尚未达到本需求的全部可维护源码交付；P1历史接入证据不覆盖这一当前选源缺口。原全部需求、阶段依赖和验收条件保留，没有新增完整勾选，当前无新包或设备通过结论。

后续新增1条普通容器生产源与1条现有fixture改动：ZSTD ramdisk已接共享有界解码及尾部检查，待整合后统一验证。原119项、P0–P7依赖和终验条件不变，本次未以源码适配增加完整勾选。

| 范围 | 当前真实进度 | 尚未完成 |
|---|---|---|
| P1 | r9源码正式默认接入、五消费者及本台五项/fixed single通过 | partial全部行为、完整自举可复现、全功能与最终发布/清退 |
| P2 | 布局/缓存/容器与持久恢复已有分批窄证据；R20/R21去预检/直接begin和文件交付62例通过，R22单执行方及安装prepare时刻4例通过；四份已知旧图原核恢复已有既有离线证据并核对接线 | 四旧图真正跨SDK/正式包/设备、完整NGKI、独立Ultra init_boot桥、全语料/设备；新整包待接入；旧SDK生命周期补编/R11用例的停止范围保持 |
| P3 | QUERY/ECHO64/READ、metadata、标准帧与参数/身份边界有分批证据；R25真实Key三个变体、旧base43/metadata2400检查及三Android TU通过，TSan工具环境阻塞 | 独立执行/资源生命周期、旧cred/x17/securebits回执、首次全零QUERY歧义及新旧完整配对 |
| P4 | 能力reader、m070/m072错误传播已有主机/Android编译证据 | prepare/commit/abort核心复制替换尚未实施，依赖P3 |
| P5–P7 | R20拥有范围组合与后续R21 typed/R23–R24子项有分批证据；本次只读接入索引已到R25聚焦选源 | R25源码仍按其实际验收收据；大部分未正式合入，去Key、真实su/无Manager启动、全部模块与设备未闭合 |
| P8 | 保留r9与各批来源/测试/设备基线 | 新正式整包、完整API、多设备/零模块冷启动、32位、环境与时延终验 |

设备2026-11-16已只读确认PLQ110在线、6.6.89/槽位_a，所装APK摘要等于P1验收r9；未部署本批开发修改，不能把它算作P2–P7新源码的实机通过。独立EXEC实现和NGKI后续入口分析已有自动审批停止记录，相关部分保持阻塞，详见进展评估。原119个ID、完整需求与最终条件均保留。

最新`R25–R26源码批`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R25-R26.md`）另补历史归档旧图在永久卸载消费链的来源接入，以及共享镜像复制的发布前回读；三生产源窄编与6个普通用户态方法通过。独立Ultra早期入口已有R23静态/host证据，实际bridge、正式producer和冷启动仍缺；当前Ultra仍固定boot，显式init_boot尚未支持。本批没有新增完整需求勾选或设备/正式交付结论。

`R27续批`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R27.md`）已修普通boot/init_boot组件填充保全、构建收据一致性和历史SERVICE任务恢复；406/181组件检查、18项工具测试及25个普通恢复方法通过。三份真实样本只做原组件重建，未进行内核功能修补；原需求、完整NGKI、独立桥、P3/P4核心及正式包/设备终验保持，完整勾选仍3/119。

2026-11-17用户最新分工：主对话专注P2 boot/init_boot修补与刷写；为用户准备两个新增对话分别承接P3/P4依赖组和SDK源码来源/构建交付，既有P5–P7保持。完整需求和终验不缩减，镜像层与运行时依赖分别记账，见`依赖及所有权补充`（开发证据未随公开快照分发：`parallel/2026-11-17/P2-DEPENDENCIES-AND-HANDOFF.md`）。原停止范围不转交重试；提示词交付不等于对应功能已实施。

既有源码及验证分工见`R5–R6记录`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-UNIFIED-BATCH-R5-SOURCE.md`）及`R7–R9记录`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/LAYOUT-CONSUMER-SOURCE-R7-R9.md`）。`R9–R11源码批`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-LIFECYCLE-BATCH-R9-R10.md`）的已产出结果按原范围保存，后续回归被自动审查中止的缺项保持未验证。`最新R12源码续批`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R12.md`）已修切换完整写集、容器字节间隙及五个inode字段入口；本批只写源码/用例，未运行产品测试。不重复历史整批，不将局部主机证据视为完整P2或设备验收。

后续范围见`R14–R16源码与验收`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R14-R16.md`）：R12/R13的普通文件/模式编排与gap已由唯一测试代理集中验证；原inode/SDK停止回归没有转交或重试。当前主线仍是P2，修补修复尚未完整验收，新增SERVICE持久计划不等于独立init_boot桥、完整NGKI或P3/P4完成。

2026-11-17 R35 追加：首次输入容器精确保全、流式恢复记录和 Manager 实际 Builder/Recovery/永久卸载来源接线已完成限定主机验证；Recovery 最终300检查，17唯一普通Kotlin方法分代15＋2通过。容量不足仍保原v1并准确记恢复范围，未新增安装票。原3/119勾选、119个完整验收及正式r9/设备不变；Flow/stock本批仅完整TU编译。见`R35实施与残留`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-17/SOURCE-CONTINUATION-R35.md`）。

2026-11-17 R36–R37追加：恢复完成边界与Manager临时资源收尾修复后，Recovery300/16方法、新ramdisk文件记录419检查及byte/span真实容量增量1方法通过。两boot＋两init_boot真实普通容器完成载体变化与首输入整图相等，原编码组件未改；不是Root修补/冷启动/OEM认证。全部119要求、3原勾选、正式r9与设备不变，见`R36–R37`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-17/SOURCE-CONTINUATION-R36-R37.md`）。

## 2026-11-16 P5–P7真实进度复核

按当前总账119个唯一完整需求核对：**3项已勾选（SDK-01/07/08，沿用原P1限定接入验收），116项完整需求仍未闭合；P5–P7没有新增完整父项完成。** 3/119约2.52%只是完整条目勾选比例，不是源码工作量、功能可用率或项目工期比例；本次不编造加权工程完成百分比。已有多批源码与主机子项通过，不能据此把下面的核心目标改成完成。

| 核心目标 | 真实进度与当前证据 | 尚缺的完成条件 |
|---|---|---|
| SDK与MapleSU深度适配 | 已实现待设备验证：R20拥有范围源码组合87阶段/316登记路径；授权、安装、模块、状态与WebUI已有分批源码成果。E2-C→D1–D5→B/C2的22路径共享授权包实际应用/逐字节回读通过；R21写回执两源的21项Manager与10项daemon增量、Android窄编通过。 | 这些共享候选尚未正式接入，完整SDK/helper/payload/backend/APK及设备闭环仍缺，不能称与KSU同等一体化完成。 |
| 去不必要内部Key/手动配对 | 存在阻塞：签名轮换/恢复与boot-session选路有局部实现；**去Key主目标尚未实施完成**。当前ProEnvironmentProbe仍要求48字符Key与验证/配对记录，ProSdkCredentials仍选择session key或永久Key。 | 唯一早期身份与持久授权接入、旧Key迁移/撤销、无本地Key重装恢复、无需打开Manager的启动验证；不能只删除参数或将缺Key当未安装。 |
| UID授权、传统SU与KSU等价 | 已实现待设备验证：持久化/撤权错误、名单读取、同次提交/回读/pending/unknown及Profile假失败已修限定路径；B/C2分别23/28项行为通过，R21另31项增量通过。 | 名单或commit成功不证明真实Root；普通32/64位应用su、-c/交互、stdout/stderr/退出码、多用户/共享UID/撤权、Manager关闭/未启动/重启的正式设备验收尚缺。**完整等价未完成。** |
| SDK完整重构与内部MapleSU归属 | 已实现待设备验证：多个唯一业务实现已迁移，旧模块ABI/格式/数值码集中兼容；R21五成员native载体与runtime/WebUI/helper调用方源码正在同批实施，尚未验证。 | 部分内部载体/命名/错误域、共享公共Hook事务仍未清零；P3独立执行回执/生命周期、P4凭据复制替换归原所有者，当前未完成。必要版权许可保留。 |
| 无新增环境副作用/无泄露点 | 尚未实施完整验收：已有私有目录/错误传播等限定修复；/data/local/tmp首次父目录777分支只有独立候选，不能据此认定每次开机写者已定位或既有777已修。 | 最终组合下文件权限、SELinux真实状态/策略、挂载、进程、属性、未授权视角与延迟差分仍待验证；**没有“零泄露/零时序差异”通过结论**。不以检测器计数伪造作为完成证据。 |
| 无Manager开机、模块兼容与速度 | 已实现待设备验证：helper阶段消费者、事件顺序、列表/策略请求和WebUI编排有主机证据，未重复测试计数。 | 真实init事件producer、Zygisk-Next/LSPosed原包实际效果、零模块首启/安全模式修复、Y700/iQOO/VIVO等设备及真实300ms目标仍未终验。 |

本次更新只补充P5–P7真实子项与缺口，不改变119个唯一ID、原全部验收和原完整勾选。共享接入按`统一索引`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p5-p7/SHARED-CONTACTS-R18.md`），当前证据与实现状态见`阶段账本`（开发证据未随公开快照分发：`SDK-REFACTOR-P5-P7.md`）、`本次复核`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p5-p7/progress-review-20261116/PROGRESS.md`）。用户最新节奏继续执行：先完成整批源码，仅做必要接口核对，再集中运行受影响验证，减少历史重跑和重复构建。 当前Key/SU/环境事实另有39份输入的`源码复核`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p5-p7/progress-review-20261116/KEY-SU-CURRENT-AUDIT.md`），记录当前原树、隔离源码和独立候选的真实区别。

## 1. 持续执行约束

1. 保留 Ultra SDK 和全部既有功能，保持原 Ultra/SKRoot 原生模块兼容；APatch/KernelPatch 是实现参考和后续对照对象，当前不替换为另一套后端。
2. 先评估、明确调用链与验收，再改代码。修改以 SDK、后端、构建、修补、模块和授权为主；UI 布局固定，局部逻辑调整限本需求所必需。
3. 前端只显示必要状态、可执行操作和结果；调用链、SDK 诊断、兼容明细、开发证据进入开发日志。日志清晰，以 KSU 的使用流程为参考。
4. 目标内核布局由目标镜像的 BTF、访问器语义和只读验证获得；不建立固定内核指纹库，不以镜像哈希识别兼容性。版本仅作诊断；交付物哈希只证明实际打包内容。
5. 移除镜像修补、安装事务的独立前置检查和阻断，对齐KSU实际执行流程，不新增“安装检查尚未完成”式门槛；目标解析和执行错误由执行链当场返回，保留回读与失败恢复。文件修补不依赖Root服务、已安装环境或安装事务就绪。
6. 保持 Ultra 既有环境边界，避免新增普通用户可见的挂载、全局 SELinux 策略、属性或常驻判定依赖；非授权应用的开销与环境变化需要测量。
7. 内部命名、实现与维护归属 MapleSU；既有外部符号、数字错误码、模块格式和持久化输入通过集中兼容层维护，禁止全局替换破坏 ABI。
8. 去除不必要SDK Key是必达目标：在现有通道稳定后实施身份/签名与持久授权方案，消除手动配对、重复注入和打开Manager才启动的依赖；独立评估用于确定实施方案，不能替代最终交付。
9. 子代理按独立所有权并行，主代理管理需求、依赖、边界、证据和验收。不得覆盖已有未提交改动。
10. 验收状态只用“已验证”“已实现待设备验证”“尚未实施”“存在阻塞”。编译、静态形态、进程存在、历史设备日志分别记账，不等同功能完成。
11. 完成一项并取得该项所需证据后打勾；单台设备、单个内核或部分模块通过，记录其范围，不扩大为全部内核/全部模块通过。
12. P0阶段已按只读边界完成，源码、预编译库与备份、链接SDK和设备状态均有基线。后续按用户已授权的P1接入与必要实测推进；正式消费切换、清理和设备写操作分别取得对应验收证据，唯一预编译备份继续保留。
13. 最终发布的SDK、helper、payload、backend全部来自对应可维护源码；IR、参照二进制和旧对象仅作过渡/比对。各子系统替换验收后清退重复实现，功能保全与过渡残留逐项记账，终验遵守第10节。
14. 环境安装可靠性须由SDK、目标镜像修补和启动/授权链路的根因修复达成：复现失败并证明最早异常环节，修复对应实现，验证首次安装、首次启动与实际Root结果。扩大重试/延时、反复重启、依赖Manager在线、添加包装兜底或抑制错误提示均不单独构成通过；已有失败恢复能力继续保留，验收明确真实设备与目标镜像覆盖范围。

15. 优先实现功能并使用已有工具链做聚焦验证，减少重复 SDK/Gradle/APK 全量构建；统一到实际接入节点核验交付身份与必要设备行为。
16. 第三方 SU 已可用而 MapleSU/KSU 尚未安装时，默认从当前目标分区快照或用户文件直接生产候选，对齐选定 KSU 的实际直接安装流程。删除按旧管理器身份自动寻找/还原备份的前置分支，不要求原 Manager 在线或备份齐全；必须覆盖其他软件已授 Root、init_boot 已被第三方修补的直接安装，用 KSU 实际目标/路径和真实结果对照，第三方标记本身不构成阻断条件。显式原厂恢复、永久卸载和确需移除旧载体的模式转换各自保留真实来源；具体格式不兼容由修补器返回，不把全部第三方修改宣称为可覆盖布局。
17. 模式切换入口点击后立即进入切换页；页面导航不等待安装事务连接、Root/Manager就绪、原件或完整备份。切换执行按实际载体选择最少恢复步骤，不把完整boot/init_boot baseline作为所有切换的统一前提：Ultra载体在init_boot且格式已识别时复用KSU式ramdisk精确恢复/再修补；Ultra载体在boot时先用适用原备份，再尝试源码支持的精确还原。缺少整图备份本身不阻断可证明的原地恢复；真实目标格式/执行错误由操作链返回，写后回读和失败补偿保留。覆盖备份存在/缺失、两种载体、已修补图与第三方SU，分别验收实际分区和启动结果；独立Ultra init_boot内核桥尚未闭合的事实继续记账，不据此虚报支持。关联IMG-03/04/07/08/09、API-02、CASE-06a。
18. 零模块启动为SDK/镜像修补的共同必验基线：逐机型/固件/内核/页大小/载体覆盖原图修补、5.2.0已修补图升级与重复修补，首次冷启动在Manager未打开时取得真实内核回执和已授权应用Root；分别定位无Root、真实安全模式、残留标记与状态误报。安全模式修复动作须对应真实原因，验证执行回执、重启后的通道/授权及状态，不以反复重启、清提示或强置Running验收。工作区全部实际boot/init_boot及其实际压缩/签名/损坏边界逐项登记；未取得的设备、镜像类型和启动组合明确待验，不从单机或容器回读推导全机型通过。关联QA-09、CASE-09/16、INIT-01、IMG-08、FINAL-08/09。

19. 2026-11-17最新统筹要求覆盖此前小批次验证节奏：先完成拟交付版本全部源码、公共接口、选源与约束收口，再由唯一测试代理统一执行受影响回归、Boot_Research单轮和匹配设备验收。禁止改一点回归一点，不以继续增加小批次、冻结包或重复归集候选代替产品集成。主代理统筹常驻宿主→P7身份生产端、P3完整独立回执→P4凭据生命周期、独立Ultra init_boot桥、同代正式交付四条关键链。未解除的实际停止范围明确保留，源码未变且证据有效的项目不重跑；减少测试不减最终验收。

20. 当前阶段出口依次为：TRUST-02真实宿主/发布位置和producer实际交付，或准确记录具体停止交点；已选源合成一套主线，保留P2改动并清楚列未合入路径；Boot_Research按内容去重仅跑一轮、逐图记录真实成功与首错；产出同代SDK/helper/payload/backend/APK，在匹配设备证明“修补→未打开Manager冷启动自动安装→Manager识别→实际Root/SU”。源码缺口与自动审批停止分别记账，不将尚未实施的架构误写成审批拒绝。状态见`主线集成`（开发证据未随公开快照分发：`SDK-REFACTOR-INTEGRATION.md`）。

## 2. P0 交付清单

以下勾选指 P0 审计交付，和后续功能实现的勾选分别记录。

- [x] P0-01 固化全部需求、依赖、边界、状态及持续更新办法。
- [x] P0-02 建立 Git 工作区、SDK 源码输入、正式消费库与候选库的基线。
- [x] P0-03 对账源码、归档成员、旧补丁、helper、payload、bridge、backend 与 APK。
- [x] P0-04 核实还原程度和剩余不等价项；区分元数据声明、结构/符号等价、运行行为。
- [x] P0-05 核实布局、通信、凭据、Hook、重定位和初始化的当前调用链。
- [x] P0-06 核实 Normal、Ultra、越狱、模块与开机路径，以及“入口未就绪”和 ksud 错误线索。
- [x] P0-07 只读盘点现有设备、ABI、内核、APK 和运行状态，记录实机/模拟测试条件缺口。
- [x] P0-08 提供分阶段实施、分工、回退和验收矩阵，写明下一批具体工作。
- [x] P0-09 复核本轮保护文件与基线一致，设备执行操作维持只读。

2026-11-15 P0验收：以上9项仅确认审计交付。P0-01/08见本账本与`P0评估`（开发证据未随公开快照分发：`SDK-REFACTOR-P0.md`）的阶段/验收矩阵；P0-02/03/04见`交付审计`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p0/SDK-DELIVERY-AUDIT.md`）；P0-05见`核心审计`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p0/SDK-CORE-AUDIT.md`）；P0-06/07见`模式与设备审计`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p0/MODES-DEVICE-AUDIT.md`）；P0-09见`原状复核`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p0/evidence/workspace-verification.json`）。本账本现有119个唯一功能/案例/终验需求ID（不含上述9项P0审计交付；P0冻结时95条，后续补充与细化要求均保留在下表），仍逐项独立验收，未因P0完成打勾。2026-11-15本批复核仅SDK-01/07/08三项取得对应接入验收并勾选，其余116项继续按各自完整条件验收。

## 3. SDK 来源、构建和清理

| 完成 | ID | 要求与验收 | 阶段 | 当前状态 |
|---|---|---|---|---|
| [x] | SDK-01 | 每个归档成员映射 idx、来源文件、实际编译输入、producer、旧对象和旧补丁；保留同名成员的序号隔离。 | P1 | 已验证：保留r9的260成员/38历史修补成员来源映射和正式接入完成；仅r9冻结路线已验证；当前开发变更和m073选择另计，不推导全部行为完成；未完成：当前后续源码发布时重建对应来源→对象闭包；SDK-04历史修复逐行为证明 |
| [ ] | SDK-02 | 对比正式 SDK 与重建库，逐项处置 partial、IR、inline、符号和结构差异；不能用 adjusted 门禁替代 raw 差异说明。 | P1 | 存在阻塞：原始/adjusted差异与partial元数据仍保留；实际清单仍含未恢复完整成员；r9的来源路线/历史raw审阅有限通过，不代表当前全部inline/结构和函数行为相等；未完成：25 partial成员逐项行为和inline/结构差异关闭；本轮当前源真实raw门禁与ABI差异处置；m073完整可维护源码 |
| [ ] | SDK-03 | 完整可维护源码成为真实构建输入，SDK/helper/payload/backend逐项记录来源与替换状态；IR、旧对象及参照二进制仅作过渡/比对，最终发布不使用它们，补齐真正行为缺口。 | P1/P8 | 存在阻塞：SDK/helper/payload/backend维护构建链和r9正式接入已经实施；本轮普通新源已合入，但当前仍有原bitcode选源缺口；r9实际archive/helper/backend和五项/single限定验证成立；当前全部新源尚无同代正式三件/APK；未完成：m073 bitcode替换为完整可维护源码；全260当前源→实际对象→四嵌入payload→helper/backend/APK闭包；全部partial行为与完整既有功能保全；最终过渡输入清退 |
| [ ] | SDK-04 | 所有原二进制修复迁入实际参与构建的源码；验证发布包执行该实现，不能只改未选中的 C++。 | P1 | 存在阻塞：r9的38个历史修补成员已映射真实构建路线；当前部分历史补丁行为仍未在所选源码闭合；来源映射已验，当前发布包执行全部旧修复的功能证明未完成；未完成：38修复逐行为→当前源→正式对象→执行证明；m029 checked发布/完整宽度候选正式接线；m073完整源恢复；实际新APK执行路径验收 |
| [ ] | SDK-05 | build.py 形成统一源码构建入口；对 --all 与 --source-payloads 的真实差异逐一验收。 | P1 | 已实现待设备验证：r9真实build.py --all由空输出完成256→260、4源码payload/0reference及最终helper/backend复链一致；17项入口用例实际验证--all/--source-payloads/--reference-payloads阶段差异。后续build.py/build_helper/build_payloads及安装器选源已变化，尚缺当前入口/选源的受影响聚焦回归。两次clean归SDK-09、m073完整源归SDK-03，不作本条额外门槛。沿用source-bootstrap-validation-r9.json/source-bootstrap-unit-r9.json范围，待sole同批确认当前入口。 |
| [ ] | SDK-06 | helper 的 7 TU、su/resetprop/WebUI 及四段嵌入资源分别核对契约、来源和实际嵌入字节。 | P1 | 已实现待设备验证：r9实际12TU helper来源、137契约断言/4配对loader、su/resetprop/WebUI/helper四段真实source嵌入字节及最终复链有证据。当前helper argv与su main/root_grant失败/exec合同已修改，缺本批聚焦行为验证和对应当前四段嵌入来源/字节；旧CLI解析/byte equality不覆盖曾存在的握手失败继续/exec假零。本条不附加INIT四事件、全32位或全模块生命周期门槛，相关条件各归原ID，整体目标不减。 |
| [x] | SDK-07 | 新建隔离候选 SDK，配齐 include、archive、helper、backend、entry、SDK-RELEASE、SDK-IDENTITY 和 retained inputs，五个消费目标链接通过。 | P1 | 已验证：保留r9隔离候选部件齐全、默认五消费者实际链接完成；r9候选与正式默认五target来源/身份限定验证成立；未完成：后续完整功能和同代新交付仍由其它ID验收 |
| [x] | SDK-08 | 主机行为和必要实机比对通过后正式接入，更新 CMake/Gradle 构建与身份，产物链各环记录哈希和版本。 | P1 | 已验证：r9经必要设备五项与fixed single后正式提升、CMake/Gradle身份更新已完成；本台6.6.89/arm64、同签debug代码APK的真实设备范围保持；新修改未继承r9设备结论；未完成：后续源改同代交付和新设备验收另记；release混淆/全内核/全功能不在原checked范围 |
| [ ] | SDK-09 | 两次干净源码构建可复现；保留原始库与源码构建库的独立身份，完整 APK 的库/helper/backend 来源可追溯。 | P1 | 已实现待设备验证：已有r5/r6重复归档和r9APK来源追溯；两次完整当前源六档自举未完成；r5/r6仅308对象/260成员与archive等字节；其runtimeHelperSource=false、runtimeBackendSource=false，不可扩成全链可复现；未完成：两次包含六档kernel/backend/helper/四payload的干净完整构建；当前APK实际Zip与设备加载身份；路径/时间等原始manifest差异解释 |
| [ ] | SDK-10 | 接入验收后清理杂乱预编译与链接 SDK，仅留一套选定的最新正式预编译备份；清理前解析引用与链接目标、冻结回退材料。 | P1 后 | 尚未实施：已存在限定中间输出清理工具和历史清理成果；最终SDK/过渡引用清退尚未实施完成；当前正式三件仍r9；本轮未重新删除或执行清理，旧账3152项/3.98GiB不提升为全部预编译清退；未完成：最终解析全部引用/链接SDK目标；确认唯一选定正式备份及回退材料；仅替换验收后删除过渡实现/重复产物；历史清理精确收据与保留清单在最终交付附全 |
| [ ] | SDK-11 | 修正文档、manifest、源码清单和消费契约之间的漂移；两枚 MapleSU 扩展错误码按实际消费者做兼容映射。 | P1 | 存在阻塞：两扩展错误码集中映射和普通manifest/helper接线已实施；当前manifest235 complete/25 partial并含FROM_IR，旧开发证据不等于最终对象来源；r9身份与历史窄语法/事务证据保持范围；本轮最终全构建、raw gate和实际打包仍未闭合；未完成：当前全部清单→实际TU/头依赖/compose漂移核对；m073完整源和非IR发布；完整raw差异与符号/inline ABI处置；实际交付消费者映射执行 |

## 4. 布局与正向镜像修补

| 完成 | ID | 要求与验收 | 阶段 | 当前状态 |
|---|---|---|---|---|
| [ ] | LAY-01 | 审计真正执行的全部修补路径，统一离线修补器、SDK 和运行时的布局解释，避免只统一旁路 BTF 代码。 | P2 | 存在阻塞：离线recipe、SDK若干getter和共享native BTF均有真实消费；当前仍存在旧执行型BTF/其它getter与独立Kotlin解释；历史共享parser/5.10和6.6 raw生成已验；最新18boot源生成属于镜像范围，非全部运行时布局统一；未完成：全部getter/boot消费者与旧推导清退；统一Kotlin/C++描述及语义；当前SDK同代产物和目标设备 |
| [ ] | LAY-02 | 建立共享布局描述：字段偏移、宽度、来源、解析版本和必要语义；目标身份不演变为指纹库或固定偏移数据库。 | P2 | 存在阻塞：共享描述与目标cred关系图已实施，未知owner不伪造；历史native parser和真实raw输入有通过证据；全部语言/无BTF语义/实际运行时闭包未完成；未完成：Kotlin与C++全部字段/语义解释一致；无BTF完整owner/回调语义；所有旧getter/启动消费者迁移 |
| [ ] | LAY-03 | 优先使用目标 BTF；无 BTF 使用访问器语义与只读验证，记录识别失败的具体字段，不凭版本号猜布局。按实际能力将无BTF动态解析作为可选路径接入NGKI目标，覆盖离线生成、首次启动布局生产和运行时消费者；无BTF与NGKI分别记账，不仅扩大系列名单，不伪造owner大小或套用固定偏移。 | P2 | 存在阻塞：无BTF可选访问器已接部分真实消费者；不能记成只有名单扩大或全部尚未实现；5.10/6.6源码raw生成有证据；NGKI describe和实际generate首错必须分别保留；未完成：NGKI完整generate与首次启动布局producer/运行时；无BTF全部必需字段/函数合同；目标缺do_execveat_common首错未解除；不重试原停止后续 |
| [ ] | LAY-04 | 替换 real_cred+8、uid+16 等未充分验证的关系推导和通过改写 GID/凭据猜字段的方法。 | P2 | 已实现待设备验证：对应关系猜测及候选GID写探测已被实际选源getter替换；能力getter也已前进为cap_capget输出语义；冻结无BTF/目标raw生成证据成立，当前完整runtime写集合未验；未完成：其它getter/启动/旧协议回执残留；P4字段独立布局与写入语义；同代实际SDK与必要设备 |
| [ ] | LAY-05 | 重构偏移缓存，隔离过期或跨目标结果，不再复用孤立四字节值；优先按本次目标解析，缓存不成为兼容前提。 | P2 | 存在阻塞：m038独立4B磁盘cache和name-only全局map已替换为可选线程/PID/身份cache；其它cache仍需收口；历史98检查/sanitizer与Android TU仅覆盖冻结m038；本次只核实际选源，不新增性能/设备通过；未完成：m028/global/live与其它getter缓存生命周期；遗留目录/旧缓存消费者清退；当前SDK/身份切换与设备性能 |
| [ ] | LAY-06 | 正确处理 cred、task、seccomp、thread flags、回调参数宽度/签名及 BTF 变体；覆盖边界写入和错误调用。 | P2 | 存在阻塞：seccomp.mode精确组合偏移/宽度与共享BTF变体解析已实施；cred/thread/callback全写集合仍未正确闭合；历史52例seccomp与native parser窄证据保留，当前不宣称全部凭据/线程/回调通过；未完成：cred/caps全部字段/owner与边界写；thread flags目标语义及旧执行器对象寿命；回调签名/参数宽度完整覆盖；正式当前三件/APK与设备 |
| [ ] | LAY-07 | 目标范围 5.10–6.18，按真实内核/配置/BTF/页大小/厂商分层验证；缺样本组合保持未验收。 | P2/P8 | 已实现待设备验证：5.10–6.18仍为目标范围；当前部分真实目标及源码负载可执行，样本缺口未缩小；最新集中18boot逐图普通生成/回读仅现有语料；目录缺5.15/6.18/Linux4.x镜像，设备/页大小未覆盖；未完成：5.15/6.18实际镜像与全部厂商配置；16K/64K真实设备；5.10–6.18完整启动/授权/模块和必要32位组合 |
| [ ] | LAY-08 | 用户确认“4系镜像”为Linux 4.x内核镜像；在保留5.10–6.18目标的同时评估4.x扩展，逐系列/架构/厂商配置记录支持条件与缺口，覆盖实际取得的4.4/4.9/4.14/4.19等样本，不静默缩小范围。核实无BTF布局解析、cred/task/seccomp、通信/compat、Hook/重定位、初始化与工具链契约；不得仅删除版本门槛或套用现有偏移认定支持。先提交源码依据与可行性方案，再以目标镜像模拟修补、回读和必要实机验证决定已支持范围；32位应用兼容与32位内核支持分别记账。 | P2/P8 | 存在阻塞：4.x可行性与current模型部分已分析/实施，未声明4.4/4.9/4.14/4.19完整支持；32位应用与32位内核分账；冻结current模型35 tests/52观察符合预期，真实5.10仅current helper；此证据非真实4.x完整subject/CONFIGURE；未完成：实际取得4.x系列/架构/厂商样本与全合同；旧thread_info current消费者；无BTF L3/CONFIGURE、通信/compat、Hook/重定位和工具链完整接入；逐图模拟修补/回读和必要设备 |
| [ ] | IMG-01 | 参考 KSU/KernelPatch 的目标镜像解析与修补实现，正向生成修改，复核每个修改范围、分支/调用和重建后的内核。 | P2 | 存在阻塞：目标解析→布局→正向计划→生成→回读已进入实际boot入口；四份历史Hook输入已恢复原kernel后进入当前生成器。 已验证范围：Boot_Research 25唯一容器解析，18份boot生命周期/候选writer通过，14份首输入整图精确恢复，4份仅生成baseline精确恢复；14份候选加签/独立验签。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：独立bridge.ko可维护源码、目标KMI/符号版本/CFI契约、正式ramdisk生产及init事件生产端尚未闭合；NGKI完整目标生成受原缺符号停止约束；全部真实语料计划区/分支与目标设备冷启动回执。 |
| [ ] | IMG-02 | 保留重建内核回读和哈希比对；证明写入仅发生在计划区域及必要容器元数据，覆盖错误偏移与截断输入。 | P2 | 已实现待设备验证：重建组件/全kernel回读、journal逐差异验证、错误偏移/截断拒绝和容器逆记录均有实际实现。 已验证范围：18份boot候选writer/生命周期，14份精确首输入恢复，6份init_boot仅容器保全；16/16历史签名/codec套件和14份实际候选验签。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：未覆盖的工作区镜像与全部损坏边界仍须逐图证据；同代最终产物在匹配目标的写后回读/冷启动结果。 |
| [ ] | IMG-03 | Ultra与KSU一样识别已修补boot，支持当前版本重修补和跨SDK升级；兼容时直接更新目标区，需恢复时使用保存的原boot再修补，避免叠加补丁。验证原图/已修补图、重复执行和失败恢复的实际结果。 | P2/P8 | 存在阻塞：当前已修补图先用镜像绑定逆记录/归档恢复再生成，避免递归叠加；SDK r7/r8/r9配对及失败回退有历史实测。 已验证范围：18份boot的同运行代重修补/恢复成功；r7 -19997、r8 -19998后旧APK恢复Root，r9既有boot五读成功。 待闭合：稳定SDK/boot运行时ABI与不兼容载荷升级/回退协议尚未形成完整实现和同代证明；历史第三方/旧格式的兼容及不兼容结果逐输入记录；真正改变boot载荷的跨SDK升级/回退与必要冷启动。 |
| [ ] | IMG-04 | 测试重复修补、跨 SDK 升级、失败回滚、原 boot 缺失、镜像容器/压缩/AVB/分区差异和模拟刷写恢复。 | P2/P8 | 存在阻塞：重复修补、无外部receipt恢复、容器/codec/签名和失败恢复已有实际入口与普通文件模拟。 已验证范围：18候选直写/短IO/部分写/readback损坏/journal失败/取消及9个既有多分区方法；14候选实际签名/独立验签/恢复。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：稳定SDK/boot运行时ABI与不兼容载荷升级/回退协议尚未形成完整实现和同代证明；独立bridge.ko可维护源码、目标KMI/符号版本/CFI契约、正式ramdisk生产及init事件生产端尚未闭合；4份签名前置输入缺失需真实可适用metadata/主vbmeta；NGKI及未覆盖历史输入与实际分区恢复。 |
| [ ] | IMG-05 | 明确底层镜像与 SDK 的稳定 ABI，兼容变更尽量仅升级 SDK；不兼容 ABI 的镜像升级路径必须有明确结果和回退。 | P3/P8 | 存在阻塞：镜像恢复记录已版本化，r7/r8/r9 SDK/Manager与既有boot的配对/错误及回退实际执行；不能再笼统写为从未实施。 已验证范围：r7 -19997、r8 -19998并恢复旧APK UID0，r9五读和fixed single通过；当前矩阵crossSdkUpgradePerformed=false。 待闭合：稳定SDK/boot运行时ABI与不兼容载荷升级/回退协议尚未形成完整实现和同代证明；按ABI兼容/不兼容分类的镜像更新与完整原状态回退；同代SDK/helper/backend/boot/Manager组合身份和冷启动证据。 |
| [ ] | IMG-06 | 优化修补/刷写操作流与用户日志，以 KSU 为参考；技术诊断进入开发日志，保留原有功能与恢复能力。 | P2/P8 | 已实现待设备验证：普通文件流程直接执行ksud，真实参数、stdout/stderr和原退出码回传；安装/恢复用户结果已区分实际写入和恢复范围。 已验证范围：旧Normal资产19项/16内嵌与12项payload核验、现有文件模拟写后回读证据保留。 待闭合：当前target-KMI新产物/最终APK的完整参数及用户结果；首次/迁移/切换/失败时UI布局与开发日志分层设备观察。 |
| [ ] | IMG-07 | 去除镜像修补检查、安装事务检查等独立前置条件；按KSU选取目标/传参/执行并回传结果。移除推荐资产、旧事务状态、Root服务或环境就绪对离线修补的附加阻断；目标解析、写入与错误处理在执行链完成，保留回读和失败恢复。 | P2 | 已实现待设备验证：Ultra默认prepareTarget与Normal普通进程文件修补真实选源；目标错误由执行链返回，保留回读/补偿。 已验证范围：当前boot18份生命周期、旧Normal资产核验与前期无Root文件进程用例为限定主机证据。 待闭合：target-KMI新正式ksud和当前APK同代接线；无Root服务/旧事务残留/其他SU迁移的真实入口、原码与设备结果。 |
| [ ] | IMG-08 | 实施并验证Ultra的init_boot路线，保留现有boot路线。参考KernelPatch/KSU/APatch实际入口，按目标镜像内核/ramdisk和分区内容区分早期辅助、配合已修补boot、或唯一修补目标。纯ramdisk加helper不等同具备内核能力；独立MapleSU内核桥须有可维护源码、目标构建/符号版本/CFI契约和真实SDK回执。先证明原厂boot字节不变、仅写init_boot冷启动后通道可用，再接完整SDK/helper及既有模块；覆盖目标分区选择、签名、重复修补、升级、恢复和实际写集合，以工作区样本逐项回读及模拟修补验证。容器、桥接、真实启动/SU证据分层验收；SDK凭据/通信/Hook的Panic根因继续独立修复。关联INIT-01、IMG-01/03/04/05、CASE-14。 | P2/P6/P8 | 存在阻塞：v4/kernel=0容器/ramdisk重建、恢复和独立AArch64 handoff已实施；并非全无成果。 已验证范围：10个all-OS-wrap主机场景与静态4K/16K/64K；6份init_boot当前仅容器恢复、kernelAndRamdiskContentChanged=false，没有Ultra部署条目。 待闭合：独立bridge.ko可维护源码、目标KMI/符号版本/CFI契约、正式ramdisk生产及init事件生产端尚未闭合；正式Ultra目标仍固定boot，当前generator要求非空kernel；原厂boot字节不变/仅写init_boot的真实SDK内核回执、传统su与未打开Manager冷启动；升级/重复修补/签名/切换/恢复完整最小写集合。 |
| [ ] | IMG-09 | boot/init_boot选择贯穿离线修补、直接安装、工作模式切换、热更新、升级、备份与恢复：按真实镜像内容、当前槽位和已实现的后端能力自动确定目标及最小写集合，操作日志回传实际分区与KSU参数/结果，不新增安装事务、Manager就绪或资产目录门槛。对照固定KSU源码的已修补镜像更新语义，覆盖从KSU及其他管理器迁移、原图/已修补图、同版本重复安装、跨版本升级、原始备份存在/缺失、混合boot/init_boot、失败恢复；可复用的已修补镜像直接更新，需要恢复时使用真实原件再修补，禁止把第三方未知修改当作可覆盖空洞、叠加旧载荷或宣称已恢复原厂。原厂kernel保全与SDK运行时边界分别验证；独立Ultra桥未闭合时不把惰性ramdisk当作可启动Ultra。保留UI布局与已有功能，以实际候选/写集合、回读及必要冷启动证明模式切换正确。关联IMG-02/03/04/07/08、API-02、CASE-06a/14。 | P2/P6/P8 | 存在阻塞：统一SourceRecovery、持久实际写集、最小恢复、最终签名与读回均已接既有生产类；第三方标记不是统一阻断。 已验证范围：18份boot候选writer、14份签名/独立验签/恢复与signed owned-removal文件模拟；不含SERVICE/Binder和冷启动。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：独立bridge.ko可维护源码、目标KMI/符号版本/CFI契约、正式ramdisk生产及init事件生产端尚未闭合；boot/init_boot贯穿热更新/切换/备份恢复的全部真实生产入口；跨SDK/第三方SU/A-B槽与中断后的同代写集、实际分区和冷启动。 |

| [ ] | IMG-10 | 设置中的永久卸载在保存有适用原厂镜像时恢复对应boot/init_boot及槽位原厂内容：区分真正原厂备份、安装前快照和第三方已修补镜像，保留其它分区，写后回读；缺少原厂证据时准确说明可恢复范围，不把迁移前镜像称为原厂。已有KSU/其他管理器SU时参照KSU直接安装机制，识别可重修补载体后直接安装，不要求先卸载对方Manager或先恢复原厂；需要恢复再修补时采用实际可用来源。覆盖首次/迁移/升级/切换/跨槽/中断与永久卸载，日志返回实际分区、参数、退出码和恢复结果，UI布局固定。关联IMG-03/04/07/09、API-02、QA-04。 | P2/P6/P8 | 已实现待设备验证：永久卸载复用同一taskId先恢复，失败保环境；真正原厂、首输入和仅移除自有修改的语义分开，未变化companion不写。 已验证范围：signed owned-removal前后验签、5错误输入拒绝、普通文件写后REBOOT_REQUIRED/失败RESTORED；14当前签名候选读回。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：适用固件/槽位原厂来源的真实匹配与stock写回；第三方SU直接安装/迁移、跨槽/中断与永久卸载同代APK和设备结果；独立init_boot所依赖的IMG-08桥与签名组合。 |

## 5. SDK 核心重构

| 完成 | ID | 要求与验收 | 阶段 | 当前状态 |
|---|---|---|---|---|
| [ ] | IPC-01 | 独立、共享的请求/响应定义：版本、长度、命令、状态和完整返回值，正确处理错误、未知命令、32/64 位边界。 | P3 | 存在阻塞：A R02共享wire、R03普通消费者与新resident QUERY/ECHO/READ producer的生产源码已交付；P2隔离native六family及最终support构建通过，15项fresh离线验证与11项复用前置失败分开记账。完整执行/资源命令独立回执、32位真实app/compat及设备配对仍未完成；Manager681ec6实际资产仍为legacy v1，未包含新resident。未完成：EXEC/ALLOC/WRITE等完整合同与生产接线、32/64真实配对、同代正式资产与匹配设备。 |
| [ ] | IPC-02 | 回执与 cred、securebits、x17 约定解耦；元数据通过显式指针和版本取得，逐步替代 LR 距离约定。 | P3 | 存在阻塞：只读QUERY/ECHO/READ、显式metadata、append54B边界已实施；标准帧已收敛，但执行回执仍未解耦；冻结真实冷链91/0、unpublishedReads=0有效；其中缺audit字段仍missingFieldImplemented=false。当前R03不是历史测试全部无变化的保证；未完成：独立EXEC及完整资源所有权/释放；全零QUERY的gateway身份歧义与选择合同；旧镜像集中兼容、完整audit字段及P4依赖；实际SDK与目标producer同代交付 |
| [ ] | IPC-03 | SDK 上层原调用方式不变；旧镜像、旧二进制和旧模块通过独立兼容路径承接，并做版本配对测试。 | P3 | 已实现待设备验证：公开调用形状、旧格式集中兼容和内部owner/v2选择均有源码；完整版本矩阵未完成；r7/r8真实Manager降回旧包、r9在旧环境五项成功已存在，不能称完全没有不同SDK构建配对；其范围仅Manager/归档构建替换；未完成：两代完整SDK/helper/backend/镜像/Manager支持矩阵；真实ABI升级/降级/不兼容恢复；当前owner/legacy选择的同代正式产物与必要设备 |
| [ ] | CRED-01 | 新协议完成后采用 prepare_creds → 修改已验证字段 → commit_creds，失败释放新对象，保持复制后替换语义。 | P4 | 源码开发已闭合（含隔离增量）：Root/CAPS共用manager_management.c的唯一prepare/逐字段/commit/abort事务，SDK新credential-capable路径实际接入，profile验证与引用所有权保持。当前精确事务四配置156场景/1,176检查、native management247及SDK回归通过；zero-handle采用后当前六目标三管理对象18对象Kbuild通过。ROOT errno窄修及必要测试/验证器在.tmp/p4-closeout-candidate-20261120/SOURCE.patch，尚未由MAIN采用。目标原生符号/引用/CFI/LSM及真实失败/并发、正式包与设备独立待验；完整父项维持未勾，见SDK-REFACTOR-P4-DEVELOPMENT-CLOSEOUT-20261120.md |
| [ ] | CRED-02 | seccomp、线程标志和 capabilities 依目标语义处理，避免凭据头连续清零和越界；旧执行器指针不悬空。 | P4 | 源码开发已闭合：新能力路径使用独立credential-object replacement，清退共享cred连续头及原位caps写；仅旧producer明确缺credential能力时保留独立兼容，错误不回退。task/seccomp/filter/count、syscall_work或thread_flag及LSM按真实目标布局/API资格处理，未通过资格时不虚报支持。精确task-state五配置260场景/3,290检查、PatchCurrent35及m07028场景和Android TU通过，当前六目标18对象编译通过。主机seam/对象编译不替代目标seccomp、引用、LSM/CFI、并发与设备证明；完整父项维持未勾 |
| [ ] | CRED-03 | Root 与回执在多线程、fork/clone、失败/撤权条件下均保持对象生命周期和结果正确。 | P4/P8 | 源码开发已闭合：事务持有task/mm/exec和主客观cred身份，commit effect与后置错误分开，session仅采纳实际committed cred；受控procattr合法替换按exact after-image重绑定。m070组合ROOT快照与m060/m046消费者保完整回执；隔离窄修保留post-commit errno。当前事务1,176检查、task-state3,290检查、Root receipt390（主机真实fork/pipe、内核seam）、native management247通过。七文件候选尚未由MAIN采用；目标真实线程/fork/clone/exec/撤权/竞争及正式producer/设备回执独立待验，完整父项维持未勾 |
| [ ] | HOOK-01 | 所有 before/after/替换 Hook 安装统一进入发布事务，覆盖已恢复源码与真正交付对象。 | P5 | 存在阻塞：当前manifest选中m029/m043已在本次3源批改为完整64位距离/对齐校验后编码，before/after真实调用既有checked publisher，保入口读回/恢复/pending；非空与空句柄均保disable标志。after禁用分支已按源码复核恢复原SP/FP/LR后tail bypass，尚未编测。active after仍有SP-96/9+栈参数源码缺口；当前语义/失败/SMP/正式产物均未验，不继承隔离818结果。源见 .tmp/mainline-source-integration/audit-followup-source/hook/RECEIPT.json。 |
| [ ] | HOOK-02 | 准备完成后发布、入口回读、跨 CPU 指令缓存同步、失败可追踪，停用与跳板释放遵循仍可能被执行的生命周期。 | P5 | 存在阻塞：实际m029两安装器已进入同一checked发布事务，准备后写入口、读回、禁用激活/恢复及pending保留；null out_handle不再省略disable标志。当前仅源码，分配/idle资源完整生命周期、异常和并发停用/grace period、跨CPU/SMP/PAC及实际产物/设备仍未闭合。after active栈参数缺口独立保留。源见 .tmp/mainline-source-integration/audit-followup-source/hook/RECEIPT.json。 |
| [ ] | HOOK-03 | 分支差值先按完整宽度校验再编码，编码失败不得发布；近/远分支、超界、回滚均有运行验证。 | P5 | 存在阻塞：完整宽度/失败不发布在隔离候选中已有实现和证据；当前主线所选路径仍未接收该修复；P5隔离测试保原818范围；当前源静态核对发现明确残留，未执行危险边界；未完成：当前所有实际分支完整宽度校验→成功编码→发布；近/远/超界/错误编码/回滚的当前真实运行；同代归档与设备边界 |
| [ ] | RELOC-01 | 统一指令重定位与语义；覆盖 PC 相对寻址、B/BL、条件分支、CBZ/CBNZ、字面量、原指令调用和支持范围。 | P5 | 存在阻塞：实际选中m043跳回和m029入口/跳回已复用完整宽度编码器，失败回传；普通B/BL、CB/TB及ADR等既有实现和隔离成果保留各自范围。本次需从当前实际源验证近远/对齐/溢出、BRK/BTI/PAC及SP/FP/LR；active after栈参数、完整重定位/现场与跨CPU硬件生命周期仍缺，未编译或新增设备结论。见 .tmp/mainline-source-integration/audit-followup-source/hook/RECEIPT.json。 |
| [ ] | INIT-01 | 明确解析、通信、凭据、内存、Hook、模块和开机服务的初始化依赖；避免重入、半初始化和无期限等待。 | P3/P5/P6 | 存在阻塞：原目标argv早退、会话/能力/保护、core→event→模块消费者已经在当前主线源码；真实init producer仍缺；历史25/25原目标入口和r9 single分别保范围；Manager普通接口编译不等于early boot执行；未完成：真实init触发四阶段及工具预部署；无Manager零模块首冷启动、连续重启、实际Root；重复/并发/半初始化/失败与所有阶段真实顺序；同代helper/SDK/backend/镜像/APK |
| [ ] | SDK-12 | 深度审计现有不合理实现，在逐项行为验收前不标为已解决 Panic；每类修复具备复现、失败边界和回归证据。 | P2–P8 | 存在阻塞：Key解析/append发布边界、普通容器边界等缺陷已有根因修复与限定回归；全核心缺口未清零；冻结append配对91/0且unpublishedReads=0成立，纯Key解析42例红22失败→绿42/42历史范围保留；尚无对应设备Panic因果证明；未完成：每类当前修复对应复现/失败边界/源与交付；P3/P4/Hook/布局/初始化全部残留；指定崩溃pstore/时序与匹配实机回归；实际同代发布及所有必要设备 |

## 6. SU、授权、命名与错误体系

| 完成 | ID | 要求与验收 | 阶段 | 当前状态 |
|---|---|---|---|---|
| [ ] | SU-01 | 对齐KSU授权语义，形成稳定UID授权和传统su获取Root的完整路径，兼容su、su -c及交互调用，正确返回退出码和stdout/stderr；保留Ultra现有底层与功能。 | P6 | 存在阻塞：传统su、typed授权与Manager/越狱会话接点已在主线；本次3源已修握手/目标身份失败立即非零退出、exec失败保原errno并返回127/126，待sole普通host验证。标准su/su-c/PTY、完整双流/退出语义、同代payload与32/64应用真实Root仍未闭合；正式r9不含本批字节，原SDK补编停止保留。源见 .tmp/mainline-source-integration/audit-followup-source/su-errors/RECEIPT.json。 |
| [ ] | SU-02 | 已授权调用无需每次查询 Manager/额外进程；测试管理器关闭、未启动、进程重建和重启后执行。 | P6 | 存在阻塞：授权和boot-session消费者已主线；真实早期身份/宿主/启动producer未闭合。 验收缺口：真实早期身份/入口/保留宿主及init producer；Manager关闭/未启动/重建/重启后SU和额外查询/时序。 |
| [ ] | SU-03 | 支持32位软件通过传统su获取Root，兼容32/64位应用混合使用，验证入口发现、compat请求、UID、参数/结果宽度及ABI；验收必须包含实际32位应用的su命令与交互执行。 | P3/P6/P8 | 存在阻塞：32位compat/参数限定子项按原P3证据保留；此片核到的是传统su与配对backend接点，配对loader本身不证明32位compat；完整标准su/无Manager启动依赖未闭合。 验收缺口：32位应用su命令/交互和32/64位混用；入口发现/compat/UID/参数结果宽度/ABI；SU-05/06差分。 |
| [ ] | SU-04 | 授权/拒绝/撤销/重复授权、共享 UID、多用户、包更新和并发的身份与持久化结果一致；减少授权故障。 | P6 | 已实现待设备验证：typed授权JNI/Manager和scope迁移/恢复已正式主线，旧待接入说法失效。 验收缺口：真实UID授权/拒绝/撤销/重复执行；共享UID/多用户/包更新/并发的身份持久化；同代归档/JNI/Manager。 |
| [ ] | SU-05 | 未授权应用不获得Root环境、SDK、Key或其他应用授权状态等新增可观测信息；不因SU适配增加调用耗时或改变调用时序，不新增Manager/额外进程查询、IPC或等待。按固定KSU基线及同设备原始环境分别验证授权/未授权路径，记录延迟分布与测试条件。 | P6/P8 | 尚未实施：已有私有工具/名单/scope约束；完整固定KSU与原始环境差分尚未实施。 验收缺口：同设备KSU/原始环境授权与未授权观察面；SDK/Key/其他应用状态差分；额外进程/IPC/等待/调用时序与延迟分布条件。 |
| [ ] | SU-06 | 32位软件与传统su兼容不增加SELinux策略修改或挂载；未授权应用视角不新增可见的Root相关文件、进程、属性或环境状态。对齐KSU授权语义，Ultra保留既有低暴露边界，逐项做环境差分验收。 | P6/P8 | 尚未实施：私有发布与模式边界保留；32位/传统su完整环境差分尚未实施。 验收缺口：32位/传统su不新增SELinux/挂载实测；未授权应用文件/进程/属性/环境差分；固定KSU和同设备原始环境对照。 |
| [ ] | TRUST-01 | 单独设计并实施 SDK 与 MapleSU 身份/签名绑定：信任根、系统早期开机、升级、签名轮换、卸载重装、旧key迁移/撤销、故障恢复和旧模块兼容。 | P7/P8 | 存在阻塞：平台轮换/重装恢复、签名parser与owner/watch已有源码；2026-11-18 P2交付真实resident host、boot slot来源、同一R02 producer及owner/watch/epoch生产接线，隔离native/support与15项fresh离线验证通过。未完成：目标RW/NX叶权限及新resident内核冷启/跨请求/并发/boot寿命设备证据；PMS/VFS撤销窗口、签名轮换/重装、旧Key迁移撤权、完整免Key消费者与匹配设备。源码和离线结果不等于实际owner claim/Root/SU成功。 |
| [ ] | TRUST-02 | 稳定通道后去除不必要Key、手动配对、重复注入和打开Manager才启动的依赖；必要认证与UID授权由明确身份和持久授权机制承担，实测无Manager冷启动及升级/重装路径。 | P7/P8 | 存在阻塞：R02/R03消费者及Manager/JNI已在主线；2026-11-18 resident host、boot publication slot来源、一次boot初始化、同一producer、P7核签/fullUID/epoch及watch撤销接点已有生产源码与隔离产物交付，状态为已实现待设备验证。15项fresh离线通过，11项前置失败明确复用；目标RW/NX叶权限未证，新resident内核未启动，Manager681ec6实际仍打包legacy v1。未完成：正式组合、实际claim/allowed/撤销/生命周期、完整免Key及旧资格迁移、持久授权、无Manager冷启动/升级/重装、三模式32/64传统su与150ms/300ms。 |
| [ ] | OWN-01 | SDK 内部命名与实现逐层归属 MapleSU，外部 SKRoot 符号/格式/数字码/持久化资料集中兼容，不破坏原 Ultra 模块。 | P7 | 已实现待设备验证：当前1945路径SDK/runtime/Manager与共享入口已成套采用；活动内部命名与相关备注已清除，旧符号/格式/持久输入集中兼容；受影响JNI/ABI/Kotlin/恢复状态验证通过。验收缺口：同代完整可维护源码发布、全部旧模块/持久资料设备回归与相关核心替换后清退；m073仍FROM_IR、25 partial，不据此勾选整体。 |
| [ ] | OWN-02 | 错误码参考 KSU 的可解释处理语义，保留原始原因、阶段和真实返回值，统一 SDK→bridge→Manager 映射。 | P3/P6/P7 | 已实现待设备验证：错误分类、typed授权/设置链已主线；新日志query补失败与disabled分离。 验收缺口：新m059归档与JNI同代配对，旧r9不可承载新hidden符号；完整SDK→bridge→Manager错误/阶段执行；同次升级/忙/取消/身份变化设备证据。 |
| [ ] | OWN-03 | 减少重复检查、虚假未就绪、空泛提示；将正常/重试/不支持/失败清晰区分，禁止把非零一律当成功。 | P6/P7 | 已实现待设备验证：设置显式安装、真实状态/列表回执已主线；新日志query让现失败分支承接。 验收缺口：跨层正常/重试/不支持/失败真实运行；同代新包执行日志query；全部入口用户提示/前检边界。 |
| [ ] | OWN-04 | KSU真实argv、stdout、stderr、完整退出码与执行阶段通过backend/bridge/Manager回传，避免压为Boolean或泛化提示；用户获得必要进度和结果日志，完整参数与诊断进入开发日志并沿用凭据脱敏。Normal、Ultra初次修补与重修补均验收。 | P2/P6 | 已实现待设备验证：Normal argv/deadline/双流/完整code与bootstrap输出源已主线；19生产资产已集成。 验收缺口：Normal/Ultra首次修补与重修补全部阶段日志；独立bootstrap期限与真实参数执行；脱敏及新APK设备回执。 |

当前同版主线源码补充（终验勾选保持）：OWN-02/03/04、MOD-02/03/04/10、CASE-06b/15/16及FINAL-04的普通消费链已继续实际窄合。Normal安装/模块执行保留原参数、返回值与双流异常，移除错误转空列表和不确定回复后重放；Ultra普通模块库存/取消/同次列表与状态缓存归属已修。设置页不把Hook命令读取失败标成启动Fault，数值SDK错误不再形成激活证据。源码证据统一见`主线集成`（开发证据未随公开快照分发：`SDK-REFACTOR-INTEGRATION.md`）及其源索引；没有本轮编译/测试/设备/新包证据，不继承历史通过到当前源，也不据此完成去Key、真实SU、早期模块启动或Panic验收。

## 7. 三种模式与模块

| 完成 | ID | 要求与验收 | 阶段 | 当前状态 |
|---|---|---|---|---|
| [ ] | MOD-01 | 核对 Normal/Ultra/越狱每个功能的实际后端，不把回退到其他通道成功视为目标 SDK 等价。 | P1/P6 | 已实现待设备验证：三模式实际持久值与Root/模块后端已逐源盘点，关键入口主线。 验收缺口：每功能backend/产物/执行结果矩阵；fallback与目标SDK分别取证；三模式设备全链。 |
| [ ] | MOD-02 | Ultra 对齐 KSU ZIP 安装逻辑、环境变量、BusyBox/ksud 使用、安装回执和脚本阶段，定位真实 ksud 非零原因。 | P6 | 存在阻塞：ZIP安装/环境/策略/pending/boot merge源已主线；hot-install/preinit与真实启动接线未闭合。 验收缺口：hot-install/preinit与真实init producer；原包完整customize/ksud非零首因；无Manager冷启动及环境边界。 |
| [ ] | MOD-03 | Normal 与选定本仓 KSU 基线在模块安装/更新/启禁/删除、脚本与启动行为上等价，同时保留 MapleSU 自有功能。 | P6 | 已实现待设备验证：Normal模块全生命周期源已主线；f4603d14官方成功CI#5545固定，19资产集成。 验收缺口：固定失败原包完整实际生命周期；最新source/userland/JNI/APK同代；固定上游与MapleSU自有功能设备对照。 |
| [ ] | MOD-04 | Ultra 维持原生 Ultra 模块、SKRoot 外部 ABI、原模块列表/回调/状态/WebUI 的兼容。 | P1/P6/P7 | 已实现待设备验证：原生Ultra ABI/loader/list/描述/回调/卸载状态事务已主线。 验收缺口：全部旧原生模块ABI/回调/WebUI实际行为；长生命周期并发卸载和状态一致；同代归档/helper/APK设备。 |
| [ ] | MOD-05 | SDK/helper与MapleSU模块后端共同正确执行post-fs-data.sh、post-mount.sh（上游与模块支持时）、service.sh、boot-completed.sh及所选KSU基线支持的其他模块/公共脚本阶段，分别对齐真实触发事件、先后顺序、同步/异步与阻塞语义、执行身份/环境、次数及失败处理；属性/挂载等声明能力与Ultra环境边界冲突时逐项评估，不以新增全局策略/挂载换取表面通过。脚本安装与实际开机调度分离，模块声明和旧Ultra原生回调契约均保全。 | P6 | 存在阻塞：helper/runner四事件、公共脚本/模块阶段consumer已主线；真实init producer缺失。 验收缺口：真实init producer和每阶段时刻；同步异步/身份环境/次数失败全部脚本；无Manager同boot单调时间/效果和环境差分。 |
| [ ] | MOD-06 | Ultra模块及其开机脚本由SDK/helper与启动链在对应阶段触发，执行时刻对齐固定官方KSU基线，不依赖打开/恢复MapleSU、Activity resume、Manager进程/在线响应、解锁后的首次页面访问或列表刷新；Manager仅提交管理操作与读取实际状态，不补跑已错过启动阶段冒充等价。逐脚本以同一boot会话的单调时间/事件顺序和真实效果验收，覆盖首次安装后生效时点、Manager从未启动的冷启动、未解锁与已解锁阶段、连续重启、升级/更新、禁用/卸载/开机收尾、重复事件不重复执行及中途失败；保持Ultra既有环境边界，不新增可见挂载、全局SELinux修改、属性或额外常驻判定进程。设备观测用的临时测试标记与生产运行机制分开，环境/时延差分按SU-05/06与QA-07记录。 | P6/P8 | 存在阻塞：事件consumer/工具部署/同boot状态源已主线；真实早期producer与无Manager启动链未完成。 验收缺口：init producer/早期身份/宿主实际接线；未解锁/冷启动/重启/升级/更新/禁卸/失败矩阵；零模块原包效果和SU-05/06差分。 |
| [ ] | MOD-07 | 固定 Zygisk-Next 的具体版本、原包与测试操作，验收刷入、重启、注入实际效果、禁用/更新/卸载，进程存在仅是中间证据。 | P6/P8 | 尚未实施：已有相关语料/路由；指定Zygisk Next1.5.3原包匹配及完整注入验收尚未实施。 验收缺口：指定1.5.3原包/版本/SHA与操作；实际注入、禁用/更新/卸载/冷启动；nsfs首因和137终止证据。 |
| [ ] | MOD-08 | 同样固定 LSPosed 的具体发行与依赖，验证实际作用和开机生命周期；不从 lspd 进程存在推断可用。 | P6/P8 | 尚未实施：固定LSPosed发行/依赖及真实作用完整验收尚未实施。 验收缺口：固定发行原包/依赖/SHA；实际作用与开机/禁用更新卸载；匹配设备及依赖结果。 |
| [ ] | MOD-09 | 建立现有全部 KSU 模块包及其声明能力的回归语料，逐包记录安装与运行；“全部兼容”以明确语料、版本和能力范围判定。 | P6/P8 | 已实现待设备验证：M01–M08八原包SHA/能力库存、阶段与移除切片已建立。 验收缺口：逐包完整安装/开机功能/更新启禁/卸载；指定Zygisk Next及LSPosed；声明能力与设备支持范围。 |
| [ ] | MOD-10 | WebUI完整契约对齐：支持内联打开（管理器内嵌WebView）、独立页面及现有其他打开入口，保持UI布局；脚本webroot/AssetLoader、Ultra原生SDK/loopback HTTP及既有代理形式分别正确路由。固定实施与终验时选定的官方KSU源码提交、JS包和实际桥版本，逐项对齐全部WebUI入口、直接ksu桥、旧同步/回调与官方JS封装：exec/spawn的cwd/env、errno/stdout/stderr、事件/异常/取消及并发语义，toast/fullScreen/enableEdgeToEdge/moduleInfo/listPackages/getPackagesInfo/exit，以及当前上游io/pm、外部打开、保存与分块下载等完整接口和嵌套方法；以真实上游清单为准，不限于本仓旧副本。覆盖静态/SPA资源、fetch与页面存储、文件选择/下载、对话框、页面内跳转及上游实际支持的Intent/深链/快捷方式参数；保留原Ultra模块契约与origin/权限边界。各模式/宿主验证实际页面、关键交互和命令结果，覆盖冷/暖打开、返回、重复请求、切后台、旋转/进程重建、主题、WebView版本、模块更新/卸载与渲染进程失效；处理真实生命周期根因，禁止仅吞异常、显示空白或统一外部跳转代替兼容。关联CASE-13/18、MOD-04、SDK-06、API-03、OWN-04、FINAL-07/09。 | P6/P8 | 存在阻塞：WebUI33文件/共享接点、身份/callback/Normal新接口及HTTP Insets源已主线；完整旧provider/全部官方桥契约与宿主矩阵尚未闭合。 验收缺口：固定官方源码/JS包/实际桥全部接口及旧provider完整差异处置；所有宿主/入口/资源/命令/回调并发；下载Intent/包列表/edge-IME/旋转重建/更新卸载；同代loader/JNI/APK与失败设备。 |
| [ ] | JB-01 | 从 SDK/通道路由根因修复越狱模式“su 入口尚未就绪”，验证 lease/carrier/授权与实际命令执行。 | P6 | 存在阻塞：provider/gate/carrier/lease/SDK修复已主线；真实通道/宿主/Root尚未走通。 验收缺口：真实身份/通道/宿主producer；iQOO15首次进入/Manager关闭重建/32与64位传统su；真实模块结果。 |
| [ ] | JB-02 | 越狱模式模块安装和启动正常；修复始终等 Manager、重试/超时/会话失效及错误回执逻辑。 | P6 | 存在阻塞：等待/lease/会话及同次结果已主线，完整越狱通道和启动依赖未闭合。 验收缺口：越狱模块实际安装/启动与超时会话错误；Manager关闭后运行与启动阶段；真实身份/通道。 |
| [ ] | JB-03 | 覆盖越狱临时态与持久 Ultra/Normal 的交接、退出、进程失效、设备重启和既有恢复行为。 | P6/P8 | 存在阻塞：临时/持久路由与取消已主线，交接/重启/失效组合依赖未闭合。 验收缺口：交接退出/进程失效/重启/原恢复全组合；关闭/重建后的实际UID和模块；同代正式产物。 |

## 8. 原功能保全与验收

| 完成 | ID | 要求与验收 | 阶段 | 当前状态 |
|---|---|---|---|---|
| [ ] | API-01 | Root 命令、标准 su、会话/取消、退出码、stdout/stderr、交互和授权回执完整。 | P1/P6/P8 | 存在阻塞：Root命令/会话/取消/typed授权已主线，标准su交互与完整流退出语义仍有源码缺口。 验收缺口：标准su/su-c/PTY双流与真实code；会话取消超时/授权执行；同代payload/JNI和32/64位设备。 |
| [ ] | API-02 | 环境安装/卸载/升级、状态/版本、恢复和模式切换保留实际语义。覆盖同版本不同构建升级；区分真实授权失败、SDK忙、取消与身份变化，不把准备阶段合成AUTH错误阻断合法升级。 | P1/P6/P8 | 存在阻塞：升级/设置/切换载体恢复源已主线；完整无Manager启动/身份和独立Ultra桥仍缺源。 验收缺口：完整身份/启动/独立桥接线；同版本异构建、重装/忙/取消/身份设备；安装卸载升级恢复/切换实际分区与同代包。 |
| [ ] | API-03 | 模块常规/一次性安装、描述/列表、启禁/更新/卸载、回调和 WebUI 各自验证。 | P1/P6/P8 | 存在阻塞：模块管理/WebUI关键源已合；完整无Manager阶段/收尾及全部WebUI契约有核心接线缺口。 验收缺口：真实init调度/开机收尾和全部WebUI契约源闭合；固定原包各功能callback及生命周期；同代SDK/helper/APK/设备。 |
| [ ] | API-04 | 授权增删列清、日志开关/读清、Root 诊断、自检、已暴露设备专项和工具接口保持原功能边界。 | P1/P6/P8 | 已实现待设备验证：授权/名单/日志/诊断工具源已主线，CALL-0485日志query失败回执新增。 验收缺口：新m059归档/JNI同代构建执行；全部公开诊断/工具操作与原功能边界；设备日志授权结果。 |
| [ ] | API-05 | Lite 兜底、寄生/迁移、隐藏模式和其他既有产品能力按当前调用路径盘点，防止清理或改名误删。 | P1/P6/P8 | 已实现待设备验证：Lite兜底、隐藏/寄生/迁移/恢复的现行生产路径已盘点保全。 验收缺口：逐能力backend/选源/产物和实际效果；隐藏寄生迁移自救/恢复生命周期；清理改名后功能保全。 |
| [ ] | QA-01 | 每个改变的功能具有必要主机/模拟/实机证据；并行复用设备采样，避免低价值重复回归。 | 全程 | 已实现待设备验证：已将源码、窄产物、主机/模拟及历史设备证据分层；唯一测试负责人继续统一收集，未重跑旧有效证据。 已验证范围：r9限定设备/单次helper与当前Boot_Research/14签名执行事实已核，后续未打包源码不继承旧通过。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：对每个改变功能绑定最终选源/同代产物与必要主机/模拟/设备证据；新日志查询/Insets及target-KMI等待唯一测试者既定集中验证。 |
| [ ] | QA-02 | 跨镜像布局、无 BTF、错误 BTF、混合宽度与边界样本，覆盖真实目标内核系列。 | P2/P8 | 存在阻塞：BTF/无BTF访问器、字段宽度与损坏边界有分批实现和验证，不建立机型指纹表。 已验证范围：当前25容器/19kernel codec/18完整boot生命周期，历史边界证据仅保持原覆盖。 待闭合：NGKI缺do_execveat_common后续生成保持原停止；全部实际4.x/5.10–6.18/BTF错误/混宽/页大小配置的正向生成与必要设备组合。 |
| [ ] | QA-03 | 并发线程/fork 的 Root/回执、Hook 安装/失败恢复/停用/生命周期实机验收。 | P3–P5/P8 | 存在阻塞：实际m029 before/after已接checked发布，m029/m043完整宽度分支及disabled after原SP/FP/LR源码已修，集中主机验证进行中；active after栈参数/逐调用返回上下文及完整P3/P4生产生命周期仍缺。 已验证范围：保留已有局部主机/历史设备收据，不存在能覆盖本项完整并发线程/fork+Hook生命周期的当前设备收据。 待闭合：P3独立回执与P4凭据生命周期实际生产接线；当前Hook语义、安装/失败恢复/停用并发生命周期；同代产物匹配设备线程/fork Root、原码、UID、回执/回滚实测。 |
| [ ] | QA-04 | SDK/helper/backend/镜像/Manager 的新旧配对矩阵，验证支持的升级路径及不兼容时原状态完整。 | P1/P3/P8 | 存在阻塞：历史三代SDK/Manager对既有boot实测及错误回退、部分client/emitter/loader主机配对已执行。 已验证范围：r7真实-19997、r8真实-19998并恢复Root，r9五读/fixed single；这些是既有boot组合，不是新boot载荷跨SDK升级。 待闭合：稳定SDK/boot运行时ABI与不兼容载荷升级/回退协议尚未形成完整实现和同代证明；同代SDK/helper/backend/镜像/Manager完整支持配对表与不兼容原状态保持；改变boot载荷后的升级/回退、首次/再次冷启动。 |
| [ ] | QA-05 | 模拟刷写覆盖容器回封、目标范围、短写/中断/回滚、多分区、重复升级和原 boot 恢复。 | P2/P8 | 存在阻塞：实际writer短写循环、持久屏障、读回、按尝试写集逆序恢复已实现。 已验证范围：18份候选writer及故障注入，9个多分区既有方法；14签名候选普通文件模拟与恢复，不做真实块设备写。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：所要求跨SDK载荷升级的实现/稳定ABI未闭合，当前模拟不能覆盖不存在的真实升级协议；跨SDK不兼容升级路径及跨槽多分区恢复所需同代候选；必要真实分区写后恢复/启动证据单列，不能把普通文件模拟算设备通过。 |
| [ ] | QA-06 | 设备按模式逐项回归授权、模块、开机和恢复，必要刷写才实施；保留测试设备恢复条件。 | P6/P8 | 存在阻塞：历史设备身份/Root/版本/环境/模块五读、fixed single与失败APK回退已执行，设备验收不是从未开始。 已验证范围：PLQ110/kernel6.6.89/arm64一台，既有boot+r9实际APK；无当前所有源码/模式/开机/模块生命周期合格证据。 待闭合：当前拟交付版真实init事件/自动身份生产和完整P3/P4关键链尚缺，完整模式开机验收先依赖源码闭合；唯一测试者读取当前设备清单并按同代产物/目标镜像匹配后验收；按模式授权/模块/Manager未开冷启动/恢复，保留适用镜像与恢复条件；旧12:17的0设备快照只作历史；当前连接由唯一测试者确认。 |
| [ ] | QA-07 | Duck Detector 采用固定包版本及实际结果作观察基准，同时记录挂载、SELinux、属性、可见文件/进程和调用延迟；现有 P0 不运行有副作用的采集。 | P8 | 尚未实施：固定Duck包和历史P0观察已保留；当前拟交付候选的Duck/环境/时延完整观察尚未执行。 已验证范围：历史P0和r9身份/Root观察不等于当前候选的挂载/SELinux/属性/可见性/延迟联合验收。 待闭合：最终同代候选上固定包实际结果及版本/hash；同次挂载/SELinux/属性/可见文件进程/非授权调用耗时基线与差分，P0不做副作用采集。 |
| [ ] | QA-08 | 最终仅将有足够源码、产物与相应设备证据的需求勾选；其他保留状态、缺少材料和下一步，不宣称全内核无 Panic。 | 全程 | 已实现待设备验证：权威原文与原3个完整勾选保留，当前119项逐项审计分开源码、交付物、执行和缺证据。 已验证范围：仅SDK-01/07/08原勾选；14实际签名和r9限定实测均保留，不扩大支持范围。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：全部119项与20约束、两组十项实际验收对应及最后源码/包核验；仅相应复合验收完成后新增勾选；其他列残留/缺料/下一步。 |
| [ ] | QA-09 | 盘点工作区所有实际镜像，按原镜像/已修补镜像/重复内容和容器类型建语料；Ultra逐镜像模拟修补、解析和验证布局与写入范围，不以14组合成测试替代真实镜像遍历。 | P2/P8 | 存在阻塞：历史整个工作区1690路径/226唯一组清单保留；当前Boot_Research60路径/26镜像路径/25内容唯一组按内容去重执行。 已验证范围：25容器解析，18boot生命周期/writer、14首输入精确/4历史仅生成baseline恢复；6init_boot仅容器。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：不要将当前25组范围冒充历史全工作区226组完整遍历；NGKI原停止及独立Ultra init_boot桥源码/生产缺口；所有未通过/未执行图逐图首错、布局/修改范围/恢复与实际支持边界。 |
| [ ] | QA-10 | Normal离线修补对齐选定最新官方KSU成功CI的实际实现、参数和结果，并保留MapleSU功能；区分仓库旧基线、当前上游与applied，升级前再次确认目标提交。 | P1/P2/P6 | 已实现待设备验证：Normal已固定官方成功CI#5545/f4603d14，新run44095205135/260f197c的19资产/16内嵌独核、44路径正式集成和最终12唯一方法门禁通过。新daemon在PLQ110真实boot文件修补/重补/还原exit0及独立kernel/KO/ksuinit读回通过；运行6.6设备对6.1目标不传--kmi亦实证按目标选源。完整原文的官方实际结果/日志对照和MapleSU功能保全由sole逐项判定，不因设备文件三步通过提前整项勾选；证据在 image-patcher-validation/target-kmi-and-switch-signing/。 |

## 9. 用户补充的缺陷与设备案例

来源：用户在P0进行中补充的十类示例问题；这些是必须最终覆盖的回归要求，不是本轮已复现事实。Y700四代与当前已连接PLQ110分别记录；设备错误消息的旧表述先追实际调用链。案例并非需求范围的上限。

| 完成 | 案例ID | 必须闭合的问题 | 后续通过判据与关联需求 | 当前状态 |
|---|---|---|---|---|
| [ ] | CASE-01 | Y700四代Ultra未知崩溃重启，多次重启后才恢复 | 固定原boot/SDK/模块/安装状态，捕获pstore/ramoops及启动时序，定位根因；修复后首次与连续冷启动无需“多重启几次”恢复。关联LAY/IPC/CRED/HOOK/INIT/QA。 | 存在阻塞：通用helper会话/原目标argv/保护等子项已实施；Y700四代崩溃重启的实际最早异常尚未取得因果闭环；本轮只读追当前链；r9 PLQ110和POSIX helper测试不作为Y700通过证据；未完成：Y700原boot/SDK/模块状态和固件身份；同次pstore/ramoops/启动时序/最早异常；匹配修复产物首次与连续冷启动、无需多次重启恢复 |
| [ ] | CASE-02 | Y700四代Ultra隐藏模式启动失败 | 验证用户动作→配置→底层启动→实际可访问结果、失败回执和Manager关闭时状态；修复不改变UI布局。关联SU/MOD/INIT。 | 存在阻塞：隐藏启动/配置/回执修复主线；完整早期宿主/启动producer缺口仍阻断无Manager链。 验收缺口：真实宿主/init producer启动接线；Y700动作→配置→监听访问首错/根因；Manager关闭/重启状态与同代产物。 |
| [ ] | CASE-03 | 隐藏模式端口修复失败，默认18427 | 核对默认/自定义端口的单一配置源、实际监听、持久化、修复动作与回读；覆盖冲突、重启和重新启用。关联API/OWN/QA。 | 已实现待设备验证：源码深审发现HTTP改端口误接默认Unavailable，现已在本次13路径普通批接通实际daemon/RootService listener.rebind；默认/自定义/同口和配置读回、原manifest配置项同步及失败恢复已有源。当前请求连接保留，不stop/join自身。尚无本批编译/HTTP或Y700设备证据；双文件rename崩溃窗口和跨进程写边界待验。源与待验见 .tmp/mainline-source-integration/audit-followup-source/liteweb-port/RECEIPT.json；历史54候选断言不继承。 |
| [ ] | CASE-04 | 首装超级用户列表加载失败，多次重启后才恢复 | 首装无授权、已有授权、启动未完成/失败、进程重建、升级各自回执正确；首次可用后列表加载不依赖多次重启。关联SU-04/OWN-02/INIT-01。 | 存在阻塞：首装列表scope/cache/typed结果修复主线；首次可用通道/身份producer仍缺。 验收缺口：首次通道/身份生产与无Manager启动；首装空授权/已有授权/未就绪失败/重建升级；不靠多次重启真实列表和UID。 |
| [ ] | CASE-05 | 部分应用获取或识别不到SU权限 | 按应用UID、多用户、32/64位软件、nativeLibraryDir/传统su、共享UID/更新与撤权逐项追踪；既测入口发现，也测su -c与交互执行，未授权应用另验信息、时序、SELinux与挂载无新增变化。关联SU-01–06/API-01。 | 存在阻塞：应用授权/识别、传统su/compat关键源主线；完整标准su/启动依赖仍未闭合。 验收缺口：标准su/早期通道源缺口；失败应用按UID/多用户/共享UID/更新撤权；32/64位su-c/交互及未授权环境时序差分。 |
| [ ] | CASE-06a | 刷写被无必要条件阻拦 | 移除镜像修补/安装事务的独立前置检查，按KSU直接进入执行；覆盖首次、重修补、旧事务残留和离线无Root服务，真实执行错误与日志完整返回，保留回读/恢复。关联IMG-02/04/06/07、OWN-03/04。 | 已实现待设备验证：默认目标直接执行、无Root文件修补、现有SERVICE恢复/跨实例计划与最小写集合已接线；缺整套备份不统一阻断可证明恢复。 已验证范围：18boot当前生命周期/writer与14签名文件模拟；实际服务/当前APK未由这些收据覆盖。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：首次/重修补/旧事务残留/离线无Root真实入口和完整原错误码/日志；第三方SU迁移及当前ksud/Manager同代产物；实际SERVICE/Binder/分区写后结果和冷启动。 |
| [ ] | CASE-06b | 安装事务连接失败 | 分开定位会话创建、进程启动、权限、IPC、断线与回执，修复后合法任务可完成，失败显示准确用户结果，内部诊断不进入前端布局。关联IPC/API/QA-05。 | 已实现待设备验证：进程/会话/恢复计划与用户结果已有实际实现，分开文件producer与真实SERVICE/Binder执行。 已验证范围：现有主机snapshot/计划恢复和18候选writer文件模拟；不证明真实断线/IPC/Root service链。 待闭合：当前APK真实会话创建/权限/进程/IPC/断线各阶段首错和合法完成；失败准确结果及前端不渗透内部诊断；同代必要设备安装/恢复。 |
| [ ] | CASE-06c | 补丁集生成失败 | 从目标镜像解析→布局→源码修补计划→生成→回读定位，不继续依赖补丁反推；有效镜像生成正确且保留原图。关联LAY/IMG/SDK。 | 存在阻塞：目标解析→布局→正向生成→回读已实现；四份旧Hook已能恢复原kernel并进入当前生成流程。 已验证范围：18boot生命周期/writer，14精确首输入/4仅恢复生成baseline；14实际加签与文件恢复。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：NGKI缺符号后续生成的原停止保持；独立init_boot内核桥/正式生产端；所有有效语料首错与目标修改/分支、原图保全及必要设备。 |
| [ ] | CASE-07a | Normal离线修补与KSU实际实现不等价 | 同一输入、版本、参数、签名/容器契约下按当前官方成功CI源码差分，区分必要MapleSU适配与偏差。关联QA-10/IMG。 | 已实现待设备验证：当前正式Normal基线已接f4603d14/官方成功CI#5545；当前目标kernel KMI选择与Rust测试直接依赖修复是后续源码代。 已验证范围：旧正式19资产和16内嵌；其包未包含后来target-KMI/lz4源变更。 首target-KMI CI的真实test构建失败E0433/101且无production导出，补依赖后的run44095205135仅已派发。 待闭合：target-KMI新用户态构建/测试成功后核验实际嵌入和同输入ksud行为；必要MapleSU适配与官方偏差逐项列明参数/签名/容器/原退出码；必要设备直接安装、模块和恢复。 |
| [ ] | CASE-07b | Ultra对工作区镜像的布局/模拟修补覆盖不足 | 对完整镜像语料逐项运行，保存类型、BTF/无BTF解析、字段宽度、目标区修改及重建回读结果；失败原因逐图闭合。关联QA-09/LAY/IMG。 | 存在阻塞：当前Boot_Research已统一25内容唯一组，并使四历史Hook恢复进入当前生成；历史大语料单列。 已验证范围：25容器/19kernel codec/18boot完整生命周期，14首输入精确，4历史仅生成baseline，6init_boot仅容器。 待闭合：完整工作区语料与当前Boot_Research范围保持分别记账；NGKI实际正向生成与独立init_boot桥缺口；各图BTF/字段宽度/修改范围/重建回读及失败首错闭合。 |
| [ ] | CASE-08 | 玩机兼容模式未与KSU完全等价 | 先核实该模式真实持久化值与后端；对齐官方最新成功CI并记录本仓自有功能差异，验收su/授权/模块/镜像与开机全链。关联MOD-03/QA-10。 | 已实现待设备验证：模式值/后端已核，官方成功f4603d14/CI#5545固定且Normal19资产集成，自有补丁保留。 验收缺口：su/授权/模块/镜像/开机全链官方对照；MapleSU自有差异与结果；最新同代source/payload/JNI/APK设备。 |
| [ ] | CASE-09 | 多设备Ultra环境安装失败 | 建立实际失败设备/镜像/阶段样本；统一初始化、helper/entry/协议/布局与安装结果，逐根因正向修复，记录覆盖和未覆盖组合。关联SDK/INIT/IMG/API-02。 | 存在阻塞：至少原目标argv早退等安装/启动根因子项已修并入当前源；不得继续表述只有6例复现、尚无生产修复；r6-f的25/25 fixture/POSIX入口通过；最新18boot普通生成/回读通过不等于多设备首次启动Root；未完成：失败设备/固件/载体逐项最早异常；真实init producer与同代helper/SDK/backend/APK；零模块首冷启动、Manager未打开、32/64位传统su；每个实际镜像/设备升级、恢复和连续重启 |
| [ ] | CASE-10a | SDK与MapleSU身份/安装状态脱节 | 权威状态以实际内核/运行时/持久授权为准，处理管理器包身份、SDK/Helper代际、重装和恢复；稳定后实施独立签名绑定。关联TRUST/SU/IPC。 | 存在阻塞：内核/环境/本地身份分账、recovery/签名轮换和owner身份消费者已实施；旧账整项“尚未实施”失真；历史96项trust/recovery控制流有窄证据，原Root/PM为fixture；正式r9与当前owner源码分账；未完成：实际helper/kernel身份与持久授权同代恢复；卸载重装/升级/签名轮换真实运行；resident host/owner producer与旧Key迁移/撤销；无Manager早期初始化与Root |
| [ ] | CASE-10b | Ultra频繁SU授权失败 | 查明授权提交→内核UID→入口→执行→撤权与列表的真实一致性，不以本地缓存/回退通路成功冒充SDK正确。关联SU-01/02/04/MOD-01。 | 存在阻塞：授权同scope提交/回读、typed结果和真实SDK事务子项已在当前源，不再全部隔离候选；历史R30的28有效owner行为与后续组合分别有证据；不等于真实普通应用UID或撤权生效；未完成：同次频繁失败的实际SDK/kernel UID/入口因果；普通32/64位应用传统su与撤权/共享UID/多用户；并发、包更新、Manager关闭和无Manager开机；同代实际SDK/APK/模块结果 |
| [ ] | CASE-10c | 寄生路径复杂脆弱并增加环境暴露 | 按现有功能拆清生命周期与单一执行归属，减少重复投递/启动/自修复，保留原功能，做文件/进程/挂载/策略/耗时差分。关联API-05/SU-05/06/QA-07。 | 尚未实施：现有功能链、证据化生命周期和SDK适配存在；本需求减少重复投递/启动/自修复及环境差分的专项重构尚未实施完成；本轮只读追生产端口与状态机，未取得寄生文件/进程/挂载/策略/耗时前后差分执行证据；未完成：当前全部寄生启动/重投递/自修复实际调用清单；按必要功能收敛为单一执行归属；真实文件/进程/挂载/策略/延迟基线差分；实际迁移/宿主死亡/恢复与功能保全 |
| [ ] | CASE-10d | SELinux隐藏加载失败、策略读取失败 | 区分加载、就绪、读取、解析、授权和目标语义；验证实际生效与失败结果，不以Enforcing或空成功认定正常，不增加用户可见全局策略。关联LAY/INIT/OWN/QA-07/FINAL-06。 | 存在阻塞：producer/checker/同次scope修复主线；真实策略后端加载/隐藏根因和对应源修复未闭合。 验收缺口：指定归档后端配置/加载/读取/隐藏首因及修复；CONFIG_PAGE_IDLE_FLAG/TRIM_UNUSED_KSYMS线索实际验证；冷启动升级重装/失败恢复及目标环境边界。 |
| [ ] | CASE-10e | 主页SELinux状态经常缺失或确认缓慢 | 修复后端状态来源、超时/缓存与并发依赖，测冷/暖启动时延和状态一致性；前端只保留现有布局内必要状态。关联API-04/OWN-02/03。 | 已实现待设备验证：状态producer/checker/scope/cache已主线，隔离描述失效。 验收缺口：首页冷暖状态/延迟分布；多设备并发页面/重建/boot首读；同代APK后端状态。 |
| [ ] | CASE-10f | 管理器卸载重装显示未安装，但旧授权应用仍有Root | 设备内核安装态/授权态与Manager本地数据清除分开识别，重装能恢复真实状态；明确旧授权保留/撤销的实际契约，测试升级、重装与恢复。关联TRUST/SU/API-02。 | 存在阻塞：重装身份恢复、旧授权与本地数据分离的代码已实施，不能继续写整项尚未实施；历史96项恢复控制流仅fixtures，未在匹配设备实际卸载/重装；旧授权仍Root的具体故障闭环未终验；未完成：实际卸载重装后旧授权保留/撤销合同；新Manager恢复真实安装态与Root证明；包更新/签名轮换/多用户和失败恢复；同代producer/host/新APK设备 |
| [ ] | CASE-10g | 越狱持续SU入口未就绪 | 将provider→gate→carrier/lease→SDK→真实uid与模块安装走通，验证等待/超时/管理器死亡恢复，不强行置Ready。关联JB-01–03/INIT。 | 存在阻塞：provider→gate→authenticated lease→执行器及SU endpoint已有生产代码和恢复候选，不能写成入口完全没有实现；本轮直接追当前调用；历史provider恢复窄证据不等于真实传统su/模块/目标设备走通；未完成：同次provider/carrier/gate→SDK→实际UID完整因果回执；普通32/64位传统su命令/交互；真实模块安装/启动；Manager关闭/死亡重建、超时/lease失效恢复 |
| [ ] | CASE-11 | KSU模块长期显示“将在下次重启时卸载”，部分模块重启后仍残留 | 按选定官方KSU源码与实际打包ksud版本对齐卸载、撤销卸载、remove状态持久化、对应开机阶段的卸载脚本与metamodule回调、模块及待更新目录清理和列表刷新；管理器未打开时也完成开机卸载。固定失败原包，覆盖重复卸载、取消、先卸后装、脚本失败及重启中断；记录实际参数、stdout/stderr、退出码和残留原因。以对应模块实际文件/运行状态及重启后枚举一致为准，不用隐藏列表项或仅清除提示代替卸载。关联MOD-03/05/06、API-03、OWN-04、FINAL-07。 | 已实现待设备验证：KSU卸载/撤销/pending/merge及结果源主线，19资产已集成。 验收缺口：失败原包重复/撤销/先卸后装/脚本失败/重启中断；metamodule/update/目录收尾与枚举一致；Manager从未打开实际开机卸载。 |
| [ ] | CASE-12 | 部分Ultra原生模块总是卸载失败 | 从应用卸载入口、bridge回执、SDK提权/子进程与卸载回调，追到模块私有目录/存储、全局模块状态、bootstrap和应用目录记录，定位真实失败阶段；保留原Ultra模块ABI与回调契约。覆盖回调超时/崩溃/非零结果、权限/文件占用、重复请求、部分清理、Manager退出和重启后的收尾；按真实运行生命周期处理资源，失败可重试且结果准确。需要开机收尾的状态在对应启动阶段完成，无需打开管理器，不长期滞留待卸载。关联SDK-04/06、MOD-04/06、API-03、INIT-01、FINAL-07。 | 存在阻塞：原生卸载/child错误/状态RMW主线；无Manager开机收尾依赖真实init producer未闭合。 验收缺口：真实init producer/无Manager开机收尾；callback超时崩溃非零/权限占用/部分清理；私有目录/全局状态/bootstrap/应用记录及并发重复/重启。 |
| [ ] | CASE-13 | Ultra环境下部分脚本模块或原生模块WebUI打不开 | 固定失败模块原包/版本/ABI，按真实模块契约区分脚本资源与原生HTTP加载，ZIP含ELF不单独作为分类依据。联查应用入口与路由/身份、SDK元数据与WebUI声明、实际打包loader、提权/fork/IPC/exec、监听端口、HTTP资源及浏览器/WebView导航和交互桥；记录最早失败阶段及真实错误。验收实际页面、静态资源与关键交互，覆盖首次/重复打开、端口冲突、关闭重开、进程重建、重启及模块更新/卸载后的生命周期；SDK返回成功、分配端口或端口监听均不单独构成通过。保留UI布局与Ultra既有环境边界。关联SDK-06、MOD-04、API-03、OWN-02/03、CASE-18、FINAL-07/09。 | 已实现待设备验证：导航/route/loader/HTTP桥/身份/回收和Insets源已主线。 验收缺口：原包实际页面/静态资源/关键命令交互；冲突/重开/重建/重启/更新卸载；各宿主/模式同代loader/JNI/APK和设备。 |
| [ ] | CASE-14 | 部分iQOO/VIVO设备安装环境失败或启用后未取得权限 | 对照SakiSU（用户原称Sakisu）的固定源码提交，从SDK初始化/布局/通信/凭据、目标boot/init_boot修补及厂商模块实际加载顺序定位安装与授权各自根因。核对vr.ko来源、早期加载、vendor_boot全部ramdisk分片及相关模块索引/加载引用，按真实目标摘除干扰模块及已确认关联的后续通知来源；vklp.ko等以镜像实物和依赖为准，区分内核notifier/回调与系统通知，保持无关模块与通知功能。Normal的KSU/LKM路径与Ultra既有底层分别对账，不将SakiSU运行时过滤后手直接视为根因修复。验证首次安装/首次冷启动、Manager未打开、实际UID授权与传统su、连续重启、升级/重修补、A/B槽位及恢复；保留UI、原Ultra模块与环境边界，不增加独立安装前置门槛。关联CASE-09、SDK-12、LAY、IMG-01/03/04/07、INIT-01、SU、FINAL-08。 | 存在阻塞：固定SakiSU基线、vr filter/companion/vendor_boot处理和Ultra缺版本显式修复已实施，旧“尚未实施”应纠正；全目标安装可靠性仍阻塞；Normal19产物正式集成与显式repair冻结host证据存在，均不证明iQOO/VIVO首次冷启动、vr通知因果或UltraRoot通过；未完成：指定失败iQOO/VIVO实际固件/boot/vendor_boot全部分片和索引；vr.ko/vklp等实际加载与通知的同次因果；首装首次冷启动无Manager实际UID/32/64su；连续重启/跨SDK/重修补/槽位/恢复；独立Ultra桥与同代正式SDK/新APK |
| [ ] | CASE-15 | Ultra模块列表偶发加载失败、策略设置读取不可靠、SDK频繁Busy | 从页面操作、应用入场锁/读槽位、JNI入场、子进程/IPC、SDK执行到回执、缓存与状态刷新逐段定位，区分未提交、真实执行错误及超时后仍在运行。取得同一次故障的请求关联证据，修复锁/生命周期/串并行契约根因，不以延长等待、盲重试、空列表或旧缓存冒充成功。覆盖冷/暖启动、模块/策略并发读取、授权与安装/卸载交叉、Manager重建及开机后首次读取；保留真实失败结果与现有UI。关联INIT-01、OWN-02/03、API-03/04、CASE-10d/e、FINAL-09。 | 已实现待设备验证：列表/状态repository/checker、typed回执和双层锁生命周期修复已主线。 验收缺口：同次请求关联/最早阻塞/未提交/超时后仍跑；冷暖读取及授权安装卸载交叉/重建首读；同代产物设备与时延分布。 |
| [ ] | CASE-16 | Ultra无故显示或进入安全模式 | 对账SDK/helper的启动会话、safe_flag/boot_fail_protect与应用State.Fault的来源、持久化、清除和展示条件，分别处理真实启动失败、初始化未完成、通道入场失败、状态上报错误、未知态及残留标记，修复最早错误转移。对照选定KSU源码的触发/恢复语义，覆盖未安装任何模块、正常冷启动、连续重启、升级/重修补、安装中断、Manager未打开及重建、真实故障保护；正常路径不轻易进入安全模式，真实需要的启动保护继续保全。逐项验证现有修复按钮对每类可恢复原因真正执行对应修复，动作结果、持久状态与后续启动/Root/模块结果一致；需重启或仍有故障时如实返回可操作结果，重复点击结果一致，保留原始失败证据。禁止全局清标记、忽略错误、强置Running或仅消除提示。关联INIT-01、API-02/04、CASE-01、CASE-15、FINAL-09。 | 存在阻塞：boot会话/保护/版本wait/HotLoad与状态修复源主线；真实早期开机producer仍缺。 验收缺口：真实init producer及每类故障最早转移；按钮修复/持久状态/重启后Root模块；零模块冷启动/连续重启升级中断/未开Manager和真保护保全。 |
| [ ] | CASE-17 | 指定KSU模块安装/运行/卸载失败，Zygisk-Next安装遇nsfs策略错误 | 以补充评估中的7个用户原包及3张截图为固定回归材料，同原包/目标策略/参数对照选定官方KSU实际实现，覆盖ZIP结构、安装环境、脚本阶段、SELinux规则解析/应用、完整回执、开机运行、禁用、更新、卸载与开机收尾。截图中的Zygisk Next 1.5.3原包另行取得并核对版本/哈希，定制包不替代；追踪target type nsfs does not exist与validation_failed的首个因果分支，不伪造类型、全局放宽策略或强改非零结果。脚本退出137需实际终止证据，不预设原因。关联MOD-02/03/05/06/07/09、CASE-11/12、OWN-04、FINAL-09。 | 存在阻塞：七原包/截图、策略/pending/结果修复主线；指定Zygisk原包缺失及真实nsfs首因/修复尚未闭合。 验收缺口：指定Zygisk Next1.5.3原包/SHA；目标nsfs/validation_failed首个因果分支及对应修复；137真实终止证据，不预设OOM；七原包完整开机/更新/禁卸设备。 |
| [ ] | CASE-18 | 月虹等模块在Ultra打开WebUI闪退，部分设备兼容性差 | 固定月虹一键隐藏v5.5.7与月虹游戏助手v1.4.7原包，针对截图LocalNavigator not provided保留APK/R8映射与完整栈，验证主题/导航上下文、Activity与WebView生命周期及实际模块路由，再修对应源码。允许在保持UI布局、模块资源/JS/命令桥/origin/生命周期契约的前提下采用可靠宿主；脚本AssetLoader与原生HTTP路径分别验收。覆盖目标设备/System WebView、主题、冷/暖打开、返回、旋转、进程重建和模块更新/卸载，不以页面出现或HTTP探活单独判通过。除两个月虹故障外，所有WebUI形式、内联打开与KSU完整调用方式另按MOD-10逐项验收，不将单宿主或单模块通过扩大为全部兼容。关联CASE-13、MOD-04/10、API-03、FINAL-09。 | 已实现待设备验证：LocalNavigator/打开回收候选、两宿主/身份/回调/HTTP Insets已主线。 验收缺口：两个月虹固定原包在vivoV2507A/Android15/对应WebView；失败APK/R8/fullstack和主题导航根因匹配；冷暖返回旋转重建/更新卸载与资源命令交互；MOD-10全部宿主另验。 |
| [ ] | CASE-19 | iQOO 15越狱模式SU入口未就绪，需验证根因修复能扩展至其他设备 | 明确iQOO 15为待实测目标，记录设备/固件/内核/ABI与实际APK/SDK身份，复现provider→gate→carrier/lease→SDK→UID/SU与模块操作；验收首次进入、冷启动、Manager关闭/重建、授权/撤权和真实32/64位应用传统su执行。修复通用通道根因后按其他设备/内核组合复核，不硬编码机型、不强置Ready，不把用户期望或其他设备结果当作iQOO 15已通过。关联JB-01/02/03、CASE-10g/14、SU-01/03/04、FINAL-09。 | 存在阻塞：通用越狱provider/lease/SU入口已有实现；iQOO15是具体待实测目标，专属根因/扩展效果未闭环；本轮仅源码事实，不继承PLQ110或其他设备结果为iQOO15成功；无iQOO15实际UID/传统su/模块收据；未完成：iQOO15设备/固件/kernel/ABI及真实APK/SDK身份；同次provider→gate→lease/carrier→SDK→UID/SU/模块复现；首次进入/冷启动/Manager关闭重建/授权撤权；真实32/64app su命令与交互；通用修复后其它设备组合 |
| [ ] | CASE-20 | Tenc🎮 游戏优化助手-C版_Log测试版在Ultra模式完全不生效 | 固定用户原包WORKSPACE/Log_Reference/11.14/Module/Tenc🎮 游戏优化助手-C版_Log测试版.zip，module.prop id=Tenc-ano_tmp，SHA-256=d60a337bac49c7b74f1d81d5f0f5f6285c9f68f44a9c54f56263ddb1222c319c。从ZIP安装/目标目录、post-fs-data/service与Manager未打开时的开机执行，追踪真实UID/能力、配置读取、ABI/子进程、文件访问、声明的策略规则、SDK/bridge回执及WebUI操作到实际结果，复现并修复首个功能分歧；按原包配置逐项核验实际效果，覆盖目标多用户/分身及已声明支持范围。记录模块日志原始错误，不把安装成功、脚本返回0或后台进程存在当作生效。验证冷启动、重启、更新/启禁、内联/独立WebUI与设置持久化、卸载及开机收尾；保留UI、原模块契约与Ultra环境边界，不以额外全局策略放宽、盲重试或假成功替代修复。关联MOD-02/04/05/06/09/10、CASE-11/12/15/17/18、FINAL-09；原包静态盘点见补充评估M08，当前无设备生效证据。 | 尚未实施：M08精确原包/配置/ARM64能力已盘点；真实功能分歧根因/对应实现尚未实施。 验收缺口：固定原包开机UID/能力/配置/ABI/子进程/文件策略首错与修复；真实配置各项效果/多用户分身支持；无Manager冷启动/重启/更新启禁/WebUI设置/卸载收尾。 |

2026-11-15追加回归材料与可行性：详见`模块、WebUI、Busy、安全模式及镜像支持补充评估`（开发证据未随公开快照分发：`WORKSPACE/docs/SDK-REFACTOR-REGRESSION-SUPPLEMENT.md`），含7个原包、3张截图的SHA-256、源码核对点、init_boot与Linux 4.x的条件及设备缺口。原包已盘点、截图已保存；仅取得静态证据，不构成功能修复或实机通过。

2026-11-15追加案例证据：用户截图中“伪装DrmID v1.0.0”模块划线并显示“将在下次重启时卸载”；截图仅证明当时的待卸载界面状态。重启后持续残留、Ultra卸载失败和WebUI打不开为用户报告，待按失败原包取得对应SDK/交付物/设备证据后确认根因。

2026-11-15用户继续补强API-02、CASE-09、CASE-13/15/18与MOD-10：设置安装环境不得被独立的“内核通道检测未完成”门槛反复阻断，现有重新检测/安装操作须触达相应真实SDK操作并保留认证、取消和执行错误；不得把未知态强置就绪。WebUI须减少每次打开前的重复环境/身份查询，记录冷/暖打开的实际调用数、等待和页面可交互时延，并对照选定KSU基线；“秒打开”为性能目标，取得对应设备证据后才声明达到。等待WebUI期间Manager应可继续滚动和返回，页面销毁、命令取消、shell/文件流/下载回收不得在Main等待长IO锁；覆盖连续点击、取消、失败重试、迟到结果、重建和渲染进程退出，确保无资源泄漏或旧页面回调污染。所有原需求、UI布局、旧模块契约和Ultra环境边界继续保留。

2026-11-15本批已验证的主机子项如下；勾选仅限所列路径，API-02、MOD-10、CASE-09/13/18及其他完整复合项继续保留未勾选。源码候选、窄编译产物、正式SDK/APK和设备分别记账，证据集中在`当前P5/P6/P7账本`（开发证据未随公开快照分发：`SDK-REFACTOR-P5-P7.md`）。

- [x] API-02/OWN-02：升级授权准备与受影响既有测试48/48；保留一次真实Root证明，区分Busy、取消、UID/身份变化与SDK失败，不统一合成AUTH。
- [x] API-02/OWN-03：设置显式安装专项及受影响既有测试30/30；操作进入唯一Installer/SDK，未知态保留NotChecked，真实失败不置成功。
- [x] API-02/TRUST-01的局部合并验证：6个实际源Android 37窄编译通过，输入SHA稳定；P7基础→升级→设置三补丁实际应用25→28→33文件SHA一致，签名轮换与BackupOutcome字段保留。
- [x] MOD-10/CASE-13/18的打开编排：34/34回归；已验证身份路径五次打开的前置环境查询5→0，SDK启动与页面启动各5次；取消后不再启动Manager拥有的迟到页面。JVM注入75ms环境查询的中位78→1ms仅为该测试条件，不代表设备页面时延。
- [x] MOD-10/CASE-13/18的销毁链：52/52回归，覆盖完整helper/command、IO/File/RAF、Blob与下载；页面先失效，close/flush/文件删除移交独立于页面Job的后台回收，旧页面新IO与迟到回调被隔离。
- [x] WebUI实际33源Android/Compose窄编译通过，sourceInputsStable=true；包含ModuleScreen与WebUIResourceCleanup，以及明确共享DownloadManager提案。完整APK、实际滚动/手势、冷暖打开、旧WebView provider和指定故障模块/设备仍待验收。
- [x] 本批源码交付组合：P5/P6/P7/升级/设置冻结变体/WebUI六补丁从冻结ZIP实际应用，118/118最终文件SHA一致。设置两个基线变体择一，不重复叠加；共享提案、SDK loader、正式产物与设备另计，见COMBINED-SOURCE-APPLY.json和既有阶段交接当前索引。

本轮后续独立主机子项（2026-11-15；完整父项与正式产物/设备仍未验收）：

- [x] HOOK-01/02、RELOC-01的拒绝边界：保留SIMD literal聚焦86/86、UDF实际发射/安装18/18；拒绝时入口零写；受影响NDK成员编译通过。818/584仅为R1实际回归范围，R2未重复累计。见p5/remaining/R1-DELIVERY.json与R2-DELIVERY.json。
- [x] API-03/04的native安装/日志回执：实际71项由37失败降为0，fork失败、自身child、EINTR/退出/信号和首错保留；日志Root查询失败不假开启、删除错误回传，两成员NDK/旧强导出一致。见p6/remaining/r6-a/DELIVERY.json。
- [x] SU-04/API-04的授权持久化异常：真实journal/授权store/事务46/46；名单IO/提交/UID解析20/20，保存失败不发布未提交内存，外部符号与类布局保留。见p7/remaining/r1/DELIVERY.json与r2/DELIVERY.json。
- [x] MOD-10/CASE-13/18的页面重试所有权：实际Activity回调/释放/手动重试18项由16失败降为0；迟到页面回调与自动任务按当前WebView失效。完整WebUI diff包含此增量，旧provider/实际renderer/设备继续待验。见webui/page-retry/DELIVERY.json。

本轮只读源码排查入口：KSU应用命令见`manager/src/ksu/java/dev/maplesu/manager/ksu/KsuModuleOps.kt`的`uninstall/undoUninstall`，本仓上游开机卸载参考位于`third_party/kernelsu/upstream/userspace/ksud/src/module.rs`与`init_event.rs`；Ultra见`ProModuleUninstallTransaction.kt`、`runtime/pro/bridge/uninstall_callback_watchdog.cpp`及SDK的`skroot_core/skroot_module_install.cpp`。WebUI见`ModuleWebUiLauncher.kt`、`runtime/pro/bridge/native_lib_pro.cpp`、SDK的`skroot_core/skroot_module_webui_loader.cpp`及`src/payload/webui_loader`。SDK卸载源码在删除前调用回调，应用再按SDK列表归类完成/待重启；WebUI源码在`execve`前写入启动回执，应用另有HTTP探活与页面归属判断。这些调用顺序仅是后续核对点，尚无本轮修复、实际交付等价或设备回归完成证据。

CASE-14对照基线：2026-11-15只读查询`XingChenRS/SakiSU`的`main`，提交为`6f9672837b9359f8853f47e18e00261edbf6d31e`。固定提交的`docs/zh/vivo.md`、`DEVLOG-VIVO.md`及`kernel/hook/init_module_filter.c`区分boot/init_boot的标准LKM路径、vendor_boot的rmvr路径与运行时vr过滤后手；LKM vermagic适配属于Normal参考，不直接套入Ultra SDK。实施时对照该提交的实际源码并重新确认选用基线；上游方案和上游设备结果均不等同MapleSU已验证。用户所述“后续通知”的具体来源尚未由设备证据确定，后续沿目标模块/回调/通知的真实因果链闭合。

CASE-14本仓源码入口：`manager/src/main/java/dev/maplesu/manager/installer/VivoCompatibility.kt`、`VivoCompatibilityStatus.kt`及`prokernel/ProKernelVivoFilter.kt`。现有源码包含vendor_boot清理和boot内早期过滤；`verifyRuntime`在LITE且`kernelFilterVerified=true`时使用`KERNEL_VERIFIED`，状态判定也接受该标记。后续须分别证明镜像修改、真实启动执行和授权结果，不把静态标记或`/proc/modules`无vr单独认作启用成功。以上仅为源码核对点，本轮尚无对应交付物消费证明、故障实机复现或修复验证。

其中“全部设备兼容”“全部模块兼容”和“完全等价”是覆盖目标；每项声明必须附真实设备、内核配置、ABI、原始模块包和产物范围。未取得相应材料的格子保留状态和补测条件，不自动打勾。

## 10. 最终验收硬条件

用户于2026-11-15补强以下条件；原有需求全部保留。阶段接入仅是起点，以下每项闭合前总目标均保持未完成。

| 完成 | ID | 最终通过判据 | 阶段 | 当前状态 |
|---|---|---|---|---|
| [ ] | FINAL-01 | 实际发布SDK的每个成员以及helper、全部payload、backend均由对应可维护源码构建；源码→构建输入→归档/嵌入资源→APK→设备实际加载逐项可追溯。IR、参照二进制和旧对象的过渡替换表全部闭合，不以重打包旧程序计源码完成。 | P1–P8 | 存在阻塞：历史r9链保原范围；当前SDK0110be、helper/payload/backend及Manager681ec6已源码构建、来源校验并签名，组合版尚未安装；设备仍为此前新源码Manager06f39代且其helper身份不匹配。m073仍FROM_IR/skp bitcode，25 partial与全部过渡来源未闭合。新P2 resident生产源/隔离产物已交付但未打包进681ec6，不能以公共头更新代替生产选择。待闭合：全部可维护源码替换与清退、P3/P4/独立init_boot和P7完整消费者、同代正式组合及设备实际加载/行为。 |
| [ ] | FINAL-02 | 布局、通信、凭据、Hook、初始化、授权、模块执行各自统一实现；替换功能通过验收后删除重复路径、无必要包装/自修复/回退及失效构建入口，保留外部SKRoot格式的集中边界兼容，内部实现归属MapleSU。提交清退清单与实际无旧入口调用证据。 | P2–P8 | 存在阻塞：目标布局/恢复已有统一入口，部分声明/内部归属已整理；Hook受检发布helper存在但两处安装器仍直写，启动消费者缺真实producer。 已验证范围：本次对当前实际before/after源码518/733与helper860对读；函数存在不等于安装链已选用。 待闭合：P3独立回执→P4凭据生命周期、Hook完整宽度/统一发布实际调用；真实init/授权/模块启动producer与单一路径；替换功能先验收后清退重复路径，提交无旧入口调用的实际证据。 |
| [ ] | FINAL-03 | 消除不必要Key、手动配对、重复注入与打开Manager的启动依赖；必要认证/授权由明确身份与持久授权机制完成。签名绑定方案完成实现并通过早期开机、升级、轮换、卸载重装及旧模块兼容验收，评估报告或候选方案不单独构成通过。 | P7/P8 | 存在阻塞：局部签名/身份/恢复候选和持久授权实现有成果；实际无需Key/Manager的启动身份producer尚缺完整宿主。 已验证范围：历史r9五读和single仅证明既有通道，不证明早期开机身份生产、轮换与无Manager。 待闭合：TRUST-02实际宿主/发布位置与P7 producer；去不必要Key/手动配对/重复注入的实际同代路径；早期开机/升级/轮换/卸载重装/旧模块兼容设备验收。 |
| [ ] | FINAL-04 | 每个既有SDK功能都有MapleSU实际调用入口、打包产物身份和验收用例；验证真实发布产物，重点闭合Ultra重修补、授权、隐藏模式、SELinux状态、越狱入口、模块安装及Manager未打开时的开机运行。功能不通过回退到另一旧通道冒充保全。 | P1–P8 | 存在阻塞：r9基础功能与当前镜像/签名等实际入口有分层证据；完整API/caller/pack/case映射工作已保存，映射本身不是功能通过。 已验证范围：历史r9五读/single与18boot/14签名限定范围；原48项cred/task/dentry/inode来源映射不等于48功能实测。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：全部既有API实际当前调用/最终打包/真实用例；P3/P4/P7及Hook/初始化等核心源缺口；Ultra重修补、授权、隐藏/SELinux、越狱/模块及Manager未打开冷启动逐功能验收。 |
| [ ] | FINAL-05 | 每阶段交付完成项、残留旧实现/过渡产物、源码/交付物/必要设备验证范围和未完成项；全部原有及追加要求逐项取得相应证据后打勾。最终审查实际工作树与发布包，不以接入成功、链接通过或单设备结果缩小总目标。 | 全程/P8 | 已实现待设备验证：本次119项深审保留原文/原勾选及20条约束，按源码/产物/执行/残留和下一步分别列账。 已验证范围：只有原SDK-01/07/08三项完整勾选；历史设备与当前文件证据保留各自原范围。 实际签名14份中12份直供输入精确恢复，2份历史仅生成baseline；28次文件安装/恢复读回，不证明OEM信任或启动。 待闭合：P0–P8所有阶段对齐统一119项及追加条件，残留/清退列表最终核对；最终工作树与实际同代发布包/设备覆盖联合验收；所有完整复合条件取得证据后逐项勾选。 |
| [ ] | FINAL-06 | 以`本次策略隐藏故障归档`（开发证据未随公开快照分发：`../Log_Reference/11.14/Selinux/MapleSU_bugreport_2026-11-15_11_45.tar.gz`）为必测回归样本，从SDK源码与MapleSU实际调用链查明根因，评估并实施最合适的统一方案，消除“策略隐藏后端加载失败”及关联策略读取故障；以适配全机型为目标，按目标内核/BTF/配置正向适配，不依赖固定机型指纹。验证真实后端加载、策略读取与隐藏生效，以及冷启动、重启、升级、重装、启停、失败恢复和状态时延；清空提示、强制成功、空结果或仅进入fallback均不构成通过。保持UI布局、原功能和Ultra既有环境边界，遵守SU-05/06与QA-07的时序、SELinux、挂载及可见性约束；提交源码、实际交付物与设备证据，分别列明已测和未测机型/内核组合。 | P2/P6/P8 | 存在阻塞：指定策略隐藏故障归档线索及状态/策略局部修复已保存；当前m073相关核心选源仍有IR过渡。 已验证范围：历史配置不匹配/hide-fallback仅作线索；没有指定失败包在当前最终backend上的加载、读取与隐藏效果闭环。 待闭合：指定bugreport原失败输入→最早异常→实际加载/策略读取统一根因；目标BTF/配置/页大小正向适配及核心源码过渡消除；冷启动/重启/升级/重装/启停/恢复与状态时延，原码和效果同时记录；保持SU-05/06环境约束并按已测/未测设备核验。 |
| [ ] | FINAL-07 | CASE-11/12/13逐项修复并以失败原包及实际发布产物验收：KSU卸载/撤销与开机卸载对齐选定官方实现，重启后实际完成清理且Manager未打开也能收尾；Ultra原生模块卸载回调、资源/文件/状态与应用列表一致，不永久滞留待卸载；原有WebUI从SDK加载到应用导航、页面资源和关键交互均可用。提交源码、交付物及必要实机证据，覆盖失败/重试/重启生命周期，保持原Ultra模块兼容、UI布局与既有环境边界。 | P6/P8 | 存在阻塞：原生模块卸载/Normal撤销/开机事件消费者及WebUI候选、原包清单和窄验证已有实际代码；无需Manager的实际init producer仍缺。 已验证范围：历史r9模块列表/single与旧Normal产物不能代替固定失败包完整安装/开机卸载/页面交互。 待闭合：真实init事件生产端及未打开Manager的开机脚本/卸载收尾；CASE-11/12/13固定失败原包在同代产物的失败/重试/重启生命周期；Ultra回调/文件/状态/列表一致与原WebUI导航资源/关键交互设备证据。 |
| [ ] | FINAL-08 | CASE-14与CASE-09的环境安装可靠性按根因交付：提交SakiSU固定基线差分、失败设备/镜像复现、SDK及修补/启动/授权实现修复、实际打包身份、逐镜像模拟修补及必要实机回归证据。目标iQOO/VIVO首次安装后首次启动即正常启用，管理器未打开仍初始化并使已授权32/64位软件经传统su实际取得Root；验证vr.ko及确认关联的加载/通知干扰已消除，未改变无关模块、UI与Ultra既有环境边界。连续重启、升级重修补、槽位切换及失败恢复结果一致；不以新增阻断、等待/重试、静态标记或提示隐藏替代真实启用。已测与未测机型/内核/镜像分别列明，取得相应证据后逐项打勾。 | P2/P6/P8 | 存在阻塞：VIVO/vendor_boot候选清理/companion真实写集、Ultra目标过滤器及SDK修补/授权局部修复已实施；旧“尚未实施”需保留这些成果。 已验证范围：VIVO-PROCESS 11项仅普通进程fixture，18boot生命周期不是iQOO/VIVO首次启动Root；尚无对应首次冷启动完整证据。 待闭合：固定SakiSU差分与失败设备/镜像最早异常可重放证据；真实启动/身份/授权producer及必要SDK/目标适配修复；同代首次安装→未打开Manager首次冷启动→32/64位传统su UID0；vr.ko相关加载/通知因果、连续重启/升级重补/槽位/失败恢复。 |
| [ ] | FINAL-09 | 本批CASE-15–20、LAY-08、IMG-08、MOD-10与扩展后的CASE-13逐项交付：模块列表/策略读取可靠且SDK Busy根因有真实并发回归证据；正常路径不误入安全模式且真实保护/恢复保全；8个固定原包（原7包加CASE-20包）及指定Zygisk-Next的安装、开机运行、更新、WebUI与卸载/开机卸载按各自实际能力验收。Tenc游戏优化助手-C版Log测试包按实际配置验证功能生效与开机生命周期；两个月虹WebUI在指定失败设备上正常且保留原模块契约；内联与独立宿主、KSU全部WebUI入口/JS桥及最新选定基线新增接口有逐项调用与生命周期回归，页面、命令结果和资源一致且无对应闪退；iQOO 15越狱路径以真实UID、传统su及模块结果验证，再分设备扩展。init_boot与Linux 4.x分别交付有源码依据的可行性、实现范围和缺口，支持声明须附对应模拟修补/实机证据，不将评估通过当成功能支持。保持全部既有需求、UI与Ultra环境边界，源代码、实际交付物、设备证据分别登记，未覆盖组合明确保留待验收。 | P2/P6/P8 | 存在阻塞：CASE-15–20、模块/WebUI/越狱、4.x评估和init_boot容器/handoff有分批实际代码与原包清单；广泛源码/接线与最终产品验收仍不齐。 已验证范围：仅各原收据明确的主机/窄产物/历史设备范围；当前init_boot六图为容器，handoff不是bridge，模块/API映射不是运行通过。 待闭合：SDK Busy真实并发根因/安全模式保护与恢复；8固定原包及指定Zygisk-Next、Tenc配置实际效果/开机/WebUI/卸载；月虹两包指定失败设备的全部宿主/JS桥/导航生命周期；iQOO15 provider→carrier/lease→SDK→真实UID/32/64 su与模块；Linux4.x及独立init_boot的实现/源码缺口/模拟与设备证据分别交付。 |

FINAL-06归档证据：SHA-256为`daa584d7729c6a9005e61d38f4f2041d76ce724e7bf466541ba31deaf175fa1c`（仅用于标识回归材料）。归档内`pro-module-policy.txt:7636–7637`及后续重复记录出现`MODULE_POLICY_LOADER_CONFIG_MISMATCH:CONFIG_PAGE_IDLE_FLAG,CONFIG_TRIM_UNUSED_KSYMS`，`:7679–7680`与`:7708–7709`记录`hide-fallback`。这些是历史设备失败线索，尚未确认完整根因；后续须追溯SDK及实际交付后端的构建配置、加载与策略访问契约，凭对应设备回归结果闭合，不直接删除配置判定后宣称修复。

过渡登记至少记录：子系统/成员、当前真实生产输入、目标源码、过渡资产用途、统一实现入口、替换验证、旧路径删除状态、实际打包及设备证据。未达到最终状态的字段保留残留标记，不用新的包装层替代清退。

## 11. 后续更新规则

每次实施批次必须更新：需求 ID → 变更文件/函数 → 旧行为与新行为 → 构建产物身份 → 执行命令 → 期望与实际 → 验收范围 → 残留旧路径/过渡输入 → 退出过渡与删除条件 → 回退办法。证据可集中保存在该批开发目录，前端不展示这些内部细节。

所有阶段继续沿用本清单。完成状态变更写明日期和证据；依赖尚未满足时不跳到下游替换凭据或发布 Hook。范围变化由会话中用户的明确新要求更新本文，不以子代理建议静默删减。

## 2026-11-15：零模块与镜像覆盖补充证据

本批不新增或缩减需求ID，119项总需求继续按三类证据验收，完整完成项保持原范围。

- [x] 限定主机子项：模式切换真实生产接线219项通过；实际源码摘要运行前后稳定，未以无备份直接跳过源载体恢复。
- [x] 限定主机子项：共同BTF输入50例符合各自合同，18例合法完整字段108项一致、20例非法输入拒绝；12例字节/消费约束差异分别记录，未把两套parser称为已收敛。
- [x] 只读覆盖盘点：1690命名文件归并226个字节唯一组，192组当前boot容器解析成功；147组带内核boot与7份raw Image取得ARM64/release/IKCONFIG，均实际4K。无设备数量外推；真实16K/64K、Linux4.x、legacy容器及完整ZSTD压缩支持仍有缺口。
- [x] 零模块入口复现：真实helper/session/bootstrap_finish夹具6例47断言，-Xzygote在SDK初始化及原target交接前返回2；只证明该主机入口异常，未归因全部Panic或设备安全模式。
- [ ] 正式产物、零模块首次冷启动/连续重启/管理器未打开Root与每类修复动作验收；原完整需求不由以上局部勾选折算完成。

证据：`载体恢复`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/MODE-SWITCH-CARRIER-SOURCES.md`）、`共同BTF差分`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/btf-common-differential-r1/RESULT.md`）、`镜像覆盖`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/IMAGE-COMPATIBILITY-COVERAGE.md`）、`helper入口`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/HELPER-ENTRY-ZERO-MODULE-REPRO.md`）。

## 2026-11-15：压缩容器与重签的限定修复

本批继续保留119个唯一需求和所有原最终验收条件，关联IMG-02/04/08/09、CASE-09/16及FINAL-09；不新增完整完成勾选。

- [x] LZ4 frame/XZ真实ramdisk入口不再预先排除合法格式；实际decoder补齐LZ4 block/content XXH32与XZ CRC64/SHA256。独立压缩资源2028项、Inspector合计2995项通过；48个校验字节损坏输入的真实红例与绿测均保存。
- [x] kernel=0 init_boot真实重建/重签链521项通过，覆盖RAW/GZIP/LZ4增长、重复、缩小、恢复与非链式AVB每次密码学校验；相同property累加的12→15红例已由最小生产修复消除，保留不同值和非目标描述符。全image/installer主机门禁16/16 suite通过，原签名104项通过。
- [ ] Android正式产物、独立Ultra init_boot内核桥、零模块首次冷启动/连续重启/Manager未打开真实Root、逐原因修复安全模式与多机型设备验收仍保留未完成。外部ZSTD/type2、其他尚缺格式、真实16K/64K等缺口不由合成或容器测试补成支持。

本批没有SDK/APK重建、设备写入或重启；前端布局和既有安装入口保留。源码、测试产物与运行设备分别记账。27项717455字节证据已逐项回读，详见`压缩修复`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/RAMDISK-CODEC-COMPATIBILITY.md`）、`重签修复`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/AVB-REPATCH-PROPERTIES.md`）及`统一发布收据`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/evidence/ramdisk-codecs-avb-r1/PUBLICATION.json`）。零模块故障分类与当前fix入口的实际边界见`最新只读审计`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/ZERO-MODULE-STARTUP-NEXT.md`）。

## 2026-11-15：P3标准执行帧的限定验收

关联IPC-01/02/03与FINAL-02/04/05；119个唯一需求和完整项的原勾选范围保持，独立回执与P4依赖继续保留。

- [x] 实际m025标准帧与集中旧producer adapter：修改前3项契约检查全部失败，修后70项检查通过；包括完整64位/VOID返回、96B对齐保存帧、实际NDK A64 C callee、旧外部帧、raw restore、两个LR locator及m073共享builder。vector长度在缩窄前校验；m025两TU及消费者四TU Android语法通过。
- [x] 受影响主机接点：metadata 2307项通过、16线程1600次lookup；icache 32策略与4生成用例通过。15项当前源码及4份冻结收据的恢复核验一致，未重复运行已通过门禁。
- [ ] 旧outer adapter的凭据回执尚未清退；owned EXEC、失败后持久owner、公开ALLOC/WRITE/释放迁移、P4凭据复制替换、正式SDK/helper/backend/APK及必要设备回归尚未完成。EXEC capability保持未发布，不以标准帧通过代替独立响应或设备稳定性验收。

证据：`执行帧实施与复现`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p3/EXEC-FRAME-NEXT-BATCH-REVIEW.md`）、`冻结收据`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p3/evidence/exec-frame-r1/receipt.json`）、`恢复核验`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p3/evidence/exec-frame-r1/resume-verification.json`）。本批未重建正式产物、操作设备或修改UI布局。

## 2026-11-15：Ultra init_boot与boot完整功能等价

用户继续补强IMG-08/09与FINAL-04/09：Ultra的init_boot路线参考KSU/APatch的实际源码和适用入口，以与现有boot路线完整功能等价为最终验收，分区只改变加载载体。冷启动Root、标准32/64位su和UID持久授权、隐藏模式及端口、SELinux状态/策略读取/隐藏、原Ultra和KSU模块安装与开机post-fs-data/service/boot-completed、SDK升级与重修补、模式切换及永久卸载还原均逐项对照实际调用、打包产物和必要设备结果；保持Manager未打开时的启动时刻及既有环境边界。不得以惰性helper、仅容器重建、桥加载成功、静态Ready标记或部分能力通过代替功能保全。独立路线须证明仅修改init_boot时原厂boot保持不变、冷启动后真实内核回执和上述功能生效；保留boot路线并继续修复SDK运行时根因，不增加安装事务前置要求。

- [x] IMG-02/04/08的限定主机子项：v0–v3实际修改writer与固定AOSP工具独立生成的oracle整图一致，4例85项断言/24条命令通过；含多页与空组件、ID/DTBO、幂等和输入保全。见`AOSP互验`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/AOSP-REBUILD-INTEROP-NEXT.md`）。
- [ ] 独立Ultra init_boot的完整功能等价、正式桥/SDK/helper/backend/APK配对和必要设备验收仍未完成；119个唯一需求及完整项原勾选范围不变。

## 2026-11-15：可靠修补优先与GKI/BTF实际判断

用户要求优先完成最可靠的boot/init_boot修补器、减少错位及Panic根因，修复后复测再继续；既有119项需求、UI边界和三层证据规则全部保留。目标BTF可用时直接消费实际类型图、字段偏移/宽度/owner，不增加版本/机型偏移库；无BTF才补目标访问器与只读验证。移除无必要安装前置门槛与保留真实写入边界是两项独立要求，不因减少前置门槛而删除范围、编码、目标或回读约束。

- [x] 只读配置及源码核对：7份固定Android官方GKI配置中android12-5.10未设置BTF，其余六份明确启用；这不是全部厂商设备证明。离线生产已优先目标BTF，SDK仅五getter接共享描述，Kotlin重复parser、无BTF旧推导/候选写、boot SDK及凭据连续写仍是残留。见`BTF实际路径`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/BTF-GKI-ACTUAL-PATH-REVIEW.md`）与`官方配置`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/BTF-GKI-CONFIG-EVIDENCE.md`）。
- [x] IPC-01/03限定消费者修复：实际54/58B append发布差异先红测2310项/2失败，按可选audit能力声明修后2327项/0失败，四生产TU Android语法通过；默认离线54B与命令能力7保持，完整范围先校验，不静默回退旧executor。见`实施和证据`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p3/APPEND-AUDIT-CAPABILITY-PLAN.md`）。正式产物和设备不在此勾选范围。
- [x] boot SDK两端把task.seccomp外层位置当mode发布的确定合同缺陷已取得限定主机证据：C++真实BTF派生52/0及Android语法、Python初批15/0；匿名owner差分另按同一输入60/0核验。合法mode组合非零子偏移，宽度/owner边界错误不发布environment能力；正式交付与设备仍待验。随后按P3/P4退出凭据回执、布局邻接和连续写。
- [ ] 独立init_boot完整功能、正式交付及设备验收继续未完成；桥复用的12组功能差距和验收接点见`boot/init_boot等价矩阵`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/INITBOOT-BOOT-PARITY-MATRIX.md`）。不得把本批主机修复写成全部Panic已解决。

## 2026-11-15：可选NGKI无BTF接入与当前验收范围

用户新增可选NGKI（非GKI）无BTF动态解析修补要求，纳入LAY-01–08、IMG-01/02/04/08与FINAL-02/04/05/09；原119项需求及所有最终验收继续保留。目标BTF优先；无BTF使用本目标访问器语义与只读验证，不按版本/机型/指纹套偏移，不通过写UID/GID猜字段。评估通过的路径须接入实际修补和SDK消费者，替换验收后清退重复路径。支持范围按目标架构、符号、布局、调用ABI、Hook/重定位、页大小、初始化及必要设备结果登记，不能把无BTF、NGKI、Linux4.x或32位应用兼容混为同一支持声明。独立Ultra init_boot仍须闭合内核桥与早期启动。

- [x] 当前源码raw Image七目标实际生成/独立回读7/7通过，区域外变化0；容器34路径/33唯一内容逐份复测已完成记录，其中20/31 boot完整通过、11份直接生成退出、2份init_boot仅容器通过。历史恢复入口与三份旧AVB异常单独处理。见`P2当前矩阵`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/CURRENT-SOURCE-CONTAINER-RETEST.md`）。
- [x] root-loader B1与真实SDK客户端六会话254项/0失败、59传输；主代理独立核对41份发布文件/27份生产输入。动态构建入口31项、gateway356项及三Android TU通过；尚未取得完整动态READY。见`P3真实配对`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p3/ROOT-LOADER-CLIENT-PAIRING.md`）及`P2动态入口`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/BOOT-SDK-DYNAMIC-BUILD-ENTRY.md`）。
- [x] ZSTD帧checksum/无符号FCS/块大小限定修复已红绿验收，固定112/0、外部66帧合计244/0；11套image回归按7项首过/4项修夹具后重跑分别留证。Python/C++匿名seccomp路径在同一60输入上0差异；主代理逐份核对两批74/24份小证据。完整entropy/type2及真实ZSTD镜像/设备仍待完成，见`P2实施范围`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）。
- [x] LAY-03限定主机修复：声明BTF但缺完整task owner不再悄悄转入访问器；真实调用红41/1→最终41/0，空消费者和无blob正控保留。NGKI无BTF入口仍按独立字段/ABI实现，不借此宣称支持；见`来源边界`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/BTF-OWNER-ABSENCE-PLAN.md`）。
- [ ] NGKI可选路径实现与完整接入、无BTF写探测/关系推导清退、P3 owned EXEC/P4凭据替换、独立init_boot桥、正式新产物与设备测试继续推进；本批局部勾选不代表这些完整需求完成。本批未改设备启动配置、刷写或重启。

## 2026-11-15：无BTF字段接入的限定完成项

119项总需求与全部最终验收保持。LAY-03/04/08、IMG及NGKI扩展按实际证据推进：SDK UID/EUID/task.cred 已替换缺BTF时的对应写候选探测/邻接推导，离线SUID/SGID独立识别；其余getter和旧协议仍登记残留，不以本批清退代替整体统一。

- [x] 源码/主机限定验收：SDK 61场景437断言、三个Android生产TU语法，离线9套89项通过；严格CSEL和单边BTF声明边界保留。一次实际生产来源编译后，真实5.10/6.6 raw分别六accessor/六BTF，独立顺序重放与完整摘要匹配、区域外变化0、候选字节与此前一致；两端函数夹具和共同字段已对照同一真实Image。见`接入记录`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/NO-BTF-SOURCE-INTEGRATION.md`）。
- [ ] NGKI完整入口/ABI、其它无BTF旧路径、独立init_boot功能、P3/P4、正式SDK/helper/payload/backend/APK与设备验收仍未完成。本批不操作设备启动配置、刷写或重启；当前ADB列表为空，未新增实机通过项。

- [x] LAY-03/06与INIT-01限定源码子项：SDK seccomp已消费同一BTF/缺blob访问器；85场景596断言及Android语法通过。task state只读探针的失败挂起、线程析构和资源泄漏已分别复现并修复，28/28真实线程/pipe故障用例及完整TU Android21 `-c`通过；143/143发布身份与当前精确源码独立核验一致。保留其它getter、缓存、首次启动与正式归档/设备缺口，详见`P2接入范围`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）。
- [x] LAY-01/02/03限定源码子项：boot SDK凭据布局与字段ABI统一跟随实际task.cred指针，清退无关同名cred补查；四真实BTF矩阵182/182、共享parser 186例及两TU Android语法通过。55份发布文件、9份当前源码与701份唯一NDK依赖独立核验一致，旧三参公开符号保留。此勾不包含完整无BTF/NGKI启动、正式新产物或设备，见`P2本批范围`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）。

## 2026-11-15：可选无BTF接入的限定增量

原119个唯一需求与完整项的勾选范围保持；关联LAY-01/02/03/04/08及FINAL-02/04/05/09。

- [x] 目标初始化实参的owner-size已进入实际describe；r2地址来源/编码修复39/39通过，真实5.10与6.6对象范围保留，未知仍null、BTF仍优先。主代理独立核验31份发布证据与红绿输入身份；见`owner接入与验收`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/NGKI-OWNER-SIZE-INTEGRATION-PLAN.md`）。
- [x] kallsyms同名候选保全并由getter/saved-ID逐个消费，红4处行为遗漏、绿24/24；真实目标三个put_cred地址均保留，旧单地址API语义不变；见`实施报告`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/evidence/symbol-candidates-r1/REPORT.md`）。
- [x] controller来源I/O与调用顺序窄修复，实际完整TU的ASan/UBSan最终23/23、Android31语法和独立审核通过；缺源、权限、读取、空内容诊断分开，未增加安装事务或前置条件。见`来源读取收据`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/evidence/controller-source-r1/REPORT.md`）。resolver链接替身不作为布局或设备证据。
- [ ] 无BTF完整subject/CONFIGURE与生产接线、NGKI首启、独立Ultra init_boot桥、正式SDK/helper/payload/backend/APK及必要设备验收仍未完成；当前controller仍只有sysfs BTF来源。以上局部通过不折算完整NGKI支持或Panic根因全部闭合。

- [x] LAY-01/02/03的统一事实消费子项：实际boot SDK入口收敛到`describe → consume`，48项事实检查及182真实BTF场景通过，148份完整wire与34份错误结果保持。65份证据与1,373条NDK依赖经独立复核；32个页变体的输入身份明确继承基例。保留公开入口、wire和可选能力边界，完整无BTF producer、正式交付及设备继续待验。见`P2本批收敛范围`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）及`独立审核`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/evidence/boot-sdk-facts-r1/INDEPENDENT-REVIEW.md`）。119个唯一需求及完整项原勾选范围不变。

## 2026-11-16：无BTF subject实际默认消费者

原119个唯一需求及全部最终验收保留，关联LAY-01/02/03/04/06/08和FINAL-02/04/05/09；下列仅为源码/主机子项。

- [x] 六字段subject默认producer/consumer真实接通，legacy旧注入旁路及对应固定量测已收敛；七份真实Image、八次描述调用，42项consumer测试和59项观察通过。新增CSEL W截断红例71/1、修后72/0，当前源码与实际完整JDK17 host构建分别留证，独立核验280/280通过。
- [ ] 完整无BTF CONFIGURE、真实NGKI及其它内核/页大小组合、独立Ultra init_boot桥、P3/P4依赖、正式SDK/helper/payload/backend/APK和必要设备验收继续未完成。不得将六字段通过折算上述整体完成；正式r9未替换，本批未写分区或重启，最近ADB库存为空。

证据：`默认入口限定验收`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/NGKI-SUBJECT-CONSUMER-RESULT.md`）、`独立审核`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/evidence/ngki-subject-consumer-r1/INDEPENDENT-REVIEW.md`）。原publication不重写，审核文件单独追加身份登记；哈希仅用于产物/证据身份，不用于目标内核识别。

## 2026-11-16：修补回归收尾与P3主线

119个唯一需求及完整验收继续保留；以下为限定源码/主机子项，未将完整LAY/IPC/CRED项改为完成。

- [x] LAY-01/02/03/06：securebits独立目标语义与真实BTF指针图接入；统一walker具体NZCV/WX/加载扩展错误接受已修。最终121/0、全源码宿主9/9、真实subject consumer42/0及59观察、协议11/11、当前raw七目标生成/独立回读7/7与代表容器5/5通过；R1–R7独立审查完成。见`P2限定结果`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/NGKI-SECUREBITS-RESULT.md`）。init_boot代表项只有容器回读，未部署独立内核桥。
- [x] IPC-01/03限定参数边界：实际m023请求TU修复u32对齐溢出、非法页大小/零长、READ/WRITE空缓冲和两端范围；旧TU红221/42，修后legacy240/0、V2 9741/0、READ8427/0和实际Android21单TU编译通过。原wire/命令/旧回执保持，见`P3本批验收`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p3/REQUEST-ARGUMENT-BOUNDS-RESULT.md`）。
- [ ] 用户指定的Boot_Research/NGKI与全目录boot/init_boot的真实修补、还原与升级另立实际矩阵，不以名称、容器往返或凭据轮换冒充版本升级/Root启动。
- [ ] P3独立执行回执与生命周期、P4复制后替换、无BTF CONFIGURE、NGKI首启、独立Ultra init_boot桥、正式新交付物及必要实机仍未完成。P5–P7隔离修复在P4后正式合入并完成P8；当前r9未替换，ADB库存为空。

## 2026-11-16：无外部备份的镜像生命周期最终条件

继续保留119个唯一需求及全部最终验收；本次加强IMG-01/02/04/08/09、CASE-06/09/16和FINAL-01/02/04/05/09，不增加Manager在线、安装事务连接或强制本地备份门槛。

- [ ] 新版源码生产的修补镜像应自包含必要的版本、布局来源、原始修改范围及恢复信息；移除本地回执、卸载重装Manager、换工作目录后，仍逐项验证同版重补、真实跨版本升级/回退、移除MapleSU改动及再次修补。复用并接入既有可逆记录实现，验收后收敛重复恢复路径，不靠猜测原字节或略过Hook/布局/边界校验制造成功。
- [ ] boot与init_boot分别验收；init_boot须有真实独立内核桥和早期启动回执，容器往返或惰性helper不折算功能通过。当前有原镜像备份时，永久卸载还须验证整份原镜像逐字节还原。
- [ ] “移除MapleSU改动且功能回到修改前状态”与“原厂整镜像逐字节一致”分开记录。无外部备份而要求后者时，首次修补必须同时保全被替换/删除的原压缩字节、签名、AVB和必要容器内容；旧镜像未保存的内容列实际缺口，不伪造为原厂字节。
- [ ] 真实样本分别固定输入代次，记录源文件测试前后身份。外部更新同一路径时另立代次，不拿旧结果验证新文件；原件只读，候选隔离，不自动刷写测试失败的镜像。
- [ ] 镜像通过后继续P2→P3→P4，SDK每项功能按源码、实际发布产物和必要设备证据验收；授权、隐藏、SELinux状态、越狱、零模块首启/安全模式、模块阶段与三模式仍逐项保留。主机生成、同版凭据变化、单内核回读均不替代完整功能或真实版本升级。

## 2026-11-16：指定镜像实测结果与残留

- [x] 测试执行及基线已验证：Boot_Research全部17份非NGKI输入实际调用完成，10份boot生成/完整kernel回读、带回执同版重补、原kernel恢复及凭据变更通过；有原图备份的整图恢复10/10逐字节一致。两代NGKI 4.9/5.4分别调用真实describe/generate并保留首失败，外部输入替换按代次记账。详见`P2逐项实测`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）。此勾仅标记测试已执行，不是完整修补功能已通过。
- [ ] 待修复：无本地回执重补0/10；无外部备份整图恢复4/10；5份boot Hook入口识别失败、1份旧LZ4图footer落入kernel；init_boot无独立Ultra入口；两代NGKI无候选。真实跨版本升级/回退、完整无备份恢复及必要设备验收继续未完成，119个唯一需求及所有完整项保持。

## 2026-11-16：像 KSU 一样按需执行的源码批次

保留119个唯一需求和原全部最终条件；关联IMG-01/02/04/06/08/09、CASE-06a/07a/07b/09及FINAL-01/02/04/05/09。操作合同与证据见 `KSU 式镜像操作`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/KSU-CONVENIENCE-IMPLEMENTATION.md`）。

- [x] 限定源码子项：共享 gzip 组件保尾及 Lite 消费接点通过；单一镜像内恢复区和签名保全5,459项主机检查、Manager实际39项回归及独立审查通过。模式切换同一恢复入口33项通过；KSU备份失败仅日志的偏差修复8项通过，23补丁可重放。上述均未折算成完整IMG或设备功能完成。
- [ ] 新源码真实首轮17份全部执行、原件保持；7份生成成功且7/7无本地记录重补。三份旧成功同SHA输入的差异声明并集回归已定位，必须修复后按新代次复验，当前不记完整矩阵通过。旧基线0/10属于接入前代次，保留历史，不混用新旧结果。
- [ ] 重补、升级、切换和卸载由后端自动选择材料，UI布局不变，不新增安装事务连接、Manager在线或强制本地备份门槛。有原图时精确恢复；无原图时移除自有写入的结果不得冒称原厂整图。无备份永久卸载实际入口、真实跨版本升级/回退、NGKI与独立Ultra init_boot桥、正式交付及必要实机证据继续未完成。

## 2026-11-16：恢复子代理与真实 r2 验收

原 119 个唯一需求与全部最终条件继续保留。上一节首轮三份回归的后续状态如下，原红证据保持，不混用不同源码代次。

- [x] 限定源码/主机范围：相邻声明区间并集修复的 5,469 项检查通过；新 Java 代 consumer 39/39。真实 r2 全部 17 份实际调用，10 份生成，10/10 完整内核回读、无本地 receipt 重补、镜像内 raw 恢复及换 Key 重补通过；原三份同 SHA 回归消除。
- [x] 另有输入原图时整图恢复 10/10 逐字节一致，候选再生成一致；无外部备份精确输入整图恢复为 4/10，未把 raw 内核恢复冒称原厂整图。模式切换新 Java 代复测 33/33，输入身份保持。详见 `P2 最新证据`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`） 与 `r2 逐图结果`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-15-p2/evidence/embedded-real-lifecycle-r2/RESULT-SUMMARY.md`）。
- [ ] 剩余 3 份 Hook、3 份旧 AVB 与独立 init_boot 入口继续逐项修复；case04 非 PAC 前序红绿验证、无备份永久卸载接线及独立审查由恢复后的三个子代理分别推进，不重跑已通过的大构建。
- [ ] 真实跨 SDK 升级/回退、NGKI、独立 Ultra init_boot 功能等价、P3 独立回执/P4 凭据复制替换、P5–P7 正式合入、真实交付物与必要设备回归继续未完成。本批设备列表为空，未新增刷写、安装、重启或稳定性通过项。

## 2026-11-16：恢复后的卸载接线、AVB与能力读取验收

119个唯一需求及全部最终条件保留；本节只更新源码/主机子项，不新增完整需求勾选。

- [x] 无原图永久卸载已接入开发源码，原图优先、缺失时从镜像内记录移除自有写入，复用唯一writer及回读/回滚。raw与精确原图的结果贯穿持久化、重启状态及公共文案，不再折叠为原厂恢复；失败不清身份。123核心+13公共回归通过、完整ViewModel窄编与独审通过。334项冻结文件及18份当前输入由主代理核验，差异0。unsigned raw boot限定范围、r2核心来源及两份后续缺失临时缓存如实记录；raw signed/正式APK/设备仍待验，见`卸载实施`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/evidence/owned-environment-removal-r1/REPORT.md`）。
- [x] 三份旧AVB失败的实际字节范围已核实：缺少1664B vbmeta，尾部全零，近似独立图不足以证明原metadata来源。保留真实失败；固定KSU依赖的处理策略继续先评估，未通过放宽检查或套用近似原图制造成功。见`P2最新记录`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）。
- [x] SDK实际能力读取函数消除恒真成功、prefix-only数字和未检查stream error；保留四字段/可选ambient及合法零值。最终同一32场景/96检查，旧源41失败→当前0失败，原14场景通过；实际Android21 m000单TU编译及960条依赖记录独审通过。仅用户态读取修复，m070错误传播和P4复制替换继续待实施，见`P4限定范围`（开发证据未随公开快照分发：`SDK-REFACTOR-P4.md`）。
- [ ] P3独立执行回执/P4生命周期、NGKI、独立Ultra init_boot、真实跨版本、P5–P7正式合入、实际新交付及必要设备回归继续未完成。UI布局固定；本批ADB为空，未新增实机通过声明。原有owned EXEC停止边界保持。

## 2026-11-16：恢复代理后的case04限定验收

119个唯一需求与原全部最终条件保留，完整项仍仅SDK-01/07/08勾选。

- [x] LAY/IMG限定子项：case04实际saved-ID wrapper ABI修复已接入；53项窄回归通过。真实boot生成、完整kernel独立回读、带/无receipt同版重补、raw恢复、有输入原图整图恢复及换Key重补分两轮通过，记录外变化0。主代理逐项核验172发布副本、5当前源码、13夹具、69依赖、55个class和overlay，差异0。见`P2本批验收`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）及`saved-ID报告`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SAVED-ID-FRAME-RESULT.md`）。
- [ ] 剩余两份历史Hook、三份旧AVB、独立Ultra init_boot桥、完整NGKI与真实跨SDK升级继续逐项推进。raw恢复和精确原厂整图恢复分别记账；不将同版换Key称版本升级，不将单个5.10访问器通过称NGKI完整支持。
- [ ] P3独立执行回执/P4凭据复制替换、P5–P7正式合入、正式SDK/helper/payload/backend/APK及必要设备仍待完成；既有owned EXEC停止边界保持。最新ADB列表为空，无新增安装、刷写或重启通过项。

## 2026-11-16：最新核心整合与raw signed待修复

- [x] 限定主机子项：现有123核心+13公共卸载回归已接本轮已测Shellcode与static SCS Recipe覆盖、冻结56个Java类，两个JVM全部通过，无新增编译；来源组合单独保存，不冒称整树当前源码。主代理核验最新manifest及固定KSU小证据，6985个唯一文件身份一致。见`P2整合记录`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）。
- [x] 签名负控测试已执行：有效CHAINED signed输入经实际owned恢复后，内核正确恢复且记录消失，但旧vbmeta摘要失效。已记录真实红测，不能把unsigned通过扩大为signed可写入。
- [x] 该signed恢复链的重签/验证和失败收尾已完成下文限定主机验收；正式交付、设备、其他签名模式及剩余旧Hook迁移、P3/P4、NGKI、独立init_boot仍分别待验。保留119个唯一需求和原全部终验，不增加完整需求勾选。

## 2026-11-16：显式候选重建与旧Hook边界

- [x] IMG限定源码/主机子项：同一writer显式处理有效组件＋全零尾＋损坏AVBf，原严格API保持；12新增/2015既有检查通过。Recovery早探测与实际Builder已配对编译和调用，红6/2→绿6/0，完整kernel回读、strict/非零尾/组件越界负控均通过。缺失原签名不被冒称恢复，真实三图和设备另验。见`P2限定接线`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）。
- [x] 三份真实旧AVB输入已通过冻结writer的同组件容器重建3/3，完整header/body和六类组件字节相同、原图不变、输出无AVBf；22份证据与实际工具/输入身份由主代理复核。此勾仅验容器，真实完整prepareTarget与设备另验，见`限定结果`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/evidence/invalid-avb-real-container-r1/REPORT.md`）。Recovery/Builder小容器接点另有`独立复核`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/RECOVERY-INVALID-AVB-INDEPENDENT-REVIEW.md`）。
- [x] 剩余两份历史Hook的真实入口B/52B框架与首错已核实。常量PAC不当原始字节，合成单word控制不作真图修复；统一旧通道资格、可逆自有区域更新和未知原字节缺口分别推进，见`legacy评估`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/REMAINING-LEGACY-HOOK-RESULT.md`）。
- [ ] 全部119个唯一需求继续保留，完整项原勾选不变。真实跨SDK升级/回退、其他签名模式、完整NGKI与独立init_boot、P3/P4、P5–P7合入、实际新交付和必要实机仍待完成；signed raw仅下节CHAINED来源和模拟范围通过。

## 2026-11-16 signed raw限定修复与验收

- [x] IMG/CASE限定源码/主机子项：实际CHAINED恢复摘要红测已修，复用既有signer重签/复验boot-only；123核心+13公共回归、五签名负控与正常/故障回滚两模拟通过，main vbmeta与init_boot零写且不变。两当前源/10个signed class/12个Recovery class/14条实际来源独审一致，详见`P2限定验收`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）及`独审`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/OWNED-SIGNED-INDEPENDENT-REVIEW.md`）。
- [ ] 此为现有匹配key/chain、真实resolver与sequence模拟的范围；Android公开操作整链、其他签名模式、真实跨SDK升级、NGKI/独立Ultra init_boot、P3/P4、P5–P7正式合入、实际新发布与必要实机继续待验。119唯一需求及SDK-01/07/08完整项原勾选保持。UI布局固定，不新增Manager在线、备份或安装事务连接前置；本轮ADB为空，无安装/刷写/重启通过项。

## 2026-11-16 三坏AVB后续完整调用与旧通道资格

- [x] IMG限定测试执行子项：三图各一次完整prepareTarget已执行，case09（6.6.118）返回候选、17记录完整kernel独立回读/区域外0和容器回读通过；case02/08从AVB失败转到当前Hook入口首错，无候选。22份正式材料/三原图/19条真实class来源核验一致，case09后续runner管道异常与已完成产品结果分开保存，整批未记全绿。见`P2最新分层结果`（开发证据未随公开快照分发：`SDK-REFACTOR-P2.md`）。
- [x] 原case07/17完整旧通道资格已作有界只读评估，具体metadata、容量、native入边与ABI缺口已登记；此为评估完成，非迁移功能完成。新case02/08另行归类，不套同一旧格式。
- [ ] 当前四份Hook、真实跨SDK升级/回退、完整NGKI、独立Ultra init_boot、P3/P4、P5–P7正式合入、真实发布与设备继续待验。原119唯一需求与SDK-01/07/08完整项勾选保持；UI及操作前置边界不变。

## 2026-11-16 源码优先与统一批次验收

执行方式按用户最新要求更新：先集中完成可独立源码修改，减少重复红绿测试和构建；修改批次结束后统一运行相关回归，再集中构建交付物及必要设备验证。必要接口/编译检查保留，测试尚未执行的源码不冒称已验证。

`继续推进计划`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-BATCH-CONTINUATION-PLAN.md`）逐组覆盖全部116项未完成ID；原119项总需求、全部最终条件及SDK-01/07/08原完整勾选保持。恢复后的代理按文件独占推进NGKI解析、能力同次快照/错误传播及旧输入合同；主代理负责缓存读取、公共接线和统一验收。新增case02/08均已核为旧Hook结构，四输入继续分别记账；`分类材料独立核验`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/evidence/additional-hook-classification-primary-review-r1/RESULT.json`）只证明本轮小证据完整，不代替生产迁移、正式发布或设备通过。

## 2026-11-16 集中源码与统一验证结果

6组生产源码修复已完成限定主机验证：`本批报告`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-UNIFIED-BATCH-R2.md`）。包括NGKI符号表10项、Key9项、缓存23检查、live BTF 5场景33断言、能力同次快照/m070共21场景及append2350检查；实际Android编译/语法范围分项记录。旧executor2用例只验证来源，不提升旧镜像迁移资格。NGKI真实describe已进入下一调用语义修复，继续推进。

所有119个唯一ID、全部最终要求和SDK-01/07/08原完整勾选保持；此次不新增完整需求勾选。P3/P4关键依赖、独立Ultra init_boot、隔离P5–P7合入、正式SDK/helper/payload/backend/APK、跨版本与必要设备仍待完成；不把主机批次通过当作完整功能或设备通过。

## 2026-11-16 R3/R4统一源码结果与继续接线

119个唯一需求与全部最终要求保持，完整勾选仍仅SDK-01/07/08。已完成的限定子项分层见`R3`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-UNIFIED-BATCH-R3.md`）、`R4`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-UNIFIED-BATCH-R4.md`）：真实NGKI两项describe、156项Kotlin回归、metadata身份、live BTF真实段/字段边界、请求身份快照、claim最小收敛及实际PatchCurrent错误传播分别有当前或冻结收据；不同代次不拼作完整功能总分。

- [x] 限定源码/主机子项：m072 28检查及Android TU、request三套及Android TU、claim10编排/两DDK实际ELF通过；live首版50场景及独审后4受影响场景分别记录。正式SDK/helper/payload/backend/APK、唯一freeze与原图保持。
- [ ] 最新公共缺口：SERVICE模式切换的分区参数两端丢失已落源码纠正，缺外部baseline的SERVICE恢复接线仍在实施，统一验证尚未执行；UI不重复修改，writer互斥/回读保持。
- [ ] 四份旧Hook、完整NGKI、独立Ultra init_boot桥、跨SDK版本、P3独立执行/P4复制替换、P5–P7正式接入、新发布和必要设备仍未完成。NGKI完整generate真实首错缺do_execveat_common，后续入口分析已有自动审批停止，保留该边界。未以接入、编译或局部回归替代完整验收。

## 2026-11-16 R10/R11源码继续与验证边界

本轮已收敛三份path探测owner、独立cap输出访问器与旧real_cred猜测TU；inode/task结果/窗口、ptrace资源生命周期及真实options判据均有源码增量。cap限定主机/Android证据和原生命周期片段结果分别登记；实际inode整TU发现的namespace错误已修，修后编译与R11用例尚未验证。专用测试代理被自动审查以“possible cybersecurity risk”中止后续回归，未转交或改写请求重试。源码、实际交付和设备继续分层，原119个完整需求和三项勾选保持。详见`本批源码`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-LIFECYCLE-BATCH-R9-R10.md`）及`证据范围`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/evidence/source-lifecycle-batch-r9-r10/REPORT.md`）。

## 2026-11-16 R18/R19 限定主机证据

R18目标上下文/进度、真实SERVICE镜像导出与writer首错保全：18个不同JUnit组合通过及writer11+4检查通过，fixture三次首错均保留；R19同次回执/任务身份与签名派生binding：12个新增受影响用例和三源窄编通过。两批实际冻结身份独立，R21继续演进的源不继承旧代验证。未执行SDK/APK、真实Binder/FD、镜像修补或设备；sync调用不证明断电持久性。详见`SOURCE-CONTINUATION-R18`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R18.md`）和`R19`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R19.md`）。

继续P2：R20镜像交付和R21去历史preflight资格票已在源码推进，最终受影响验证待完整批冻结。UI布局固定、实际分区/内容边界/认证/回读与失败恢复保留；119个完整ID勾选保持3项，不把直接begin或主机编译当作模式切换/实机成功。随后仍按四类旧Hook、完整NGKI及独立Ultra init_boot桥的原缺口推进，已有自动审批停止范围不重试/转交。

## 2026-11-16 R20/R21 切换入口与执行收敛

六个生产源和六个用例源已冻结，唯一测试代理集中验证中。移除Facade重复preflight、固定空间/诊断/旧就绪标志前提；SERVICE与Pro begin不依赖历史预检缓存；InstallCommit真实安装入口也去S0b，begin后才确定搬移归属并保全abort未确认状态。R20同时统一三处镜像核验交付与必要同步。真实认证/目标范围/完整性/回读与失败恢复保留，UI布局不动。详细前后行为、来源及限制见`R20/R21`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R20-R21.md`）。

当前只记已实现待设备验证，不把宽松入口或建立BEGIN当成功；未新生成正式SDK/APK或设备证据，不新增完整需求勾选。四类旧Hook、完整NGKI、独立Ultra init_boot桥及原P3/P4/最终交付验收继续保持。

## 2026-11-16 R21通过与四旧图现状校正

R20/R21的31源窄编及62个受影响JUnit一次通过，零重试；只证明直接begin、同次输入、实际错误/恢复记账与普通文件交付。后续R22默认单执行方及安装prepare时刻另代待验，不继承62例。见`实际批次报告`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R20-R21.md`）。

四份旧Hook图已有另一批真实原核/旧图配对和逆记录恢复，当前接线已复用同一Recovery/Flow/Builder；本轮只读复核8资产和已有收据，不重复实现/运行。四个已知输入离线范围已通过；原厂完整容器、真正跨SDK升级、全部未知旧图、正式新包/设备仍未闭合。SDK完整门禁19失败、完整NGKI及独立Ultra init_boot桥继续保留。见`来源与状态校正`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/LEGACY-FOUR-STATUS-R22.md`）；旧“缺原核/四图都未迁移”的历史段落不再当作当前这四个有限样本的结论，全部119需求与原完整勾选保持。

## 2026-11-16 R22 默认执行方与安装时刻

已继续源改：默认Runtime从固定PRO策略下的Selector/构造失败暗退SERVICE收敛为同一个direct Pro执行方；安装入口既有prepare提前到active读取前，允许执行时补齐源模式。R22单独待集中验证，不重跑或继承R21的62例；历史SERVICE恢复、真实启动与正式发布仍未完成。详见`R22源码记录`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R22.md`）。全部原要求、UI布局和119项完整验收保持。

## 2026-11-16 R22限定验证与R23早期入口

R22完整Runtime/Facade窄编及4个新受影响用例已通过，初轮测试接线错误、主代理聚焦修正与组合收据保留；实际private构造无Root/资产调用，完整initialize/Android设备未执行。见`R22范围`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R22.md`）。R21的62用例未重复运行，正式SDK/APK与设备仍未变。

R23继续独立Ultra早期入口source，仅正常加载一次目标模块并保留原init/argv/envp交接模型，不引入版本/符号改写、挂载或全局策略。实际第一方bridge未交付，入口未打包，不把静态目标源码或容器通过声明为init_boot已获得Root；相关父项继续未完成。SDK19成员门禁另按已有冻结收据只读归因，不以重复构建掩盖缺口。

## 2026-11-16 R23最终限定证据与R24缺口

独立Ultra早期入口10个全OS wrap场景通过；Android ARM64静态target构建后，新增检查发现原4K页布局缺口，source只加64K链接对齐并复用原两对象仅重link，最终4K/16K/64K静态页关系/W^X通过。strip-only436064B仅作空间参考；没有实际bridge、producer/正式打包、loader执行或冷启动/设备，不增加完整IMG-08/FINAL父项勾选。详见`R23最终范围`（开发证据未随公开快照分发：`dual-mode/impl/_work/2026-11-16/SOURCE-CONTINUATION-R23.md`）。

SDK19项门禁已逐项只读归因：旧强定义缺失为0，多数是请求中的符号/依赖形状改变；当前073 FROM_IR、041完整字段及260当前源→对象闭包是真实未完成，未加豁免/重跑门禁。R24只读核实当前所审getter已无原UID/GID写探测和cred邻接旧猜测，comm BTF16B/noBTF关系推断等缺口继续；m001原停止回归没有转交或复试。三个正式r9产物实际摘要重算与身份一致，未替换包或设备。原全部要求和UI布局保持，完整需求仍3/119。

## 2026-11-22 追加必修：管理器在线更新模块后卸载失败

- `ADD-MOD-UPDATE-UNINSTALL-01`：用户报告从管理器模块列表“显示更新”入口直接更新安装后，偶发卸载失败，需重装旧版本才可卸载。必须修复该真实更新至卸载调用链，使更新后的模块可直接按既有生命周期卸载，无需重装旧版。
- 从实际模块列表更新入口验证更新后重启前及重启后的卸载、管理器退出/重建后的卸载、失败后重试与重复请求；重复多轮并保存每轮结果。对实际适用模式分别验收，保留原脚本/原生模块 ABI、卸载回调及失败语义。按原合同需要开机收尾的，验证未打开管理器时完成，准确区分成功、待重启与失败。
- 逐段关联下载/更新包身份、module ID、安装/暂存与生效版本、bridge/SDK/child/回调、模块私有及全局状态、bootstrap/应用记录，修复首个分歧；不以重新安装旧版、绕开回调删除目录、隐藏条目或统一成功回执结算。
- 此为独立追加项，关联 `CASE-11/12`、`MOD-04/06`、`API-03`、`FINAL-09`；原119项、20约束、两组10项及20组29子项保留。正式清单为 `.tmp/supervision-20261121/case01-20-acceptance/CASES.json` 的 `additionalCases`，执行卡见同目录 `DEVICE-PLAN.md`。当前已登记且主线与源码负责人实际接单，源码追踪中，尚未完成修复或实机验收；旧CASE-12窄JVM结果不覆盖本项。
