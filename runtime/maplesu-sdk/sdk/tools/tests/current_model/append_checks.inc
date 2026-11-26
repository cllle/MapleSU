// Appended to the unchanged original caps_precheck/test_patch_current.cpp main.
    using kernel_module::CurrentTaskSource;
    constexpr uint64_t stack_base = 0xffff000000100000ULL;
    constexpr uint64_t task_base = 0xffff000000300000ULL;
    constexpr uint64_t cred_base = 0xffff000000500000ULL;
    for (const uint32_t size : {0x4000U,0x8000U,0x10000U,0x20000U}) {
        for (const uint32_t position : {0x100U,size/2+0x100U,size-0x100U}) {
            reset();
            current_test::model={CurrentTaskSource::stack_thread_info,size};
            current_test::sp_value=stack_base+position; current_test::sp_el0=0;
            current_test::memory[stack_base+16]=task_base;
            current_test::memory[task_base+512]=cred_base;
            auto ctx=init_aarch64_asm(); auto* a=ctx.assembler();
            check(current_test::emit_task(a,asmjit::a64::x12)==KModErr::OK && a->values[12]==task_base,
                "actual inline current uses injected synthetic model size at every SP position");
            current_test::masks.clear();
            check(current_test::emit_info(a,asmjit::a64::x13)==KModErr::OK && a->values[13]==stack_base &&
                current_test::masks.size()==1 && current_test::masks[0]==~(uint64_t(size)-1),
                "actual inline thread_info uses exact injected synthetic model mask");
            current_test::flag_addresses.clear(); current_test::store_addresses.clear();
            check(current.set_current_root(1024)==KModErr::OK && current_test::flag_addresses.size()==1 &&
                current_test::flag_addresses[0]==stack_base,
                "actual m072 root flags use same injected synthetic stack base as current");
            check(!current_test::store_addresses.empty() && current_test::store_addresses.front()==cred_base+4,
                "actual m072 first target credential store uses the fixture task address");
        }
    }
    for (const auto source : {CurrentTaskSource::sp_el0_task,CurrentTaskSource::sp_el0_thread_info}) {
        for (const uint64_t adjustment : {uint64_t(0),uint64_t(0x80)}) {
            reset(); current_test::model={source,0}; current_test::huawei_offset=adjustment;
            current_test::sp_el0=source==CurrentTaskSource::sp_el0_task ? task_base-adjustment : stack_base-adjustment;
            current_test::memory[stack_base+16]=task_base; current_test::memory[task_base+512]=cred_base;
            auto ctx=init_aarch64_asm(); auto* a=ctx.assembler();
            check(current_test::emit_task(a,asmjit::a64::x12)==KModErr::OK && a->values[12]==task_base,
                "actual inline emitter retains injected task/thread_info models with synthetic Huawei adjustment");
            check(current_test::emit_info(a,asmjit::a64::x13)==KModErr::OK &&
                a->values[13]==(source==CurrentTaskSource::sp_el0_task ? task_base : stack_base),
                "actual inline flags retain injected task/thread_info models with synthetic Huawei adjustment");
            check(current.set_current_root(1024)==KModErr::OK && current_test::flag_addresses.size()==1 &&
                current_test::flag_addresses[0]==(source==CurrentTaskSource::sp_el0_task ? task_base : stack_base),
                "actual m072 retains injected embedded/SP_EL0-thread_info flags route");
        }
    }
    for (const auto error : {KModErr::ERR_MODULE_ASM,KModErr::ERR_MODULE_NOT_KADDR,KModErr::ERR_MODULE_OFFSET_NOT_FOUND}) {
        reset(); current_test::model_error=error;
        check(current.set_current_root(1024)==error && current_test::stores.empty() &&
            current_test::store_addresses.empty() && fixture.executions==0 && fixture.saved==0,
            "actual m072 root model error remains exact and emits zero target credential stores in fixture");
        reset(); current_test::model_error=error;
        check(current.set_current_caps(requested)==error && current_test::stores.empty() &&
            current_test::store_addresses.empty() && fixture.executions==0,
            "actual m072 caps model error remains exact and emits zero target credential stores in fixture");
        reset(); current_test::model_error=error; fixture.managed=true;
        fixture.capabilities=MAPLESU_CAP_CREDENTIAL|MAPLESU_CAP_TASK_STATE;
        check(current.set_current_root(1024)==KModErr::OK && current.set_current_caps(requested)==KModErr::OK &&
            current_test::model_calls==0 && current_test::builds==0,
            "managed typed route does not consult legacy current model even when probe would fail");
    }
    reset(); current_test::model_error=KModErr::ERR_MODULE_READ_FILE; current_test::fail_model_call=2;
    check(current.set_current_root(1024)==KModErr::ERR_MODULE_READ_FILE && current_test::stores.empty() &&
        current_test::store_addresses.empty() && fixture.executions==0 && fixture.saved==0,
        "second thread_info model error precedes every emitted root target credential store in fixture");
    for (const auto entry : {kernel_module::CurrentTaskModel{CurrentTaskSource::stack_thread_info,0},
                             kernel_module::CurrentTaskModel{CurrentTaskSource::stack_thread_info,0x6000},
                             kernel_module::CurrentTaskModel{static_cast<CurrentTaskSource>(99),0x10000}}) {
        reset(); current_test::model=entry;
        check(current.set_current_root(1024)==KModErr::ERR_MODULE_OFFSET_NOT_FOUND &&
            current_test::stores.empty() && current_test::store_addresses.empty() && fixture.executions==0,
            "invalid synthetic model or size emits zero root target credential stores in fixture");
        reset(); current_test::model=entry;
        auto ctx=init_aarch64_asm(); auto* a=ctx.assembler();
        check(current_test::emit_task(a,asmjit::a64::x12)==KModErr::ERR_MODULE_OFFSET_NOT_FOUND &&
            current_test::reads.empty() && current_test::masks.empty(),
            "actual inline current rejects invalid synthetic model values before instruction emission");
    }
