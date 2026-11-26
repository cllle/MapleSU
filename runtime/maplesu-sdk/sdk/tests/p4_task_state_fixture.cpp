#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <initializer_list>

#ifndef EUCLEAN
#define EUCLEAN 117
#endif
#ifndef ESTALE
#define ESTALE 116
#endif
#ifndef MSU_TASK_SECCOMP_RELEASE_LOCKS_TASK
#define MSU_TASK_SECCOMP_RELEASE_LOCKS_TASK 0
#endif

using u32 = uint32_t;
using u64 = uint64_t;
using atomic_t = int;

#if FIXTURE_SECCOMP
#define CONFIG_SECCOMP 1
#if FIXTURE_FILTER
#define CONFIG_SECCOMP_FILTER 1
#endif
#endif
#if FIXTURE_GENERIC_ENTRY
#define CONFIG_GENERIC_ENTRY 1
#endif
#define SECCOMP_MODE_DISABLED 0
#define SECCOMP_MODE_STRICT 1
#define SECCOMP_MODE_FILTER 2
#define PF_EXITING (1UL << 0)
#define SECCOMP (1UL << 1)
#define TIF_SECCOMP 5
#define READ_ONCE(value) (value)
#define WRITE_ONCE(value, next) ((value) = (next))

// EXACT_PROTOCOL_EFFECTS

struct cred { unsigned references = 1; };
struct mm_struct { unsigned references = 0; };
struct seccomp_filter { unsigned references = 1; };
struct seccomp_state { int mode = 0; atomic_t filter_count = 0; seccomp_filter* filter = nullptr; };
struct spinlock_t { bool locked = false; };
struct sighand_struct { spinlock_t siglock; };
struct thread_info { unsigned long syscall_work = 0; unsigned long flags = 0; };
struct task_struct {
    mm_struct* mm = nullptr;
    unsigned long flags = 0;
    u64 self_exec_id = 0;
    sighand_struct* sighand = nullptr;
    const struct cred* cred = nullptr;
    const struct cred* real_cred = nullptr;
    thread_info ti;
    bool no_new_privs = true;
#ifdef CONFIG_SECCOMP
    seccomp_state seccomp;
#endif
};

namespace fixture {
task_struct subject, alternate_task, sibling;
task_struct* task = &subject;
mm_struct original_mm, alternate_mm;
cred original_cred, alternate_cred;
sighand_struct original_signal, alternate_signal;
seccomp_filter original_filter, attached_filter;
unsigned checks = 0, scenarios = 0;
unsigned gets = 0, puts = 0, grabs = 0, drops = 0;
unsigned locks = 0, unlocks = 0, releases = 0, validations = 0, barriers = 0;
bool task_context = true, atomic_context = false, interrupts_disabled = false;
bool layout_ready = true;
int first_error = 0, second_error = 0;
const char* scenario = "initial";
enum class mutation { none, task, mm, exec, exiting, cred, real_cred, sighand, filter };
mutation first_validate = mutation::none, second_validate = mutation::none;
mutation first_lock = mutation::none, final_lock = mutation::none, release_hook = mutation::none;

void check(bool value, const char* message) {
    ++checks;
    if (!value) {
        std::fprintf(stderr, "FAIL [%s]: %s\n", scenario, message);
        std::exit(1);
    }
}
void set_work(task_struct* value, bool enabled) {
#ifdef CONFIG_GENERIC_ENTRY
    if (enabled) value->ti.syscall_work |= SECCOMP;
    else value->ti.syscall_work &= ~SECCOMP;
#else
    if (enabled) value->ti.flags |= 1UL << TIF_SECCOMP;
    else value->ti.flags &= ~(1UL << TIF_SECCOMP);
#endif
}
bool has_work(task_struct* value) {
#ifdef CONFIG_GENERIC_ENTRY
    return (value->ti.syscall_work & SECCOMP) != 0;
#else
    return (value->ti.flags & (1UL << TIF_SECCOMP)) != 0;
#endif
}
void mutate(mutation change) {
    switch (change) {
    case mutation::none: break;
    case mutation::task: task = &alternate_task; break;
    case mutation::mm: subject.mm = &alternate_mm; break;
    case mutation::exec: ++subject.self_exec_id; break;
    case mutation::exiting: subject.flags |= PF_EXITING; break;
    case mutation::cred: subject.cred = &alternate_cred; break;
    case mutation::real_cred: subject.real_cred = &alternate_cred; break;
    case mutation::sighand: subject.sighand = &alternate_signal; break;
    case mutation::filter:
#ifdef CONFIG_SECCOMP
        check(!subject.seccomp.filter, "reattach seam starts after detach");
        ++attached_filter.references;
        subject.seccomp.filter = &attached_filter;
        subject.seccomp.filter_count = 1;
        subject.seccomp.mode = SECCOMP_MODE_FILTER;
        set_work(&subject, true);
#endif
        break;
    }
}
bool in_task() { return task_context; }
bool in_atomic() { return atomic_context; }
bool irqs_disabled() { return interrupts_disabled; }
void mmgrab(mm_struct* value) { ++grabs; ++value->references; }
void mmdrop(mm_struct* value) { ++drops; --value->references; }
const cred* current_cred() { return task->cred; }
const cred* current_real_cred() { return task->real_cred; }
const cred* get_current_cred() {
    ++gets;
    ++const_cast<cred*>(task->cred)->references;
    return task->cred;
}
void put_cred(const cred* value) { ++puts; --const_cast<cred*>(value)->references; }
int atomic_read(const atomic_t* value) { return *value; }
void atomic_set(atomic_t* value, int next) { *value = next; }
void spin_lock_irqsave(spinlock_t* lock, unsigned long& flags) {
    ++locks;
    mutate(locks == 1 ? first_lock : final_lock);
    check(!lock->locked, "siglock acquired only when unlocked");
    flags = 0;
    lock->locked = true;
}
void spin_unlock_irqrestore(spinlock_t* lock, unsigned long&) {
    check(lock->locked, "siglock released only when held");
    lock->locked = false;
    ++unlocks;
}
bool test_work(task_struct* value, int) { return (value->ti.syscall_work & SECCOMP) != 0; }
void clear_work(task_struct* value, int) { value->ti.syscall_work &= ~SECCOMP; }
bool test_flag(task_struct* value, int bit) { return (value->ti.flags & (1UL << bit)) != 0; }
void clear_flag(task_struct* value, int bit) { value->ti.flags &= ~(1UL << bit); }
void memory_barrier_after_atomic() { ++barriers; }
void seccomp_filter_release(seccomp_filter* value) {
    check(!original_signal.siglock.locked, "native release runs outside siglock");
    check(original_cred.references == 2 && original_mm.references == 1,
          "cred and mm remain held through native release");
#ifdef CONFIG_SECCOMP
    check(!subject.seccomp.filter && !subject.seccomp.filter_count &&
          subject.seccomp.mode == SECCOMP_MODE_DISABLED && !has_work(&subject),
          "native release observes fully detached current state");
#endif
    check(value == &original_filter && value->references > 0,
          "native release consumes the detached filter reference");
    --value->references;
    ++releases;
    mutate(release_hook);
}
void seccomp_task_release(task_struct* value) {
    check(value != task && value->flags == PF_EXITING,
          "native helper consumes the private exiting task carrier");
#if MSU_TASK_SECCOMP_RELEASE_LOCKS_TASK
    check(value->sighand == &original_signal,
          "locking helper receives the original live sighand");
#endif
#ifdef CONFIG_SECCOMP
    seccomp_filter_release(value->seccomp.filter);
#endif
}
int validate(void*) {
    ++validations;
    check(!original_signal.siglock.locked && !alternate_signal.siglock.locked,
          "validator runs outside either siglock");
    check(original_cred.references == 2 && original_mm.references == 1,
          "original identity references remain held during validation");
    mutate(validations == 1 ? first_validate : second_validate);
    return validations == 1 ? first_error : second_error;
}
void setup(const char* name, int mode = SECCOMP_MODE_DISABLED) {
    scenario = name;
    ++scenarios;
    subject = {};
    alternate_task = {};
    sibling = {};
    original_mm = {};
    alternate_mm = {};
    original_cred = {};
    alternate_cred = {};
    original_signal = {};
    alternate_signal = {};
    original_filter = {};
    attached_filter = {};
    attached_filter.references = 0;
    task = &subject;
    subject.mm = &original_mm;
    subject.self_exec_id = 3;
    subject.sighand = &original_signal;
    subject.cred = subject.real_cred = &original_cred;
    subject.ti.flags = 1UL << 6;
    subject.ti.syscall_work = 1UL << 9;
#ifdef CONFIG_SECCOMP
    subject.seccomp.mode = mode;
    if (mode == SECCOMP_MODE_FILTER) {
        subject.seccomp.filter_count = 1;
        subject.seccomp.filter = &original_filter;
    }
    set_work(&subject, mode != SECCOMP_MODE_DISABLED);
#endif
    alternate_task = subject;
    gets = puts = grabs = drops = locks = unlocks = releases = validations = barriers = 0;
    task_context = true;
    layout_ready = true;
    atomic_context = interrupts_disabled = false;
    first_error = second_error = 0;
    first_validate = second_validate = first_lock = final_lock = release_hook = mutation::none;
}
void balanced() {
    check(gets == puts && grabs == drops && gets == grabs,
          "identity acquire/release counts balance");
    check(original_cred.references == 1 && alternate_cred.references == 1 &&
          original_mm.references == 0 && alternate_mm.references == 0,
          "held references return to each original object");
    check(locks == unlocks && !original_signal.siglock.locked && !alternate_signal.siglock.locked,
          "every siglock is released");
    check(subject.no_new_privs && (subject.ti.flags & (1UL << 6)) &&
          (subject.ti.syscall_work & (1UL << 9)), "unrelated task and work bits survive");
}
}

#define current fixture::task
#define current_cred() fixture::current_cred()
#define current_real_cred() fixture::current_real_cred()
#define in_task() fixture::in_task()
#define in_atomic() fixture::in_atomic()
#define irqs_disabled() fixture::irqs_disabled()
#define get_current_cred() fixture::get_current_cred()
#define mmgrab(mm) fixture::mmgrab(mm)
#define mmdrop(mm) fixture::mmdrop(mm)
#define put_cred(cred) fixture::put_cred(cred)
#define atomic_read(value) fixture::atomic_read(value)
#define atomic_set(value, next) fixture::atomic_set(value, next)
#define spin_lock_irqsave(lock, flags) fixture::spin_lock_irqsave(lock, flags)
#define spin_unlock_irqrestore(lock, flags) fixture::spin_unlock_irqrestore(lock, flags)
#define smp_mb__after_atomic() fixture::memory_barrier_after_atomic()
#define __seccomp_filter_release(filter) fixture::seccomp_filter_release(filter)
#define test_task_syscall_work(task, work) fixture::test_work(task, work)
#define clear_task_syscall_work(task, work) fixture::clear_work(task, work)
#define test_tsk_thread_flag(task, flag) fixture::test_flag(task, flag)
#define clear_tsk_thread_flag(task, flag) fixture::clear_flag(task, flag)
#define MSU_DYN_FIELD(object, type, member) ((object)->member)
#define msu_dynamic_task_thread_info(task) (&(task)->ti)
#define msu_dynamic_current_cred() (fixture::current_cred)()
#define msu_dynamic_current_real_cred() (fixture::current_real_cred)()
#define msu_dynamic_get_current_cred() (fixture::get_current_cred)()
#define msu_dynamic_put_cred(value) (fixture::put_cred)(value)
#define msu_dynamic_mmgrab(value) (fixture::mmgrab)(value)
#define msu_dynamic_mmdrop(value) (fixture::mmdrop)(value)
#define msu_runtime_layout_ready() fixture::layout_ready
#define MSU_DYN_task_struct_flags 0
static const struct { struct { unsigned owner_bytes; } values[1]; }
    msu_runtime_layout = {{{sizeof(task_struct)}}};
#define GFP_KERNEL 0
#define kvzalloc(bytes, flags) static_cast<task_struct*>(std::calloc(1, bytes))
#define kvfree(value) std::free(value)
#define seccomp_filter_release(task) fixture::seccomp_task_release(task)
#define SYSCALL_WORK_BIT_SECCOMP 1
#define test_bit(bit, value) ((*(value) & (1UL << (bit))) != 0)
#define clear_bit(bit, value) (*(value) &= ~(1UL << (bit)))

// EXACT_TASK_STATE_MACROS
// EXACT_TASK_STATE_FUNCTION

namespace fixture {
void expect(int expected_error, u32 expected_effects, u64 expected_previous, bool held = true) {
    u32 effects = 77;
    u64 previous = 99;
    const int error = msu_task_state_release_current(validate, nullptr, &effects, &previous);
    check(error == expected_error, "exact transaction error is retained");
    check(effects == expected_effects && previous == expected_previous,
          "completed effects and previous mode match the transaction point");
    check(gets == unsigned(held) && grabs == unsigned(held),
          "only admitted transactions acquire identity references");
    balanced();
}
void untouched(int mode) {
#ifdef CONFIG_SECCOMP
    check(subject.seccomp.mode == mode &&
          subject.seccomp.filter == (mode == SECCOMP_MODE_FILTER ? &original_filter : nullptr) &&
          subject.seccomp.filter_count == (mode == SECCOMP_MODE_FILTER ? 1 : 0) &&
          has_work(&subject) == (mode != SECCOMP_MODE_DISABLED),
          "failed admission leaves seccomp state untouched");
#endif
    check(releases == 0 && barriers == 0, "failed admission performs no filter release or bit update");
}
}

int main() {
    using namespace fixture;
    const u32 completed = MAPLESU_EFFECT_TASK_STATE_COMPLETED;
    const u32 changed = completed | MAPLESU_EFFECT_TASK_STATE_CHANGED;
    setup("null effects");
    u64 previous = 99;
    check(msu_task_state_release_current(validate, nullptr, nullptr, &previous) == -EINVAL && previous == 99,
          "null effects rejected before output or identity changes");
    balanced();
    setup("null previous mode");
    u32 effects = 77;
    check(msu_task_state_release_current(validate, nullptr, &effects, nullptr) == -EINVAL && effects == 77,
          "null previous mode rejected before effects change");
    balanced();
    setup("null validator");
    check(msu_task_state_release_current(nullptr, nullptr, &effects, &previous) == -EINVAL && effects == 0,
          "null validator rejected before identity acquisition");
    balanced();
    setup("runtime layout not published");
    layout_ready = false;
    expect(-EPROTO, 0, 99, false);
    check(validations == 0 && locks == 0, "missing private facts reject before callbacks or mutation");
    for (int context = 0; context != 3; ++context) {
        setup("non-sleepable context");
        if (context == 0) task_context = false;
        if (context == 1) atomic_context = true;
        if (context == 2) interrupts_disabled = true;
        expect(-EWOULDBLOCK, 0, 99, false);
        check(validations == 0 && locks == 0, "invalid execution context invokes no validator or lock");
    }
    for (int invalid = 0; invalid != 3; ++invalid) {
        setup("initial current identity rejection");
        if (invalid == 0) subject.mm = nullptr;
        if (invalid == 1) subject.flags |= PF_EXITING;
        if (invalid == 2) subject.cred = &alternate_cred;
        expect(-EPERM, 0, 99, false);
        check(validations == 0 && locks == 0, "invalid current task is rejected before callbacks");
    }
    setup("first validator error");
    first_error = -EACCES;
    expect(-EACCES, 0, 99);
    check(validations == 1 && locks == 0, "first error returns before any seccomp lock");
    for (mutation change : {mutation::task, mutation::mm, mutation::exec,
                            mutation::exiting, mutation::cred, mutation::real_cred}) {
        setup("identity changes during first validation");
        first_validate = change;
        expect(-ESTALE, 0, 99);
        untouched(SECCOMP_MODE_DISABLED);
    }
    setup("already disabled or CONFIG_SECCOMP=n");
    expect(0, completed, SECCOMP_MODE_DISABLED);
    check(validations == 2 && releases == 0 && barriers == 0,
          "no-op validates twice without detach");
#ifdef CONFIG_SECCOMP
    check(locks == 2, "disabled state is observed under both native locks");
#else
    check(locks == 0, "CONFIG_SECCOMP=n never accesses seccomp locks");
#endif
    setup("final validator exact error");
    second_error = -EIO;
    expect(-EIO, completed, SECCOMP_MODE_DISABLED);
    check(validations == 2, "final validation error retains completed no-op");
    for (mutation change : {mutation::task, mutation::mm, mutation::exec,
                            mutation::exiting, mutation::cred, mutation::real_cred}) {
        setup("identity changes during final validation");
        second_validate = change;
        expect(-ESTALE, completed, SECCOMP_MODE_DISABLED);
    }
#ifdef CONFIG_SECCOMP
    setup("missing sighand after validation");
    subject.sighand = nullptr;
    expect(-ESTALE, 0, 99);
    check(locks == 0 && validations == 1, "missing sighand returns before lock");
    for (mutation change : {mutation::task, mutation::mm, mutation::exec,
                            mutation::exiting, mutation::cred, mutation::real_cred, mutation::sighand}) {
        setup("identity changes immediately before first lock", SECCOMP_MODE_STRICT);
        first_lock = change;
        expect(-ESTALE, 0, 99);
        untouched(SECCOMP_MODE_STRICT);
    }
    for (int invalid = 0; invalid != 10; ++invalid) {
        setup("inconsistent seccomp state rejected");
        switch (invalid) {
        case 0: subject.seccomp.filter = &original_filter; break;
        case 1: subject.seccomp.filter_count = 1; break;
        case 2: set_work(&subject, true); break;
        case 3: subject.seccomp.mode = SECCOMP_MODE_STRICT; break;
        case 4: subject.seccomp.mode = SECCOMP_MODE_STRICT; set_work(&subject, true);
                subject.seccomp.filter = &original_filter; break;
        case 5: subject.seccomp.mode = SECCOMP_MODE_STRICT; set_work(&subject, true);
                subject.seccomp.filter_count = 1; break;
        case 6: subject.seccomp.mode = SECCOMP_MODE_FILTER; set_work(&subject, true); break;
        case 7: subject.seccomp.mode = SECCOMP_MODE_FILTER; set_work(&subject, true);
                subject.seccomp.filter = &original_filter; subject.seccomp.filter_count = -1; break;
        case 8: subject.seccomp.mode = SECCOMP_MODE_FILTER;
                subject.seccomp.filter = &original_filter; subject.seccomp.filter_count = 1; break;
        case 9: subject.seccomp.mode = 7; break;
        }
        const auto before = subject.seccomp;
        const bool work_before = has_work(&subject);
        expect(-EUCLEAN, 0, 99);
        check(subject.seccomp.mode == before.mode && subject.seccomp.filter == before.filter &&
              subject.seccomp.filter_count == before.filter_count && has_work(&subject) == work_before &&
              releases == 0 && barriers == 0, "inconsistent state is rejected without repair");
    }
    setup("strict transition");
    subject.seccomp.mode = SECCOMP_MODE_STRICT;
    set_work(&subject, true);
    expect(0, changed, SECCOMP_MODE_STRICT);
    check(subject.seccomp.mode == SECCOMP_MODE_DISABLED && !has_work(&subject) &&
          releases == 0 && barriers == 1, "strict mode clears only its native work bit");
    setup("sighand replacement in final validation", SECCOMP_MODE_STRICT);
    second_validate = mutation::sighand;
    expect(-ESTALE, changed, SECCOMP_MODE_STRICT);
    check(locks == 1 && releases == 0, "replaced sighand is rejected before final lock");
    for (mutation change : {mutation::task, mutation::mm, mutation::exec,
                            mutation::exiting, mutation::cred, mutation::real_cred,
                            mutation::sighand, mutation::filter}) {
        setup("identity or filter changes at final lock", SECCOMP_MODE_STRICT);
        final_lock = change;
        expect(-ESTALE, changed, SECCOMP_MODE_STRICT);
        check(locks == 2 && releases == 0, "final observation retains completed transition");
    }
    setup("filter attached during final validation", SECCOMP_MODE_STRICT);
    second_validate = mutation::filter;
    expect(-ESTALE, changed, SECCOMP_MODE_STRICT);
    check(subject.seccomp.filter == &attached_filter && attached_filter.references == 1 &&
          releases == 0, "newly attached filter is left owned by current task");
#ifdef CONFIG_SECCOMP_FILTER
    setup("filter transition", SECCOMP_MODE_FILTER);
    expect(0, changed, SECCOMP_MODE_FILTER);
    check(original_filter.references == 0 && releases == 1 && barriers == 1,
          "one detached filter reference is released exactly once");
    setup("shared filter transition", SECCOMP_MODE_FILTER);
    original_filter.references = 2;
    sibling = subject;
    expect(0, changed, SECCOMP_MODE_FILTER);
    check(original_filter.references == 1 && sibling.seccomp.filter == &original_filter &&
          sibling.seccomp.mode == SECCOMP_MODE_FILTER && sibling.seccomp.filter_count == 1 &&
          has_work(&sibling), "another task retains its filter reference and state");
    for (mutation change : {mutation::task, mutation::mm, mutation::exec,
                            mutation::exiting, mutation::cred, mutation::real_cred,
                            mutation::sighand, mutation::filter}) {
        setup("identity or filter changes during native release", SECCOMP_MODE_FILTER);
        release_hook = change;
        expect(-ESTALE, changed, SECCOMP_MODE_FILTER);
        check(releases == 1 && original_filter.references == 0,
              "release-time race retains the completed detach and original release");
        if (change == mutation::filter)
            check(subject.seccomp.filter == &attached_filter && attached_filter.references == 1,
                  "re-attached filter reference is not consumed by prior detach");
    }
    setup("error after filter release", SECCOMP_MODE_FILTER);
    second_error = -EACCES;
    expect(-EACCES, changed, SECCOMP_MODE_FILTER);
    check(releases == 1 && original_filter.references == 0,
          "post-release first error does not roll back or repeat release");
#else
    setup("CONFIG_SECCOMP_FILTER=n rejects filter mode", SECCOMP_MODE_FILTER);
    expect(-EUCLEAN, 0, 99);
    untouched(SECCOMP_MODE_FILTER);
#endif
#endif
    std::printf("p4-task-state checks=%u scenarios=%u passed=1 config=%d/%d/%d\n",
                checks, scenarios, FIXTURE_SECCOMP, FIXTURE_FILTER, FIXTURE_GENERIC_ENTRY);
    return 0;
}
