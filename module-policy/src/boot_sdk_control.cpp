// SPDX-License-Identifier: GPL-3.0-only
#include "../../jailbreak-providers/root-loader/boot_sdk_abi.h"
#include "boot_sdk_layout.h"
#include <cerrno>
#include <climits>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <fstream>
#include <array>
#include <stdexcept>
#include <string>
#include <sys/prctl.h>
#include <unistd.h>

int boot_sdk_control(int argc, char** argv) {
    static_assert(sizeof(msu_boot_sdk_request) == 88);
    if (argc != 2 || (strcmp(argv[1], "boot-sdk-info") && strcmp(argv[1], "boot-sdk-start")))
        throw std::runtime_error("BOOT_SDK_USAGE");
    if (getuid() || geteuid()) throw std::runtime_error("BOOT_SDK_ROOT_REQUIRED");
    std::string boot;
    std::ifstream stream("/proc/sys/kernel/random/boot_id");
    if (!(stream >> boot) || boot.size() != 36 || boot.find_first_not_of("0123456789abcdef-") != std::string::npos)
        throw std::runtime_error("BOOT_SDK_BOOT_ID");
    msu_boot_sdk_configuration configuration{};
    auto& request = configuration.reply;
    auto transact = [&](unsigned command) {
        request = {};
        request.version = MSU_BOOT_SDK_VERSION; request.command = command;
        request.status = INT_MIN;
        // No EINTR retry: a missing reply may follow successful initialization.
        if (prctl(4, static_cast<unsigned long>(MSU_BOOT_SDK_OP),
            static_cast<unsigned long>(MSU_BOOT_SDK_TAG_LO),
            static_cast<unsigned long>(MSU_BOOT_SDK_TAG_HI), &request))
            throw std::runtime_error("BOOT_SDK_TRANSPORT:" + std::to_string(errno));
        if (request.version != MSU_BOOT_SDK_VERSION || request.command != command ||
            request.status == INT_MIN || request.reserved || request.state > MSU_BOOT_SDK_FAILED ||
            (request.capabilities & ~7u) || request.key[48])
            throw std::runtime_error("BOOT_SDK_RESPONSE");
    };
    // Discover without mutating the listener. A READY gateway is never rebound.
    transact(MSU_BOOT_SDK_QUERY);
    if (!strcmp(argv[1], "boot-sdk-start") && request.state == MSU_BOOT_SDK_EMPTY && !request.status) {
        if (request.padding[0] == 1 && !request.padding[2]) {
            const bool environment = request.padding[1] == 1;
            const bool inner_allocator = request.padding[4] == 1;
            const bool user_sampler = request.padding[5] == 1;
            const unsigned segmented_allocator = request.padding[6] &
                (MSU_BOOT_SDK_SEGMENTED_OBSERVER | MSU_BOOT_SDK_SEGMENTED_CALLABLE);
            std::vector<uint8_t> btf;
            {
                const int descriptor = open("/sys/kernel/btf/vmlinux", O_RDONLY | O_CLOEXEC);
                if (descriptor < 0) {
                    const int error = errno;
                    const char* source = error == ENOENT ? "BOOT_SDK_BTF_UNAVAILABLE:" : "BOOT_SDK_BTF_OPEN:";
                    throw std::runtime_error(std::string(source) + std::to_string(error));
                }
                struct Input {
                    int descriptor;
                    ~Input() { close(descriptor); }
                } input{descriptor};
                std::array<uint8_t, 65536> chunk;
                for (;;) {
                    const ssize_t count = read(descriptor, chunk.data(), chunk.size());
                    if (count < 0) {
                        if (errno == EINTR) continue;
                        throw std::runtime_error("BOOT_SDK_BTF_READ:" + std::to_string(errno));
                    }
                    if (!count) break;
                    if (static_cast<size_t>(count) > 64 * 1024 * 1024 - btf.size())
                        throw std::runtime_error("BOOT_SDK_BTF_SIZE");
                    btf.insert(btf.end(), chunk.data(), chunk.data() + count);
                }
            }
            if (btf.empty()) throw std::runtime_error("BOOT_SDK_BTF_EMPTY");
            configuration.layout = resolve_boot_sdk_layout(btf, static_cast<uint32_t>(sysconf(_SC_PAGESIZE)), inner_allocator, user_sampler, segmented_allocator);
            if (!environment) configuration.layout.environment = 0;
            transact(MSU_BOOT_SDK_CONFIGURE);
            // A zero-hit inner sample leaves no resident state or allocation.
            // The outer ABI was also qualified; sample it once before bootstrap.
            if (request.status == -61 && request.padding[3] == MSU_BS_CONFIG_CAPTURE &&
                request.state == MSU_BOOT_SDK_EMPTY && !request.padding[2] &&
                (configuration.layout.allocator_noprof &
                    (MSU_BOOT_SDK_ALLOCATOR_INNER | MSU_BOOT_SDK_ALLOCATOR_SEGMENTED)) == MSU_BOOT_SDK_ALLOCATOR_INNER) {
                configuration.layout.allocator_noprof &= ~MSU_BOOT_SDK_ALLOCATOR_INNER;
                transact(MSU_BOOT_SDK_CONFIGURE);
            }
            if (request.status) {
                static const char* const stages[] = {"DYNAMIC_CONFIG", "CONFIG_LAYOUT", "CONFIG_ENVIRONMENT",
                    "CONFIG_ALLOCATOR", "CONFIG_IMAGE_READ", "CONFIG_IMAGE_HEADER", "CONFIG_KALLSYMS",
                    "CONFIG_CREDENTIAL", "CONFIG_ENV_SYMBOLS", "CONFIG_PROBE", "CONFIG_PROBE_ENTRY",
                    "CONFIG_ALLOCATE", "CONFIG_CAPTURE", "CONFIG_ARGUMENTS"};
                const unsigned stage = request.padding[3];
                const char* name = stage < sizeof(stages) / sizeof(stages[0]) ? stages[stage] : stages[0];
                throw std::runtime_error(std::string("BOOT_SDK_") + name + ":" + std::to_string(request.status));
            }
        }
        transact(MSU_BOOT_SDK_BOOTSTRAP);
    }
    const bool ready = !request.status && request.state == MSU_BOOT_SDK_READY;
    if (ready) {
        if (request.kernel_base < 0xffff000000000000ull || (request.capabilities & 3u) != 3u)
            throw std::runtime_error("BOOT_SDK_METADATA");
        for (unsigned i = 0; i < MSU_BOOT_SDK_KEY_BYTES; ++i) {
            const unsigned char c = request.key[i];
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')))
                throw std::runtime_error("BOOT_SDK_KEY_SHAPE");
        }
    }
    // The key is delivered on the authenticated Hook pipe, never argv or a log.
    printf("MAPLESU_BOOT_SDK:{\"v\":1,\"bootId\":\"%s\",\"state\":%u,\"error\":%d,"
           "\"capabilities\":%u,\"kernelBase\":\"0x%016llx\",\"key\":\"%s\"}\n",
           boot.c_str(), request.state, request.status, request.capabilities,
           request.kernel_base, ready ? request.key : "");
    volatile char* secret = request.key;
    for (unsigned i = 0; i < sizeof request.key; ++i) secret[i] = 0;
    return 0;
}
