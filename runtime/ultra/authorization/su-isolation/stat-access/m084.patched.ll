; ModuleID = 'C:\MapleSU-Work\helper-su-isolation\stat-access\m084.original.bc'
source_filename = "C:/Users/abc/Desktop/testModule/kernel_module_kit/src/jni/../../../kernel_module_kit/src/jni/default_module/module_su_redirect/patch_sys_newfstatat.cpp"
target datalayout = "e-m:e-i8:8:32-i16:16:32-i64:64-i128:128-n32:64-S128"
target triple = "aarch64-unknown-linux-android21"

%"class.std::__ndk1::vector" = type { ptr, ptr, %"class.std::__ndk1::__compressed_pair.2" }
%"class.std::__ndk1::__compressed_pair.2" = type { %"struct.std::__ndk1::__compressed_pair_elem.3" }
%"struct.std::__ndk1::__compressed_pair_elem.3" = type { ptr }
%"class.std::__ndk1::basic_ostream" = type { ptr, %"class.std::__ndk1::basic_ios.base" }
%"class.std::__ndk1::basic_ios.base" = type <{ %"class.std::__ndk1::ios_base", ptr, i32 }>
%"class.std::__ndk1::ios_base" = type { ptr, i32, i64, i64, i32, i32, ptr, ptr, ptr, ptr, i64, i64, ptr, i64, i64, ptr, i64, i64 }
%"class.std::__ndk1::locale::id" = type <{ %"struct.std::__ndk1::once_flag", i32, [4 x i8] }>
%"struct.std::__ndk1::once_flag" = type { i64 }
%"struct.asmjit::v1_16::OperandSignature" = type { i32 }
%"class.std::__ndk1::unordered_map" = type { %"class.std::__ndk1::__hash_table" }
%"class.std::__ndk1::__hash_table" = type <{ %"class.std::__ndk1::unique_ptr.61", %"class.std::__ndk1::__compressed_pair.71", %"class.std::__ndk1::__compressed_pair.77", %"class.std::__ndk1::__compressed_pair.79", [4 x i8] }>
%"class.std::__ndk1::unique_ptr.61" = type { %"class.std::__ndk1::__compressed_pair.62" }
%"class.std::__ndk1::__compressed_pair.62" = type { %"struct.std::__ndk1::__compressed_pair_elem.63", %"struct.std::__ndk1::__compressed_pair_elem.64" }
%"struct.std::__ndk1::__compressed_pair_elem.63" = type { ptr }
%"struct.std::__ndk1::__compressed_pair_elem.64" = type { %"class.std::__ndk1::__bucket_list_deallocator" }
%"class.std::__ndk1::__bucket_list_deallocator" = type { %"class.std::__ndk1::__compressed_pair.65" }
%"class.std::__ndk1::__compressed_pair.65" = type { %"struct.std::__ndk1::__compressed_pair_elem.66" }
%"struct.std::__ndk1::__compressed_pair_elem.66" = type { i64 }
%"class.std::__ndk1::__compressed_pair.71" = type { %"struct.std::__ndk1::__compressed_pair_elem.72" }
%"struct.std::__ndk1::__compressed_pair_elem.72" = type { %"struct.std::__ndk1::__hash_node_base" }
%"struct.std::__ndk1::__hash_node_base" = type { ptr }
%"class.std::__ndk1::__compressed_pair.77" = type { %"struct.std::__ndk1::__compressed_pair_elem.66" }
%"class.std::__ndk1::__compressed_pair.79" = type { %"struct.std::__ndk1::__compressed_pair_elem.80" }
%"struct.std::__ndk1::__compressed_pair_elem.80" = type { float }
%"struct.std::__ndk1::piecewise_construct_t" = type { i8 }
%"struct.std::__ndk1::basic_string<char>::__long" = type { %struct.anon, i64, ptr }
%struct.anon = type { i64 }
%"struct.std::__ndk1::__exception_guard" = type <{ %"class.std::__ndk1::vector<std::__ndk1::string>::__destroy_vector", i8, [7 x i8] }>
%"class.std::__ndk1::vector<std::__ndk1::string>::__destroy_vector" = type { ptr }
%"class.std::__ndk1::basic_string" = type { %"class.std::__ndk1::__compressed_pair" }
%"class.std::__ndk1::__compressed_pair" = type { %"struct.std::__ndk1::__compressed_pair_elem" }
%"struct.std::__ndk1::__compressed_pair_elem" = type { %"struct.std::__ndk1::basic_string<char>::__rep" }
%"struct.std::__ndk1::basic_string<char>::__rep" = type { %union.anon }
%union.anon = type { %"struct.std::__ndk1::basic_string<char>::__long" }
%"class.default_modules::module_su_redirect::PatchSysNewfstatat" = type { %"class.default_modules::module_su_redirect::PatchBase", i64 }
%"class.default_modules::module_su_redirect::PatchBase" = type { i32, i32, i32, %"class.std::__ndk1::basic_string" }
%"struct.(anonymous namespace)::aarch64_asm_ctx" = type { %"class.std::__ndk1::unique_ptr", %"class.std::__ndk1::unique_ptr.13", %"class.std::__ndk1::unique_ptr.23", %"class.std::__ndk1::unique_ptr.29" }
%"class.std::__ndk1::unique_ptr" = type { %"class.std::__ndk1::__compressed_pair.8" }
%"class.std::__ndk1::__compressed_pair.8" = type { %"struct.std::__ndk1::__compressed_pair_elem.9" }
%"struct.std::__ndk1::__compressed_pair_elem.9" = type { ptr }
%"class.std::__ndk1::unique_ptr.13" = type { %"class.std::__ndk1::__compressed_pair.14" }
%"class.std::__ndk1::__compressed_pair.14" = type { %"struct.std::__ndk1::__compressed_pair_elem.15" }
%"struct.std::__ndk1::__compressed_pair_elem.15" = type { ptr }
%"class.std::__ndk1::unique_ptr.23" = type { %"class.std::__ndk1::__compressed_pair.24" }
%"class.std::__ndk1::__compressed_pair.24" = type { %"struct.std::__ndk1::__compressed_pair_elem.25" }
%"struct.std::__ndk1::__compressed_pair_elem.25" = type { ptr }
%"class.std::__ndk1::unique_ptr.29" = type { %"class.std::__ndk1::__compressed_pair.30" }
%"class.std::__ndk1::__compressed_pair.30" = type { %"struct.std::__ndk1::__compressed_pair_elem.31" }
%"struct.std::__ndk1::__compressed_pair_elem.31" = type { ptr }
%"class.asmjit::v1_16::Label" = type { %"class.asmjit::v1_16::Operand" }
%"class.asmjit::v1_16::Operand" = type { %"struct.asmjit::v1_16::Operand_" }
%"struct.asmjit::v1_16::Operand_" = type { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] }
%"class.std::__ndk1::vector.49" = type { ptr, ptr, %"class.std::__ndk1::__compressed_pair.50" }
%"class.std::__ndk1::__compressed_pair.50" = type { %"struct.std::__ndk1::__compressed_pair_elem.51" }
%"struct.std::__ndk1::__compressed_pair_elem.51" = type { ptr }
%"class.asmjit::v1_16::a64::GpX" = type { %"class.asmjit::v1_16::a64::Gp" }
%"class.asmjit::v1_16::a64::Gp" = type { %"class.asmjit::v1_16::arm::Reg" }
%"class.asmjit::v1_16::arm::Reg" = type { %"class.asmjit::v1_16::BaseReg" }
%"class.asmjit::v1_16::BaseReg" = type { %"class.asmjit::v1_16::Operand" }
%"class.asmjit::v1_16::arm::Mem" = type { %"class.asmjit::v1_16::BaseMem" }
%"class.asmjit::v1_16::BaseMem" = type { %"class.asmjit::v1_16::Operand" }
%class.RegProtectGuard = type <{ ptr, %"class.std::__ndk1::vector.37", i8, [3 x i8], %"class.asmjit::v1_16::a64::GpX", %"class.asmjit::v1_16::a64::GpX", [4 x i8] }>
%"class.std::__ndk1::vector.37" = type { ptr, ptr, %"class.std::__ndk1::__compressed_pair.38" }
%"class.std::__ndk1::__compressed_pair.38" = type { %"struct.std::__ndk1::__compressed_pair_elem.39" }
%"struct.std::__ndk1::__compressed_pair_elem.39" = type { ptr }
%"struct.std::__ndk1::basic_string<char>::__short" = type { %struct.anon.0, [0 x i8], [23 x i8] }
%struct.anon.0 = type { i8 }
%"class.asmjit::v1_16::BaseAssembler" = type { %"class.asmjit::v1_16::BaseEmitter", ptr, ptr, ptr, ptr }
%"class.asmjit::v1_16::BaseEmitter" = type { ptr, i8, i8, i8, i8, i32, i32, i64, i32, i32, i32, ptr, ptr, ptr, %"class.asmjit::v1_16::Environment", %"struct.asmjit::v1_16::OperandSignature", i32, %"struct.asmjit::v1_16::RegOnly", ptr, %"struct.asmjit::v1_16::BaseEmitter::Funcs" }
%"class.asmjit::v1_16::Environment" = type { i8, i8, i8, i8, i8, i8, i8, i8 }
%"struct.asmjit::v1_16::RegOnly" = type { %"struct.asmjit::v1_16::OperandSignature", i32 }
%"struct.asmjit::v1_16::BaseEmitter::Funcs" = type { ptr, ptr, ptr, ptr, ptr }
%"class.(anonymous namespace)::MyAsmJitErrorHandler" = type <{ %"class.asmjit::v1_16::ErrorHandler", i8, [7 x i8] }>
%"class.asmjit::v1_16::ErrorHandler" = type { ptr }
%"class.asmjit::v1_16::CodeHolder" = type { %"class.asmjit::v1_16::Environment", %"class.asmjit::v1_16::CpuFeatures", i64, ptr, ptr, %"class.asmjit::v1_16::Zone", %"class.asmjit::v1_16::ZoneAllocator", %"class.asmjit::v1_16::ZoneVector", %"class.asmjit::v1_16::ZoneVector.17", %"class.asmjit::v1_16::ZoneVector.17", %"class.asmjit::v1_16::ZoneVector.18", %"class.asmjit::v1_16::ZoneVector.19", %"class.asmjit::v1_16::ZoneHash", i64, ptr, %"class.asmjit::v1_16::ZoneTree" }
%"class.asmjit::v1_16::CpuFeatures" = type { %"struct.asmjit::v1_16::CpuFeatures::Data" }
%"struct.asmjit::v1_16::CpuFeatures::Data" = type { %"struct.asmjit::v1_16::Support::Array.16" }
%"struct.asmjit::v1_16::Support::Array.16" = type { [4 x i64] }
%"class.asmjit::v1_16::Zone" = type { ptr, ptr, ptr, i8, i8, i8, i8, i32, i64 }
%"class.asmjit::v1_16::ZoneAllocator" = type { ptr, [10 x ptr], ptr }
%"class.asmjit::v1_16::ZoneVector" = type { %"class.asmjit::v1_16::ZoneVectorBase" }
%"class.asmjit::v1_16::ZoneVectorBase" = type { ptr, i32, i32 }
%"class.asmjit::v1_16::ZoneVector.17" = type { %"class.asmjit::v1_16::ZoneVectorBase" }
%"class.asmjit::v1_16::ZoneVector.18" = type { %"class.asmjit::v1_16::ZoneVectorBase" }
%"class.asmjit::v1_16::ZoneVector.19" = type { %"class.asmjit::v1_16::ZoneVectorBase" }
%"class.asmjit::v1_16::ZoneHash" = type { %"class.asmjit::v1_16::ZoneHashBase" }
%"class.asmjit::v1_16::ZoneHashBase" = type { ptr, i64, i32, i32, i32, i8, i8, [1 x ptr] }
%"class.asmjit::v1_16::ZoneTree" = type { ptr }
%"class.asmjit::v1_16::Section" = type { i32, i32, i32, i32, i64, i64, %"union.asmjit::v1_16::FixedString", %"struct.asmjit::v1_16::CodeBuffer" }
%"union.asmjit::v1_16::FixedString" = type { [9 x i32] }
%"struct.asmjit::v1_16::CodeBuffer" = type { ptr, i64, i64, i32 }
%"struct.std::__ndk1::__exception_guard.46" = type <{ %"class.std::__ndk1::_AllocatorDestroyRangeReverse", i8, [7 x i8] }>
%"class.std::__ndk1::_AllocatorDestroyRangeReverse" = type { ptr, ptr, ptr }
%"class.std::__ndk1::basic_ostream<char>::sentry" = type { i8, ptr }
%"class.std::__ndk1::basic_ios" = type <{ %"class.std::__ndk1::ios_base", ptr, i32, [4 x i8] }>
%"class.std::__ndk1::locale" = type { ptr }
%"class.std::__ndk1::vector<asmjit::v1_16::a64::GpX>::__destroy_vector" = type { ptr }
%"struct.std::__ndk1::__exception_guard.56" = type <{ %"class.std::__ndk1::vector<unsigned char>::__destroy_vector", i8, [7 x i8] }>
%"class.std::__ndk1::vector<unsigned char>::__destroy_vector" = type { ptr }
%"class.std::__ndk1::tuple" = type { %"struct.std::__ndk1::__tuple_impl" }
%"struct.std::__ndk1::__tuple_impl" = type { %"class.std::__ndk1::__tuple_leaf", %"class.std::__ndk1::__tuple_leaf.82", %"class.std::__ndk1::__tuple_leaf.83" }
%"class.std::__ndk1::__tuple_leaf" = type { %"class.asmjit::v1_16::a64::GpX" }
%"class.std::__ndk1::__tuple_leaf.82" = type { %"class.asmjit::v1_16::a64::GpX" }
%"class.std::__ndk1::__tuple_leaf.83" = type { i64 }
%"struct.std::__ndk1::hash.86" = type { i8 }
%class.anon.88 = type { ptr, ptr, ptr }
%"struct.std::__ndk1::__hash_node" = type { %"struct.std::__ndk1::__hash_node_base", i64, %"struct.std::__ndk1::__hash_value_type" }
%"struct.std::__ndk1::__hash_value_type" = type { %"struct.std::__ndk1::pair.89" }
%"struct.std::__ndk1::pair.89" = type { i64, %"class.std::__ndk1::basic_string" }
%class.anon.93 = type { ptr, ptr, ptr }
%"class.std::__ndk1::tuple.150" = type { %"struct.std::__ndk1::__tuple_impl.151" }
%"struct.std::__ndk1::__tuple_impl.151" = type { %"class.std::__ndk1::__tuple_leaf.152" }
%"class.std::__ndk1::__tuple_leaf.152" = type { ptr }
%"class.std::__ndk1::tuple.153" = type { i8 }
%"struct.std::__ndk1::__murmur2_or_cityhash" = type { i8 }
%struct.Arm64Arg = type <{ i8, [3 x i8], %"class.asmjit::v1_16::a64::GpX", %"class.asmjit::v1_16::a64::GpW", [4 x i8], i64, i32, [4 x i8] }>
%"class.asmjit::v1_16::a64::GpW" = type { %"class.asmjit::v1_16::a64::Gp" }
%"class.std::__ndk1::vector.94" = type { ptr, ptr, %"class.std::__ndk1::__compressed_pair.95" }
%"class.std::__ndk1::__compressed_pair.95" = type { %"struct.std::__ndk1::__compressed_pair_elem.96" }
%"struct.std::__ndk1::__compressed_pair_elem.96" = type { ptr }
%"class.std::__ndk1::set" = type { %"class.std::__ndk1::__tree" }
%"class.std::__ndk1::__tree" = type { ptr, %"class.std::__ndk1::__compressed_pair.110", %"class.std::__ndk1::__compressed_pair.116" }
%"class.std::__ndk1::__compressed_pair.110" = type { %"struct.std::__ndk1::__compressed_pair_elem.111" }
%"struct.std::__ndk1::__compressed_pair_elem.111" = type { %"class.std::__ndk1::__tree_end_node" }
%"class.std::__ndk1::__tree_end_node" = type { ptr }
%"class.std::__ndk1::__compressed_pair.116" = type { %"struct.std::__ndk1::__compressed_pair_elem.66" }
%class.IdleRegPool = type { %"class.std::__ndk1::set", %"class.std::__ndk1::set" }
%"class.std::__ndk1::__tree_node_base" = type <{ %"class.std::__ndk1::__tree_end_node", ptr, ptr, i8, [7 x i8] }>
%"class.std::__ndk1::vector<Arm64Arg>::__destroy_vector" = type { ptr }
%"struct.std::__ndk1::__split_buffer" = type { ptr, ptr, ptr, %"class.std::__ndk1::__compressed_pair.101" }
%"class.std::__ndk1::__compressed_pair.101" = type { %"struct.std::__ndk1::__compressed_pair_elem.96", %"struct.std::__ndk1::__compressed_pair_elem.102" }
%"struct.std::__ndk1::__compressed_pair_elem.102" = type { ptr }
%"class.std::__ndk1::__tree_node" = type { %"class.std::__ndk1::__tree_node_base.base", i32 }
%"class.std::__ndk1::__tree_node_base.base" = type <{ %"class.std::__ndk1::__tree_end_node", ptr, ptr, i8 }>
%"class.std::__ndk1::unique_ptr.122" = type { %"class.std::__ndk1::__compressed_pair.123" }
%"class.std::__ndk1::__compressed_pair.123" = type { %"struct.std::__ndk1::__compressed_pair_elem.124", %"struct.std::__ndk1::__compressed_pair_elem.125" }
%"struct.std::__ndk1::__compressed_pair_elem.124" = type { ptr }
%"struct.std::__ndk1::__compressed_pair_elem.125" = type { %"class.std::__ndk1::__tree_node_destructor" }
%"class.std::__ndk1::__tree_node_destructor" = type <{ ptr, i8, [7 x i8] }>
%"class.std::__ndk1::__tree_const_iterator" = type { ptr }
%"struct.std::__ndk1::__split_buffer.127" = type { ptr, ptr, ptr, %"class.std::__ndk1::__compressed_pair.128" }
%"class.std::__ndk1::__compressed_pair.128" = type { %"struct.std::__ndk1::__compressed_pair_elem.39", %"struct.std::__ndk1::__compressed_pair_elem.129" }
%"struct.std::__ndk1::__compressed_pair_elem.129" = type { ptr }
%"struct.std::__ndk1::__exception_guard.139" = type <{ %"class.std::__ndk1::vector<Arm64Arg>::__destroy_vector", i8, [7 x i8] }>
%"struct.kernel_module::SymbolHit" = type { i64, [1024 x i8] }
%"class.asmjit::v1_16::Imm" = type { %"class.asmjit::v1_16::Operand" }
%"class.std::__ndk1::unique_ptr.144" = type { %"class.std::__ndk1::__compressed_pair.145" }
%"class.std::__ndk1::__compressed_pair.145" = type { %"struct.std::__ndk1::__compressed_pair_elem.146", %"struct.std::__ndk1::__compressed_pair_elem.147" }
%"struct.std::__ndk1::__compressed_pair_elem.146" = type { ptr }
%"struct.std::__ndk1::__compressed_pair_elem.147" = type { %"class.std::__ndk1::__hash_node_destructor" }
%"class.std::__ndk1::__hash_node_destructor" = type <{ ptr, i8, [7 x i8] }>

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc = comdat any

$_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEC2B7v170000ESt16initializer_listIS6_E = comdat any

$_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3cbzERKNS1_2GpERKNS0_5LabelE = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3ldrERKNS1_2GpERKNS0_3arm3MemE = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_ = comdat any

$_ZN15RegProtectGuardC2IJN6asmjit5v1_163a643GpXEEvEEPNS3_9AssemblerEDpRKT_ = comdat any

$_ZN15RegProtectGuardD2Ev = comdat any

$_ZN13kernel_module13export_symbol12copy_to_userEPN6asmjit5v1_163a649AssemblerER7KModErrNS3_3GpXES8_m = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4cbnzERKNS1_2GpERKNS0_5LabelE = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3strERKNS1_2GpERKNS0_3arm3MemE = comdat any

$_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev = comdat any

$_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m = comdat any

$_ZNSt6__ndk120__throw_length_errorB7v170000EPKc = comdat any

$_ZNSt12length_errorC2B7v170000EPKc = comdat any

$_ZSt28__throw_bad_array_new_lengthB7v170000v = comdat any

$__clang_call_terminate = comdat any

$_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE11__vallocateB7v170000Em = comdat any

$_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE18__construct_at_endIPKS6_Li0EEEvT_SC_m = comdat any

$_ZNSt6__ndk117__exception_guardINS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEED2B7v170000Ev = comdat any

$_ZNKSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE20__throw_length_errorB7v170000Ev = comdat any

$_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERSA_m = comdat any

$_ZNSt6__ndk19allocatorINS_12basic_stringIcNS_11char_traitsIcEENS0_IcEEEEE8allocateB7v170000Em = comdat any

$_ZNSt6__ndk130__uninitialized_allocator_copyB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPKS6_S9_PS6_EET2_RT_T0_T1_SB_ = comdat any

$_ZNSt6__ndk117__exception_guardINS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEED2B7v170000Ev = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE25__init_copy_ctor_externalEPKcm = comdat any

$_ZNKSt6__ndk129_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPS6_EclB7v170000Ev = comdat any

$_ZNSt6__ndk119__allocator_destroyB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEENS_16reverse_iteratorIPS6_EESA_EEvRT_T0_T1_ = comdat any

$_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE16__destroy_vectorclB7v170000Ev = comdat any

$_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE7__clearB7v170000Ev = comdat any

$_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE22__base_destruct_at_endB7v170000EPS6_ = comdat any

$_ZNSt6__ndk111make_uniqueB7v170000IN6asmjit5v1_1610CodeHolderEJEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_ = comdat any

$_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1610CodeHolderENS_14default_deleteIS3_EEED2B7v170000Ev = comdat any

$_ZNSt6__ndk111make_uniqueB7v170000IN6asmjit5v1_1612StringLoggerEJEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_ = comdat any

$_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1612StringLoggerENS_14default_deleteIS3_EEED2B7v170000Ev = comdat any

$_ZNSt6__ndk111make_uniqueB7v170000IN6asmjit5v1_163a649AssemblerEJPNS2_10CodeHolderEEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_ = comdat any

$_ZNSt6__ndk110unique_ptrIN6asmjit5v1_163a649AssemblerENS_14default_deleteIS4_EEED2B7v170000Ev = comdat any

$_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1610CodeHolderENS_14default_deleteIS3_EEE5resetB7v170000EPS3_ = comdat any

$_ZNKSt6__ndk114default_deleteIN6asmjit5v1_1610CodeHolderEEclB7v170000EPS3_ = comdat any

$_ZNSt6__ndk1lsB7v170000INS_11char_traitsIcEEEERNS_13basic_ostreamIcT_EES6_PKc = comdat any

$_ZNSt6__ndk14endlB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_ = comdat any

$_ZNSt6__ndk124__put_character_sequenceB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m = comdat any

$_ZNSt6__ndk116__pad_and_outputB7v170000IcNS_11char_traitsIcEEEENS_19ostreambuf_iteratorIT_T0_EES6_PKS4_S8_S8_RNS_8ios_baseES4_ = comdat any

$_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4fillB7v170000Ev = comdat any

$_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE5widenB7v170000Ec = comdat any

$_ZNSt6__ndk19use_facetB7v170000INS_5ctypeIcEEEERKT_RKNS_6localeE = comdat any

$_ZNSt6__ndk18ios_base8setstateB7v170000Ej = comdat any

$_ZN15RegProtectGuard7cleanupEv = comdat any

$_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEED2B7v170000Ev = comdat any

$_ZN15RegProtectGuard10doPopPairsEv = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3ldpERKNS1_2GpES7_RKNS0_3arm3MemE = comdat any

$_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE16__destroy_vectorclB7v170000Ev = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEEC2EmRKh = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE11__vallocateB7v170000Em = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE18__construct_at_endEmRKh = comdat any

$_ZNSt6__ndk117__exception_guardINS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEED2B7v170000Ev = comdat any

$_ZNKSt6__ndk16vectorIhNS_9allocatorIhEEE20__throw_length_errorB7v170000Ev = comdat any

$_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIhEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE16__destroy_vectorclB7v170000Ev = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE1bERKNS0_5LabelE = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3adrERKNS1_2GpERKNS0_5LabelE = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3nopEv = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEEC2IPKhLi0EEET_S7_ = comdat any

$_ZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ = comdat any

$_ZNSt6__ndk113unordered_mapImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS_4hashImEENS_8equal_toImEENS4_INS_4pairIKmS6_EEEEED2B7v170000Ev = comdat any

$_ZNKSt6__ndk118__string_view_hashIcEclB7v170000ENS_17basic_string_viewIcNS_11char_traitsIcEEEE = comdat any

$_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEcvNS_17basic_string_viewIcS2_EEB7v170000Ev = comdat any

$_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ENKUlPKcE_clESS_ = comdat any

$_ZNSt6__ndk1eqB7v170000INS_9allocatorIcEEEEbRKNS_12basic_stringIcNS_11char_traitsIcEET_EES9_ = comdat any

$_ZNSt6__ndk113unordered_mapImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS_4hashImEENS_8equal_toImEENS4_INS_4pairIKmS6_EEEEEixERSC_ = comdat any

$_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEED2Ev = comdat any

$_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE17__deallocate_nodeEPNS_16__hash_node_baseIPNS_11__hash_nodeIS8_PvEEEE = comdat any

$_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEED2B7v170000Ev = comdat any

$_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEE5resetB7v170000EDn = comdat any

$_ZNSt6__ndk116__do_string_hashB7v170000IPKcEEmT_S3_ = comdat any

$_ZNSt6__ndk121__murmur2_or_cityhashImLm64EEclEPKvm = comdat any

$_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE18__hash_len_0_to_16EPKcm = comdat any

$_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE19__hash_len_17_to_32EPKcm = comdat any

$_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE19__hash_len_33_to_64EPKcm = comdat any

$_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE13__hash_len_16Emm = comdat any

$_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE29__weak_hash_len_32_with_seedsEPKcmm = comdat any

$_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE4findImEENS_15__hash_iteratorIPNS_11__hash_nodeIS8_PvEEEERKT_ = comdat any

$_ZNSt6__ndk15applyB7v170000IZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES7_RmEEEvPNS5_9AssemblerER7KModErrRKNS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENSH_ISJ_EEEENS1_12NeedReturnX0EDpOT_ENKUlPKcE_clEST_EUlSR_E_RNS_5tupleIJS6_S6_mEEEEEDcOT_OT0_ = comdat any

$_ZNSt6__ndk118__apply_tuple_implB7v170000IZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES7_RmEEEvPNS5_9AssemblerER7KModErrRKNS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENSH_ISJ_EEEENS1_12NeedReturnX0EDpOT_ENKUlPKcE_clEST_EUlSR_E_RNS_5tupleIJS6_S6_mEEEJLm0ELm1ELm2EEEEDcOT_OT0_NS_15__tuple_indicesIJXspT1_EEEE = comdat any

$_ZZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ENKUlPKcE_clESS_ENKUlSQ_E_clIJS5_S5_S6_EEEDaSQ_ = comdat any

$_ZN26Aarch64KernelSymCallHelper12callNameAutoIJRN6asmjit5v1_163a643GpXES5_RmELi0EEE7KModErrPNS3_9AssemblerEPKcNS_12NeedReturnX0EDpOT_ = comdat any

$_ZN26Aarch64KernelSymCallHelper12callNameAutoEPN6asmjit5v1_163a649AssemblerEPKcNS_12NeedReturnX0ERKNSt6__ndk16vectorI8Arm64ArgNS8_9allocatorISA_EEEE = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE7reserveEm = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJS1_EEERS1_DpOT_ = comdat any

$_ZNKSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE20__throw_length_errorB7v170000Ev = comdat any

$_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEEC2EmmS4_ = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE26__swap_out_circular_bufferERNS_14__split_bufferIS1_RS3_EE = comdat any

$_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEED2Ev = comdat any

$_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorI8Arm64ArgEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS6_m = comdat any

$_ZNSt6__ndk19allocatorI8Arm64ArgE8allocateB7v170000Em = comdat any

$_ZNSt6__ndk142__uninitialized_allocator_move_if_noexceptB7v170000INS_9allocatorI8Arm64ArgEENS_16reverse_iteratorIPS2_EES6_S6_EET2_RT_T0_T1_S7_ = comdat any

$_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEE5clearB7v170000Ev = comdat any

$_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEE17__destruct_at_endB7v170000EPS1_ = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE24__emplace_back_slow_pathIJS1_EEEvDpOT_ = comdat any

$_ZNKSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE11__recommendB7v170000Em = comdat any

$_ZN8Arm64Arg1XERKN6asmjit5v1_163a643GpXE = comdat any

$_ZN8Arm64ArgC2Ev = comdat any

$_ZN8Arm64Arg3U64Em = comdat any

$_ZN26Aarch64KernelSymCallHelper14makeProtectSetENS_19AAPCS64_ProtectRuleE = comdat any

$_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEEC2B7v170000ERKS5_ = comdat any

$_ZN15RegProtectGuardC2EPN6asmjit5v1_163a649AssemblerERKNSt6__ndk13setIjNS5_4lessIjEENS5_9allocatorIjEEEE = comdat any

$_ZN26Aarch64KernelSymCallHelper19makeDirtyArgsX0ToX7ERKNSt6__ndk16vectorI8Arm64ArgNS0_9allocatorIS2_EEEE = comdat any

$_ZN11IdleRegPool11makeFromVecERKNSt6__ndk16vectorI8Arm64ArgNS0_9allocatorIS2_EEEE = comdat any

$_ZN26Aarch64KernelSymCallHelper17acquireNewArgRegsER11IdleRegPoolRKNSt6__ndk16vectorI8Arm64ArgNS2_9allocatorIS4_EEEE = comdat any

$_ZN26Aarch64KernelSymCallHelper21emitMovesFromOldToNewEPN6asmjit5v1_163a649AssemblerERKNSt6__ndk16vectorI8Arm64ArgNS5_9allocatorIS7_EEEESC_ = comdat any

$_ZN26Aarch64KernelSymCallHelper16call_by_name_rawEPN6asmjit5v1_163a649AssemblerEPKcRKNSt6__ndk16vectorI8Arm64ArgNS7_9allocatorIS9_EEEE = comdat any

$_ZN11IdleRegPoolD2Ev = comdat any

$_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000ERKj = comdat any

$_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000EOj = comdat any

$_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEEvT_SD_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE15__insert_uniqueB7v170000ERKj = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE25__emplace_unique_key_argsIjJRKjEEENS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEERKT_DpOT0_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE12__find_equalIjEERPNS_16__tree_node_baseIPvEERPNS_15__tree_end_nodeISA_EERKT_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__insert_node_atEPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEERSA_SA_ = comdat any

$_ZNSt6__ndk110unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS_9allocatorIS3_EEEEED2B7v170000Ev = comdat any

$_ZNSt6__ndk127__tree_balance_after_insertB7v170000IPNS_16__tree_node_baseIPvEEEEvT_S5_ = comdat any

$_ZNSt6__ndk118__tree_left_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_ = comdat any

$_ZNSt6__ndk119__tree_right_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_ = comdat any

$_ZNSt6__ndk110unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS_9allocatorIS3_EEEEE5resetB7v170000EPS3_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE15__insert_uniqueB7v170000EOj = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE25__emplace_unique_key_argsIjJjEEENS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEERKT_DpOT0_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE15__insert_uniqueB7v170000ENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEERKj = comdat any

$_ZNSt6__ndk121__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEppB7v170000Ev = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE30__emplace_hint_unique_key_argsIjJRKjEEENS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEENS_21__tree_const_iteratorIjSE_lEERKT_DpOT0_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE12__find_equalIjEERPNS_16__tree_node_baseIPvEENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjS8_EElEERPNS_15__tree_end_nodeISA_EESB_RKT_ = comdat any

$_ZNSt6__ndk121__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEmmB7v170000Ev = comdat any

$_ZNSt6__ndk14nextB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEENS_9enable_ifIXsr25__is_cpp17_input_iteratorIT_EE5valueES8_E4typeES8_NS_15iterator_traitsIS8_E15difference_typeE = comdat any

$_ZNSt6__ndk116__tree_prev_iterB7v170000IPNS_16__tree_node_baseIPvEEPNS_15__tree_end_nodeIS4_EEEET_T0_ = comdat any

$_ZNSt6__ndk110__tree_maxB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_ = comdat any

$_ZNSt6__ndk17advanceB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEllvEEvRT_T0_ = comdat any

$_ZNSt6__ndk19__advanceB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEEvRT_NS_15iterator_traitsIS7_E15difference_typeENS_26bidirectional_iterator_tagE = comdat any

$_ZNSt6__ndk116__tree_next_iterB7v170000IPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEES5_EET_T0_ = comdat any

$_ZNSt6__ndk110__tree_minB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE14__erase_uniqueIjEEmRKT_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE4findIjEENS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEERKT_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE5eraseENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEE = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE13__lower_boundIjEENS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEERKT_SB_PNS_15__tree_end_nodeIPNS_16__tree_node_baseIS9_EEEE = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE21__remove_node_pointerEPNS_11__tree_nodeIjPvEE = comdat any

$_ZNSt6__ndk113__tree_removeB7v170000IPNS_16__tree_node_baseIPvEEEEvT_S5_ = comdat any

$_ZNSt6__ndk111__tree_nextB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE7destroyEPNS_11__tree_nodeIjPvEE = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEaSEOS5_ = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE13__move_assignERS5_NS_17integral_constantIbLb1EEE = comdat any

$_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE7reserveEm = comdat any

$_ZN15RegProtectGuard15appendOneUniqueERKN6asmjit5v1_163a643GpXE = comdat any

$_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE9push_backB7v170000ERKS4_ = comdat any

$_ZN15RegProtectGuard11doPushPairsEv = comdat any

$_ZNKSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE20__throw_length_errorB7v170000Ev = comdat any

$_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE26__swap_out_circular_bufferERNS_14__split_bufferIS4_RS6_EE = comdat any

$_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEED2Ev = comdat any

$_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIN6asmjit5v1_163a643GpXEEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS9_m = comdat any

$_ZNSt6__ndk19allocatorIN6asmjit5v1_163a643GpXEE8allocateB7v170000Em = comdat any

$_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEE5clearB7v170000Ev = comdat any

$_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEE17__destruct_at_endB7v170000EPS4_ = comdat any

$_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE21__push_back_slow_pathIRKS4_EEvOT_ = comdat any

$_ZNKSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE11__recommendB7v170000Em = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3stpERKNS1_2GpES7_RKNS0_3arm3MemE = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEC2ERKS4_ = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE11__vallocateB7v170000Em = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE18__construct_at_endIPS1_Li0EEEvT_S7_m = comdat any

$_ZNSt6__ndk117__exception_guardINS_6vectorI8Arm64ArgNS_9allocatorIS2_EEE16__destroy_vectorEED2B7v170000Ev = comdat any

$_ZNSt6__ndk130__uninitialized_allocator_copyB7v170000INS_9allocatorI8Arm64ArgEEPS2_S4_S4_EET2_RT_T0_T1_S5_ = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE16__destroy_vectorclB7v170000Ev = comdat any

$_ZN11IdleRegPool4initEv = comdat any

$_ZN11IdleRegPool6useOneERKN6asmjit5v1_163a643GpXE = comdat any

$_ZN11IdleRegPool6useOneERKN6asmjit5v1_163a643GpWE = comdat any

$_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE5clearEv = comdat any

$_ZN11IdleRegPool8acquireXEv = comdat any

$_ZN11IdleRegPool8acquireWEv = comdat any

$_ZN8Arm64Arg1WERKN6asmjit5v1_163a643GpWE = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJRKS1_EEERS1_DpOT_ = comdat any

$_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE24__emplace_back_slow_pathIJRKS1_EEEvDpOT_ = comdat any

$_ZN13kernel_module20kallsyms_lookup_nameEPKcRm = comdat any

$_ZN26Aarch64KernelSymCallHelper15pushArgsFromVecEPN6asmjit5v1_163a649AssemblerERKNSt6__ndk16vectorI8Arm64ArgNS5_9allocatorIS7_EEEE = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movzERKNS1_2GpERKNS0_3ImmESA_ = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movkERKNS1_2GpERKNS0_3ImmESA_ = comdat any

$_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3blrERKNS1_2GpE = comdat any

$_ZNSt6__ndk111char_traitsIcE7compareEPKcS3_m = comdat any

$_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE5eraseENS_21__hash_const_iteratorIPNS_11__hash_nodeIS8_PvEEEE = comdat any

$_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE6removeENS_21__hash_const_iteratorIPNS_11__hash_nodeIS8_PvEEEE = comdat any

$_ZNSt6__ndk110unique_ptrINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEENS_22__hash_node_destructorINS6_ISB_EEEEED2B7v170000Ev = comdat any

$_ZNSt6__ndk110unique_ptrINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEENS_22__hash_node_destructorINS6_ISB_EEEEE5resetB7v170000EPSB_ = comdat any

$_ZNSt6__ndk122__hash_node_destructorINS_9allocatorINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPvEEEEEclB7v170000EPSB_ = comdat any

$_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE25__emplace_unique_key_argsImJRKNS_21piecewise_construct_tENS_5tupleIJRKmEEENSN_IJEEEEEENS_4pairINS_15__hash_iteratorIPNS_11__hash_nodeIS8_PvEEEEbEERKT_DpOT0_ = comdat any

$_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE21__construct_node_hashIRKNS_21piecewise_construct_tEJNS_5tupleIJRKmEEENSN_IJEEEEEENS_10unique_ptrINS_11__hash_nodeIS8_PvEENS_22__hash_node_destructorINS5_ISV_EEEEEEmOT_DpOT0_ = comdat any

$_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE8__rehashILb1EEEvm = comdat any

$_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE11__do_rehashILb1EEEvm = comdat any

$_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEE5resetB7v170000IPSF_EENS_9enable_ifIXsr28_CheckArrayPointerConversionIT_EE5valueEvE4typeESO_ = comdat any

$_ZNSt6__ndk19allocatorIPNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS0_IcEEEEEEPvEEEEE8allocateB7v170000Em = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb1EEERS5_PKcm = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb0EEERS5_PKcm = comdat any

$skroot_web_ui_enable_flag_e0c73f6473a0e653be67875a3cf23a53 = comdat any

$skroot_web_ui_handler_6f9d89cc84c2ea1bf7364fc1afda99b5 = comdat any

$_ZTSSt12length_error = comdat any

$_ZTSSt11logic_error = comdat any

$_ZTSSt9exception = comdat any

$_ZTISt9exception = comdat any

$_ZTISt11logic_error = comdat any

$_ZTISt12length_error = comdat any

$_ZTSSt20bad_array_new_length = comdat any

$_ZTSSt9bad_alloc = comdat any

$_ZTISt9bad_alloc = comdat any

$_ZTISt20bad_array_new_length = comdat any

$_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok = comdat any

$_ZGVZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok = comdat any

@_ZN13kernel_module13export_symbolL27copy_from_user_symbol_namesE = internal global %"class.std::__ndk1::vector" zeroinitializer, align 8
@.str = private unnamed_addr constant [22 x i8] c"__arch_copy_from_user\00", align 1
@.str.1 = private unnamed_addr constant [17 x i8] c"__copy_from_user\00", align 1
@.str.2 = private unnamed_addr constant [16 x i8] c"_copy_from_user\00", align 1
@.str.3 = private unnamed_addr constant [21 x i8] c"arm64_copy_from_user\00", align 1
@.str.4 = private unnamed_addr constant [15 x i8] c"copy_from_user\00", align 1
@__dso_handle = external hidden global i8
@_ZN13kernel_module13export_symbolL25copy_to_user_symbol_namesE = internal global %"class.std::__ndk1::vector" zeroinitializer, align 8
@.str.6 = private unnamed_addr constant [20 x i8] c"__arch_copy_to_user\00", align 1
@.str.7 = private unnamed_addr constant [15 x i8] c"__copy_to_user\00", align 1
@.str.8 = private unnamed_addr constant [14 x i8] c"_copy_to_user\00", align 1
@.str.9 = private unnamed_addr constant [19 x i8] c"arm64_copy_to_user\00", align 1
@.str.10 = private unnamed_addr constant [13 x i8] c"copy_to_user\00", align 1
@_ZN13kernel_module13export_symbolL37copy_from_kernel_nofault_symbol_namesE = internal global %"class.std::__ndk1::vector" zeroinitializer, align 8
@.str.12 = private unnamed_addr constant [25 x i8] c"copy_from_kernel_nofault\00", align 1
@.str.13 = private unnamed_addr constant [18 x i8] c"probe_kernel_read\00", align 1
@.str.14 = private unnamed_addr constant [20 x i8] c"__probe_kernel_read\00", align 1
@skroot_web_ui_enable_flag_e0c73f6473a0e653be67875a3cf23a53 = linkonce_odr hidden global [1024 x i8] zeroinitializer, comdat, align 1
@skroot_web_ui_handler_6f9d89cc84c2ea1bf7364fc1afda99b5 = linkonce_odr hidden local_unnamed_addr global ptr null, comdat, align 8
@.str.16 = private unnamed_addr constant [13 x i8] c"basic_string\00", align 1
@_ZTVN10__cxxabiv120__si_class_type_infoE = external global ptr
@_ZTSSt12length_error = linkonce_odr constant [17 x i8] c"St12length_error\00", comdat, align 1
@_ZTSSt11logic_error = linkonce_odr constant [16 x i8] c"St11logic_error\00", comdat, align 1
@_ZTVN10__cxxabiv117__class_type_infoE = external global ptr
@_ZTSSt9exception = linkonce_odr constant [13 x i8] c"St9exception\00", comdat, align 1
@_ZTISt9exception = linkonce_odr constant { ptr, ptr } { ptr getelementptr inbounds (ptr, ptr @_ZTVN10__cxxabiv117__class_type_infoE, i64 2), ptr @_ZTSSt9exception }, comdat, align 8
@_ZTISt11logic_error = linkonce_odr constant { ptr, ptr, ptr } { ptr getelementptr inbounds (ptr, ptr @_ZTVN10__cxxabiv120__si_class_type_infoE, i64 2), ptr @_ZTSSt11logic_error, ptr @_ZTISt9exception }, comdat, align 8
@_ZTISt12length_error = linkonce_odr constant { ptr, ptr, ptr } { ptr getelementptr inbounds (ptr, ptr @_ZTVN10__cxxabiv120__si_class_type_infoE, i64 2), ptr @_ZTSSt12length_error, ptr @_ZTISt11logic_error }, comdat, align 8
@_ZTVSt12length_error = available_externally unnamed_addr constant { [5 x ptr] } { [5 x ptr] [ptr null, ptr null, ptr @_ZNSt12length_errorD1Ev, ptr @_ZNSt12length_errorD0Ev, ptr @_ZNKSt11logic_error4whatEv] }, align 8
@_ZTSSt20bad_array_new_length = linkonce_odr constant [25 x i8] c"St20bad_array_new_length\00", comdat, align 1
@_ZTSSt9bad_alloc = linkonce_odr constant [13 x i8] c"St9bad_alloc\00", comdat, align 1
@_ZTISt9bad_alloc = linkonce_odr constant { ptr, ptr, ptr } { ptr getelementptr inbounds (ptr, ptr @_ZTVN10__cxxabiv120__si_class_type_infoE, i64 2), ptr @_ZTSSt9bad_alloc, ptr @_ZTISt9exception }, comdat, align 8
@_ZTISt20bad_array_new_length = linkonce_odr constant { ptr, ptr, ptr } { ptr getelementptr inbounds (ptr, ptr @_ZTVN10__cxxabiv120__si_class_type_infoE, i64 2), ptr @_ZTSSt20bad_array_new_length, ptr @_ZTISt9bad_alloc }, comdat, align 8
@.str.17 = private unnamed_addr constant [7 x i8] c"vector\00", align 1
@_ZTVN12_GLOBAL__N_120MyAsmJitErrorHandlerE = internal unnamed_addr constant { [5 x ptr] } { [5 x ptr] [ptr null, ptr null, ptr @_ZN6asmjit5v1_1612ErrorHandlerD2Ev, ptr @_ZN12_GLOBAL__N_120MyAsmJitErrorHandlerD0Ev, ptr @_ZN12_GLOBAL__N_120MyAsmJitErrorHandler11handleErrorEjPKcPN6asmjit5v1_1611BaseEmitterE] }, align 8, !type !0, !type !2, !type !4, !type !5
@_ZNSt6__ndk14cerrE = external global %"class.std::__ndk1::basic_ostream", align 8
@.str.18 = private unnamed_addr constant [16 x i8] c"[\E5\8F\91\E7\94\9F\E9\94\99\E8\AF\AF] \00", align 1
@.str.19 = private unnamed_addr constant [3 x i8] c": \00", align 1
@_ZNSt6__ndk15ctypeIcE2idE = external global %"class.std::__ndk1::locale::id", align 8
@_ZN6asmjit5v1_163a644regsL3x10E = internal constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 10, [2 x i32] zeroinitializer }, align 8
@_ZN6asmjit5v1_163a644regsL3x11E = internal constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 11, [2 x i32] zeroinitializer }, align 4
@_ZN6asmjit5v1_163a644regsL2x0E = internal constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 0, [2 x i32] zeroinitializer }, align 4
@_ZN6asmjit5v1_163a644regsL3x12E = internal constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 12, [2 x i32] zeroinitializer }, align 4
@_ZN6asmjit5v1_163a644regsL2x1E = internal constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 1, [2 x i32] zeroinitializer }, align 4
@_ZN6asmjit5v1_163a644regsL2x2E = internal constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 2, [2 x i32] zeroinitializer }, align 4
@_ZNSt6__ndk14coutE = external global %"class.std::__ndk1::basic_ostream", align 8
@.str.26 = private unnamed_addr constant [66 x i8] c"[\E5\8F\91\E7\94\9F\E9\94\99\E8\AF\AF] The ADR instruction offset must be a multiple of 4\00", align 1
@.str.27 = private unnamed_addr constant [59 x i8] c"[\E5\8F\91\E7\94\9F\E9\94\99\E8\AF\AF] ADR instruction offset exceeds \C2\B1 1MB range\00", align 1
@_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok = linkonce_odr hidden thread_local global %"class.std::__ndk1::unordered_map" zeroinitializer, comdat, align 8
@_ZGVZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok = linkonce_odr hidden thread_local local_unnamed_addr global i64 0, comdat, align 8
@.str.28 = private unnamed_addr constant [47 x i8] c"fallback candidate symbol '%s' found (cached)\0A\00", align 1
@.str.29 = private unnamed_addr constant [38 x i8] c"fallback candidate symbol '%s' found\0A\00", align 1
@_ZN6asmjit5v1_163a644regsL3xzrE = internal unnamed_addr constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 63, [2 x i32] zeroinitializer }, align 4
@_ZN6asmjit5v1_163a644regsL2spE = internal unnamed_addr constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 31, [2 x i32] zeroinitializer }, align 4
@_ZN6asmjit5v1_163a644regsL3x29E = internal constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 29, [2 x i32] zeroinitializer }, align 4
@_ZN6asmjit5v1_163a644regsL3x30E = internal constant { %"struct.asmjit::v1_16::OperandSignature", i32, [2 x i32] } { %"struct.asmjit::v1_16::OperandSignature" { i32 134217785 }, i32 30, [2 x i32] zeroinitializer }, align 4
@_ZNSt6__ndk1L19piecewise_constructE = internal constant %"struct.std::__ndk1::piecewise_construct_t" undef, align 1
@llvm.global_ctors = appending global [1 x { i32, ptr, ptr }] [{ i32, ptr, ptr } { i32 65535, ptr @_GLOBAL__sub_I_patch_sys_newfstatat.cpp, ptr null }]
@str = private unnamed_addr constant [26 x i8] c"IdleRegPoo pool is empty!\00", align 1
@str.36 = private unnamed_addr constant [27 x i8] c"IdleRegPool pool is empty!\00", align 1
@llvm.compiler.used = appending global [3 x ptr] [ptr @__skroot_module_before_server_exit_action_81a98535f12c96a0b3e457727705e44c, ptr @__skroot_module_last_web_ui_server_port_db2a2bebe2b190482ad0ac73996c7b86, ptr @__skroot_module_web_ui_main_ceb6f0bbebe5c8978eb2168799aa0ca4], section "llvm.metadata"

@_ZN15default_modules18module_su_redirect18PatchSysNewfstatatC1ERKNS0_9PatchBaseEm = hidden unnamed_addr alias void (ptr, ptr, i64), ptr @_ZN15default_modules18module_su_redirect18PatchSysNewfstatatC2ERKNS0_9PatchBaseEm
@_ZN15default_modules18module_su_redirect18PatchSysNewfstatatD1Ev = hidden unnamed_addr alias void (ptr), ptr @_ZN15default_modules18module_su_redirect18PatchSysNewfstatatD2Ev

; Function Attrs: nocallback nofree nosync nounwind willreturn memory(argmem: readwrite)
declare void @llvm.lifetime.start.p0(i64 immarg, ptr nocapture) #0

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = icmp ne ptr %1, null
  tail call void @llvm.assume(i1 %3)
  %4 = tail call i64 @strlen(ptr noundef nonnull dereferenceable(1) %1) #26
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1, i64 noundef %4) #27
  ret void
}

declare i32 @__gxx_personality_v0(...)

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #2 align 2 personality ptr @__gxx_personality_v0 {
  %2 = load i8, ptr %0, align 8
  %3 = and i8 %2, 1
  %4 = icmp eq i8 %3, 0
  br i1 %4, label %8, label %5

5:                                                ; preds = %1
  %6 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %7 = load ptr, ptr %6, align 8, !tbaa !12
  tail call void @_ZdlPv(ptr noundef %7) #28
  br label %8

8:                                                ; preds = %5, %1
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEC2B7v170000ESt16initializer_listIS6_E(ptr noundef nonnull align 8 dereferenceable(24) %0, [2 x i64] %1) unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = alloca %"struct.std::__ndk1::__exception_guard", align 8
  %4 = extractvalue [2 x i64] %1, 0
  %5 = inttoptr i64 %4 to ptr
  %6 = extractvalue [2 x i64] %1, 1
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %3) #24
  store ptr %0, ptr %3, align 8, !tbaa.struct !15, !alias.scope !18
  %7 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard", ptr %3, i64 0, i32 1
  store i8 0, ptr %7, align 8, !tbaa !21, !alias.scope !18
  %8 = icmp eq i64 %6, 0
  br i1 %8, label %14, label %9

9:                                                ; preds = %2
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE11__vallocateB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %6) #27
          to label %10 unwind label %12

10:                                               ; preds = %9
  %11 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %5, i64 %6
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE18__construct_at_endIPKS6_Li0EEEvT_SC_m(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %5, ptr noundef nonnull %11, i64 noundef %6) #27
          to label %14 unwind label %12

12:                                               ; preds = %10, %9
  %13 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %3) #26
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %3) #24
  resume { ptr, i32 } %13

14:                                               ; preds = %10, %2
  store i8 1, ptr %7, align 8, !tbaa !21
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %3) #26
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %3) #24
  ret void
}

; Function Attrs: nocallback nofree nosync nounwind willreturn memory(argmem: readwrite)
declare void @llvm.lifetime.end.p0(i64 immarg, ptr nocapture) #0

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::vector<std::__ndk1::string>::__destroy_vector", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #24
  store ptr %0, ptr %2, align 8, !tbaa !16
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %2) #27
          to label %3 unwind label %4

3:                                                ; preds = %1
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #24
  ret void

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          catch ptr null
  %6 = extractvalue { ptr, i32 } %5, 0
  call void @__clang_call_terminate(ptr %6) #29
  unreachable
}

; Function Attrs: nofree nounwind
declare i32 @__cxa_atexit(ptr, ptr, ptr) local_unnamed_addr #5

; Function Attrs: minsize mustprogress optsize uwtable
define weak void @__skroot_module_web_ui_main_ceb6f0bbebe5c8978eb2168799aa0ca4(ptr noundef %0, ptr noundef %1, ptr noundef %2, ptr noundef %3, i32 noundef %4, ptr noundef nonnull align 8 dereferenceable(8) %5) #6 {
  %7 = tail call i64 @__strlen_chk(ptr noundef nonnull @skroot_web_ui_enable_flag_e0c73f6473a0e653be67875a3cf23a53, i64 noundef 1024) #27
  %8 = load ptr, ptr @skroot_web_ui_handler_6f9d89cc84c2ea1bf7364fc1afda99b5, align 8, !tbaa !16
  %9 = tail call i64 @unsafe_module_start_web_ui_server(ptr noundef %0, ptr noundef %1, ptr noundef %2, ptr noundef %3, ptr noundef %8, i32 noundef %4) #27
  store i64 %9, ptr %5, align 8, !tbaa !25
  ret void
}

; Function Attrs: minsize nofree nounwind optsize
declare noundef i32 @printf(ptr nocapture noundef readonly, ...) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare i64 @unsafe_module_start_web_ui_server(ptr noundef, ptr noundef, ptr noundef, ptr noundef, ptr noundef, i32 noundef) local_unnamed_addr #8

; Function Attrs: minsize mustprogress optsize uwtable
define weak void @__skroot_module_last_web_ui_server_port_db2a2bebe2b190482ad0ac73996c7b86(ptr noundef nonnull align 4 dereferenceable(4) %0, ptr noundef nonnull align 8 dereferenceable(8) %1) #6 {
  %3 = tail call i64 @__strlen_chk(ptr noundef nonnull @skroot_web_ui_enable_flag_e0c73f6473a0e653be67875a3cf23a53, i64 noundef 1024) #27
  %4 = tail call i64 @unsafe_module_get_last_web_ui_server_port(ptr noundef nonnull align 4 dereferenceable(4) %0) #27
  store i64 %4, ptr %1, align 8, !tbaa !25
  ret void
}

; Function Attrs: minsize optsize
declare i64 @unsafe_module_get_last_web_ui_server_port(ptr noundef nonnull align 4 dereferenceable(4)) local_unnamed_addr #8

; Function Attrs: minsize mustprogress optsize uwtable
define weak void @__skroot_module_before_server_exit_action_81a98535f12c96a0b3e457727705e44c(ptr noundef nonnull align 4 dereferenceable(4) %0, ptr noundef nonnull align 8 dereferenceable(8) %1) #6 {
  %3 = tail call i64 @__strlen_chk(ptr noundef nonnull @skroot_web_ui_enable_flag_e0c73f6473a0e653be67875a3cf23a53, i64 noundef 1024) #27
  %4 = tail call i64 @unsafe_module_get_before_server_exit_action(ptr noundef nonnull align 4 dereferenceable(4) %0) #27
  store i64 %4, ptr %1, align 8, !tbaa !25
  ret void
}

; Function Attrs: minsize optsize
declare i64 @unsafe_module_get_before_server_exit_action(ptr noundef nonnull align 4 dereferenceable(4)) local_unnamed_addr #8

; Function Attrs: minsize optsize uwtable
define hidden void @_ZN15default_modules18module_su_redirect18PatchSysNewfstatatC2ERKNS0_9PatchBaseEm(ptr noundef nonnull align 8 dereferenceable(48) %0, ptr noundef nonnull align 8 dereferenceable(40) %1, i64 noundef %2) unnamed_addr #1 align 2 {
  tail call void @_ZN15default_modules18module_su_redirect9PatchBaseC2ERKS1_(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef nonnull align 8 dereferenceable(40) %1) #27
  %4 = getelementptr inbounds %"class.default_modules::module_su_redirect::PatchSysNewfstatat", ptr %0, i64 0, i32 1
  store i64 %2, ptr %4, align 8, !tbaa !27
  ret void
}

; Function Attrs: minsize optsize
declare void @_ZN15default_modules18module_su_redirect9PatchBaseC2ERKS1_(ptr noundef nonnull align 8 dereferenceable(40), ptr noundef nonnull align 8 dereferenceable(40)) unnamed_addr #8

; Function Attrs: minsize nounwind optsize
declare void @_ZN15default_modules18module_su_redirect9PatchBaseD2Ev(ptr noundef nonnull align 8 dereferenceable(40)) unnamed_addr #9

; Function Attrs: minsize nounwind optsize uwtable
define hidden void @_ZN15default_modules18module_su_redirect18PatchSysNewfstatatD2Ev(ptr noundef nonnull align 8 dereferenceable(48) %0) unnamed_addr #4 align 2 {
  tail call void @_ZN15default_modules18module_su_redirect9PatchBaseD2Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) #26
  ret void
}

; Function Attrs: minsize optsize uwtable
define hidden noundef i64 @_ZN15default_modules18module_su_redirect18PatchSysNewfstatat20patch_sys_newfstatatEm(ptr noundef nonnull align 8 dereferenceable(48) %0, i64 noundef %1) local_unnamed_addr #1 align 2 personality ptr @__gxx_personality_v0 !type !36 {
  %3 = alloca %"struct.(anonymous namespace)::aarch64_asm_ctx", align 8
  %4 = alloca %"class.asmjit::v1_16::Label", align 4
  %5 = alloca %"class.std::__ndk1::vector.49", align 8
  %6 = alloca i8, align 4
  %7 = alloca %"class.std::__ndk1::vector.49", align 8
  %8 = alloca %"class.std::__ndk1::vector.49", align 8
  %9 = alloca i8, align 4
  %10 = alloca %"class.asmjit::v1_16::Label", align 4
  %11 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %12 = alloca %"class.std::__ndk1::vector.49", align 8
  %13 = alloca i8, align 4
  %14 = alloca i64, align 8
  %15 = alloca %"class.std::__ndk1::basic_string", align 8
  %16 = alloca %"struct.(anonymous namespace)::aarch64_asm_ctx", align 8
  %17 = alloca %"class.asmjit::v1_16::Label", align 4
  %18 = alloca %"class.asmjit::v1_16::arm::Mem", align 4
  %19 = alloca %"class.asmjit::v1_16::arm::Mem", align 4
  %20 = alloca %class.RegProtectGuard, align 8
  %21 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %22 = alloca %class.RegProtectGuard, align 8
  %23 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %24 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %25 = alloca %"class.asmjit::v1_16::arm::Mem", align 4
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %14) #24
  store i64 0, ptr %14, align 8, !tbaa !25
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %15) #24
  call void @maplesu_stat_uid_template_84(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %15, ptr noundef nonnull align 8 dereferenceable(40) %0) #27
  call void @llvm.lifetime.start.p0(i64 32, ptr nonnull %16) #24
  invoke fastcc void @_ZN12_GLOBAL__N_116init_aarch64_asmEv(ptr nonnull sret(%"struct.(anonymous namespace)::aarch64_asm_ctx") align 8 %16) #27
          to label %26 unwind label %50

26:                                               ; preds = %2
  %27 = getelementptr inbounds %"struct.(anonymous namespace)::aarch64_asm_ctx", ptr %16, i64 0, i32 3
  %28 = load ptr, ptr %27, align 8, !tbaa !16
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %17) #24
  %29 = load ptr, ptr %28, align 8, !tbaa !37
  %30 = getelementptr inbounds ptr, ptr %29, i64 4
  %31 = load ptr, ptr %30, align 8
  invoke void %31(ptr nonnull sret(%"class.asmjit::v1_16::Label") align 4 %17, ptr noundef nonnull align 8 dereferenceable(176) %28) #27
          to label %32 unwind label %52

32:                                               ; preds = %26
  invoke void @_ZN13kernel_module23arm64_before_hook_startEPN6asmjit5v1_163a649AssemblerE(ptr noundef nonnull %28) #27
          to label %33 unwind label %52

33:                                               ; preds = %32
  invoke void @_ZN15default_modules18module_su_redirect9PatchBase34emit_check_current_allow_su_to_x10EPN6asmjit5v1_163a649AssemblerEm(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef nonnull %28, i64 noundef %1) #27
          to label %34 unwind label %52

34:                                               ; preds = %33
  %35 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3cbzERKNS1_2GpERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x10E, ptr noundef nonnull align 4 dereferenceable(16) %17) #27
          to label %36 unwind label %52

36:                                               ; preds = %34
  %37 = invoke noundef i1 @_ZN13kernel_module25is_syscall_args_in_ptregsEv() #27
          to label %38 unwind label %52

38:                                               ; preds = %36
  br i1 %37, label %39, label %58

39:                                               ; preds = %38
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %18) #24
  store i32 58, ptr %18, align 4, !tbaa !39, !alias.scope !41
  %40 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %18, i64 0, i32 1
  store i32 0, ptr %40, align 4, !tbaa !44, !alias.scope !41
  %41 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %18, i64 0, i32 2
  store i32 0, ptr %41, align 4, !tbaa !46, !alias.scope !41
  %42 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %18, i64 0, i32 2, i64 1
  store i32 8, ptr %42, align 4, !tbaa !46, !alias.scope !41
  %43 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3ldrERKNS1_2GpERKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x11E, ptr noundef nonnull align 4 dereferenceable(16) %18) #27
          to label %44 unwind label %54

44:                                               ; preds = %39
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %18) #24
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %19) #24
  store i32 58, ptr %19, align 4, !tbaa !39, !alias.scope !47
  %45 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %19, i64 0, i32 1
  store i32 0, ptr %45, align 4, !tbaa !44, !alias.scope !47
  %46 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %19, i64 0, i32 2
  store i32 0, ptr %46, align 4, !tbaa !46, !alias.scope !47
  %47 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %19, i64 0, i32 2, i64 1
  store i32 16, ptr %47, align 4, !tbaa !46, !alias.scope !47
  %48 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3ldrERKNS1_2GpERKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x12E, ptr noundef nonnull align 4 dereferenceable(16) %19) #27
          to label %49 unwind label %56

49:                                               ; preds = %44
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %19) #24
  br label %62

50:                                               ; preds = %2
  %51 = landingpad { ptr, i32 }
          cleanup
  br label %352

52:                                               ; preds = %342, %341, %336, %334, %321, %319, %75, %73, %60, %58, %36, %34, %33, %32, %26
  %53 = landingpad { ptr, i32 }
          cleanup
  br label %350

54:                                               ; preds = %39
  %55 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %18) #24
  br label %350

56:                                               ; preds = %44
  %57 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %19) #24
  br label %350

58:                                               ; preds = %38
  %59 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x11E, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL2x1E) #27
          to label %60 unwind label %52

60:                                               ; preds = %58
  %61 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x12E, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL2x2E) #27
          to label %62 unwind label %52

62:                                               ; preds = %60, %49
  call void @llvm.lifetime.start.p0(i64 72, ptr nonnull %20) #24
  invoke void @_ZN15RegProtectGuardC2IJN6asmjit5v1_163a643GpXEEvEEPNS3_9AssemblerEDpRKT_(ptr noundef nonnull align 8 dereferenceable(68) %20, ptr noundef nonnull %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL2x0E) #27
          to label %63 unwind label %67

63:                                               ; preds = %62
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %21, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x11E, i64 16, i1 false)
  invoke void @_ZN15default_modules18module_su_redirect9PatchBase30emit_check_user_filename_is_suEPN6asmjit5v1_163a649AssemblerER7KModErrNS4_3GpXE(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef nonnull %28, ptr noundef nonnull align 8 dereferenceable(8) %14, ptr noundef nonnull %21) #27
          to label %64 unwind label %69

64:                                               ; preds = %63
  %65 = load i64, ptr %14, align 8, !tbaa !25
  %66 = icmp eq i64 %65, 0
  br i1 %66, label %71, label %346

67:                                               ; preds = %62
  %68 = landingpad { ptr, i32 }
          cleanup
  br label %311

69:                                               ; preds = %71, %63
  %70 = landingpad { ptr, i32 }
          cleanup
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %20) #26
  br label %311

71:                                               ; preds = %64
  %72 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x10E, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL2x0E) #27
          to label %73 unwind label %69

73:                                               ; preds = %71
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %20) #26
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %20) #24
  %74 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3cbzERKNS1_2GpERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x10E, ptr noundef nonnull align 4 dereferenceable(16) %17) #27
          to label %75 unwind label %52

75:                                               ; preds = %73
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %12) #24
  %76 = load i8, ptr %15, align 8
  %77 = and i8 %76, 1
  %78 = icmp eq i8 %77, 0
  %79 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %15, i64 0, i32 1
  %80 = load i64, ptr %79, align 8
  %81 = lshr i8 %76, 1
  %82 = zext i8 %81 to i64
  %83 = select i1 %78, i64 %82, i64 %80
  %84 = add i64 %83, 1
  call void @llvm.lifetime.start.p0(i64 1, ptr nonnull %13) #24
  store i8 0, ptr %13, align 4, !tbaa !12
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEEC2EmRKh(ptr noundef nonnull align 8 dereferenceable(24) %12, i64 noundef %84, ptr noundef nonnull align 1 dereferenceable(1) %13) #27
          to label %85 unwind label %52

85:                                               ; preds = %75
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %13) #24
  %86 = load i8, ptr %15, align 8
  %87 = and i8 %86, 1
  %88 = icmp eq i8 %87, 0
  %89 = load i64, ptr %79, align 8
  %90 = lshr i8 %86, 1
  %91 = zext i8 %90 to i64
  %92 = select i1 %88, i64 %91, i64 %89
  %93 = icmp eq i64 %92, 0
  br i1 %93, label %106, label %94

94:                                               ; preds = %85
  %95 = load ptr, ptr %12, align 8, !tbaa !50
  %96 = call i64 @llvm.objectsize.i64.p0(ptr %95, i1 false, i1 true, i1 false)
  %97 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %15, i64 0, i32 2
  %98 = load ptr, ptr %97, align 8
  %99 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %15, i64 0, i32 2
  %100 = select i1 %88, ptr %99, ptr %98
  %101 = call ptr @__memcpy_chk(ptr noundef %95, ptr noundef %100, i64 noundef %92, i64 noundef %96) #26
  br label %106

102:                                              ; preds = %106
  %103 = landingpad { ptr, i32 }
          cleanup
  br label %104

104:                                              ; preds = %296, %102
  %105 = phi { ptr, i32 } [ %103, %102 ], [ %297, %296 ]
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %12) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %12) #24
  br label %350

106:                                              ; preds = %94, %85
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %11)
  %107 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %12, i64 0, i32 1
  %108 = load ptr, ptr %107, align 8, !tbaa !54
  %109 = load ptr, ptr %12, align 8, !tbaa !50
  %110 = ptrtoint ptr %108 to i64
  %111 = ptrtoint ptr %109 to i64
  %112 = add i64 %110, 3
  %113 = sub i64 %112, %111
  %114 = and i64 %113, -4
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %8) #24
  call void @llvm.lifetime.start.p0(i64 1, ptr nonnull %9) #24
  store i8 0, ptr %9, align 4, !tbaa !12
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEEC2EmRKh(ptr noundef nonnull align 8 dereferenceable(24) %8, i64 noundef %114, ptr noundef nonnull align 1 dereferenceable(1) %9) #27
          to label %115 unwind label %102

115:                                              ; preds = %106
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %9) #24
  %116 = load ptr, ptr %12, align 8, !tbaa !50
  %117 = load ptr, ptr %107, align 8, !tbaa !54
  %118 = icmp eq ptr %116, %117
  br i1 %118, label %126, label %119

119:                                              ; preds = %115
  %120 = load ptr, ptr %8, align 8, !tbaa !50
  %121 = call i64 @llvm.objectsize.i64.p0(ptr %120, i1 false, i1 true, i1 false)
  %122 = ptrtoint ptr %117 to i64
  %123 = ptrtoint ptr %116 to i64
  %124 = sub i64 %122, %123
  %125 = call ptr @__memcpy_chk(ptr noundef %120, ptr noundef %116, i64 noundef %124, i64 noundef %121) #26
  br label %126

126:                                              ; preds = %119, %115
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %10) #24
  %127 = load ptr, ptr %28, align 8, !tbaa !37
  %128 = getelementptr inbounds ptr, ptr %127, i64 4
  %129 = load ptr, ptr %128, align 8
  invoke void %129(ptr nonnull sret(%"class.asmjit::v1_16::Label") align 4 %10, ptr noundef nonnull align 8 dereferenceable(176) %28) #27
          to label %130 unwind label %290

130:                                              ; preds = %126
  %131 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE1bERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) %10) #27
          to label %132 unwind label %290

132:                                              ; preds = %130
  %133 = getelementptr inbounds %"class.asmjit::v1_16::BaseAssembler", ptr %28, i64 0, i32 4
  %134 = load ptr, ptr %133, align 8, !tbaa !55
  %135 = getelementptr inbounds %"class.asmjit::v1_16::BaseAssembler", ptr %28, i64 0, i32 2
  %136 = load ptr, ptr %135, align 8, !tbaa !74
  %137 = ptrtoint ptr %134 to i64
  %138 = ptrtoint ptr %136 to i64
  %139 = load ptr, ptr %8, align 8, !tbaa !50
  %140 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %8, i64 0, i32 1
  %141 = load ptr, ptr %140, align 8, !tbaa !54
  %142 = ptrtoint ptr %141 to i64
  %143 = ptrtoint ptr %139 to i64
  %144 = sub i64 %142, %143
  %145 = load ptr, ptr %28, align 8, !tbaa !37
  %146 = getelementptr inbounds ptr, ptr %145, i64 10
  %147 = load ptr, ptr %146, align 8
  %148 = invoke noundef i32 %147(ptr noundef nonnull align 8 dereferenceable(176) %28, ptr noundef %139, i64 noundef %144) #27
          to label %149 unwind label %292

149:                                              ; preds = %132
  %150 = load ptr, ptr %28, align 8, !tbaa !37
  %151 = getelementptr inbounds ptr, ptr %150, i64 6
  %152 = load ptr, ptr %151, align 8
  %153 = invoke noundef i32 %152(ptr noundef nonnull align 8 dereferenceable(176) %28, ptr noundef nonnull align 4 dereferenceable(16) %10) #27
          to label %154 unwind label %292

154:                                              ; preds = %149
  %155 = load ptr, ptr %133, align 8, !tbaa !55
  %156 = load ptr, ptr %135, align 8, !tbaa !74
  %157 = ptrtoint ptr %155 to i64
  %158 = ptrtoint ptr %156 to i64
  %159 = add i64 %138, %157
  %160 = sub i64 %137, %159
  %161 = add i64 %160, %158
  %162 = trunc i64 %161 to i32
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %11, ptr noundef nonnull align 8 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x10E, i64 16, i1 false)
  %163 = and i32 %162, 3
  %164 = icmp eq i32 %163, 0
  br i1 %164, label %165, label %168

165:                                              ; preds = %154
  %166 = add i32 %162, -1048576
  %167 = icmp ult i32 %166, -2097152
  br i1 %167, label %168, label %173

168:                                              ; preds = %165, %154
  %169 = phi ptr [ @.str.26, %154 ], [ @.str.27, %165 ]
  %170 = invoke noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk1lsB7v170000INS_11char_traitsIcEEEERNS_13basic_ostreamIcT_EES6_PKc(ptr noundef nonnull align 8 dereferenceable(8) @_ZNSt6__ndk14coutE, ptr noundef nonnull %169) #27
          to label %171 unwind label %294

171:                                              ; preds = %168
  %172 = invoke noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk14endlB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_(ptr noundef nonnull align 8 dereferenceable(8) %170) #27
          to label %298 unwind label %294

173:                                              ; preds = %165
  call void @llvm.lifetime.start.p0(i64 32, ptr nonnull %3) #24
  invoke fastcc void @_ZN12_GLOBAL__N_116init_aarch64_asmEv(ptr nonnull sret(%"struct.(anonymous namespace)::aarch64_asm_ctx") align 8 %3) #27
          to label %174 unwind label %294

174:                                              ; preds = %173
  %175 = getelementptr inbounds %"struct.(anonymous namespace)::aarch64_asm_ctx", ptr %3, i64 0, i32 3
  %176 = load ptr, ptr %175, align 8, !tbaa !16
  %177 = icmp eq ptr %176, null
  br i1 %177, label %289, label %178

178:                                              ; preds = %174
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  %179 = load ptr, ptr %176, align 8, !tbaa !37
  %180 = getelementptr inbounds ptr, ptr %179, i64 4
  %181 = load ptr, ptr %180, align 8
  invoke void %181(ptr nonnull sret(%"class.asmjit::v1_16::Label") align 4 %4, ptr noundef nonnull align 8 dereferenceable(176) %176) #27
          to label %182 unwind label %200

182:                                              ; preds = %178
  %183 = call i32 @llvm.abs.i32(i32 %162, i1 true)
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %5) #24
  %184 = zext i32 %183 to i64
  call void @llvm.lifetime.start.p0(i64 1, ptr nonnull %6) #24
  store i8 0, ptr %6, align 4, !tbaa !12
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEEC2EmRKh(ptr noundef nonnull align 8 dereferenceable(24) %5, i64 noundef %184, ptr noundef nonnull align 1 dereferenceable(1) %6) #27
          to label %185 unwind label %202

185:                                              ; preds = %182
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %6) #24
  %186 = icmp sgt i32 %162, 0
  br i1 %186, label %187, label %213

187:                                              ; preds = %185
  %188 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3adrERKNS1_2GpERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %176, ptr noundef nonnull align 4 dereferenceable(16) %11, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
          to label %189 unwind label %204

189:                                              ; preds = %187
  %190 = load ptr, ptr %5, align 8, !tbaa !50
  %191 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %5, i64 0, i32 1
  %192 = load ptr, ptr %191, align 8, !tbaa !54
  %193 = ptrtoint ptr %192 to i64
  %194 = ptrtoint ptr %190 to i64
  %195 = sub i64 %193, %194
  %196 = load ptr, ptr %176, align 8, !tbaa !37
  %197 = getelementptr inbounds ptr, ptr %196, i64 10
  %198 = load ptr, ptr %197, align 8
  %199 = invoke noundef i32 %198(ptr noundef nonnull align 8 dereferenceable(176) %176, ptr noundef %190, i64 noundef %195) #27
          to label %206 unwind label %204

200:                                              ; preds = %178
  %201 = landingpad { ptr, i32 }
          cleanup
  br label %287

202:                                              ; preds = %182
  %203 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %6) #24
  br label %285

204:                                              ; preds = %231, %220, %213, %211, %206, %189, %187
  %205 = landingpad { ptr, i32 }
          cleanup
  br label %283

206:                                              ; preds = %189
  %207 = load ptr, ptr %176, align 8, !tbaa !37
  %208 = getelementptr inbounds ptr, ptr %207, i64 6
  %209 = load ptr, ptr %208, align 8
  %210 = invoke noundef i32 %209(ptr noundef nonnull align 8 dereferenceable(176) %176, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
          to label %211 unwind label %204

211:                                              ; preds = %206
  %212 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3nopEv(ptr noundef nonnull align 1 dereferenceable(1) %176) #27
          to label %233 unwind label %204

213:                                              ; preds = %185
  %214 = load ptr, ptr %176, align 8, !tbaa !37
  %215 = getelementptr inbounds ptr, ptr %214, i64 6
  %216 = load ptr, ptr %215, align 8
  %217 = invoke noundef i32 %216(ptr noundef nonnull align 8 dereferenceable(176) %176, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
          to label %218 unwind label %204

218:                                              ; preds = %213
  %219 = icmp eq i32 %162, 0
  br i1 %219, label %231, label %220

220:                                              ; preds = %218
  %221 = load ptr, ptr %5, align 8, !tbaa !50
  %222 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %5, i64 0, i32 1
  %223 = load ptr, ptr %222, align 8, !tbaa !54
  %224 = ptrtoint ptr %223 to i64
  %225 = ptrtoint ptr %221 to i64
  %226 = sub i64 %224, %225
  %227 = load ptr, ptr %176, align 8, !tbaa !37
  %228 = getelementptr inbounds ptr, ptr %227, i64 10
  %229 = load ptr, ptr %228, align 8
  %230 = invoke noundef i32 %229(ptr noundef nonnull align 8 dereferenceable(176) %176, ptr noundef %221, i64 noundef %226) #27
          to label %231 unwind label %204

231:                                              ; preds = %220, %218
  %232 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3adrERKNS1_2GpERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %176, ptr noundef nonnull align 4 dereferenceable(16) %11, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
          to label %233 unwind label %204

233:                                              ; preds = %231, %211
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %7) #24
  call void @llvm.experimental.noalias.scope.decl(metadata !75)
  %234 = getelementptr inbounds %"class.asmjit::v1_16::BaseEmitter", ptr %176, i64 0, i32 13
  %235 = load ptr, ptr %234, align 8, !tbaa !78, !noalias !75
  %236 = icmp eq ptr %235, null
  br i1 %236, label %279, label %237

237:                                              ; preds = %233
  %238 = getelementptr inbounds %"class.(anonymous namespace)::MyAsmJitErrorHandler", ptr %235, i64 0, i32 1
  %239 = load i8, ptr %238, align 8, !tbaa !79, !range !82, !noalias !75, !noundef !83
  %240 = icmp eq i8 %239, 0
  br i1 %240, label %241, label %279

241:                                              ; preds = %237
  %242 = getelementptr inbounds %"class.asmjit::v1_16::BaseEmitter", ptr %176, i64 0, i32 11
  %243 = load ptr, ptr %242, align 8, !tbaa !84, !noalias !75
  %244 = icmp eq ptr %243, null
  br i1 %244, label %279, label %245

245:                                              ; preds = %241
  %246 = getelementptr inbounds %"class.asmjit::v1_16::CodeHolder", ptr %243, i64 0, i32 8
  %247 = load ptr, ptr %246, align 8, !tbaa !85, !noalias !75
  %248 = load ptr, ptr %247, align 8, !tbaa !16, !noalias !75
  %249 = icmp eq ptr %248, null
  br i1 %249, label %279, label %250

250:                                              ; preds = %245
  %251 = getelementptr inbounds %"class.asmjit::v1_16::Section", ptr %248, i64 0, i32 7
  %252 = load ptr, ptr %251, align 8, !tbaa !87, !noalias !75
  %253 = getelementptr inbounds %"class.asmjit::v1_16::Section", ptr %248, i64 0, i32 7, i32 1
  %254 = load i64, ptr %253, align 8, !tbaa !90, !noalias !75
  %255 = getelementptr inbounds i8, ptr %252, i64 %254
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEEC2IPKhLi0EEET_S7_(ptr noundef nonnull align 8 dereferenceable(24) %7, ptr noundef %252, ptr noundef %255) #27
          to label %256 unwind label %261

256:                                              ; preds = %250
  %257 = load ptr, ptr %7, align 8, !tbaa !50
  %258 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %7, i64 0, i32 1
  %259 = load ptr, ptr %258, align 8, !tbaa !54
  %260 = icmp eq ptr %257, %259
  br i1 %260, label %280, label %263

261:                                              ; preds = %250
  %262 = landingpad { ptr, i32 }
          cleanup
  br label %281

263:                                              ; preds = %256
  %264 = icmp slt i32 %162, 0
  %265 = select i1 %264, i64 %184, i64 0
  %266 = ptrtoint ptr %259 to i64
  %267 = ptrtoint ptr %257 to i64
  %268 = sub i64 %266, %267
  %269 = add nuw nsw i64 %265, 4
  %270 = icmp ult i64 %268, %269
  br i1 %270, label %280, label %271

271:                                              ; preds = %263
  %272 = getelementptr inbounds i8, ptr %257, i64 %265
  %273 = load ptr, ptr %28, align 8, !tbaa !37
  %274 = getelementptr inbounds ptr, ptr %273, i64 10
  %275 = load ptr, ptr %274, align 8
  %276 = invoke noundef i32 %275(ptr noundef nonnull align 8 dereferenceable(176) %28, ptr noundef %272, i64 noundef 4) #27
          to label %280 unwind label %277

277:                                              ; preds = %271
  %278 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  br label %281

279:                                              ; preds = %245, %241, %237, %233
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %7, i8 0, i64 24, i1 false), !alias.scope !75
  br label %280

280:                                              ; preds = %279, %271, %263, %256
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #24
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  br label %289

281:                                              ; preds = %277, %261
  %282 = phi { ptr, i32 } [ %278, %277 ], [ %262, %261 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #24
  br label %283

283:                                              ; preds = %281, %204
  %284 = phi { ptr, i32 } [ %282, %281 ], [ %205, %204 ]
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #26
  br label %285

285:                                              ; preds = %283, %202
  %286 = phi { ptr, i32 } [ %284, %283 ], [ %203, %202 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #24
  br label %287

287:                                              ; preds = %285, %200
  %288 = phi { ptr, i32 } [ %286, %285 ], [ %201, %200 ]
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  call fastcc void @_ZN12_GLOBAL__N_115aarch64_asm_ctxD2Ev(ptr noundef nonnull align 8 dereferenceable(32) %3) #26
  call void @llvm.lifetime.end.p0(i64 32, ptr nonnull %3) #24
  br label %296

289:                                              ; preds = %280, %174
  call fastcc void @_ZN12_GLOBAL__N_115aarch64_asm_ctxD2Ev(ptr noundef nonnull align 8 dereferenceable(32) %3) #26
  call void @llvm.lifetime.end.p0(i64 32, ptr nonnull %3) #24
  br label %298

290:                                              ; preds = %130, %126
  %291 = landingpad { ptr, i32 }
          cleanup
  br label %296

292:                                              ; preds = %149, %132
  %293 = landingpad { ptr, i32 }
          cleanup
  br label %296

294:                                              ; preds = %173, %171, %168
  %295 = landingpad { ptr, i32 }
          cleanup
  br label %296

296:                                              ; preds = %294, %292, %290, %287
  %297 = phi { ptr, i32 } [ %291, %290 ], [ %293, %292 ], [ %295, %294 ], [ %288, %287 ]
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %10) #24
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %8) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %8) #24
  br label %104

298:                                              ; preds = %289, %171
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %10) #24
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %8) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %8) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %11)
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %12) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %12) #24
  call void @llvm.lifetime.start.p0(i64 72, ptr nonnull %22) #24
  invoke void @_ZN15RegProtectGuardC2IJN6asmjit5v1_163a643GpXEEvEEPNS3_9AssemblerEDpRKT_(ptr noundef nonnull align 8 dereferenceable(68) %22, ptr noundef nonnull %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL2x0E) #27
          to label %299 unwind label %313

299:                                              ; preds = %298
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %23, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x12E, i64 16, i1 false)
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %24, ptr noundef nonnull align 8 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x10E, i64 16, i1 false)
  %300 = load i8, ptr %15, align 8
  %301 = and i8 %300, 1
  %302 = icmp eq i8 %301, 0
  %303 = load i64, ptr %79, align 8
  %304 = lshr i8 %300, 1
  %305 = zext i8 %304 to i64
  %306 = select i1 %302, i64 %305, i64 %303
  %307 = add i64 %306, 1
  invoke void @maplesu_emit_uid_stat_copy_84(ptr noundef nonnull %28, ptr noundef nonnull align 8 dereferenceable(8) %14, ptr noundef nonnull %23, ptr noundef nonnull %24, i64 noundef %307, ptr noundef %0) #27
          to label %308 unwind label %315

308:                                              ; preds = %299
  %309 = load i64, ptr %14, align 8, !tbaa !25
  %310 = icmp eq i64 %309, 0
  br i1 %310, label %317, label %347

311:                                              ; preds = %69, %67
  %312 = phi { ptr, i32 } [ %70, %69 ], [ %68, %67 ]
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %20) #24
  br label %350

313:                                              ; preds = %298
  %314 = landingpad { ptr, i32 }
          cleanup
  br label %330

315:                                              ; preds = %317, %299
  %316 = landingpad { ptr, i32 }
          cleanup
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %22) #26
  br label %330

317:                                              ; preds = %308
  %318 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x10E, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL2x0E) #27
          to label %319 unwind label %315

319:                                              ; preds = %317
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %22) #26
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %22) #24
  %320 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4cbnzERKNS1_2GpERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x10E, ptr noundef nonnull align 4 dereferenceable(16) %17) #27
          to label %321 unwind label %52

321:                                              ; preds = %319
  %322 = invoke noundef i1 @_ZN13kernel_module25is_syscall_args_in_ptregsEv() #27
          to label %323 unwind label %52

323:                                              ; preds = %321
  br i1 %322, label %324, label %334

324:                                              ; preds = %323
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %25) #24
  store i32 58, ptr %25, align 4, !tbaa !39, !alias.scope !91
  %325 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %25, i64 0, i32 1
  store i32 0, ptr %325, align 4, !tbaa !44, !alias.scope !91
  %326 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %25, i64 0, i32 2
  store i32 0, ptr %326, align 4, !tbaa !46, !alias.scope !91
  %327 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %25, i64 0, i32 2, i64 1
  store i32 8, ptr %327, align 4, !tbaa !46, !alias.scope !91
  %328 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3strERKNS1_2GpERKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x12E, ptr noundef nonnull align 4 dereferenceable(16) %25) #27
          to label %329 unwind label %332

329:                                              ; preds = %324
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %25) #24
  br label %336

330:                                              ; preds = %315, %313
  %331 = phi { ptr, i32 } [ %316, %315 ], [ %314, %313 ]
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %22) #24
  br label %350

332:                                              ; preds = %324
  %333 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %25) #24
  br label %350

334:                                              ; preds = %323
  %335 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %28, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL2x1E, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x12E) #27
          to label %336 unwind label %52

336:                                              ; preds = %334, %329
  %337 = load ptr, ptr %28, align 8, !tbaa !37
  %338 = getelementptr inbounds ptr, ptr %337, i64 6
  %339 = load ptr, ptr %338, align 8
  %340 = invoke noundef i32 %339(ptr noundef nonnull align 8 dereferenceable(176) %28, ptr noundef nonnull align 4 dereferenceable(16) %17) #27
          to label %341 unwind label %52

341:                                              ; preds = %336
  invoke void @_ZN13kernel_module21arm64_before_hook_endEPN6asmjit5v1_163a649AssemblerEb(ptr noundef nonnull %28, i1 noundef true) #27
          to label %342 unwind label %52

342:                                              ; preds = %341
  %343 = getelementptr inbounds %"class.default_modules::module_su_redirect::PatchSysNewfstatat", ptr %0, i64 0, i32 1
  %344 = load i64, ptr %343, align 8, !tbaa !27
  %345 = invoke noundef i64 @_ZN15default_modules18module_su_redirect9PatchBase24patch_kernel_before_hookEmPKN6asmjit5v1_163a649AssemblerE(ptr noundef nonnull align 8 dereferenceable(40) %0, i64 noundef %344, ptr noundef nonnull %28) #27
          to label %348 unwind label %52

346:                                              ; preds = %64
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %20) #26
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %20) #24
  br label %348

347:                                              ; preds = %308
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %22) #26
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %22) #24
  br label %348

348:                                              ; preds = %347, %346, %342
  %349 = phi i64 [ %65, %346 ], [ %309, %347 ], [ %345, %342 ]
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %17) #24
  call fastcc void @_ZN12_GLOBAL__N_115aarch64_asm_ctxD2Ev(ptr noundef nonnull align 8 dereferenceable(32) %16) #26
  call void @llvm.lifetime.end.p0(i64 32, ptr nonnull %16) #24
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %15) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %15) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %14) #24
  ret i64 %349

350:                                              ; preds = %332, %330, %311, %104, %56, %54, %52
  %351 = phi { ptr, i32 } [ %333, %332 ], [ %331, %330 ], [ %312, %311 ], [ %57, %56 ], [ %55, %54 ], [ %53, %52 ], [ %105, %104 ]
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %17) #24
  call fastcc void @_ZN12_GLOBAL__N_115aarch64_asm_ctxD2Ev(ptr noundef nonnull align 8 dereferenceable(32) %16) #26
  br label %352

352:                                              ; preds = %350, %50
  %353 = phi { ptr, i32 } [ %351, %350 ], [ %51, %50 ]
  call void @llvm.lifetime.end.p0(i64 32, ptr nonnull %16) #24
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %15) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %15) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %14) #24
  resume { ptr, i32 } %353
}

; Function Attrs: minsize optsize
declare void @_ZN15default_modules18module_su_redirect9PatchBase16get_fake_su_pathEv(ptr sret(%"class.std::__ndk1::basic_string") align 8, ptr noundef nonnull align 8 dereferenceable(40)) local_unnamed_addr #8

; Function Attrs: minsize optsize uwtable
define internal fastcc void @_ZN12_GLOBAL__N_116init_aarch64_asmEv(ptr noalias sret(%"struct.(anonymous namespace)::aarch64_asm_ctx") align 8 %0) unnamed_addr #1 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.asmjit::v1_16::Environment", align 4
  %3 = alloca %"class.std::__ndk1::unique_ptr.13", align 8
  %4 = alloca %"class.std::__ndk1::unique_ptr", align 8
  %5 = alloca %"class.std::__ndk1::unique_ptr.29", align 8
  %6 = alloca ptr, align 8
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(32) %0, i8 0, i64 32, i1 false)
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #24
  store i8 6, ptr %2, align 4, !tbaa !94
  %7 = getelementptr inbounds %"class.asmjit::v1_16::Environment", ptr %2, i64 0, i32 1
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 1 dereferenceable(7) %7, i8 0, i64 7, i1 false)
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %3) #24
  invoke void @_ZNSt6__ndk111make_uniqueB7v170000IN6asmjit5v1_1610CodeHolderEJEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_(ptr nonnull sret(%"class.std::__ndk1::unique_ptr.13") align 8 %3) #27
          to label %8 unwind label %60

8:                                                ; preds = %1
  %9 = getelementptr inbounds %"struct.(anonymous namespace)::aarch64_asm_ctx", ptr %0, i64 0, i32 1
  %10 = load ptr, ptr %3, align 8, !tbaa !16
  store ptr null, ptr %3, align 8, !tbaa !16
  call void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1610CodeHolderENS_14default_deleteIS3_EEE5resetB7v170000EPS3_(ptr noundef nonnull align 8 dereferenceable(8) %9, ptr noundef %10) #26
  call void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1610CodeHolderENS_14default_deleteIS3_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %3) #26
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #24
  %11 = load ptr, ptr %9, align 8, !tbaa !16
  %12 = call noundef i32 @_ZN6asmjit5v1_1610CodeHolder4initERKNS0_11EnvironmentEm(ptr noundef nonnull align 8 dereferenceable(344) %11, ptr noundef nonnull align 1 dereferenceable(8) %2, i64 noundef -1) #26
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %4) #24
  invoke void @_ZNSt6__ndk111make_uniqueB7v170000IN6asmjit5v1_1612StringLoggerEJEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_(ptr nonnull sret(%"class.std::__ndk1::unique_ptr") align 8 %4) #27
          to label %13 unwind label %62

13:                                               ; preds = %8
  %14 = load ptr, ptr %4, align 8, !tbaa !16
  store ptr null, ptr %4, align 8, !tbaa !16
  %15 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr %14, ptr %0, align 8, !tbaa !16
  %16 = icmp eq ptr %15, null
  br i1 %16, label %27, label %17

17:                                               ; preds = %13
  %18 = load ptr, ptr %15, align 8, !tbaa !37
  %19 = getelementptr inbounds ptr, ptr %18, i64 1
  %20 = load ptr, ptr %19, align 8
  call void %20(ptr noundef nonnull align 8 dereferenceable(56) %15) #26
  %21 = load ptr, ptr %4, align 8, !tbaa !16
  store ptr null, ptr %4, align 8, !tbaa !16
  %22 = icmp eq ptr %21, null
  br i1 %22, label %27, label %23

23:                                               ; preds = %17
  %24 = load ptr, ptr %21, align 8, !tbaa !37
  %25 = getelementptr inbounds ptr, ptr %24, i64 1
  %26 = load ptr, ptr %25, align 8
  call void %26(ptr noundef nonnull align 8 dereferenceable(56) %21) #26
  br label %27

27:                                               ; preds = %23, %17, %13
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %4) #24
  %28 = load ptr, ptr %0, align 8, !tbaa !16
  %29 = load ptr, ptr %9, align 8, !tbaa !16
  call void @_ZN6asmjit5v1_1610CodeHolder9setLoggerEPNS0_6LoggerE(ptr noundef nonnull align 8 dereferenceable(344) %29, ptr noundef %28) #26
  %30 = invoke noalias noundef nonnull dereferenceable(16) ptr @_Znwm(i64 noundef 16) #30
          to label %31 unwind label %64

31:                                               ; preds = %27
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 16 dereferenceable(16) %30, i8 0, i64 16, i1 false), !noalias !95
  call void @_ZN6asmjit5v1_1612ErrorHandlerC2Ev(ptr noundef nonnull align 8 dereferenceable(8) %30) #26, !noalias !95
  store ptr getelementptr inbounds ({ [5 x ptr] }, ptr @_ZTVN12_GLOBAL__N_120MyAsmJitErrorHandlerE, i64 0, inrange i32 0, i64 2), ptr %30, align 8, !tbaa !37, !noalias !95
  %32 = getelementptr inbounds %"class.(anonymous namespace)::MyAsmJitErrorHandler", ptr %30, i64 0, i32 1
  store i8 0, ptr %32, align 8, !tbaa !79, !noalias !95
  %33 = getelementptr inbounds %"struct.(anonymous namespace)::aarch64_asm_ctx", ptr %0, i64 0, i32 2
  %34 = load ptr, ptr %33, align 8, !tbaa !16
  store ptr %30, ptr %33, align 8, !tbaa !16
  %35 = icmp eq ptr %34, null
  br i1 %35, label %40, label %36

36:                                               ; preds = %31
  %37 = load ptr, ptr %34, align 8, !tbaa !37
  %38 = getelementptr inbounds ptr, ptr %37, i64 1
  %39 = load ptr, ptr %38, align 8
  call void %39(ptr noundef nonnull align 8 dereferenceable(9) %34) #26
  br label %40

40:                                               ; preds = %36, %31
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %5) #24
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %6) #24
  %41 = load ptr, ptr %9, align 8, !tbaa !16
  store ptr %41, ptr %6, align 8, !tbaa !16
  invoke void @_ZNSt6__ndk111make_uniqueB7v170000IN6asmjit5v1_163a649AssemblerEJPNS2_10CodeHolderEEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_(ptr nonnull sret(%"class.std::__ndk1::unique_ptr.29") align 8 %5, ptr noundef nonnull align 8 dereferenceable(8) %6) #27
          to label %42 unwind label %66

42:                                               ; preds = %40
  %43 = getelementptr inbounds %"struct.(anonymous namespace)::aarch64_asm_ctx", ptr %0, i64 0, i32 3
  %44 = load ptr, ptr %5, align 8, !tbaa !16
  store ptr null, ptr %5, align 8, !tbaa !16
  %45 = load ptr, ptr %43, align 8, !tbaa !16
  store ptr %44, ptr %43, align 8, !tbaa !16
  %46 = icmp eq ptr %45, null
  br i1 %46, label %57, label %47

47:                                               ; preds = %42
  %48 = load ptr, ptr %45, align 8, !tbaa !37
  %49 = getelementptr inbounds ptr, ptr %48, i64 1
  %50 = load ptr, ptr %49, align 8
  call void %50(ptr noundef nonnull align 8 dereferenceable(176) %45) #26
  %51 = load ptr, ptr %5, align 8, !tbaa !16
  store ptr null, ptr %5, align 8, !tbaa !16
  %52 = icmp eq ptr %51, null
  br i1 %52, label %57, label %53

53:                                               ; preds = %47
  %54 = load ptr, ptr %51, align 8, !tbaa !37
  %55 = getelementptr inbounds ptr, ptr %54, i64 1
  %56 = load ptr, ptr %55, align 8
  call void %56(ptr noundef nonnull align 8 dereferenceable(176) %51) #26
  br label %57

57:                                               ; preds = %53, %47, %42
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %5) #24
  %58 = load ptr, ptr %43, align 8, !tbaa !16
  %59 = load ptr, ptr %33, align 8, !tbaa !16
  call void @_ZN6asmjit5v1_1611BaseEmitter15setErrorHandlerEPNS0_12ErrorHandlerE(ptr noundef nonnull align 8 dereferenceable(144) %58, ptr noundef %59) #26
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #24
  ret void

60:                                               ; preds = %1
  %61 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #24
  br label %68

62:                                               ; preds = %8
  %63 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %4) #24
  br label %68

64:                                               ; preds = %27
  %65 = landingpad { ptr, i32 }
          cleanup
  br label %68

66:                                               ; preds = %40
  %67 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %5) #24
  br label %68

68:                                               ; preds = %66, %64, %62, %60
  %69 = phi { ptr, i32 } [ %67, %66 ], [ %65, %64 ], [ %63, %62 ], [ %61, %60 ]
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #24
  call fastcc void @_ZN12_GLOBAL__N_115aarch64_asm_ctxD2Ev(ptr noundef nonnull align 8 dereferenceable(32) %0) #26
  resume { ptr, i32 } %69
}

; Function Attrs: minsize optsize
declare void @_ZN13kernel_module23arm64_before_hook_startEPN6asmjit5v1_163a649AssemblerE(ptr noundef) local_unnamed_addr #8

; Function Attrs: minsize optsize
declare void @_ZN15default_modules18module_su_redirect9PatchBase34emit_check_current_allow_su_to_x10EPN6asmjit5v1_163a649AssemblerEm(ptr noundef nonnull align 8 dereferenceable(40), ptr noundef, i64 noundef) local_unnamed_addr #8

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3cbzERKNS1_2GpERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) local_unnamed_addr #10 comdat align 2 !type !98 {
  %4 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 56, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) #27
  ret i32 %4
}

; Function Attrs: minsize optsize
declare noundef i1 @_ZN13kernel_module25is_syscall_args_in_ptregsEv() local_unnamed_addr #8

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3ldrERKNS1_2GpERKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) local_unnamed_addr #10 comdat align 2 !type !99 {
  %4 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 154, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) #27
  ret i32 %4
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) local_unnamed_addr #10 comdat align 2 !type !100 {
  %4 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 244, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) #27
  ret i32 %4
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN15RegProtectGuardC2IJN6asmjit5v1_163a643GpXEEvEEPNS3_9AssemblerEDpRKT_(ptr noundef nonnull align 8 dereferenceable(68) %0, ptr noundef %1, ptr noundef nonnull align 4 dereferenceable(16) %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  store ptr %1, ptr %0, align 8, !tbaa !101
  %4 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1
  %5 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 2
  %6 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(25) %4, i8 0, i64 25, i1 false)
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %6, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3xzrE, i64 16, i1 false)
  %7 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 5
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %7, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL2spE, i64 16, i1 false)
  invoke void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE7reserveEm(ptr noundef nonnull align 8 dereferenceable(24) %4, i64 noundef 2) #27
          to label %8 unwind label %14

8:                                                ; preds = %3
  invoke void @_ZN15RegProtectGuard15appendOneUniqueERKN6asmjit5v1_163a643GpXE(ptr noundef nonnull align 8 dereferenceable(68) %0, ptr noundef nonnull align 4 dereferenceable(16) %2) #27
          to label %9 unwind label %14

9:                                                ; preds = %8
  %10 = load ptr, ptr %4, align 8, !tbaa !111
  %11 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1, i32 1
  %12 = load ptr, ptr %11, align 8, !tbaa !112
  %13 = icmp eq ptr %10, %12
  br i1 %13, label %25, label %16

14:                                               ; preds = %23, %22, %8, %3
  %15 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #26
  resume { ptr, i32 } %15

16:                                               ; preds = %9
  %17 = ptrtoint ptr %12 to i64
  %18 = ptrtoint ptr %10 to i64
  %19 = sub i64 %17, %18
  %20 = and i64 %19, 16
  %21 = icmp eq i64 %20, 0
  br i1 %21, label %23, label %22

22:                                               ; preds = %16
  invoke void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE9push_backB7v170000ERKS4_(ptr noundef nonnull align 8 dereferenceable(24) %4, ptr noundef nonnull align 4 dereferenceable(16) %6) #27
          to label %23 unwind label %14

23:                                               ; preds = %22, %16
  invoke void @_ZN15RegProtectGuard11doPushPairsEv(ptr noundef nonnull align 8 dereferenceable(68) %0) #27
          to label %24 unwind label %14

24:                                               ; preds = %23
  store i8 1, ptr %5, align 8, !tbaa !113
  br label %25

25:                                               ; preds = %24, %9
  ret void
}

; Function Attrs: minsize optsize
declare void @_ZN15default_modules18module_su_redirect9PatchBase30emit_check_user_filename_is_suEPN6asmjit5v1_163a649AssemblerER7KModErrNS4_3GpXE(ptr noundef nonnull align 8 dereferenceable(40), ptr noundef, ptr noundef nonnull align 8 dereferenceable(8), ptr noundef) local_unnamed_addr #8

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  invoke void @_ZN15RegProtectGuard7cleanupEv(ptr noundef nonnull align 8 dereferenceable(68) %0) #27
          to label %2 unwind label %4

2:                                                ; preds = %1
  %3 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1
  tail call void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %3) #26
  ret void

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          catch ptr null
  %6 = extractvalue { ptr, i32 } %5, 0
  tail call void @__clang_call_terminate(ptr %6) #29
  unreachable
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZN13kernel_module13export_symbol12copy_to_userEPN6asmjit5v1_163a649AssemblerER7KModErrNS3_3GpXES8_m(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(8) %1, ptr noundef %2, ptr noundef %3, i64 noundef %4) local_unnamed_addr #10 comdat {
  %6 = alloca i64, align 8
  store i64 %4, ptr %6, align 8, !tbaa !114
  call void @_ZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(8) %1, ptr noundef nonnull align 8 dereferenceable(24) @_ZN13kernel_module13export_symbolL25copy_to_user_symbol_namesE, i1 noundef true, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3, ptr noundef nonnull align 8 dereferenceable(8) %6) #27
  ret void
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4cbnzERKNS1_2GpERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) local_unnamed_addr #10 comdat align 2 !type !98 {
  %4 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 55, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) #27
  ret i32 %4
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3strERKNS1_2GpERKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) local_unnamed_addr #10 comdat align 2 !type !99 {
  %4 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 326, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) #27
  ret i32 %4
}

; Function Attrs: minsize optsize
declare void @_ZN13kernel_module21arm64_before_hook_endEPN6asmjit5v1_163a649AssemblerEb(ptr noundef, i1 noundef) local_unnamed_addr #8

; Function Attrs: minsize optsize
declare noundef i64 @_ZN15default_modules18module_su_redirect9PatchBase24patch_kernel_before_hookEmPKN6asmjit5v1_163a649AssemblerE(ptr noundef nonnull align 8 dereferenceable(40), i64 noundef, ptr noundef) local_unnamed_addr #8

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define internal fastcc void @_ZN12_GLOBAL__N_115aarch64_asm_ctxD2Ev(ptr noundef nonnull align 8 dereferenceable(32) %0) unnamed_addr #2 align 2 {
  %2 = getelementptr inbounds %"struct.(anonymous namespace)::aarch64_asm_ctx", ptr %0, i64 0, i32 3
  tail call void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_163a649AssemblerENS_14default_deleteIS4_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %2) #26
  %3 = getelementptr inbounds %"struct.(anonymous namespace)::aarch64_asm_ctx", ptr %0, i64 0, i32 2
  tail call fastcc void @_ZNSt6__ndk110unique_ptrIN12_GLOBAL__N_120MyAsmJitErrorHandlerENS_14default_deleteIS2_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %3) #26
  %4 = getelementptr inbounds %"struct.(anonymous namespace)::aarch64_asm_ctx", ptr %0, i64 0, i32 1
  tail call void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1610CodeHolderENS_14default_deleteIS3_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %4) #26
  tail call void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1612StringLoggerENS_14default_deleteIS3_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) #26
  ret void
}

; Function Attrs: nocallback nofree nosync nounwind willreturn memory(inaccessiblemem: readwrite)
declare void @llvm.assume(i1 noundef) #11

; Function Attrs: inlinehint minsize optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #3 align 2 personality ptr @__gxx_personality_v0 {
  %4 = icmp ugt i64 %2, -17
  br i1 %4, label %5, label %6

5:                                                ; preds = %3
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

6:                                                ; preds = %3
  %7 = icmp ult i64 %2, 23
  br i1 %7, label %8, label %12

8:                                                ; preds = %6
  %9 = trunc i64 %2 to i8
  %10 = shl nuw nsw i8 %9, 1
  store i8 %10, ptr %0, align 8
  %11 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  br label %22

12:                                               ; preds = %6
  %13 = or i64 %2, 15
  %14 = add i64 %13, 1
  %15 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %14) #27
  %16 = extractvalue [2 x i64] %15, 0
  %17 = inttoptr i64 %16 to ptr
  %18 = extractvalue [2 x i64] %15, 1
  %19 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  store ptr %17, ptr %19, align 8, !tbaa !12
  %20 = or i64 %18, 1
  store i64 %20, ptr %0, align 8
  %21 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %2, ptr %21, align 8, !tbaa !12
  br label %22

22:                                               ; preds = %12, %8
  %23 = phi ptr [ %11, %8 ], [ %17, %12 ]
  %24 = icmp ugt ptr %23, %1
  %25 = getelementptr inbounds i8, ptr %23, i64 %2
  %26 = icmp ule ptr %25, %1
  %27 = or i1 %24, %26
  tail call void @llvm.assume(i1 %27)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %23, ptr align 1 %1, i64 %2, i1 false)
  store i8 0, ptr %25, align 1, !tbaa !12
  ret void
}

; Function Attrs: minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #12 comdat align 2 {
  tail call void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef nonnull @.str.16) #31
  unreachable
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat {
  %3 = tail call noalias noundef nonnull ptr @_Znwm(i64 noundef %1) #30
  %4 = ptrtoint ptr %3 to i64
  %5 = insertvalue [2 x i64] poison, i64 %4, 0
  %6 = insertvalue [2 x i64] %5, i64 %1, 1
  ret [2 x i64] %6
}

; Function Attrs: inlinehint minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef %0) local_unnamed_addr #13 comdat personality ptr @__gxx_personality_v0 {
  %2 = tail call ptr @__cxa_allocate_exception(i64 16) #24
  invoke void @_ZNSt12length_errorC2B7v170000EPKc(ptr noundef nonnull align 8 dereferenceable(16) %2, ptr noundef %0) #27
          to label %3 unwind label %4

3:                                                ; preds = %1
  tail call void @__cxa_throw(ptr nonnull %2, ptr nonnull @_ZTISt12length_error, ptr nonnull @_ZNSt12length_errorD1Ev) #32
  unreachable

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          cleanup
  tail call void @__cxa_free_exception(ptr %2) #24
  resume { ptr, i32 } %5
}

declare ptr @__cxa_allocate_exception(i64) local_unnamed_addr

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt12length_errorC2B7v170000EPKc(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %1) unnamed_addr #1 comdat align 2 {
  tail call void @_ZNSt11logic_errorC2EPKc(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %1) #27
  store ptr getelementptr inbounds ({ [5 x ptr] }, ptr @_ZTVSt12length_error, i64 0, inrange i32 0, i64 2), ptr %0, align 8, !tbaa !37
  ret void
}

declare void @__cxa_free_exception(ptr) local_unnamed_addr

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt12length_errorD1Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #9

declare void @__cxa_throw(ptr, ptr, ptr) local_unnamed_addr

; Function Attrs: minsize optsize
declare void @_ZNSt11logic_errorC2EPKc(ptr noundef nonnull align 8 dereferenceable(16), ptr noundef) unnamed_addr #8

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt12length_errorD0Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #9

; Function Attrs: minsize nounwind optsize
declare noundef ptr @_ZNKSt11logic_error4whatEv(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #9

; Function Attrs: inlinehint minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZSt28__throw_bad_array_new_lengthB7v170000v() local_unnamed_addr #13 comdat {
  %1 = tail call ptr @__cxa_allocate_exception(i64 8) #24
  tail call void @_ZNSt20bad_array_new_lengthC1Ev(ptr noundef nonnull align 8 dereferenceable(8) %1) #26
  tail call void @__cxa_throw(ptr nonnull %1, ptr nonnull @_ZTISt20bad_array_new_length, ptr nonnull @_ZNSt20bad_array_new_lengthD1Ev) #32
  unreachable
}

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt20bad_array_new_lengthC1Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #9

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt20bad_array_new_lengthD1Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #9

; Function Attrs: minsize nobuiltin optsize allocsize(0)
declare noundef nonnull ptr @_Znwm(i64 noundef) local_unnamed_addr #14

; Function Attrs: minsize noinline noreturn nounwind optsize
define linkonce_odr hidden void @__clang_call_terminate(ptr noundef %0) local_unnamed_addr #15 comdat {
  %2 = tail call ptr @__cxa_begin_catch(ptr %0) #24
  tail call void @_ZSt9terminatev() #29
  unreachable
}

declare ptr @__cxa_begin_catch(ptr) local_unnamed_addr

declare void @_ZSt9terminatev() local_unnamed_addr

; Function Attrs: nocallback nofree nounwind willreturn memory(argmem: readwrite)
declare void @llvm.memmove.p0.p0.i64(ptr nocapture writeonly, ptr nocapture readonly, i64, i1 immarg) #16

; Function Attrs: minsize mustprogress nofree nounwind optsize willreturn memory(argmem: read)
declare i64 @strlen(ptr nocapture noundef) local_unnamed_addr #17

; Function Attrs: minsize nobuiltin nounwind optsize
declare void @_ZdlPv(ptr noundef) local_unnamed_addr #18

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE11__vallocateB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = icmp ugt i64 %1, 768614336404564650
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZNKSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

5:                                                ; preds = %2
  %6 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 2
  %7 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERSA_m(ptr noundef nonnull align 1 dereferenceable(1) %6, i64 noundef %1) #27
  %8 = extractvalue [2 x i64] %7, 0
  %9 = inttoptr i64 %8 to ptr
  %10 = extractvalue [2 x i64] %7, 1
  store ptr %9, ptr %0, align 8, !tbaa !115
  %11 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 1
  store ptr %9, ptr %11, align 8, !tbaa !119
  %12 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %9, i64 %10
  store ptr %12, ptr %6, align 8, !tbaa !16
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE18__construct_at_endIPKS6_Li0EEEvT_SC_m(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, ptr noundef %2, i64 noundef %3) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %5 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 1
  %6 = load ptr, ptr %5, align 8, !tbaa !119
  %7 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 2
  %8 = invoke noundef ptr @_ZNSt6__ndk130__uninitialized_allocator_copyB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPKS6_S9_PS6_EET2_RT_T0_T1_SB_(ptr noundef nonnull align 1 dereferenceable(1) %7, ptr noundef %1, ptr noundef %2, ptr noundef %6) #27
          to label %9 unwind label %10

9:                                                ; preds = %4
  store ptr %8, ptr %5, align 8, !tbaa !119
  ret void

10:                                               ; preds = %4
  %11 = landingpad { ptr, i32 }
          cleanup
  store ptr %6, ptr %5, align 8, !tbaa !119
  resume { ptr, i32 } %11
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk117__exception_guardINS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard", ptr %0, i64 0, i32 1
  %3 = load i8, ptr %2, align 8, !tbaa !21, !range !82, !noundef !83
  %4 = icmp eq i8 %3, 0
  br i1 %4, label %5, label %6

5:                                                ; preds = %1
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) #27
          to label %6 unwind label %7

6:                                                ; preds = %5, %1
  ret void

7:                                                ; preds = %5
  %8 = landingpad { ptr, i32 }
          catch ptr null
  %9 = extractvalue { ptr, i32 } %8, 0
  tail call void @__clang_call_terminate(ptr %9) #29
  unreachable
}

; Function Attrs: nocallback nofree nounwind willreturn memory(argmem: readwrite)
declare void @llvm.memcpy.p0.p0.i64(ptr noalias nocapture writeonly, ptr noalias nocapture readonly, i64, i1 immarg) #16

; Function Attrs: minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #12 comdat align 2 {
  tail call void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef nonnull @.str.17) #31
  unreachable
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERSA_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat {
  %3 = tail call noundef ptr @_ZNSt6__ndk19allocatorINS_12basic_stringIcNS_11char_traitsIcEENS0_IcEEEEE8allocateB7v170000Em(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) #27
  %4 = ptrtoint ptr %3 to i64
  %5 = insertvalue [2 x i64] poison, i64 %4, 0
  %6 = insertvalue [2 x i64] %5, i64 %1, 1
  ret [2 x i64] %6
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk19allocatorINS_12basic_stringIcNS_11char_traitsIcEENS0_IcEEEEE8allocateB7v170000Em(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 {
  %3 = icmp ugt i64 %1, 768614336404564650
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZSt28__throw_bad_array_new_lengthB7v170000v() #31
  unreachable

5:                                                ; preds = %2
  %6 = mul nuw i64 %1, 24
  %7 = tail call noalias noundef nonnull ptr @_Znwm(i64 noundef %6) #30
  ret ptr %7
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk130__uninitialized_allocator_copyB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPKS6_S9_PS6_EET2_RT_T0_T1_SB_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef %1, ptr noundef %2, ptr noundef %3) local_unnamed_addr #1 comdat personality ptr @__gxx_personality_v0 {
  %5 = alloca ptr, align 8
  %6 = alloca ptr, align 8
  %7 = alloca %"struct.std::__ndk1::__exception_guard.46", align 8
  store ptr %3, ptr %5, align 8, !tbaa !16
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %6) #24
  store ptr %3, ptr %6, align 8, !tbaa !16
  call void @llvm.lifetime.start.p0(i64 32, ptr nonnull %7) #24
  store ptr %0, ptr %7, align 8
  %8 = getelementptr inbounds i8, ptr %7, i64 8
  store ptr %6, ptr %8, align 8
  %9 = getelementptr inbounds i8, ptr %7, i64 16
  store ptr %5, ptr %9, align 8
  %10 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard.46", ptr %7, i64 0, i32 1
  store i8 0, ptr %10, align 8, !tbaa !120, !alias.scope !123
  br label %11

11:                                               ; preds = %16, %4
  %12 = phi ptr [ %3, %4 ], [ %19, %16 ]
  %13 = phi ptr [ %1, %4 ], [ %17, %16 ]
  %14 = icmp eq ptr %13, %2
  br i1 %14, label %22, label %15

15:                                               ; preds = %11
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %12, ptr noundef nonnull align 8 dereferenceable(24) %13) #27
          to label %16 unwind label %20

16:                                               ; preds = %15
  %17 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %13, i64 1
  %18 = load ptr, ptr %5, align 8, !tbaa !16
  %19 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %18, i64 1
  store ptr %19, ptr %5, align 8, !tbaa !16
  br label %11, !llvm.loop !126

20:                                               ; preds = %15
  %21 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk117__exception_guardINS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(25) %7) #26
  call void @llvm.lifetime.end.p0(i64 32, ptr nonnull %7) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #24
  resume { ptr, i32 } %21

22:                                               ; preds = %11
  store i8 1, ptr %10, align 8, !tbaa !120
  call void @_ZNSt6__ndk117__exception_guardINS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(25) %7) #26
  call void @llvm.lifetime.end.p0(i64 32, ptr nonnull %7) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #24
  ret ptr %12
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk117__exception_guardINS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(25) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard.46", ptr %0, i64 0, i32 1
  %3 = load i8, ptr %2, align 8, !tbaa !120, !range !82, !noundef !83
  %4 = icmp eq i8 %3, 0
  br i1 %4, label %5, label %6

5:                                                ; preds = %1
  invoke void @_ZNKSt6__ndk129_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPS6_EclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #27
          to label %6 unwind label %7

6:                                                ; preds = %5, %1
  ret void

7:                                                ; preds = %5
  %8 = landingpad { ptr, i32 }
          catch ptr null
  %9 = extractvalue { ptr, i32 } %8, 0
  tail call void @__clang_call_terminate(ptr %9) #29
  unreachable
}

; Function Attrs: minsize optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) unnamed_addr #1 align 2 {
  %3 = load i8, ptr %1, align 8
  %4 = and i8 %3, 1
  %5 = icmp eq i8 %4, 0
  br i1 %5, label %6, label %7

6:                                                ; preds = %2
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i64 24, i1 false), !tbaa.struct !128
  br label %12

7:                                                ; preds = %2
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 2
  %9 = load ptr, ptr %8, align 8, !tbaa !12
  %10 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 1
  %11 = load i64, ptr %10, align 8, !tbaa !12
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE25__init_copy_ctor_externalEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %9, i64 noundef %11) #27
  br label %12

12:                                               ; preds = %7, %6
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE25__init_copy_ctor_externalEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = icmp ult i64 %2, 23
  br i1 %4, label %5, label %9

5:                                                ; preds = %3
  %6 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %7 = trunc i64 %2 to i8
  %8 = shl nuw nsw i8 %7, 1
  store i8 %8, ptr %0, align 8
  br label %22

9:                                                ; preds = %3
  %10 = icmp ugt i64 %2, -17
  br i1 %10, label %11, label %12

11:                                               ; preds = %9
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

12:                                               ; preds = %9
  %13 = or i64 %2, 15
  %14 = add i64 %13, 1
  %15 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %14) #27
  %16 = extractvalue [2 x i64] %15, 0
  %17 = inttoptr i64 %16 to ptr
  %18 = extractvalue [2 x i64] %15, 1
  %19 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  store ptr %17, ptr %19, align 8, !tbaa !12
  %20 = or i64 %18, 1
  store i64 %20, ptr %0, align 8
  %21 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %2, ptr %21, align 8, !tbaa !12
  br label %22

22:                                               ; preds = %12, %5
  %23 = phi ptr [ %6, %5 ], [ %17, %12 ]
  %24 = add nuw i64 %2, 1
  %25 = icmp ugt ptr %23, %1
  %26 = getelementptr inbounds i8, ptr %23, i64 %24
  %27 = icmp ule ptr %26, %1
  %28 = select i1 %25, i1 true, i1 %27
  tail call void @llvm.assume(i1 %28)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %23, ptr align 1 %1, i64 %24, i1 false)
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk129_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPS6_EclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #1 comdat align 2 !type !129 {
  %2 = load ptr, ptr %0, align 8, !tbaa !130
  %3 = getelementptr inbounds %"class.std::__ndk1::_AllocatorDestroyRangeReverse", ptr %0, i64 0, i32 2
  %4 = load ptr, ptr %3, align 8, !tbaa !131
  %5 = load ptr, ptr %4, align 8, !tbaa !16
  %6 = getelementptr inbounds %"class.std::__ndk1::_AllocatorDestroyRangeReverse", ptr %0, i64 0, i32 1
  %7 = load ptr, ptr %6, align 8, !tbaa !132
  %8 = load ptr, ptr %7, align 8, !tbaa !16
  %9 = ptrtoint ptr %5 to i64
  %10 = insertvalue [2 x i64] poison, i64 %9, 0
  %11 = insertvalue [2 x i64] %10, i64 %9, 1
  %12 = ptrtoint ptr %8 to i64
  %13 = insertvalue [2 x i64] poison, i64 %12, 0
  %14 = insertvalue [2 x i64] %13, i64 %12, 1
  tail call void @_ZNSt6__ndk119__allocator_destroyB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEENS_16reverse_iteratorIPS6_EESA_EEvRT_T0_T1_(ptr noundef nonnull align 1 dereferenceable(1) %2, [2 x i64] %11, [2 x i64] %14) #27
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk119__allocator_destroyB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEENS_16reverse_iteratorIPS6_EESA_EEvRT_T0_T1_(ptr noundef nonnull align 1 dereferenceable(1) %0, [2 x i64] %1, [2 x i64] %2) local_unnamed_addr #6 comdat personality ptr @__gxx_personality_v0 {
  %4 = extractvalue [2 x i64] %1, 1
  %5 = inttoptr i64 %4 to ptr
  %6 = extractvalue [2 x i64] %2, 1
  %7 = inttoptr i64 %6 to ptr
  br label %8

8:                                                ; preds = %11, %3
  %9 = phi ptr [ %5, %3 ], [ %12, %11 ]
  %10 = icmp eq ptr %9, %7
  br i1 %10, label %13, label %11

11:                                               ; preds = %8
  %12 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %9, i64 -1
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %12) #26
  br label %8, !llvm.loop !133

13:                                               ; preds = %8
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = load ptr, ptr %0, align 8, !tbaa !134
  %3 = load ptr, ptr %2, align 8, !tbaa !115
  %4 = icmp eq ptr %3, null
  br i1 %4, label %8, label %5

5:                                                ; preds = %1
  tail call void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE7__clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #26
  %6 = load ptr, ptr %0, align 8, !tbaa !134
  %7 = load ptr, ptr %6, align 8, !tbaa !115
  tail call void @_ZdlPv(ptr noundef %7) #28
  br label %8

8:                                                ; preds = %5, %1
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE7__clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #19 comdat align 2 {
  %2 = load ptr, ptr %0, align 8, !tbaa !115
  tail call void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE22__base_destruct_at_endB7v170000EPS6_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %2) #26
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE22__base_destruct_at_endB7v170000EPS6_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !119
  br label %5

5:                                                ; preds = %8, %2
  %6 = phi ptr [ %4, %2 ], [ %9, %8 ]
  %7 = icmp eq ptr %6, %1
  br i1 %7, label %10, label %8

8:                                                ; preds = %5
  %9 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %6, i64 -1
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %9) #26
  br label %5

10:                                               ; preds = %5
  store ptr %1, ptr %3, align 8, !tbaa !119
  ret void
}

; Function Attrs: minsize nofree optsize
declare i64 @__strlen_chk(ptr noundef, i64 noundef) local_unnamed_addr #20

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk111make_uniqueB7v170000IN6asmjit5v1_1610CodeHolderEJEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_(ptr noalias sret(%"class.std::__ndk1::unique_ptr.13") align 8 %0) local_unnamed_addr #3 comdat personality ptr @__gxx_personality_v0 {
  %2 = tail call noalias noundef nonnull dereferenceable(344) ptr @_Znwm(i64 noundef 344) #30
  tail call void @_ZN6asmjit5v1_1610CodeHolderC1EPKNS0_7Support9TemporaryE(ptr noundef nonnull align 8 dereferenceable(344) %2, ptr noundef null) #26
  store ptr %2, ptr %0, align 8, !tbaa !135
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1610CodeHolderENS_14default_deleteIS3_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) unnamed_addr #4 comdat align 2 {
  tail call void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1610CodeHolderENS_14default_deleteIS3_EEE5resetB7v170000EPS3_(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef null) #26
  ret void
}

; Function Attrs: minsize nounwind optsize
declare noundef i32 @_ZN6asmjit5v1_1610CodeHolder4initERKNS0_11EnvironmentEm(ptr noundef nonnull align 8 dereferenceable(344), ptr noundef nonnull align 1 dereferenceable(8), i64 noundef) local_unnamed_addr #9

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk111make_uniqueB7v170000IN6asmjit5v1_1612StringLoggerEJEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_(ptr noalias sret(%"class.std::__ndk1::unique_ptr") align 8 %0) local_unnamed_addr #3 comdat personality ptr @__gxx_personality_v0 {
  %2 = tail call noalias noundef nonnull dereferenceable(56) ptr @_Znwm(i64 noundef 56) #30
  tail call void @_ZN6asmjit5v1_1612StringLoggerC1Ev(ptr noundef nonnull align 8 dereferenceable(56) %2) #26
  store ptr %2, ptr %0, align 8, !tbaa !137
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1612StringLoggerENS_14default_deleteIS3_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) unnamed_addr #4 comdat align 2 {
  %2 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr null, ptr %0, align 8, !tbaa !16
  %3 = icmp eq ptr %2, null
  br i1 %3, label %8, label %4

4:                                                ; preds = %1
  %5 = load ptr, ptr %2, align 8, !tbaa !37
  %6 = getelementptr inbounds ptr, ptr %5, i64 1
  %7 = load ptr, ptr %6, align 8
  tail call void %7(ptr noundef nonnull align 8 dereferenceable(56) %2) #26
  br label %8

8:                                                ; preds = %4, %1
  ret void
}

; Function Attrs: minsize nounwind optsize
declare void @_ZN6asmjit5v1_1610CodeHolder9setLoggerEPNS0_6LoggerE(ptr noundef nonnull align 8 dereferenceable(344), ptr noundef) local_unnamed_addr #9

; Function Attrs: minsize nounwind optsize uwtable
define internal fastcc void @_ZNSt6__ndk110unique_ptrIN12_GLOBAL__N_120MyAsmJitErrorHandlerENS_14default_deleteIS2_EEED2B7v170000Ev(ptr nocapture noundef nonnull align 8 dereferenceable(8) %0) unnamed_addr #4 align 2 {
  %2 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr null, ptr %0, align 8, !tbaa !16
  %3 = icmp eq ptr %2, null
  br i1 %3, label %8, label %4

4:                                                ; preds = %1
  %5 = load ptr, ptr %2, align 8, !tbaa !37
  %6 = getelementptr inbounds ptr, ptr %5, i64 1
  %7 = load ptr, ptr %6, align 8
  tail call void %7(ptr noundef nonnull align 8 dereferenceable(9) %2) #26
  br label %8

8:                                                ; preds = %4, %1
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk111make_uniqueB7v170000IN6asmjit5v1_163a649AssemblerEJPNS2_10CodeHolderEEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_(ptr noalias sret(%"class.std::__ndk1::unique_ptr.29") align 8 %0, ptr noundef nonnull align 8 dereferenceable(8) %1) local_unnamed_addr #3 comdat personality ptr @__gxx_personality_v0 {
  %3 = tail call noalias noundef nonnull dereferenceable(176) ptr @_Znwm(i64 noundef 176) #30
  %4 = load ptr, ptr %1, align 8, !tbaa !16
  tail call void @_ZN6asmjit5v1_163a649AssemblerC1EPNS0_10CodeHolderE(ptr noundef nonnull align 8 dereferenceable(176) %3, ptr noundef %4) #26
  store ptr %3, ptr %0, align 8, !tbaa !139
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_163a649AssemblerENS_14default_deleteIS4_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) unnamed_addr #4 comdat align 2 {
  %2 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr null, ptr %0, align 8, !tbaa !16
  %3 = icmp eq ptr %2, null
  br i1 %3, label %8, label %4

4:                                                ; preds = %1
  %5 = load ptr, ptr %2, align 8, !tbaa !37
  %6 = getelementptr inbounds ptr, ptr %5, i64 1
  %7 = load ptr, ptr %6, align 8
  tail call void %7(ptr noundef nonnull align 8 dereferenceable(176) %2) #26
  br label %8

8:                                                ; preds = %4, %1
  ret void
}

; Function Attrs: minsize nounwind optsize
declare void @_ZN6asmjit5v1_1611BaseEmitter15setErrorHandlerEPNS0_12ErrorHandlerE(ptr noundef nonnull align 8 dereferenceable(144), ptr noundef) local_unnamed_addr #9

; Function Attrs: minsize nounwind optsize
declare void @_ZN6asmjit5v1_1610CodeHolderC1EPKNS0_7Support9TemporaryE(ptr noundef nonnull align 8 dereferenceable(344), ptr noundef) unnamed_addr #9

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrIN6asmjit5v1_1610CodeHolderENS_14default_deleteIS3_EEE5resetB7v170000EPS3_(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 !type !141 {
  %3 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr %1, ptr %0, align 8, !tbaa !16
  %4 = icmp eq ptr %3, null
  br i1 %4, label %6, label %5

5:                                                ; preds = %2
  tail call void @_ZNKSt6__ndk114default_deleteIN6asmjit5v1_1610CodeHolderEEclB7v170000EPS3_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull %3) #26
  br label %6

6:                                                ; preds = %5, %2
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk114default_deleteIN6asmjit5v1_1610CodeHolderEEclB7v170000EPS3_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 !type !142 {
  %3 = icmp eq ptr %1, null
  br i1 %3, label %5, label %4

4:                                                ; preds = %2
  tail call void @_ZN6asmjit5v1_1610CodeHolderD1Ev(ptr noundef nonnull align 8 dereferenceable(344) %1) #26
  br label %5

5:                                                ; preds = %4, %2
  tail call void @_ZdlPv(ptr noundef %1) #28
  ret void
}

; Function Attrs: minsize nounwind optsize
declare void @_ZN6asmjit5v1_1610CodeHolderD1Ev(ptr noundef nonnull align 8 dereferenceable(344)) unnamed_addr #9

; Function Attrs: minsize nounwind optsize
declare void @_ZN6asmjit5v1_1612StringLoggerC1Ev(ptr noundef nonnull align 8 dereferenceable(56)) unnamed_addr #9

; Function Attrs: nocallback nofree nounwind willreturn memory(argmem: write)
declare void @llvm.memset.p0.i64(ptr nocapture writeonly, i8, i64, i1 immarg) #21

; Function Attrs: minsize nounwind optsize
declare void @_ZN6asmjit5v1_1612ErrorHandlerC2Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #9

; Function Attrs: minsize nounwind optsize
declare void @_ZN6asmjit5v1_1612ErrorHandlerD2Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #9

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define internal void @_ZN12_GLOBAL__N_120MyAsmJitErrorHandlerD0Ev(ptr noundef nonnull align 8 dereferenceable(9) %0) unnamed_addr #2 align 2 {
  tail call void @_ZN6asmjit5v1_1612ErrorHandlerD2Ev(ptr noundef nonnull align 8 dereferenceable(9) %0) #26
  tail call void @_ZdlPv(ptr noundef nonnull %0) #28
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define internal void @_ZN12_GLOBAL__N_120MyAsmJitErrorHandler11handleErrorEjPKcPN6asmjit5v1_1611BaseEmitterE(ptr nocapture noundef nonnull writeonly align 8 dereferenceable(9) %0, i32 noundef %1, ptr noundef %2, ptr nocapture readnone %3) unnamed_addr #6 align 2 {
  %5 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk1lsB7v170000INS_11char_traitsIcEEEERNS_13basic_ostreamIcT_EES6_PKc(ptr noundef nonnull align 8 dereferenceable(8) @_ZNSt6__ndk14cerrE, ptr noundef nonnull @.str.18) #27
  %6 = tail call noundef ptr @_ZN6asmjit5v1_1610DebugUtils13errorAsStringEj(i32 noundef %1) #26
  %7 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk1lsB7v170000INS_11char_traitsIcEEEERNS_13basic_ostreamIcT_EES6_PKc(ptr noundef nonnull align 8 dereferenceable(8) %5, ptr noundef %6) #27
  %8 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk1lsB7v170000INS_11char_traitsIcEEEERNS_13basic_ostreamIcT_EES6_PKc(ptr noundef nonnull align 8 dereferenceable(8) %7, ptr noundef nonnull @.str.19) #27
  %9 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk1lsB7v170000INS_11char_traitsIcEEEERNS_13basic_ostreamIcT_EES6_PKc(ptr noundef nonnull align 8 dereferenceable(8) %8, ptr noundef %2) #27
  %10 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk14endlB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_(ptr noundef nonnull align 8 dereferenceable(8) %9) #27
  %11 = getelementptr inbounds %"class.(anonymous namespace)::MyAsmJitErrorHandler", ptr %0, i64 0, i32 1
  store i8 1, ptr %11, align 8, !tbaa !79
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk1lsB7v170000INS_11char_traitsIcEEEERNS_13basic_ostreamIcT_EES6_PKc(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef %1) local_unnamed_addr #6 comdat personality ptr @__gxx_personality_v0 {
  %3 = tail call i64 @strlen(ptr noundef nonnull dereferenceable(1) %1) #26
  %4 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk124__put_character_sequenceB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef %1, i64 noundef %3) #27
  ret ptr %4
}

; Function Attrs: minsize nounwind optsize
declare noundef ptr @_ZN6asmjit5v1_1610DebugUtils13errorAsStringEj(i32 noundef) local_unnamed_addr #9

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk14endlB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #10 comdat {
  %2 = load ptr, ptr %0, align 8, !tbaa !37
  %3 = getelementptr i8, ptr %2, i64 -24
  %4 = load i64, ptr %3, align 8
  %5 = getelementptr inbounds i8, ptr %0, i64 %4
  %6 = tail call noundef i8 @_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE5widenB7v170000Ec(ptr noundef nonnull align 8 dereferenceable(148) %5, i8 noundef 10) #27
  %7 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE3putEc(ptr noundef nonnull align 8 dereferenceable(8) %0, i8 noundef %6) #27
  %8 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE5flushEv(ptr noundef nonnull align 8 dereferenceable(8) %0) #27
  ret ptr %0
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk124__put_character_sequenceB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #1 comdat personality ptr @__gxx_personality_v0 {
  %4 = alloca %"class.std::__ndk1::basic_ostream<char>::sentry", align 8
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  invoke void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryC1ERS3_(ptr noundef nonnull align 8 dereferenceable(16) %4, ptr noundef nonnull align 8 dereferenceable(8) %0) #27
          to label %5 unwind label %32

5:                                                ; preds = %3
  %6 = load i8, ptr %4, align 8, !tbaa !143, !range !82, !noundef !83
  %7 = icmp eq i8 %6, 0
  br i1 %7, label %38, label %8

8:                                                ; preds = %5
  %9 = load ptr, ptr %0, align 8, !tbaa !37
  %10 = getelementptr i8, ptr %9, i64 -24
  %11 = load i64, ptr %10, align 8
  %12 = getelementptr inbounds i8, ptr %0, i64 %11
  %13 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %12, i64 0, i32 6
  %14 = load ptr, ptr %13, align 8, !tbaa !145
  %15 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %12, i64 0, i32 1
  %16 = load i32, ptr %15, align 8, !tbaa !147
  %17 = invoke noundef i8 @_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4fillB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(148) %12) #27
          to label %18 unwind label %36

18:                                               ; preds = %8
  %19 = and i32 %16, 176
  %20 = icmp eq i32 %19, 32
  %21 = getelementptr inbounds i8, ptr %1, i64 %2
  %22 = select i1 %20, ptr %21, ptr %1
  %23 = ptrtoint ptr %14 to i64
  %24 = invoke i64 @_ZNSt6__ndk116__pad_and_outputB7v170000IcNS_11char_traitsIcEEEENS_19ostreambuf_iteratorIT_T0_EES6_PKS4_S8_S8_RNS_8ios_baseES4_(i64 %23, ptr noundef %1, ptr noundef %22, ptr noundef %21, ptr noundef nonnull align 8 dereferenceable(136) %12, i8 noundef %17) #27
          to label %25 unwind label %36

25:                                               ; preds = %18
  %26 = icmp eq i64 %24, 0
  br i1 %26, label %27, label %38

27:                                               ; preds = %25
  %28 = load ptr, ptr %0, align 8, !tbaa !37
  %29 = getelementptr i8, ptr %28, i64 -24
  %30 = load i64, ptr %29, align 8
  %31 = getelementptr inbounds i8, ptr %0, i64 %30
  invoke void @_ZNSt6__ndk18ios_base8setstateB7v170000Ej(ptr noundef nonnull align 8 dereferenceable(136) %31, i32 noundef 5) #27
          to label %38 unwind label %34

32:                                               ; preds = %3
  %33 = landingpad { ptr, i32 }
          catch ptr null
  br label %41

34:                                               ; preds = %27
  %35 = landingpad { ptr, i32 }
          catch ptr null
  br label %39

36:                                               ; preds = %18, %8
  %37 = landingpad { ptr, i32 }
          catch ptr null
  br label %39

38:                                               ; preds = %27, %25, %5
  call void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryD1Ev(ptr noundef nonnull align 8 dereferenceable(16) %4) #26
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  br label %50

39:                                               ; preds = %36, %34
  %40 = phi { ptr, i32 } [ %35, %34 ], [ %37, %36 ]
  call void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryD1Ev(ptr noundef nonnull align 8 dereferenceable(16) %4) #26
  br label %41

41:                                               ; preds = %39, %32
  %42 = phi { ptr, i32 } [ %40, %39 ], [ %33, %32 ]
  %43 = extractvalue { ptr, i32 } %42, 0
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  %44 = call ptr @__cxa_begin_catch(ptr %43) #24
  %45 = load ptr, ptr %0, align 8, !tbaa !37
  %46 = getelementptr i8, ptr %45, i64 -24
  %47 = load i64, ptr %46, align 8
  %48 = getelementptr inbounds i8, ptr %0, i64 %47
  invoke void @_ZNSt6__ndk18ios_base33__set_badbit_and_consider_rethrowEv(ptr noundef nonnull align 8 dereferenceable(136) %48) #27
          to label %49 unwind label %51

49:                                               ; preds = %41
  call void @__cxa_end_catch()
  br label %50

50:                                               ; preds = %49, %38
  ret ptr %0

51:                                               ; preds = %41
  %52 = landingpad { ptr, i32 }
          cleanup
  invoke void @__cxa_end_catch()
          to label %53 unwind label %54

53:                                               ; preds = %51
  resume { ptr, i32 } %52

54:                                               ; preds = %51
  %55 = landingpad { ptr, i32 }
          catch ptr null
  %56 = extractvalue { ptr, i32 } %55, 0
  call void @__clang_call_terminate(ptr %56) #29
  unreachable
}

; Function Attrs: minsize optsize
declare void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryC1ERS3_(ptr noundef nonnull align 8 dereferenceable(16), ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #8

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden i64 @_ZNSt6__ndk116__pad_and_outputB7v170000IcNS_11char_traitsIcEEEENS_19ostreambuf_iteratorIT_T0_EES6_PKS4_S8_S8_RNS_8ios_baseES4_(i64 %0, ptr noundef %1, ptr noundef %2, ptr noundef %3, ptr noundef nonnull align 8 dereferenceable(136) %4, i8 noundef %5) local_unnamed_addr #1 comdat personality ptr @__gxx_personality_v0 {
  %7 = alloca %"class.std::__ndk1::basic_string", align 8
  %8 = inttoptr i64 %0 to ptr
  %9 = icmp eq i64 %0, 0
  br i1 %9, label %56, label %10

10:                                               ; preds = %6
  %11 = ptrtoint ptr %3 to i64
  %12 = ptrtoint ptr %1 to i64
  %13 = sub i64 %11, %12
  %14 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %4, i64 0, i32 3
  %15 = load i64, ptr %14, align 8, !tbaa !148
  %16 = icmp sgt i64 %15, %13
  %17 = sub nsw i64 %15, %13
  %18 = select i1 %16, i64 %17, i64 0
  %19 = ptrtoint ptr %2 to i64
  %20 = sub i64 %19, %12
  %21 = icmp sgt i64 %20, 0
  br i1 %21, label %22, label %28

22:                                               ; preds = %10
  %23 = load ptr, ptr %8, align 8, !tbaa !37
  %24 = getelementptr inbounds ptr, ptr %23, i64 12
  %25 = load ptr, ptr %24, align 8
  %26 = tail call noundef i64 %25(ptr noundef nonnull align 8 dereferenceable(64) %8, ptr noundef %1, i64 noundef %20) #27
  %27 = icmp eq i64 %26, %20
  br i1 %27, label %28, label %56

28:                                               ; preds = %22, %10
  %29 = icmp sgt i64 %18, 0
  br i1 %29, label %30, label %46

30:                                               ; preds = %28
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %7) #24
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initEmc(ptr noundef nonnull align 8 dereferenceable(24) %7, i64 noundef %18, i8 noundef %5) #27
  %31 = load i8, ptr %7, align 8
  %32 = and i8 %31, 1
  %33 = icmp eq i8 %32, 0
  %34 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %7, i64 0, i32 2
  %35 = load ptr, ptr %34, align 8
  %36 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %7, i64 0, i32 2
  %37 = select i1 %33, ptr %36, ptr %35
  %38 = load ptr, ptr %8, align 8, !tbaa !37
  %39 = getelementptr inbounds ptr, ptr %38, i64 12
  %40 = load ptr, ptr %39, align 8
  %41 = invoke noundef i64 %40(ptr noundef nonnull align 8 dereferenceable(64) %8, ptr noundef %37, i64 noundef %18) #27
          to label %42 unwind label %44

42:                                               ; preds = %30
  %43 = icmp eq i64 %41, %18
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #24
  br i1 %43, label %46, label %56

44:                                               ; preds = %30
  %45 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #24
  resume { ptr, i32 } %45

46:                                               ; preds = %42, %28
  %47 = sub i64 %11, %19
  %48 = icmp sgt i64 %47, 0
  br i1 %48, label %49, label %55

49:                                               ; preds = %46
  %50 = load ptr, ptr %8, align 8, !tbaa !37
  %51 = getelementptr inbounds ptr, ptr %50, i64 12
  %52 = load ptr, ptr %51, align 8
  %53 = call noundef i64 %52(ptr noundef nonnull align 8 dereferenceable(64) %8, ptr noundef %2, i64 noundef %47) #27
  %54 = icmp eq i64 %53, %47
  br i1 %54, label %55, label %56

55:                                               ; preds = %49, %46
  store i64 0, ptr %14, align 8, !tbaa !148
  br label %56

56:                                               ; preds = %55, %49, %42, %22, %6
  %57 = phi i64 [ 0, %6 ], [ %0, %55 ], [ 0, %42 ], [ 0, %22 ], [ 0, %49 ]
  ret i64 %57
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i8 @_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4fillB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(148) %0) local_unnamed_addr #10 comdat align 2 {
  %2 = getelementptr inbounds %"class.std::__ndk1::basic_ios", ptr %0, i64 0, i32 2
  %3 = load i32, ptr %2, align 8, !tbaa !149
  %4 = icmp eq i32 %3, -1
  br i1 %4, label %5, label %8

5:                                                ; preds = %1
  %6 = tail call noundef i8 @_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE5widenB7v170000Ec(ptr noundef nonnull align 8 dereferenceable(148) %0, i8 noundef 32) #27
  %7 = zext i8 %6 to i32
  store i32 %7, ptr %2, align 8, !tbaa !149
  br label %8

8:                                                ; preds = %5, %1
  %9 = phi i32 [ %7, %5 ], [ %3, %1 ]
  %10 = trunc i32 %9 to i8
  ret i8 %10
}

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryD1Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #9

; Function Attrs: minsize optsize
declare void @_ZNSt6__ndk18ios_base33__set_badbit_and_consider_rethrowEv(ptr noundef nonnull align 8 dereferenceable(136)) local_unnamed_addr #8

declare void @__cxa_end_catch() local_unnamed_addr

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initEmc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1, i8 noundef %2) local_unnamed_addr #10 align 2 personality ptr @__gxx_personality_v0 {
  %4 = icmp ugt i64 %1, -17
  br i1 %4, label %5, label %6

5:                                                ; preds = %3
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

6:                                                ; preds = %3
  %7 = icmp ult i64 %1, 23
  br i1 %7, label %8, label %12

8:                                                ; preds = %6
  %9 = trunc i64 %1 to i8
  %10 = shl nuw nsw i8 %9, 1
  store i8 %10, ptr %0, align 8
  %11 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  br label %22

12:                                               ; preds = %6
  %13 = or i64 %1, 15
  %14 = add i64 %13, 1
  %15 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %14) #27
  %16 = extractvalue [2 x i64] %15, 0
  %17 = inttoptr i64 %16 to ptr
  %18 = extractvalue [2 x i64] %15, 1
  %19 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  store ptr %17, ptr %19, align 8, !tbaa !12
  %20 = or i64 %18, 1
  store i64 %20, ptr %0, align 8
  %21 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %1, ptr %21, align 8, !tbaa !12
  br label %22

22:                                               ; preds = %12, %8
  %23 = phi ptr [ %11, %8 ], [ %17, %12 ]
  br label %24

24:                                               ; preds = %28, %22
  %25 = phi i64 [ %1, %22 ], [ %30, %28 ]
  %26 = phi ptr [ %23, %22 ], [ %29, %28 ]
  %27 = icmp eq i64 %25, 0
  br i1 %27, label %31, label %28

28:                                               ; preds = %24
  store i8 %2, ptr %26, align 1, !tbaa !12
  %29 = getelementptr inbounds i8, ptr %26, i64 1
  %30 = add i64 %25, -1
  br label %24, !llvm.loop !151

31:                                               ; preds = %24
  %32 = getelementptr inbounds i8, ptr %23, i64 %1
  store i8 0, ptr %32, align 1, !tbaa !12
  ret void
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i8 @_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE5widenB7v170000Ec(ptr noundef nonnull align 8 dereferenceable(148) %0, i8 noundef %1) local_unnamed_addr #10 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = alloca %"class.std::__ndk1::locale", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %3) #24
  call void @_ZNKSt6__ndk18ios_base6getlocEv(ptr nonnull sret(%"class.std::__ndk1::locale") align 8 %3, ptr noundef nonnull align 8 dereferenceable(136) %0) #27
  %4 = invoke noundef nonnull align 8 dereferenceable(25) ptr @_ZNSt6__ndk19use_facetB7v170000INS_5ctypeIcEEEERKT_RKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(8) %3) #27
          to label %5 unwind label %11

5:                                                ; preds = %2
  %6 = load ptr, ptr %4, align 8, !tbaa !37
  %7 = getelementptr inbounds ptr, ptr %6, i64 7
  %8 = load ptr, ptr %7, align 8
  %9 = invoke noundef i8 %8(ptr noundef nonnull align 8 dereferenceable(25) %4, i8 noundef %1) #27
          to label %10 unwind label %11

10:                                               ; preds = %5
  call void @_ZNSt6__ndk16localeD1Ev(ptr noundef nonnull align 8 dereferenceable(8) %3) #26
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #24
  ret i8 %9

11:                                               ; preds = %5, %2
  %12 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk16localeD1Ev(ptr noundef nonnull align 8 dereferenceable(8) %3) #26
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #24
  resume { ptr, i32 } %12
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(25) ptr @_ZNSt6__ndk19use_facetB7v170000INS_5ctypeIcEEEERKT_RKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #10 comdat {
  %2 = tail call noundef ptr @_ZNKSt6__ndk16locale9use_facetERNS0_2idE(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef nonnull align 8 dereferenceable(12) @_ZNSt6__ndk15ctypeIcE2idE) #27
  ret ptr %2
}

; Function Attrs: minsize optsize
declare void @_ZNKSt6__ndk18ios_base6getlocEv(ptr sret(%"class.std::__ndk1::locale") align 8, ptr noundef nonnull align 8 dereferenceable(136)) local_unnamed_addr #8

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk16localeD1Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #9

; Function Attrs: minsize optsize
declare noundef ptr @_ZNKSt6__ndk16locale9use_facetERNS0_2idE(ptr noundef nonnull align 8 dereferenceable(8), ptr noundef nonnull align 8 dereferenceable(12)) local_unnamed_addr #8

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk18ios_base8setstateB7v170000Ej(ptr noundef nonnull align 8 dereferenceable(136) %0, i32 noundef %1) local_unnamed_addr #10 comdat align 2 {
  %3 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %0, i64 0, i32 4
  %4 = load i32, ptr %3, align 8, !tbaa !152
  %5 = or i32 %4, %1
  tail call void @_ZNSt6__ndk18ios_base5clearEj(ptr noundef nonnull align 8 dereferenceable(136) %0, i32 noundef %5) #27
  ret void
}

; Function Attrs: minsize optsize
declare void @_ZNSt6__ndk18ios_base5clearEj(ptr noundef nonnull align 8 dereferenceable(136), i32 noundef) local_unnamed_addr #8

; Function Attrs: minsize optsize
declare noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE3putEc(ptr noundef nonnull align 8 dereferenceable(8), i8 noundef) local_unnamed_addr #8

; Function Attrs: minsize optsize
declare noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE5flushEv(ptr noundef nonnull align 8 dereferenceable(8)) local_unnamed_addr #8

; Function Attrs: minsize nounwind optsize
declare void @_ZN6asmjit5v1_163a649AssemblerC1EPNS0_10CodeHolderE(ptr noundef nonnull align 8 dereferenceable(176), ptr noundef) unnamed_addr #9

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZN15RegProtectGuard7cleanupEv(ptr noundef nonnull align 8 dereferenceable(68) %0) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 !type !153 {
  %2 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 2
  %3 = load i8, ptr %2, align 8, !tbaa !113, !range !82, !noundef !83
  %4 = icmp eq i8 %3, 0
  %5 = load ptr, ptr %0, align 8
  %6 = icmp eq ptr %5, null
  %7 = select i1 %4, i1 true, i1 %6
  br i1 %7, label %12, label %8

8:                                                ; preds = %1
  tail call void @_ZN15RegProtectGuard10doPopPairsEv(ptr noundef nonnull align 8 dereferenceable(68) %0) #27
  store i8 0, ptr %2, align 8, !tbaa !113
  %9 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1
  %10 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1, i32 1
  %11 = load ptr, ptr %9, align 8, !tbaa !111
  store ptr %11, ptr %10, align 8, !tbaa !112
  br label %12

12:                                               ; preds = %8, %1
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::vector<asmjit::v1_16::a64::GpX>::__destroy_vector", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #24
  store ptr %0, ptr %2, align 8, !tbaa !16
  invoke void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %2) #27
          to label %3 unwind label %4

3:                                                ; preds = %1
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #24
  ret void

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          catch ptr null
  %6 = extractvalue { ptr, i32 } %5, 0
  call void @__clang_call_terminate(ptr %6) #29
  unreachable
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN15RegProtectGuard10doPopPairsEv(ptr noundef nonnull align 8 dereferenceable(68) %0) local_unnamed_addr #1 comdat align 2 !type !153 {
  %2 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %3 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %4 = alloca %"class.asmjit::v1_16::arm::Mem", align 4
  %5 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1
  %6 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1, i32 1
  %7 = load ptr, ptr %6, align 8, !tbaa !112
  %8 = load ptr, ptr %5, align 8, !tbaa !111
  %9 = ptrtoint ptr %7 to i64
  %10 = ptrtoint ptr %8 to i64
  %11 = sub i64 %9, %10
  %12 = ashr exact i64 %11, 4
  %13 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4
  %14 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %2, i64 0, i32 1
  %15 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  %16 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %2, i64 0, i32 2
  %17 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2
  %18 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %2, i64 0, i32 2, i64 1
  %19 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2, i64 1
  %20 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %3, i64 0, i32 1
  %21 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %3, i64 0, i32 2
  %22 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %3, i64 0, i32 2, i64 1
  %23 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 5
  %24 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 5, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  %25 = getelementptr inbounds i8, ptr %4, i64 4
  %26 = getelementptr inbounds i8, ptr %4, i64 8
  %27 = getelementptr inbounds i8, ptr %4, i64 12
  br label %28

28:                                               ; preds = %72, %1
  %29 = phi i64 [ %12, %1 ], [ %33, %72 ]
  %30 = icmp eq i64 %29, 0
  br i1 %30, label %31, label %32

31:                                               ; preds = %28
  ret void

32:                                               ; preds = %28
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %2) #24
  %33 = add i64 %29, -2
  %34 = load ptr, ptr %5, align 8, !tbaa !111
  %35 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %34, i64 %33
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %35, i64 16, i1 false)
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %3) #24
  %36 = add i64 %29, -1
  %37 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %34, i64 %36
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %3, ptr noundef nonnull align 4 dereferenceable(16) %37, i64 16, i1 false)
  %38 = load i32, ptr %2, align 4, !tbaa !39
  %39 = load i32, ptr %13, align 4, !tbaa !39
  %40 = icmp eq i32 %38, %39
  %41 = load i32, ptr %14, align 4, !tbaa !44
  %42 = load i32, ptr %15, align 8, !tbaa !44
  %43 = icmp eq i32 %41, %42
  %44 = and i1 %40, %43
  %45 = load i32, ptr %16, align 4, !tbaa !46
  %46 = load i32, ptr %17, align 4, !tbaa !46
  %47 = icmp eq i32 %45, %46
  %48 = and i1 %44, %47
  %49 = load i32, ptr %18, align 4, !tbaa !46
  %50 = load i32, ptr %19, align 8, !tbaa !46
  %51 = icmp eq i32 %49, %50
  %52 = and i1 %48, %51
  br i1 %52, label %53, label %65

53:                                               ; preds = %32
  %54 = load i32, ptr %3, align 4, !tbaa !39
  %55 = icmp eq i32 %54, %38
  %56 = load i32, ptr %20, align 4, !tbaa !44
  %57 = icmp eq i32 %56, %41
  %58 = and i1 %55, %57
  %59 = load i32, ptr %21, align 4, !tbaa !46
  %60 = icmp eq i32 %59, %45
  %61 = and i1 %58, %60
  %62 = load i32, ptr %22, align 4, !tbaa !46
  %63 = icmp eq i32 %62, %49
  %64 = and i1 %61, %63
  br i1 %64, label %72, label %65

65:                                               ; preds = %53, %32
  %66 = load ptr, ptr %0, align 8, !tbaa !101
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  %67 = load i32, ptr %23, align 4, !tbaa !39, !noalias !154
  %68 = and i32 %67, 248
  %69 = load i32, ptr %24, align 8, !tbaa !44, !noalias !154
  store i32 %69, ptr %25, align 4
  store i32 0, ptr %26, align 4
  %70 = or i32 %68, 33554434
  store i32 %70, ptr %4, align 4, !tbaa !39, !alias.scope !157
  store i32 16, ptr %27, align 4, !tbaa !46
  %71 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3ldpERKNS1_2GpES7_RKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %66, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  br label %72

72:                                               ; preds = %65, %53
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %3) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %2) #24
  br label %28, !llvm.loop !160
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3ldpERKNS1_2GpES7_RKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3) local_unnamed_addr #10 comdat align 2 !type !161 {
  %5 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_S4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 152, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3) #27
  ret i32 %5
}

; Function Attrs: minsize optsize
declare noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_S4_(ptr noundef nonnull align 8 dereferenceable(144), i32 noundef, ptr noundef nonnull align 4 dereferenceable(16), ptr noundef nonnull align 4 dereferenceable(16), ptr noundef nonnull align 4 dereferenceable(16)) local_unnamed_addr #8

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 !type !162 {
  %2 = load ptr, ptr %0, align 8, !tbaa !163
  %3 = load ptr, ptr %2, align 8, !tbaa !111
  %4 = icmp eq ptr %3, null
  br i1 %4, label %7, label %5

5:                                                ; preds = %1
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %2, i64 0, i32 1
  store ptr %3, ptr %6, align 8, !tbaa !112
  tail call void @_ZdlPv(ptr noundef nonnull %3) #28
  br label %7

7:                                                ; preds = %5, %1
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEEC2EmRKh(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1, ptr noundef nonnull align 1 dereferenceable(1) %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = alloca %"struct.std::__ndk1::__exception_guard.56", align 8
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  store ptr %0, ptr %4, align 8, !tbaa.struct !15, !alias.scope !165
  %5 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard.56", ptr %4, i64 0, i32 1
  store i8 0, ptr %5, align 8, !tbaa !168, !alias.scope !165
  %6 = icmp eq i64 %1, 0
  br i1 %6, label %11, label %7

7:                                                ; preds = %3
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE11__vallocateB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) #27
          to label %8 unwind label %9

8:                                                ; preds = %7
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE18__construct_at_endEmRKh(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1, ptr noundef nonnull align 1 dereferenceable(1) %2) #27
          to label %11 unwind label %9

9:                                                ; preds = %8, %7
  %10 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %4) #26
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  resume { ptr, i32 } %10

11:                                               ; preds = %8, %3
  store i8 1, ptr %5, align 8, !tbaa !168
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %4) #26
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  ret void
}

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.objectsize.i64.p0(ptr, i1 immarg, i1 immarg, i1 immarg) #22

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::vector<unsigned char>::__destroy_vector", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #24
  store ptr %0, ptr %2, align 8, !tbaa !16
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %2) #27
          to label %3 unwind label %4

3:                                                ; preds = %1
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #24
  ret void

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          catch ptr null
  %6 = extractvalue { ptr, i32 } %5, 0
  call void @__clang_call_terminate(ptr %6) #29
  unreachable
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE11__vallocateB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = icmp slt i64 %1, 0
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZNKSt6__ndk16vectorIhNS_9allocatorIhEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

5:                                                ; preds = %2
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %0, i64 0, i32 2
  %7 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIhEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %6, i64 noundef %1) #27
  %8 = extractvalue [2 x i64] %7, 0
  %9 = inttoptr i64 %8 to ptr
  %10 = extractvalue [2 x i64] %7, 1
  store ptr %9, ptr %0, align 8, !tbaa !50
  %11 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %0, i64 0, i32 1
  store ptr %9, ptr %11, align 8, !tbaa !54
  %12 = getelementptr inbounds i8, ptr %9, i64 %10
  store ptr %12, ptr %6, align 8, !tbaa !16
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE18__construct_at_endEmRKh(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1, ptr noundef nonnull align 1 dereferenceable(1) %2) local_unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %0, i64 0, i32 1
  %5 = load ptr, ptr %4, align 8, !tbaa !54
  %6 = getelementptr inbounds i8, ptr %5, i64 %1
  br label %7

7:                                                ; preds = %11, %3
  %8 = phi ptr [ %5, %3 ], [ %14, %11 ]
  %9 = icmp eq ptr %8, %6
  br i1 %9, label %10, label %11

10:                                               ; preds = %7
  store ptr %6, ptr %4, align 8, !tbaa !54
  ret void

11:                                               ; preds = %7
  %12 = icmp ne ptr %8, null
  tail call void @llvm.assume(i1 %12)
  %13 = load i8, ptr %2, align 1, !tbaa !12
  store i8 %13, ptr %8, align 1, !tbaa !12
  %14 = getelementptr inbounds i8, ptr %8, i64 1
  br label %7, !llvm.loop !171
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk117__exception_guardINS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard.56", ptr %0, i64 0, i32 1
  %3 = load i8, ptr %2, align 8, !tbaa !168, !range !82, !noundef !83
  %4 = icmp eq i8 %3, 0
  br i1 %4, label %5, label %6

5:                                                ; preds = %1
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) #27
          to label %6 unwind label %7

6:                                                ; preds = %5, %1
  ret void

7:                                                ; preds = %5
  %8 = landingpad { ptr, i32 }
          catch ptr null
  %9 = extractvalue { ptr, i32 } %8, 0
  tail call void @__clang_call_terminate(ptr %9) #29
  unreachable
}

; Function Attrs: minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk16vectorIhNS_9allocatorIhEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #12 comdat align 2 {
  tail call void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef nonnull @.str.17) #31
  unreachable
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIhEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat {
  %3 = tail call noalias noundef nonnull ptr @_Znwm(i64 noundef %1) #30
  %4 = ptrtoint ptr %3 to i64
  %5 = insertvalue [2 x i64] poison, i64 %4, 0
  %6 = insertvalue [2 x i64] %5, i64 %1, 1
  ret [2 x i64] %6
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = load ptr, ptr %0, align 8, !tbaa !172
  %3 = load ptr, ptr %2, align 8, !tbaa !50
  %4 = icmp eq ptr %3, null
  br i1 %4, label %7, label %5

5:                                                ; preds = %1
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %2, i64 0, i32 1
  store ptr %3, ptr %6, align 8, !tbaa !54
  tail call void @_ZdlPv(ptr noundef nonnull %3) #28
  br label %7

7:                                                ; preds = %5, %1
  ret void
}

; Function Attrs: minsize nofree nounwind optsize memory(argmem: readwrite)
declare ptr @__memcpy_chk(ptr noalias noundef writeonly, ptr noalias nocapture noundef readonly, i64 noundef, i64 noundef) local_unnamed_addr #23

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE1bERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1) local_unnamed_addr #10 comdat align 2 !type !173 {
  %3 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_E(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 28, ptr noundef nonnull align 4 dereferenceable(16) %1) #27
  ret i32 %3
}

; Function Attrs: minsize optsize
declare noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_E(ptr noundef nonnull align 8 dereferenceable(144), i32 noundef, ptr noundef nonnull align 4 dereferenceable(16)) local_unnamed_addr #8

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3adrERKNS1_2GpERKNS0_5LabelE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) local_unnamed_addr #10 comdat align 2 !type !98 {
  %4 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 6, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2) #27
  ret i32 %4
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3nopEv(ptr noundef nonnull align 1 dereferenceable(1) %0) local_unnamed_addr #10 comdat align 2 !type !174 {
  %2 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEj(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 257) #27
  ret i32 %2
}

; Function Attrs: minsize optsize
declare noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_(ptr noundef nonnull align 8 dereferenceable(144), i32 noundef, ptr noundef nonnull align 4 dereferenceable(16), ptr noundef nonnull align 4 dereferenceable(16)) local_unnamed_addr #8

; Function Attrs: minsize optsize
declare noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEj(ptr noundef nonnull align 8 dereferenceable(144), i32 noundef) local_unnamed_addr #8

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEEC2IPKhLi0EEET_S7_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, ptr noundef %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = alloca %"struct.std::__ndk1::__exception_guard.56", align 8
  %5 = getelementptr inbounds %"class.std::__ndk1::vector.49", ptr %0, i64 0, i32 1
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  store ptr %0, ptr %4, align 8, !tbaa.struct !15, !alias.scope !175
  %6 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard.56", ptr %4, i64 0, i32 1
  store i8 0, ptr %6, align 8, !tbaa !168, !alias.scope !175
  %7 = ptrtoint ptr %2 to i64
  %8 = ptrtoint ptr %1 to i64
  %9 = sub i64 %7, %8
  %10 = icmp eq ptr %2, %1
  br i1 %10, label %17, label %11

11:                                               ; preds = %3
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE11__vallocateB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %9) #27
          to label %12 unwind label %15

12:                                               ; preds = %11
  %13 = load ptr, ptr %5, align 8, !tbaa !54
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %13, ptr align 1 %1, i64 %9, i1 false)
  %14 = getelementptr inbounds i8, ptr %13, i64 %9
  store ptr %14, ptr %5, align 8, !tbaa !54
  br label %17

15:                                               ; preds = %11
  %16 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %4) #26
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  resume { ptr, i32 } %16

17:                                               ; preds = %12, %3
  store i8 1, ptr %6, align 8, !tbaa !168
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %4) #26
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(8) %1, ptr noundef nonnull align 8 dereferenceable(24) %2, i1 noundef %3, ptr noundef nonnull align 4 dereferenceable(16) %4, ptr noundef nonnull align 4 dereferenceable(16) %5, ptr noundef nonnull align 8 dereferenceable(8) %6) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %8 = alloca ptr, align 8
  %9 = alloca i8, align 4
  %10 = alloca %"class.std::__ndk1::tuple", align 8
  %11 = alloca i64, align 8
  %12 = alloca %"struct.std::__ndk1::hash.86", align 1
  %13 = alloca %class.anon.88, align 8
  store ptr %0, ptr %8, align 8, !tbaa !16
  %14 = zext i1 %3 to i8
  store i8 %14, ptr %9, align 4, !tbaa !178
  %15 = load i8, ptr @_ZGVZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok, align 8
  %16 = and i8 %15, 1
  %17 = icmp eq i8 %16, 0
  br i1 %17, label %18, label %20, !prof !180

18:                                               ; preds = %7
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(32) @_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok, i8 0, i64 32, i1 false)
  store float 1.000000e+00, ptr getelementptr inbounds (%"class.std::__ndk1::unordered_map", ptr @_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok, i64 0, i32 0, i32 3), align 8, !tbaa !181
  %19 = tail call i32 @__cxa_thread_atexit(ptr nonnull @_ZNSt6__ndk113unordered_mapImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS_4hashImEENS_8equal_toImEENS4_INS_4pairIKmS6_EEEEED2B7v170000Ev, ptr nonnull @_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok, ptr nonnull @__dso_handle) #24
  store i8 1, ptr @_ZGVZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok, align 8
  br label %20

20:                                               ; preds = %18, %7
  call void @llvm.lifetime.start.p0(i64 40, ptr nonnull %10) #24
  tail call void @llvm.experimental.noalias.scope.decl(metadata !184)
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %10, ptr noundef nonnull align 4 dereferenceable(16) %4, i64 16, i1 false)
  %21 = getelementptr inbounds i8, ptr %10, i64 16
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %21, ptr noundef nonnull align 4 dereferenceable(16) %5, i64 16, i1 false)
  %22 = getelementptr inbounds i8, ptr %10, i64 32
  %23 = load i64, ptr %6, align 8, !tbaa !114, !noalias !184
  store i64 %23, ptr %22, align 8, !tbaa !187, !alias.scope !184
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %11) #24
  store i64 0, ptr %11, align 8, !tbaa !114
  call void @llvm.lifetime.start.p0(i64 1, ptr nonnull %12) #24
  %24 = load ptr, ptr %2, align 8, !tbaa !115
  %25 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %2, i64 0, i32 1
  %26 = load ptr, ptr %25, align 8, !tbaa !119
  br label %27

27:                                               ; preds = %37, %20
  %28 = phi i64 [ 0, %20 ], [ %45, %37 ]
  %29 = phi ptr [ %24, %20 ], [ %46, %37 ]
  %30 = icmp eq ptr %29, %26
  br i1 %30, label %31, label %37

31:                                               ; preds = %27
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %13) #24
  store ptr %8, ptr %13, align 8, !tbaa !16
  %32 = getelementptr inbounds %class.anon.88, ptr %13, i64 0, i32 1
  store ptr %9, ptr %32, align 8, !tbaa !16
  %33 = getelementptr inbounds %class.anon.88, ptr %13, i64 0, i32 2
  store ptr %10, ptr %33, align 8, !tbaa !16
  %34 = call align 8 ptr @llvm.threadlocal.address.p0(ptr align 8 @_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_E9s_last_ok)
  %35 = call i64 @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE4findImEENS_15__hash_iteratorIPNS_11__hash_nodeIS8_PvEEEERKT_(ptr noundef nonnull align 8 dereferenceable(36) %34, ptr noundef nonnull align 8 dereferenceable(8) %11) #27
  %36 = icmp eq i64 %35, 0
  br i1 %36, label %73, label %47

37:                                               ; preds = %27
  %38 = call [2 x i64] @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEcvNS_17basic_string_viewIcS2_EEB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %29) #26
  %39 = call noundef i64 @_ZNKSt6__ndk118__string_view_hashIcEclB7v170000ENS_17basic_string_viewIcNS_11char_traitsIcEEEE(ptr noundef nonnull align 1 dereferenceable(1) %12, [2 x i64] %38) #26
  %40 = add i64 %39, -7046029254386353131
  %41 = shl i64 %28, 6
  %42 = add i64 %40, %41
  %43 = lshr i64 %28, 2
  %44 = add i64 %42, %43
  %45 = xor i64 %44, %28
  store i64 %45, ptr %11, align 8, !tbaa !114
  %46 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %29, i64 1
  br label %27

47:                                               ; preds = %31
  %48 = inttoptr i64 %35 to ptr
  %49 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %48, i64 0, i32 2, i32 0, i32 1
  %50 = load i8, ptr %49, align 8
  %51 = and i8 %50, 1
  %52 = icmp eq i8 %51, 0
  %53 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %48, i64 0, i32 2, i32 0, i32 1, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2
  %54 = load ptr, ptr %53, align 8
  %55 = getelementptr inbounds i8, ptr %48, i64 25
  %56 = select i1 %52, ptr %55, ptr %54
  %57 = call noundef i64 @_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ENKUlPKcE_clESS_(ptr noundef nonnull align 8 dereferenceable(24) %13, ptr noundef %56) #27
  store i64 %57, ptr %1, align 8, !tbaa !25
  switch i64 %57, label %112 [
    i64 0, label %58
    i64 -19978, label %71
  ]

58:                                               ; preds = %47
  %59 = load ptr, ptr %2, align 8, !tbaa !115
  %60 = load ptr, ptr %25, align 8, !tbaa !119
  %61 = icmp eq ptr %59, %60
  br i1 %61, label %112, label %62

62:                                               ; preds = %58
  %63 = call noundef i1 @_ZNSt6__ndk1eqB7v170000INS_9allocatorIcEEEEbRKNS_12basic_stringIcNS_11char_traitsIcEET_EES9_(ptr noundef nonnull align 8 dereferenceable(24) %49, ptr noundef nonnull align 8 dereferenceable(24) %59) #26
  br i1 %63, label %112, label %64

64:                                               ; preds = %62
  %65 = load i8, ptr %49, align 8
  %66 = and i8 %65, 1
  %67 = icmp eq i8 %66, 0
  %68 = load ptr, ptr %53, align 8
  %69 = select i1 %67, ptr %55, ptr %68
  %70 = call i32 (ptr, ...) @printf(ptr noundef nonnull dereferenceable(1) @.str.28, ptr noundef %69) #27
  br label %112

71:                                               ; preds = %47
  %72 = call i64 @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE5eraseENS_21__hash_const_iteratorIPNS_11__hash_nodeIS8_PvEEEE(ptr noundef nonnull align 8 dereferenceable(36) %34, i64 %35) #27
  br label %73

73:                                               ; preds = %71, %31
  br label %74

74:                                               ; preds = %109, %73
  %75 = phi i64 [ 0, %73 ], [ %110, %109 ]
  %76 = load ptr, ptr %25, align 8, !tbaa !119
  %77 = load ptr, ptr %2, align 8, !tbaa !115
  %78 = ptrtoint ptr %76 to i64
  %79 = ptrtoint ptr %77 to i64
  %80 = sub i64 %78, %79
  %81 = sdiv exact i64 %80, 24
  %82 = icmp ult i64 %75, %81
  br i1 %82, label %83, label %111

83:                                               ; preds = %74
  %84 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %77, i64 %75
  %85 = load i8, ptr %84, align 8
  %86 = and i8 %85, 1
  %87 = icmp eq i8 %86, 0
  %88 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %84, i64 0, i32 2
  %89 = load ptr, ptr %88, align 8
  %90 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %84, i64 0, i32 2
  %91 = select i1 %87, ptr %90, ptr %89
  %92 = call noundef i64 @_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ENKUlPKcE_clESS_(ptr noundef nonnull align 8 dereferenceable(24) %13, ptr noundef %91) #27
  store i64 %92, ptr %1, align 8, !tbaa !25
  switch i64 %92, label %106 [
    i64 0, label %93
    i64 -19978, label %109
  ]

93:                                               ; preds = %83
  %94 = icmp eq i64 %75, 0
  br i1 %94, label %103, label %95

95:                                               ; preds = %93
  %96 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %84, i64 0, i32 2
  %97 = load i8, ptr %84, align 8
  %98 = and i8 %97, 1
  %99 = icmp eq i8 %98, 0
  %100 = load ptr, ptr %96, align 8
  %101 = select i1 %99, ptr %90, ptr %100
  %102 = call i32 (ptr, ...) @printf(ptr noundef nonnull dereferenceable(1) @.str.29, ptr noundef %101) #27
  br label %103

103:                                              ; preds = %95, %93
  %104 = call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk113unordered_mapImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS_4hashImEENS_8equal_toImEENS4_INS_4pairIKmS6_EEEEEixERSC_(ptr noundef nonnull align 8 dereferenceable(40) %34, ptr noundef nonnull align 8 dereferenceable(8) %11) #27
  %105 = call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEaSERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %104, ptr noundef nonnull align 8 dereferenceable(24) %84) #27
  br label %112

106:                                              ; preds = %83
  %107 = call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk113unordered_mapImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS_4hashImEENS_8equal_toImEENS4_INS_4pairIKmS6_EEEEEixERSC_(ptr noundef nonnull align 8 dereferenceable(40) %34, ptr noundef nonnull align 8 dereferenceable(8) %11) #27
  %108 = call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEaSERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %107, ptr noundef nonnull align 8 dereferenceable(24) %84) #27
  br label %112

109:                                              ; preds = %83
  %110 = add nuw i64 %75, 1
  br label %74, !llvm.loop !189

111:                                              ; preds = %74
  store i64 -19978, ptr %1, align 8, !tbaa !25
  br label %112

112:                                              ; preds = %111, %106, %103, %64, %62, %58, %47
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %13) #24
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %12) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %11) #24
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %10) #24
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk113unordered_mapImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS_4hashImEENS_8equal_toImEENS4_INS_4pairIKmS6_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) unnamed_addr #4 comdat align 2 {
  tail call void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(36) %0) #26
  ret void
}

; Function Attrs: nounwind
declare i32 @__cxa_thread_atexit(ptr, ptr, ptr) local_unnamed_addr #24

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNKSt6__ndk118__string_view_hashIcEclB7v170000ENS_17basic_string_viewIcNS_11char_traitsIcEEEE(ptr noundef nonnull align 1 dereferenceable(1) %0, [2 x i64] %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 !type !190 {
  %3 = extractvalue [2 x i64] %1, 0
  %4 = inttoptr i64 %3 to ptr
  %5 = extractvalue [2 x i64] %1, 1
  %6 = getelementptr inbounds i8, ptr %4, i64 %5
  %7 = invoke noundef i64 @_ZNSt6__ndk116__do_string_hashB7v170000IPKcEEmT_S3_(ptr noundef %4, ptr noundef %6) #27
          to label %8 unwind label %9

8:                                                ; preds = %2
  ret i64 %7

9:                                                ; preds = %2
  %10 = landingpad { ptr, i32 }
          catch ptr null
  %11 = extractvalue { ptr, i32 } %10, 0
  tail call void @__clang_call_terminate(ptr %11) #29
  unreachable
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEcvNS_17basic_string_viewIcS2_EEB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #4 comdat align 2 {
  %2 = load i8, ptr %0, align 8
  %3 = and i8 %2, 1
  %4 = icmp eq i8 %3, 0
  %5 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %6 = load ptr, ptr %5, align 8
  %7 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %8 = select i1 %4, ptr %7, ptr %6
  %9 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %10 = load i64, ptr %9, align 8
  %11 = lshr i8 %2, 1
  %12 = zext i8 %11 to i64
  %13 = select i1 %4, i64 %12, i64 %10
  %14 = icmp eq i64 %13, 0
  %15 = icmp ne ptr %8, null
  %16 = or i1 %15, %14
  tail call void @llvm.assume(i1 %16)
  %17 = ptrtoint ptr %8 to i64
  %18 = insertvalue [2 x i64] poison, i64 %17, 0
  %19 = insertvalue [2 x i64] %18, i64 %13, 1
  ret [2 x i64] %19
}

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare nonnull ptr @llvm.threadlocal.address.p0(ptr nonnull) #22

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ENKUlPKcE_clESS_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #10 comdat align 2 !type !191 {
  %3 = alloca ptr, align 8
  %4 = alloca %class.anon.93, align 8
  store ptr %1, ptr %3, align 8, !tbaa !16
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %4) #24
  %5 = load ptr, ptr %0, align 8, !tbaa !192
  store ptr %5, ptr %4, align 8, !tbaa !16
  %6 = getelementptr inbounds %class.anon.93, ptr %4, i64 0, i32 1
  store ptr %3, ptr %6, align 8, !tbaa !16
  %7 = getelementptr inbounds %class.anon.93, ptr %4, i64 0, i32 2
  %8 = getelementptr inbounds %class.anon.88, ptr %0, i64 0, i32 1
  %9 = load ptr, ptr %8, align 8, !tbaa !194
  store ptr %9, ptr %7, align 8, !tbaa !16
  %10 = getelementptr inbounds %class.anon.88, ptr %0, i64 0, i32 2
  %11 = load ptr, ptr %10, align 8, !tbaa !195
  %12 = call noundef i64 @_ZNSt6__ndk15applyB7v170000IZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES7_RmEEEvPNS5_9AssemblerER7KModErrRKNS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENSH_ISJ_EEEENS1_12NeedReturnX0EDpOT_ENKUlPKcE_clEST_EUlSR_E_RNS_5tupleIJS6_S6_mEEEEEDcOT_OT0_(ptr noundef nonnull align 8 dereferenceable(24) %4, ptr noundef nonnull align 8 dereferenceable(40) %11) #27
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #24
  ret i64 %12
}

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNSt6__ndk1eqB7v170000INS_9allocatorIcEEEEbRKNS_12basic_stringIcNS_11char_traitsIcEET_EES9_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #25 comdat {
  %3 = load i8, ptr %0, align 8
  %4 = and i8 %3, 1
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %7 = load i64, ptr %6, align 8
  %8 = lshr i8 %3, 1
  %9 = zext i8 %8 to i64
  %10 = select i1 %5, i64 %9, i64 %7
  %11 = load i8, ptr %1, align 8
  %12 = and i8 %11, 1
  %13 = icmp eq i8 %12, 0
  %14 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 1
  %15 = load i64, ptr %14, align 8
  %16 = lshr i8 %11, 1
  %17 = zext i8 %16 to i64
  %18 = select i1 %13, i64 %17, i64 %15
  %19 = icmp eq i64 %10, %18
  br i1 %19, label %20, label %44

20:                                               ; preds = %2
  %21 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %22 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 2
  %23 = load ptr, ptr %22, align 8
  %24 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %1, i64 0, i32 2
  %25 = select i1 %13, ptr %24, ptr %23
  br i1 %5, label %31, label %26

26:                                               ; preds = %20
  %27 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %28 = load ptr, ptr %27, align 8
  %29 = tail call noundef i32 @_ZNSt6__ndk111char_traitsIcE7compareEPKcS3_m(ptr noundef %28, ptr noundef %25, i64 noundef %10) #26
  %30 = icmp eq i32 %29, 0
  br label %44

31:                                               ; preds = %40, %20
  %32 = phi i64 [ %41, %40 ], [ %9, %20 ]
  %33 = phi ptr [ %42, %40 ], [ %21, %20 ]
  %34 = phi ptr [ %43, %40 ], [ %25, %20 ]
  %35 = icmp eq i64 %32, 0
  br i1 %35, label %44, label %36

36:                                               ; preds = %31
  %37 = load i8, ptr %33, align 1, !tbaa !12
  %38 = load i8, ptr %34, align 1, !tbaa !12
  %39 = icmp eq i8 %37, %38
  br i1 %39, label %40, label %44

40:                                               ; preds = %36
  %41 = add i64 %32, -1
  %42 = getelementptr inbounds i8, ptr %33, i64 1
  %43 = getelementptr inbounds i8, ptr %34, i64 1
  br label %31, !llvm.loop !196

44:                                               ; preds = %36, %31, %26, %2
  %45 = phi i1 [ false, %2 ], [ %30, %26 ], [ false, %36 ], [ true, %31 ]
  ret i1 %45
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk113unordered_mapImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS_4hashImEENS_8equal_toImEENS4_INS_4pairIKmS6_EEEEEixERSC_(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef nonnull align 8 dereferenceable(8) %1) local_unnamed_addr #1 comdat align 2 {
  %3 = alloca %"class.std::__ndk1::tuple.150", align 8
  %4 = alloca %"class.std::__ndk1::tuple.153", align 1
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %3) #24
  store ptr %1, ptr %3, align 8
  call void @llvm.lifetime.start.p0(i64 1, ptr nonnull %4) #24
  %5 = call [2 x i64] @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE25__emplace_unique_key_argsImJRKNS_21piecewise_construct_tENS_5tupleIJRKmEEENSN_IJEEEEEENS_4pairINS_15__hash_iteratorIPNS_11__hash_nodeIS8_PvEEEEbEERKT_DpOT0_(ptr noundef nonnull align 8 dereferenceable(36) %0, ptr noundef nonnull align 8 dereferenceable(8) %1, ptr noundef nonnull align 1 dereferenceable(1) @_ZNSt6__ndk1L19piecewise_constructE, ptr noundef nonnull align 8 dereferenceable(8) %3, ptr noundef nonnull align 1 dereferenceable(1) %4) #27
  %6 = extractvalue [2 x i64] %5, 0
  %7 = inttoptr i64 %6 to ptr
  %8 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %7, i64 0, i32 2, i32 0, i32 1
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %4) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #24
  ret ptr %8
}

; Function Attrs: minsize mustprogress optsize uwtable
define available_externally noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEaSERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #6 align 2 {
  %3 = icmp eq ptr %0, %1
  br i1 %3, label %30, label %4

4:                                                ; preds = %2
  %5 = load i8, ptr %0, align 8
  %6 = and i8 %5, 1
  %7 = icmp eq i8 %6, 0
  %8 = load i8, ptr %1, align 8
  %9 = and i8 %8, 1
  %10 = icmp eq i8 %9, 0
  br i1 %7, label %11, label %19

11:                                               ; preds = %4
  br i1 %10, label %12, label %13

12:                                               ; preds = %11
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i64 24, i1 false), !tbaa.struct !128
  br label %30

13:                                               ; preds = %11
  %14 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 2
  %15 = load ptr, ptr %14, align 8
  %16 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 1
  %17 = load i64, ptr %16, align 8
  %18 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb1EEERS5_PKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %15, i64 noundef %17) #27
  br label %30

19:                                               ; preds = %4
  %20 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 2
  %21 = load ptr, ptr %20, align 8
  %22 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %1, i64 0, i32 2
  %23 = select i1 %10, ptr %22, ptr %21
  %24 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 1
  %25 = load i64, ptr %24, align 8
  %26 = lshr i8 %8, 1
  %27 = zext i8 %26 to i64
  %28 = select i1 %10, i64 %27, i64 %25
  %29 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb0EEERS5_PKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %23, i64 noundef %28) #27
  br label %30

30:                                               ; preds = %19, %13, %12, %2
  %31 = phi ptr [ %29, %19 ], [ %18, %13 ], [ %0, %12 ], [ %0, %2 ]
  ret ptr %31
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(36) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %0, i64 0, i32 1
  %3 = load ptr, ptr %2, align 8, !tbaa !197
  tail call void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE17__deallocate_nodeEPNS_16__hash_node_baseIPNS_11__hash_nodeIS8_PvEEEE(ptr noundef nonnull align 8 dereferenceable(36) %0, ptr noundef %3) #26
  tail call void @_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(16) %0) #26
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE17__deallocate_nodeEPNS_16__hash_node_baseIPNS_11__hash_nodeIS8_PvEEEE(ptr noundef nonnull align 8 dereferenceable(36) %0, ptr noundef %1) local_unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 !type !199 {
  br label %3

3:                                                ; preds = %6, %2
  %4 = phi ptr [ %1, %2 ], [ %7, %6 ]
  %5 = icmp eq ptr %4, null
  br i1 %5, label %9, label %6

6:                                                ; preds = %3
  %7 = load ptr, ptr %4, align 8, !tbaa !197
  %8 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %4, i64 0, i32 2, i32 0, i32 1
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %8) #26
  tail call void @_ZdlPv(ptr noundef nonnull %4) #28
  br label %3, !llvm.loop !200

9:                                                ; preds = %3
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(16) %0) unnamed_addr #4 comdat align 2 {
  tail call void @_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEE5resetB7v170000EDn(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr null) #26
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEE5resetB7v170000EDn(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 !type !201 {
  %3 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr null, ptr %0, align 8, !tbaa !16
  %4 = icmp eq ptr %3, null
  br i1 %4, label %6, label %5

5:                                                ; preds = %2
  tail call void @_ZdlPv(ptr noundef nonnull %3) #28
  br label %6

6:                                                ; preds = %5, %2
  ret void
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNSt6__ndk116__do_string_hashB7v170000IPKcEEmT_S3_(ptr noundef %0, ptr noundef %1) local_unnamed_addr #10 comdat {
  %3 = alloca %"struct.std::__ndk1::__murmur2_or_cityhash", align 1
  call void @llvm.lifetime.start.p0(i64 1, ptr nonnull %3) #24
  %4 = ptrtoint ptr %1 to i64
  %5 = ptrtoint ptr %0 to i64
  %6 = sub i64 %4, %5
  %7 = call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EEclEPKvm(ptr noundef nonnull align 1 dereferenceable(1) %3, ptr noundef %0, i64 noundef %6) #27
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %3) #24
  ret i64 %7
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EEclEPKvm(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #10 comdat align 2 !type !202 {
  %4 = icmp ult i64 %2, 33
  br i1 %4, label %5, label %11

5:                                                ; preds = %3
  %6 = icmp ult i64 %2, 17
  br i1 %6, label %7, label %9

7:                                                ; preds = %5
  %8 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE18__hash_len_0_to_16EPKcm(ptr noundef %1, i64 noundef %2) #27
  br label %99

9:                                                ; preds = %5
  %10 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE19__hash_len_17_to_32EPKcm(ptr noundef %1, i64 noundef %2) #27
  br label %99

11:                                               ; preds = %3
  %12 = icmp ult i64 %2, 65
  br i1 %12, label %13, label %15

13:                                               ; preds = %11
  %14 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE19__hash_len_33_to_64EPKcm(ptr noundef %1, i64 noundef %2) #27
  br label %99

15:                                               ; preds = %11
  %16 = getelementptr inbounds i8, ptr %1, i64 %2
  %17 = getelementptr inbounds i8, ptr %16, i64 -40
  %18 = load i64, ptr %17, align 1
  %19 = getelementptr inbounds i8, ptr %16, i64 -16
  %20 = load i64, ptr %19, align 1
  %21 = getelementptr inbounds i8, ptr %16, i64 -56
  %22 = load i64, ptr %21, align 1
  %23 = add i64 %22, %20
  %24 = getelementptr inbounds i8, ptr %16, i64 -48
  %25 = load i64, ptr %24, align 1
  %26 = add i64 %25, %2
  %27 = getelementptr inbounds i8, ptr %16, i64 -24
  %28 = load i64, ptr %27, align 1
  %29 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE13__hash_len_16Emm(i64 noundef %26, i64 noundef %28) #27
  %30 = getelementptr inbounds i8, ptr %16, i64 -64
  %31 = tail call [2 x i64] @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE29__weak_hash_len_32_with_seedsEPKcmm(ptr noundef nonnull %30, i64 noundef %2, i64 noundef %29) #27
  %32 = getelementptr inbounds i8, ptr %16, i64 -32
  %33 = add i64 %23, -5435081209227447693
  %34 = tail call [2 x i64] @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE29__weak_hash_len_32_with_seedsEPKcmm(ptr noundef nonnull %32, i64 noundef %33, i64 noundef %18) #27
  %35 = mul i64 %18, -5435081209227447693
  %36 = load i64, ptr %1, align 1
  %37 = add i64 %36, %35
  %38 = add i64 %2, -1
  %39 = and i64 %38, -64
  br label %40

40:                                               ; preds = %40, %15
  %41 = phi [2 x i64] [ %34, %15 ], [ %81, %40 ]
  %42 = phi [2 x i64] [ %31, %15 ], [ %75, %40 ]
  %43 = phi i64 [ %29, %15 ], [ %65, %40 ]
  %44 = phi i64 [ %37, %15 ], [ %72, %40 ]
  %45 = phi i64 [ %39, %15 ], [ %83, %40 ]
  %46 = phi ptr [ %1, %15 ], [ %82, %40 ]
  %47 = phi i64 [ %23, %15 ], [ %69, %40 ]
  %48 = extractvalue [2 x i64] %42, 1
  %49 = extractvalue [2 x i64] %42, 0
  %50 = extractvalue [2 x i64] %41, 0
  %51 = extractvalue [2 x i64] %41, 1
  %52 = getelementptr inbounds i8, ptr %46, i64 8
  %53 = load i64, ptr %52, align 1
  %54 = add i64 %44, %49
  %55 = add i64 %54, %47
  %56 = add i64 %55, %53
  %57 = tail call i64 @llvm.fshl.i64(i64 %56, i64 %56, i64 27)
  %58 = mul i64 %57, -5435081209227447693
  %59 = add i64 %47, %48
  %60 = getelementptr inbounds i8, ptr %46, i64 48
  %61 = load i64, ptr %60, align 1
  %62 = add i64 %59, %61
  %63 = tail call i64 @llvm.fshl.i64(i64 %62, i64 %62, i64 22)
  %64 = mul i64 %63, -5435081209227447693
  %65 = xor i64 %58, %51
  %66 = getelementptr inbounds i8, ptr %46, i64 40
  %67 = load i64, ptr %66, align 1
  %68 = add i64 %67, %49
  %69 = add i64 %68, %64
  %70 = add i64 %43, %50
  %71 = tail call i64 @llvm.fshl.i64(i64 %70, i64 %70, i64 31)
  %72 = mul i64 %71, -5435081209227447693
  %73 = mul i64 %48, -5435081209227447693
  %74 = add i64 %65, %50
  %75 = tail call [2 x i64] @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE29__weak_hash_len_32_with_seedsEPKcmm(ptr noundef nonnull %46, i64 noundef %73, i64 noundef %74) #27
  %76 = getelementptr inbounds i8, ptr %46, i64 32
  %77 = add i64 %72, %51
  %78 = getelementptr inbounds i8, ptr %46, i64 16
  %79 = load i64, ptr %78, align 1
  %80 = add i64 %69, %79
  %81 = tail call [2 x i64] @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE29__weak_hash_len_32_with_seedsEPKcmm(ptr noundef nonnull %76, i64 noundef %77, i64 noundef %80) #27
  %82 = getelementptr inbounds i8, ptr %46, i64 64
  %83 = add i64 %45, -64
  %84 = icmp eq i64 %83, 0
  br i1 %84, label %85, label %40, !llvm.loop !203

85:                                               ; preds = %40
  %86 = extractvalue [2 x i64] %75, 0
  %87 = extractvalue [2 x i64] %75, 1
  %88 = extractvalue [2 x i64] %81, 0
  %89 = extractvalue [2 x i64] %81, 1
  %90 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE13__hash_len_16Emm(i64 noundef %86, i64 noundef %88) #27
  %91 = lshr i64 %69, 47
  %92 = xor i64 %91, %69
  %93 = mul i64 %92, -5435081209227447693
  %94 = add i64 %93, %65
  %95 = add i64 %94, %90
  %96 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE13__hash_len_16Emm(i64 noundef %87, i64 noundef %89) #27
  %97 = add i64 %96, %72
  %98 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE13__hash_len_16Emm(i64 noundef %95, i64 noundef %97) #27
  br label %99

99:                                               ; preds = %85, %13, %9, %7
  %100 = phi i64 [ %8, %7 ], [ %10, %9 ], [ %14, %13 ], [ %98, %85 ]
  ret i64 %100
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE18__hash_len_0_to_16EPKcm(ptr noundef %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 {
  %3 = icmp ugt i64 %1, 8
  br i1 %3, label %4, label %18

4:                                                ; preds = %2
  %5 = load i64, ptr %0, align 1
  %6 = getelementptr inbounds i8, ptr %0, i64 %1
  %7 = getelementptr inbounds i8, ptr %6, i64 -8
  %8 = load i64, ptr %7, align 1
  %9 = add i64 %8, %1
  %10 = and i64 %1, 4294967295
  %11 = lshr i64 %9, %10
  %12 = sub i64 64, %1
  %13 = and i64 %12, 4294967295
  %14 = shl i64 %9, %13
  %15 = or i64 %14, %11
  %16 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE13__hash_len_16Emm(i64 noundef %5, i64 noundef %15) #27
  %17 = xor i64 %16, %8
  br label %53

18:                                               ; preds = %2
  %19 = icmp ugt i64 %1, 3
  br i1 %19, label %20, label %30

20:                                               ; preds = %18
  %21 = load i32, ptr %0, align 1
  %22 = getelementptr inbounds i8, ptr %0, i64 %1
  %23 = getelementptr inbounds i8, ptr %22, i64 -4
  %24 = load i32, ptr %23, align 1
  %25 = zext i32 %21 to i64
  %26 = shl nuw nsw i64 %25, 3
  %27 = add nuw nsw i64 %26, %1
  %28 = zext i32 %24 to i64
  %29 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE13__hash_len_16Emm(i64 noundef %27, i64 noundef %28) #27
  br label %53

30:                                               ; preds = %18
  %31 = icmp eq i64 %1, 0
  br i1 %31, label %53, label %32

32:                                               ; preds = %30
  %33 = load i8, ptr %0, align 1, !tbaa !12
  %34 = lshr i64 %1, 1
  %35 = getelementptr inbounds i8, ptr %0, i64 %34
  %36 = load i8, ptr %35, align 1, !tbaa !12
  %37 = add nsw i64 %1, -1
  %38 = getelementptr inbounds i8, ptr %0, i64 %37
  %39 = load i8, ptr %38, align 1, !tbaa !12
  %40 = zext i8 %33 to i64
  %41 = zext i8 %36 to i64
  %42 = shl nuw nsw i64 %41, 8
  %43 = or i64 %42, %40
  %44 = zext i8 %39 to i64
  %45 = shl nuw nsw i64 %44, 2
  %46 = add nuw nsw i64 %45, %1
  %47 = mul i64 %43, -7286425919675154353
  %48 = mul i64 %46, -3942382747735136937
  %49 = xor i64 %48, %47
  %50 = lshr i64 %49, 47
  %51 = xor i64 %50, %49
  %52 = mul i64 %51, -7286425919675154353
  br label %53

53:                                               ; preds = %32, %30, %20, %4
  %54 = phi i64 [ %17, %4 ], [ %29, %20 ], [ %52, %32 ], [ -7286425919675154353, %30 ]
  ret i64 %54
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE19__hash_len_17_to_32EPKcm(ptr noundef %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 {
  %3 = load i64, ptr %0, align 1
  %4 = mul i64 %3, -5435081209227447693
  %5 = getelementptr inbounds i8, ptr %0, i64 8
  %6 = load i64, ptr %5, align 1
  %7 = getelementptr inbounds i8, ptr %0, i64 %1
  %8 = getelementptr inbounds i8, ptr %7, i64 -8
  %9 = load i64, ptr %8, align 1
  %10 = mul i64 %9, -7286425919675154353
  %11 = getelementptr inbounds i8, ptr %7, i64 -16
  %12 = load i64, ptr %11, align 1
  %13 = mul i64 %12, -4348849565147123417
  %14 = sub i64 %4, %6
  %15 = tail call i64 @llvm.fshl.i64(i64 %14, i64 %14, i64 21)
  %16 = tail call i64 @llvm.fshl.i64(i64 %10, i64 %10, i64 34)
  %17 = add i64 %16, %15
  %18 = add i64 %17, %13
  %19 = xor i64 %6, -3942382747735136937
  %20 = tail call i64 @llvm.fshl.i64(i64 %19, i64 %19, i64 44)
  %21 = add i64 %4, %1
  %22 = add i64 %21, %20
  %23 = sub i64 %22, %10
  %24 = tail call noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE13__hash_len_16Emm(i64 noundef %18, i64 noundef %23) #27
  ret i64 %24
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE19__hash_len_33_to_64EPKcm(ptr noundef %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 {
  %3 = getelementptr inbounds i8, ptr %0, i64 24
  %4 = load i64, ptr %3, align 1
  %5 = load i64, ptr %0, align 1
  %6 = getelementptr inbounds i8, ptr %0, i64 %1
  %7 = getelementptr inbounds i8, ptr %6, i64 -16
  %8 = load i64, ptr %7, align 1
  %9 = add i64 %8, %1
  %10 = mul i64 %9, -4348849565147123417
  %11 = add i64 %10, %5
  %12 = add i64 %11, %4
  %13 = tail call i64 @llvm.fshl.i64(i64 %12, i64 %12, i64 12)
  %14 = tail call i64 @llvm.fshl.i64(i64 %11, i64 %11, i64 27)
  %15 = getelementptr inbounds i8, ptr %0, i64 8
  %16 = load i64, ptr %15, align 1
  %17 = add i64 %11, %16
  %18 = tail call i64 @llvm.fshl.i64(i64 %17, i64 %17, i64 57)
  %19 = getelementptr inbounds i8, ptr %0, i64 16
  %20 = load i64, ptr %19, align 1
  %21 = add i64 %17, %20
  %22 = tail call i64 @llvm.fshl.i64(i64 %21, i64 %21, i64 33)
  %23 = add i64 %18, %14
  %24 = add i64 %23, %13
  %25 = add i64 %24, %22
  %26 = getelementptr inbounds i8, ptr %6, i64 -32
  %27 = load i64, ptr %26, align 1
  %28 = add i64 %27, %20
  %29 = getelementptr inbounds i8, ptr %6, i64 -8
  %30 = load i64, ptr %29, align 1
  %31 = add i64 %30, %4
  %32 = add i64 %31, %28
  %33 = tail call i64 @llvm.fshl.i64(i64 %32, i64 %32, i64 12)
  %34 = tail call i64 @llvm.fshl.i64(i64 %28, i64 %28, i64 27)
  %35 = getelementptr inbounds i8, ptr %6, i64 -24
  %36 = load i64, ptr %35, align 1
  %37 = add i64 %36, %28
  %38 = tail call i64 @llvm.fshl.i64(i64 %37, i64 %37, i64 57)
  %39 = add i64 %37, %8
  %40 = add i64 %39, %31
  %41 = tail call i64 @llvm.fshl.i64(i64 %39, i64 %39, i64 33)
  %42 = add i64 %21, %4
  %43 = add i64 %42, %34
  %44 = add i64 %43, %38
  %45 = add i64 %44, %33
  %46 = add i64 %45, %41
  %47 = mul i64 %46, -7286425919675154353
  %48 = add i64 %40, %25
  %49 = mul i64 %48, -4348849565147123417
  %50 = add i64 %47, %49
  %51 = lshr i64 %50, 47
  %52 = xor i64 %51, %50
  %53 = mul i64 %52, -4348849565147123417
  %54 = add i64 %53, %25
  %55 = lshr i64 %54, 47
  %56 = xor i64 %55, %54
  %57 = mul i64 %56, -7286425919675154353
  ret i64 %57
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE13__hash_len_16Emm(i64 noundef %0, i64 noundef %1) local_unnamed_addr #19 comdat align 2 {
  %3 = xor i64 %1, %0
  %4 = mul i64 %3, -7070675565921424023
  %5 = lshr i64 %4, 47
  %6 = xor i64 %5, %1
  %7 = xor i64 %6, %4
  %8 = mul i64 %7, -7070675565921424023
  %9 = lshr i64 %8, 47
  %10 = xor i64 %9, %8
  %11 = mul i64 %10, -7070675565921424023
  ret i64 %11
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk121__murmur2_or_cityhashImLm64EE29__weak_hash_len_32_with_seedsEPKcmm(ptr noundef %0, i64 noundef %1, i64 noundef %2) local_unnamed_addr #1 comdat align 2 {
  %4 = load i64, ptr %0, align 1
  %5 = getelementptr inbounds i8, ptr %0, i64 8
  %6 = load i64, ptr %5, align 1
  %7 = getelementptr inbounds i8, ptr %0, i64 16
  %8 = load i64, ptr %7, align 1
  %9 = getelementptr inbounds i8, ptr %0, i64 24
  %10 = load i64, ptr %9, align 1
  %11 = add i64 %4, %1
  %12 = add i64 %11, %2
  %13 = add i64 %12, %10
  %14 = tail call i64 @llvm.fshl.i64(i64 %13, i64 %13, i64 43)
  %15 = add i64 %11, %6
  %16 = add i64 %15, %8
  %17 = tail call i64 @llvm.fshl.i64(i64 %16, i64 %16, i64 20)
  %18 = add i64 %16, %10
  %19 = add i64 %17, %11
  %20 = add i64 %19, %14
  %21 = insertvalue [2 x i64] poison, i64 %18, 0
  %22 = insertvalue [2 x i64] %21, i64 %20, 1
  ret [2 x i64] %22
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden i64 @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE4findImEENS_15__hash_iteratorIPNS_11__hash_nodeIS8_PvEEEERKT_(ptr noundef nonnull align 8 dereferenceable(36) %0, ptr noundef nonnull align 8 dereferenceable(8) %1) local_unnamed_addr #1 comdat align 2 !type !204 {
  %3 = load i64, ptr %1, align 8, !tbaa !114
  %4 = getelementptr inbounds i8, ptr %0, i64 8
  %5 = load i64, ptr %4, align 8, !tbaa !114
  %6 = icmp eq i64 %5, 0
  br i1 %6, label %48, label %7

7:                                                ; preds = %2
  %8 = tail call i64 @llvm.ctpop.i64(i64 %5), !range !205
  %9 = icmp ult i64 %8, 2
  br i1 %9, label %10, label %13

10:                                               ; preds = %7
  %11 = add i64 %5, -1
  %12 = and i64 %11, %3
  br label %17

13:                                               ; preds = %7
  %14 = icmp ult i64 %3, %5
  br i1 %14, label %17, label %15

15:                                               ; preds = %13
  %16 = urem i64 %3, %5
  br label %17

17:                                               ; preds = %15, %13, %10
  %18 = phi i64 [ %12, %10 ], [ %16, %15 ], [ %3, %13 ]
  %19 = load ptr, ptr %0, align 8, !tbaa !16
  %20 = getelementptr inbounds ptr, ptr %19, i64 %18
  %21 = load ptr, ptr %20, align 8, !tbaa !16
  %22 = icmp eq ptr %21, null
  br i1 %22, label %48, label %23

23:                                               ; preds = %17
  %24 = add i64 %5, -1
  br label %25

25:                                               ; preds = %47, %23
  %26 = phi ptr [ %27, %47 ], [ %21, %23 ]
  %27 = load ptr, ptr %26, align 8, !tbaa !197
  %28 = icmp eq ptr %27, null
  br i1 %28, label %48, label %29

29:                                               ; preds = %25
  %30 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %27, i64 0, i32 1
  %31 = load i64, ptr %30, align 8, !tbaa !206
  %32 = icmp eq i64 %31, %3
  br i1 %32, label %43, label %33

33:                                               ; preds = %29
  br i1 %9, label %34, label %36

34:                                               ; preds = %33
  %35 = and i64 %31, %24
  br label %40

36:                                               ; preds = %33
  %37 = icmp ult i64 %31, %5
  br i1 %37, label %40, label %38

38:                                               ; preds = %36
  %39 = urem i64 %31, %5
  br label %40

40:                                               ; preds = %38, %36, %34
  %41 = phi i64 [ %35, %34 ], [ %39, %38 ], [ %31, %36 ]
  %42 = icmp eq i64 %41, %18
  br i1 %42, label %47, label %48

43:                                               ; preds = %29
  %44 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %27, i64 0, i32 2
  %45 = load i64, ptr %44, align 8, !tbaa !114
  %46 = icmp eq i64 %45, %3
  br i1 %46, label %48, label %47

47:                                               ; preds = %43, %40
  br label %25, !llvm.loop !210

48:                                               ; preds = %43, %40, %25, %17, %2
  %49 = phi ptr [ null, %17 ], [ null, %2 ], [ null, %25 ], [ null, %40 ], [ %27, %43 ]
  %50 = ptrtoint ptr %49 to i64
  ret i64 %50
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNSt6__ndk15applyB7v170000IZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES7_RmEEEvPNS5_9AssemblerER7KModErrRKNS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENSH_ISJ_EEEENS1_12NeedReturnX0EDpOT_ENKUlPKcE_clEST_EUlSR_E_RNS_5tupleIJS6_S6_mEEEEEDcOT_OT0_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %1) local_unnamed_addr #10 comdat {
  %3 = tail call noundef i64 @_ZNSt6__ndk118__apply_tuple_implB7v170000IZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES7_RmEEEvPNS5_9AssemblerER7KModErrRKNS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENSH_ISJ_EEEENS1_12NeedReturnX0EDpOT_ENKUlPKcE_clEST_EUlSR_E_RNS_5tupleIJS6_S6_mEEEJLm0ELm1ELm2EEEEDcOT_OT0_NS_15__tuple_indicesIJXspT1_EEEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %1, i8 undef) #27
  ret i64 %3
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNSt6__ndk118__apply_tuple_implB7v170000IZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES7_RmEEEvPNS5_9AssemblerER7KModErrRKNS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENSH_ISJ_EEEENS1_12NeedReturnX0EDpOT_ENKUlPKcE_clEST_EUlSR_E_RNS_5tupleIJS6_S6_mEEEJLm0ELm1ELm2EEEEDcOT_OT0_NS_15__tuple_indicesIJXspT1_EEEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %1, i8 %2) local_unnamed_addr #10 comdat {
  %4 = getelementptr inbounds i8, ptr %1, i64 16
  %5 = getelementptr inbounds i8, ptr %1, i64 32
  %6 = tail call noundef i64 @_ZZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ENKUlPKcE_clESS_ENKUlSQ_E_clIJS5_S5_S6_EEEDaSQ_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %4, ptr noundef nonnull align 8 dereferenceable(8) %5) #27
  ret i64 %6
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ENKUlPKcE_clESS_ENKUlSQ_E_clIJS5_S5_S6_EEEDaSQ_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 8 dereferenceable(8) %3) local_unnamed_addr #10 comdat align 2 !type !211 {
  %5 = load ptr, ptr %0, align 8, !tbaa !212
  %6 = load ptr, ptr %5, align 8, !tbaa !16
  %7 = getelementptr inbounds %class.anon.93, ptr %0, i64 0, i32 1
  %8 = load ptr, ptr %7, align 8, !tbaa !214
  %9 = load ptr, ptr %8, align 8, !tbaa !16
  %10 = getelementptr inbounds %class.anon.93, ptr %0, i64 0, i32 2
  %11 = load ptr, ptr %10, align 8, !tbaa !215
  %12 = load i8, ptr %11, align 1, !tbaa !178, !range !82, !noundef !83
  %13 = icmp ne i8 %12, 0
  %14 = tail call noundef i64 @_ZN26Aarch64KernelSymCallHelper12callNameAutoIJRN6asmjit5v1_163a643GpXES5_RmELi0EEE7KModErrPNS3_9AssemblerEPKcNS_12NeedReturnX0EDpOT_(ptr noundef %6, ptr noundef %9, i1 noundef %13, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 8 dereferenceable(8) %3) #27
  ret i64 %14
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef i64 @_ZN26Aarch64KernelSymCallHelper12callNameAutoIJRN6asmjit5v1_163a643GpXES5_RmELi0EEE7KModErrPNS3_9AssemblerEPKcNS_12NeedReturnX0EDpOT_(ptr noundef %0, ptr noundef %1, i1 noundef %2, ptr noundef nonnull align 4 dereferenceable(16) %3, ptr noundef nonnull align 4 dereferenceable(16) %4, ptr noundef nonnull align 8 dereferenceable(8) %5) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %7 = alloca %struct.Arm64Arg, align 8
  %8 = alloca %struct.Arm64Arg, align 8
  %9 = alloca %struct.Arm64Arg, align 8
  %10 = alloca %"class.std::__ndk1::vector.94", align 8
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %10) #24
  tail call void @llvm.experimental.noalias.scope.decl(metadata !216)
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %10, i8 0, i64 24, i1 false), !alias.scope !216
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE7reserveEm(ptr noundef nonnull align 8 dereferenceable(24) %10, i64 noundef 3) #27
          to label %11 unwind label %18

11:                                               ; preds = %6
  call void @llvm.lifetime.start.p0(i64 56, ptr nonnull %7) #24, !noalias !216
  call void @_ZN8Arm64Arg1XERKN6asmjit5v1_163a643GpXE(ptr nonnull sret(%struct.Arm64Arg) align 8 %7, ptr noundef nonnull align 4 dereferenceable(16) %3) #27
  %12 = invoke noundef nonnull align 8 dereferenceable(52) ptr @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJS1_EEERS1_DpOT_(ptr noundef nonnull align 8 dereferenceable(24) %10, ptr noundef nonnull align 8 dereferenceable(52) %7) #27
          to label %13 unwind label %20

13:                                               ; preds = %11
  call void @llvm.lifetime.start.p0(i64 56, ptr nonnull %8) #24, !noalias !216
  call void @_ZN8Arm64Arg1XERKN6asmjit5v1_163a643GpXE(ptr nonnull sret(%struct.Arm64Arg) align 8 %8, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
  %14 = invoke noundef nonnull align 8 dereferenceable(52) ptr @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJS1_EEERS1_DpOT_(ptr noundef nonnull align 8 dereferenceable(24) %10, ptr noundef nonnull align 8 dereferenceable(52) %8) #27
          to label %15 unwind label %22

15:                                               ; preds = %13
  call void @llvm.lifetime.start.p0(i64 56, ptr nonnull %9) #24, !noalias !216
  %16 = load i64, ptr %5, align 8, !tbaa !114, !noalias !216
  call void @_ZN8Arm64Arg3U64Em(ptr nonnull sret(%struct.Arm64Arg) align 8 %9, i64 noundef %16) #27
  %17 = invoke noundef nonnull align 8 dereferenceable(52) ptr @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJS1_EEERS1_DpOT_(ptr noundef nonnull align 8 dereferenceable(24) %10, ptr noundef nonnull align 8 dereferenceable(52) %9) #27
          to label %34 unwind label %24

18:                                               ; preds = %6
  %19 = landingpad { ptr, i32 }
          cleanup
  br label %32

20:                                               ; preds = %11
  %21 = landingpad { ptr, i32 }
          cleanup
  br label %28

22:                                               ; preds = %13
  %23 = landingpad { ptr, i32 }
          cleanup
  br label %26

24:                                               ; preds = %15
  %25 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %9) #24, !noalias !216
  br label %26

26:                                               ; preds = %24, %22
  %27 = phi { ptr, i32 } [ %25, %24 ], [ %23, %22 ]
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %8) #24, !noalias !216
  br label %28

28:                                               ; preds = %26, %20
  %29 = phi { ptr, i32 } [ %27, %26 ], [ %21, %20 ]
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %7) #24, !noalias !216
  br label %32

30:                                               ; preds = %37, %32
  %31 = phi { ptr, i32 } [ %33, %32 ], [ %38, %37 ]
  resume { ptr, i32 } %31

32:                                               ; preds = %28, %18
  %33 = phi { ptr, i32 } [ %29, %28 ], [ %19, %18 ]
  call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %10) #26
  br label %30

34:                                               ; preds = %15
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %9) #24, !noalias !216
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %8) #24, !noalias !216
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %7) #24, !noalias !216
  %35 = invoke noundef i64 @_ZN26Aarch64KernelSymCallHelper12callNameAutoEPN6asmjit5v1_163a649AssemblerEPKcNS_12NeedReturnX0ERKNSt6__ndk16vectorI8Arm64ArgNS8_9allocatorISA_EEEE(ptr noundef %0, ptr noundef %1, i1 noundef %2, ptr noundef nonnull align 8 dereferenceable(24) %10) #27
          to label %36 unwind label %37

36:                                               ; preds = %34
  call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %10) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %10) #24
  ret i64 %35

37:                                               ; preds = %34
  %38 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %10) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %10) #24
  br label %30
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef i64 @_ZN26Aarch64KernelSymCallHelper12callNameAutoEPN6asmjit5v1_163a649AssemblerEPKcNS_12NeedReturnX0ERKNSt6__ndk16vectorI8Arm64ArgNS8_9allocatorISA_EEEE(ptr noundef %0, ptr noundef %1, i1 noundef %2, ptr noundef nonnull align 8 dereferenceable(24) %3) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %5 = alloca i32, align 4
  %6 = alloca %"class.std::__ndk1::set", align 8
  %7 = alloca %"class.std::__ndk1::set", align 8
  %8 = alloca %"class.std::__ndk1::set", align 8
  %9 = alloca %class.RegProtectGuard, align 8
  %10 = alloca %"class.std::__ndk1::vector.94", align 8
  %11 = alloca %class.IdleRegPool, align 8
  %12 = alloca %"class.std::__ndk1::vector.94", align 8
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %6) #24
  call void @_ZN26Aarch64KernelSymCallHelper14makeProtectSetENS_19AAPCS64_ProtectRuleE(ptr nonnull sret(%"class.std::__ndk1::set") align 8 %6, i32 noundef 0) #27
  br i1 %2, label %13, label %37

13:                                               ; preds = %4
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %7) #24
  invoke void @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEEC2B7v170000ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %8, ptr noundef nonnull align 8 dereferenceable(24) %6) #27
          to label %14 unwind label %31

14:                                               ; preds = %13
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %5) #24, !noalias !219
  store i32 0, ptr %5, align 4, !tbaa !46, !noalias !219
  %15 = invoke noundef i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE14__erase_uniqueIjEEmRKT_(ptr noundef nonnull align 8 dereferenceable(24) %8, ptr noundef nonnull align 4 dereferenceable(4) %5) #27
          to label %16 unwind label %33

16:                                               ; preds = %14
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %5) #24, !noalias !219
  %17 = load ptr, ptr %8, align 8, !tbaa !16
  store ptr %17, ptr %7, align 8, !tbaa !222
  %18 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %7, i64 0, i32 1
  %19 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %8, i64 0, i32 1
  %20 = load i64, ptr %19, align 8
  store i64 %20, ptr %18, align 8
  %21 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %7, i64 0, i32 2
  %22 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %8, i64 0, i32 2
  %23 = load i64, ptr %22, align 8
  store i64 %23, ptr %21, align 8
  %24 = icmp eq i64 %23, 0
  br i1 %24, label %25, label %26

25:                                               ; preds = %16
  store ptr %18, ptr %7, align 8, !tbaa !16
  br label %29

26:                                               ; preds = %16
  %27 = inttoptr i64 %20 to ptr
  %28 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %27, i64 0, i32 2
  store ptr %18, ptr %28, align 8, !tbaa !229
  store ptr %19, ptr %8, align 8, !tbaa !16
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %19, i8 0, i64 16, i1 false)
  br label %29

29:                                               ; preds = %26, %25
  %30 = call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEaSEOS5_(ptr noundef nonnull align 8 dereferenceable(24) %6, ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %8) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #24
  br label %37

31:                                               ; preds = %13
  %32 = landingpad { ptr, i32 }
          cleanup
  br label %35

33:                                               ; preds = %14
  %34 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %8) #26
  br label %35

35:                                               ; preds = %33, %31
  %36 = phi { ptr, i32 } [ %34, %33 ], [ %32, %31 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #24
  br label %63

37:                                               ; preds = %29, %4
  call void @llvm.lifetime.start.p0(i64 72, ptr nonnull %9) #24
  invoke void @_ZN15RegProtectGuardC2EPN6asmjit5v1_163a649AssemblerERKNSt6__ndk13setIjNS5_4lessIjEENS5_9allocatorIjEEEE(ptr noundef nonnull align 8 dereferenceable(68) %9, ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(24) %6) #27
          to label %38 unwind label %45

38:                                               ; preds = %37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %10) #24
  invoke void @_ZN26Aarch64KernelSymCallHelper19makeDirtyArgsX0ToX7ERKNSt6__ndk16vectorI8Arm64ArgNS0_9allocatorIS2_EEEE(ptr nonnull sret(%"class.std::__ndk1::vector.94") align 8 %10, ptr noundef nonnull align 8 dereferenceable(24) %3) #27
          to label %39 unwind label %47

39:                                               ; preds = %38
  call void @llvm.lifetime.start.p0(i64 48, ptr nonnull %11) #24
  invoke void @_ZN11IdleRegPool11makeFromVecERKNSt6__ndk16vectorI8Arm64ArgNS0_9allocatorIS2_EEEE(ptr nonnull sret(%class.IdleRegPool) align 8 %11, ptr noundef nonnull align 8 dereferenceable(24) %10) #27
          to label %40 unwind label %49

40:                                               ; preds = %39
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %12) #24
  invoke void @_ZN26Aarch64KernelSymCallHelper17acquireNewArgRegsER11IdleRegPoolRKNSt6__ndk16vectorI8Arm64ArgNS2_9allocatorIS4_EEEE(ptr nonnull sret(%"class.std::__ndk1::vector.94") align 8 %12, ptr noundef nonnull align 8 dereferenceable(48) %11, ptr noundef nonnull align 8 dereferenceable(24) %3) #27
          to label %41 unwind label %51

41:                                               ; preds = %40
  invoke void @_ZN26Aarch64KernelSymCallHelper21emitMovesFromOldToNewEPN6asmjit5v1_163a649AssemblerERKNSt6__ndk16vectorI8Arm64ArgNS5_9allocatorIS7_EEEESC_(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(24) %12, ptr noundef nonnull align 8 dereferenceable(24) %3) #27
          to label %42 unwind label %53

42:                                               ; preds = %41
  %43 = invoke noundef i64 @_ZN26Aarch64KernelSymCallHelper16call_by_name_rawEPN6asmjit5v1_163a649AssemblerEPKcRKNSt6__ndk16vectorI8Arm64ArgNS7_9allocatorIS9_EEEE(ptr noundef %0, ptr noundef %1, ptr noundef nonnull align 8 dereferenceable(24) %12) #27
          to label %44 unwind label %53

44:                                               ; preds = %42
  call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %12) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %12) #24
  call void @_ZN11IdleRegPoolD2Ev(ptr noundef nonnull align 8 dereferenceable(48) %11) #26
  call void @llvm.lifetime.end.p0(i64 48, ptr nonnull %11) #24
  call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %10) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %10) #24
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %9) #26
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %9) #24
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %6) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %6) #24
  ret i64 %43

45:                                               ; preds = %37
  %46 = landingpad { ptr, i32 }
          cleanup
  br label %61

47:                                               ; preds = %38
  %48 = landingpad { ptr, i32 }
          cleanup
  br label %59

49:                                               ; preds = %39
  %50 = landingpad { ptr, i32 }
          cleanup
  br label %57

51:                                               ; preds = %40
  %52 = landingpad { ptr, i32 }
          cleanup
  br label %55

53:                                               ; preds = %42, %41
  %54 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %12) #26
  br label %55

55:                                               ; preds = %53, %51
  %56 = phi { ptr, i32 } [ %54, %53 ], [ %52, %51 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %12) #24
  call void @_ZN11IdleRegPoolD2Ev(ptr noundef nonnull align 8 dereferenceable(48) %11) #26
  br label %57

57:                                               ; preds = %55, %49
  %58 = phi { ptr, i32 } [ %56, %55 ], [ %50, %49 ]
  call void @llvm.lifetime.end.p0(i64 48, ptr nonnull %11) #24
  call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %10) #26
  br label %59

59:                                               ; preds = %57, %47
  %60 = phi { ptr, i32 } [ %58, %57 ], [ %48, %47 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %10) #24
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %9) #26
  br label %61

61:                                               ; preds = %59, %45
  %62 = phi { ptr, i32 } [ %60, %59 ], [ %46, %45 ]
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %9) #24
  br label %63

63:                                               ; preds = %61, %35
  %64 = phi { ptr, i32 } [ %62, %61 ], [ %36, %35 ]
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %6) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %6) #24
  resume { ptr, i32 } %64
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::vector<Arm64Arg>::__destroy_vector", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #24
  store ptr %0, ptr %2, align 8, !tbaa !16
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %2) #27
          to label %3 unwind label %4

3:                                                ; preds = %1
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #24
  ret void

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          catch ptr null
  %6 = extractvalue { ptr, i32 } %5, 0
  call void @__clang_call_terminate(ptr %6) #29
  unreachable
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE7reserveEm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 !type !231 {
  %3 = alloca %"struct.std::__ndk1::__split_buffer", align 8
  %4 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 2
  %5 = load ptr, ptr %4, align 8, !tbaa !16
  %6 = load ptr, ptr %0, align 8, !tbaa !232
  %7 = ptrtoint ptr %5 to i64
  %8 = ptrtoint ptr %6 to i64
  %9 = sub i64 %7, %8
  %10 = sdiv exact i64 %9, 56
  %11 = icmp ult i64 %10, %1
  br i1 %11, label %12, label %24

12:                                               ; preds = %2
  %13 = icmp ugt i64 %1, 329406144173384850
  br i1 %13, label %14, label %15

14:                                               ; preds = %12
  tail call void @_ZNKSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

15:                                               ; preds = %12
  call void @llvm.lifetime.start.p0(i64 40, ptr nonnull %3) #24
  %16 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 1
  %17 = load ptr, ptr %16, align 8, !tbaa !236
  %18 = ptrtoint ptr %17 to i64
  %19 = sub i64 %18, %8
  %20 = sdiv exact i64 %19, 56
  call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEEC2EmmS4_(ptr noundef nonnull align 8 dereferenceable(40) %3, i64 noundef %1, i64 noundef %20, ptr noundef nonnull align 1 dereferenceable(1) %4) #27
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE26__swap_out_circular_bufferERNS_14__split_bufferIS1_RS3_EE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %3) #27
          to label %21 unwind label %22

21:                                               ; preds = %15
  call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  br label %24

22:                                               ; preds = %15
  %23 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  resume { ptr, i32 } %23

24:                                               ; preds = %21, %2
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(52) ptr @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJS1_EEERS1_DpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %1) local_unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 !type !237 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !236
  %5 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 2
  %6 = load ptr, ptr %5, align 8, !tbaa !16
  %7 = icmp ult ptr %4, %6
  br i1 %7, label %8, label %17

8:                                                ; preds = %2
  %9 = load i8, ptr %1, align 8, !tbaa !238
  store i8 %9, ptr %4, align 8, !tbaa !238
  %10 = getelementptr inbounds %struct.Arm64Arg, ptr %4, i64 0, i32 2
  %11 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 2
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %10, ptr noundef nonnull align 4 dereferenceable(16) %11, i64 16, i1 false)
  %12 = getelementptr inbounds %struct.Arm64Arg, ptr %4, i64 0, i32 3
  %13 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 3
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %12, ptr noundef nonnull align 4 dereferenceable(16) %13, i64 16, i1 false)
  %14 = getelementptr inbounds %struct.Arm64Arg, ptr %4, i64 0, i32 5
  %15 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 5
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(12) %14, ptr noundef nonnull align 8 dereferenceable(12) %15, i64 12, i1 false)
  %16 = getelementptr inbounds %struct.Arm64Arg, ptr %4, i64 1
  store ptr %16, ptr %3, align 8, !tbaa !236
  br label %19

17:                                               ; preds = %2
  tail call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE24__emplace_back_slow_pathIJS1_EEEvDpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %1) #27
  %18 = load ptr, ptr %3, align 8, !tbaa !236
  br label %19

19:                                               ; preds = %17, %8
  %20 = phi ptr [ %18, %17 ], [ %16, %8 ]
  %21 = getelementptr inbounds %struct.Arm64Arg, ptr %20, i64 -1
  ret ptr %21
}

; Function Attrs: minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #12 comdat align 2 !type !242 {
  tail call void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef nonnull @.str.17) #31
  unreachable
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEEC2EmmS4_(ptr noundef nonnull align 8 dereferenceable(40) %0, i64 noundef %1, i64 noundef %2, ptr noundef nonnull align 1 dereferenceable(1) %3) unnamed_addr #1 comdat align 2 {
  %5 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %0, i64 0, i32 3
  store ptr null, ptr %5, align 8, !tbaa !243
  %6 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %0, i64 0, i32 3, i32 1
  store ptr %3, ptr %6, align 8, !tbaa !16
  %7 = icmp eq i64 %1, 0
  br i1 %7, label %13, label %8

8:                                                ; preds = %4
  %9 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorI8Arm64ArgEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS6_m(ptr noundef nonnull align 1 dereferenceable(1) %3, i64 noundef %1) #27
  %10 = extractvalue [2 x i64] %9, 0
  %11 = inttoptr i64 %10 to ptr
  %12 = extractvalue [2 x i64] %9, 1
  br label %13

13:                                               ; preds = %8, %4
  %14 = phi ptr [ %11, %8 ], [ null, %4 ]
  %15 = phi i64 [ %12, %8 ], [ 0, %4 ]
  store ptr %14, ptr %0, align 8, !tbaa !244
  %16 = getelementptr inbounds %struct.Arm64Arg, ptr %14, i64 %2
  %17 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %0, i64 0, i32 2
  store ptr %16, ptr %17, align 8, !tbaa !248
  %18 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %0, i64 0, i32 1
  store ptr %16, ptr %18, align 8, !tbaa !249
  %19 = getelementptr inbounds %struct.Arm64Arg, ptr %14, i64 %15
  store ptr %19, ptr %5, align 8, !tbaa !16
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE26__swap_out_circular_bufferERNS_14__split_bufferIS1_RS3_EE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %1) local_unnamed_addr #1 comdat align 2 !type !250 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 2
  %4 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 1
  %5 = load ptr, ptr %4, align 8, !tbaa !236
  %6 = load ptr, ptr %0, align 8, !tbaa !232
  %7 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %1, i64 0, i32 1
  %8 = load ptr, ptr %7, align 8, !tbaa !249
  %9 = ptrtoint ptr %5 to i64
  %10 = insertvalue [2 x i64] poison, i64 %9, 0
  %11 = insertvalue [2 x i64] %10, i64 %9, 1
  %12 = ptrtoint ptr %6 to i64
  %13 = insertvalue [2 x i64] poison, i64 %12, 0
  %14 = insertvalue [2 x i64] %13, i64 %12, 1
  %15 = ptrtoint ptr %8 to i64
  %16 = insertvalue [2 x i64] poison, i64 %15, 0
  %17 = insertvalue [2 x i64] %16, i64 %15, 1
  %18 = tail call [2 x i64] @_ZNSt6__ndk142__uninitialized_allocator_move_if_noexceptB7v170000INS_9allocatorI8Arm64ArgEENS_16reverse_iteratorIPS2_EES6_S6_EET2_RT_T0_T1_S7_(ptr noundef nonnull align 1 dereferenceable(1) %3, [2 x i64] %11, [2 x i64] %14, [2 x i64] %17) #27
  %19 = extractvalue [2 x i64] %18, 1
  %20 = inttoptr i64 %19 to ptr
  store ptr %20, ptr %7, align 8, !tbaa !249
  %21 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr %20, ptr %0, align 8, !tbaa !16
  store ptr %21, ptr %7, align 8, !tbaa !16
  %22 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %1, i64 0, i32 2
  %23 = load ptr, ptr %4, align 8, !tbaa !16
  %24 = load ptr, ptr %22, align 8, !tbaa !16
  store ptr %24, ptr %4, align 8, !tbaa !16
  store ptr %23, ptr %22, align 8, !tbaa !16
  %25 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %1, i64 0, i32 3
  %26 = load ptr, ptr %3, align 8, !tbaa !16
  %27 = load ptr, ptr %25, align 8, !tbaa !16
  store ptr %27, ptr %3, align 8, !tbaa !16
  store ptr %26, ptr %25, align 8, !tbaa !16
  %28 = load ptr, ptr %7, align 8, !tbaa !249
  store ptr %28, ptr %1, align 8, !tbaa !244
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  tail call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEE5clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) #26
  %2 = load ptr, ptr %0, align 8, !tbaa !244
  %3 = icmp eq ptr %2, null
  br i1 %3, label %5, label %4

4:                                                ; preds = %1
  tail call void @_ZdlPv(ptr noundef nonnull %2) #28
  br label %5

5:                                                ; preds = %4, %1
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorI8Arm64ArgEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS6_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat {
  %3 = tail call noundef ptr @_ZNSt6__ndk19allocatorI8Arm64ArgE8allocateB7v170000Em(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) #27
  %4 = ptrtoint ptr %3 to i64
  %5 = insertvalue [2 x i64] poison, i64 %4, 0
  %6 = insertvalue [2 x i64] %5, i64 %1, 1
  ret [2 x i64] %6
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk19allocatorI8Arm64ArgE8allocateB7v170000Em(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 !type !251 {
  %3 = icmp ugt i64 %1, 329406144173384850
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZSt28__throw_bad_array_new_lengthB7v170000v() #31
  unreachable

5:                                                ; preds = %2
  %6 = mul nuw i64 %1, 56
  %7 = tail call noalias noundef nonnull ptr @_Znwm(i64 noundef %6) #30
  ret ptr %7
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk142__uninitialized_allocator_move_if_noexceptB7v170000INS_9allocatorI8Arm64ArgEENS_16reverse_iteratorIPS2_EES6_S6_EET2_RT_T0_T1_S7_(ptr noundef nonnull align 1 dereferenceable(1) %0, [2 x i64] %1, [2 x i64] %2, [2 x i64] %3) local_unnamed_addr #1 comdat personality ptr @__gxx_personality_v0 {
  %5 = extractvalue [2 x i64] %1, 1
  %6 = inttoptr i64 %5 to ptr
  %7 = extractvalue [2 x i64] %2, 1
  %8 = inttoptr i64 %7 to ptr
  %9 = extractvalue [2 x i64] %3, 1
  %10 = inttoptr i64 %9 to ptr
  br label %11

11:                                               ; preds = %15, %4
  %12 = phi ptr [ %10, %4 ], [ %16, %15 ]
  %13 = phi ptr [ %6, %4 ], [ %17, %15 ]
  %14 = icmp eq ptr %13, %8
  br i1 %14, label %25, label %15

15:                                               ; preds = %11
  %16 = getelementptr inbounds %struct.Arm64Arg, ptr %12, i64 -1
  %17 = getelementptr inbounds %struct.Arm64Arg, ptr %13, i64 -1
  %18 = load i8, ptr %17, align 8, !tbaa !238
  store i8 %18, ptr %16, align 8, !tbaa !238
  %19 = getelementptr %struct.Arm64Arg, ptr %12, i64 -1, i32 2
  %20 = getelementptr %struct.Arm64Arg, ptr %13, i64 -1, i32 2
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %19, ptr noundef nonnull align 4 dereferenceable(16) %20, i64 16, i1 false)
  %21 = getelementptr %struct.Arm64Arg, ptr %12, i64 -1, i32 3
  %22 = getelementptr %struct.Arm64Arg, ptr %13, i64 -1, i32 3
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %21, ptr noundef nonnull align 4 dereferenceable(16) %22, i64 16, i1 false)
  %23 = getelementptr %struct.Arm64Arg, ptr %12, i64 -1, i32 5
  %24 = getelementptr %struct.Arm64Arg, ptr %13, i64 -1, i32 5
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(12) %23, ptr noundef nonnull align 8 dereferenceable(12) %24, i64 12, i1 false)
  br label %11, !llvm.loop !252

25:                                               ; preds = %11
  %26 = ptrtoint ptr %12 to i64
  %27 = insertvalue [2 x i64] %3, i64 %26, 1
  ret [2 x i64] %27
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEE5clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) local_unnamed_addr #19 comdat align 2 !type !253 {
  %2 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %0, i64 0, i32 1
  %3 = load ptr, ptr %2, align 8, !tbaa !249
  tail call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEE17__destruct_at_endB7v170000EPS1_(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef %3) #26
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEE17__destruct_at_endB7v170000EPS1_(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 !type !254 {
  %3 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %0, i64 0, i32 2
  %4 = load ptr, ptr %3, align 8, !tbaa !248
  br label %5

5:                                                ; preds = %8, %2
  %6 = phi ptr [ %9, %8 ], [ %4, %2 ]
  %7 = icmp eq ptr %6, %1
  br i1 %7, label %10, label %8

8:                                                ; preds = %5
  %9 = getelementptr inbounds %struct.Arm64Arg, ptr %6, i64 -1
  store ptr %9, ptr %3, align 8, !tbaa !248
  br label %5

10:                                               ; preds = %5
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE24__emplace_back_slow_pathIJS1_EEEvDpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %1) local_unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 !type !255 {
  %3 = alloca %"struct.std::__ndk1::__split_buffer", align 8
  %4 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 2
  call void @llvm.lifetime.start.p0(i64 40, ptr nonnull %3) #24
  %5 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 1
  %6 = load ptr, ptr %5, align 8, !tbaa !236
  %7 = load ptr, ptr %0, align 8, !tbaa !232
  %8 = ptrtoint ptr %6 to i64
  %9 = ptrtoint ptr %7 to i64
  %10 = sub i64 %8, %9
  %11 = sdiv exact i64 %10, 56
  %12 = add nsw i64 %11, 1
  %13 = tail call noundef i64 @_ZNKSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE11__recommendB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %12) #27
  %14 = load ptr, ptr %5, align 8, !tbaa !236
  %15 = load ptr, ptr %0, align 8, !tbaa !232
  %16 = ptrtoint ptr %14 to i64
  %17 = ptrtoint ptr %15 to i64
  %18 = sub i64 %16, %17
  %19 = sdiv exact i64 %18, 56
  call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEEC2EmmS4_(ptr noundef nonnull align 8 dereferenceable(40) %3, i64 noundef %13, i64 noundef %19, ptr noundef nonnull align 1 dereferenceable(1) %4) #27
  %20 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %3, i64 0, i32 2
  %21 = load ptr, ptr %20, align 8, !tbaa !248, !nonnull !83
  %22 = load i8, ptr %1, align 8, !tbaa !238
  store i8 %22, ptr %21, align 8, !tbaa !238
  %23 = getelementptr inbounds %struct.Arm64Arg, ptr %21, i64 0, i32 2
  %24 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 2
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %23, ptr noundef nonnull align 4 dereferenceable(16) %24, i64 16, i1 false)
  %25 = getelementptr inbounds %struct.Arm64Arg, ptr %21, i64 0, i32 3
  %26 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 3
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %25, ptr noundef nonnull align 4 dereferenceable(16) %26, i64 16, i1 false)
  %27 = getelementptr inbounds %struct.Arm64Arg, ptr %21, i64 0, i32 5
  %28 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 5
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(12) %27, ptr noundef nonnull align 8 dereferenceable(12) %28, i64 12, i1 false)
  %29 = load ptr, ptr %20, align 8, !tbaa !248
  %30 = getelementptr inbounds %struct.Arm64Arg, ptr %29, i64 1
  store ptr %30, ptr %20, align 8, !tbaa !248
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE26__swap_out_circular_bufferERNS_14__split_bufferIS1_RS3_EE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %3) #27
          to label %31 unwind label %32

31:                                               ; preds = %2
  call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  ret void

32:                                               ; preds = %2
  %33 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  resume { ptr, i32 } %33
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNKSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE11__recommendB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #10 comdat align 2 personality ptr @__gxx_personality_v0 !type !256 {
  %3 = icmp ugt i64 %1, 329406144173384850
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZNKSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

5:                                                ; preds = %2
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 2
  %7 = load ptr, ptr %6, align 8, !tbaa !16
  %8 = load ptr, ptr %0, align 8, !tbaa !232
  %9 = ptrtoint ptr %7 to i64
  %10 = ptrtoint ptr %8 to i64
  %11 = sub i64 %9, %10
  %12 = sdiv exact i64 %11, 56
  %13 = icmp ult i64 %12, 164703072086692425
  %14 = shl nuw nsw i64 %12, 1
  %15 = tail call i64 @llvm.umax.i64(i64 %14, i64 %1)
  %16 = select i1 %13, i64 %15, i64 329406144173384850
  ret i64 %16
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZN8Arm64Arg1XERKN6asmjit5v1_163a643GpXE(ptr noalias sret(%struct.Arm64Arg) align 8 %0, ptr noundef nonnull align 4 dereferenceable(16) %1) local_unnamed_addr #19 comdat align 2 {
  tail call void @_ZN8Arm64ArgC2Ev(ptr noundef nonnull align 8 dereferenceable(52) %0) #26
  store i8 0, ptr %0, align 8, !tbaa !238
  %3 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 2
  %4 = load i32, ptr %1, align 4, !tbaa !257
  store i32 %4, ptr %3, align 4, !tbaa !257
  %5 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %1, i64 0, i32 1
  %6 = load i32, ptr %5, align 4, !tbaa !44
  %7 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 2, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  store i32 %6, ptr %7, align 8, !tbaa !44
  %8 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %1, i64 0, i32 2
  %9 = load i32, ptr %8, align 4, !tbaa !46
  %10 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 2, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2
  store i32 %9, ptr %10, align 4, !tbaa !46
  %11 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %1, i64 0, i32 2, i64 1
  %12 = load i32, ptr %11, align 4, !tbaa !46
  %13 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 2, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2, i64 1
  store i32 %12, ptr %13, align 8, !tbaa !46
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZN8Arm64ArgC2Ev(ptr noundef nonnull align 8 dereferenceable(52) %0) unnamed_addr #2 comdat align 2 {
  store i8 0, ptr %0, align 8, !tbaa !238
  %2 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 2
  store i32 134217785, ptr %2, align 4, !tbaa !39
  %3 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 2, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  store i32 255, ptr %3, align 8, !tbaa !44
  %4 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 2, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2
  store i32 0, ptr %4, align 4, !tbaa !46
  %5 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 2, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2, i64 1
  store i32 0, ptr %5, align 8, !tbaa !46
  %6 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 3
  store i32 67108913, ptr %6, align 4, !tbaa !39
  %7 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 3, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  store i32 255, ptr %7, align 8, !tbaa !44
  %8 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 3, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2
  store i32 0, ptr %8, align 4, !tbaa !46
  %9 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 3, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2, i64 1
  store i32 0, ptr %9, align 8, !tbaa !46
  %10 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 5
  store i64 0, ptr %10, align 8, !tbaa !258
  %11 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 6
  store i32 0, ptr %11, align 8, !tbaa !259
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZN8Arm64Arg3U64Em(ptr noalias sret(%struct.Arm64Arg) align 8 %0, i64 noundef %1) local_unnamed_addr #19 comdat align 2 {
  tail call void @_ZN8Arm64ArgC2Ev(ptr noundef nonnull align 8 dereferenceable(52) %0) #26
  store i8 2, ptr %0, align 8, !tbaa !238
  %3 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 5
  store i64 %1, ptr %3, align 8, !tbaa !258
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN26Aarch64KernelSymCallHelper14makeProtectSetENS_19AAPCS64_ProtectRuleE(ptr noalias sret(%"class.std::__ndk1::set") align 8 %0, i32 noundef %1) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = alloca i32, align 4
  %4 = alloca i32, align 4
  %5 = alloca i32, align 4
  %6 = alloca %"class.std::__ndk1::set", align 8
  %7 = alloca %"class.std::__ndk1::set", align 8
  %8 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %8, i8 0, i64 16, i1 false)
  store ptr %8, ptr %0, align 8, !tbaa !16
  switch i32 %1, label %54 [
    i32 0, label %9
    i32 1, label %21
    i32 2, label %37
  ]

9:                                                ; preds = %2
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %3) #24
  br label %10

10:                                               ; preds = %16, %9
  %11 = phi i32 [ 0, %9 ], [ %18, %16 ]
  store i32 %11, ptr %3, align 4, !tbaa !46
  %12 = icmp ult i32 %11, 18
  br i1 %12, label %14, label %13

13:                                               ; preds = %10
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %3) #24
  br label %54

14:                                               ; preds = %10
  %15 = invoke [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000ERKj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %3) #27
          to label %16 unwind label %19

16:                                               ; preds = %14
  %17 = load i32, ptr %3, align 4, !tbaa !46
  %18 = add i32 %17, 1
  br label %10, !llvm.loop !260

19:                                               ; preds = %14
  %20 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %3) #24
  br label %55

21:                                               ; preds = %2
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %4) #24
  br label %22

22:                                               ; preds = %29, %21
  %23 = phi i32 [ 19, %21 ], [ %31, %29 ]
  store i32 %23, ptr %4, align 4, !tbaa !46
  %24 = icmp ult i32 %23, 29
  br i1 %24, label %27, label %25

25:                                               ; preds = %22
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %4) #24
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %5) #24
  store i32 29, ptr %5, align 4, !tbaa !46
  %26 = invoke [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000EOj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %5) #27
          to label %34 unwind label %35

27:                                               ; preds = %22
  %28 = invoke [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000ERKj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %4) #27
          to label %29 unwind label %32

29:                                               ; preds = %27
  %30 = load i32, ptr %4, align 4, !tbaa !46
  %31 = add i32 %30, 1
  br label %22, !llvm.loop !261

32:                                               ; preds = %27
  %33 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %4) #24
  br label %55

34:                                               ; preds = %25
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %5) #24
  br label %54

35:                                               ; preds = %25
  %36 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %5) #24
  br label %55

37:                                               ; preds = %2
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %6) #24
  invoke void @_ZN26Aarch64KernelSymCallHelper14makeProtectSetENS_19AAPCS64_ProtectRuleE(ptr nonnull sret(%"class.std::__ndk1::set") align 8 %6, i32 noundef 0) #27
          to label %38 unwind label %46

38:                                               ; preds = %37
  %39 = call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEaSEOS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %6) #26
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %6) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %6) #24
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %7) #24
  invoke void @_ZN26Aarch64KernelSymCallHelper14makeProtectSetENS_19AAPCS64_ProtectRuleE(ptr nonnull sret(%"class.std::__ndk1::set") align 8 %7, i32 noundef 1) #27
          to label %40 unwind label %48

40:                                               ; preds = %38
  %41 = load ptr, ptr %7, align 8, !tbaa !16
  %42 = ptrtoint ptr %41 to i64
  %43 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %7, i64 0, i32 1
  %44 = ptrtoint ptr %43 to i64
  invoke void @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEEvT_SD_(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %42, i64 %44) #27
          to label %45 unwind label %50

45:                                               ; preds = %40
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #24
  br label %54

46:                                               ; preds = %37
  %47 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %6) #24
  br label %55

48:                                               ; preds = %38
  %49 = landingpad { ptr, i32 }
          cleanup
  br label %52

50:                                               ; preds = %40
  %51 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  br label %52

52:                                               ; preds = %50, %48
  %53 = phi { ptr, i32 } [ %51, %50 ], [ %49, %48 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #24
  br label %55

54:                                               ; preds = %45, %34, %13, %2
  ret void

55:                                               ; preds = %52, %46, %35, %32, %19
  %56 = phi { ptr, i32 } [ %53, %52 ], [ %47, %46 ], [ %33, %32 ], [ %36, %35 ], [ %20, %19 ]
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #26
  resume { ptr, i32 } %56
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEEC2B7v170000ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %3, i8 0, i64 16, i1 false)
  store ptr %3, ptr %0, align 8, !tbaa !16
  %4 = load ptr, ptr %1, align 8, !tbaa !16
  %5 = ptrtoint ptr %4 to i64
  %6 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %1, i64 0, i32 1
  %7 = ptrtoint ptr %6 to i64
  invoke void @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEEvT_SD_(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %5, i64 %7) #27
          to label %8 unwind label %9

8:                                                ; preds = %2
  ret void

9:                                                ; preds = %2
  %10 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #26
  resume { ptr, i32 } %10
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN15RegProtectGuardC2EPN6asmjit5v1_163a649AssemblerERKNSt6__ndk13setIjNS5_4lessIjEENS5_9allocatorIjEEEE(ptr noundef nonnull align 8 dereferenceable(68) %0, ptr noundef %1, ptr noundef nonnull align 8 dereferenceable(24) %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  store ptr %1, ptr %0, align 8, !tbaa !101
  %5 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1
  %6 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 2
  %7 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(25) %5, i8 0, i64 25, i1 false)
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %7, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3xzrE, i64 16, i1 false)
  %8 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 5
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %8, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL2spE, i64 16, i1 false)
  %9 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %2, i64 0, i32 2
  %10 = load i64, ptr %9, align 8, !tbaa !114
  %11 = add i64 %10, 1
  invoke void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE7reserveEm(ptr noundef nonnull align 8 dereferenceable(24) %5, i64 noundef %11) #27
          to label %12 unwind label %26

12:                                               ; preds = %3
  %13 = load ptr, ptr %2, align 8, !tbaa !16
  %14 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %2, i64 0, i32 1
  %15 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %4, i64 0, i32 1
  %16 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %4, i64 0, i32 2
  %17 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %4, i64 0, i32 2, i64 1
  br label %18

18:                                               ; preds = %31, %12
  %19 = phi ptr [ %13, %12 ], [ %32, %31 ]
  %20 = icmp eq ptr %19, %14
  br i1 %20, label %21, label %28

21:                                               ; preds = %18
  %22 = load ptr, ptr %5, align 8, !tbaa !111
  %23 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1, i32 1
  %24 = load ptr, ptr %23, align 8, !tbaa !112
  %25 = icmp eq ptr %22, %24
  br i1 %25, label %44, label %35

26:                                               ; preds = %42, %41, %3
  %27 = landingpad { ptr, i32 }
          cleanup
  br label %45

28:                                               ; preds = %18
  %29 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %19, i64 0, i32 1
  %30 = load i32, ptr %29, align 4, !tbaa !46
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  store i32 134217785, ptr %4, align 4, !tbaa !39, !alias.scope !262
  store i32 %30, ptr %15, align 4, !tbaa !44, !alias.scope !262
  store i32 0, ptr %16, align 4, !tbaa !46, !alias.scope !262
  store i32 0, ptr %17, align 4, !tbaa !46, !alias.scope !262
  invoke void @_ZN15RegProtectGuard15appendOneUniqueERKN6asmjit5v1_163a643GpXE(ptr noundef nonnull align 8 dereferenceable(68) %0, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
          to label %31 unwind label %33

31:                                               ; preds = %28
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  %32 = call noundef ptr @_ZNSt6__ndk116__tree_next_iterB7v170000IPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEES5_EET_T0_(ptr noundef nonnull %19) #26
  br label %18

33:                                               ; preds = %28
  %34 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  br label %45

35:                                               ; preds = %21
  %36 = ptrtoint ptr %24 to i64
  %37 = ptrtoint ptr %22 to i64
  %38 = sub i64 %36, %37
  %39 = and i64 %38, 16
  %40 = icmp eq i64 %39, 0
  br i1 %40, label %42, label %41

41:                                               ; preds = %35
  invoke void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE9push_backB7v170000ERKS4_(ptr noundef nonnull align 8 dereferenceable(24) %5, ptr noundef nonnull align 4 dereferenceable(16) %7) #27
          to label %42 unwind label %26

42:                                               ; preds = %41, %35
  invoke void @_ZN15RegProtectGuard11doPushPairsEv(ptr noundef nonnull align 8 dereferenceable(68) %0) #27
          to label %43 unwind label %26

43:                                               ; preds = %42
  store i8 1, ptr %6, align 8, !tbaa !113
  br label %44

44:                                               ; preds = %43, %21
  ret void

45:                                               ; preds = %33, %26
  %46 = phi { ptr, i32 } [ %27, %26 ], [ %34, %33 ]
  call void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #26
  resume { ptr, i32 } %46
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN26Aarch64KernelSymCallHelper19makeDirtyArgsX0ToX7ERKNSt6__ndk16vectorI8Arm64ArgNS0_9allocatorIS2_EEEE(ptr noalias sret(%"class.std::__ndk1::vector.94") align 8 %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = alloca [8 x i8], align 8
  %4 = alloca %struct.Arm64Arg, align 8
  %5 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %3) #24
  store i64 0, ptr %3, align 8
  %6 = load ptr, ptr %1, align 8, !tbaa !232
  %7 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %1, i64 0, i32 1
  %8 = load ptr, ptr %7, align 8, !tbaa !236
  br label %9

9:                                                ; preds = %38, %2
  %10 = phi ptr [ %6, %2 ], [ %39, %38 ]
  %11 = icmp eq ptr %10, %8
  br i1 %11, label %12, label %24

12:                                               ; preds = %9
  tail call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEC2ERKS4_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) #27
  %13 = load ptr, ptr %7, align 8, !tbaa !236
  %14 = load ptr, ptr %1, align 8, !tbaa !232
  %15 = ptrtoint ptr %13 to i64
  %16 = ptrtoint ptr %14 to i64
  %17 = sub i64 %15, %16
  %18 = sdiv exact i64 %17, 56
  %19 = add nsw i64 %18, 8
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE7reserveEm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %19) #27
          to label %20 unwind label %43

20:                                               ; preds = %12
  %21 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %5, i64 0, i32 1
  %22 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %5, i64 0, i32 2
  %23 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %5, i64 0, i32 2, i64 1
  br label %40

24:                                               ; preds = %9
  %25 = load i8, ptr %10, align 8, !tbaa !238
  switch i8 %25, label %38 [
    i8 0, label %26
    i8 1, label %30
  ]

26:                                               ; preds = %24
  %27 = getelementptr inbounds %struct.Arm64Arg, ptr %10, i64 0, i32 2, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  %28 = load i32, ptr %27, align 4, !tbaa !44
  %29 = icmp ult i32 %28, 8
  br i1 %29, label %34, label %38

30:                                               ; preds = %24
  %31 = getelementptr inbounds %struct.Arm64Arg, ptr %10, i64 0, i32 3, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  %32 = load i32, ptr %31, align 4, !tbaa !44
  %33 = icmp ult i32 %32, 8
  br i1 %33, label %34, label %38

34:                                               ; preds = %30, %26
  %35 = phi i32 [ %28, %26 ], [ %32, %30 ]
  %36 = zext i32 %35 to i64
  %37 = getelementptr inbounds [8 x i8], ptr %3, i64 0, i64 %36
  store i8 1, ptr %37, align 1, !tbaa !265
  br label %38

38:                                               ; preds = %34, %30, %26, %24
  %39 = getelementptr inbounds %struct.Arm64Arg, ptr %10, i64 1
  br label %9

40:                                               ; preds = %55, %20
  %41 = phi i64 [ 0, %20 ], [ %56, %55 ]
  %42 = icmp eq i64 %41, 8
  br i1 %42, label %57, label %45

43:                                               ; preds = %12
  %44 = landingpad { ptr, i32 }
          cleanup
  br label %58

45:                                               ; preds = %40
  %46 = getelementptr inbounds [8 x i8], ptr %3, i64 0, i64 %41
  %47 = load i8, ptr %46, align 1, !tbaa !265, !range !82, !noundef !83
  %48 = icmp eq i8 %47, 0
  br i1 %48, label %49, label %55

49:                                               ; preds = %45
  call void @llvm.lifetime.start.p0(i64 56, ptr nonnull %4) #24
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %5) #24
  store i32 134217785, ptr %5, align 4, !tbaa !39, !alias.scope !266
  %50 = trunc i64 %41 to i32
  store i32 %50, ptr %21, align 4, !tbaa !44, !alias.scope !266
  store i32 0, ptr %22, align 4, !tbaa !46, !alias.scope !266
  store i32 0, ptr %23, align 4, !tbaa !46, !alias.scope !266
  call void @_ZN8Arm64Arg1XERKN6asmjit5v1_163a643GpXE(ptr nonnull sret(%struct.Arm64Arg) align 8 %4, ptr noundef nonnull align 4 dereferenceable(16) %5) #27
  %51 = invoke noundef nonnull align 8 dereferenceable(52) ptr @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJS1_EEERS1_DpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %4) #27
          to label %52 unwind label %53

52:                                               ; preds = %49
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %5) #24
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %4) #24
  br label %55

53:                                               ; preds = %49
  %54 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %5) #24
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %4) #24
  br label %58

55:                                               ; preds = %52, %45
  %56 = add nuw nsw i64 %41, 1
  br label %40, !llvm.loop !269

57:                                               ; preds = %40
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #24
  ret void

58:                                               ; preds = %53, %43
  %59 = phi { ptr, i32 } [ %54, %53 ], [ %44, %43 ]
  call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #26
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #24
  resume { ptr, i32 } %59
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN11IdleRegPool11makeFromVecERKNSt6__ndk16vectorI8Arm64ArgNS0_9allocatorIS2_EEEE(ptr noalias sret(%class.IdleRegPool) align 8 %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %3, i8 0, i64 16, i1 false)
  store ptr %3, ptr %0, align 8, !tbaa !16
  %4 = getelementptr inbounds %class.IdleRegPool, ptr %0, i64 0, i32 1
  %5 = getelementptr inbounds %class.IdleRegPool, ptr %0, i64 0, i32 1, i32 0, i32 1
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %5, i8 0, i64 16, i1 false)
  store ptr %5, ptr %4, align 8, !tbaa !16
  invoke void @_ZN11IdleRegPool4initEv(ptr noundef nonnull align 8 dereferenceable(48) %0) #27
          to label %6 unwind label %14

6:                                                ; preds = %2
  %7 = load ptr, ptr %1, align 8, !tbaa !232
  %8 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %1, i64 0, i32 1
  %9 = load ptr, ptr %8, align 8, !tbaa !236
  br label %10

10:                                               ; preds = %24, %6
  %11 = phi ptr [ %7, %6 ], [ %25, %24 ]
  %12 = icmp eq ptr %11, %9
  br i1 %12, label %13, label %16

13:                                               ; preds = %10
  ret void

14:                                               ; preds = %2
  %15 = landingpad { ptr, i32 }
          cleanup
  br label %26

16:                                               ; preds = %10
  %17 = load i8, ptr %11, align 8, !tbaa !238
  switch i8 %17, label %24 [
    i8 0, label %18
    i8 1, label %22
  ]

18:                                               ; preds = %16
  %19 = getelementptr inbounds %struct.Arm64Arg, ptr %11, i64 0, i32 2
  invoke void @_ZN11IdleRegPool6useOneERKN6asmjit5v1_163a643GpXE(ptr noundef nonnull align 8 dereferenceable(48) %0, ptr noundef nonnull align 4 dereferenceable(16) %19) #27
          to label %24 unwind label %20

20:                                               ; preds = %22, %18
  %21 = landingpad { ptr, i32 }
          cleanup
  br label %26

22:                                               ; preds = %16
  %23 = getelementptr inbounds %struct.Arm64Arg, ptr %11, i64 0, i32 3
  invoke void @_ZN11IdleRegPool6useOneERKN6asmjit5v1_163a643GpWE(ptr noundef nonnull align 8 dereferenceable(48) %0, ptr noundef nonnull align 4 dereferenceable(16) %23) #27
          to label %24 unwind label %20

24:                                               ; preds = %22, %18, %16
  %25 = getelementptr inbounds %struct.Arm64Arg, ptr %11, i64 1
  br label %10

26:                                               ; preds = %20, %14
  %27 = phi { ptr, i32 } [ %21, %20 ], [ %15, %14 ]
  tail call void @_ZN11IdleRegPoolD2Ev(ptr noundef nonnull align 8 dereferenceable(48) %0) #26
  resume { ptr, i32 } %27
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN26Aarch64KernelSymCallHelper17acquireNewArgRegsER11IdleRegPoolRKNSt6__ndk16vectorI8Arm64ArgNS2_9allocatorIS4_EEEE(ptr noalias sret(%"class.std::__ndk1::vector.94") align 8 %0, ptr noundef nonnull align 8 dereferenceable(48) %1, ptr noundef nonnull align 8 dereferenceable(24) %2) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %5 = alloca %struct.Arm64Arg, align 8
  %6 = alloca %"class.asmjit::v1_16::a64::GpW", align 4
  %7 = alloca %struct.Arm64Arg, align 8
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  %8 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %2, i64 0, i32 1
  %9 = load ptr, ptr %8, align 8, !tbaa !236
  %10 = load ptr, ptr %2, align 8, !tbaa !232
  %11 = ptrtoint ptr %9 to i64
  %12 = ptrtoint ptr %10 to i64
  %13 = sub i64 %11, %12
  %14 = sdiv exact i64 %13, 56
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE7reserveEm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %14) #27
          to label %15 unwind label %22

15:                                               ; preds = %3
  %16 = load ptr, ptr %2, align 8, !tbaa !232
  %17 = load ptr, ptr %8, align 8, !tbaa !236
  br label %18

18:                                               ; preds = %50, %15
  %19 = phi ptr [ %16, %15 ], [ %51, %50 ]
  %20 = icmp eq ptr %19, %17
  br i1 %20, label %21, label %24

21:                                               ; preds = %18
  ret void

22:                                               ; preds = %3
  %23 = landingpad { ptr, i32 }
          cleanup
  br label %52

24:                                               ; preds = %18
  %25 = load i8, ptr %19, align 8, !tbaa !238
  switch i8 %25, label %50 [
    i8 0, label %26
    i8 1, label %36
    i8 2, label %46
    i8 3, label %46
  ]

26:                                               ; preds = %24
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  invoke void @_ZN11IdleRegPool8acquireXEv(ptr nonnull sret(%"class.asmjit::v1_16::a64::GpX") align 4 %4, ptr noundef nonnull align 8 dereferenceable(48) %1) #27
          to label %27 unwind label %30

27:                                               ; preds = %26
  call void @llvm.lifetime.start.p0(i64 56, ptr nonnull %5) #24
  call void @_ZN8Arm64Arg1XERKN6asmjit5v1_163a643GpXE(ptr nonnull sret(%struct.Arm64Arg) align 8 %5, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
  %28 = invoke noundef nonnull align 8 dereferenceable(52) ptr @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJS1_EEERS1_DpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %5) #27
          to label %29 unwind label %32

29:                                               ; preds = %27
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %5) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  br label %50

30:                                               ; preds = %26
  %31 = landingpad { ptr, i32 }
          cleanup
  br label %34

32:                                               ; preds = %27
  %33 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %5) #24
  br label %34

34:                                               ; preds = %32, %30
  %35 = phi { ptr, i32 } [ %33, %32 ], [ %31, %30 ]
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  br label %52

36:                                               ; preds = %24
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %6) #24
  invoke void @_ZN11IdleRegPool8acquireWEv(ptr nonnull sret(%"class.asmjit::v1_16::a64::GpW") align 4 %6, ptr noundef nonnull align 8 dereferenceable(48) %1) #27
          to label %37 unwind label %40

37:                                               ; preds = %36
  call void @llvm.lifetime.start.p0(i64 56, ptr nonnull %7) #24
  call void @_ZN8Arm64Arg1WERKN6asmjit5v1_163a643GpWE(ptr nonnull sret(%struct.Arm64Arg) align 8 %7, ptr noundef nonnull align 4 dereferenceable(16) %6) #27
  %38 = invoke noundef nonnull align 8 dereferenceable(52) ptr @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJS1_EEERS1_DpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %7) #27
          to label %39 unwind label %42

39:                                               ; preds = %37
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %7) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %6) #24
  br label %50

40:                                               ; preds = %36
  %41 = landingpad { ptr, i32 }
          cleanup
  br label %44

42:                                               ; preds = %37
  %43 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 56, ptr nonnull %7) #24
  br label %44

44:                                               ; preds = %42, %40
  %45 = phi { ptr, i32 } [ %43, %42 ], [ %41, %40 ]
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %6) #24
  br label %52

46:                                               ; preds = %24, %24
  %47 = invoke noundef nonnull align 8 dereferenceable(52) ptr @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJRKS1_EEERS1_DpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %19) #27
          to label %50 unwind label %48

48:                                               ; preds = %46
  %49 = landingpad { ptr, i32 }
          cleanup
  br label %52

50:                                               ; preds = %46, %39, %29, %24
  %51 = getelementptr inbounds %struct.Arm64Arg, ptr %19, i64 1
  br label %18

52:                                               ; preds = %48, %44, %34, %22
  %53 = phi { ptr, i32 } [ %23, %22 ], [ %49, %48 ], [ %45, %44 ], [ %35, %34 ]
  call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #26
  resume { ptr, i32 } %53
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZN26Aarch64KernelSymCallHelper21emitMovesFromOldToNewEPN6asmjit5v1_163a649AssemblerERKNSt6__ndk16vectorI8Arm64ArgNS5_9allocatorIS7_EEEESC_(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(24) %1, ptr noundef nonnull align 8 dereferenceable(24) %2) local_unnamed_addr #6 comdat align 2 {
  %4 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %1, i64 0, i32 1
  %5 = load ptr, ptr %4, align 8, !tbaa !236
  %6 = load ptr, ptr %1, align 8, !tbaa !232
  %7 = ptrtoint ptr %5 to i64
  %8 = ptrtoint ptr %6 to i64
  %9 = sub i64 %7, %8
  %10 = sdiv exact i64 %9, 56
  %11 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %2, i64 0, i32 1
  %12 = load ptr, ptr %11, align 8, !tbaa !236
  %13 = load ptr, ptr %2, align 8, !tbaa !232
  %14 = ptrtoint ptr %12 to i64
  %15 = ptrtoint ptr %13 to i64
  %16 = sub i64 %14, %15
  %17 = sdiv exact i64 %16, 56
  %18 = tail call i64 @llvm.umin.i64(i64 %17, i64 %10)
  br label %19

19:                                               ; preds = %45, %3
  %20 = phi i64 [ 0, %3 ], [ %46, %45 ]
  %21 = icmp eq i64 %20, %18
  br i1 %21, label %22, label %23

22:                                               ; preds = %19
  ret void

23:                                               ; preds = %19
  %24 = load ptr, ptr %1, align 8, !tbaa !232
  %25 = getelementptr inbounds %struct.Arm64Arg, ptr %24, i64 %20
  %26 = load ptr, ptr %2, align 8, !tbaa !232
  %27 = getelementptr inbounds %struct.Arm64Arg, ptr %26, i64 %20
  %28 = load i8, ptr %25, align 8, !tbaa !238
  %29 = icmp eq i8 %28, 0
  %30 = load i8, ptr %27, align 8
  %31 = icmp eq i8 %30, 0
  %32 = select i1 %29, i1 %31, i1 false
  br i1 %32, label %33, label %37

33:                                               ; preds = %23
  %34 = getelementptr inbounds %struct.Arm64Arg, ptr %24, i64 %20, i32 2
  %35 = getelementptr inbounds %struct.Arm64Arg, ptr %26, i64 %20, i32 2
  %36 = tail call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %34, ptr noundef nonnull align 4 dereferenceable(16) %35) #27
  br label %45

37:                                               ; preds = %23
  %38 = icmp eq i8 %28, 1
  %39 = icmp eq i8 %30, 1
  %40 = select i1 %38, i1 %39, i1 false
  br i1 %40, label %41, label %45

41:                                               ; preds = %37
  %42 = getelementptr inbounds %struct.Arm64Arg, ptr %24, i64 %20, i32 3
  %43 = getelementptr inbounds %struct.Arm64Arg, ptr %26, i64 %20, i32 3
  %44 = tail call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %42, ptr noundef nonnull align 4 dereferenceable(16) %43) #27
  br label %45

45:                                               ; preds = %41, %37, %33
  %46 = add i64 %20, 1
  br label %19, !llvm.loop !270
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef i64 @_ZN26Aarch64KernelSymCallHelper16call_by_name_rawEPN6asmjit5v1_163a649AssemblerEPKcRKNSt6__ndk16vectorI8Arm64ArgNS7_9allocatorIS9_EEEE(ptr noundef %0, ptr noundef %1, ptr noundef nonnull align 8 dereferenceable(24) %2) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = alloca %"class.asmjit::v1_16::arm::Mem", align 4
  %5 = alloca %"class.asmjit::v1_16::arm::Mem", align 4
  %6 = alloca i64, align 8
  %7 = alloca %class.RegProtectGuard, align 8
  %8 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %9 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %10 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %6) #24
  store i64 0, ptr %6, align 8, !tbaa !114
  %11 = call noundef i64 @_ZN13kernel_module20kallsyms_lookup_nameEPKcRm(ptr noundef %1, ptr noundef nonnull align 8 dereferenceable(8) %6) #27
  %12 = icmp eq i64 %11, 0
  br i1 %12, label %13, label %41

13:                                               ; preds = %3
  %14 = call noundef i1 @_ZN26Aarch64KernelSymCallHelper15pushArgsFromVecEPN6asmjit5v1_163a649AssemblerERKNSt6__ndk16vectorI8Arm64ArgNS5_9allocatorIS7_EEEE(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(24) %2) #27
  br i1 %14, label %15, label %41

15:                                               ; preds = %13
  call void @llvm.lifetime.start.p0(i64 72, ptr nonnull %7) #24
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %8) #24
  store i32 134217785, ptr %8, align 4, !tbaa !39, !alias.scope !271
  %16 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %8, i64 0, i32 1
  store i32 11, ptr %16, align 4, !tbaa !44, !alias.scope !271
  %17 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %8, i64 0, i32 2
  store i32 0, ptr %17, align 4, !tbaa !46, !alias.scope !271
  %18 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %8, i64 0, i32 2, i64 1
  store i32 0, ptr %18, align 4, !tbaa !46, !alias.scope !271
  call void @_ZN15RegProtectGuardC2IJN6asmjit5v1_163a643GpXEEvEEPNS3_9AssemblerEDpRKT_(ptr noundef nonnull align 8 dereferenceable(68) %7, ptr noundef %0, ptr noundef nonnull align 4 dereferenceable(16) %8) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %8) #24
  store i32 134217785, ptr %9, align 4, !tbaa !39, !alias.scope !274
  %19 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %9, i64 0, i32 1
  store i32 11, ptr %19, align 4, !tbaa !44, !alias.scope !274
  %20 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %9, i64 0, i32 2
  store i32 0, ptr %20, align 4, !tbaa !46, !alias.scope !274
  %21 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %9, i64 0, i32 2, i64 1
  store i32 0, ptr %21, align 4, !tbaa !46, !alias.scope !274
  %22 = load i64, ptr %6, align 8, !tbaa !114
  invoke fastcc void @_ZN12_GLOBAL__N_117aarch64_asm_mov_xEPN6asmjit5v1_163a649AssemblerENS2_3GpXEm(ptr noundef %0, ptr noundef nonnull %9, i64 noundef %22) #27
          to label %23 unwind label %39

23:                                               ; preds = %15
  store i32 134217785, ptr %10, align 4, !tbaa !39, !alias.scope !277
  %24 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %10, i64 0, i32 1
  store i32 11, ptr %24, align 4, !tbaa !44, !alias.scope !277
  %25 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %10, i64 0, i32 2
  store i32 0, ptr %25, align 4, !tbaa !46, !alias.scope !277
  %26 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %10, i64 0, i32 2, i64 1
  store i32 0, ptr %26, align 4, !tbaa !46, !alias.scope !277
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  %27 = getelementptr inbounds i8, ptr %4, i64 4
  store i32 31, ptr %27, align 4
  %28 = getelementptr inbounds i8, ptr %4, i64 8
  store i32 0, ptr %28, align 4
  %29 = getelementptr inbounds i8, ptr %4, i64 12
  store i32 16777274, ptr %4, align 4, !tbaa !39, !alias.scope !280
  store i32 -16, ptr %29, align 4, !tbaa !46, !alias.scope !280
  %30 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3stpERKNS1_2GpES7_RKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x29E, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x30E, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
          to label %31 unwind label %39

31:                                               ; preds = %23
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  %32 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3blrERKNS1_2GpE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %10) #27
          to label %33 unwind label %39

33:                                               ; preds = %31
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %5) #24
  %34 = getelementptr inbounds i8, ptr %5, i64 4
  store i32 31, ptr %34, align 4
  %35 = getelementptr inbounds i8, ptr %5, i64 8
  store i32 0, ptr %35, align 4
  %36 = getelementptr inbounds i8, ptr %5, i64 12
  store i32 33554490, ptr %5, align 4, !tbaa !39, !alias.scope !283
  store i32 16, ptr %36, align 4, !tbaa !46, !alias.scope !283
  %37 = invoke noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3ldpERKNS1_2GpES7_RKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x29E, ptr noundef nonnull align 4 dereferenceable(16) @_ZN6asmjit5v1_163a644regsL3x30E, ptr noundef nonnull align 4 dereferenceable(16) %5) #27
          to label %38 unwind label %39

38:                                               ; preds = %33
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %5) #24
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %7) #26
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %7) #24
  br label %41

39:                                               ; preds = %33, %31, %23, %15
  %40 = landingpad { ptr, i32 }
          cleanup
  call void @_ZN15RegProtectGuardD2Ev(ptr noundef nonnull align 8 dereferenceable(68) %7) #26
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %7) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #24
  resume { ptr, i32 } %40

41:                                               ; preds = %38, %13, %3
  %42 = phi i64 [ 0, %38 ], [ %11, %3 ], [ -19979, %13 ]
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #24
  ret i64 %42
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZN11IdleRegPoolD2Ev(ptr noundef nonnull align 8 dereferenceable(48) %0) unnamed_addr #2 comdat align 2 {
  %2 = getelementptr inbounds %class.IdleRegPool, ptr %0, i64 0, i32 1
  tail call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #26
  tail call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #26
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000ERKj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1) local_unnamed_addr #1 comdat align 2 {
  %3 = tail call [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE15__insert_uniqueB7v170000ERKj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1) #27
  %4 = extractvalue [2 x i64] %3, 1
  %5 = and i64 %4, 255
  %6 = insertvalue [2 x i64] %3, i64 %5, 1
  ret [2 x i64] %6
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000EOj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1) local_unnamed_addr #1 comdat align 2 {
  %3 = tail call [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE15__insert_uniqueB7v170000EOj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1) #27
  %4 = extractvalue [2 x i64] %3, 1
  %5 = and i64 %4, 255
  %6 = insertvalue [2 x i64] %3, i64 %5, 1
  ret [2 x i64] %6
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEEvT_SD_(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %1, i64 %2) local_unnamed_addr #1 comdat align 2 {
  %4 = inttoptr i64 %1 to ptr
  %5 = inttoptr i64 %2 to ptr
  %6 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %7 = ptrtoint ptr %6 to i64
  br label %8

8:                                                ; preds = %12, %3
  %9 = phi ptr [ %4, %3 ], [ %15, %12 ]
  %10 = icmp eq ptr %9, %5
  br i1 %10, label %11, label %12

11:                                               ; preds = %8
  ret void

12:                                               ; preds = %8
  %13 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %9, i64 0, i32 1
  %14 = tail call i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE15__insert_uniqueB7v170000ENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEERKj(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %7, ptr noundef nonnull align 4 dereferenceable(4) %13) #27
  %15 = tail call noundef ptr @_ZNSt6__ndk116__tree_next_iterB7v170000IPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEES5_EET_T0_(ptr noundef %9) #26
  br label %8, !llvm.loop !286
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE15__insert_uniqueB7v170000ERKj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1) local_unnamed_addr #6 comdat align 2 !type !287 {
  %3 = tail call [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE25__emplace_unique_key_argsIjJRKjEEENS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEERKT_DpOT0_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1, ptr noundef nonnull align 4 dereferenceable(4) %1) #27
  ret [2 x i64] %3
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE25__emplace_unique_key_argsIjJRKjEEENS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEERKT_DpOT0_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1, ptr noundef nonnull align 4 dereferenceable(4) %2) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 !type !288 {
  %4 = alloca ptr, align 8
  %5 = alloca %"class.std::__ndk1::unique_ptr.122", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %4) #24
  %6 = call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE12__find_equalIjEERPNS_16__tree_node_baseIPvEERPNS_15__tree_end_nodeISA_EERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(8) %4, ptr noundef nonnull align 4 dereferenceable(4) %1) #27
  %7 = load ptr, ptr %6, align 8, !tbaa !16
  %8 = icmp eq ptr %7, null
  br i1 %8, label %9, label %17

9:                                                ; preds = %3
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %5) #24
  call void @llvm.experimental.noalias.scope.decl(metadata !289)
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %11 = call noalias noundef nonnull dereferenceable(32) ptr @_Znwm(i64 noundef 32) #30
  %12 = getelementptr inbounds i8, ptr %5, i64 8
  store ptr %10, ptr %12, align 8, !tbaa.struct !292, !alias.scope !289
  %13 = getelementptr inbounds i8, ptr %5, i64 16
  %14 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %11, i64 0, i32 1
  %15 = load i32, ptr %2, align 4, !tbaa !46, !noalias !289
  store i32 %15, ptr %14, align 4, !tbaa !46, !noalias !289
  store i8 1, ptr %13, align 8, !tbaa !293, !alias.scope !289
  %16 = load ptr, ptr %4, align 8, !tbaa !16
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__insert_node_atEPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEERSA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %16, ptr noundef nonnull align 8 dereferenceable(8) %6, ptr noundef nonnull %11) #26
  store ptr null, ptr %5, align 8, !tbaa !16
  call void @_ZNSt6__ndk110unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS_9allocatorIS3_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #24
  br label %17

17:                                               ; preds = %9, %3
  %18 = phi i64 [ 1, %9 ], [ 0, %3 ]
  %19 = phi ptr [ %11, %9 ], [ %7, %3 ]
  %20 = ptrtoint ptr %19 to i64
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %4) #24
  %21 = insertvalue [2 x i64] poison, i64 %20, 0
  %22 = insertvalue [2 x i64] %21, i64 %18, 1
  ret [2 x i64] %22
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE12__find_equalIjEERPNS_16__tree_node_baseIPvEERPNS_15__tree_end_nodeISA_EERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(8) %1, ptr noundef nonnull align 4 dereferenceable(4) %2) local_unnamed_addr #6 comdat align 2 !type !295 {
  %4 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %5 = load ptr, ptr %4, align 8, !tbaa !296
  %6 = icmp eq ptr %5, null
  br i1 %6, label %27, label %7

7:                                                ; preds = %3
  %8 = load i32, ptr %2, align 4, !tbaa !46
  br label %9

9:                                                ; preds = %24, %7
  %10 = phi ptr [ %25, %24 ], [ %5, %7 ]
  %11 = phi ptr [ %26, %24 ], [ %4, %7 ]
  %12 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %10, i64 0, i32 1
  %13 = load i32, ptr %12, align 4, !tbaa !46
  %14 = icmp ult i32 %8, %13
  br i1 %14, label %15, label %18

15:                                               ; preds = %9
  %16 = load ptr, ptr %10, align 8, !tbaa !296
  %17 = icmp eq ptr %16, null
  br i1 %17, label %27, label %24

18:                                               ; preds = %9
  %19 = icmp ult i32 %13, %8
  br i1 %19, label %20, label %27

20:                                               ; preds = %18
  %21 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %10, i64 0, i32 1
  %22 = load ptr, ptr %21, align 8, !tbaa !297
  %23 = icmp eq ptr %22, null
  br i1 %23, label %27, label %24

24:                                               ; preds = %20, %15
  %25 = phi ptr [ %16, %15 ], [ %22, %20 ]
  %26 = phi ptr [ %10, %15 ], [ %21, %20 ]
  br label %9, !llvm.loop !298

27:                                               ; preds = %20, %18, %15, %3
  %28 = phi ptr [ %10, %15 ], [ %10, %20 ], [ %10, %18 ], [ %4, %3 ]
  %29 = phi ptr [ %10, %15 ], [ %21, %20 ], [ %11, %18 ], [ %4, %3 ]
  store ptr %28, ptr %1, align 8, !tbaa !16
  ret ptr %29
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__insert_node_atEPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEERSA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, ptr noundef nonnull align 8 dereferenceable(8) %2, ptr noundef %3) local_unnamed_addr #19 comdat align 2 !type !299 {
  %5 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %3, i64 0, i32 2
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %3, i8 0, i64 16, i1 false)
  store ptr %1, ptr %5, align 8, !tbaa !229
  store ptr %3, ptr %2, align 8, !tbaa !16
  %6 = load ptr, ptr %0, align 8, !tbaa !16
  %7 = load ptr, ptr %6, align 8, !tbaa !296
  %8 = icmp eq ptr %7, null
  br i1 %8, label %11, label %9

9:                                                ; preds = %4
  store ptr %7, ptr %0, align 8, !tbaa !16
  %10 = load ptr, ptr %2, align 8, !tbaa !16
  br label %11

11:                                               ; preds = %9, %4
  %12 = phi ptr [ %10, %9 ], [ %3, %4 ]
  %13 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %14 = load ptr, ptr %13, align 8, !tbaa !296
  tail call void @_ZNSt6__ndk127__tree_balance_after_insertB7v170000IPNS_16__tree_node_baseIPvEEEEvT_S5_(ptr noundef %14, ptr noundef %12) #26
  %15 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 2
  %16 = load i64, ptr %15, align 8, !tbaa !114
  %17 = add i64 %16, 1
  store i64 %17, ptr %15, align 8, !tbaa !114
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS_9allocatorIS3_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 {
  tail call void @_ZNSt6__ndk110unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS_9allocatorIS3_EEEEE5resetB7v170000EPS3_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef null) #26
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk127__tree_balance_after_insertB7v170000IPNS_16__tree_node_baseIPvEEEEvT_S5_(ptr noundef %0, ptr noundef %1) local_unnamed_addr #19 comdat personality ptr @__gxx_personality_v0 {
  %3 = icmp ne ptr %0, null
  tail call void @llvm.assume(i1 %3)
  %4 = icmp ne ptr %1, null
  tail call void @llvm.assume(i1 %4)
  %5 = icmp eq ptr %1, %0
  %6 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %1, i64 0, i32 3
  %7 = zext i1 %5 to i8
  store i8 %7, ptr %6, align 8, !tbaa !300
  br label %8

8:                                                ; preds = %62, %2
  %9 = phi ptr [ %1, %2 ], [ %19, %62 ]
  %10 = icmp eq ptr %9, %0
  br i1 %10, label %67, label %11

11:                                               ; preds = %8
  %12 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %9, i64 0, i32 2
  %13 = load ptr, ptr %12, align 8, !tbaa !229
  %14 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %13, i64 0, i32 3
  %15 = load i8, ptr %14, align 8, !tbaa !300, !range !82, !noundef !83
  %16 = icmp eq i8 %15, 0
  br i1 %16, label %17, label %67

17:                                               ; preds = %11
  %18 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %13, i64 0, i32 2
  %19 = load ptr, ptr %18, align 8, !tbaa !229
  %20 = load ptr, ptr %19, align 8, !tbaa !296
  %21 = icmp eq ptr %20, %13
  br i1 %21, label %22, label %43

22:                                               ; preds = %17
  %23 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %19, i64 0, i32 1
  %24 = load ptr, ptr %23, align 8, !tbaa !297
  %25 = icmp eq ptr %24, null
  br i1 %25, label %30, label %26

26:                                               ; preds = %22
  %27 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %24, i64 0, i32 3
  %28 = load i8, ptr %27, align 8, !tbaa !300, !range !82, !noundef !83
  %29 = icmp eq i8 %28, 0
  br i1 %29, label %62, label %30

30:                                               ; preds = %26, %22
  %31 = load ptr, ptr %13, align 8, !tbaa !296
  %32 = icmp eq ptr %31, %9
  br i1 %32, label %38, label %33

33:                                               ; preds = %30
  %34 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %13, i64 0, i32 2
  tail call void @_ZNSt6__ndk118__tree_left_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef nonnull %13) #26
  %35 = load ptr, ptr %34, align 8, !tbaa !229
  %36 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %35, i64 0, i32 2
  %37 = load ptr, ptr %36, align 8, !tbaa !229
  br label %38

38:                                               ; preds = %33, %30
  %39 = phi ptr [ %19, %30 ], [ %37, %33 ]
  %40 = phi ptr [ %13, %30 ], [ %35, %33 ]
  %41 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %40, i64 0, i32 3
  store i8 1, ptr %41, align 8, !tbaa !300
  %42 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %39, i64 0, i32 3
  store i8 0, ptr %42, align 8, !tbaa !300
  tail call void @_ZNSt6__ndk119__tree_right_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef %39) #26
  br label %67

43:                                               ; preds = %17
  %44 = icmp eq ptr %20, null
  br i1 %44, label %49, label %45

45:                                               ; preds = %43
  %46 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %20, i64 0, i32 3
  %47 = load i8, ptr %46, align 8, !tbaa !300, !range !82, !noundef !83
  %48 = icmp eq i8 %47, 0
  br i1 %48, label %62, label %49

49:                                               ; preds = %45, %43
  %50 = load ptr, ptr %13, align 8, !tbaa !296
  %51 = icmp eq ptr %50, %9
  br i1 %51, label %52, label %57

52:                                               ; preds = %49
  %53 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %13, i64 0, i32 2
  tail call void @_ZNSt6__ndk119__tree_right_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef nonnull %13) #26
  %54 = load ptr, ptr %53, align 8, !tbaa !229
  %55 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %54, i64 0, i32 2
  %56 = load ptr, ptr %55, align 8, !tbaa !229
  br label %57

57:                                               ; preds = %52, %49
  %58 = phi ptr [ %56, %52 ], [ %19, %49 ]
  %59 = phi ptr [ %54, %52 ], [ %13, %49 ]
  %60 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %59, i64 0, i32 3
  store i8 1, ptr %60, align 8, !tbaa !300
  %61 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %58, i64 0, i32 3
  store i8 0, ptr %61, align 8, !tbaa !300
  tail call void @_ZNSt6__ndk118__tree_left_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef %58) #26
  br label %67

62:                                               ; preds = %45, %26
  %63 = phi ptr [ %27, %26 ], [ %46, %45 ]
  store i8 1, ptr %14, align 8, !tbaa !300
  %64 = icmp eq ptr %19, %0
  %65 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %19, i64 0, i32 3
  %66 = zext i1 %64 to i8
  store i8 %66, ptr %65, align 8, !tbaa !300
  store i8 1, ptr %63, align 8, !tbaa !300
  br label %8, !llvm.loop !301

67:                                               ; preds = %57, %38, %11, %8
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk118__tree_left_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef %0) local_unnamed_addr #19 comdat personality ptr @__gxx_personality_v0 {
  %2 = icmp ne ptr %0, null
  tail call void @llvm.assume(i1 %2)
  %3 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !297, !nonnull !83
  %5 = load ptr, ptr %4, align 8, !tbaa !296
  store ptr %5, ptr %3, align 8, !tbaa !297
  %6 = icmp eq ptr %5, null
  br i1 %6, label %9, label %7

7:                                                ; preds = %1
  %8 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %5, i64 0, i32 2
  store ptr %0, ptr %8, align 8, !tbaa !229
  br label %9

9:                                                ; preds = %7, %1
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %0, i64 0, i32 2
  %11 = load ptr, ptr %10, align 8, !tbaa !229
  %12 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %4, i64 0, i32 2
  store ptr %11, ptr %12, align 8, !tbaa !229
  %13 = load ptr, ptr %10, align 8, !tbaa !229
  %14 = load ptr, ptr %13, align 8, !tbaa !296
  %15 = icmp eq ptr %14, %0
  %16 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %13, i64 0, i32 1
  %17 = select i1 %15, ptr %13, ptr %16
  store ptr %4, ptr %17, align 8, !tbaa !16
  store ptr %0, ptr %4, align 8, !tbaa !296
  store ptr %4, ptr %10, align 8, !tbaa !229
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk119__tree_right_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef %0) local_unnamed_addr #19 comdat {
  %2 = icmp ne ptr %0, null
  tail call void @llvm.assume(i1 %2)
  %3 = load ptr, ptr %0, align 8, !tbaa !296, !nonnull !83
  %4 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %3, i64 0, i32 1
  %5 = load ptr, ptr %4, align 8, !tbaa !297
  store ptr %5, ptr %0, align 8, !tbaa !296
  %6 = icmp eq ptr %5, null
  br i1 %6, label %9, label %7

7:                                                ; preds = %1
  %8 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %5, i64 0, i32 2
  store ptr %0, ptr %8, align 8, !tbaa !229
  br label %9

9:                                                ; preds = %7, %1
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %0, i64 0, i32 2
  %11 = load ptr, ptr %10, align 8, !tbaa !229
  %12 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %3, i64 0, i32 2
  store ptr %11, ptr %12, align 8, !tbaa !229
  %13 = load ptr, ptr %10, align 8, !tbaa !229
  %14 = load ptr, ptr %13, align 8, !tbaa !296
  %15 = icmp eq ptr %14, %0
  %16 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %13, i64 0, i32 1
  %17 = select i1 %15, ptr %13, ptr %16
  store ptr %3, ptr %17, align 8, !tbaa !16
  store ptr %0, ptr %4, align 8, !tbaa !297
  store ptr %3, ptr %10, align 8, !tbaa !229
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS_9allocatorIS3_EEEEE5resetB7v170000EPS3_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 !type !302 {
  %3 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr %1, ptr %0, align 8, !tbaa !16
  %4 = icmp eq ptr %3, null
  br i1 %4, label %6, label %5

5:                                                ; preds = %2
  tail call void @_ZdlPv(ptr noundef nonnull %3) #28
  br label %6

6:                                                ; preds = %5, %2
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE15__insert_uniqueB7v170000EOj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1) local_unnamed_addr #6 comdat align 2 !type !303 {
  %3 = tail call [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE25__emplace_unique_key_argsIjJjEEENS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEERKT_DpOT0_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1, ptr noundef nonnull align 4 dereferenceable(4) %1) #27
  ret [2 x i64] %3
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE25__emplace_unique_key_argsIjJjEEENS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEERKT_DpOT0_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1, ptr noundef nonnull align 4 dereferenceable(4) %2) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 !type !304 {
  %4 = alloca ptr, align 8
  %5 = alloca %"class.std::__ndk1::unique_ptr.122", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %4) #24
  %6 = call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE12__find_equalIjEERPNS_16__tree_node_baseIPvEERPNS_15__tree_end_nodeISA_EERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(8) %4, ptr noundef nonnull align 4 dereferenceable(4) %1) #27
  %7 = load ptr, ptr %6, align 8, !tbaa !16
  %8 = icmp eq ptr %7, null
  br i1 %8, label %9, label %17

9:                                                ; preds = %3
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %5) #24
  call void @llvm.experimental.noalias.scope.decl(metadata !305)
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %11 = call noalias noundef nonnull dereferenceable(32) ptr @_Znwm(i64 noundef 32) #30
  %12 = getelementptr inbounds i8, ptr %5, i64 8
  store ptr %10, ptr %12, align 8, !tbaa.struct !292, !alias.scope !305
  %13 = getelementptr inbounds i8, ptr %5, i64 16
  %14 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %11, i64 0, i32 1
  %15 = load i32, ptr %2, align 4, !tbaa !46, !noalias !305
  store i32 %15, ptr %14, align 4, !tbaa !46, !noalias !305
  store i8 1, ptr %13, align 8, !tbaa !293, !alias.scope !305
  %16 = load ptr, ptr %4, align 8, !tbaa !16
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__insert_node_atEPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEERSA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %16, ptr noundef nonnull align 8 dereferenceable(8) %6, ptr noundef nonnull %11) #26
  store ptr null, ptr %5, align 8, !tbaa !16
  call void @_ZNSt6__ndk110unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS_9allocatorIS3_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #24
  br label %17

17:                                               ; preds = %9, %3
  %18 = phi i64 [ 1, %9 ], [ 0, %3 ]
  %19 = phi ptr [ %11, %9 ], [ %7, %3 ]
  %20 = ptrtoint ptr %19 to i64
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %4) #24
  %21 = insertvalue [2 x i64] poison, i64 %20, 0
  %22 = insertvalue [2 x i64] %21, i64 %18, 1
  ret [2 x i64] %22
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE15__insert_uniqueB7v170000ENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEERKj(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %1, ptr noundef nonnull align 4 dereferenceable(4) %2) local_unnamed_addr #6 comdat align 2 !type !308 {
  %4 = tail call [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE30__emplace_hint_unique_key_argsIjJRKjEEENS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEENS_21__tree_const_iteratorIjSE_lEERKT_DpOT0_(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %1, ptr noundef nonnull align 4 dereferenceable(4) %2, ptr noundef nonnull align 4 dereferenceable(4) %2) #27
  %5 = extractvalue [2 x i64] %4, 0
  ret i64 %5
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk121__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEppB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #19 comdat align 2 !type !309 {
  %2 = load ptr, ptr %0, align 8, !tbaa !310
  %3 = tail call noundef ptr @_ZNSt6__ndk116__tree_next_iterB7v170000IPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEES5_EET_T0_(ptr noundef %2) #26
  store ptr %3, ptr %0, align 8, !tbaa !310
  ret ptr %0
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE30__emplace_hint_unique_key_argsIjJRKjEEENS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEENS_21__tree_const_iteratorIjSE_lEERKT_DpOT0_(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %1, ptr noundef nonnull align 4 dereferenceable(4) %2, ptr noundef nonnull align 4 dereferenceable(4) %3) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 !type !312 {
  %5 = alloca ptr, align 8
  %6 = alloca ptr, align 8
  %7 = alloca %"class.std::__ndk1::unique_ptr.122", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %5) #24
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %6) #24
  %8 = call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE12__find_equalIjEERPNS_16__tree_node_baseIPvEENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjS8_EElEERPNS_15__tree_end_nodeISA_EESB_RKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %1, ptr noundef nonnull align 8 dereferenceable(8) %5, ptr noundef nonnull align 8 dereferenceable(8) %6, ptr noundef nonnull align 4 dereferenceable(4) %2) #27
  %9 = load ptr, ptr %8, align 8, !tbaa !16
  %10 = icmp eq ptr %9, null
  br i1 %10, label %11, label %19

11:                                               ; preds = %4
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %7) #24
  call void @llvm.experimental.noalias.scope.decl(metadata !313)
  %12 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %13 = call noalias noundef nonnull dereferenceable(32) ptr @_Znwm(i64 noundef 32) #30
  %14 = getelementptr inbounds i8, ptr %7, i64 8
  store ptr %12, ptr %14, align 8, !tbaa.struct !292, !alias.scope !313
  %15 = getelementptr inbounds i8, ptr %7, i64 16
  %16 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %13, i64 0, i32 1
  %17 = load i32, ptr %3, align 4, !tbaa !46, !noalias !313
  store i32 %17, ptr %16, align 4, !tbaa !46, !noalias !313
  store i8 1, ptr %15, align 8, !tbaa !293, !alias.scope !313
  %18 = load ptr, ptr %5, align 8, !tbaa !16
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__insert_node_atEPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEERSA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %18, ptr noundef nonnull align 8 dereferenceable(8) %8, ptr noundef nonnull %13) #26
  store ptr null, ptr %7, align 8, !tbaa !16
  call void @_ZNSt6__ndk110unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS_9allocatorIS3_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #24
  br label %19

19:                                               ; preds = %11, %4
  %20 = phi i64 [ 1, %11 ], [ 0, %4 ]
  %21 = phi ptr [ %13, %11 ], [ %9, %4 ]
  %22 = ptrtoint ptr %21 to i64
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #24
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %5) #24
  %23 = insertvalue [2 x i64] poison, i64 %22, 0
  %24 = insertvalue [2 x i64] %23, i64 %20, 1
  ret [2 x i64] %24
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE12__find_equalIjEERPNS_16__tree_node_baseIPvEENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjS8_EElEERPNS_15__tree_end_nodeISA_EESB_RKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %1, ptr noundef nonnull align 8 dereferenceable(8) %2, ptr noundef nonnull align 8 dereferenceable(8) %3, ptr noundef nonnull align 4 dereferenceable(4) %4) local_unnamed_addr #1 comdat align 2 !type !316 {
  %6 = inttoptr i64 %1 to ptr
  %7 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %8 = icmp eq ptr %7, %6
  br i1 %8, label %14, label %9

9:                                                ; preds = %5
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %6, i64 0, i32 1
  %11 = load i32, ptr %4, align 4, !tbaa !46
  %12 = load i32, ptr %10, align 4, !tbaa !46
  %13 = icmp ult i32 %11, %12
  br i1 %13, label %14, label %32

14:                                               ; preds = %9, %5
  %15 = load ptr, ptr %0, align 8, !tbaa !16
  %16 = icmp eq ptr %15, %6
  br i1 %16, label %23, label %17

17:                                               ; preds = %14
  %18 = tail call noundef ptr @_ZNSt6__ndk116__tree_prev_iterB7v170000IPNS_16__tree_node_baseIPvEEPNS_15__tree_end_nodeIS4_EEEET_T0_(ptr noundef %6) #26
  %19 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %18, i64 0, i32 1
  %20 = load i32, ptr %19, align 4, !tbaa !46
  %21 = load i32, ptr %4, align 4, !tbaa !46
  %22 = icmp ult i32 %20, %21
  br i1 %22, label %23, label %30

23:                                               ; preds = %17, %14
  %24 = phi ptr [ %18, %17 ], [ %6, %14 ]
  %25 = load ptr, ptr %6, align 8, !tbaa !296
  %26 = icmp eq ptr %25, null
  br i1 %26, label %27, label %28

27:                                               ; preds = %23
  store ptr %6, ptr %2, align 8, !tbaa !16
  br label %52

28:                                               ; preds = %23
  store ptr %24, ptr %2, align 8, !tbaa !16
  %29 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %24, i64 0, i32 1
  br label %52

30:                                               ; preds = %17
  %31 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE12__find_equalIjEERPNS_16__tree_node_baseIPvEERPNS_15__tree_end_nodeISA_EERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(8) %2, ptr noundef nonnull align 4 dereferenceable(4) %4) #27
  br label %52

32:                                               ; preds = %9
  %33 = icmp ult i32 %12, %11
  br i1 %33, label %34, label %51

34:                                               ; preds = %32
  %35 = tail call i64 @_ZNSt6__ndk14nextB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEENS_9enable_ifIXsr25__is_cpp17_input_iteratorIT_EE5valueES8_E4typeES8_NS_15iterator_traitsIS8_E15difference_typeE(i64 %1, i64 noundef 1) #27
  %36 = inttoptr i64 %35 to ptr
  %37 = icmp eq ptr %7, %36
  br i1 %37, label %43, label %38

38:                                               ; preds = %34
  %39 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %36, i64 0, i32 1
  %40 = load i32, ptr %4, align 4, !tbaa !46
  %41 = load i32, ptr %39, align 4, !tbaa !46
  %42 = icmp ult i32 %40, %41
  br i1 %42, label %43, label %49

43:                                               ; preds = %38, %34
  %44 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %6, i64 0, i32 1
  %45 = load ptr, ptr %44, align 8, !tbaa !297
  %46 = icmp eq ptr %45, null
  br i1 %46, label %47, label %48

47:                                               ; preds = %43
  store ptr %6, ptr %2, align 8, !tbaa !16
  br label %52

48:                                               ; preds = %43
  store ptr %36, ptr %2, align 8, !tbaa !16
  br label %52

49:                                               ; preds = %38
  %50 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE12__find_equalIjEERPNS_16__tree_node_baseIPvEERPNS_15__tree_end_nodeISA_EERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(8) %2, ptr noundef nonnull align 4 dereferenceable(4) %4) #27
  br label %52

51:                                               ; preds = %32
  store ptr %6, ptr %2, align 8, !tbaa !16
  store ptr %6, ptr %3, align 8, !tbaa !16
  br label %52

52:                                               ; preds = %51, %49, %48, %47, %30, %28, %27
  %53 = phi ptr [ %3, %51 ], [ %6, %27 ], [ %29, %28 ], [ %31, %30 ], [ %44, %47 ], [ %36, %48 ], [ %50, %49 ]
  ret ptr %53
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk121__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEmmB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #19 comdat align 2 !type !309 {
  %2 = load ptr, ptr %0, align 8, !tbaa !310
  %3 = tail call noundef ptr @_ZNSt6__ndk116__tree_prev_iterB7v170000IPNS_16__tree_node_baseIPvEEPNS_15__tree_end_nodeIS4_EEEET_T0_(ptr noundef %2) #26
  store ptr %3, ptr %0, align 8, !tbaa !310
  ret ptr %0
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden i64 @_ZNSt6__ndk14nextB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEENS_9enable_ifIXsr25__is_cpp17_input_iteratorIT_EE5valueES8_E4typeES8_NS_15iterator_traitsIS8_E15difference_typeE(i64 %0, i64 noundef %1) local_unnamed_addr #10 comdat {
  %3 = alloca %"class.std::__ndk1::__tree_const_iterator", align 8
  %4 = inttoptr i64 %0 to ptr
  store ptr %4, ptr %3, align 8
  call void @_ZNSt6__ndk17advanceB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEllvEEvRT_T0_(ptr noundef nonnull align 8 dereferenceable(8) %3, i64 noundef %1) #27
  %5 = load ptr, ptr %3, align 8, !tbaa.struct !15
  %6 = ptrtoint ptr %5 to i64
  ret i64 %6
}

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk116__tree_prev_iterB7v170000IPNS_16__tree_node_baseIPvEEPNS_15__tree_end_nodeIS4_EEEET_T0_(ptr noundef %0) local_unnamed_addr #25 comdat {
  %2 = icmp ne ptr %0, null
  tail call void @llvm.assume(i1 %2)
  %3 = load ptr, ptr %0, align 8, !tbaa !296
  %4 = icmp eq ptr %3, null
  br i1 %4, label %7, label %5

5:                                                ; preds = %1
  %6 = tail call noundef ptr @_ZNSt6__ndk110__tree_maxB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_(ptr noundef nonnull %3) #26
  br label %13

7:                                                ; preds = %7, %1
  %8 = phi ptr [ %10, %7 ], [ %0, %1 ]
  %9 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %8, i64 0, i32 2
  %10 = load ptr, ptr %9, align 8, !tbaa !229
  %11 = load ptr, ptr %10, align 8, !tbaa !296
  %12 = icmp eq ptr %11, %8
  br i1 %12, label %7, label %13, !llvm.loop !317

13:                                               ; preds = %7, %5
  %14 = phi ptr [ %6, %5 ], [ %10, %7 ]
  ret ptr %14
}

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk110__tree_maxB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_(ptr noundef %0) local_unnamed_addr #25 comdat {
  %2 = icmp ne ptr %0, null
  tail call void @llvm.assume(i1 %2)
  br label %3

3:                                                ; preds = %3, %1
  %4 = phi ptr [ %0, %1 ], [ %6, %3 ]
  %5 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %4, i64 0, i32 1
  %6 = load ptr, ptr %5, align 8, !tbaa !297
  %7 = icmp eq ptr %6, null
  br i1 %7, label %8, label %3, !llvm.loop !318

8:                                                ; preds = %3
  ret ptr %4
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk17advanceB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEllvEEvRT_T0_(ptr noundef nonnull align 8 dereferenceable(8) %0, i64 noundef %1) local_unnamed_addr #6 comdat {
  tail call void @_ZNSt6__ndk19__advanceB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEEvRT_NS_15iterator_traitsIS7_E15difference_typeENS_26bidirectional_iterator_tagE(ptr noundef nonnull align 8 dereferenceable(8) %0, i64 noundef %1, i8 undef) #27
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk19__advanceB7v170000INS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEEEvRT_NS_15iterator_traitsIS7_E15difference_typeENS_26bidirectional_iterator_tagE(ptr noundef nonnull align 8 dereferenceable(8) %0, i64 noundef %1, i8 %2) local_unnamed_addr #6 comdat {
  %4 = icmp sgt i64 %1, -1
  br i1 %4, label %5, label %11

5:                                                ; preds = %8, %3
  %6 = phi i64 [ %10, %8 ], [ %1, %3 ]
  %7 = icmp sgt i64 %6, 0
  br i1 %7, label %8, label %17

8:                                                ; preds = %5
  %9 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk121__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEppB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) #27
  %10 = add nsw i64 %6, -1
  br label %5, !llvm.loop !319

11:                                               ; preds = %14, %3
  %12 = phi i64 [ %16, %14 ], [ %1, %3 ]
  %13 = icmp eq i64 %12, 0
  br i1 %13, label %17, label %14

14:                                               ; preds = %11
  %15 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk121__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEmmB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) #27
  %16 = add i64 %12, 1
  br label %11, !llvm.loop !320

17:                                               ; preds = %11, %5
  ret void
}

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk116__tree_next_iterB7v170000IPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEES5_EET_T0_(ptr noundef %0) local_unnamed_addr #25 comdat {
  %2 = icmp ne ptr %0, null
  tail call void @llvm.assume(i1 %2)
  %3 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !297
  %5 = icmp eq ptr %4, null
  br i1 %5, label %8, label %6

6:                                                ; preds = %1
  %7 = tail call noundef ptr @_ZNSt6__ndk110__tree_minB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_(ptr noundef nonnull %4) #26
  br label %14

8:                                                ; preds = %8, %1
  %9 = phi ptr [ %11, %8 ], [ %0, %1 ]
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %9, i64 0, i32 2
  %11 = load ptr, ptr %10, align 8, !tbaa !229
  %12 = load ptr, ptr %11, align 8, !tbaa !296
  %13 = icmp eq ptr %12, %9
  br i1 %13, label %14, label %8, !llvm.loop !321

14:                                               ; preds = %8, %6
  %15 = phi ptr [ %7, %6 ], [ %11, %8 ]
  ret ptr %15
}

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk110__tree_minB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_(ptr noundef %0) local_unnamed_addr #25 comdat {
  %2 = icmp ne ptr %0, null
  tail call void @llvm.assume(i1 %2)
  br label %3

3:                                                ; preds = %3, %1
  %4 = phi ptr [ %0, %1 ], [ %5, %3 ]
  %5 = load ptr, ptr %4, align 8, !tbaa !296
  %6 = icmp eq ptr %5, null
  br i1 %6, label %7, label %3, !llvm.loop !322

7:                                                ; preds = %3
  ret ptr %4
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE14__erase_uniqueIjEEmRKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1) local_unnamed_addr #1 comdat align 2 !type !323 {
  %3 = tail call i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE4findIjEENS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1) #27
  %4 = inttoptr i64 %3 to ptr
  %5 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %6 = icmp eq ptr %5, %4
  br i1 %6, label %9, label %7

7:                                                ; preds = %2
  %8 = tail call i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE5eraseENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEE(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %3) #27
  br label %9

9:                                                ; preds = %7, %2
  %10 = phi i64 [ 1, %7 ], [ 0, %2 ]
  ret i64 %10
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE4findIjEENS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1) local_unnamed_addr #1 comdat align 2 !type !324 {
  %3 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !296
  %5 = tail call i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE13__lower_boundIjEENS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEERKT_SB_PNS_15__tree_end_nodeIPNS_16__tree_node_baseIS9_EEEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1, ptr noundef %4, ptr noundef nonnull %3) #27
  %6 = inttoptr i64 %5 to ptr
  %7 = ptrtoint ptr %3 to i64
  %8 = icmp eq ptr %3, %6
  br i1 %8, label %14, label %9

9:                                                ; preds = %2
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %6, i64 0, i32 1
  %11 = load i32, ptr %1, align 4, !tbaa !46
  %12 = load i32, ptr %10, align 4, !tbaa !46
  %13 = icmp ult i32 %11, %12
  br i1 %13, label %14, label %15

14:                                               ; preds = %9, %2
  br label %15

15:                                               ; preds = %14, %9
  %16 = phi i64 [ %7, %14 ], [ %5, %9 ]
  ret i64 %16
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE5eraseENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEE(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %1) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 !type !325 {
  %3 = inttoptr i64 %1 to ptr
  %4 = tail call i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE21__remove_node_pointerEPNS_11__tree_nodeIjPvEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %3) #26
  tail call void @_ZdlPv(ptr noundef %3) #28
  ret i64 %4
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE13__lower_boundIjEENS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEERKT_SB_PNS_15__tree_end_nodeIPNS_16__tree_node_baseIS9_EEEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %1, ptr noundef %2, ptr noundef %3) local_unnamed_addr #4 comdat align 2 !type !326 {
  %5 = load i32, ptr %1, align 4
  br label %6

6:                                                ; preds = %10, %4
  %7 = phi ptr [ %2, %4 ], [ %17, %10 ]
  %8 = phi ptr [ %3, %4 ], [ %16, %10 ]
  %9 = icmp eq ptr %7, null
  br i1 %9, label %18, label %10

10:                                               ; preds = %6
  %11 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %7, i64 0, i32 1
  %12 = load i32, ptr %11, align 4, !tbaa !46
  %13 = icmp ult i32 %12, %5
  %14 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %7, i64 0, i32 1
  %15 = select i1 %13, ptr %14, ptr %7
  %16 = select i1 %13, ptr %8, ptr %7
  %17 = load ptr, ptr %15, align 8, !tbaa !16
  br label %6, !llvm.loop !327

18:                                               ; preds = %6
  %19 = ptrtoint ptr %8 to i64
  ret i64 %19
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE21__remove_node_pointerEPNS_11__tree_nodeIjPvEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 !type !328 {
  %3 = tail call noundef ptr @_ZNSt6__ndk116__tree_next_iterB7v170000IPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEES5_EET_T0_(ptr noundef %1) #26
  %4 = load ptr, ptr %0, align 8, !tbaa !16
  %5 = icmp eq ptr %4, %1
  br i1 %5, label %6, label %7

6:                                                ; preds = %2
  store ptr %3, ptr %0, align 8, !tbaa !16
  br label %7

7:                                                ; preds = %6, %2
  %8 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 2
  %9 = load i64, ptr %8, align 8, !tbaa !114
  %10 = add i64 %9, -1
  store i64 %10, ptr %8, align 8, !tbaa !114
  %11 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %12 = load ptr, ptr %11, align 8, !tbaa !296
  tail call void @_ZNSt6__ndk113__tree_removeB7v170000IPNS_16__tree_node_baseIPvEEEEvT_S5_(ptr noundef %12, ptr noundef %1) #26
  %13 = ptrtoint ptr %3 to i64
  ret i64 %13
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk113__tree_removeB7v170000IPNS_16__tree_node_baseIPvEEEEvT_S5_(ptr noundef %0, ptr noundef %1) local_unnamed_addr #19 comdat {
  %3 = icmp ne ptr %0, null
  tail call void @llvm.assume(i1 %3)
  %4 = icmp ne ptr %1, null
  tail call void @llvm.assume(i1 %4)
  %5 = load ptr, ptr %1, align 8, !tbaa !296
  %6 = icmp eq ptr %5, null
  br i1 %6, label %15, label %7

7:                                                ; preds = %2
  %8 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %1, i64 0, i32 1
  %9 = load ptr, ptr %8, align 8, !tbaa !297
  %10 = icmp eq ptr %9, null
  br i1 %10, label %20, label %11

11:                                               ; preds = %7
  %12 = tail call noundef ptr @_ZNSt6__ndk111__tree_nextB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_(ptr noundef nonnull %1) #26
  %13 = load ptr, ptr %12, align 8, !tbaa !296
  %14 = icmp eq ptr %13, null
  br i1 %14, label %15, label %20

15:                                               ; preds = %11, %2
  %16 = phi ptr [ %12, %11 ], [ %1, %2 ]
  %17 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %16, i64 0, i32 1
  %18 = load ptr, ptr %17, align 8, !tbaa !297
  %19 = icmp eq ptr %18, null
  br i1 %19, label %26, label %20

20:                                               ; preds = %15, %11, %7
  %21 = phi ptr [ %18, %15 ], [ %13, %11 ], [ %5, %7 ]
  %22 = phi ptr [ %16, %15 ], [ %12, %11 ], [ %1, %7 ]
  %23 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %22, i64 0, i32 2
  %24 = load ptr, ptr %23, align 8, !tbaa !229
  %25 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %21, i64 0, i32 2
  store ptr %24, ptr %25, align 8, !tbaa !229
  br label %26

26:                                               ; preds = %20, %15
  %27 = phi i1 [ false, %20 ], [ true, %15 ]
  %28 = phi ptr [ %21, %20 ], [ null, %15 ]
  %29 = phi ptr [ %22, %20 ], [ %16, %15 ]
  %30 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %29, i64 0, i32 2
  %31 = load ptr, ptr %30, align 8, !tbaa !229
  %32 = load ptr, ptr %31, align 8, !tbaa !296
  %33 = icmp eq ptr %32, %29
  br i1 %33, label %34, label %39

34:                                               ; preds = %26
  store ptr %28, ptr %31, align 8, !tbaa !296
  %35 = icmp eq ptr %29, %0
  br i1 %35, label %41, label %36

36:                                               ; preds = %34
  %37 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %31, i64 0, i32 1
  %38 = load ptr, ptr %37, align 8, !tbaa !297
  br label %41

39:                                               ; preds = %26
  %40 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %31, i64 0, i32 1
  store ptr %28, ptr %40, align 8, !tbaa !297
  br label %41

41:                                               ; preds = %39, %36, %34
  %42 = phi ptr [ %38, %36 ], [ %32, %39 ], [ null, %34 ]
  %43 = phi ptr [ %0, %36 ], [ %0, %39 ], [ %28, %34 ]
  %44 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %29, i64 0, i32 3
  %45 = load i8, ptr %44, align 8, !tbaa !300, !range !82, !noundef !83
  %46 = icmp ne i8 %45, 0
  %47 = icmp eq ptr %29, %1
  br i1 %47, label %69, label %48

48:                                               ; preds = %41
  %49 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %1, i64 0, i32 2
  %50 = load ptr, ptr %49, align 8, !tbaa !229
  store ptr %50, ptr %30, align 8, !tbaa !229
  %51 = load ptr, ptr %49, align 8, !tbaa !229
  %52 = load ptr, ptr %51, align 8, !tbaa !296
  %53 = icmp eq ptr %52, %1
  %54 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %50, i64 0, i32 1
  %55 = select i1 %53, ptr %50, ptr %54
  store ptr %29, ptr %55, align 8, !tbaa !16
  %56 = load ptr, ptr %1, align 8, !tbaa !296
  store ptr %56, ptr %29, align 8, !tbaa !296
  %57 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %56, i64 0, i32 2
  store ptr %29, ptr %57, align 8, !tbaa !229
  %58 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %1, i64 0, i32 1
  %59 = load ptr, ptr %58, align 8, !tbaa !297
  %60 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %29, i64 0, i32 1
  store ptr %59, ptr %60, align 8, !tbaa !297
  %61 = icmp eq ptr %59, null
  br i1 %61, label %64, label %62

62:                                               ; preds = %48
  %63 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %59, i64 0, i32 2
  store ptr %29, ptr %63, align 8, !tbaa !229
  br label %64

64:                                               ; preds = %62, %48
  %65 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %1, i64 0, i32 3
  %66 = load i8, ptr %65, align 8, !tbaa !300, !range !82, !noundef !83
  store i8 %66, ptr %44, align 8, !tbaa !300
  %67 = icmp eq ptr %43, %1
  %68 = select i1 %67, ptr %29, ptr %43
  br label %69

69:                                               ; preds = %64, %41
  %70 = phi ptr [ %43, %41 ], [ %68, %64 ]
  %71 = icmp ne ptr %70, null
  %72 = select i1 %46, i1 %71, i1 false
  br i1 %72, label %73, label %218

73:                                               ; preds = %69
  br i1 %27, label %76, label %74

74:                                               ; preds = %73
  %75 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %28, i64 0, i32 3
  store i8 1, ptr %75, align 8, !tbaa !300
  br label %218

76:                                               ; preds = %208, %73
  %77 = phi ptr [ %217, %208 ], [ %42, %73 ]
  %78 = phi ptr [ %210, %208 ], [ %70, %73 ]
  %79 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %77, i64 0, i32 2
  %80 = load ptr, ptr %79, align 8, !tbaa !229
  %81 = load ptr, ptr %80, align 8, !tbaa !296
  %82 = icmp eq ptr %81, %77
  %83 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %77, i64 0, i32 3
  %84 = load i8, ptr %83, align 8, !tbaa !300, !range !82
  %85 = icmp eq i8 %84, 0
  br i1 %82, label %148, label %86

86:                                               ; preds = %76
  br i1 %85, label %87, label %94

87:                                               ; preds = %86
  store i8 1, ptr %83, align 8, !tbaa !300
  %88 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %80, i64 0, i32 3
  store i8 0, ptr %88, align 8, !tbaa !300
  tail call void @_ZNSt6__ndk118__tree_left_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef nonnull %80) #26
  %89 = load ptr, ptr %77, align 8, !tbaa !296
  %90 = icmp eq ptr %78, %89
  %91 = select i1 %90, ptr %77, ptr %78
  %92 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %89, i64 0, i32 1
  %93 = load ptr, ptr %92, align 8, !tbaa !297
  br label %94

94:                                               ; preds = %87, %86
  %95 = phi ptr [ %77, %86 ], [ %93, %87 ]
  %96 = phi ptr [ %78, %86 ], [ %91, %87 ]
  %97 = load ptr, ptr %95, align 8, !tbaa !296
  %98 = icmp eq ptr %97, null
  br i1 %98, label %103, label %99

99:                                               ; preds = %94
  %100 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %97, i64 0, i32 3
  %101 = load i8, ptr %100, align 8, !tbaa !300, !range !82, !noundef !83
  %102 = icmp eq i8 %101, 0
  br i1 %102, label %123, label %103

103:                                              ; preds = %99, %94
  %104 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %95, i64 0, i32 1
  %105 = load ptr, ptr %104, align 8, !tbaa !297
  %106 = icmp eq ptr %105, null
  br i1 %106, label %111, label %107

107:                                              ; preds = %103
  %108 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %105, i64 0, i32 3
  %109 = load i8, ptr %108, align 8, !tbaa !300, !range !82, !noundef !83
  %110 = icmp eq i8 %109, 0
  br i1 %110, label %127, label %111

111:                                              ; preds = %107, %103
  %112 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %95, i64 0, i32 3
  store i8 0, ptr %112, align 8, !tbaa !300
  %113 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %95, i64 0, i32 2
  %114 = load ptr, ptr %113, align 8, !tbaa !229
  %115 = icmp eq ptr %114, %96
  br i1 %115, label %120, label %116

116:                                              ; preds = %111
  %117 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %114, i64 0, i32 3
  %118 = load i8, ptr %117, align 8, !tbaa !300, !range !82, !noundef !83
  %119 = icmp eq i8 %118, 0
  br i1 %119, label %120, label %208

120:                                              ; preds = %116, %111
  %121 = phi ptr [ %114, %116 ], [ %96, %111 ]
  %122 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %121, i64 0, i32 3
  store i8 1, ptr %122, align 8, !tbaa !300
  br label %218

123:                                              ; preds = %99
  %124 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %95, i64 0, i32 1
  %125 = load ptr, ptr %124, align 8, !tbaa !297
  %126 = icmp eq ptr %125, null
  br i1 %126, label %132, label %127

127:                                              ; preds = %123, %107
  %128 = phi ptr [ %125, %123 ], [ %105, %107 ]
  %129 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %128, i64 0, i32 3
  %130 = load i8, ptr %129, align 8, !tbaa !300, !range !82, !noundef !83
  %131 = icmp eq i8 %130, 0
  br i1 %131, label %139, label %132

132:                                              ; preds = %127, %123
  %133 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %97, i64 0, i32 3
  store i8 1, ptr %133, align 8, !tbaa !300
  %134 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %95, i64 0, i32 3
  store i8 0, ptr %134, align 8, !tbaa !300
  tail call void @_ZNSt6__ndk119__tree_right_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef nonnull %95) #26
  %135 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %95, i64 0, i32 2
  %136 = load ptr, ptr %135, align 8, !tbaa !229
  %137 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %136, i64 0, i32 1
  %138 = load ptr, ptr %137, align 8, !tbaa !297
  br label %139

139:                                              ; preds = %132, %127
  %140 = phi ptr [ %138, %132 ], [ %128, %127 ]
  %141 = phi ptr [ %136, %132 ], [ %95, %127 ]
  %142 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %141, i64 0, i32 2
  %143 = load ptr, ptr %142, align 8, !tbaa !229
  %144 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %143, i64 0, i32 3
  %145 = load i8, ptr %144, align 8, !tbaa !300, !range !82, !noundef !83
  %146 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %141, i64 0, i32 3
  store i8 %145, ptr %146, align 8, !tbaa !300
  store i8 1, ptr %144, align 8, !tbaa !300
  %147 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %140, i64 0, i32 3
  store i8 1, ptr %147, align 8, !tbaa !300
  tail call void @_ZNSt6__ndk118__tree_left_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef %143) #26
  br label %218

148:                                              ; preds = %76
  br i1 %85, label %149, label %156

149:                                              ; preds = %148
  store i8 1, ptr %83, align 8, !tbaa !300
  %150 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %80, i64 0, i32 3
  store i8 0, ptr %150, align 8, !tbaa !300
  tail call void @_ZNSt6__ndk119__tree_right_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef nonnull %80) #26
  %151 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %77, i64 0, i32 1
  %152 = load ptr, ptr %151, align 8, !tbaa !297
  %153 = icmp eq ptr %78, %152
  %154 = select i1 %153, ptr %77, ptr %78
  %155 = load ptr, ptr %152, align 8, !tbaa !296
  br label %156

156:                                              ; preds = %149, %148
  %157 = phi ptr [ %77, %148 ], [ %155, %149 ]
  %158 = phi ptr [ %78, %148 ], [ %154, %149 ]
  %159 = load ptr, ptr %157, align 8, !tbaa !296
  %160 = icmp eq ptr %159, null
  br i1 %160, label %165, label %161

161:                                              ; preds = %156
  %162 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %159, i64 0, i32 3
  %163 = load i8, ptr %162, align 8, !tbaa !300, !range !82, !noundef !83
  %164 = icmp eq i8 %163, 0
  br i1 %164, label %199, label %165

165:                                              ; preds = %161, %156
  %166 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %157, i64 0, i32 1
  %167 = load ptr, ptr %166, align 8, !tbaa !297
  %168 = icmp eq ptr %167, null
  br i1 %168, label %173, label %169

169:                                              ; preds = %165
  %170 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %167, i64 0, i32 3
  %171 = load i8, ptr %170, align 8, !tbaa !300, !range !82, !noundef !83
  %172 = icmp eq i8 %171, 0
  br i1 %172, label %184, label %173

173:                                              ; preds = %169, %165
  %174 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %157, i64 0, i32 3
  store i8 0, ptr %174, align 8, !tbaa !300
  %175 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %157, i64 0, i32 2
  %176 = load ptr, ptr %175, align 8, !tbaa !229
  %177 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %176, i64 0, i32 3
  %178 = load i8, ptr %177, align 8, !tbaa !300, !range !82, !noundef !83
  %179 = icmp eq i8 %178, 0
  %180 = icmp eq ptr %176, %158
  %181 = select i1 %179, i1 true, i1 %180
  br i1 %181, label %182, label %208

182:                                              ; preds = %173
  %183 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %176, i64 0, i32 3
  store i8 1, ptr %183, align 8, !tbaa !300
  br label %218

184:                                              ; preds = %169
  br i1 %160, label %192, label %185

185:                                              ; preds = %184
  %186 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %159, i64 0, i32 3
  %187 = load i8, ptr %186, align 8, !tbaa !300, !range !82
  %188 = icmp eq i8 %187, 0
  br i1 %188, label %199, label %189

189:                                              ; preds = %185
  %190 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %157, i64 0, i32 1
  %191 = load ptr, ptr %190, align 8, !tbaa !297
  br label %192

192:                                              ; preds = %189, %184
  %193 = phi ptr [ %191, %189 ], [ %167, %184 ]
  %194 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %193, i64 0, i32 3
  store i8 1, ptr %194, align 8, !tbaa !300
  %195 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %157, i64 0, i32 3
  store i8 0, ptr %195, align 8, !tbaa !300
  tail call void @_ZNSt6__ndk118__tree_left_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef nonnull %157) #26
  %196 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %157, i64 0, i32 2
  %197 = load ptr, ptr %196, align 8, !tbaa !229
  %198 = load ptr, ptr %197, align 8, !tbaa !296
  br label %199

199:                                              ; preds = %192, %185, %161
  %200 = phi ptr [ %198, %192 ], [ %159, %185 ], [ %159, %161 ]
  %201 = phi ptr [ %197, %192 ], [ %157, %185 ], [ %157, %161 ]
  %202 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %201, i64 0, i32 2
  %203 = load ptr, ptr %202, align 8, !tbaa !229
  %204 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %203, i64 0, i32 3
  %205 = load i8, ptr %204, align 8, !tbaa !300, !range !82, !noundef !83
  %206 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %201, i64 0, i32 3
  store i8 %205, ptr %206, align 8, !tbaa !300
  store i8 1, ptr %204, align 8, !tbaa !300
  %207 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %200, i64 0, i32 3
  store i8 1, ptr %207, align 8, !tbaa !300
  tail call void @_ZNSt6__ndk119__tree_right_rotateB7v170000IPNS_16__tree_node_baseIPvEEEEvT_(ptr noundef %203) #26
  br label %218

208:                                              ; preds = %173, %116
  %209 = phi ptr [ %114, %116 ], [ %176, %173 ]
  %210 = phi ptr [ %96, %116 ], [ %158, %173 ]
  %211 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %209, i64 0, i32 2
  %212 = load ptr, ptr %211, align 8, !tbaa !229
  %213 = load ptr, ptr %212, align 8, !tbaa !296
  %214 = icmp eq ptr %213, %209
  %215 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %212, i64 0, i32 1
  %216 = select i1 %214, ptr %215, ptr %212
  %217 = load ptr, ptr %216, align 8, !tbaa !16
  br label %76, !llvm.loop !329

218:                                              ; preds = %199, %182, %139, %120, %74, %69
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk111__tree_nextB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_(ptr noundef %0) local_unnamed_addr #19 comdat {
  %2 = icmp ne ptr %0, null
  tail call void @llvm.assume(i1 %2)
  %3 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !297
  %5 = icmp eq ptr %4, null
  br i1 %5, label %8, label %6

6:                                                ; preds = %1
  %7 = tail call noundef ptr @_ZNSt6__ndk110__tree_minB7v170000IPNS_16__tree_node_baseIPvEEEET_S5_(ptr noundef nonnull %4) #26
  br label %14

8:                                                ; preds = %8, %1
  %9 = phi ptr [ %11, %8 ], [ %0, %1 ]
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %9, i64 0, i32 2
  %11 = load ptr, ptr %10, align 8, !tbaa !229
  %12 = load ptr, ptr %11, align 8, !tbaa !296
  %13 = icmp eq ptr %12, %9
  br i1 %13, label %14, label %8, !llvm.loop !330

14:                                               ; preds = %8, %6
  %15 = phi ptr [ %7, %6 ], [ %11, %8 ]
  ret ptr %15
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 {
  %2 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %3 = load ptr, ptr %2, align 8, !tbaa !296
  tail call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE7destroyEPNS_11__tree_nodeIjPvEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %3) #26
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE7destroyEPNS_11__tree_nodeIjPvEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 !type !331 {
  %3 = icmp eq ptr %1, null
  br i1 %3, label %4, label %5

4:                                                ; preds = %5, %2
  ret void

5:                                                ; preds = %2
  %6 = load ptr, ptr %1, align 8, !tbaa !296
  tail call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE7destroyEPNS_11__tree_nodeIjPvEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %6) #26
  %7 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %1, i64 0, i32 1
  %8 = load ptr, ptr %7, align 8, !tbaa !297
  tail call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE7destroyEPNS_11__tree_nodeIjPvEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %8) #26
  tail call void @_ZdlPv(ptr noundef nonnull %1) #28
  br label %4
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEaSEOS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #19 comdat align 2 !type !332 {
  tail call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE13__move_assignERS5_NS_17integral_constantIbLb1EEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i8 undef) #26
  ret ptr %0
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE13__move_assignERS5_NS_17integral_constantIbLb1EEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i8 %2) local_unnamed_addr #19 comdat align 2 !type !333 {
  %4 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %5 = load ptr, ptr %4, align 8, !tbaa !296
  tail call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE7destroyEPNS_11__tree_nodeIjPvEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %5) #26
  %6 = load ptr, ptr %1, align 8, !tbaa !222
  store ptr %6, ptr %0, align 8, !tbaa !222
  %7 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %1, i64 0, i32 1
  %8 = load i64, ptr %7, align 8, !tbaa !16
  store i64 %8, ptr %4, align 8, !tbaa !16
  %9 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %1, i64 0, i32 2
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 2
  %11 = load i64, ptr %9, align 8
  store i64 %11, ptr %10, align 8
  %12 = icmp eq i64 %11, 0
  br i1 %12, label %13, label %14

13:                                               ; preds = %3
  store ptr %4, ptr %0, align 8, !tbaa !16
  br label %17

14:                                               ; preds = %3
  %15 = inttoptr i64 %8 to ptr
  %16 = getelementptr inbounds %"class.std::__ndk1::__tree_node_base", ptr %15, i64 0, i32 2
  store ptr %4, ptr %16, align 8, !tbaa !229
  store ptr %7, ptr %1, align 8, !tbaa !16
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %7, i8 0, i64 16, i1 false)
  br label %17

17:                                               ; preds = %14, %13
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE7reserveEm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 !type !334 {
  %3 = alloca %"struct.std::__ndk1::__split_buffer.127", align 8
  %4 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %0, i64 0, i32 2
  %5 = load ptr, ptr %4, align 8, !tbaa !16
  %6 = load ptr, ptr %0, align 8, !tbaa !111
  %7 = ptrtoint ptr %5 to i64
  %8 = ptrtoint ptr %6 to i64
  %9 = sub i64 %7, %8
  %10 = ashr exact i64 %9, 4
  %11 = icmp ult i64 %10, %1
  br i1 %11, label %12, label %34

12:                                               ; preds = %2
  %13 = icmp ugt i64 %1, 1152921504606846975
  br i1 %13, label %14, label %15

14:                                               ; preds = %12
  tail call void @_ZNKSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

15:                                               ; preds = %12
  call void @llvm.lifetime.start.p0(i64 40, ptr nonnull %3) #24
  %16 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %0, i64 0, i32 1
  %17 = load ptr, ptr %16, align 8, !tbaa !112
  %18 = ptrtoint ptr %17 to i64
  %19 = sub i64 %18, %8
  %20 = ashr exact i64 %19, 4
  %21 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %3, i64 0, i32 3
  %22 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %3, i64 0, i32 3, i32 1
  store ptr %4, ptr %22, align 8, !tbaa !16
  %23 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIN6asmjit5v1_163a643GpXEEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS9_m(ptr noundef nonnull align 1 dereferenceable(1) %4, i64 noundef %1) #27
  %24 = extractvalue [2 x i64] %23, 0
  %25 = inttoptr i64 %24 to ptr
  %26 = extractvalue [2 x i64] %23, 1
  store ptr %25, ptr %3, align 8, !tbaa !335
  %27 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %25, i64 %20
  %28 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %3, i64 0, i32 2
  store ptr %27, ptr %28, align 8, !tbaa !339
  %29 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %3, i64 0, i32 1
  store ptr %27, ptr %29, align 8, !tbaa !340
  %30 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %25, i64 %26
  store ptr %30, ptr %21, align 8, !tbaa !16
  invoke void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE26__swap_out_circular_bufferERNS_14__split_bufferIS4_RS6_EE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %3) #27
          to label %31 unwind label %32

31:                                               ; preds = %15
  call void @_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  br label %34

32:                                               ; preds = %15
  %33 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  resume { ptr, i32 } %33

34:                                               ; preds = %31, %2
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN15RegProtectGuard15appendOneUniqueERKN6asmjit5v1_163a643GpXE(ptr noundef nonnull align 8 dereferenceable(68) %0, ptr noundef nonnull align 4 dereferenceable(16) %1) local_unnamed_addr #1 comdat align 2 !type !341 {
  %3 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %1, i64 0, i32 1
  %4 = load i32, ptr %3, align 4, !tbaa !44
  %5 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1
  %6 = load ptr, ptr %5, align 8, !tbaa !111
  %7 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1, i32 1
  %8 = load ptr, ptr %7, align 8, !tbaa !112
  br label %9

9:                                                ; preds = %12, %2
  %10 = phi ptr [ %6, %2 ], [ %16, %12 ]
  %11 = icmp eq ptr %10, %8
  br i1 %11, label %17, label %12

12:                                               ; preds = %9
  %13 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %10, i64 0, i32 1
  %14 = load i32, ptr %13, align 4, !tbaa !44
  %15 = icmp eq i32 %14, %4
  %16 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %10, i64 1
  br i1 %15, label %18, label %9

17:                                               ; preds = %9
  tail call void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE9push_backB7v170000ERKS4_(ptr noundef nonnull align 8 dereferenceable(24) %5, ptr noundef nonnull align 4 dereferenceable(16) %1) #27
  br label %18

18:                                               ; preds = %17, %12
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE9push_backB7v170000ERKS4_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(16) %1) local_unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 !type !342 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !112
  %5 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %0, i64 0, i32 2
  %6 = load ptr, ptr %5, align 8, !tbaa !16
  %7 = icmp eq ptr %4, %6
  br i1 %7, label %10, label %8

8:                                                ; preds = %2
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %4, ptr noundef nonnull align 4 dereferenceable(16) %1, i64 16, i1 false)
  %9 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %4, i64 1
  store ptr %9, ptr %3, align 8, !tbaa !112
  br label %11

10:                                               ; preds = %2
  tail call void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE21__push_back_slow_pathIRKS4_EEvOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(16) %1) #27
  br label %11

11:                                               ; preds = %10, %8
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN15RegProtectGuard11doPushPairsEv(ptr noundef nonnull align 8 dereferenceable(68) %0) local_unnamed_addr #1 comdat align 2 !type !153 {
  %2 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %3 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %4 = alloca %"class.asmjit::v1_16::arm::Mem", align 4
  %5 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1
  %6 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 1, i32 1
  %7 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4
  %8 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %2, i64 0, i32 1
  %9 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  %10 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %2, i64 0, i32 2
  %11 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2
  %12 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %2, i64 0, i32 2, i64 1
  %13 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 4, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2, i64 1
  %14 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %3, i64 0, i32 1
  %15 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %3, i64 0, i32 2
  %16 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %3, i64 0, i32 2, i64 1
  %17 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 5
  %18 = getelementptr inbounds %class.RegProtectGuard, ptr %0, i64 0, i32 5, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  %19 = getelementptr inbounds i8, ptr %4, i64 4
  %20 = getelementptr inbounds i8, ptr %4, i64 8
  %21 = getelementptr inbounds i8, ptr %4, i64 12
  br label %22

22:                                               ; preds = %74, %1
  %23 = phi i64 [ 0, %1 ], [ %75, %74 ]
  %24 = load ptr, ptr %6, align 8, !tbaa !112
  %25 = load ptr, ptr %5, align 8, !tbaa !111
  %26 = ptrtoint ptr %24 to i64
  %27 = ptrtoint ptr %25 to i64
  %28 = sub i64 %26, %27
  %29 = ashr exact i64 %28, 4
  %30 = icmp ult i64 %23, %29
  br i1 %30, label %32, label %31

31:                                               ; preds = %22
  ret void

32:                                               ; preds = %22
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %2) #24
  %33 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %25, i64 %23
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %33, i64 16, i1 false)
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %3) #24
  %34 = or i64 %23, 1
  %35 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %25, i64 %34
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %3, ptr noundef nonnull align 4 dereferenceable(16) %35, i64 16, i1 false)
  %36 = load i32, ptr %2, align 4, !tbaa !39
  %37 = load i32, ptr %7, align 4, !tbaa !39
  %38 = icmp eq i32 %36, %37
  %39 = load i32, ptr %8, align 4, !tbaa !44
  %40 = load i32, ptr %9, align 8, !tbaa !44
  %41 = icmp eq i32 %39, %40
  %42 = and i1 %38, %41
  %43 = load i32, ptr %10, align 4, !tbaa !46
  %44 = load i32, ptr %11, align 4, !tbaa !46
  %45 = icmp eq i32 %43, %44
  %46 = and i1 %42, %45
  %47 = load i32, ptr %12, align 4, !tbaa !46
  %48 = load i32, ptr %13, align 8, !tbaa !46
  %49 = icmp eq i32 %47, %48
  %50 = and i1 %46, %49
  br i1 %50, label %51, label %63

51:                                               ; preds = %32
  %52 = load i32, ptr %3, align 4, !tbaa !39
  %53 = icmp eq i32 %52, %36
  %54 = load i32, ptr %14, align 4, !tbaa !44
  %55 = icmp eq i32 %54, %39
  %56 = and i1 %53, %55
  %57 = load i32, ptr %15, align 4, !tbaa !46
  %58 = icmp eq i32 %57, %43
  %59 = and i1 %56, %58
  %60 = load i32, ptr %16, align 4, !tbaa !46
  %61 = icmp eq i32 %60, %47
  %62 = and i1 %59, %61
  br i1 %62, label %74, label %63

63:                                               ; preds = %51, %32
  %64 = load ptr, ptr %0, align 8, !tbaa !101
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  %65 = load i32, ptr %17, align 4, !tbaa !39, !noalias !343
  %66 = and i32 %65, 248
  %67 = load i32, ptr %18, align 8, !tbaa !44, !noalias !343
  store i32 %67, ptr %19, align 4
  store i32 0, ptr %20, align 4
  %68 = or i32 %66, 16777218
  store i32 %68, ptr %4, align 4, !tbaa !39, !alias.scope !346
  %69 = icmp eq i32 %66, 0
  store i32 -16, ptr %21, align 4, !tbaa !46
  br i1 %69, label %70, label %72

70:                                               ; preds = %63
  %71 = add i32 %67, -1
  store i32 %71, ptr %19, align 4, !tbaa !44, !alias.scope !346
  br label %72

72:                                               ; preds = %70, %63
  %73 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3stpERKNS1_2GpES7_RKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %64, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  br label %74

74:                                               ; preds = %72, %51
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %3) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %2) #24
  %75 = add i64 %23, 2
  br label %22, !llvm.loop !349
}

; Function Attrs: minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #12 comdat align 2 !type !350 {
  tail call void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef nonnull @.str.17) #31
  unreachable
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE26__swap_out_circular_bufferERNS_14__split_bufferIS4_RS6_EE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %1) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 !type !351 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !112
  %5 = load ptr, ptr %0, align 8, !tbaa !111
  %6 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %1, i64 0, i32 1
  %7 = load ptr, ptr %6, align 8, !tbaa !340
  br label %8

8:                                                ; preds = %12, %2
  %9 = phi ptr [ %7, %2 ], [ %13, %12 ]
  %10 = phi ptr [ %4, %2 ], [ %14, %12 ]
  %11 = icmp eq ptr %10, %5
  br i1 %11, label %15, label %12

12:                                               ; preds = %8
  %13 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %9, i64 -1
  %14 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %10, i64 -1
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %13, ptr noundef nonnull align 4 dereferenceable(16) %14, i64 16, i1 false)
  br label %8, !llvm.loop !352

15:                                               ; preds = %8
  store ptr %9, ptr %6, align 8, !tbaa !340
  %16 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr %9, ptr %0, align 8, !tbaa !16
  store ptr %16, ptr %6, align 8, !tbaa !16
  %17 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %1, i64 0, i32 2
  %18 = load ptr, ptr %3, align 8, !tbaa !16
  %19 = load ptr, ptr %17, align 8, !tbaa !16
  store ptr %19, ptr %3, align 8, !tbaa !16
  store ptr %18, ptr %17, align 8, !tbaa !16
  %20 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %0, i64 0, i32 2
  %21 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %1, i64 0, i32 3
  %22 = load ptr, ptr %20, align 8, !tbaa !16
  %23 = load ptr, ptr %21, align 8, !tbaa !16
  store ptr %23, ptr %20, align 8, !tbaa !16
  store ptr %22, ptr %21, align 8, !tbaa !16
  %24 = load ptr, ptr %6, align 8, !tbaa !340
  store ptr %24, ptr %1, align 8, !tbaa !335
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  tail call void @_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEE5clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) #26
  %2 = load ptr, ptr %0, align 8, !tbaa !335
  %3 = icmp eq ptr %2, null
  br i1 %3, label %5, label %4

4:                                                ; preds = %1
  tail call void @_ZdlPv(ptr noundef nonnull %2) #28
  br label %5

5:                                                ; preds = %4, %1
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIN6asmjit5v1_163a643GpXEEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS9_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat {
  %3 = tail call noundef ptr @_ZNSt6__ndk19allocatorIN6asmjit5v1_163a643GpXEE8allocateB7v170000Em(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) #27
  %4 = ptrtoint ptr %3 to i64
  %5 = insertvalue [2 x i64] poison, i64 %4, 0
  %6 = insertvalue [2 x i64] %5, i64 %1, 1
  ret [2 x i64] %6
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk19allocatorIN6asmjit5v1_163a643GpXEE8allocateB7v170000Em(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 !type !353 {
  %3 = icmp ugt i64 %1, 1152921504606846975
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZSt28__throw_bad_array_new_lengthB7v170000v() #31
  unreachable

5:                                                ; preds = %2
  %6 = shl nuw i64 %1, 4
  %7 = tail call noalias noundef nonnull ptr @_Znwm(i64 noundef %6) #30
  ret ptr %7
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEE5clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) local_unnamed_addr #19 comdat align 2 !type !354 {
  %2 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %0, i64 0, i32 1
  %3 = load ptr, ptr %2, align 8, !tbaa !340
  tail call void @_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEE17__destruct_at_endB7v170000EPS4_(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef %3) #26
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEE17__destruct_at_endB7v170000EPS4_(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 !type !355 {
  %3 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %0, i64 0, i32 2
  %4 = load ptr, ptr %3, align 8, !tbaa !339
  br label %5

5:                                                ; preds = %8, %2
  %6 = phi ptr [ %9, %8 ], [ %4, %2 ]
  %7 = icmp eq ptr %6, %1
  br i1 %7, label %10, label %8

8:                                                ; preds = %5
  %9 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %6, i64 -1
  store ptr %9, ptr %3, align 8, !tbaa !339
  br label %5

10:                                               ; preds = %5
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE21__push_back_slow_pathIRKS4_EEvOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(16) %1) local_unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 !type !342 {
  %3 = alloca %"struct.std::__ndk1::__split_buffer.127", align 8
  %4 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %0, i64 0, i32 2
  call void @llvm.lifetime.start.p0(i64 40, ptr nonnull %3) #24
  %5 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %0, i64 0, i32 1
  %6 = load ptr, ptr %5, align 8, !tbaa !112
  %7 = load ptr, ptr %0, align 8, !tbaa !111
  %8 = ptrtoint ptr %6 to i64
  %9 = ptrtoint ptr %7 to i64
  %10 = sub i64 %8, %9
  %11 = ashr exact i64 %10, 4
  %12 = add nsw i64 %11, 1
  %13 = tail call noundef i64 @_ZNKSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE11__recommendB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %12) #27
  %14 = load ptr, ptr %5, align 8, !tbaa !112
  %15 = load ptr, ptr %0, align 8, !tbaa !111
  %16 = ptrtoint ptr %14 to i64
  %17 = ptrtoint ptr %15 to i64
  %18 = sub i64 %16, %17
  %19 = ashr exact i64 %18, 4
  %20 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %3, i64 0, i32 3
  %21 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %3, i64 0, i32 3, i32 1
  store ptr %4, ptr %21, align 8, !tbaa !16
  %22 = icmp eq i64 %13, 0
  br i1 %22, label %28, label %23

23:                                               ; preds = %2
  %24 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIN6asmjit5v1_163a643GpXEEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS9_m(ptr noundef nonnull align 1 dereferenceable(1) %4, i64 noundef %13) #27
  %25 = extractvalue [2 x i64] %24, 0
  %26 = inttoptr i64 %25 to ptr
  %27 = extractvalue [2 x i64] %24, 1
  br label %28

28:                                               ; preds = %23, %2
  %29 = phi ptr [ %26, %23 ], [ null, %2 ]
  %30 = phi i64 [ %27, %23 ], [ 0, %2 ]
  store ptr %29, ptr %3, align 8, !tbaa !335
  %31 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %29, i64 %19
  %32 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %3, i64 0, i32 2
  %33 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer.127", ptr %3, i64 0, i32 1
  store ptr %31, ptr %33, align 8, !tbaa !340
  %34 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %29, i64 %30
  store ptr %34, ptr %20, align 8, !tbaa !16
  %35 = icmp ne ptr %29, null
  tail call void @llvm.assume(i1 %35)
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %31, ptr noundef nonnull align 4 dereferenceable(16) %1, i64 16, i1 false)
  %36 = getelementptr inbounds %"class.asmjit::v1_16::a64::GpX", ptr %31, i64 1
  store ptr %36, ptr %32, align 8, !tbaa !339
  invoke void @_ZNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE26__swap_out_circular_bufferERNS_14__split_bufferIS4_RS6_EE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %3) #27
          to label %37 unwind label %38

37:                                               ; preds = %28
  call void @_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  ret void

38:                                               ; preds = %28
  %39 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  resume { ptr, i32 } %39
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNKSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE11__recommendB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #10 comdat align 2 personality ptr @__gxx_personality_v0 !type !356 {
  %3 = icmp ugt i64 %1, 1152921504606846975
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZNKSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

5:                                                ; preds = %2
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.37", ptr %0, i64 0, i32 2
  %7 = load ptr, ptr %6, align 8, !tbaa !16
  %8 = load ptr, ptr %0, align 8, !tbaa !111
  %9 = ptrtoint ptr %7 to i64
  %10 = ptrtoint ptr %8 to i64
  %11 = sub i64 %9, %10
  %12 = icmp ult i64 %11, 9223372036854775792
  %13 = lshr exact i64 %11, 3
  %14 = tail call i64 @llvm.umax.i64(i64 %13, i64 %1)
  %15 = select i1 %12, i64 %14, i64 1152921504606846975
  ret i64 %15
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3stpERKNS1_2GpES7_RKNS0_3arm3MemE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3) local_unnamed_addr #10 comdat align 2 !type !161 {
  %5 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_S4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 325, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3) #27
  ret i32 %5
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEC2ERKS4_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = alloca %"struct.std::__ndk1::__exception_guard.139", align 8
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %3) #24
  store ptr %0, ptr %3, align 8, !tbaa.struct !15, !alias.scope !357
  %4 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard.139", ptr %3, i64 0, i32 1
  store i8 0, ptr %4, align 8, !tbaa !360, !alias.scope !357
  %5 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %1, i64 0, i32 1
  %6 = load ptr, ptr %5, align 8, !tbaa !236
  %7 = load ptr, ptr %1, align 8, !tbaa !232
  %8 = ptrtoint ptr %6 to i64
  %9 = ptrtoint ptr %7 to i64
  %10 = sub i64 %8, %9
  %11 = sdiv exact i64 %10, 56
  %12 = icmp eq ptr %6, %7
  br i1 %12, label %19, label %13

13:                                               ; preds = %2
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE11__vallocateB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %11) #27
          to label %14 unwind label %17

14:                                               ; preds = %13
  %15 = load ptr, ptr %1, align 8, !tbaa !232
  %16 = load ptr, ptr %5, align 8, !tbaa !236
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE18__construct_at_endIPS1_Li0EEEvT_S7_m(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %15, ptr noundef %16, i64 noundef %11) #27
          to label %19 unwind label %17

17:                                               ; preds = %14, %13
  %18 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorI8Arm64ArgNS_9allocatorIS2_EEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %3) #26
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %3) #24
  resume { ptr, i32 } %18

19:                                               ; preds = %14, %2
  store i8 1, ptr %4, align 8, !tbaa !360
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorI8Arm64ArgNS_9allocatorIS2_EEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %3) #26
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %3) #24
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE11__vallocateB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 !type !231 {
  %3 = icmp ugt i64 %1, 329406144173384850
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZNKSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

5:                                                ; preds = %2
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 2
  %7 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorI8Arm64ArgEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS6_m(ptr noundef nonnull align 1 dereferenceable(1) %6, i64 noundef %1) #27
  %8 = extractvalue [2 x i64] %7, 0
  %9 = inttoptr i64 %8 to ptr
  %10 = extractvalue [2 x i64] %7, 1
  store ptr %9, ptr %0, align 8, !tbaa !232
  %11 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 1
  store ptr %9, ptr %11, align 8, !tbaa !236
  %12 = getelementptr inbounds %struct.Arm64Arg, ptr %9, i64 %10
  store ptr %12, ptr %6, align 8, !tbaa !16
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE18__construct_at_endIPS1_Li0EEEvT_S7_m(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, ptr noundef %2, i64 noundef %3) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 !type !363 {
  %5 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 1
  %6 = load ptr, ptr %5, align 8, !tbaa !236
  %7 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 2
  %8 = invoke noundef ptr @_ZNSt6__ndk130__uninitialized_allocator_copyB7v170000INS_9allocatorI8Arm64ArgEEPS2_S4_S4_EET2_RT_T0_T1_S5_(ptr noundef nonnull align 1 dereferenceable(1) %7, ptr noundef %1, ptr noundef %2, ptr noundef %6) #27
          to label %9 unwind label %10

9:                                                ; preds = %4
  store ptr %8, ptr %5, align 8, !tbaa !236
  ret void

10:                                               ; preds = %4
  %11 = landingpad { ptr, i32 }
          cleanup
  store ptr %6, ptr %5, align 8, !tbaa !236
  resume { ptr, i32 } %11
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk117__exception_guardINS_6vectorI8Arm64ArgNS_9allocatorIS2_EEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard.139", ptr %0, i64 0, i32 1
  %3 = load i8, ptr %2, align 8, !tbaa !360, !range !82, !noundef !83
  %4 = icmp eq i8 %3, 0
  br i1 %4, label %5, label %6

5:                                                ; preds = %1
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) #27
          to label %6 unwind label %7

6:                                                ; preds = %5, %1
  ret void

7:                                                ; preds = %5
  %8 = landingpad { ptr, i32 }
          catch ptr null
  %9 = extractvalue { ptr, i32 } %8, 0
  tail call void @__clang_call_terminate(ptr %9) #29
  unreachable
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk130__uninitialized_allocator_copyB7v170000INS_9allocatorI8Arm64ArgEEPS2_S4_S4_EET2_RT_T0_T1_S5_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef %1, ptr noundef %2, ptr noundef %3) local_unnamed_addr #1 comdat personality ptr @__gxx_personality_v0 {
  br label %5

5:                                                ; preds = %9, %4
  %6 = phi ptr [ %3, %4 ], [ %19, %9 ]
  %7 = phi ptr [ %1, %4 ], [ %18, %9 ]
  %8 = icmp eq ptr %7, %2
  br i1 %8, label %20, label %9

9:                                                ; preds = %5
  %10 = icmp ne ptr %6, null
  tail call void @llvm.assume(i1 %10)
  %11 = load i8, ptr %7, align 8, !tbaa !238
  store i8 %11, ptr %6, align 8, !tbaa !238
  %12 = getelementptr inbounds %struct.Arm64Arg, ptr %6, i64 0, i32 2
  %13 = getelementptr inbounds %struct.Arm64Arg, ptr %7, i64 0, i32 2
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %12, ptr noundef nonnull align 4 dereferenceable(16) %13, i64 16, i1 false)
  %14 = getelementptr inbounds %struct.Arm64Arg, ptr %6, i64 0, i32 3
  %15 = getelementptr inbounds %struct.Arm64Arg, ptr %7, i64 0, i32 3
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %14, ptr noundef nonnull align 4 dereferenceable(16) %15, i64 16, i1 false)
  %16 = getelementptr inbounds %struct.Arm64Arg, ptr %6, i64 0, i32 5
  %17 = getelementptr inbounds %struct.Arm64Arg, ptr %7, i64 0, i32 5
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(12) %16, ptr noundef nonnull align 8 dereferenceable(12) %17, i64 12, i1 false)
  %18 = getelementptr inbounds %struct.Arm64Arg, ptr %7, i64 1
  %19 = getelementptr inbounds %struct.Arm64Arg, ptr %6, i64 1
  br label %5, !llvm.loop !364

20:                                               ; preds = %5
  ret ptr %6
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 !type !365 {
  %2 = load ptr, ptr %0, align 8, !tbaa !366
  %3 = load ptr, ptr %2, align 8, !tbaa !232
  %4 = icmp eq ptr %3, null
  br i1 %4, label %7, label %5

5:                                                ; preds = %1
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %2, i64 0, i32 1
  store ptr %3, ptr %6, align 8, !tbaa !236
  tail call void @_ZdlPv(ptr noundef nonnull %3) #28
  br label %7

7:                                                ; preds = %5, %1
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZN11IdleRegPool4initEv(ptr noundef nonnull align 8 dereferenceable(48) %0) local_unnamed_addr #6 comdat align 2 !type !367 {
  %2 = alloca i32, align 4
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %2) #24
  br label %3

3:                                                ; preds = %13, %1
  %4 = phi i32 [ 0, %1 ], [ %15, %13 ]
  store i32 %4, ptr %2, align 4, !tbaa !46
  %5 = icmp ult i32 %4, 29
  br i1 %5, label %8, label %6

6:                                                ; preds = %3
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %2) #24
  %7 = getelementptr inbounds %class.IdleRegPool, ptr %0, i64 0, i32 1
  call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE5clearEv(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  ret void

8:                                                ; preds = %3
  %9 = icmp eq i32 %4, 18
  br i1 %9, label %13, label %10

10:                                               ; preds = %8
  %11 = call [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000ERKj(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %2) #27
  %12 = load i32, ptr %2, align 4, !tbaa !46
  br label %13

13:                                               ; preds = %10, %8
  %14 = phi i32 [ 18, %8 ], [ %12, %10 ]
  %15 = add i32 %14, 1
  br label %3, !llvm.loop !368
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZN11IdleRegPool6useOneERKN6asmjit5v1_163a643GpXE(ptr noundef nonnull align 8 dereferenceable(48) %0, ptr noundef nonnull align 4 dereferenceable(16) %1) local_unnamed_addr #6 comdat align 2 !type !369 {
  %3 = alloca i32, align 4
  %4 = alloca i32, align 4
  %5 = getelementptr inbounds %class.IdleRegPool, ptr %0, i64 0, i32 1
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %3) #24
  %6 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %1, i64 0, i32 1
  %7 = load i32, ptr %6, align 4, !tbaa !44
  store i32 %7, ptr %3, align 4, !tbaa !46
  %8 = call [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000EOj(ptr noundef nonnull align 8 dereferenceable(24) %5, ptr noundef nonnull align 4 dereferenceable(4) %3) #27
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %3) #24
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %4) #24
  %9 = load i32, ptr %6, align 4, !tbaa !44
  store i32 %9, ptr %4, align 4, !tbaa !46
  %10 = call noundef i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE14__erase_uniqueIjEEmRKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %4) #27
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %4) #24
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZN11IdleRegPool6useOneERKN6asmjit5v1_163a643GpWE(ptr noundef nonnull align 8 dereferenceable(48) %0, ptr noundef nonnull align 4 dereferenceable(16) %1) local_unnamed_addr #6 comdat align 2 !type !370 {
  %3 = alloca i32, align 4
  %4 = alloca i32, align 4
  %5 = getelementptr inbounds %class.IdleRegPool, ptr %0, i64 0, i32 1
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %3) #24
  %6 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %1, i64 0, i32 1
  %7 = load i32, ptr %6, align 4, !tbaa !44
  store i32 %7, ptr %3, align 4, !tbaa !46
  %8 = call [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000EOj(ptr noundef nonnull align 8 dereferenceable(24) %5, ptr noundef nonnull align 4 dereferenceable(4) %3) #27
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %3) #24
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %4) #24
  %9 = load i32, ptr %6, align 4, !tbaa !44
  store i32 %9, ptr %4, align 4, !tbaa !46
  %10 = call noundef i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE14__erase_uniqueIjEEmRKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 4 dereferenceable(4) %4) #27
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %4) #24
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE5clearEv(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #19 comdat align 2 !type !371 {
  %2 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 1
  %3 = load ptr, ptr %2, align 8, !tbaa !296
  tail call void @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE7destroyEPNS_11__tree_nodeIjPvEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %3) #26
  %4 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %0, i64 0, i32 2
  store i64 0, ptr %4, align 8, !tbaa !114
  store ptr %2, ptr %0, align 8, !tbaa !16
  store ptr null, ptr %2, align 8, !tbaa !296
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN11IdleRegPool8acquireXEv(ptr noalias sret(%"class.asmjit::v1_16::a64::GpX") align 4 %0, ptr noundef nonnull align 8 dereferenceable(48) %1) local_unnamed_addr #1 comdat align 2 !type !372 {
  %3 = alloca i32, align 4
  %4 = load ptr, ptr %1, align 8, !tbaa !16
  %5 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %1, i64 0, i32 1
  %6 = icmp eq ptr %4, %5
  br i1 %6, label %7, label %9

7:                                                ; preds = %2
  %8 = tail call i32 @puts(ptr nonnull dereferenceable(1) @str)
  br label %9

9:                                                ; preds = %7, %2
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %3) #24
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %4, i64 0, i32 1
  %11 = load i32, ptr %10, align 4, !tbaa !46
  store i32 %11, ptr %3, align 4, !tbaa !46
  %12 = call noundef i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE14__erase_uniqueIjEEmRKT_(ptr noundef nonnull align 8 dereferenceable(24) %1, ptr noundef nonnull align 4 dereferenceable(4) %3) #27
  %13 = getelementptr inbounds %class.IdleRegPool, ptr %1, i64 0, i32 1
  %14 = call [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000ERKj(ptr noundef nonnull align 8 dereferenceable(24) %13, ptr noundef nonnull align 4 dereferenceable(4) %3) #27
  %15 = load i32, ptr %3, align 4, !tbaa !46
  store i32 134217785, ptr %0, align 4, !tbaa !39, !alias.scope !373
  %16 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %0, i64 0, i32 1
  store i32 %15, ptr %16, align 4, !tbaa !44, !alias.scope !373
  %17 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %0, i64 0, i32 2
  store i32 0, ptr %17, align 4, !tbaa !46, !alias.scope !373
  %18 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %0, i64 0, i32 2, i64 1
  store i32 0, ptr %18, align 4, !tbaa !46, !alias.scope !373
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %3) #24
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN11IdleRegPool8acquireWEv(ptr noalias sret(%"class.asmjit::v1_16::a64::GpW") align 4 %0, ptr noundef nonnull align 8 dereferenceable(48) %1) local_unnamed_addr #1 comdat align 2 !type !376 {
  %3 = alloca i32, align 4
  %4 = load ptr, ptr %1, align 8, !tbaa !16
  %5 = getelementptr inbounds %"class.std::__ndk1::__tree", ptr %1, i64 0, i32 1
  %6 = icmp eq ptr %4, %5
  br i1 %6, label %7, label %9

7:                                                ; preds = %2
  %8 = tail call i32 @puts(ptr nonnull dereferenceable(1) @str.36)
  br label %9

9:                                                ; preds = %7, %2
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %3) #24
  %10 = getelementptr inbounds %"class.std::__ndk1::__tree_node", ptr %4, i64 0, i32 1
  %11 = load i32, ptr %10, align 4, !tbaa !46
  store i32 %11, ptr %3, align 4, !tbaa !46
  %12 = call noundef i64 @_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE14__erase_uniqueIjEEmRKT_(ptr noundef nonnull align 8 dereferenceable(24) %1, ptr noundef nonnull align 4 dereferenceable(4) %3) #27
  %13 = getelementptr inbounds %class.IdleRegPool, ptr %1, i64 0, i32 1
  %14 = call [2 x i64] @_ZNSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEE6insertB7v170000ERKj(ptr noundef nonnull align 8 dereferenceable(24) %13, ptr noundef nonnull align 4 dereferenceable(4) %3) #27
  %15 = load i32, ptr %3, align 4, !tbaa !46
  store i32 67108913, ptr %0, align 4, !tbaa !39, !alias.scope !377
  %16 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %0, i64 0, i32 1
  store i32 %15, ptr %16, align 4, !tbaa !44, !alias.scope !377
  %17 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %0, i64 0, i32 2
  store i32 0, ptr %17, align 4, !tbaa !46, !alias.scope !377
  %18 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %0, i64 0, i32 2, i64 1
  store i32 0, ptr %18, align 4, !tbaa !46, !alias.scope !377
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %3) #24
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZN8Arm64Arg1WERKN6asmjit5v1_163a643GpWE(ptr noalias sret(%struct.Arm64Arg) align 8 %0, ptr noundef nonnull align 4 dereferenceable(16) %1) local_unnamed_addr #19 comdat align 2 {
  tail call void @_ZN8Arm64ArgC2Ev(ptr noundef nonnull align 8 dereferenceable(52) %0) #26
  store i8 1, ptr %0, align 8, !tbaa !238
  %3 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 3
  %4 = load i32, ptr %1, align 4, !tbaa !257
  store i32 %4, ptr %3, align 4, !tbaa !257
  %5 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %1, i64 0, i32 1
  %6 = load i32, ptr %5, align 4, !tbaa !44
  %7 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 3, i32 0, i32 0, i32 0, i32 0, i32 0, i32 1
  store i32 %6, ptr %7, align 8, !tbaa !44
  %8 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %1, i64 0, i32 2
  %9 = load i32, ptr %8, align 4, !tbaa !46
  %10 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 3, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2
  store i32 %9, ptr %10, align 4, !tbaa !46
  %11 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %1, i64 0, i32 2, i64 1
  %12 = load i32, ptr %11, align 4, !tbaa !46
  %13 = getelementptr inbounds %struct.Arm64Arg, ptr %0, i64 0, i32 3, i32 0, i32 0, i32 0, i32 0, i32 0, i32 2, i64 1
  store i32 %12, ptr %13, align 8, !tbaa !46
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(52) ptr @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE12emplace_backIJRKS1_EEERS1_DpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %1) local_unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 !type !380 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !236
  %5 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 2
  %6 = load ptr, ptr %5, align 8, !tbaa !16
  %7 = icmp ult ptr %4, %6
  br i1 %7, label %8, label %17

8:                                                ; preds = %2
  %9 = load i8, ptr %1, align 8, !tbaa !238
  store i8 %9, ptr %4, align 8, !tbaa !238
  %10 = getelementptr inbounds %struct.Arm64Arg, ptr %4, i64 0, i32 2
  %11 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 2
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %10, ptr noundef nonnull align 4 dereferenceable(16) %11, i64 16, i1 false)
  %12 = getelementptr inbounds %struct.Arm64Arg, ptr %4, i64 0, i32 3
  %13 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 3
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %12, ptr noundef nonnull align 4 dereferenceable(16) %13, i64 16, i1 false)
  %14 = getelementptr inbounds %struct.Arm64Arg, ptr %4, i64 0, i32 5
  %15 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 5
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(12) %14, ptr noundef nonnull align 8 dereferenceable(12) %15, i64 12, i1 false)
  %16 = getelementptr inbounds %struct.Arm64Arg, ptr %4, i64 1
  store ptr %16, ptr %3, align 8, !tbaa !236
  br label %19

17:                                               ; preds = %2
  tail call void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE24__emplace_back_slow_pathIJRKS1_EEEvDpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %1) #27
  %18 = load ptr, ptr %3, align 8, !tbaa !236
  br label %19

19:                                               ; preds = %17, %8
  %20 = phi ptr [ %18, %17 ], [ %16, %8 ]
  %21 = getelementptr inbounds %struct.Arm64Arg, ptr %20, i64 -1
  ret ptr %21
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE24__emplace_back_slow_pathIJRKS1_EEEvDpOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(52) %1) local_unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 !type !381 {
  %3 = alloca %"struct.std::__ndk1::__split_buffer", align 8
  %4 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 2
  call void @llvm.lifetime.start.p0(i64 40, ptr nonnull %3) #24
  %5 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %0, i64 0, i32 1
  %6 = load ptr, ptr %5, align 8, !tbaa !236
  %7 = load ptr, ptr %0, align 8, !tbaa !232
  %8 = ptrtoint ptr %6 to i64
  %9 = ptrtoint ptr %7 to i64
  %10 = sub i64 %8, %9
  %11 = sdiv exact i64 %10, 56
  %12 = add nsw i64 %11, 1
  %13 = tail call noundef i64 @_ZNKSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE11__recommendB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %12) #27
  %14 = load ptr, ptr %5, align 8, !tbaa !236
  %15 = load ptr, ptr %0, align 8, !tbaa !232
  %16 = ptrtoint ptr %14 to i64
  %17 = ptrtoint ptr %15 to i64
  %18 = sub i64 %16, %17
  %19 = sdiv exact i64 %18, 56
  call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEEC2EmmS4_(ptr noundef nonnull align 8 dereferenceable(40) %3, i64 noundef %13, i64 noundef %19, ptr noundef nonnull align 1 dereferenceable(1) %4) #27
  %20 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %3, i64 0, i32 2
  %21 = load ptr, ptr %20, align 8, !tbaa !248, !nonnull !83
  %22 = load i8, ptr %1, align 8, !tbaa !238
  store i8 %22, ptr %21, align 8, !tbaa !238
  %23 = getelementptr inbounds %struct.Arm64Arg, ptr %21, i64 0, i32 2
  %24 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 2
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %23, ptr noundef nonnull align 4 dereferenceable(16) %24, i64 16, i1 false)
  %25 = getelementptr inbounds %struct.Arm64Arg, ptr %21, i64 0, i32 3
  %26 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 3
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %25, ptr noundef nonnull align 4 dereferenceable(16) %26, i64 16, i1 false)
  %27 = getelementptr inbounds %struct.Arm64Arg, ptr %21, i64 0, i32 5
  %28 = getelementptr inbounds %struct.Arm64Arg, ptr %1, i64 0, i32 5
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(12) %27, ptr noundef nonnull align 8 dereferenceable(12) %28, i64 12, i1 false)
  %29 = load ptr, ptr %20, align 8, !tbaa !248
  %30 = getelementptr inbounds %struct.Arm64Arg, ptr %29, i64 1
  store ptr %30, ptr %20, align 8, !tbaa !248
  invoke void @_ZNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE26__swap_out_circular_bufferERNS_14__split_bufferIS1_RS3_EE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %3) #27
          to label %31 unwind label %32

31:                                               ; preds = %2
  call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  ret void

32:                                               ; preds = %2
  %33 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #26
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #24
  resume { ptr, i32 } %33
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZN13kernel_module20kallsyms_lookup_nameEPKcRm(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(8) %1) local_unnamed_addr #10 comdat {
  %3 = alloca %"struct.kernel_module::SymbolHit", align 8
  call void @llvm.lifetime.start.p0(i64 1032, ptr nonnull %3) #24
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(1032) %3, i8 0, i64 1032, i1 false)
  %4 = call noundef i64 @_ZN13kernel_module20kallsyms_lookup_nameEPKcRNS_9SymbolHitENS_15SymbolMatchModeE(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(1032) %3, i8 noundef 0) #27
  %5 = load i64, ptr %3, align 8, !tbaa !382
  store i64 %5, ptr %1, align 8, !tbaa !114
  call void @llvm.lifetime.end.p0(i64 1032, ptr nonnull %3) #24
  ret i64 %4
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef i1 @_ZN26Aarch64KernelSymCallHelper15pushArgsFromVecEPN6asmjit5v1_163a649AssemblerERKNSt6__ndk16vectorI8Arm64ArgNS5_9allocatorIS7_EEEE(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #1 comdat align 2 {
  %3 = alloca %"class.asmjit::v1_16::Imm", align 4
  %4 = alloca %"class.asmjit::v1_16::Imm", align 4
  %5 = alloca %"class.asmjit::v1_16::Imm", align 4
  %6 = alloca %"class.asmjit::v1_16::Imm", align 4
  %7 = alloca %"class.asmjit::v1_16::Imm", align 4
  %8 = alloca %"class.asmjit::v1_16::Imm", align 4
  %9 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %10 = alloca %"class.asmjit::v1_16::a64::GpW", align 4
  %11 = alloca %"class.asmjit::v1_16::a64::GpX", align 4
  %12 = alloca %"class.asmjit::v1_16::a64::GpW", align 4
  %13 = getelementptr inbounds %"class.std::__ndk1::vector.94", ptr %1, i64 0, i32 1
  %14 = load ptr, ptr %13, align 8, !tbaa !236
  %15 = load ptr, ptr %1, align 8, !tbaa !232
  %16 = ptrtoint ptr %14 to i64
  %17 = ptrtoint ptr %15 to i64
  %18 = sub i64 %16, %17
  %19 = sdiv exact i64 %18, 56
  %20 = icmp ult i64 %19, 9
  br i1 %20, label %21, label %97

21:                                               ; preds = %2
  %22 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %12, i64 0, i32 1
  %23 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %12, i64 0, i32 2
  %24 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %12, i64 0, i32 2, i64 1
  %25 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %3, i64 0, i32 1
  %26 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %3, i64 0, i32 2
  %27 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %3, i64 0, i32 2, i64 1
  %28 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %4, i64 0, i32 1
  %29 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %4, i64 0, i32 2
  %30 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %4, i64 0, i32 2, i64 1
  %31 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %5, i64 0, i32 1
  %32 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %5, i64 0, i32 2
  %33 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %5, i64 0, i32 2, i64 1
  %34 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %6, i64 0, i32 1
  %35 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %6, i64 0, i32 2
  %36 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %6, i64 0, i32 2, i64 1
  %37 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %7, i64 0, i32 1
  %38 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %7, i64 0, i32 2
  %39 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %7, i64 0, i32 2, i64 1
  %40 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %8, i64 0, i32 1
  %41 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %8, i64 0, i32 2
  %42 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %8, i64 0, i32 2, i64 1
  %43 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %11, i64 0, i32 1
  %44 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %11, i64 0, i32 2
  %45 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %11, i64 0, i32 2, i64 1
  %46 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %10, i64 0, i32 1
  %47 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %10, i64 0, i32 2
  %48 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %10, i64 0, i32 2, i64 1
  %49 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %9, i64 0, i32 1
  %50 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %9, i64 0, i32 2
  %51 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %9, i64 0, i32 2, i64 1
  br label %52

52:                                               ; preds = %95, %21
  %53 = phi i64 [ %96, %95 ], [ 0, %21 ]
  %54 = icmp eq i64 %53, %19
  br i1 %54, label %97, label %55

55:                                               ; preds = %52
  %56 = load ptr, ptr %1, align 8, !tbaa !232
  %57 = getelementptr inbounds %struct.Arm64Arg, ptr %56, i64 %53
  %58 = load i8, ptr %57, align 8, !tbaa !238
  switch i8 %58, label %95 [
    i8 0, label %59
    i8 1, label %63
    i8 2, label %67
    i8 3, label %71
  ]

59:                                               ; preds = %55
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %9) #24
  %60 = trunc i64 %53 to i32
  store i32 134217785, ptr %9, align 4, !tbaa !39, !alias.scope !384
  store i32 %60, ptr %49, align 4, !tbaa !44, !alias.scope !384
  store i32 0, ptr %50, align 4, !tbaa !46, !alias.scope !384
  store i32 0, ptr %51, align 4, !tbaa !46, !alias.scope !384
  %61 = getelementptr inbounds %struct.Arm64Arg, ptr %56, i64 %53, i32 2
  %62 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %9, ptr noundef nonnull align 4 dereferenceable(16) %61) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %9) #24
  br label %95

63:                                               ; preds = %55
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %10) #24
  %64 = trunc i64 %53 to i32
  store i32 67108913, ptr %10, align 4, !tbaa !39, !alias.scope !387
  store i32 %64, ptr %46, align 4, !tbaa !44, !alias.scope !387
  store i32 0, ptr %47, align 4, !tbaa !46, !alias.scope !387
  store i32 0, ptr %48, align 4, !tbaa !46, !alias.scope !387
  %65 = getelementptr inbounds %struct.Arm64Arg, ptr %56, i64 %53, i32 3
  %66 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3movERKNS1_2GpES7_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %10, ptr noundef nonnull align 4 dereferenceable(16) %65) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %10) #24
  br label %95

67:                                               ; preds = %55
  %68 = trunc i64 %53 to i32
  store i32 134217785, ptr %11, align 4, !tbaa !39, !alias.scope !390
  store i32 %68, ptr %43, align 4, !tbaa !44, !alias.scope !390
  store i32 0, ptr %44, align 4, !tbaa !46, !alias.scope !390
  store i32 0, ptr %45, align 4, !tbaa !46, !alias.scope !390
  %69 = getelementptr inbounds %struct.Arm64Arg, ptr %56, i64 %53, i32 5
  %70 = load i64, ptr %69, align 8, !tbaa !258
  call fastcc void @_ZN12_GLOBAL__N_117aarch64_asm_mov_xEPN6asmjit5v1_163a649AssemblerENS2_3GpXEm(ptr noundef %0, ptr noundef nonnull %11, i64 noundef %70) #27
  br label %95

71:                                               ; preds = %55
  %72 = trunc i64 %53 to i32
  store i32 67108913, ptr %12, align 4, !tbaa !39, !alias.scope !393
  store i32 %72, ptr %22, align 4, !tbaa !44, !alias.scope !393
  store i32 0, ptr %23, align 4, !tbaa !46, !alias.scope !393
  store i32 0, ptr %24, align 4, !tbaa !46, !alias.scope !393
  %73 = getelementptr inbounds %struct.Arm64Arg, ptr %56, i64 %53, i32 6
  %74 = load i32, ptr %73, align 8, !tbaa !259
  br label %75

75:                                               ; preds = %90, %71
  %76 = phi i1 [ false, %71 ], [ %91, %90 ]
  %77 = phi i32 [ 0, %71 ], [ %92, %90 ]
  %78 = icmp eq i32 %77, 2
  br i1 %78, label %79, label %80

79:                                               ; preds = %75
  br i1 %76, label %95, label %93

80:                                               ; preds = %75
  %81 = shl nuw nsw i32 %77, 4
  %82 = lshr i32 %74, %81
  %83 = and i32 %82, 65535
  %84 = icmp eq i32 %83, 0
  br i1 %84, label %90, label %85

85:                                               ; preds = %80
  br i1 %76, label %88, label %86

86:                                               ; preds = %85
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %3) #24
  store i32 4, ptr %3, align 4, !tbaa !39
  store i32 0, ptr %25, align 4, !tbaa !44
  store i32 %83, ptr %26, align 4, !tbaa !46
  store i32 0, ptr %27, align 4, !tbaa !46
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  store i32 4, ptr %4, align 4, !tbaa !39
  store i32 0, ptr %28, align 4, !tbaa !44
  store i32 %81, ptr %29, align 4, !tbaa !46
  store i32 0, ptr %30, align 4, !tbaa !46
  %87 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movzERKNS1_2GpERKNS0_3ImmESA_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %12, ptr noundef nonnull align 4 dereferenceable(16) %3, ptr noundef nonnull align 4 dereferenceable(16) %4) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %3) #24
  br label %90

88:                                               ; preds = %85
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %5) #24
  store i32 4, ptr %5, align 4, !tbaa !39
  store i32 0, ptr %31, align 4, !tbaa !44
  store i32 %83, ptr %32, align 4, !tbaa !46
  store i32 0, ptr %33, align 4, !tbaa !46
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %6) #24
  store i32 4, ptr %6, align 4, !tbaa !39
  store i32 0, ptr %34, align 4, !tbaa !44
  store i32 %81, ptr %35, align 4, !tbaa !46
  store i32 0, ptr %36, align 4, !tbaa !46
  %89 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movkERKNS1_2GpERKNS0_3ImmESA_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %12, ptr noundef nonnull align 4 dereferenceable(16) %5, ptr noundef nonnull align 4 dereferenceable(16) %6) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %6) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %5) #24
  br label %90

90:                                               ; preds = %88, %86, %80
  %91 = phi i1 [ %76, %80 ], [ true, %88 ], [ true, %86 ]
  %92 = add nuw nsw i32 %77, 1
  br label %75, !llvm.loop !396

93:                                               ; preds = %79
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %7) #24
  store i32 4, ptr %7, align 4, !tbaa !39
  store i32 0, ptr %37, align 4, !tbaa !44
  store i32 0, ptr %38, align 4, !tbaa !46
  store i32 0, ptr %39, align 4, !tbaa !46
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %8) #24
  store i32 4, ptr %8, align 4, !tbaa !39
  store i32 0, ptr %40, align 4, !tbaa !44
  store i32 0, ptr %41, align 4, !tbaa !46
  store i32 0, ptr %42, align 4, !tbaa !46
  %94 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movzERKNS1_2GpERKNS0_3ImmESA_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %12, ptr noundef nonnull align 4 dereferenceable(16) %7, ptr noundef nonnull align 4 dereferenceable(16) %8) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %8) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %7) #24
  br label %95

95:                                               ; preds = %93, %79, %67, %63, %59, %55
  %96 = add i64 %53, 1
  br label %52, !llvm.loop !397

97:                                               ; preds = %52, %2
  ret i1 %20
}

; Function Attrs: minsize optsize uwtable
define internal fastcc void @_ZN12_GLOBAL__N_117aarch64_asm_mov_xEPN6asmjit5v1_163a649AssemblerENS2_3GpXEm(ptr noundef %0, ptr noundef %1, i64 noundef %2) unnamed_addr #1 {
  %4 = alloca %"class.asmjit::v1_16::Imm", align 4
  %5 = alloca %"class.asmjit::v1_16::Imm", align 4
  %6 = alloca %"class.asmjit::v1_16::Imm", align 4
  %7 = alloca %"class.asmjit::v1_16::Imm", align 4
  %8 = alloca %"class.asmjit::v1_16::Imm", align 4
  %9 = alloca %"class.asmjit::v1_16::Imm", align 4
  %10 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %4, i64 0, i32 1
  %11 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %4, i64 0, i32 2
  %12 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %4, i64 0, i32 2, i64 1
  %13 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %5, i64 0, i32 1
  %14 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %5, i64 0, i32 2
  %15 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %5, i64 0, i32 2, i64 1
  %16 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %6, i64 0, i32 1
  %17 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %6, i64 0, i32 2
  %18 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %6, i64 0, i32 2, i64 1
  %19 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %7, i64 0, i32 1
  %20 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %7, i64 0, i32 2
  %21 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %7, i64 0, i32 2, i64 1
  br label %22

22:                                               ; preds = %40, %3
  %23 = phi i64 [ %42, %40 ], [ 0, %3 ]
  %24 = phi i1 [ %41, %40 ], [ false, %3 ]
  %25 = icmp eq i64 %23, 4
  br i1 %25, label %26, label %27

26:                                               ; preds = %22
  br i1 %24, label %51, label %43

27:                                               ; preds = %22
  %28 = shl nuw nsw i64 %23, 4
  %29 = lshr i64 %2, %28
  %30 = and i64 %29, 65535
  %31 = icmp eq i64 %30, 0
  br i1 %31, label %40, label %32

32:                                               ; preds = %27
  %33 = trunc i64 %29 to i32
  %34 = and i32 %33, 65535
  %35 = trunc i64 %28 to i32
  br i1 %24, label %38, label %36

36:                                               ; preds = %32
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #24
  store i32 4, ptr %4, align 4, !tbaa !39
  store i32 0, ptr %10, align 4, !tbaa !44
  store i32 %34, ptr %11, align 4, !tbaa !46
  store i32 0, ptr %12, align 4, !tbaa !46
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %5) #24
  store i32 4, ptr %5, align 4, !tbaa !39
  store i32 0, ptr %13, align 4, !tbaa !44
  store i32 %35, ptr %14, align 4, !tbaa !46
  store i32 0, ptr %15, align 4, !tbaa !46
  %37 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movzERKNS1_2GpERKNS0_3ImmESA_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %4, ptr noundef nonnull align 4 dereferenceable(16) %5) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %5) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #24
  br label %40

38:                                               ; preds = %32
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %6) #24
  store i32 4, ptr %6, align 4, !tbaa !39
  store i32 0, ptr %16, align 4, !tbaa !44
  store i32 %34, ptr %17, align 4, !tbaa !46
  store i32 0, ptr %18, align 4, !tbaa !46
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %7) #24
  store i32 4, ptr %7, align 4, !tbaa !39
  store i32 0, ptr %19, align 4, !tbaa !44
  store i32 %35, ptr %20, align 4, !tbaa !46
  store i32 0, ptr %21, align 4, !tbaa !46
  %39 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movkERKNS1_2GpERKNS0_3ImmESA_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %6, ptr noundef nonnull align 4 dereferenceable(16) %7) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %7) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %6) #24
  br label %40

40:                                               ; preds = %38, %36, %27
  %41 = phi i1 [ %24, %27 ], [ true, %38 ], [ true, %36 ]
  %42 = add nuw nsw i64 %23, 1
  br label %22, !llvm.loop !398

43:                                               ; preds = %26
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %8) #24
  store i32 4, ptr %8, align 4, !tbaa !39
  %44 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %8, i64 0, i32 1
  store i32 0, ptr %44, align 4, !tbaa !44
  %45 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %8, i64 0, i32 2
  store i32 0, ptr %45, align 4, !tbaa !46
  %46 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %8, i64 0, i32 2, i64 1
  store i32 0, ptr %46, align 4, !tbaa !46
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %9) #24
  store i32 4, ptr %9, align 4, !tbaa !39
  %47 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %9, i64 0, i32 1
  store i32 0, ptr %47, align 4, !tbaa !44
  %48 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %9, i64 0, i32 2
  store i32 0, ptr %48, align 4, !tbaa !46
  %49 = getelementptr inbounds %"struct.asmjit::v1_16::Operand_", ptr %9, i64 0, i32 2, i64 1
  store i32 0, ptr %49, align 4, !tbaa !46
  %50 = call noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movzERKNS1_2GpERKNS0_3ImmESA_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %8, ptr noundef nonnull align 4 dereferenceable(16) %9) #27
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %9) #24
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %8) #24
  br label %51

51:                                               ; preds = %43, %26
  ret void
}

; Function Attrs: minsize optsize
declare noundef i64 @_ZN13kernel_module20kallsyms_lookup_nameEPKcRNS_9SymbolHitENS_15SymbolMatchModeE(ptr noundef, ptr noundef nonnull align 8 dereferenceable(1032), i8 noundef) local_unnamed_addr #8

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movzERKNS1_2GpERKNS0_3ImmESA_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3) local_unnamed_addr #10 comdat align 2 !type !399 {
  %5 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_S4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 247, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3) #27
  ret i32 %5
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE4movkERKNS1_2GpERKNS0_3ImmESA_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3) local_unnamed_addr #10 comdat align 2 !type !399 {
  %5 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_ES4_S4_(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 245, ptr noundef nonnull align 4 dereferenceable(16) %1, ptr noundef nonnull align 4 dereferenceable(16) %2, ptr noundef nonnull align 4 dereferenceable(16) %3) #27
  ret i32 %5
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEE3blrERKNS1_2GpE(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef nonnull align 4 dereferenceable(16) %1) local_unnamed_addr #10 comdat align 2 !type !400 {
  %3 = tail call noundef i32 @_ZN6asmjit5v1_1611BaseEmitter6_emitIEjRKNS0_8Operand_E(ptr noundef nonnull align 8 dereferenceable(144) %0, i32 noundef 36, ptr noundef nonnull align 4 dereferenceable(16) %1) #27
  ret i32 %3
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i32 @_ZNSt6__ndk111char_traitsIcE7compareEPKcS3_m(ptr noundef %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = icmp eq i64 %2, 0
  br i1 %4, label %7, label %5

5:                                                ; preds = %3
  %6 = tail call i32 @memcmp(ptr noundef %0, ptr noundef %1, i64 noundef %2) #26
  br label %7

7:                                                ; preds = %5, %3
  %8 = phi i32 [ %6, %5 ], [ 0, %3 ]
  ret i32 %8
}

; Function Attrs: minsize mustprogress nofree nounwind optsize willreturn memory(argmem: read)
declare i32 @memcmp(ptr nocapture noundef, ptr nocapture noundef, i64 noundef) local_unnamed_addr #17

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden i64 @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE5eraseENS_21__hash_const_iteratorIPNS_11__hash_nodeIS8_PvEEEE(ptr noundef nonnull align 8 dereferenceable(36) %0, i64 %1) local_unnamed_addr #1 comdat align 2 !type !401 {
  %3 = alloca %"class.std::__ndk1::unique_ptr.144", align 8
  %4 = inttoptr i64 %1 to ptr
  %5 = load ptr, ptr %4, align 8, !tbaa !197
  call void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE6removeENS_21__hash_const_iteratorIPNS_11__hash_nodeIS8_PvEEEE(ptr nonnull sret(%"class.std::__ndk1::unique_ptr.144") align 8 %3, ptr noundef nonnull align 8 dereferenceable(36) %0, i64 %1) #26
  call void @_ZNSt6__ndk110unique_ptrINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEENS_22__hash_node_destructorINS6_ISB_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %3) #26
  %6 = ptrtoint ptr %5 to i64
  ret i64 %6
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE6removeENS_21__hash_const_iteratorIPNS_11__hash_nodeIS8_PvEEEE(ptr noalias sret(%"class.std::__ndk1::unique_ptr.144") align 8 %0, ptr noundef nonnull align 8 dereferenceable(36) %1, i64 %2) local_unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 !type !402 {
  %4 = inttoptr i64 %2 to ptr
  %5 = getelementptr inbounds i8, ptr %1, i64 8
  %6 = load i64, ptr %5, align 8, !tbaa !114
  %7 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %4, i64 0, i32 1
  %8 = load i64, ptr %7, align 8, !tbaa !206
  %9 = tail call i64 @llvm.ctpop.i64(i64 %6), !range !205
  %10 = icmp ult i64 %9, 2
  br i1 %10, label %11, label %14

11:                                               ; preds = %3
  %12 = add i64 %6, -1
  %13 = and i64 %12, %8
  br label %18

14:                                               ; preds = %3
  %15 = icmp ult i64 %8, %6
  br i1 %15, label %18, label %16

16:                                               ; preds = %14
  %17 = urem i64 %8, %6
  br label %18

18:                                               ; preds = %16, %14, %11
  %19 = phi i64 [ %13, %11 ], [ %17, %16 ], [ %8, %14 ]
  %20 = load ptr, ptr %1, align 8, !tbaa !16
  %21 = getelementptr inbounds ptr, ptr %20, i64 %19
  %22 = load ptr, ptr %21, align 8, !tbaa !16
  br label %23

23:                                               ; preds = %23, %18
  %24 = phi ptr [ %22, %18 ], [ %25, %23 ]
  %25 = load ptr, ptr %24, align 8, !tbaa !197
  %26 = icmp eq ptr %25, %4
  br i1 %26, label %27, label %23, !llvm.loop !403

27:                                               ; preds = %23
  %28 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %1, i64 0, i32 1
  %29 = icmp eq ptr %24, %28
  br i1 %29, label %43, label %30

30:                                               ; preds = %27
  %31 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %24, i64 0, i32 1
  %32 = load i64, ptr %31, align 8, !tbaa !206
  br i1 %10, label %33, label %36

33:                                               ; preds = %30
  %34 = add i64 %6, -1
  %35 = and i64 %32, %34
  br label %40

36:                                               ; preds = %30
  %37 = icmp ult i64 %32, %6
  br i1 %37, label %40, label %38

38:                                               ; preds = %36
  %39 = urem i64 %32, %6
  br label %40

40:                                               ; preds = %38, %36, %33
  %41 = phi i64 [ %35, %33 ], [ %39, %38 ], [ %32, %36 ]
  %42 = icmp eq i64 %41, %19
  br i1 %42, label %60, label %43

43:                                               ; preds = %40, %27
  %44 = load ptr, ptr %4, align 8, !tbaa !197
  %45 = icmp eq ptr %44, null
  br i1 %45, label %59, label %46

46:                                               ; preds = %43
  %47 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %44, i64 0, i32 1
  %48 = load i64, ptr %47, align 8, !tbaa !206
  br i1 %10, label %49, label %52

49:                                               ; preds = %46
  %50 = add i64 %6, -1
  %51 = and i64 %48, %50
  br label %56

52:                                               ; preds = %46
  %53 = icmp ult i64 %48, %6
  br i1 %53, label %56, label %54

54:                                               ; preds = %52
  %55 = urem i64 %48, %6
  br label %56

56:                                               ; preds = %54, %52, %49
  %57 = phi i64 [ %51, %49 ], [ %55, %54 ], [ %48, %52 ]
  %58 = icmp eq i64 %57, %19
  br i1 %58, label %60, label %59

59:                                               ; preds = %56, %43
  store ptr null, ptr %21, align 8, !tbaa !16
  br label %60

60:                                               ; preds = %59, %56, %40
  %61 = load ptr, ptr %4, align 8, !tbaa !197
  %62 = icmp eq ptr %61, null
  br i1 %62, label %80, label %63

63:                                               ; preds = %60
  %64 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %61, i64 0, i32 1
  %65 = load i64, ptr %64, align 8, !tbaa !206
  br i1 %10, label %66, label %69

66:                                               ; preds = %63
  %67 = add i64 %6, -1
  %68 = and i64 %65, %67
  br label %73

69:                                               ; preds = %63
  %70 = icmp ult i64 %65, %6
  br i1 %70, label %73, label %71

71:                                               ; preds = %69
  %72 = urem i64 %65, %6
  br label %73

73:                                               ; preds = %71, %69, %66
  %74 = phi i64 [ %68, %66 ], [ %72, %71 ], [ %65, %69 ]
  %75 = icmp eq i64 %74, %19
  br i1 %75, label %80, label %76

76:                                               ; preds = %73
  %77 = load ptr, ptr %1, align 8, !tbaa !16
  %78 = getelementptr inbounds ptr, ptr %77, i64 %74
  store ptr %24, ptr %78, align 8, !tbaa !16
  %79 = load ptr, ptr %4, align 8, !tbaa !197
  br label %80

80:                                               ; preds = %76, %73, %60
  %81 = phi ptr [ %61, %73 ], [ %79, %76 ], [ null, %60 ]
  store ptr %81, ptr %24, align 8, !tbaa !197
  store ptr null, ptr %4, align 8, !tbaa !197
  %82 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %1, i64 0, i32 2
  %83 = load i64, ptr %82, align 8, !tbaa !114
  %84 = add i64 %83, -1
  store i64 %84, ptr %82, align 8, !tbaa !114
  store ptr %4, ptr %0, align 8, !tbaa !404
  %85 = getelementptr inbounds i8, ptr %0, i64 8
  store ptr %28, ptr %85, align 8, !tbaa.struct !292
  %86 = getelementptr inbounds i8, ptr %0, i64 16
  store i8 1, ptr %86, align 8, !tbaa.struct !406
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEENS_22__hash_node_destructorINS6_ISB_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 {
  tail call void @_ZNSt6__ndk110unique_ptrINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEENS_22__hash_node_destructorINS6_ISB_EEEEE5resetB7v170000EPSB_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef null) #26
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEENS_22__hash_node_destructorINS6_ISB_EEEEE5resetB7v170000EPSB_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 !type !407 {
  %3 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr %1, ptr %0, align 8, !tbaa !16
  %4 = icmp eq ptr %3, null
  br i1 %4, label %7, label %5

5:                                                ; preds = %2
  %6 = getelementptr inbounds i8, ptr %0, i64 8
  tail call void @_ZNSt6__ndk122__hash_node_destructorINS_9allocatorINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPvEEEEEclB7v170000EPSB_(ptr noundef nonnull align 8 dereferenceable(9) %6, ptr noundef nonnull %3) #26
  br label %7

7:                                                ; preds = %5, %2
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk122__hash_node_destructorINS_9allocatorINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPvEEEEEclB7v170000EPSB_(ptr noundef nonnull align 8 dereferenceable(9) %0, ptr noundef %1) local_unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 !type !408 {
  %3 = getelementptr inbounds %"class.std::__ndk1::__hash_node_destructor", ptr %0, i64 0, i32 1
  %4 = load i8, ptr %3, align 8, !tbaa !409, !range !82, !noundef !83
  %5 = icmp eq i8 %4, 0
  br i1 %5, label %8, label %6

6:                                                ; preds = %2
  %7 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %1, i64 0, i32 2, i32 0, i32 1
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #26
  br label %8

8:                                                ; preds = %6, %2
  %9 = icmp eq ptr %1, null
  br i1 %9, label %11, label %10

10:                                               ; preds = %8
  tail call void @_ZdlPv(ptr noundef nonnull %1) #28
  br label %11

11:                                               ; preds = %10, %8
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE25__emplace_unique_key_argsImJRKNS_21piecewise_construct_tENS_5tupleIJRKmEEENSN_IJEEEEEENS_4pairINS_15__hash_iteratorIPNS_11__hash_nodeIS8_PvEEEEbEERKT_DpOT0_(ptr noundef nonnull align 8 dereferenceable(36) %0, ptr noundef nonnull align 8 dereferenceable(8) %1, ptr noundef nonnull align 1 dereferenceable(1) %2, ptr noundef nonnull align 8 dereferenceable(8) %3, ptr noundef nonnull align 1 dereferenceable(1) %4) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 !type !411 {
  %6 = alloca %"class.std::__ndk1::unique_ptr.144", align 8
  %7 = load i64, ptr %1, align 8, !tbaa !114
  %8 = getelementptr inbounds i8, ptr %0, i64 8
  %9 = load i64, ptr %8, align 8, !tbaa !114
  %10 = icmp eq i64 %9, 0
  br i1 %10, label %51, label %11

11:                                               ; preds = %5
  %12 = tail call i64 @llvm.ctpop.i64(i64 %9), !range !205
  %13 = icmp ult i64 %12, 2
  br i1 %13, label %14, label %17

14:                                               ; preds = %11
  %15 = add i64 %9, -1
  %16 = and i64 %15, %7
  br label %21

17:                                               ; preds = %11
  %18 = icmp ult i64 %7, %9
  br i1 %18, label %21, label %19

19:                                               ; preds = %17
  %20 = urem i64 %7, %9
  br label %21

21:                                               ; preds = %19, %17, %14
  %22 = phi i64 [ %16, %14 ], [ %20, %19 ], [ %7, %17 ]
  %23 = load ptr, ptr %0, align 8, !tbaa !16
  %24 = getelementptr inbounds ptr, ptr %23, i64 %22
  %25 = load ptr, ptr %24, align 8, !tbaa !16
  %26 = icmp eq ptr %25, null
  br i1 %26, label %51, label %27

27:                                               ; preds = %21
  %28 = add i64 %9, -1
  br label %29

29:                                               ; preds = %47, %27
  %30 = phi ptr [ %31, %47 ], [ %25, %27 ]
  %31 = load ptr, ptr %30, align 8, !tbaa !197
  %32 = icmp eq ptr %31, null
  br i1 %32, label %51, label %33

33:                                               ; preds = %29
  %34 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %31, i64 0, i32 1
  %35 = load i64, ptr %34, align 8, !tbaa !206
  %36 = icmp eq i64 %35, %7
  br i1 %36, label %47, label %37

37:                                               ; preds = %33
  br i1 %13, label %38, label %40

38:                                               ; preds = %37
  %39 = and i64 %35, %28
  br label %44

40:                                               ; preds = %37
  %41 = icmp ult i64 %35, %9
  br i1 %41, label %44, label %42

42:                                               ; preds = %40
  %43 = urem i64 %35, %9
  br label %44

44:                                               ; preds = %42, %40, %38
  %45 = phi i64 [ %39, %38 ], [ %43, %42 ], [ %35, %40 ]
  %46 = icmp eq i64 %45, %22
  br i1 %46, label %47, label %51

47:                                               ; preds = %44, %33
  %48 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %31, i64 0, i32 2
  %49 = load i64, ptr %48, align 8, !tbaa !114
  %50 = icmp eq i64 %49, %7
  br i1 %50, label %131, label %29, !llvm.loop !412

51:                                               ; preds = %44, %29, %21, %5
  %52 = phi i64 [ %22, %21 ], [ undef, %5 ], [ %22, %44 ], [ %22, %29 ]
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %6) #24
  call void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE21__construct_node_hashIRKNS_21piecewise_construct_tEJNS_5tupleIJRKmEEENSN_IJEEEEEENS_10unique_ptrINS_11__hash_nodeIS8_PvEENS_22__hash_node_destructorINS5_ISV_EEEEEEmOT_DpOT0_(ptr nonnull sret(%"class.std::__ndk1::unique_ptr.144") align 8 %6, ptr noundef nonnull align 8 dereferenceable(36) %0, i64 noundef %7, ptr noundef nonnull align 1 dereferenceable(1) %2, ptr noundef nonnull align 8 dereferenceable(8) %3, ptr noundef nonnull align 1 dereferenceable(1) %4) #27
  %53 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %0, i64 0, i32 2
  %54 = load i64, ptr %53, align 8, !tbaa !114
  %55 = add i64 %54, 1
  %56 = uitofp i64 %55 to float
  %57 = uitofp i64 %9 to float
  %58 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %0, i64 0, i32 3
  %59 = load float, ptr %58, align 8, !tbaa !413
  %60 = fmul float %59, %57
  %61 = fcmp olt float %60, %56
  %62 = or i1 %10, %61
  br i1 %62, label %63, label %90

63:                                               ; preds = %51
  %64 = shl i64 %9, 1
  %65 = icmp ugt i64 %9, 2
  br i1 %65, label %66, label %70

66:                                               ; preds = %63
  %67 = call i64 @llvm.ctpop.i64(i64 %9), !range !205
  %68 = icmp ugt i64 %67, 1
  %69 = zext i1 %68 to i64
  br label %70

70:                                               ; preds = %66, %63
  %71 = phi i64 [ 1, %63 ], [ %69, %66 ]
  %72 = or i64 %71, %64
  %73 = fdiv float %56, %59
  %74 = call float @llvm.ceil.f32(float %73)
  %75 = fptoui float %74 to i64
  %76 = call i64 @llvm.umax.i64(i64 %72, i64 %75)
  invoke void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE8__rehashILb1EEEvm(ptr noundef nonnull align 8 dereferenceable(36) %0, i64 noundef %76) #27
          to label %77 unwind label %88

77:                                               ; preds = %70
  %78 = load i64, ptr %8, align 8, !tbaa !114
  %79 = call i64 @llvm.ctpop.i64(i64 %78), !range !205
  %80 = icmp ult i64 %79, 2
  br i1 %80, label %81, label %84

81:                                               ; preds = %77
  %82 = add i64 %78, -1
  %83 = and i64 %82, %7
  br label %90

84:                                               ; preds = %77
  %85 = icmp ult i64 %7, %78
  br i1 %85, label %90, label %86

86:                                               ; preds = %84
  %87 = urem i64 %7, %78
  br label %90

88:                                               ; preds = %70
  %89 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk110unique_ptrINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEENS_22__hash_node_destructorINS6_ISB_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %6) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %6) #24
  resume { ptr, i32 } %89

90:                                               ; preds = %86, %84, %81, %51
  %91 = phi i64 [ %52, %51 ], [ %83, %81 ], [ %87, %86 ], [ %7, %84 ]
  %92 = phi i64 [ %9, %51 ], [ %78, %81 ], [ %78, %86 ], [ %78, %84 ]
  %93 = load ptr, ptr %0, align 8, !tbaa !16
  %94 = getelementptr inbounds ptr, ptr %93, i64 %91
  %95 = load ptr, ptr %94, align 8, !tbaa !16
  %96 = icmp eq ptr %95, null
  br i1 %96, label %97, label %123

97:                                               ; preds = %90
  %98 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %0, i64 0, i32 1
  %99 = load ptr, ptr %98, align 8, !tbaa !197
  %100 = load ptr, ptr %6, align 8, !tbaa !16
  store ptr %99, ptr %100, align 8, !tbaa !197
  %101 = load ptr, ptr %6, align 8, !tbaa !16
  store ptr %101, ptr %98, align 8, !tbaa !197
  %102 = load ptr, ptr %0, align 8, !tbaa !16
  %103 = getelementptr inbounds ptr, ptr %102, i64 %91
  store ptr %98, ptr %103, align 8, !tbaa !16
  %104 = load ptr, ptr %6, align 8, !tbaa !16
  %105 = load ptr, ptr %104, align 8, !tbaa !197
  %106 = icmp eq ptr %105, null
  br i1 %106, label %127, label %107

107:                                              ; preds = %97
  %108 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %105, i64 0, i32 1
  %109 = load i64, ptr %108, align 8, !tbaa !206
  %110 = call i64 @llvm.ctpop.i64(i64 %92), !range !205
  %111 = icmp ult i64 %110, 2
  br i1 %111, label %112, label %115

112:                                              ; preds = %107
  %113 = add i64 %92, -1
  %114 = and i64 %109, %113
  br label %119

115:                                              ; preds = %107
  %116 = icmp ult i64 %109, %92
  br i1 %116, label %119, label %117

117:                                              ; preds = %115
  %118 = urem i64 %109, %92
  br label %119

119:                                              ; preds = %117, %115, %112
  %120 = phi i64 [ %114, %112 ], [ %118, %117 ], [ %109, %115 ]
  %121 = load ptr, ptr %0, align 8, !tbaa !16
  %122 = getelementptr inbounds ptr, ptr %121, i64 %120
  store ptr %104, ptr %122, align 8, !tbaa !16
  br label %127

123:                                              ; preds = %90
  %124 = load ptr, ptr %95, align 8, !tbaa !197
  %125 = load ptr, ptr %6, align 8, !tbaa !16
  store ptr %124, ptr %125, align 8, !tbaa !197
  %126 = load ptr, ptr %6, align 8, !tbaa !16
  store ptr %126, ptr %95, align 8, !tbaa !197
  br label %127

127:                                              ; preds = %123, %119, %97
  %128 = load ptr, ptr %6, align 8, !tbaa !16
  store ptr null, ptr %6, align 8, !tbaa !16
  %129 = load i64, ptr %53, align 8, !tbaa !114
  %130 = add i64 %129, 1
  store i64 %130, ptr %53, align 8, !tbaa !114
  call void @_ZNSt6__ndk110unique_ptrINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEENS_22__hash_node_destructorINS6_ISB_EEEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %6) #26
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %6) #24
  br label %131

131:                                              ; preds = %127, %47
  %132 = phi i64 [ 1, %127 ], [ 0, %47 ]
  %133 = phi ptr [ %128, %127 ], [ %31, %47 ]
  %134 = ptrtoint ptr %133 to i64
  %135 = insertvalue [2 x i64] poison, i64 %134, 0
  %136 = insertvalue [2 x i64] %135, i64 %132, 1
  ret [2 x i64] %136
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE21__construct_node_hashIRKNS_21piecewise_construct_tEJNS_5tupleIJRKmEEENSN_IJEEEEEENS_10unique_ptrINS_11__hash_nodeIS8_PvEENS_22__hash_node_destructorINS5_ISV_EEEEEEmOT_DpOT0_(ptr noalias sret(%"class.std::__ndk1::unique_ptr.144") align 8 %0, ptr noundef nonnull align 8 dereferenceable(36) %1, i64 noundef %2, ptr noundef nonnull align 1 dereferenceable(1) %3, ptr noundef nonnull align 8 dereferenceable(8) %4, ptr noundef nonnull align 1 dereferenceable(1) %5) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 !type !414 {
  %7 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %1, i64 0, i32 1
  %8 = tail call noalias noundef nonnull dereferenceable(48) ptr @_Znwm(i64 noundef 48) #30
  store ptr %8, ptr %0, align 8, !tbaa !404
  %9 = getelementptr inbounds i8, ptr %0, i64 8
  store ptr %7, ptr %9, align 8, !tbaa.struct !292
  %10 = getelementptr inbounds i8, ptr %0, i64 16
  %11 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %8, i64 0, i32 2
  %12 = load ptr, ptr %4, align 8
  %13 = load i64, ptr %12, align 8, !tbaa !114
  store i64 %13, ptr %11, align 8, !tbaa !415
  %14 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %8, i64 0, i32 2, i32 0, i32 1
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %14, i8 0, i64 24, i1 false)
  store i8 1, ptr %10, align 8, !tbaa !409
  %15 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %8, i64 0, i32 1
  store i64 %2, ptr %15, align 8, !tbaa !206
  store ptr null, ptr %8, align 8, !tbaa !197
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE8__rehashILb1EEEvm(ptr noundef nonnull align 8 dereferenceable(36) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 !type !416 {
  %3 = icmp eq i64 %1, 1
  br i1 %3, label %9, label %4

4:                                                ; preds = %2
  %5 = tail call i64 @llvm.ctpop.i64(i64 %1), !range !205
  %6 = icmp ult i64 %5, 2
  br i1 %6, label %9, label %7

7:                                                ; preds = %4
  %8 = tail call noundef i64 @_ZNSt6__ndk112__next_primeEm(i64 noundef %1) #27
  br label %9

9:                                                ; preds = %7, %4, %2
  %10 = phi i64 [ %1, %4 ], [ %8, %7 ], [ 2, %2 ]
  %11 = getelementptr inbounds i8, ptr %0, i64 8
  %12 = load i64, ptr %11, align 8, !tbaa !114
  %13 = icmp ugt i64 %10, %12
  br i1 %13, label %42, label %14

14:                                               ; preds = %9
  %15 = icmp ult i64 %10, %12
  br i1 %15, label %16, label %44

16:                                               ; preds = %14
  %17 = icmp ugt i64 %12, 2
  %18 = tail call i64 @llvm.ctpop.i64(i64 %12), !range !205
  %19 = icmp ult i64 %18, 2
  %20 = and i1 %17, %19
  %21 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %0, i64 0, i32 2
  %22 = load i64, ptr %21, align 8, !tbaa !114
  %23 = uitofp i64 %22 to float
  %24 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %0, i64 0, i32 3
  %25 = load float, ptr %24, align 8, !tbaa !413
  %26 = fdiv float %23, %25
  %27 = tail call float @llvm.ceil.f32(float %26)
  %28 = fptoui float %27 to i64
  br i1 %20, label %29, label %36

29:                                               ; preds = %16
  %30 = icmp ult i64 %28, 2
  %31 = add i64 %28, -1
  %32 = tail call i64 @llvm.ctlz.i64(i64 %31, i1 false), !range !205
  %33 = sub nuw nsw i64 64, %32
  %34 = shl nuw i64 1, %33
  %35 = select i1 %30, i64 %28, i64 %34
  br label %38

36:                                               ; preds = %16
  %37 = tail call noundef i64 @_ZNSt6__ndk112__next_primeEm(i64 noundef %28) #27
  br label %38

38:                                               ; preds = %36, %29
  %39 = phi i64 [ %35, %29 ], [ %37, %36 ]
  %40 = tail call i64 @llvm.umax.i64(i64 %10, i64 %39)
  %41 = icmp ult i64 %40, %12
  br i1 %41, label %42, label %44

42:                                               ; preds = %38, %9
  %43 = phi i64 [ %10, %9 ], [ %40, %38 ]
  tail call void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE11__do_rehashILb1EEEvm(ptr noundef nonnull align 8 dereferenceable(36) %0, i64 noundef %43) #27
  br label %44

44:                                               ; preds = %42, %38, %14
  ret void
}

; Function Attrs: minsize optsize
declare noundef i64 @_ZNSt6__ndk112__next_primeEm(i64 noundef) local_unnamed_addr #8

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEE11__do_rehashILb1EEEvm(ptr noundef nonnull align 8 dereferenceable(36) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 !type !416 {
  %3 = getelementptr inbounds i8, ptr %0, i64 8
  %4 = icmp eq i64 %1, 0
  br i1 %4, label %70, label %5

5:                                                ; preds = %2
  %6 = tail call noundef ptr @_ZNSt6__ndk19allocatorIPNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS0_IcEEEEEEPvEEEEE8allocateB7v170000Em(ptr noundef nonnull align 1 dereferenceable(1) %3, i64 noundef %1) #27
  tail call void @_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEE5resetB7v170000IPSF_EENS_9enable_ifIXsr28_CheckArrayPointerConversionIT_EE5valueEvE4typeESO_(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %6) #26
  store i64 %1, ptr %3, align 8, !tbaa !114
  br label %7

7:                                                ; preds = %14, %5
  %8 = phi i64 [ %17, %14 ], [ 0, %5 ]
  %9 = icmp eq i64 %8, %1
  br i1 %9, label %10, label %14

10:                                               ; preds = %7
  %11 = getelementptr inbounds %"class.std::__ndk1::__hash_table", ptr %0, i64 0, i32 1
  %12 = load ptr, ptr %11, align 8, !tbaa !197
  %13 = icmp eq ptr %12, null
  br i1 %13, label %71, label %18

14:                                               ; preds = %7
  %15 = load ptr, ptr %0, align 8, !tbaa !16
  %16 = getelementptr inbounds ptr, ptr %15, i64 %8
  store ptr null, ptr %16, align 8, !tbaa !16
  %17 = add i64 %8, 1
  br label %7, !llvm.loop !417

18:                                               ; preds = %10
  %19 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %12, i64 0, i32 1
  %20 = load i64, ptr %19, align 8, !tbaa !206
  %21 = tail call i64 @llvm.ctpop.i64(i64 %1), !range !205
  %22 = icmp ult i64 %21, 2
  br i1 %22, label %23, label %26

23:                                               ; preds = %18
  %24 = add i64 %1, -1
  %25 = and i64 %20, %24
  br label %30

26:                                               ; preds = %18
  %27 = icmp ult i64 %20, %1
  br i1 %27, label %30, label %28

28:                                               ; preds = %26
  %29 = urem i64 %20, %1
  br label %30

30:                                               ; preds = %28, %26, %23
  %31 = phi i64 [ %25, %23 ], [ %29, %28 ], [ %20, %26 ]
  %32 = load ptr, ptr %0, align 8, !tbaa !16
  %33 = getelementptr inbounds ptr, ptr %32, i64 %31
  store ptr %11, ptr %33, align 8, !tbaa !16
  %34 = add i64 %1, -1
  br label %35

35:                                               ; preds = %67, %30
  %36 = phi ptr [ %12, %30 ], [ %68, %67 ]
  %37 = phi i64 [ %31, %30 ], [ %69, %67 ]
  %38 = load ptr, ptr %36, align 8, !tbaa !197
  %39 = icmp eq ptr %38, null
  br i1 %39, label %71, label %40

40:                                               ; preds = %35
  %41 = getelementptr inbounds %"struct.std::__ndk1::__hash_node", ptr %38, i64 0, i32 1
  %42 = load i64, ptr %41, align 8, !tbaa !206
  br i1 %22, label %43, label %45

43:                                               ; preds = %40
  %44 = and i64 %42, %34
  br label %49

45:                                               ; preds = %40
  %46 = icmp ult i64 %42, %1
  br i1 %46, label %49, label %47

47:                                               ; preds = %45
  %48 = urem i64 %42, %1
  br label %49

49:                                               ; preds = %47, %45, %43
  %50 = phi i64 [ %44, %43 ], [ %48, %47 ], [ %42, %45 ]
  %51 = icmp eq i64 %50, %37
  br i1 %51, label %67, label %52

52:                                               ; preds = %49
  %53 = load ptr, ptr %0, align 8, !tbaa !16
  %54 = getelementptr inbounds ptr, ptr %53, i64 %50
  %55 = load ptr, ptr %54, align 8, !tbaa !16
  %56 = icmp eq ptr %55, null
  br i1 %56, label %57, label %58

57:                                               ; preds = %52
  store ptr %36, ptr %54, align 8, !tbaa !16
  br label %67

58:                                               ; preds = %52
  %59 = load ptr, ptr %38, align 8, !tbaa !197
  store ptr %59, ptr %36, align 8, !tbaa !197
  %60 = load ptr, ptr %0, align 8, !tbaa !16
  %61 = getelementptr inbounds ptr, ptr %60, i64 %50
  %62 = load ptr, ptr %61, align 8, !tbaa !16
  %63 = load ptr, ptr %62, align 8, !tbaa !197
  store ptr %63, ptr %38, align 8, !tbaa !197
  %64 = load ptr, ptr %0, align 8, !tbaa !16
  %65 = getelementptr inbounds ptr, ptr %64, i64 %50
  %66 = load ptr, ptr %65, align 8, !tbaa !16
  store ptr %38, ptr %66, align 8, !tbaa !197
  br label %67

67:                                               ; preds = %58, %57, %49
  %68 = phi ptr [ %38, %57 ], [ %36, %58 ], [ %38, %49 ]
  %69 = phi i64 [ %50, %57 ], [ %37, %58 ], [ %37, %49 ]
  br label %35, !llvm.loop !418

70:                                               ; preds = %2
  tail call void @_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEE5resetB7v170000IPSF_EENS_9enable_ifIXsr28_CheckArrayPointerConversionIT_EE5valueEvE4typeESO_(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef null) #26
  store i64 0, ptr %3, align 8, !tbaa !114
  br label %71

71:                                               ; preds = %70, %35, %10
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEE5resetB7v170000IPSF_EENS_9enable_ifIXsr28_CheckArrayPointerConversionIT_EE5valueEvE4typeESO_(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 !type !419 {
  %3 = load ptr, ptr %0, align 8, !tbaa !16
  store ptr %1, ptr %0, align 8, !tbaa !16
  %4 = icmp eq ptr %3, null
  br i1 %4, label %6, label %5

5:                                                ; preds = %2
  tail call void @_ZdlPv(ptr noundef nonnull %3) #28
  br label %6

6:                                                ; preds = %5, %2
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk19allocatorIPNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS0_IcEEEEEEPvEEEEE8allocateB7v170000Em(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 !type !420 {
  %3 = icmp ugt i64 %1, 2305843009213693951
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZSt28__throw_bad_array_new_lengthB7v170000v() #31
  unreachable

5:                                                ; preds = %2
  %6 = shl nuw i64 %1, 3
  %7 = tail call noalias noundef nonnull ptr @_Znwm(i64 noundef %6) #30
  ret ptr %7
}

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.ctlz.i64(i64, i1 immarg) #22

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare float @llvm.ceil.f32(float) #22

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb1EEERS5_PKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = icmp ult i64 %2, 23
  br i1 %4, label %5, label %13

5:                                                ; preds = %3
  %6 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %7 = trunc i64 %2 to i8
  %8 = shl nuw nsw i8 %7, 1
  store i8 %8, ptr %0, align 8
  %9 = icmp ugt ptr %6, %1
  %10 = getelementptr inbounds i8, ptr %6, i64 %2
  %11 = icmp ule ptr %10, %1
  %12 = or i1 %9, %11
  tail call void @llvm.assume(i1 %12)
  tail call void @llvm.memmove.p0.p0.i64(ptr nonnull align 1 %6, ptr align 1 %1, i64 %2, i1 false)
  store i8 0, ptr %10, align 1, !tbaa !12
  br label %18

13:                                               ; preds = %3
  %14 = load i8, ptr %0, align 8
  %15 = lshr i8 %14, 1
  %16 = zext i8 %15 to i64
  %17 = add i64 %2, -22
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE21__grow_by_and_replaceEmmmmmmPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef 22, i64 noundef %17, i64 noundef %16, i64 noundef 0, i64 noundef %16, i64 noundef %2, ptr noundef %1) #27
  br label %18

18:                                               ; preds = %13, %5
  ret ptr %0
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb0EEERS5_PKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = load i64, ptr %0, align 8
  %5 = and i64 %4, -2
  %6 = icmp ugt i64 %5, %2
  br i1 %6, label %7, label %15

7:                                                ; preds = %3
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %9 = load ptr, ptr %8, align 8, !tbaa !12
  %10 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %2, ptr %10, align 8, !tbaa !12
  %11 = icmp ugt ptr %9, %1
  %12 = getelementptr inbounds i8, ptr %9, i64 %2
  %13 = icmp ule ptr %12, %1
  %14 = or i1 %11, %13
  tail call void @llvm.assume(i1 %14)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %9, ptr align 1 %1, i64 %2, i1 false)
  store i8 0, ptr %12, align 1, !tbaa !12
  br label %21

15:                                               ; preds = %3
  %16 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %17 = load i64, ptr %16, align 8, !tbaa !12
  %18 = add i64 %5, -1
  %19 = add i64 %2, 1
  %20 = sub i64 %19, %5
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE21__grow_by_and_replaceEmmmmmmPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %18, i64 noundef %20, i64 noundef %17, i64 noundef 0, i64 noundef %17, i64 noundef %2, ptr noundef %1) #27
  br label %21

21:                                               ; preds = %15, %7
  ret ptr %0
}

; Function Attrs: minsize optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE21__grow_by_and_replaceEmmmmmmPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1, i64 noundef %2, i64 noundef %3, i64 noundef %4, i64 noundef %5, i64 noundef %6, ptr noundef %7) local_unnamed_addr #1 align 2 personality ptr @__gxx_personality_v0 {
  %9 = sub i64 -18, %1
  %10 = icmp ult i64 %9, %2
  br i1 %10, label %11, label %12

11:                                               ; preds = %8
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #31
  unreachable

12:                                               ; preds = %8
  %13 = load i8, ptr %0, align 8
  %14 = and i8 %13, 1
  %15 = icmp eq i8 %14, 0
  %16 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %17 = load ptr, ptr %16, align 8
  %18 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %19 = select i1 %15, ptr %18, ptr %17
  %20 = icmp ult i64 %1, 9223372036854775783
  br i1 %20, label %21, label %29

21:                                               ; preds = %12
  %22 = add i64 %2, %1
  %23 = shl nuw i64 %1, 1
  %24 = tail call i64 @llvm.umax.i64(i64 %22, i64 %23)
  %25 = or i64 %24, 15
  %26 = add i64 %25, 1
  %27 = icmp ult i64 %24, 23
  %28 = select i1 %27, i64 23, i64 %26
  br label %29

29:                                               ; preds = %21, %12
  %30 = phi i64 [ %28, %21 ], [ -17, %12 ]
  %31 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %30) #27
  %32 = extractvalue [2 x i64] %31, 0
  %33 = inttoptr i64 %32 to ptr
  %34 = icmp eq i64 %4, 0
  br i1 %34, label %40, label %35

35:                                               ; preds = %29
  %36 = icmp ult ptr %19, %33
  %37 = getelementptr inbounds i8, ptr %33, i64 %4
  %38 = icmp ule ptr %37, %19
  %39 = select i1 %36, i1 true, i1 %38
  tail call void @llvm.assume(i1 %39)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %33, ptr align 1 %19, i64 %4, i1 false)
  br label %40

40:                                               ; preds = %35, %29
  %41 = icmp eq i64 %6, 0
  br i1 %41, label %48, label %42

42:                                               ; preds = %40
  %43 = getelementptr inbounds i8, ptr %33, i64 %4
  %44 = icmp ugt ptr %43, %7
  %45 = getelementptr inbounds i8, ptr %43, i64 %6
  %46 = icmp ule ptr %45, %7
  %47 = select i1 %44, i1 true, i1 %46
  tail call void @llvm.assume(i1 %47)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %43, ptr align 1 %7, i64 %6, i1 false)
  br label %48

48:                                               ; preds = %42, %40
  %49 = add i64 %5, %4
  %50 = sub i64 %3, %49
  %51 = icmp eq i64 %49, %3
  br i1 %51, label %61, label %52

52:                                               ; preds = %48
  %53 = getelementptr inbounds i8, ptr %33, i64 %4
  %54 = getelementptr inbounds i8, ptr %53, i64 %6
  %55 = getelementptr inbounds i8, ptr %19, i64 %4
  %56 = getelementptr inbounds i8, ptr %55, i64 %5
  %57 = icmp ult ptr %56, %54
  %58 = getelementptr inbounds i8, ptr %54, i64 %50
  %59 = icmp ule ptr %58, %56
  %60 = select i1 %57, i1 true, i1 %59
  tail call void @llvm.assume(i1 %60)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %54, ptr align 1 %56, i64 %50, i1 false)
  br label %61

61:                                               ; preds = %52, %48
  %62 = icmp eq i64 %1, 22
  br i1 %62, label %64, label %63

63:                                               ; preds = %61
  tail call void @_ZdlPv(ptr noundef %19) #28
  br label %64

64:                                               ; preds = %63, %61
  %65 = extractvalue [2 x i64] %31, 1
  store ptr %33, ptr %16, align 8, !tbaa !12
  %66 = or i64 %65, 1
  store i64 %66, ptr %0, align 8
  %67 = add i64 %6, %4
  %68 = add i64 %67, %50
  %69 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %68, ptr %69, align 8, !tbaa !12
  %70 = getelementptr inbounds i8, ptr %33, i64 %68
  store i8 0, ptr %70, align 1, !tbaa !12
  ret void
}

; Function Attrs: minsize optsize uwtable
define internal void @_GLOBAL__sub_I_patch_sys_newfstatat.cpp() #1 section ".text.startup" personality ptr @__gxx_personality_v0 {
  %1 = alloca [3 x %"class.std::__ndk1::basic_string"], align 8
  %2 = alloca [5 x %"class.std::__ndk1::basic_string"], align 8
  %3 = alloca [5 x %"class.std::__ndk1::basic_string"], align 8
  call void @llvm.lifetime.start.p0(i64 120, ptr nonnull %3) #24
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %3, ptr noundef nonnull @.str) #27
  %4 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 1
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %4, ptr noundef nonnull @.str.1) #27
          to label %5 unwind label %21

5:                                                ; preds = %0
  %6 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 2
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %6, ptr noundef nonnull @.str.2) #27
          to label %7 unwind label %21

7:                                                ; preds = %5
  %8 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 3
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %8, ptr noundef nonnull @.str.3) #27
          to label %9 unwind label %21

9:                                                ; preds = %7
  %10 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 4
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %10, ptr noundef nonnull @.str.4) #27
          to label %11 unwind label %21

11:                                               ; preds = %9
  %12 = ptrtoint ptr %3 to i64
  %13 = insertvalue [2 x i64] poison, i64 %12, 0
  %14 = insertvalue [2 x i64] %13, i64 5, 1
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEC2B7v170000ESt16initializer_listIS6_E(ptr noundef nonnull align 8 dereferenceable(24) @_ZN13kernel_module13export_symbolL27copy_from_user_symbol_namesE, [2 x i64] %14) #27
          to label %15 unwind label %28

15:                                               ; preds = %11
  %16 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 5
  br label %17

17:                                               ; preds = %17, %15
  %18 = phi ptr [ %16, %15 ], [ %19, %17 ]
  %19 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %18, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %19) #26
  %20 = icmp eq ptr %19, %3
  br i1 %20, label %39, label %17

21:                                               ; preds = %9, %7, %5, %0
  %22 = phi ptr [ %10, %9 ], [ %8, %7 ], [ %6, %5 ], [ %4, %0 ]
  %23 = landingpad { ptr, i32 }
          cleanup
  br label %24

24:                                               ; preds = %24, %21
  %25 = phi ptr [ %22, %21 ], [ %26, %24 ]
  %26 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %25, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %26) #26
  %27 = icmp eq ptr %26, %3
  br i1 %27, label %37, label %24

28:                                               ; preds = %11
  %29 = landingpad { ptr, i32 }
          cleanup
  %30 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 5
  br label %31

31:                                               ; preds = %31, %28
  %32 = phi ptr [ %30, %28 ], [ %33, %31 ]
  %33 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %32, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %33) #26
  %34 = icmp eq ptr %33, %3
  br i1 %34, label %37, label %31

35:                                               ; preds = %103, %72, %37
  %36 = phi { ptr, i32 } [ %38, %37 ], [ %73, %72 ], [ %104, %103 ]
  resume { ptr, i32 } %36

37:                                               ; preds = %31, %24
  %38 = phi { ptr, i32 } [ %29, %31 ], [ %23, %24 ]
  call void @llvm.lifetime.end.p0(i64 120, ptr nonnull %3) #24
  br label %35

39:                                               ; preds = %17
  call void @llvm.lifetime.end.p0(i64 120, ptr nonnull %3) #24
  %40 = call i32 @__cxa_atexit(ptr nonnull @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev, ptr nonnull @_ZN13kernel_module13export_symbolL27copy_from_user_symbol_namesE, ptr nonnull @__dso_handle) #24
  call void @llvm.lifetime.start.p0(i64 120, ptr nonnull %2) #24
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %2, ptr noundef nonnull @.str.6) #27
  %41 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 1
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %41, ptr noundef nonnull @.str.7) #27
          to label %42 unwind label %58

42:                                               ; preds = %39
  %43 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 2
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %43, ptr noundef nonnull @.str.8) #27
          to label %44 unwind label %58

44:                                               ; preds = %42
  %45 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 3
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %45, ptr noundef nonnull @.str.9) #27
          to label %46 unwind label %58

46:                                               ; preds = %44
  %47 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 4
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %47, ptr noundef nonnull @.str.10) #27
          to label %48 unwind label %58

48:                                               ; preds = %46
  %49 = ptrtoint ptr %2 to i64
  %50 = insertvalue [2 x i64] poison, i64 %49, 0
  %51 = insertvalue [2 x i64] %50, i64 5, 1
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEC2B7v170000ESt16initializer_listIS6_E(ptr noundef nonnull align 8 dereferenceable(24) @_ZN13kernel_module13export_symbolL25copy_to_user_symbol_namesE, [2 x i64] %51) #27
          to label %52 unwind label %65

52:                                               ; preds = %48
  %53 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 5
  br label %54

54:                                               ; preds = %54, %52
  %55 = phi ptr [ %53, %52 ], [ %56, %54 ]
  %56 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %55, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %56) #26
  %57 = icmp eq ptr %56, %2
  br i1 %57, label %74, label %54

58:                                               ; preds = %46, %44, %42, %39
  %59 = phi ptr [ %47, %46 ], [ %45, %44 ], [ %43, %42 ], [ %41, %39 ]
  %60 = landingpad { ptr, i32 }
          cleanup
  br label %61

61:                                               ; preds = %61, %58
  %62 = phi ptr [ %59, %58 ], [ %63, %61 ]
  %63 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %62, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %63) #26
  %64 = icmp eq ptr %63, %2
  br i1 %64, label %72, label %61

65:                                               ; preds = %48
  %66 = landingpad { ptr, i32 }
          cleanup
  %67 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 5
  br label %68

68:                                               ; preds = %68, %65
  %69 = phi ptr [ %67, %65 ], [ %70, %68 ]
  %70 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %69, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %70) #26
  %71 = icmp eq ptr %70, %2
  br i1 %71, label %72, label %68

72:                                               ; preds = %68, %61
  %73 = phi { ptr, i32 } [ %66, %68 ], [ %60, %61 ]
  call void @llvm.lifetime.end.p0(i64 120, ptr nonnull %2) #24
  br label %35

74:                                               ; preds = %54
  call void @llvm.lifetime.end.p0(i64 120, ptr nonnull %2) #24
  %75 = call i32 @__cxa_atexit(ptr nonnull @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev, ptr nonnull @_ZN13kernel_module13export_symbolL25copy_to_user_symbol_namesE, ptr nonnull @__dso_handle) #24
  call void @llvm.lifetime.start.p0(i64 72, ptr nonnull %1) #24
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %1, ptr noundef nonnull @.str.12) #27
  %76 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %1, i64 1
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %76, ptr noundef nonnull @.str.13) #27
          to label %77 unwind label %89

77:                                               ; preds = %74
  %78 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %1, i64 2
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %78, ptr noundef nonnull @.str.14) #27
          to label %79 unwind label %89

79:                                               ; preds = %77
  %80 = ptrtoint ptr %1 to i64
  %81 = insertvalue [2 x i64] poison, i64 %80, 0
  %82 = insertvalue [2 x i64] %81, i64 3, 1
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEC2B7v170000ESt16initializer_listIS6_E(ptr noundef nonnull align 8 dereferenceable(24) @_ZN13kernel_module13export_symbolL37copy_from_kernel_nofault_symbol_namesE, [2 x i64] %82) #27
          to label %83 unwind label %96

83:                                               ; preds = %79
  %84 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %1, i64 3
  br label %85

85:                                               ; preds = %85, %83
  %86 = phi ptr [ %84, %83 ], [ %87, %85 ]
  %87 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %86, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %87) #26
  %88 = icmp eq ptr %87, %1
  br i1 %88, label %105, label %85

89:                                               ; preds = %77, %74
  %90 = phi ptr [ %78, %77 ], [ %76, %74 ]
  %91 = landingpad { ptr, i32 }
          cleanup
  br label %92

92:                                               ; preds = %92, %89
  %93 = phi ptr [ %90, %89 ], [ %94, %92 ]
  %94 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %93, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %94) #26
  %95 = icmp eq ptr %94, %1
  br i1 %95, label %103, label %92

96:                                               ; preds = %79
  %97 = landingpad { ptr, i32 }
          cleanup
  %98 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %1, i64 3
  br label %99

99:                                               ; preds = %99, %96
  %100 = phi ptr [ %98, %96 ], [ %101, %99 ]
  %101 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %100, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %101) #26
  %102 = icmp eq ptr %101, %1
  br i1 %102, label %103, label %99

103:                                              ; preds = %99, %92
  %104 = phi { ptr, i32 } [ %97, %99 ], [ %91, %92 ]
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %1) #24
  br label %35

105:                                              ; preds = %85
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %1) #24
  %106 = call i32 @__cxa_atexit(ptr nonnull @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev, ptr nonnull @_ZN13kernel_module13export_symbolL37copy_from_kernel_nofault_symbol_namesE, ptr nonnull @__dso_handle) #24
  ret void
}

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i32 @llvm.abs.i32(i32, i1 immarg) #22

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.ctpop.i64(i64) #22

; Function Attrs: nofree nounwind
declare noundef i32 @puts(ptr nocapture noundef readonly) local_unnamed_addr #5

; Function Attrs: nocallback nofree nosync nounwind willreturn memory(inaccessiblemem: readwrite)
declare void @llvm.experimental.noalias.scope.decl(metadata) #11

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.umax.i64(i64, i64) #22

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.fshl.i64(i64, i64, i64) #22

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.umin.i64(i64, i64) #22

attributes #0 = { nocallback nofree nosync nounwind willreturn memory(argmem: readwrite) }
attributes #1 = { minsize optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #2 = { inlinehint minsize nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #3 = { inlinehint minsize optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #4 = { minsize nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #5 = { nofree nounwind }
attributes #6 = { minsize mustprogress optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #7 = { minsize nofree nounwind optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #8 = { minsize optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #9 = { minsize nounwind optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #10 = { inlinehint minsize mustprogress optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #11 = { nocallback nofree nosync nounwind willreturn memory(inaccessiblemem: readwrite) }
attributes #12 = { minsize mustprogress noreturn optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #13 = { inlinehint minsize mustprogress noreturn optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #14 = { minsize nobuiltin optsize allocsize(0) "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #15 = { minsize noinline noreturn nounwind optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #16 = { nocallback nofree nounwind willreturn memory(argmem: readwrite) }
attributes #17 = { minsize mustprogress nofree nounwind optsize willreturn memory(argmem: read) "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #18 = { minsize nobuiltin nounwind optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #19 = { minsize mustprogress nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #20 = { minsize nofree optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #21 = { nocallback nofree nounwind willreturn memory(argmem: write) }
attributes #22 = { nocallback nofree nosync nounwind speculatable willreturn memory(none) }
attributes #23 = { minsize nofree nounwind optsize memory(argmem: readwrite) "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #24 = { nounwind }
attributes #25 = { inlinehint minsize mustprogress nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #26 = { minsize nounwind optsize }
attributes #27 = { minsize optsize }
attributes #28 = { builtin minsize nounwind optsize }
attributes #29 = { noreturn nounwind }
attributes #30 = { builtin minsize optsize allocsize(0) }
attributes #31 = { minsize noreturn optsize }
attributes #32 = { noreturn }

!llvm.linker.options = !{}
!llvm.module.flags = !{!6, !7, !8, !9, !10}
!llvm.ident = !{!11}

!0 = !{i64 16, !1}
!1 = distinct !{}
!2 = !{i64 32, !3}
!3 = distinct !{}
!4 = !{i64 16, !"_ZTSN6asmjit5v1_1612ErrorHandlerE"}
!5 = !{i64 32, !"_ZTSMN6asmjit5v1_1612ErrorHandlerEFvjPKcPNS0_11BaseEmitterEE.virtual"}
!6 = !{i32 1, !"wchar_size", i32 4}
!7 = !{i32 8, !"PIC Level", i32 2}
!8 = !{i32 7, !"uwtable", i32 2}
!9 = !{i32 7, !"frame-pointer", i32 1}
!10 = !{i32 1, !"EnableSplitLTOUnit", i32 0}
!11 = !{!"Android (11349228, based on r487747e) clang version 17.0.2 (https://android.googlesource.com/toolchain/llvm-project d9f89f4d16663d5012e5c09495f3b30ece3d2362)"}
!12 = !{!13, !13, i64 0}
!13 = !{!"omnipotent char", !14, i64 0}
!14 = !{!"Simple C++ TBAA"}
!15 = !{i64 0, i64 8, !16}
!16 = !{!17, !17, i64 0}
!17 = !{!"any pointer", !13, i64 0}
!18 = !{!19}
!19 = distinct !{!19, !20, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEEENS_17__exception_guardIT_EESC_: argument 0"}
!20 = distinct !{!20, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEEENS_17__exception_guardIT_EESC_"}
!21 = !{!22, !24, i64 8}
!22 = !{!"_ZTSNSt6__ndk117__exception_guardINS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEEE", !23, i64 0, !24, i64 8}
!23 = !{!"_ZTSNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE16__destroy_vectorE", !17, i64 0}
!24 = !{!"bool", !13, i64 0}
!25 = !{!26, !26, i64 0}
!26 = !{!"_ZTS7KModErr", !13, i64 0}
!27 = !{!28, !35, i64 40}
!28 = !{!"_ZTSN15default_modules18module_su_redirect18PatchSysNewfstatatE", !29, i64 0, !35, i64 40}
!29 = !{!"_ZTSN15default_modules18module_su_redirect9PatchBaseE", !30, i64 0, !30, i64 4, !30, i64 8, !31, i64 16}
!30 = !{!"int", !13, i64 0}
!31 = !{!"_ZTSNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEE", !32, i64 0}
!32 = !{!"_ZTSNSt6__ndk117__compressed_pairINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE5__repES5_EE", !33, i64 0}
!33 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE5__repELi0ELb0EEE", !34, i64 0}
!34 = !{!"_ZTSNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE5__repE", !13, i64 0}
!35 = !{!"long", !13, i64 0}
!36 = !{i64 0, !"_ZTSMN15default_modules18module_su_redirect9PatchBaseEF7KModErrmE"}
!37 = !{!38, !38, i64 0}
!38 = !{!"vtable pointer", !14, i64 0}
!39 = !{!40, !30, i64 0}
!40 = !{!"_ZTSN6asmjit5v1_1616OperandSignatureE", !30, i64 0}
!41 = !{!42}
!42 = distinct !{!42, !43, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi: argument 0"}
!43 = distinct !{!43, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi"}
!44 = !{!45, !30, i64 4}
!45 = !{!"_ZTSN6asmjit5v1_168Operand_E", !40, i64 0, !30, i64 4, !13, i64 8}
!46 = !{!30, !30, i64 0}
!47 = !{!48}
!48 = distinct !{!48, !49, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi: argument 0"}
!49 = distinct !{!49, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi"}
!50 = !{!51, !17, i64 0}
!51 = !{!"_ZTSNSt6__ndk16vectorIhNS_9allocatorIhEEEE", !17, i64 0, !17, i64 8, !52, i64 16}
!52 = !{!"_ZTSNSt6__ndk117__compressed_pairIPhNS_9allocatorIhEEEE", !53, i64 0}
!53 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPhLi0ELb0EEE", !17, i64 0}
!54 = !{!51, !17, i64 8}
!55 = !{!56, !17, i64 168}
!56 = !{!"_ZTSN6asmjit5v1_1613BaseAssemblerE", !57, i64 0, !17, i64 144, !17, i64 152, !17, i64 160, !17, i64 168}
!57 = !{!"_ZTSN6asmjit5v1_1611BaseEmitterE", !58, i64 8, !59, i64 9, !13, i64 10, !13, i64 11, !60, i64 12, !61, i64 16, !35, i64 24, !62, i64 32, !63, i64 36, !30, i64 40, !17, i64 48, !17, i64 56, !17, i64 64, !64, i64 72, !40, i64 80, !63, i64 84, !72, i64 88, !17, i64 96, !73, i64 104}
!58 = !{!"_ZTSN6asmjit5v1_1611EmitterTypeE", !13, i64 0}
!59 = !{!"_ZTSN6asmjit5v1_1612EmitterFlagsE", !13, i64 0}
!60 = !{!"_ZTSN6asmjit5v1_1615ValidationFlagsE", !13, i64 0}
!61 = !{!"_ZTSN6asmjit5v1_1617DiagnosticOptionsE", !13, i64 0}
!62 = !{!"_ZTSN6asmjit5v1_1615EncodingOptionsE", !13, i64 0}
!63 = !{!"_ZTSN6asmjit5v1_1611InstOptionsE", !13, i64 0}
!64 = !{!"_ZTSN6asmjit5v1_1611EnvironmentE", !65, i64 0, !66, i64 1, !67, i64 2, !68, i64 3, !69, i64 4, !70, i64 5, !71, i64 6, !13, i64 7}
!65 = !{!"_ZTSN6asmjit5v1_164ArchE", !13, i64 0}
!66 = !{!"_ZTSN6asmjit5v1_167SubArchE", !13, i64 0}
!67 = !{!"_ZTSN6asmjit5v1_166VendorE", !13, i64 0}
!68 = !{!"_ZTSN6asmjit5v1_168PlatformE", !13, i64 0}
!69 = !{!"_ZTSN6asmjit5v1_1611PlatformABIE", !13, i64 0}
!70 = !{!"_ZTSN6asmjit5v1_1612ObjectFormatE", !13, i64 0}
!71 = !{!"_ZTSN6asmjit5v1_168FloatABIE", !13, i64 0}
!72 = !{!"_ZTSN6asmjit5v1_167RegOnlyE", !40, i64 0, !30, i64 4}
!73 = !{!"_ZTSN6asmjit5v1_1611BaseEmitter5FuncsE", !17, i64 0, !17, i64 8, !17, i64 16, !17, i64 24, !17, i64 32}
!74 = !{!56, !17, i64 152}
!75 = !{!76}
!76 = distinct !{!76, !77, !"_ZN12_GLOBAL__N_120aarch64_asm_to_bytesEPKN6asmjit5v1_163a649AssemblerE: argument 0"}
!77 = distinct !{!77, !"_ZN12_GLOBAL__N_120aarch64_asm_to_bytesEPKN6asmjit5v1_163a649AssemblerE"}
!78 = !{!57, !17, i64 64}
!79 = !{!80, !24, i64 8}
!80 = !{!"_ZTSN12_GLOBAL__N_120MyAsmJitErrorHandlerE", !81, i64 0, !24, i64 8}
!81 = !{!"_ZTSN6asmjit5v1_1612ErrorHandlerE"}
!82 = !{i8 0, i8 2}
!83 = !{}
!84 = !{!57, !17, i64 48}
!85 = !{!86, !17, i64 0}
!86 = !{!"_ZTSN6asmjit5v1_1614ZoneVectorBaseE", !17, i64 0, !30, i64 8, !30, i64 12}
!87 = !{!88, !17, i64 0}
!88 = !{!"_ZTSN6asmjit5v1_1610CodeBufferE", !17, i64 0, !35, i64 8, !35, i64 16, !89, i64 24}
!89 = !{!"_ZTSN6asmjit5v1_1615CodeBufferFlagsE", !13, i64 0}
!90 = !{!88, !35, i64 8}
!91 = !{!92}
!92 = distinct !{!92, !93, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi: argument 0"}
!93 = distinct !{!93, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi"}
!94 = !{!64, !65, i64 0}
!95 = !{!96}
!96 = distinct !{!96, !97, !"_ZNSt6__ndk111make_uniqueB7v170000IN12_GLOBAL__N_120MyAsmJitErrorHandlerEJEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_: argument 0"}
!97 = distinct !{!97, !"_ZNSt6__ndk111make_uniqueB7v170000IN12_GLOBAL__N_120MyAsmJitErrorHandlerEJEEENS_11__unique_ifIT_E15__unique_singleEDpOT0_"}
!98 = !{i64 0, !"_ZTSMN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEEEFjRKNS1_2GpERKNS0_5LabelEE"}
!99 = !{i64 0, !"_ZTSMN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEEEFjRKNS1_2GpERKNS0_3arm3MemEE"}
!100 = !{i64 0, !"_ZTSMN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEEEFjRKNS1_2GpES7_E"}
!101 = !{!102, !17, i64 0}
!102 = !{!"_ZTS15RegProtectGuard", !17, i64 0, !103, i64 8, !24, i64 32, !106, i64 36, !106, i64 52}
!103 = !{!"_ZTSNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEEE", !17, i64 0, !17, i64 8, !104, i64 16}
!104 = !{!"_ZTSNSt6__ndk117__compressed_pairIPN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEEE", !105, i64 0}
!105 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPN6asmjit5v1_163a643GpXELi0ELb0EEE", !17, i64 0}
!106 = !{!"_ZTSN6asmjit5v1_163a643GpXE", !107, i64 0}
!107 = !{!"_ZTSN6asmjit5v1_163a642GpE", !108, i64 0}
!108 = !{!"_ZTSN6asmjit5v1_163arm3RegE", !109, i64 0}
!109 = !{!"_ZTSN6asmjit5v1_167BaseRegE", !110, i64 0}
!110 = !{!"_ZTSN6asmjit5v1_167OperandE", !45, i64 0}
!111 = !{!103, !17, i64 0}
!112 = !{!103, !17, i64 8}
!113 = !{!102, !24, i64 32}
!114 = !{!35, !35, i64 0}
!115 = !{!116, !17, i64 0}
!116 = !{!"_ZTSNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEE", !17, i64 0, !17, i64 8, !117, i64 16}
!117 = !{!"_ZTSNSt6__ndk117__compressed_pairIPNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEE", !118, i64 0}
!118 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEELi0ELb0EEE", !17, i64 0}
!119 = !{!116, !17, i64 8}
!120 = !{!121, !24, i64 24}
!121 = !{!"_ZTSNSt6__ndk117__exception_guardINS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEEE", !122, i64 0, !24, i64 24}
!122 = !{!"_ZTSNSt6__ndk129_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPS6_EE", !17, i64 0, !17, i64 8, !17, i64 16}
!123 = !{!124}
!124 = distinct !{!124, !125, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEEENS_17__exception_guardIT_EESC_: argument 0"}
!125 = distinct !{!125, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEEENS_17__exception_guardIT_EESC_"}
!126 = distinct !{!126, !127}
!127 = !{!"llvm.loop.mustprogress"}
!128 = !{i64 0, i64 8, !114, i64 0, i64 8, !114, i64 8, i64 8, !114, i64 16, i64 8, !16, i64 0, i64 1, !12, i64 0, i64 1, !12, i64 1, i64 0, !12, i64 1, i64 23, !12, i64 0, i64 24, !12}
!129 = !{i64 0, !"_ZTSMNSt6__ndk129_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPS6_EEKFvvE"}
!130 = !{!122, !17, i64 0}
!131 = !{!122, !17, i64 16}
!132 = !{!122, !17, i64 8}
!133 = distinct !{!133, !127}
!134 = !{!23, !17, i64 0}
!135 = !{!136, !17, i64 0}
!136 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPN6asmjit5v1_1610CodeHolderELi0ELb0EEE", !17, i64 0}
!137 = !{!138, !17, i64 0}
!138 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPN6asmjit5v1_1612StringLoggerELi0ELb0EEE", !17, i64 0}
!139 = !{!140, !17, i64 0}
!140 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPN6asmjit5v1_163a649AssemblerELi0ELb0EEE", !17, i64 0}
!141 = !{i64 0, !"_ZTSMNSt6__ndk110unique_ptrIN6asmjit5v1_1610CodeHolderENS_14default_deleteIS3_EEEEDoFvPS3_E"}
!142 = !{i64 0, !"_ZTSMNSt6__ndk114default_deleteIN6asmjit5v1_1610CodeHolderEEEKDoFvPS3_E"}
!143 = !{!144, !24, i64 0}
!144 = !{!"_ZTSNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryE", !24, i64 0, !17, i64 8}
!145 = !{!146, !17, i64 40}
!146 = !{!"_ZTSNSt6__ndk18ios_baseE", !30, i64 8, !35, i64 16, !35, i64 24, !30, i64 32, !30, i64 36, !17, i64 40, !17, i64 48, !17, i64 56, !17, i64 64, !35, i64 72, !35, i64 80, !17, i64 88, !35, i64 96, !35, i64 104, !17, i64 112, !35, i64 120, !35, i64 128}
!147 = !{!146, !30, i64 8}
!148 = !{!146, !35, i64 24}
!149 = !{!150, !30, i64 144}
!150 = !{!"_ZTSNSt6__ndk19basic_iosIcNS_11char_traitsIcEEEE", !146, i64 0, !17, i64 136, !30, i64 144}
!151 = distinct !{!151, !127}
!152 = !{!146, !30, i64 32}
!153 = !{i64 0, !"_ZTSM15RegProtectGuardFvvE"}
!154 = !{!155}
!155 = distinct !{!155, !156, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi: argument 0"}
!156 = distinct !{!156, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi"}
!157 = !{!158}
!158 = distinct !{!158, !159, !"_ZNK6asmjit5v1_163arm3Mem4postEl: argument 0"}
!159 = distinct !{!159, !"_ZNK6asmjit5v1_163arm3Mem4postEl"}
!160 = distinct !{!160, !127}
!161 = !{i64 0, !"_ZTSMN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEEEFjRKNS1_2GpES7_RKNS0_3arm3MemEE"}
!162 = !{i64 0, !"_ZTSMNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE16__destroy_vectorEFvvE"}
!163 = !{!164, !17, i64 0}
!164 = !{!"_ZTSNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEE16__destroy_vectorE", !17, i64 0}
!165 = !{!166}
!166 = distinct !{!166, !167, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEEENS_17__exception_guardIT_EES7_: argument 0"}
!167 = distinct !{!167, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEEENS_17__exception_guardIT_EES7_"}
!168 = !{!169, !24, i64 8}
!169 = !{!"_ZTSNSt6__ndk117__exception_guardINS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEEE", !170, i64 0, !24, i64 8}
!170 = !{!"_ZTSNSt6__ndk16vectorIhNS_9allocatorIhEEE16__destroy_vectorE", !17, i64 0}
!171 = distinct !{!171, !127}
!172 = !{!170, !17, i64 0}
!173 = !{i64 0, !"_ZTSMN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEEEFjRKNS0_5LabelEE"}
!174 = !{i64 0, !"_ZTSMN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEEEFjvE"}
!175 = !{!176}
!176 = distinct !{!176, !177, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEEENS_17__exception_guardIT_EES7_: argument 0"}
!177 = distinct !{!177, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorIhNS_9allocatorIhEEE16__destroy_vectorEEENS_17__exception_guardIT_EES7_"}
!178 = !{!179, !179, i64 0}
!179 = !{!"_ZTSN26Aarch64KernelSymCallHelper12NeedReturnX0E", !13, i64 0}
!180 = !{!"branch_weights", i32 1, i32 1023}
!181 = !{!182, !183, i64 0}
!182 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIfLi0ELb0EEE", !183, i64 0}
!183 = !{!"float", !13, i64 0}
!184 = !{!185}
!185 = distinct !{!185, !186, !"_ZNSt6__ndk110make_tupleB7v170000IJRN6asmjit5v1_163a643GpXES5_RmEEENS_5tupleIJDpNS_18__unwrap_ref_decayIT_E4typeEEEEDpOS9_: argument 0"}
!186 = distinct !{!186, !"_ZNSt6__ndk110make_tupleB7v170000IJRN6asmjit5v1_163a643GpXES5_RmEEENS_5tupleIJDpNS_18__unwrap_ref_decayIT_E4typeEEEEDpOS9_"}
!187 = !{!188, !35, i64 0}
!188 = !{!"_ZTSNSt6__ndk112__tuple_leafILm2EmLb0EEE", !35, i64 0}
!189 = distinct !{!189, !127}
!190 = !{i64 0, !"_ZTSMNSt6__ndk133__unary_function_keep_layout_baseINS_17basic_string_viewIcNS_11char_traitsIcEEEEmEEKDoFmS4_E"}
!191 = !{i64 0, !"_ZTSMZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_EUlPKcE_KFS9_SS_E"}
!192 = !{!193, !17, i64 0}
!193 = !{!"_ZTSZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_EUlPKcE_", !17, i64 0, !17, i64 8, !17, i64 16}
!194 = !{!193, !17, i64 8}
!195 = !{!193, !17, i64 16}
!196 = distinct !{!196, !127}
!197 = !{!198, !17, i64 0}
!198 = !{!"_ZTSNSt6__ndk116__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEE", !17, i64 0}
!199 = !{i64 0, !"_ZTSMNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEEEDoFvPNS_16__hash_node_baseIPNS_11__hash_nodeIS8_PvEEEEE"}
!200 = distinct !{!200, !127}
!201 = !{i64 0, !"_ZTSMNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEEEDoFvDnE"}
!202 = !{i64 0, !"_ZTSMNSt6__ndk121__murmur2_or_cityhashImLm64EEEFmPKvmE"}
!203 = distinct !{!203, !127}
!204 = !{i64 0, !"_ZTSMNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEEEFNS_15__hash_iteratorIPNS_11__hash_nodeIS8_PvEEEERKmE"}
!205 = !{i64 0, i64 65}
!206 = !{!207, !35, i64 8}
!207 = !{!"_ZTSNSt6__ndk111__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEE", !198, i64 0, !35, i64 8, !208, i64 16}
!208 = !{!"_ZTSNSt6__ndk117__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEE", !209, i64 0}
!209 = !{!"_ZTSNSt6__ndk14pairIKmNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEE", !35, i64 0, !31, i64 8}
!210 = distinct !{!210, !127}
!211 = !{i64 0, !"_ZTSMZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ENKUlPKcE_clESS_EUlSQ_E_KFS9_S5_S5_S6_E"}
!212 = !{!213, !17, i64 0}
!213 = !{!"_ZTSZZN26Aarch64KernelSymCallHelper20callSymbolCandidatesIJRN6asmjit5v1_163a643GpXES5_RmEEEvPNS3_9AssemblerER7KModErrRKNSt6__ndk16vectorINSB_12basic_stringIcNSB_11char_traitsIcEENSB_9allocatorIcEEEENSG_ISI_EEEENS_12NeedReturnX0EDpOT_ENKUlPKcE_clESS_EUlSQ_E_", !17, i64 0, !17, i64 8, !17, i64 16}
!214 = !{!213, !17, i64 8}
!215 = !{!213, !17, i64 16}
!216 = !{!217}
!217 = distinct !{!217, !218, !"_ZL7mk_argsIJRN6asmjit5v1_163a643GpXES4_RmEENSt6__ndk16vectorI8Arm64ArgNS6_9allocatorIS8_EEEEDpOT_: argument 0"}
!218 = distinct !{!218, !"_ZL7mk_argsIJRN6asmjit5v1_163a643GpXES4_RmEENSt6__ndk16vectorI8Arm64ArgNS6_9allocatorIS8_EEEEDpOT_"}
!219 = !{!220}
!220 = distinct !{!220, !221, !"_ZL12excluding_x0NSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEEE: argument 0"}
!221 = distinct !{!221, !"_ZL12excluding_x0NSt6__ndk13setIjNS_4lessIjEENS_9allocatorIjEEEE"}
!222 = !{!223, !17, i64 0}
!223 = !{!"_ZTSNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEE", !17, i64 0, !224, i64 8, !227, i64 16}
!224 = !{!"_ZTSNSt6__ndk117__compressed_pairINS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEENS_9allocatorINS_11__tree_nodeIjS3_EEEEEE", !225, i64 0}
!225 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemINS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEELi0ELb0EEE", !226, i64 0}
!226 = !{!"_ZTSNSt6__ndk115__tree_end_nodeIPNS_16__tree_node_baseIPvEEEE", !17, i64 0}
!227 = !{!"_ZTSNSt6__ndk117__compressed_pairImNS_4lessIjEEEE", !228, i64 0}
!228 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemImLi0ELb0EEE", !35, i64 0}
!229 = !{!230, !17, i64 16}
!230 = !{!"_ZTSNSt6__ndk116__tree_node_baseIPvEE", !226, i64 0, !17, i64 8, !17, i64 16, !24, i64 24}
!231 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEEFvmE"}
!232 = !{!233, !17, i64 0}
!233 = !{!"_ZTSNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEE", !17, i64 0, !17, i64 8, !234, i64 16}
!234 = !{!"_ZTSNSt6__ndk117__compressed_pairIP8Arm64ArgNS_9allocatorIS1_EEEE", !235, i64 0}
!235 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIP8Arm64ArgLi0ELb0EEE", !17, i64 0}
!236 = !{!233, !17, i64 8}
!237 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEEFRS1_OS1_E"}
!238 = !{!239, !240, i64 0}
!239 = !{!"_ZTS8Arm64Arg", !240, i64 0, !106, i64 4, !241, i64 20, !35, i64 40, !30, i64 48}
!240 = !{!"_ZTS12Arm64ArgKind", !13, i64 0}
!241 = !{!"_ZTSN6asmjit5v1_163a643GpWE", !107, i64 0}
!242 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEEKFvvE"}
!243 = !{!235, !17, i64 0}
!244 = !{!245, !17, i64 0}
!245 = !{!"_ZTSNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEEE", !17, i64 0, !17, i64 8, !17, i64 16, !246, i64 24}
!246 = !{!"_ZTSNSt6__ndk117__compressed_pairIP8Arm64ArgRNS_9allocatorIS1_EEEE", !235, i64 0, !247, i64 8}
!247 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIRNS_9allocatorI8Arm64ArgEELi1ELb0EEE", !17, i64 0}
!248 = !{!245, !17, i64 16}
!249 = !{!245, !17, i64 8}
!250 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEEFvRNS_14__split_bufferIS1_RS3_EEE"}
!251 = !{i64 0, !"_ZTSMNSt6__ndk116__non_trivial_ifILb1ENS_9allocatorI8Arm64ArgEEEEFPS2_mE"}
!252 = distinct !{!252, !127}
!253 = !{i64 0, !"_ZTSMNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEEEDoFvvE"}
!254 = !{i64 0, !"_ZTSMNSt6__ndk114__split_bufferI8Arm64ArgRNS_9allocatorIS1_EEEEDoFvPS1_E"}
!255 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEEFvOS1_E"}
!256 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEEKFmmE"}
!257 = !{!45, !30, i64 0}
!258 = !{!239, !35, i64 40}
!259 = !{!239, !30, i64 48}
!260 = distinct !{!260, !127}
!261 = distinct !{!261, !127}
!262 = !{!263}
!263 = distinct !{!263, !264, !"_ZN6asmjit5v1_163a644regsL1xEj: argument 0"}
!264 = distinct !{!264, !"_ZN6asmjit5v1_163a644regsL1xEj"}
!265 = !{!24, !24, i64 0}
!266 = !{!267}
!267 = distinct !{!267, !268, !"_ZN6asmjit5v1_163a644regsL1xEj: argument 0"}
!268 = distinct !{!268, !"_ZN6asmjit5v1_163a644regsL1xEj"}
!269 = distinct !{!269, !127}
!270 = distinct !{!270, !127}
!271 = !{!272}
!272 = distinct !{!272, !273, !"_ZN6asmjit5v1_163a644regsL1xEj: argument 0"}
!273 = distinct !{!273, !"_ZN6asmjit5v1_163a644regsL1xEj"}
!274 = !{!275}
!275 = distinct !{!275, !276, !"_ZN6asmjit5v1_163a644regsL1xEj: argument 0"}
!276 = distinct !{!276, !"_ZN6asmjit5v1_163a644regsL1xEj"}
!277 = !{!278}
!278 = distinct !{!278, !279, !"_ZN6asmjit5v1_163a644regsL1xEj: argument 0"}
!279 = distinct !{!279, !"_ZN6asmjit5v1_163a644regsL1xEj"}
!280 = !{!281}
!281 = distinct !{!281, !282, !"_ZNK6asmjit5v1_163arm3Mem3preEl: argument 0"}
!282 = distinct !{!282, !"_ZNK6asmjit5v1_163arm3Mem3preEl"}
!283 = !{!284}
!284 = distinct !{!284, !285, !"_ZNK6asmjit5v1_163arm3Mem4postEl: argument 0"}
!285 = distinct !{!285, !"_ZNK6asmjit5v1_163arm3Mem4postEl"}
!286 = distinct !{!286, !127}
!287 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFNS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEERKjE"}
!288 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFNS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEERKjSF_E"}
!289 = !{!290}
!290 = distinct !{!290, !291, !"_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__construct_nodeIJRKjEEENS_10unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS3_ISC_EEEEEEDpOT_: argument 0"}
!291 = distinct !{!291, !"_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__construct_nodeIJRKjEEENS_10unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS3_ISC_EEEEEEDpOT_"}
!292 = !{i64 0, i64 8, !16, i64 8, i64 1, !265}
!293 = !{!294, !24, i64 8}
!294 = !{!"_ZTSNSt6__ndk122__tree_node_destructorINS_9allocatorINS_11__tree_nodeIjPvEEEEEE", !17, i64 0, !24, i64 8}
!295 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFRPNS_16__tree_node_baseIPvEERPNS_15__tree_end_nodeIS9_EERKjE"}
!296 = !{!226, !17, i64 0}
!297 = !{!230, !17, i64 8}
!298 = distinct !{!298, !127}
!299 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEDoFvPNS_15__tree_end_nodeIPNS_16__tree_node_baseIPvEEEERSA_SA_E"}
!300 = !{!230, !24, i64 24}
!301 = distinct !{!301, !127}
!302 = !{i64 0, !"_ZTSMNSt6__ndk110unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS_9allocatorIS3_EEEEEEDoFvPS3_E"}
!303 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFNS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEEOjE"}
!304 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFNS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEERKjOjE"}
!305 = !{!306}
!306 = distinct !{!306, !307, !"_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__construct_nodeIJjEEENS_10unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS3_ISA_EEEEEEDpOT_: argument 0"}
!307 = distinct !{!307, !"_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__construct_nodeIJjEEENS_10unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS3_ISA_EEEEEEDpOT_"}
!308 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFNS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEENS_21__tree_const_iteratorIjSA_lEERKjE"}
!309 = !{i64 0, !"_ZTSMNSt6__ndk121__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEEFRS5_vE"}
!310 = !{!311, !17, i64 0}
!311 = !{!"_ZTSNSt6__ndk121__tree_const_iteratorIjPNS_11__tree_nodeIjPvEElEE", !17, i64 0}
!312 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFNS_4pairINS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEEbEENS_21__tree_const_iteratorIjSB_lEERKjSH_E"}
!313 = !{!314}
!314 = distinct !{!314, !315, !"_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__construct_nodeIJRKjEEENS_10unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS3_ISC_EEEEEEDpOT_: argument 0"}
!315 = distinct !{!315, !"_ZNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEE16__construct_nodeIJRKjEEENS_10unique_ptrINS_11__tree_nodeIjPvEENS_22__tree_node_destructorINS3_ISC_EEEEEEDpOT_"}
!316 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFRPNS_16__tree_node_baseIPvEENS_21__tree_const_iteratorIjPNS_11__tree_nodeIjS7_EElEERPNS_15__tree_end_nodeIS9_EESA_RKjE"}
!317 = distinct !{!317, !127}
!318 = distinct !{!318, !127}
!319 = distinct !{!319, !127}
!320 = distinct !{!320, !127}
!321 = distinct !{!321, !127}
!322 = distinct !{!322, !127}
!323 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFmRKjE"}
!324 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFNS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEERKjE"}
!325 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFNS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEENS_21__tree_const_iteratorIjSA_lEEE"}
!326 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEFNS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEERKjSA_PNS_15__tree_end_nodeIPNS_16__tree_node_baseIS8_EEEEE"}
!327 = distinct !{!327, !127}
!328 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEDoFNS_15__tree_iteratorIjPNS_11__tree_nodeIjPvEElEESA_E"}
!329 = distinct !{!329, !127}
!330 = distinct !{!330, !127}
!331 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEDoFvPNS_11__tree_nodeIjPvEEE"}
!332 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEDoFRS5_OS5_E"}
!333 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEDoFvRS5_NS_17integral_constantIbLb1EEEE"}
!334 = !{i64 0, !"_ZTSMNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEEEFvmE"}
!335 = !{!336, !17, i64 0}
!336 = !{!"_ZTSNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEEE", !17, i64 0, !17, i64 8, !17, i64 16, !337, i64 24}
!337 = !{!"_ZTSNSt6__ndk117__compressed_pairIPN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEEE", !105, i64 0, !338, i64 8}
!338 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIRNS_9allocatorIN6asmjit5v1_163a643GpXEEELi1ELb0EEE", !17, i64 0}
!339 = !{!336, !17, i64 16}
!340 = !{!336, !17, i64 8}
!341 = !{i64 0, !"_ZTSM15RegProtectGuardFvRKN6asmjit5v1_163a643GpXEE"}
!342 = !{i64 0, !"_ZTSMNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEEEFvRKS4_E"}
!343 = !{!344}
!344 = distinct !{!344, !345, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi: argument 0"}
!345 = distinct !{!345, !"_ZN6asmjit5v1_163a64L3ptrERKNS1_2GpEi"}
!346 = !{!347}
!347 = distinct !{!347, !348, !"_ZNK6asmjit5v1_163arm3Mem3preEl: argument 0"}
!348 = distinct !{!348, !"_ZNK6asmjit5v1_163arm3Mem3preEl"}
!349 = distinct !{!349, !127}
!350 = !{i64 0, !"_ZTSMNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEEEKFvvE"}
!351 = !{i64 0, !"_ZTSMNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEEEFvRNS_14__split_bufferIS4_RS6_EEE"}
!352 = distinct !{!352, !127}
!353 = !{i64 0, !"_ZTSMNSt6__ndk116__non_trivial_ifILb1ENS_9allocatorIN6asmjit5v1_163a643GpXEEEEEFPS5_mE"}
!354 = !{i64 0, !"_ZTSMNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEEEDoFvvE"}
!355 = !{i64 0, !"_ZTSMNSt6__ndk114__split_bufferIN6asmjit5v1_163a643GpXERNS_9allocatorIS4_EEEEDoFvPS4_E"}
!356 = !{i64 0, !"_ZTSMNSt6__ndk16vectorIN6asmjit5v1_163a643GpXENS_9allocatorIS4_EEEEKFmmE"}
!357 = !{!358}
!358 = distinct !{!358, !359, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorI8Arm64ArgNS_9allocatorIS2_EEE16__destroy_vectorEEENS_17__exception_guardIT_EES8_: argument 0"}
!359 = distinct !{!359, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorI8Arm64ArgNS_9allocatorIS2_EEE16__destroy_vectorEEENS_17__exception_guardIT_EES8_"}
!360 = !{!361, !24, i64 8}
!361 = !{!"_ZTSNSt6__ndk117__exception_guardINS_6vectorI8Arm64ArgNS_9allocatorIS2_EEE16__destroy_vectorEEE", !362, i64 0, !24, i64 8}
!362 = !{!"_ZTSNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE16__destroy_vectorE", !17, i64 0}
!363 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEEFvPS1_S5_mE"}
!364 = distinct !{!364, !127}
!365 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEE16__destroy_vectorEFvvE"}
!366 = !{!362, !17, i64 0}
!367 = !{i64 0, !"_ZTSM11IdleRegPoolFvvE"}
!368 = distinct !{!368, !127}
!369 = !{i64 0, !"_ZTSM11IdleRegPoolFvRKN6asmjit5v1_163a643GpXEE"}
!370 = !{i64 0, !"_ZTSM11IdleRegPoolFvRKN6asmjit5v1_163a643GpWEE"}
!371 = !{i64 0, !"_ZTSMNSt6__ndk16__treeIjNS_4lessIjEENS_9allocatorIjEEEEDoFvvE"}
!372 = !{i64 0, !"_ZTSM11IdleRegPoolFN6asmjit5v1_163a643GpXEvE"}
!373 = !{!374}
!374 = distinct !{!374, !375, !"_ZN6asmjit5v1_163a644regsL1xEj: argument 0"}
!375 = distinct !{!375, !"_ZN6asmjit5v1_163a644regsL1xEj"}
!376 = !{i64 0, !"_ZTSM11IdleRegPoolFN6asmjit5v1_163a643GpWEvE"}
!377 = !{!378}
!378 = distinct !{!378, !379, !"_ZN6asmjit5v1_163a644regsL1wEj: argument 0"}
!379 = distinct !{!379, !"_ZN6asmjit5v1_163a644regsL1wEj"}
!380 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEEFRS1_RKS1_E"}
!381 = !{i64 0, !"_ZTSMNSt6__ndk16vectorI8Arm64ArgNS_9allocatorIS1_EEEEFvRKS1_E"}
!382 = !{!383, !35, i64 0}
!383 = !{!"_ZTSN13kernel_module9SymbolHitE", !35, i64 0, !13, i64 8}
!384 = !{!385}
!385 = distinct !{!385, !386, !"_ZN6asmjit5v1_163a644regsL1xEj: argument 0"}
!386 = distinct !{!386, !"_ZN6asmjit5v1_163a644regsL1xEj"}
!387 = !{!388}
!388 = distinct !{!388, !389, !"_ZN6asmjit5v1_163a644regsL1wEj: argument 0"}
!389 = distinct !{!389, !"_ZN6asmjit5v1_163a644regsL1wEj"}
!390 = !{!391}
!391 = distinct !{!391, !392, !"_ZN6asmjit5v1_163a644regsL1xEj: argument 0"}
!392 = distinct !{!392, !"_ZN6asmjit5v1_163a644regsL1xEj"}
!393 = !{!394}
!394 = distinct !{!394, !395, !"_ZN6asmjit5v1_163a644regsL1wEj: argument 0"}
!395 = distinct !{!395, !"_ZN6asmjit5v1_163a644regsL1wEj"}
!396 = distinct !{!396, !127}
!397 = distinct !{!397, !127}
!398 = distinct !{!398, !127}
!399 = !{i64 0, !"_ZTSMN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEEEFjRKNS1_2GpERKNS0_3ImmESA_E"}
!400 = !{i64 0, !"_ZTSMN6asmjit5v1_163a6416EmitterExplicitTINS1_9AssemblerEEEFjRKNS1_2GpEE"}
!401 = !{i64 0, !"_ZTSMNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEEEFNS_15__hash_iteratorIPNS_11__hash_nodeIS8_PvEEEENS_21__hash_const_iteratorISN_EEE"}
!402 = !{i64 0, !"_ZTSMNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEEEDoFNS_10unique_ptrINS_11__hash_nodeIS8_PvEENS_22__hash_node_destructorINS5_ISM_EEEEEENS_21__hash_const_iteratorIPSM_EEE"}
!403 = distinct !{!403, !127}
!404 = !{!405, !17, i64 0}
!405 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEELi0ELb0EEE", !17, i64 0}
!406 = !{i64 0, i64 1, !265}
!407 = !{i64 0, !"_ZTSMNSt6__ndk110unique_ptrINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEENS_22__hash_node_destructorINS6_ISB_EEEEEEDoFvPSB_E"}
!408 = !{i64 0, !"_ZTSMNSt6__ndk122__hash_node_destructorINS_9allocatorINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPvEEEEEEDoFvPSB_E"}
!409 = !{!410, !24, i64 8}
!410 = !{!"_ZTSNSt6__ndk122__hash_node_destructorINS_9allocatorINS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPvEEEEEE", !17, i64 0, !24, i64 8}
!411 = !{i64 0, !"_ZTSMNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEEEFNS_4pairINS_15__hash_iteratorIPNS_11__hash_nodeIS8_PvEEEEbEERKmRKNS_21piecewise_construct_tEONS_5tupleIJSS_EEEONSW_IJEEEE"}
!412 = distinct !{!412, !127}
!413 = !{!183, !183, i64 0}
!414 = !{i64 0, !"_ZTSMNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEEEFNS_10unique_ptrINS_11__hash_nodeIS8_PvEENS_22__hash_node_destructorINS5_ISM_EEEEEEmRKNS_21piecewise_construct_tEONS_5tupleIJRKmEEEONSU_IJEEEE"}
!415 = !{!209, !35, i64 0}
!416 = !{i64 0, !"_ZTSMNSt6__ndk112__hash_tableINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEENS_22__unordered_map_hasherImS8_NS_4hashImEENS_8equal_toImEELb1EEENS_21__unordered_map_equalImS8_SD_SB_Lb1EEENS5_IS8_EEEEFvmE"}
!417 = distinct !{!417, !127}
!418 = distinct !{!418, !127}
!419 = !{i64 0, !"_ZTSMNSt6__ndk110unique_ptrIA_PNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEPvEEEENS_25__bucket_list_deallocatorINS7_ISF_EEEEEEDoFvPSF_E"}
!420 = !{i64 0, !"_ZTSMNSt6__ndk116__non_trivial_ifILb1ENS_9allocatorIPNS_16__hash_node_baseIPNS_11__hash_nodeINS_17__hash_value_typeImNS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPvEEEEEEEEFPSF_mE"}

declare void @maplesu_stat_uid_template_84(ptr sret(%"class.std::__ndk1::basic_string") align 8, ptr)
declare void @maplesu_emit_uid_stat_copy_84(ptr, ptr, ptr, ptr, i64, ptr)
