// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "../../jailbreak-providers/root-loader/boot_sdk_abi.h"
#include "../../runtime/ultra/policy/kernel_layout.h"
#include <cstdint>
#include <utility>
#include <vector>

// Complete facts from one target. Optional groups may be absent; required
// owners, fields and ABIs are never synthesized by the wire consumer.
struct BootSdkLayoutFacts {
    maplesu::module_policy::KernelLayoutDescription layout, stack, environment;
    std::vector<maplesu::module_policy::KernelFunctionAbi> field_abis;
    // Preserve errors from the BTF adapter until the corresponding ABI is used.
    std::vector<std::pair<std::string, std::string>> function_errors;
};
BootSdkLayoutFacts describe_boot_sdk_layout(const std::vector<uint8_t>& btf,
    bool allow_inner_allocator = false, bool allow_user_sampler = false, unsigned segmented_capabilities = 0);
msu_boot_sdk_layout consume_boot_sdk_layout(const BootSdkLayoutFacts& facts, uint32_t page_bytes,
    bool allow_inner_allocator = false, bool allow_user_sampler = false, unsigned segmented_capabilities = 0);
msu_boot_sdk_layout resolve_boot_sdk_layout(const std::vector<uint8_t>& btf, uint32_t page_bytes, bool allow_inner_allocator = false, bool allow_user_sampler = false, unsigned segmented_capabilities = 0);
