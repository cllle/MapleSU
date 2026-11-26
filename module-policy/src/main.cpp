#include "policy.hpp"
#include "shared_file_contract.h"
#include "module_policy_config.h"
#ifdef MAPLESU_POLICY_HOOK_BACKEND
#include "kernel_payload_hook.h"
int policy_hook_loader(int argc, char** argv);
#endif
extern "C" {
#include <sepol/kernel_to_conf.h>
}
#include <cerrno>
#include <fcntl.h>
#include <fstream>
#include <functional>
#include <memory>
#include <stdexcept>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>
#ifdef __ANDROID__
#include <android/api-level.h>
#endif

int jailbreak_selinux_hide(int argc, char** argv);
int ksu_manager_fd(int argc, char** argv);
int boot_sdk_control(int argc, char** argv);

namespace {
constexpr const char *LIVE = "/sys/fs/selinux/policy";
constexpr const char *LOAD = "/sys/fs/selinux/load";
constexpr const char *ENFORCE = "/sys/fs/selinux/enforce";
using Strings = std::vector<std::string>;
struct Failure : std::runtime_error {
    using std::runtime_error::runtime_error;
    int error_number = -1;
    size_t requested_bytes = 0;
    ssize_t written_bytes = -1;
    bool write_failure = false;
};
struct Rule { std::string file, text; size_t line; };
struct Fd {
    int value;
    explicit Fd(int value) : value(value) {}
    ~Fd() { if (value >= 0) close(value); }
    Fd(const Fd &) = delete;
};
std::string quote(std::string_view value) {
    std::string out = "\"";
    const char *hex = "0123456789abcdef";
    for (unsigned char c : value) {
        if (c == '"' || c == '\\') { out += '\\'; out += c; }
        else if (c < 32) { out += "\\u00"; out += hex[c >> 4]; out += hex[c & 15]; }
        else out += c;
    }
    return out + '"';
}
[[noreturn]] void system_error(const std::string &action) {
    int code = errno;
    Failure failure(action + ": errno=" + std::to_string(code) + " " + strerror(code));
    failure.error_number = code;
    throw failure;
}
std::vector<char> read_file(const std::string &path) {
    Fd fd(open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW));
    if (fd.value < 0) system_error("open " + path);
    std::vector<char> bytes;
    char buffer[65536];
    for (;;) {
        ssize_t n = read(fd.value, buffer, sizeof(buffer));
        if (n < 0) { if (errno == EINTR) continue; system_error("read " + path); }
        if (!n) break;
        bytes.insert(bytes.end(), buffer, buffer + n);
        if (bytes.size() > 256U * 1024U * 1024U) throw Failure("input exceeds 256 MiB: " + path);
    }
    return bytes;
}
std::unique_ptr<sepol_impl> decode(std::vector<char> &bytes) {
    auto db = static_cast<policydb_t *>(calloc(1, sizeof(policydb_t)));
    if (!db) throw Failure("allocate policydb failed");
    if (policydb_init(db)) { free(db); throw Failure("policydb_init failed"); }
    std::unique_ptr<sepol_impl> out(new sepol_impl(db));
    policy_file_t file;
    policy_file_init(&file);
    file.type = PF_USE_MEMORY;
    file.data = bytes.data();
    file.len = bytes.size();
    if (policydb_read(db, &file, 0)) throw Failure("policydb_read rejected policy image");
    return out;
}
void restore_android_netlink(sepol_impl &policy, int api) {
    // Android kernels before 6.18 omit these bits when exporting live policy.
    // Reloading that export must retain Android's app netlink restrictions.
    auto cls = static_cast<class_datum_t *>(hashtab_search(policy.db->p_classes.table, "netlink_route_socket"));
    if (!cls) return;
    if (api >= 30 && hashtab_search(cls->permissions.table, "nlmsg_readpriv"))
        policy.db->android_extra |= POLICYDB_CONFIG_ANDROID_NETLINK_ROUTE;
    if (api >= 33 && hashtab_search(cls->permissions.table, "nlmsg_getneigh"))
        policy.db->android_extra |= POLICYDB_CONFIG_ANDROID_NETLINK_GETNEIGH;
}
std::vector<char> encode(sepol_impl &policy) {
    void *image = nullptr;
    size_t size = 0;
    if (policydb_to_image(nullptr, policy.db, &image, &size)) throw Failure("policydb_to_image failed");
    std::unique_ptr<void, decltype(&free)> owner(image, free);
    if (!image || !size) throw Failure("empty serialized policy");
    return {static_cast<char *>(image), static_cast<char *>(image) + size};
}
// Kernel exports may reorder binary hash tables. Compare the complete sorted
// policy representation, not a subset of rules or raw serialization order.
std::string policy_text(sepol_impl &policy) {
    char *text = nullptr;
    size_t size = 0;
    FILE *stream = open_memstream(&text, &size);
    if (!stream) system_error("create policy verification stream");
    int result = sepol_kernel_policydb_to_conf(stream, policy.db);
    int closed = fclose(stream);
    std::unique_ptr<char, decltype(&free)> owner(text, free);
    if (result || closed || !text) throw Failure("complete policy verification failed");
    return {text, size};
}
bool policy_equivalent(sepol_impl &expected, sepol_impl &actual) {
    return expected.db->policyvers == actual.db->policyvers &&
        expected.db->android_extra == actual.db->android_extra &&
        policy_text(expected) == policy_text(actual);
}
Strings tokens(std::string_view text) {
    Strings out;
    std::string word;
    bool quoted = false;
    auto flush = [&] { if (!word.empty()) { out.push_back(word); word.clear(); } };
    for (char c : text) {
        if (c == '"') { quoted = !quoted; continue; }
        if (quoted) { word += c; continue; }
        if (c == '#') break;
        if (c == '{' || c == '}' || c == ':' || c == ';') {
            flush(); out.emplace_back(1, c);
        } else if (c == ' ' || c == '\t' || c == '\r' || c == '\n') flush();
        else word += c;
    }
    if (quoted) throw Failure("unterminated quoted string");
    flush();
    if (!out.empty() && out.back() == ";") out.pop_back();
    return out;
}
class Parser {
    Strings words;
    size_t at = 0;
public:
    explicit Parser(const std::string &text) : words(tokens(text)) {}
    bool empty() const { return words.empty(); }
    bool more() const { return at < words.size(); }
    std::string one() {
        if (!more()) throw Failure("missing argument");
        auto item = words[at++];
        if (item == "{" || item == "}" || item == ":" || item == ";") throw Failure("unexpected token " + item);
        if (item.size() >= 256) throw Failure("symbol exceeds 255 bytes");
        return item;
    }
    Strings group(bool class_position = false) {
        if (class_position && more() && words[at] == ":") ++at;
        if (!more()) throw Failure("missing argument");
        if (words[at] != "{") return {one()};
        ++at;
        Strings values;
        while (more() && words[at] != "}") values.push_back(one());
        if (!more()) throw Failure("unclosed set");
        ++at;
        if (values.empty()) throw Failure("empty set");
        if (values.size() > 1 && std::find(values.begin(), values.end(), "*") != values.end()) throw Failure("wildcard must be the only member of a set");
        return values;
    }
    void end() const { if (more()) throw Failure("unexpected trailing token " + words[at]); }
};
std::string_view wildcard(const std::string &name) { return name == "*" ? std::string_view{} : std::string_view(name); }
void require(bool result) {
    if (!result) throw Failure(policy_diagnostic.empty() ? "policy operation rejected" : policy_diagnostic);
}
void deny_inherited(sepol_impl &policy, Str source, Str target, Str cls, Str permission) {
    // SELinux unions concrete and attribute grants. Removing only the exact
    // AVTAB key leaves inherited allows intact. Split only the removed bits;
    // preserve every other source/target pair and all other permissions.
    auto db = policy.db;
    std::map<uint16_t, std::vector<uint16_t>> members;
    auto expand = [&](uint16_t symbol) -> const std::vector<uint16_t> & {
        auto [entry, inserted] = members.try_emplace(symbol);
        if (inserted) for (uint32_t i = 0; i < db->p_types.nprim; ++i) {
            if (db->type_val_to_struct[i]->flavor == TYPE_TYPE &&
                (!symbol || ebitmap_get_bit(&db->type_attr_map[i], symbol - 1)))
                entry->second.push_back(i + 1);
        }
        return entry->second;
    };
    auto symbol = [&](Str name) -> uint16_t {
        if (name.empty()) return 0;
        return static_cast<type_datum_t *>(hashtab_search(db->p_types.table, std::string(name).c_str()))->s.value;
    };
    const auto &sources = expand(symbol(source));
    const auto &targets = expand(symbol(target));
    uint16_t class_value = cls.empty() ? 0 : static_cast<class_datum_t *>(
        hashtab_search(db->p_classes.table, std::string(cls).c_str()))->s.value;
    uint32_t mask = UINT32_MAX;
    if (!permission.empty()) {
        auto class_data = db->class_val_to_struct[class_value - 1];
        auto perm = static_cast<perm_datum_t *>(hashtab_search(class_data->permissions.table, std::string(permission).c_str()));
        if (!perm && class_data->comdatum)
            perm = static_cast<perm_datum_t *>(hashtab_search(class_data->comdatum->permissions.table, std::string(permission).c_str()));
        mask = 1U << (perm->s.value - 1);
    }
    auto contains = [](const auto &values, uint16_t value) {
        return std::binary_search(values.begin(), values.end(), value);
    };
    auto intersects = [&](const auto &left, const auto &right) {
        return std::any_of(left.begin(), left.end(), [&](uint16_t value) { return contains(right, value); });
    };
    auto matches = [&](avtab_ptr_t node) {
        return (node->key.specified & ~AVTAB_ENABLED) == AVTAB_ALLOWED &&
            (!class_value || node->key.target_class == class_value) && (node->datum.data & mask) &&
            intersects(expand(node->key.source_type), sources) && intersects(expand(node->key.target_type), targets);
    };
    // Conditional nodes also belong to boolean expression lists. Do not silently
    // convert a conditional allow into an unconditional one while splitting it.
    for (uint32_t i = 0; i < db->te_cond_avtab.nslot; ++i)
        for (auto node = db->te_cond_avtab.htable[i]; node; node = node->next)
            if (matches(node)) throw Failure("deny overlaps a conditional grant; policy not committed");
    std::vector<avtab_ptr_t> affected;
    for (uint32_t i = 0; i < db->te_avtab.nslot; ++i)
        for (auto node = db->te_avtab.htable[i]; node; node = node->next)
            if (matches(node)) affected.push_back(node);
    for (auto node : affected) {
        uint32_t removed = node->datum.data & mask;
        node->datum.data &= ~mask;
        auto grant = [&](uint16_t s, uint16_t t) {
            avtab_key_t key{s, t, node->key.target_class, AVTAB_ALLOWED};
            auto existing = avtab_search_node(&db->te_avtab, &key);
            if (existing) existing->datum.data |= removed;
            else {
                avtab_datum_t value{removed, nullptr};
                if (!avtab_insert_nonunique(&db->te_avtab, &key, &value)) throw Failure("allocate split permission failed");
            }
        };
        for (auto s : expand(node->key.source_type)) {
            if (!contains(sources, s)) grant(s, node->key.target_type);
            else for (auto t : expand(node->key.target_type))
                if (!contains(targets, t)) grant(s, t);
        }
    }
    for (uint32_t i = 0; i < db->te_avtab.nslot; ++i) {
        auto *link = &db->te_avtab.htable[i];
        while (*link) {
            auto node = *link;
            if (node->key.specified == AVTAB_ALLOWED && !node->datum.data) {
                *link = node->next;
                free(node);
                --db->te_avtab.nel;
            } else link = &node->next;
        }
    }
}
struct Atomic {
    Rule rule;
    std::function<void(sepol_impl &)> apply;
};
// Parsing/expansion performs no policy lookup. Module checks use this same
// grammar without opening selinuxfs or entering a policy transaction.
std::vector<Atomic> parse_rule(const Rule &rule, bool module = false) {
    std::vector<Atomic> out;
    auto emit = [&](std::string text, std::function<void(sepol_impl &)> apply) {
        out.push_back({{rule.file, std::move(text), rule.line}, std::move(apply)});
    };
    Parser p(rule.text);
    if (p.empty()) return out;
    std::string action = p.one();
    if (module && action == "attradd") action = "typeattribute";
    if (module && action == "name_transition") action = "type_transition";
    if (action == "type" || action == "attribute") {
        auto name = p.one();
        if (name == "*") throw Failure("declaration requires an explicit symbol");
        auto attrs = p.more() ? p.group() : Strings{};
        if (action == "attribute" && !attrs.empty()) throw Failure("attribute takes one symbol");
        p.end();
        if (module && action == "type") {
            if (attrs.empty()) attrs.push_back("domain");
            for (auto &attr : attrs) emit("type " + name + " " + attr, [=](sepol_impl &db) {
                require(db.add_type(name, TYPE_TYPE)); require(db.add_typeattribute(name, attr));
            });
        } else {
            emit(action + " " + name, [=](sepol_impl &db) { require(db.add_type(name, action == "type" ? TYPE_TYPE : TYPE_ATTRIB)); });
            for (auto &attr : attrs) emit("typeattribute " + name + " " + attr,
                [=](sepol_impl &db) { require(db.add_typeattribute(name, attr)); });
        }
    } else if (action == "typeattribute") {
        auto types = p.group(), attrs = p.group(); p.end();
        for (auto &type : types) for (auto &attr : attrs) emit("typeattribute " + type + " " + attr,
            [=](sepol_impl &db) { require(db.add_typeattribute(type, attr)); });
    } else if (action == "allow" || action == "deny" || action == "auditallow" || action == "dontaudit") {
        auto sources = p.group(), targets = p.group(), classes = p.group(true), perms = p.group(); p.end();
        int effect = action == "auditallow" ? AVTAB_AUDITALLOW : action == "dontaudit" ? AVTAB_AUDITDENY : AVTAB_ALLOWED;
        bool invert = action == "deny" || action == "dontaudit";
        for (auto &s : sources) for (auto &t : targets) for (auto &c : classes) for (auto &perm : perms) {
            if (t == "self" && s == "*") throw Failure("self with wildcard source is unsupported");
            auto target = t == "self" ? s : t;
            emit(action + " " + s + " " + target + " " + c + " " + perm, [=](sepol_impl &db) {
                require(db.add_rule(wildcard(s), wildcard(target), wildcard(c), wildcard(perm), effect, invert));
                if (action == "deny") deny_inherited(db, wildcard(s), wildcard(target), wildcard(c), wildcard(perm));
            });
        }
    } else if (action == "allowxperm" || action == "auditallowxperm" || action == "dontauditxperm") {
        auto sources = p.group(), targets = p.group(), classes = p.group(true);
        if (p.one() != "ioctl") throw Failure("only ioctl extended permissions are supported");
        auto ranges = p.group(); p.end();
        int effect = action == "allowxperm" ? AVTAB_XPERMS_ALLOWED : action == "auditallowxperm" ? AVTAB_XPERMS_AUDITALLOW : AVTAB_XPERMS_DONTAUDIT;
        for (auto &range : ranges) {
            auto number = [](const std::string &value) {
                size_t used = 0; unsigned long n;
                try { n = std::stoul(value, &used, 0); } catch (...) { throw Failure("invalid ioctl number " + value); }
                if (used != value.size() || n > 65535) throw Failure("ioctl number outside 0..65535: " + value);
                return static_cast<uint16_t>(n);
            };
            Xperm x{0, 65535, false};
            if (range != "*") {
                if (range.front() == '~') throw Failure("complement xperm ranges are unsupported");
                auto dash = range.find('-');
                x.low = number(range.substr(0, dash));
                x.high = dash == std::string::npos ? x.low : number(range.substr(dash + 1));
                if (x.low > x.high) throw Failure("reversed ioctl range");
            }
            for (auto &s : sources) for (auto &t : targets) for (auto &c : classes) {
                if (t == "self" && s == "*") throw Failure("self with wildcard source is unsupported");
                auto target = t == "self" ? s : t;
                emit(action + " " + s + " " + target + " " + c + " ioctl " + range,
                    [=](sepol_impl &db) { require(db.add_xperm_rule(wildcard(s), wildcard(target), wildcard(c), x, effect)); });
            }
        }
    } else if (action == "type_transition" || action == "type_change" || action == "type_member") {
        auto s = p.one(), t = p.one();
        auto classes = p.group(true);
        if (classes.size() != 1) throw Failure("type transition requires one class");
        auto d = p.one(); auto object = p.more() ? p.one() : std::string{}; p.end();
        if (!object.empty() && action != "type_transition") throw Failure("filename only allowed on type_transition");
        emit(rule.text, [=](sepol_impl &db) {
            if (!object.empty()) require(db.add_filename_trans(s, t, classes[0], d, object));
            else require(db.add_type_rule(s, t, classes[0], d, action == "type_transition" ? AVTAB_TRANSITION : action == "type_change" ? AVTAB_CHANGE : AVTAB_MEMBER));
        });
    } else if (action == "genfscon") {
        auto fs = p.one(), path = p.one();
        // Context includes ':' and is consumed directly from the original rule.
        auto begin = rule.text.find(path);
        auto context = rule.text.substr(begin + path.size());
        auto first = context.find_first_not_of(" \t");
        auto last = context.find_last_not_of(" \t\r;");
        if (first == std::string::npos) throw Failure("missing genfscon context");
        context = context.substr(first, last - first + 1);
        emit(rule.text, [=](sepol_impl &db) { require(db.add_genfscon(fs, path, context)); });
    } else if (action == "enforce" || action == "permissive") {
        auto types = p.group(); p.end();
        for (auto &type : types) emit(action + " " + type,
            [=](sepol_impl &db) { require(db.set_type_state(wildcard(type), action == "permissive")); });
    } else throw Failure("unsupported operation " + action);
    return out;
}
void apply_atomic(sepol_impl &db, const Atomic &atomic) {
    policy_diagnostic.clear();
    atomic.apply(db);
    if (!policy_diagnostic.empty()) throw Failure(policy_diagnostic);
}
size_t apply_rule(sepol_impl &db, const Rule &rule) {
    auto operations = parse_rule(rule);
    for (const auto &operation : operations) apply_atomic(db, operation);
    return operations.size();
}
void save(const std::string &path, const std::vector<char> &image);
void prepare_shared_context(const std::string &path, const std::string &text, const std::string &boot) {
    if (path != "/data/adb/modules/zygisk_lsposed/sepolicy.rule" ||
        !maple_shared_file_contract(text.c_str())) return;
    Fd directory(open("/data/adb/modules/zygisk_lsposed", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
    struct stat identity{}, library_stat{}, state_stat{};
    if (directory.value < 0 || fstat(directory.value, &identity) || identity.st_uid ||
        (identity.st_mode & (S_IWGRP | S_IWOTH))) return;
    char library[4096];
    if (!realpath("/data/adb/modules/maplesu_zygisk/lib64/liblsp-shared.so", library)) return;
    if (strpbrk(library, " \t\r\n")) return;
    Fd binary(open(library, O_RDONLY | O_NOFOLLOW | O_CLOEXEC));
    if (binary.value < 0 || fstat(binary.value, &library_stat) || !S_ISREG(library_stat.st_mode) ||
        library_stat.st_uid || (library_stat.st_mode & (S_IWGRP | S_IWOTH))) return;
    if (mkdir("/data/adb/maplesu_zygisk", 0700) && errno != EEXIST)
        system_error("create LSPosed shared-context directory");
    Fd state(open("/data/adb/maplesu_zygisk", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
    if (state.value < 0) system_error("open LSPosed shared-context directory");
    if (fstat(state.value, &state_stat)) system_error("stat LSPosed shared-context directory");
    if (state_stat.st_uid || (state_stat.st_mode & (S_IWGRP | S_IWOTH)))
        throw Failure("LSPosed shared-context directory is not root-owned private storage");
    std::string receipt = boot + " " + std::to_string(identity.st_dev) + " " +
        std::to_string(identity.st_ino) + " " + library + "\n";
    save(MAPLE_SHARED_CONTEXT_STATE, {receipt.begin(), receipt.end()});
}
bool shared_context_file(const std::string &path) {
    FILE *state = fopen(MAPLE_SHARED_CONTEXT_STATE, "re");
    if (!state) return false;
    char boot[40], current[40], library[4096];
    unsigned long long device = 0, inode = 0;
    bool valid = fscanf(state, "%39s %llu %llu %4095s", boot, &device, &inode, library) == 4;
    fclose(state);
    FILE *epoch = fopen("/proc/sys/kernel/random/boot_id", "re");
    if (!epoch) return false;
    valid = valid && fscanf(epoch, "%39s", current) == 1 && !strcmp(boot, current);
    fclose(epoch);
    auto slash = path.find_last_of('/');
    if (!valid || slash == std::string::npos || path.substr(slash + 1) != "sepolicy.rule") return false;
    struct stat parent;
    return stat(path.substr(0, slash).c_str(), &parent) == 0 &&
        parent.st_dev == device && parent.st_ino == inode && access(library, R_OK) == 0;
}
std::vector<Rule> read_rules(const Strings &paths, const std::string &prepare_boot = {}, bool module = false, bool syntax_only = false) {
    std::vector<Rule> rules;
    for (auto &path : paths) {
        auto bytes = read_file(path);
        if (std::find(bytes.begin(), bytes.end(), '\0') != bytes.end()) throw Failure("NUL byte in rule file " + path);
        std::string text(bytes.begin(), bytes.end());
        // Only an admitted boot policy transaction prepares this explicit module
        // adapter. Plain check/save and ordinary SDK calls remain read-only.
        if (!prepare_boot.empty()) prepare_shared_context(path, text, prepare_boot);
        bool shared = !syntax_only && shared_context_file(path);
        if (shared && !maple_shared_file_contract(text.c_str()))
            throw Failure("unsupported LSPosed shared-file contract: " + path);
        size_t line = 1, statement_line = 1, start = 0;
        bool quoted = false, comment = false;
        for (size_t i = 0; i <= text.size(); ++i) {
            if (module && i < text.size()) {
                if (text[i] == '#' && !quoted) comment = true;
                if (text[i] == '"' && !comment) quoted = !quoted;
            }
            if (i == text.size() || text[i] == '\n' || (module && text[i] == ';' && !quoted && !comment)) {
                auto statement = text.substr(start, i - start);
                int adapted = shared ? maple_shared_file_rule(statement.c_str()) : 0;
                if (adapted < 0) throw Failure("unsupported LSPosed shared-file contract: " + path);
                if (!adapted) rules.push_back({path, statement, statement_line});
                if (i < text.size() && text[i] == '\n') ++line;
                statement_line = line; start = i + 1;
                quoted = comment = false;
            }
        }
    }
    return rules;
}
bool apply_all(sepol_impl &policy, const std::vector<Rule> &rules, size_t &operations, bool report) {
    bool ok = true;
    for (auto &rule : rules) {
        try {
            size_t count = apply_rule(policy, rule); operations += count;
            if (report && count) printf("{\"event\":\"rule\",\"file\":%s,\"line\":%zu,\"status\":\"validated\",\"operations\":%zu}\n", quote(rule.file).c_str(), rule.line, count);
        } catch (const Failure &e) {
            ok = false;
            if (report) printf("{\"event\":\"rule\",\"file\":%s,\"line\":%zu,\"status\":\"error\",\"message\":%s}\n", quote(rule.file).c_str(), rule.line, quote(e.what()).c_str());
        }
    }
    return ok;
}
void verify_rules(sepol_impl &policy, const std::vector<Rule> &rules) {
    auto before = encode(policy);
    size_t operations = 0;
    if (!apply_all(policy, rules, operations, false)) throw Failure("semantic verification: replay rejected rules");
    if (before != encode(policy)) throw Failure("semantic verification: expected rules missing from policy");
}
struct ModuleResult {
    size_t attempted = 0, skipped = 0, failed = 0, syntax_errors = 0;
    bool fatal = false;
    std::vector<Atomic> applied;
};
std::vector<Atomic> module_rules(const std::vector<Rule> &rules, ModuleResult &result) {
    std::vector<Atomic> out;
    for (const auto &rule : rules) {
        try {
            auto expanded = parse_rule(rule, true);
            out.insert(out.end(), expanded.begin(), expanded.end());
        } catch (const Failure &e) {
            ++result.syntax_errors;
            printf("{\"event\":\"rule\",\"file\":%s,\"line\":%zu,\"status\":\"syntax_error\",\"message\":%s}\n",
                quote(rule.file).c_str(), rule.line, quote(e.what()).c_str());
        }
    }
    return out;
}
void verify_atomics(sepol_impl &policy, const std::vector<Atomic> &operations) {
    auto before = encode(policy);
    for (const auto &operation : operations) apply_atomic(policy, operation);
    if (before != encode(policy)) throw Failure("semantic verification: expected module operations missing from policy");
}
// Only ordinary symbol/operation incompatibilities may be skipped. Allocation,
// serialization and conditional-deny failures abort the entire transaction.
bool module_rejection(const std::string &message) {
    return !policy_diagnostic.empty() && message == policy_diagnostic &&
        (message.find("does not exist") != std::string::npos ||
         message.find("different flavor") != std::string::npos ||
         message.find("is an attribute") != std::string::npos ||
         message.find("is not an attribute") != std::string::npos ||
         message.find("No class is specified") != std::string::npos ||
         message.find("does not support") != std::string::npos);
}
void apply_module(std::unique_ptr<sepol_impl> &policy, const std::vector<Rule> &rules, ModuleResult &result) {
    auto operations = module_rules(rules, result);
    auto baseline = encode(*policy);
    for (const auto &operation : operations) {
        ++result.attempted;
        std::string status = "applied", message;
        try {
            apply_atomic(*policy, operation);
            result.applied.push_back(operation);
        } catch (const Failure &e) {
            message = e.what();
            if (module_rejection(message)) { ++result.skipped; status = "skipped"; }
            else { ++result.failed; result.fatal = true; status = "failed"; }
            // Some mutators can reject after a partial change (type + attr,
            // inherited deny). Rebuild only on rejection, from the frozen
            // baseline and successful subset; no failed mutation is published.
            policy = decode(baseline);
            for (const auto &accepted : result.applied) apply_atomic(*policy, accepted);
        }
        printf("{\"event\":\"atomic\",\"file\":%s,\"line\":%zu,\"atomic_index\":%zu,\"operation\":%s,\"status\":%s,\"message\":%s}\n",
            quote(operation.rule.file).c_str(), operation.rule.line, result.attempted,
            quote(operation.rule.text).c_str(), quote(status).c_str(), quote(message).c_str());
        if (result.fatal) break;
    }
}
void module_counts(const ModuleResult &result) {
    printf(",\"attempted_operations\":%zu,\"applied_operations\":%zu,\"skipped_operations\":%zu,\"failed_operations\":%zu,\"failed_statements\":%zu",
        result.attempted, result.applied.size(), result.skipped, result.failed, result.syntax_errors);
}
char enforcing_state() {
    auto value = read_file(ENFORCE);
    if (value.empty() || (value[0] != '0' && value[0] != '1')) throw Failure("invalid global SELinux state");
    return value[0];
}
void write_once(int fd, const std::vector<char> &image, bool module_write = false) {
    ssize_t n;
#ifdef MAPLESU_POLICY_HOOK_BACKEND
    if (module_write && maplesu::module_policy::hook_policy_backend_present()) {
        maplesu::module_policy::HookKernelIo io;
        n = io.query().prepared ? io.write_policy(fd, image.data(), image.size()) : write(fd, image.data(), image.size());
    } else
#else
    (void)module_write;
#endif
        n = write(fd, image.data(), image.size());
    if (n < 0 || static_cast<size_t>(n) != image.size()) {
        int code = n < 0 ? errno : 0;
        Failure failure(std::string(n < 0 ? "policy write failed: " : "short policy write: ") + "requested_bytes=" + std::to_string(image.size()) + " written_bytes=" + std::to_string(n) + " errno=" + std::to_string(code));
        failure.error_number = code;
        failure.requested_bytes = image.size();
        failure.written_bytes = n;
        failure.write_failure = true;
        throw failure;
    }
}
void save(const std::string &path, const std::vector<char> &image) {
    std::string tmp = path + ".XXXXXX";
    std::vector<char> name(tmp.begin(), tmp.end()); name.push_back(0);
    Fd fd(mkstemp(name.data()));
    if (fd.value < 0) system_error("create output temporary file");
    try {
        write_once(fd.value, image);
        if (fsync(fd.value)) system_error("fsync output");
        if (rename(name.data(), path.c_str())) system_error("rename output");
    } catch (...) { unlink(name.data()); throw; }
}
int run(int argc, char **argv) {
    if (argc < 2) throw Failure("usage: maplesu-policy check|save|apply|restore|module-check|module-apply [--policy FILE] [--rules FILE]... [--output FILE]");
    if (std::string_view(argv[1]) == "boot-sdk-info" || std::string_view(argv[1]) == "boot-sdk-start")
        return boot_sdk_control(argc, argv);
#ifdef MAPLESU_POLICY_HOOK_BACKEND
    if (std::string_view(argv[1]) == "hook-info" || std::string_view(argv[1]) == "hook-load")
        return policy_hook_loader(argc, argv);
#endif
    if (std::string_view(argv[1]) == "ksu-manager-fd") return ksu_manager_fd(argc, argv);
    if (std::string_view(argv[1]) == "jailbreak-hide") return jailbreak_selinux_hide(argc, argv);
    if (std::string_view(argv[1]) == "prepare-hook" || std::string_view(argv[1]) == "config-hook") {
        using namespace maplesu::module_policy;
        const bool configure = std::string_view(argv[1]) == "config-hook";
        if (getuid() != 0 || geteuid() != 0) throw Failure("MODULE_POLICY_HOOK_ROOT_REQUIRED");
        std::optional<Mode> requested;
        bool allow_unverified = false;
        if (configure) {
            if (argc != 3 || (std::string_view(argv[2]) != "0" && std::string_view(argv[2]) != "1"
#ifdef MAPLESU_POLICY_HOOK_BACKEND
                && std::string_view(argv[2]) != "3" && std::string_view(argv[2]) != "7"
#endif
                ))
                throw Failure("MODULE_POLICY_HOOK_HIDE_UNSUPPORTED");
            requested = static_cast<Mode>((argv[2][0] - '0') & 3);
            allow_unverified = argv[2][0] == '7';
        } else if (argc != 2) throw Failure("usage: maplesu-policy prepare-hook");
        // A temporary Root session may have no boot-installed /data/adb yet.
        // Create only the missing directory; never change an existing one's
        // ownership, permissions or symlink target. Store validates it below.
        Fd data(open("/data", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
        if (data.value < 0) system_error("open data directory");
        if (mkdirat(data.value, "adb", 0700) && errno != EEXIST) system_error("create adb directory");
        auto store = Store::open(true);
        // Gate changes and live policy writes share one non-blocking lock. A
        // concurrent transaction finishes unchanged; the caller may retry later.
        Fd transaction(open("/data/adb/maplesu-policy.lock", O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, 0600));
        if (transaction.value < 0) system_error("policy transaction lock");
        if (flock(transaction.value, LOCK_EX | LOCK_NB)) {
            if (errno == EWOULDBLOCK || errno == EAGAIN) throw Failure("MODULE_POLICY_HOOK_BUSY");
            system_error("policy transaction lock");
        }
        unsigned (*prepare)(Mode, bool) = nullptr;
#ifdef MAPLESU_POLICY_HOOK_BACKEND
        if (hook_policy_backend_present()) prepare = prepare_hook_kernel_policy;
#endif
#ifdef MAPLESU_POLICY_HOOK_BACKEND
        auto current = store.read(current_boot_id());
        if (!configure && current.effective && current.effective->mode == Mode::Hide &&
            observe_hook_policy_state(current, store.owns_hook(current_boot_id())).hide_failed) {
            requested = Mode::Hide;
            allow_unverified = current.unverified_hide;
        }
#endif
        store.prepare_hook(current_boot_id(), requested, prepare, allow_unverified);
        printf("%s\n", json(store.read(current_boot_id())).c_str());
        return 0;
    }
    if (std::string_view(argv[1]) == "config") {
        using namespace maplesu::module_policy;
        if (argc > 3) throw Failure("usage: maplesu-policy config [0|1|3]");
        auto store = Store::open(argc == 3);
        auto boot = current_boot_id();
        if (argc == 3) {
            std::string_view value(argv[2]);
            if (value.size() != 1 || !valid_mode(static_cast<unsigned>(value[0] - '0')))
                throw Failure("MODULE_POLICY_CONFIG_MODE");
            store.request(static_cast<Mode>(value[0] - '0'), boot);
        }
        auto state = store.read(boot);
#ifdef MAPLESU_POLICY_HOOK_BACKEND
        state = observe_hook_policy_state(state, store.owns_hook(boot));
#endif
        printf("%s\n", json(state).c_str());
        return 0;
    }
    std::string mode = argv[1], policy_path = LIVE, output;
    Strings paths;
    bool require_boot_policy = false;
    for (int i = 2; i < argc; ++i) {
        std::string option = argv[i];
        if (option == "--require-boot-policy") { require_boot_policy = true; continue; }
        if (++i >= argc) throw Failure("missing value for " + option);
        if (option == "--policy") policy_path = argv[i];
        else if (option == "--rules") paths.emplace_back(argv[i]);
        else if (option == "--output") output = argv[i];
        else throw Failure("unknown option " + option);
    }
    const bool module = mode == "module-apply", syntax_only = mode == "module-check";
    if (mode != "check" && mode != "save" && mode != "apply" && mode != "restore" && !module && !syntax_only) throw Failure("unknown mode " + mode);
    if (mode == "save" && output.empty()) throw Failure("save requires --output");
    if (mode != "save" && !output.empty()) throw Failure("--output is valid only with save");
    if ((mode == "apply" || module) && policy_path != LIVE) throw Failure("apply requires current live policy");
    bool restore = mode == "restore", transaction = mode == "apply" || module || restore;
    if (require_boot_policy && !transaction) throw Failure("--require-boot-policy is valid only with apply or restore");
    if (restore && (policy_path == LIVE || !paths.empty())) throw Failure("restore requires --policy BACKUP without --rules");
    if (syntax_only) {
        if (policy_path != LIVE) throw Failure("module-check accepts only --rules");
        ModuleResult result;
        auto operations = module_rules(read_rules(paths, {}, true, true), result);
        result.attempted = operations.size();
        printf("{\"event\":\"result\",\"status\":%s,\"committed\":false,\"expanded_operations\":%zu",
            quote(result.syntax_errors ? "validation_failed" : "checked").c_str(), operations.size());
        module_counts(result); puts("}");
        return result.syntax_errors ? 2 : 0;
    }
    const auto boot = transaction ? maplesu::module_policy::current_boot_id() : std::string{};
    auto blocked = [&] {
        if (!maplesu::module_policy::Store::open(false).blocks_transactions(boot, require_boot_policy)) return false;
        printf("{\"event\":\"result\",\"status\":\"blocked\",\"committed\":false,\"reason\":\"MODULE_POLICY_LOAD_DISABLED\"}\n");
        return true;
    };
    if (transaction && blocked()) return 77;
    Fd lock(transaction ? open("/data/adb/maplesu-policy.lock", O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, 0600) : -1);
    char initial_enforcing = 0;
    if (transaction) {
        if (geteuid() != 0) throw Failure("policy transaction requires UID 0");
        if (lock.value < 0) system_error("policy transaction lock");
        if (flock(lock.value, LOCK_EX | LOCK_NB)) {
            if (errno == EWOULDBLOCK || errno == EAGAIN) {
                printf("{\"event\":\"result\",\"status\":\"busy\",\"committed\":false}\n");
                return 75;
            }
            system_error("policy transaction lock");
        }
        initial_enforcing = enforcing_state();
    }
    auto rules = read_rules(paths, (mode == "apply" || module) && require_boot_policy ? boot : std::string{}, module);
    auto live_before = restore ? read_file(LIVE) : std::vector<char>{};
    auto baseline = read_file(policy_path);
    auto policy = decode(baseline);
#ifdef __ANDROID__
    if (policy_path == LIVE || restore) restore_android_netlink(*policy, android_get_device_api_level());
#endif
    unsigned version = policy->db->policyvers;
    size_t operations = 0;
    ModuleResult module_result;
    bool valid;
    if (module) {
        apply_module(policy, rules, module_result);
        operations = module_result.applied.size();
        valid = !module_result.fatal && (operations || (!module_result.attempted && !module_result.syntax_errors));
    } else valid = apply_all(*policy, rules, operations, true);
    if (!valid) {
        printf("{\"event\":\"result\",\"status\":\"validation_failed\",\"committed\":false,\"expanded_operations\":%zu", operations);
        if (module) module_counts(module_result);
        puts("}");
        return 2;
    }
    if (module && !operations) {
        printf("{\"event\":\"result\",\"status\":\"unchanged\",\"committed\":false");
        module_counts(module_result); puts("}");
        return 0;
    }
    auto image = encode(*policy);
    auto checked = decode(image);
    if (module) verify_atomics(*checked, module_result.applied);
    else verify_rules(*checked, rules);
    if (mode == "save") save(output, image);
    if (transaction) {
        if ((restore ? live_before : baseline) != read_file(LIVE)) throw Failure("live policy changed before commit; retry from the new baseline");
        // Re-read the rule files immediately before committing the frozen candidate.
        auto current_rules = read_rules(paths, {}, module);
        if (current_rules.size() != rules.size()) throw Failure("rule files changed before commit");
        for (size_t i = 0; i < rules.size(); ++i) if (rules[i].text != current_rules[i].text) throw Failure("rule files changed before commit");
        if (enforcing_state() != initial_enforcing) throw Failure("global SELinux state changed before commit");
        if (blocked()) return 77;
        Fd fd(open(LOAD, O_WRONLY | O_CLOEXEC | O_NOFOLLOW));
        if (fd.value < 0) system_error("open SELinux load");
        write_once(fd.value, image, true);
        try {
            if (enforcing_state() != initial_enforcing) throw Failure("global SELinux state changed during commit");
            auto loaded = read_file(LIVE);
            auto actual = decode(loaded);
            if (restore) {
#ifdef __ANDROID__
                restore_android_netlink(*actual, android_get_device_api_level());
#endif
                if (!policy_equivalent(*checked, *actual)) throw Failure("restored policy differs from complete backup");
            } else if (module) verify_atomics(*actual, module_result.applied);
            else verify_rules(*actual, rules);
        } catch (const std::exception &e) {
            printf("{\"event\":\"result\",\"status\":\"applied_verification_failed\",\"committed\":true,\"written_bytes\":%zu,\"message\":%s", image.size(), quote(e.what()).c_str());
            if (module) module_counts(module_result);
            puts("}");
            return 3;
        }
    }
    printf("{\"event\":\"result\",\"status\":%s,\"committed\":%s,\"policy_version\":%u,\"input_bytes\":%zu,\"candidate_bytes\":%zu,\"written_bytes\":%zu,\"expanded_operations\":%zu", quote(restore ? "restored" : module ? (module_result.skipped || module_result.syntax_errors ? "applied_partial" : "applied") : mode == "apply" ? "applied" : mode == "save" ? "saved" : "checked").c_str(), transaction ? "true" : "false", version, baseline.size(), image.size(), mode == "check" ? 0 : image.size(), operations);
    if (module) module_counts(module_result);
    puts("}");
    return 0;
}
} // namespace
int main(int argc, char **argv) {
    try { return run(argc, argv); }
    catch (const Failure &e) {
        printf("{\"event\":\"result\",\"status\":\"error\",\"committed\":false,\"message\":%s", quote(e.what()).c_str());
        if (e.error_number >= 0) printf(",\"errno\":%d", e.error_number);
        if (e.write_failure) printf(",\"requested_bytes\":%zu,\"written_bytes\":%zd", e.requested_bytes, e.written_bytes);
        puts("}");
        return 1;
    }
    catch (const std::exception &e) {
        printf("{\"event\":\"result\",\"status\":\"error\",\"committed\":false,\"message\":%s}\n", quote(e.what()).c_str());
        return 1;
    }
}
