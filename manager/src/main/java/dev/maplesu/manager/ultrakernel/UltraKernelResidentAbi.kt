package dev.maplesu.manager.ultrakernel

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/** Verifies represented DDK layout/config facts against this exact target.
 * BTF size/member facts do not prove C over-alignment or all inline semantics. */
internal object UltraKernelResidentAbi {
    data class CallbackOrigin(val kind: String, val source: String, val signature: String,
                              val secondarySource: String? = null, val parameterIndex: Int? = null)
    data class CompiledMember(val offset: Int, val bytes: Int, val signature: String)
    data class CompiledLayout(val bytes: Int, val signature: String, val members: Map<String, CompiledMember>)
    data class BoundField(val compiledOffset: Int, val compiledBytes: Int, val compiledOwnerBytes: Int,
        val target: UltraKernelLayoutField)
    private fun fieldMatches(key: String, actual: UltraKernelLayoutField?, offset: Int, width: Int,
        bound: Map<String, BoundField>): Boolean {
        val binding = bound[key] ?: bound.entries.filter { key.startsWith(it.key + ".") }
            .maxByOrNull { it.key.length }?.value
        val exact = key in setOf("task_struct.thread_info", "thread_info.preempt_count") ||
            key.startsWith("task_struct.thread_info.")
        require(binding == null || if (key in bound) binding.compiledOffset == offset && binding.compiledBytes == width
            else offset >= binding.compiledOffset && offset.toLong() + width <= binding.compiledOffset.toLong() + binding.compiledBytes) {
            "ULTRA_KERNEL_RUNTIME_LAYOUT_COMPILED_FIELD:$key"
        }
        val target = if (binding == null || exact) offset else binding.target.offsetBytes + offset - binding.compiledOffset
        return actual != null && actual.widthBytes == width && actual.offsetBytes == target &&
            (binding == null || actual.ownerSizeBytes == binding.target.ownerSizeBytes)
    }
    // These objects are borrowed through kernel pointers. Embedded locks, work items and copied
    // value objects keep the existing exact size and layout checks.
    private val runtimeOwnerTypes = setOf("task_struct", "cred", "mm_struct", "file", "inode", "dentry",
        "super_block", "file_operations", "sighand_struct", "seccomp", "signal_struct")
    private fun ownerSize(type: String, compiled: Int, members: Collection<String>, bound: Map<String, BoundField>): Int {
        val keys = members.filter { it.substringBefore('.') == type && it in bound }
        if (type !in runtimeOwnerTypes || keys.isEmpty()) return compiled
        val bindings = keys.map { bound.getValue(it) }
        require(bindings.all { it.compiledOwnerBytes == compiled }) { "ULTRA_KERNEL_RUNTIME_LAYOUT_COMPILED_SIZE:$type" }
        val sizes = bindings.map { it.target.ownerSizeBytes }.distinct()
        require(sizes.size == 1 && sizes.single() != null) { "ULTRA_KERNEL_RUNTIME_LAYOUT_TARGET_SIZE:$type" }
        return sizes.single()!!
    }
    data class UidSnapshotFacts(val compiledFacts: Map<String, Int>,
        val compiledLayouts: Map<String, CompiledLayout>, val config: Map<String, String>,
        val callbackOrigins: Map<String, CallbackOrigin>)
    data class ProcattrFacts(val layoutSchema: Int, val layoutWords: Int, val layoutSha256: String,
        val compiledFacts: Map<String, Int>, val compiledLayouts: Map<String, CompiledLayout>,
        val typeAlignments: Map<String, Int>, val config: Map<String, String>,
        val functions: Map<String, String>, val writeSignature: String?,
        val cfiMode: String?, val normalizeIntegers: Boolean?, val markerTypeid: Int?)
    data class SupplementalFacts(val layoutSchema: Int, val layoutWords: Int,
        val compiledLayoutSha256: String, val config: Map<String, String> = emptyMap(),
        val constants: Map<String, Int> = emptyMap(), val typeAlignments: Map<String, Int> = emptyMap(),
        val targetQualified: Boolean = false, val callbackOrigins: Map<String, CallbackOrigin> = emptyMap(),
        val sourceQualification: UltraKernelManagerClaimPayload.SourceQualification? = null)
    data class ManagementFacts(val layoutSchema: Int, val layoutWords: Int,
        val config: Map<String, String>, val constants: Map<String, Int>,
        val typeAlignments: Map<String, Int>, val callbackOrigins: Map<String, CallbackOrigin>,
        val enabledFeatures: Set<String>, val gateVerified: Boolean,
        val wireLayout: Map<String, Int> = emptyMap(), val uidSnapshot: UidSnapshotFacts? = null,
        val procattr: SupplementalFacts? = null, val taskState: SupplementalFacts? = null,
        val procattrNative: ProcattrFacts? = null,
        val sourceQualification: UltraKernelManagerClaimPayload.SourceQualification? = null)
    private val fsnotifyCallbacks = setOf("fsnotify_ops.handle_inode_event",
        "fsnotify_ops.freeing_mark", "fsnotify_ops.free_mark")
    private val managementCallbacks = setOf("sched_process_exit", "sched_process_exec", "work_struct.func",
        "msu_publication_cleanup", "msu_credential_replace_current.validate")
    private val uidCallbacks = setOf("msu_uid_snapshot_reclaim_rcu", "msu_uid_snapshot_reclaim_work",
        "msu_uid_gateway_dispatch.validate")
    private val taskCallbacks = setOf("msu_task_state_release_current.validate")
    private fun validOrigins(origins: Set<String>) = origins.isEmpty() || origins == managementCallbacks ||
        origins == managementCallbacks + uidCallbacks || origins == managementCallbacks + taskCallbacks ||
        origins == managementCallbacks + uidCallbacks + taskCallbacks
    private val cfiConfigs = setOf("CONFIG_CFI", "CONFIG_CFI_CLANG", "CONFIG_CFI_ICALL_NORMALIZE_INTEGERS")
    private val sessionConfigKeys = setOf("CONFIG_COMPAT")

    fun verifyCfi(kernel: ByteArray, mode: String, normalizeIntegers: Boolean) {
        val config = targetConfig(kernel)
        val enabled = config.getValue("CONFIG_CFI") == "y" || config.getValue("CONFIG_CFI_CLANG") == "y"
        require((mode == "kcfi") == enabled) { "ULTRA_KERNEL_RESIDENT_CFI_TARGET_MODE" }
        require(!enabled || normalizeIntegers == (config.getValue("CONFIG_CFI_ICALL_NORMALIZE_INTEGERS") == "y")) {
            "ULTRA_KERNEL_RESIDENT_CFI_TARGET_NORMALIZATION"
        }
    }
    fun verifySession(kernel: ByteArray, symbols: UltraKernelSymbols,
        facts: UltraKernelManagerClaimPayload.SessionAbiFacts,
        legacyCallerTypes: Map<String, Set<String>>? = null) {
        val group = UltraKernelBtf.selectedStructure(kernel, symbols, "group_info", setOf("usage", "ngroups"))
            ?: error("ULTRA_KERNEL_SESSION_ABI_GROUP_BTF_REQUIRED")
        require(group == facts.groupInfo &&
            UltraKernelBtf.typeSignature(kernel, symbols, "group_info") == facts.groupInfo.signature) {
            "ULTRA_KERNEL_SESSION_ABI_GROUP_EXACT"
        }
        val members = facts.fileOperations.members.keys
        val operations = UltraKernelBtf.selectedStructure(kernel, symbols, "file_operations", members)
            ?: error("ULTRA_KERNEL_SESSION_ABI_FOPS_BTF_REQUIRED")
        require(operations.bytes == facts.fileOperations.bytes && operations.members == facts.fileOperations.members) {
            "ULTRA_KERNEL_SESSION_ABI_FOPS_EXACT"
        }
        require(("compat_ioctl" in members) == (targetConfig(kernel).getValue("CONFIG_COMPAT") == "y")) {
            "ULTRA_KERNEL_SESSION_ABI_COMPAT_TARGET"
        }
        for ((name, callback) in facts.callbacks) {
            val owner = if ('.' in callback.member) callback.member.substringBefore('.') else "file_operations"
            val member = callback.member.substringAfter('.')
            require(UltraKernelBtf.memberSignature(kernel, symbols, owner, member) == "*" + callback.signature) {
                "ULTRA_KERNEL_SESSION_ABI_CALLBACK_TARGET:$name"
            }
        }
        if (facts.cfiMode == "legacy-lto-cfi") {
            require(UltraKernelLegacyModuleRoute.required(kernel) && !legacyCallerTypes.isNullOrEmpty()) {
                "ULTRA_KERNEL_SESSION_ABI_LEGACY_CONTRACT"
            }
            for ((caller, expected) in legacyCallerTypes) require(expected.isNotEmpty() &&
                UltraKernelLegacyCfi.slowpathCallsites(kernel, symbols, caller).map { it.typeIdHex }.toSet() == expected) {
                "ULTRA_KERNEL_SESSION_ABI_LEGACY_CALLER:$caller"
            }
        } else {
            require(facts.cfiMode in setOf("none", "kcfi")) { "ULTRA_KERNEL_SESSION_ABI_CFI_UNSUPPORTED" }
            verifyCfi(kernel, facts.cfiMode, facts.normalizeIntegers)
        }
        if (facts.cfiMode == "kcfi") {
            fun member(callback: UltraKernelManagerClaimPayload.SessionCallback) =
                if ('.' in callback.member) callback.member else "file_operations." + callback.member
            val callbacks = facts.callbacks.values.groupBy(::member).mapValues { (key, values) ->
                val signatures = values.map { it.signature }.distinct()
                require(signatures.size == 1) { "ULTRA_KERNEL_SESSION_ABI_CALLBACK_MEMBER:$key" }
                signatures.single()
            }
            val target = UltraKernelBtf.sessionCallbackTypeids(kernel, symbols, callbacks)
            for ((name, callback) in facts.callbacks) require(target[member(callback)] == setOf(callback.kcfiPrefix)) {
                "ULTRA_KERNEL_SESSION_ABI_CALLBACK_KCFI:$name"
            }
        }
    }

    fun verifyRuntimeCfi(kernel: ByteArray, enabled: Boolean) {
        val config = targetConfig(kernel)
        val legacy = UltraKernelLegacyModuleRoute.required(kernel)
        require(enabled == (!legacy && (config.getValue("CONFIG_CFI") == "y" || config.getValue("CONFIG_CFI_CLANG") == "y"))) {
            "ULTRA_KERNEL_SU_RUNTIME_PROFILES_CFI_MODE"
        }
    }
    fun verifyCallbackCfi(kernel: ByteArray, symbols: UltraKernelSymbols,
                          callbacks: Map<String, String>, expected: Map<String, Int>,
                          origins: Map<String, CallbackOrigin> = emptyMap()) {
        require(symbols is UltraKernelKallsymsTable && expected.keys == callbacks.keys) {
            "ULTRA_KERNEL_RESIDENT_CFI_TARGET_SOURCE"
        }
        require(callbacks.keys == fsnotifyCallbacks + origins.keys &&
            validOrigins(origins.keys)) { "ULTRA_KERNEL_MANAGEMENT_CFI_ORIGINS" }
        val actual = UltraKernelBtf.callbackTypeids(kernel, symbols, callbacks.filterKeys { it in fsnotifyCallbacks }) +
            if (origins.isEmpty()) emptyMap() else UltraKernelBtf.managementCallbackTypeids(kernel, symbols, callbacks, origins)
        for ((name, typeid) in expected) {
            // Internal validators have no target-kernel caller: Payload has
            // bound their compiled entry and same-object caller type proof.
            if (origins[name]?.kind == "internal") continue
            require(actual[name] == setOf(typeid)) {
                "ULTRA_KERNEL_RESIDENT_CFI_TARGET_TYPEID:$name"
            }
        }
    }
    fun verifyFunctions(kernel: ByteArray, symbols: UltraKernelSymbols,
                        functions: Map<String, String>, callbacks: Map<String, String>,
                        origins: Map<String, CallbackOrigin> = emptyMap()) {
        require(functions.isNotEmpty() && callbacks.keys == fsnotifyCallbacks + origins.keys &&
            validOrigins(origins.keys)) {
            "ULTRA_KERNEL_RESIDENT_ABI_FUNCTION_MANIFEST"
        }
        require(functions.all { (key, value) -> key.isNotEmpty() && value.startsWith("f") &&
            value.length <= 65536 } && callbacks.all { (_, value) ->
                value.startsWith("*f") && value.length <= 65536 }) {
            "ULTRA_KERNEL_RESIDENT_ABI_FUNCTION_SIGNATURE"
        }
        val actual = UltraKernelBtf.signatures(kernel, symbols, functions.keys, fsnotifyCallbacks)
            ?: error("ULTRA_KERNEL_RESIDENT_ABI_BTF_REQUIRED")
        for ((key, expected) in functions.toSortedMap()) require(actual[key] == expected) {
            "ULTRA_KERNEL_RESIDENT_ABI_FUNCTION_MISMATCH:$key"
        }
        for ((key, expected) in callbacks.filterKeys { it in fsnotifyCallbacks }.toSortedMap()) require(actual[key] == expected) {
            "ULTRA_KERNEL_RESIDENT_ABI_CALLBACK_MISMATCH:$key"
        }
        for ((key, origin) in origins.toSortedMap()) {
            require(origin.signature == callbacks.getValue(key)) { "ULTRA_KERNEL_MANAGEMENT_CALLBACK_REFERENCE:$key" }
            val observed = when (origin.kind) {
                "tracepoint" -> {
                    require(key in setOf("sched_process_exit", "sched_process_exec") &&
                        origin.source == "register_trace_$key" && origin.secondarySource == "unregister_trace_$key" &&
                        origin.parameterIndex == 0) { "ULTRA_KERNEL_MANAGEMENT_TRACE_ORIGIN:$key" }
                    val registered = UltraKernelBtf.parameterSignature(kernel, symbols, origin.source, 0)
                    val unregistered = UltraKernelBtf.parameterSignature(kernel, symbols, origin.secondarySource!!, 0)
                    if (registered == null && unregistered == null) {
                        // The actual void probestub is a probe type source;
                        // the int-returning trace iterator is never substituted.
                        UltraKernelBtf.functionSignature(kernel, symbols, "__probestub_$key")?.let { "*$it" }
                    } else {
                        require(registered != null && registered == unregistered) {
                            "ULTRA_KERNEL_MANAGEMENT_TRACE_REGISTER_PAIR:$key"
                        }
                        registered
                    }
                }
                "work" -> {
                    require(key in setOf("work_struct.func", "msu_publication_cleanup", "msu_uid_snapshot_reclaim_work") &&
                        origin.source == "work_struct.func" && origin.secondarySource == "work_func_t" &&
                        origin.parameterIndex == null) { "ULTRA_KERNEL_MANAGEMENT_WORK_ORIGIN:$key" }
                    val member = UltraKernelBtf.memberSignature(kernel, symbols, "work_struct", "func")
                    val alias = UltraKernelBtf.typedefSignature(kernel, symbols, "work_func_t")
                    require(member != null && member == alias) { "ULTRA_KERNEL_MANAGEMENT_WORK_TYPE:$key" }
                    member
                }
                "rcu" -> {
                    require(key == "msu_uid_snapshot_reclaim_rcu" && origin.source == "call_rcu" &&
                        origin.secondarySource == "rcu_callback_t" && origin.parameterIndex == 1) {
                        "ULTRA_KERNEL_UID_RCU_ORIGIN"
                    }
                    val argument = UltraKernelBtf.parameterSignature(kernel, symbols, "call_rcu", 1)
                    val alias = UltraKernelBtf.typedefSignature(kernel, symbols, "rcu_callback_t")
                    val member = UltraKernelBtf.memberSignature(kernel, symbols, "callback_head", "func")
                    require(argument != null && argument == alias && argument == member) { "ULTRA_KERNEL_UID_RCU_TYPE" }
                    argument
                }
                "internal" -> {
                    require((key == "msu_credential_replace_current.validate" &&
                        origin.source == "msu_credential_replace_current" && origin.parameterIndex == 1 ||
                        key == "msu_uid_gateway_dispatch.validate" && origin.source == "msu_uid_gateway_dispatch" &&
                        origin.parameterIndex == 4 || key == "msu_task_state_release_current.validate" &&
                        origin.source == "msu_task_state_release_current" && origin.parameterIndex == 0) &&
                        origin.secondarySource == null) { "ULTRA_KERNEL_MANAGEMENT_VALIDATOR_ORIGIN" }
                    // Bound to the same management.o BTF/caller by the build
                    // receipt and Payload; it is not a kernel BTF assertion.
                    origin.signature
                }
                else -> error("ULTRA_KERNEL_MANAGEMENT_CALLBACK_ORIGIN:$key")
            }
            require(observed != null && observed == origin.signature) { "ULTRA_KERNEL_MANAGEMENT_CALLBACK_TYPE:$key" }
        }
    }

    fun verifyData(kernel: ByteArray, symbols: UltraKernelSymbols, dataSignatures: Map<String, String>) {
        if (dataSignatures.isEmpty()) return
        UltraKernelBtf.dataObjectSizes(kernel, symbols, dataSignatures)
            ?: error("ULTRA_KERNEL_MANAGEMENT_DATA_BTF_REQUIRED")
    }

    private val resolver = listOf(
        "task_struct.cred",
        "task_struct.real_cred",
        "task_struct.comm",
        "task_struct.fs",
        "task_struct.mm",
        "task_struct.alloc_lock",
        "task_struct.usage",
        "task_struct.rcu",
        "task_struct.thread_info",
        "cred.uid",
        "cred.euid",
        "fs_struct.root",
        "fs_struct.seq",
        "fs_struct.lock",
        "path.dentry",
        "path.mnt",
        "dentry.d_inode",
        "file.f_path",
        "file.f_inode",
        "inode.i_uid",
        "inode.i_gid",
        "inode.i_mode",
        "vm_area_struct.vm_file",
        "vm_area_struct.vm_end",
        "mm_struct.mmap_lock",
        "rw_semaphore.__size",
        "rw_semaphore.count",
        "rw_semaphore.owner",
        "rw_semaphore.osq",
        "rw_semaphore.wait_lock",
        "rw_semaphore.wait_list",
        "rw_semaphore.dep_map",
        "rw_semaphore.magic",
        "kstat.__size",
        "kstat.ino",
        "kstat.dev",
        "kstat.size",
        "kstat.nlink",
        "kstat.mode",
        "kstat.uid",
        "kstat.gid",
        "kstat.mtime",
        "kstat.ctime",
        "timespec64.tv_sec",
        "timespec64.tv_nsec",
        "spinlock_t.__size",
        "spinlock_t.rlock",
        "raw_spinlock_t.raw_lock",
        "arch_spinlock_t.__size",
        "atomic_t.counter",
        "atomic_long_t.counter",
        "refcount_t.refs",
        "fs_struct.seq.lock",
        "task_struct.usage.refs.counter",
        "thread_info.preempt_count",
        "arch_spinlock_t.val.counter",
        "raw_spinlock_t.magic",
        "raw_spinlock_t.owner_cpu",
        "raw_spinlock_t.owner",
        "raw_spinlock_t.dep_map",
        "spinlock_t.dep_map",
        "callback_head.next",
        "callback_head.func",
        "kuid_t.val",
        "kgid_t.val",
        "thread_info.flags",
    )
    private val configs = listOf(
        "CONFIG_SMP",
        "CONFIG_PREEMPT_RT",
        "CONFIG_PREEMPT_COUNT",
        "CONFIG_THREAD_INFO_IN_TASK",
        "CONFIG_DEBUG_SPINLOCK",
        "CONFIG_DEBUG_LOCK_ALLOC",
        "CONFIG_LOCKDEP",
        "CONFIG_RWSEM_SPIN_ON_OWNER",
        "CONFIG_DEBUG_RWSEMS",
        "CONFIG_TRACEPOINTS",
        "CONFIG_PREEMPTION",
        "CONFIG_PREEMPT",
        "CONFIG_PREEMPT_DYNAMIC",
        "CONFIG_PROVE_LOCKING",
        "CONFIG_PROVE_RCU",
        "CONFIG_DEBUG_PREEMPT",
        "CONFIG_LOCK_STAT",
    )
    private val managementConfigKeys = (configs + cfiConfigs + setOf(
        "CONFIG_MODULES", "CONFIG_EXECMEM", "CONFIG_ARCH_HAS_EXECMEM_ROX", "CONFIG_MULTIUSER",
        "CONFIG_HOTPLUG_CPU", "CONFIG_DEBUG_OBJECTS_WORK", "CONFIG_DEBUG_OBJECTS",
        "CONFIG_DEBUG_CREDENTIALS", "CONFIG_REFCOUNT_FULL", "CONFIG_GENERIC_LOCKBREAK",
        "CONFIG_PREEMPT_RCU", "CONFIG_TREE_RCU", "CONFIG_TINY_RCU", "CONFIG_DEBUG_ATOMIC_SLEEP",
    )).toSet()
    private val managementWireLayout = mapOf(
        "requestBytes" to 48, "responseBytes" to 48, "managementBytes" to 64, "metadataBytes" to 80,
        "publicationControlBytes" to 64, "publicationControlAlign" to 8,
        "publicationPrepareBytes" to 64, "publicationPrepareAlign" to 8,
        "publicationMemberBytes" to 24, "publicationMemberAlign" to 8,
        "publicationMemberHandleOffset" to 0, "publicationMemberRoleOffset" to 8,
        "publicationMemberReservedOffset" to 12, "publicationMemberAddressOffset" to 16,
        "publicationPrepareMembersOffset" to 0, "publicationPrepareMemberCountOffset" to 8,
        "publicationPrepareGateIndexOffset" to 12, "publicationPrepareEntryAddressOffset" to 16,
        "publicationPrepareGateEntryOffsetOffset" to 24, "publicationPrepareExpectedInstructionOffset" to 32,
        "publicationPrepareFlagsOffset" to 36, "publicationPreparePayloadEntryOffset" to 40,
        "publicationPrepareReservedOffset" to 48,
        "publicationControlPublicationIdOffset" to 0, "publicationControlEnterFunctionOffset" to 8,
        "publicationControlLeaveFunctionOffset" to 16, "publicationControlPayloadEntryOffset" to 24,
        "publicationControlEntryAddressOffset" to 32, "publicationControlResumeAddressOffset" to 40,
        "publicationControlSchemaOffset" to 48, "publicationControlLengthOffset" to 52,
        "publicationControlEpochOffset" to 56, "publicationControlReservedOffset" to 60,
        "publicationPrepareCommand" to 12, "publicationPublishCommand" to 13,
        "publicationDisableCommand" to 14, "publicationRetireCommand" to 15,
        "sharedBoolCreateCommand" to 16, "sharedBoolStoreCommand" to 17, "sharedBoolReleaseCommand" to 18,
        "sharedBoolCapability" to 16384, "sharedBoolRole" to 5, "sharedBoolValueBytes" to 1,
        "referenceDroppedEffect" to 8192, "sharedRetainedEffect" to 16384,
        "baseCapabilities" to 2023, "optionalCapabilities" to 30720, "ownerCapabilities" to 32743,
        "queryViews" to 2, "queryBaseArg0" to 0, "queryExtendedArg0" to 30720,
        "publicationControlSchema" to 1,
    )

    private val uidWireLayout = mapOf(
        "ownerCapabilities" to 65511, "queryViews" to 3, "uidCreateCommand" to 19,
        "uidPrepareCommand" to 20, "uidCommitCommand" to 21, "uidCancelCommand" to 22,
        "uidReadbackCommand" to 23, "uidShutdownCommand" to 24, "uidSnapshotCapability" to 32768,
        "uidQueryArg0" to 63488, "uidRecordBytes" to 280, "uidMetadataBytes" to 40,
        "uidPrepareBytes" to 32, "uidReadbackBytes" to 24, "uidSchema" to 1, "uidMaxRecords" to 4095,
        "uidRecordFullUidOffset" to 0, "uidRecordDecisionOffset" to 4,
        "uidRecordInstallationGenerationOffset" to 8, "uidRecordProfileRevisionOffset" to 16, "uidRecordPackageOffset" to 24,
        "uidMetadataSchemaOffset" to 0, "uidMetadataCountOffset" to 4, "uidMetadataRecordBytesOffset" to 8,
        "uidMetadataReservedOffset" to 12, "uidMetadataContentRevisionOffset" to 16, "uidMetadataHostOffset" to 24,
        "uidPrepareRecordsUserOffset" to 0, "uidPrepareExpectedRevisionOffset" to 8, "uidPrepareCountOffset" to 16,
        "uidPrepareRecordBytesOffset" to 20, "uidPrepareSchemaOffset" to 24, "uidPrepareReservedOffset" to 28,
        "uidReadbackDestinationUserOffset" to 0, "uidReadbackCapacityOffset" to 8,
        "uidReadbackReservedOffset" to 12, "uidReadbackExpectedRevisionOffset" to 16,
    )
    private val procattrWireLayout = mapOf(
        "ownerCapabilities" to 131047, "queryViews" to 4, "credProcattrCommand" to 25,
        "procattrCapability" to 65536, "procattrQueryArg0" to 129024, "procattrCurrent" to 1,
        "procattrExec" to 2, "procattrMaxBytes" to 4096, "procWriteAttemptedEffect" to 32768,
        "credChangedEffect" to 65536,
    )
    private val taskWireLayout = mapOf(
        "ownerCapabilities" to 262119, "queryViews" to 5, "taskStateCommand" to 26,
        "taskStateCapability" to 131072, "taskQueryArg0" to 260096,
        "taskStateChangedEffect" to 131072, "taskStateCompletedEffect" to 262144,
    )
    private val procattrLayoutTypes = mapOf(
        "file" to ("file" to mapOf("f_op" to 5, "f_path" to 7, "f_inode" to 9)),
        "path" to ("path" to mapOf("dentry" to 13)),
        "fileOperations" to ("file_operations" to mapOf("write" to 17)),
        "inode" to ("inode" to mapOf("i_sb" to 21)),
        "superBlock" to ("super_block" to mapOf("s_magic" to 25)),
        "dentry" to ("dentry" to mapOf("d_parent" to 29, "d_name" to 31)),
        "qstr" to ("qstr" to mapOf("len" to 35, "name" to 37)),
    )
    private val procattrSizeWords = mapOf("file" to 3, "path" to 11, "fileOperations" to 15,
        "inode" to 19, "superBlock" to 23, "dentry" to 27, "qstr" to 33)
    private val procattrConfigKeys = setOf("CONFIG_PROC_FS", "CONFIG_SECURITY")
    private val taskConfigKeys = setOf("CONFIG_SECCOMP", "CONFIG_SECCOMP_FILTER", "CONFIG_GENERIC_ENTRY",
        "CONFIG_THREAD_INFO_IN_TASK", "CONFIG_PREEMPT_RT", "CONFIG_DEBUG_SPINLOCK", "CONFIG_DEBUG_LOCK_ALLOC")

    fun queryViews(facts: ManagementFacts): Int {
        val views = facts.wireLayout["queryViews"]
        require(views != null && views in 2..5) { "ULTRA_KERNEL_MANAGEMENT_QUERY_VIEWS" }
        return views
    }
    private val uidLayoutTypes = mapOf(
        "state" to ("msu_uid_snapshot_state" to listOf("mutation", "published_snapshot", "host", "revision", "closed")),
        "snapshot" to ("msu_uid_snapshot" to listOf("publication", "references", "reclaim_rcu", "reclaim_work", "metadata", "published", "records")),
        "record" to ("msu_uid_snapshot_record" to listOf("full_uid", "decision", "installation_generation", "profile_revision", "package")),
        "metadata" to ("msu_uid_snapshot_metadata" to listOf("schema", "count", "record_bytes", "reserved", "content_revision", "host")),
        "controller" to ("msu_uid_controller_slot" to listOf("state", "host", "id", "closed")),
        "candidate" to ("msu_uid_candidate_slot" to listOf("snapshot", "controller", "id", "session", "expected_revision")),
        "refcount" to ("refcount_struct" to listOf("refs")),
        "rcuHead" to ("callback_head" to listOf("next", "func")),
        "work" to ("work_struct" to listOf("data", "entry", "func")),
    )
    private val uidConfigKeys = setOf("CONFIG_SMP", "CONFIG_PREEMPT_RT", "CONFIG_PREEMPT_COUNT", "CONFIG_THREAD_INFO_IN_TASK",
        "CONFIG_PREEMPT_RCU", "CONFIG_TREE_RCU", "CONFIG_TINY_RCU", "CONFIG_RCU_BOOST", "CONFIG_RCU_NOCB_CPU",
        "CONFIG_RCU_LAZY", "CONFIG_TASKS_RCU", "CONFIG_TASKS_TRACE_RCU", "CONFIG_PROVE_RCU", "CONFIG_DEBUG_OBJECTS_RCU_HEAD",
        "CONFIG_REFCOUNT_FULL", "CONFIG_DEBUG_LOCK_ALLOC", "CONFIG_DEBUG_OBJECTS_WORK", "CONFIG_LOCKDEP",
        "CONFIG_DEBUG_ATOMIC_SLEEP", "CONFIG_CFI", "CONFIG_CFI_CLANG", "CONFIG_CFI_ICALL_NORMALIZE_INTEGERS")

    private val suLayoutTypes = mapOf(
        "profile" to ("msu_su_profile" to listOf("uid", "gid", "groups_count", "groups", "inheritable", "permitted", "effective",
            "bounding", "ambient", "selinux_domain", "namespace_mode", "flags", "use_default", "non_root", "umount_modules", "template_name")),
        "installMember" to ("msu_su_install_member" to listOf("package")),
        "verifiedInstall" to ("msu_su_verified_install" to listOf("full_uid", "installation_id", "pin", "members", "member_count")),
        "savedGrant" to ("msu_su_saved_grant" to listOf("full_uid", "decision", "installation_id", "profile")),
        "registrySource" to ("msu_su_registry_source" to listOf("owner", "pin_current", "put_install", "read_grant", "validate_profile")),
        "revokeReceipt" to ("msu_su_revoke_receipt" to listOf("generation", "full_uid", "inflight")),
        "lease" to ("msu_su_lease" to listOf("registry", "entry", "snapshot", "task", "cred")),
        "registry" to ("msu_su_registry" to listOf("lock", "host", "controller", "next_generation", "source", "count", "closed", "entries")),
        "installEntry" to ("msu_su_install_entry" to listOf("full_uid", "users", "generation", "revoked", "busy", "cancelled", "granted", "installation", "profile")),
    )

    /** Optional build diagnostics; these facts do not publish a native SU binding. */
    fun verifySuProviderMetadata(facts: Map<String, Int>, layouts: Map<String, CompiledLayout>,
                                 functions: Map<String, String>, sourceCallbacks: Map<String, String>, complete: Boolean, revision: Int = 1) {
        require(revision in setOf(1, 2)) { "ULTRA_KERNEL_SU_METADATA_REVISION" }
        val types = if (revision == 1) suLayoutTypes else suLayoutTypes + mapOf(
            "registry" to ("msu_su_registry" to (suLayoutTypes.getValue("registry").second + "producer")),
            "lease" to ("msu_su_lease" to (suLayoutTypes.getValue("lease").second + listOf("generation", "invalidated"))),
            "update" to ("msu_su_update" to listOf("registry", "records", "expected_revision", "count")),
        )
        require(types.keys.containsAll(layouts.keys) && (!complete || layouts.keys == types.keys)) {
            "ULTRA_KERNEL_SU_LAYOUT_FIELDS"
        }
        val constants = mapOf("schema" to 1, "registryMaxUids" to 4095, "installationIdBytes" to 32,
            "profileGroups" to 32, "profileContextBytes" to 64, "installMaxMembers" to 128)
        val expected = (constants.keys + setOf("capLastCap", "configMultiuser")).toMutableSet()
        for ((prefix, type) in types) {
            expected += prefix + "Bytes"; expected += prefix + "Align"
            for (member in type.second) {
                val key = prefix + member.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
                expected += key + "Offset"; expected += key + "Bytes"
            }
        }
        require((facts.isEmpty() && layouts.isEmpty() && !complete) || facts.keys == expected &&
            constants.all { (key, value) -> facts[key] == value } && (facts["capLastCap"] ?: -1) in 0..63 &&
            (facts["configMultiuser"] ?: -1) in 0..1) { "ULTRA_KERNEL_SU_COMPILED_FACTS" }
        for ((prefix, layout) in layouts) {
            val type = types.getValue(prefix)
            val bytes = facts.getValue(prefix + "Bytes")
            val alignment = facts.getValue(prefix + "Align")
            require(bytes > 0 && bytes == layout.bytes && alignment > 0 && alignment and (alignment - 1) == 0 &&
                bytes % alignment == 0 && layout.members.keys == type.second.toSet()) { "ULTRA_KERNEL_SU_LAYOUT:$prefix" }
            val canonical = "s${type.first}:$bytes{" + layout.members.values.sortedBy { it.offset }.joinToString("") {
                require(it.offset >= 0 && it.bytes > 0 && it.offset.toLong() + it.bytes <= bytes && it.signature.isNotEmpty()) {
                    "ULTRA_KERNEL_SU_MEMBER_RANGE:$prefix"
                }
                "${it.offset * 8}=${it.signature};"
            } + "}"
            require(canonical == layout.signature) { "ULTRA_KERNEL_SU_LAYOUT_SIGNATURE:$prefix" }
            for ((member, field) in layout.members) {
                val key = prefix + member.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
                require(field.offset == facts[key + "Offset"] && field.bytes == facts[key + "Bytes"]) {
                    "ULTRA_KERNEL_SU_COMPILED_MEMBER:$prefix.$member"
                }
            }
        }
        val names = setOf("msu_su_registry_create", "msu_su_registry_refresh", "msu_su_registry_revoke", "msu_su_registry_revoke_package",
            "msu_su_registry_shutdown", "msu_su_registry_destroy_quiesced", "msu_su_read_current", "msu_su_lease_begin",
            "msu_su_lease_validate", "msu_su_lease_profile", "msu_su_lease_end", "msu_su_apply_current") +
            if (revision == 2) setOf("msu_su_registry_rows", "msu_su_registry_update_begin", "msu_su_registry_update_finish") else emptySet()
        require((functions.isEmpty() && !complete || functions.keys == names) &&
            functions.values.all { it.startsWith("f") && it.length <= 65536 }) { "ULTRA_KERNEL_SU_FUNCTION_ABI" }
        val sourceMembers = setOf("pin_current", "put_install", "read_grant", "validate_profile")
        require(sourceCallbacks.isEmpty() && !complete || sourceCallbacks.keys == sourceMembers && sourceCallbacks.all { (name, signature) ->
            signature.startsWith("*f") && signature == layouts["registrySource"]?.members?.get(name)?.signature
        }) { "ULTRA_KERNEL_SU_SOURCE_CALLBACK_ABI" }
    }

    private val coreLayoutTypes = mapOf(
        "profile" to ("msu_credential_profile" to listOf("uid", "gid", "groups_count", "groups", "caps",
            "cap_write_mask", "replace_ids", "replace_groups", "clear_securebits", "selinux_domain")),
        "serviceDescriptor" to ("msu_runtime_service_descriptor" to listOf("host", "kind", "lifecycle_owner", "entries", "entry_count")),
        "service" to ("msu_runtime_service" to listOf("operation", "host", "lifecycle_owner", "id", "entries", "entry_count",
            "operations", "kind", "reserved", "active", "aborting", "discarded", "factory_owned", "sealed")),
        "readerFence" to ("msu_uid_reader_fence" to listOf("owner", "check_closed", "begin", "revoke", "finish")),
        "controller" to ("msu_uid_controller_slot" to listOf("state", "host", "fence", "quarantined_transaction", "id",
            "closed", "shutdown_pending", "fence_broken")),
    )
    private val coreFunctions = setOf("msu_runtime_service_register", "msu_runtime_service_bind", "msu_runtime_service_bind_held",
        "msu_runtime_service_revoke", "msu_runtime_service_alloc", "msu_runtime_service_write", "msu_runtime_service_shared_bool_create",
        "msu_runtime_service_prepare", "msu_runtime_service_publish", "msu_runtime_service_abort_unexposed",
        "msu_credential_profile_validate", "msu_credential_replace_profile_current", "msu_uid_gateway_create_kernel",
        "msu_uid_gateway_bind_reader", "msu_task_state_release_current", "msu_credential_domain_supported", "msu_credential_domain_prepare")
    private val coreCallbackParameters = mapOf(
        "profile.validate" to ("msu_credential_replace_profile_current" to 1),
        "profile.adopt" to ("msu_credential_replace_profile_current" to 2),
        "uid.create.validate" to ("msu_uid_gateway_create_kernel" to 1),
        "task.validate" to ("msu_task_state_release_current" to 0),
    )

    private fun compiledParameter(signature: String, index: Int): String {
        require(signature.startsWith("f") && signature.length <= 65536 && index >= 0) { "ULTRA_KERNEL_CORE_FUNCTION_SIGNATURE" }
        val first = signature.indexOf('(')
        require(first > 1 && signature.last() == ')') { "ULTRA_KERNEL_CORE_FUNCTION_SIGNATURE" }
        var parentheses = 1
        var braces = 0
        var start = first + 1
        val parameters = mutableListOf<String>()
        for (at in first + 1 until signature.length) {
            when (signature[at]) {
                '(' -> parentheses++
                ')' -> {
                    parentheses--
                    require(parentheses >= 0 && (parentheses != 0 || at == signature.lastIndex && start == at && braces == 0)) {
                        "ULTRA_KERNEL_CORE_FUNCTION_SIGNATURE"
                    }
                }
                '{' -> braces++
                '}' -> { braces--; require(braces >= 0) { "ULTRA_KERNEL_CORE_FUNCTION_SIGNATURE" } }
                ';' -> if (parentheses == 1 && braces == 0) {
                    require(at > start) { "ULTRA_KERNEL_CORE_FUNCTION_SIGNATURE" }
                    parameters += signature.substring(start, at)
                    start = at + 1
                }
            }
        }
        require(parentheses == 0 && braces == 0 && index < parameters.size) { "ULTRA_KERNEL_CORE_FUNCTION_PARAMETER" }
        return parameters[index]
    }

    fun verifyCoreLifecycleMetadata(compiledFacts: Map<String, Int>, uidCompiledFacts: Map<String, Int>,
        compiledLayouts: Map<String, CompiledLayout>, localFunctionAbi: Map<String, String>,
        callbackParameters: Map<String, CallbackOrigin>, readerFenceAbi: Map<String, String>, complete: Boolean,
        lifecycleRevision: Int, runtimeSuccessor: Boolean = false, runtimeLayoutSuccessor: Boolean = false) {
        require(lifecycleRevision in setOf(1, 2) && (!runtimeSuccessor || lifecycleRevision == 2)) { "ULTRA_KERNEL_CORE_LIFECYCLE_REVISION" }
        val revisionTypes = if (lifecycleRevision == 1) coreLayoutTypes else coreLayoutTypes + mapOf(
            "readerFence" to ("msu_uid_reader_fence" to (coreLayoutTypes.getValue("readerFence").second + "retire")),
            "controller" to ("msu_uid_controller_slot" to listOf("state", "host", "fence", "quarantined_transaction",
                "id", "revision", "closed", "shutdown_pending", "shutdown_complete", "fence_broken")),
            "candidate" to ("msu_uid_candidate_slot" to listOf("snapshot", "controller", "kernel_owner", "id", "session", "expected_revision")),
        )
        val layoutTypes = revisionTypes + if (runtimeSuccessor) mapOf(
            "service" to ("msu_runtime_service" to listOf("operation", "host", "lifecycle_owner", "id", "entries", "entry_count",
                "operations", "helper_sessions", "kind", "reserved", "active", "aborting", "discarded", "factory_owned", "sealed")),
            "session" to ("msu_management_session" to listOf("operation", "cleanup", "task", "mm", "original", "active", "helper",
                "service", "id", "exec_id", "principal_uid", "epoch", "references", "attached")),
        ) else emptyMap()
        val functionNames = coreFunctions + if (lifecycleRevision == 2) setOf("msu_management_discard_unpublished",
            "msu_uid_gateway_prepare_kernel", "msu_uid_gateway_commit_kernel", "msu_uid_gateway_cancel_kernel",
            "msu_uid_gateway_discard_host_unpublished", "msu_uid_snapshot_state_destroy_quiesced", "msu_uid_snapshot_runtime_discard") else emptySet()
        val parameters = coreCallbackParameters + if (lifecycleRevision == 2) mapOf(
            "uid.prepare.validate" to ("msu_uid_gateway_prepare_kernel" to 7),
            "uid.commit.validate" to ("msu_uid_gateway_commit_kernel" to 5),
        ) else emptyMap()
        require(layoutTypes.keys.containsAll(compiledLayouts.keys) && (!complete || compiledLayouts.keys == layoutTypes.keys)) {
            "ULTRA_KERNEL_CORE_LAYOUT_FIELDS"
        }
        val constants = mapOf("schema" to 1, "runtimeMaxEntries" to 64, "profileMaxGroups" to 32,
            "profileDomainBytes" to 64, "capAll" to 31, "taskStateVerified" to 0, "profileDomainVerified" to 0)
        val uidTypes = setOf("readerFence", "controller", "candidate")
        for (uid in listOf(false, true)) {
            val facts = if (uid) uidCompiledFacts else compiledFacts
            val types = layoutTypes.filterKeys { (it in uidTypes) == uid }
            val values = if (uid) mapOf("schema" to 1) else constants
            val expected = values.keys.toMutableSet()
            for ((prefix, type) in types) {
                expected += prefix + "Bytes"; expected += prefix + "Align"
                for (member in type.second) {
                    val key = prefix + member.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
                    expected += key + "Offset"; expected += key + "Bytes"
                }
            }
            require(facts.isEmpty() && !complete || facts.keys == expected && values.all { (key, value) -> facts[key] == value }) {
                "ULTRA_KERNEL_CORE_COMPILED_FACTS"
            }
            for ((prefix, type) in types) {
                val layout = compiledLayouts[prefix] ?: continue
                val bytes = facts.getValue(prefix + "Bytes")
                val alignment = facts.getValue(prefix + "Align")
                require(bytes > 0 && bytes == layout.bytes && alignment > 0 && alignment and (alignment - 1) == 0 &&
                    bytes % alignment == 0 && layout.members.keys == type.second.toSet()) { "ULTRA_KERNEL_CORE_LAYOUT:$prefix" }
                // CORE selects fact members before the later runtime source adds legacy session state.
                // Its full BTF signature still includes those exact runtime members.
                val fullMembers = if (runtimeLayoutSuccessor && prefix == "session") {
                    val service = layout.members.getValue("service")
                    val id = layout.members.getValue("id")
                    val attached = layout.members.getValue("attached")
                    val legacyGatewayOffset = service.offset.toLong() + service.bytes
                    val alignedIdOffset = (legacyGatewayOffset + 8 + 7) and -8L
                    require(service.bytes == 8 && id.bytes == 8 && attached.bytes == 1 &&
                        legacyGatewayOffset % 8 == 0L && id.offset.toLong() == alignedIdOffset) {
                        "ULTRA_KERNEL_CORE_SESSION_RUNTIME_LAYOUT"
                    }
                    layout.members.values + listOf(
                        CompiledMember(legacyGatewayOffset.toInt(), 8, "*smsu_legacy_gateway"),
                        CompiledMember(attached.offset + attached.bytes, attached.bytes, attached.signature),
                        CompiledMember(attached.offset + attached.bytes * 2, attached.bytes, attached.signature),
                    )
                } else layout.members.values
                val ordered = fullMembers.sortedBy { it.offset }
                var end = 0L
                val canonical = "s${type.first}:$bytes{" + ordered.joinToString("") {
                    require(it.offset >= end && it.bytes > 0 && it.offset.toLong() + it.bytes <= bytes &&
                        it.signature.isNotEmpty() && it.signature.length <= 65536) { "ULTRA_KERNEL_CORE_MEMBER_RANGE:$prefix" }
                    end = it.offset.toLong() + it.bytes
                    "${it.offset.toLong() * 8}=${it.signature};"
                } + "}"
                require(canonical == layout.signature) { "ULTRA_KERNEL_CORE_LAYOUT_SIGNATURE:$prefix" }
                for ((member, field) in layout.members) {
                    val key = prefix + member.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
                    require(field.offset == facts[key + "Offset"] && field.bytes == facts[key + "Bytes"]) {
                        "ULTRA_KERNEL_CORE_COMPILED_MEMBER:$prefix.$member"
                    }
                }
            }
        }
        require((localFunctionAbi.isEmpty() && !complete || localFunctionAbi.keys == functionNames) &&
            localFunctionAbi.values.all { it.startsWith("f") && it.length <= 65536 }) { "ULTRA_KERNEL_CORE_FUNCTION_ABI" }
        require(callbackParameters.isEmpty() && !complete || callbackParameters.keys == parameters.keys) {
            "ULTRA_KERNEL_CORE_CALLBACK_PARAMETERS"
        }
        for ((key, origin) in callbackParameters) {
            val (source, position) = parameters.getValue(key)
            require(origin.kind == "internal" && origin.source == source && origin.parameterIndex == position &&
                origin.secondarySource == null && origin.signature.startsWith("*f") &&
                origin.signature == compiledParameter(localFunctionAbi.getValue(source), position)) {
                "ULTRA_KERNEL_CORE_CALLBACK_PARAMETER:$key"
            }
        }
        val fenceMembers = layoutTypes.getValue("readerFence").second.toSet() - "owner"
        require(readerFenceAbi.isEmpty() && !complete || readerFenceAbi.keys == fenceMembers && readerFenceAbi.all { (name, signature) ->
            signature.startsWith("*f") && signature.length <= 65536 &&
                signature == compiledLayouts["readerFence"]?.members?.get(name)?.signature
        }) { "ULTRA_KERNEL_CORE_READER_FENCE_ABI" }
    }

    fun verifyCredentialDomain(words: IntArray, compiledLayoutSha256: String) {
        require(words.size == 26 && words[0] == 1 && words[2] == 0 && words[3] in 0..1) {
            "ULTRA_KERNEL_CREDENTIAL_DOMAIN_LAYOUT"
        }
        val bytes = java.nio.ByteBuffer.allocate(words.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .apply { words.forEach { putInt(it) } }.array()
        require(compiledLayoutSha256 == UltraKernelSpec.sha256(bytes)) { "ULTRA_KERNEL_CREDENTIAL_DOMAIN_IDENTITY" }
        require((4 until words.size step 2).all { words[it] == -1 && words[it + 1] == 0 }) {
            "ULTRA_KERNEL_CREDENTIAL_DOMAIN_UNQUALIFIED_FIELDS"
        }
    }

    fun verifyUidSnapshot(kernel: ByteArray, symbols: UltraKernelSymbols, facts: UidSnapshotFacts) {
        require(facts.compiledLayouts.keys == uidLayoutTypes.keys && facts.callbackOrigins.keys == uidCallbacks &&
            facts.compiledFacts["schema"] == 1 && facts.compiledFacts["recordStride"] == 280) { "ULTRA_KERNEL_UID_FACTS_SCHEMA" }
        val expectedKeys = mutableSetOf("schema", "recordStride")
        for ((prefix, type) in uidLayoutTypes) {
            val layout = facts.compiledLayouts.getValue(prefix)
            val bytes = facts.compiledFacts[prefix + "Bytes"] ?: error("ULTRA_KERNEL_UID_SIZE:$prefix")
            val alignment = facts.compiledFacts[prefix + "Align"] ?: error("ULTRA_KERNEL_UID_ALIGNMENT:$prefix")
            expectedKeys += prefix + "Bytes"; expectedKeys += prefix + "Align"
            require(bytes > 0 && layout.bytes == bytes && alignment > 0 && alignment and (alignment - 1) == 0 &&
                bytes % alignment == 0 && layout.members.keys.containsAll(type.second)) { "ULTRA_KERNEL_UID_LAYOUT:$prefix" }
            val canonical = "s${type.first}:$bytes{" + layout.members.values.sortedBy { it.offset }.joinToString("") {
                require(it.offset >= 0 && it.bytes >= 0 && it.offset.toLong() + it.bytes <= bytes && it.signature.isNotEmpty()) {
                    "ULTRA_KERNEL_UID_MEMBER_RANGE:$prefix"
                }
                "${it.offset * 8}=${it.signature};"
            } + "}"
            require(canonical == layout.signature) { "ULTRA_KERNEL_UID_SIGNATURE:$prefix" }
            for (member in type.second) {
                val key = prefix + member.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
                expectedKeys += key + "Offset"; expectedKeys += key + "Bytes"
                val actual = layout.members.getValue(member)
                require(actual.offset == facts.compiledFacts[key + "Offset"] && actual.bytes == facts.compiledFacts[key + "Bytes"]) {
                    "ULTRA_KERNEL_UID_COMPILED_MEMBER:$prefix.$member"
                }
            }
            if (prefix in setOf("refcount", "rcuHead", "work")) require(
                UltraKernelBtf.typeSignature(kernel, symbols, type.first) == layout.signature) { "ULTRA_KERNEL_UID_TARGET_TYPE:$prefix" }
        }
        require(facts.compiledFacts.keys == expectedKeys && facts.compiledFacts["recordBytes"] == 280 &&
            facts.compiledFacts["recordAlign"] == 8 && facts.compiledFacts["metadataBytes"] == 40 &&
            facts.compiledFacts["snapshotRecordsBytes"] == 0) { "ULTRA_KERNEL_UID_FACTS_FIELDS" }
        val config = targetConfig(kernel)
        require(facts.config.keys == uidConfigKeys) { "ULTRA_KERNEL_UID_CONFIG_FIELDS" }
        for ((key, value) in facts.config) require(value in setOf("y", "n") && value == config.getValue(key)) {
            "ULTRA_KERNEL_UID_CONFIG:$key"
        }
    }

    /** Complete schema-1 preflight. Alignment words remain compiled facts:
     * BTF does not encode over-alignment or prove execution-context closure. */
    fun verifyManagement(kernel: ByteArray, symbols: UltraKernelSymbols,
                          words: IntArray, facts: ManagementFacts, bound: Map<String, BoundField> = emptyMap()) {
        require(words.size == 98 && words[0] == 1 && facts.layoutSchema == 1 && facts.layoutWords == words.size) {
            "ULTRA_KERNEL_MANAGEMENT_ABI_LAYOUT_SCHEMA"
        }
        // These are same-build wire facts, separate from target kernel ABI and context proof.
        val views = queryViews(facts)
        val expectedWire = when (views) {
            2 -> managementWireLayout
            3 -> managementWireLayout + uidWireLayout
            4 -> managementWireLayout + uidWireLayout + procattrWireLayout
            5 -> managementWireLayout + uidWireLayout + procattrWireLayout + taskWireLayout
            else -> error("ULTRA_KERNEL_MANAGEMENT_QUERY_VIEWS")
        }
        require(facts.wireLayout.keys.containsAll(expectedWire.keys)) { "ULTRA_KERNEL_MANAGEMENT_WIRE_FIELDS" }
        for ((name, expected) in expectedWire) require(facts.wireLayout[name] == expected) {
            "ULTRA_KERNEL_MANAGEMENT_WIRE_VALUE:$name"
        }
        require((!facts.gateVerified || facts.sourceQualification != null) &&
            ("publication" !in facts.enabledFeatures || facts.gateVerified && "code" in facts.enabledFeatures) &&
            facts.enabledFeatures.all { it in setOf("text", "code", "shared_bool", "uid_snapshot", "procattr", "task_state", "publication") } &&
            ("uid_snapshot" !in facts.enabledFeatures || views >= 3) &&
            ("procattr" !in facts.enabledFeatures || views >= 4) &&
            ("task_state" !in facts.enabledFeatures || views == 5)) {
            "ULTRA_KERNEL_MANAGEMENT_PUBLICATION_GATE_UNBOUND"
        }
        require(facts.callbackOrigins.keys == managementCallbacks) { "ULTRA_KERNEL_MANAGEMENT_CALLBACK_ORIGINS" }
        require((facts.uidSnapshot != null) == (views >= 3)) { "ULTRA_KERNEL_UID_REFERENCE_MISSING" }
        facts.uidSnapshot?.let { verifyUidSnapshot(kernel, symbols, it) }
        require((facts.procattr != null) == (views >= 4) && (facts.taskState != null) == (views == 5)) {
            "ULTRA_KERNEL_MANAGEMENT_SUPPLEMENTAL_REFERENCE"
        }
        if (views == 5) {
            val verified = facts.constants["MSU_MANAGEMENT_TASK_STATE_VERIFIED"] ?: 0
            require(verified in 0..1 && (verified == 1) == ("task_state" in facts.enabledFeatures)) {
                "ULTRA_KERNEL_TASK_STATE_FEATURE"
            }
        }
        val config = targetConfig(kernel)
        require(facts.config.keys.containsAll(managementConfigKeys)) { "ULTRA_KERNEL_MANAGEMENT_ABI_CONFIG_INCOMPLETE" }
        for (key in managementConfigKeys.sorted()) {
            val reference = facts.config.getValue(key)
            require(reference in setOf("y", "n") && reference == (config[key] ?: "n")) {
                "ULTRA_KERNEL_MANAGEMENT_ABI_CONFIG:$key"
            }
        }
        val sizes = linkedMapOf(2 to "task_struct", 16 to "cred", 58 to "group_info", 63 to "mm_struct",
            67 to "mutex", 69 to "raw_spinlock_t", 81 to "work_struct", 91 to "kernel_cap_t")
        val members = linkedMapOf(
            4 to "task_struct.cred", 6 to "task_struct.real_cred", 8 to "task_struct.mm",
            10 to "task_struct.self_exec_id", 12 to "task_struct.flags", 14 to "task_struct.usage",
            18 to "cred.usage", 20 to "cred.non_rcu", 22 to "cred.uid", 24 to "cred.gid",
            26 to "cred.suid", 28 to "cred.sgid", 30 to "cred.euid", 32 to "cred.egid",
            34 to "cred.fsuid", 36 to "cred.fsgid", 38 to "cred.securebits",
            40 to "cred.cap_inheritable", 42 to "cred.cap_permitted", 44 to "cred.cap_effective",
            46 to "cred.cap_bset", 48 to "cred.cap_ambient", 50 to "cred.user", 52 to "cred.user_ns",
            54 to "cred.ucounts", 56 to "cred.group_info", 60 to "group_info.usage",
            65 to "mm_struct.mm_count", 71 to "raw_spinlock_t.raw_lock", 73 to "raw_spinlock_t.magic",
            75 to "raw_spinlock_t.owner_cpu", 77 to "raw_spinlock_t.owner", 79 to "raw_spinlock_t.dep_map",
            83 to "work_struct.data", 85 to "work_struct.entry", 87 to "work_struct.func",
            89 to "work_struct.lockdep_map",
        )
        val optional = members.filterKeys { words[it] == -1 }.values.toSet()
        val wanted = members.values.toSet() + sizes.values.map { "$it.__size" }
        val layout = UltraKernelBtf.abi(kernel, symbols, wanted, optional)
            ?: error("ULTRA_KERNEL_MANAGEMENT_ABI_BTF_REQUIRED")
        for ((at, type) in sizes) {
            val observed = layout.fields.getValue("$type.__size")
            require(words[at] >= 0 && observed.widthBytes == ownerSize(type, words[at], members.values, bound)) {
                "ULTRA_KERNEL_MANAGEMENT_ABI_SIZE:$type"
            }
            val alignment = words[at + 1]
            require(alignment > 0 && alignment and (alignment - 1) == 0 && words[at] % alignment == 0 &&
                facts.typeAlignments[type] == alignment) { "ULTRA_KERNEL_MANAGEMENT_ABI_COMPILED_ALIGNMENT:$type" }
        }
        for ((at, key) in members) {
            val observed = layout.fields[key]
            if (words[at] == -1) require(words[at + 1] == 0 && observed == null) {
                "ULTRA_KERNEL_MANAGEMENT_ABI_OPTIONAL:$key"
            } else require(fieldMatches(key, observed, words[at], words[at + 1], bound)) {
                "ULTRA_KERNEL_MANAGEMENT_ABI_MEMBER:$key"
            }
        }
        val usage = UltraKernelBtf.memberSignature(kernel, symbols, "group_info", "usage")
        val refcount = UltraKernelBtf.typedefSignature(kernel, symbols, "refcount_t")
        require(words[62] in 0..1 && usage != null &&
            (words[62] == 1) == (refcount != null && usage == refcount)) {
            "ULTRA_KERNEL_MANAGEMENT_ABI_GROUP_USAGE_TYPE"
        }
        fun constant(name: String) = facts.constants[name] ?: error("ULTRA_KERNEL_MANAGEMENT_ABI_CONSTANT_MISSING:$name")
        val page = when ((UltraKernelA64.le32Bits(kernel, 24) ushr 1) and 3) {
            1 -> 12
            2 -> 14
            3 -> 16
            else -> error("ULTRA_KERNEL_MANAGEMENT_ABI_PAGE_SIZE")
        }
        require(words[94] == page && constant("PAGE_SHIFT") == page) { "ULTRA_KERNEL_MANAGEMENT_ABI_PAGE_SHIFT" }
        val last = UltraKernelBtf.integerVariable(kernel, symbols, "cap_last_cap")
            ?: error("ULTRA_KERNEL_MANAGEMENT_ABI_CAP_LAST_SOURCE")
        require(last in 0..63 && words[93].toLong() == last && constant("CAP_LAST_CAP").toLong() == last) {
            "ULTRA_KERNEL_MANAGEMENT_ABI_CAP_LAST"
        }
        for ((at, key) in mapOf(95 to "CONFIG_TRACEPOINTS", 96 to "CONFIG_PREEMPT_RT", 97 to "CONFIG_MULTIUSER"))
            require(words[at] in 0..1 && (words[at] == 1) == (config[key] == "y")) {
                "ULTRA_KERNEL_MANAGEMENT_ABI_FLAG:$key"
            }
        require(words[95] == 1 && words[96] == 0 && words[97] == 1) {
            "ULTRA_KERNEL_MANAGEMENT_ABI_UNSUPPORTED_CONFIGURATION"
        }
        val execmem = constant("MSU_MANAGEMENT_EXECMEM")
        val execmemRw = constant("MSU_MANAGEMENT_EXECMEM_ALLOC_RW")
        require(execmem in 0..1 && execmemRw in 0..1 &&
            constant("MSU_MANAGEMENT_PUBLICATION_GATE_VERIFIED") == (if (facts.gateVerified) 1 else 0) &&
            ("publication" !in facts.enabledFeatures || config["CONFIG_TASKS_RCU"] == "y")) {
            "ULTRA_KERNEL_MANAGEMENT_ABI_BACKEND"
        }
        if (execmem == 1) {
            val value = UltraKernelBtf.enumValue(kernel, symbols, "EXECMEM_DEFAULT")
                ?: error("ULTRA_KERNEL_MANAGEMENT_ABI_EXECMEM_TYPE_SOURCE")
            require(value == constant("EXECMEM_DEFAULT").toLong()) { "ULTRA_KERNEL_MANAGEMENT_ABI_EXECMEM_TYPE" }
            require("code" !in facts.enabledFeatures || config["CONFIG_EXECMEM"] == "y" &&
                (config["CONFIG_ARCH_HAS_EXECMEM_ROX"] != "y" || execmemRw == 1)) {
                "ULTRA_KERNEL_MANAGEMENT_ABI_EXECMEM_WRITABLE_SOURCE"
            }
        } else require(execmemRw == 0 && ("code" !in facts.enabledFeatures || config["CONFIG_MODULES"] == "y")) {
            "ULTRA_KERNEL_MANAGEMENT_ABI_MODULE_SOURCE"
        }
        if ("text" in facts.enabledFeatures) {
            for (name in setOf("aarch64_insn_patch_text", "cpus_read_lock", "cpus_read_unlock"))
                require(UltraKernelBtf.functionSignature(kernel, symbols, name) != null) {
                    "ULTRA_KERNEL_MANAGEMENT_ABI_TEXT_FUNCTION:$name"
                }
            val mutex = UltraKernelBtf.typeSignature(kernel, symbols, "mutex")
            require(mutex != null && mutex.startsWith("smutex:") &&
                UltraKernelBtf.variableSignatures(kernel, symbols, setOf("text_mutex"))?.get("text_mutex") == mutex) {
                "ULTRA_KERNEL_MANAGEMENT_ABI_TEXT_MUTEX"
            }
        }
    }

    fun verifyProcattr(kernel: ByteArray, symbols: UltraKernelSymbols, words: IntArray, facts: ProcattrFacts,
        bound: Map<String, BoundField> = emptyMap()) {
        require(words.size == 40 && words[0] == 1 && facts.layoutSchema == 1 && facts.layoutWords == 40 &&
            facts.compiledLayouts.keys == procattrLayoutTypes.keys && facts.typeAlignments.keys == procattrLayoutTypes.keys) {
            "ULTRA_KERNEL_PROCATTR_LAYOUT_SCHEMA"
        }
        val encoded = java.nio.ByteBuffer.allocate(words.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .apply { words.forEach { putInt(it) } }.array()
        require(facts.layoutSha256 == UltraKernelSpec.sha256(encoded)) { "ULTRA_KERNEL_PROCATTR_LAYOUT_IDENTITY" }
        val expectedFacts = setOf("schema", "kernelVersion", "configured", "procMagic") +
            procattrLayoutTypes.keys.map { it + "Align" }
        require(facts.compiledFacts.keys == expectedFacts && facts.compiledFacts["schema"] == words[0] &&
            facts.compiledFacts["kernelVersion"] == words[1] && facts.compiledFacts["configured"] == words[2] &&
            facts.compiledFacts["procMagic"] == words[39] && words[39] == 0x9fa0 && words[2] in 0..1) {
            "ULTRA_KERNEL_PROCATTR_COMPILED_FACTS"
        }
        val config = targetConfig(kernel)
        require(facts.config.keys == cfiConfigs + procattrConfigKeys) { "ULTRA_KERNEL_PROCATTR_CONFIG_FIELDS" }
        for ((key, value) in facts.config) require(value in setOf("y", "n") && value == config.getValue(key)) {
            "ULTRA_KERNEL_PROCATTR_CONFIG:$key"
        }
        require((words[2] == 1) == (config["CONFIG_PROC_FS"] == "y" && config["CONFIG_SECURITY"] == "y")) {
            "ULTRA_KERNEL_PROCATTR_CONFIGURED"
        }
        for ((prefix, type) in procattrLayoutTypes) {
            val layout = facts.compiledLayouts.getValue(prefix)
            val sizeAt = procattrSizeWords.getValue(prefix)
            val bytes = words[sizeAt]
            val alignment = words[sizeAt + 1]
            require(bytes > 0 && layout.bytes == bytes && alignment > 0 && alignment and (alignment - 1) == 0 &&
                bytes % alignment == 0 && facts.typeAlignments[prefix] == alignment &&
                facts.compiledFacts[prefix + "Align"] == alignment && layout.members.keys == type.second.keys &&
                layout.signature.startsWith("s${type.first}:$bytes{")) { "ULTRA_KERNEL_PROCATTR_COMPILED_TYPE:$prefix" }
            val target = UltraKernelBtf.selectedStructure(kernel, symbols, type.first, type.second.keys)
                ?: error("ULTRA_KERNEL_PROCATTR_BTF_REQUIRED:$prefix")
            val names = type.second.keys.map { type.first + "." + it }
            require(target.bytes == ownerSize(type.first, bytes, names, bound)) { "ULTRA_KERNEL_PROCATTR_TARGET_SIZE:$prefix" }
            for ((member, at) in type.second) {
                val field = layout.members.getValue(member)
                require(field.offset == words[at] && field.bytes == words[at + 1] && field.offset >= 0 &&
                    field.bytes > 0 && field.offset.toLong() + field.bytes <= bytes &&
                    field.signature.isNotEmpty() && target.members[member]?.let { actual ->
                        actual.signature == field.signature && fieldMatches(type.first + "." + member,
                            UltraKernelLayoutField(actual.offset, actual.bytes, target.bytes, "btf"), field.offset, field.bytes, bound)
                    } == true) {
                    "ULTRA_KERNEL_PROCATTR_TARGET_MEMBER:$prefix.$member"
                }
            }
        }
        if (words[2] == 0) {
            require(facts.functions.isEmpty() && facts.writeSignature == null && facts.cfiMode == null &&
                facts.normalizeIntegers == null && facts.markerTypeid == null) { "ULTRA_KERNEL_PROCATTR_DISABLED_NATIVE_FACTS" }
            return
        }
        require(facts.functions.keys == setOf("filp_open", "vfs_write", "fput", "proc_pid_attr_write") &&
            facts.functions.values.all { it.startsWith("f") && it.length <= 65536 } &&
            facts.writeSignature == "*" + facts.functions.getValue("vfs_write") &&
            facts.writeSignature == "*" + facts.functions.getValue("proc_pid_attr_write") &&
            facts.writeSignature == facts.compiledLayouts.getValue("fileOperations").members.getValue("write").signature) {
            "ULTRA_KERNEL_PROCATTR_NATIVE_ABI"
        }
        for ((name, expected) in facts.functions) require(
            UltraKernelBtf.functionSignature(kernel, symbols, name) == expected) { "ULTRA_KERNEL_PROCATTR_FUNCTION:$name" }
        require(facts.cfiMode in setOf("none", "kcfi") && facts.normalizeIntegers != null) {
            "ULTRA_KERNEL_PROCATTR_MARKER_CFI"
        }
        verifyCfi(kernel, facts.cfiMode!!, facts.normalizeIntegers!!)
        require((facts.markerTypeid != null) == (facts.cfiMode == "kcfi")) { "ULTRA_KERNEL_PROCATTR_MARKER_TYPEID" }
        UltraKernelBtf.verifyFunctionMarker(kernel, symbols, "proc_pid_attr_write",
            facts.functions.getValue("proc_pid_attr_write"), facts.markerTypeid)
    }

    private fun verifySupplemental(kernel: ByteArray, symbols: UltraKernelSymbols, words: IntArray,
        facts: SupplementalFacts, expectedWords: Int, sizes: Map<Int, String>, members: Map<Int, String>,
        optionalTypes: Set<Int>, optionalMembers: Set<Int>, enabled: Boolean, bound: Map<String, BoundField>) {
        require(words.size == expectedWords && words[0] == 1 && facts.layoutSchema == 1 &&
            facts.layoutWords == expectedWords) { "ULTRA_KERNEL_SUPPLEMENTAL_LAYOUT_SCHEMA" }
        val compiled = java.nio.ByteBuffer.allocate(words.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .apply { words.forEach { putInt(it) } }.array()
        require(facts.compiledLayoutSha256 == UltraKernelSpec.sha256(compiled)) {
            "ULTRA_KERNEL_SUPPLEMENTAL_LAYOUT_IDENTITY"
        }
        val typePositions = sizes.entries.associate { it.value to it.key }
        for ((at, type) in sizes) {
            val bytes = words[at]; val alignment = words[at + 1]
            if (bytes == -1) require(at in optionalTypes && alignment == 0) {
                "ULTRA_KERNEL_SUPPLEMENTAL_TYPE_ABSENT:$type"
            } else require(bytes > 0 && alignment > 0 && alignment and (alignment - 1) == 0 && bytes % alignment == 0) {
                "ULTRA_KERNEL_SUPPLEMENTAL_TYPE:$type"
            }
        }
        for ((at, member) in members) {
            val offset = words[at]; val bytes = words[at + 1]
            if (offset == -1) require(at in optionalMembers && bytes == 0) {
                "ULTRA_KERNEL_SUPPLEMENTAL_MEMBER_ABSENT:$member"
            } else {
                val owner = words[typePositions.getValue(member.substringBefore('.'))]
                require(offset >= 0 && bytes > 0 && owner > 0 && offset.toLong() + bytes <= owner) {
                    "ULTRA_KERNEL_SUPPLEMENTAL_MEMBER_RANGE:$member"
                }
            }
        }
        if (!enabled) return
        require(facts.sourceQualification != null || facts.targetQualified) { "ULTRA_KERNEL_SUPPLEMENTAL_SOURCE_UNQUALIFIED" }
        // Source proof permits these actual target checks; it carries no target/device pass flag.
        val config = targetConfig(kernel)
        for ((key, value) in facts.config) require(value in setOf("y", "n") && value == config[key]) {
            "ULTRA_KERNEL_SUPPLEMENTAL_TARGET_CONFIG:$key"
        }
        val wanted = sizes.filterKeys { words[it] != -1 }.values.map { "$it.__size" }.toSet() +
            members.filterKeys { words[it] != -1 }.values
        val layout = UltraKernelBtf.abi(kernel, symbols, wanted)
            ?: error("ULTRA_KERNEL_SUPPLEMENTAL_TARGET_BTF")
        for ((at, type) in sizes) {
            if (words[at] == -1) continue
            val actual = layout.fields["$type.__size"]
            require(actual?.widthBytes == ownerSize(type, words[at], members.values, bound) &&
                facts.typeAlignments[type] == words[at + 1]) {
                "ULTRA_KERNEL_SUPPLEMENTAL_TARGET_TYPE:$type"
            }
        }
        for ((at, member) in members) {
            if (words[at] == -1) continue
            val actual = layout.fields[member]
            require(fieldMatches(member, actual, words[at], words[at + 1], bound)) {
                "ULTRA_KERNEL_SUPPLEMENTAL_TARGET_MEMBER:$member"
            }
        }
    }

    fun verifyProcattr(kernel: ByteArray, symbols: UltraKernelSymbols, words: IntArray,
        facts: SupplementalFacts, enabled: Boolean, bound: Map<String, BoundField> = emptyMap()) {
        require(words.size == 40 && words[2] in 0..1 && words[39] == 0x9fa0) {
            "ULTRA_KERNEL_PROCATTR_LAYOUT"
        }
        val sizes = linkedMapOf(3 to "file", 11 to "path", 15 to "file_operations", 19 to "inode",
            23 to "super_block", 27 to "dentry", 33 to "qstr")
        val members = linkedMapOf(5 to "file.f_op", 7 to "file.f_path", 9 to "file.f_inode",
            13 to "path.dentry", 17 to "file_operations.write", 21 to "inode.i_sb",
            25 to "super_block.s_magic", 29 to "dentry.d_parent", 31 to "dentry.d_name",
            35 to "qstr.len", 37 to "qstr.name")
        verifySupplemental(kernel, symbols, words, facts, 40, sizes, members, emptySet(), emptySet(), enabled, bound)
        if (!enabled) return
        require(facts.config.keys.containsAll(procattrConfigKeys) &&
            (words[2] == 1) == procattrConfigKeys.all { facts.config[it] == "y" }) {
            "ULTRA_KERNEL_PROCATTR_CONFIG"
        }
    }

    fun verifyTaskState(kernel: ByteArray, symbols: UltraKernelSymbols, words: IntArray,
        facts: SupplementalFacts, enabled: Boolean, bound: Map<String, BoundField> = emptyMap()) {
        require(words.size == 53 && (2..6).all { words[it] in 0..1 } &&
            words.sliceArray(50..52).contentEquals(intArrayOf(0, 1, 2))) { "ULTRA_KERNEL_TASK_STATE_LAYOUT" }
        val seccomp = words[2] == 1
        val sizes = linkedMapOf(7 to "task_struct", 17 to "seccomp", 25 to "sighand_struct",
            29 to "thread_info", 34 to "spinlock_t", 38 to "raw_spinlock_t")
        val work = if (words[4] == 1) "thread_info.syscall_work" else "thread_info.flags"
        val members = linkedMapOf(9 to "task_struct.seccomp", 11 to "task_struct.sighand",
            13 to "task_struct.thread_info", 15 to "task_struct.stack", 19 to "seccomp.mode",
            21 to "seccomp.filter_count", 23 to "seccomp.filter", 27 to "sighand_struct.siglock",
            31 to work, 36 to "spinlock_t.rlock", 40 to "raw_spinlock_t.raw_lock",
            42 to "raw_spinlock_t.magic", 44 to "raw_spinlock_t.owner_cpu", 46 to "raw_spinlock_t.owner",
            48 to "raw_spinlock_t.dep_map")
        val seccompMembers = setOf(9, 19, 21, 23, 31)
        val optional = setOf(42, 44, 46, 48) + (if (seccomp) emptySet() else seccompMembers) +
            (if (words[5] == 0) setOf(13) else emptySet()) + (if (words[6] == 1) setOf(36) else emptySet())
        require((words[17] == -1) == !seccomp && (words[13] == -1) == (words[5] == 0) &&
            (words[36] == -1) == (words[6] == 1) &&
            (!seccomp || words[3] in 0..1) &&
            (if (seccomp) words[33] >= 0 && words[33].toLong() < words[32].toLong() * 8
                else words[3] == 0 && words[33] == -1 && seccompMembers.all { words[it] == -1 })) {
            "ULTRA_KERNEL_TASK_STATE_OPTIONAL"
        }
        verifySupplemental(kernel, symbols, words, facts, 53, sizes, members,
            if (seccomp) emptySet() else setOf(17), optional, enabled, bound)
        if (!enabled) return
        require(facts.config.keys.containsAll(taskConfigKeys) &&
            facts.constants["MSU_MANAGEMENT_TASK_STATE_VERIFIED"] == 1 &&
            facts.callbackOrigins.keys == taskCallbacks) { "ULTRA_KERNEL_TASK_STATE_TARGET_PROOF" }
        require(facts.constants["SECCOMP_MODE_DISABLED"] == words[50] &&
            facts.constants["SECCOMP_MODE_STRICT"] == words[51] &&
            facts.constants["SECCOMP_MODE_FILTER"] == words[52]) { "ULTRA_KERNEL_TASK_STATE_MODES" }
        for ((at, name) in mapOf(2 to "CONFIG_SECCOMP", 3 to "CONFIG_SECCOMP_FILTER", 4 to "CONFIG_GENERIC_ENTRY",
            5 to "CONFIG_THREAD_INFO_IN_TASK", 6 to "CONFIG_PREEMPT_RT")) require((words[at] == 1) == (facts.config[name] == "y")) {
            "ULTRA_KERNEL_TASK_STATE_CONFIG:$name"
        }
        if (seccomp) require(facts.constants[if (words[4] == 1) "SYSCALL_WORK_BIT_SECCOMP" else "TIF_SECCOMP"] == words[33]) {
            "ULTRA_KERNEL_TASK_STATE_WORK_BIT"
        }
        require((words[42] != -1) == (facts.config["CONFIG_DEBUG_SPINLOCK"] == "y") &&
            (words[44] != -1) == (words[42] != -1) && (words[46] != -1) == (words[42] != -1) &&
            (words[48] != -1) == (facts.config["CONFIG_DEBUG_LOCK_ALLOC"] == "y")) {
            "ULTRA_KERNEL_TASK_STATE_LOCK_CONFIG"
        }
        val lock = UltraKernelBtf.memberSignature(kernel, symbols, "sighand_struct", "siglock")
        require(lock != null && lock == UltraKernelBtf.typedefSignature(kernel, symbols, "spinlock_t")) {
            "ULTRA_KERNEL_TASK_STATE_LOCK_TYPE"
        }
    }

    fun verify(kernel: ByteArray, symbols: UltraKernelSymbols, resolverWords: IntArray,
               watchWords: IntArray, ownerWords: IntArray, bound: Map<String, BoundField> = emptyMap()) {
        require(resolverWords.size == 6 + resolver.size * 3 && resolverWords[0] == 1 &&
            resolverWords[1] == resolver.size && resolverWords[5] == 0 &&
            resolverWords[2] in 0..1 && resolverWords[3] in 0..1) {
            "ULTRA_KERNEL_RESIDENT_ABI_RESOLVER_SCHEMA"
        }
        require(ownerWords.size == 2) { "ULTRA_KERNEL_RESIDENT_ABI_OWNER_SCHEMA" }
        require(watchWords.size == 47 && watchWords[0] == 1) {
            "ULTRA_KERNEL_RESIDENT_ABI_WATCH_SCHEMA"
        }
        val config = targetConfig(kernel)
        val mask = configs.foldIndexed(0) { index, bits, name ->
            bits or (if (config.getValue(name) == "y") 1 shl index else 0)
        }
        require(mask == resolverWords[4]) { "ULTRA_KERNEL_RESIDENT_ABI_CONFIG_MISMATCH" }
        // RT lock internals and preemption variants require their own ABI proof.
        require(config.getValue("CONFIG_PREEMPT_RT") != "y") { "ULTRA_KERNEL_RESIDENT_ABI_PREEMPT_RT" }
        val wanted = resolver.toMutableSet()
        val optional = resolver.filterIndexed { index, _ -> resolverWords[6 + index * 3] == -1 }.toMutableSet()
        val watchPairs = linkedMapOf(
            2 to "fsnotify_mark.__size", 4 to "fsnotify_mark.mask",
            6 to "fsnotify_ops.__size", 8 to "fsnotify_ops.handle_inode_event",
            10 to "fsnotify_ops.freeing_mark", 12 to "fsnotify_ops.free_mark",
            14 to "qstr.__size", 16 to "qstr.len", 18 to "qstr.name",
            20 to "inode.__size", 22 to "inode.i_mode",
            24 to "mutex.__size", 26 to "mutex.rtmutex", 28 to "mutex.owner",
            30 to "mutex.wait_lock", 32 to "mutex.wait_list", 34 to "mutex.osq",
            36 to "mutex.magic", 38 to "mutex.dep_map",
            40 to "lock_class_key.__size", 42 to "raw_spinlock_t.__size",
            44 to "list_head.__size",
        )
        wanted += watchPairs.values
        optional += watchPairs.filterKeys { watchWords[it] == -1 }.values
        val layout = UltraKernelBtf.abi(kernel, symbols, wanted, optional)
            ?: error("ULTRA_KERNEL_RESIDENT_ABI_BTF_REQUIRED")
        for ((index, key) in resolver.withIndex()) {
            val at = 6 + index * 3
            val offset = resolverWords[at]
            val width = resolverWords[at + 1]
            val owner = resolverWords[at + 2]
            val actual = layout.fields[key]
            if (offset == -1) {
                require(width == 0 && owner == 0 && actual == null) {
                    "ULTRA_KERNEL_RESIDENT_ABI_OPTIONAL_MISMATCH:$key"
                }
            } else {
                val exactSize = key.startsWith("kstat.") || key.startsWith("path.") ||
                    key == "spinlock_t.__size" || key == "arch_spinlock_t.__size"
                require(fieldMatches(key, actual, offset, width, bound) && actual != null &&
                    owner >= offset.toLong() + width && actual.ownerSizeBytes != null &&
                    actual.ownerSizeBytes >= actual.offsetBytes.toLong() + width &&
                    (!exactSize || actual.ownerSizeBytes == owner)) {
                    "ULTRA_KERNEL_RESIDENT_ABI_FIELD_MISMATCH:$key"
                }
            }
        }
        require(fieldMatches("task_struct.cred", layout.fields.getValue("task_struct.cred"), ownerWords[0], 8, bound) &&
            fieldMatches("cred.uid", layout.fields.getValue("cred.uid"), ownerWords[1], 4, bound)) {
            "ULTRA_KERNEL_RESIDENT_ABI_OWNER_MISMATCH"
        }
        for ((at, key) in watchPairs) {
            val actual = layout.fields[key]
            if (watchWords[at] == -1) {
                require(watchWords[at + 1] == 0 && actual == null) {
                    "ULTRA_KERNEL_RESIDENT_ABI_WATCH_OPTIONAL:$key"
                }
            } else if (key.endsWith(".__size")) {
                val external = key == "qstr.__size" || key == "inode.__size"
                require(actual != null && (external || actual.widthBytes == watchWords[at])) {
                    "ULTRA_KERNEL_RESIDENT_ABI_WATCH_SIZE:$key"
                }
                // BTF specifies size, not C object alignment. This only checks
                // that the compiled alignment is internally consistent.
                require(watchWords[at + 1] > 0 &&
                    watchWords[at + 1] and (watchWords[at + 1] - 1) == 0 &&
                    watchWords[at] % watchWords[at + 1] == 0) {
                    "ULTRA_KERNEL_RESIDENT_ABI_WATCH_ALIGN:$key"
                }
            } else require(fieldMatches(key, actual, watchWords[at], watchWords[at + 1], bound)) {
                "ULTRA_KERNEL_RESIDENT_ABI_WATCH_FIELD:$key"
            }
        }
        val targetGroup = UltraKernelBtf.functionParameterCount(kernel, symbols, "fsnotify_alloc_group")
            ?: error("ULTRA_KERNEL_RESIDENT_ABI_FUNCTION_MISSING:fsnotify_alloc_group")
        require(targetGroup == watchWords[46]) { "ULTRA_KERNEL_RESIDENT_ABI_GROUP_ARGUMENTS" }
    }

    internal fun targetConfig(kernel: ByteArray): Map<String, String> {
        fun find(marker: ByteArray, from: Int): Int {
            for (at in from..kernel.size - marker.size)
                if (marker.indices.all { kernel[at + it] == marker[it] }) return at
            return -1
        }
        val beginMarker = "IKCFG_ST".toByteArray(Charsets.US_ASCII)
        val endMarker = "IKCFG_ED".toByteArray(Charsets.US_ASCII)
        val start = find(beginMarker, 0)
        require(start >= 0 && find(beginMarker, start + 1) < 0) {
            "ULTRA_KERNEL_RESIDENT_ABI_CONFIG_MISSING"
        }
        val origin = start + beginMarker.size
        val end = find(endMarker, origin)
        require(end > origin && end - origin <= 1048576) { "ULTRA_KERNEL_RESIDENT_ABI_CONFIG_BOUNDS" }
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(kernel, origin, end - origin)).use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 4 * 1048576) { "ULTRA_KERNEL_RESIDENT_ABI_CONFIG_SIZE" }
                output.write(buffer, 0, count)
            }
        }
        val result = linkedMapOf<String, String>()
        for (line in String(output.toByteArray(), Charsets.US_ASCII).lineSequence()) {
            val key: String
            val value: String
            if (line.startsWith("CONFIG_") && '=' in line) {
                key = line.substringBefore('='); value = line.substringAfter('=')
            } else if (line.startsWith("# CONFIG_") && line.endsWith(" is not set")) {
                key = line.removePrefix("# ").removeSuffix(" is not set"); value = "n"
            } else continue
            if (key in configs || key in cfiConfigs || key in managementConfigKeys || key in uidConfigKeys ||
                key in procattrConfigKeys || key in taskConfigKeys || key in sessionConfigKeys) {
                require(value in setOf("y", "n") && result.put(key, value) == null) {
                    "ULTRA_KERNEL_RESIDENT_ABI_CONFIG_VALUE:$key"
                }
            }
        }
        // In the target's embedded complete .config, an undefined CONFIG_* is
        // disabled, exactly as IS_ENABLED() in the compiled payload observes.
        return (configs + cfiConfigs + managementConfigKeys + uidConfigKeys + procattrConfigKeys + taskConfigKeys + sessionConfigKeys)
            .associateWith { result[it] ?: "n" }
    }
}
