# idx070 预置素材（尚未恢复成员）

`module_root.o`（idx 70）本轮**未恢复**，这里只预置了它依赖的 3 个同源私有头，
供下一批次直接起步，**不要**把它们当成"已恢复成员"的源码计入任何统计。

来源（同源副本，非本仓库 `include/`）：

```
.analysis/pro-delivery-20261025/frozen-source/runtime/upstream/src/jni/common/cgroup_v2_self.h
.analysis/pro-delivery-20261025/frozen-source/runtime/upstream/src/jni/common/cgroup_v1_tasks_self.h
.analysis/pro-delivery-20261025/frozen-source/runtime/upstream/src/jni/common/selinux_procattr_util.h
```

与冻结成员的匹配性核对（pristine IR: `out/evidence-recon/vendor-ir/m070_module_root.ll`）：

| IR 里的 linkonce_odr 符号 | 本目录头文件里的定义 | 结论 |
|---|---|---|
| `cg_v2::migrate_self_to_root()` → `i64` | `inline Result migrate_self_to_root()`，`Result{bool ok; int err;}` = 8 B（寄存器返回 i64） | 形状一致 |
| `cg_v1::migrate_self_threads_v1(const char*, const char*)` → `[2 x i64]` | `inline Result migrate_self_threads_v1(...)`，v1 的 `Result` 是 4×int = 16 B | 形状一致 |
| `cg_v1/cg_v2::write_all / path_exists / write_num_newline / UniqueFd::~UniqueFd` | 同名 inline | 一致 |
| `selinux_procattr::setcon / setexeccon / detail::{write_attr, open_self_attr, gettid_compat}` | 同名 inline | 一致 |

仍需补齐（换到 `.cpp` 里写声明即可，这些类由**别的成员**定义，本 TU 只调用）：

```
namespace default_modules { namespace module_root {
class PatchBase { PatchBase(uint32_t,uint32_t,uint32_t,uint32_t); ~PatchBase(); ... };  // 4×u32
class PatchCurrent      : 继承 PatchBase；set_current_root(uint32_t) / set_current_caps(const kernel_module::caps_info&)
class PatchAvcDenied    : ...；patch_avc_denied()
class PatchFilldir64    : ...；patch_filldir64(const std::vector<std::string>&)
class PatchCompatFilldir: ...；patch_compat_filldir(const std::vector<std::string>&)
class PatchMtkHbtFilldir64 : ...；generate_hook_fake_filldir64(const std::vector<std::string>&, uint64_t&)
class PatchIterateDir   : ...；patch_iterate_dir(uint64_t, uint64_t)
class PatchAuditLogStart: ...；patch_audit_log_start()
}}
```

待恢复的 3 个导出函数（IR 行数）：`init_anti_detect_root_env` 594、`set_current_root` 222、
`set_current_caps` 91；另有内部 `get_module_eng_name()`（8 行，返回字面量）。
详见 `notes/work/CORE-03/UNRECOVERED.fragment.md` 的 `U-0070`。
