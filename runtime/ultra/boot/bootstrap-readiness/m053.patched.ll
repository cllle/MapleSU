; ModuleID = 'D:\Maple_Workflow\MapleSU\runtime\pro\boot\sdk-m053.bc'
source_filename = "C:/Users/abc/Desktop/testModule/kernel_module_kit/src/jni/../../../kernel_module_kit/src/jni/skroot_env/skroot_installer.cpp"
target datalayout = "e-m:e-i8:8:32-i16:16:32-i64:64-i128:128-n32:64-S128"
target triple = "aarch64-unknown-linux-android21"

%"class.std::__ndk1::vector" = type { ptr, ptr, %"class.std::__ndk1::__compressed_pair.2" }
%"class.std::__ndk1::__compressed_pair.2" = type { %"struct.std::__ndk1::__compressed_pair_elem.3" }
%"struct.std::__ndk1::__compressed_pair_elem.3" = type { ptr }
%"class.std::__ndk1::locale::id" = type <{ %"struct.std::__ndk1::once_flag", i32, [4 x i8] }>
%"struct.std::__ndk1::once_flag" = type { i64 }
%"struct.std::__ndk1::basic_string<char>::__long" = type { %struct.anon, i64, ptr }
%struct.anon = type { i64 }
%"struct.std::__ndk1::__exception_guard" = type <{ %"class.std::__ndk1::vector<std::__ndk1::string>::__destroy_vector", i8, [7 x i8] }>
%"class.std::__ndk1::vector<std::__ndk1::string>::__destroy_vector" = type { ptr }
%"class.std::__ndk1::basic_string" = type { %"class.std::__ndk1::__compressed_pair" }
%"class.std::__ndk1::__compressed_pair" = type { %"struct.std::__ndk1::__compressed_pair_elem" }
%"struct.std::__ndk1::__compressed_pair_elem" = type { %"struct.std::__ndk1::basic_string<char>::__rep" }
%"struct.std::__ndk1::basic_string<char>::__rep" = type { %union.anon }
%union.anon = type { %"struct.std::__ndk1::basic_string<char>::__long" }
%"class.std::__ndk1::__fs::filesystem::path" = type { %"class.std::__ndk1::basic_string" }
%"class.skroot_env::fork_pipe_info" = type { %"class.skroot_env::fork_base_info" }
%"class.skroot_env::fork_base_info" = type { i32, i32, i32, i32, i32, i32 }
%"struct.std::__ndk1::basic_string<char>::__short" = type { %struct.anon.0, [0 x i8], [23 x i8] }
%struct.anon.0 = type { i8 }
%"class.std::__ndk1::error_code" = type { i32, ptr }
%"class.std::__ndk1::vector.32" = type { ptr, ptr, %"class.std::__ndk1::__compressed_pair.33" }
%"class.std::__ndk1::__compressed_pair.33" = type { %"struct.std::__ndk1::__compressed_pair_elem.34" }
%"struct.std::__ndk1::__compressed_pair_elem.34" = type { ptr }
%"struct.skroot_env::module_desc" = type { [1024 x i8], [1024 x i8], [4096 x i8], [1024 x i8], [1024 x i8], [1024 x i8], i8, %"struct.skroot_env::SkrootSdkVersion" }
%"struct.skroot_env::SkrootSdkVersion" = type { i32, i32, i32 }
%"struct.skroot_env::module_record" = type { %"struct.skroot_env::module_desc", i32 }
%"struct.std::__ndk1::__exception_guard.10" = type <{ %"class.std::__ndk1::_AllocatorDestroyRangeReverse", i8, [7 x i8] }>
%"class.std::__ndk1::_AllocatorDestroyRangeReverse" = type { ptr, ptr, ptr }
%"class.std::__ndk1::allocator" = type { i8 }
%"class.std::__ndk1::basic_ifstream" = type { %"class.std::__ndk1::basic_istream.base", %"class.std::__ndk1::basic_filebuf", %"class.std::__ndk1::basic_ios.base" }
%"class.std::__ndk1::basic_istream.base" = type { ptr, i64 }
%"class.std::__ndk1::basic_filebuf" = type <{ %"class.std::__ndk1::basic_streambuf", ptr, ptr, ptr, [8 x i8], i64, ptr, i64, ptr, ptr, %struct.mbstate_t, %struct.mbstate_t, i32, i32, i8, i8, i8, [5 x i8] }>
%"class.std::__ndk1::basic_streambuf" = type { ptr, %"class.std::__ndk1::locale", ptr, ptr, ptr, ptr, ptr, ptr }
%"class.std::__ndk1::locale" = type { ptr }
%struct.mbstate_t = type { [4 x i8], [4 x i8] }
%"class.std::__ndk1::basic_ios.base" = type <{ %"class.std::__ndk1::ios_base", ptr, i32 }>
%"class.std::__ndk1::ios_base" = type { ptr, i32, i64, i64, i32, i32, ptr, ptr, ptr, ptr, i64, i64, ptr, i64, i64, ptr, i64, i64 }
%"class.std::__ndk1::vector.13" = type { ptr, ptr, %"class.std::__ndk1::__compressed_pair.14" }
%"class.std::__ndk1::__compressed_pair.14" = type { %"struct.std::__ndk1::__compressed_pair_elem.15" }
%"struct.std::__ndk1::__compressed_pair_elem.15" = type { ptr }
%"struct.std::__va_list" = type { ptr, ptr, ptr, i32, i32 }
%"class.std::__ndk1::__fs::filesystem::file_status" = type { i8, i32 }
%"class.std::__ndk1::vector<unsigned char>::__destroy_vector" = type { ptr }
%"class.std::__ndk1::basic_istream" = type { ptr, i64, %"class.std::__ndk1::basic_ios.base" }
%"class.std::__ndk1::basic_ios" = type <{ %"class.std::__ndk1::ios_base", ptr, i32, [4 x i8] }>
%"class.std::__ndk1::unique_ptr" = type { %"class.std::__ndk1::__compressed_pair.21" }
%"class.std::__ndk1::__compressed_pair.21" = type { %"struct.std::__ndk1::__compressed_pair_elem.22", %"struct.std::__ndk1::__compressed_pair_elem.23" }
%"struct.std::__ndk1::__compressed_pair_elem.22" = type { ptr }
%"struct.std::__ndk1::__compressed_pair_elem.23" = type { ptr }
%"struct.std::__ndk1::__split_buffer" = type { ptr, ptr, ptr, %"class.std::__ndk1::__compressed_pair.24" }
%"class.std::__ndk1::__compressed_pair.24" = type { %"struct.std::__ndk1::__compressed_pair_elem.15", %"struct.std::__ndk1::__compressed_pair_elem.25" }
%"struct.std::__ndk1::__compressed_pair_elem.25" = type { ptr }
%"class.std::__ndk1::basic_string_view" = type { ptr, i64 }
%"class.std::__ndk1::basic_ofstream" = type { %"class.std::__ndk1::basic_ostream.base", %"class.std::__ndk1::basic_filebuf", %"class.std::__ndk1::basic_ios.base" }
%"class.std::__ndk1::basic_ostream.base" = type { ptr }
%"class.std::__ndk1::basic_ostream<char>::sentry" = type { i8, ptr }
%"class.std::__ndk1::vector<skroot_env::module_record>::__destroy_vector" = type { ptr }

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc = comdat any

$_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEC2B7v170000ESt16initializer_listIS6_E = comdat any

$_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev = comdat any

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

$_ZN10skroot_env14fork_base_info9close_allEv = comdat any

$_ZN10skroot_env14fork_base_infoD2Ev = comdat any

$_ZN10skroot_env14fork_base_infoC2Ev = comdat any

$_ZN10skroot_env14fork_base_info5resetEv = comdat any

$_ZNSt6__ndk1eqB7v170000IcNS_11char_traitsIcEENS_9allocatorIcEEEEbRKNS_12basic_stringIT_T0_T1_EEPKS6_ = comdat any

$_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6substrB7v170000Emm = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEaSB7v170000EOS5_ = comdat any

$_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_out_of_rangeB7v170000Ev = comdat any

$_ZNSt6__ndk120__throw_out_of_rangeB7v170000EPKc = comdat any

$_ZNSt12out_of_rangeC2B7v170000EPKc = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE13__move_assignB7v170000ERS5_NS_17integral_constantIbLb1EEE = comdat any

$_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000INS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEvEERKT_NS2_6formatE = comdat any

$_ZNSt6__ndk1plB7v170000IcNS_11char_traitsIcEENS_9allocatorIcEEEENS_12basic_stringIT_T0_T1_EEOS9_PKS6_ = comdat any

$_ZNSt6__ndk1eqB7v170000INS_9allocatorIcEEEEbRKNS_12basic_stringIcNS_11char_traitsIcEET_EES9_ = comdat any

$_ZNSt6__ndk111char_traitsIcE7compareEPKcS3_m = comdat any

$_ZNSt6__ndk14__fs10filesystemdvB7v170000ERKNS1_4pathES4_ = comdat any

$_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA15_cvEERKT_NS2_6formatE = comdat any

$_ZNSt6__ndk14__fs10filesystem4pathdVB7v170000ERKS2_ = comdat any

$_ZNKSt6__ndk14__fs10filesystem4path12has_filenameB7v170000Ev = comdat any

$_ZNKSt6__ndk14__fs10filesystem4path18has_root_directoryB7v170000Ev = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb1EEERS5_PKcm = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb0EEERS5_PKcm = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000ERKS5_ = comdat any

$_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA15_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_ = comdat any

$_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA15_cPccLb1EE11__range_endB7v170000EPKc = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000IPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESA_SA_ = comdat any

$_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE15__addr_in_rangeB7v170000IRKcEEbOT_ = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initIPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueEvE4typeESA_SA_ = comdat any

$_ZNSt6__ndk14__fs10filesystem6existsB7v170000ERKNS1_4pathERNS_10error_codeE = comdat any

$_ZNSt6__ndk110error_code5clearB7v170000Ev = comdat any

$_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000INS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEvRSA_RKT_ = comdat any

$_ZNSt6__ndk14__fs10filesystem20__is_pathable_stringINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEvE11__range_endB7v170000ERKS8_ = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignINS_11__wrap_iterIPhEEEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESB_SB_ = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev = comdat any

$_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEC1EPKcj = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE6resizeEm = comdat any

$_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev = comdat any

$_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEEC2B7v170000EPNS_15basic_streambufIcS2_EE = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEC2Ev = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE4openEPKcj = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED2Ev = comdat any

$_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED0Ev = comdat any

$_ZTv0_n24_NSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev = comdat any

$_ZTv0_n24_NSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED0Ev = comdat any

$_ZNSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4initB7v170000EPNS_15basic_streambufIcS2_EE = comdat any

$_ZNSt6__ndk19has_facetB7v170000INS_7codecvtIcc9mbstate_tEEEEbRKNS_6localeE = comdat any

$_ZNSt6__ndk19use_facetB7v170000INS_7codecvtIcc9mbstate_tEEEERKT_RKNS_6localeE = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED0Ev = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE5imbueERKNS_6localeE = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE6setbufEPcl = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE7seekoffExNS_8ios_base7seekdirEj = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE7seekposENS_4fposI9mbstate_tEEj = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE4syncEv = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE9underflowEv = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE9pbackfailEi = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE8overflowEi = comdat any

$_ZNSt6__ndk116__throw_bad_castB7v170000Ev = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE11__read_modeEv = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE12__write_modeEv = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE15__make_mdstringEj = comdat any

$_ZNSt6__ndk18ios_base8setstateB7v170000Ej = comdat any

$_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE5closeEv = comdat any

$_ZNSt6__ndk110unique_ptrI7__sFILEPFiPS1_EED2B7v170000Ev = comdat any

$_ZNSt6__ndk110unique_ptrI7__sFILEPFiPS1_EE5resetB7v170000ES2_ = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE8__appendEm = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE18__construct_at_endEm = comdat any

$_ZNKSt6__ndk16vectorIhNS_9allocatorIhEEE11__recommendB7v170000Em = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE26__swap_out_circular_bufferERNS_14__split_bufferIhRS2_EE = comdat any

$_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEED2Ev = comdat any

$_ZNKSt6__ndk16vectorIhNS_9allocatorIhEEE20__throw_length_errorB7v170000Ev = comdat any

$_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIhEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m = comdat any

$_ZNSt6__ndk14moveB7v170000INS_16reverse_iteratorIPhEES3_EET0_T_S5_S4_ = comdat any

$_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEE5clearB7v170000Ev = comdat any

$_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEE17__destruct_at_endB7v170000EPh = comdat any

$_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED2Ev = comdat any

$_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE15__addr_in_rangeB7v170000IRhEEbOT_ = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initINS_11__wrap_iterIPhEEEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueEvE4typeESB_SB_ = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE14__assign_shortEPKcm = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_externalEPKcm = comdat any

$_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE16__destroy_vectorclB7v170000Ev = comdat any

$_ZNSt6__ndk1eqB7v170000IcNS_11char_traitsIcEEEEbNS_17basic_string_viewIT_T0_EES6_ = comdat any

$_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEcvNS_17basic_string_viewIcS2_EEB7v170000Ev = comdat any

$_ZNKSt6__ndk117basic_string_viewIcNS_11char_traitsIcEEE7compareES3_ = comdat any

$_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEC1EPKcj = comdat any

$_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEE5closeEv = comdat any

$_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev = comdat any

$_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEEC2B7v170000EPNS_15basic_streambufIcS2_EE = comdat any

$_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED0Ev = comdat any

$_ZTv0_n24_NSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev = comdat any

$_ZTv0_n24_NSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED0Ev = comdat any

$_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED2Ev = comdat any

$_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA13_cvEERKT_NS2_6formatE = comdat any

$_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA13_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_ = comdat any

$_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA13_cPccLb1EE11__range_endB7v170000EPKc = comdat any

$_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_externalEPKc = comdat any

$_ZNSt6__ndk1lsB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_NS_17basic_string_viewIS4_S5_EE = comdat any

$_ZNSt6__ndk124__put_character_sequenceB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m = comdat any

$_ZNSt6__ndk116__pad_and_outputB7v170000IcNS_11char_traitsIcEEEENS_19ostreambuf_iteratorIT_T0_EES6_PKS4_S8_S8_RNS_8ios_baseES4_ = comdat any

$_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4fillB7v170000Ev = comdat any

$_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE5widenB7v170000Ec = comdat any

$_ZNSt6__ndk19use_facetB7v170000INS_5ctypeIcEEEERKT_RKNS_6localeE = comdat any

$_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA23_cvEERKT_NS2_6formatE = comdat any

$_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA23_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_ = comdat any

$_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA23_cPccLb1EE11__range_endB7v170000EPKc = comdat any

$_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA5_cvEERKT_NS2_6formatE = comdat any

$_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA5_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_ = comdat any

$_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA5_cPccLb1EE11__range_endB7v170000EPKc = comdat any

$_ZN10skroot_env14fork_base_info10safe_closeERi = comdat any

$_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IPKcvEERKT_NS2_6formatE = comdat any

$_ZNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEED2B7v170000Ev = comdat any

$_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IPKcEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_ = comdat any

$_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIPKcS4_cLb1EE11__range_endB7v170000ES4_ = comdat any

$_ZNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEE16__destroy_vectorclB7v170000Ev = comdat any

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

$_ZTSSt12out_of_range = comdat any

$_ZTISt12out_of_range = comdat any

$_ZTVNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE = comdat any

$_ZTTNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE = comdat any

$_ZTCNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE0_NS_13basic_istreamIcS2_EE = comdat any

$_ZTVNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEE = comdat any

$_ZTSSt8bad_cast = comdat any

$_ZTISt8bad_cast = comdat any

$_ZTVNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE = comdat any

$_ZTTNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE = comdat any

$_ZTCNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE0_NS_13basic_ostreamIcS2_EE = comdat any

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
@.str.16 = private unnamed_addr constant [9 x i8] c"%u.%u.%u\00", align 1
@.str.17 = private unnamed_addr constant [6 x i8] c"5.0.5\00", align 1
@.str.18 = private unnamed_addr constant [13 x i8] c"basic_string\00", align 1
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
@.str.19 = private unnamed_addr constant [7 x i8] c"vector\00", align 1
@.str.20 = private unnamed_addr constant [32 x i8] c"/proc/sys/kernel/random/boot_id\00", align 1
@.str.21 = private unnamed_addr constant [2 x i8] c"r\00", align 1
@_ZTSSt12out_of_range = linkonce_odr constant [17 x i8] c"St12out_of_range\00", comdat, align 1
@_ZTISt12out_of_range = linkonce_odr constant { ptr, ptr, ptr } { ptr getelementptr inbounds (ptr, ptr @_ZTVN10__cxxabiv120__si_class_type_infoE, i64 2), ptr @_ZTSSt12out_of_range, ptr @_ZTISt11logic_error }, comdat, align 8
@_ZTVSt12out_of_range = available_externally unnamed_addr constant { [5 x ptr] } { [5 x ptr] [ptr null, ptr null, ptr @_ZNSt12out_of_rangeD1Ev, ptr @_ZNSt12out_of_rangeD0Ev, ptr @_ZNKSt11logic_error4whatEv] }, align 8
@.str.22 = private unnamed_addr constant [19 x i8] c".boot_session.work\00", align 1
@.str.23 = private unnamed_addr constant [15 x i8] c"skroot.sdk.ver\00", align 1
@.str.24 = private unnamed_addr constant [10 x i8] c"/data/%s/\00", align 1
@_ZTVNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE = linkonce_odr unnamed_addr constant { [5 x ptr], [5 x ptr] } { [5 x ptr] [ptr inttoptr (i64 184 to ptr), ptr null, ptr null, ptr @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev, ptr @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED0Ev], [5 x ptr] [ptr inttoptr (i64 -184 to ptr), ptr inttoptr (i64 -184 to ptr), ptr null, ptr @_ZTv0_n24_NSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev, ptr @_ZTv0_n24_NSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED0Ev] }, comdat, align 8, !type !0, !type !1, !type !2, !type !3
@_ZTTNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE = linkonce_odr unnamed_addr constant [4 x ptr] [ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE, i32 0, inrange i32 0, i32 3), ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTCNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE0_NS_13basic_istreamIcS2_EE, i32 0, inrange i32 0, i32 3), ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTCNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE0_NS_13basic_istreamIcS2_EE, i32 0, inrange i32 1, i32 3), ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE, i32 0, inrange i32 1, i32 3)], comdat, align 8
@_ZTCNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE0_NS_13basic_istreamIcS2_EE = linkonce_odr unnamed_addr constant { [5 x ptr], [5 x ptr] } { [5 x ptr] [ptr inttoptr (i64 184 to ptr), ptr null, ptr null, ptr @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED1Ev, ptr @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED0Ev], [5 x ptr] [ptr inttoptr (i64 -184 to ptr), ptr inttoptr (i64 -184 to ptr), ptr null, ptr @_ZTv0_n24_NSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED1Ev, ptr @_ZTv0_n24_NSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED0Ev] }, comdat, align 8, !type !0, !type !2, !type !3
@_ZTVNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEE = linkonce_odr unnamed_addr constant { [16 x ptr] } { [16 x ptr] [ptr null, ptr null, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED2Ev, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED0Ev, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE5imbueERKNS_6localeE, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE6setbufEPcl, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE7seekoffExNS_8ios_base7seekdirEj, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE7seekposENS_4fposI9mbstate_tEEj, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE4syncEv, ptr @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEE9showmanycEv, ptr @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEE6xsgetnEPcl, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE9underflowEv, ptr @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEE5uflowEv, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE9pbackfailEi, ptr @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEE6xsputnEPKcl, ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE8overflowEi] }, comdat, align 8, !type !4, !type !5, !type !6, !type !7, !type !8, !type !9, !type !10, !type !11, !type !12, !type !13, !type !14, !type !15, !type !16, !type !17, !type !18, !type !19, !type !20, !type !21, !type !22, !type !23, !type !24, !type !25, !type !26, !type !27, !type !28, !type !29
@_ZNSt6__ndk17codecvtIcc9mbstate_tE2idE = external global %"class.std::__ndk1::locale::id", align 8
@_ZTSSt8bad_cast = linkonce_odr constant [12 x i8] c"St8bad_cast\00", comdat, align 1
@_ZTISt8bad_cast = linkonce_odr constant { ptr, ptr, ptr } { ptr getelementptr inbounds (ptr, ptr @_ZTVN10__cxxabiv120__si_class_type_infoE, i64 2), ptr @_ZTSSt8bad_cast, ptr @_ZTISt9exception }, comdat, align 8
@.str.25 = private unnamed_addr constant [3 x i8] c"we\00", align 1
@.str.26 = private unnamed_addr constant [3 x i8] c"ae\00", align 1
@.str.27 = private unnamed_addr constant [3 x i8] c"re\00", align 1
@.str.28 = private unnamed_addr constant [4 x i8] c"r+e\00", align 1
@.str.29 = private unnamed_addr constant [4 x i8] c"w+e\00", align 1
@.str.30 = private unnamed_addr constant [4 x i8] c"a+e\00", align 1
@.str.31 = private unnamed_addr constant [4 x i8] c"wbe\00", align 1
@.str.32 = private unnamed_addr constant [4 x i8] c"abe\00", align 1
@.str.33 = private unnamed_addr constant [4 x i8] c"rbe\00", align 1
@.str.34 = private unnamed_addr constant [5 x i8] c"r+be\00", align 1
@.str.35 = private unnamed_addr constant [5 x i8] c"w+be\00", align 1
@.str.36 = private unnamed_addr constant [5 x i8] c"a+be\00", align 1
@.str.37 = private unnamed_addr constant [8 x i8] c".enable\00", align 1
@.str.38 = private unnamed_addr constant [5 x i8] c".log\00", align 1
@_ZTVNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE = linkonce_odr unnamed_addr constant { [5 x ptr], [5 x ptr] } { [5 x ptr] [ptr inttoptr (i64 176 to ptr), ptr null, ptr null, ptr @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev, ptr @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED0Ev], [5 x ptr] [ptr inttoptr (i64 -176 to ptr), ptr inttoptr (i64 -176 to ptr), ptr null, ptr @_ZTv0_n24_NSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev, ptr @_ZTv0_n24_NSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED0Ev] }, comdat, align 8, !type !30, !type !31, !type !2, !type !3
@_ZTTNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE = linkonce_odr unnamed_addr constant [4 x ptr] [ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE, i32 0, inrange i32 0, i32 3), ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTCNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE0_NS_13basic_ostreamIcS2_EE, i32 0, inrange i32 0, i32 3), ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTCNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE0_NS_13basic_ostreamIcS2_EE, i32 0, inrange i32 1, i32 3), ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE, i32 0, inrange i32 1, i32 3)], comdat, align 8
@_ZTCNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE0_NS_13basic_ostreamIcS2_EE = linkonce_odr unnamed_addr constant { [5 x ptr], [5 x ptr] } { [5 x ptr] [ptr inttoptr (i64 176 to ptr), ptr null, ptr null, ptr @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED1Ev, ptr @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED0Ev], [5 x ptr] [ptr inttoptr (i64 -176 to ptr), ptr inttoptr (i64 -176 to ptr), ptr null, ptr @_ZTv0_n24_NSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED1Ev, ptr @_ZTv0_n24_NSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED0Ev] }, comdat, align 8, !type !30, !type !2, !type !3
@.str.40 = private unnamed_addr constant [21 x i8] c"u:object_r:kernel:s0\00", align 1
@.str.41 = private unnamed_addr constant [17 x i8] c"security.selinux\00", align 1
@.str.42 = private unnamed_addr constant [19 x i8] c".boot_fail_protect\00", align 1
@.str.43 = private unnamed_addr constant [5 x i8] c"boot\00", align 1
@.str.44 = private unnamed_addr constant [8 x i8] c"hotload\00", align 1
@.str.45 = private unnamed_addr constant [13 x i8] c"install.mode\00", align 1
@_ZNSt6__ndk15ctypeIcE2idE = external global %"class.std::__ndk1::locale::id", align 8
@.str.46 = private unnamed_addr constant [7 x i8] c"magica\00", align 1
@.str.47 = private unnamed_addr constant [14 x i8] c"cve2026_43499\00", align 1
@.str.48 = private unnamed_addr constant [5 x i8] c"none\00", align 1
@.str.49 = private unnamed_addr constant [23 x i8] c"install_config.exploit\00", align 1
@.str.50 = private unnamed_addr constant [7 x i8] c"reboot\00", align 1
@environ = external local_unnamed_addr global ptr, align 8
@.str.51 = private unnamed_addr constant [5 x i8] c"init\00", align 1
@llvm.global_ctors = appending global [1 x { i32, ptr, ptr }] [{ i32, ptr, ptr } { i32 65535, ptr @_GLOBAL__sub_I_skroot_installer.cpp, ptr null }]
@llvm.compiler.used = appending global [3 x ptr] [ptr @__skroot_module_before_server_exit_action_81a98535f12c96a0b3e457727705e44c, ptr @__skroot_module_last_web_ui_server_port_db2a2bebe2b190482ad0ac73996c7b86, ptr @__skroot_module_web_ui_main_ceb6f0bbebe5c8978eb2168799aa0ca4], section "llvm.metadata"

; Function Attrs: nocallback nofree nosync nounwind willreturn memory(argmem: readwrite)
declare void @llvm.lifetime.start.p0(i64 immarg, ptr nocapture) #0

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = icmp ne ptr %1, null
  tail call void @llvm.assume(i1 %3)
  %4 = tail call i64 @strlen(ptr noundef nonnull dereferenceable(1) %1) #34
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1, i64 noundef %4) #35
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
  %7 = load ptr, ptr %6, align 8, !tbaa !38
  tail call void @_ZdlPv(ptr noundef %7) #36
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
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %3) #37
  store ptr %0, ptr %3, align 8, !tbaa.struct !41, !alias.scope !44
  %7 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard", ptr %3, i64 0, i32 1
  store i8 0, ptr %7, align 8, !tbaa !47, !alias.scope !44
  %8 = icmp eq i64 %6, 0
  br i1 %8, label %14, label %9

9:                                                ; preds = %2
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE11__vallocateB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %6) #35
          to label %10 unwind label %12

10:                                               ; preds = %9
  %11 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %5, i64 %6
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE18__construct_at_endIPKS6_Li0EEEvT_SC_m(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %5, ptr noundef nonnull %11, i64 noundef %6) #35
          to label %14 unwind label %12

12:                                               ; preds = %10, %9
  %13 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %3) #34
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %3) #37
  resume { ptr, i32 } %13

14:                                               ; preds = %10, %2
  store i8 1, ptr %7, align 8, !tbaa !47
  call void @_ZNSt6__ndk117__exception_guardINS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %3) #34
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %3) #37
  ret void
}

; Function Attrs: nocallback nofree nosync nounwind willreturn memory(argmem: readwrite)
declare void @llvm.lifetime.end.p0(i64 immarg, ptr nocapture) #0

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::vector<std::__ndk1::string>::__destroy_vector", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #37
  store ptr %0, ptr %2, align 8, !tbaa !42
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %2) #35
          to label %3 unwind label %4

3:                                                ; preds = %1
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #37
  ret void

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          catch ptr null
  %6 = extractvalue { ptr, i32 } %5, 0
  call void @__clang_call_terminate(ptr %6) #38
  unreachable
}

; Function Attrs: nofree nounwind
declare i32 @__cxa_atexit(ptr, ptr, ptr) local_unnamed_addr #5

; Function Attrs: minsize mustprogress optsize uwtable
define weak void @__skroot_module_web_ui_main_ceb6f0bbebe5c8978eb2168799aa0ca4(ptr noundef %0, ptr noundef %1, ptr noundef %2, ptr noundef %3, i32 noundef %4, ptr noundef nonnull align 8 dereferenceable(8) %5) #6 {
  %7 = tail call i64 @__strlen_chk(ptr noundef nonnull @skroot_web_ui_enable_flag_e0c73f6473a0e653be67875a3cf23a53, i64 noundef 1024) #35
  %8 = load ptr, ptr @skroot_web_ui_handler_6f9d89cc84c2ea1bf7364fc1afda99b5, align 8, !tbaa !42
  %9 = tail call i64 @unsafe_module_start_web_ui_server(ptr noundef %0, ptr noundef %1, ptr noundef %2, ptr noundef %3, ptr noundef %8, i32 noundef %4) #35
  store i64 %9, ptr %5, align 8, !tbaa !51
  ret void
}

; Function Attrs: minsize optsize
declare i64 @unsafe_module_start_web_ui_server(ptr noundef, ptr noundef, ptr noundef, ptr noundef, ptr noundef, i32 noundef) local_unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define weak void @__skroot_module_last_web_ui_server_port_db2a2bebe2b190482ad0ac73996c7b86(ptr noundef nonnull align 4 dereferenceable(4) %0, ptr noundef nonnull align 8 dereferenceable(8) %1) #6 {
  %3 = tail call i64 @__strlen_chk(ptr noundef nonnull @skroot_web_ui_enable_flag_e0c73f6473a0e653be67875a3cf23a53, i64 noundef 1024) #35
  %4 = tail call i64 @unsafe_module_get_last_web_ui_server_port(ptr noundef nonnull align 4 dereferenceable(4) %0) #35
  store i64 %4, ptr %1, align 8, !tbaa !51
  ret void
}

; Function Attrs: minsize optsize
declare i64 @unsafe_module_get_last_web_ui_server_port(ptr noundef nonnull align 4 dereferenceable(4)) local_unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define weak void @__skroot_module_before_server_exit_action_81a98535f12c96a0b3e457727705e44c(ptr noundef nonnull align 4 dereferenceable(4) %0, ptr noundef nonnull align 8 dereferenceable(8) %1) #6 {
  %3 = tail call i64 @__strlen_chk(ptr noundef nonnull @skroot_web_ui_enable_flag_e0c73f6473a0e653be67875a3cf23a53, i64 noundef 1024) #35
  %4 = tail call i64 @unsafe_module_get_before_server_exit_action(ptr noundef nonnull align 4 dereferenceable(4) %0) #35
  store i64 %4, ptr %1, align 8, !tbaa !51
  ret void
}

; Function Attrs: minsize optsize
declare i64 @unsafe_module_get_before_server_exit_action(ptr noundef nonnull align 4 dereferenceable(4)) local_unnamed_addr #7

; Function Attrs: minsize optsize uwtable
define hidden noundef i64 @_ZN10skroot_env26install_skroot_environmentEPKcNS_11InstallModeERKNS_13InstallConfigE(ptr noundef %sdk0, i32 noundef %sdk1, ptr nocapture noundef nonnull readonly align 4 dereferenceable(4) %sdk2) local_unnamed_addr #1 personality ptr @__gxx_personality_v0 {
sdk3:
  %launch.result.pipe = alloca [2 x i32], align 4
  %sdk4 = alloca i32, align 4
  %sdk5 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %sdk6 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %sdk7 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk8 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %sdk9 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk10 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk11 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %sdk12 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %sdk13 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk14 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %sdk15 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk16 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk17 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk18 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk19 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk20 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk21 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk22 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk23 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk24 = alloca %"class.std::__ndk1::basic_string", align 8
  %sdk25 = alloca %"class.skroot_env::fork_pipe_info", align 4
  %sdk26 = alloca i64, align 8
  %sdk27 = alloca i32, align 4
  %sdk28 = icmp eq ptr %sdk0, null
  br i1 %sdk28, label %sdk440, label %sdk29

sdk29:
  %sdk30 = tail call i64 @strnlen(ptr noundef nonnull dereferenceable(1) %sdk0, i64 noundef 1024) #39
  %sdk31 = add i64 %sdk30, -1024
  %sdk32 = icmp ult i64 %sdk31, -1008
  br i1 %sdk32, label %sdk440, label %sdk33

sdk33:
  tail call void @__set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(ptr noundef nonnull %sdk0) #35
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk25) #37
  call void @_ZN10skroot_env14fork_base_infoC2Ev(ptr noundef nonnull align 4 dereferenceable(24) %sdk25) #35
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %sdk26) #37
  store i64 0, ptr %sdk26, align 8, !tbaa !51
  %sdk34 = invoke noundef i1 @_ZN10skroot_env23fork_pipe_child_processERNS_14fork_pipe_infoE(ptr noundef nonnull align 4 dereferenceable(24) %sdk25) #35
          to label %sdk35 unwind label %sdk424

sdk35:
  br i1 %sdk34, label %sdk36, label %sdk426

sdk36:
  %sdk37 = invoke noundef i64 @_ZN15default_modules11module_root16set_current_rootEb(i1 noundef true) #35
          to label %sdk38 unwind label %sdk424

sdk38:
  %sdk39 = icmp eq i64 %sdk37, 0
  br i1 %sdk39, label %sdk40, label %sdk418

sdk40:
  %sdk41 = invoke noundef i64 @_ZN10skroot_env27unsafe_clean_older_hide_dirEv() #35
          to label %sdk42 unwind label %sdk424

sdk42:
  %sdk43 = icmp eq i64 %sdk41, 0
  br i1 %sdk43, label %sdk44, label %sdk418

sdk44:
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk22) #37
  invoke fastcc void @_ZN18boot_session_utilsL17read_boot_sessionEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %sdk22) #35
          to label %sdk45 unwind label %sdk424

sdk45:
  %sdk46 = load i8, ptr %sdk22, align 8
  %sdk47 = and i8 %sdk46, 1
  %sdk48 = icmp eq i8 %sdk47, 0
  %sdk49 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %sdk22, i64 0, i32 1
  %sdk50 = load i64, ptr %sdk49, align 8
  %sdk51 = lshr i8 %sdk46, 1
  %sdk52 = zext i8 %sdk51 to i64
  %sdk53 = select i1 %sdk48, i64 %sdk52, i64 %sdk50
  %sdk54 = icmp eq i64 %sdk53, 0
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk22) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk22) #37
  br i1 %sdk54, label %sdk418, label %sdk55

sdk55:
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk23) #37
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %sdk23, i8 0, i64 24, i1 false)
  %sdk56 = invoke fastcc noundef i64 @_ZN10skroot_envL47unsafe_get_installed_skroot_environment_versionERNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEE(ptr noundef nonnull align 8 dereferenceable(24) %sdk23) #35
          to label %sdk57 unwind label %sdk67

sdk57:
  %sdk58 = icmp ne i32 %sdk1, 1
  %sdk59 = call noundef i1 @_ZNSt6__ndk1eqB7v170000IcNS_11char_traitsIcEENS_9allocatorIcEEEEbRKNS_12basic_stringIT_T0_T1_EEPKS6_(ptr noundef nonnull align 8 dereferenceable(24) %sdk23, ptr noundef nonnull @.str.17) #34
  %sdk60 = icmp ne i32 %sdk1, 0
  %sdk61 = or i1 %sdk58, %sdk59
  %sdk62 = and i1 %sdk60, %sdk61
  br i1 %sdk62, label %sdk170, label %sdk63

sdk63:
  %sdk64 = invoke noundef i64 @_ZN10skroot_env22unsafe_create_hide_dirEv() #35
          to label %sdk65 unwind label %sdk69

sdk65:
  %sdk66 = icmp eq i64 %sdk64, 0
  br i1 %sdk66, label %sdk71, label %sdk414

sdk67:
  %sdk68 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk69:
  %sdk70 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk71:
  %sdk72 = invoke noundef i64 @_ZN10skroot_env17unsafe_install_suEv() #35
          to label %sdk73 unwind label %sdk75

sdk73:
  %sdk74 = icmp eq i64 %sdk72, 0
  br i1 %sdk74, label %sdk77, label %sdk414

sdk75:
  %sdk76 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk77:
  %sdk78 = invoke noundef i64 @_ZN10skroot_env27unsafe_install_webui_loaderEv() #35
          to label %sdk79 unwind label %sdk81

sdk79:
  %sdk80 = icmp eq i64 %sdk78, 0
  br i1 %sdk80, label %sdk83, label %sdk414

sdk81:
  %sdk82 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk83:
  %sdk84 = invoke noundef i64 @_ZN10skroot_env32unsafe_install_autorun_bootstrapEv() #35
          to label %sdk85 unwind label %sdk87

sdk85:
  %sdk86 = icmp eq i64 %sdk84, 0
  br i1 %sdk86, label %sdk89, label %sdk414

sdk87:
  %sdk88 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk89:
  %sdk90 = invoke noundef i64 @_ZN10skroot_env24unsafe_install_resetpropEv() #35
          to label %sdk91 unwind label %sdk93

sdk91:
  %sdk92 = icmp eq i64 %sdk90, 0
  br i1 %sdk92, label %sdk95, label %sdk414

sdk93:
  %sdk94 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk95:
  %sdk96 = invoke noundef i64 @_ZN15default_modules11module_root16set_current_rootEb(i1 noundef true) #35
          to label %sdk97 unwind label %sdk130

sdk97:
  %sdk98 = icmp eq i64 %sdk96, 0
  br i1 %sdk98, label %sdk99, label %sdk414

sdk99:
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk21) #37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk20) #37, !noalias !53
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk19) #37, !noalias !56
  invoke fastcc void @_ZN10skroot_envL26get_autorun_bootstrap_pathEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %sdk19) #35
          to label %sdk100 unwind label %sdk130

sdk100:
  invoke void @_ZNSt6__ndk1plB7v170000IcNS_11char_traitsIcEENS_9allocatorIcEEEENS_12basic_stringIT_T0_T1_EEOS9_PKS6_(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %sdk20, ptr noundef nonnull align 8 dereferenceable(24) %sdk19, ptr noundef nonnull @.str.38) #35
          to label %sdk103 unwind label %sdk101, !noalias !53

sdk101:
  %sdk102 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk19) #34, !noalias !53
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk19) #37, !noalias !56
  br label %sdk416

sdk103:
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk19) #34, !noalias !53
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk19) #37, !noalias !56
  invoke void @_ZNSt6__ndk1plB7v170000IcNS_11char_traitsIcEENS_9allocatorIcEEEENS_12basic_stringIT_T0_T1_EEOS9_PKS6_(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %sdk21, ptr noundef nonnull align 8 dereferenceable(24) %sdk20, ptr noundef nonnull @.str.37) #35
          to label %sdk106 unwind label %sdk104

sdk104:
  %sdk105 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk20) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk20) #37, !noalias !53
  br label %sdk416

sdk106:
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk20) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk20) #37, !noalias !53
  %sdk107 = load i8, ptr %sdk21, align 8
  %sdk108 = and i8 %sdk107, 1
  %sdk109 = icmp eq i8 %sdk108, 0
  %sdk110 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %sdk21, i64 0, i32 2
  %sdk111 = load ptr, ptr %sdk110, align 8
  %sdk112 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %sdk21, i64 0, i32 2
  %sdk113 = select i1 %sdk109, ptr %sdk112, ptr %sdk111
  %sdk114 = invoke fastcc noundef i1 @_ZN10file_utilsL17create_empty_fileEPKc(ptr noundef %sdk113) #35
          to label %sdk115 unwind label %sdk116

sdk115:
  br i1 %sdk114, label %sdk118, label %sdk127

sdk116:
  %sdk117 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk21) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk21) #37
  br label %sdk416

sdk118:
  %sdk119 = load i8, ptr %sdk21, align 8
  %sdk120 = and i8 %sdk119, 1
  %sdk121 = icmp eq i8 %sdk120, 0
  %sdk122 = load ptr, ptr %sdk110, align 8
  %sdk123 = select i1 %sdk121, ptr %sdk112, ptr %sdk122
  %sdk124 = invoke fastcc noundef i1 @_ZN10file_utilsL28set_file_selinux_access_modeEPKcNS_15SelinuxFileFlagE(ptr noundef %sdk123) #35
          to label %sdk125 unwind label %sdk116

sdk125:
  br i1 %sdk124, label %sdk126, label %sdk127

sdk126:
  invoke void @sync() #35
          to label %sdk127 unwind label %sdk116

sdk127:
  %sdk128 = phi i1 [ false, %sdk115 ], [ false, %sdk125 ], [ true, %sdk126 ]
  %sdk129 = phi i64 [ -19935, %sdk115 ], [ -19915, %sdk125 ], [ 0, %sdk126 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk21) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk21) #37
  br i1 %sdk128, label %sdk132, label %sdk414

sdk130:
  %sdk131 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk132:
  %sdk133 = invoke noundef i64 @_ZN15default_modules11module_root16set_current_rootEb(i1 noundef true) #35
          to label %sdk134 unwind label %sdk164

sdk134:
  %sdk135 = icmp eq i64 %sdk133, 0
  br i1 %sdk135, label %sdk136, label %sdk414

sdk136:
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk18) #37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk17) #37, !noalias !59
  invoke fastcc void @_ZN10skroot_envL26get_autorun_bootstrap_pathEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %sdk17) #35
          to label %sdk137 unwind label %sdk164

sdk137:
  invoke void @_ZNSt6__ndk1plB7v170000IcNS_11char_traitsIcEENS_9allocatorIcEEEENS_12basic_stringIT_T0_T1_EEOS9_PKS6_(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %sdk18, ptr noundef nonnull align 8 dereferenceable(24) %sdk17, ptr noundef nonnull @.str.42) #35
          to label %sdk140 unwind label %sdk138

sdk138:
  %sdk139 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk17) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk17) #37, !noalias !59
  br label %sdk416

sdk140:
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk17) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk17) #37, !noalias !59
  %sdk141 = load i8, ptr %sdk18, align 8
  %sdk142 = and i8 %sdk141, 1
  %sdk143 = icmp eq i8 %sdk142, 0
  %sdk144 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %sdk18, i64 0, i32 2
  %sdk145 = load ptr, ptr %sdk144, align 8
  %sdk146 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %sdk18, i64 0, i32 2
  %sdk147 = select i1 %sdk143, ptr %sdk146, ptr %sdk145
  %sdk148 = invoke fastcc noundef i1 @_ZN10file_utilsL17create_empty_fileEPKc(ptr noundef %sdk147) #35
          to label %sdk149 unwind label %sdk150

sdk149:
  br i1 %sdk148, label %sdk152, label %sdk161

sdk150:
  %sdk151 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk18) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk18) #37
  br label %sdk416

sdk152:
  %sdk153 = load i8, ptr %sdk18, align 8
  %sdk154 = and i8 %sdk153, 1
  %sdk155 = icmp eq i8 %sdk154, 0
  %sdk156 = load ptr, ptr %sdk144, align 8
  %sdk157 = select i1 %sdk155, ptr %sdk146, ptr %sdk156
  %sdk158 = invoke fastcc noundef i1 @_ZN10file_utilsL28set_file_selinux_access_modeEPKcNS_15SelinuxFileFlagE(ptr noundef %sdk157) #35
          to label %sdk159 unwind label %sdk150

sdk159:
  br i1 %sdk158, label %sdk160, label %sdk161

sdk160:
  invoke void @sync() #35
          to label %sdk161 unwind label %sdk150

sdk161:
  %sdk162 = phi i1 [ false, %sdk149 ], [ false, %sdk159 ], [ true, %sdk160 ]
  %sdk163 = phi i64 [ -19935, %sdk149 ], [ -19915, %sdk159 ], [ 0, %sdk160 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk18) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk18) #37
  br i1 %sdk162, label %sdk166, label %sdk414

sdk164:
  %sdk165 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk166:
  %sdk167 = invoke noundef i1 @_ZN15default_modules32module_bypass_oplus_secure_guard18first_install_initEv() #35
          to label %sdk170 unwind label %sdk168

sdk168:
  %sdk169 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk170:
  %sdk171 = invoke noundef i64 @_ZN15default_modules11module_root16set_current_rootEb(i1 noundef true) #35
          to label %sdk172 unwind label %sdk218

sdk172:
  %sdk173 = icmp eq i64 %sdk171, 0
  br i1 %sdk173, label %sdk174, label %sdk414

sdk174:
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk15) #37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk11) #37, !noalias !62
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk12) #37, !noalias !62
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk13) #37, !noalias !62
  invoke fastcc void @_ZN10skroot_envL24get_kernel_file_dir_pathEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %sdk13) #35
          to label %sdk175 unwind label %sdk218

sdk175:
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %sdk12, ptr noundef nonnull align 8 dereferenceable(24) %sdk13, i64 24, i1 false), !noalias !62
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %sdk13, i8 0, i64 24, i1 false), !noalias !62
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk14) #37, !noalias !62
  invoke void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA13_cvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %sdk14, ptr noundef nonnull align 1 dereferenceable(13) @.str.45, i8 noundef 0) #35
          to label %sdk176 unwind label %sdk178, !noalias !62

sdk176:
  invoke void @_ZNSt6__ndk14__fs10filesystemdvB7v170000ERKNS1_4pathES4_(ptr nonnull sret(%"class.std::__ndk1::__fs::filesystem::path") align 8 %sdk11, ptr noundef nonnull align 8 dereferenceable(24) %sdk12, ptr noundef nonnull align 8 dereferenceable(24) %sdk14) #35
          to label %sdk177 unwind label %sdk180, !noalias !62

sdk177:
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %sdk15, ptr noundef nonnull align 8 dereferenceable(24) %sdk11) #35
          to label %sdk188 unwind label %sdk182

sdk178:
  %sdk179 = landingpad { ptr, i32 }
          cleanup
  br label %sdk186

sdk180:
  %sdk181 = landingpad { ptr, i32 }
          cleanup
  br label %sdk184

sdk182:
  %sdk183 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk11) #34
  br label %sdk184

sdk184:
  %sdk185 = phi { ptr, i32 } [ %sdk183, %sdk182 ], [ %sdk181, %sdk180 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk14) #34
  br label %sdk186

sdk186:
  %sdk187 = phi { ptr, i32 } [ %sdk185, %sdk184 ], [ %sdk179, %sdk178 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk14) #37, !noalias !62
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk12) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk13) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk13) #37, !noalias !62
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk12) #37, !noalias !62
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk11) #37, !noalias !62
  br label %sdk416

sdk188:
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk11) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk14) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk14) #37, !noalias !62
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk12) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk13) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk13) #37, !noalias !62
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk12) #37, !noalias !62
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk11) #37, !noalias !62
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk16) #37
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %sdk16, i8 0, i64 24, i1 false)
  switch i32 %sdk1, label %sdk195 [
    i32 0, label %sdk189
    i32 1, label %sdk192
  ]

sdk189:
  br label %sdk192

sdk190:
  %sdk191 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk16) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk16) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk15) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk15) #37
  br label %sdk416

sdk192:
  %sdk193 = phi ptr [ @.str.43, %sdk189 ], [ @.str.44, %sdk188 ]
  %sdk194 = invoke noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignEPKc(ptr noundef nonnull align 8 dereferenceable(24) %sdk16, ptr noundef nonnull %sdk193) #35
          to label %sdk195 unwind label %sdk190

sdk195:
  %sdk196 = load i8, ptr %sdk15, align 8
  %sdk197 = and i8 %sdk196, 1
  %sdk198 = icmp eq i8 %sdk197, 0
  %sdk199 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %sdk15, i64 0, i32 2
  %sdk200 = load ptr, ptr %sdk199, align 8
  %sdk201 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %sdk15, i64 0, i32 2
  %sdk202 = select i1 %sdk198, ptr %sdk201, ptr %sdk200
  %sdk203 = call [2 x i64] @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEcvNS_17basic_string_viewIcS2_EEB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk16) #34
  %sdk204 = invoke fastcc noundef i1 @_ZN10file_utilsL15write_text_fileEPKcNSt6__ndk117basic_string_viewIcNS2_11char_traitsIcEEEE(ptr noundef %sdk202, [2 x i64] %sdk203) #35
          to label %sdk205 unwind label %sdk190

sdk205:
  br i1 %sdk204, label %sdk206, label %sdk215

sdk206:
  %sdk207 = load i8, ptr %sdk15, align 8
  %sdk208 = and i8 %sdk207, 1
  %sdk209 = icmp eq i8 %sdk208, 0
  %sdk210 = load ptr, ptr %sdk199, align 8
  %sdk211 = select i1 %sdk209, ptr %sdk201, ptr %sdk210
  %sdk212 = invoke fastcc noundef i1 @_ZN10file_utilsL28set_file_selinux_access_modeEPKcNS_15SelinuxFileFlagE(ptr noundef %sdk211) #35
          to label %sdk213 unwind label %sdk190

sdk213:
  %sdk214 = select i1 %sdk212, i64 0, i64 -19915
  br label %sdk215

sdk215:
  %sdk216 = phi i64 [ -19933, %sdk205 ], [ %sdk214, %sdk213 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk16) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk16) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk15) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk15) #37
  %sdk217 = icmp eq i64 %sdk216, 0
  br i1 %sdk217, label %sdk220, label %sdk414

sdk218:
  %sdk219 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk220:
  %sdk221 = load i32, ptr %sdk2, align 4, !tbaa !65
  %sdk222 = invoke noundef i64 @_ZN15default_modules11module_root16set_current_rootEb(i1 noundef true) #35
          to label %sdk223 unwind label %sdk270

sdk223:
  %sdk224 = icmp eq i64 %sdk222, 0
  br i1 %sdk224, label %sdk225, label %sdk414

sdk225:
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk9) #37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk5) #37, !noalias !68
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk6) #37, !noalias !68
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk7) #37, !noalias !68
  invoke fastcc void @_ZN10skroot_envL24get_kernel_file_dir_pathEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %sdk7) #35
          to label %sdk226 unwind label %sdk270

sdk226:
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %sdk6, ptr noundef nonnull align 8 dereferenceable(24) %sdk7, i64 24, i1 false), !noalias !68
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %sdk7, i8 0, i64 24, i1 false), !noalias !68
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk8) #37, !noalias !68
  invoke void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA23_cvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %sdk8, ptr noundef nonnull align 1 dereferenceable(23) @.str.49, i8 noundef 0) #35
          to label %sdk227 unwind label %sdk229, !noalias !68

sdk227:
  invoke void @_ZNSt6__ndk14__fs10filesystemdvB7v170000ERKNS1_4pathES4_(ptr nonnull sret(%"class.std::__ndk1::__fs::filesystem::path") align 8 %sdk5, ptr noundef nonnull align 8 dereferenceable(24) %sdk6, ptr noundef nonnull align 8 dereferenceable(24) %sdk8) #35
          to label %sdk228 unwind label %sdk231, !noalias !68

sdk228:
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %sdk9, ptr noundef nonnull align 8 dereferenceable(24) %sdk5) #35
          to label %sdk239 unwind label %sdk233

sdk229:
  %sdk230 = landingpad { ptr, i32 }
          cleanup
  br label %sdk237

sdk231:
  %sdk232 = landingpad { ptr, i32 }
          cleanup
  br label %sdk235

sdk233:
  %sdk234 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk5) #34
  br label %sdk235

sdk235:
  %sdk236 = phi { ptr, i32 } [ %sdk234, %sdk233 ], [ %sdk232, %sdk231 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk8) #34
  br label %sdk237

sdk237:
  %sdk238 = phi { ptr, i32 } [ %sdk236, %sdk235 ], [ %sdk230, %sdk229 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk8) #37, !noalias !68
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk6) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk7) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk7) #37, !noalias !68
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk6) #37, !noalias !68
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk5) #37, !noalias !68
  br label %sdk416

sdk239:
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk5) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk8) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk8) #37, !noalias !68
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk6) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk7) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk7) #37, !noalias !68
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk6) #37, !noalias !68
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk5) #37, !noalias !68
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk10) #37
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %sdk10, i8 0, i64 24, i1 false)
  %sdk240 = icmp eq i32 %sdk221, 2
  %sdk241 = select i1 %sdk240, ptr @.str.47, ptr @.str.48
  %sdk242 = icmp eq i32 %sdk221, 1
  %sdk243 = select i1 %sdk242, ptr @.str.46, ptr %sdk241
  %sdk244 = invoke noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignEPKc(ptr noundef nonnull align 8 dereferenceable(24) %sdk10, ptr noundef nonnull %sdk243) #35
          to label %sdk247 unwind label %sdk245

sdk245:
  %sdk246 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk10) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk10) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk9) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk9) #37
  br label %sdk416

sdk247:
  %sdk248 = load i8, ptr %sdk9, align 8
  %sdk249 = and i8 %sdk248, 1
  %sdk250 = icmp eq i8 %sdk249, 0
  %sdk251 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %sdk9, i64 0, i32 2
  %sdk252 = load ptr, ptr %sdk251, align 8
  %sdk253 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %sdk9, i64 0, i32 2
  %sdk254 = select i1 %sdk250, ptr %sdk253, ptr %sdk252
  %sdk255 = call [2 x i64] @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEcvNS_17basic_string_viewIcS2_EEB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk10) #34
  %sdk256 = invoke fastcc noundef i1 @_ZN10file_utilsL15write_text_fileEPKcNSt6__ndk117basic_string_viewIcNS2_11char_traitsIcEEEE(ptr noundef %sdk254, [2 x i64] %sdk255) #35
          to label %sdk257 unwind label %sdk245

sdk257:
  br i1 %sdk256, label %sdk258, label %sdk267

sdk258:
  %sdk259 = load i8, ptr %sdk9, align 8
  %sdk260 = and i8 %sdk259, 1
  %sdk261 = icmp eq i8 %sdk260, 0
  %sdk262 = load ptr, ptr %sdk251, align 8
  %sdk263 = select i1 %sdk261, ptr %sdk253, ptr %sdk262
  %sdk264 = invoke fastcc noundef i1 @_ZN10file_utilsL28set_file_selinux_access_modeEPKcNS_15SelinuxFileFlagE(ptr noundef %sdk263) #35
          to label %sdk265 unwind label %sdk245

sdk265:
  %sdk266 = select i1 %sdk264, i64 0, i64 -19915
  br label %sdk267

sdk267:
  %sdk268 = phi i64 [ -19933, %sdk257 ], [ %sdk266, %sdk265 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk10) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk10) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk9) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk9) #37
  %sdk269 = icmp eq i64 %sdk268, 0
  br i1 %sdk269, label %sdk272, label %sdk414

sdk270:
  %sdk271 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk272:
  %sdk273 = invoke noundef i64 @_ZN10skroot_env40unsafe_reset_autorun_bootstrap_safe_flagEv() #35
          to label %sdk274 unwind label %sdk276

sdk274:
  %sdk275 = icmp eq i64 %sdk273, 0
  br i1 %sdk275, label %sdk278, label %sdk414

sdk276:
  %sdk277 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk278:
  %sdk279 = invoke noundef i64 @_ZN10skroot_env58unsafe_reset_autorun_bootstrap_duplicate_boot_session_flagEv() #35
          to label %sdk280 unwind label %sdk282

sdk280:
  %sdk281 = icmp eq i64 %sdk279, 0
  br i1 %sdk281, label %sdk284, label %sdk414

sdk282:
  %sdk283 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk284:
  invoke void @sync() #35
          to label %sdk285 unwind label %sdk168

sdk285:
  %launch.pipe.error = call i64 @maplesu_installer_result_open(i32 %sdk1, ptr %launch.result.pipe)
  %launch.pipe.ok = icmp eq i64 %launch.pipe.error, 0
  br i1 %launch.pipe.ok, label %launch.pipe.opened, label %sdk414

launch.pipe.opened:
  %sdk286 = invoke i32 @fork() #35
          to label %sdk287 unwind label %sdk293

sdk287:
  %detach.failed = icmp slt i32 %sdk286, 0
  br i1 %detach.failed, label %launch.detach.failed, label %launch.detach.checked

launch.detach.failed:
  %detach.error.recorded = call i64 @maplesu_installer_launch_error(i32 1, ptr null)
  %detach.error = call i64 @maplesu_installer_result_parent(i32 %sdk286, i32 %sdk1, ptr %launch.result.pipe)
  br label %sdk414

launch.detach.checked:
  %sdk288 = icmp eq i32 %sdk286, 0
  br i1 %sdk288, label %sdk289, label %launch.parent.result

launch.parent.result:
  %launch.parent.error = call i64 @maplesu_installer_result_parent(i32 %sdk286, i32 %sdk1, ptr %launch.result.pipe)
  br label %sdk414

sdk289:
  call void @maplesu_installer_result_worker(ptr %launch.result.pipe)
  br i1 %sdk60, label %sdk295, label %sdk290

sdk290:
  %sdk291 = invoke i32 @sleep(i32 noundef 3) #35
          to label %sdk292 unwind label %sdk293

sdk292:
  call fastcc void @_ZL12linux_rebootv() #35
  br label %sdk412

sdk293:
  %sdk294 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk295:
  br i1 %sdk58, label %sdk412, label %sdk296

sdk296:
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk24) #37
  invoke fastcc void @_ZN10skroot_envL26get_autorun_bootstrap_pathEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %sdk24) #35
          to label %sdk297 unwind label %sdk398

sdk297:
  %sdk298 = load i8, ptr %sdk24, align 8
  %sdk299 = and i8 %sdk298, 1
  %sdk300 = icmp eq i8 %sdk299, 0
  %sdk301 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %sdk24, i64 0, i32 2
  %sdk302 = load ptr, ptr %sdk301, align 8
  %sdk303 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %sdk24, i64 0, i32 2
  %sdk304 = select i1 %sdk300, ptr %sdk303, ptr %sdk302
  %sdk305 = invoke i32 @fork() #35
          to label %sdk306 unwind label %sdk400

sdk306:
  %sdk307 = icmp slt i32 %sdk305, 0
  br i1 %sdk307, label %launch.fork.failed, label %sdk308

launch.fork.failed:
  %exec.fork.error = call i64 @maplesu_installer_launch_error(i32 2, ptr %sdk304)
  br label %sdk392

sdk308:
  %sdk309 = icmp eq i32 %sdk305, 0
  br i1 %sdk309, label %sdk310, label %sdk389

sdk310:
  %sdk311 = icmp eq ptr %sdk304, null
  br i1 %sdk311, label %sdk387, label %sdk312

sdk312:
  %sdk313 = load i8, ptr %sdk304, align 1, !tbaa !38
  %sdk314 = icmp eq i8 %sdk313, 0
  br i1 %sdk314, label %sdk387, label %sdk315

sdk315:
  %sdk316 = call ptr @strdup(ptr noundef nonnull %sdk304) #35
  %sdk317 = icmp eq ptr %sdk316, null
  br i1 %sdk317, label %sdk387, label %sdk318

sdk318:
  %sdk319 = call noalias dereferenceable_or_null(72) ptr @calloc(i64 noundef 9, i64 noundef 8) #40
  %sdk320 = icmp eq ptr %sdk319, null
  br i1 %sdk320, label %sdk321, label %sdk322

sdk321:
  call void @free(ptr noundef nonnull %sdk316) #35
  br label %sdk387

sdk322:
  %sdk323 = phi ptr [ %sdk351, %sdk376 ], [ %sdk319, %sdk318 ]
  %sdk324 = phi i64 [ %sdk353, %sdk376 ], [ 0, %sdk318 ]
  %sdk325 = phi ptr [ %sdk377, %sdk376 ], [ %sdk316, %sdk318 ]
  %sdk326 = phi i64 [ %sdk352, %sdk376 ], [ 8, %sdk318 ]
  %sdk327 = load i8, ptr %sdk325, align 1, !tbaa !38
  %sdk328 = icmp eq i8 %sdk327, 0
  br i1 %sdk328, label %sdk378, label %sdk329

sdk329:
  %sdk330 = phi i8 [ %sdk334, %sdk332 ], [ %sdk327, %sdk322 ]
  %sdk331 = phi ptr [ %sdk333, %sdk332 ], [ %sdk325, %sdk322 ]
  switch i8 %sdk330, label %sdk335 [
    i8 32, label %sdk332
    i8 9, label %sdk332
    i8 10, label %sdk332
    i8 13, label %sdk332
    i8 0, label %sdk378
  ]

sdk332:
  %sdk333 = getelementptr inbounds i8, ptr %sdk331, i64 1
  %sdk334 = load i8, ptr %sdk333, align 1, !tbaa !38
  br label %sdk329, !llvm.loop !71

sdk335:
  %sdk336 = icmp ult i64 %sdk324, %sdk326
  br i1 %sdk336, label %sdk350, label %sdk337

sdk337:
  %sdk338 = shl i64 %sdk326, 1
  %sdk339 = or i64 %sdk338, 1
  %sdk340 = shl i64 %sdk339, 3
  %sdk341 = call ptr @realloc(ptr noundef %sdk323, i64 noundef %sdk340) #41
  %sdk342 = icmp eq ptr %sdk341, null
  br i1 %sdk342, label %sdk343, label %sdk344

sdk343:
  call void @free(ptr noundef %sdk323) #35
  call void @free(ptr noundef %sdk316) #35
  br label %sdk387

sdk344:
  %sdk345 = getelementptr inbounds ptr, ptr %sdk341, i64 %sdk326
  %sdk346 = call i64 @llvm.objectsize.i64.p0(ptr %sdk345, i1 false, i1 true, i1 false)
  %sdk347 = sub i64 %sdk339, %sdk326
  %sdk348 = shl i64 %sdk347, 3
  %sdk349 = call ptr @__memset_chk(ptr noundef nonnull %sdk345, i32 noundef 0, i64 noundef %sdk348, i64 noundef %sdk346) #34
  br label %sdk350

sdk350:
  %sdk351 = phi ptr [ %sdk341, %sdk344 ], [ %sdk323, %sdk335 ]
  %sdk352 = phi i64 [ %sdk338, %sdk344 ], [ %sdk326, %sdk335 ]
  %sdk353 = add i64 %sdk324, 1
  %sdk354 = getelementptr inbounds ptr, ptr %sdk351, i64 %sdk324
  store ptr %sdk331, ptr %sdk354, align 8, !tbaa !42
  %sdk355 = load i8, ptr %sdk331, align 1, !tbaa !38
  switch i8 %sdk355, label %sdk368 [
    i8 34, label %sdk356
    i8 39, label %sdk356
  ]

sdk356:
  %sdk357 = getelementptr inbounds i8, ptr %sdk331, i64 1
  store ptr %sdk357, ptr %sdk354, align 8, !tbaa !42
  br label %sdk358

sdk358:
  %sdk359 = phi ptr [ %sdk357, %sdk356 ], [ %sdk364, %sdk358 ]
  %sdk360 = load i8, ptr %sdk359, align 1, !tbaa !38
  %sdk361 = icmp eq i8 %sdk360, 0
  %sdk362 = icmp eq i8 %sdk360, %sdk355
  %sdk363 = or i1 %sdk361, %sdk362
  %sdk364 = getelementptr i8, ptr %sdk359, i64 1
  br i1 %sdk363, label %sdk365, label %sdk358, !llvm.loop !73

sdk365:
  %sdk366 = zext i1 %sdk362 to i64
  %sdk367 = getelementptr i8, ptr %sdk359, i64 %sdk366
  store i8 0, ptr %sdk359, align 1, !tbaa !38
  br label %sdk376

sdk368:
  %sdk369 = phi i8 [ %sdk373, %sdk371 ], [ %sdk355, %sdk350 ]
  %sdk370 = phi ptr [ %sdk372, %sdk371 ], [ %sdk331, %sdk350 ]
  switch i8 %sdk369, label %sdk371 [
    i8 0, label %sdk376
    i8 9, label %sdk374
    i8 10, label %sdk374
    i8 13, label %sdk374
    i8 32, label %sdk374
  ]

sdk371:
  %sdk372 = getelementptr inbounds i8, ptr %sdk370, i64 1
  %sdk373 = load i8, ptr %sdk372, align 1, !tbaa !38
  br label %sdk368, !llvm.loop !74

sdk374:
  store i8 0, ptr %sdk370, align 1, !tbaa !38
  %sdk375 = getelementptr inbounds i8, ptr %sdk370, i64 1
  br label %sdk376

sdk376:
  %sdk377 = phi ptr [ %sdk367, %sdk365 ], [ %sdk375, %sdk374 ], [ %sdk370, %sdk368 ]
  br label %sdk322, !llvm.loop !75

sdk378:
  %sdk379 = icmp eq i64 %sdk324, 0
  br i1 %sdk379, label %sdk380, label %sdk381

sdk380:
  call void @free(ptr noundef %sdk323) #35
  call void @free(ptr noundef %sdk316) #35
  br label %sdk387

sdk381:
  %sdk382 = getelementptr inbounds ptr, ptr %sdk323, i64 %sdk324
  store ptr null, ptr %sdk382, align 8, !tbaa !42
  %sdk383 = load ptr, ptr @environ, align 8
  %sdk384 = load ptr, ptr %sdk323, align 8, !tbaa !42
  %sdk385 = invoke i32 @maplesu_installer_exec(ptr %sdk384, ptr %sdk323, ptr %sdk383) #35
          to label %sdk386 unwind label %sdk400

sdk386:
  call void @free(ptr noundef nonnull %sdk323) #35
  call void @free(ptr noundef nonnull %sdk316) #35
  br label %sdk387

sdk387:
  invoke void @_exit(i32 noundef 127) #42
          to label %sdk388 unwind label %sdk400

sdk388:
  unreachable

sdk389:
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %sdk4) #37
  store i32 0, ptr %sdk4, align 4, !tbaa !76
  %sdk390 = invoke i32 @maplesu_installer_wait_child(i32 %sdk305, ptr %sdk4, ptr %sdk304) #35
          to label %sdk391 unwind label %sdk400

sdk391:
  %launch.wait.status = load i32, ptr %sdk4, align 4
  %launch.wait.pid.ok = icmp eq i32 %sdk390, %sdk305
  %launch.wait.status.ok = icmp eq i32 %launch.wait.status, 0
  %launch.wait.ok = and i1 %launch.wait.pid.ok, %launch.wait.status.ok
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %sdk4) #37
  br label %sdk392

sdk392:
  %launch.ok = phi i1 [ false, %launch.fork.failed ], [ %launch.wait.ok, %sdk391 ]
  %launch.raw.ok = zext i1 %launch.ok to i32
  %launch.completion = call i32 @maplesu_installer_result_complete(ptr %launch.result.pipe, ptr %sdk304, i32 %launch.raw.ok)
  %launch.completed = icmp ne i32 %launch.completion, 0
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk24) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk24) #37
  br i1 %launch.completed, label %launch.restart.checked, label %launch.stop

launch.stop:
  call void @_exit(i32 127)
  unreachable

launch.restart.checked:
  %sdk393 = load i32, ptr %sdk2, align 4, !tbaa !65
  switch i32 %sdk393, label %sdk412 [
    i32 0, label %sdk394
    i32 1, label %sdk406
  ]

sdk394:
  %sdk395 = invoke noundef i64 @_ZN10skroot_env23unsafe_restart_zygote64Ev() #35
          to label %sdk396 unwind label %sdk404

sdk396:
  %sdk397 = icmp eq i64 %sdk395, 0
  br i1 %sdk397, label %sdk412, label %sdk414

sdk398:
  %sdk399 = landingpad { ptr, i32 }
          cleanup
  br label %sdk402

sdk400:
  %sdk401 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk24) #34
  br label %sdk402

sdk402:
  %sdk403 = phi { ptr, i32 } [ %sdk401, %sdk400 ], [ %sdk399, %sdk398 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk24) #37
  br label %sdk416

sdk404:
  %sdk405 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk406:
  %sdk407 = invoke noundef i64 @_ZN10skroot_env23unsafe_restart_zygote64Ev() #35
          to label %sdk408 unwind label %sdk410

sdk408:
  %sdk409 = icmp eq i64 %sdk407, 0
  br i1 %sdk409, label %sdk412, label %sdk414

sdk410:
  %sdk411 = landingpad { ptr, i32 }
          cleanup
  br label %sdk416

sdk412:
  invoke void @_exit(i32 noundef 0) #42
          to label %sdk413 unwind label %sdk293

sdk413:
  unreachable

sdk414:
  %sdk415 = phi i64 [ %sdk279, %sdk280 ], [ %sdk273, %sdk274 ], [ %sdk268, %sdk267 ], [ %sdk216, %sdk215 ], [ %sdk163, %sdk161 ], [ %sdk129, %sdk127 ], [ %sdk90, %sdk91 ], [ %sdk84, %sdk85 ], [ %sdk78, %sdk79 ], [ %sdk72, %sdk73 ], [ %sdk64, %sdk65 ], [ %sdk395, %sdk396 ], [ %sdk407, %sdk408 ], [ %launch.parent.error, %launch.parent.result ], [ %detach.error, %launch.detach.failed ], [ %launch.pipe.error, %sdk285 ], [ %sdk96, %sdk97 ], [ %sdk133, %sdk134 ], [ %sdk171, %sdk172 ], [ %sdk222, %sdk223 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk23) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk23) #37
  br label %sdk418

sdk416:
  %sdk417 = phi { ptr, i32 } [ %sdk68, %sdk67 ], [ %sdk169, %sdk168 ], [ %sdk283, %sdk282 ], [ %sdk277, %sdk276 ], [ %sdk94, %sdk93 ], [ %sdk88, %sdk87 ], [ %sdk82, %sdk81 ], [ %sdk76, %sdk75 ], [ %sdk70, %sdk69 ], [ %sdk294, %sdk293 ], [ %sdk405, %sdk404 ], [ %sdk411, %sdk410 ], [ %sdk403, %sdk402 ], [ %sdk131, %sdk130 ], [ %sdk117, %sdk116 ], [ %sdk102, %sdk101 ], [ %sdk105, %sdk104 ], [ %sdk165, %sdk164 ], [ %sdk139, %sdk138 ], [ %sdk151, %sdk150 ], [ %sdk219, %sdk218 ], [ %sdk187, %sdk186 ], [ %sdk191, %sdk190 ], [ %sdk271, %sdk270 ], [ %sdk238, %sdk237 ], [ %sdk246, %sdk245 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %sdk23) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk23) #37
  br label %sdk436

sdk418:
  %sdk419 = phi i64 [ %sdk37, %sdk38 ], [ %sdk41, %sdk42 ], [ %sdk415, %sdk414 ], [ -19994, %sdk45 ]
  store i64 %sdk419, ptr %sdk26, align 8, !tbaa !51
  %sdk420 = invoke noundef i1 @_ZN10skroot_env24write_errcode_from_childERKNS_14fork_pipe_infoE7KModErr(ptr noundef nonnull align 4 dereferenceable(24) %sdk25, i64 noundef %sdk419) #35
          to label %sdk421 unwind label %sdk424

sdk421:
  invoke void @_ZN10skroot_env14fork_base_info9close_allEv(ptr noundef nonnull align 4 dereferenceable(24) %sdk25) #35
          to label %sdk422 unwind label %sdk424

sdk422:
  invoke void @_exit(i32 noundef 0) #42
          to label %sdk423 unwind label %sdk424

sdk423:
  unreachable

sdk424:
  %sdk425 = landingpad { ptr, i32 }
          cleanup
  br label %sdk436

sdk426:
  %sdk427 = invoke noundef i1 @_ZN10skroot_env23read_errcode_from_childERKNS_14fork_pipe_infoER7KModErr(ptr noundef nonnull align 4 dereferenceable(24) %sdk25, ptr noundef nonnull align 8 dereferenceable(8) %sdk26) #35
          to label %sdk428 unwind label %sdk424

sdk428:
  br i1 %sdk427, label %sdk430, label %sdk429

sdk429:
  store i64 -19914, ptr %sdk26, align 8, !tbaa !51
  br label %sdk430

sdk430:
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %sdk27) #37
  store i32 0, ptr %sdk27, align 4, !tbaa !76
  %sdk431 = getelementptr inbounds %"class.skroot_env::fork_base_info", ptr %sdk25, i64 0, i32 5
  %sdk432 = load i32, ptr %sdk431, align 4, !tbaa !78
  %sdk433 = invoke i32 @maplesu_wait_environment_child(i32 %sdk432, ptr %sdk27) #35
          to label %sdk438 unwind label %sdk434

sdk434:
  %sdk435 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %sdk27) #37
  br label %sdk436

sdk436:
  %sdk437 = phi { ptr, i32 } [ %sdk435, %sdk434 ], [ %sdk425, %sdk424 ], [ %sdk417, %sdk416 ]
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %sdk26) #37
  call void @_ZN10skroot_env14fork_base_infoD2Ev(ptr noundef nonnull align 4 dereferenceable(24) %sdk25) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk25) #37
  resume { ptr, i32 } %sdk437

sdk438:
  %launch.final.error = load i64, ptr %sdk26, align 8, !tbaa !51
  %launch.final.status = load i32, ptr %sdk27, align 4
  %launch.final.pid.ok = icmp eq i32 %sdk433, %sdk432
  %launch.final.status.ok = icmp eq i32 %launch.final.status, 0
  %launch.final.wait.ok = and i1 %launch.final.pid.ok, %launch.final.status.ok
  %sdk439 = select i1 %launch.final.wait.ok, i64 %launch.final.error, i64 -21002
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %sdk27) #37
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %sdk26) #37
  call void @_ZN10skroot_env14fork_base_infoD2Ev(ptr noundef nonnull align 4 dereferenceable(24) %sdk25) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk25) #37
  br label %sdk440

sdk440:
  %sdk441 = phi i64 [ %sdk439, %sdk438 ], [ -19997, %sdk3 ], [ -19997, %sdk29 ]
  ret i64 %sdk441
}

; Function Attrs: minsize mustprogress nofree nounwind optsize willreturn memory(argmem: read)
declare i64 @strnlen(ptr nocapture noundef, i64 noundef) local_unnamed_addr #8

; Function Attrs: minsize optsize
declare void @__set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(ptr noundef) local_unnamed_addr #7

; Function Attrs: minsize optsize uwtable
define hidden noundef i64 @_ZN10skroot_env28uninstall_skroot_environmentEPKc(ptr noundef %0) local_unnamed_addr #1 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::error_code", align 8
  %3 = alloca %"class.std::__ndk1::vector.32", align 8
  %4 = alloca %"class.std::__ndk1::basic_string", align 8
  %5 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %6 = alloca ptr, align 8
  %7 = alloca %"class.skroot_env::fork_pipe_info", align 4
  %8 = alloca i64, align 8
  %9 = alloca i32, align 4
  %10 = icmp eq ptr %0, null
  br i1 %10, label %114, label %11

11:                                               ; preds = %1
  %12 = tail call i64 @strnlen(ptr noundef nonnull dereferenceable(1) %0, i64 noundef 1024) #39
  %13 = add i64 %12, -1024
  %14 = icmp ult i64 %13, -1008
  br i1 %14, label %114, label %15

15:                                               ; preds = %11
  tail call void @__set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(ptr noundef nonnull %0) #35
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %7) #37
  call void @_ZN10skroot_env14fork_base_infoC2Ev(ptr noundef nonnull align 4 dereferenceable(24) %7) #35
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %8) #37
  store i64 0, ptr %8, align 8, !tbaa !51
  %16 = invoke noundef i1 @_ZN10skroot_env23fork_pipe_child_processERNS_14fork_pipe_infoE(ptr noundef nonnull align 4 dereferenceable(24) %7) #35
          to label %17 unwind label %98

17:                                               ; preds = %15
  br i1 %16, label %18, label %100

18:                                               ; preds = %17
  %19 = invoke noundef i64 @_ZN15default_modules11module_root16set_current_rootEb(i1 noundef true) #35
          to label %20 unwind label %98

20:                                               ; preds = %18
  %21 = icmp eq i64 %19, 0
  br i1 %21, label %22, label %92

22:                                               ; preds = %20
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %3) #37
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %3, i8 0, i64 24, i1 false)
  %23 = invoke noundef i64 @_ZN10skroot_env22unsafe_get_all_modulesERNSt6__ndk16vectorINS_13module_recordENS0_9allocatorIS2_EEEE(ptr noundef nonnull align 8 dereferenceable(24) %3) #35
          to label %24 unwind label %33

24:                                               ; preds = %22
  %25 = load ptr, ptr %3, align 8, !tbaa !80
  %26 = getelementptr inbounds %"class.std::__ndk1::vector.32", ptr %3, i64 0, i32 1
  %27 = load ptr, ptr %26, align 8, !tbaa !84
  br label %28

28:                                               ; preds = %38, %24
  %29 = phi ptr [ %25, %24 ], [ %39, %38 ]
  %30 = icmp eq ptr %29, %27
  br i1 %30, label %31, label %35

31:                                               ; preds = %28
  %32 = invoke noundef i64 @_ZN10skroot_env27unsafe_clean_older_hide_dirEv() #35
          to label %42 unwind label %33

33:                                               ; preds = %31, %22
  %34 = landingpad { ptr, i32 }
          cleanup
  br label %90

35:                                               ; preds = %28
  %36 = getelementptr inbounds %"struct.skroot_env::module_desc", ptr %29, i64 0, i32 4
  %37 = invoke noundef i64 @_ZN10skroot_env21safe_uninstall_moduleEPKc(ptr noundef nonnull %36) #35
          to label %38 unwind label %40

38:                                               ; preds = %35
  %39 = getelementptr inbounds %"struct.skroot_env::module_record", ptr %29, i64 1
  br label %28

40:                                               ; preds = %35
  %41 = landingpad { ptr, i32 }
          cleanup
  br label %90

42:                                               ; preds = %31
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %4) #37
  invoke fastcc void @_ZN10skroot_envL26get_autorun_bootstrap_pathEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %4) #35
          to label %43 unwind label %57

43:                                               ; preds = %42
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %5) #37
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %6) #37
  %44 = load i8, ptr %4, align 8
  %45 = and i8 %44, 1
  %46 = icmp eq i8 %45, 0
  %47 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %4, i64 0, i32 2
  %48 = load ptr, ptr %47, align 8
  %49 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %4, i64 0, i32 2
  %50 = select i1 %46, ptr %49, ptr %48
  store ptr %50, ptr %6, align 8, !tbaa !42
  invoke void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IPKcvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %5, ptr noundef nonnull align 8 dereferenceable(8) %6, i8 noundef 0) #35
          to label %51 unwind label %59

51:                                               ; preds = %43
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %2) #37
  store i32 0, ptr %2, align 8, !tbaa !85
  %52 = getelementptr inbounds %"class.std::__ndk1::error_code", ptr %2, i64 0, i32 1
  %53 = call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk115system_categoryEv() #34
  store ptr %53, ptr %52, align 8, !tbaa !87
  %54 = invoke noundef i64 @_ZNSt6__ndk14__fs10filesystem12__remove_allERKNS1_4pathEPNS_10error_codeE(ptr noundef nonnull align 8 dereferenceable(24) %5, ptr noundef nonnull %2) #35
          to label %55 unwind label %61

55:                                               ; preds = %51
  %56 = call fastcc noundef i1 @_ZN10file_utilsL11file_existsERKNSt6__ndk14__fs10filesystem4pathE(ptr noundef nonnull align 8 dereferenceable(24) %5) #35
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %2) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #34
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #37
  br i1 %56, label %84, label %65

57:                                               ; preds = %42
  %58 = landingpad { ptr, i32 }
          cleanup
  br label %88

59:                                               ; preds = %43
  %60 = landingpad { ptr, i32 }
          cleanup
  br label %63

61:                                               ; preds = %51
  %62 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #34
  br label %63

63:                                               ; preds = %61, %59
  %64 = phi { ptr, i32 } [ %62, %61 ], [ %60, %59 ]
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #37
  br label %86

65:                                               ; preds = %55
  %66 = invoke noundef i64 @_ZN10skroot_env19unsafe_del_hide_dirEv() #35
          to label %67 unwind label %69

67:                                               ; preds = %65
  %68 = icmp eq i64 %66, 0
  br i1 %68, label %71, label %84

69:                                               ; preds = %65
  %70 = landingpad { ptr, i32 }
          cleanup
  br label %86

71:                                               ; preds = %67
  invoke void @sync() #35
          to label %72 unwind label %80

72:                                               ; preds = %71
  %73 = invoke i32 @fork() #35
          to label %74 unwind label %82

74:                                               ; preds = %72
  %75 = icmp eq i32 %73, 0
  br i1 %75, label %76, label %84

76:                                               ; preds = %74
  %77 = invoke i32 @sleep(i32 noundef 3) #35
          to label %78 unwind label %82

78:                                               ; preds = %76
  call fastcc void @_ZL12linux_rebootv() #35
  invoke void @_exit(i32 noundef 0) #42
          to label %79 unwind label %82

79:                                               ; preds = %78
  unreachable

80:                                               ; preds = %71
  %81 = landingpad { ptr, i32 }
          cleanup
  br label %86

82:                                               ; preds = %78, %76, %72
  %83 = landingpad { ptr, i32 }
          cleanup
  br label %86

84:                                               ; preds = %74, %67, %55
  %85 = phi i64 [ %66, %67 ], [ -19931, %55 ], [ 0, %74 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  call void @_ZNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %3) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  br label %92

86:                                               ; preds = %82, %80, %69, %63
  %87 = phi { ptr, i32 } [ %83, %82 ], [ %81, %80 ], [ %70, %69 ], [ %64, %63 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  br label %88

88:                                               ; preds = %86, %57
  %89 = phi { ptr, i32 } [ %87, %86 ], [ %58, %57 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  br label %90

90:                                               ; preds = %88, %40, %33
  %91 = phi { ptr, i32 } [ %41, %40 ], [ %89, %88 ], [ %34, %33 ]
  call void @_ZNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %3) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  br label %110

92:                                               ; preds = %84, %20
  %93 = phi i64 [ %19, %20 ], [ %85, %84 ]
  %94 = invoke noundef i1 @_ZN10skroot_env24write_errcode_from_childERKNS_14fork_pipe_infoE7KModErr(ptr noundef nonnull align 4 dereferenceable(24) %7, i64 noundef %93) #35
          to label %95 unwind label %98

95:                                               ; preds = %92
  invoke void @_ZN10skroot_env14fork_base_info9close_allEv(ptr noundef nonnull align 4 dereferenceable(24) %7) #35
          to label %96 unwind label %98

96:                                               ; preds = %95
  invoke void @_exit(i32 noundef 0) #42
          to label %97 unwind label %98

97:                                               ; preds = %96
  unreachable

98:                                               ; preds = %100, %96, %95, %92, %18, %15
  %99 = landingpad { ptr, i32 }
          cleanup
  br label %110

100:                                              ; preds = %17
  %101 = invoke noundef i1 @_ZN10skroot_env23read_errcode_from_childERKNS_14fork_pipe_infoER7KModErr(ptr noundef nonnull align 4 dereferenceable(24) %7, ptr noundef nonnull align 8 dereferenceable(8) %8) #35
          to label %102 unwind label %98

102:                                              ; preds = %100
  br i1 %101, label %104, label %103

103:                                              ; preds = %102
  store i64 -19914, ptr %8, align 8, !tbaa !51
  br label %104

104:                                              ; preds = %103, %102
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %9) #37
  store i32 0, ptr %9, align 4, !tbaa !76
  %105 = getelementptr inbounds %"class.skroot_env::fork_base_info", ptr %7, i64 0, i32 5
  %106 = load i32, ptr %105, align 4, !tbaa !78
  %107 = invoke i32 @waitpid(i32 noundef %106, ptr noundef nonnull %9, i32 noundef 0) #35
          to label %112 unwind label %108

108:                                              ; preds = %104
  %109 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %9) #37
  br label %110

110:                                              ; preds = %108, %98, %90
  %111 = phi { ptr, i32 } [ %109, %108 ], [ %99, %98 ], [ %91, %90 ]
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %8) #37
  call void @_ZN10skroot_env14fork_base_infoD2Ev(ptr noundef nonnull align 4 dereferenceable(24) %7) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #37
  resume { ptr, i32 } %111

112:                                              ; preds = %104
  %113 = load i64, ptr %8, align 8, !tbaa !51
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %9) #37
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %8) #37
  call void @_ZN10skroot_env14fork_base_infoD2Ev(ptr noundef nonnull align 4 dereferenceable(24) %7) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #37
  br label %114

114:                                              ; preds = %112, %11, %1
  %115 = phi i64 [ %113, %112 ], [ -19997, %1 ], [ -19997, %11 ]
  ret i64 %115
}

; Function Attrs: minsize optsize uwtable
define hidden noundef i32 @_ZN10skroot_env28get_skroot_environment_stateEPKc(ptr noundef %sdk0) local_unnamed_addr #1 personality ptr @__gxx_personality_v0 {
sdk1:
  %sdk2 = alloca %"class.skroot_env::fork_pipe_info", align 4
  %sdk3 = alloca i32, align 4
  %sdk4 = alloca i32, align 4
  tail call void @__set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(ptr noundef %sdk0) #35
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %sdk2) #37
  call void @_ZN10skroot_env14fork_base_infoC2Ev(ptr noundef nonnull align 4 dereferenceable(24) %sdk2) #35
  %sdk5 = invoke noundef i1 @_ZN10skroot_env23fork_pipe_child_processERNS_14fork_pipe_infoE(ptr noundef nonnull align 4 dereferenceable(24) %sdk2) #35
          to label %sdk6 unwind label %sdk14

sdk6:
  br i1 %sdk5, label %sdk7, label %sdk16

sdk7:
  %sdk8 = invoke fastcc noundef i32 @_ZN10skroot_envL35unsafe_get_skroot_environment_stateEv() #35
          to label %sdk9 unwind label %sdk14, !range !88

sdk9:
  %sdk10 = invoke noundef i1 @_ZN10skroot_env20write_int_from_childERKNS_14fork_pipe_infoEi(ptr noundef nonnull align 4 dereferenceable(24) %sdk2, i32 noundef %sdk8) #35
          to label %sdk11 unwind label %sdk14

sdk11:
  invoke void @_ZN10skroot_env14fork_base_info9close_allEv(ptr noundef nonnull align 4 dereferenceable(24) %sdk2) #35
          to label %sdk12 unwind label %sdk14

sdk12:
  invoke void @_exit(i32 noundef 0) #42
          to label %sdk13 unwind label %sdk14

sdk13:
  unreachable

sdk14:
  %sdk15 = landingpad { ptr, i32 }
          cleanup
  br label %sdk29

sdk16:
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %sdk3) #37
  store i32 3, ptr %sdk3, align 4, !tbaa !76
  %sdk17 = invoke noundef i1 @_ZN10skroot_env19read_int_from_childERKNS_14fork_pipe_infoERi(ptr noundef nonnull align 4 dereferenceable(24) %sdk2, ptr noundef nonnull align 4 dereferenceable(4) %sdk3) #35
          to label %sdk18 unwind label %sdk23

sdk18:
  %sdk19 = load i32, ptr %sdk3, align 4, !tbaa !76
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %sdk4) #37
  store i32 0, ptr %sdk4, align 4, !tbaa !76
  %sdk20 = getelementptr inbounds %"class.skroot_env::fork_base_info", ptr %sdk2, i64 0, i32 5
  %sdk21 = load i32, ptr %sdk20, align 4, !tbaa !78
  %sdk22 = invoke i32 @maplesu_wait_environment_child(i32 %sdk21, ptr %sdk4) #35
          to label %sdk31 unwind label %sdk25

sdk23:
  %sdk24 = landingpad { ptr, i32 }
          cleanup
  br label %sdk27

sdk25:
  %sdk26 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %sdk4) #37
  br label %sdk27

sdk27:
  %sdk28 = phi { ptr, i32 } [ %sdk26, %sdk25 ], [ %sdk24, %sdk23 ]
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %sdk3) #37
  br label %sdk29

sdk29:
  %sdk30 = phi { ptr, i32 } [ %sdk15, %sdk14 ], [ %sdk28, %sdk27 ]
  call void @_ZN10skroot_env14fork_base_infoD2Ev(ptr noundef nonnull align 4 dereferenceable(24) %sdk2) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk2) #37
  resume { ptr, i32 } %sdk30

sdk31:
  %wait.ok = icmp eq i32 %sdk22, %sdk21
  %child.valid = icmp sgt i32 %sdk21, 0
  %child.status = load i32, ptr %sdk4, align 4
  %child.ok = icmp eq i32 %child.status, 0
  %reply.range = icmp ult i32 %sdk19, 4
  %reply.read = and i1 %sdk17, %wait.ok
  %reply.exit = and i1 %child.valid, %child.ok
  %reply.transport = and i1 %reply.read, %reply.exit
  %reply.ok = and i1 %reply.transport, %reply.range
  %verified.state = select i1 %reply.ok, i32 %sdk19, i32 3
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %sdk4) #37
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %sdk3) #37
  call void @_ZN10skroot_env14fork_base_infoD2Ev(ptr noundef nonnull align 4 dereferenceable(24) %sdk2) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %sdk2) #37
  ret i32 %verified.state
}

; Function Attrs: minsize optsize uwtable
define hidden noundef i64 @_ZN10skroot_env40get_installed_skroot_environment_versionEPKcRNS_16SkrootSdkVersionE(ptr noundef %0, ptr noundef nonnull align 4 dereferenceable(12) %1) local_unnamed_addr #1 personality ptr @__gxx_personality_v0 {
  %3 = alloca %"class.skroot_env::fork_pipe_info", align 4
  %4 = alloca i64, align 8
  %5 = alloca i32, align 4
  %6 = alloca %"class.std::__ndk1::basic_string", align 8
  %7 = icmp eq ptr %0, null
  br i1 %7, label %62, label %8

8:                                                ; preds = %2
  %9 = tail call i64 @strnlen(ptr noundef nonnull dereferenceable(1) %0, i64 noundef 1024) #39
  %10 = add i64 %9, -1024
  %11 = icmp ult i64 %10, -1008
  br i1 %11, label %62, label %12

12:                                               ; preds = %8
  tail call void @__set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(ptr noundef nonnull %0) #35
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %6) #37
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %6, i8 0, i64 24, i1 false)
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %3) #37
  invoke void @_ZN10skroot_env14fork_base_infoC2Ev(ptr noundef nonnull align 4 dereferenceable(24) %3) #35
          to label %13 unwind label %46

13:                                               ; preds = %12
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %4) #37
  store i64 0, ptr %4, align 8, !tbaa !51
  %14 = invoke noundef i1 @_ZN10skroot_env23fork_pipe_child_processERNS_14fork_pipe_infoE(ptr noundef nonnull align 4 dereferenceable(24) %3) #35
          to label %15 unwind label %25

15:                                               ; preds = %13
  br i1 %14, label %16, label %27

16:                                               ; preds = %15
  %17 = invoke fastcc noundef i64 @_ZN10skroot_envL47unsafe_get_installed_skroot_environment_versionERNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEE(ptr noundef nonnull align 8 dereferenceable(24) %6) #35
          to label %18 unwind label %25

18:                                               ; preds = %16
  %19 = invoke noundef i1 @_ZN10skroot_env24write_errcode_from_childERKNS_14fork_pipe_infoE7KModErr(ptr noundef nonnull align 4 dereferenceable(24) %3, i64 noundef %17) #35
          to label %20 unwind label %25

20:                                               ; preds = %18
  %21 = invoke noundef i1 @_ZN10skroot_env23write_string_from_childERKNS_14fork_pipe_infoERKNSt6__ndk112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEE(ptr noundef nonnull align 4 dereferenceable(24) %3, ptr noundef nonnull align 8 dereferenceable(24) %6) #35
          to label %22 unwind label %25

22:                                               ; preds = %20
  invoke void @_ZN10skroot_env14fork_base_info9close_allEv(ptr noundef nonnull align 4 dereferenceable(24) %3) #35
          to label %23 unwind label %25

23:                                               ; preds = %22
  invoke void @_exit(i32 noundef 0) #42
          to label %24 unwind label %25

24:                                               ; preds = %23
  unreachable

25:                                               ; preds = %31, %27, %23, %22, %20, %18, %16, %13
  %26 = landingpad { ptr, i32 }
          cleanup
  br label %41

27:                                               ; preds = %15
  %28 = invoke noundef i1 @_ZN10skroot_env23read_errcode_from_childERKNS_14fork_pipe_infoER7KModErr(ptr noundef nonnull align 4 dereferenceable(24) %3, ptr noundef nonnull align 8 dereferenceable(8) %4) #35
          to label %29 unwind label %25

29:                                               ; preds = %27
  br i1 %28, label %31, label %30

30:                                               ; preds = %29
  store i64 -19914, ptr %4, align 8, !tbaa !51
  br label %31

31:                                               ; preds = %30, %29
  %32 = invoke noundef i1 @_ZN10skroot_env22read_string_from_childERNS_14fork_pipe_infoERNSt6__ndk112basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEE(ptr noundef nonnull align 4 dereferenceable(24) %3, ptr noundef nonnull align 8 dereferenceable(24) %6) #35
          to label %33 unwind label %25

33:                                               ; preds = %31
  br i1 %32, label %35, label %34

34:                                               ; preds = %33
  store i64 -19913, ptr %4, align 8, !tbaa !51
  br label %35

35:                                               ; preds = %34, %33
  call void @llvm.lifetime.start.p0(i64 4, ptr nonnull %5) #37
  store i32 0, ptr %5, align 4, !tbaa !76
  %36 = getelementptr inbounds %"class.skroot_env::fork_base_info", ptr %3, i64 0, i32 5
  %37 = load i32, ptr %36, align 4, !tbaa !78
  %38 = invoke i32 @waitpid(i32 noundef %37, ptr noundef nonnull %5, i32 noundef 0) #35
          to label %43 unwind label %39

39:                                               ; preds = %35
  %40 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %5) #37
  br label %41

41:                                               ; preds = %39, %25
  %42 = phi { ptr, i32 } [ %26, %25 ], [ %40, %39 ]
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %4) #37
  call void @_ZN10skroot_env14fork_base_infoD2Ev(ptr noundef nonnull align 4 dereferenceable(24) %3) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  br label %48

43:                                               ; preds = %35
  %44 = load i64, ptr %4, align 8, !tbaa !51
  call void @llvm.lifetime.end.p0(i64 4, ptr nonnull %5) #37
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %4) #37
  call void @_ZN10skroot_env14fork_base_infoD2Ev(ptr noundef nonnull align 4 dereferenceable(24) %3) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  %45 = icmp eq i64 %44, 0
  br i1 %45, label %50, label %61

46:                                               ; preds = %12
  %47 = landingpad { ptr, i32 }
          cleanup
  br label %48

48:                                               ; preds = %46, %41
  %49 = phi { ptr, i32 } [ %47, %46 ], [ %42, %41 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %6) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %6) #37
  resume { ptr, i32 } %49

50:                                               ; preds = %43
  %51 = load i8, ptr %6, align 8
  %52 = and i8 %51, 1
  %53 = icmp eq i8 %52, 0
  %54 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %6, i64 0, i32 2
  %55 = load ptr, ptr %54, align 8
  %56 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %6, i64 0, i32 2
  %57 = select i1 %53, ptr %56, ptr %55
  %58 = getelementptr inbounds %"struct.skroot_env::SkrootSdkVersion", ptr %1, i64 0, i32 1
  %59 = getelementptr inbounds %"struct.skroot_env::SkrootSdkVersion", ptr %1, i64 0, i32 2
  %60 = call i32 (ptr, ptr, ...) @sscanf(ptr noundef %57, ptr noundef nonnull @.str.16, ptr noundef nonnull %1, ptr noundef nonnull %58, ptr noundef nonnull %59) #35
  br label %61

61:                                               ; preds = %50, %43
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %6) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %6) #37
  br label %62

62:                                               ; preds = %61, %8, %2
  %63 = phi i64 [ %44, %61 ], [ -19997, %2 ], [ -19997, %8 ]
  ret i64 %63
}

; Function Attrs: minsize nofree nounwind optsize
declare noundef i32 @sscanf(ptr nocapture noundef readonly, ptr nocapture noundef readonly, ...) local_unnamed_addr #9

; Function Attrs: minsize mustprogress nofree nounwind optsize uwtable
define hidden [2 x i64] @_ZN10skroot_env15get_sdk_versionEv() local_unnamed_addr #10 {
  %1 = alloca %"struct.skroot_env::SkrootSdkVersion", align 8
  call void @llvm.lifetime.start.p0(i64 12, ptr nonnull %1)
  store i32 0, ptr %1, align 8, !tbaa !89
  %2 = getelementptr inbounds %"struct.skroot_env::SkrootSdkVersion", ptr %1, i64 0, i32 1
  store i32 0, ptr %2, align 4, !tbaa !91
  %3 = getelementptr inbounds %"struct.skroot_env::SkrootSdkVersion", ptr %1, i64 0, i32 2
  store i32 0, ptr %3, align 8, !tbaa !92
  %4 = call i32 (ptr, ptr, ...) @sscanf(ptr noundef nonnull @.str.17, ptr noundef nonnull @.str.16, ptr noundef nonnull %1, ptr noundef nonnull %2, ptr noundef nonnull %3) #35
  %5 = load i64, ptr %1, align 8
  %6 = load i32, ptr %3, align 8
  %7 = zext i32 %6 to i64
  %8 = insertvalue [2 x i64] poison, i64 %5, 0
  call void @llvm.lifetime.end.p0(i64 12, ptr nonnull %1)
  %9 = insertvalue [2 x i64] %8, i64 %7, 1
  ret [2 x i64] %9
}

; Function Attrs: nocallback nofree nounwind willreturn memory(argmem: readwrite)
declare void @llvm.memcpy.p0.p0.i64(ptr noalias nocapture writeonly, ptr noalias nocapture readonly, i64, i1 immarg) #11

; Function Attrs: nocallback nofree nosync nounwind willreturn memory(inaccessiblemem: readwrite)
declare void @llvm.assume(i1 noundef) #12

; Function Attrs: inlinehint minsize optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #3 align 2 personality ptr @__gxx_personality_v0 {
  %4 = icmp ugt i64 %2, -17
  br i1 %4, label %5, label %6

5:                                                ; preds = %3
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
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
  %15 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %14) #35
  %16 = extractvalue [2 x i64] %15, 0
  %17 = inttoptr i64 %16 to ptr
  %18 = extractvalue [2 x i64] %15, 1
  %19 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  store ptr %17, ptr %19, align 8, !tbaa !38
  %20 = or i64 %18, 1
  store i64 %20, ptr %0, align 8
  %21 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %2, ptr %21, align 8, !tbaa !38
  br label %22

22:                                               ; preds = %12, %8
  %23 = phi ptr [ %11, %8 ], [ %17, %12 ]
  %24 = icmp ugt ptr %23, %1
  %25 = getelementptr inbounds i8, ptr %23, i64 %2
  %26 = icmp ule ptr %25, %1
  %27 = or i1 %24, %26
  tail call void @llvm.assume(i1 %27)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %23, ptr align 1 %1, i64 %2, i1 false)
  store i8 0, ptr %25, align 1, !tbaa !38
  ret void
}

; Function Attrs: minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #13 comdat align 2 {
  tail call void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef nonnull @.str.18) #42
  unreachable
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat {
  %3 = tail call noalias noundef nonnull ptr @_Znwm(i64 noundef %1) #43
  %4 = ptrtoint ptr %3 to i64
  %5 = insertvalue [2 x i64] poison, i64 %4, 0
  %6 = insertvalue [2 x i64] %5, i64 %1, 1
  ret [2 x i64] %6
}

; Function Attrs: inlinehint minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef %0) local_unnamed_addr #14 comdat personality ptr @__gxx_personality_v0 {
  %2 = tail call ptr @__cxa_allocate_exception(i64 16) #37
  invoke void @_ZNSt12length_errorC2B7v170000EPKc(ptr noundef nonnull align 8 dereferenceable(16) %2, ptr noundef %0) #35
          to label %3 unwind label %4

3:                                                ; preds = %1
  tail call void @__cxa_throw(ptr nonnull %2, ptr nonnull @_ZTISt12length_error, ptr nonnull @_ZNSt12length_errorD1Ev) #44
  unreachable

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          cleanup
  tail call void @__cxa_free_exception(ptr %2) #37
  resume { ptr, i32 } %5
}

declare ptr @__cxa_allocate_exception(i64) local_unnamed_addr

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt12length_errorC2B7v170000EPKc(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %1) unnamed_addr #1 comdat align 2 {
  tail call void @_ZNSt11logic_errorC2EPKc(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %1) #35
  store ptr getelementptr inbounds ({ [5 x ptr] }, ptr @_ZTVSt12length_error, i64 0, inrange i32 0, i64 2), ptr %0, align 8, !tbaa !93
  ret void
}

declare void @__cxa_free_exception(ptr) local_unnamed_addr

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt12length_errorD1Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #15

declare void @__cxa_throw(ptr, ptr, ptr) local_unnamed_addr

; Function Attrs: minsize optsize
declare void @_ZNSt11logic_errorC2EPKc(ptr noundef nonnull align 8 dereferenceable(16), ptr noundef) unnamed_addr #7

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt12length_errorD0Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize
declare noundef ptr @_ZNKSt11logic_error4whatEv(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #15

; Function Attrs: inlinehint minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZSt28__throw_bad_array_new_lengthB7v170000v() local_unnamed_addr #14 comdat {
  %1 = tail call ptr @__cxa_allocate_exception(i64 8) #37
  tail call void @_ZNSt20bad_array_new_lengthC1Ev(ptr noundef nonnull align 8 dereferenceable(8) %1) #34
  tail call void @__cxa_throw(ptr nonnull %1, ptr nonnull @_ZTISt20bad_array_new_length, ptr nonnull @_ZNSt20bad_array_new_lengthD1Ev) #44
  unreachable
}

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt20bad_array_new_lengthC1Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt20bad_array_new_lengthD1Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #15

; Function Attrs: minsize nobuiltin optsize allocsize(0)
declare noundef nonnull ptr @_Znwm(i64 noundef) local_unnamed_addr #16

; Function Attrs: minsize noinline noreturn nounwind optsize
define linkonce_odr hidden void @__clang_call_terminate(ptr noundef %0) local_unnamed_addr #17 comdat {
  %2 = tail call ptr @__cxa_begin_catch(ptr %0) #37
  tail call void @_ZSt9terminatev() #38
  unreachable
}

declare ptr @__cxa_begin_catch(ptr) local_unnamed_addr

declare void @_ZSt9terminatev() local_unnamed_addr

; Function Attrs: nocallback nofree nounwind willreturn memory(argmem: readwrite)
declare void @llvm.memmove.p0.p0.i64(ptr nocapture writeonly, ptr nocapture readonly, i64, i1 immarg) #11

; Function Attrs: minsize mustprogress nofree nounwind optsize willreturn memory(argmem: read)
declare i64 @strlen(ptr nocapture noundef) local_unnamed_addr #8

; Function Attrs: minsize nobuiltin nounwind optsize
declare void @_ZdlPv(ptr noundef) local_unnamed_addr #18

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE11__vallocateB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = icmp ugt i64 %1, 768614336404564650
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZNKSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
  unreachable

5:                                                ; preds = %2
  %6 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 2
  %7 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERSA_m(ptr noundef nonnull align 1 dereferenceable(1) %6, i64 noundef %1) #35
  %8 = extractvalue [2 x i64] %7, 0
  %9 = inttoptr i64 %8 to ptr
  %10 = extractvalue [2 x i64] %7, 1
  store ptr %9, ptr %0, align 8, !tbaa !95
  %11 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 1
  store ptr %9, ptr %11, align 8, !tbaa !99
  %12 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %9, i64 %10
  store ptr %12, ptr %6, align 8, !tbaa !42
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE18__construct_at_endIPKS6_Li0EEEvT_SC_m(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, ptr noundef %2, i64 noundef %3) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %5 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 1
  %6 = load ptr, ptr %5, align 8, !tbaa !99
  %7 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 2
  %8 = invoke noundef ptr @_ZNSt6__ndk130__uninitialized_allocator_copyB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPKS6_S9_PS6_EET2_RT_T0_T1_SB_(ptr noundef nonnull align 1 dereferenceable(1) %7, ptr noundef %1, ptr noundef %2, ptr noundef %6) #35
          to label %9 unwind label %10

9:                                                ; preds = %4
  store ptr %8, ptr %5, align 8, !tbaa !99
  ret void

10:                                               ; preds = %4
  %11 = landingpad { ptr, i32 }
          cleanup
  store ptr %6, ptr %5, align 8, !tbaa !99
  resume { ptr, i32 } %11
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk117__exception_guardINS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(9) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard", ptr %0, i64 0, i32 1
  %3 = load i8, ptr %2, align 8, !tbaa !47, !range !100, !noundef !101
  %4 = icmp eq i8 %3, 0
  br i1 %4, label %5, label %6

5:                                                ; preds = %1
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) #35
          to label %6 unwind label %7

6:                                                ; preds = %5, %1
  ret void

7:                                                ; preds = %5
  %8 = landingpad { ptr, i32 }
          catch ptr null
  %9 = extractvalue { ptr, i32 } %8, 0
  tail call void @__clang_call_terminate(ptr %9) #38
  unreachable
}

; Function Attrs: minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #13 comdat align 2 {
  tail call void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef nonnull @.str.19) #42
  unreachable
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERSA_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat {
  %3 = tail call noundef ptr @_ZNSt6__ndk19allocatorINS_12basic_stringIcNS_11char_traitsIcEENS0_IcEEEEE8allocateB7v170000Em(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) #35
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
  tail call void @_ZSt28__throw_bad_array_new_lengthB7v170000v() #42
  unreachable

5:                                                ; preds = %2
  %6 = mul nuw i64 %1, 24
  %7 = tail call noalias noundef nonnull ptr @_Znwm(i64 noundef %6) #43
  ret ptr %7
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk130__uninitialized_allocator_copyB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPKS6_S9_PS6_EET2_RT_T0_T1_SB_(ptr noundef nonnull align 1 dereferenceable(1) %0, ptr noundef %1, ptr noundef %2, ptr noundef %3) local_unnamed_addr #1 comdat personality ptr @__gxx_personality_v0 {
  %5 = alloca ptr, align 8
  %6 = alloca ptr, align 8
  %7 = alloca %"struct.std::__ndk1::__exception_guard.10", align 8
  store ptr %3, ptr %5, align 8, !tbaa !42
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %6) #37
  store ptr %3, ptr %6, align 8, !tbaa !42
  call void @llvm.lifetime.start.p0(i64 32, ptr nonnull %7) #37
  store ptr %0, ptr %7, align 8
  %8 = getelementptr inbounds i8, ptr %7, i64 8
  store ptr %6, ptr %8, align 8
  %9 = getelementptr inbounds i8, ptr %7, i64 16
  store ptr %5, ptr %9, align 8
  %10 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard.10", ptr %7, i64 0, i32 1
  store i8 0, ptr %10, align 8, !tbaa !102, !alias.scope !105
  br label %11

11:                                               ; preds = %16, %4
  %12 = phi ptr [ %3, %4 ], [ %19, %16 ]
  %13 = phi ptr [ %1, %4 ], [ %17, %16 ]
  %14 = icmp eq ptr %13, %2
  br i1 %14, label %22, label %15

15:                                               ; preds = %11
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %12, ptr noundef nonnull align 8 dereferenceable(24) %13) #35
          to label %16 unwind label %20

16:                                               ; preds = %15
  %17 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %13, i64 1
  %18 = load ptr, ptr %5, align 8, !tbaa !42
  %19 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %18, i64 1
  store ptr %19, ptr %5, align 8, !tbaa !42
  br label %11, !llvm.loop !108

20:                                               ; preds = %15
  %21 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk117__exception_guardINS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(25) %7) #34
  call void @llvm.lifetime.end.p0(i64 32, ptr nonnull %7) #37
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #37
  resume { ptr, i32 } %21

22:                                               ; preds = %11
  store i8 1, ptr %10, align 8, !tbaa !102
  call void @_ZNSt6__ndk117__exception_guardINS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(25) %7) #34
  call void @llvm.lifetime.end.p0(i64 32, ptr nonnull %7) #37
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %6) #37
  ret ptr %12
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk117__exception_guardINS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(25) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = getelementptr inbounds %"struct.std::__ndk1::__exception_guard.10", ptr %0, i64 0, i32 1
  %3 = load i8, ptr %2, align 8, !tbaa !102, !range !100, !noundef !101
  %4 = icmp eq i8 %3, 0
  br i1 %4, label %5, label %6

5:                                                ; preds = %1
  invoke void @_ZNKSt6__ndk129_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPS6_EclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #35
          to label %6 unwind label %7

6:                                                ; preds = %5, %1
  ret void

7:                                                ; preds = %5
  %8 = landingpad { ptr, i32 }
          catch ptr null
  %9 = extractvalue { ptr, i32 } %8, 0
  tail call void @__clang_call_terminate(ptr %9) #38
  unreachable
}

; Function Attrs: minsize optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) unnamed_addr #1 align 2 {
  %3 = load i8, ptr %1, align 8
  %4 = and i8 %3, 1
  %5 = icmp eq i8 %4, 0
  br i1 %5, label %6, label %7

6:                                                ; preds = %2
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i64 24, i1 false), !tbaa.struct !109
  br label %12

7:                                                ; preds = %2
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 2
  %9 = load ptr, ptr %8, align 8, !tbaa !38
  %10 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 1
  %11 = load i64, ptr %10, align 8, !tbaa !38
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE25__init_copy_ctor_externalEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %9, i64 noundef %11) #35
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
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
  unreachable

12:                                               ; preds = %9
  %13 = or i64 %2, 15
  %14 = add i64 %13, 1
  %15 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %14) #35
  %16 = extractvalue [2 x i64] %15, 0
  %17 = inttoptr i64 %16 to ptr
  %18 = extractvalue [2 x i64] %15, 1
  %19 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  store ptr %17, ptr %19, align 8, !tbaa !38
  %20 = or i64 %18, 1
  store i64 %20, ptr %0, align 8
  %21 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %2, ptr %21, align 8, !tbaa !38
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
define linkonce_odr hidden void @_ZNKSt6__ndk129_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPS6_EclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #1 comdat align 2 !type !112 {
  %2 = load ptr, ptr %0, align 8, !tbaa !113
  %3 = getelementptr inbounds %"class.std::__ndk1::_AllocatorDestroyRangeReverse", ptr %0, i64 0, i32 2
  %4 = load ptr, ptr %3, align 8, !tbaa !114
  %5 = load ptr, ptr %4, align 8, !tbaa !42
  %6 = getelementptr inbounds %"class.std::__ndk1::_AllocatorDestroyRangeReverse", ptr %0, i64 0, i32 1
  %7 = load ptr, ptr %6, align 8, !tbaa !115
  %8 = load ptr, ptr %7, align 8, !tbaa !42
  %9 = ptrtoint ptr %5 to i64
  %10 = insertvalue [2 x i64] poison, i64 %9, 0
  %11 = insertvalue [2 x i64] %10, i64 %9, 1
  %12 = ptrtoint ptr %8 to i64
  %13 = insertvalue [2 x i64] poison, i64 %12, 0
  %14 = insertvalue [2 x i64] %13, i64 %12, 1
  tail call void @_ZNSt6__ndk119__allocator_destroyB7v170000INS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEENS_16reverse_iteratorIPS6_EESA_EEvRT_T0_T1_(ptr noundef nonnull align 1 dereferenceable(1) %2, [2 x i64] %11, [2 x i64] %14) #35
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
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %12) #34
  br label %8, !llvm.loop !116

13:                                               ; preds = %8
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = load ptr, ptr %0, align 8, !tbaa !117
  %3 = load ptr, ptr %2, align 8, !tbaa !95
  %4 = icmp eq ptr %3, null
  br i1 %4, label %8, label %5

5:                                                ; preds = %1
  tail call void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE7__clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  %6 = load ptr, ptr %0, align 8, !tbaa !117
  %7 = load ptr, ptr %6, align 8, !tbaa !95
  tail call void @_ZdlPv(ptr noundef %7) #36
  br label %8

8:                                                ; preds = %5, %1
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE7__clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #19 comdat align 2 {
  %2 = load ptr, ptr %0, align 8, !tbaa !95
  tail call void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE22__base_destruct_at_endB7v170000EPS6_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %2) #34
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE22__base_destruct_at_endB7v170000EPS6_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !99
  br label %5

5:                                                ; preds = %8, %2
  %6 = phi ptr [ %4, %2 ], [ %9, %8 ]
  %7 = icmp eq ptr %6, %1
  br i1 %7, label %10, label %8

8:                                                ; preds = %5
  %9 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %6, i64 -1
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %9) #34
  br label %5

10:                                               ; preds = %5
  store ptr %1, ptr %3, align 8, !tbaa !99
  ret void
}

; Function Attrs: minsize nofree optsize
declare i64 @__strlen_chk(ptr noundef, i64 noundef) local_unnamed_addr #20

; Function Attrs: minsize optsize
declare noundef i1 @_ZN10skroot_env23fork_pipe_child_processERNS_14fork_pipe_infoE(ptr noundef nonnull align 4 dereferenceable(24)) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i1 @_ZN10skroot_env24write_errcode_from_childERKNS_14fork_pipe_infoE7KModErr(ptr noundef nonnull align 4 dereferenceable(24), i64 noundef) local_unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZN10skroot_env14fork_base_info9close_allEv(ptr noundef nonnull align 4 dereferenceable(24) %0) local_unnamed_addr #6 comdat align 2 !type !118 {
  tail call void @_ZN10skroot_env14fork_base_info10safe_closeERi(ptr noundef nonnull align 4 dereferenceable(4) %0) #35
  %2 = getelementptr inbounds %"class.skroot_env::fork_base_info", ptr %0, i64 0, i32 1
  tail call void @_ZN10skroot_env14fork_base_info10safe_closeERi(ptr noundef nonnull align 4 dereferenceable(4) %2) #35
  %3 = getelementptr inbounds %"class.skroot_env::fork_base_info", ptr %0, i64 0, i32 2
  tail call void @_ZN10skroot_env14fork_base_info10safe_closeERi(ptr noundef nonnull align 4 dereferenceable(4) %3) #35
  %4 = getelementptr inbounds %"class.skroot_env::fork_base_info", ptr %0, i64 0, i32 3
  tail call void @_ZN10skroot_env14fork_base_info10safe_closeERi(ptr noundef nonnull align 4 dereferenceable(4) %4) #35
  ret void
}

; Function Attrs: minsize noreturn optsize
declare void @_exit(i32 noundef) local_unnamed_addr #21

; Function Attrs: minsize optsize
declare noundef i1 @_ZN10skroot_env23read_errcode_from_childERKNS_14fork_pipe_infoER7KModErr(ptr noundef nonnull align 4 dereferenceable(24), ptr noundef nonnull align 8 dereferenceable(8)) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare i32 @waitpid(i32 noundef, ptr noundef, i32 noundef) local_unnamed_addr #7

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZN10skroot_env14fork_base_infoD2Ev(ptr noundef nonnull align 4 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  invoke void @_ZN10skroot_env14fork_base_info9close_allEv(ptr noundef nonnull align 4 dereferenceable(24) %0) #35
          to label %2 unwind label %3

2:                                                ; preds = %1
  ret void

3:                                                ; preds = %1
  %4 = landingpad { ptr, i32 }
          catch ptr null
  %5 = extractvalue { ptr, i32 } %4, 0
  tail call void @__clang_call_terminate(ptr %5) #38
  unreachable
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZN10skroot_env14fork_base_infoC2Ev(ptr noundef nonnull align 4 dereferenceable(24) %0) unnamed_addr #1 comdat align 2 {
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 4 dereferenceable(24) %0, i8 -1, i64 24, i1 false)
  tail call void @_ZN10skroot_env14fork_base_info5resetEv(ptr noundef nonnull align 4 dereferenceable(24) %0) #35
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZN10skroot_env14fork_base_info5resetEv(ptr noundef nonnull align 4 dereferenceable(24) %0) local_unnamed_addr #6 comdat align 2 !type !118 {
  tail call void @_ZN10skroot_env14fork_base_info9close_allEv(ptr noundef nonnull align 4 dereferenceable(24) %0) #35
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 4 dereferenceable(16) %0, i8 -1, i64 16, i1 false)
  %2 = tail call i32 @getpid() #35
  %3 = getelementptr inbounds %"class.skroot_env::fork_base_info", ptr %0, i64 0, i32 4
  store i32 %2, ptr %3, align 4, !tbaa !119
  %4 = getelementptr inbounds %"class.skroot_env::fork_base_info", ptr %0, i64 0, i32 5
  store i32 0, ptr %4, align 4, !tbaa !78
  ret void
}

; Function Attrs: minsize optsize
declare i32 @getpid() local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN15default_modules11module_root16set_current_rootEb(i1 noundef) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env27unsafe_clean_older_hide_dirEv() local_unnamed_addr #7

; Function Attrs: minsize optsize uwtable
define internal fastcc void @_ZN18boot_session_utilsL17read_boot_sessionEv(ptr noalias sret(%"class.std::__ndk1::basic_string") align 8 %0) unnamed_addr #1 personality ptr @__gxx_personality_v0 {
  %2 = alloca [128 x i8], align 1
  %3 = tail call ptr @fopen(ptr noundef nonnull @.str.20, ptr noundef nonnull @.str.21) #35
  %4 = icmp eq ptr %3, null
  br i1 %4, label %5, label %6

5:                                                ; preds = %1
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  br label %15

6:                                                ; preds = %1
  call void @llvm.lifetime.start.p0(i64 128, ptr nonnull %2) #37
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 1 dereferenceable(128) %2, i8 0, i64 128, i1 false)
  %7 = call noundef ptr @fgets(ptr noundef nonnull %2, i32 noundef 128, ptr noundef nonnull %3) #35
  %8 = icmp eq ptr %7, null
  %9 = call i32 @fclose(ptr noundef nonnull %3) #35
  br i1 %8, label %10, label %11

10:                                               ; preds = %6
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  br label %14

11:                                               ; preds = %6
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %2) #35
  invoke fastcc void @_ZN18boot_session_utils6detailL24trim_ascii_space_inplaceERNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE(ptr noundef nonnull align 8 dereferenceable(24) %0) #35
          to label %14 unwind label %12

12:                                               ; preds = %11
  %13 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #34
  call void @llvm.lifetime.end.p0(i64 128, ptr nonnull %2) #37
  resume { ptr, i32 } %13

14:                                               ; preds = %11, %10
  call void @llvm.lifetime.end.p0(i64 128, ptr nonnull %2) #37
  br label %15

15:                                               ; preds = %14, %5
  ret void
}

; Function Attrs: minsize optsize uwtable
define internal fastcc noundef i64 @_ZN10skroot_envL47unsafe_get_installed_skroot_environment_versionERNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEE(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #1 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::basic_string", align 8
  %3 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %4 = tail call noundef i64 @_ZN15default_modules11module_root16set_current_rootEb(i1 noundef true) #35
  %5 = icmp eq i64 %4, 0
  br i1 %5, label %6, label %31

6:                                                ; preds = %1
  %7 = tail call fastcc noundef i32 @_ZN10skroot_envL35unsafe_get_skroot_environment_stateEv() #35, !range !88
  %8 = icmp eq i32 %7, 0
  br i1 %8, label %31, label %9

9:                                                ; preds = %6
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %2) #37
  call fastcc void @_ZN10skroot_envL23get_skroot_sdk_ver_pathEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %2) #35
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %3) #37
  invoke void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000INS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %3, ptr noundef nonnull align 8 dereferenceable(24) %2, i8 noundef 0) #35
          to label %10 unwind label %12

10:                                               ; preds = %9
  %11 = call fastcc noundef i1 @_ZN10file_utilsL11file_existsERKNSt6__ndk14__fs10filesystem4pathE(ptr noundef nonnull align 8 dereferenceable(24) %3) #35
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %3) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  br i1 %11, label %14, label %27

12:                                               ; preds = %9
  %13 = landingpad { ptr, i32 }
          cleanup
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  br label %29

14:                                               ; preds = %10
  %15 = load i8, ptr %2, align 8
  %16 = and i8 %15, 1
  %17 = icmp eq i8 %16, 0
  %18 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %2, i64 0, i32 2
  %19 = load ptr, ptr %18, align 8
  %20 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %2, i64 0, i32 2
  %21 = select i1 %17, ptr %20, ptr %19
  %22 = invoke fastcc noundef i1 @_ZN10file_utilsL14read_text_fileEPKcRNSt6__ndk112basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEE(ptr noundef %21, ptr noundef nonnull align 8 dereferenceable(24) %0) #35
          to label %23 unwind label %25

23:                                               ; preds = %14
  %24 = select i1 %22, i64 0, i64 -19932
  br label %27

25:                                               ; preds = %14
  %26 = landingpad { ptr, i32 }
          cleanup
  br label %29

27:                                               ; preds = %23, %10
  %28 = phi i64 [ -19936, %10 ], [ %24, %23 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %2) #37
  br label %31

29:                                               ; preds = %25, %12
  %30 = phi { ptr, i32 } [ %26, %25 ], [ %13, %12 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %2) #37
  resume { ptr, i32 } %30

31:                                               ; preds = %27, %6, %1
  %32 = phi i64 [ %4, %1 ], [ %28, %27 ], [ -19916, %6 ]
  ret i64 %32
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNSt6__ndk1eqB7v170000IcNS_11char_traitsIcEENS_9allocatorIcEEEEbRKNS_12basic_stringIT_T0_T1_EEPKS6_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #2 comdat personality ptr @__gxx_personality_v0 {
  %3 = tail call [2 x i64] @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEcvNS_17basic_string_viewIcS2_EEB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #34
  %4 = icmp ne ptr %1, null
  tail call void @llvm.assume(i1 %4)
  %5 = tail call i64 @strlen(ptr noundef nonnull dereferenceable(1) %1) #34
  %6 = ptrtoint ptr %1 to i64
  %7 = insertvalue [2 x i64] poison, i64 %6, 0
  %8 = insertvalue [2 x i64] %7, i64 %5, 1
  %9 = tail call noundef i1 @_ZNSt6__ndk1eqB7v170000IcNS_11char_traitsIcEEEEbNS_17basic_string_viewIT_T0_EES6_([2 x i64] %3, [2 x i64] %8) #34
  ret i1 %9
}

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env22unsafe_create_hide_dirEv() local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env17unsafe_install_suEv() local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env27unsafe_install_webui_loaderEv() local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env32unsafe_install_autorun_bootstrapEv() local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env24unsafe_install_resetpropEv() local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i1 @_ZN15default_modules32module_bypass_oplus_secure_guard18first_install_initEv() local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env40unsafe_reset_autorun_bootstrap_safe_flagEv() local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env58unsafe_reset_autorun_bootstrap_duplicate_boot_session_flagEv() local_unnamed_addr #7

; Function Attrs: minsize optsize
declare void @sync() local_unnamed_addr #7

; Function Attrs: minsize nofree optsize
declare i32 @fork() local_unnamed_addr #20

; Function Attrs: minsize optsize
declare i32 @sleep(i32 noundef) local_unnamed_addr #7

; Function Attrs: minsize mustprogress nofree nounwind optsize uwtable
define internal fastcc void @_ZL12linux_rebootv() unnamed_addr #10 {
  %1 = tail call ptr @popen(ptr noundef nonnull @.str.50, ptr noundef nonnull @.str.21) #35
  %2 = icmp eq ptr %1, null
  br i1 %2, label %5, label %3

3:                                                ; preds = %0
  %4 = tail call i32 @pclose(ptr noundef nonnull %1) #35
  br label %5

5:                                                ; preds = %3, %0
  ret void
}

; Function Attrs: minsize optsize uwtable
define internal fastcc void @_ZN10skroot_envL26get_autorun_bootstrap_pathEv(ptr noalias sret(%"class.std::__ndk1::basic_string") align 8 %0) unnamed_addr #1 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %3 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %4 = alloca %"class.std::__ndk1::basic_string", align 8
  %5 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %2) #37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %3) #37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %4) #37
  call fastcc void @_ZN10skroot_envL24get_kernel_file_dir_pathEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %4) #35
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %3, ptr noundef nonnull align 8 dereferenceable(24) %4, i64 24, i1 false)
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %4, i8 0, i64 24, i1 false)
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %5) #37
  invoke void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA5_cvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %5, ptr noundef nonnull align 1 dereferenceable(5) @.str.51, i8 noundef 0) #35
          to label %6 unwind label %9

6:                                                ; preds = %1
  invoke void @_ZNSt6__ndk14__fs10filesystemdvB7v170000ERKNS1_4pathES4_(ptr nonnull sret(%"class.std::__ndk1::__fs::filesystem::path") align 8 %2, ptr noundef nonnull align 8 dereferenceable(24) %3, ptr noundef nonnull align 8 dereferenceable(24) %5) #35
          to label %7 unwind label %11

7:                                                ; preds = %6
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %2) #35
          to label %8 unwind label %13

8:                                                ; preds = %7
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %3) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %2) #37
  ret void

9:                                                ; preds = %1
  %10 = landingpad { ptr, i32 }
          cleanup
  br label %17

11:                                               ; preds = %6
  %12 = landingpad { ptr, i32 }
          cleanup
  br label %15

13:                                               ; preds = %7
  %14 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  br label %15

15:                                               ; preds = %13, %11
  %16 = phi { ptr, i32 } [ %14, %13 ], [ %12, %11 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #34
  br label %17

17:                                               ; preds = %15, %9
  %18 = phi { ptr, i32 } [ %16, %15 ], [ %10, %9 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %3) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %2) #37
  resume { ptr, i32 } %18
}

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env23unsafe_restart_zygote64Ev() local_unnamed_addr #7

; Function Attrs: minsize nofree nounwind optsize
declare noalias noundef ptr @fopen(ptr nocapture noundef readonly, ptr nocapture noundef readonly) local_unnamed_addr #9

; Function Attrs: nocallback nofree nounwind willreturn memory(argmem: write)
declare void @llvm.memset.p0.i64(ptr nocapture writeonly, i8, i64, i1 immarg) #22

; Function Attrs: minsize nofree nounwind optsize
declare noundef i32 @fclose(ptr nocapture noundef) #9

; Function Attrs: minsize mustprogress optsize uwtable
define internal fastcc void @_ZN18boot_session_utils6detailL24trim_ascii_space_inplaceERNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #6 {
  %2 = alloca %"class.std::__ndk1::basic_string", align 8
  %3 = load i8, ptr %0, align 8
  %4 = and i8 %3, 1
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %7 = load i64, ptr %6, align 8
  %8 = lshr i8 %3, 1
  %9 = zext i8 %8 to i64
  %10 = select i1 %5, i64 %9, i64 %7
  %11 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %12 = load ptr, ptr %11, align 8
  %13 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %14 = select i1 %5, ptr %13, ptr %12
  br label %15

15:                                               ; preds = %21, %1
  %16 = phi i64 [ 0, %1 ], [ %22, %21 ]
  %17 = icmp eq i64 %16, %10
  br i1 %17, label %23, label %18

18:                                               ; preds = %15
  %19 = getelementptr inbounds i8, ptr %14, i64 %16
  %20 = load i8, ptr %19, align 1, !tbaa !38
  switch i8 %20, label %23 [
    i8 32, label %21
    i8 13, label %21
    i8 9, label %21
    i8 10, label %21
  ]

21:                                               ; preds = %18, %18, %18, %18
  %22 = add i64 %16, 1
  br label %15, !llvm.loop !120

23:                                               ; preds = %18, %15
  %24 = phi i64 [ %16, %18 ], [ %10, %15 ]
  %25 = call i64 @llvm.umin.i64(i64 %10, i64 %24)
  br label %26

26:                                               ; preds = %33, %23
  %27 = phi i64 [ %10, %23 ], [ %30, %33 ]
  %28 = icmp ugt i64 %27, %24
  br i1 %28, label %29, label %34

29:                                               ; preds = %26
  %30 = add i64 %27, -1
  %31 = getelementptr inbounds i8, ptr %14, i64 %30
  %32 = load i8, ptr %31, align 1, !tbaa !38
  switch i8 %32, label %34 [
    i8 32, label %33
    i8 13, label %33
    i8 9, label %33
    i8 10, label %33
  ]

33:                                               ; preds = %29, %29, %29, %29
  br label %26, !llvm.loop !121

34:                                               ; preds = %29, %26
  %35 = phi i64 [ %27, %29 ], [ %25, %26 ]
  %36 = icmp eq i64 %24, 0
  %37 = icmp eq i64 %35, %10
  %38 = select i1 %36, i1 %37, i1 false
  br i1 %38, label %42, label %39

39:                                               ; preds = %34
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %2) #37
  %40 = sub i64 %35, %24
  call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6substrB7v170000Emm(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %2, ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %24, i64 noundef %40) #35
  %41 = call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEaSB7v170000EOS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %2) #37
  br label %42

42:                                               ; preds = %39, %34
  ret void
}

; Function Attrs: convergent nocallback nofree nosync nounwind willreturn memory(none)
declare i1 @llvm.is.constant.i64(i64) #23

; Function Attrs: minsize nofree nounwind optsize
declare noundef ptr @fgets(ptr noundef, i32 noundef, ptr nocapture noundef) local_unnamed_addr #9

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6substrB7v170000Emm(ptr noalias sret(%"class.std::__ndk1::basic_string") align 8 %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i64 noundef %2, i64 noundef %3) local_unnamed_addr #1 comdat align 2 {
  %5 = alloca %"class.std::__ndk1::allocator", align 1
  call void @llvm.lifetime.start.p0(i64 1, ptr nonnull %5) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_mmRKS4_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i64 noundef %2, i64 noundef %3, ptr noundef nonnull align 1 dereferenceable(1) %5) #35
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %5) #37
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEaSB7v170000EOS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #19 comdat align 2 {
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE13__move_assignB7v170000ERS5_NS_17integral_constantIbLb1EEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i8 undef) #34
  ret ptr %0
}

; Function Attrs: minsize optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_mmRKS4_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i64 noundef %2, i64 noundef %3, ptr noundef nonnull align 1 dereferenceable(1) %4) unnamed_addr #1 align 2 {
  %6 = load i8, ptr %1, align 8
  %7 = and i8 %6, 1
  %8 = icmp eq i8 %7, 0
  %9 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 1
  %10 = load i64, ptr %9, align 8
  %11 = lshr i8 %6, 1
  %12 = zext i8 %11 to i64
  %13 = select i1 %8, i64 %12, i64 %10
  %14 = icmp ult i64 %13, %2
  br i1 %14, label %15, label %16

15:                                               ; preds = %5
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_out_of_rangeB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
  unreachable

16:                                               ; preds = %5
  %17 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 2
  %18 = load ptr, ptr %17, align 8
  %19 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %1, i64 0, i32 2
  %20 = select i1 %8, ptr %19, ptr %18
  %21 = getelementptr inbounds i8, ptr %20, i64 %2
  %22 = sub i64 %13, %2
  %23 = tail call i64 @llvm.umin.i64(i64 %22, i64 %3)
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %21, i64 noundef %23) #35
  ret void
}

; Function Attrs: minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_out_of_rangeB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #13 comdat align 2 {
  tail call void @_ZNSt6__ndk120__throw_out_of_rangeB7v170000EPKc(ptr noundef nonnull @.str.18) #42
  unreachable
}

; Function Attrs: inlinehint minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk120__throw_out_of_rangeB7v170000EPKc(ptr noundef %0) local_unnamed_addr #14 comdat personality ptr @__gxx_personality_v0 {
  %2 = tail call ptr @__cxa_allocate_exception(i64 16) #37
  invoke void @_ZNSt12out_of_rangeC2B7v170000EPKc(ptr noundef nonnull align 8 dereferenceable(16) %2, ptr noundef %0) #35
          to label %3 unwind label %4

3:                                                ; preds = %1
  tail call void @__cxa_throw(ptr nonnull %2, ptr nonnull @_ZTISt12out_of_range, ptr nonnull @_ZNSt12out_of_rangeD1Ev) #44
  unreachable

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          cleanup
  tail call void @__cxa_free_exception(ptr %2) #37
  resume { ptr, i32 } %5
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt12out_of_rangeC2B7v170000EPKc(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %1) unnamed_addr #1 comdat align 2 {
  tail call void @_ZNSt11logic_errorC2EPKc(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %1) #35
  store ptr getelementptr inbounds ({ [5 x ptr] }, ptr @_ZTVSt12out_of_range, i64 0, inrange i32 0, i64 2), ptr %0, align 8, !tbaa !93
  ret void
}

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt12out_of_rangeD1Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt12out_of_rangeD0Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #15

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE13__move_assignB7v170000ERS5_NS_17integral_constantIbLb1EEE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i8 %2) local_unnamed_addr #24 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = load i8, ptr %0, align 8
  %5 = and i8 %4, 1
  %6 = icmp eq i8 %5, 0
  br i1 %6, label %10, label %7

7:                                                ; preds = %3
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %9 = load ptr, ptr %8, align 8, !tbaa !38
  tail call void @_ZdlPv(ptr noundef %9) #36
  br label %10

10:                                               ; preds = %7, %3
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i64 24, i1 false), !tbaa.struct !109
  store i8 0, ptr %1, align 8
  %11 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %1, i64 0, i32 2
  store i8 0, ptr %11, align 1, !tbaa !38
  ret void
}

; Function Attrs: minsize optsize uwtable
define internal fastcc noundef i32 @_ZN10skroot_envL35unsafe_get_skroot_environment_stateEv() unnamed_addr #1 {
  %state = tail call i32 @maplesu_environment_state()
  ret i32 %state
}

; Function Attrs: minsize optsize uwtable
define internal fastcc void @_ZN10skroot_envL23get_skroot_sdk_ver_pathEv(ptr noalias sret(%"class.std::__ndk1::basic_string") align 8 %0) unnamed_addr #1 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %3 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  %4 = alloca %"class.std::__ndk1::basic_string", align 8
  %5 = alloca %"class.std::__ndk1::__fs::filesystem::path", align 8
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %2) #37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %3) #37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %4) #37
  call fastcc void @_ZN10skroot_envL24get_kernel_file_dir_pathEv(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %4) #35
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %3, ptr noundef nonnull align 8 dereferenceable(24) %4, i64 24, i1 false)
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %4, i8 0, i64 24, i1 false)
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %5) #37
  invoke void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA15_cvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %5, ptr noundef nonnull align 1 dereferenceable(15) @.str.23, i8 noundef 0) #35
          to label %6 unwind label %9

6:                                                ; preds = %1
  invoke void @_ZNSt6__ndk14__fs10filesystemdvB7v170000ERKNS1_4pathES4_(ptr nonnull sret(%"class.std::__ndk1::__fs::filesystem::path") align 8 %2, ptr noundef nonnull align 8 dereferenceable(24) %3, ptr noundef nonnull align 8 dereferenceable(24) %5) #35
          to label %7 unwind label %11

7:                                                ; preds = %6
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %2) #35
          to label %8 unwind label %13

8:                                                ; preds = %7
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %3) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %2) #37
  ret void

9:                                                ; preds = %1
  %10 = landingpad { ptr, i32 }
          cleanup
  br label %17

11:                                               ; preds = %6
  %12 = landingpad { ptr, i32 }
          cleanup
  br label %15

13:                                               ; preds = %7
  %14 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  br label %15

15:                                               ; preds = %13, %11
  %16 = phi { ptr, i32 } [ %14, %13 ], [ %12, %11 ]
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %5) #34
  br label %17

17:                                               ; preds = %15, %9
  %18 = phi { ptr, i32 } [ %16, %15 ], [ %10, %9 ]
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %5) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %3) #34
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %3) #37
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %2) #37
  resume { ptr, i32 } %18
}

; Function Attrs: minsize nounwind optsize uwtable
define internal fastcc noundef i1 @_ZN10file_utilsL11file_existsERKNSt6__ndk14__fs10filesystem4pathE(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 {
  %2 = alloca %"class.std::__ndk1::error_code", align 8
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %2) #37
  store i32 0, ptr %2, align 8, !tbaa !85
  %3 = getelementptr inbounds %"class.std::__ndk1::error_code", ptr %2, i64 0, i32 1
  %4 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk115system_categoryEv() #34
  store ptr %4, ptr %3, align 8, !tbaa !87
  %5 = call noundef i1 @_ZNSt6__ndk14__fs10filesystem6existsB7v170000ERKNS1_4pathERNS_10error_codeE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(16) %2) #34
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %2) #37
  ret i1 %5
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000INS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i8 noundef %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  invoke void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000INS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEvRSA_RKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) #35
          to label %4 unwind label %5

4:                                                ; preds = %3
  ret void

5:                                                ; preds = %3
  %6 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #34
  resume { ptr, i32 } %6
}

; Function Attrs: minsize optsize uwtable
define internal fastcc noundef i1 @_ZN10file_utilsL14read_text_fileEPKcRNSt6__ndk112basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEE(ptr noundef %0, ptr noundef nonnull align 8 dereferenceable(24) %1) unnamed_addr #1 personality ptr @__gxx_personality_v0 {
  %3 = alloca %"class.std::__ndk1::basic_ifstream", align 8
  %4 = alloca %"class.std::__ndk1::vector.13", align 8
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %4) #37
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %4, i8 0, i64 24, i1 false)
  call void @llvm.lifetime.start.p0(i64 336, ptr nonnull %3) #37
  invoke void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEC1EPKcj(ptr noundef nonnull align 8 dereferenceable(184) %3, ptr noundef %0, i32 noundef 4) #35
          to label %5 unwind label %46

5:                                                ; preds = %2
  %6 = load ptr, ptr %3, align 8, !tbaa !93
  %7 = getelementptr i8, ptr %6, i64 -24
  %8 = load i64, ptr %7, align 8
  %9 = getelementptr inbounds i8, ptr %3, i64 %8
  %10 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %9, i64 0, i32 4
  %11 = load i32, ptr %10, align 8, !tbaa !125
  %12 = and i32 %11, 5
  %13 = icmp eq i32 %12, 0
  br i1 %13, label %16, label %44

14:                                               ; preds = %16
  %15 = landingpad { ptr, i32 }
          cleanup
  br label %42

16:                                               ; preds = %5
  %17 = invoke noundef nonnull align 8 dereferenceable(16) ptr @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEE5seekgExNS_8ios_base7seekdirE(ptr noundef nonnull align 8 dereferenceable(16) %3, i64 noundef 0, i32 noundef 2) #35
          to label %18 unwind label %14

18:                                               ; preds = %16
  %19 = invoke [2 x i64] @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEE5tellgEv(ptr noundef nonnull align 8 dereferenceable(16) %3) #35
          to label %20 unwind label %23

20:                                               ; preds = %18
  %21 = extractvalue [2 x i64] %19, 1
  %22 = icmp slt i64 %21, 0
  br i1 %22, label %44, label %25

23:                                               ; preds = %18
  %24 = landingpad { ptr, i32 }
          cleanup
  br label %42

25:                                               ; preds = %20
  %26 = invoke noundef nonnull align 8 dereferenceable(16) ptr @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEE5seekgExNS_8ios_base7seekdirE(ptr noundef nonnull align 8 dereferenceable(16) %3, i64 noundef 0, i32 noundef 0) #35
          to label %27 unwind label %40

27:                                               ; preds = %25
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE6resizeEm(ptr noundef nonnull align 8 dereferenceable(24) %4, i64 noundef %21) #35
          to label %28 unwind label %40

28:                                               ; preds = %27
  %29 = load ptr, ptr %4, align 8, !tbaa !127
  %30 = invoke noundef nonnull align 8 dereferenceable(16) ptr @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEE4readEPcl(ptr noundef nonnull align 8 dereferenceable(16) %3, ptr noundef %29, i64 noundef %21) #35
          to label %31 unwind label %40

31:                                               ; preds = %28
  %32 = load ptr, ptr %30, align 8, !tbaa !93
  %33 = getelementptr i8, ptr %32, i64 -24
  %34 = load i64, ptr %33, align 8
  %35 = getelementptr inbounds i8, ptr %30, i64 %34
  %36 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %35, i64 0, i32 4
  %37 = load i32, ptr %36, align 8, !tbaa !125
  %38 = and i32 %37, 5
  %39 = icmp eq i32 %38, 0
  br label %44

40:                                               ; preds = %28, %27, %25
  %41 = landingpad { ptr, i32 }
          cleanup
  br label %42

42:                                               ; preds = %40, %23, %14
  %43 = phi { ptr, i32 } [ %15, %14 ], [ %41, %40 ], [ %24, %23 ]
  call void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(184) %3) #34
  call void @llvm.lifetime.end.p0(i64 336, ptr nonnull %3) #37
  br label %48

44:                                               ; preds = %31, %20, %5
  %45 = phi i1 [ false, %5 ], [ false, %20 ], [ %39, %31 ]
  call void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(184) %3) #34
  call void @llvm.lifetime.end.p0(i64 336, ptr nonnull %3) #37
  br i1 %45, label %50, label %57

46:                                               ; preds = %50, %2
  %47 = landingpad { ptr, i32 }
          cleanup
  br label %48

48:                                               ; preds = %46, %42
  %49 = phi { ptr, i32 } [ %47, %46 ], [ %43, %42 ]
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  resume { ptr, i32 } %49

50:                                               ; preds = %44
  %51 = load ptr, ptr %4, align 8, !tbaa !127
  %52 = ptrtoint ptr %51 to i64
  %53 = getelementptr inbounds %"class.std::__ndk1::vector.13", ptr %4, i64 0, i32 1
  %54 = load ptr, ptr %53, align 8, !tbaa !131
  %55 = ptrtoint ptr %54 to i64
  %56 = invoke noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignINS_11__wrap_iterIPhEEEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESB_SB_(ptr noundef nonnull align 8 dereferenceable(24) %1, i64 %52, i64 %55) #35
          to label %57 unwind label %46

57:                                               ; preds = %50, %44
  call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  ret i1 %45
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk1plB7v170000IcNS_11char_traitsIcEENS_9allocatorIcEEEENS_12basic_stringIT_T0_T1_EEOS9_PKS6_(ptr noalias sret(%"class.std::__ndk1::basic_string") align 8 %0, ptr noundef nonnull align 8 dereferenceable(24) %1, ptr noundef %2) local_unnamed_addr #3 comdat personality ptr @__gxx_personality_v0 {
  %4 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendEPKc(ptr noundef nonnull align 8 dereferenceable(24) %1, ptr noundef %2) #35
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %4, i64 24, i1 false)
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %4, i8 0, i64 24, i1 false)
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define available_externally noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendEPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #6 align 2 personality ptr @__gxx_personality_v0 {
  %3 = icmp ne ptr %1, null
  tail call void @llvm.assume(i1 %3)
  %4 = tail call i64 @strlen(ptr noundef nonnull dereferenceable(1) %1) #34
  %5 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1, i64 noundef %4) #35
  ret ptr %5
}

; Function Attrs: minsize optsize uwtable
define available_externally noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #1 align 2 personality ptr @__gxx_personality_v0 {
  %4 = icmp eq i64 %2, 0
  %5 = icmp ne ptr %1, null
  %6 = or i1 %5, %4
  tail call void @llvm.assume(i1 %6)
  %7 = load i8, ptr %0, align 8
  %8 = and i8 %7, 1
  %9 = icmp eq i8 %8, 0
  %10 = load i64, ptr %0, align 8
  %11 = and i64 %10, -2
  %12 = add i64 %11, -1
  %13 = select i1 %9, i64 22, i64 %12
  %14 = and i64 %10, 1
  %15 = icmp eq i64 %14, 0
  %16 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %17 = load i64, ptr %16, align 8
  %18 = lshr i64 %10, 1
  %19 = and i64 %18, 127
  %20 = select i1 %15, i64 %19, i64 %17
  %21 = sub i64 %13, %20
  %22 = icmp ult i64 %21, %2
  br i1 %22, label %45, label %23

23:                                               ; preds = %3
  br i1 %4, label %48, label %24

24:                                               ; preds = %23
  %25 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %26 = load ptr, ptr %25, align 8
  %27 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %28 = select i1 %15, ptr %27, ptr %26
  %29 = getelementptr inbounds i8, ptr %28, i64 %20
  %30 = icmp ugt ptr %29, %1
  %31 = getelementptr inbounds i8, ptr %29, i64 %2
  %32 = icmp ule ptr %31, %1
  %33 = select i1 %30, i1 true, i1 %32
  tail call void @llvm.assume(i1 %33)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %29, ptr align 1 %1, i64 %2, i1 false)
  %34 = add i64 %20, %2
  %35 = load i8, ptr %0, align 8
  %36 = and i8 %35, 1
  %37 = icmp eq i8 %36, 0
  br i1 %37, label %39, label %38

38:                                               ; preds = %24
  store i64 %34, ptr %16, align 8, !tbaa !38
  br label %43

39:                                               ; preds = %24
  %40 = icmp ult i64 %34, 23
  tail call void @llvm.assume(i1 %40)
  %41 = trunc i64 %34 to i8
  %42 = shl nuw nsw i8 %41, 1
  store i8 %42, ptr %0, align 8
  br label %43

43:                                               ; preds = %39, %38
  %44 = getelementptr inbounds i8, ptr %28, i64 %34
  store i8 0, ptr %44, align 1, !tbaa !38
  br label %48

45:                                               ; preds = %3
  %46 = add i64 %20, %2
  %47 = sub i64 %46, %13
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE21__grow_by_and_replaceEmmmmmmPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %13, i64 noundef %47, i64 noundef %20, i64 noundef %20, i64 noundef 0, i64 noundef %2, ptr noundef %1) #35
  br label %48

48:                                               ; preds = %45, %43, %23
  ret ptr %0
}

; Function Attrs: minsize optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE21__grow_by_and_replaceEmmmmmmPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1, i64 noundef %2, i64 noundef %3, i64 noundef %4, i64 noundef %5, i64 noundef %6, ptr noundef %7) local_unnamed_addr #1 align 2 personality ptr @__gxx_personality_v0 {
  %9 = sub i64 -18, %1
  %10 = icmp ult i64 %9, %2
  br i1 %10, label %11, label %12

11:                                               ; preds = %8
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
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
  %31 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %30) #35
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
  tail call void @_ZdlPv(ptr noundef %19) #36
  br label %64

64:                                               ; preds = %63, %61
  %65 = extractvalue [2 x i64] %31, 1
  store ptr %33, ptr %16, align 8, !tbaa !38
  %66 = or i64 %65, 1
  store i64 %66, ptr %0, align 8
  %67 = add i64 %6, %4
  %68 = add i64 %67, %50
  %69 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %68, ptr %69, align 8, !tbaa !38
  %70 = getelementptr inbounds i8, ptr %33, i64 %68
  store i8 0, ptr %70, align 1, !tbaa !38
  ret void
}

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNSt6__ndk1eqB7v170000INS_9allocatorIcEEEEbRKNS_12basic_stringIcNS_11char_traitsIcEET_EES9_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #24 comdat {
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
  %29 = tail call noundef i32 @_ZNSt6__ndk111char_traitsIcE7compareEPKcS3_m(ptr noundef %28, ptr noundef %25, i64 noundef %10) #34
  %30 = icmp eq i32 %29, 0
  br label %44

31:                                               ; preds = %40, %20
  %32 = phi i64 [ %41, %40 ], [ %9, %20 ]
  %33 = phi ptr [ %42, %40 ], [ %21, %20 ]
  %34 = phi ptr [ %43, %40 ], [ %25, %20 ]
  %35 = icmp eq i64 %32, 0
  br i1 %35, label %44, label %36

36:                                               ; preds = %31
  %37 = load i8, ptr %33, align 1, !tbaa !38
  %38 = load i8, ptr %34, align 1, !tbaa !38
  %39 = icmp eq i8 %37, %38
  br i1 %39, label %40, label %44

40:                                               ; preds = %36
  %41 = add i64 %32, -1
  %42 = getelementptr inbounds i8, ptr %33, i64 1
  %43 = getelementptr inbounds i8, ptr %34, i64 1
  br label %31, !llvm.loop !132

44:                                               ; preds = %36, %31, %26, %2
  %45 = phi i1 [ false, %2 ], [ %30, %26 ], [ false, %36 ], [ true, %31 ]
  ret i1 %45
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i32 @_ZNSt6__ndk111char_traitsIcE7compareEPKcS3_m(ptr noundef %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = icmp eq i64 %2, 0
  br i1 %4, label %7, label %5

5:                                                ; preds = %3
  %6 = tail call i32 @memcmp(ptr noundef %0, ptr noundef %1, i64 noundef %2) #34
  br label %7

7:                                                ; preds = %5, %3
  %8 = phi i32 [ %6, %5 ], [ 0, %3 ]
  ret i32 %8
}

; Function Attrs: minsize mustprogress nofree nounwind optsize willreturn memory(argmem: read)
declare i32 @memcmp(ptr nocapture noundef, ptr nocapture noundef, i64 noundef) local_unnamed_addr #8

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystemdvB7v170000ERKNS1_4pathES4_(ptr noalias sret(%"class.std::__ndk1::__fs::filesystem::path") align 8 %0, ptr noundef nonnull align 8 dereferenceable(24) %1, ptr noundef nonnull align 8 dereferenceable(24) %2) local_unnamed_addr #1 comdat personality ptr @__gxx_personality_v0 {
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) #35
  %4 = invoke noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk14__fs10filesystem4pathdVB7v170000ERKS2_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %2) #35
          to label %7 unwind label %5

5:                                                ; preds = %3
  %6 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #34
  resume { ptr, i32 } %6

7:                                                ; preds = %3
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define internal fastcc void @_ZN10skroot_envL24get_kernel_file_dir_pathEv(ptr noalias sret(%"class.std::__ndk1::basic_string") align 8 %0) unnamed_addr #6 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::basic_string", align 8
  %3 = alloca [1024 x i8], align 1
  %4 = alloca %"class.std::__ndk1::basic_string", align 8
  call void @llvm.lifetime.start.p0(i64 1024, ptr nonnull %3) #37
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 1 dereferenceable(1024) %3, i8 0, i64 1024, i1 false)
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %4) #37
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %2) #37, !noalias !133
  %5 = tail call ptr @__get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW() #35, !noalias !133
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %2, ptr noundef %5) #35, !noalias !133
  invoke void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6substrB7v170000Emm(ptr nonnull sret(%"class.std::__ndk1::basic_string") align 8 %4, ptr noundef nonnull align 8 dereferenceable(24) %2, i64 noundef 0, i64 noundef 16) #35
          to label %8 unwind label %6

6:                                                ; preds = %1
  %7 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %2) #37, !noalias !133
  resume { ptr, i32 } %7

8:                                                ; preds = %1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %2) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %2) #37, !noalias !133
  %9 = load i8, ptr %4, align 8
  %10 = and i8 %9, 1
  %11 = icmp eq i8 %10, 0
  %12 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %4, i64 0, i32 2
  %13 = load ptr, ptr %12, align 8
  %14 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %4, i64 0, i32 2
  %15 = select i1 %11, ptr %14, ptr %13
  call void (ptr, i64, i64, ptr, ...) @_ZL8snprintfPcU17pass_object_size1mPKcz(ptr noundef nonnull %3, i64 poison, i64 poison, ptr nonnull poison, ptr noundef %15) #35
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %3) #35
  call void @llvm.lifetime.end.p0(i64 1024, ptr nonnull %3) #37
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA15_cvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(15) %1, i8 noundef %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  invoke void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA15_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(15) %1) #35
          to label %4 unwind label %5

4:                                                ; preds = %3
  ret void

5:                                                ; preds = %3
  %6 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #34
  resume { ptr, i32 } %6
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk14__fs10filesystem4pathdVB7v170000ERKS2_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #6 comdat align 2 {
  %3 = tail call noundef i1 @_ZNKSt6__ndk14__fs10filesystem4path18has_root_directoryB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %1) #35
  br i1 %3, label %4, label %6

4:                                                ; preds = %2
  %5 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEaSERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) #35
  br label %11

6:                                                ; preds = %2
  %7 = tail call noundef i1 @_ZNKSt6__ndk14__fs10filesystem4path12has_filenameB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #35
  br i1 %7, label %8, label %9

8:                                                ; preds = %6
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE9push_backEc(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 noundef 47) #35
  br label %9

9:                                                ; preds = %8, %6
  %10 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) #35
  br label %11

11:                                               ; preds = %9, %4
  ret ptr %0
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
  tail call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1, i64 24, i1 false), !tbaa.struct !109
  br label %30

13:                                               ; preds = %11
  %14 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 2
  %15 = load ptr, ptr %14, align 8
  %16 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 1
  %17 = load i64, ptr %16, align 8
  %18 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb1EEERS5_PKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %15, i64 noundef %17) #35
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
  %29 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_no_aliasILb0EEERS5_PKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %23, i64 noundef %28) #35
  br label %30

30:                                               ; preds = %19, %13, %12, %2
  %31 = phi ptr [ %29, %19 ], [ %18, %13 ], [ %0, %12 ], [ %0, %2 ]
  ret ptr %31
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNKSt6__ndk14__fs10filesystem4path12has_filenameB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #6 comdat align 2 {
  %2 = tail call [2 x i64] @_ZNKSt6__ndk14__fs10filesystem4path10__filenameEv(ptr noundef nonnull align 8 dereferenceable(24) %0) #35
  %3 = extractvalue [2 x i64] %2, 1
  %4 = icmp ne i64 %3, 0
  ret i1 %4
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNKSt6__ndk14__fs10filesystem4path18has_root_directoryB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #6 comdat align 2 {
  %2 = tail call [2 x i64] @_ZNKSt6__ndk14__fs10filesystem4path16__root_directoryEv(ptr noundef nonnull align 8 dereferenceable(24) %0) #35
  %3 = extractvalue [2 x i64] %2, 1
  %4 = icmp ne i64 %3, 0
  ret i1 %4
}

; Function Attrs: minsize optsize
declare [2 x i64] @_ZNKSt6__ndk14__fs10filesystem4path16__root_directoryEv(ptr noundef nonnull align 8 dereferenceable(24)) local_unnamed_addr #7

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
  store i8 0, ptr %10, align 1, !tbaa !38
  br label %18

13:                                               ; preds = %3
  %14 = load i8, ptr %0, align 8
  %15 = lshr i8 %14, 1
  %16 = zext i8 %15 to i64
  %17 = add i64 %2, -22
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE21__grow_by_and_replaceEmmmmmmPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef 22, i64 noundef %17, i64 noundef %16, i64 noundef 0, i64 noundef %16, i64 noundef %2, ptr noundef %1) #35
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
  %9 = load ptr, ptr %8, align 8, !tbaa !38
  %10 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %2, ptr %10, align 8, !tbaa !38
  %11 = icmp ugt ptr %9, %1
  %12 = getelementptr inbounds i8, ptr %9, i64 %2
  %13 = icmp ule ptr %12, %1
  %14 = or i1 %11, %13
  tail call void @llvm.assume(i1 %14)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %9, ptr align 1 %1, i64 %2, i1 false)
  store i8 0, ptr %12, align 1, !tbaa !38
  br label %21

15:                                               ; preds = %3
  %16 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %17 = load i64, ptr %16, align 8, !tbaa !38
  %18 = add i64 %5, -1
  %19 = add i64 %2, 1
  %20 = sub i64 %19, %5
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE21__grow_by_and_replaceEmmmmmmPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %18, i64 noundef %20, i64 noundef %17, i64 noundef 0, i64 noundef %17, i64 noundef %2, ptr noundef %1) #35
  br label %21

21:                                               ; preds = %15, %7
  ret ptr %0
}

; Function Attrs: minsize optsize
declare [2 x i64] @_ZNKSt6__ndk14__fs10filesystem4path10__filenameEv(ptr noundef nonnull align 8 dereferenceable(24)) local_unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE9push_backEc(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 noundef %1) local_unnamed_addr #6 align 2 {
  %3 = load i8, ptr %0, align 8
  %4 = and i8 %3, 1
  %5 = icmp eq i8 %4, 0
  br i1 %5, label %6, label %9

6:                                                ; preds = %2
  %7 = lshr i8 %3, 1
  %8 = icmp eq i8 %7, 22
  br i1 %8, label %16, label %18

9:                                                ; preds = %2
  %10 = load i64, ptr %0, align 8
  %11 = and i64 %10, -2
  %12 = add i64 %11, -1
  %13 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %14 = load i64, ptr %13, align 8, !tbaa !38
  %15 = icmp eq i64 %14, %12
  br i1 %15, label %16, label %23

16:                                               ; preds = %9, %6
  %17 = phi i64 [ %12, %9 ], [ 22, %6 ]
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE9__grow_byEmmmmmm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %17, i64 noundef 1, i64 noundef %17, i64 noundef %17, i64 noundef 0, i64 noundef 0) #35
  br label %23

18:                                               ; preds = %6
  %19 = zext i8 %7 to i64
  %20 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %21 = and i8 %3, -2
  %22 = add i8 %21, 2
  store i8 %22, ptr %0, align 8
  br label %29

23:                                               ; preds = %16, %9
  %24 = phi i64 [ %17, %16 ], [ %14, %9 ]
  %25 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %26 = load ptr, ptr %25, align 8, !tbaa !38
  %27 = add i64 %24, 1
  %28 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %27, ptr %28, align 8, !tbaa !38
  br label %29

29:                                               ; preds = %23, %18
  %30 = phi i64 [ %19, %18 ], [ %24, %23 ]
  %31 = phi ptr [ %20, %18 ], [ %26, %23 ]
  %32 = getelementptr inbounds i8, ptr %31, i64 %30
  store i8 %1, ptr %32, align 1, !tbaa !38
  %33 = getelementptr inbounds i8, ptr %32, i64 1
  store i8 0, ptr %33, align 1, !tbaa !38
  ret void
}

; Function Attrs: minsize optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE9__grow_byEmmmmmm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1, i64 noundef %2, i64 noundef %3, i64 noundef %4, i64 noundef %5, i64 noundef %6) local_unnamed_addr #1 align 2 personality ptr @__gxx_personality_v0 {
  %8 = sub i64 -17, %1
  %9 = icmp ult i64 %8, %2
  br i1 %9, label %10, label %11

10:                                               ; preds = %7
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
  unreachable

11:                                               ; preds = %7
  %12 = load i8, ptr %0, align 8
  %13 = and i8 %12, 1
  %14 = icmp eq i8 %13, 0
  %15 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %16 = load ptr, ptr %15, align 8
  %17 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %18 = select i1 %14, ptr %17, ptr %16
  %19 = icmp ult i64 %1, 9223372036854775783
  br i1 %19, label %20, label %28

20:                                               ; preds = %11
  %21 = add i64 %2, %1
  %22 = shl nuw i64 %1, 1
  %23 = tail call i64 @llvm.umax.i64(i64 %21, i64 %22)
  %24 = or i64 %23, 15
  %25 = add i64 %24, 1
  %26 = icmp ult i64 %23, 23
  %27 = select i1 %26, i64 23, i64 %25
  br label %28

28:                                               ; preds = %20, %11
  %29 = phi i64 [ %27, %20 ], [ -17, %11 ]
  %30 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %29) #35
  %31 = extractvalue [2 x i64] %30, 0
  %32 = inttoptr i64 %31 to ptr
  %33 = icmp eq i64 %4, 0
  br i1 %33, label %39, label %34

34:                                               ; preds = %28
  %35 = icmp ult ptr %18, %32
  %36 = getelementptr inbounds i8, ptr %32, i64 %4
  %37 = icmp ule ptr %36, %18
  %38 = select i1 %35, i1 true, i1 %37
  tail call void @llvm.assume(i1 %38)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %32, ptr align 1 %18, i64 %4, i1 false)
  br label %39

39:                                               ; preds = %34, %28
  %40 = add i64 %5, %4
  %41 = icmp eq i64 %40, %3
  br i1 %41, label %52, label %42

42:                                               ; preds = %39
  %43 = sub i64 %3, %40
  %44 = getelementptr inbounds i8, ptr %32, i64 %4
  %45 = getelementptr inbounds i8, ptr %44, i64 %6
  %46 = getelementptr inbounds i8, ptr %18, i64 %4
  %47 = getelementptr inbounds i8, ptr %46, i64 %5
  %48 = icmp ult ptr %47, %45
  %49 = getelementptr inbounds i8, ptr %45, i64 %43
  %50 = icmp ule ptr %49, %47
  %51 = select i1 %48, i1 true, i1 %50
  tail call void @llvm.assume(i1 %51)
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %45, ptr align 1 %47, i64 %43, i1 false)
  br label %52

52:                                               ; preds = %42, %39
  %53 = icmp eq i64 %1, 22
  br i1 %53, label %55, label %54

54:                                               ; preds = %52
  tail call void @_ZdlPv(ptr noundef %18) #36
  br label %55

55:                                               ; preds = %54, %52
  %56 = extractvalue [2 x i64] %30, 1
  store ptr %32, ptr %15, align 8, !tbaa !38
  %57 = or i64 %56, 1
  store i64 %57, ptr %0, align 8
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000ERKS5_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #6 comdat align 2 {
  %3 = load i8, ptr %1, align 8
  %4 = and i8 %3, 1
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 2
  %7 = load ptr, ptr %6, align 8
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %1, i64 0, i32 2
  %9 = select i1 %5, ptr %8, ptr %7
  %10 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 1
  %11 = load i64, ptr %10, align 8
  %12 = lshr i8 %3, 1
  %13 = zext i8 %12 to i64
  %14 = select i1 %5, i64 %13, i64 %11
  %15 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %9, i64 noundef %14) #35
  ret ptr %15
}

; Function Attrs: inlinehint minsize mustprogress nofree nounwind optsize uwtable
define internal void @_ZL8snprintfPcU17pass_object_size1mPKcz(ptr nocapture noundef %0, i64 %1, i64 %2, ptr nocapture readnone %3, ...) unnamed_addr #25 {
  %5 = alloca %"struct.std::__va_list", align 8
  %6 = alloca %"struct.std::__va_list", align 8
  call void @llvm.lifetime.start.p0(i64 32, ptr nonnull %5) #37
  call void @llvm.va_start(ptr nonnull %5)
  call void @llvm.memcpy.p0.p0.i64(ptr noundef nonnull align 8 dereferenceable(32) %6, ptr noundef nonnull align 8 dereferenceable(32) %5, i64 32, i1 false), !tbaa.struct !136
  %7 = call i32 @vsnprintf(ptr %0, i64 1024, ptr nonnull @.str.24, ptr nonnull %6)
  call void @llvm.va_end(ptr nonnull %5)
  call void @llvm.lifetime.end.p0(i64 32, ptr nonnull %5) #37
  ret void
}

; Function Attrs: nocallback nofree nosync nounwind willreturn
declare void @llvm.va_start(ptr) #26

; Function Attrs: nocallback nofree nosync nounwind willreturn
declare void @llvm.va_end(ptr) #26

; Function Attrs: minsize optsize
declare ptr @__get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW() local_unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA15_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(15) %1) local_unnamed_addr #6 comdat align 2 {
  %3 = tail call noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA15_cPccLb1EE11__range_endB7v170000EPKc(ptr noundef nonnull %1) #35
  %4 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000IPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1, ptr noundef %3) #35
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA15_cPccLb1EE11__range_endB7v170000EPKc(ptr noundef %0) local_unnamed_addr #19 comdat align 2 {
  br label %2

2:                                                ; preds = %2, %1
  %3 = phi ptr [ %0, %1 ], [ %6, %2 ]
  %4 = load i8, ptr %3, align 1, !tbaa !38
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds i8, ptr %3, i64 1
  br i1 %5, label %7, label %2, !llvm.loop !137

7:                                                ; preds = %2
  ret ptr %3
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000IPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, ptr noundef %2) local_unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = alloca %"class.std::__ndk1::basic_string", align 8
  %5 = load i8, ptr %0, align 8
  %6 = and i8 %5, 1
  %7 = icmp eq i8 %6, 0
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %9 = load i64, ptr %8, align 8
  %10 = lshr i8 %5, 1
  %11 = zext i8 %10 to i64
  %12 = select i1 %7, i64 %11, i64 %9
  %13 = load i64, ptr %0, align 8
  %14 = and i64 %13, -2
  %15 = add i64 %14, -1
  %16 = select i1 %7, i64 22, i64 %15
  %17 = ptrtoint ptr %2 to i64
  %18 = ptrtoint ptr %1 to i64
  %19 = sub i64 %17, %18
  %20 = icmp eq ptr %2, %1
  br i1 %20, label %73, label %21

21:                                               ; preds = %3
  %22 = tail call noundef i1 @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE15__addr_in_rangeB7v170000IRKcEEbOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(1) %1) #35
  br i1 %22, label %56, label %23

23:                                               ; preds = %21
  %24 = sub i64 %16, %12
  %25 = icmp ult i64 %24, %19
  br i1 %25, label %26, label %29

26:                                               ; preds = %23
  %27 = add i64 %12, %19
  %28 = sub i64 %27, %16
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE9__grow_byEmmmmmm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %16, i64 noundef %28, i64 noundef %12, i64 noundef %12, i64 noundef 0, i64 noundef 0) #35
  br label %29

29:                                               ; preds = %26, %23
  %30 = load i8, ptr %0, align 8
  %31 = and i8 %30, 1
  %32 = icmp eq i8 %31, 0
  %33 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %34 = load ptr, ptr %33, align 8
  %35 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %36 = select i1 %32, ptr %35, ptr %34
  %37 = getelementptr inbounds i8, ptr %36, i64 %12
  br label %38

38:                                               ; preds = %42, %29
  %39 = phi ptr [ %37, %29 ], [ %44, %42 ]
  %40 = phi ptr [ %1, %29 ], [ %45, %42 ]
  %41 = icmp eq ptr %40, %2
  br i1 %41, label %46, label %42

42:                                               ; preds = %38
  %43 = load i8, ptr %40, align 1, !tbaa !38
  store i8 %43, ptr %39, align 1, !tbaa !38
  %44 = getelementptr inbounds i8, ptr %39, i64 1
  %45 = getelementptr inbounds i8, ptr %40, i64 1
  br label %38, !llvm.loop !138

46:                                               ; preds = %38
  store i8 0, ptr %39, align 1, !tbaa !38
  %47 = add i64 %12, %19
  %48 = load i8, ptr %0, align 8
  %49 = and i8 %48, 1
  %50 = icmp eq i8 %49, 0
  br i1 %50, label %52, label %51

51:                                               ; preds = %46
  store i64 %47, ptr %8, align 8, !tbaa !38
  br label %73

52:                                               ; preds = %46
  %53 = icmp ult i64 %47, 23
  tail call void @llvm.assume(i1 %53)
  %54 = trunc i64 %47 to i8
  %55 = shl nuw nsw i8 %54, 1
  store i8 %55, ptr %0, align 8
  br label %73

56:                                               ; preds = %21
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %4) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initIPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueEvE4typeESA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %4, ptr noundef nonnull %1, ptr noundef %2) #35
  %57 = load i8, ptr %4, align 8
  %58 = and i8 %57, 1
  %59 = icmp eq i8 %58, 0
  %60 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %4, i64 0, i32 2
  %61 = load ptr, ptr %60, align 8
  %62 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %4, i64 0, i32 2
  %63 = select i1 %59, ptr %62, ptr %61
  %64 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %4, i64 0, i32 1
  %65 = load i64, ptr %64, align 8
  %66 = lshr i8 %57, 1
  %67 = zext i8 %66 to i64
  %68 = select i1 %59, i64 %67, i64 %65
  %69 = invoke noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %63, i64 noundef %68) #35
          to label %70 unwind label %71

70:                                               ; preds = %56
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  br label %73

71:                                               ; preds = %56
  %72 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  resume { ptr, i32 } %72

73:                                               ; preds = %70, %52, %51, %3
  ret ptr %0
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE15__addr_in_rangeB7v170000IRKcEEbOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(1) %1) local_unnamed_addr #19 comdat align 2 {
  %3 = load i8, ptr %0, align 8
  %4 = and i8 %3, 1
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %7 = load ptr, ptr %6, align 8
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %9 = select i1 %5, ptr %8, ptr %7
  %10 = icmp ule ptr %9, %1
  %11 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %12 = load i64, ptr %11, align 8
  %13 = lshr i8 %3, 1
  %14 = zext i8 %13 to i64
  %15 = select i1 %5, i64 %14, i64 %12
  %16 = getelementptr inbounds i8, ptr %9, i64 %15
  %17 = icmp uge ptr %16, %1
  %18 = select i1 %10, i1 %17, i1 false
  ret i1 %18
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initIPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueEvE4typeESA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, ptr noundef %2) local_unnamed_addr #27 comdat align 2 {
  %4 = ptrtoint ptr %2 to i64
  %5 = ptrtoint ptr %1 to i64
  %6 = sub i64 %4, %5
  %7 = icmp ugt i64 %6, -17
  br i1 %7, label %8, label %9

8:                                                ; preds = %3
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
  unreachable

9:                                                ; preds = %3
  %10 = icmp ult i64 %6, 23
  br i1 %10, label %11, label %15

11:                                               ; preds = %9
  %12 = trunc i64 %6 to i8
  %13 = shl nuw nsw i8 %12, 1
  store i8 %13, ptr %0, align 8
  %14 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  br label %25

15:                                               ; preds = %9
  %16 = or i64 %6, 15
  %17 = add i64 %16, 1
  %18 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %17) #35
  %19 = extractvalue [2 x i64] %18, 0
  %20 = inttoptr i64 %19 to ptr
  %21 = extractvalue [2 x i64] %18, 1
  %22 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  store ptr %20, ptr %22, align 8, !tbaa !38
  %23 = or i64 %21, 1
  store i64 %23, ptr %0, align 8
  %24 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %6, ptr %24, align 8, !tbaa !38
  br label %25

25:                                               ; preds = %15, %11
  %26 = phi ptr [ %14, %11 ], [ %20, %15 ]
  br label %27

27:                                               ; preds = %31, %25
  %28 = phi ptr [ %26, %25 ], [ %34, %31 ]
  %29 = phi ptr [ %1, %25 ], [ %33, %31 ]
  %30 = icmp eq ptr %29, %2
  br i1 %30, label %35, label %31

31:                                               ; preds = %27
  %32 = load i8, ptr %29, align 1, !tbaa !38
  store i8 %32, ptr %28, align 1, !tbaa !38
  %33 = getelementptr inbounds i8, ptr %29, i64 1
  %34 = getelementptr inbounds i8, ptr %28, i64 1
  br label %27, !llvm.loop !139

35:                                               ; preds = %27
  store i8 0, ptr %28, align 1, !tbaa !38
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNSt6__ndk14__fs10filesystem6existsB7v170000ERKNS1_4pathERNS_10error_codeE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(16) %1) local_unnamed_addr #2 comdat personality ptr @__gxx_personality_v0 {
  %3 = alloca %"class.std::__ndk1::__fs::filesystem::file_status", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %3) #37
  invoke void @_ZNSt6__ndk14__fs10filesystem8__statusERKNS1_4pathEPNS_10error_codeE(ptr nonnull sret(%"class.std::__ndk1::__fs::filesystem::file_status") align 4 %3, ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1) #35
          to label %4 unwind label %16

4:                                                ; preds = %2
  %5 = load i64, ptr %3, align 8
  %6 = and i64 %5, 255
  %7 = icmp eq i64 %6, 0
  br i1 %7, label %11, label %8

8:                                                ; preds = %4
  call void @_ZNSt6__ndk110error_code5clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(16) %1) #34
  %9 = load i64, ptr %3, align 8
  %10 = and i64 %9, 255
  br label %11

11:                                               ; preds = %8, %4
  %12 = phi i64 [ %10, %8 ], [ 0, %4 ]
  %13 = icmp ne i64 %12, 0
  %14 = icmp ne i64 %12, 255
  %15 = and i1 %13, %14
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #37
  ret i1 %15

16:                                               ; preds = %2
  %17 = landingpad { ptr, i32 }
          catch ptr null
  %18 = extractvalue { ptr, i32 } %17, 0
  call void @__clang_call_terminate(ptr %18) #38
  unreachable
}

; Function Attrs: minsize nounwind optsize
declare noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk115system_categoryEv() local_unnamed_addr #15

; Function Attrs: minsize optsize
declare void @_ZNSt6__ndk14__fs10filesystem8__statusERKNS1_4pathEPNS_10error_codeE(ptr sret(%"class.std::__ndk1::__fs::filesystem::file_status") align 4, ptr noundef nonnull align 8 dereferenceable(24), ptr noundef) local_unnamed_addr #7

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110error_code5clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(16) %0) local_unnamed_addr #19 comdat align 2 {
  store i32 0, ptr %0, align 8, !tbaa !85
  %2 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk115system_categoryEv() #34
  %3 = getelementptr inbounds %"class.std::__ndk1::error_code", ptr %0, i64 0, i32 1
  store ptr %2, ptr %3, align 8, !tbaa !87
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000INS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEEEvRSA_RKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(24) %1) local_unnamed_addr #6 comdat align 2 {
  %3 = load i8, ptr %1, align 8
  %4 = and i8 %3, 1
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %1, i64 0, i32 2
  %7 = load ptr, ptr %6, align 8
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %1, i64 0, i32 2
  %9 = select i1 %5, ptr %8, ptr %7
  %10 = tail call noundef ptr @_ZNSt6__ndk14__fs10filesystem20__is_pathable_stringINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEvE11__range_endB7v170000ERKS8_(ptr noundef nonnull align 8 dereferenceable(24) %1) #35
  %11 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000IPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %9, ptr noundef %10) #35
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk14__fs10filesystem20__is_pathable_stringINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEEvE11__range_endB7v170000ERKS8_(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #19 comdat align 2 {
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
  %14 = getelementptr inbounds i8, ptr %8, i64 %13
  ret ptr %14
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignINS_11__wrap_iterIPhEEEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESB_SB_(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %1, i64 %2) local_unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = alloca %"class.std::__ndk1::basic_string", align 8
  %5 = inttoptr i64 %1 to ptr
  %6 = inttoptr i64 %2 to ptr
  %7 = load i8, ptr %0, align 8
  %8 = and i8 %7, 1
  %9 = icmp eq i8 %8, 0
  %10 = load i64, ptr %0, align 8
  %11 = and i64 %10, -2
  %12 = add i64 %11, -1
  %13 = select i1 %9, i64 22, i64 %12
  %14 = sub i64 %2, %1
  %15 = icmp ugt i64 %14, %13
  %16 = trunc i64 %10 to i8
  br i1 %15, label %17, label %30

17:                                               ; preds = %3
  %18 = tail call noundef i1 @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE15__addr_in_rangeB7v170000IRhEEbOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(1) %5) #35
  br i1 %18, label %56, label %19

19:                                               ; preds = %17
  %20 = load i8, ptr %0, align 8
  %21 = and i8 %20, 1
  %22 = icmp eq i8 %21, 0
  %23 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %24 = load i64, ptr %23, align 8
  %25 = lshr i8 %20, 1
  %26 = zext i8 %25 to i64
  %27 = select i1 %22, i64 %26, i64 %24
  %28 = sub i64 %14, %13
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE9__grow_byEmmmmmm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %13, i64 noundef %28, i64 noundef %27, i64 noundef 0, i64 noundef %27, i64 noundef 0) #35
  %29 = load i8, ptr %0, align 8
  br label %30

30:                                               ; preds = %19, %3
  %31 = phi i8 [ %16, %3 ], [ %29, %19 ]
  %32 = and i8 %31, 1
  %33 = icmp eq i8 %32, 0
  %34 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %35 = load ptr, ptr %34, align 8
  %36 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %37 = select i1 %33, ptr %36, ptr %35
  br label %38

38:                                               ; preds = %42, %30
  %39 = phi ptr [ %5, %30 ], [ %45, %42 ]
  %40 = phi ptr [ %37, %30 ], [ %44, %42 ]
  %41 = icmp eq ptr %39, %6
  br i1 %41, label %46, label %42

42:                                               ; preds = %38
  %43 = load i8, ptr %39, align 1, !tbaa !38
  store i8 %43, ptr %40, align 1, !tbaa !38
  %44 = getelementptr inbounds i8, ptr %40, i64 1
  %45 = getelementptr inbounds i8, ptr %39, i64 1
  br label %38, !llvm.loop !140

46:                                               ; preds = %38
  store i8 0, ptr %40, align 1, !tbaa !38
  %47 = load i8, ptr %0, align 8
  %48 = and i8 %47, 1
  %49 = icmp eq i8 %48, 0
  br i1 %49, label %52, label %50

50:                                               ; preds = %46
  %51 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %14, ptr %51, align 8, !tbaa !38
  br label %73

52:                                               ; preds = %46
  %53 = icmp ult i64 %14, 23
  tail call void @llvm.assume(i1 %53)
  %54 = trunc i64 %14 to i8
  %55 = shl nuw nsw i8 %54, 1
  store i8 %55, ptr %0, align 8
  br label %73

56:                                               ; preds = %17
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %4) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initINS_11__wrap_iterIPhEEEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueEvE4typeESB_SB_(ptr noundef nonnull align 8 dereferenceable(24) %4, i64 %1, i64 %2) #35
  %57 = load i8, ptr %4, align 8
  %58 = and i8 %57, 1
  %59 = icmp eq i8 %58, 0
  %60 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %4, i64 0, i32 2
  %61 = load ptr, ptr %60, align 8
  %62 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %4, i64 0, i32 2
  %63 = select i1 %59, ptr %62, ptr %61
  %64 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %4, i64 0, i32 1
  %65 = load i64, ptr %64, align 8
  %66 = lshr i8 %57, 1
  %67 = zext i8 %66 to i64
  %68 = select i1 %59, i64 %67, i64 %65
  %69 = invoke noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %63, i64 noundef %68) #35
          to label %70 unwind label %71

70:                                               ; preds = %56
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  br label %73

71:                                               ; preds = %56
  %72 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %4) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %4) #37
  resume { ptr, i32 } %72

73:                                               ; preds = %70, %52, %50
  ret ptr %0
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::vector<unsigned char>::__destroy_vector", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #37
  store ptr %0, ptr %2, align 8, !tbaa !42
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %2) #35
          to label %3 unwind label %4

3:                                                ; preds = %1
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #37
  ret void

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          catch ptr null
  %6 = extractvalue { ptr, i32 } %5, 0
  call void @__clang_call_terminate(ptr %6) #38
  unreachable
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEC1EPKcj(ptr noundef nonnull align 8 dereferenceable(184) %0, ptr noundef %1, i32 noundef %2) unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = getelementptr inbounds i8, ptr %0, i64 184
  store ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE, i64 0, inrange i32 0, i64 3), ptr %0, align 8, !tbaa !93
  store ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE, i64 0, inrange i32 1, i64 3), ptr %4, align 8, !tbaa !93
  %5 = getelementptr inbounds %"class.std::__ndk1::basic_ifstream", ptr %0, i64 0, i32 1
  invoke void @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEEC2B7v170000EPNS_15basic_streambufIcS2_EE(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef nonnull getelementptr inbounds ([4 x ptr], ptr @_ZTTNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE, i64 0, i64 1), ptr noundef nonnull %5) #35
          to label %6 unwind label %17

6:                                                ; preds = %3
  store ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE, i64 0, inrange i32 0, i64 3), ptr %0, align 8, !tbaa !93
  store ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE, i64 0, inrange i32 1, i64 3), ptr %4, align 8, !tbaa !93
  invoke void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEC2Ev(ptr noundef nonnull align 8 dereferenceable(163) %5) #35
          to label %7 unwind label %19

7:                                                ; preds = %6
  %8 = or i32 %2, 8
  %9 = invoke noundef ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE4openEPKcj(ptr noundef nonnull align 8 dereferenceable(163) %5, ptr noundef %1, i32 noundef %8) #35
          to label %10 unwind label %21

10:                                               ; preds = %7
  %11 = icmp eq ptr %9, null
  br i1 %11, label %12, label %23

12:                                               ; preds = %10
  %13 = load ptr, ptr %0, align 8, !tbaa !93
  %14 = getelementptr i8, ptr %13, i64 -24
  %15 = load i64, ptr %14, align 8
  %16 = getelementptr inbounds i8, ptr %0, i64 %15
  invoke void @_ZNSt6__ndk18ios_base8setstateB7v170000Ej(ptr noundef nonnull align 8 dereferenceable(136) %16, i32 noundef 4) #35
          to label %23 unwind label %21

17:                                               ; preds = %3
  %18 = landingpad { ptr, i32 }
          cleanup
  br label %26

19:                                               ; preds = %6
  %20 = landingpad { ptr, i32 }
          cleanup
  br label %24

21:                                               ; preds = %12, %7
  %22 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(163) %5) #34
  br label %24

23:                                               ; preds = %12, %10
  ret void

24:                                               ; preds = %21, %19
  %25 = phi { ptr, i32 } [ %22, %21 ], [ %20, %19 ]
  tail call void @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef nonnull getelementptr inbounds ([4 x ptr], ptr @_ZTTNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE, i64 0, i64 1)) #34
  br label %26

26:                                               ; preds = %24, %17
  %27 = phi { ptr, i32 } [ %25, %24 ], [ %18, %17 ]
  tail call void @_ZNSt6__ndk19basic_iosIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(148) %4) #34
  resume { ptr, i32 } %27
}

; Function Attrs: minsize optsize
declare noundef nonnull align 8 dereferenceable(16) ptr @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEE5seekgExNS_8ios_base7seekdirE(ptr noundef nonnull align 8 dereferenceable(16), i64 noundef, i32 noundef) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare [2 x i64] @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEE5tellgEv(ptr noundef nonnull align 8 dereferenceable(16)) local_unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE6resizeEm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector.13", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !131
  %5 = load ptr, ptr %0, align 8, !tbaa !127
  %6 = ptrtoint ptr %4 to i64
  %7 = ptrtoint ptr %5 to i64
  %8 = sub i64 %6, %7
  %9 = icmp ult i64 %8, %1
  br i1 %9, label %10, label %12

10:                                               ; preds = %2
  %11 = sub i64 %1, %8
  tail call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE8__appendEm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %11) #35
  br label %16

12:                                               ; preds = %2
  %13 = icmp ugt i64 %8, %1
  br i1 %13, label %14, label %16

14:                                               ; preds = %12
  %15 = getelementptr inbounds i8, ptr %5, i64 %1
  store ptr %15, ptr %3, align 8, !tbaa !131
  br label %16

16:                                               ; preds = %14, %12, %10
  ret void
}

; Function Attrs: minsize optsize
declare noundef nonnull align 8 dereferenceable(16) ptr @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEE4readEPcl(ptr noundef nonnull align 8 dereferenceable(16), ptr noundef, i64 noundef) local_unnamed_addr #7

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(184) %0) unnamed_addr #2 comdat align 2 {
  tail call void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(184) %0, ptr noundef nonnull @_ZTTNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE) #34
  %2 = getelementptr inbounds i8, ptr %0, i64 184
  tail call void @_ZNSt6__ndk19basic_iosIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(148) %2) #34
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEEC2B7v170000EPNS_15basic_streambufIcS2_EE(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %1, ptr noundef %2) unnamed_addr #3 comdat align 2 {
  %4 = load ptr, ptr %1, align 8
  store ptr %4, ptr %0, align 8, !tbaa !93
  %5 = getelementptr inbounds ptr, ptr %1, i64 1
  %6 = load ptr, ptr %5, align 8
  %7 = getelementptr i8, ptr %4, i64 -24
  %8 = load i64, ptr %7, align 8
  %9 = getelementptr inbounds i8, ptr %0, i64 %8
  store ptr %6, ptr %9, align 8, !tbaa !93
  %10 = getelementptr inbounds %"class.std::__ndk1::basic_istream", ptr %0, i64 0, i32 1
  store i64 0, ptr %10, align 8, !tbaa !141
  %11 = load ptr, ptr %0, align 8, !tbaa !93
  %12 = getelementptr i8, ptr %11, i64 -24
  %13 = load i64, ptr %12, align 8
  %14 = getelementptr inbounds i8, ptr %0, i64 %13
  tail call void @_ZNSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4initB7v170000EPNS_15basic_streambufIcS2_EE(ptr noundef nonnull align 8 dereferenceable(148) %14, ptr noundef %2) #35
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEC2Ev(ptr noundef nonnull align 8 dereferenceable(163) %0) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::locale", align 8
  %3 = alloca %"class.std::__ndk1::locale", align 8
  tail call void @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEC2Ev(ptr noundef nonnull align 8 dereferenceable(64) %0) #35
  store ptr getelementptr inbounds ({ [16 x ptr] }, ptr @_ZTVNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEE, i64 0, inrange i32 0, i64 2), ptr %0, align 8, !tbaa !93
  %4 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %5 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  %6 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 9
  %7 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 16
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %4, i8 0, i64 24, i1 false)
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(67) %5, i8 0, i64 67, i1 false)
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #37
  %8 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 1
  call void @_ZNSt6__ndk16localeC1ERKS0_(ptr noundef nonnull align 8 dereferenceable(8) %2, ptr noundef nonnull align 8 dereferenceable(8) %8) #34
  %9 = call noundef i1 @_ZNSt6__ndk19has_facetB7v170000INS_7codecvtIcc9mbstate_tEEEEbRKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(8) %2) #34
  call void @_ZNSt6__ndk16localeD1Ev(ptr noundef nonnull align 8 dereferenceable(8) %2) #34
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #37
  br i1 %9, label %10, label %21

10:                                               ; preds = %1
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %3) #37
  call void @_ZNSt6__ndk16localeC1ERKS0_(ptr noundef nonnull align 8 dereferenceable(8) %3, ptr noundef nonnull align 8 dereferenceable(8) %8) #34
  %11 = invoke noundef nonnull align 8 dereferenceable(16) ptr @_ZNSt6__ndk19use_facetB7v170000INS_7codecvtIcc9mbstate_tEEEERKT_RKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(8) %3) #35
          to label %12 unwind label %19

12:                                               ; preds = %10
  store ptr %11, ptr %6, align 8, !tbaa !143
  call void @_ZNSt6__ndk16localeD1Ev(ptr noundef nonnull align 8 dereferenceable(8) %3) #34
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #37
  %13 = load ptr, ptr %6, align 8, !tbaa !143
  %14 = load ptr, ptr %13, align 8, !tbaa !93
  %15 = getelementptr inbounds ptr, ptr %14, i64 7
  %16 = load ptr, ptr %15, align 8
  %17 = call noundef i1 %16(ptr noundef nonnull align 8 dereferenceable(16) %13) #34
  %18 = zext i1 %17 to i8
  store i8 %18, ptr %7, align 2, !tbaa !148
  br label %21

19:                                               ; preds = %10
  %20 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk16localeD1Ev(ptr noundef nonnull align 8 dereferenceable(8) %3) #34
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #37
  br label %29

21:                                               ; preds = %12, %1
  %22 = load ptr, ptr %0, align 8, !tbaa !93
  %23 = getelementptr inbounds ptr, ptr %22, i64 3
  %24 = load ptr, ptr %23, align 8
  %25 = invoke noundef ptr %24(ptr noundef nonnull align 8 dereferenceable(163) %0, ptr noundef null, i64 noundef 4096) #35
          to label %26 unwind label %27

26:                                               ; preds = %21
  ret void

27:                                               ; preds = %21
  %28 = landingpad { ptr, i32 }
          cleanup
  br label %29

29:                                               ; preds = %27, %19
  %30 = phi { ptr, i32 } [ %28, %27 ], [ %20, %19 ]
  call void @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(64) %0) #34
  resume { ptr, i32 } %30
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE4openEPKcj(ptr noundef nonnull align 8 dereferenceable(163) %0, ptr noundef %1, i32 noundef %2) local_unnamed_addr #6 comdat align 2 {
  %4 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 8
  %5 = load ptr, ptr %4, align 8, !tbaa !149
  %6 = icmp eq ptr %5, null
  br i1 %6, label %7, label %23

7:                                                ; preds = %3
  %8 = tail call noundef ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE15__make_mdstringEj(i32 noundef %2) #34
  %9 = icmp eq ptr %8, null
  br i1 %9, label %23, label %10

10:                                               ; preds = %7
  %11 = tail call ptr @fopen(ptr noundef %1, ptr noundef nonnull %8) #35
  store ptr %11, ptr %4, align 8, !tbaa !149
  %12 = icmp eq ptr %11, null
  br i1 %12, label %23, label %13

13:                                               ; preds = %10
  %14 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 12
  store i32 %2, ptr %14, align 8, !tbaa !150
  %15 = and i32 %2, 2
  %16 = icmp eq i32 %15, 0
  br i1 %16, label %23, label %17

17:                                               ; preds = %13
  %18 = tail call i32 @fseek(ptr noundef nonnull %11, i64 noundef 0, i32 noundef 2) #35
  %19 = icmp eq i32 %18, 0
  br i1 %19, label %23, label %20

20:                                               ; preds = %17
  %21 = load ptr, ptr %4, align 8, !tbaa !149
  %22 = tail call i32 @fclose(ptr noundef %21) #35
  store ptr null, ptr %4, align 8, !tbaa !149
  br label %23

23:                                               ; preds = %20, %17, %13, %10, %7, %3
  %24 = phi ptr [ null, %3 ], [ null, %20 ], [ %0, %17 ], [ %0, %13 ], [ null, %7 ], [ null, %10 ]
  ret ptr %24
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(163) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  store ptr getelementptr inbounds ({ [16 x ptr] }, ptr @_ZTVNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEE, i64 0, inrange i32 0, i64 2), ptr %0, align 8, !tbaa !93
  %2 = invoke noundef ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE5closeEv(ptr noundef nonnull align 8 dereferenceable(163) %0) #35
          to label %7 unwind label %3

3:                                                ; preds = %1
  %4 = landingpad { ptr, i32 }
          catch ptr null
  %5 = extractvalue { ptr, i32 } %4, 0
  %6 = tail call ptr @__cxa_begin_catch(ptr %5) #37
  invoke void @__cxa_end_catch()
          to label %7 unwind label %26

7:                                                ; preds = %3, %1
  %8 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 14
  %9 = load i8, ptr %8, align 8, !tbaa !151, !range !100, !noundef !101
  %10 = icmp eq i8 %9, 0
  br i1 %10, label %16, label %11

11:                                               ; preds = %7
  %12 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %13 = load ptr, ptr %12, align 8, !tbaa !152
  %14 = icmp eq ptr %13, null
  br i1 %14, label %16, label %15

15:                                               ; preds = %11
  tail call void @_ZdaPv(ptr noundef nonnull %13) #36
  br label %16

16:                                               ; preds = %15, %11, %7
  %17 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 15
  %18 = load i8, ptr %17, align 1, !tbaa !153, !range !100, !noundef !101
  %19 = icmp eq i8 %18, 0
  br i1 %19, label %25, label %20

20:                                               ; preds = %16
  %21 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  %22 = load ptr, ptr %21, align 8, !tbaa !154
  %23 = icmp eq ptr %22, null
  br i1 %23, label %25, label %24

24:                                               ; preds = %20
  tail call void @_ZdaPv(ptr noundef nonnull %22) #36
  br label %25

25:                                               ; preds = %24, %20, %16
  tail call void @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(64) %0) #34
  ret void

26:                                               ; preds = %3
  %27 = landingpad { ptr, i32 }
          catch ptr null
  %28 = extractvalue { ptr, i32 } %27, 0
  tail call void @__clang_call_terminate(ptr %28) #38
  unreachable
}

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(16), ptr noundef) unnamed_addr #15

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk19basic_iosIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(148)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED0Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize uwtable
define available_externally void @_ZTv0_n24_NSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED1Ev(ptr noundef %0) unnamed_addr #4 align 2 {
  %2 = load ptr, ptr %0, align 8
  %3 = getelementptr inbounds i8, ptr %2, i64 -24
  %4 = load i64, ptr %3, align 8
  %5 = getelementptr inbounds i8, ptr %0, i64 %4
  tail call void @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(16) %5) #34
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define available_externally void @_ZTv0_n24_NSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED0Ev(ptr noundef %0) unnamed_addr #4 align 2 {
  %2 = load ptr, ptr %0, align 8
  %3 = getelementptr inbounds i8, ptr %2, i64 -24
  %4 = load i64, ptr %3, align 8
  %5 = getelementptr inbounds i8, ptr %0, i64 %4
  tail call void @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED0Ev(ptr noundef nonnull align 8 dereferenceable(16) %5) #34
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED0Ev(ptr noundef nonnull align 8 dereferenceable(184) %0) unnamed_addr #2 comdat align 2 {
  tail call void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(184) %0) #34
  tail call void @_ZdlPv(ptr noundef nonnull %0) #36
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZTv0_n24_NSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef %0) unnamed_addr #2 comdat align 2 {
  %2 = load ptr, ptr %0, align 8
  %3 = getelementptr inbounds i8, ptr %2, i64 -24
  %4 = load i64, ptr %3, align 8
  %5 = getelementptr inbounds i8, ptr %0, i64 %4
  tail call void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(184) %5) #34
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZTv0_n24_NSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED0Ev(ptr noundef %0) unnamed_addr #2 comdat align 2 {
  %2 = load ptr, ptr %0, align 8
  %3 = getelementptr inbounds i8, ptr %2, i64 -24
  %4 = load i64, ptr %3, align 8
  %5 = getelementptr inbounds i8, ptr %0, i64 %4
  tail call void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED0Ev(ptr noundef nonnull align 8 dereferenceable(184) %5) #34
  ret void
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4initB7v170000EPNS_15basic_streambufIcS2_EE(ptr noundef nonnull align 8 dereferenceable(148) %0, ptr noundef %1) local_unnamed_addr #27 comdat align 2 {
  tail call void @_ZNSt6__ndk18ios_base4initEPv(ptr noundef nonnull align 8 dereferenceable(136) %0, ptr noundef %1) #35
  %3 = getelementptr inbounds %"class.std::__ndk1::basic_ios", ptr %0, i64 0, i32 1
  store ptr null, ptr %3, align 8, !tbaa !155
  %4 = getelementptr inbounds %"class.std::__ndk1::basic_ios", ptr %0, i64 0, i32 2
  store i32 -1, ptr %4, align 8, !tbaa !157
  ret void
}

; Function Attrs: minsize optsize
declare void @_ZNSt6__ndk18ios_base4initEPv(ptr noundef nonnull align 8 dereferenceable(136), ptr noundef) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare void @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEC2Ev(ptr noundef nonnull align 8 dereferenceable(64)) unnamed_addr #7

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNSt6__ndk19has_facetB7v170000INS_7codecvtIcc9mbstate_tEEEEbRKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #24 comdat personality ptr @__gxx_personality_v0 {
  %2 = invoke noundef i1 @_ZNKSt6__ndk16locale9has_facetERNS0_2idE(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef nonnull align 8 dereferenceable(12) @_ZNSt6__ndk17codecvtIcc9mbstate_tE2idE) #35
          to label %3 unwind label %4

3:                                                ; preds = %1
  ret i1 %2

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          catch ptr null
  %6 = extractvalue { ptr, i32 } %5, 0
  tail call void @__clang_call_terminate(ptr %6) #38
  unreachable
}

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk16localeD1Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #15

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(16) ptr @_ZNSt6__ndk19use_facetB7v170000INS_7codecvtIcc9mbstate_tEEEERKT_RKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #27 comdat {
  %2 = tail call noundef ptr @_ZNKSt6__ndk16locale9use_facetERNS0_2idE(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef nonnull align 8 dereferenceable(12) @_ZNSt6__ndk17codecvtIcc9mbstate_tE2idE) #35
  ret ptr %2
}

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(64)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED0Ev(ptr noundef nonnull align 8 dereferenceable(163) %0) unnamed_addr #4 comdat align 2 {
  tail call void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(163) %0) #34
  tail call void @_ZdlPv(ptr noundef nonnull %0) #36
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE5imbueERKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(163) %0, ptr noundef nonnull align 8 dereferenceable(8) %1) unnamed_addr #6 comdat align 2 {
  %3 = load ptr, ptr %0, align 8, !tbaa !93
  %4 = getelementptr inbounds ptr, ptr %3, i64 6
  %5 = load ptr, ptr %4, align 8
  %6 = tail call noundef i32 %5(ptr noundef nonnull align 8 dereferenceable(163) %0) #35
  %7 = tail call noundef nonnull align 8 dereferenceable(16) ptr @_ZNSt6__ndk19use_facetB7v170000INS_7codecvtIcc9mbstate_tEEEERKT_RKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(8) %1) #35
  %8 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 9
  store ptr %7, ptr %8, align 8, !tbaa !143
  %9 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 16
  %10 = load i8, ptr %9, align 2, !tbaa !148, !range !100, !noundef !101
  %11 = load ptr, ptr %7, align 8, !tbaa !93
  %12 = getelementptr inbounds ptr, ptr %11, i64 7
  %13 = load ptr, ptr %12, align 8
  %14 = tail call noundef i1 %13(ptr noundef nonnull align 8 dereferenceable(16) %7) #34
  %15 = zext i1 %14 to i8
  store i8 %15, ptr %9, align 2, !tbaa !148
  %16 = icmp eq i8 %10, %15
  br i1 %16, label %57, label %17

17:                                               ; preds = %2
  %18 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(48) %18, i8 0, i64 48, i1 false)
  %19 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 14
  %20 = load i8, ptr %19, align 8, !tbaa !151, !range !100
  %21 = icmp eq i8 %20, 0
  br i1 %14, label %22, label %37

22:                                               ; preds = %17
  br i1 %21, label %28, label %23

23:                                               ; preds = %22
  %24 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %25 = load ptr, ptr %24, align 8, !tbaa !152
  %26 = icmp eq ptr %25, null
  br i1 %26, label %28, label %27

27:                                               ; preds = %23
  tail call void @_ZdaPv(ptr noundef nonnull %25) #36
  br label %28

28:                                               ; preds = %27, %23, %22
  %29 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 15
  %30 = load i8, ptr %29, align 1, !tbaa !153, !range !100, !noundef !101
  store i8 %30, ptr %19, align 8, !tbaa !151
  %31 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 7
  %32 = load i64, ptr %31, align 8, !tbaa !158
  %33 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  store i64 %32, ptr %33, align 8, !tbaa !159
  %34 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  %35 = load ptr, ptr %34, align 8, !tbaa !154
  %36 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  store ptr %35, ptr %36, align 8, !tbaa !152
  store i8 0, ptr %29, align 1, !tbaa !153
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %34, i8 0, i64 16, i1 false)
  br label %57

37:                                               ; preds = %17
  br i1 %21, label %38, label %50

38:                                               ; preds = %37
  %39 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %40 = load ptr, ptr %39, align 8, !tbaa !152
  %41 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 4
  %42 = icmp eq ptr %40, %41
  br i1 %42, label %50, label %43

43:                                               ; preds = %38
  %44 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  %45 = load i64, ptr %44, align 8, !tbaa !159
  %46 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 7
  store i64 %45, ptr %46, align 8, !tbaa !158
  %47 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  store ptr %40, ptr %47, align 8, !tbaa !154
  %48 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 15
  store i8 0, ptr %48, align 1, !tbaa !153
  %49 = tail call noalias noundef nonnull ptr @_Znam(i64 noundef %45) #43
  store ptr %49, ptr %39, align 8, !tbaa !152
  store i8 1, ptr %19, align 8, !tbaa !151
  br label %57

50:                                               ; preds = %38, %37
  %51 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  %52 = load i64, ptr %51, align 8, !tbaa !159
  %53 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 7
  store i64 %52, ptr %53, align 8, !tbaa !158
  %54 = tail call noalias noundef nonnull ptr @_Znam(i64 noundef %52) #43
  %55 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  store ptr %54, ptr %55, align 8, !tbaa !154
  %56 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 15
  store i8 1, ptr %56, align 1, !tbaa !153
  br label %57

57:                                               ; preds = %50, %43, %28, %2
  ret void
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE6setbufEPcl(ptr noundef nonnull align 8 dereferenceable(163) %0, ptr noundef %1, i64 noundef %2) unnamed_addr #6 comdat align 2 {
  %4 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  %5 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 14
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(48) %4, i8 0, i64 48, i1 false)
  %6 = load i8, ptr %5, align 8, !tbaa !151, !range !100, !noundef !101
  %7 = icmp eq i8 %6, 0
  br i1 %7, label %13, label %8

8:                                                ; preds = %3
  %9 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %10 = load ptr, ptr %9, align 8, !tbaa !152
  %11 = icmp eq ptr %10, null
  br i1 %11, label %13, label %12

12:                                               ; preds = %8
  tail call void @_ZdaPv(ptr noundef nonnull %10) #36
  br label %13

13:                                               ; preds = %12, %8, %3
  %14 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 15
  %15 = load i8, ptr %14, align 1, !tbaa !153, !range !100, !noundef !101
  %16 = icmp eq i8 %15, 0
  br i1 %16, label %22, label %17

17:                                               ; preds = %13
  %18 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  %19 = load ptr, ptr %18, align 8, !tbaa !154
  %20 = icmp eq ptr %19, null
  br i1 %20, label %22, label %21

21:                                               ; preds = %17
  tail call void @_ZdaPv(ptr noundef nonnull %19) #36
  br label %22

22:                                               ; preds = %21, %17, %13
  %23 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  store i64 %2, ptr %23, align 8, !tbaa !159
  %24 = icmp ugt i64 %2, 8
  br i1 %24, label %25, label %36

25:                                               ; preds = %22
  %26 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 16
  %27 = load i8, ptr %26, align 2, !tbaa !148, !range !100, !noundef !101
  %28 = icmp ne i8 %27, 0
  %29 = icmp ne ptr %1, null
  %30 = and i1 %29, %28
  br i1 %30, label %31, label %33

31:                                               ; preds = %25
  %32 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  store ptr %1, ptr %32, align 8, !tbaa !152
  store i8 0, ptr %5, align 8, !tbaa !151
  br label %53

33:                                               ; preds = %25
  %34 = tail call noalias noundef nonnull ptr @_Znam(i64 noundef %2) #43
  %35 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  store ptr %34, ptr %35, align 8, !tbaa !152
  store i8 1, ptr %5, align 8, !tbaa !151
  br label %41

36:                                               ; preds = %22
  %37 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 4
  %38 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  store ptr %37, ptr %38, align 8, !tbaa !152
  store i64 8, ptr %23, align 8, !tbaa !159
  store i8 0, ptr %5, align 8, !tbaa !151
  %39 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 16
  %40 = load i8, ptr %39, align 2, !tbaa !148, !range !100
  br label %41

41:                                               ; preds = %36, %33
  %42 = phi i8 [ %27, %33 ], [ %40, %36 ]
  %43 = icmp eq i8 %42, 0
  br i1 %43, label %44, label %53

44:                                               ; preds = %41
  %45 = tail call i64 @llvm.smax.i64(i64 %2, i64 8)
  %46 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 7
  store i64 %45, ptr %46, align 8, !tbaa !158
  %47 = icmp eq ptr %1, null
  br i1 %47, label %50, label %48

48:                                               ; preds = %44
  %49 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  store ptr %1, ptr %49, align 8, !tbaa !154
  store i8 0, ptr %14, align 1, !tbaa !153
  br label %55

50:                                               ; preds = %44
  %51 = tail call noalias noundef nonnull ptr @_Znam(i64 noundef %45) #43
  %52 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  store ptr %51, ptr %52, align 8, !tbaa !154
  store i8 1, ptr %14, align 1, !tbaa !153
  br label %55

53:                                               ; preds = %41, %31
  %54 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  store i8 0, ptr %14, align 1, !tbaa !153
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(16) %54, i8 0, i64 16, i1 false)
  br label %55

55:                                               ; preds = %53, %50, %48
  ret ptr %0
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE7seekoffExNS_8ios_base7seekdirEj(ptr noundef nonnull align 8 dereferenceable(163) %0, i64 noundef %1, i32 noundef %2, i32 noundef %3) unnamed_addr #1 comdat align 2 {
  %5 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 9
  %6 = load ptr, ptr %5, align 8, !tbaa !143
  %7 = icmp eq ptr %6, null
  br i1 %7, label %8, label %9

8:                                                ; preds = %4
  tail call void @_ZNSt6__ndk116__throw_bad_castB7v170000Ev() #42
  unreachable

9:                                                ; preds = %4
  %10 = load ptr, ptr %6, align 8, !tbaa !93
  %11 = getelementptr inbounds ptr, ptr %10, i64 6
  %12 = load ptr, ptr %11, align 8
  %13 = tail call noundef i32 %12(ptr noundef nonnull align 8 dereferenceable(16) %6) #34
  %14 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 8
  %15 = load ptr, ptr %14, align 8, !tbaa !149
  %16 = icmp eq ptr %15, null
  br i1 %16, label %42, label %17

17:                                               ; preds = %9
  %18 = icmp slt i32 %13, 1
  %19 = icmp ne i64 %1, 0
  %20 = and i1 %19, %18
  br i1 %20, label %42, label %21

21:                                               ; preds = %17
  %22 = load ptr, ptr %0, align 8, !tbaa !93
  %23 = getelementptr inbounds ptr, ptr %22, i64 6
  %24 = load ptr, ptr %23, align 8
  %25 = tail call noundef i32 %24(ptr noundef nonnull align 8 dereferenceable(163) %0) #35
  %26 = icmp eq i32 %25, 0
  %27 = icmp ult i32 %2, 3
  %28 = and i1 %27, %26
  br i1 %28, label %29, label %42

29:                                               ; preds = %21
  %30 = load ptr, ptr %14, align 8, !tbaa !149
  %31 = icmp sgt i32 %13, 0
  %32 = zext i32 %13 to i64
  %33 = mul nsw i64 %32, %1
  %34 = select i1 %31, i64 %33, i64 0
  %35 = tail call i32 @fseeko(ptr noundef %30, i64 noundef %34, i32 noundef %2) #35
  %36 = icmp eq i32 %35, 0
  br i1 %36, label %37, label %42

37:                                               ; preds = %29
  %38 = load ptr, ptr %14, align 8, !tbaa !149
  %39 = tail call i64 @ftello(ptr noundef %38) #35
  %40 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 10
  %41 = load i64, ptr %40, align 8, !tbaa.struct !160
  br label %42

42:                                               ; preds = %37, %29, %21, %17, %9
  %43 = phi i64 [ %39, %37 ], [ -1, %17 ], [ -1, %21 ], [ -1, %9 ], [ -1, %29 ]
  %44 = phi i64 [ %41, %37 ], [ 0, %17 ], [ 0, %21 ], [ 0, %9 ], [ 0, %29 ]
  %45 = insertvalue [2 x i64] poison, i64 %44, 0
  %46 = insertvalue [2 x i64] %45, i64 %43, 1
  ret [2 x i64] %46
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE7seekposENS_4fposI9mbstate_tEEj(ptr noundef nonnull align 8 dereferenceable(163) %0, [2 x i64] %1, i32 noundef %2) unnamed_addr #1 comdat align 2 {
  %4 = extractvalue [2 x i64] %1, 0
  %5 = extractvalue [2 x i64] %1, 1
  %6 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 8
  %7 = load ptr, ptr %6, align 8, !tbaa !149
  %8 = icmp eq ptr %7, null
  br i1 %8, label %21, label %9

9:                                                ; preds = %3
  %10 = load ptr, ptr %0, align 8, !tbaa !93
  %11 = getelementptr inbounds ptr, ptr %10, i64 6
  %12 = load ptr, ptr %11, align 8
  %13 = tail call noundef i32 %12(ptr noundef nonnull align 8 dereferenceable(163) %0) #35
  %14 = icmp eq i32 %13, 0
  br i1 %14, label %15, label %21

15:                                               ; preds = %9
  %16 = load ptr, ptr %6, align 8, !tbaa !149
  %17 = tail call i32 @fseeko(ptr noundef %16, i64 noundef %5, i32 noundef 0) #35
  %18 = icmp eq i32 %17, 0
  br i1 %18, label %19, label %21

19:                                               ; preds = %15
  %20 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 10
  store i64 %4, ptr %20, align 8, !tbaa.struct !160
  br label %21

21:                                               ; preds = %19, %15, %9, %3
  %22 = phi i64 [ %4, %19 ], [ 0, %9 ], [ 0, %3 ], [ 0, %15 ]
  %23 = phi i64 [ %5, %19 ], [ -1, %9 ], [ -1, %3 ], [ -1, %15 ]
  %24 = insertvalue [2 x i64] poison, i64 %22, 0
  %25 = insertvalue [2 x i64] %24, i64 %23, 1
  ret [2 x i64] %25
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE4syncEv(ptr noundef nonnull align 8 dereferenceable(163) %0) unnamed_addr #6 comdat align 2 {
  %2 = alloca ptr, align 8
  %3 = alloca %struct.mbstate_t, align 8
  %4 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 8
  %5 = load ptr, ptr %4, align 8, !tbaa !149
  %6 = icmp eq ptr %5, null
  br i1 %6, label %142, label %7

7:                                                ; preds = %1
  %8 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 9
  %9 = load ptr, ptr %8, align 8, !tbaa !143
  %10 = icmp eq ptr %9, null
  br i1 %10, label %11, label %12

11:                                               ; preds = %7
  tail call void @_ZNSt6__ndk116__throw_bad_castB7v170000Ev() #42
  unreachable

12:                                               ; preds = %7
  %13 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 13
  %14 = load i32, ptr %13, align 4, !tbaa !161
  %15 = and i32 %14, 16
  %16 = icmp eq i32 %15, 0
  br i1 %16, label %56, label %17

17:                                               ; preds = %12
  %18 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 6
  %19 = load ptr, ptr %18, align 8, !tbaa !162
  %20 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 5
  %21 = load ptr, ptr %20, align 8, !tbaa !163
  %22 = icmp eq ptr %19, %21
  br i1 %22, label %29, label %23

23:                                               ; preds = %17
  %24 = load ptr, ptr %0, align 8, !tbaa !93
  %25 = getelementptr inbounds ptr, ptr %24, i64 13
  %26 = load ptr, ptr %25, align 8
  %27 = tail call noundef i32 %26(ptr noundef nonnull align 8 dereferenceable(163) %0, i32 noundef -1) #35
  %28 = icmp eq i32 %27, -1
  br i1 %28, label %142, label %29

29:                                               ; preds = %23, %17
  %30 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 10
  %31 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %32 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  br label %33

33:                                               ; preds = %50, %29
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #37
  %34 = load ptr, ptr %8, align 8, !tbaa !143
  %35 = load ptr, ptr %31, align 8, !tbaa !152
  %36 = load i64, ptr %32, align 8, !tbaa !159
  %37 = getelementptr inbounds i8, ptr %35, i64 %36
  %38 = load ptr, ptr %34, align 8, !tbaa !93
  %39 = getelementptr inbounds ptr, ptr %38, i64 5
  %40 = load ptr, ptr %39, align 8
  %41 = call noundef i32 %40(ptr noundef nonnull align 8 dereferenceable(16) %34, ptr noundef nonnull align 1 dereferenceable(8) %30, ptr noundef %35, ptr noundef %37, ptr noundef nonnull align 8 dereferenceable(8) %2) #35
  %42 = load ptr, ptr %2, align 8, !tbaa !42
  %43 = load ptr, ptr %31, align 8, !tbaa !152
  %44 = ptrtoint ptr %42 to i64
  %45 = ptrtoint ptr %43 to i64
  %46 = sub i64 %44, %45
  %47 = load ptr, ptr %4, align 8, !tbaa !149
  %48 = call noundef i64 @fwrite(ptr noundef %43, i64 noundef 1, i64 noundef %46, ptr noundef %47) #35
  %49 = icmp eq i64 %48, %46
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #37
  br i1 %49, label %50, label %55

50:                                               ; preds = %33
  switch i32 %41, label %51 [
    i32 1, label %33
    i32 2, label %55
  ]

51:                                               ; preds = %50
  %52 = load ptr, ptr %4, align 8, !tbaa !149
  %53 = call i32 @fflush(ptr noundef %52) #35
  %54 = icmp eq i32 %53, 0
  br i1 %54, label %142, label %55

55:                                               ; preds = %51, %50, %33
  br label %142

56:                                               ; preds = %12
  %57 = and i32 %14, 8
  %58 = icmp eq i32 %57, 0
  br i1 %58, label %142, label %59

59:                                               ; preds = %56
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %3) #37
  %60 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 11
  %61 = load i64, ptr %60, align 8
  store i64 %61, ptr %3, align 8
  %62 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 16
  %63 = load i8, ptr %62, align 2, !tbaa !148, !range !100, !noundef !101
  %64 = icmp eq i8 %63, 0
  br i1 %64, label %73, label %65

65:                                               ; preds = %59
  %66 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 4
  %67 = load ptr, ptr %66, align 8, !tbaa !164
  %68 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 3
  %69 = load ptr, ptr %68, align 8, !tbaa !165
  %70 = ptrtoint ptr %67 to i64
  %71 = ptrtoint ptr %69 to i64
  %72 = sub i64 %70, %71
  br label %124

73:                                               ; preds = %59
  %74 = load ptr, ptr %9, align 8, !tbaa !93
  %75 = getelementptr inbounds ptr, ptr %74, i64 6
  %76 = load ptr, ptr %75, align 8
  %77 = tail call noundef i32 %76(ptr noundef nonnull align 8 dereferenceable(16) %9) #34
  %78 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 3
  %79 = load ptr, ptr %78, align 8, !tbaa !166
  %80 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 2
  %81 = load ptr, ptr %80, align 8, !tbaa !167
  %82 = ptrtoint ptr %79 to i64
  %83 = ptrtoint ptr %81 to i64
  %84 = sub i64 %82, %83
  %85 = icmp sgt i32 %77, 0
  br i1 %85, label %86, label %97

86:                                               ; preds = %73
  %87 = zext i32 %77 to i64
  %88 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 4
  %89 = load ptr, ptr %88, align 8, !tbaa !164
  %90 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 3
  %91 = load ptr, ptr %90, align 8, !tbaa !165
  %92 = ptrtoint ptr %89 to i64
  %93 = ptrtoint ptr %91 to i64
  %94 = sub i64 %92, %93
  %95 = mul nsw i64 %94, %87
  %96 = add nsw i64 %95, %84
  br label %124

97:                                               ; preds = %73
  %98 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 3
  %99 = load ptr, ptr %98, align 8, !tbaa !165
  %100 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 4
  %101 = load ptr, ptr %100, align 8, !tbaa !164
  %102 = icmp eq ptr %99, %101
  br i1 %102, label %124, label %103

103:                                              ; preds = %97
  %104 = load ptr, ptr %8, align 8, !tbaa !143
  %105 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %106 = load ptr, ptr %105, align 8, !tbaa !152
  %107 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  %108 = load ptr, ptr %107, align 8, !tbaa !168
  %109 = ptrtoint ptr %99 to i64
  %110 = ptrtoint ptr %108 to i64
  %111 = sub i64 %109, %110
  %112 = load ptr, ptr %104, align 8, !tbaa !93
  %113 = getelementptr inbounds ptr, ptr %112, i64 8
  %114 = load ptr, ptr %113, align 8
  %115 = call noundef i32 %114(ptr noundef nonnull align 8 dereferenceable(16) %104, ptr noundef nonnull align 1 dereferenceable(8) %3, ptr noundef %106, ptr noundef %81, i64 noundef %111) #35
  %116 = load ptr, ptr %80, align 8, !tbaa !167
  %117 = load ptr, ptr %105, align 8, !tbaa !152
  %118 = ptrtoint ptr %116 to i64
  %119 = ptrtoint ptr %117 to i64
  %120 = sext i32 %115 to i64
  %121 = add i64 %84, %118
  %122 = add i64 %120, %119
  %123 = sub i64 %121, %122
  br label %124

124:                                              ; preds = %103, %97, %86, %65
  %125 = phi i64 [ %72, %65 ], [ %96, %86 ], [ %123, %103 ], [ %84, %97 ]
  %126 = phi i1 [ false, %65 ], [ false, %86 ], [ true, %103 ], [ false, %97 ]
  %127 = load ptr, ptr %4, align 8, !tbaa !149
  %128 = sub nsw i64 0, %125
  %129 = call i32 @fseeko(ptr noundef %127, i64 noundef %128, i32 noundef 1) #35
  %130 = icmp eq i32 %129, 0
  br i1 %130, label %131, label %141

131:                                              ; preds = %124
  br i1 %126, label %132, label %135

132:                                              ; preds = %131
  %133 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 10
  %134 = load i64, ptr %3, align 8
  store i64 %134, ptr %133, align 8
  br label %135

135:                                              ; preds = %132, %131
  %136 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %137 = load ptr, ptr %136, align 8, !tbaa !152
  %138 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 3
  store ptr %137, ptr %138, align 8, !tbaa !166
  %139 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 2
  store ptr %137, ptr %139, align 8, !tbaa !167
  %140 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  store i32 0, ptr %13, align 4, !tbaa !161
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %140, i8 0, i64 24, i1 false)
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #37
  br label %142

141:                                              ; preds = %124
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #37
  br label %142

142:                                              ; preds = %141, %135, %56, %55, %51, %23, %1
  %143 = phi i32 [ -1, %141 ], [ 0, %1 ], [ -1, %23 ], [ -1, %55 ], [ 0, %135 ], [ 0, %51 ], [ 0, %56 ]
  ret i32 %143
}

; Function Attrs: minsize optsize
declare noundef i64 @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEE9showmanycEv(ptr noundef nonnull align 8 dereferenceable(64)) unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEE6xsgetnEPcl(ptr noundef nonnull align 8 dereferenceable(64), ptr noundef, i64 noundef) unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE9underflowEv(ptr noundef nonnull align 8 dereferenceable(163) %0) unnamed_addr #6 comdat align 2 {
  %2 = alloca i8, align 1
  %3 = alloca ptr, align 8
  %4 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 8
  %5 = load ptr, ptr %4, align 8, !tbaa !149
  %6 = icmp eq ptr %5, null
  br i1 %6, label %147, label %7

7:                                                ; preds = %1
  %8 = tail call noundef i1 @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE11__read_modeEv(ptr noundef nonnull align 8 dereferenceable(163) %0) #35
  call void @llvm.lifetime.start.p0(i64 1, ptr nonnull %2) #37
  %9 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 3
  %10 = load ptr, ptr %9, align 8, !tbaa !165
  %11 = icmp eq ptr %10, null
  br i1 %11, label %12, label %16

12:                                               ; preds = %7
  %13 = getelementptr inbounds i8, ptr %2, i64 1
  %14 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  store ptr %2, ptr %14, align 8, !tbaa !168
  store ptr %13, ptr %9, align 8, !tbaa !165
  %15 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 4
  store ptr %13, ptr %15, align 8, !tbaa !164
  br label %16

16:                                               ; preds = %12, %7
  %17 = phi ptr [ %13, %12 ], [ %10, %7 ]
  %18 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 4
  %19 = load ptr, ptr %18, align 8, !tbaa !164
  br i1 %8, label %28, label %20

20:                                               ; preds = %16
  %21 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  %22 = load ptr, ptr %21, align 8, !tbaa !168
  %23 = ptrtoint ptr %19 to i64
  %24 = ptrtoint ptr %22 to i64
  %25 = sub i64 %23, %24
  %26 = sdiv i64 %25, 2
  %27 = call i64 @llvm.umin.i64(i64 %26, i64 4)
  br label %28

28:                                               ; preds = %20, %16
  %29 = phi i64 [ %27, %20 ], [ 0, %16 ]
  %30 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 4
  %31 = icmp eq ptr %17, %19
  br i1 %31, label %32, label %137

32:                                               ; preds = %28
  %33 = sub nsw i64 0, %29
  %34 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  %35 = load ptr, ptr %34, align 8, !tbaa !168
  %36 = call i64 @llvm.objectsize.i64.p0(ptr %35, i1 false, i1 true, i1 false)
  %37 = getelementptr inbounds i8, ptr %17, i64 %33
  %38 = call ptr @__memmove_chk(ptr noundef %35, ptr noundef %37, i64 noundef %29, i64 noundef %36) #34
  %39 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 16
  %40 = load i8, ptr %39, align 2, !tbaa !148, !range !100, !noundef !101
  %41 = icmp eq i8 %40, 0
  br i1 %41, label %59, label %42

42:                                               ; preds = %32
  %43 = load ptr, ptr %30, align 8, !tbaa !164
  %44 = load ptr, ptr %34, align 8, !tbaa !168
  %45 = ptrtoint ptr %43 to i64
  %46 = ptrtoint ptr %44 to i64
  %47 = add i64 %29, %46
  %48 = sub i64 %45, %47
  %49 = getelementptr inbounds i8, ptr %44, i64 %29
  %50 = load ptr, ptr %4, align 8, !tbaa !149
  %51 = call noundef i64 @fread(ptr noundef %49, i64 noundef 1, i64 noundef %48, ptr noundef %50) #35
  %52 = icmp eq i64 %51, 0
  br i1 %52, label %140, label %53

53:                                               ; preds = %42
  %54 = load ptr, ptr %34, align 8, !tbaa !168
  %55 = getelementptr inbounds i8, ptr %54, i64 %29
  %56 = getelementptr inbounds i8, ptr %55, i64 %51
  store ptr %55, ptr %9, align 8, !tbaa !165
  store ptr %56, ptr %30, align 8, !tbaa !164
  %57 = load i8, ptr %55, align 1, !tbaa !38
  %58 = zext i8 %57 to i32
  br label %140

59:                                               ; preds = %32
  %60 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 3
  %61 = load ptr, ptr %60, align 8, !tbaa !166
  %62 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 2
  %63 = load ptr, ptr %62, align 8, !tbaa !167
  %64 = icmp eq ptr %61, %63
  br i1 %64, label %76, label %65

65:                                               ; preds = %59
  %66 = icmp ne ptr %63, null
  call void @llvm.assume(i1 %66)
  %67 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %68 = load ptr, ptr %67, align 8, !tbaa !152, !nonnull !101
  %69 = call i64 @llvm.objectsize.i64.p0(ptr nonnull %68, i1 false, i1 true, i1 false)
  %70 = ptrtoint ptr %61 to i64
  %71 = ptrtoint ptr %63 to i64
  %72 = sub i64 %70, %71
  %73 = call ptr @__memmove_chk(ptr noundef nonnull %68, ptr noundef nonnull %63, i64 noundef %72, i64 noundef %69) #34
  %74 = load ptr, ptr %60, align 8, !tbaa !166
  %75 = load ptr, ptr %62, align 8, !tbaa !167
  br label %76

76:                                               ; preds = %65, %59
  %77 = phi ptr [ %75, %65 ], [ %61, %59 ]
  %78 = phi ptr [ %74, %65 ], [ %61, %59 ]
  %79 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %80 = load ptr, ptr %79, align 8, !tbaa !152
  %81 = ptrtoint ptr %78 to i64
  %82 = ptrtoint ptr %77 to i64
  %83 = sub i64 %81, %82
  %84 = getelementptr inbounds i8, ptr %80, i64 %83
  store ptr %84, ptr %62, align 8, !tbaa !167
  %85 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 4
  %86 = icmp eq ptr %80, %85
  %87 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  %88 = load i64, ptr %87, align 8
  %89 = select i1 %86, i64 8, i64 %88
  %90 = getelementptr inbounds i8, ptr %80, i64 %89
  store ptr %90, ptr %60, align 8, !tbaa !166
  %91 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 7
  %92 = load i64, ptr %91, align 8, !tbaa !158
  %93 = sub i64 %92, %29
  %94 = ptrtoint ptr %90 to i64
  %95 = ptrtoint ptr %84 to i64
  %96 = sub i64 %94, %95
  %97 = call i64 @llvm.umin.i64(i64 %96, i64 %93)
  %98 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 10
  %99 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 11
  %100 = load i64, ptr %98, align 8
  store i64 %100, ptr %99, align 8
  %101 = load ptr, ptr %4, align 8, !tbaa !149
  %102 = call noundef i64 @fread(ptr noundef %84, i64 noundef 1, i64 noundef %97, ptr noundef %101) #35
  %103 = icmp eq i64 %102, 0
  br i1 %103, label %140, label %104

104:                                              ; preds = %76
  %105 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 9
  %106 = load ptr, ptr %105, align 8, !tbaa !143
  %107 = icmp eq ptr %106, null
  br i1 %107, label %108, label %109

108:                                              ; preds = %104
  call void @_ZNSt6__ndk116__throw_bad_castB7v170000Ev() #42
  unreachable

109:                                              ; preds = %104
  %110 = load ptr, ptr %62, align 8, !tbaa !167
  %111 = getelementptr inbounds i8, ptr %110, i64 %102
  store ptr %111, ptr %60, align 8, !tbaa !166
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %3) #37
  %112 = load ptr, ptr %79, align 8, !tbaa !152
  %113 = load ptr, ptr %34, align 8, !tbaa !168
  %114 = getelementptr inbounds i8, ptr %113, i64 %29
  %115 = load i64, ptr %91, align 8, !tbaa !158
  %116 = getelementptr inbounds i8, ptr %113, i64 %115
  %117 = load ptr, ptr %106, align 8, !tbaa !93
  %118 = getelementptr inbounds ptr, ptr %117, i64 4
  %119 = load ptr, ptr %118, align 8
  %120 = call noundef i32 %119(ptr noundef nonnull align 8 dereferenceable(16) %106, ptr noundef nonnull align 1 dereferenceable(8) %98, ptr noundef %112, ptr noundef nonnull %111, ptr noundef nonnull align 8 dereferenceable(8) %62, ptr noundef %114, ptr noundef %116, ptr noundef nonnull align 8 dereferenceable(8) %3) #35
  %121 = icmp eq i32 %120, 3
  br i1 %121, label %122, label %125

122:                                              ; preds = %109
  %123 = load ptr, ptr %79, align 8, !tbaa !152
  %124 = load ptr, ptr %60, align 8, !tbaa !166
  store ptr %123, ptr %34, align 8, !tbaa !168
  br label %130

125:                                              ; preds = %109
  %126 = load ptr, ptr %3, align 8, !tbaa !42
  %127 = load ptr, ptr %34, align 8, !tbaa !168
  %128 = getelementptr inbounds i8, ptr %127, i64 %29
  %129 = icmp eq ptr %126, %128
  br i1 %129, label %135, label %130

130:                                              ; preds = %125, %122
  %131 = phi ptr [ %123, %122 ], [ %128, %125 ]
  %132 = phi ptr [ %124, %122 ], [ %126, %125 ]
  store ptr %131, ptr %9, align 8, !tbaa !165
  store ptr %132, ptr %30, align 8, !tbaa !164
  %133 = load i8, ptr %131, align 1, !tbaa !38
  %134 = zext i8 %133 to i32
  br label %135

135:                                              ; preds = %130, %125
  %136 = phi i32 [ -1, %125 ], [ %134, %130 ]
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #37
  br label %140

137:                                              ; preds = %28
  %138 = load i8, ptr %17, align 1, !tbaa !38
  %139 = zext i8 %138 to i32
  br label %140

140:                                              ; preds = %137, %135, %76, %53, %42
  %141 = phi i32 [ %139, %137 ], [ %58, %53 ], [ -1, %42 ], [ %136, %135 ], [ -1, %76 ]
  %142 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  %143 = load ptr, ptr %142, align 8, !tbaa !168
  %144 = icmp eq ptr %143, %2
  br i1 %144, label %145, label %146

145:                                              ; preds = %140
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %142, i8 0, i64 24, i1 false)
  br label %146

146:                                              ; preds = %145, %140
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %2) #37
  br label %147

147:                                              ; preds = %146, %1
  %148 = phi i32 [ %141, %146 ], [ -1, %1 ]
  ret i32 %148
}

; Function Attrs: minsize optsize
declare noundef i32 @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEE5uflowEv(ptr noundef nonnull align 8 dereferenceable(64)) unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE9pbackfailEi(ptr noundef nonnull align 8 dereferenceable(163) %0, i32 noundef %1) unnamed_addr #6 comdat align 2 {
  %3 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 8
  %4 = load ptr, ptr %3, align 8, !tbaa !149
  %5 = icmp eq ptr %4, null
  br i1 %5, label %28, label %6

6:                                                ; preds = %2
  %7 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  %8 = load ptr, ptr %7, align 8, !tbaa !168
  %9 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 3
  %10 = load ptr, ptr %9, align 8, !tbaa !165
  %11 = icmp ult ptr %8, %10
  br i1 %11, label %12, label %28

12:                                               ; preds = %6
  %13 = icmp eq i32 %1, -1
  br i1 %13, label %14, label %16

14:                                               ; preds = %12
  %15 = getelementptr inbounds i8, ptr %10, i64 -1
  store ptr %15, ptr %9, align 8, !tbaa !165
  br label %28

16:                                               ; preds = %12
  %17 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 12
  %18 = load i32, ptr %17, align 8, !tbaa !150
  %19 = and i32 %18, 16
  %20 = icmp eq i32 %19, 0
  %21 = trunc i32 %1 to i8
  br i1 %20, label %22, label %26

22:                                               ; preds = %16
  %23 = getelementptr inbounds i8, ptr %10, i64 -1
  %24 = load i8, ptr %23, align 1, !tbaa !38
  %25 = icmp eq i8 %24, %21
  br i1 %25, label %26, label %28

26:                                               ; preds = %22, %16
  %27 = getelementptr inbounds i8, ptr %10, i64 -1
  store ptr %27, ptr %9, align 8, !tbaa !165
  store i8 %21, ptr %27, align 1, !tbaa !38
  br label %28

28:                                               ; preds = %26, %22, %14, %6, %2
  %29 = phi i32 [ 0, %14 ], [ %1, %26 ], [ -1, %22 ], [ -1, %6 ], [ -1, %2 ]
  ret i32 %29
}

; Function Attrs: minsize optsize
declare noundef i64 @_ZNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEE6xsputnEPKcl(ptr noundef nonnull align 8 dereferenceable(64), ptr noundef, i64 noundef) unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i32 @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE8overflowEi(ptr noundef nonnull align 8 dereferenceable(163) %0, i32 noundef %1) unnamed_addr #6 comdat align 2 {
  %3 = alloca i8, align 1
  %4 = alloca ptr, align 8
  %5 = alloca ptr, align 8
  %6 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 8
  %7 = load ptr, ptr %6, align 8, !tbaa !149
  %8 = icmp eq ptr %7, null
  br i1 %8, label %105, label %9

9:                                                ; preds = %2
  tail call void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE12__write_modeEv(ptr noundef nonnull align 8 dereferenceable(163) %0) #35
  call void @llvm.lifetime.start.p0(i64 1, ptr nonnull %3) #37
  %10 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 5
  %11 = load ptr, ptr %10, align 8, !tbaa !163
  %12 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 7
  %13 = load ptr, ptr %12, align 8, !tbaa !169
  %14 = icmp eq i32 %1, -1
  %15 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 6
  %16 = load ptr, ptr %15, align 8, !tbaa !162
  br i1 %14, label %27, label %17

17:                                               ; preds = %9
  %18 = icmp eq ptr %16, null
  br i1 %18, label %19, label %21

19:                                               ; preds = %17
  %20 = getelementptr inbounds i8, ptr %3, i64 1
  store ptr %3, ptr %15, align 8, !tbaa !162
  store ptr %3, ptr %10, align 8, !tbaa !163
  store ptr %20, ptr %12, align 8, !tbaa !169
  br label %21

21:                                               ; preds = %19, %17
  %22 = phi ptr [ %3, %19 ], [ %16, %17 ]
  %23 = trunc i32 %1 to i8
  store i8 %23, ptr %22, align 1, !tbaa !38
  %24 = load ptr, ptr %15, align 8, !tbaa !162
  %25 = getelementptr inbounds i8, ptr %24, i64 1
  store ptr %25, ptr %15, align 8, !tbaa !162
  %26 = load ptr, ptr %10, align 8, !tbaa !163
  br label %27

27:                                               ; preds = %21, %9
  %28 = phi ptr [ %26, %21 ], [ %11, %9 ]
  %29 = phi ptr [ %25, %21 ], [ %16, %9 ]
  %30 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 6
  %31 = icmp eq ptr %29, %28
  br i1 %31, label %100, label %32

32:                                               ; preds = %27
  %33 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 16
  %34 = load i8, ptr %33, align 2, !tbaa !148, !range !100, !noundef !101
  %35 = icmp eq i8 %34, 0
  br i1 %35, label %43, label %36

36:                                               ; preds = %32
  %37 = ptrtoint ptr %29 to i64
  %38 = ptrtoint ptr %28 to i64
  %39 = sub i64 %37, %38
  %40 = load ptr, ptr %6, align 8, !tbaa !149
  %41 = call noundef i64 @fwrite(ptr noundef %28, i64 noundef 1, i64 noundef %39, ptr noundef %40) #35
  %42 = icmp eq i64 %41, %39
  br i1 %42, label %99, label %103

43:                                               ; preds = %32
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %4) #37
  %44 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %45 = load ptr, ptr %44, align 8, !tbaa !152
  store ptr %45, ptr %4, align 8, !tbaa !42
  %46 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 9
  %47 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 10
  %48 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  br label %49

49:                                               ; preds = %91, %43
  %50 = phi ptr [ %97, %91 ], [ %29, %43 ]
  %51 = phi ptr [ %92, %91 ], [ %28, %43 ]
  %52 = load ptr, ptr %46, align 8, !tbaa !143
  %53 = icmp eq ptr %52, null
  br i1 %53, label %54, label %55

54:                                               ; preds = %49
  call void @_ZNSt6__ndk116__throw_bad_castB7v170000Ev() #42
  unreachable

55:                                               ; preds = %49
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %5) #37
  %56 = load ptr, ptr %44, align 8, !tbaa !152
  %57 = load i64, ptr %48, align 8, !tbaa !159
  %58 = getelementptr inbounds i8, ptr %56, i64 %57
  %59 = load ptr, ptr %52, align 8, !tbaa !93
  %60 = getelementptr inbounds ptr, ptr %59, i64 3
  %61 = load ptr, ptr %60, align 8
  %62 = call noundef i32 %61(ptr noundef nonnull align 8 dereferenceable(16) %52, ptr noundef nonnull align 1 dereferenceable(8) %47, ptr noundef %51, ptr noundef %50, ptr noundef nonnull align 8 dereferenceable(8) %5, ptr noundef %56, ptr noundef %58, ptr noundef nonnull align 8 dereferenceable(8) %4) #35
  %63 = load ptr, ptr %5, align 8, !tbaa !42
  %64 = load ptr, ptr %10, align 8, !tbaa !163
  %65 = icmp eq ptr %63, %64
  br i1 %65, label %102, label %66

66:                                               ; preds = %55
  %67 = icmp eq i32 %62, 3
  br i1 %67, label %68, label %77

68:                                               ; preds = %66
  %69 = load ptr, ptr %30, align 8, !tbaa !162
  %70 = ptrtoint ptr %69 to i64
  %71 = ptrtoint ptr %64 to i64
  %72 = sub i64 %70, %71
  %73 = load ptr, ptr %6, align 8, !tbaa !149
  %74 = call noundef i64 @fwrite(ptr noundef %64, i64 noundef 1, i64 noundef %72, ptr noundef %73) #35
  %75 = icmp eq i64 %74, %72
  br i1 %75, label %76, label %102

76:                                               ; preds = %68
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %5) #37
  br label %98

77:                                               ; preds = %66
  %78 = icmp eq i32 %62, 1
  %79 = icmp ult i32 %62, 2
  br i1 %79, label %80, label %102

80:                                               ; preds = %77
  %81 = load ptr, ptr %4, align 8, !tbaa !42
  %82 = load ptr, ptr %44, align 8, !tbaa !152
  %83 = ptrtoint ptr %81 to i64
  %84 = ptrtoint ptr %82 to i64
  %85 = sub i64 %83, %84
  %86 = load ptr, ptr %6, align 8, !tbaa !149
  %87 = call noundef i64 @fwrite(ptr noundef %82, i64 noundef 1, i64 noundef %85, ptr noundef %86) #35
  %88 = icmp eq i64 %87, %85
  br i1 %88, label %89, label %102

89:                                               ; preds = %80
  br i1 %78, label %91, label %90

90:                                               ; preds = %89
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %5) #37
  br label %98

91:                                               ; preds = %89
  %92 = load ptr, ptr %5, align 8, !tbaa !42
  %93 = load ptr, ptr %30, align 8, !tbaa !162
  store ptr %92, ptr %10, align 8, !tbaa !163
  store ptr %93, ptr %12, align 8, !tbaa !169
  %94 = ptrtoint ptr %93 to i64
  %95 = ptrtoint ptr %92 to i64
  %96 = sub i64 %94, %95
  %97 = getelementptr inbounds i8, ptr %92, i64 %96
  store ptr %97, ptr %30, align 8, !tbaa !162
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %5) #37
  br label %49, !llvm.loop !170

98:                                               ; preds = %90, %76
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %4) #37
  br label %99

99:                                               ; preds = %98, %36
  store ptr %11, ptr %30, align 8, !tbaa !162
  store ptr %11, ptr %10, align 8, !tbaa !163
  store ptr %13, ptr %12, align 8, !tbaa !169
  br label %100

100:                                              ; preds = %99, %27
  %101 = select i1 %14, i32 0, i32 %1
  br label %103

102:                                              ; preds = %80, %77, %68, %55
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %5) #37
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %4) #37
  br label %103

103:                                              ; preds = %102, %100, %36
  %104 = phi i32 [ %101, %100 ], [ -1, %36 ], [ -1, %102 ]
  call void @llvm.lifetime.end.p0(i64 1, ptr nonnull %3) #37
  br label %105

105:                                              ; preds = %103, %2
  %106 = phi i32 [ %104, %103 ], [ -1, %2 ]
  ret i32 %106
}

; Function Attrs: minsize optsize
declare noundef i1 @_ZNKSt6__ndk16locale9has_facetERNS0_2idE(ptr noundef nonnull align 8 dereferenceable(8), ptr noundef nonnull align 8 dereferenceable(12)) local_unnamed_addr #7

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk16localeC1ERKS0_(ptr noundef nonnull align 8 dereferenceable(8), ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #15

; Function Attrs: minsize optsize
declare noundef ptr @_ZNKSt6__ndk16locale9use_facetERNS0_2idE(ptr noundef nonnull align 8 dereferenceable(8), ptr noundef nonnull align 8 dereferenceable(12)) local_unnamed_addr #7

; Function Attrs: minsize nobuiltin nounwind optsize
declare void @_ZdaPv(ptr noundef) local_unnamed_addr #18

; Function Attrs: minsize nobuiltin optsize allocsize(0)
declare noundef nonnull ptr @_Znam(i64 noundef) local_unnamed_addr #16

; Function Attrs: inlinehint minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk116__throw_bad_castB7v170000Ev() local_unnamed_addr #14 comdat {
  %1 = tail call ptr @__cxa_allocate_exception(i64 8) #37
  tail call void @_ZNSt8bad_castC1Ev(ptr noundef nonnull align 8 dereferenceable(8) %1) #34
  tail call void @__cxa_throw(ptr nonnull %1, ptr nonnull @_ZTISt8bad_cast, ptr nonnull @_ZNSt8bad_castD1Ev) #44
  unreachable
}

; Function Attrs: minsize nofree nounwind optsize
declare noundef i32 @fseeko(ptr nocapture noundef, i64 noundef, i32 noundef) local_unnamed_addr #9

; Function Attrs: minsize nofree nounwind optsize
declare noundef i64 @ftello(ptr nocapture noundef) local_unnamed_addr #9

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt8bad_castC1Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt8bad_castD1Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #15

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.objectsize.i64.p0(ptr, i1 immarg, i1 immarg, i1 immarg) #28

; Function Attrs: minsize nofree nounwind optsize
declare noundef i32 @fflush(ptr nocapture noundef) local_unnamed_addr #9

; Function Attrs: minsize nofree nounwind optsize
declare noundef i64 @fwrite(ptr nocapture noundef, i64 noundef, i64 noundef, ptr nocapture noundef) local_unnamed_addr #9

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE11__read_modeEv(ptr noundef nonnull align 8 dereferenceable(163) %0) local_unnamed_addr #19 comdat align 2 {
  %2 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 13
  %3 = load i32, ptr %2, align 4, !tbaa !161
  %4 = and i32 %3, 8
  %5 = icmp eq i32 %4, 0
  br i1 %5, label %6, label %25

6:                                                ; preds = %1
  %7 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 5
  %8 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 16
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %7, i8 0, i64 24, i1 false)
  %9 = load i8, ptr %8, align 2, !tbaa !148, !range !100, !noundef !101
  %10 = icmp eq i8 %9, 0
  %11 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  %12 = load ptr, ptr %11, align 8
  %13 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 7
  %14 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %15 = load ptr, ptr %14, align 8
  %16 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  %17 = select i1 %10, ptr %12, ptr %15
  %18 = load i64, ptr %13, align 8
  %19 = load i64, ptr %16, align 8
  %20 = select i1 %10, i64 %18, i64 %19
  %21 = getelementptr inbounds i8, ptr %17, i64 %20
  %22 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  store ptr %17, ptr %22, align 8
  %23 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 3
  store ptr %21, ptr %23, align 8
  %24 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 4
  store ptr %21, ptr %24, align 8
  store i32 8, ptr %2, align 4, !tbaa !161
  br label %25

25:                                               ; preds = %6, %1
  ret i1 %5
}

; Function Attrs: minsize nofree nounwind optsize
declare ptr @__memmove_chk(ptr noundef, ptr noundef, i64 noundef, i64 noundef) local_unnamed_addr #9

; Function Attrs: minsize nofree nounwind optsize
declare noundef i64 @fread(ptr nocapture noundef, i64 noundef, i64 noundef, ptr nocapture noundef) local_unnamed_addr #9

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE12__write_modeEv(ptr noundef nonnull align 8 dereferenceable(163) %0) local_unnamed_addr #19 comdat align 2 {
  %2 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 13
  %3 = load i32, ptr %2, align 4, !tbaa !161
  %4 = and i32 %3, 16
  %5 = icmp eq i32 %4, 0
  br i1 %5, label %6, label %36

6:                                                ; preds = %1
  %7 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 2
  %8 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 5
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %7, i8 0, i64 24, i1 false)
  %9 = load i64, ptr %8, align 8, !tbaa !159
  %10 = icmp ugt i64 %9, 8
  br i1 %10, label %11, label %33

11:                                               ; preds = %6
  %12 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 16
  %13 = load i8, ptr %12, align 2, !tbaa !148, !range !100, !noundef !101
  %14 = icmp eq i8 %13, 0
  br i1 %14, label %23, label %15

15:                                               ; preds = %11
  %16 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 1
  %17 = load ptr, ptr %16, align 8, !tbaa !152
  %18 = add i64 %9, -1
  %19 = getelementptr inbounds i8, ptr %17, i64 %18
  %20 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 6
  store ptr %17, ptr %20, align 8, !tbaa !162
  %21 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 5
  store ptr %17, ptr %21, align 8, !tbaa !163
  %22 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 7
  store ptr %19, ptr %22, align 8, !tbaa !169
  br label %35

23:                                               ; preds = %11
  %24 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 6
  %25 = load ptr, ptr %24, align 8, !tbaa !154
  %26 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 7
  %27 = load i64, ptr %26, align 8, !tbaa !158
  %28 = add i64 %27, -1
  %29 = getelementptr inbounds i8, ptr %25, i64 %28
  %30 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 6
  store ptr %25, ptr %30, align 8, !tbaa !162
  %31 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 5
  store ptr %25, ptr %31, align 8, !tbaa !163
  %32 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 7
  store ptr %29, ptr %32, align 8, !tbaa !169
  br label %35

33:                                               ; preds = %6
  %34 = getelementptr inbounds %"class.std::__ndk1::basic_streambuf", ptr %0, i64 0, i32 5
  call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %34, i8 0, i64 24, i1 false)
  br label %35

35:                                               ; preds = %33, %23, %15
  store i32 16, ptr %2, align 4, !tbaa !161
  br label %36

36:                                               ; preds = %35, %1
  ret void
}

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE15__make_mdstringEj(i32 noundef %0) local_unnamed_addr #24 comdat align 2 {
  %2 = and i32 %0, -3
  switch i32 %2, label %14 [
    i32 16, label %15
    i32 48, label %15
    i32 17, label %3
    i32 1, label %3
    i32 8, label %4
    i32 24, label %5
    i32 56, label %6
    i32 25, label %7
    i32 9, label %7
    i32 20, label %8
    i32 52, label %8
    i32 21, label %9
    i32 5, label %9
    i32 12, label %10
    i32 28, label %11
    i32 60, label %12
    i32 29, label %13
    i32 13, label %13
  ]

3:                                                ; preds = %1, %1
  br label %15

4:                                                ; preds = %1
  br label %15

5:                                                ; preds = %1
  br label %15

6:                                                ; preds = %1
  br label %15

7:                                                ; preds = %1, %1
  br label %15

8:                                                ; preds = %1, %1
  br label %15

9:                                                ; preds = %1, %1
  br label %15

10:                                               ; preds = %1
  br label %15

11:                                               ; preds = %1
  br label %15

12:                                               ; preds = %1
  br label %15

13:                                               ; preds = %1, %1
  br label %15

14:                                               ; preds = %1
  br label %15

15:                                               ; preds = %14, %13, %12, %11, %10, %9, %8, %7, %6, %5, %4, %3, %1, %1
  %16 = phi ptr [ null, %14 ], [ @.str.36, %13 ], [ @.str.35, %12 ], [ @.str.34, %11 ], [ @.str.33, %10 ], [ @.str.32, %9 ], [ @.str.31, %8 ], [ @.str.30, %7 ], [ @.str.29, %6 ], [ @.str.28, %5 ], [ @.str.27, %4 ], [ @.str.26, %3 ], [ @.str.25, %1 ], [ @.str.25, %1 ]
  ret ptr %16
}

; Function Attrs: minsize nofree nounwind optsize
declare noundef i32 @fseek(ptr nocapture noundef, i64 noundef, i32 noundef) local_unnamed_addr #9

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk18ios_base8setstateB7v170000Ej(ptr noundef nonnull align 8 dereferenceable(136) %0, i32 noundef %1) local_unnamed_addr #27 comdat align 2 {
  %3 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %0, i64 0, i32 4
  %4 = load i32, ptr %3, align 8, !tbaa !125
  %5 = or i32 %4, %1
  tail call void @_ZNSt6__ndk18ios_base5clearEj(ptr noundef nonnull align 8 dereferenceable(136) %0, i32 noundef %5) #35
  ret void
}

; Function Attrs: minsize optsize
declare void @_ZNSt6__ndk18ios_base5clearEj(ptr noundef nonnull align 8 dereferenceable(136), i32 noundef) local_unnamed_addr #7

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE5closeEv(ptr noundef nonnull align 8 dereferenceable(163) %0) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::unique_ptr", align 8
  %3 = getelementptr inbounds %"class.std::__ndk1::basic_filebuf", ptr %0, i64 0, i32 8
  %4 = load ptr, ptr %3, align 8, !tbaa !149
  %5 = icmp eq ptr %4, null
  br i1 %5, label %24, label %6

6:                                                ; preds = %1
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %2) #37
  store ptr %4, ptr %2, align 8, !tbaa !171
  %7 = getelementptr inbounds i8, ptr %2, i64 8
  store ptr @fclose, ptr %7, align 8, !tbaa !173
  %8 = load ptr, ptr %0, align 8, !tbaa !93
  %9 = getelementptr inbounds ptr, ptr %8, i64 6
  %10 = load ptr, ptr %9, align 8
  %11 = invoke noundef i32 %10(ptr noundef nonnull align 8 dereferenceable(163) %0) #35
          to label %12 unwind label %18

12:                                               ; preds = %6
  store ptr null, ptr %2, align 8, !tbaa !42
  %13 = tail call i32 @fclose(ptr noundef nonnull %4) #35
  store ptr null, ptr %3, align 8, !tbaa !149
  %14 = load ptr, ptr %0, align 8, !tbaa !93
  %15 = getelementptr inbounds ptr, ptr %14, i64 3
  %16 = load ptr, ptr %15, align 8
  %17 = invoke noundef ptr %16(ptr noundef nonnull align 8 dereferenceable(163) %0, ptr noundef null, i64 noundef 0) #35
          to label %20 unwind label %18

18:                                               ; preds = %12, %6
  %19 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk110unique_ptrI7__sFILEPFiPS1_EED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(16) %2) #34
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %2) #37
  resume { ptr, i32 } %19

20:                                               ; preds = %12
  %21 = or i32 %13, %11
  %22 = icmp eq i32 %21, 0
  %23 = select i1 %22, ptr %0, ptr null
  call void @_ZNSt6__ndk110unique_ptrI7__sFILEPFiPS1_EED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(16) %2) #34
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %2) #37
  br label %24

24:                                               ; preds = %20, %1
  %25 = phi ptr [ %23, %20 ], [ null, %1 ]
  ret ptr %25
}

declare void @__cxa_end_catch() local_unnamed_addr

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrI7__sFILEPFiPS1_EED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(16) %0) unnamed_addr #4 comdat align 2 {
  tail call void @_ZNSt6__ndk110unique_ptrI7__sFILEPFiPS1_EE5resetB7v170000ES2_(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef null) #34
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk110unique_ptrI7__sFILEPFiPS1_EE5resetB7v170000ES2_(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 !type !175 {
  %3 = load ptr, ptr %0, align 8, !tbaa !42
  store ptr %1, ptr %0, align 8, !tbaa !42
  %4 = icmp eq ptr %3, null
  br i1 %4, label %9, label %5

5:                                                ; preds = %2
  %6 = getelementptr inbounds i8, ptr %0, i64 8
  %7 = load ptr, ptr %6, align 8, !tbaa !42
  %8 = invoke noundef i32 %7(ptr noundef nonnull %3) #35
          to label %9 unwind label %10

9:                                                ; preds = %5, %2
  ret void

10:                                               ; preds = %5
  %11 = landingpad { ptr, i32 }
          catch ptr null
  %12 = extractvalue { ptr, i32 } %11, 0
  tail call void @__clang_call_terminate(ptr %12) #38
  unreachable
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE8__appendEm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = alloca %"struct.std::__ndk1::__split_buffer", align 8
  %4 = getelementptr inbounds %"class.std::__ndk1::vector.13", ptr %0, i64 0, i32 2
  %5 = load ptr, ptr %4, align 8, !tbaa !42
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.13", ptr %0, i64 0, i32 1
  %7 = load ptr, ptr %6, align 8, !tbaa !131
  %8 = ptrtoint ptr %5 to i64
  %9 = ptrtoint ptr %7 to i64
  %10 = sub i64 %8, %9
  %11 = icmp ult i64 %10, %1
  br i1 %11, label %13, label %12

12:                                               ; preds = %2
  tail call void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE18__construct_at_endEm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) #35
  br label %50

13:                                               ; preds = %2
  call void @llvm.lifetime.start.p0(i64 40, ptr nonnull %3) #37
  %14 = load ptr, ptr %0, align 8, !tbaa !127
  %15 = ptrtoint ptr %14 to i64
  %16 = add i64 %9, %1
  %17 = sub i64 %16, %15
  %18 = tail call noundef i64 @_ZNKSt6__ndk16vectorIhNS_9allocatorIhEEE11__recommendB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %17) #35
  %19 = load ptr, ptr %6, align 8, !tbaa !131
  %20 = load ptr, ptr %0, align 8, !tbaa !127
  %21 = ptrtoint ptr %19 to i64
  %22 = ptrtoint ptr %20 to i64
  %23 = sub i64 %21, %22
  %24 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %3, i64 0, i32 3
  %25 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %3, i64 0, i32 3, i32 1
  store ptr %4, ptr %25, align 8, !tbaa !42
  %26 = icmp eq i64 %18, 0
  br i1 %26, label %32, label %27

27:                                               ; preds = %13
  %28 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIhEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %4, i64 noundef %18) #35
  %29 = extractvalue [2 x i64] %28, 0
  %30 = inttoptr i64 %29 to ptr
  %31 = extractvalue [2 x i64] %28, 1
  br label %32

32:                                               ; preds = %27, %13
  %33 = phi ptr [ %30, %27 ], [ null, %13 ]
  %34 = phi i64 [ %31, %27 ], [ 0, %13 ]
  store ptr %33, ptr %3, align 8, !tbaa !176
  %35 = getelementptr inbounds i8, ptr %33, i64 %23
  %36 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %3, i64 0, i32 2
  %37 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %3, i64 0, i32 1
  store ptr %35, ptr %37, align 8, !tbaa !180
  %38 = getelementptr inbounds i8, ptr %33, i64 %34
  store ptr %38, ptr %24, align 8, !tbaa !42
  %39 = getelementptr inbounds i8, ptr %35, i64 %1
  br label %40

40:                                               ; preds = %43, %32
  %41 = phi ptr [ %35, %32 ], [ %45, %43 ]
  %42 = icmp eq ptr %41, %39
  br i1 %42, label %46, label %43

43:                                               ; preds = %40
  %44 = icmp ne ptr %41, null
  tail call void @llvm.assume(i1 %44)
  store i8 0, ptr %41, align 1, !tbaa !38
  %45 = getelementptr inbounds i8, ptr %41, i64 1
  br label %40, !llvm.loop !181

46:                                               ; preds = %40
  store ptr %39, ptr %36, align 8, !tbaa !42
  invoke void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE26__swap_out_circular_bufferERNS_14__split_bufferIhRS2_EE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %3) #35
          to label %47 unwind label %48

47:                                               ; preds = %46
  call void @_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #34
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #37
  br label %50

48:                                               ; preds = %46
  %49 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %3) #34
  call void @llvm.lifetime.end.p0(i64 40, ptr nonnull %3) #37
  resume { ptr, i32 } %49

50:                                               ; preds = %47, %12
  ret void
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE18__construct_at_endEm(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector.13", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !131
  %5 = getelementptr inbounds i8, ptr %4, i64 %1
  br label %6

6:                                                ; preds = %10, %2
  %7 = phi ptr [ %4, %2 ], [ %12, %10 ]
  %8 = icmp eq ptr %7, %5
  br i1 %8, label %9, label %10

9:                                                ; preds = %6
  store ptr %5, ptr %3, align 8, !tbaa !131
  ret void

10:                                               ; preds = %6
  %11 = icmp ne ptr %7, null
  tail call void @llvm.assume(i1 %11)
  store i8 0, ptr %7, align 1, !tbaa !38
  %12 = getelementptr inbounds i8, ptr %7, i64 1
  br label %6, !llvm.loop !182
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i64 @_ZNKSt6__ndk16vectorIhNS_9allocatorIhEEE11__recommendB7v170000Em(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1) local_unnamed_addr #27 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = icmp slt i64 %1, 0
  br i1 %3, label %4, label %5

4:                                                ; preds = %2
  tail call void @_ZNKSt6__ndk16vectorIhNS_9allocatorIhEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
  unreachable

5:                                                ; preds = %2
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.13", ptr %0, i64 0, i32 2
  %7 = load ptr, ptr %6, align 8, !tbaa !42
  %8 = load ptr, ptr %0, align 8, !tbaa !127
  %9 = ptrtoint ptr %7 to i64
  %10 = ptrtoint ptr %8 to i64
  %11 = sub i64 %9, %10
  %12 = icmp ult i64 %11, 4611686018427387903
  %13 = shl nuw nsw i64 %11, 1
  %14 = tail call i64 @llvm.umax.i64(i64 %13, i64 %1)
  %15 = select i1 %12, i64 %14, i64 9223372036854775807
  ret i64 %15
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE26__swap_out_circular_bufferERNS_14__split_bufferIhRS2_EE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(40) %1) local_unnamed_addr #1 comdat align 2 {
  %3 = getelementptr inbounds %"class.std::__ndk1::vector.13", ptr %0, i64 0, i32 1
  %4 = load ptr, ptr %3, align 8, !tbaa !131
  %5 = load ptr, ptr %0, align 8, !tbaa !127
  %6 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %1, i64 0, i32 1
  %7 = load ptr, ptr %6, align 8, !tbaa !180
  %8 = ptrtoint ptr %4 to i64
  %9 = insertvalue [2 x i64] poison, i64 %8, 0
  %10 = insertvalue [2 x i64] %9, i64 %8, 1
  %11 = ptrtoint ptr %5 to i64
  %12 = insertvalue [2 x i64] poison, i64 %11, 0
  %13 = insertvalue [2 x i64] %12, i64 %11, 1
  %14 = ptrtoint ptr %7 to i64
  %15 = insertvalue [2 x i64] poison, i64 %14, 0
  %16 = insertvalue [2 x i64] %15, i64 %14, 1
  %17 = tail call [2 x i64] @_ZNSt6__ndk14moveB7v170000INS_16reverse_iteratorIPhEES3_EET0_T_S5_S4_([2 x i64] %10, [2 x i64] %13, [2 x i64] %16) #35
  %18 = extractvalue [2 x i64] %17, 1
  %19 = inttoptr i64 %18 to ptr
  store ptr %19, ptr %6, align 8, !tbaa !180
  %20 = load ptr, ptr %0, align 8, !tbaa !42
  store ptr %19, ptr %0, align 8, !tbaa !42
  store ptr %20, ptr %6, align 8, !tbaa !42
  %21 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %1, i64 0, i32 2
  %22 = load ptr, ptr %3, align 8, !tbaa !42
  %23 = load ptr, ptr %21, align 8, !tbaa !42
  store ptr %23, ptr %3, align 8, !tbaa !42
  store ptr %22, ptr %21, align 8, !tbaa !42
  %24 = getelementptr inbounds %"class.std::__ndk1::vector.13", ptr %0, i64 0, i32 2
  %25 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %1, i64 0, i32 3
  %26 = load ptr, ptr %24, align 8, !tbaa !42
  %27 = load ptr, ptr %25, align 8, !tbaa !42
  store ptr %27, ptr %24, align 8, !tbaa !42
  store ptr %26, ptr %25, align 8, !tbaa !42
  %28 = load ptr, ptr %6, align 8, !tbaa !180
  store ptr %28, ptr %1, align 8, !tbaa !176
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEED2Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  tail call void @_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEE5clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) #34
  %2 = load ptr, ptr %0, align 8, !tbaa !176
  %3 = icmp eq ptr %2, null
  br i1 %3, label %5, label %4

4:                                                ; preds = %1
  tail call void @_ZdlPv(ptr noundef nonnull %2) #36
  br label %5

5:                                                ; preds = %4, %1
  ret void
}

; Function Attrs: minsize mustprogress noreturn optsize uwtable
define linkonce_odr hidden void @_ZNKSt6__ndk16vectorIhNS_9allocatorIhEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) local_unnamed_addr #13 comdat align 2 {
  tail call void @_ZNSt6__ndk120__throw_length_errorB7v170000EPKc(ptr noundef nonnull @.str.19) #42
  unreachable
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIhEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %1) local_unnamed_addr #6 comdat {
  %3 = tail call noalias noundef nonnull ptr @_Znwm(i64 noundef %1) #43
  %4 = ptrtoint ptr %3 to i64
  %5 = insertvalue [2 x i64] poison, i64 %4, 0
  %6 = insertvalue [2 x i64] %5, i64 %1, 1
  ret [2 x i64] %6
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden [2 x i64] @_ZNSt6__ndk14moveB7v170000INS_16reverse_iteratorIPhEES3_EET0_T_S5_S4_([2 x i64] %0, [2 x i64] %1, [2 x i64] %2) local_unnamed_addr #3 comdat personality ptr @__gxx_personality_v0 {
  %4 = extractvalue [2 x i64] %0, 1
  %5 = extractvalue [2 x i64] %1, 1
  %6 = inttoptr i64 %4 to ptr
  %7 = inttoptr i64 %5 to ptr
  %8 = extractvalue [2 x i64] %2, 1
  %9 = inttoptr i64 %8 to ptr
  br label %10

10:                                               ; preds = %14, %3
  %11 = phi ptr [ %9, %3 ], [ %17, %14 ]
  %12 = phi ptr [ %6, %3 ], [ %15, %14 ]
  %13 = icmp eq ptr %12, %7
  br i1 %13, label %18, label %14

14:                                               ; preds = %10
  %15 = getelementptr inbounds i8, ptr %12, i64 -1
  %16 = load i8, ptr %15, align 1, !tbaa !38, !noalias !183
  %17 = getelementptr inbounds i8, ptr %11, i64 -1
  store i8 %16, ptr %17, align 1, !tbaa !38, !noalias !183
  br label %10, !llvm.loop !188

18:                                               ; preds = %10
  %19 = ptrtoint ptr %11 to i64
  %20 = insertvalue [2 x i64] %2, i64 %19, 1
  ret [2 x i64] %20
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEE5clearB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(40) %0) local_unnamed_addr #19 comdat align 2 !type !189 {
  %2 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %0, i64 0, i32 1
  %3 = load ptr, ptr %2, align 8, !tbaa !180
  tail call void @_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEE17__destruct_at_endB7v170000EPh(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef %3) #34
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEE17__destruct_at_endB7v170000EPh(ptr noundef nonnull align 8 dereferenceable(40) %0, ptr noundef %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 !type !190 {
  %3 = getelementptr inbounds %"struct.std::__ndk1::__split_buffer", ptr %0, i64 0, i32 2
  %4 = load ptr, ptr %3, align 8, !tbaa !191
  br label %5

5:                                                ; preds = %8, %2
  %6 = phi ptr [ %9, %8 ], [ %4, %2 ]
  %7 = icmp eq ptr %6, %1
  br i1 %7, label %10, label %8

8:                                                ; preds = %5
  %9 = getelementptr inbounds i8, ptr %6, i64 -1
  store ptr %9, ptr %3, align 8, !tbaa !191
  br label %5

10:                                               ; preds = %5
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(184) %0, ptr noundef %1) unnamed_addr #2 comdat align 2 {
  %3 = load ptr, ptr %1, align 8
  store ptr %3, ptr %0, align 8, !tbaa !93
  %4 = getelementptr inbounds ptr, ptr %1, i64 3
  %5 = load ptr, ptr %4, align 8
  %6 = getelementptr i8, ptr %3, i64 -24
  %7 = load i64, ptr %6, align 8
  %8 = getelementptr inbounds i8, ptr %0, i64 %7
  store ptr %5, ptr %8, align 8, !tbaa !93
  %9 = getelementptr inbounds %"class.std::__ndk1::basic_ifstream", ptr %0, i64 0, i32 1
  tail call void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(163) %9) #34
  %10 = getelementptr inbounds ptr, ptr %1, i64 1
  tail call void @_ZNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(16) %0, ptr noundef nonnull %10) #34
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE15__addr_in_rangeB7v170000IRhEEbOT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(1) %1) local_unnamed_addr #19 comdat align 2 {
  %3 = load i8, ptr %0, align 8
  %4 = and i8 %3, 1
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %7 = load ptr, ptr %6, align 8
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %9 = select i1 %5, ptr %8, ptr %7
  %10 = icmp ule ptr %9, %1
  %11 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %12 = load i64, ptr %11, align 8
  %13 = lshr i8 %3, 1
  %14 = zext i8 %13 to i64
  %15 = select i1 %5, i64 %14, i64 %12
  %16 = getelementptr inbounds i8, ptr %9, i64 %15
  %17 = icmp uge ptr %16, %1
  %18 = select i1 %10, i1 %17, i1 false
  ret i1 %18
}

; Function Attrs: minsize mustprogress optsize uwtable
define available_externally noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #6 align 2 {
  %4 = icmp eq i64 %2, 0
  %5 = icmp ne ptr %1, null
  %6 = or i1 %5, %4
  tail call void @llvm.assume(i1 %6)
  %7 = tail call i1 @llvm.is.constant.i64(i64 %2)
  %8 = icmp ult i64 %2, 23
  %9 = and i1 %8, %7
  br i1 %9, label %10, label %12

10:                                               ; preds = %3
  %11 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE14__assign_shortEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) #35
  br label %14

12:                                               ; preds = %3
  %13 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_externalEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) #35
  br label %14

14:                                               ; preds = %12, %10
  %15 = phi ptr [ %11, %10 ], [ %13, %12 ]
  ret ptr %15
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initINS_11__wrap_iterIPhEEEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueEvE4typeESB_SB_(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 %1, i64 %2) local_unnamed_addr #27 comdat align 2 {
  %4 = inttoptr i64 %1 to ptr
  %5 = inttoptr i64 %2 to ptr
  %6 = sub i64 %2, %1
  %7 = icmp ugt i64 %6, -17
  br i1 %7, label %8, label %9

8:                                                ; preds = %3
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
  unreachable

9:                                                ; preds = %3
  %10 = icmp ult i64 %6, 23
  br i1 %10, label %11, label %15

11:                                               ; preds = %9
  %12 = trunc i64 %6 to i8
  %13 = shl nuw nsw i8 %12, 1
  store i8 %13, ptr %0, align 8
  %14 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  br label %25

15:                                               ; preds = %9
  %16 = or i64 %6, 15
  %17 = add i64 %16, 1
  %18 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %17) #35
  %19 = extractvalue [2 x i64] %18, 0
  %20 = inttoptr i64 %19 to ptr
  %21 = extractvalue [2 x i64] %18, 1
  %22 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  store ptr %20, ptr %22, align 8, !tbaa !38
  %23 = or i64 %21, 1
  store i64 %23, ptr %0, align 8
  %24 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %6, ptr %24, align 8, !tbaa !38
  br label %25

25:                                               ; preds = %15, %11
  %26 = phi ptr [ %14, %11 ], [ %20, %15 ]
  br label %27

27:                                               ; preds = %31, %25
  %28 = phi ptr [ %4, %25 ], [ %33, %31 ]
  %29 = phi ptr [ %26, %25 ], [ %34, %31 ]
  %30 = icmp eq ptr %28, %5
  br i1 %30, label %35, label %31

31:                                               ; preds = %27
  %32 = load i8, ptr %28, align 1, !tbaa !38
  store i8 %32, ptr %29, align 1, !tbaa !38
  %33 = getelementptr inbounds i8, ptr %28, i64 1
  %34 = getelementptr inbounds i8, ptr %29, i64 1
  br label %27, !llvm.loop !192

35:                                               ; preds = %27
  store i8 0, ptr %29, align 1, !tbaa !38
  ret void
}

; Function Attrs: inlinehint minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE14__assign_shortEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #24 comdat align 2 {
  %4 = load i8, ptr %0, align 8
  %5 = and i8 %4, 1
  %6 = icmp eq i8 %5, 0
  br i1 %6, label %11, label %7

7:                                                ; preds = %3
  %8 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %2, ptr %8, align 8, !tbaa !38
  %9 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %10 = load ptr, ptr %9, align 8, !tbaa !38
  br label %16

11:                                               ; preds = %3
  %12 = icmp ult i64 %2, 23
  tail call void @llvm.assume(i1 %12)
  %13 = trunc i64 %2 to i8
  %14 = shl nuw nsw i8 %13, 1
  store i8 %14, ptr %0, align 8
  %15 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  br label %16

16:                                               ; preds = %11, %7
  %17 = phi ptr [ %10, %7 ], [ %15, %11 ]
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %17, ptr align 1 %1, i64 %2, i1 false)
  %18 = getelementptr inbounds i8, ptr %17, i64 %2
  store i8 0, ptr %18, align 1, !tbaa !38
  ret ptr %0
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_externalEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #6 comdat align 2 {
  %4 = load i8, ptr %0, align 8
  %5 = and i8 %4, 1
  %6 = icmp eq i8 %5, 0
  %7 = load i64, ptr %0, align 8
  %8 = and i64 %7, -2
  %9 = add i64 %8, -1
  %10 = select i1 %6, i64 22, i64 %9
  %11 = icmp ult i64 %10, %2
  %12 = and i64 %7, 1
  %13 = icmp eq i64 %12, 0
  br i1 %11, label %30, label %14

14:                                               ; preds = %3
  %15 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  %16 = load ptr, ptr %15, align 8
  %17 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %0, i64 0, i32 2
  %18 = select i1 %13, ptr %17, ptr %16
  tail call void @llvm.memmove.p0.p0.i64(ptr align 1 %18, ptr align 1 %1, i64 %2, i1 false)
  %19 = load i8, ptr %0, align 8
  %20 = and i8 %19, 1
  %21 = icmp eq i8 %20, 0
  br i1 %21, label %24, label %22

22:                                               ; preds = %14
  %23 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %2, ptr %23, align 8, !tbaa !38
  br label %28

24:                                               ; preds = %14
  %25 = icmp ult i64 %2, 23
  tail call void @llvm.assume(i1 %25)
  %26 = trunc i64 %2 to i8
  %27 = shl nuw nsw i8 %26, 1
  store i8 %27, ptr %0, align 8
  br label %28

28:                                               ; preds = %24, %22
  %29 = getelementptr inbounds i8, ptr %18, i64 %2
  store i8 0, ptr %29, align 1, !tbaa !38
  br label %37

30:                                               ; preds = %3
  %31 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  %32 = load i64, ptr %31, align 8
  %33 = lshr i64 %7, 1
  %34 = and i64 %33, 127
  %35 = select i1 %13, i64 %34, i64 %32
  %36 = sub i64 %2, %10
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE21__grow_by_and_replaceEmmmmmmPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %10, i64 noundef %36, i64 noundef %35, i64 noundef 0, i64 noundef %35, i64 noundef %2, ptr noundef %1) #35
  br label %37

37:                                               ; preds = %30, %28
  ret ptr %0
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIhNS_9allocatorIhEEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = load ptr, ptr %0, align 8, !tbaa !193
  %3 = load ptr, ptr %2, align 8, !tbaa !127
  %4 = icmp eq ptr %3, null
  br i1 %4, label %7, label %5

5:                                                ; preds = %1
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.13", ptr %2, i64 0, i32 1
  store ptr %3, ptr %6, align 8, !tbaa !131
  tail call void @_ZdlPv(ptr noundef nonnull %3) #36
  br label %7

7:                                                ; preds = %5, %1
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i1 @_ZNSt6__ndk1eqB7v170000IcNS_11char_traitsIcEEEEbNS_17basic_string_viewIT_T0_EES6_([2 x i64] %0, [2 x i64] %1) local_unnamed_addr #19 comdat {
  %3 = alloca %"class.std::__ndk1::basic_string_view", align 8
  %4 = extractvalue [2 x i64] %0, 0
  store i64 %4, ptr %3, align 8
  %5 = extractvalue [2 x i64] %0, 1
  %6 = getelementptr inbounds [2 x i64], ptr %3, i64 0, i64 1
  store i64 %5, ptr %6, align 8
  %7 = extractvalue [2 x i64] %1, 1
  %8 = icmp eq i64 %5, %7
  br i1 %8, label %9, label %12

9:                                                ; preds = %2
  %10 = call noundef i32 @_ZNKSt6__ndk117basic_string_viewIcNS_11char_traitsIcEEE7compareES3_(ptr noundef nonnull align 8 dereferenceable(16) %3, [2 x i64] %1) #34
  %11 = icmp eq i32 %10, 0
  br label %12

12:                                               ; preds = %9, %2
  %13 = phi i1 [ %11, %9 ], [ false, %2 ]
  ret i1 %13
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

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef i32 @_ZNKSt6__ndk117basic_string_viewIcNS_11char_traitsIcEEE7compareES3_(ptr noundef nonnull align 8 dereferenceable(16) %0, [2 x i64] %1) local_unnamed_addr #19 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = extractvalue [2 x i64] %1, 0
  %4 = inttoptr i64 %3 to ptr
  %5 = extractvalue [2 x i64] %1, 1
  %6 = getelementptr inbounds %"class.std::__ndk1::basic_string_view", ptr %0, i64 0, i32 1
  %7 = load i64, ptr %6, align 8, !tbaa !195
  %8 = tail call i64 @llvm.umin.i64(i64 %5, i64 %7)
  %9 = load ptr, ptr %0, align 8, !tbaa !197
  %10 = tail call noundef i32 @_ZNSt6__ndk111char_traitsIcE7compareEPKcS3_m(ptr noundef %9, ptr noundef %4, i64 noundef %8) #34
  %11 = icmp eq i32 %10, 0
  br i1 %11, label %12, label %18

12:                                               ; preds = %2
  %13 = load i64, ptr %6, align 8, !tbaa !195
  %14 = icmp eq i64 %13, %5
  br i1 %14, label %18, label %15

15:                                               ; preds = %12
  %16 = icmp ult i64 %13, %5
  %17 = select i1 %16, i32 -1, i32 1
  br label %18

18:                                               ; preds = %15, %12, %2
  %19 = phi i32 [ %10, %2 ], [ %17, %15 ], [ 0, %12 ]
  ret i32 %19
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define internal fastcc noundef i1 @_ZN10file_utilsL17create_empty_fileEPKc(ptr noundef %0) unnamed_addr #27 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::basic_ofstream", align 8
  call void @llvm.lifetime.start.p0(i64 328, ptr nonnull %2) #37
  call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEC1EPKcj(ptr noundef nonnull align 8 dereferenceable(176) %2, ptr noundef %0, i32 noundef 52) #35
  %3 = getelementptr inbounds %"class.std::__ndk1::basic_ofstream", ptr %2, i64 0, i32 1, i32 8
  %4 = load ptr, ptr %3, align 8, !tbaa !149
  %5 = icmp ne ptr %4, null
  br i1 %5, label %8, label %9

6:                                                ; preds = %8
  %7 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(176) %2) #34
  call void @llvm.lifetime.end.p0(i64 328, ptr nonnull %2) #37
  resume { ptr, i32 } %7

8:                                                ; preds = %1
  invoke void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEE5closeEv(ptr noundef nonnull align 8 dereferenceable(176) %2) #35
          to label %9 unwind label %6

9:                                                ; preds = %8, %1
  call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(176) %2) #34
  call void @llvm.lifetime.end.p0(i64 328, ptr nonnull %2) #37
  ret i1 %5
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define internal fastcc noundef i1 @_ZN10file_utilsL28set_file_selinux_access_modeEPKcNS_15SelinuxFileFlagE(ptr noundef %0) unnamed_addr #27 {
  %2 = tail call i32 @chmod(ptr noundef %0, i32 noundef 511) #35
  %3 = icmp eq i32 %2, 0
  br i1 %3, label %4, label %7

4:                                                ; preds = %1
  %5 = tail call i32 @setxattr(ptr noundef %0, ptr noundef nonnull @.str.41, ptr noundef nonnull @.str.40, i64 noundef 21, i32 noundef 0) #35
  %6 = icmp eq i32 %5, 0
  br label %7

7:                                                ; preds = %4, %1
  %8 = phi i1 [ %6, %4 ], [ false, %1 ]
  ret i1 %8
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEC1EPKcj(ptr noundef nonnull align 8 dereferenceable(176) %0, ptr noundef %1, i32 noundef %2) unnamed_addr #3 comdat align 2 personality ptr @__gxx_personality_v0 {
  %4 = getelementptr inbounds i8, ptr %0, i64 176
  store ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE, i64 0, inrange i32 0, i64 3), ptr %0, align 8, !tbaa !93
  store ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE, i64 0, inrange i32 1, i64 3), ptr %4, align 8, !tbaa !93
  %5 = getelementptr inbounds %"class.std::__ndk1::basic_ofstream", ptr %0, i64 0, i32 1
  invoke void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEEC2B7v170000EPNS_15basic_streambufIcS2_EE(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef nonnull getelementptr inbounds ([4 x ptr], ptr @_ZTTNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE, i64 0, i64 1), ptr noundef nonnull %5) #35
          to label %6 unwind label %17

6:                                                ; preds = %3
  store ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE, i64 0, inrange i32 0, i64 3), ptr %0, align 8, !tbaa !93
  store ptr getelementptr inbounds ({ [5 x ptr], [5 x ptr] }, ptr @_ZTVNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE, i64 0, inrange i32 1, i64 3), ptr %4, align 8, !tbaa !93
  invoke void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEC2Ev(ptr noundef nonnull align 8 dereferenceable(163) %5) #35
          to label %7 unwind label %19

7:                                                ; preds = %6
  %8 = or i32 %2, 16
  %9 = invoke noundef ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE4openEPKcj(ptr noundef nonnull align 8 dereferenceable(163) %5, ptr noundef %1, i32 noundef %8) #35
          to label %10 unwind label %21

10:                                               ; preds = %7
  %11 = icmp eq ptr %9, null
  br i1 %11, label %12, label %23

12:                                               ; preds = %10
  %13 = load ptr, ptr %0, align 8, !tbaa !93
  %14 = getelementptr i8, ptr %13, i64 -24
  %15 = load i64, ptr %14, align 8
  %16 = getelementptr inbounds i8, ptr %0, i64 %15
  invoke void @_ZNSt6__ndk18ios_base8setstateB7v170000Ej(ptr noundef nonnull align 8 dereferenceable(136) %16, i32 noundef 4) #35
          to label %23 unwind label %21

17:                                               ; preds = %3
  %18 = landingpad { ptr, i32 }
          cleanup
  br label %26

19:                                               ; preds = %6
  %20 = landingpad { ptr, i32 }
          cleanup
  br label %24

21:                                               ; preds = %12, %7
  %22 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(163) %5) #34
  br label %24

23:                                               ; preds = %12, %10
  ret void

24:                                               ; preds = %21, %19
  %25 = phi { ptr, i32 } [ %22, %21 ], [ %20, %19 ]
  tail call void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef nonnull getelementptr inbounds ([4 x ptr], ptr @_ZTTNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE, i64 0, i64 1)) #34
  br label %26

26:                                               ; preds = %24, %17
  %27 = phi { ptr, i32 } [ %25, %24 ], [ %18, %17 ]
  tail call void @_ZNSt6__ndk19basic_iosIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(148) %4) #34
  resume { ptr, i32 } %27
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEE5closeEv(ptr noundef nonnull align 8 dereferenceable(176) %0) local_unnamed_addr #27 comdat align 2 {
  %2 = getelementptr inbounds %"class.std::__ndk1::basic_ofstream", ptr %0, i64 0, i32 1
  %3 = tail call noundef ptr @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEE5closeEv(ptr noundef nonnull align 8 dereferenceable(163) %2) #35
  %4 = icmp eq ptr %3, null
  br i1 %4, label %5, label %10

5:                                                ; preds = %1
  %6 = load ptr, ptr %0, align 8, !tbaa !93
  %7 = getelementptr i8, ptr %6, i64 -24
  %8 = load i64, ptr %7, align 8
  %9 = getelementptr inbounds i8, ptr %0, i64 %8
  tail call void @_ZNSt6__ndk18ios_base8setstateB7v170000Ej(ptr noundef nonnull align 8 dereferenceable(136) %9, i32 noundef 4) #35
  br label %10

10:                                               ; preds = %5, %1
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(176) %0) unnamed_addr #2 comdat align 2 {
  tail call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(176) %0, ptr noundef nonnull @_ZTTNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE) #34
  %2 = getelementptr inbounds i8, ptr %0, i64 176
  tail call void @_ZNSt6__ndk19basic_iosIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(148) %2) #34
  ret void
}

; Function Attrs: inlinehint minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEEC2B7v170000EPNS_15basic_streambufIcS2_EE(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef %1, ptr noundef %2) unnamed_addr #3 comdat align 2 {
  %4 = load ptr, ptr %1, align 8
  store ptr %4, ptr %0, align 8, !tbaa !93
  %5 = getelementptr inbounds ptr, ptr %1, i64 1
  %6 = load ptr, ptr %5, align 8
  %7 = getelementptr i8, ptr %4, i64 -24
  %8 = load i64, ptr %7, align 8
  %9 = getelementptr inbounds i8, ptr %0, i64 %8
  store ptr %6, ptr %9, align 8, !tbaa !93
  %10 = load ptr, ptr %0, align 8, !tbaa !93
  %11 = getelementptr i8, ptr %10, i64 -24
  %12 = load i64, ptr %11, align 8
  %13 = getelementptr inbounds i8, ptr %0, i64 %12
  tail call void @_ZNSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4initB7v170000EPNS_15basic_streambufIcS2_EE(ptr noundef nonnull align 8 dereferenceable(148) %13, ptr noundef %2) #35
  ret void
}

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(8), ptr noundef) unnamed_addr #15

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED0Ev(ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #15

; Function Attrs: minsize nounwind optsize uwtable
define available_externally void @_ZTv0_n24_NSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED1Ev(ptr noundef %0) unnamed_addr #4 align 2 {
  %2 = load ptr, ptr %0, align 8
  %3 = getelementptr inbounds i8, ptr %2, i64 -24
  %4 = load i64, ptr %3, align 8
  %5 = getelementptr inbounds i8, ptr %0, i64 %4
  tail call void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(8) %5) #34
  ret void
}

; Function Attrs: minsize nounwind optsize uwtable
define available_externally void @_ZTv0_n24_NSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED0Ev(ptr noundef %0) unnamed_addr #4 align 2 {
  %2 = load ptr, ptr %0, align 8
  %3 = getelementptr inbounds i8, ptr %2, i64 -24
  %4 = load i64, ptr %3, align 8
  %5 = getelementptr inbounds i8, ptr %0, i64 %4
  tail call void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED0Ev(ptr noundef nonnull align 8 dereferenceable(8) %5) #34
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED0Ev(ptr noundef nonnull align 8 dereferenceable(176) %0) unnamed_addr #2 comdat align 2 {
  tail call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(176) %0) #34
  tail call void @_ZdlPv(ptr noundef nonnull %0) #36
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZTv0_n24_NSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef %0) unnamed_addr #2 comdat align 2 {
  %2 = load ptr, ptr %0, align 8
  %3 = getelementptr inbounds i8, ptr %2, i64 -24
  %4 = load i64, ptr %3, align 8
  %5 = getelementptr inbounds i8, ptr %0, i64 %4
  tail call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(176) %5) #34
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZTv0_n24_NSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED0Ev(ptr noundef %0) unnamed_addr #2 comdat align 2 {
  %2 = load ptr, ptr %0, align 8
  %3 = getelementptr inbounds i8, ptr %2, i64 -24
  %4 = load i64, ptr %3, align 8
  %5 = getelementptr inbounds i8, ptr %0, i64 %4
  tail call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED0Ev(ptr noundef nonnull align 8 dereferenceable(176) %5) #34
  ret void
}

; Function Attrs: inlinehint minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(176) %0, ptr noundef %1) unnamed_addr #2 comdat align 2 {
  %3 = load ptr, ptr %1, align 8
  store ptr %3, ptr %0, align 8, !tbaa !93
  %4 = getelementptr inbounds ptr, ptr %1, i64 3
  %5 = load ptr, ptr %4, align 8
  %6 = getelementptr i8, ptr %3, i64 -24
  %7 = load i64, ptr %6, align 8
  %8 = getelementptr inbounds i8, ptr %0, i64 %7
  store ptr %5, ptr %8, align 8, !tbaa !93
  %9 = getelementptr inbounds %"class.std::__ndk1::basic_ofstream", ptr %0, i64 0, i32 1
  tail call void @_ZNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(163) %9) #34
  %10 = getelementptr inbounds ptr, ptr %1, i64 1
  tail call void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef nonnull %10) #34
  ret void
}

; Function Attrs: minsize nofree nounwind optsize
declare noundef i32 @chmod(ptr nocapture noundef readonly, i32 noundef) local_unnamed_addr #9

; Function Attrs: minsize optsize
declare i32 @setxattr(ptr noundef, ptr noundef, ptr noundef, i64 noundef, i32 noundef) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZNSt6__ndk14__fs10filesystem12__remove_allERKNS1_4pathEPNS_10error_codeE(ptr noundef nonnull align 8 dereferenceable(24), ptr noundef) local_unnamed_addr #7

; Function Attrs: minsize mustprogress optsize uwtable
define internal fastcc noundef i1 @_ZN10file_utilsL15write_text_fileEPKcNSt6__ndk117basic_string_viewIcNS2_11char_traitsIcEEEE(ptr noundef %0, [2 x i64] %1) unnamed_addr #6 personality ptr @__gxx_personality_v0 {
  %3 = alloca %"class.std::__ndk1::basic_ofstream", align 8
  %4 = icmp eq ptr %0, null
  br i1 %4, label %23, label %5

5:                                                ; preds = %2
  call void @llvm.lifetime.start.p0(i64 328, ptr nonnull %3) #37
  call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEC1EPKcj(ptr noundef nonnull align 8 dereferenceable(176) %3, ptr noundef nonnull %0, i32 noundef 48) #35
  %6 = getelementptr inbounds %"class.std::__ndk1::basic_ofstream", ptr %3, i64 0, i32 1, i32 8
  %7 = load ptr, ptr %6, align 8, !tbaa !149
  %8 = icmp eq ptr %7, null
  br i1 %8, label %21, label %11

9:                                                ; preds = %11
  %10 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(176) %3) #34
  call void @llvm.lifetime.end.p0(i64 328, ptr nonnull %3) #37
  resume { ptr, i32 } %10

11:                                               ; preds = %5
  %12 = invoke noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk1lsB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_NS_17basic_string_viewIS4_S5_EE(ptr noundef nonnull align 8 dereferenceable(8) %3, [2 x i64] %1) #35
          to label %13 unwind label %9

13:                                               ; preds = %11
  %14 = load ptr, ptr %3, align 8, !tbaa !93
  %15 = getelementptr i8, ptr %14, i64 -24
  %16 = load i64, ptr %15, align 8
  %17 = getelementptr inbounds i8, ptr %3, i64 %16
  %18 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %17, i64 0, i32 4
  %19 = load i32, ptr %18, align 8, !tbaa !125
  %20 = icmp eq i32 %19, 0
  br label %21

21:                                               ; preds = %13, %5
  %22 = phi i1 [ false, %5 ], [ %20, %13 ]
  call void @_ZNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEED1Ev(ptr noundef nonnull align 8 dereferenceable(176) %3) #34
  call void @llvm.lifetime.end.p0(i64 328, ptr nonnull %3) #37
  br label %23

23:                                               ; preds = %21, %2
  %24 = phi i1 [ %22, %21 ], [ false, %2 ]
  ret i1 %24
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA13_cvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(13) %1, i8 noundef %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  invoke void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA13_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(13) %1) #35
          to label %4 unwind label %5

4:                                                ; preds = %3
  ret void

5:                                                ; preds = %3
  %6 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #34
  resume { ptr, i32 } %6
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA13_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(13) %1) local_unnamed_addr #6 comdat align 2 {
  %3 = tail call noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA13_cPccLb1EE11__range_endB7v170000EPKc(ptr noundef nonnull %1) #35
  %4 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000IPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1, ptr noundef %3) #35
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA13_cPccLb1EE11__range_endB7v170000EPKc(ptr noundef %0) local_unnamed_addr #19 comdat align 2 {
  br label %2

2:                                                ; preds = %2, %1
  %3 = phi ptr [ %0, %1 ], [ %6, %2 ]
  %4 = load i8, ptr %3, align 1, !tbaa !38
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds i8, ptr %3, i64 1
  br i1 %5, label %7, label %2, !llvm.loop !198

7:                                                ; preds = %2
  ret ptr %3
}

; Function Attrs: minsize mustprogress optsize uwtable
define available_externally noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignEPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #6 align 2 personality ptr @__gxx_personality_v0 {
  %3 = icmp ne ptr %1, null
  tail call void @llvm.assume(i1 %3)
  %4 = load i8, ptr %1, align 1, !tbaa !38
  %5 = tail call i1 @llvm.is.constant.i8(i8 %4)
  br i1 %5, label %6, label %13

6:                                                ; preds = %2
  %7 = tail call i64 @strlen(ptr noundef nonnull dereferenceable(1) %1) #34
  %8 = icmp ult i64 %7, 23
  br i1 %8, label %9, label %11

9:                                                ; preds = %6
  %10 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE14__assign_shortEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1, i64 noundef %7) #35
  br label %15

11:                                               ; preds = %6
  %12 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_externalEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1, i64 noundef %7) #35
  br label %15

13:                                               ; preds = %2
  %14 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_externalEPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1) #35
  br label %15

15:                                               ; preds = %13, %11, %9
  %16 = phi ptr [ %14, %13 ], [ %10, %9 ], [ %12, %11 ]
  ret ptr %16
}

; Function Attrs: convergent nocallback nofree nosync nounwind willreturn memory(none)
declare i1 @llvm.is.constant.i8(i8) #23

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_externalEPKc(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = tail call i64 @strlen(ptr noundef nonnull dereferenceable(1) %1) #34
  %4 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE17__assign_externalEPKcm(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %1, i64 noundef %3) #35
  ret ptr %4
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk1lsB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_NS_17basic_string_viewIS4_S5_EE(ptr noundef nonnull align 8 dereferenceable(8) %0, [2 x i64] %1) local_unnamed_addr #6 comdat {
  %3 = extractvalue [2 x i64] %1, 0
  %4 = inttoptr i64 %3 to ptr
  %5 = extractvalue [2 x i64] %1, 1
  %6 = tail call noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk124__put_character_sequenceB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef %4, i64 noundef %5) #35
  ret ptr %6
}

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(8) ptr @_ZNSt6__ndk124__put_character_sequenceB7v170000IcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef %1, i64 noundef %2) local_unnamed_addr #1 comdat personality ptr @__gxx_personality_v0 {
  %4 = alloca %"class.std::__ndk1::basic_ostream<char>::sentry", align 8
  call void @llvm.lifetime.start.p0(i64 16, ptr nonnull %4) #37
  invoke void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryC1ERS3_(ptr noundef nonnull align 8 dereferenceable(16) %4, ptr noundef nonnull align 8 dereferenceable(8) %0) #35
          to label %5 unwind label %32

5:                                                ; preds = %3
  %6 = load i8, ptr %4, align 8, !tbaa !199, !range !100, !noundef !101
  %7 = icmp eq i8 %6, 0
  br i1 %7, label %38, label %8

8:                                                ; preds = %5
  %9 = load ptr, ptr %0, align 8, !tbaa !93
  %10 = getelementptr i8, ptr %9, i64 -24
  %11 = load i64, ptr %10, align 8
  %12 = getelementptr inbounds i8, ptr %0, i64 %11
  %13 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %12, i64 0, i32 6
  %14 = load ptr, ptr %13, align 8, !tbaa !201
  %15 = getelementptr inbounds %"class.std::__ndk1::ios_base", ptr %12, i64 0, i32 1
  %16 = load i32, ptr %15, align 8, !tbaa !202
  %17 = invoke noundef i8 @_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4fillB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(148) %12) #35
          to label %18 unwind label %36

18:                                               ; preds = %8
  %19 = and i32 %16, 176
  %20 = icmp eq i32 %19, 32
  %21 = getelementptr inbounds i8, ptr %1, i64 %2
  %22 = select i1 %20, ptr %21, ptr %1
  %23 = ptrtoint ptr %14 to i64
  %24 = invoke i64 @_ZNSt6__ndk116__pad_and_outputB7v170000IcNS_11char_traitsIcEEEENS_19ostreambuf_iteratorIT_T0_EES6_PKS4_S8_S8_RNS_8ios_baseES4_(i64 %23, ptr noundef %1, ptr noundef %22, ptr noundef %21, ptr noundef nonnull align 8 dereferenceable(136) %12, i8 noundef %17) #35
          to label %25 unwind label %36

25:                                               ; preds = %18
  %26 = icmp eq i64 %24, 0
  br i1 %26, label %27, label %38

27:                                               ; preds = %25
  %28 = load ptr, ptr %0, align 8, !tbaa !93
  %29 = getelementptr i8, ptr %28, i64 -24
  %30 = load i64, ptr %29, align 8
  %31 = getelementptr inbounds i8, ptr %0, i64 %30
  invoke void @_ZNSt6__ndk18ios_base8setstateB7v170000Ej(ptr noundef nonnull align 8 dereferenceable(136) %31, i32 noundef 5) #35
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
  call void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryD1Ev(ptr noundef nonnull align 8 dereferenceable(16) %4) #34
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #37
  br label %50

39:                                               ; preds = %36, %34
  %40 = phi { ptr, i32 } [ %35, %34 ], [ %37, %36 ]
  call void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryD1Ev(ptr noundef nonnull align 8 dereferenceable(16) %4) #34
  br label %41

41:                                               ; preds = %39, %32
  %42 = phi { ptr, i32 } [ %40, %39 ], [ %33, %32 ]
  %43 = extractvalue { ptr, i32 } %42, 0
  call void @llvm.lifetime.end.p0(i64 16, ptr nonnull %4) #37
  %44 = call ptr @__cxa_begin_catch(ptr %43) #37
  %45 = load ptr, ptr %0, align 8, !tbaa !93
  %46 = getelementptr i8, ptr %45, i64 -24
  %47 = load i64, ptr %46, align 8
  %48 = getelementptr inbounds i8, ptr %0, i64 %47
  invoke void @_ZNSt6__ndk18ios_base33__set_badbit_and_consider_rethrowEv(ptr noundef nonnull align 8 dereferenceable(136) %48) #35
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
  call void @__clang_call_terminate(ptr %56) #38
  unreachable
}

; Function Attrs: minsize optsize
declare void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryC1ERS3_(ptr noundef nonnull align 8 dereferenceable(16), ptr noundef nonnull align 8 dereferenceable(8)) unnamed_addr #7

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
  %15 = load i64, ptr %14, align 8, !tbaa !203
  %16 = icmp sgt i64 %15, %13
  %17 = sub nsw i64 %15, %13
  %18 = select i1 %16, i64 %17, i64 0
  %19 = ptrtoint ptr %2 to i64
  %20 = sub i64 %19, %12
  %21 = icmp sgt i64 %20, 0
  br i1 %21, label %22, label %28

22:                                               ; preds = %10
  %23 = load ptr, ptr %8, align 8, !tbaa !93
  %24 = getelementptr inbounds ptr, ptr %23, i64 12
  %25 = load ptr, ptr %24, align 8
  %26 = tail call noundef i64 %25(ptr noundef nonnull align 8 dereferenceable(64) %8, ptr noundef %1, i64 noundef %20) #35
  %27 = icmp eq i64 %26, %20
  br i1 %27, label %28, label %56

28:                                               ; preds = %22, %10
  %29 = icmp sgt i64 %18, 0
  br i1 %29, label %30, label %46

30:                                               ; preds = %28
  call void @llvm.lifetime.start.p0(i64 24, ptr nonnull %7) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initEmc(ptr noundef nonnull align 8 dereferenceable(24) %7, i64 noundef %18, i8 noundef %5) #35
  %31 = load i8, ptr %7, align 8
  %32 = and i8 %31, 1
  %33 = icmp eq i8 %32, 0
  %34 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %7, i64 0, i32 2
  %35 = load ptr, ptr %34, align 8
  %36 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__short", ptr %7, i64 0, i32 2
  %37 = select i1 %33, ptr %36, ptr %35
  %38 = load ptr, ptr %8, align 8, !tbaa !93
  %39 = getelementptr inbounds ptr, ptr %38, i64 12
  %40 = load ptr, ptr %39, align 8
  %41 = invoke noundef i64 %40(ptr noundef nonnull align 8 dereferenceable(64) %8, ptr noundef %37, i64 noundef %18) #35
          to label %42 unwind label %44

42:                                               ; preds = %30
  %43 = icmp eq i64 %41, %18
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #37
  br i1 %43, label %46, label %56

44:                                               ; preds = %30
  %45 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %7) #34
  call void @llvm.lifetime.end.p0(i64 24, ptr nonnull %7) #37
  resume { ptr, i32 } %45

46:                                               ; preds = %42, %28
  %47 = sub i64 %11, %19
  %48 = icmp sgt i64 %47, 0
  br i1 %48, label %49, label %55

49:                                               ; preds = %46
  %50 = load ptr, ptr %8, align 8, !tbaa !93
  %51 = getelementptr inbounds ptr, ptr %50, i64 12
  %52 = load ptr, ptr %51, align 8
  %53 = call noundef i64 %52(ptr noundef nonnull align 8 dereferenceable(64) %8, ptr noundef %2, i64 noundef %47) #35
  %54 = icmp eq i64 %53, %47
  br i1 %54, label %55, label %56

55:                                               ; preds = %49, %46
  store i64 0, ptr %14, align 8, !tbaa !203
  br label %56

56:                                               ; preds = %55, %49, %42, %22, %6
  %57 = phi i64 [ 0, %6 ], [ %0, %55 ], [ 0, %42 ], [ 0, %22 ], [ 0, %49 ]
  ret i64 %57
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i8 @_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE4fillB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(148) %0) local_unnamed_addr #27 comdat align 2 {
  %2 = getelementptr inbounds %"class.std::__ndk1::basic_ios", ptr %0, i64 0, i32 2
  %3 = load i32, ptr %2, align 8, !tbaa !157
  %4 = icmp eq i32 %3, -1
  br i1 %4, label %5, label %8

5:                                                ; preds = %1
  %6 = tail call noundef i8 @_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE5widenB7v170000Ec(ptr noundef nonnull align 8 dereferenceable(148) %0, i8 noundef 32) #35
  %7 = zext i8 %6 to i32
  store i32 %7, ptr %2, align 8, !tbaa !157
  br label %8

8:                                                ; preds = %5, %1
  %9 = phi i32 [ %7, %5 ], [ %3, %1 ]
  %10 = trunc i32 %9 to i8
  ret i8 %10
}

; Function Attrs: minsize nounwind optsize
declare void @_ZNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryD1Ev(ptr noundef nonnull align 8 dereferenceable(16)) unnamed_addr #15

; Function Attrs: minsize optsize
declare void @_ZNSt6__ndk18ios_base33__set_badbit_and_consider_rethrowEv(ptr noundef nonnull align 8 dereferenceable(136)) local_unnamed_addr #7

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define available_externally void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6__initEmc(ptr noundef nonnull align 8 dereferenceable(24) %0, i64 noundef %1, i8 noundef %2) local_unnamed_addr #27 align 2 personality ptr @__gxx_personality_v0 {
  %4 = icmp ugt i64 %1, -17
  br i1 %4, label %5, label %6

5:                                                ; preds = %3
  tail call void @_ZNKSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE20__throw_length_errorB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #42
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
  %15 = tail call [2 x i64] @_ZNSt6__ndk119__allocate_at_leastB7v170000INS_9allocatorIcEEEENS_19__allocation_resultINS_16allocator_traitsIT_E7pointerEEERS5_m(ptr noundef nonnull align 1 dereferenceable(1) %0, i64 noundef %14) #35
  %16 = extractvalue [2 x i64] %15, 0
  %17 = inttoptr i64 %16 to ptr
  %18 = extractvalue [2 x i64] %15, 1
  %19 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 2
  store ptr %17, ptr %19, align 8, !tbaa !38
  %20 = or i64 %18, 1
  store i64 %20, ptr %0, align 8
  %21 = getelementptr inbounds %"struct.std::__ndk1::basic_string<char>::__long", ptr %0, i64 0, i32 1
  store i64 %1, ptr %21, align 8, !tbaa !38
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
  store i8 %2, ptr %26, align 1, !tbaa !38
  %29 = getelementptr inbounds i8, ptr %26, i64 1
  %30 = add i64 %25, -1
  br label %24, !llvm.loop !204

31:                                               ; preds = %24
  %32 = getelementptr inbounds i8, ptr %23, i64 %1
  store i8 0, ptr %32, align 1, !tbaa !38
  ret void
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef i8 @_ZNKSt6__ndk19basic_iosIcNS_11char_traitsIcEEE5widenB7v170000Ec(ptr noundef nonnull align 8 dereferenceable(148) %0, i8 noundef %1) local_unnamed_addr #27 comdat align 2 personality ptr @__gxx_personality_v0 {
  %3 = alloca %"class.std::__ndk1::locale", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %3) #37
  call void @_ZNKSt6__ndk18ios_base6getlocEv(ptr nonnull sret(%"class.std::__ndk1::locale") align 8 %3, ptr noundef nonnull align 8 dereferenceable(136) %0) #35
  %4 = invoke noundef nonnull align 8 dereferenceable(25) ptr @_ZNSt6__ndk19use_facetB7v170000INS_5ctypeIcEEEERKT_RKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(8) %3) #35
          to label %5 unwind label %11

5:                                                ; preds = %2
  %6 = load ptr, ptr %4, align 8, !tbaa !93
  %7 = getelementptr inbounds ptr, ptr %6, i64 7
  %8 = load ptr, ptr %7, align 8
  %9 = invoke noundef i8 %8(ptr noundef nonnull align 8 dereferenceable(25) %4, i8 noundef %1) #35
          to label %10 unwind label %11

10:                                               ; preds = %5
  call void @_ZNSt6__ndk16localeD1Ev(ptr noundef nonnull align 8 dereferenceable(8) %3) #34
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #37
  ret i8 %9

11:                                               ; preds = %5, %2
  %12 = landingpad { ptr, i32 }
          cleanup
  call void @_ZNSt6__ndk16localeD1Ev(ptr noundef nonnull align 8 dereferenceable(8) %3) #34
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %3) #37
  resume { ptr, i32 } %12
}

; Function Attrs: inlinehint minsize mustprogress optsize uwtable
define linkonce_odr hidden noundef nonnull align 8 dereferenceable(25) ptr @_ZNSt6__ndk19use_facetB7v170000INS_5ctypeIcEEEERKT_RKNS_6localeE(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #27 comdat {
  %2 = tail call noundef ptr @_ZNKSt6__ndk16locale9use_facetERNS0_2idE(ptr noundef nonnull align 8 dereferenceable(8) %0, ptr noundef nonnull align 8 dereferenceable(12) @_ZNSt6__ndk15ctypeIcE2idE) #35
  ret ptr %2
}

; Function Attrs: minsize optsize
declare void @_ZNKSt6__ndk18ios_base6getlocEv(ptr sret(%"class.std::__ndk1::locale") align 8, ptr noundef nonnull align 8 dereferenceable(136)) local_unnamed_addr #7

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA23_cvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(23) %1, i8 noundef %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  invoke void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA23_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(23) %1) #35
          to label %4 unwind label %5

4:                                                ; preds = %3
  ret void

5:                                                ; preds = %3
  %6 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #34
  resume { ptr, i32 } %6
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA23_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(23) %1) local_unnamed_addr #6 comdat align 2 {
  %3 = tail call noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA23_cPccLb1EE11__range_endB7v170000EPKc(ptr noundef nonnull %1) #35
  %4 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000IPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1, ptr noundef %3) #35
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA23_cPccLb1EE11__range_endB7v170000EPKc(ptr noundef %0) local_unnamed_addr #19 comdat align 2 {
  br label %2

2:                                                ; preds = %2, %1
  %3 = phi ptr [ %0, %1 ], [ %6, %2 ]
  %4 = load i8, ptr %3, align 1, !tbaa !38
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds i8, ptr %3, i64 1
  br i1 %5, label %7, label %2, !llvm.loop !205

7:                                                ; preds = %2
  ret ptr %3
}

; Function Attrs: minsize nofree nounwind optsize
declare noalias noundef ptr @popen(ptr nocapture noundef readonly, ptr nocapture noundef readonly) local_unnamed_addr #9

; Function Attrs: minsize nofree nounwind optsize
declare noundef i32 @pclose(ptr nocapture noundef) local_unnamed_addr #9

; Function Attrs: minsize nofree optsize
declare i32 @execve(ptr noundef, ptr noundef, ptr noundef) local_unnamed_addr #20

; Function Attrs: minsize mustprogress nofree nounwind optsize willreturn memory(argmem: readwrite, inaccessiblemem: readwrite)
declare noalias ptr @strdup(ptr nocapture noundef readonly) local_unnamed_addr #29

; Function Attrs: minsize mustprogress nofree nounwind optsize willreturn allockind("alloc,zeroed") allocsize(0,1) memory(inaccessiblemem: readwrite)
declare noalias noundef ptr @calloc(i64 noundef, i64 noundef) local_unnamed_addr #30

; Function Attrs: minsize mustprogress nounwind optsize willreturn allockind("free") memory(argmem: readwrite, inaccessiblemem: readwrite)
declare void @free(ptr allocptr nocapture noundef) local_unnamed_addr #31

; Function Attrs: minsize mustprogress nounwind optsize willreturn allockind("realloc") allocsize(1) memory(argmem: readwrite, inaccessiblemem: readwrite)
declare noalias noundef ptr @realloc(ptr allocptr nocapture noundef, i64 noundef) local_unnamed_addr #32

; Function Attrs: minsize nofree nounwind optsize memory(argmem: readwrite)
declare ptr @__memset_chk(ptr noundef writeonly, i32 noundef, i64 noundef, i64 noundef) local_unnamed_addr #33

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IA5_cvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(5) %1, i8 noundef %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  invoke void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA5_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(5) %1) #35
          to label %4 unwind label %5

4:                                                ; preds = %3
  ret void

5:                                                ; preds = %3
  %6 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #34
  resume { ptr, i32 } %6
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IA5_cEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 1 dereferenceable(5) %1) local_unnamed_addr #6 comdat align 2 {
  %3 = tail call noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA5_cPccLb1EE11__range_endB7v170000EPKc(ptr noundef nonnull %1) #35
  %4 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000IPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull %1, ptr noundef %3) #35
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIA5_cPccLb1EE11__range_endB7v170000EPKc(ptr noundef %0) local_unnamed_addr #19 comdat align 2 {
  br label %2

2:                                                ; preds = %2, %1
  %3 = phi ptr [ %0, %1 ], [ %6, %2 ]
  %4 = load i8, ptr %3, align 1, !tbaa !38
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds i8, ptr %3, i64 1
  br i1 %5, label %7, label %2, !llvm.loop !206

7:                                                ; preds = %2
  ret ptr %3
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZN10skroot_env14fork_base_info10safe_closeERi(ptr noundef nonnull align 4 dereferenceable(4) %0) local_unnamed_addr #6 comdat align 2 {
  %2 = load i32, ptr %0, align 4, !tbaa !76
  %3 = icmp sgt i32 %2, -1
  br i1 %3, label %4, label %6

4:                                                ; preds = %1
  %5 = tail call i32 @close(i32 noundef %2) #35
  store i32 -1, ptr %0, align 4, !tbaa !76
  br label %6

6:                                                ; preds = %4, %1
  ret void
}

; Function Attrs: minsize optsize
declare i32 @close(i32 noundef) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env22unsafe_get_all_modulesERNSt6__ndk16vectorINS_13module_recordENS0_9allocatorIS2_EEEE(ptr noundef nonnull align 8 dereferenceable(24)) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env21safe_uninstall_moduleEPKc(ptr noundef) local_unnamed_addr #7

; Function Attrs: minsize optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem4pathC2B7v170000IPKcvEERKT_NS2_6formatE(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(8) %1, i8 noundef %2) unnamed_addr #1 comdat align 2 personality ptr @__gxx_personality_v0 {
  tail call void @llvm.memset.p0.i64(ptr noundef nonnull align 8 dereferenceable(24) %0, i8 0, i64 24, i1 false)
  invoke void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IPKcEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(8) %1) #35
          to label %4 unwind label %5

4:                                                ; preds = %3
  ret void

5:                                                ; preds = %3
  %6 = landingpad { ptr, i32 }
          cleanup
  tail call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) #34
  resume { ptr, i32 } %6
}

; Function Attrs: minsize optsize
declare noundef i64 @_ZN10skroot_env19unsafe_del_hide_dirEv() local_unnamed_addr #7

; Function Attrs: minsize nounwind optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEED2B7v170000Ev(ptr noundef nonnull align 8 dereferenceable(24) %0) unnamed_addr #4 comdat align 2 personality ptr @__gxx_personality_v0 {
  %2 = alloca %"class.std::__ndk1::vector<skroot_env::module_record>::__destroy_vector", align 8
  call void @llvm.lifetime.start.p0(i64 8, ptr nonnull %2) #37
  store ptr %0, ptr %2, align 8, !tbaa !42
  invoke void @_ZNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %2) #35
          to label %3 unwind label %4

3:                                                ; preds = %1
  call void @llvm.lifetime.end.p0(i64 8, ptr nonnull %2) #37
  ret void

4:                                                ; preds = %1
  %5 = landingpad { ptr, i32 }
          catch ptr null
  %6 = extractvalue { ptr, i32 } %5, 0
  call void @__clang_call_terminate(ptr %6) #38
  unreachable
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk14__fs10filesystem8_PathCVTIcE15__append_sourceB7v170000IPKcEEvRNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEERKT_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef nonnull align 8 dereferenceable(8) %1) local_unnamed_addr #6 comdat align 2 {
  %3 = load ptr, ptr %1, align 8, !tbaa !42
  %4 = tail call noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIPKcS4_cLb1EE11__range_endB7v170000ES4_(ptr noundef %3) #35
  %5 = tail call noundef nonnull align 8 dereferenceable(24) ptr @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6appendB7v170000IPKcEENS_9enable_ifIXsr27__is_cpp17_forward_iteratorIT_EE5valueERS5_E4typeESA_SA_(ptr noundef nonnull align 8 dereferenceable(24) %0, ptr noundef %3, ptr noundef %4) #35
  ret void
}

; Function Attrs: minsize mustprogress nounwind optsize uwtable
define linkonce_odr hidden noundef ptr @_ZNSt6__ndk14__fs10filesystem24__is_pathable_char_arrayIPKcS4_cLb1EE11__range_endB7v170000ES4_(ptr noundef %0) local_unnamed_addr #19 comdat align 2 {
  br label %2

2:                                                ; preds = %2, %1
  %3 = phi ptr [ %0, %1 ], [ %6, %2 ]
  %4 = load i8, ptr %3, align 1, !tbaa !38
  %5 = icmp eq i8 %4, 0
  %6 = getelementptr inbounds i8, ptr %3, i64 1
  br i1 %5, label %7, label %2, !llvm.loop !207

7:                                                ; preds = %2
  ret ptr %3
}

; Function Attrs: minsize mustprogress optsize uwtable
define linkonce_odr hidden void @_ZNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEE16__destroy_vectorclB7v170000Ev(ptr noundef nonnull align 8 dereferenceable(8) %0) local_unnamed_addr #6 comdat align 2 personality ptr @__gxx_personality_v0 !type !208 {
  %2 = load ptr, ptr %0, align 8, !tbaa !209
  %3 = load ptr, ptr %2, align 8, !tbaa !80
  %4 = icmp eq ptr %3, null
  br i1 %4, label %7, label %5

5:                                                ; preds = %1
  %6 = getelementptr inbounds %"class.std::__ndk1::vector.32", ptr %2, i64 0, i32 1
  store ptr %3, ptr %6, align 8, !tbaa !84
  tail call void @_ZdlPv(ptr noundef nonnull %3) #36
  br label %7

7:                                                ; preds = %5, %1
  ret void
}

; Function Attrs: minsize optsize
declare noundef i1 @_ZN10skroot_env20write_int_from_childERKNS_14fork_pipe_infoEi(ptr noundef nonnull align 4 dereferenceable(24), i32 noundef) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i1 @_ZN10skroot_env19read_int_from_childERKNS_14fork_pipe_infoERi(ptr noundef nonnull align 4 dereferenceable(24), ptr noundef nonnull align 4 dereferenceable(4)) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i1 @_ZN10skroot_env23write_string_from_childERKNS_14fork_pipe_infoERKNSt6__ndk112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEE(ptr noundef nonnull align 4 dereferenceable(24), ptr noundef nonnull align 8 dereferenceable(24)) local_unnamed_addr #7

; Function Attrs: minsize optsize
declare noundef i1 @_ZN10skroot_env22read_string_from_childERNS_14fork_pipe_infoERNSt6__ndk112basic_stringIcNS2_11char_traitsIcEENS2_9allocatorIcEEEE(ptr noundef nonnull align 4 dereferenceable(24), ptr noundef nonnull align 8 dereferenceable(24)) local_unnamed_addr #7

; Function Attrs: minsize optsize uwtable
define internal void @_GLOBAL__sub_I_skroot_installer.cpp() #1 section ".text.startup" personality ptr @__gxx_personality_v0 {
  %1 = alloca [3 x %"class.std::__ndk1::basic_string"], align 8
  %2 = alloca [5 x %"class.std::__ndk1::basic_string"], align 8
  %3 = alloca [5 x %"class.std::__ndk1::basic_string"], align 8
  call void @llvm.lifetime.start.p0(i64 120, ptr nonnull %3) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %3, ptr noundef nonnull @.str) #35
  %4 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 1
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %4, ptr noundef nonnull @.str.1) #35
          to label %5 unwind label %21

5:                                                ; preds = %0
  %6 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 2
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %6, ptr noundef nonnull @.str.2) #35
          to label %7 unwind label %21

7:                                                ; preds = %5
  %8 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 3
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %8, ptr noundef nonnull @.str.3) #35
          to label %9 unwind label %21

9:                                                ; preds = %7
  %10 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 4
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %10, ptr noundef nonnull @.str.4) #35
          to label %11 unwind label %21

11:                                               ; preds = %9
  %12 = ptrtoint ptr %3 to i64
  %13 = insertvalue [2 x i64] poison, i64 %12, 0
  %14 = insertvalue [2 x i64] %13, i64 5, 1
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEC2B7v170000ESt16initializer_listIS6_E(ptr noundef nonnull align 8 dereferenceable(24) @_ZN13kernel_module13export_symbolL27copy_from_user_symbol_namesE, [2 x i64] %14) #35
          to label %15 unwind label %28

15:                                               ; preds = %11
  %16 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %3, i64 5
  br label %17

17:                                               ; preds = %17, %15
  %18 = phi ptr [ %16, %15 ], [ %19, %17 ]
  %19 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %18, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %19) #34
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
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %26) #34
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
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %33) #34
  %34 = icmp eq ptr %33, %3
  br i1 %34, label %37, label %31

35:                                               ; preds = %103, %72, %37
  %36 = phi { ptr, i32 } [ %38, %37 ], [ %73, %72 ], [ %104, %103 ]
  resume { ptr, i32 } %36

37:                                               ; preds = %31, %24
  %38 = phi { ptr, i32 } [ %29, %31 ], [ %23, %24 ]
  call void @llvm.lifetime.end.p0(i64 120, ptr nonnull %3) #37
  br label %35

39:                                               ; preds = %17
  call void @llvm.lifetime.end.p0(i64 120, ptr nonnull %3) #37
  %40 = call i32 @__cxa_atexit(ptr nonnull @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev, ptr nonnull @_ZN13kernel_module13export_symbolL27copy_from_user_symbol_namesE, ptr nonnull @__dso_handle) #37
  call void @llvm.lifetime.start.p0(i64 120, ptr nonnull %2) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %2, ptr noundef nonnull @.str.6) #35
  %41 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 1
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %41, ptr noundef nonnull @.str.7) #35
          to label %42 unwind label %58

42:                                               ; preds = %39
  %43 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 2
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %43, ptr noundef nonnull @.str.8) #35
          to label %44 unwind label %58

44:                                               ; preds = %42
  %45 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 3
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %45, ptr noundef nonnull @.str.9) #35
          to label %46 unwind label %58

46:                                               ; preds = %44
  %47 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 4
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %47, ptr noundef nonnull @.str.10) #35
          to label %48 unwind label %58

48:                                               ; preds = %46
  %49 = ptrtoint ptr %2 to i64
  %50 = insertvalue [2 x i64] poison, i64 %49, 0
  %51 = insertvalue [2 x i64] %50, i64 5, 1
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEC2B7v170000ESt16initializer_listIS6_E(ptr noundef nonnull align 8 dereferenceable(24) @_ZN13kernel_module13export_symbolL25copy_to_user_symbol_namesE, [2 x i64] %51) #35
          to label %52 unwind label %65

52:                                               ; preds = %48
  %53 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %2, i64 5
  br label %54

54:                                               ; preds = %54, %52
  %55 = phi ptr [ %53, %52 ], [ %56, %54 ]
  %56 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %55, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %56) #34
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
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %63) #34
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
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %70) #34
  %71 = icmp eq ptr %70, %2
  br i1 %71, label %72, label %68

72:                                               ; preds = %68, %61
  %73 = phi { ptr, i32 } [ %66, %68 ], [ %60, %61 ]
  call void @llvm.lifetime.end.p0(i64 120, ptr nonnull %2) #37
  br label %35

74:                                               ; preds = %54
  call void @llvm.lifetime.end.p0(i64 120, ptr nonnull %2) #37
  %75 = call i32 @__cxa_atexit(ptr nonnull @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev, ptr nonnull @_ZN13kernel_module13export_symbolL25copy_to_user_symbol_namesE, ptr nonnull @__dso_handle) #37
  call void @llvm.lifetime.start.p0(i64 72, ptr nonnull %1) #37
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %1, ptr noundef nonnull @.str.12) #35
  %76 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %1, i64 1
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %76, ptr noundef nonnull @.str.13) #35
          to label %77 unwind label %89

77:                                               ; preds = %74
  %78 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %1, i64 2
  invoke void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEC2B7v170000IDnEEPKc(ptr noundef nonnull align 8 dereferenceable(24) %78, ptr noundef nonnull @.str.14) #35
          to label %79 unwind label %89

79:                                               ; preds = %77
  %80 = ptrtoint ptr %1 to i64
  %81 = insertvalue [2 x i64] poison, i64 %80, 0
  %82 = insertvalue [2 x i64] %81, i64 3, 1
  invoke void @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEC2B7v170000ESt16initializer_listIS6_E(ptr noundef nonnull align 8 dereferenceable(24) @_ZN13kernel_module13export_symbolL37copy_from_kernel_nofault_symbol_namesE, [2 x i64] %82) #35
          to label %83 unwind label %96

83:                                               ; preds = %79
  %84 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %1, i64 3
  br label %85

85:                                               ; preds = %85, %83
  %86 = phi ptr [ %84, %83 ], [ %87, %85 ]
  %87 = getelementptr inbounds %"class.std::__ndk1::basic_string", ptr %86, i64 -1
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %87) #34
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
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %94) #34
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
  call void @_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED2Ev(ptr noundef nonnull align 8 dereferenceable(24) %101) #34
  %102 = icmp eq ptr %101, %1
  br i1 %102, label %103, label %99

103:                                              ; preds = %99, %92
  %104 = phi { ptr, i32 } [ %97, %99 ], [ %91, %92 ]
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %1) #37
  br label %35

105:                                              ; preds = %85
  call void @llvm.lifetime.end.p0(i64 72, ptr nonnull %1) #37
  %106 = call i32 @__cxa_atexit(ptr nonnull @_ZNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEED2B7v170000Ev, ptr nonnull @_ZN13kernel_module13export_symbolL37copy_from_kernel_nofault_symbol_namesE, ptr nonnull @__dso_handle) #37
  ret void
}

; Function Attrs: nofree nounwind
declare noundef i32 @vsnprintf(ptr nocapture noundef, i64 noundef, ptr nocapture noundef readonly, ptr noundef) local_unnamed_addr #5

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.umin.i64(i64, i64) #28

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.umax.i64(i64, i64) #28

; Function Attrs: nocallback nofree nosync nounwind speculatable willreturn memory(none)
declare i64 @llvm.smax.i64(i64, i64) #28

attributes #0 = { nocallback nofree nosync nounwind willreturn memory(argmem: readwrite) }
attributes #1 = { minsize optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #2 = { inlinehint minsize nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #3 = { inlinehint minsize optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #4 = { minsize nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #5 = { nofree nounwind }
attributes #6 = { minsize mustprogress optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #7 = { minsize optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #8 = { minsize mustprogress nofree nounwind optsize willreturn memory(argmem: read) "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #9 = { minsize nofree nounwind optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #10 = { minsize mustprogress nofree nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #11 = { nocallback nofree nounwind willreturn memory(argmem: readwrite) }
attributes #12 = { nocallback nofree nosync nounwind willreturn memory(inaccessiblemem: readwrite) }
attributes #13 = { minsize mustprogress noreturn optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #14 = { inlinehint minsize mustprogress noreturn optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #15 = { minsize nounwind optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #16 = { minsize nobuiltin optsize allocsize(0) "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #17 = { minsize noinline noreturn nounwind optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #18 = { minsize nobuiltin nounwind optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #19 = { minsize mustprogress nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #20 = { minsize nofree optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #21 = { minsize noreturn optsize "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #22 = { nocallback nofree nounwind willreturn memory(argmem: write) }
attributes #23 = { convergent nocallback nofree nosync nounwind willreturn memory(none) }
attributes #24 = { inlinehint minsize mustprogress nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #25 = { inlinehint minsize mustprogress nofree nounwind optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #26 = { nocallback nofree nosync nounwind willreturn }
attributes #27 = { inlinehint minsize mustprogress optsize uwtable "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #28 = { nocallback nofree nosync nounwind speculatable willreturn memory(none) }
attributes #29 = { minsize mustprogress nofree nounwind optsize willreturn memory(argmem: readwrite, inaccessiblemem: readwrite) "alloc-family"="malloc" "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #30 = { minsize mustprogress nofree nounwind optsize willreturn allockind("alloc,zeroed") allocsize(0,1) memory(inaccessiblemem: readwrite) "alloc-family"="malloc" "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #31 = { minsize mustprogress nounwind optsize willreturn allockind("free") memory(argmem: readwrite, inaccessiblemem: readwrite) "alloc-family"="malloc" "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #32 = { minsize mustprogress nounwind optsize willreturn allockind("realloc") allocsize(1) memory(argmem: readwrite, inaccessiblemem: readwrite) "alloc-family"="malloc" "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #33 = { minsize nofree nounwind optsize memory(argmem: readwrite) "frame-pointer"="non-leaf" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+fix-cortex-a53-835769,+fp-armv8,+neon,+outline-atomics,+v8a" }
attributes #34 = { minsize nounwind optsize }
attributes #35 = { minsize optsize }
attributes #36 = { builtin minsize nounwind optsize }
attributes #37 = { nounwind }
attributes #38 = { noreturn nounwind }
attributes #39 = { minsize nounwind optsize willreturn memory(read) }
attributes #40 = { minsize optsize allocsize(0,1) }
attributes #41 = { minsize optsize allocsize(1) }
attributes #42 = { minsize noreturn optsize }
attributes #43 = { builtin minsize optsize allocsize(0) }
attributes #44 = { noreturn }

!llvm.linker.options = !{}
!llvm.module.flags = !{!32, !33, !34, !35, !36}
!llvm.ident = !{!37}

!0 = !{i64 24, !"_ZTSNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEEE"}
!1 = !{i64 24, !"_ZTSNSt6__ndk114basic_ifstreamIcNS_11char_traitsIcEEEE"}
!2 = !{i64 64, !"_ZTSNSt6__ndk18ios_baseE"}
!3 = !{i64 64, !"_ZTSNSt6__ndk19basic_iosIcNS_11char_traitsIcEEEE"}
!4 = !{i64 16, !"_ZTSNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEE"}
!5 = !{i64 32, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFvRKNS_6localeEE.virtual"}
!6 = !{i64 40, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFPNS_15basic_streambufIcS2_EEPclE.virtual"}
!7 = !{i64 48, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFNS_4fposI9mbstate_tEExNS_8ios_base7seekdirEjE.virtual"}
!8 = !{i64 56, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFNS_4fposI9mbstate_tEES6_jE.virtual"}
!9 = !{i64 64, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFivE.virtual"}
!10 = !{i64 72, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFlvE.virtual"}
!11 = !{i64 80, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFlPclE.virtual"}
!12 = !{i64 88, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFivE.virtual"}
!13 = !{i64 96, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFivE.virtual"}
!14 = !{i64 104, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFiiE.virtual"}
!15 = !{i64 112, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFlPKclE.virtual"}
!16 = !{i64 120, !"_ZTSMNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEEFiiE.virtual"}
!17 = !{i64 16, !"_ZTSNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEE"}
!18 = !{i64 32, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFvRKNS_6localeEE.virtual"}
!19 = !{i64 40, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFPS3_PclE.virtual"}
!20 = !{i64 48, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFNS_4fposI9mbstate_tEExNS_8ios_base7seekdirEjE.virtual"}
!21 = !{i64 56, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFNS_4fposI9mbstate_tEES6_jE.virtual"}
!22 = !{i64 64, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFivE.virtual"}
!23 = !{i64 72, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFlvE.virtual"}
!24 = !{i64 80, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFlPclE.virtual"}
!25 = !{i64 88, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFivE.virtual"}
!26 = !{i64 96, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFivE.virtual"}
!27 = !{i64 104, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFiiE.virtual"}
!28 = !{i64 112, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFlPKclE.virtual"}
!29 = !{i64 120, !"_ZTSMNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEEFiiE.virtual"}
!30 = !{i64 24, !"_ZTSNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEEE"}
!31 = !{i64 24, !"_ZTSNSt6__ndk114basic_ofstreamIcNS_11char_traitsIcEEEE"}
!32 = !{i32 1, !"wchar_size", i32 4}
!33 = !{i32 8, !"PIC Level", i32 2}
!34 = !{i32 7, !"uwtable", i32 2}
!35 = !{i32 7, !"frame-pointer", i32 1}
!36 = !{i32 1, !"EnableSplitLTOUnit", i32 0}
!37 = !{!"Android (11349228, based on r487747e) clang version 17.0.2 (https://android.googlesource.com/toolchain/llvm-project d9f89f4d16663d5012e5c09495f3b30ece3d2362)"}
!38 = !{!39, !39, i64 0}
!39 = !{!"omnipotent char", !40, i64 0}
!40 = !{!"Simple C++ TBAA"}
!41 = !{i64 0, i64 8, !42}
!42 = !{!43, !43, i64 0}
!43 = !{!"any pointer", !39, i64 0}
!44 = !{!45}
!45 = distinct !{!45, !46, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEEENS_17__exception_guardIT_EESC_: argument 0"}
!46 = distinct !{!46, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEEENS_17__exception_guardIT_EESC_"}
!47 = !{!48, !50, i64 8}
!48 = !{!"_ZTSNSt6__ndk117__exception_guardINS_6vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS5_IS7_EEE16__destroy_vectorEEE", !49, i64 0, !50, i64 8}
!49 = !{!"_ZTSNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEE16__destroy_vectorE", !43, i64 0}
!50 = !{!"bool", !39, i64 0}
!51 = !{!52, !52, i64 0}
!52 = !{!"_ZTS7KModErr", !39, i64 0}
!53 = !{!54}
!54 = distinct !{!54, !55, !"_ZN10skroot_envL24get_log_enable_flag_pathEv: argument 0"}
!55 = distinct !{!55, !"_ZN10skroot_envL24get_log_enable_flag_pathEv"}
!56 = !{!57, !54}
!57 = distinct !{!57, !58, !"_ZN10skroot_envL19get_skroot_log_pathEv: argument 0"}
!58 = distinct !{!58, !"_ZN10skroot_envL19get_skroot_log_pathEv"}
!59 = !{!60}
!60 = distinct !{!60, !61, !"_ZN10skroot_envL38get_boot_fail_protect_enable_flag_pathEv: argument 0"}
!61 = distinct !{!61, !"_ZN10skroot_envL38get_boot_fail_protect_enable_flag_pathEv"}
!62 = !{!63}
!63 = distinct !{!63, !64, !"_ZN10skroot_envL28get_skroot_install_mode_pathEv: argument 0"}
!64 = distinct !{!64, !"_ZN10skroot_envL28get_skroot_install_mode_pathEv"}
!65 = !{!66, !67, i64 0}
!66 = !{!"_ZTSN10skroot_env13InstallConfigE", !67, i64 0}
!67 = !{!"_ZTSN10skroot_env13ExploitMethodE", !39, i64 0}
!68 = !{!69}
!69 = distinct !{!69, !70, !"_ZN10skroot_envL38get_skroot_install_config_exploit_pathEv: argument 0"}
!70 = distinct !{!70, !"_ZN10skroot_envL38get_skroot_install_config_exploit_pathEv"}
!71 = distinct !{!71, !72}
!72 = !{!"llvm.loop.mustprogress"}
!73 = distinct !{!73, !72}
!74 = distinct !{!74, !72}
!75 = distinct !{!75, !72}
!76 = !{!77, !77, i64 0}
!77 = !{!"int", !39, i64 0}
!78 = !{!79, !77, i64 20}
!79 = !{!"_ZTSN10skroot_env14fork_base_infoE", !77, i64 0, !77, i64 4, !77, i64 8, !77, i64 12, !77, i64 16, !77, i64 20}
!80 = !{!81, !43, i64 0}
!81 = !{!"_ZTSNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEEE", !43, i64 0, !43, i64 8, !82, i64 16}
!82 = !{!"_ZTSNSt6__ndk117__compressed_pairIPN10skroot_env13module_recordENS_9allocatorIS2_EEEE", !83, i64 0}
!83 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPN10skroot_env13module_recordELi0ELb0EEE", !43, i64 0}
!84 = !{!81, !43, i64 8}
!85 = !{!86, !77, i64 0}
!86 = !{!"_ZTSNSt6__ndk110error_codeE", !77, i64 0, !43, i64 8}
!87 = !{!86, !43, i64 8}
!88 = !{i32 0, i32 4}
!89 = !{!90, !77, i64 0}
!90 = !{!"_ZTSN10skroot_env16SkrootSdkVersionE", !77, i64 0, !77, i64 4, !77, i64 8}
!91 = !{!90, !77, i64 4}
!92 = !{!90, !77, i64 8}
!93 = !{!94, !94, i64 0}
!94 = !{!"vtable pointer", !40, i64 0}
!95 = !{!96, !43, i64 0}
!96 = !{!"_ZTSNSt6__ndk16vectorINS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEE", !43, i64 0, !43, i64 8, !97, i64 16}
!97 = !{!"_ZTSNSt6__ndk117__compressed_pairIPNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEENS4_IS6_EEEE", !98, i64 0}
!98 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPNS_12basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEEELi0ELb0EEE", !43, i64 0}
!99 = !{!96, !43, i64 8}
!100 = !{i8 0, i8 2}
!101 = !{}
!102 = !{!103, !50, i64 24}
!103 = !{!"_ZTSNSt6__ndk117__exception_guardINS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEEE", !104, i64 0, !50, i64 24}
!104 = !{!"_ZTSNSt6__ndk129_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPS6_EE", !43, i64 0, !43, i64 8, !43, i64 16}
!105 = !{!106}
!106 = distinct !{!106, !107, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEEENS_17__exception_guardIT_EESC_: argument 0"}
!107 = distinct !{!107, !"_ZNSt6__ndk122__make_exception_guardB7v170000INS_29_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS2_IcEEEEEEPS7_EEEENS_17__exception_guardIT_EESC_"}
!108 = distinct !{!108, !72}
!109 = !{i64 0, i64 8, !110, i64 0, i64 8, !110, i64 8, i64 8, !110, i64 16, i64 8, !42, i64 0, i64 1, !38, i64 0, i64 1, !38, i64 1, i64 0, !38, i64 1, i64 23, !38, i64 0, i64 24, !38}
!110 = !{!111, !111, i64 0}
!111 = !{!"long", !39, i64 0}
!112 = !{i64 0, !"_ZTSMNSt6__ndk129_AllocatorDestroyRangeReverseINS_9allocatorINS_12basic_stringIcNS_11char_traitsIcEENS1_IcEEEEEEPS6_EEKFvvE"}
!113 = !{!104, !43, i64 0}
!114 = !{!104, !43, i64 16}
!115 = !{!104, !43, i64 8}
!116 = distinct !{!116, !72}
!117 = !{!49, !43, i64 0}
!118 = !{i64 0, !"_ZTSMN10skroot_env14fork_base_infoEFvvE"}
!119 = !{!79, !77, i64 16}
!120 = distinct !{!120, !72}
!121 = distinct !{!121, !72}
!122 = !{!123}
!123 = distinct !{!123, !124, !"_ZN10skroot_envL44get_autorun_bootstrap_work_boot_session_pathEv: argument 0"}
!124 = distinct !{!124, !"_ZN10skroot_envL44get_autorun_bootstrap_work_boot_session_pathEv"}
!125 = !{!126, !77, i64 32}
!126 = !{!"_ZTSNSt6__ndk18ios_baseE", !77, i64 8, !111, i64 16, !111, i64 24, !77, i64 32, !77, i64 36, !43, i64 40, !43, i64 48, !43, i64 56, !43, i64 64, !111, i64 72, !111, i64 80, !43, i64 88, !111, i64 96, !111, i64 104, !43, i64 112, !111, i64 120, !111, i64 128}
!127 = !{!128, !43, i64 0}
!128 = !{!"_ZTSNSt6__ndk16vectorIhNS_9allocatorIhEEEE", !43, i64 0, !43, i64 8, !129, i64 16}
!129 = !{!"_ZTSNSt6__ndk117__compressed_pairIPhNS_9allocatorIhEEEE", !130, i64 0}
!130 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPhLi0ELb0EEE", !43, i64 0}
!131 = !{!128, !43, i64 8}
!132 = distinct !{!132, !72}
!133 = !{!134}
!134 = distinct !{!134, !135, !"_ZN10skroot_envL24get_kernel_file_dir_nameEv: argument 0"}
!135 = distinct !{!135, !"_ZN10skroot_envL24get_kernel_file_dir_nameEv"}
!136 = !{i64 0, i64 8, !42, i64 8, i64 8, !42, i64 16, i64 8, !42, i64 24, i64 4, !76, i64 28, i64 4, !76}
!137 = distinct !{!137, !72}
!138 = distinct !{!138, !72}
!139 = distinct !{!139, !72}
!140 = distinct !{!140, !72}
!141 = !{!142, !111, i64 8}
!142 = !{!"_ZTSNSt6__ndk113basic_istreamIcNS_11char_traitsIcEEEE", !111, i64 8}
!143 = !{!144, !43, i64 128}
!144 = !{!"_ZTSNSt6__ndk113basic_filebufIcNS_11char_traitsIcEEEE", !145, i64 0, !43, i64 64, !43, i64 72, !43, i64 80, !39, i64 88, !111, i64 96, !43, i64 104, !111, i64 112, !43, i64 120, !43, i64 128, !147, i64 136, !147, i64 144, !77, i64 152, !77, i64 156, !50, i64 160, !50, i64 161, !50, i64 162}
!145 = !{!"_ZTSNSt6__ndk115basic_streambufIcNS_11char_traitsIcEEEE", !146, i64 8, !43, i64 16, !43, i64 24, !43, i64 32, !43, i64 40, !43, i64 48, !43, i64 56}
!146 = !{!"_ZTSNSt6__ndk16localeE", !43, i64 0}
!147 = !{!"_ZTS9mbstate_t", !39, i64 0, !39, i64 4}
!148 = !{!144, !50, i64 162}
!149 = !{!144, !43, i64 120}
!150 = !{!144, !77, i64 152}
!151 = !{!144, !50, i64 160}
!152 = !{!144, !43, i64 64}
!153 = !{!144, !50, i64 161}
!154 = !{!144, !43, i64 104}
!155 = !{!156, !43, i64 136}
!156 = !{!"_ZTSNSt6__ndk19basic_iosIcNS_11char_traitsIcEEEE", !126, i64 0, !43, i64 136, !77, i64 144}
!157 = !{!156, !77, i64 144}
!158 = !{!144, !111, i64 112}
!159 = !{!144, !111, i64 96}
!160 = !{i64 0, i64 4, !38, i64 4, i64 4, !38}
!161 = !{!144, !77, i64 156}
!162 = !{!145, !43, i64 48}
!163 = !{!145, !43, i64 40}
!164 = !{!145, !43, i64 32}
!165 = !{!145, !43, i64 24}
!166 = !{!144, !43, i64 80}
!167 = !{!144, !43, i64 72}
!168 = !{!145, !43, i64 16}
!169 = !{!145, !43, i64 56}
!170 = distinct !{!170, !72}
!171 = !{!172, !43, i64 0}
!172 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIP7__sFILELi0ELb0EEE", !43, i64 0}
!173 = !{!174, !43, i64 0}
!174 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIPFiP7__sFILEELi1ELb0EEE", !43, i64 0}
!175 = !{i64 0, !"_ZTSMNSt6__ndk110unique_ptrI7__sFILEPFiPS1_EEEDoFvS2_E"}
!176 = !{!177, !43, i64 0}
!177 = !{!"_ZTSNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEEE", !43, i64 0, !43, i64 8, !43, i64 16, !178, i64 24}
!178 = !{!"_ZTSNSt6__ndk117__compressed_pairIPhRNS_9allocatorIhEEEE", !130, i64 0, !179, i64 8}
!179 = !{!"_ZTSNSt6__ndk122__compressed_pair_elemIRNS_9allocatorIhEELi1ELb0EEE", !43, i64 0}
!180 = !{!177, !43, i64 8}
!181 = distinct !{!181, !72}
!182 = distinct !{!182, !72}
!183 = !{!184, !186}
!184 = distinct !{!184, !185, !"_ZNKSt6__ndk111__move_loopINS_17_ClassicAlgPolicyEEclB7v170000INS_16reverse_iteratorIPhEES6_S6_EENS_4pairIT_T1_EES8_T0_S9_: argument 0"}
!185 = distinct !{!185, !"_ZNKSt6__ndk111__move_loopINS_17_ClassicAlgPolicyEEclB7v170000INS_16reverse_iteratorIPhEES6_S6_EENS_4pairIT_T1_EES8_T0_S9_"}
!186 = distinct !{!186, !187, !"_ZNSt6__ndk121__unwrap_and_dispatchB7v170000INS_10__overloadINS_11__move_loopINS_17_ClassicAlgPolicyEEENS_14__move_trivialEEENS_16reverse_iteratorIPhEES9_S9_Li0EEENS_4pairIT0_T2_EESB_T1_SC_: argument 0"}
!187 = distinct !{!187, !"_ZNSt6__ndk121__unwrap_and_dispatchB7v170000INS_10__overloadINS_11__move_loopINS_17_ClassicAlgPolicyEEENS_14__move_trivialEEENS_16reverse_iteratorIPhEES9_S9_Li0EEENS_4pairIT0_T2_EESB_T1_SC_"}
!188 = distinct !{!188, !72}
!189 = !{i64 0, !"_ZTSMNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEEEDoFvvE"}
!190 = !{i64 0, !"_ZTSMNSt6__ndk114__split_bufferIhRNS_9allocatorIhEEEEDoFvPhE"}
!191 = !{!177, !43, i64 16}
!192 = distinct !{!192, !72}
!193 = !{!194, !43, i64 0}
!194 = !{!"_ZTSNSt6__ndk16vectorIhNS_9allocatorIhEEE16__destroy_vectorE", !43, i64 0}
!195 = !{!196, !111, i64 8}
!196 = !{!"_ZTSNSt6__ndk117basic_string_viewIcNS_11char_traitsIcEEEE", !43, i64 0, !111, i64 8}
!197 = !{!196, !43, i64 0}
!198 = distinct !{!198, !72}
!199 = !{!200, !50, i64 0}
!200 = !{!"_ZTSNSt6__ndk113basic_ostreamIcNS_11char_traitsIcEEE6sentryE", !50, i64 0, !43, i64 8}
!201 = !{!126, !43, i64 40}
!202 = !{!126, !77, i64 8}
!203 = !{!126, !111, i64 24}
!204 = distinct !{!204, !72}
!205 = distinct !{!205, !72}
!206 = distinct !{!206, !72}
!207 = distinct !{!207, !72}
!208 = !{i64 0, !"_ZTSMNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEE16__destroy_vectorEFvvE"}
!209 = !{!210, !43, i64 0}
!210 = !{!"_ZTSNSt6__ndk16vectorIN10skroot_env13module_recordENS_9allocatorIS2_EEE16__destroy_vectorE", !43, i64 0}

declare i32 @maplesu_environment_state()
declare i32 @maplesu_wait_environment_child(i32, ptr)
define hidden void @maplesu_environment_paths(ptr %helper, ptr %version) {
  call fastcc void @_ZN10skroot_envL26get_autorun_bootstrap_pathEv(ptr sret(%"class.std::__ndk1::basic_string") align 8 %helper)
  call fastcc void @_ZN10skroot_envL23get_skroot_sdk_ver_pathEv(ptr sret(%"class.std::__ndk1::basic_string") align 8 %version)
  ret void
}

declare i64 @maplesu_installer_launch_error(i32, ptr)
declare i32 @maplesu_installer_exec(ptr, ptr, ptr)
declare i32 @maplesu_installer_wait_child(i32, ptr, ptr)

declare i64 @maplesu_installer_result_open(i32, ptr)
declare i64 @maplesu_installer_result_parent(i32, i32, ptr)
declare void @maplesu_installer_result_worker(ptr)
declare i32 @maplesu_installer_result_complete(ptr, ptr, i32)
