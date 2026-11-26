#include "maplesu/compat/native_module_abi.h"
// MapleSU production Ultra bridge.
//
// This file is NOT the reference bridge. The reference bytes are kept unmodified in
// runtime/ultra/reference/native-lib.cpp for diff and provenance. Here the same 30 JNI
// entry names are implemented against the fixed prebuilt SDK archive, but every entry
// returns one structured JSON receipt instead of a human string:
//
//   {"schemaVersion":1,"backend":"maplesu_ultra","operation":"install_module","ok":false,
//    "sdkError":"ERR_MAPLESU_ENV_UNPREPARED","sdkErrorCode":-19916,
//    "reason":"...","data":{...}}
//
// Success requires KModErr::OK and, for module install, an accepting callback.
// No caller may infer success from the presence of
// "OK" inside any text: the reference UI did that (indexOf("OK")) and it mis-reads errors
// such as "HOOK..." - that heuristic is deliberately not reproduced here.
//
// 5.0.5 adds one identity entry on top of the reference set, getSdkBuildIdentity: it
// reports the bundled build identity that was compiled into these bytes (see the
// MAPLESU_* compile definitions below) and never calls the SDK. The 33 pre-existing
// declarations are unchanged.
//
// Ownership: every cJSON tree is created and destroyed inside this translation unit.
// No SDK C++ object crosses the JNI boundary; only UTF-8 JSON text does.

#include <jni.h>
#include <android/log.h>

#include <dlfcn.h>

#include <array>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <exception>
#include <map>
#include <memory>
#include <string>
#include <vector>
#include <sys/wait.h>

#include "kernel_module_kit_umbrella.h"
#include "maplesu_module_control.h"
#include "../../maplesu-sdk/sdk/include/maplesu_module_install_private.h"
#include "maplesu_parasite.h"
#include "../../maplesu-sdk/sdk/include/private_maple_runtime.h"
#include "../../maplesu-sdk/sdk/include/maplesu_root_command_private.h"
#include "../../maplesu-sdk/sdk/include/maplesu_root_status_private.h"

#include "cJSON.h"
#include "sdk_call_scope.h"
#include "module_environment_capture.h"
#include "sdk_error_receipt.h"
#include "su_authorization_receipt.h"
#include "../../maplesu-sdk/sdk/include/maplesu_status_query_private.h"
#include "../../maplesu-sdk/sdk/src/module/su_redirect_tu/su_uid_profile_private.h"
#include "oplusDeviceDetect.h"
#include "sysNodeHelper.h"
#include "urlEncodeUtils.h"
#include "webui_lifecycle.h"
#include "../../maplesu-sdk/sdk/src/maplesu_core/module_webui_host.h"
#include "image_credentials.h"
#include "manager_identity_claim.h"
#include "manager_owner_identity.h"
#include "manager_owner_root_probe.h"
#include "installed_helper_readback.h"
#include "../policy/module_policy_management.h"
#include "../policy/backend_publish.h"

// Bundled build identity. runtime/CMakeLists.txt supplies all seven macros as
// PRIVATE compile definitions from runtime/maplesu-sdk/SDK-IDENTITY.cmake (which
// scripts/import-ultra-sdk-505.ps1 derives from sdk-build.json), and falls back to the
// same values when that file is absent. The #ifndef block below only keeps this
// translation unit self-contained for a standalone/IDE compile; it never overrides a
// value that CMake provided, and it never invents an identity.
#ifndef MAPLESU_ULTRA_SDK_BUILD_ID
#define MAPLESU_ULTRA_SDK_BUILD_ID "unknown"
#endif
#ifndef MAPLESU_ULTRA_SDK_VERSION
#define MAPLESU_ULTRA_SDK_VERSION "unknown"
#endif
#ifndef MAPLESU_ULTRA_SDK_SHA256
#define MAPLESU_ULTRA_SDK_SHA256 "unknown"
#endif
#ifndef MAPLESU_ULTRA_HELPER_SHA256
#define MAPLESU_ULTRA_HELPER_SHA256 "unknown"
#endif
#ifndef MAPLESU_ULTRA_HELPER_BUILD_ID
#define MAPLESU_ULTRA_HELPER_BUILD_ID "unknown"
#endif
#ifndef MAPLESU_ULTRA_HELPER_SIZE
#define MAPLESU_ULTRA_HELPER_SIZE -1
#endif
#ifndef MAPLESU_ULTRA_KEY_SLOT_OFFSET
#define MAPLESU_ULTRA_KEY_SLOT_OFFSET -1
#endif
#ifndef MAPLESU_APP_VERSION
#define MAPLESU_APP_VERSION "unknown"
#endif

// OPPO/OnePlus bypass primitives are exported by the archive but have no public header,
// so they are declared here. They must stay at global scope: inside the anonymous
// namespace they would acquire internal linkage and never resolve to the archive's
// definitions.
KModErr oneplus_bypass_stage1_and_2(const char* root_key, void (*cb)(const char* info));
bool oneplus_bypass_is_work_normal(const char* root_key);

namespace {

constexpr int kSchemaVersion = 1;
constexpr int kBridgeVersion = 1;

// The bridge's own failures use codes outside the SDK's own range so a consumer can
// never confuse "the SDK said ERR_x" with "the bridge could not run the call".
constexpr int kBridgeException = -1001;
constexpr int kBridgeBadEnum = -1002;
constexpr int kBridgeAlloc = -1003;
constexpr int kBridgeNullArgument = -1004;
constexpr int kBridgeProcess = -1006;

std::string jstring_to_utf8(JNIEnv* env, jstring value) {
    if (env == nullptr || value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string module_id_to_utf8(JNIEnv* env, jstring value) {
    const std::string modified = jstring_to_utf8(env, value);
    std::string result;
    for (size_t index = 0; index < modified.size(); ++index) {
        const auto byte = static_cast<unsigned char>(modified[index]);
        const auto next = index + 1 < modified.size()
            ? static_cast<unsigned char>(modified[index + 1]) : 0;
        if (byte == 0xc0 && next == 0x80) return {}; // Module IDs exclude NUL.
        if (byte == 0xed && next >= 0xa0 && next <= 0xbf) {
            // JNI encodes a supplementary character as two UTF-16 surrogates.
            if (next >= 0xb0 || modified.size() - index < 6 ||
                static_cast<unsigned char>(modified[index + 3]) != 0xed) return {};
            const auto low = static_cast<unsigned char>(modified[index + 4]);
            if (low < 0xb0 || low > 0xbf) return {};
            const uint32_t high_bits = ((next & 15u) << 6) |
                (static_cast<unsigned char>(modified[index + 2]) & 63u);
            const uint32_t low_bits = ((low & 15u) << 6) |
                (static_cast<unsigned char>(modified[index + 5]) & 63u);
            const uint32_t point = 0x10000u + (high_bits << 10) + low_bits;
            result.push_back(static_cast<char>(0xf0u | (point >> 18)));
            result.push_back(static_cast<char>(0x80u | ((point >> 12) & 63u)));
            result.push_back(static_cast<char>(0x80u | ((point >> 6) & 63u)));
            result.push_back(static_cast<char>(0x80u | (point & 63u)));
            index += 5;
        } else {
            result.push_back(modified[index]);
        }
    }
    return result;
}

// The reference bridge URL-encodes descriptor and package strings because its WebUI
// consumes them as query parameters. That shape is preserved, and the receipt says so
// explicitly with "encoding":"url" so the Kotlin side can decode deterministically.
std::string url_encode_copy(const std::string& value) {
    if (value.empty()) return {};
    std::string input(value);
    std::vector<char> buffer(3 * input.size() + 4, 0);
    url_encode(input.data(), buffer.data());
    return std::string(buffer.data());
}

cJSON* begin(const char* operation) {
    cJSON* root = cJSON_CreateObject();
    if (root == nullptr) return nullptr;
    cJSON_AddNumberToObject(root, "schemaVersion", kSchemaVersion);
    cJSON_AddStringToObject(root, "backend", "maplesu_ultra");
    cJSON_AddNumberToObject(root, "bridgeVersion", kBridgeVersion);
    cJSON_AddStringToObject(root, "operation", operation);
    cJSON_AddBoolToObject(root, "ok", false);
    return root;
}

void mark_ok(cJSON* root) {
    cJSON_AddStringToObject(root, "sdkError", "OK");
    cJSON_AddNumberToObject(root, "sdkErrorCode", 0);
    cJSON_ReplaceItemInObject(root, "ok", cJSON_CreateTrue());
}

void mark_sdk_error(cJSON* root, KModErr err, const std::string& reason) {
    maplesu::ultra::write_sdk_error_receipt(root, err, reason);
}

void mark_bridge_error(cJSON* root, int code, const char* name, const std::string& detail) {
    cJSON_SetBoolValue(cJSON_GetObjectItemCaseSensitive(root, "ok"), false);
    // A late subprocess failure must replace, rather than duplicate, an SDK result.
    cJSON_DeleteItemFromObject(root, "sdkError");
    cJSON_DeleteItemFromObject(root, "sdkErrorCode");
    cJSON_DeleteItemFromObject(root, "reason");
    cJSON_AddStringToObject(root, "sdkError", name);
    cJSON_AddNumberToObject(root, "sdkErrorCode", code);
    if (!detail.empty()) cJSON_AddStringToObject(root, "reason", detail.c_str());
}

void mark_call_bridge_error(cJSON* root, const KModErr* sdk_error, int code,
                           const char* name, const std::string& detail) {
    if (!sdk_error || !is_failed(*sdk_error)) {
        mark_bridge_error(root, code, name, detail);
        return;
    }
    cJSON_SetBoolValue(cJSON_GetObjectItemCaseSensitive(root, "ok"), false);
    if (!cJSON_GetObjectItemCaseSensitive(root, "sdkErrorCode"))
        cJSON_AddNumberToObject(root, "sdkErrorCode", static_cast<double>(to_num(*sdk_error)));
    if (cJSON_GetObjectItemCaseSensitive(root, "bridgeError")) return;
    cJSON_AddStringToObject(root, "bridgeError", name);
    cJSON_AddNumberToObject(root, "bridgeErrorCode", code);
    if (!detail.empty()) cJSON_AddStringToObject(root, "bridgeReason", detail.c_str());
}

void attach_data(cJSON* root, cJSON* data) {
    if (data != nullptr) cJSON_AddItemToObject(root, "data", data);
}

jstring finish(JNIEnv* env, cJSON* root, const char* operation, const std::string& detail) {
    constexpr char allocation_receipt[] =
        "{\"schemaVersion\":1,\"backend\":\"maplesu_ultra\",\"bridgeVersion\":1,"
        "\"operation\":\"unknown\",\"ok\":false,"
        "\"sdkError\":\"MAPLESU_BRIDGE_ALLOC\",\"sdkErrorCode\":-1003}";
    if (root == nullptr) {
        // Allocation failed before any field existed: emit the smallest valid receipt.
        root = cJSON_CreateObject();
        if (root == nullptr) return env->NewStringUTF(allocation_receipt);
        cJSON_AddNumberToObject(root, "schemaVersion", kSchemaVersion);
        cJSON_AddStringToObject(root, "backend", "maplesu_ultra");
        cJSON_AddNumberToObject(root, "bridgeVersion", kBridgeVersion);
        cJSON_AddStringToObject(root, "operation", operation);
        cJSON_AddBoolToObject(root, "ok", false);
        // Allocation failure has its own code so it can never be read as a C++ exception.
        mark_bridge_error(root, kBridgeAlloc, "MAPLESU_BRIDGE_ALLOC", detail);
    }
    char* text = cJSON_PrintUnformatted(root);
    const auto* bytes = reinterpret_cast<const unsigned char*>(text == nullptr ? "{}" : text);
    const size_t size = std::strlen(reinterpret_cast<const char*>(bytes));
    auto* utf16 = size <= INT32_MAX ? static_cast<jchar*>(cJSON_malloc(size * sizeof(jchar))) : nullptr;
    jstring result;
    if (utf16 == nullptr) {
        result = env->NewStringUTF(allocation_receipt);
    } else {
        jsize written = 0;
        // cJSON emits standard UTF-8; NewStringUTF accepts JNI modified UTF-8.
        for (size_t index = 0; index < size;) {
            const unsigned char first = bytes[index];
            size_t count = first < 0x80 ? 1 : first >= 0xc2 && first <= 0xdf ? 2 :
                first >= 0xe0 && first <= 0xef ? 3 : first >= 0xf0 && first <= 0xf4 ? 4 : 0;
            uint32_t point = count == 1 ? first : first & (0x7f >> count);
            bool valid = count != 0 && count <= size - index;
            for (size_t offset = 1; valid && offset < count; ++offset) {
                const unsigned char next = bytes[index + offset];
                valid = (next & 0xc0) == 0x80;
                point = (point << 6) | (next & 0x3f);
            }
            valid = valid && (count < 2 || point >= 0x80) && (count < 3 || point >= 0x800) &&
                (count < 4 || point >= 0x10000) && point <= 0x10ffff &&
                !(point >= 0xd800 && point <= 0xdfff);
            if (!valid) {
                utf16[written++] = 0xfffd;
                ++index;
            } else {
                if (point <= 0xffff) utf16[written++] = static_cast<jchar>(point);
                else {
                    point -= 0x10000;
                    utf16[written++] = static_cast<jchar>(0xd800 | (point >> 10));
                    utf16[written++] = static_cast<jchar>(0xdc00 | (point & 0x3ff));
                }
                index += count;
            }
        }
        result = env->NewString(utf16, written);
        cJSON_free(utf16);
    }
    if (text != nullptr) cJSON_free(text);
    cJSON_Delete(root);
    return result;
}

// ModuleRunState is a closed enum. An unknown value must surface as an error rather than
// being silently reported as "NotRunning".
const char* module_state_name(maplesu::sdk::ModuleRunState state, int* code) {
    switch (state) {
        case maplesu::sdk::ModuleRunState::NotRunning:
            *code = 0;
            return "NotRunning";
        case maplesu::sdk::ModuleRunState::Running:
            *code = 1;
            return "Running";
        case maplesu::sdk::ModuleRunState::Abnormal:
            *code = 2;
            return "Abnormal";
        case maplesu::sdk::ModuleRunState::RemovedPendingReboot:
            *code = 3;
            return "RemovedPendingReboot";
    }
    *code = -1;
    return nullptr;
}

struct JsonDeleter {
    void operator()(cJSON* value) const { cJSON_Delete(value); }
};
using OwnedJson = std::unique_ptr<cJSON, JsonDeleter>;

bool attach_owned_json(cJSON* object, const char* field, cJSON* child);

cJSON* descriptor_to_json(const maplesu::sdk::module_desc& desc) {
    OwnedJson item(cJSON_CreateObject());
    if (!item ||
        !cJSON_AddStringToObject(item.get(), "name", url_encode_copy(desc.name).c_str()) ||
        !cJSON_AddStringToObject(item.get(), "ver", url_encode_copy(desc.version).c_str()) ||
        !cJSON_AddStringToObject(item.get(), "desc", url_encode_copy(desc.desc).c_str()) ||
        !cJSON_AddStringToObject(item.get(), "author", url_encode_copy(desc.author).c_str()) ||
        !cJSON_AddStringToObject(item.get(), "id32", url_encode_copy(desc.id32).c_str()) ||
        !cJSON_AddStringToObject(item.get(), "update_json", url_encode_copy(desc.update_json).c_str()) ||
        !cJSON_AddBoolToObject(item.get(), "web_ui", desc.web_ui)) return nullptr;
    char version[32] = {0};
    std::snprintf(version, sizeof(version), "%u.%u.%u", desc.min_sdk_ver.major,
                  desc.min_sdk_ver.minor, desc.min_sdk_ver.patch);
    if (!cJSON_AddStringToObject(item.get(), "min_sdk_ver", version) ||
        !cJSON_AddStringToObject(item.get(), "encoding", "url")) return nullptr;
    return item.release();
}

cJSON* record_to_json(const maplesu::sdk::module_record& record, bool* state_known) {
    OwnedJson item(cJSON_CreateObject());
    if (!item || !attach_owned_json(item.get(), "desc", descriptor_to_json(record.desc))) return nullptr;
    int code = -1;
    const char* name = module_state_name(record.state, &code);
    *state_known = name != nullptr;
    if (name != nullptr) {
        if (!cJSON_AddStringToObject(item.get(), "state", name) ||
            !cJSON_AddNumberToObject(item.get(), "state_code", code)) return nullptr;
    } else {
        if (!cJSON_AddNumberToObject(item.get(), "state_code",
                static_cast<double>(static_cast<uint32_t>(record.state)))) return nullptr;
    }
    return item.release();
}

cJSON* owner_u64(cJSON* object, const char* field, uint64_t value) {
    char text[17];
    std::snprintf(text, sizeof(text), "%016llx", static_cast<unsigned long long>(value));
    return cJSON_AddStringToObject(object, field, text);
}

cJSON* owner_error(cJSON* object, const char* field, int64_t value) {
    char text[32];
    std::snprintf(text, sizeof(text), "%lld", static_cast<long long>(value));
    return cJSON_AddStringToObject(object, field, text);
}

bool owner_root_command_succeeded(const maplesu::ManagerOwnerRootCommand& value, uint32_t command, uint32_t effect) {
    return value.attempted && value.reply_observed && value.completed && value.command == command &&
        !value.error && !value.status && !value.result && !value.handle && value.effects == effect;
}

bool attach_owned_json(cJSON* object, const char* field, cJSON* child) {
    if (child && cJSON_AddItemToObject(object, field, child)) return true;
    cJSON_Delete(child);
    return false;
}

cJSON* owner_root_command_json(const maplesu::ManagerOwnerRootCommand& value) {
    cJSON* object = cJSON_CreateObject();
    if (!object) return nullptr;
    if (!cJSON_AddBoolToObject(object, "attempted", value.attempted)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddBoolToObject(object, "replyObserved", value.reply_observed)) { cJSON_Delete(object); return nullptr; }
    if (!owner_error(object, "error", value.error)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "command", value.command)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "status", value.status)) { cJSON_Delete(object); return nullptr; }
    if (!owner_u64(object, "result", value.result)) { cJSON_Delete(object); return nullptr; }
    if (!owner_u64(object, "handle", value.handle)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "effects", value.effects)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddBoolToObject(object, "completed", value.completed)) { cJSON_Delete(object); return nullptr; }
    return object;
}

cJSON* root_command_result_json(const maplesu::sdk::RootCommandResult& value) {
    cJSON* object = cJSON_CreateObject();
    if (!object) return nullptr;
    if (!owner_error(object, "error", static_cast<int64_t>(value.error))) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "phase", static_cast<int>(value.phase))) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "systemError", value.system_error)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "workingDirectoryError", value.working_directory_error)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddBoolToObject(object, "shellCloseAttempted", value.shell_close_attempted)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "shellStatus", value.shell_status)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "shellError", value.shell_error)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "transportPhase", static_cast<int>(value.transport_phase))) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "transportError", value.transport_error)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "childStatus", value.child_status)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddBoolToObject(object, "childStatusValid", value.child_status_valid)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "vendorChildStatus", value.vendor_child_status)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddBoolToObject(object, "vendorChildStatusValid", value.vendor_child_status_valid)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddBoolToObject(object, "receiptComplete", value.receipt_complete)) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddNumberToObject(object, "rootProcess", value.root_process)) { cJSON_Delete(object); return nullptr; }
    const bool invocation_known = value.receipt_complete || value.root_report_received ||
        value.root_process > 0 || value.root_attempted ||
        value.phase == maplesu::sdk::RootCommandPhase::Validate ||
        value.phase == maplesu::sdk::RootCommandPhase::Fork;
    if (!cJSON_AddBoolToObject(object, "rootInvocationKnown", invocation_known)) { cJSON_Delete(object); return nullptr; }
    if (invocation_known && (!cJSON_AddBoolToObject(object, "rootAttempted", value.root_attempted) ||
        !cJSON_AddBoolToObject(object, "rootReturned", value.root_returned))) { cJSON_Delete(object); return nullptr; }
    if (!cJSON_AddBoolToObject(object, "rootReportReceived", value.root_report_received)) { cJSON_Delete(object); return nullptr; }
    if (value.root_attempted) {
        const auto& transition = value.root_transition;
        if (!cJSON_AddStringToObject(object, "rootPhase", transition.phase ? transition.phase : "")) { cJSON_Delete(object); return nullptr; }
        if (!owner_error(object, "rootError", static_cast<int64_t>(transition.error))) { cJSON_Delete(object); return nullptr; }
        if (!cJSON_AddNumberToObject(object, "rootSystemError", transition.system_error)) { cJSON_Delete(object); return nullptr; }
        if (!cJSON_AddBoolToObject(object, "ownerMode", transition.owner_mode)) { cJSON_Delete(object); return nullptr; }
        if (!cJSON_AddBoolToObject(object, "environmentCompleted", transition.environment_completed)) { cJSON_Delete(object); return nullptr; }
        if (!cJSON_AddNumberToObject(object, "threadsTotal", transition.threads_total) ||
            !cJSON_AddNumberToObject(object, "cpusetMoved", transition.cpuset_moved) ||
            !cJSON_AddNumberToObject(object, "stuneMoved", transition.stune_moved) ||
            !cJSON_AddNumberToObject(object, "cgroupError", transition.cgroup_error)) { cJSON_Delete(object); return nullptr; }
        maplesu::ManagerOwnerRootCommand credentials;
        credentials.attempted = transition.credentials_attempted;
        credentials.reply_observed = transition.credentials.command != 0;
        credentials.error = static_cast<int64_t>(transition.credentials_error);
        credentials.command = transition.credentials.command;
        credentials.status = transition.credentials.status;
        credentials.result = transition.credentials.result;
        credentials.handle = transition.credentials.handle;
        credentials.effects = transition.credentials.effects;
        credentials.completed = transition.credentials.completed;
        if (!attach_owned_json(object, "credentials", owner_root_command_json(credentials))) { cJSON_Delete(object); return nullptr; }
        if (value.root_report_received) {
            maplesu::ManagerOwnerRootCommand task;
            task.attempted = transition.task_state_attempted;
            task.reply_observed = transition.task_state.command != 0;
            task.error = static_cast<int64_t>(transition.task_state_error);
            task.command = transition.task_state.command;
            task.status = transition.task_state.status;
            task.result = transition.task_state.result;
            task.handle = transition.task_state.handle;
            task.effects = transition.task_state.effects;
            task.completed = transition.task_state.completed;
            if (!attach_owned_json(object, "taskState", owner_root_command_json(task))) { cJSON_Delete(object); return nullptr; }
        }
    }
    return object;
}

cJSON* owner_root_credentials_json(const maplesu::ManagerOwnerRootCredentials& value) {
    cJSON* object = cJSON_CreateObject();
    if (!object) return nullptr;
    cJSON_AddNumberToObject(object, "ruid", value.ruid);
    cJSON_AddNumberToObject(object, "euid", value.euid);
    cJSON_AddNumberToObject(object, "suid", value.suid);
    cJSON_AddNumberToObject(object, "rgid", value.rgid);
    cJSON_AddNumberToObject(object, "egid", value.egid);
    cJSON_AddNumberToObject(object, "sgid", value.sgid);
    owner_u64(object, "inheritable", value.inheritable);
    owner_u64(object, "permitted", value.permitted);
    owner_u64(object, "effective", value.effective);
    owner_u64(object, "bounding", value.bounding);
    owner_u64(object, "ambient", value.ambient);
    owner_u64(object, "capabilityMask", value.capability_mask);
    cJSON_AddNumberToObject(object, "capLastCap", value.cap_last_cap);
    cJSON_AddNumberToObject(object, "securebits", value.securebits);
    cJSON_AddNumberToObject(object, "uidError", value.uid_error);
    cJSON_AddNumberToObject(object, "gidError", value.gid_error);
    owner_error(object, "capsError", value.caps_error);
    cJSON_AddNumberToObject(object, "capLimitError", value.cap_limit_error);
    cJSON_AddNumberToObject(object, "securebitsError", value.securebits_error);
    cJSON_AddBoolToObject(object, "uidObserved", value.uid_observed);
    cJSON_AddBoolToObject(object, "gidObserved", value.gid_observed);
    cJSON_AddBoolToObject(object, "capsObserved", value.caps_observed);
    cJSON_AddBoolToObject(object, "ambientPresent", value.ambient_present);
    cJSON_AddBoolToObject(object, "capLimitObserved", value.cap_limit_observed);
    cJSON_AddBoolToObject(object, "securebitsObserved", value.securebits_observed);
    cJSON_AddBoolToObject(object, "matchesRoot", value.matches_root);
    return object;
}

cJSON* root_status_facts_json(const maplesu::sdk::RootProcessFacts& value) {
    OwnedJson object(cJSON_CreateObject());
    if (!object ||
        !cJSON_AddNumberToObject(object.get(), "ruid", value.ruid) ||
        !cJSON_AddNumberToObject(object.get(), "euid", value.euid) ||
        !cJSON_AddNumberToObject(object.get(), "suid", value.suid) ||
        !cJSON_AddNumberToObject(object.get(), "rgid", value.rgid) ||
        !cJSON_AddNumberToObject(object.get(), "egid", value.egid) ||
        !cJSON_AddNumberToObject(object.get(), "sgid", value.sgid) ||
        !cJSON_AddNumberToObject(object.get(), "fsuid", value.fsuid) ||
        !cJSON_AddNumberToObject(object.get(), "fsgid", value.fsgid) ||
        !owner_u64(object.get(), "inheritable", value.inheritable) ||
        !owner_u64(object.get(), "permitted", value.permitted) ||
        !owner_u64(object.get(), "effective", value.effective) ||
        !owner_u64(object.get(), "bounding", value.bounding) ||
        !owner_u64(object.get(), "ambient", value.ambient) ||
        !owner_u64(object.get(), "capabilityMask", value.capability_mask) ||
        !cJSON_AddNumberToObject(object.get(), "capLastCap", value.cap_last_cap) ||
        !cJSON_AddNumberToObject(object.get(), "uidError", value.uid_error) ||
        !cJSON_AddNumberToObject(object.get(), "gidError", value.gid_error) ||
        !cJSON_AddNumberToObject(object.get(), "fsuidError", value.fsuid_error) ||
        !cJSON_AddNumberToObject(object.get(), "fsgidError", value.fsgid_error) ||
        !owner_error(object.get(), "capsError", to_num(value.caps_error)) ||
        !cJSON_AddNumberToObject(object.get(), "capsErrno", value.caps_errno) ||
        !cJSON_AddNumberToObject(object.get(), "capLimitError", value.cap_limit_error) ||
        !cJSON_AddNumberToObject(object.get(), "securebitsError", value.securebits_error) ||
        !cJSON_AddNumberToObject(object.get(), "securebits", value.securebits) ||
        !cJSON_AddBoolToObject(object.get(), "uidObserved", value.uid_observed) ||
        !cJSON_AddBoolToObject(object.get(), "gidObserved", value.gid_observed) ||
        !cJSON_AddBoolToObject(object.get(), "fsidsObserved", value.fsids_observed) ||
        !cJSON_AddBoolToObject(object.get(), "capsObserved", value.caps_observed) ||
        !cJSON_AddBoolToObject(object.get(), "ambientPresent", value.ambient_present) ||
        !cJSON_AddBoolToObject(object.get(), "capLimitObserved", value.cap_limit_observed) ||
        !cJSON_AddBoolToObject(object.get(), "securebitsObserved", value.securebits_observed) ||
        !cJSON_AddBoolToObject(object.get(), "matchesRoot", value.matches_root)) return nullptr;
    return object.release();
}

cJSON* root_status_request_json(const kernel_module::RequestManagementReply& value,
                               KModErr error, bool attempted) {
    return owner_root_command_json({attempted, value.command != 0, to_num(error),
        value.command, value.status, value.result, value.handle, value.effects, value.completed});
}

cJSON* root_status_credentials_json(const maplesu::sdk::RootStatusCredentialFacts& value) {
    OwnedJson object(cJSON_CreateObject());
    if (!object ||
        !cJSON_AddBoolToObject(object.get(), "commandObserved", value.command_observed) ||
        !cJSON_AddNumberToObject(object.get(), "commandPhase", value.command_phase) ||
        !owner_error(object.get(), "commandError", to_num(value.command_error)) ||
        !cJSON_AddNumberToObject(object.get(), "commandErrno", value.command_errno) ||
        !cJSON_AddNumberToObject(object.get(), "process", value.process) ||
        !cJSON_AddBoolToObject(object.get(), "rootAttempted", value.root_attempted) ||
        !cJSON_AddBoolToObject(object.get(), "rootReturned", value.root_returned) ||
        !cJSON_AddBoolToObject(object.get(), "rootReportReceived", value.root_report_received) ||
        !cJSON_AddBoolToObject(object.get(), "credentialsAttempted", value.credentials_attempted) ||
        !owner_error(object.get(), "credentialsError", to_num(value.credentials_error)) ||
        !owner_error(object.get(), "transitionError", to_num(value.transition_error)) ||
        !cJSON_AddNumberToObject(object.get(), "transitionErrno", value.transition_errno) ||
        !cJSON_AddNumberToObject(object.get(), "transitionPhase", value.transition_phase) ||
        !cJSON_AddBoolToObject(object.get(), "ownerMode", value.owner_mode) ||
        !cJSON_AddBoolToObject(object.get(), "environmentCompleted", value.environment_completed) ||
        !attach_owned_json(object.get(), "credentials", root_status_request_json(
            value.credentials, value.credentials_error, value.credentials_attempted)) ||
        !owner_error(object.get(), "taskStateError", to_num(value.task_state_error)) ||
        !cJSON_AddBoolToObject(object.get(), "taskStateAttempted", value.task_state_attempted) ||
        !attach_owned_json(object.get(), "taskState", root_status_request_json(
            value.task_state, value.task_state_error, value.task_state_attempted))) return nullptr;
    return object.release();
}

cJSON* root_status_result_json(const maplesu::sdk::RootStatusResult& value) {
    OwnedJson object(cJSON_CreateObject());
    if (!object ||
        !cJSON_AddNumberToObject(object.get(), "diagnosticVersion", 1) ||
        !owner_error(object.get(), "error", to_num(value.error)) ||
        !owner_error(object.get(), "rootError", to_num(value.root_error)) ||
        !cJSON_AddNumberToObject(object.get(), "phase", static_cast<int>(value.phase)) ||
        !cJSON_AddNumberToObject(object.get(), "systemError", value.system_error) ||
        !cJSON_AddNumberToObject(object.get(), "rootErrno", value.root_errno) ||
        !cJSON_AddNumberToObject(object.get(), "transportError", value.transport_error) ||
        !cJSON_AddNumberToObject(object.get(), "waitError", value.wait_error) ||
        !cJSON_AddNumberToObject(object.get(), "process", value.process) ||
        !cJSON_AddNumberToObject(object.get(), "childPid", value.child_pid) ||
        !cJSON_AddNumberToObject(object.get(), "childStatus", value.child_status) ||
        !cJSON_AddBoolToObject(object.get(), "childStatusValid", value.child_status_valid) ||
        !cJSON_AddBoolToObject(object.get(), "receiptComplete", value.receipt_complete) ||
        !cJSON_AddBoolToObject(object.get(), "rootAttempted", value.root_attempted) ||
        !cJSON_AddBoolToObject(object.get(), "rootReturned", value.root_returned) ||
        !cJSON_AddBoolToObject(object.get(), "rootVerified", value.root_verified) ||
        !attach_owned_json(object.get(), "facts", root_status_facts_json(value.facts)) ||
        !attach_owned_json(object.get(), "rootCredentials", root_status_credentials_json(value.root_credentials))) return nullptr;
    return object.release();
}

class HelperReadbackDigest final {
public:
    explicit HelperReadbackDigest(JNIEnv* env) : env_(env) {}
    ~HelperReadbackDigest() {
        if (digest_ && reset_) {
            env_->CallVoidMethod(digest_, reset_);
            jni_ok();
        }
        if (buffer_) {
            const std::array<jbyte, maplesu::kInstalledHelperReadbackChunkBytes> empty{};
            env_->SetByteArrayRegion(buffer_, 0, static_cast<jsize>(empty.size()), empty.data());
            jni_ok();
            env_->DeleteLocalRef(buffer_);
        }
        if (digest_) env_->DeleteLocalRef(digest_);
        if (type_) env_->DeleteLocalRef(type_);
    }
    HelperReadbackDigest(const HelperReadbackDigest&) = delete;
    HelperReadbackDigest& operator=(const HelperReadbackDigest&) = delete;

    bool initialize() {
        type_ = env_->FindClass("java/security/MessageDigest");
        if (!jni_ok() || !type_) return false;
        const auto factory = env_->GetStaticMethodID(type_, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
        if (!jni_ok() || !factory) return false;
        update_ = env_->GetMethodID(type_, "update", "([BII)V");
        if (!jni_ok() || !update_) return false;
        finish_ = env_->GetMethodID(type_, "digest", "()[B");
        if (!jni_ok() || !finish_) return false;
        reset_ = env_->GetMethodID(type_, "reset", "()V");
        if (!jni_ok() || !reset_) return false;
        const auto name = env_->NewStringUTF("SHA-256");
        if (!jni_ok() || !name) return false;
        digest_ = env_->CallStaticObjectMethod(type_, factory, name);
        env_->DeleteLocalRef(name);
        if (!jni_ok() || !digest_) return false;
        buffer_ = env_->NewByteArray(static_cast<jsize>(maplesu::kInstalledHelperReadbackChunkBytes));
        return jni_ok() && buffer_;
    }

    static bool consume(void* context, const uint8_t* bytes, size_t size) {
        auto& self = *static_cast<HelperReadbackDigest*>(context);
        if (!bytes || !size || size > maplesu::kInstalledHelperReadbackChunkBytes) return false;
        self.env_->SetByteArrayRegion(self.buffer_, 0, static_cast<jsize>(size),
            reinterpret_cast<const jbyte*>(bytes));
        if (!self.jni_ok()) return false;
        self.env_->CallVoidMethod(self.digest_, self.update_, self.buffer_, 0, static_cast<jint>(size));
        return self.jni_ok();
    }

    bool finish(char (&hex)[65]) {
        const auto value = static_cast<jbyteArray>(env_->CallObjectMethod(digest_, finish_));
        if (!jni_ok() || !value) return false;
        const jsize count = env_->GetArrayLength(value);
        std::array<jbyte, 32> bytes{};
        bool ok = jni_ok() && count == static_cast<jsize>(bytes.size());
        if (ok) {
            env_->GetByteArrayRegion(value, 0, count, bytes.data());
            ok = jni_ok();
        }
        env_->DeleteLocalRef(value);
        if (!ok) return false;
        constexpr char digits[] = "0123456789abcdef";
        for (size_t i = 0; i < bytes.size(); ++i) {
            const auto byte = static_cast<uint8_t>(bytes[i]);
            hex[2 * i] = digits[byte >> 4];
            hex[2 * i + 1] = digits[byte & 15];
        }
        hex[64] = '\0';
        return true;
    }

private:
    bool jni_ok() {
        if (!env_->ExceptionCheck()) return true;
        env_->ExceptionClear();
        return false;
    }
    JNIEnv* env_;
    jclass type_ = nullptr;
    jobject digest_ = nullptr;
    jbyteArray buffer_ = nullptr;
    jmethodID update_ = nullptr, finish_ = nullptr, reset_ = nullptr;
};

struct HelperReadbackKey final {
    char bytes[49]{};
    ~HelperReadbackKey() {
        volatile char* value = bytes;
        for (size_t i = 0; i < sizeof(bytes); ++i) value[i] = 0;
    }
};

// Every entry funnels through this guard: a C++ exception must never cross JNI, and a
// caught exception must never be reported as success.
template <typename Body>
jstring guarded(JNIEnv* env, const char* operation, Body&& body, const KModErr* sdk_error = nullptr) {
    cJSON* root = begin(operation);
    try {
        maplesu::SdkCallScope scope(operation);
        if (scope.acquired()) {
            body(root);
            if (scope.process_error()) {
                if (!sdk_error) maplesu::ultra::retain_authorization_result_on_process_failure(root);
                mark_call_bridge_error(root, sdk_error, kBridgeProcess, "MAPLESU_SDK_PROCESS_ERROR",
                    "SDK subprocess setup or wait failed (errno=" + std::to_string(scope.process_error()) + ").");
            }
        }
        else mark_bridge_error(root, -1005, "MAPLESU_SDK_BUSY", "SDK admission timed out; this request was not submitted.");
    } catch (const std::exception& error) {
        if (root != nullptr) mark_call_bridge_error(root, sdk_error, kBridgeException, "MAPLESU_BRIDGE_EXCEPTION", error.what());
    } catch (...) {
        if (root != nullptr) mark_call_bridge_error(root, sdk_error, kBridgeException, "MAPLESU_BRIDGE_EXCEPTION", "unknown C++ exception");
    }
    return finish(env, root, operation, {});
}

KModErr authenticated_primary_error(cJSON* root, KModErr error) {
    if (is_failed(error)) return error;
    const auto* code = cJSON_GetObjectItemCaseSensitive(root, "sdkErrorCode");
    return cJSON_IsNumber(code) && code->valuedouble != 0
        ? static_cast<KModErr>(static_cast<int64_t>(code->valuedouble)) : error;
}

void authenticated_receipt_alloc(cJSON* root, KModErr error) {
    const auto primary = authenticated_primary_error(root, error);
    mark_call_bridge_error(root, &primary, kBridgeAlloc, "MAPLESU_BRIDGE_ALLOC",
        "SDK receipt allocation failed; refresh state before retrying.");
}

void mark_authenticated_ok(cJSON* root) {
    OwnedJson ok(cJSON_CreateTrue());
    if (!ok || !cJSON_AddStringToObject(root, "sdkError", "OK") ||
        !cJSON_AddNumberToObject(root, "sdkErrorCode", 0) ||
        !cJSON_ReplaceItemInObjectCaseSensitive(root, "ok", ok.get())) {
        authenticated_receipt_alloc(root, KModErr::OK);
        return;
    }
    (void)ok.release();
}

void restore_authenticated_mode(cJSON* root, KModErr restore, KModErr& sdk_error) {
    if (!is_failed(restore)) return;
    const bool operation_completed = cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(root, "ok"));
    if (operation_completed) {
        cJSON_DeleteItemFromObject(root, "sdkError");
        cJSON_DeleteItemFromObject(root, "sdkErrorCode");
        sdk_error = restore;
        mark_sdk_error(root, restore, {});
        if (!cJSON_GetObjectItemCaseSensitive(root, "operationCompleted") &&
            !cJSON_AddBoolToObject(root, "operationCompleted", true)) {
            authenticated_receipt_alloc(root, sdk_error);
            return;
        }
    }
    if (!cJSON_AddNumberToObject(root, "authModeRestoreErrorCode", static_cast<double>(to_num(restore))))
        authenticated_receipt_alloc(root, sdk_error);
}

template <typename Body>
jstring guarded_authenticated(JNIEnv* env, const char* operation, jstring root_key, Body&& body) {
    KModErr sdk_error = KModErr::OK;
    return guarded(env, operation, [&](cJSON* root) {
        if (!root) return;
        if (!cJSON_IsNumber(cJSON_GetObjectItemCaseSensitive(root, "schemaVersion")) ||
            !cJSON_IsString(cJSON_GetObjectItemCaseSensitive(root, "backend")) ||
            !cJSON_IsNumber(cJSON_GetObjectItemCaseSensitive(root, "bridgeVersion")) ||
            !cJSON_IsString(cJSON_GetObjectItemCaseSensitive(root, "operation")) ||
            !cJSON_IsFalse(cJSON_GetObjectItemCaseSensitive(root, "ok"))) {
            authenticated_receipt_alloc(root, sdk_error);
            return;
        }
        const auto key = jstring_to_utf8(env, root_key);
        if (env->ExceptionCheck()) return;
        maplesu::RequestAuthModeScope mode(root_key ? kernel_module::RequestAuthMode::LegacyKey :
            kernel_module::RequestAuthMode::ManagerOwner);
        sdk_error = mode.error();
        if (is_failed(sdk_error)) { mark_sdk_error(root, sdk_error, {}); return; }
        try {
            body(root, root_key ? key.c_str() : nullptr, sdk_error);
        } catch (...) {
            sdk_error = authenticated_primary_error(root, sdk_error);
            restore_authenticated_mode(root, mode.restore(), sdk_error);
            throw;
        }
        sdk_error = authenticated_primary_error(root, sdk_error);
        restore_authenticated_mode(root, mode.restore(), sdk_error);
    }, &sdk_error);
}

cJSON* control_to_json(const maplesu::ModuleControlState& state) {
    OwnedJson data(cJSON_CreateObject());
    if (!data || !cJSON_AddNumberToObject(data.get(), "abiVersion", state.abiVersion) ||
        !cJSON_AddBoolToObject(data.get(), "enabled", state.enabled != 0) ||
        !cJSON_AddNumberToObject(data.get(), "state_code", state.runtimeState)) return nullptr;
    int code = -1;
    const char* name = module_state_name(static_cast<maplesu::sdk::ModuleRunState>(state.runtimeState), &code);
    if (name && !cJSON_AddStringToObject(data.get(), "runtimeState", name)) return nullptr;
    if (!cJSON_AddBoolToObject(data.get(), "needsReboot", state.needsReboot != 0) ||
        !cJSON_AddBoolToObject(data.get(), "supportsEnableToggle", state.toggleSupported != 0) ||
        !cJSON_AddBoolToObject(data.get(), "supportsImmediateStop", false)) return nullptr;
    return data.release();
}

void control_error(cJSON* root, int64_t error) {
    const char* name = "MAPLESU_MODULE_CONTROL_IO";
    if (error == maplesu::MODULE_CONTROL_UNSUPPORTED) name = "MAPLESU_MODULE_CONTROL_UNSUPPORTED";
    else if (error == maplesu::MODULE_CONTROL_INVALID_ID) name = "MAPLESU_MODULE_CONTROL_INVALID_ID";
    else if (error == maplesu::MODULE_CONTROL_NOT_FOUND) name = "MAPLESU_MODULE_CONTROL_NOT_FOUND";
    else if (error == maplesu::MODULE_CONTROL_BUSY) name = "MAPLESU_MODULE_CONTROL_BUSY";
    else if (error == maplesu::MODULE_CONTROL_ENVIRONMENT) name = "MAPLESU_MODULE_CONTROL_ENVIRONMENT";
    mark_bridge_error(root, static_cast<int>(error), name, "Refresh module state before retrying.");
}

}  // namespace

// --- Environment and version (5) ------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_installEnvironment(
    JNIEnv* env, jclass, jstring rootKey, jstring insMode, jstring insExploitMethod) {
    const std::string mode_text = jstring_to_utf8(env, insMode);
    const std::string exploit = jstring_to_utf8(env, insExploitMethod);
    if (env->ExceptionCheck()) return nullptr;
    return guarded_authenticated(env, "install_maplesu_environment", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        maplesu::sdk::InstallMode mode;
        if (mode_text == "Boot") {
            mode = maplesu::sdk::InstallMode::Boot;
        } else if (mode_text == "HotLoad") {
            mode = maplesu::sdk::InstallMode::HotLoad;
        } else if (mode_text == "Refresh") {
            mode = maplesu::sdk::InstallMode::Refresh;
        } else {
            mark_bridge_error(root, kBridgeBadEnum, "MAPLESU_BRIDGE_BAD_INSTALL_MODE",
                              "InstallMode must be Boot, HotLoad or Refresh");
            return;
        }
        maplesu::sdk::InstallConfig config{};
        // The reference Manager's HotloadMethod enum has three values: MAGICA, SHELL and
        // CVE-2026-43499, and it passes their config strings straight through. The installed
        // SDK header only names None/Magica/CVE2026_43499, so SHELL means "no socket-level
        // exploit method" (the default) rather than an error. Rejecting SHELL would break
        // the reference's own hotload flow.
        if (exploit == "MAGICA") {
            config.exploit = maplesu::sdk::ExploitMethod::Magica;
        } else if (exploit == "CVE-2026-43499") {
            config.exploit = maplesu::sdk::ExploitMethod::CVE2026_43499;
        } else if (exploit.empty() || exploit == "NONE" || exploit == "SHELL") {
            config.exploit = maplesu::sdk::ExploitMethod::None;
        } else {
            mark_bridge_error(root, kBridgeBadEnum, "MAPLESU_BRIDGE_BAD_EXPLOIT_METHOD",
                              "ExploitMethod must be SHELL, NONE, MAGICA or CVE-2026-43499");
            return;
        }
        OwnedJson completed(cJSON_CreateTrue());
        if (!completed) { authenticated_receipt_alloc(root, err); return; }
        err = maplesu::sdk::install_maplesu_environment(key, mode, config);
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            return;
        }
        cJSON_AddItemToObjectCS(root, "operationCompleted", completed.release());
        OwnedJson data(cJSON_CreateObject());
        // The SDK documents that a Boot-mode install may restart the device on its own.
        if (!data || !cJSON_AddStringToObject(data.get(), "activationMode", mode_text.c_str()) ||
            !cJSON_AddBoolToObject(data.get(), "mayReboot", mode == maplesu::sdk::InstallMode::Boot) ||
            !attach_owned_json(root, "data", data.release())) {
            authenticated_receipt_alloc(root, err);
            return;
        }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_uninstallEnvironment(
    JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "uninstall_maplesu_environment", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        OwnedJson completed(cJSON_CreateTrue());
        if (!completed) { authenticated_receipt_alloc(root, err); return; }
        err = maplesu::sdk::uninstall_maplesu_environment(key);
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            return;
        }
        if (!attach_owned_json(root, "operationCompleted", completed.release())) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        // Removal completes on the next boot; the flag keeps the caller from claiming
        // the environment is gone while this boot still runs it.
        if (!data || !cJSON_AddBoolToObject(data.get(), "effectiveAfterReboot", true) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getEnvironmentState(
    JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "get_maplesu_environment_state", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        const maplesu::sdk::MapleSUEnvState state = maplesu::sdk::get_maplesu_environment_state(key);
        const char* name = nullptr;
        switch (state) {
            case maplesu::sdk::MapleSUEnvState::NotInstalled:
                name = "NotInstalled";
                break;
            case maplesu::sdk::MapleSUEnvState::Running:
                name = "Running";
                break;
            case maplesu::sdk::MapleSUEnvState::Fault:
                name = "Fault";
                break;
            case maplesu::sdk::MapleSUEnvState::Unknown:
                name = "Unknown";
                break;
        }
        if (name == nullptr) {
            // Never default an unknown enum to a healthy state.
            mark_bridge_error(root, kBridgeBadEnum, "MAPLESU_BRIDGE_UNKNOWN_ENV_STATE",
                "SDK returned an environment state outside the public enum");
            OwnedJson data(cJSON_CreateObject());
            if (!data || !cJSON_AddNumberToObject(data.get(), "rawState",
                    static_cast<double>(static_cast<uint32_t>(state))) ||
                !attach_owned_json(root, "data", data.release())) authenticated_receipt_alloc(root, err);
            return;
        }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddStringToObject(data.get(), "state", name) ||
            (state == maplesu::sdk::MapleSUEnvState::Fault &&
                !cJSON_AddStringToObject(data.get(), "stateReason", "current_boot_protection")) ||
            (state == maplesu::sdk::MapleSUEnvState::Unknown &&
                !cJSON_AddStringToObject(data.get(), "stateReason", "boot_state_unconfirmed")) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getInstalledEnvironmentVersion(
    JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "get_installed_maplesu_environment_version", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        maplesu::sdk::MapleSUSdkVersion version{};
        err = maplesu::sdk::get_installed_maplesu_environment_version(key, version);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        OwnedJson data(cJSON_CreateObject());
        char text[32] = {0};
        std::snprintf(text, sizeof(text), "%u.%u.%u", version.major, version.minor, version.patch);
        if (!data || !cJSON_AddStringToObject(data.get(), "version", text) ||
            !cJSON_AddNumberToObject(data.get(), "major", version.major) ||
            !cJSON_AddNumberToObject(data.get(), "minor", version.minor) ||
            !cJSON_AddNumberToObject(data.get(), "patch", version.patch) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getSdkVersion(JNIEnv* env, jclass) {
    return guarded(env, "get_sdk_version", [&](cJSON* root) {
        const maplesu::sdk::MapleSUSdkVersion version = maplesu::sdk::get_sdk_version();
        mark_ok(root);
        cJSON* data = cJSON_CreateObject();
        char text[32] = {0};
        std::snprintf(text, sizeof(text), "%u.%u.%u", version.major, version.minor, version.patch);
        cJSON_AddStringToObject(data, "version", text);
        cJSON_AddNumberToObject(data, "major", version.major);
        cJSON_AddNumberToObject(data, "minor", version.minor);
        cJSON_AddNumberToObject(data, "patch", version.patch);
        attach_data(root, data);
    });
}

// --- Bundled build identity (5.0.5, additive) ------------------------------------
// The identity of the bytes packaged with this build: SDK build id, upstream version,
// archive/helper digests, helper build id and the helper's key slot offset. Every value
// comes from compile definitions that runtime/CMakeLists.txt took from
// runtime/maplesu-sdk/SDK-IDENTITY.cmake, so it describes what was actually linked in.
// This entry never calls the SDK: that is exactly why it stays useful when the SDK
// itself cannot answer (wrong archive, missing helper, unknown build).
//
// identityKnown is deliberately narrow: a build id other than the "unknown" sentinel
// plus a 64-hex helper digest. A placeholder SDK-IDENTITY.cmake therefore reports
// ok=true (the call ran) with data.identityKnown=false (nothing was imported), and no
// caller may read ok as "identity present".
extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getSdkBuildIdentity(JNIEnv* env, jclass) {
    return guarded(env, "get_sdk_build_identity", [&](cJSON* root) {
        const std::string build_id = MAPLESU_ULTRA_SDK_BUILD_ID;
        const std::string helper_sha256 = MAPLESU_ULTRA_HELPER_SHA256;
        const bool identity_known = build_id != "unknown" && helper_sha256.size() == 64;
        mark_ok(root);
        cJSON* data = cJSON_CreateObject();
        cJSON_AddStringToObject(data, "buildId", build_id.c_str());
        cJSON_AddStringToObject(data, "sdkVersion", MAPLESU_ULTRA_SDK_VERSION);
        // Compatibility alias: historical source versions live in provenance evidence.
        cJSON_AddStringToObject(data, "upstreamVersion", MAPLESU_ULTRA_SDK_VERSION);
        cJSON_AddNumberToObject(data, "helperSize", static_cast<double>(MAPLESU_ULTRA_HELPER_SIZE));
        cJSON_AddStringToObject(data, "appVersion", MAPLESU_APP_VERSION);
        cJSON_AddStringToObject(data, "sdkSha256", MAPLESU_ULTRA_SDK_SHA256);
        cJSON_AddStringToObject(data, "helperSha256", helper_sha256.c_str());
        cJSON_AddStringToObject(data, "helperBuildId", MAPLESU_ULTRA_HELPER_BUILD_ID);
        cJSON_AddNumberToObject(data, "helperKeySlotOffset",
                                static_cast<double>(MAPLESU_ULTRA_KEY_SLOT_OFFSET));
        cJSON_AddNumberToObject(data, "bridgeVersion", kBridgeVersion);
        cJSON_AddBoolToObject(data, "identityKnown", identity_known);
        attach_data(root, data);
    });
}

// --- System status (1) ----------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getSystemStatusJson(JNIEnv* env, jclass) {
    return guarded(env, "get_system_status", [&](cJSON* root) {
        const int selinux = get_selinux_enforce_status();
        const int seccomp = get_seccomp_status();
        const bool adb = is_enable_adb();
        mark_ok(root);
        cJSON* data = cJSON_CreateObject();
        // Reference field names are preserved; booleans are added because the raw
        // integer admits -1 ("unreadable"), which is not the same as 0 ("permissive").
        cJSON_AddNumberToObject(data, "selinux", selinux);
        cJSON_AddNumberToObject(data, "seccomp", seccomp);
        cJSON_AddBoolToObject(data, "adb", adb);
        cJSON_AddBoolToObject(data, "selinux_enforcing", selinux == 1);
        cJSON_AddBoolToObject(data, "selinux_readable", selinux >= 0);
        attach_data(root, data);
    });
}

// --- Root command (2) -----------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_queryManagerOwnerIdentity(
    JNIEnv* env, jclass) {
    return guarded(env, "query_manager_owner_identity", [&](cJSON* root) {
        const auto identity = maplesu::query_manager_owner_identity();
        if (is_failed(identity.error)) {
            mark_sdk_error(root, identity.error, {});
            return;
        }
        char host[33];
        std::snprintf(host, sizeof(host), "%016llx%016llx",
            static_cast<unsigned long long>(identity.host.high),
            static_cast<unsigned long long>(identity.host.low));
        cJSON* data = cJSON_CreateObject();
        if (!data) {
            mark_bridge_error(root, kBridgeAlloc, "MAPLESU_BRIDGE_ALLOC", {});
            return;
        }
        cJSON_AddBoolToObject(data, "ownerBound", true);
        cJSON_AddNumberToObject(data, "protocolVersion", MAPLESU_OWNER_PROTOCOL_VERSION);
        cJSON_AddNumberToObject(data, "metadataVersion", MAPLESU_OWNER_METADATA_VERSION);
        cJSON_AddNumberToObject(data, "ownerSchema", MAPLESU_OWNER_SCHEMA);
        cJSON_AddNumberToObject(data, "fullUid", identity.full_uid);
        cJSON_AddNumberToObject(data, "epoch", identity.epoch);
        cJSON_AddStringToObject(data, "hostInstance", host);
        cJSON_AddBoolToObject(data, "liveRootVerified", false);
        attach_data(root, data);
        mark_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_probeManagerOwnerRoot(
    JNIEnv* env, jclass) {
    return guarded(env, "probe_manager_owner_root", [&](cJSON* root) {
        const auto observed = maplesu::probe_manager_owner_root();
        cJSON* data = cJSON_CreateObject();
        cJSON* root_command = owner_root_command_json(observed.root);
        cJSON* close_command = owner_root_command_json(observed.close);
        cJSON* credentials = owner_root_credentials_json(observed.credentials);
        if (!data || !root_command || !close_command || !credentials) {
            cJSON_Delete(data);
            cJSON_Delete(root_command);
            cJSON_Delete(close_command);
            cJSON_Delete(credentials);
            mark_bridge_error(root, kBridgeAlloc, "MAPLESU_BRIDGE_ALLOC", {});
            return;
        }
        cJSON_AddNumberToObject(data, "abiVersion", observed.abi_version);
        owner_error(data, "modeError", observed.mode_error);
        owner_error(data, "channelError", observed.channel_error);
        cJSON_AddBoolToObject(data, "modeSelected", observed.mode_selected);
        cJSON_AddBoolToObject(data, "ownerBound", observed.owner_bound);
        cJSON_AddNumberToObject(data, "fullUid", observed.owner_full_uid);
        cJSON_AddNumberToObject(data, "epoch", observed.owner_epoch);
        char host[33];
        std::snprintf(host, sizeof(host), "%016llx%016llx",
            static_cast<unsigned long long>(observed.host_high),
            static_cast<unsigned long long>(observed.host_low));
        cJSON_AddStringToObject(data, "hostInstance", host);
        owner_u64(data, "ownerCapabilities", observed.owner_capabilities);
        cJSON_AddItemToObject(data, "root", root_command);
        cJSON_AddItemToObject(data, "credentials", credentials);
        cJSON_AddItemToObject(data, "close", close_command);
        cJSON_AddBoolToObject(data, "rootCommitted", observed.root_committed);
        cJSON_AddBoolToObject(data, "rootVerified", observed.root_verified);
        cJSON_AddNumberToObject(data, "watchdogError", observed.watchdog_error);
        cJSON_AddBoolToObject(data, "watchdogArmed", observed.watchdog_armed);
        cJSON_AddNumberToObject(data, "childPid", observed.child_pid);
        cJSON_AddBoolToObject(data, "cleanupPending", observed.cleanup_pending);
        cJSON_AddBoolToObject(data, "cleanupScheduled", observed.cleanup_scheduled);
        cJSON_AddNumberToObject(data, "cleanupError", observed.cleanup_error);
        cJSON_AddNumberToObject(data, "probeAdmissionError", observed.probe_admission_error);
        cJSON_AddBoolToObject(data, "childException", observed.child_exception);
        cJSON_AddNumberToObject(data, "childIoError", observed.child_io_error);
        cJSON_AddBoolToObject(data, "rootCheckpointReceived", observed.root_checkpoint_received);
        cJSON_AddBoolToObject(data, "receiptComplete", observed.receipt_complete);
        cJSON_AddNumberToObject(data, "transportError", observed.transport_error);
        cJSON_AddNumberToObject(data, "waitError", observed.wait_error);
        cJSON_AddNumberToObject(data, "childStatus", observed.child_status);
        cJSON_AddBoolToObject(data, "childStatusValid", observed.child_status_valid);
        cJSON_AddBoolToObject(data, "timedOut", observed.timed_out);
        cJSON_AddBoolToObject(data, "terminationRequested", observed.termination_requested);
        cJSON_AddNumberToObject(data, "terminationError", observed.termination_error);
        char elapsed[32];
        std::snprintf(elapsed, sizeof(elapsed), "%llu", static_cast<unsigned long long>(observed.elapsed_us));
        cJSON_AddStringToObject(data, "elapsedMicros", elapsed);
        attach_data(root, data);
        const bool exited = observed.child_status_valid && WIFEXITED(observed.child_status) &&
            WEXITSTATUS(observed.child_status) == 0 && !observed.cleanup_pending && !observed.cleanup_scheduled;
        const bool cleanup_scheduled = !observed.child_status_valid && observed.cleanup_pending &&
            observed.cleanup_scheduled;
        if (observed.probe_admission_error) {
            mark_bridge_error(root, kBridgeProcess, "MAPLESU_OWNER_ROOT_IN_PROGRESS",
                "An Owner credential probe or its cleanup is already in progress.");
        } else if (observed.transport_error || observed.wait_error || observed.watchdog_error ||
            !observed.watchdog_armed || !observed.child_pid || observed.cleanup_error ||
            observed.timed_out || observed.termination_requested || observed.termination_error ||
            (!exited && !cleanup_scheduled) || observed.child_exception || observed.child_io_error ||
            !observed.root_checkpoint_received || !observed.receipt_complete) {
            mark_bridge_error(root, kBridgeProcess, "MAPLESU_BRIDGE_PROCESS", "Owner Root worker did not complete.");
        } else if (observed.mode_error || observed.channel_error || observed.root.error || observed.close.error) {
            const auto error = observed.mode_error ? observed.mode_error : observed.channel_error ? observed.channel_error :
                observed.root.error ? observed.root.error : observed.close.error;
            mark_sdk_error(root, static_cast<KModErr>(error), "Owner Root transaction failed; completed effects are retained.");
        } else if (!observed.owner_bound || !observed.root_committed || !observed.root_verified ||
            !owner_root_command_succeeded(observed.root, MAPLESU_REQUEST_CRED_ROOT, MAPLESU_EFFECT_COMMITTED) ||
            !owner_root_command_succeeded(observed.close, MAPLESU_REQUEST_SESSION_CLOSE, MAPLESU_EFFECT_SESSION_CLOSED)) {
            mark_sdk_error(root, KModErr::ERR_MODULE_TEST_CHANNEL, "Owner Root credential proof is incomplete.");
        } else mark_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_claimManagerIdentity(
    JNIEnv* env, jclass) {
    return guarded(env, "claim_manager_identity", [&](cJSON* root) {
        const std::string candidate = maplesu::manager_identity_candidate();
        cJSON* data = cJSON_CreateObject();
        if (!candidate.empty()) cJSON_AddStringToObject(data, "candidate", candidate.c_str());
        cJSON_AddBoolToObject(data, "liveRootVerified", false);
        attach_data(root, data);
        mark_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_recoverImageCredentials(
    JNIEnv* env, jclass, jbyteArray kernel) {
    return guarded(env, "recover_image_credentials", [&](cJSON* root) {
        const jsize size = kernel == nullptr ? 0 : env->GetArrayLength(kernel);
        if (size < 64 || size > 256 * 1024 * 1024) {
            mark_bridge_error(root, kBridgeNullArgument, "MAPLESU_IMAGE_KERNEL_INVALID", {});
            return;
        }
        std::vector<uint8_t> bytes(static_cast<size_t>(size));
        env->GetByteArrayRegion(kernel, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            mark_bridge_error(root, kBridgeException, "MAPLESU_IMAGE_KERNEL_READ_FAILED", {});
            return;
        }
        const auto keys = maplesu::image_credentials(bytes.data(), bytes.size());
        cJSON* data = cJSON_CreateObject();
        cJSON* candidates = cJSON_CreateArray();
        for (const auto& key : keys) cJSON_AddItemToArray(candidates, cJSON_CreateString(key.c_str()));
        cJSON_AddItemToObject(data, "credentials", candidates);
        cJSON_AddBoolToObject(data, "liveRootVerified", false);
        attach_data(root, data);
        mark_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_testRoot(JNIEnv* env, jclass, jstring rootKey) {
    const std::string key = jstring_to_utf8(env, rootKey);
    KModErr sdk_error = KModErr::OK;
    return guarded(env, "get_root_status_report", [&](cJSON* root) {
        if (!root) return;
        const char* raw = maplesu::sdk::get_root_status_report(key.c_str());
        const auto observed = maplesu::sdk::current_root_status_result();
        sdk_error = observed.error;
        if (is_failed(sdk_error)) mark_sdk_error(root, sdk_error, {});
        const std::string report = raw == nullptr ? std::string() : std::string(raw);
        OwnedJson data(root_status_result_json(observed));
        if (!data || !cJSON_AddStringToObject(data.get(), "report", url_encode_copy(report).c_str()) ||
            !cJSON_AddBoolToObject(data.get(), "reportAvailable", raw != nullptr) ||
            !cJSON_AddBoolToObject(data.get(), "reportEmpty", report.empty()) ||
            !cJSON_AddStringToObject(data.get(), "encoding", "url") ||
            !cJSON_AddItemToObject(root, "data", data.get())) {
            authenticated_receipt_alloc(root, sdk_error);
            return;
        }
        (void)data.release();
        if (!raw) {
            mark_call_bridge_error(root, &sdk_error, kBridgeNullArgument, "MAPLESU_BRIDGE_NULL_REPORT",
                "get_root_status_report returned null");
        } else if (is_failed(sdk_error)) return;
        else if (!observed.root_verified) {
            mark_sdk_error(root, KModErr::OK, "Root report is available; credential proof is incomplete.");
        } else mark_authenticated_ok(root);
    }, &sdk_error);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_runRootCmd(
    JNIEnv* env, jclass, jstring rootKey, jstring cmd) {
    return guarded_authenticated(env, "run_root_cmd", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        const auto command = jstring_to_utf8(env, cmd);
        if (env->ExceptionCheck()) return;
        OwnedJson completed(cJSON_CreateTrue());
        if (!completed) {
            authenticated_receipt_alloc(root, err);
            return;
        }
        std::string output;
        err = maplesu::sdk::run_root_cmd(key, command.c_str(), output);
        const auto command_result = maplesu::sdk::current_root_command_result();
        if (is_failed(err)) mark_sdk_error(root, err, {});
        else {
            // The node is reserved before submission; the constant key allocates nothing.
            cJSON_AddItemToObjectCS(root, "operationCompleted", completed.release());
        }
        const auto encoded_output = url_encode_copy(output);
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddStringToObject(data.get(), "result", encoded_output.c_str()) ||
            !cJSON_AddStringToObject(data.get(), "encoding", "url") ||
            !cJSON_AddNumberToObject(data.get(), "resultBytes", static_cast<double>(output.size())) ||
            !attach_owned_json(data.get(), "rootCommand", root_command_result_json(command_result)) ||
            !attach_owned_json(root, "data", data.release())) {
            authenticated_receipt_alloc(root, err);
            return;
        }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_readInstalledHelper(
    JNIEnv* env, jclass, jstring rootKey, jstring helperPath, jlong keySlotOffset, jint timeoutMillis) {
    KModErr sdk_error = KModErr::OK;
    return guarded(env, "read_installed_helper", [&](cJSON* root) {
        cJSON* ok = cJSON_GetObjectItemCaseSensitive(root, "ok");
        if (!cJSON_IsNumber(cJSON_GetObjectItemCaseSensitive(root, "schemaVersion")) ||
            !cJSON_IsString(cJSON_GetObjectItemCaseSensitive(root, "backend")) ||
            !cJSON_IsNumber(cJSON_GetObjectItemCaseSensitive(root, "bridgeVersion")) ||
            !cJSON_IsString(cJSON_GetObjectItemCaseSensitive(root, "operation")) || !cJSON_IsFalse(ok)) {
            mark_call_bridge_error(root, &sdk_error, kBridgeAlloc, "MAPLESU_BRIDGE_ALLOC", {});
            return;
        }
        if (timeoutMillis <= 0 || timeoutMillis > 5000 || (!helperPath && rootKey) || (helperPath && (env->GetStringLength(helperPath) != 27 ||
            env->GetStringUTFLength(helperPath) != 27)) ||
            (rootKey && (env->GetStringLength(rootKey) != 48 || env->GetStringUTFLength(rootKey) != 48))) {
            mark_bridge_error(root, kBridgeNullArgument, "MAPLESU_HELPER_READBACK_ARGUMENT", {});
            return;
        }
        HelperReadbackKey key;
        if (rootKey) env->GetStringUTFRegion(rootKey, 0, 48, key.bytes);
        const auto path = helperPath ? jstring_to_utf8(env, helperPath) : std::string{};
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            mark_bridge_error(root, kBridgeException, "MAPLESU_HELPER_READBACK_ARGUMENT", {});
            return;
        }
        HelperReadbackDigest digest(env);
        if (!digest.initialize()) {
            mark_bridge_error(root, kBridgeException, "MAPLESU_HELPER_DIGEST_FAILED", {});
            return;
        }
        const auto observed = maplesu::read_installed_helper(rootKey ? key.bytes : nullptr,
            helperPath ? path.c_str() : nullptr, static_cast<int64_t>(keySlotOffset), HelperReadbackDigest::consume, &digest,
            static_cast<uint32_t>(timeoutMillis));
        const int64_t error = observed.mode_error ? observed.mode_error : observed.channel_error ? observed.channel_error :
            observed.sdk_error ? observed.sdk_error : observed.root.error ? observed.root.error : observed.close.error;
        sdk_error = static_cast<KModErr>(error);
        char sha[65]{};
        const bool completed = observed.complete && digest.finish(sha);
        const char* failure_stage = maplesu::installed_helper_readback_failure_stage(observed, completed);
        const std::string failure_reason = std::string("Installed helper readback: ") + failure_stage +
            " (errno=" + std::to_string(observed.system_error) + ").";
        const auto allocation_failed = [&] {
            mark_call_bridge_error(root, &sdk_error, kBridgeAlloc, "MAPLESU_BRIDGE_ALLOC", {});
        };
        cJSON* data = cJSON_CreateObject();
        if (!attach_owned_json(root, "data", data)) {
            allocation_failed();
            return;
        }
        // Keep completed command facts attached if a later receipt field runs out of memory.
        if (!attach_owned_json(data, "root", owner_root_command_json(observed.root)) ||
            !attach_owned_json(data, "close", owner_root_command_json(observed.close)) ||
            (observed.transition_observed &&
                !attach_owned_json(data, "taskState", owner_root_command_json(observed.task_state))) ||
            (observed.namespace_requested &&
                !attach_owned_json(data, "deployment", owner_root_command_json(observed.deployment)))) {
            allocation_failed();
            return;
        }
        if (error) {
            const auto name = maplesu::ultra::sdk_error_name(sdk_error);
            if (!cJSON_AddStringToObject(root, "sdkError", name.c_str()) ||
                !cJSON_AddNumberToObject(root, "sdkErrorCode", static_cast<double>(error)) ||
                !cJSON_AddStringToObject(root, "reason", failure_reason.c_str())) {
                allocation_failed();
                return;
            }
        }
        if (!cJSON_AddNumberToObject(data, "abiVersion", observed.abi_version) ||
            !cJSON_AddBoolToObject(data, "complete", completed) ||
            !cJSON_AddStringToObject(data, "failureStage", failure_stage) ||
            !cJSON_AddNumberToObject(data, "elapsedUs", static_cast<double>(observed.elapsed_us)) ||
            !cJSON_AddNumberToObject(data, "completionUs", static_cast<double>(observed.completion_us)) ||
            !cJSON_AddNumberToObject(data, "timeoutMillis", timeoutMillis) ||
            !cJSON_AddNumberToObject(data, "admissionUs", static_cast<double>(observed.admission_us)) ||
            !cJSON_AddNumberToObject(data, "forkUs", static_cast<double>(observed.fork_us)) ||
            !cJSON_AddNumberToObject(data, "authenticationUs", static_cast<double>(observed.authentication_us)) ||
            !cJSON_AddNumberToObject(data, "sourceUs", static_cast<double>(observed.source_us)) ||
            !cJSON_AddNumberToObject(data, "receiveUs", static_cast<double>(observed.receive_us)) ||
            !cJSON_AddNumberToObject(data, "waitUs", static_cast<double>(observed.wait_us)) ||
            !cJSON_AddBoolToObject(data, "digestAttempted", observed.complete) ||
            !cJSON_AddBoolToObject(data, "digestComplete", completed) ||
            !cJSON_AddBoolToObject(data, "rootVerified", observed.root_verified) ||
            !cJSON_AddBoolToObject(data, "credentialsManaged", observed.credentials_managed) ||
            !cJSON_AddBoolToObject(data, "ownerMode", observed.owner_mode) ||
            !cJSON_AddBoolToObject(data, "namespaceRequested", observed.namespace_requested) ||
            !cJSON_AddBoolToObject(data, "namespaceResolved", observed.namespace_resolved) ||
            !cJSON_AddNumberToObject(data, "ownerFullUid", observed.owner_full_uid) ||
            !cJSON_AddNumberToObject(data, "ownerEpoch", observed.owner_epoch) ||
            !owner_u64(data, "ownerHostLow", observed.host_low) ||
            !owner_u64(data, "ownerHostHigh", observed.host_high) ||
            !cJSON_AddStringToObject(data, "path", observed.helper_path) ||
            !cJSON_AddNumberToObject(data, "size", static_cast<double>(observed.actual_size)) ||
            !cJSON_AddNumberToObject(data, "bytesRead", static_cast<double>(observed.received_bytes)) ||
            !cJSON_AddNumberToObject(data, "sourceBytes", static_cast<double>(observed.source_bytes)) ||
            !cJSON_AddNumberToObject(data, "streamBytes", static_cast<double>(observed.stream_bytes)) ||
            !cJSON_AddBoolToObject(data, "readAttempted", observed.read_attempted) ||
            !cJSON_AddBoolToObject(data, "headerReceived", observed.header_received) ||
            !cJSON_AddBoolToObject(data, "producerEndReceived", observed.producer_end_received) ||
            !cJSON_AddBoolToObject(data, "sourceEof", observed.source_eof) ||
            !cJSON_AddBoolToObject(data, "metadataStable", observed.metadata_stable) ||
            !cJSON_AddBoolToObject(data, "pathStable", observed.path_stable) ||
            !cJSON_AddBoolToObject(data, "rootCheckpointReceived", observed.root_checkpoint_received) ||
            !cJSON_AddBoolToObject(data, "receiptComplete", observed.receipt_complete) ||
            !cJSON_AddBoolToObject(data, "streamEof", observed.stream_eof) ||
            !cJSON_AddBoolToObject(data, "uidObserved", observed.uid_observed) ||
            !cJSON_AddNumberToObject(data, "ruid", observed.ruid) ||
            !cJSON_AddNumberToObject(data, "euid", observed.euid) ||
            !cJSON_AddNumberToObject(data, "uidError", observed.uid_error) ||
            !cJSON_AddNumberToObject(data, "normalizedOffset", static_cast<double>(observed.normalized_offset)) ||
            !cJSON_AddBoolToObject(data, "taskStateKnown", observed.transition_observed) ||
            !owner_error(data, "modeError", observed.mode_error) ||
            !owner_error(data, "channelError", observed.channel_error) ||
            !owner_error(data, "sdkError", observed.sdk_error) ||
            !owner_error(data, "credentialsError", observed.credentials_error) ||
            !cJSON_AddStringToObject(data, "rootPhase", observed.root_phase) ||
            !cJSON_AddNumberToObject(data, "rootSystemError", observed.root_system_error) ||
            !cJSON_AddBoolToObject(data, "legacyEnvironmentCompleted", observed.legacy_environment_completed) ||
            !cJSON_AddNumberToObject(data, "producerError", observed.producer_error) ||
            !cJSON_AddNumberToObject(data, "sinkError", observed.sink_error) ||
            !cJSON_AddNumberToObject(data, "transportError", observed.transport_error) ||
            !cJSON_AddNumberToObject(data, "systemError", observed.system_error) ||
            !cJSON_AddNumberToObject(data, "waitError", observed.wait_error) ||
            !cJSON_AddNumberToObject(data, "admissionError", observed.admission_error) ||
            !cJSON_AddNumberToObject(data, "childPid", observed.child_pid) ||
            !cJSON_AddBoolToObject(data, "watchdogArmed", observed.watchdog_armed) ||
            !cJSON_AddNumberToObject(data, "watchdogError", observed.watchdog_error) ||
            !cJSON_AddNumberToObject(data, "childIoError", observed.child_io_error) ||
            !cJSON_AddBoolToObject(data, "childException", observed.child_exception) ||
            !cJSON_AddBoolToObject(data, "terminationRequested", observed.termination_requested) ||
            !cJSON_AddNumberToObject(data, "terminationError", observed.termination_error) ||
            !cJSON_AddBoolToObject(data, "timedOut", observed.timed_out) ||
            !cJSON_AddBoolToObject(data, "cleanupPending", observed.cleanup_pending) ||
            !cJSON_AddBoolToObject(data, "cleanupScheduled", observed.cleanup_scheduled) ||
            !cJSON_AddNumberToObject(data, "cleanupError", observed.cleanup_error) ||
            !cJSON_AddBoolToObject(data, "childStatusValid", observed.child_status_valid) ||
            !cJSON_AddNumberToObject(data, "childStatus", observed.child_status) ||
            (completed && !cJSON_AddStringToObject(data, "sha256", sha))) {
            allocation_failed();
            return;
        }
        if (completed && !error) {
            if (!cJSON_AddStringToObject(root, "sdkError", "OK") ||
                !cJSON_AddNumberToObject(root, "sdkErrorCode", 0)) {
                allocation_failed();
                return;
            }
            cJSON_SetBoolValue(ok, true);
        } else if (!error) mark_bridge_error(root, kBridgeProcess, "MAPLESU_HELPER_READBACK_INCOMPLETE", failure_reason);
    }, &sdk_error);
}

// --- Application authorization --------------------------------------------------
// These are SDK touchpoints, not MapleSU's authorization authority. The Manager keeps
// DirectPolicy/ManagerAuthorizations as the only decision maker; these entries exist so
// the 30-entry contract is complete and so a future sync can be reasoned about.

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_addSuAuth(
    JNIEnv* env, jclass, jstring rootKey, jstring appPackageName) {
    const std::string package = jstring_to_utf8(env, appPackageName);
    if (env->ExceptionCheck()) return nullptr;
    return guarded_authenticated(env, "add_su_auth_list", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        maplesu::su_auth::ResultV1 result(maplesu::su_auth::Operation::Add);
        err = maplesu::su_auth::add(key, package.c_str(), result);
        cJSON* data = maplesu::ultra::write_authorization_result(root, result);
        if (!data) { authenticated_receipt_alloc(root, err); return; }
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            return;
        }
        if (!cJSON_AddStringToObject(data, "package", url_encode_copy(package).c_str())) { authenticated_receipt_alloc(root, err); return; }
        if (!cJSON_AddStringToObject(data, "encoding", "url")) { authenticated_receipt_alloc(root, err); return; }
        if (!cJSON_AddBoolToObject(data, "authority", false)) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

// Only the environment-owned root boot service uses this entry. A null package
// selects the repaired SDK's refresh-only transaction inside its existing root
// fork; unlike addSuAuth, it neither creates nor persists any package grant.
extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_refreshSuAuthList(JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "refresh_su_auth_list", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        maplesu::su_auth::ResultV1 result(maplesu::su_auth::Operation::Refresh);
        err = getuid() == 0 ? maplesu::su_auth::add(key, nullptr, result)
            : maplesu::su_auth::fail(result, KModErr::ERR_MODULE_NO_ROOT, maplesu::su_auth::Phase::EntryRoot);
        auto* data = maplesu::ultra::write_authorization_result(root, result);
        if (!data) { authenticated_receipt_alloc(root, err); return; }
        if (is_failed(err)) mark_sdk_error(root, err, {});
        else mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_removeSuAuth(
    JNIEnv* env, jclass, jstring rootKey, jstring modUuid) {
    const std::string uuid = jstring_to_utf8(env, modUuid);
    if (env->ExceptionCheck()) return nullptr;
    return guarded_authenticated(env, "remove_su_auth_list", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        maplesu::su_auth::ResultV1 result(maplesu::su_auth::Operation::Remove);
        err = maplesu::su_auth::remove(key, uuid.c_str(), result);
        cJSON* data = maplesu::ultra::write_authorization_result(root, result);
        if (!data) { authenticated_receipt_alloc(root, err); return; }
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            return;
        }
        if (!cJSON_AddStringToObject(data, "removed", url_encode_copy(uuid).c_str())) { authenticated_receipt_alloc(root, err); return; }
        if (!cJSON_AddBoolToObject(data, "authority", false)) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getSuAuthList(JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "get_su_auth_list", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        std::vector<maplesu::sdk::su_auth_item> items;
        err = maplesu::sdk::get_su_auth_list(key, items);
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            return;
        }
        OwnedJson data(cJSON_CreateObject());
        OwnedJson packages(cJSON_CreateArray());
        if (!data || !packages) { authenticated_receipt_alloc(root, err); return; }
        for (const auto& item : items) {
            OwnedJson package(cJSON_CreateString(url_encode_copy(item.app_package_name).c_str()));
            if (!package || !cJSON_AddItemToArray(packages.get(), package.get())) {
                authenticated_receipt_alloc(root, err); return;
            }
            (void)package.release();
        }
        if (!attach_owned_json(data.get(), "packages", packages.release()) ||
            !cJSON_AddNumberToObject(data.get(), "count", static_cast<double>(items.size())) ||
            !cJSON_AddStringToObject(data.get(), "encoding", "url") ||
            !cJSON_AddBoolToObject(data.get(), "authority", false) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_setSuUidProfile(
    JNIEnv* env, jclass, jstring rootKey, jint uid, jstring packageName, jboolean allow) {
    const auto package = jstring_to_utf8(env, packageName);
    if (env->ExceptionCheck()) return nullptr;
    const bool granted = allow == JNI_TRUE;
    return guarded_authenticated(env, "set_su_uid_profile", rootKey,
        [&](cJSON* root, const char* key, KModErr& error) {
        maplesu::su_auth::ResultV1 result(granted ? maplesu::su_auth::Operation::Add : maplesu::su_auth::Operation::Remove);
        error = maplesu::su_auth::set_uid_profile(
            key, static_cast<uint32_t>(uid), package.c_str(), granted, result);
        auto* data = maplesu::ultra::write_authorization_result(root, result);
        if (!data || !cJSON_AddNumberToObject(data, "uid", uid) ||
            !cJSON_AddStringToObject(data, "package", url_encode_copy(package).c_str()) ||
            !cJSON_AddBoolToObject(data, "allow", granted)) {
            authenticated_receipt_alloc(root, error);
            return;
        }
        if (is_failed(error)) { mark_sdk_error(root, error, {}); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getSuUidProfiles(JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "get_su_uid_profiles", rootKey,
        [&](cJSON* root, const char* key, KModErr& error) {
        using maplesu::su_auth::UidProfileV1;
        std::vector<UidProfileV1> profiles;
        static thread_local std::vector<UidProfileV1>* output = nullptr;
        output = &profiles;
        try {
            error = maplesu::su_auth::get_uid_profiles_with_cb(key, [](const UidProfileV1* profile) {
                if (output && profile) output->push_back(*profile);
            });
        } catch (...) {
            output = nullptr;
            throw;
        }
        output = nullptr;
        if (is_failed(error)) { mark_sdk_error(root, error, {}); return; }
        OwnedJson data(cJSON_CreateObject());
        OwnedJson rows(cJSON_CreateArray());
        if (!data || !rows) { authenticated_receipt_alloc(root, error); return; }
        for (const auto& profile : profiles) {
            OwnedJson row(cJSON_CreateObject());
            if (!row || !cJSON_AddNumberToObject(row.get(), "uid", profile.uid) ||
                !cJSON_AddBoolToObject(row.get(), "allow", profile.allow != 0) ||
                !cJSON_AddStringToObject(row.get(), "package", url_encode_copy(profile.package).c_str()) ||
                !cJSON_AddItemToArray(rows.get(), row.get())) {
                authenticated_receipt_alloc(root, error);
                return;
            }
            (void)row.release();
        }
        if (!attach_owned_json(data.get(), "profiles", rows.release()) ||
            !cJSON_AddNumberToObject(data.get(), "count", static_cast<double>(profiles.size())) ||
            !cJSON_AddStringToObject(data.get(), "encoding", "url") ||
            !cJSON_AddBoolToObject(data.get(), "deliveryVerified", true) ||
            !attach_owned_json(root, "data", data.release())) {
            authenticated_receipt_alloc(root, error);
            return;
        }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_clearSuAuthList(JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "clear_su_auth_list", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        maplesu::su_auth::ResultV1 result(maplesu::su_auth::Operation::Clear);
        err = maplesu::su_auth::clear(key, result);
        cJSON* data = maplesu::ultra::write_authorization_result(root, result);
        if (!data) { authenticated_receipt_alloc(root, err); return; }
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            return;
        }
        if (!cJSON_AddBoolToObject(data, "cleared", true)) { authenticated_receipt_alloc(root, err); return; }
        if (!cJSON_AddBoolToObject(data, "authority", false)) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

// --- Native module lifecycle (4) ------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_installModule(
    JNIEnv* env, jclass, jstring rootKey, jstring zipFilePath, jboolean isDevRunOnceMode) {
    const bool dev_once = isDevRunOnceMode == JNI_TRUE;
    return guarded_authenticated(env, dev_once ? "install_module_dev_run_once" : "install_module", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        const std::string zip = jstring_to_utf8(env, zipFilePath);
        if (env->ExceptionCheck()) return;
        std::string reason;
        maplesu::module_install::InstallCompletion completion;
        err = maplesu::module_install::install_with_completion(
            key, zip.c_str(), dev_once, reason, completion);
        // A non-empty reason is callback data; a committed install retains its completion fact.
        if (is_failed(err)) {
            mark_sdk_error(root, err,
                err == KModErr::ERR_READ_CHILD_ERRCODE && !completion.committed &&
                    !completion.cleanup_complete && reason.empty()
                ? "Install worker ended without a receipt; reconcile module state before retrying." : reason);
            if (err == KModErr::ERR_READ_CHILD_ERRCODE && !completion.committed && !completion.cleanup_complete)
                mark_call_bridge_error(root, &err, -1005, "MAPLESU_MODULE_INSTALL_INTERRUPTED",
                    "Install worker ended without a receipt; reconcile module state before retrying.");
        }
        const bool accepted = completion.committed || reason.empty();
        if (!is_failed(err) && accepted && !cJSON_AddBoolToObject(root, "operationCompleted", true)) {
            authenticated_receipt_alloc(root, err);
            return;
        }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddStringToObject(data.get(), "mode", dev_once ? "dev_run_once" : "normal") ||
            !cJSON_AddStringToObject(data.get(), "zipPath", zip.c_str()) ||
            !cJSON_AddBoolToObject(data.get(), "installCommitted", completion.committed) ||
            !cJSON_AddBoolToObject(data.get(), "immediateStartupFailed", completion.immediate_start_failed) ||
            !cJSON_AddBoolToObject(data.get(), "cleanupComplete", completion.cleanup_complete) ||
            !cJSON_AddBoolToObject(data.get(), "cleanupPending", completion.cleanup_pending) ||
            !cJSON_AddBoolToObject(data.get(), "needsReboot", completion.needs_reboot) ||
            (is_failed(err) && !completion.committed &&
                (completion.cleanup_pending ||
                 (err == KModErr::ERR_READ_CHILD_ERRCODE && !completion.cleanup_complete)) &&
                !cJSON_AddBoolToObject(data.get(), "reconciliationRequired", true)) ||
            (!is_failed(err) && (!cJSON_AddBoolToObject(data.get(), "installCallbackAccepted", accepted) ||
                (!completion.committed && !reason.empty() &&
                    (!cJSON_AddStringToObject(data.get(), "refusalReason", url_encode_copy(reason).c_str()) ||
                     !cJSON_AddStringToObject(data.get(), "encoding", "url"))))) ||
            !attach_owned_json(root, "data", data.release())) {
            if (!is_failed(err) && !accepted)
                mark_bridge_error(root, -1007, "MAPLESU_MODULE_INSTALL_CALLBACK_DECLINED", reason);
            authenticated_receipt_alloc(root, err);
            return;
        }
        if (is_failed(err)) return;
        if (accepted) {
            if (!reason.empty() && !cJSON_AddStringToObject(root, "reason", reason.c_str())) {
                authenticated_receipt_alloc(root, err);
                return;
            }
            mark_authenticated_ok(root);
        } else mark_bridge_error(root, -1007, "MAPLESU_MODULE_INSTALL_CALLBACK_DECLINED", reason);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_uninstallModule(
    JNIEnv* env, jclass, jstring rootKey, jstring modUuid) {
    return guarded_authenticated(env, "uninstall_module", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        const std::string id32 = module_id_to_utf8(env, modUuid);
        if (env->ExceptionCheck()) return;
        err = maplesu::sdk::uninstall_module(key, id32.c_str());
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            if (err == KModErr::ERR_READ_CHILD_ERRCODE) {
                mark_call_bridge_error(root, &err, -1005, "MAPLESU_MODULE_UNINSTALL_INTERRUPTED",
                    "Uninstall worker ended without a receipt; refresh module state before retrying.");
                OwnedJson data(cJSON_CreateObject());
                if (!data || !cJSON_AddBoolToObject(data.get(), "reconciliationRequired", true) ||
                    !attach_owned_json(root, "data", data.release())) authenticated_receipt_alloc(root, err);
            }
            return;
        }
        if (!cJSON_AddBoolToObject(root, "operationCompleted", true)) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddStringToObject(data.get(), "id32", id32.c_str()) ||
            !cJSON_AddBoolToObject(data.get(), "mayBePendingReboot", true) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getModuleList(JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "get_all_modules_list", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        struct PhaseLatency {
            const char* phase;
            long long count = -1;
            std::chrono::steady_clock::time_point started = std::chrono::steady_clock::now();
            ~PhaseLatency() {
                const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now() - started).count();
                __android_log_print(ANDROID_LOG_INFO, "MapleSUUltra",
                    "MAPLESU_MODULE_LATENCY phase=%s count=%lld elapsed=%lld",
                    phase, count, static_cast<long long>(elapsed));
            }
        };
        std::vector<maplesu::sdk::module_record> records;
        {
            PhaseLatency latency{"native-list"};
            err = maplesu::sdk::get_all_modules_list(key, records);
            latency.count = static_cast<long long>(records.size());
        }
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        const auto controls = [&] {
            PhaseLatency latency{"native-control"};
            auto result = maplesu::get_module_controls(key, records);
            latency.count = static_cast<long long>(result.size());
            return result;
        }();
        if (controls.size() != records.size()) { control_error(root, maplesu::MODULE_CONTROL_IO); return; }
        OwnedJson modules(cJSON_CreateArray());
        if (!modules) { authenticated_receipt_alloc(root, err); return; }
        int unknown_states = 0;
        for (size_t index = 0; index < records.size(); ++index) {
            const auto& record = records[index];
            bool known = false;
            OwnedJson item(record_to_json(record, &known));
            if (!item) { authenticated_receipt_alloc(root, err); return; }
            if (!known) ++unknown_states;
            const auto& control = controls[index];
            if (control.error == 0) {
                if (!attach_owned_json(item.get(), "control", control_to_json(control.state))) {
                    authenticated_receipt_alloc(root, err);
                    return;
                }
            } else if (!cJSON_AddNumberToObject(item.get(), "controlErrorCode", static_cast<double>(control.error))) {
                authenticated_receipt_alloc(root, err);
                return;
            }
            if (!cJSON_AddItemToArray(modules.get(), item.get())) { authenticated_receipt_alloc(root, err); return; }
            (void)item.release();
        }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !attach_owned_json(data.get(), "modules", modules.release()) ||
            !cJSON_AddNumberToObject(data.get(), "count", static_cast<double>(records.size())) ||
            !cJSON_AddNumberToObject(data.get(), "unknownStates", unknown_states) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (unknown_states > 0) {
            // The list is returned, but its state column is not trustworthy.
            mark_bridge_error(root, kBridgeBadEnum, "MAPLESU_BRIDGE_UNKNOWN_MODULE_STATE",
                "one or more module records carried an unknown ModuleRunState");
        } else {
            mark_authenticated_ok(root);
        }
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_parseModuleDescriptor(
    JNIEnv* env, jclass, jstring rootKey, jstring zipFilePath) {
    return guarded_authenticated(env, "parse_module_desc_from_zip_file", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        const std::string zip = jstring_to_utf8(env, zipFilePath);
        if (env->ExceptionCheck()) return;
        maplesu::sdk::module_desc desc;
        err = maplesu::sdk::parse_module_desc_from_zip_file(key, zip.c_str(), desc);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !attach_owned_json(data.get(), "desc", descriptor_to_json(desc)) ||
            !cJSON_AddStringToObject(data.get(), "zipPath", zip.c_str()) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getModuleControlCapabilities(
    JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "get_module_control_capabilities", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        bool supported = false;
        const auto error = maplesu::module_control_capability(key, supported);
        err = static_cast<KModErr>(error);
        if (error != 0) { control_error(root, error); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "supportsEnableToggle", supported) ||
            !cJSON_AddBoolToObject(data.get(), "supportsImmediateStop", false) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_getModuleControl(
    JNIEnv* env, jclass, jstring rootKey, jstring moduleId) {
    return guarded_authenticated(env, "get_module_control", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        const auto id = module_id_to_utf8(env, moduleId);
        if (env->ExceptionCheck()) return;
        maplesu::ModuleControlState state;
        const auto error = maplesu::get_module_control(key, id.c_str(), state);
        err = static_cast<KModErr>(error);
        if (error != 0) { control_error(root, error); return; }
        OwnedJson data(control_to_json(state));
        if (!data || !cJSON_AddStringToObject(data.get(), "id32", id.c_str()) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_setModuleEnabled(
    JNIEnv* env, jclass, jstring rootKey, jstring moduleId, jboolean enabled) {
    return guarded_authenticated(env, "set_module_enabled", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        const auto id = module_id_to_utf8(env, moduleId);
        if (env->ExceptionCheck()) return;
        maplesu::ModuleControlState state;
        const auto error = maplesu::set_module_enabled(key, id.c_str(), enabled == JNI_TRUE, state);
        err = static_cast<KModErr>(error);
        if (error != 0) { control_error(root, error); return; }
        if (!cJSON_AddBoolToObject(root, "operationCompleted", true)) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(control_to_json(state));
        if (!data || !cJSON_AddStringToObject(data.get(), "id32", id.c_str()) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

// --- Module WebUI (1) -----------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_publishPolicyBackend(
    JNIEnv* env, jclass, jstring rootKey, jstring sha256, jbyteArray verifiedBytes) {
    const auto digest = jstring_to_utf8(env, sha256);
    if (env->ExceptionCheck()) return nullptr;
    return guarded_authenticated(env, "publish_policy_backend", rootKey,
        [&](cJSON* root, const char* key, KModErr& error) {
        const auto size = verifiedBytes ? env->GetArrayLength(verifiedBytes) : 0;
        if (size < 64 || size > 32 * 1024 * 1024) {
            error = KModErr::ERR_MODULE_PARAM;
            mark_sdk_error(root, error, {});
            return;
        }
        std::vector<unsigned char> bytes(static_cast<size_t>(size));
        env->GetByteArrayRegion(verifiedBytes, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
        if (env->ExceptionCheck()) return;
        OwnedJson completed(cJSON_CreateTrue());
        if (!completed) { authenticated_receipt_alloc(root, error); return; }
        error = static_cast<KModErr>(maplesu::module_policy::publish_backend(key, digest.c_str(), bytes.data(), bytes.size()));
        if (is_failed(error)) {
            mark_sdk_error(root, error, {});
            return;
        }
        cJSON_AddItemToObjectCS(root, "operationCompleted", completed.release());
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddStringToObject(data.get(), "sha256", digest.c_str()) ||
            !cJSON_AddNumberToObject(data.get(), "bytes", size) ||
            !attach_owned_json(root, "data", data.release())) {
            authenticated_receipt_alloc(root, error);
            return;
        }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_modulePolicySettings(
    JNIEnv* env, jclass, jstring rootKey, jint requested) {
    return guarded_authenticated(env, requested < 0 ? "get_module_policy_settings" : "set_module_policy_settings", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        const auto state = maplesu::module_policy::manage(key, requested);
        if (state.error != 0) {
            err = static_cast<KModErr>(state.error);
            mark_bridge_error(root, -1010, "MAPLESU_MODULE_POLICY_SETTINGS", state.reason);
            return;
        }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddNumberToObject(data.get(), "requested", state.requested) ||
            !(state.effective < 0 ? cJSON_AddNullToObject(data.get(), "effective") :
                cJSON_AddNumberToObject(data.get(), "effective", state.effective)) ||
            !cJSON_AddNumberToObject(data.get(), "capabilities", state.capabilities) ||
            !cJSON_AddBoolToObject(data.get(), "unverifiedHide", state.unverified_hide) ||
            !cJSON_AddBoolToObject(data.get(), "canTryUnverified", state.can_try_unverified) ||
            !cJSON_AddBoolToObject(data.get(), "hideFailed", state.hide_failed) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_openModuleWebUi(
    JNIEnv* env, jclass, jstring rootKey, jstring modUuid) {
    return guarded_authenticated(env, "start_module_web_ui_server_async", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        const std::string id32 = module_id_to_utf8(env, modUuid);
        if (env->ExceptionCheck()) return;
        int port = 0;
        std::string module_directory;
        // This host opts into SDK KeepRunning; other modules retain their SDK lifecycle.
        err = id32 == "c45e8a7390b24987a4b01d6e2c5f9143"
            ? maplesu::start_detached_module_webui(key, id32.c_str(), port, module_directory)
            : maplesu::sdk::features::web_ui::start_module_web_ui_server_for_manager(
                key, id32.c_str(), port, module_directory);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        if (!cJSON_AddBoolToObject(root, "operationCompleted", true)) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddNumberToObject(data.get(), "port", port) ||
            !cJSON_AddBoolToObject(data.get(), "portAssigned", port > 0) ||
            !cJSON_AddStringToObject(data.get(), "id32", id32.c_str()) ||
            !cJSON_AddStringToObject(data.get(), "pageOpener", "manager") ||
            !cJSON_AddStringToObject(data.get(), "moduleDir", module_directory.c_str()) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

// --- Boot failure protection (2) ------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_setBootFailProtectEnabled(
    JNIEnv* env, jclass, jstring rootKey, jboolean enable) {
    const bool requested = enable == JNI_TRUE;
    return guarded_authenticated(env, "set_boot_fail_protect_enabled", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        OwnedJson completed(cJSON_CreateTrue());
        if (!completed) { authenticated_receipt_alloc(root, err); return; }
        err = maplesu::sdk::set_boot_fail_protect_enabled(key, requested);
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            return;
        }
        if (!attach_owned_json(root, "operationCompleted", completed.release())) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "requested", requested) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_isBootFailProtectEnabled(
    JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "is_boot_fail_protect_enabled", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        bool enabled = false;
        err = maplesu::sdk::runtime_settings::query_boot_fail_protect(key, enabled);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "enabled", enabled) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

// --- ADB control (2) ------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_setAdbForcedDisabled(
    JNIEnv* env, jclass, jstring rootKey, jboolean enable) {
    const bool requested = enable == JNI_TRUE;
    return guarded_authenticated(env, "set_adb_forced_disabled", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        OwnedJson completed(cJSON_CreateTrue());
        if (!completed) { authenticated_receipt_alloc(root, err); return; }
        err = maplesu::sdk::set_adb_forced_disabled(key, requested);
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            return;
        }
        if (!attach_owned_json(root, "operationCompleted", completed.release())) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "requested", requested) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_isAdbForcedDisabled(
    JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "is_adb_forced_disabled", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        bool disabled = false;
        err = maplesu::sdk::runtime_settings::query_adb_forced_disabled(key, disabled);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "disabled", disabled) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

// --- Diagnostics (3) ------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_testBasics(
    JNIEnv* env, jclass, jstring rootKey, jstring item) {
    const std::string name = jstring_to_utf8(env, item);
    if (env->ExceptionCheck()) return nullptr;
    return guarded_authenticated(env, "test_maplesu_basics", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        maplesu::sdk::BasicItem basic;
        if (name == "Channel") basic = maplesu::sdk::BasicItem::Channel;
        else if (name == "KernelBase") basic = maplesu::sdk::BasicItem::KernelBase;
        else if (name == "WriteTest") basic = maplesu::sdk::BasicItem::WriteTest;
        else if (name == "ReadTrampoline") basic = maplesu::sdk::BasicItem::ReadTrampoline;
        else if (name == "WriteTrampoline") basic = maplesu::sdk::BasicItem::WriteTrampoline;
        else if (name == "PhysAddrCalc") basic = maplesu::sdk::BasicItem::PhysAddrCalc;
        else {
            mark_bridge_error(root, kBridgeBadEnum, "MAPLESU_BRIDGE_BAD_DIAG_ITEM",
                              "unknown BasicItem name");
            return;
        }
        std::string info;
        err = maplesu::sdk::test_maplesu_basics(key, basic, info);
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
        }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddStringToObject(data.get(), "item", name.c_str()) ||
            !cJSON_AddStringToObject(data.get(), "info", url_encode_copy(info).c_str()) ||
            !cJSON_AddStringToObject(data.get(), "encoding", "url") ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_testDefaultModule(
    JNIEnv* env, jclass, jstring rootKey, jstring name) {
    const std::string module_name = jstring_to_utf8(env, name);
    if (env->ExceptionCheck()) return nullptr;
    return guarded_authenticated(env, "test_maplesu_default_module", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        maplesu::sdk::DeafultModuleName target;
        if (module_name == "RootBridgePrint") target = maplesu::sdk::DeafultModuleName::RootBridgePrint;
        else if (module_name == "RootBridgeExec") target = maplesu::sdk::DeafultModuleName::RootBridgeExec;
        else if (module_name == "SuRedirectPrint") target = maplesu::sdk::DeafultModuleName::SuRedirectPrint;
        else if (module_name == "TombstonesPurgePrint") target = maplesu::sdk::DeafultModuleName::TombstonesPurgePrint;
        else {
            mark_bridge_error(root, kBridgeBadEnum, "MAPLESU_BRIDGE_BAD_DEFAULT_MODULE",
                              "unknown DeafultModuleName");
            return;
        }
        std::string info;
        err = maplesu::sdk::test_maplesu_deafult_module(key, target, info);
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
        }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddStringToObject(data.get(), "name", module_name.c_str()) ||
            !cJSON_AddStringToObject(data.get(), "info", url_encode_copy(info).c_str()) ||
            !cJSON_AddStringToObject(data.get(), "encoding", "url") ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_restartZygote64(JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "restart_zygote64", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        OwnedJson completed(cJSON_CreateTrue());
        if (!completed) { authenticated_receipt_alloc(root, err); return; }
        err = maplesu::sdk::restart_zygote64(key);
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
            return;
        }
        if (!attach_owned_json(root, "operationCompleted", completed.release())) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        // Restarting zygote64 ends application processes; the caller must treat the
        // Manager's own process as disposable after this call returns.
        if (!data || !cJSON_AddBoolToObject(data.get(), "callerProcessMayExit", true) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

// --- Environment log (4) --------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_setLogEnabled(
    JNIEnv* env, jclass, jstring rootKey, jboolean enable) {
    const bool requested = enable == JNI_TRUE;
    return guarded_authenticated(env, "set_maplesu_log_enabled", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        err = maplesu::sdk::set_maplesu_log_enabled(key, requested);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        if (!cJSON_AddBoolToObject(root, "operationCompleted", true)) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "requested", requested) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_isLogEnabled(
    JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "is_maplesu_log_enabled", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        bool enabled = false;
        err = maplesu::runtime_log::query_enabled(key, enabled);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "enabled", enabled) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_readLog(JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "read_maplesu_log", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        std::string log;
        err = maplesu::sdk::read_maplesu_log(key, log);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddStringToObject(data.get(), "log", url_encode_copy(log).c_str()) ||
            !cJSON_AddStringToObject(data.get(), "encoding", "url") ||
            !cJSON_AddNumberToObject(data.get(), "logBytes", static_cast<double>(log.size())) ||
            !cJSON_AddBoolToObject(data.get(), "mayContainSensitiveText", true) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_clearLog(JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "clear_maplesu_log", rootKey,
        [&](cJSON* root, const char* key, KModErr& err) {
        err = maplesu::sdk::clear_maplesu_log(key);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        if (!cJSON_AddBoolToObject(root, "operationCompleted", true)) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "cleared", true) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        mark_authenticated_ok(root);
    });
}

// --- OEM (2) --------------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_oneplusBypassWriteStage1(
    JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "oneplus_bypass_stage1_and_2", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        if (!device_detect::is_oplus_device()) {
            // Not an OPPO/OnePlus device: report the condition instead of a fake success.
            mark_bridge_error(root, kBridgeBadEnum, "MAPLESU_BRIDGE_UNSUPPORTED_DEVICE",
                              "not an OPPO/OnePlus device");
            OwnedJson data(cJSON_CreateObject());
            if (!data || !cJSON_AddBoolToObject(data.get(), "supported", false) ||
                !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
            return;
        }
        thread_local std::string captured;
        captured.clear();
        auto callback = [](const char* text) { captured = text == nullptr ? "" : text; };
        err = oneplus_bypass_stage1_and_2(key, callback);
        if (is_failed(err)) {
            mark_sdk_error(root, err, {});
        }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "supported", true) ||
            !cJSON_AddStringToObject(data.get(), "result", url_encode_copy(captured).c_str()) ||
            !cJSON_AddStringToObject(data.get(), "encoding", "url") ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_oneplusBypassIsWorkNormal(
    JNIEnv* env, jclass, jstring rootKey) {
    return guarded_authenticated(env, "oneplus_bypass_is_work_normal", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        if (!device_detect::is_oplus_device()) {
            mark_bridge_error(root, kBridgeBadEnum, "MAPLESU_BRIDGE_UNSUPPORTED_DEVICE",
                              "not an OPPO/OnePlus device");
            OwnedJson data(cJSON_CreateObject());
            if (!data || !cJSON_AddBoolToObject(data.get(), "supported", false) ||
                !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
            return;
        }
        bool working = false;
        err = oneplus_bypass_query_work_normal(key, working);
        if (is_failed(err)) { mark_sdk_error(root, err, {}); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "supported", true) ||
            !cJSON_AddBoolToObject(data.get(), "working", working) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

// --- Parasite (H4, 3) ---------------------------------------------------------------
//
// SDK 5.1.0 adds source-built high-level wrappers with the legacy C++ ABI.
// The linker explicitly extracts their archive member; dlsym still reports older
// imports as MAPLESU_BRIDGE_SDK_SYMBOL_MISSING. KRootErr uses ssize_t, not int.

namespace {

constexpr int kBridgeSdkSymbol = -1005;

struct ParasiteSdkSymbols {
    // KRootErr parasite_precheck_app_with_cb(root_key, cmdline, cb(path, AppDynlibStatus))
    using PrecheckFn = decltype(&kernel_root::parasite_precheck_app_with_cb);
    using ImplantFn = decltype(&kernel_root::parasite_implant_app);
    using SuEnvFn = decltype(&kernel_root::parasite_implant_su_env);

    PrecheckFn precheck = nullptr;
    ImplantFn implant = nullptr;
    SuEnvFn suEnv = nullptr;

    bool complete() const { return precheck != nullptr && implant != nullptr && suEnv != nullptr; }
};

ParasiteSdkSymbols load_parasite_symbols() {
    ParasiteSdkSymbols symbols;
    symbols.precheck = reinterpret_cast<ParasiteSdkSymbols::PrecheckFn>(dlsym(
        RTLD_DEFAULT,
        "_ZN11kernel_root29parasite_precheck_app_with_cbEPKcS1_PFvS1_NS_15AppDynlibStatusEE"));
    symbols.implant = reinterpret_cast<ParasiteSdkSymbols::ImplantFn>(dlsym(
        RTLD_DEFAULT, "_ZN11kernel_root20parasite_implant_appEPKcS1_S1_"));
    symbols.suEnv = reinterpret_cast<ParasiteSdkSymbols::SuEnvFn>(dlsym(
        RTLD_DEFAULT, "_ZN11kernel_root23parasite_implant_su_envEPKcS1_S1_S1_"));
    return symbols;
}

void mark_sdk_symbol_missing(cJSON* root, const char* symbol) {
    mark_bridge_error(root, kBridgeSdkSymbol, "MAPLESU_BRIDGE_SDK_SYMBOL_MISSING",
                      std::string("Ultra SDK does not export ") + symbol);
    cJSON* data = cJSON_CreateObject();
    cJSON_AddBoolToObject(data, "sdkSymbolsPresent", false);
    cJSON_AddStringToObject(data, "missingSymbol", symbol);
    attach_data(root, data);
}

const char* dynlib_status_name(int status, bool* known) {
    switch (status) {
        case 0:
            // Header enum: Unknown = 0. Unknown must stay unknown, not "not running".
            *known = false;
            return "unknown";
        case 1:
            *known = true;
            return "running";
        case 2:
            *known = true;
            return "not_running";
        default:
            *known = false;
            return "unknown";
    }
}

cJSON* dynlibs_to_json(const std::map<std::string, int>& libraries) {
    OwnedJson array(cJSON_CreateArray());
    if (!array) return nullptr;
    for (const auto& entry : libraries) {
        OwnedJson item(cJSON_CreateObject());
        bool known = false;
        const char* name = dynlib_status_name(entry.second, &known);
        if (!item || !cJSON_AddStringToObject(item.get(), "path", entry.first.c_str()) ||
            !cJSON_AddStringToObject(item.get(), "status", name) ||
            !cJSON_AddNumberToObject(item.get(), "statusCode", entry.second) ||
            !cJSON_AddBoolToObject(item.get(), "statusKnown", known) ||
            !cJSON_AddItemToArray(array.get(), item.get())) return nullptr;
        (void)item.release();
    }
    return array.release();
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_parasitePrecheckApp(
    JNIEnv* env, jclass, jstring rootKey, jstring targetPidCmdline) {
    const std::string cmdline = jstring_to_utf8(env, targetPidCmdline);
    if (env->ExceptionCheck()) return nullptr;
    return guarded_authenticated(env, "parasite_precheck_app", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        if (cmdline.empty()) {
            mark_bridge_error(root, kBridgeNullArgument, "MAPLESU_BRIDGE_INVALID_ARGUMENT",
                              "targetPidCmdline is empty");
            return;
        }
        const ParasiteSdkSymbols symbols = load_parasite_symbols();
        if (symbols.precheck == nullptr) {
            mark_sdk_symbol_missing(root, "parasite_precheck_app_with_cb");
            return;
        }
        thread_local std::map<std::string, int>* captured = nullptr;
        auto callback = [](const char* dynlibPath, kernel_root::AppDynlibStatus status) {
            if (captured == nullptr || dynlibPath == nullptr) return;
            (*captured)[std::string(dynlibPath)] = static_cast<int>(status);
        };
        std::map<std::string, int> libraries;
        struct RestoreCapture {
            std::map<std::string, int>* previous;
            ~RestoreCapture() { captured = previous; }
        } restore{captured};
        captured = &libraries;
        const auto error = symbols.precheck(key, cmdline.c_str(), callback);
        err = static_cast<KModErr>(error);
        if (error != 0) {
            // Preserve both legacy KRootErr and Ultra root-channel errors verbatim.
            cJSON_AddStringToObject(root, "sdkError", "ERR_PARASITE_PRECHECK");
            cJSON_AddNumberToObject(root, "sdkErrorCode", static_cast<double>(error));
            cJSON_ReplaceItemInObject(root, "ok", cJSON_CreateFalse());
            return;
        }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !attach_owned_json(data.get(), "dynlibs", dynlibs_to_json(libraries)) ||
            !cJSON_AddBoolToObject(data.get(), "sdkSymbolsPresent", symbols.complete()) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_parasiteImplantApp(
    JNIEnv* env, jclass, jstring rootKey, jstring targetPidCmdline, jstring soPath) {
    const std::string cmdline = jstring_to_utf8(env, targetPidCmdline);
    const std::string so = jstring_to_utf8(env, soPath);
    if (env->ExceptionCheck()) return nullptr;
    return guarded_authenticated(env, "parasite_implant_app", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        OwnedJson completed(cJSON_CreateTrue());
        if (!completed) { authenticated_receipt_alloc(root, err); return; }
        if (cmdline.empty() || so.empty()) {
            mark_bridge_error(root, kBridgeNullArgument, "MAPLESU_BRIDGE_INVALID_ARGUMENT",
                              "cmdline/soPath is empty");
            return;
        }
        const ParasiteSdkSymbols symbols = load_parasite_symbols();
        if (symbols.implant == nullptr) {
            mark_sdk_symbol_missing(root, "parasite_implant_app");
            return;
        }
        const auto error = symbols.implant(key, cmdline.c_str(), so.c_str());
        err = static_cast<KModErr>(error);
        if (error != 0) {
            cJSON_AddStringToObject(root, "sdkError", "ERR_PARASITE_IMPLANT_APP");
            cJSON_AddNumberToObject(root, "sdkErrorCode", static_cast<double>(error));
            cJSON_ReplaceItemInObject(root, "ok", cJSON_CreateFalse());
            return;
        }
        if (!attach_owned_json(root, "operationCompleted", completed.release())) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "implanted", true) ||
            !cJSON_AddStringToObject(data.get(), "soPath", so.c_str()) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_parasiteImplantSuEnv(
    JNIEnv* env, jclass, jstring rootKey, jstring targetPidCmdline, jstring soPath, jstring suDir) {
    const std::string cmdline = jstring_to_utf8(env, targetPidCmdline);
    const std::string so = jstring_to_utf8(env, soPath);
    const std::string directory = jstring_to_utf8(env, suDir);
    if (env->ExceptionCheck()) return nullptr;
    return guarded_authenticated(env, "parasite_implant_su_env", rootKey, [&](cJSON* root, const char* key, KModErr& err) {
        OwnedJson completed(cJSON_CreateTrue());
        if (!completed) { authenticated_receipt_alloc(root, err); return; }
        if (cmdline.empty() || so.empty() || directory.empty()) {
            mark_bridge_error(root, kBridgeNullArgument, "MAPLESU_BRIDGE_INVALID_ARGUMENT",
                              "cmdline/soPath/suDir is empty");
            return;
        }
        const ParasiteSdkSymbols symbols = load_parasite_symbols();
        if (symbols.suEnv == nullptr) {
            mark_sdk_symbol_missing(root, "parasite_implant_su_env");
            return;
        }
        const auto error = symbols.suEnv(key, cmdline.c_str(), so.c_str(), directory.c_str());
        err = static_cast<KModErr>(error);
        if (error != 0) {
            cJSON_AddStringToObject(root, "sdkError", "ERR_PARASITE_IMPLANT_SU_ENV");
            cJSON_AddNumberToObject(root, "sdkErrorCode", static_cast<double>(error));
            cJSON_ReplaceItemInObject(root, "ok", cJSON_CreateFalse());
            return;
        }
        if (!attach_owned_json(root, "operationCompleted", completed.release())) { authenticated_receipt_alloc(root, err); return; }
        OwnedJson data(cJSON_CreateObject());
        if (!data || !cJSON_AddBoolToObject(data.get(), "implanted", true) ||
            !cJSON_AddStringToObject(data.get(), "suDir", directory.c_str()) ||
            !attach_owned_json(root, "data", data.release())) { authenticated_receipt_alloc(root, err); return; }
        if (is_ok(err)) mark_authenticated_ok(root);
    });
}

// Private module-only facts; generic Root never depends on install.mode.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_captureModuleEnvironment(
    JNIEnv* env, jclass, jstring rootKey) {
    maplesu::SdkCallScope scope("capture_module_environment");
    const std::string key = jstring_to_utf8(env, rootKey);
    std::vector<std::string> facts;
    const auto error = scope.acquired() ? maplesu::capture_module_environment(rootKey ? key.c_str() : nullptr, facts) :
        static_cast<KModErr>(-EBUSY);
    if (is_failed(error)) {
        const std::string reason = "MODULE_ENVIRONMENT_CAPTURE_FAILED:" + std::to_string(static_cast<int64_t>(error));
        env->ThrowNew(env->FindClass("java/io/IOException"), reason.c_str());
        return nullptr;
    }
    jobjectArray result = env->NewObjectArray(facts.size(), env->FindClass("java/lang/String"), nullptr);
    if (!result) return nullptr;
    for (size_t i = 0; i < facts.size(); ++i) {
        jstring fact = env->NewStringUTF(facts[i].c_str());
        if (!fact) return nullptr;
        env->SetObjectArrayElement(result, i, fact); env->DeleteLocalRef(fact);
        if (env->ExceptionCheck()) return nullptr;
    }
    return result;
}
