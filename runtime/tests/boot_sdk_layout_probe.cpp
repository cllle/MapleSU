// SPDX-License-Identifier: GPL-3.0-only
#include "../../module-policy/src/boot_sdk_layout.h"
#include <cassert>
#include <cstdio>
#include <fstream>
#include <iterator>
#include <stdexcept>
#include <cstdlib>
#include <cstring>
int main(int argc, char** argv) {
    assert(argc >= 2 && argc <= 6);
    assert(argc < 4 || !std::strcmp(argv[3], "0") || !std::strcmp(argv[3], "1"));
    assert(argc < 5 || !std::strcmp(argv[4], "0") || !std::strcmp(argv[4], "1"));
    assert(argc < 6 || !std::strcmp(argv[5], "0") || !std::strcmp(argv[5], "1") ||
        !std::strcmp(argv[5], "2") || !std::strcmp(argv[5], "3"));
    const bool allow_inner_allocator = argc >= 4 && !std::strcmp(argv[3], "1");
    const bool allow_user_sampler = argc >= 5 && !std::strcmp(argv[4], "1");
    const unsigned allow_segmented_allocator = argc == 6 ? static_cast<unsigned>(std::strtoul(argv[5], nullptr, 10)) : 0;
    const auto page = argc >= 3 ? static_cast<uint32_t>(std::strtoul(argv[2], nullptr, 10)) : 4096u;
    std::ifstream input(argv[1], std::ios::binary); assert(input);
    const std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(input)), {});
    try {
        const auto result = resolve_boot_sdk_layout(bytes, page, allow_inner_allocator, allow_user_sampler, allow_segmented_allocator);
        const auto facts = describe_boot_sdk_layout(bytes, allow_inner_allocator, allow_user_sampler, allow_segmented_allocator);
        const auto direct = consume_boot_sdk_layout(facts, page, allow_inner_allocator, allow_user_sampler, allow_segmented_allocator);
        assert(!std::memcmp(&result, &direct, sizeof(result)));
        assert(msu_boot_sdk_layout_valid(&result, page));
        printf("taskCred=%u credSuid=%u credUsageBytes=%u environment=%u allocatorNoprof=%u seccomp=%u\n",
            result.task_cred, result.cred_suid, result.cred_usage_size, result.environment, result.allocator_noprof,
            result.seccomp);
        printf("wire=");
        const auto* wire = reinterpret_cast<const unsigned char*>(&result);
        for (size_t i = 0; i < sizeof(result); ++i) printf("%02x", wire[i]);
        printf("\n");
    } catch (const std::runtime_error& error) {
        fprintf(stderr, "%s\n", error.what()); return 2;
    }
}
