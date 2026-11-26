#include <algorithm>
#include <array>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>
#include "aarch64_asm_helper.h"
#include "module_err_def.h"

namespace kernel_module {
#include "actual_model.inl"
}
namespace current_fixture {
inline kernel_module::CurrentTaskModel model;
inline KModErr error = KModErr::OK;
inline unsigned model_calls = 0;
}
namespace kernel_module {
inline KModErr resolve_current_task_model(CurrentTaskModel& output) {
    ++current_fixture::model_calls;
    if (is_failed(current_fixture::error)) return current_fixture::error;
    output = current_fixture::model;
    return KModErr::OK;
}
namespace export_symbol {
using namespace asmjit;
using namespace asmjit::a64;
using namespace asmjit::a64::Predicate;
// The register resolver/vendor adjustment is outside this void-wrapper seam.
inline void emit_huawei_kti_add_ce72c7e634f27712ee84f3dd5e8e0ec9(Assembler*, GpX) {}
#include "actual_current.inl"
}
}
#include "actual_raw_export.inl"

namespace {
using asmjit::CodeHolder;
using asmjit::a64::Assembler;
unsigned checks = 0, failures = 0;
void check(bool ok, const char* text) {
    ++checks;
    if (!ok) { ++failures; std::fprintf(stderr, "VOID_COMPAT: %s\n", text); }
}
void require(bool ok, const char* text) {
    if (!ok) { std::fprintf(stderr, "SETUP: %s\n", text); std::exit(2); }
}

asmjit::Label seed(CodeHolder& code, Assembler& a) {
    require(a.nop() == asmjit::kErrorOk, "real AArch64 prefix");
    auto bound = a.newLabel();
    require(a.bind(bound) == asmjit::kErrorOk, "bound label");
    require(a.embedLabel(bound, 8) == asmjit::kErrorOk, "real relocation");
    auto pending = a.newLabel();
    require(a.b(pending) == asmjit::kErrorOk, "real unresolved branch fixup");
    asmjit::Section* data = nullptr;
    require(code.newSection(&data, ".current-model-fixture", SIZE_MAX,
        asmjit::SectionFlags::kNone, 8) == asmjit::kErrorOk, "second section");
    require(a.section(data) == asmjit::kErrorOk, "select second section");
    const std::array<uint8_t, 8> marker{0x31, 0x41, 0x59, 0x26, 0x53, 0x58, 0x97, 0x93};
    require(a.embed(marker.data(), marker.size()) == asmjit::kErrorOk, "second-section bytes");
    require(a.section(code.sectionById(0)) == asmjit::kErrorOk, "restore text section");
    require(code.sectionCount() == 2 && code.unresolvedLinkCount() &&
        !code.relocEntries().empty() && code.labelCount(), "seed sections/labels/relocations/fixups");
    return pending;
}

bool discarded(const CodeHolder& code, const Assembler& a, const Assembler* sibling) {
    return !code.isInitialized() && code.sections().empty() && code.emitters().empty() &&
        code.labelEntries().empty() && code.relocEntries().empty() && !code.unresolvedLinkCount() &&
        !a.code() && !a.currentSection() && !a.bufferData() && !a.bufferPtr() && !a.bufferEnd() &&
        (!sibling || (!sibling->code() && !sibling->currentSection() && !sibling->bufferData()));
}

struct HandlerSignal {};
struct ObservingHandler : asmjit::ErrorHandler {
    CodeHolder* watched = nullptr;
    Assembler* sibling = nullptr;
    unsigned calls = 0;
    asmjit::Error received = asmjit::kErrorOk;
    asmjit::BaseEmitter* origin = nullptr;
    bool cleaned_before_callback = false, raw_empty_before_callback = false;
    bool should_throw = false;
    void handleError(asmjit::Error error, const char* message, asmjit::BaseEmitter* source) override {
        ++calls; received = error; origin = source;
        auto* a = static_cast<Assembler*>(source);
        cleaned_before_callback = discarded(*watched, *a, sibling);
        raw_empty_before_callback = aarch64_my_asm_to_bytes(a).empty();
        require(message && std::string(message) == asmjit::DebugUtils::errorAsString(error),
            "notification retains standard error text");
        if (should_throw) throw HandlerSignal{};
    }
};

void call_void(unsigned wrapper, Assembler* a) {
    if (wrapper) kernel_module::export_symbol::get_current_thread_info(a, asmjit::a64::x12);
    else kernel_module::export_symbol::get_current(a, asmjit::a64::x12);
}
KModErr call_checked(unsigned wrapper, Assembler* a) {
    return wrapper ? kernel_module::export_symbol::get_current_thread_info_checked(a, asmjit::a64::x12)
        : kernel_module::export_symbol::get_current_checked(a, asmjit::a64::x12);
}

void configure_failure(unsigned failure) {
    current_fixture::model = {};
    current_fixture::error = failure == 0 ? KModErr::ERR_MODULE_READ_FILE :
        failure == 1 ? KModErr::ERR_MODULE_NOT_KADDR :
        failure == 2 ? KModErr::ERR_MODULE_OFFSET_NOT_FOUND : KModErr::OK;
    if (failure == 3) current_fixture::model = {kernel_module::CurrentTaskSource::stack_thread_info, 0};
    current_fixture::model_calls = 0;
}

void verify_exports_empty(CodeHolder& code, Assembler& a, Assembler& sibling) {
    check(discarded(code, a, &sibling), "all attached emitters/sections/labels/relocations/fixups discarded");
    check(aarch64_my_asm_to_bytes(&a).empty() && aarch64_my_asm_to_bytes(&sibling).empty(),
        "actual raw exporter returns no prefix for either detached assembler");
    size_t section_bytes = 0;
    for (const auto* section : code.sections()) section_bytes += section->bufferSize();
    check(!section_bytes, "retained CodeHolder exposes no raw section bytes");
    check(code.flatten() == asmjit::kErrorOk && code.codeSize() == 0,
        "flatten has no pre-failure payload");
    // reset() leaves the holder uninitialized; relocation requires a section.
    check(!code.isInitialized() && code.codeSize() == 0 && code.relocEntries().empty(),
        "invalidated holder has no relocatable pre-failure payload or fixups");
    std::array<uint8_t, 256> destination;
    destination.fill(0xa5);
    check(code.copyFlattenedData(destination.data(), destination.size(), asmjit::CopySectionFlags::kNone)
        == asmjit::kErrorOk && std::all_of(destination.begin(), destination.end(),
            [](uint8_t value) { return value == 0xa5; }),
        "flattened export writes no stale payload into retained destination");
}

void failure_case(unsigned wrapper, unsigned handler_style, unsigned failure) {
    CodeHolder code;
    require(code.init(asmjit::Environment(asmjit::Arch::kAArch64)) == asmjit::kErrorOk, "holder init");
    Assembler a(&code);
    seed(code, a);
    Assembler sibling(&code);
    ObservingHandler observed;
    observed.watched = &code; observed.sibling = &sibling; observed.should_throw = handler_style >= 4;
    MyAsmJitErrorHandler project;
    if (handler_style == 1 || handler_style == 4) a.setErrorHandler(&observed);
    if (handler_style == 2 || handler_style == 5) code.setErrorHandler(&observed);
    if (handler_style == 3) a.setErrorHandler(&project);
    configure_failure(failure);
    bool threw = false;
    try { call_void(wrapper, &a); }
    catch (const HandlerSignal&) { threw = true; }
    check(current_fixture::model_calls == 1, "void wrapper resolves its model exactly once");
    check(threw == observed.should_throw, "throwing handler retains exception propagation");
    if (handler_style != 0 && handler_style != 3) {
        check(observed.calls == 1 && observed.received == asmjit::kErrorInvalidState && observed.origin == &a,
            "own/inherited handler receives exactly one original error and origin");
        check(observed.cleaned_before_callback && observed.raw_empty_before_callback,
            "cleanup precedes collecting or throwing callback and callback raw export");
    }
    if (handler_style == 3) check(project.isError() && aarch64_asm_to_bytes(&a).empty(),
        "actual project handler records error and standard exporter stays empty");
    verify_exports_empty(code, a, sibling);
    std::printf("failure wrapper=%u handler=%u error=%u\n", wrapper, handler_style, failure);
}

void project_context_case(unsigned wrapper, unsigned failure) {
    auto context = init_aarch64_asm();
    auto* a = context.assembler(); auto* code = a->code();
    seed(*code, *a); Assembler sibling(code);
    configure_failure(failure);
    call_void(wrapper, a);
    check(context.has_error() && aarch64_asm_to_bytes(a).empty(),
        "actual project context preserves error notification and empty export");
    verify_exports_empty(*code, *a, sibling);
}

struct SuccessImage {
    std::vector<uint8_t> raw, flattened;
    bool operator==(const SuccessImage&) const = default;
};
SuccessImage success_image(unsigned wrapper, bool checked, kernel_module::CurrentTaskModel model) {
    current_fixture::model = model; current_fixture::error = KModErr::OK; current_fixture::model_calls = 0;
    CodeHolder code;
    require(code.init(asmjit::Environment(asmjit::Arch::kAArch64)) == asmjit::kErrorOk, "success holder init");
    Assembler a(&code); auto pending = seed(code, a);
    if (checked) require(call_checked(wrapper, &a) == KModErr::OK, "checked success");
    else call_void(wrapper, &a);
    require(a.bind(pending) == asmjit::kErrorOk, "resolve seeded branch");
    check(current_fixture::model_calls == 1 && a.code() == &code && code.sectionCount() == 2 &&
        code.isInitialized(), "successful wrapper retains holder, prefix and resolution behavior");
    SuccessImage image; image.raw = aarch64_my_asm_to_bytes(&a);
    require(code.flatten() == asmjit::kErrorOk && code.relocateToBase(0x10000000) == asmjit::kErrorOk,
        "success flatten/relocation");
    image.flattened.resize(code.codeSize());
    require(code.copyFlattenedData(image.flattened.data(), image.flattened.size(),
        asmjit::CopySectionFlags::kPadSectionBuffer) == asmjit::kErrorOk, "success flattened export");
    check(!image.raw.empty() && image.flattened.size() > image.raw.size(), "success exports retain both sections");
    return image;
}

void checked_error_case(unsigned wrapper, KModErr error) {
    current_fixture::error = error; current_fixture::model_calls = 0;
    CodeHolder code;
    require(code.init(asmjit::Environment(asmjit::Arch::kAArch64)) == asmjit::kErrorOk, "checked holder init");
    Assembler a(&code); seed(code, a);
    const auto before = aarch64_my_asm_to_bytes(&a);
    check(call_checked(wrapper, &a) == error && current_fixture::model_calls == 1,
        "checked ABI preserves the exact resolver error");
    check(a.code() == &code && aarch64_my_asm_to_bytes(&a) == before && code.sectionCount() == 2 &&
        code.unresolvedLinkCount() && !code.relocEntries().empty(),
        "checked failure remains caller-owned and does not trigger void invalidation");
}
}

int main() {
    for (unsigned wrapper = 0; wrapper < 2; ++wrapper) {
        for (unsigned handler = 0; handler < 6; ++handler)
            for (unsigned failure = 0; failure < 4; ++failure) failure_case(wrapper, handler, failure);
        for (unsigned failure = 0; failure < 4; ++failure) project_context_case(wrapper, failure);
        for (const auto error : {KModErr::ERR_MODULE_READ_FILE, KModErr::ERR_MODULE_NOT_KADDR,
            KModErr::ERR_MODULE_OFFSET_NOT_FOUND}) checked_error_case(wrapper, error);
        for (const auto model : {kernel_module::CurrentTaskModel{kernel_module::CurrentTaskSource::sp_el0_task, 0},
            kernel_module::CurrentTaskModel{kernel_module::CurrentTaskSource::sp_el0_thread_info, 0},
            kernel_module::CurrentTaskModel{kernel_module::CurrentTaskSource::stack_thread_info, 0x4000},
            kernel_module::CurrentTaskModel{kernel_module::CurrentTaskSource::stack_thread_info, 0x8000},
            kernel_module::CurrentTaskModel{kernel_module::CurrentTaskSource::stack_thread_info, 0x10000},
            kernel_module::CurrentTaskModel{kernel_module::CurrentTaskSource::stack_thread_info, 0x20000}})
            check(success_image(wrapper, false, model) == success_image(wrapper, true, model),
                "successful void and unchanged checked real AsmJit bytes are identical");
    }
    std::printf("real-asmjit-current-void: %u checks, %u failures\n", checks, failures);
    return failures ? 1 : 0;
}
