// SPDX-License-Identifier: GPL-3.0-only
#include "boot_sdk_layout.h"
#include <stdexcept>
#include <string>
using namespace maplesu::module_policy;
namespace {
const LayoutType& fact_owner(const KernelLayoutDescription& facts, const char* name) {
    const LayoutType* selected = nullptr;
    for (const auto& type : facts.types) if (type.name == name) {
        if (selected) throw std::runtime_error("MODULE_POLICY_LAYOUT_AMBIGUOUS:" + std::string(name));
        selected = &type;
    }
    if (!selected) throw std::runtime_error("MODULE_POLICY_LAYOUT_MISSING:" + std::string(name));
    if (!selected->size) throw std::runtime_error("MODULE_POLICY_LAYOUT_EMPTY_TYPE:" + std::string(name));
    return *selected;
}
const LayoutField& fact_field(const LayoutType& owner, const char* name) {
    const LayoutField* selected = nullptr;
    for (const auto& field : owner.fields) if (field.name == name) {
        if (selected) throw std::runtime_error("MODULE_POLICY_LAYOUT_MEMBER_AMBIGUOUS:" + std::string(name));
        selected = &field;
    }
    if (!selected) throw std::runtime_error("MODULE_POLICY_LAYOUT_MEMBER:" + owner.name + "." + name);
    if (!selected->bit_size || uint64_t(selected->bit_offset) + selected->bit_size > uint64_t(owner.size) * 8)
        throw std::runtime_error("MODULE_POLICY_LAYOUT_MEMBER_BOUNDS:" + owner.name + "." + name);
    return *selected;
}
std::string fact_abi(const std::vector<KernelFunctionAbi>& primary, const std::string& name,
                     const std::vector<KernelFunctionAbi>* extra = nullptr) {
    const KernelFunctionAbi* selected = nullptr;
    for (const auto* functions : {&primary, extra}) if (functions)
        for (const auto& function : *functions) if (function.name == name) {
            if (selected && selected->signature != function.signature)
                throw std::runtime_error("MODULE_POLICY_LAYOUT_ABI_AMBIGUOUS:" + name);
            selected = &function;
        }
    if (!selected) throw std::runtime_error("MODULE_POLICY_LAYOUT_ABI_MISSING:" + name);
    return selected->signature;
}
}

BootSdkLayoutFacts describe_boot_sdk_layout(const std::vector<uint8_t>& btf,
    bool allow_inner_allocator, bool allow_user_sampler, unsigned segmented_capabilities) {
    BootSdkLayoutFacts facts;
    const std::vector<std::string> common = {"copy_from_kernel_nofault", "copy_to_kernel_nofault",
        "copy_from_user_nofault", "copy_to_user_nofault", "set_memory_ro", "set_memory_rw", "set_memory_x",
        "set_memory_nx", "vfree", "get_random_bytes", "prepare_creds", "commit_creds", "get_task_cred",
        "__put_cred", "register_kprobe", "unregister_kprobe", "__arm64_sys_execve"};
    facts.layout = describe_task_credential_layout(btf, {"cred"},
        {"suid", "sgid", "euid", "securebits", "usage"}, facts.field_abis);
    auto common_description = describe_kernel_layout(btf, {
        {"kprobe", {"addr", "symbol_name", "pre_handler"}}, {"pt_regs", {"regs", "pc"}},
    }, common);
    facts.layout.types.insert(facts.layout.types.end(), common_description.types.begin(), common_description.types.end());
    facts.layout.functions = std::move(common_description.functions);
    // Query only requested branches. Preserve each original error until the
    // consumer reaches that ABI, including missing versus present-but-invalid.
    auto capture = [&](const std::string& name) {
        try {
            auto value = describe_kernel_layout(btf, {}, {name});
            facts.layout.functions.push_back(std::move(value.functions[0]));
            return true;
        } catch (const std::runtime_error& error) {
            facts.function_errors.emplace_back(name, error.what());
            return false;
        }
    };
    std::string suffix;
    if (!capture("vmalloc")) {
        if (facts.function_errors.back().second != "MODULE_POLICY_LAYOUT_ABI_MISSING:vmalloc") return facts;
        suffix = "_noprof";
        if (!capture("vmalloc_noprof")) return facts;
    }
    const auto outer = "__vmalloc_node_range" + suffix;
    const bool segmented_callable = (segmented_capabilities &
        (MSU_BOOT_SDK_SEGMENTED_OBSERVER | MSU_BOOT_SDK_SEGMENTED_CALLABLE)) ==
        (MSU_BOOT_SDK_SEGMENTED_OBSERVER | MSU_BOOT_SDK_SEGMENTED_CALLABLE);
    if (!capture(outer) && (!segmented_callable ||
        facts.function_errors.back().second != "MODULE_POLICY_LAYOUT_ABI_MISSING:" + outer)) return facts;
    if (allow_inner_allocator || segmented_capabilities) capture("___vmalloc_node_range" + suffix);
    if (segmented_capabilities & MSU_BOOT_SDK_SEGMENTED_OBSERVER) {
        try { facts.stack = describe_kernel_layout(btf, {{"pt_regs", {"sp"}}}); }
        catch (const std::runtime_error&) { /* This optional capability remains absent. */ }
    }
    if (allow_user_sampler) capture("vmalloc_user" + suffix);
    try {
        facts.environment = describe_kernel_layout(btf, {{"task_struct", {"seccomp.mode"}}, {"filename", {"name"}}},
            {"do_execveat_common", "avc_denied", "filldir64", "slow_avc_audit"});
        if (!capture("module_memfree") &&
            facts.function_errors.back().second == "MODULE_POLICY_LAYOUT_ABI_MISSING:module_memfree")
            capture("execmem_free");
    } catch (const std::runtime_error&) { /* The required channel facts remain intact. */ }
    return facts;
}

msu_boot_sdk_layout consume_boot_sdk_layout(const BootSdkLayoutFacts& facts, uint32_t page_bytes,
    bool allow_inner_allocator, bool allow_user_sampler, unsigned segmented_capabilities) {
    const auto& description = facts.layout;
    auto field = [&](const char* type, const char* name, uint32_t bytes) {
        const auto& value = fact_field(fact_owner(description, type), name);
        if ((value.bit_offset & 7) || (bytes && value.bit_size != bytes * 8))
            throw std::runtime_error("BOOT_SDK_LAYOUT_FIELD:" + value.name);
        return value.bit_offset / 8;
    };
    auto field_abi = [&](const char* name) { return fact_abi(facts.field_abis, name); };
    msu_boot_sdk_layout result{};
    result.version = 1; result.bytes = sizeof(result); result.page_bytes = page_bytes;
    result.task_bytes = fact_owner(description, "task_struct").size; result.cred_bytes = fact_owner(description, "cred").size;
    result.task_cred = field("task_struct", "cred", 8);
    result.cred_suid = field("cred", "suid", 4); result.cred_securebits = field("cred", "securebits", 4);
    result.cred_usage = field("cred", "usage", 0);
    result.cred_usage_size = fact_field(fact_owner(description, "cred"), "usage").bit_size / 8;
    const auto usage = result.cred_usage_size == 8 ? "s:8{0=i8:16777280;}" : "s:4{0=i4:16777248;}";
    if (field_abi("task_struct.cred") != "*scred" || field_abi("cred.securebits") != "i4:32" || field_abi("cred.usage") != usage)
        throw std::runtime_error("BOOT_SDK_CREDENTIAL_TYPES");
    for (const char* name : {"cred.suid", "cred.sgid", "cred.euid"})
        if (field_abi(name) != "s:4{0=i4:32;}") throw std::runtime_error("BOOT_SDK_CREDENTIAL_TYPES");
    if (field("cred", "sgid", 4) != result.cred_suid + 4 || field("cred", "euid", 4) != result.cred_suid + 8 ||
        result.cred_securebits != result.cred_suid + 24 ||
        (fact_field(fact_owner(description, "cred"), "usage").bit_size != 32 &&
         fact_field(fact_owner(description, "cred"), "usage").bit_size != 64) || field("pt_regs", "regs", 31 * 8))
        throw std::runtime_error("BOOT_SDK_CREDENTIAL_ABI");
    result.kprobe_bytes = fact_owner(description, "kprobe").size;
    result.kp_addr = field("kprobe", "addr", 8); result.kp_name = field("kprobe", "symbol_name", 8); result.kp_pre = field("kprobe", "pre_handler", 8);
    result.regs_pc = field("pt_regs", "pc", 8);
    if (!msu_boot_sdk_layout_valid(&result, page_bytes)) throw std::runtime_error("BOOT_SDK_LAYOUT_RANGE");
    auto abi = [&](const char* name) {
        for (const auto& error : facts.function_errors)
            if (error.first == name) throw std::runtime_error(error.second);
        return fact_abi(description.functions, name, &facts.environment.functions);
    };
    auto check = [&](const char* name, const char* expected) {
        if (abi(name) != expected) throw std::runtime_error(std::string("BOOT_SDK_ABI:") + name);
    };
    // Select by ABI presence, never by device name or release string.
    std::string allocator = "vmalloc";
    try { (void)abi(allocator.c_str()); }
    catch (const std::runtime_error& error) {
        if (std::string(error.what()) != "MODULE_POLICY_LAYOUT_ABI_MISSING:vmalloc") throw;
        allocator += "_noprof"; result.allocator_noprof = 1;
    }
    check(allocator.c_str(), "f*v(i8:64;)");
    const auto range_abi = "f*v(i8:64;i8:64;i8:64;i8:64;i4:32;s:8{0=i8:64;};i8:64;i4:16777248;*v;)";
    const std::string suffix = result.allocator_noprof ? "_noprof" : "";
    const auto outer = "__vmalloc_node_range" + suffix;
    const bool segmented_callable = (segmented_capabilities &
        (MSU_BOOT_SDK_SEGMENTED_OBSERVER | MSU_BOOT_SDK_SEGMENTED_CALLABLE)) ==
        (MSU_BOOT_SDK_SEGMENTED_OBSERVER | MSU_BOOT_SDK_SEGMENTED_CALLABLE);
    bool outer_missing = false;
    try { check(outer.c_str(), range_abi); }
    catch (const std::runtime_error& error) {
        if (!segmented_callable || std::string(error.what()) != "MODULE_POLICY_LAYOUT_ABI_MISSING:" + outer) throw;
        outer_missing = true;
    }
    // Vendor wrappers may inline into vmalloc and call a distinct inner range.
    // Only select that implementation when its complete native ABI is known.
    if (allow_inner_allocator && !outer_missing) {
        try {
            check(("___vmalloc_node_range" + suffix).c_str(), range_abi);
            result.allocator_noprof |= MSU_BOOT_SDK_ALLOCATOR_INNER;
        } catch (const std::runtime_error&) { /* Keep the qualified outer ABI. */ }
    }
    if ((segmented_capabilities & MSU_BOOT_SDK_SEGMENTED_OBSERVER) &&
        !(result.allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_INNER)) {
        try {
            check(("___vmalloc_node_range" + suffix).c_str(),
                "f*v(i8:64;i8:64;i4:32;i4:32;i4:32;i4:32;i8:64;i8:64;i4:32;s:8{0=i8:64;};i8:64;i4:16777248;*v;)");
            const auto& regs = fact_owner(facts.stack, "pt_regs");
            const auto& sp = fact_field(regs, "sp");
            if (regs.size != fact_owner(description, "pt_regs").size || sp.bit_offset != 31 * 64 ||
                sp.bit_size != 64 || result.regs_pc != 32 * 8)
                throw std::runtime_error("BOOT_SDK_ALLOCATOR_STACK_ABI");
            result.allocator_noprof |= MSU_BOOT_SDK_ALLOCATOR_SEGMENTED;
            if (outer_missing) result.allocator_noprof |= MSU_BOOT_SDK_ALLOCATOR_INNER;
        } catch (const std::runtime_error&) {
            if (outer_missing) throw;
            /* Keep the qualified outer ABI. */
        }
    }
    // User-page allocation bypasses the vendor fast return in __vmalloc_node.
    // A missing or changed ABI keeps old listeners and kernels on their existing path.
    if (allow_user_sampler) {
        try {
            check(("vmalloc_user" + suffix).c_str(), "f*v(i8:64;)");
            result.allocator_noprof |= MSU_BOOT_SDK_ALLOCATOR_USER_SAMPLE;
        } catch (const std::runtime_error&) { /* No unchecked function call. */ }
    }
    for (const char* name : {"copy_from_kernel_nofault", "copy_to_kernel_nofault",
                            "copy_from_user_nofault", "copy_to_user_nofault"})
        check(name, "fi8:16777280(*v;*v;i8:64;)");
    for (const char* name : {"set_memory_ro", "set_memory_rw", "set_memory_x", "set_memory_nx"})
        check(name, "fi4:16777248(i8:64;i4:16777248;)");
    check("vfree", "fv(*v;)"); check("get_random_bytes", "fv(*v;i8:64;)");
    check("prepare_creds", "f*scred()"); check("commit_creds", "fi4:16777248(*scred;)");
    check("get_task_cred", "f*scred(*stask_struct;)"); check("__put_cred", "fv(*scred;)");
    check("register_kprobe", "fi4:16777248(*skprobe;)"); check("unregister_kprobe", "fv(*skprobe;)");
    check("__arm64_sys_execve", "fi8:16777280(*spt_regs;)");
    // The pinned legacy environment supports this eight-argument AVC ABI.
    // Other ABIs retain the qualified channel without advertising installation.
    try {
        const auto& task = fact_owner(facts.environment, "task_struct");
        const auto& seccomp = fact_field(task, "seccomp.mode");
        const auto& filename = fact_field(fact_owner(facts.environment, "filename"), "name");
        if (task.size != result.task_bytes) return result;
        if ((seccomp.bit_offset & 7) || seccomp.bit_size != 32) return result;
        const auto seccomp_offset = seccomp.bit_offset / 8;
        if (filename.bit_offset || filename.bit_size != 64 ||
            !seccomp_offset || seccomp_offset > 32767) return result;
        check("do_execveat_common", "fi4:16777248(i4:16777248;*sfilename;suser_arg_ptr:16{0=i1:67108872;64=u:8{0=**i1:8;0=*i4:32;};};suser_arg_ptr:16{0=i1:67108872;64=u:8{0=**i1:8;0=*i4:32;};};i4:16777248;)");
        check("avc_denied", "fi4:16777248(i4:32;i4:32;i2:16;i4:32;i1:8;i1:8;i4:32;*sav_decision;)");
        check("filldir64", "fi1:67108872(*sdir_context;*i1:8;i4:16777248;i8:16777280;i8:64;i4:32;)");
        check("slow_avc_audit", "fi4:16777248(i4:32;i4:32;i2:16;i4:32;i4:32;i4:32;i4:16777248;*scommon_audit_data;)");
        try { check("module_memfree", "fv(*v;)"); }
        catch (const std::runtime_error& error) {
            if (std::string(error.what()) != "MODULE_POLICY_LAYOUT_ABI_MISSING:module_memfree") throw;
            check("execmem_free", "fv(*v;)"); result.execmem_free = 1;
        }
        result.seccomp = seccomp_offset;
        result.environment = 1;
    } catch (const std::runtime_error&) { result.environment = 0; }
    return result;
}

msu_boot_sdk_layout resolve_boot_sdk_layout(const std::vector<uint8_t>& btf, uint32_t page_bytes,
    bool allow_inner_allocator, bool allow_user_sampler, unsigned segmented_capabilities) {
    return consume_boot_sdk_layout(describe_boot_sdk_layout(btf, allow_inner_allocator,
        allow_user_sampler, segmented_capabilities), page_bytes,
        allow_inner_allocator, allow_user_sampler, segmented_capabilities);
}
