#pragma once
#include <cerrno>
#include <cstring>
#include <string>
#include <vector>

namespace maplesu::module_task {
// Private same-image handoff. Only the signed Manager's module API supplies facts.
// The standalone Root owner accepts the same snapshot from its Root-only task peer.
inline constexpr const char* fact_names[] = {"KSU", "KSU_VER", "KSU_VER_CODE", "KSU_KERNEL_VER_CODE",
    "KSU_UAPI_VER", "KSU_RUNTIME_MODE", "MAPLESU_RUNTIME_MODE", "MAPLESU_KSU_COMPAT", "KSU_LATE_LOAD", "KSU_FACT_SOURCE"};
inline bool retired(const char* value) {
    return !std::strncmp(value, "KSU=", 4) || !std::strncmp(value, "KSU_", 4) ||
        !std::strncmp(value, "MAPLESU_RUNTIME_MODE=", 21) || !std::strncmp(value, "MAPLESU_KSU_COMPAT=", 19);
}
class Environment final {
public:
    int build(char* const inherited[], const std::vector<std::string>& facts) {
        values_.clear(); pointers_.clear();
        if (facts.size() < 8 || facts.size() > 10) return -EINVAL;
        std::string found[10];
        for (const auto& fact : facts) {
            const auto equals = fact.find('=');
            if (equals == std::string::npos || fact.size() > 256 || fact.find_first_of("\r\n\0", 0, 3) != std::string::npos)
                return -EINVAL;
            size_t index = 0;
            while (index < 10 && fact.compare(0, equals, fact_names[index])) ++index;
            if (index == 10 || !found[index].empty()) return -EINVAL;
            found[index] = fact.substr(equals + 1);
            if (found[index].empty()) return -EINVAL;
        }
        for (size_t i : {0, 1, 2, 3, 4, 6, 7}) if (found[i].empty()) return -EINVAL;
        if (found[0] != "true" || found[1].rfind("MapleSU ", 0) ||
            found[4] != "0" || found[7] != "1") return -EINVAL;
        // Only the SDK Root worker probes this exact read-only runtime ABI.
        // Legacy/hook facts retain their unknown-driver contract.
        if (found[9].empty() ? (found[3] != "0" && found[3] != "10940") : found[3] != "0")
            return -EINVAL;
        if (found[9] == "legacy") {
            if (found[6] != "unknown" || !found[5].empty() || !found[8].empty()) return -EINVAL;
        } else {
            if (!found[9].empty() && found[9] != "hook") return -EINVAL;
            const bool hotload = found[6] == "hotload";
            if ((found[9] == "hook" && !hotload) ||
                (hotload ? (found[5] != "late-load" || found[8] != "1") :
                    (found[6] != "boot" || found[5] != "lkm" || !found[8].empty()))) return -EINVAL;
        }
        for (char c : found[2]) if (c < '0' || c > '9') return -EINVAL;
        if (inherited) for (size_t i = 0; inherited[i]; ++i)
            if (!retired(inherited[i])) values_.emplace_back(inherited[i]);
        values_.insert(values_.end(), facts.begin(), facts.end());
        return 0;
    }
    char* const* data() {
        pointers_.clear();
        for (auto& value : values_) pointers_.push_back(value.data());
        pointers_.push_back(nullptr);
        return pointers_.data();
    }
private:
    std::vector<std::string> values_;
    std::vector<char*> pointers_;
};
} // namespace maplesu::module_task
