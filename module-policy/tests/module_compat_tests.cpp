// Real CLI/parser/libsepol coverage. Only selinuxfs is redirected to fixture files.
#ifndef MAPLE_POLICY_MAIN
#define MAPLE_POLICY_MAIN "../src/main.cpp"
#endif
#define main policy_cli_main
#include MAPLE_POLICY_MAIN
#undef main
#include <cassert>
#include <cstdarg>
#include <sepol/policydb/conditional.h>
extern "C" int policydb_index_others(sepol_handle_t *, policydb_t *, unsigned int);

int boot_sdk_control(int, char **) { return 99; }
int jailbreak_selinux_hide(int, char **) { return 99; }
int ksu_manager_fd(int, char **) { return 99; }
static std::string fixture;
static int load_fd = -1, loads, live_reads, enforce_reads, lock_opens, checks;
static bool fail_load, stale_readback;
extern "C" int __real_open(const char *, int, ...);
extern "C" ssize_t __real_write(int, const void *, size_t);
extern "C" int __real_close(int);
extern "C" int __wrap_open(const char *name, int flags, ...) {
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list args; va_start(args, flags); mode = va_arg(args, int); va_end(args);
    }
    std::string path(name);
    if (path == LIVE) { ++live_reads; path = fixture + "/live"; }
    else if (path == LOAD) path = fixture + "/load";
    else if (path == ENFORCE) { ++enforce_reads; path = fixture + "/enforce"; }
    else if (path == "/data/adb/maplesu-policy.lock") ++lock_opens;
    int fd = __real_open(path.c_str(), flags, mode);
    if (!strcmp(name, LOAD)) load_fd = fd;
    return fd;
}
extern "C" int __wrap_close(int fd) {
    if (fd == load_fd) load_fd = -1;
    return __real_close(fd);
}
extern "C" ssize_t __wrap_write(int fd, const void *data, size_t size) {
    if (fd != load_fd) return __real_write(fd, data, size);
    ++loads;
    if (fail_load) { errno = EIO; return -1; }
    if (!stale_readback) {
        int live = __real_open((fixture + "/live").c_str(), O_WRONLY | O_TRUNC | O_CLOEXEC);
        assert(live >= 0 && __real_write(live, data, size) == static_cast<ssize_t>(size));
        close(live);
    }
    return __real_write(fd, data, size);
}
static int command(std::initializer_list<const char *> args) {
    std::vector<char *> argv;
    for (const char *arg : args) argv.push_back(const_cast<char *>(arg));
    return policy_cli_main(argv.size(), argv.data());
}
static void put(const std::string &path, const std::string &text) { save(path, {text.begin(), text.end()}); }
static void pass(const char *name) { ++checks; printf("{\"test\":%s,\"status\":\"passed\"}\n", quote(name).c_str()); }
static type_datum_t *type(sepol_impl &policy, const char *name) {
    return static_cast<type_datum_t *>(hashtab_search(policy.db->p_types.table, name));
}
static bool grant(sepol_impl &policy, const char *source, const char *target, const char *permission) {
    auto cls = static_cast<class_datum_t *>(hashtab_search(policy.db->p_classes.table, "file"));
    auto perm = static_cast<perm_datum_t *>(hashtab_search(cls->permissions.table, permission));
    if (!perm && cls->comdatum) perm = static_cast<perm_datum_t *>(hashtab_search(cls->comdatum->permissions.table, permission));
    assert(perm && type(policy, source) && type(policy, target));
    avtab_key_t key{static_cast<uint16_t>(type(policy, source)->s.value), static_cast<uint16_t>(type(policy, target)->s.value),
        static_cast<uint16_t>(cls->s.value), AVTAB_ALLOWED};
    auto node = avtab_search_node(&policy.db->te_avtab, &key);
    return node && (node->datum.data & (1U << (perm->s.value - 1)));
}
static void conditional(sepol_impl &policy) {
    auto db = policy.db;
    auto datum = static_cast<cond_bool_datum_t *>(calloc(1, sizeof(cond_bool_datum_t)));
    uint32_t value = 0;
    assert(symtab_insert(db, SYM_BOOLS, strdup("maplesu_test_bool"), datum, SCOPE_DECL, 1, &value) == 0);
    datum->s.value = value; datum->state = 1;
    assert(policydb_index_others(nullptr, db, 0) == 0);
    auto cls = static_cast<class_datum_t *>(hashtab_search(db->p_classes.table, "file"));
    auto perm = static_cast<perm_datum_t *>(hashtab_search(cls->permissions.table, "read"));
    if (!perm) perm = static_cast<perm_datum_t *>(hashtab_search(cls->comdatum->permissions.table, "read"));
    if (!db->te_cond_avtab.nslot) assert(avtab_alloc(&db->te_cond_avtab, 1) == 0);
    avtab_key_t key{static_cast<uint16_t>(type(policy, "domain")->s.value),
        static_cast<uint16_t>(type(policy, "fixture_file")->s.value), static_cast<uint16_t>(cls->s.value), AVTAB_ALLOWED | AVTAB_ENABLED};
    avtab_datum_t data{1U << (perm->s.value - 1), nullptr};
    auto node = avtab_insert_nonunique(&db->te_cond_avtab, &key, &data);
    assert(node);
    node->parse_context = node; // libsepol serializes only admitted conditional nodes.
    auto condition = static_cast<cond_node_t *>(calloc(1, sizeof(cond_node_t)));
    condition->cur_state = 1;
    condition->expr = static_cast<cond_expr_t *>(calloc(1, sizeof(cond_expr_t)));
    condition->expr->expr_type = COND_BOOL; condition->expr->boolean = value;
    condition->true_list = static_cast<cond_av_list_t *>(calloc(1, sizeof(cond_av_list_t)));
    condition->true_list->node = node;
    condition->next = db->cond_list; db->cond_list = condition;
}
int main(int argc, char **argv) {
    setbuf(stdout, nullptr);
    assert(argc == 4);
    fixture = argv[2];
    auto original = read_file(argv[1]);
    auto policy = decode(original);
    bool nsfs = hashtab_search(policy->db->p_types.table, "nsfs") != nullptr;
    printf("{\"fixture_nsfs_present\":%s}\n", nsfs ? "true" : "false");
    assert(!nsfs);
    save(fixture + "/live", original); put(fixture + "/enforce", "1\n"); put(fixture + "/load", "");
    std::string rules = fixture + "/official.rule";
    save(rules, read_file(argv[3]));
#ifdef MAPLE_MODULE_RED
    int result = command({"policy", "check", "--rules", rules.c_str()});
    printf("{\"red_original_check_exit\":%d,\"live_reads\":%d,\"loads\":%d}\n", result, live_reads, loads);
    assert(result == 2 && loads == 0);
    // Desired module check contract fails on the pre-repair production implementation.
    return result == 0 ? 0 : 1;
#else
    assert(command({"policy", "module-check", "--rules", rules.c_str()}) == 0);
    assert(live_reads == 0 && enforce_reads == 0 && lock_opens == 0 && loads == 0);
    pass("original module syntax check has zero live, enforcing, lock and load accesses");
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 77 && loads == 0);
    pass("module apply retains the boot policy gate");
    using namespace maplesu::module_policy;
    auto store = Store::open(true);
    store.publish({Mode::Load, 3, current_boot_id()});
    assert(command({"policy", "check", "--rules", rules.c_str()}) == 2 && loads == 0);
    assert(command({"policy", "apply", "--require-boot-policy", "--rules", rules.c_str()}) == 2 && loads == 0);
    pass("legacy check and apply retain whole-batch strict failure");
    auto parsed = read_rules({rules}, {}, true);
    ModuleResult partial;
    apply_module(policy, parsed, partial);
    assert(!partial.fatal && partial.attempted == 12 && partial.applied.size() == 10 && partial.skipped == 2);
    assert(!type(*policy, "nsfs") && type(*policy, "unshare_lock_file"));
    for (auto permission : {"read", "write", "lock"}) assert(grant(*policy, "zygote", "unshare_lock_file", permission));
    auto image = encode(*policy); auto copy = decode(image);
    verify_atomics(*copy, partial.applied);
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 0 && loads == 1);
    auto live = read_file(fixture + "/live"); copy = decode(live);
    assert(!type(*copy, "nsfs") && grant(*copy, "zygote", "unshare_lock_file", "lock"));
    pass("missing nsfs skips exactly two atomics; ten others survive candidate and live readback");
    policy = decode(original);
    apply_rule(*policy, {"fixture", "type nsfs { file_type mlstrustedobject }", 1});
    auto with_nsfs = encode(*policy); save(fixture + "/with-nsfs.policy", with_nsfs);
    save(fixture + "/live", with_nsfs);
    ModuleResult complete;
    apply_module(policy, parsed, complete);
    assert(complete.applied.size() == 12 && complete.skipped == 0 && !complete.fatal);
    assert(grant(*policy, "zygote", "nsfs", "open") && grant(*policy, "zygote", "nsfs", "read"));
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 0 && loads == 2);
    pass("fixture with nsfs applies all twelve original atomics");
    policy = decode(original);
    apply_rule(*policy, {"fixture", "type fixture_file { file_type mlstrustedobject }", 1});
    auto base = encode(*policy); save(fixture + "/fixture-file.policy", base);
    save(fixture + "/live", base);
    put(rules, "allow zygote { fixture_file MISSING_TARGET zygote } file { read write }\n");
    ModuleResult braces;
    apply_module(policy, read_rules({rules}, {}, true), braces);
    assert(braces.applied.size() == 4 && braces.skipped == 2 && braces.attempted == 6);
    assert(grant(*policy, "zygote", "fixture_file", "read") && grant(*policy, "zygote", "zygote", "write"));
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 0 && loads == 3);
    pass("brace members before and after missing symbols are counted and committed individually");
    save(fixture + "/live", base);
    put(rules, "type phantom_type MISSING_ATTRIBUTE; allow zygote fixture_file file write\n");
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 0 && loads == 4);
    live = read_file(fixture + "/live"); copy = decode(live);
    assert(!type(*copy, "phantom_type") && grant(*copy, "zygote", "fixture_file", "write"));
    pass("failed declaration rolls back its partial type creation; semicolon statements continue");
    save(fixture + "/live", base);
    put(rules, "bogus malformed; allow zygote fixture_file file read\n");
    assert(command({"policy", "module-check", "--rules", rules.c_str()}) == 2);
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 0 && loads == 5);
    pass("syntax check rejects malformed input; module live path preserves valid statements");
    save(fixture + "/live", base);
    put(rules, "allow zygote MISSING_TARGET file { read write }\n");
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 2 && loads == 5);
    assert(read_file(fixture + "/live") == base);
    put(rules, "bogus malformed\n");
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 2 && loads == 5);
    pass("all rejected and all malformed inputs return nonzero without a load");
    put(rules, "# empty module policy\n");
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 0 && loads == 5);
    pass("empty policies remain unchanged without a load");
    put(rules, "allow zygote fixture_file file write\n");
    fail_load = true;
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 1 && loads == 6);
    assert(read_file(fixture + "/live") == base); fail_load = false;
    stale_readback = true;
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 3 && loads == 7);
    stale_readback = false;
    pass("write EIO and committed readback failure retain distinct exit and commit facts");
    policy = decode(base); conditional(*policy);
    auto condition_image = encode(*policy); save(fixture + "/conditional.policy", condition_image);
    copy = decode(condition_image);
    assert(copy->db->te_cond_avtab.nel == 1);
    save(fixture + "/live", condition_image);
    put(rules, "allow zygote fixture_file file write\ndeny zygote fixture_file file read\n");
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 2 && loads == 7);
    assert(read_file(fixture + "/live") == condition_image);
    pass("conditional deny aborts the transaction even after an earlier successful atomic");
    put(rules, "attribute fixture_attribute; type fixture_domain; attradd fixture_domain fixture_attribute\n");
    save(fixture + "/live", base);
    assert(command({"policy", "module-apply", "--require-boot-policy", "--rules", rules.c_str()}) == 0 && loads == 8);
    live = read_file(fixture + "/live"); copy = decode(live);
    auto declared = type(*copy, "fixture_domain");
    assert(declared && ebitmap_get_bit(&copy->db->type_attr_map[declared->s.value - 1], type(*copy, "domain")->s.value - 1));
    pass("module type defaults to domain and attradd alias is preserved");
    put(rules, "allowxperm zygote zygote chr_file ioctl { 0x100-0x101 0x200 }; type_transition zygote fixture_file file fixture_file \"name;with:semicolon\"\n");
    assert(command({"policy", "module-check", "--rules", rules.c_str()}) == 0);
    int previous = loads;
    assert(command({"policy", "module-check", "--rules", "/missing-rule-file"}) == 1 && loads == previous);
    std::string nul_rule = "allow zygote zygote file read"; nul_rule.push_back('\0'); put(rules, nul_rule);
    assert(command({"policy", "module-check", "--rules", rules.c_str()}) == 1 && loads == previous);
    pass("xperm and quoted semicolons parse; file and NUL input failures remain errors");
    printf("{\"tests_passed\":%d,\"selinuxfs_fixture_writes\":%d,\"device_access\":false}\n", checks, loads);
    return 0;
#endif
}
