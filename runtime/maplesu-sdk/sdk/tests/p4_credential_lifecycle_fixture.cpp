// Exact production transaction bodies with host ownership and interleaving seams.
#include <algorithm>
#include <cassert>
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <vector>

using u32 = std::uint32_t;
using u64 = std::uint64_t;
using uid_t = std::uint32_t;
using gid_t = std::uint32_t;
using kuid_t = uid_t;
using kgid_t = gid_t;
using kernel_cap_t = u64;
#define CONFIG_MULTIUSER 1
#ifndef CAP_LAST_CAP
#define CAP_LAST_CAP 40
#endif
#ifndef MSU_MANAGEMENT_CRED_UCOUNTS
#define MSU_MANAGEMENT_CRED_UCOUNTS 1
#endif
#define MSU_CRED_PROFILE_MAX_GROUPS 32U
#define MSU_CRED_PROFILE_DOMAIN_BYTES 64U
#define MSU_CRED_CAP_INHERITABLE (1U << 0)
#define MSU_CRED_CAP_PERMITTED (1U << 1)
#define MSU_CRED_CAP_EFFECTIVE (1U << 2)
#define MSU_CRED_CAP_BOUNDING (1U << 3)
#define MSU_CRED_CAP_AMBIENT (1U << 4)
#define MSU_CRED_CAP_ALL ((1U << 5) - 1)
#define MAPLESU_EFFECT_COMMITTED 8u
#define READ_ONCE(value) (value)
// Host-only layout seam: bind the exact transaction to the fixture structs.
// Runtime offset discovery/qualification is outside this ownership test.
#define MSU_DYN_FIELD(object, type, member) ((object)->member)
#define CAP_EMPTY_SET 0ULL
#define cap_raise(set, bit) ((set) |= (1ULL << (bit)))
#ifndef ESTALE
#define ESTALE 116
#endif
#ifndef EDQUOT
#define EDQUOT 122
#endif

struct user_namespace { unsigned refs = 0; };
user_namespace init_user_ns, other_user_ns;
struct user_struct { unsigned refs = 1; kuid_t uid = 1000; };
struct atomic_t { unsigned refs = 1; };
struct refcount_t { unsigned refs = 1; };
#ifndef MSU_GROUP_INFO_REFCOUNT
#define MSU_GROUP_INFO_REFCOUNT 0
#endif
#if MSU_GROUP_INFO_REFCOUNT
using group_usage = refcount_t;
#else
using group_usage = atomic_t;
#endif
struct group_info { group_usage usage; unsigned count = 0; kgid_t gid[MSU_CRED_PROFILE_MAX_GROUPS]{}; };
struct ucounts { unsigned refs = 1; kuid_t uid = 1000; };
struct security_blob { u32 osid = 11, sid = 12, exec_sid = 13, create_sid = 14, keycreate_sid = 15, sockcreate_sid = 16; };
struct mm_struct { unsigned refs = 1; };
struct cred {
    unsigned refs = 1;
    unsigned non_rcu = 0;
    user_namespace* user_ns = &init_user_ns;
    user_struct* user = nullptr;
    group_info* group_info = nullptr;
    ucounts* ucounts = nullptr;
    uid_t uid = 1000, suid = 1001, euid = 1002, fsuid = 1003;
    gid_t gid = 2000, sgid = 2001, egid = 2002, fsgid = 2003;
    u32 securebits = 7;
    kernel_cap_t cap_inheritable = 0x0f, cap_permitted = 0x3f, cap_effective = 0x03;
    kernel_cap_t cap_bset = 0x7f, cap_ambient = 0x01;
    security_blob* security = nullptr;
};
struct task_struct { const struct cred* cred = nullptr; const struct cred* real_cred = nullptr; mm_struct* mm = nullptr; u64 self_exec_id = 9; };
thread_local task_struct* current = nullptr;
struct maplesu_caps_v3 { u64 inheritable, permitted, effective, bounding, ambient; };
struct msu_credential_profile {
    u32 uid, gid, groups_count;
    u32 groups[MSU_CRED_PROFILE_MAX_GROUPS];
    maplesu_caps_v3 caps;
    u32 cap_write_mask;
    bool replace_ids, replace_groups, clear_securebits;
    char selinux_domain[MSU_CRED_PROFILE_DOMAIN_BYTES];
};
struct credential_snapshot : cred {
    struct group_info saved_groups;
    struct user_struct saved_user;
    struct ucounts saved_ucounts;
    explicit credential_snapshot(const struct cred& source) : cred(source),
        saved_groups(*source.group_info), saved_user(*source.user), saved_ucounts(*source.ucounts) {}
};

namespace fixture {
enum class Mutation { None, Exec, Mm, Override, Task, Profile };
bool sleepable, fail_prepare, fail_alloc_uid, fail_groups, fail_ucounts, domain_supported;
int commit_error, validate_error, validate_error_at, adopt_error, domain_error;
unsigned prepares, aborts, commits, validates, adopts, uid_allocs, group_allocs, ucount_sets, domain_prepares;
unsigned live_creds = 0, live_users = 0, live_groups = 0, live_ucounts = 0, live_security = 0, live_mm = 0;
unsigned group_sets, group_local_drops, group_created, group_destroyed;
unsigned checks = 0, cases = 0;
int mutation_at;
Mutation mutation, adopt_mutation, commit_mutation;
bool retain_adopted;
const cred* adopted_reference;
const cred* committed;
const cred* adopted_before;
const cred* adopted_after;
msu_credential_profile* input_profile;
task_struct* original_task;
std::vector<task_struct*> tasks;
const char* case_name = "setup";
void check(bool value, const char* message) {
    ++checks;
    if (!value) { std::cerr << "FAIL [" << case_name << "]: " << message << '\n'; std::abort(); }
}
}

static user_struct* alloc_uid(kuid_t uid) {
    ++fixture::uid_allocs;
    if (fixture::fail_alloc_uid) return nullptr;
    ++fixture::live_users;
    return new user_struct{1, uid};
}
static void free_uid(user_struct* value) {
    assert(value && value->refs);
    if (!--value->refs) { --fixture::live_users; delete value; }
}
static group_info* groups_alloc(unsigned count) {
    ++fixture::group_allocs;
    if (fixture::fail_groups || count > MSU_CRED_PROFILE_MAX_GROUPS) return nullptr;
    auto* value = new group_info;
    value->count = count;
    ++fixture::live_groups;
    ++fixture::group_created;
    return value;
}
static void groups_sort(group_info* value) { std::sort(value->gid, value->gid + value->count); }
static void put_group_info(group_info* value) {
    assert(value && value->usage.refs);
    if (!--value->usage.refs) {
        ++fixture::group_destroyed;
        --fixture::live_groups;
        delete value;
    }
}
static void atomic_dec(atomic_t* value) {
    fixture::check(value->refs == 2, "local atomic group reference is non-last after native setter");
    --value->refs;
    ++fixture::group_local_drops;
}
static void refcount_dec(refcount_t* value) {
    fixture::check(value->refs == 2, "local refcount group reference is non-last after native setter");
    --value->refs;
    ++fixture::group_local_drops;
}
static void set_groups(cred* value, group_info* groups) {
    fixture::check(groups->usage.refs == 1, "new group starts with its one owned allocation reference");
    put_group_info(value->group_info);
    ++groups->usage.refs;
    value->group_info = groups;
    ++fixture::group_sets;
}
static void put_ucounts(ucounts* value) {
    assert(value && value->refs);
    if (!--value->refs) { --fixture::live_ucounts; delete value; }
}
#if MSU_MANAGEMENT_CRED_UCOUNTS
static int set_cred_ucounts(cred* value) {
    ++fixture::ucount_sets;
    if (fixture::fail_ucounts) return -EDQUOT;
    auto* next = new ucounts{1, value->uid};
    ++fixture::live_ucounts;
    put_ucounts(value->ucounts);
    value->ucounts = next;
    return 0;
}
#endif
static const cred* get_cred(const cred* value) {
    assert(value && value->refs);
    ++const_cast<cred*>(value)->refs;
    return value;
}
static void put_cred(const cred* value) {
    if (!value) return;
    auto* owned = const_cast<cred*>(value);
    assert(owned->refs);
    if (--owned->refs) return;
    free_uid(owned->user);
    put_group_info(owned->group_info);
    put_ucounts(owned->ucounts);
    assert(owned->user_ns->refs);
    --owned->user_ns->refs;
    --fixture::live_security;
    delete owned->security;
    --fixture::live_creds;
    delete owned;
}
static cred* clone_cred(const cred* source) {
    auto* value = new cred(*source);
    value->refs = 1;
    ++value->user->refs;
    ++value->group_info->usage.refs;
    ++value->ucounts->refs;
    ++value->user_ns->refs;
    value->security = new security_blob(*source->security);
    ++fixture::live_security;
    ++fixture::live_creds;
    return value;
}
static bool msu_management_sleepable() { return fixture::sleepable; }
static bool uid_valid(kuid_t value) { return value != UINT32_MAX; }
static bool gid_valid(kgid_t value) { return value != UINT32_MAX; }
static kuid_t make_kuid(user_namespace*, uid_t value) { return value; }
static kgid_t make_kgid(user_namespace*, gid_t value) { return value; }
static const cred* current_cred() { return current->cred; }
static const cred* current_real_cred() { return current->real_cred; }
static const cred* get_current_cred() { return get_cred(current_cred()); }
static cred* prepare_creds() {
    ++fixture::prepares;
    return fixture::fail_prepare ? nullptr : clone_cred(current_cred());
}
static void abort_creds(cred* value) { ++fixture::aborts; put_cred(value); }
static mm_struct* new_mm() { ++fixture::live_mm; return new mm_struct; }
static void mmgrab(mm_struct* value) { assert(value->refs); ++value->refs; }
static void mmdrop(mm_struct* value) {
    assert(value->refs);
    if (!--value->refs) { --fixture::live_mm; delete value; }
}
// Match manager_dynamic_layout.h reference semantics with the existing host
// allocator/destructor model; no reference operation is an unconditional pass.
static const cred* msu_dynamic_current_cred() { return current_cred(); }
static const cred* msu_dynamic_current_real_cred() { return current_real_cred(); }
static const cred* msu_dynamic_get_cred(const cred* value) {
    if (!value) return nullptr;
    const_cast<cred*>(value)->non_rcu = 0;
    return get_cred(value);
}
static void msu_dynamic_put_cred(const cred* value) { put_cred(value); }
static const cred* msu_dynamic_get_current_cred() {
    return msu_dynamic_get_cred(msu_dynamic_current_cred());
}
static void msu_dynamic_mmgrab(mm_struct* value) { mmgrab(value); }
static void msu_dynamic_mmdrop(mm_struct* value) { mmdrop(value); }
static task_struct* share_task(task_struct* source) {
    auto* task = new task_struct{get_cred(source->cred), get_cred(source->real_cred), source->mm, source->self_exec_id};
    if (task->mm) mmgrab(task->mm);
    fixture::tasks.push_back(task);
    return task;
}
static void mutate(fixture::Mutation mutation) {
    switch (mutation) {
    case fixture::Mutation::None: break;
    case fixture::Mutation::Exec: ++current->self_exec_id; break;
    case fixture::Mutation::Mm: {
        auto* old = current->mm;
        current->mm = new_mm();
        if (old) mmdrop(old);
        break;
    }
    case fixture::Mutation::Override: {
        auto* replacement = clone_cred(current->cred);
        const auto* old = current->cred;
        current->cred = replacement; // One subjective reference; real_cred stays held.
        put_cred(old);
        break;
    }
    case fixture::Mutation::Task: current = share_task(current); break;
    case fixture::Mutation::Profile:
        assert(fixture::input_profile);
        fixture::input_profile->uid = 9999;
        fixture::input_profile->gid = 9998;
        fixture::input_profile->caps = {0, 0, 0, 0, 0};
        fixture::input_profile->groups[0] = 9997;
        break;
    }
}
static int commit_creds(cred* value) {
    ++fixture::commits;
    assert(current->cred == current->real_cred && value->refs >= 1);
    const auto* old = current->cred;
    // Consume prepare's caller reference for one task slot and acquire the other.
    get_cred(value);
    current->cred = current->real_cred = value;
    fixture::committed = value;
    put_cred(old);
    put_cred(old);
    mutate(fixture::commit_mutation);
    // Real target commit_creds returns zero. This injects an unexpected return
    // after consuming the prepared reference, matching the production contract.
    return fixture::commit_error;
}
static bool cap_issubset(kernel_cap_t a, kernel_cap_t b) { return (a & ~b) == 0; }
static kernel_cap_t cap_intersect(kernel_cap_t a, kernel_cap_t b) { return a & b; }
static bool cap_ambient_invariant_ok(const cred* value) {
    return (value->cap_ambient & ~(value->cap_permitted & value->cap_inheritable)) == 0;
}
static int msu_credential_domain_supported() { return fixture::domain_supported ? 0 : -EOPNOTSUPP; }
static int msu_credential_domain_prepare(cred* prepared, const char* domain, u32 length) {
    ++fixture::domain_prepares;
    fixture::check(prepared != current->cred && prepared != current->real_cred &&
        prepared->security != current->cred->security && prepared->security != current->real_cred->security,
        "LSM preparation receives a private prepared credential and security blob");
    fixture::check(domain && length == std::strlen(domain), "domain preparation receives the bounded context");
    if (fixture::domain_error) return fixture::domain_error;
    prepared->security->sid = 42;
    prepared->security->create_sid = prepared->security->keycreate_sid = prepared->security->sockcreate_sid = 0;
    return 0;
}
static int validate_callback(void*) {
    ++fixture::validates;
    if (fixture::mutation_at == static_cast<int>(fixture::validates)) mutate(fixture::mutation);
    return fixture::validate_error_at == static_cast<int>(fixture::validates) ? fixture::validate_error : 0;
}
static int adopt_callback(void*, const cred* before, const cred* after) {
    ++fixture::adopts;
    fixture::adopted_before = before;
    fixture::adopted_after = after;
    fixture::check(before != after && before->refs >= 1 && after->refs >= 3 &&
        current->cred == after && current->real_cred == after,
        "adopt borrows live before/after references after both task slots publish");
    if (fixture::retain_adopted) fixture::adopted_reference = get_cred(after);
    mutate(fixture::adopt_mutation);
    return fixture::adopt_error;
}

// EXACT_FUNCTIONS

namespace fixture {
void setup(const char* name) {
    case_name = name;
    group_sets = group_local_drops = group_created = group_destroyed = 0;
    ++cases;
    sleepable = domain_supported = true;
    fail_prepare = fail_alloc_uid = fail_groups = fail_ucounts = false;
    commit_error = validate_error = validate_error_at = adopt_error = domain_error = 0;
    prepares = aborts = commits = validates = adopts = uid_allocs = group_allocs = ucount_sets = domain_prepares = 0;
    mutation_at = 0;
    mutation = adopt_mutation = commit_mutation = Mutation::None;
    retain_adopted = false;
    adopted_reference = committed = adopted_before = adopted_after = nullptr;
    input_profile = nullptr;
    assert(tasks.empty() && live_creds == 0 && live_mm == 0);
    auto* value = new cred;
    ++live_creds;
    ++init_user_ns.refs;
    value->user = alloc_uid(1000);
    value->group_info = groups_alloc(2);
    value->group_info->gid[0] = 2000;
    value->group_info->gid[1] = 2001;
    value->ucounts = new ucounts;
    ++live_ucounts;
    value->security = new security_blob;
    ++live_security;
    // Linux tasks independently own subjective and objective credential refs.
    current = new task_struct{value, get_cred(value), new_mm(), 9};
    original_task = current;
    tasks.push_back(current);
    uid_allocs = group_allocs = 0;
}
void teardown() {
    if (adopted_reference) put_cred(adopted_reference);
    for (auto* task : tasks) {
        put_cred(task->cred);
        put_cred(task->real_cred);
        if (task->mm) mmdrop(task->mm);
        delete task;
    }
    tasks.clear();
    current = original_task = nullptr;
    check(group_local_drops == group_sets, "each native installation returns exactly one non-last local reference");
    check(group_destroyed == group_created, "native credential cleanup finally destroys each allocated group exactly once");
    check(live_creds == 0, "all task, receipt, prepare, and adopted cred references balance");
    check(live_users == 0 && live_groups == 0 && live_ucounts == 0,
        "all user/group/ucounts dependency references balance");
    check(live_security == 0 && live_mm == 0 && init_user_ns.refs == 0 && other_user_ns.refs == 0,
        "all private security, mm, and user namespace references balance");
}
}

static msu_credential_profile root_profile() {
    msu_credential_profile value{};
    value.replace_ids = value.replace_groups = value.clear_securebits = true;
    value.cap_write_mask = MSU_CRED_CAP_ALL;
    value.caps = {0x0f, 0x3f, 0x03, 0x7f, 0x01};
    return value;
}
static int replace(msu_credential_profile& profile, u32& effects) {
    return msu_credential_replace_profile_current(&profile, validate_callback, adopt_callback, nullptr, &effects);
}
static bool same_fields(const cred* value, const credential_snapshot& before, const security_blob& security) {
    return value->uid == before.uid && value->suid == before.suid && value->euid == before.euid && value->fsuid == before.fsuid &&
        value->gid == before.gid && value->sgid == before.sgid && value->egid == before.egid && value->fsgid == before.fsgid &&
        value->securebits == before.securebits && value->cap_inheritable == before.cap_inheritable &&
        value->cap_permitted == before.cap_permitted && value->cap_effective == before.cap_effective &&
        value->cap_bset == before.cap_bset && value->cap_ambient == before.cap_ambient &&
        value->user == before.user && value->group_info == before.group_info && value->user_ns == before.user_ns &&
        value->ucounts == before.ucounts && value->security == before.security &&
        value->group_info->count == before.saved_groups.count &&
        std::equal(value->group_info->gid, value->group_info->gid + value->group_info->count, before.saved_groups.gid) &&
        value->user->uid == before.saved_user.uid && value->ucounts->uid == before.saved_ucounts.uid &&
        !std::memcmp(value->security, &security, sizeof(security));
}
static void unchanged(const cred* old, const credential_snapshot& snapshot, const security_blob& security) {
    fixture::check(same_fields(old, snapshot, security), "shared old credential fields and security blob stay unchanged");
}
static void failed_before_commit(int status, u32 effects, const cred* original, unsigned expected_prepares, unsigned expected_aborts) {
    fixture::check(status < 0 && effects == 0 && current->cred == original && current->real_cred == original,
        "failure before commit keeps current credentials and has no committed effect");
    fixture::check(fixture::prepares == expected_prepares && fixture::aborts == expected_aborts && fixture::commits == 0,
        "prepare ownership is aborted exactly once only after allocation");
    fixture::check(original->refs == 2 && original->user->refs == 1 && original->group_info->usage.refs == 1 &&
        original->ucounts->refs == 1 && original->user_ns->refs == 1 && current->mm->refs == 1,
        "failure releases held credential/mm references and prepared dependencies");
}

int main() {
    using namespace fixture;
    {
        setup("Root wrapper and shared sibling/fork references");
        auto* sibling = share_task(current);
        auto* forked = share_task(current);
        const auto* old = current->cred;
        const credential_snapshot snapshot(*old);
        const security_blob security = *old->security;
        u32 effects = 99;
        check(msu_credential_replace_current(nullptr, validate_callback, nullptr, &effects) == 0,
            "Root wrapper commits successfully");
        const auto* value = current->cred;
        check(value != old && current->real_cred == value && value->refs == 2 && effects == MAPLESU_EFFECT_COMMITTED,
            "Root installs a new cred with exactly two task references");
        check(value->uid == 0 && value->suid == 0 && value->euid == 0 && value->fsuid == 0 &&
            value->gid == 0 && value->sgid == 0 && value->egid == 0 && value->fsgid == 0 && value->securebits == 0,
            "Root replaces all eight IDs and clears securebits");
#if CAP_LAST_CAP < 63
        const u64 full = (1ULL << (CAP_LAST_CAP + 1)) - 1;
#else
        const u64 full = ~0ULL;
#endif
        check(value->cap_inheritable == full && value->cap_permitted == full && value->cap_effective == full &&
            value->cap_bset == full && value->cap_ambient == full && value->group_info->count == 0 && value->user->uid == 0,
            "Root writes all capabilities and owns a new empty group list/root user");
        check(sibling->cred == old && sibling->real_cred == old && forked->cred == old && forked->real_cred == old && old->refs == 4,
            "sibling and simulated fork retain their independent old credential references");
        unchanged(old, snapshot, security);
        check(prepares == 1 && commits == 1 && aborts == 0 && validates == 2 && adopts == 0,
            "Root wrapper uses the sole transaction and no adopt hook");
        teardown();
    }
    {
        setup("CAPS wrapper preserves IDs/groups and user ownership");
        auto* sibling = share_task(current);
        const auto* old = sibling->cred;
        const credential_snapshot snapshot(*old);
        const security_blob security = *old->security;
        maplesu_caps_v3 caps{0x06, 0x0f, 0x0a, 0x1f, 0x02};
        u32 effects = 99;
        check(msu_credential_replace_current(&caps, validate_callback, nullptr, &effects) == 0,
            "CAPS wrapper commits successfully");
        const auto* value = current->cred;
        check(value->uid == snapshot.uid && value->suid == snapshot.suid && value->euid == snapshot.euid && value->fsuid == snapshot.fsuid &&
            value->gid == snapshot.gid && value->sgid == snapshot.sgid && value->egid == snapshot.egid && value->fsgid == snapshot.fsgid &&
            value->securebits == snapshot.securebits && value->group_info == snapshot.group_info && value->user == snapshot.user &&
            value->ucounts == snapshot.ucounts && value->user_ns == snapshot.user_ns,
            "CAPS preserves all IDs, securebits, groups and dependency identities");
        check(value->cap_inheritable == caps.inheritable && value->cap_permitted == caps.permitted && value->cap_effective == caps.effective &&
            value->cap_bset == caps.bounding && value->cap_ambient == caps.ambient && effects == MAPLESU_EFFECT_COMMITTED,
            "CAPS writes all five requested sets and retains commit effect");
        check(value->refs == 2 && old->refs == 2 && value->user->refs == 2 && value->group_info->usage.refs == 2 &&
            uid_allocs == 0 && group_allocs == 0 && ucount_sets == 0,
            "CAPS shares referenced dependencies across distinct credential objects without ID allocations");
        unchanged(old, snapshot, security);
        teardown();
    }
    for (unsigned field = 0; field != 5; ++field) {
        setup("selective capability write");
        auto* sibling = share_task(current);
        const auto* old = sibling->cred;
        const credential_snapshot snapshot(*old);
        const security_blob security = *old->security;
        msu_credential_profile profile{};
        profile.cap_write_mask = 1U << field;
        profile.caps = {0x3f, 0x7f, 0x0c, 0x01, 0x02};
        u32 effects = 99;
        check(replace(profile, effects) == 0 && effects == MAPLESU_EFFECT_COMMITTED, "selected capability profile commits");
        const auto* value = current->cred;
        const u64 actual[] = {value->cap_inheritable, value->cap_permitted, value->cap_effective, value->cap_bset, value->cap_ambient};
        const u64 previous[] = {snapshot.cap_inheritable, snapshot.cap_permitted, snapshot.cap_effective, snapshot.cap_bset, snapshot.cap_ambient};
        const u64 requested[] = {profile.caps.inheritable, profile.caps.permitted, profile.caps.effective, profile.caps.bounding, profile.caps.ambient};
        for (unsigned i = 0; i != 5; ++i)
            check(actual[i] == (i == field ? requested[i] : previous[i]), "only selected capability fields change");
        check(value->uid == snapshot.uid && value->group_info == snapshot.group_info && value->securebits == snapshot.securebits &&
            validates == 3 && adopts == 1 && adopted_after == value && adopted_before == old,
            "selective capability path preserves unrelated state and revalidates after adopt");
        unchanged(old, snapshot, security);
        teardown();
    }
    {
        setup("profile IDs groups and retained consumer");
        const auto* old = get_cred(current->cred);
        const credential_snapshot snapshot(*old);
        const security_blob security = *old->security;
        auto profile = root_profile();
        profile.uid = 12345; profile.gid = 23456;
        profile.groups_count = 3;
        profile.groups[0] = 3003; profile.groups[1] = 1001; profile.groups[2] = 2002;
        retain_adopted = true;
        u32 effects = 99;
        check(replace(profile, effects) == 0, "full profile commits");
        const auto* value = current->cred;
        check(value->uid == 12345 && value->suid == 12345 && value->euid == 12345 && value->fsuid == 12345 &&
            value->gid == 23456 && value->sgid == 23456 && value->egid == 23456 && value->fsgid == 23456,
            "profile replaces each requested ID field");
        check(value->group_info != old->group_info && value->group_info->count == 3 && value->group_info->gid[0] == 1001 &&
            value->group_info->gid[1] == 2002 && value->group_info->gid[2] == 3003 && value->group_info->usage.refs == 1,
            "new supplementary groups are sorted with exactly one credential-owned reference");
        check(adopted_reference == value && value->refs == 3 && old->refs == 1,
            "retaining consumer owns one reference beyond the two committed task slots");
#if MSU_MANAGEMENT_CRED_UCOUNTS
        check(ucount_sets == 1 && value->ucounts != old->ucounts && value->ucounts->uid == 12345 && value->ucounts->refs == 1,
            "enabled ucounts replacement owns the target UID's dependency");
#else
        check(ucount_sets == 0 && value->ucounts == old->ucounts && value->ucounts->refs == 2,
            "target without credential ucounts operation retains copied dependency");
#endif
        unchanged(old, snapshot, security);
        put_cred(old);
        teardown();
    }
    for (unsigned failure = 0; failure != 4; ++failure) {
        setup("allocation failure cleanup");
        auto profile = root_profile();
        const auto* old = current->cred;
        const credential_snapshot snapshot(*old);
        const security_blob security = *old->security;
        fail_prepare = failure == 0;
        fail_alloc_uid = failure == 1;
        fail_groups = failure == 2;
        fail_ucounts = failure == 3;
        u32 effects = 99;
        const int status = replace(profile, effects);
        if (failure == 3 && !MSU_MANAGEMENT_CRED_UCOUNTS) {
            check(status == 0 && ucount_sets == 0 && effects == MAPLESU_EFFECT_COMMITTED,
                "disabled ucounts branch never calls its failure seam");
        } else {
            const int expected = failure == 1 ? -EAGAIN : failure == 3 ? -EDQUOT : -ENOMEM;
            check(status == expected, "allocation's exact failure status survives");
            failed_before_commit(status, effects, old, 1, failure == 0 ? 0 : 1);
            unchanged(old, snapshot, security);
        }
        teardown();
    }
    for (int stage = 1; stage <= 3; ++stage) {
        setup("qualification revocation validation boundary");
        auto profile = root_profile();
        validate_error_at = stage; validate_error = -EACCES;
        const auto* old = current->cred;
        u32 effects = 99;
        check(replace(profile, effects) == -EACCES, "revocation preserves the failing validation status");
        if (stage < 3) failed_before_commit(-EACCES, effects, old, stage == 1 ? 0 : 1, stage == 1 ? 0 : 1);
        else check(effects == MAPLESU_EFFECT_COMMITTED && commits == 1 && aborts == 0 && adopts == 1 && current->cred == committed && committed->refs == 2,
            "revocation after adopt preserves committed credentials and effects");
        check(validates == static_cast<unsigned>(stage), "revocation stops at the exact validation boundary");
        teardown();
    }
    for (const auto event : {Mutation::Exec, Mutation::Mm, Mutation::Override, Mutation::Task}) {
        for (int stage = 1; stage <= 2; ++stage) {
            setup("identity interleaving before commit");
            auto profile = root_profile();
            auto* sibling = share_task(current);
            const auto* old = sibling->cred;
            const credential_snapshot snapshot(*old);
            const security_blob security = *old->security;
            mutation = event; mutation_at = stage;
            u32 effects = 99;
            check(replace(profile, effects) == -ESTALE && effects == 0,
                "task/mm/exec/override interleaving before commit is stale without a commit effect");
            check(prepares == (stage == 1 ? 0U : 1U) && aborts == (stage == 1 ? 0U : 1U) && commits == 0,
                "only a prepared private object is aborted after precommit identity change");
            unchanged(old, snapshot, security);
            teardown();
        }
    }
    {
        setup("pre-existing subjective objective override split");
        mutate(Mutation::Override);
        auto profile = root_profile();
        u32 effects = 99;
        check(replace(profile, effects) == -EPERM && prepares == 0 && validates == 0 && effects == 0,
            "active override is rejected before acquiring transaction references");
        check(current->cred != current->real_cred && current->cred->refs == 1 && current->real_cred->refs == 1,
            "subjective and objective ownership remain independent after rejection");
        teardown();
    }
    for (const auto event : {Mutation::Exec, Mutation::Mm, Mutation::Override}) {
        setup("identity interleaving after adopt");
        auto profile = root_profile();
        adopt_mutation = event;
        u32 effects = 99;
        check(replace(profile, effects) == -ESTALE && effects == MAPLESU_EFFECT_COMMITTED && commits == 1 && aborts == 0 && adopts == 1,
            "post-adopt identity interleaving keeps the final credential commit and reports stale");
        teardown();
    }
    {
        setup("identity change immediately after commit");
        auto profile = root_profile();
        commit_mutation = Mutation::Exec;
        u32 effects = 99;
        check(replace(profile, effects) == -ESTALE && effects == MAPLESU_EFFECT_COMMITTED && adopts == 0 && aborts == 0,
            "postcommit identity check runs before any adoption and retains committed effect");
        teardown();
    }
    for (bool commit_failure : {false, true}) {
        setup("commit and adopt errors preserve irreversible effect");
        auto profile = root_profile();
        if (commit_failure) commit_error = -EIO; else adopt_error = -ENOSPC;
        u32 effects = 99;
        check(replace(profile, effects) == (commit_failure ? -EIO : -ENOSPC), "exact commit/adopt error is retained");
        check(effects == MAPLESU_EFFECT_COMMITTED && prepares == 1 && commits == 1 && aborts == 0 &&
            current->cred == committed && current->real_cred == committed && committed->refs == 2,
            "consumed prepared credential is neither aborted nor restored after a postcommit error");
        check(adopts == (commit_failure ? 0U : 1U) && validates == 2,
            "postcommit failure stops subsequent adoption/validation");
        teardown();
    }
    {
        setup("legal private LSM preparation");
        auto* sibling = share_task(current);
        const auto* old = sibling->cred;
        const credential_snapshot snapshot(*old);
        const security_blob security = *old->security;
        msu_credential_profile profile{};
        std::strcpy(profile.selinux_domain, "u:r:su:s0");
        u32 effects = 99;
        check(replace(profile, effects) == 0 && domain_prepares == 1 && effects == MAPLESU_EFFECT_COMMITTED,
            "qualified domain prepares on the new credential before commit");
        const auto* blob = current->cred->security;
        check(blob != old->security && blob->sid == 42 && blob->osid == security.osid && blob->exec_sid == security.exec_sid &&
            blob->create_sid == 0 && blob->keycreate_sid == 0 && blob->sockcreate_sid == 0,
            "private domain preparation preserves inherited osid/exec_sid and resets creation SIDs");
        unchanged(old, snapshot, security);
        teardown();
    }
    for (bool unsupported : {false, true}) {
        setup("LSM unsupported and prepare failure cleanup");
        auto profile = root_profile();
        std::strcpy(profile.selinux_domain, "u:r:su:s0");
        domain_supported = !unsupported;
        domain_error = -EINVAL;
        const auto* old = current->cred;
        const credential_snapshot snapshot(*old);
        const security_blob security = *old->security;
        u32 effects = 99;
        const int status = replace(profile, effects);
        check(status == (unsupported ? -EOPNOTSUPP : -EINVAL), "domain support/prepare error remains exact");
        failed_before_commit(status, effects, old, unsupported ? 0 : 1, unsupported ? 0 : 1);
        unchanged(old, snapshot, security);
        teardown();
    }
    {
        setup("profile snapshot is immutable across validation");
        auto profile = root_profile();
        profile.uid = 123; profile.gid = 456;
        profile.groups_count = 1; profile.groups[0] = 789;
        input_profile = &profile; mutation = Mutation::Profile; mutation_at = 1;
        u32 effects = 99;
        check(replace(profile, effects) == 0 && profile.uid == 9999 && current->cred->uid == 123 && current->cred->gid == 456 &&
            current->cred->group_info->gid[0] == 789 && current->cred->cap_effective == 0x03,
            "transaction consumes its initial complete profile snapshot despite caller input mutation");
        teardown();
    }
    {
        setup("partial capability combination validates inherited fields");
        msu_credential_profile profile{};
        profile.cap_write_mask = MSU_CRED_CAP_PERMITTED;
        profile.caps.permitted = 0;
        const auto* old = current->cred;
        u32 effects = 99;
        check(replace(profile, effects) == -EINVAL, "lowered permitted set rejects inherited effective/ambient mismatch");
        failed_before_commit(-EINVAL, effects, old, 1, 1);
        teardown();
    }
    for (unsigned mode = 0; mode != 2; ++mode) {
        setup("user namespace admission");
        auto* old = const_cast<cred*>(current->cred);
        --init_user_ns.refs; old->user_ns = &other_user_ns; ++other_user_ns.refs;
        auto profile = mode == 0 ? root_profile() : msu_credential_profile{};
        u32 effects = 99;
        const int status = replace(profile, effects);
        if (mode == 0) check(status == -EOPNOTSUPP && prepares == 0 && effects == 0,
            "ID/group replacement rejects a noninitial user namespace before prepare");
        else check(status == 0 && current->cred->user_ns == &other_user_ns && effects == MAPLESU_EFFECT_COMMITTED,
            "capability-only preparation retains the current namespace dependency");
        teardown();
    }
    {
        setup("context and callback admission");
        auto profile = root_profile();
        u32 effects = 99;
        sleepable = false;
        check(replace(profile, effects) == -EWOULDBLOCK && prepares == 0 && effects == 0, "nonsleepable context returns before prepare");
        sleepable = true;
        effects = 99;
        check(msu_credential_replace_profile_current(&profile, validate_callback, nullptr, nullptr, &effects) == -EINVAL && effects == 0,
            "profile API requires an adoption callback");
        effects = 99;
        check(msu_credential_replace_current(nullptr, nullptr, nullptr, &effects) == -EINVAL && effects == 0,
            "Root/CAPS wrapper requires its trusted validation callback");
        check(msu_credential_replace_profile_current(&profile, validate_callback, adopt_callback, nullptr, nullptr) == -EINVAL,
            "missing effect storage is rejected");
        teardown();
    }
    {
        setup("profile validation has no lifecycle side effects");
        auto profile = root_profile();
        profile.caps.permitted = 1; profile.caps.effective = 2;
        check(msu_credential_profile_validate(&profile) == -EINVAL, "effective outside permitted is rejected");
        profile = root_profile(); profile.caps.ambient = 0x40;
        check(msu_credential_profile_validate(&profile) == -EINVAL, "ambient outside inheritable/permitted is rejected");
        profile = root_profile(); profile.groups_count = MSU_CRED_PROFILE_MAX_GROUPS + 1;
        check(msu_credential_profile_validate(&profile) == -EINVAL, "too many groups are rejected");
        profile = {}; profile.groups_count = 1;
        check(msu_credential_profile_validate(&profile) == -EINVAL, "group payload requires selected group replacement");
        profile = root_profile(); profile.uid = UINT32_MAX;
        check(msu_credential_profile_validate(&profile) == -EINVAL, "invalid mapped UID is rejected");
        profile = root_profile(); profile.groups_count = 1; profile.groups[0] = UINT32_MAX;
        check(msu_credential_profile_validate(&profile) == -EINVAL, "invalid mapped supplementary GID is rejected");
        profile = {}; profile.cap_write_mask = MSU_CRED_CAP_ALL + 1;
        check(msu_credential_profile_validate(&profile) == -EINVAL, "unknown capability selection mask is rejected");
        profile = {}; std::memset(profile.selinux_domain, 'x', sizeof(profile.selinux_domain));
        check(msu_credential_profile_validate(&profile) == -EINVAL, "unterminated context is rejected");
#if CAP_LAST_CAP < 63
        profile = {}; profile.cap_write_mask = MSU_CRED_CAP_BOUNDING; profile.caps.bounding = 1ULL << (CAP_LAST_CAP + 1);
        check(msu_credential_profile_validate(&profile) == -EINVAL, "capabilities above target maximum are rejected");
#endif
        check(prepares == 0 && commits == 0 && aborts == 0, "profile checks never allocate or publish credentials");
        teardown();
    }
    {
        setup("installed groups abort after inherited capability validation");
        msu_credential_profile profile{};
        profile.replace_groups = true;
        profile.groups_count = 1;
        profile.groups[0] = 42;
        profile.cap_write_mask = MSU_CRED_CAP_PERMITTED;
        profile.caps.permitted = 0;
        const auto* old = current->cred;
        u32 effects = 99;
        check(replace(profile, effects) == -EINVAL, "capability failure occurs after successful group installation");
        check(group_sets == 1 && group_local_drops == 1 && group_destroyed == 1,
            "abort releases the sole prepared group reference after the local one was returned");
        failed_before_commit(-EINVAL, effects, old, 1, 1);
        teardown();
    }
    std::cout << "p4-credential-lifecycle cases=" << cases << " checks=" << checks << " passed=1 ucounts="
        << MSU_MANAGEMENT_CRED_UCOUNTS << " group_refcount=" << MSU_GROUP_INFO_REFCOUNT
        << " cap_last=" << CAP_LAST_CAP
        << "; deterministic host seams; no real kernel concurrency or target execution\n";
    return 0;
}
