package dev.maplesu.manager.ultrakernel

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Binds the existing exec frame and one-shot boot initialization to the retained host. */
internal object UltraKernelManagerClaimCarrier {
    data class Plan(
        val records: List<UltraKernelPlan.PatchRecord>,
        val bootstrapOffset: Int,
        val materializerOffset: Int,
        val anchorOffset: Int,
        val bindingOffset: Int,
        val descriptorOffset: Int,
        val descriptorBytes: Int,
        val descriptorCrc: Long,
        val chunkTableOffset: Int,
        val chunkCount: Int,
        val payloadEntryOffset: Int,
        val payloadMemoryBytes: Int,
        val qualifiedPaddingBytes: Int,
        val remainingQualifiedBytes: Int,
        val permissionsOffset: Int? = null,
        val continuationOffset: Int? = null,
        val deploymentOffset: Int? = null,
    )

    private data class Section(val name: String, val offset: Int, val file: ByteArray,
                               val memory: Int, val flags: Int)
    private data class Relocation(val target: Int, val kind: Int, val value: Int)
    private data class Payload(val sections: List<Section>, val imports: List<Int>,
                               val relocations: List<Relocation>, val entry: Int, val memory: Int,
                               val kernelMemoryBytes: Int,
                               val exports: Map<String, Int>, val layouts: Map<String, IntArray>,
                                val importNames: Set<String>, val residentHost: ResidentHost? = null,
                                val runtimeLayout: RuntimeLayout? = null,
                                val runtimeProfiles: RuntimeProfiles? = null,
                                val boundFields: Map<String, UltraKernelResidentAbi.BoundField> = emptyMap())
    private data class RuntimeLayout(val offset: Int, val bytes: Int)
    private data class RuntimeProfiles(val offset: Int, val bytes: Int)
    private data class ResidentHost(val offset: Int, val bytes: Int, val sectionName: String,
                                    val sectionOffset: Int, val sectionBytes: Int, val sectionFlags: Long)
    private data class Span(val start: Int, val end: Int)
    private data class Chunk(val source: Int, val bytes: ByteArray)

    /** Adds the retained carrier and routes the existing saved exec frame through it. */
    fun connect(
        kernel: ByteArray,
        symbols: UltraKernelSymbols,
        records: List<UltraKernelPlan.PatchRecord>,
        payload: UltraKernelManagerClaimPayload,
        deploymentNamespace: UltraKernelDeploymentNamespace? = null,
        profileSymbols: UltraKernelSymbols = symbols,
    ): List<UltraKernelPlan.PatchRecord> {
        val frame = records.single { it.role == "arm64_notify_die+0x0" }
        val gate = records.single { it.role == "die+0x0" }
        val credential = records.single { it.role == "panic+0x0" }
        require(credential.replacement.size == 48 && frame.replacement.size >= 4) {
            "ULTRA_KERNEL_MANAGER_CLAIM_RECORDS"
        }
        val frameBranch = frame.offset.toInt() + frame.replacement.size - 4
        require(UltraKernelA64.le32Bits(frame.replacement, frame.replacement.size - 4) ==
            UltraKernelA64.encodeB(frameBranch.toLong(), gate.offset)) {
            "ULTRA_KERNEL_MANAGER_CLAIM_FRAME_BRANCH"
        }
        require(kernel.size >= 64) { "ULTRA_KERNEL_MANAGER_CLAIM_IMAGE_HEADER" }
        val pageShift = when ((UltraKernelA64.le32Bits(kernel, 24) ushr 1) and 3) {
            1 -> 12
            2 -> 14
            3 -> 16
            else -> error("ULTRA_KERNEL_MANAGER_CLAIM_PAGE_SIZE")
        }
        fun symbol(vararg names: String): Int = names.firstNotNullOfOrNull(symbols::offsetOf)
            ?: error("ULTRA_KERNEL_MANAGER_CLAIM_IMPORT_MISSING:${names.joinToString("/")}")
        val execmem = symbols.offsetOf("module_alloc") == null
        val allocate = if (execmem) symbol("execmem_alloc") else symbol("module_alloc")
        val release = if (execmem) symbol("execmem_free") else symbol("module_memfree")
        val permissionNames = listOf("set_memory_nx", "set_memory_rw", "set_memory_ro", "set_memory_x")
        val permissions = permissionNames.map { symbol(it) }
        val dataSignatures = payload.referenceAbi?.let {
            require(it.dataSignatures.keys == it.dataImports) { "ULTRA_KERNEL_RESIDENT_REFERENCE_DATA_IMPORTS" }
            it.dataSignatures
        }.orEmpty()
        val elf = verifyTaskProfile(bindRuntimeProfiles(bindRuntimeLayout(parseElf(payload.elf, symbols, kernel, dataSignatures),
            payload.runtimeLayout, kernel, symbols), payload.runtimeProfiles, kernel, profileSymbols, records),
            payload.referenceAbi?.taskProfile, kernel, symbols, profileSymbols)
        val dynamicFields = elf.boundFields
        val resident = RESIDENT_EXPORTS.any(elf.exports::containsKey)
        require(payload.permissions == null || resident) { "ULTRA_KERNEL_RESIDENT_SUPPORT_REQUIRES_HOST" }
        val cacheName = if (resident) "sync_icache_aliases" else
            if (symbols.offsetOf("caches_clean_inval_pou") != null) "caches_clean_inval_pou" else "__flush_icache_range"
        val cache = symbol(cacheName)
        val bootstrapNames = permissionNames + listOf(if (execmem) "execmem_alloc" else "module_alloc",
            if (execmem) "execmem_free" else "module_memfree", cacheName)
        val storage = if (resident) {
            require(RESIDENT_EXPORTS.all(elf.exports::containsKey)) { "ULTRA_KERNEL_RESIDENT_EXPORTS" }
            require(payload.permissions != null) { "ULTRA_KERNEL_RESIDENT_PERMISSIONS_SUPPORT_MISSING" }
            val reference = payload.referenceAbi ?: error("ULTRA_KERNEL_RESIDENT_REFERENCE_ABI_MISSING")
            require(reference.functions.keys.intersect(reference.dataImports).isEmpty() &&
                reference.functions.keys + reference.dataImports == elf.importNames) { "ULTRA_KERNEL_RESIDENT_REFERENCE_IMPORTS" }
            require(bootstrapNames.all(reference.bootstrapFunctions::containsKey)) {
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_ABI_MISSING"
            }
            val managementWords = elf.layouts["msu_management_compiled_layout"]
            require((managementWords != null) == (reference.management != null) &&
                (elf.exports.keys.any { it in MANAGEMENT_EXPORTS }) == (managementWords != null)) {
                "ULTRA_KERNEL_MANAGEMENT_REFERENCE_MISSING"
            }
            require(managementWords == null || MANAGEMENT_EXPORTS.all(elf.exports::containsKey)) {
                "ULTRA_KERNEL_MANAGEMENT_EXPORTS"
            }
            val domainWords = elf.layouts["msu_credential_domain_compiled_layout"]
            require((domainWords != null) == (reference.credentialDomainSha256 != null)) {
                "ULTRA_KERNEL_CREDENTIAL_DOMAIN_REFERENCE"
            }
            if (domainWords != null) UltraKernelResidentAbi.verifyCredentialDomain(
                domainWords, requireNotNull(reference.credentialDomainSha256))
            val origins = reference.management?.let {
                it.callbackOrigins + it.uidSnapshot?.callbackOrigins.orEmpty() +
                    (if ("task_state" in it.enabledFeatures) it.taskState?.callbackOrigins.orEmpty() else emptyMap())
            }.orEmpty()
            UltraKernelResidentAbi.verifyFunctions(kernel, symbols, reference.functions +
                reference.bootstrapFunctions.filterKeys { it in bootstrapNames }, reference.callbacks, origins)
            if (managementWords != null) {
                val managementBytes = buffer(managementWords.size * 4).apply { managementWords.forEach { putInt(it) } }.array()
                require(reference.managementLayoutSha256 == UltraKernelSpec.sha256(managementBytes)) {
                    "ULTRA_KERNEL_MANAGEMENT_LAYOUT_IDENTITY"
                }
                val management = requireNotNull(reference.management)
                UltraKernelResidentAbi.verifyManagement(kernel, symbols, managementWords, management, dynamicFields)
                fun supplemental(name: String, facts: UltraKernelResidentAbi.SupplementalFacts?, words: Int): IntArray? {
                    val actual = elf.layouts[name]
                    require((actual != null) == (facts != null)) { "ULTRA_KERNEL_SUPPLEMENTAL_LAYOUT_REFERENCE:$name" }
                    if (actual == null || facts == null) return null
                    val bytes = buffer(actual.size * 4).apply { actual.forEach { putInt(it) } }.array()
                    require(actual.size == words && facts.compiledLayoutSha256 == UltraKernelSpec.sha256(bytes)) {
                        "ULTRA_KERNEL_SUPPLEMENTAL_LAYOUT_IDENTITY:$name"
                    }
                    return actual
                }
                supplemental("msu_procattr_compiled_layout", management.procattr, 40)?.let {
                    UltraKernelResidentAbi.verifyProcattr(kernel, symbols, it, management.procattr!!,
                        "procattr" in management.enabledFeatures, dynamicFields)
                    management.procattrNative?.let { native ->
                        UltraKernelResidentAbi.verifyProcattr(kernel, symbols, it, native, dynamicFields)
                    }
                }
                supplemental("msu_task_state_compiled_layout", management.taskState, 53)?.let {
                    if ("task_state" !in management.enabledFeatures) require(
                        setOf("__seccomp_filter_release", "seccomp_filter_release").none { it in elf.importNames } &&
                            "msu_task_state_release_current.validate" !in reference.callbacks) {
                        "ULTRA_KERNEL_TASK_STATE_UNQUALIFIED_IMPORT"
                    }
                    UltraKernelResidentAbi.verifyTaskState(kernel, symbols, it, management.taskState!!,
                        "task_state" in management.enabledFeatures, dynamicFields)
                }
                if ("code" in management.enabledFeatures) {
                    val execmemBackend = management.constants.getValue("MSU_MANAGEMENT_EXECMEM") == 1
                    val allocateName = if (!execmemBackend) "module_alloc" else
                        if (management.constants.getValue("MSU_MANAGEMENT_EXECMEM_ALLOC_RW") == 1) "execmem_alloc_rw" else "execmem_alloc"
                    val releaseName = if (execmemBackend) "execmem_free" else "module_memfree"
                    require(setOf(allocateName, releaseName).all(reference.functions::containsKey) &&
                        reference.functions.keys.intersect(setOf("module_alloc", "module_memfree", "execmem_alloc", "execmem_alloc_rw", "execmem_free")) ==
                            setOf(allocateName, releaseName)) { "ULTRA_KERNEL_MANAGEMENT_ALLOCATOR_IMPORTS" }
                }
                UltraKernelResidentAbi.verifyData(kernel, symbols, reference.dataSignatures)
                val descriptor = reference.descriptor ?: error("ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_SOURCE")
                val host = elf.residentHost ?: error("ULTRA_KERNEL_MANAGEMENT_HOST_SYMBOL")
                require(descriptor.hostSymbolOffset == host.offset && descriptor.hostBytes == host.bytes &&
                    descriptor.sectionName == host.sectionName && descriptor.sectionOffset == host.sectionOffset &&
                    descriptor.sectionBytes == host.sectionBytes && descriptor.sectionFlags == host.sectionFlags &&
                    host.sectionFlags and 7L == 3L && descriptor.descriptorBytes == 80 &&
                    descriptor.baseOffset >= 0 && descriptor.extendedOffset >= 0) {
                    "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_RANGE"
                }
                val descriptorOffsets = (listOf(descriptor.baseOffset, descriptor.extendedOffset) +
                    listOfNotNull(descriptor.uidOffset, descriptor.procattrOffset, descriptor.taskOffset)).sorted()
                require(descriptorOffsets.size == management.wireLayout.getValue("queryViews") &&
                    descriptorOffsets.all { it >= 0 && it % 8 == 0 && it.toLong() + 80 <= host.bytes } &&
                    descriptorOffsets.zipWithNext().all { (before, after) -> before.toLong() + 80 <= after }) {
                    "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_RANGES"
                }
            }
            UltraKernelResidentAbi.verifyCfi(kernel, reference.cfi.mode, reference.cfi.normalizeIntegers)
            if (reference.cfi.mode == "kcfi") {
                require(reference.cfi.prefixes.keys == reference.callbacks.keys) { "ULTRA_KERNEL_RESIDENT_CFI_CALLBACKS" }
                for ((name, prefix) in reference.cfi.prefixes) {
                    val section = elf.sections.singleOrNull { it.flags and 4 != 0 && prefix.entry - 4 >= it.offset &&
                        prefix.entry.toLong() + 4 <= it.offset.toLong() + it.file.size }
                        ?: error("ULTRA_KERNEL_RESIDENT_CFI_CALLBACK_RANGE:$name")
                    require(UltraKernelA64.le32Bits(section.file, prefix.entry - section.offset - 4) == prefix.typeid) {
                        "ULTRA_KERNEL_RESIDENT_CFI_CALLBACK_PREFIX:$name"
                    }
                }
                UltraKernelResidentAbi.verifyCallbackCfi(kernel, symbols, reference.callbacks,
                    reference.cfi.prefixes.mapValues { it.value.typeid }, origins)
            }
            fun layout(name: String) = elf.layouts[name] ?: error("ULTRA_KERNEL_RESIDENT_LAYOUT_MISSING:$name")
            UltraKernelResidentAbi.verify(kernel, symbols, layout("msu_resolver_target_layout"),
                layout("msu_manager_owner_watch_compiled_layout"), layout("msu_manager_owner_compiled_layout"), dynamicFields)
            UltraKernelResidentStorage.candidate(kernel, symbols)
        } else null
        val cacheAddressLength = resident && cacheUsesAddressLength(
            requireNotNull(payload.referenceAbi).bootstrapFunctions.getValue(cacheName))
        val bootInstruction = storage?.let {
            require(records.none { record -> record.offset < it.bootInitializerOffset + 4 &&
                it.bootInitializerOffset < record.offset + record.length }) { "ULTRA_KERNEL_RESIDENT_BOOT_ENTRY_OCCUPIED" }
            UltraKernelA64.le32Bits(kernel, it.bootInitializerOffset).also(::requireBootInstruction)
        }
        require(deploymentNamespace == null || payload.deploymentBootEntryOffset != null) {
            "ULTRA_KERNEL_DEPLOYMENT_SUPPORT_MISSING"
        }
        val deploymentRecord = deploymentNamespace?.record()
        val bootEntry = (if (deploymentRecord == null) payload.bootEntryOffset else payload.deploymentBootEntryOffset)?.also {
            require(storage != null && it >= 0 && it % 4 == 0 && it.toLong() + 4 <= payload.bootstrap.size) {
                "ULTRA_KERNEL_RESIDENT_BOOT_ENTRY"
            }
        }
        val bootCall = if (bootEntry == null) null else
            UltraKernelResidentStorage.bootCallsite(kernel, symbols, requireNotNull(storage)).also { at ->
                require(records.none { record -> record.offset < at + 4 && at < record.offset + record.length }) {
                    "ULTRA_KERNEL_RESIDENT_BOOT_CALL_OCCUPIED"
                }
            }
        val bootBytes = if (storage == null) 0 else
            bootInitializer(storage.bootInitializerOffset, storage, bootInstruction!!).size
        val startBytes = if (bootCall == null) 0 else BOOT_START_BYTES
        val residentBindingBytes = if (deploymentRecord == null) 32 else UltraKernelDeploymentNamespace.BINDING_BYTES
        val cacheAdapterBytes = if (cacheAddressLength) CACHE_ADAPTER_BYTES else 0
        val legacyProvider = if (resident) symbols.offsetOf("maplesu_legacy_hook_provider_get")?.let { get ->
            symbols.offsetOf("maplesu_legacy_hook_provider_put")?.let { put -> get to put }
        } else null
        val legacyBridgeBytes = if (legacyProvider == null) 0 else
            legacyScopeBridge(0, 0, 0, 0, 0).let { it.first.size + it.second.size }
        val continuation = ByteArray(RELAY_BYTES + bootBytes + startBytes +
            (if (resident) residentBindingBytes else 0) + cacheAdapterBytes + legacyBridgeBytes)
        val placed = plan(kernel, symbols, records, payload.elf, payload.bootstrap, payload.materializer,
            permissions = payload.permissions, continuation = continuation, deploymentRecord = deploymentRecord,
            runtimeLayout = payload.runtimeLayout, runtimeProfiles = payload.runtimeProfiles, profileSymbols = profileSymbols,
            sessionAbi = payload.sessionAbi, dataSignatures = dataSignatures, bootEventsAbi = payload.bootEventsAbi,
            ksuCompatAbi = payload.ksuCompatAbi)
        val relayAt = requireNotNull(placed.continuationOffset)
        val relay = relay(relayAt, placed.bootstrapOffset, placed.anchorOffset, gate.offset.toInt())
        check(relay.size == RELAY_BYTES) { "ULTRA_KERNEL_MANAGER_CLAIM_RELAY_SIZE" }
        relay.copyInto(continuation)
        val bootAt = relayAt + RELAY_BYTES
        val startAt = bootAt + bootBytes
        val residentBindingAt = startAt + startBytes
        val cacheEntry = if (cacheAddressLength) residentBindingAt + residentBindingBytes else cache
        if (cacheAddressLength) cacheAdapter(cacheEntry, cache).copyInto(continuation, cacheEntry - relayAt)
        val legacyBridgeAt = residentBindingAt + (if (resident) residentBindingBytes else 0) + cacheAdapterBytes
        val executor = if (legacyProvider == null) null else records.single { it.role == "__drm_printfn_coredump+0x0" }
        var legacyEndAt = 0
        if (legacyProvider != null && executor != null) {
            require(executor.replacement.size == UltraKernelBodies.EXECUTOR_BYTES &&
                UltraKernelA64.le32Bits(executor.replacement, 64) == UltraKernelA64.blr(10)) {
                "ULTRA_KERNEL_LEGACY_EXECUTOR_CALL"
            }
            fun destination(offset: Int): Int {
                val word = UltraKernelA64.le32Bits(executor.replacement, offset)
                require(word and 0xfc000000.toInt() == 0x14000000) { "ULTRA_KERNEL_LEGACY_EXECUTOR_BRANCH" }
                return Math.toIntExact(executor.offset + offset + UltraKernelA64.signExtend((word and 0x03ffffff).toLong(), 26) * 4)
            }
            val bridge = legacyScopeBridge(legacyBridgeAt, legacyProvider.first, legacyProvider.second,
                destination(44), destination(68))
            require(bridge.first.size + bridge.second.size == legacyBridgeBytes) { "ULTRA_KERNEL_LEGACY_SCOPE_SIZE" }
            legacyEndAt = legacyBridgeAt + bridge.first.size
            bridge.first.copyInto(continuation, legacyBridgeAt - relayAt)
            bridge.second.copyInto(continuation, legacyEndAt - relayAt)
        }
        if (storage != null) {
            bootInitializer(bootAt, storage, bootInstruction!!).copyInto(continuation, RELAY_BYTES)
            if (bootEntry != null) bootStart(startAt, storage.bootInitializerOffset,
                placed.bootstrapOffset + bootEntry, placed.anchorOffset).also {
                    check(it.size == BOOT_START_BYTES) { "ULTRA_KERNEL_RESIDENT_BOOT_START_SIZE" }
                }.copyInto(continuation, RELAY_BYTES + bootBytes)
            val append = records.single { it.role == "free_initmem+0x0" }
            require(append.replacement.size in setOf(58, 62, 92)) { "ULTRA_KERNEL_RESIDENT_APPEND_BYTES" }
            if (append.replacement.size == 92) {
                val extended = ByteBuffer.wrap(append.replacement).order(ByteOrder.LITTLE_ENDIAN)
                require(extended.getInt(4) == 0 && extended.getInt(62) == 0x3436534d &&
                    (extended.getShort(66).toInt() and 0xffff) == 88) { "ULTRA_KERNEL_RESIDENT_APPEND64_HEADER" }
            }
            val binding = buffer(residentBindingBytes).apply {
                if (deploymentRecord != null) { putInt(1); putInt(residentBindingBytes) }
                for (value in intArrayOf(storage.slotOffset, storage.imageMemoryBytes,
                    elf.exports.getValue(RESIDENT_EXPORTS[0]), elf.exports.getValue(RESIDENT_EXPORTS[1]),
                    elf.exports.getValue(RESIDENT_EXPORTS[2]), append.offset.toInt() + 4,
                    append.replacement.size - 4, 0)) putInt(value)
                if (deploymentRecord != null) {
                    putInt(requireNotNull(placed.deploymentOffset)); putInt(deploymentRecord.size); putInt(0); putInt(0)
                }
            }.array()
            if (deploymentRecord != null) bufferAt(binding, 48).putInt(
                UltraKernelDeploymentNamespace.bindingCrc(binding, deploymentRecord))
            binding.copyInto(continuation, residentBindingAt - relayAt)
        }
        val anchor = buffer(96).apply {
            for (value in intArrayOf(0x4143534d, if (deploymentRecord != null) 4 else if (bootEntry != null) 3 else if (resident) 2 else 1, placed.anchorOffset, pageShift,
                placed.materializerOffset, credential.offset.toInt(), allocate, release,
                *permissions.toIntArray(), cacheEntry, if (execmem) 1 else 0)) putInt(value)
            if (resident) putInt(80, residentBindingAt)
            placed.permissionsOffset?.let { putInt(84, it) }
        }.array()
        val carrier = placed.records.map { record ->
            val replacement = when (record.role) {
                "manager_claim:continuation" -> continuation
                "manager_claim:anchor" -> record.replacement.copyOf().apply {
                    anchor.copyInto(this, 0, 0, 56)
                    anchor.copyInto(this, 80, 80, 96)
                }
                else -> record.replacement
            }
            UltraKernelPlan.PatchRecord(record.role, record.offset, record.original, replacement)
        }
        return records.map { record ->
            when (record) {
                frame -> UltraKernelPlan.PatchRecord(record.role, record.offset,
                    record.original, record.replacement.copyOf().apply {
                        bufferAt(this, size - 4).putInt(UltraKernelA64.encodeB(frameBranch.toLong(), relayAt.toLong()))
                    }, record.cave, record.caveBase, record.caveOffset, record.shiftedFrom)
                executor -> UltraKernelPlan.PatchRecord(record.role, record.offset,
                    record.original, record.replacement.copyOf().apply {
                        bufferAt(this, 44).putInt(UltraKernelA64.encodeB(record.offset + 44, legacyBridgeAt.toLong()))
                        bufferAt(this, 68).putInt(UltraKernelA64.encodeB(record.offset + 68, legacyEndAt.toLong()))
                    }, record.cave, record.caveBase, record.caveOffset, record.shiftedFrom)
                else -> record
            }
        } + carrier + (if (storage == null) emptyList() else listOf(UltraKernelPlan.PatchRecord(
            "manager_claim:boot_init", storage.bootInitializerOffset.toLong(),
            kernel.copyOfRange(storage.bootInitializerOffset, storage.bootInitializerOffset + 4),
            buffer(4).putInt(UltraKernelA64.encodeB(storage.bootInitializerOffset.toLong(), bootAt.toLong())).array()))) +
            (if (bootCall == null) emptyList() else listOf(UltraKernelPlan.PatchRecord(
                "manager_claim:boot_start", bootCall.toLong(), kernel.copyOfRange(bootCall, bootCall + 4),
                buffer(4).putInt(UltraKernelA64.encodeBl(bootCall.toLong(), startAt.toLong())).array())))
    }

    private val RESIDENT_EXPORTS = listOf("msu_manager_resident_init", "msu_manager_resident_discard",
        "msu_manager_resident_dispatch")
    private val MANAGEMENT_EXPORTS = setOf("msu_management_owner_invalidated", "msu_credential_replace_current")

    private fun requireBootInstruction(word: Int) {
        // These native first instructions are PC independent. PAC runs after the
        // wrapper restores SP, so its signing context and SCS are unchanged.
        require(word in setOf(UltraKernelA64.NOP, 0xd503233f.toInt(), 0xd503237f.toInt(), 0xd503245f.toInt()) ||
            word and 0xffc003ff.toInt() == 0xd10003ff.toInt() ||
            word and 0xffc07fff.toInt() == 0xa9807bfd.toInt()) { "ULTRA_KERNEL_RESIDENT_BOOT_PROLOGUE" }
    }

    private fun bootInitializer(at: Int, storage: UltraKernelResidentStorage.Candidate, native: Int): ByteArray {
        val a = UltraKernelAsm(at)
        a.emit(UltraKernelA64.stpPre(0, 1, 31, -2))
        a.emit(UltraKernelA64.encodeAdrp(a.here().toLong(), storage.slotOffset.toLong(), 0))
        a.emit(UltraKernelA64.addImm(0, 0, storage.slotOffset and 0xfff))
        for (offset in 0 until UltraKernelResidentStorage.SLOT_BYTES step 16)
            a.emit(UltraKernelA64.pair("stp", 31, 31, 0, offset / 8, true, 2))
        a.emit(UltraKernelA64.encodeMovImm(1, 0x5242534d, sf = 0))
        a.emit(0x889ffc01.toInt()) // stlr w1, [x0]: boot initialization precedes every request.
        a.emit(UltraKernelA64.ldpPost(0, 1, 31, 2))
        a.emit(native)
        a.bAbs(storage.bootInitializerOffset + 4)
        return a.finish()
    }

    private fun bootStart(at: Int, native: Int, start: Int, anchor: Int): ByteArray {
        val a = UltraKernelAsm(at)
        a.emit(UltraKernelA64.stpPre(29, 30, 31, -4))
        a.blAbs(native)
        a.emit(UltraKernelA64.pair("stp", 0, 1, 31, 2, true, 2))
        a.emit(UltraKernelA64.encodeAdrp(a.here().toLong(), anchor.toLong(), 0))
        a.emit(UltraKernelA64.addImm(0, 0, anchor and 0xfff))
        a.blAbs(start)
        a.emit(UltraKernelA64.pair("ldp", 0, 1, 31, 2, true, 2))
        a.emit(UltraKernelA64.ldpPost(29, 30, 31, 4))
        a.emit(UltraKernelA64.RET)
        return a.finish()
    }

    /** The existing frame already owns 80 aligned stack bytes and the original PAC/SCS state. */
    private fun relay(at: Int, bootstrap: Int, anchor: Int, gate: Int): ByteArray {
        val a = UltraKernelAsm(at)
        a.emit(UltraKernelA64.movn(9, 4094))
        a.emit(UltraKernelA64.cmpReg(1, 9))
        a.bCond(UltraKernelA64.CS, "restore")
        a.cbz(1, "restore")
        a.emit(UltraKernelA64.ldAuto("ldr", 1, 1, 0, 8))
        a.emit(UltraKernelA64.encodeAdrp(a.here().toLong(), anchor.toLong(), 0))
        a.emit(UltraKernelA64.addImm(0, 0, anchor and 0xfff))
        a.blAbs(bootstrap)
        a.mark("restore")
        for ((offset, pair) in listOf(0 to (29 to 30), 2 to (6 to 7), 4 to (4 to 5),
            6 to (2 to 3), 8 to (0 to 1))) {
            a.emit(UltraKernelA64.pair("ldp", pair.first, pair.second, 31, offset, true, 2))
        }
        a.bAbs(gate)
        return a.finish()
    }

    private fun cacheUsesAddressLength(signature: String): Boolean = when (signature) {
        "fv(*v;i8:64;)" -> true
        "fv(i8:64;i8:64;)" -> false
        else -> error("ULTRA_KERNEL_RESIDENT_CACHE_ABI")
    }

    private fun cacheAdapter(at: Int, target: Int): ByteArray = buffer(CACHE_ADAPTER_BYTES).apply {
        putInt(0xd503245f.toInt()) // bti c: support reaches this adapter with BLR.
        putInt(UltraKernelA64.addReg(1, 1, 0, sub = true))
        putInt(UltraKernelA64.encodeB((at + 8).toLong(), target.toLong()))
    }.array()

    private const val RELAY_BYTES = 56
    private const val BOOT_START_BYTES = 36
    private const val CACHE_ADAPTER_BYTES = 12

    /** Keep the existing BLR/LR and its metadata locator unchanged. The extra
     * frame belongs to this authenticated command-3 invocation and survives a
     * sleeping JIT. Its copied first 32 bytes preserve the executor's own ABI. */
    private fun legacyScopeBridge(at: Int, get: Int, put: Int, publish: Int, cleanup: Int): Pair<ByteArray, ByteArray> {
        val a = UltraKernelAsm(at)
        a.mark("gateway")
        a.emit(UltraKernelA64.addImm(31, 31, 64, sub = true))
        for (offset in intArrayOf(0, 16)) {
            a.emit(UltraKernelA64.pair("ldp", 11, 17, 31, (64 + offset) / 8, true, 2))
            a.emit(UltraKernelA64.pair("stp", 11, 17, 31, offset / 8, true, 2))
        }
        for (offset in intArrayOf(32, 48))
            a.emit(UltraKernelA64.pair("stp", 31, 31, 31, offset / 8, true, 2))
        a.emit(UltraKernelA64.addImm(0, 31, 32))
        a.emit(UltraKernelA64.addImm(1, 31, 40))
        a.blAbs(get)
        a.cbnz(0, "ready", sf = false)
        a.emit(UltraKernelA64.ldAuto("ldr", 9, 31, 32, 8))
        a.cbz(9, "put")
        a.emit(UltraKernelA64.ldAuto("ldr", 10, 9, 0, 8))
        a.emit(UltraKernelA64.encodeImm64(12, 0x314b484c55534dL))
        a.emit(UltraKernelA64.cmpReg(10, 12))
        a.bCond(UltraKernelA64.NE, "put")
        a.emit(UltraKernelA64.ldAuto("ldr", 10, 9, 8, 8))
        a.emit(UltraKernelA64.encodeImm64(12, (72L shl 32) or 1))
        a.emit(UltraKernelA64.cmpReg(10, 12))
        a.bCond(UltraKernelA64.NE, "put")
        a.adr(0, "gateway")
        a.adr(1, "gateway-end")
        a.emit(UltraKernelA64.addReg(1, 1, 0, sub = true))
        a.emit(UltraKernelA64.ldAuto("ldr", 10, 9, 40, 8))
        a.blr(10)
        a.cbz(0, "put")
        a.emit(UltraKernelA64.ldAuto("ldr", 9, 31, 32, 8))
        a.emit(UltraKernelA64.ldAuto("ldr", 10, 9, 48, 8))
        a.blr(10)
        a.emit(UltraKernelA64.ldAuto("str", 0, 31, 48, 8))
        a.cbnz(0, "ready")
        a.mark("put")
        a.emit(UltraKernelA64.ldAuto("ldr", 0, 31, 40, 8))
        a.blAbs(put)
        a.emit(UltraKernelA64.pair("stp", 31, 31, 31, 4, true, 2))
        a.mark("ready")
        a.emit(UltraKernelA64.pair("ldp", 11, 17, 31, 2, true, 2))
        a.bAbs(publish)
        a.mark("gateway-end")
        val begin = a.finish()
        val end = UltraKernelAsm(at + begin.size)
        end.emit(UltraKernelA64.ldAuto("ldr", 0, 31, 48, 8))
        end.cbz(0, "out")
        end.emit(UltraKernelA64.ldAuto("ldr", 9, 31, 32, 8))
        end.emit(UltraKernelA64.ldAuto("ldr", 10, 9, 56, 8))
        end.blr(10)
        end.emit(UltraKernelA64.ldAuto("str", 0, 31, 56, 4))
        end.emit(UltraKernelA64.ldAuto("ldr", 0, 31, 40, 8))
        end.blAbs(put)
        end.emit(UltraKernelA64.ldAuto("ldr", 9, 31, 56, 4))
        end.cbz(9, "out", sf = false)
        end.emit(UltraKernelA64.ldAuto("ldr", 17, 31, 24, 8))
        end.emit(UltraKernelA64.movn(0, 19998))
        end.emit(UltraKernelA64.ldAuto("str", 0, 17, 0, 8))
        end.mark("out")
        end.emit(UltraKernelA64.addImm(31, 31, 64))
        end.bAbs(cleanup)
        return begin to end.finish()
    }

    /**
     * All addresses are offsets in [kernel]. [bootPolicy] is the 96-byte carrier anchor;
     * bytes 56..79 hold the binding generated here. [bootstrap] includes any caller relay space.
     */
    fun plan(
        kernel: ByteArray,
        symbols: UltraKernelSymbols,
        occupiedRecords: List<UltraKernelPlan.PatchRecord>,
        payloadElf: ByteArray,
        bootstrap: ByteArray,
        materializer: ByteArray,
        bootPolicy: ByteArray = ByteArray(96),
        permissions: ByteArray? = null,
        continuation: ByteArray? = null,
        deploymentRecord: ByteArray? = null,
        runtimeLayout: UltraKernelManagerClaimPayload.RuntimeLayoutFacts? = null,
        runtimeProfiles: UltraKernelManagerClaimPayload.RuntimeProfilesFacts? = null,
        profileSymbols: UltraKernelSymbols = symbols,
        sessionAbi: UltraKernelManagerClaimPayload.SessionAbiFacts? = null,
        dataSignatures: Map<String, String> = emptyMap(),
        bootEventsAbi: UltraKernelManagerClaimPayload.BootEventsAbiFacts? = null,
        ksuCompatAbi: UltraKernelManagerClaimPayload.KsuCompatAbiFacts? = null,
    ): Plan {
        require(symbols.imageSize == kernel.size) { "ULTRA_KERNEL_MANAGER_CLAIM_IMAGE_SIZE" }
        require(bootstrap.isNotEmpty() && bootstrap.size % 4 == 0 &&
            materializer.isNotEmpty() && materializer.size % 4 == 0 &&
            (permissions == null || permissions.isNotEmpty() && permissions.size % 4 == 0) &&
            (continuation == null || continuation.isNotEmpty() && continuation.size % 4 == 0) &&
            bootPolicy.size == 96) { "ULTRA_KERNEL_MANAGER_CLAIM_CODE_SIZE" }
        deploymentRecord?.let(UltraKernelDeploymentNamespace::decode)
        val payload = bindRuntimeProfiles(bindRuntimeLayout(parseElf(payloadElf, symbols, kernel, dataSignatures),
            runtimeLayout, kernel, symbols), runtimeProfiles, kernel, profileSymbols, occupiedRecords)
        verifySessionAbi(payload, sessionAbi, kernel, symbols)
        verifyBootEventsAbi(payload, bootEventsAbi, kernel, symbols)
        verifyKsuCompatAbi(payload, ksuCompatAbi, kernel, symbols)
        val profileSpans = runtimeProfiles?.let { facts ->
            facts.names.mapIndexedNotNull { index, compiledName ->
                val name = if (index == 0 && profileSymbols.offsetOf(compiledName) == null) "__do_execve_file" else compiledName
                profileSymbols.offsetOf(name)?.let { offset ->
                    Span(offset - if (index > 0 && facts.words[5] == 1) 4 else 0, offset + 24)
                }
            }
        }.orEmpty()
        val caves = padding(kernel, symbols, occupiedRecords, profileSpans)
        val available = caves.sumOf { it.end - it.start }
        val descriptorBytes = (52L + payload.sections.size * 16L + payload.imports.size * 4L +
            payload.relocations.size * 16L).also {
            require(it <= kernel.size && it <= Int.MAX_VALUE) { "ULTRA_KERNEL_MANAGER_CLAIM_DESCRIPTOR_SIZE" }
        }.toInt()
        var tableBudget = payload.sections.count { it.file.isNotEmpty() } * 8
        repeat(16) {
            val free = caves.map { intArrayOf(it.start, it.end) }
            fun allocate(size: Int, split: Boolean = false): Pair<Int, Int> {
                val need = align(size, 4)
                val fitting = free.indices.filter { free[it][1] - free[it][0] >= need }
                val index = (fitting.minWithOrNull(compareBy<Int> { free[it][1] - free[it][0] }.thenBy { it })
                    ?: if (split) free.indices.filter { free[it][1] > free[it][0] }
                        .maxWithOrNull(compareBy<Int> { free[it][1] - free[it][0] }.thenBy { it }) else null)
                    ?: throw UltraKernelGenerateException(UltraKernelGenerateCodes.CAVE_UNAVAILABLE,
                        "MANAGER_CLAIM:need=$size:largest=${free.maxOfOrNull { it[1] - it[0] } ?: 0}")
                val reserved = minOf(need, free[index][1] - free[index][0])
                val at = free[index][0]
                free[index][0] += reserved
                return at to minOf(size, reserved)
            }
            // Reserve both contiguous code blocks before fragmenting the remaining text.
            val bootstrapAt: Int
            val materializerAt: Int
            if (materializer.size >= bootstrap.size) {
                materializerAt = allocate(materializer.size).first
                bootstrapAt = allocate(bootstrap.size).first
            } else {
                bootstrapAt = allocate(bootstrap.size).first
                materializerAt = allocate(materializer.size).first
            }
            val permissionsAt = permissions?.let { allocate(it.size).first }
            val continuationAt = continuation?.let { allocate(it.size).first }
            val descriptorAt = allocate(descriptorBytes).first
            val anchorAt = allocate(bootPolicy.size).first
            val deploymentAt = deploymentRecord?.let { allocate(it.size).first }
            val bindingAt = anchorAt + 56
            val tableAt = allocate(tableBudget).first
            val chunks = ArrayList<Chunk>()
            val records = ArrayList<UltraKernelPlan.PatchRecord>()
            fun record(role: String, at: Int, bytes: ByteArray) {
                records += UltraKernelPlan.PatchRecord("manager_claim:$role", at.toLong(),
                    kernel.copyOfRange(at, at + bytes.size), bytes.copyOf())
            }
            for (section in payload.sections) {
                var cursor = 0
                while (cursor < section.file.size) {
                    val (at, count) = allocate(section.file.size - cursor, split = true)
                    val bytes = section.file.copyOfRange(cursor, cursor + count)
                    chunks += Chunk(at, bytes)
                    record("payload:${section.name}:${chunks.lastIndex}", at, bytes)
                    cursor += count
                }
            }
            if (chunks.size > 256) throw UltraKernelGenerateException(UltraKernelGenerateCodes.CAVE_UNAVAILABLE,
                "MANAGER_CLAIM:chunks=${chunks.size}")
            if (chunks.size * 8 > tableBudget) {
                tableBudget = chunks.size * 8
                return@repeat
            }
            val payloadCrc = CRC32().apply { payload.sections.forEach { update(it.file) } }.value
            val descriptor = buffer(descriptorBytes).apply {
                for (value in intArrayOf(0x4355534d, 3, descriptorBytes, kernel.size, payload.memory,
                    payload.entry, payload.sections.size, chunks.size, payload.imports.size,
                    payload.relocations.size, payloadCrc.toInt(), payload.kernelMemoryBytes, tableAt)) putInt(value)
                for (section in payload.sections) {
                    putInt(section.offset); putInt(section.file.size); putInt(section.memory); putInt(section.flags)
                }
                payload.imports.forEach(::putInt)
                for (relocation in payload.relocations) {
                    putInt(relocation.target); putInt(relocation.kind); putInt(relocation.value); putInt(0)
                }
            }.array()
            val table = buffer(chunks.size * 8).apply {
                chunks.forEach { putInt(it.source); putInt(it.bytes.size) }
            }.array()
            val crc = CRC32().apply { update(descriptor); update(table) }.value
            val binding = buffer(24).apply {
                for (value in intArrayOf(kernel.size, descriptorAt, descriptorBytes, crc.toInt(),
                    materializerAt, materializer.size)) putInt(value)
            }.array()
            record("bootstrap", bootstrapAt, bootstrap)
            record("materializer", materializerAt, materializer)
            if (permissions != null) record("permissions", permissionsAt!!, permissions)
            if (continuation != null) record("continuation", continuationAt!!, continuation)
            val anchor = bootPolicy.copyOf()
            binding.copyInto(anchor, 56)
            record("anchor", anchorAt, anchor)
            if (deploymentRecord != null) record("deployment_namespace", requireNotNull(deploymentAt), deploymentRecord)
            record("descriptor", descriptorAt, descriptor)
            record("chunk_table", tableAt, table)
            val sorted = records.sortedBy { it.offset }
            for ((index, item) in sorted.withIndex()) {
                check(caves.any { it.start <= item.offset && item.offset + item.length <= it.end }) {
                    "ULTRA_KERNEL_MANAGER_CLAIM_OUTSIDE_PADDING"
                }
                check(index == 0 || sorted[index - 1].offset + sorted[index - 1].length <= item.offset) {
                    "ULTRA_KERNEL_MANAGER_CLAIM_OVERLAP"
                }
            }
            return Plan(records, bootstrapAt, materializerAt, anchorAt, bindingAt, descriptorAt,
                descriptorBytes, crc, tableAt, chunks.size, payload.entry, payload.memory,
                available, free.sumOf { it[1] - it[0] }, permissionsAt, continuationAt, deploymentAt)
        }
        error("ULTRA_KERNEL_MANAGER_CLAIM_TABLE_DID_NOT_CONVERGE")
    }

    private fun padding(kernel: ByteArray, symbols: UltraKernelSymbols,
                        records: List<UltraKernelPlan.PatchRecord>, reserved: List<Span> = emptyList()): List<Span> {
        val start = symbols.offsetOf("_stext") ?: symbols.offsetOf("_text")
            ?: error("ULTRA_KERNEL_MANAGER_CLAIM_TEXT_MISSING")
        val end = symbols.offsetOf("_etext") ?: error("ULTRA_KERNEL_MANAGER_CLAIM_TEXT_MISSING")
        require(start in 0 until end && end <= kernel.size) { "ULTRA_KERNEL_MANAGER_CLAIM_TEXT_RANGE" }
        val blocked = UltraKernelCaveAllocator.runtimePatchSites(symbols, kernel)
            .mapTo(ArrayList()) { Span(it.start, it.end) }
        blocked += reserved
        for ((left, right) in listOf("__init_begin" to "__init_end", "_sinittext" to "_einittext",
            "__inittext_begin" to "__inittext_end")) {
            val a = symbols.offsetOf(left)
            val b = symbols.offsetOf(right)
            if (a != null && b != null && b > a) blocked += Span(a, b)
        }
        val groups = HashMap<Int, Int>()
        for (record in records) {
            require(record.offset <= kernel.size && record.length <= kernel.size - record.offset) {
                "ULTRA_KERNEL_MANAGER_CLAIM_RECORD_RANGE"
            }
            val at = record.offset.toInt()
            blocked += Span(at, at + record.length)
            record.caveBase?.let { base ->
                require(base in 0..at.toLong()) { "ULTRA_KERNEL_MANAGER_CLAIM_CAVE_RANGE" }
                groups[base.toInt()] = maxOf(groups[base.toInt()] ?: 0, at + record.length)
            }
            record.shiftedFrom?.let { from ->
                require(from in 0 until kernel.size.toLong()) { "ULTRA_KERNEL_MANAGER_CLAIM_ORIGINAL_CAVE_RANGE" }
                blocked += Span(from.toInt(), symbols.nextOffset(from.toInt()) ?: kernel.size)
            }
        }
        groups.forEach { (a, b) -> blocked += Span(a, b) }
        val clipped = blocked.mapNotNull {
            val a = maxOf(start, it.start and -4)
            val b = minOf(end, align(it.end, 4))
            if (a < b) Span(a, b) else null
        }.sortedBy { it.start }
        val merged = ArrayList<Span>()
        for (span in clipped) {
            if (merged.isNotEmpty() && span.start <= merged.last().end) {
                merged[merged.lastIndex] = Span(merged.last().start, maxOf(span.end, merged.last().end))
            } else merged += span
        }
        val caves = ArrayList<Span>()
        var blockedIndex = 0
        var at = align(start, 4)
        var run = -1
        fun finish() {
            if (run >= 0 && run < at) caves += Span(run, at)
            run = -1
        }
        while (at <= end - 4) {
            while (blockedIndex < merged.size && merged[blockedIndex].end <= at) blockedIndex++
            val exclusion = merged.getOrNull(blockedIndex)
            if (exclusion != null && exclusion.start < at + 4) {
                finish()
                at = align(exclusion.end, 4)
                continue
            }
            val word = UltraKernelA64.le32Bits(kernel, at)
            if (symbols.isSymbolStart(at) || (word != 0 && word != UltraKernelA64.NOP)) {
                finish()
                at += 4
                continue
            }
            if (run < 0) {
                val previous = symbols.nameBefore(at).orEmpty()
                if (listOf("__init", "_init", "__exception", "__entry", "__meminit").any(previous::startsWith)) {
                    at = align(symbols.nextOffset(at) ?: end, 4)
                    continue
                }
                run = at
            }
            at += 4
        }
        finish()
        return caves
    }

    private fun parseElf(bytes: ByteArray, symbols: UltraKernelSymbols, kernel: ByteArray,
        dataSignatures: Map<String, String>): Payload {
        val imageSize = kernel.size
        var kernelMemoryBytes = imageSize
        val dataSizes by lazy {
            UltraKernelBtf.dataObjectSizes(kernel, symbols, dataSignatures)
                ?: error("ULTRA_KERNEL_MANAGEMENT_DATA_BTF_REQUIRED")
        }
        val bss by lazy {
            fun boundary(name: String) = symbols.memoryOffsetsOf(name).singleOrNull()
                ?: error("ULTRA_KERNEL_MANAGER_CLAIM_MEMORY_BOUNDARY:$name")
            require(kernel.size >= 64 && UltraKernelA64.le32Bits(kernel, 56) == 0x644d5241 &&
                UltraKernelA64.le32Bits(kernel, 24) and 1 == 0) { "ULTRA_KERNEL_MANAGER_CLAIM_MEMORY_HEADER" }
            val memory = ByteBuffer.wrap(kernel).order(ByteOrder.LITTLE_ENDIAN).getLong(16)
            val start = boundary("__bss_start")
            val stop = boundary("__bss_stop")
            require(memory in imageSize.toLong()..Int.MAX_VALUE.toLong() && boundary("_text") == 0 &&
                boundary("_end").toLong() == memory && start >= 0 && start < stop && stop <= memory) {
                "ULTRA_KERNEL_MANAGER_CLAIM_MEMORY_RANGE"
            }
            kernelMemoryBytes = memory.toInt()
            start to stop
        }
        val elf = Elf(bytes)
        require(bytes.size >= 64 && bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() &&
            bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[4] == 2.toByte() && bytes[5] == 1.toByte() && bytes[6] == 1.toByte() &&
            elf.half(16) == 3 && elf.half(18) == 183 && elf.word(20) == 1 &&
            elf.half(52) == 64) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_HEADER" }
        val sectionAt = elf.number(40, bytes.size)
        val sectionCount = elf.half(60)
        require(elf.half(58) == 64 && sectionCount > 0 && sectionCount <= (bytes.size - sectionAt) / 64) {
            "ULTRA_KERNEL_MANAGER_CLAIM_ELF_SECTIONS"
        }
        data class Raw(val name: Int, val kind: Int, val flags: Long, val address: Long,
                       val fileAt: Long, val size: Long, val link: Int, val stride: Long)
        val raw = List(sectionCount) { index ->
            val at = sectionAt + index * 64
            Raw(elf.word(at), elf.word(at + 4), elf.wide(at + 8), elf.wide(at + 16),
                elf.wide(at + 24), elf.wide(at + 32), elf.word(at + 40), elf.wide(at + 56))
        }
        val names = raw.getOrNull(elf.half(62)) ?: error("ULTRA_KERNEL_MANAGER_CLAIM_ELF_NAMES")
        val nameBytes = elf.slice(names.fileAt, names.size)
        // Dynamic tables are consumed below; only runtime bytes belong in the retained carrier.
        val sections = raw.filter { it.flags and 2L != 0L && it.size != 0L &&
            it.kind in setOf(1, 8) && elf.string(nameBytes, it.name) !in setOf(".eh_frame", ".eh_frame_hdr", ".msu.layout",
                ".msu.procattr.layout", ".msu.task_state.layout", ".msu.credential_domain.layout", ".msu.task_profile.layout",
                ".msu.runtime.alignment", ".msu.su-runtime.alignment", ".msu.su-runtime.exec-layout", ".msu.task_profile.trace-layout")
        }.map { item ->
            require(item.address in 0..1048576L && item.size > 0 && item.size <= 1048576L - item.address) {
                "ULTRA_KERNEL_MANAGER_CLAIM_ELF_MEMORY"
            }
            val flags = 1 or (if (item.flags and 1L != 0L) 2 else 0) or
                (if (item.flags and 4L != 0L) 4 else 0)
            require(flags and 6 != 6) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_RWX" }
            Section(elf.string(nameBytes, item.name), item.address.toInt(),
                if (item.kind == 8) ByteArray(0) else elf.slice(item.fileAt, item.size), item.size.toInt(), flags)
        }.sortedBy { it.offset }
        require(sections.isNotEmpty() && sections.size <= 32) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_SECTION_COUNT" }
        for (index in 1 until sections.size) require(
            sections[index - 1].offset + sections[index - 1].memory <= sections[index].offset) {
            "ULTRA_KERNEL_MANAGER_CLAIM_ELF_SECTION_OVERLAP"
        }
        fun inside(at: Int, length: Int, permission: Int) = sections.any {
            it.flags and permission == permission && at >= it.offset &&
                at.toLong() + length <= it.offset.toLong() + it.memory
        }
        val entry = elf.number(24, 1048576)
        require(entry % 4 == 0 && sections.any {
            it.flags and 4 != 0 && entry >= it.offset && entry.toLong() + 4 <= it.offset.toLong() + it.file.size
        }) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_ENTRY" }
        val importNames = LinkedHashMap<String, Int>()
        val exports = LinkedHashMap<String, Int>()
        val layouts = LinkedHashMap<String, IntArray>()
        var runtimeLayout: RuntimeLayout? = null
        var runtimeProfiles: RuntimeProfiles? = null
        for (table in raw.filter { it.kind == 11 }) {
            require(table.stride == 24L && table.size % 24L == 0L) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_SYMBOL_TABLE" }
            val tableBytes = elf.slice(table.fileAt, table.size)
            val sym = Elf(tableBytes)
            val stringsTable = raw.getOrNull(table.link) ?: error("ULTRA_KERNEL_MANAGER_CLAIM_ELF_STRING_TABLE")
            val strings = elf.slice(stringsTable.fileAt, stringsTable.size)
            for (at in tableBytes.indices step 24) {
                if (sym.half(at + 6) == 0) continue
                val name = elf.string(strings, sym.word(at))
                if (name !in RESIDENT_EXPORTS && name !in MANAGEMENT_EXPORTS &&
                    name !in UltraKernelManagerClaimPayload.taskProfileFunctions && name !in setOf("msu_resolver_target_layout",
                    "msu_manager_owner_watch_compiled_layout", "msu_manager_owner_compiled_layout",
                    "msu_management_compiled_layout", "msu_procattr_compiled_layout", "msu_task_state_compiled_layout",
                    "msu_credential_domain_compiled_layout", "msu_task_profile_compiled_layout", "msu_runtime_layout",
                    "msu_su_runtime_profiles", "msu_su_session_abi_words", "msu_boot_events_abi_words", "msu_ksu_compat_layout")) continue
                val address = sym.number(at + 8, 1048576)
                require(exports.put(name, address) == null) { "ULTRA_KERNEL_RESIDENT_EXPORT_AMBIGUOUS:$name" }
                if (name == "msu_su_runtime_profiles") {
                    val section = raw.getOrNull(sym.half(at + 6))
                    val size = sym.number(at + 16, 65536)
                    require(runtimeProfiles == null && (tableBytes[at + 4].toInt() and 15) == 1 &&
                        section != null && section.kind == 1 && section.flags == 3L &&
                        elf.string(nameBytes, section.name) == ".msu.su-runtime.profiles" && address % 4 == 0 &&
                        address == section.address.toInt() && size == 372 && size.toLong() == section.size &&
                        inside(address, size, 2)) { "ULTRA_KERNEL_SU_RUNTIME_PROFILES_SYMBOL" }
                    runtimeProfiles = RuntimeProfiles(address, size)
                } else if (name == "msu_runtime_layout") {
                    val section = raw.getOrNull(sym.half(at + 6))
                    val size = sym.number(at + 16, 65536)
                    require(runtimeLayout == null && (tableBytes[at + 4].toInt() and 15) == 1 &&
                        section != null && section.kind == 1 && section.flags == 3L &&
                        elf.string(nameBytes, section.name) == ".msu.runtime.layout" && address % 4 == 0 &&
                        address == section.address.toInt() && size >= 24 && size.toLong() == section.size &&
                        inside(address, size, 2)) { "ULTRA_KERNEL_RUNTIME_LAYOUT_SYMBOL" }
                    runtimeLayout = RuntimeLayout(address, size)
                } else if (name in RESIDENT_EXPORTS || name in MANAGEMENT_EXPORTS || name in UltraKernelManagerClaimPayload.taskProfileFunctions) {
                    require(inside(address, 4, 4)) { "ULTRA_KERNEL_RESIDENT_EXPORT_EXEC:$name" }
                    if (name in UltraKernelManagerClaimPayload.taskProfileFunctions) {
                        val section = raw.getOrNull(sym.half(at + 6))
                        val size = sym.number(at + 16, 1048576)
                        require((tableBytes[at + 4].toInt() and 15) == 2 && size >= 4 && section != null &&
                            section.kind == 1 && section.flags and 7L == 6L && address >= section.address &&
                            address.toLong() + size <= section.address + section.size) { "ULTRA_KERNEL_TASK_PROFILE_FUNCTION:$name" }
                    }
                } else {
                    val size = sym.number(at + 16, 4096)
                    // Compiled reports are consumed here, not copied into the running host.
                    val section = raw.singleOrNull { it.kind == 1 && it.flags and 7L == 2L && address >= it.address &&
                        address.toLong() + size <= it.address + it.size }
                        ?: error("ULTRA_KERNEL_RESIDENT_LAYOUT_RANGE:$name")
                    require(size > 0 && size % 4 == 0) { "ULTRA_KERNEL_RESIDENT_LAYOUT_SIZE:$name" }
                    if (name in setOf("msu_procattr_compiled_layout", "msu_task_state_compiled_layout",
                            "msu_credential_domain_compiled_layout", "msu_task_profile_compiled_layout", "msu_su_session_abi_words",
                            "msu_boot_events_abi_words", "msu_ksu_compat_layout")) {
                        val expected = when (name) {
                            "msu_procattr_compiled_layout" -> 160 to ".msu.procattr.layout"
                            "msu_task_state_compiled_layout" -> 212 to ".msu.task_state.layout"
                            "msu_task_profile_compiled_layout" -> 104 to ".msu.task_profile.layout"
                            "msu_su_session_abi_words" -> 96 to ".msu.session.abi"
                            "msu_boot_events_abi_words" -> 152 to ".msu.boot_events.abi"
                            "msu_ksu_compat_layout" -> 56 to ".msu.ksu-compat.layout"
                            else -> 104 to ".msu.credential_domain.layout"
                        }
                        require((tableBytes[at + 4].toInt() and 15) == 1 && address % 4 == 0 &&
                            raw.getOrNull(sym.half(at + 6)) == section && size == expected.first &&
                            elf.string(nameBytes, section.name) == expected.second) {
                            "ULTRA_KERNEL_CORE_LAYOUT_SYMBOL:$name"
                        }
                    }
                    val data = elf.slice(section.fileAt + address - section.address, size.toLong())
                    layouts[name] = IntArray(size / 4) { UltraKernelA64.le32Bits(data, it * 4) }
                }
            }
        }
        var residentHost: ResidentHost? = null
        for (table in raw.filter { it.kind == 2 }) {
            require(table.stride == 24L && table.size % 24L == 0L) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_SYMBOL_TABLE" }
            val tableBytes = elf.slice(table.fileAt, table.size)
            val sym = Elf(tableBytes)
            val stringsTable = raw.getOrNull(table.link) ?: error("ULTRA_KERNEL_MANAGER_CLAIM_ELF_STRING_TABLE")
            val strings = elf.slice(stringsTable.fileAt, stringsTable.size)
            for (at in tableBytes.indices step 24) {
                if (sym.half(at + 6) == 0) continue
                val name = elf.string(strings, sym.word(at))
                val implementationNames = setOf("msu_task_profile_trace_bind", "msu_task_profile_trace_open",
                    "msu_task_profile_trace_close", "msu_task_profile_trace_ready", "msu_task_profile_sys_enter",
                    "msu_task_profile_marker_entry", "msu_task_profile_marker_return")
                if (name in implementationNames) {
                    val address = sym.number(at + 8, 1048576)
                    val size = sym.number(at + 16, 1048576)
                    val section = raw.getOrNull(sym.half(at + 6))
                    require(exports.put(name, address) == null && (tableBytes[at + 4].toInt() and 15) == 2 &&
                        section != null && section.kind == 1 && section.flags and 7L == 6L &&
                        size >= 4 && address % 4 == 0 && address >= section.address &&
                        address.toLong() + size <= section.address + section.size && inside(address, size, 4)) {
                        "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_ELF_FUNCTION:$name"
                    }
                    continue
                }
                val implementationTables = mapOf(
                    "msu_task_profile_target" to Triple(".msu.task_profile.target", 136, 3L),
                    "msu_task_profile_marker_target" to Triple(".msu.task_profile.marker-target", 112, 3L),
                    "msu_task_profile_trace_compiled_layout" to Triple(".msu.task_profile.trace-layout", 48, 2L))
                if (name in implementationTables) {
                    val expected = implementationTables.getValue(name)
                    val address = sym.number(at + 8, 1048576)
                    val size = sym.number(at + 16, 4096)
                    val section = raw.getOrNull(sym.half(at + 6))
                    require(name !in layouts && (tableBytes[at + 4].toInt() and 15) == 1 && section != null &&
                        section.kind == 1 && section.flags and 7L == expected.third &&
                        elf.string(nameBytes, section.name) == expected.first && address % 4 == 0 &&
                        address == section.address.toInt() && size == expected.second && size.toLong() == section.size &&
                        raw.count { elf.string(nameBytes, it.name) == expected.first } == 1) {
                        "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_ELF_TABLE:$name"
                    }
                    val data = elf.slice(section.fileAt, section.size)
                    layouts[name] = IntArray(size / 4) { UltraKernelA64.le32Bits(data, it * 4) }
                    continue
                }
                if (name == "msu_su_runtime_target_facts") {
                    val address = sym.number(at + 8, 1048576)
                    val size = sym.number(at + 16, 4096)
                    val section = raw.getOrNull(sym.half(at + 6))
                    require(name !in layouts && (tableBytes[at + 4].toInt() and 15) == 1 && section != null &&
                        section.kind == 1 && section.flags == 3L &&
                        elf.string(nameBytes, section.name) == ".msu.su-runtime.target" &&
                        address % 4 == 0 && address == section.address.toInt() && size == 36 &&
                        size.toLong() == section.size && inside(address, size, 2)) { "ULTRA_KERNEL_SU_RUNTIME_TARGET_SYMBOL" }
                    val data = elf.slice(section.fileAt, section.size)
                    layouts[name] = IntArray(9) { UltraKernelA64.le32Bits(data, it * 4) }
                    continue
                }
                if (name != "host" || "msu_management_compiled_layout" !in layouts) continue
                require(residentHost == null && (tableBytes[at + 4].toInt() and 255) == 1) {
                    "ULTRA_KERNEL_MANAGEMENT_HOST_AMBIGUOUS"
                }
                val address = sym.number(at + 8, 1048576)
                val size = sym.number(at + 16, 1048576)
                val section = raw.getOrNull(sym.half(at + 6)) ?: error("ULTRA_KERNEL_MANAGEMENT_HOST_SECTION")
                require(size > 0 && section.kind == 8 && section.flags and 7L == 3L &&
                    section.address in 0..1048576L && section.size in 1..1048576L &&
                    address >= section.address && address.toLong() + size <= section.address + section.size &&
                    inside(address, size, 2)) { "ULTRA_KERNEL_MANAGEMENT_HOST_RANGE" }
                residentHost = ResidentHost(address, size, elf.string(nameBytes, section.name),
                    section.address.toInt(), section.size.toInt(), section.flags)
            }
        }
        val imports = ArrayList<Int>()
        val relocations = ArrayList<Relocation>()
        val targets = HashSet<Int>()
        for (table in raw) {
            require(table.kind != 9 && table.kind != 19) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_RELOCATION_FORMAT" }
            if (table.kind != 4 || table.size == 0L) continue
            require(table.stride == 24L && table.size % 24L == 0L) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_RELOCATION_SIZE" }
            val tableBytes = elf.slice(table.fileAt, table.size)
            val symbolsTable = raw.getOrNull(table.link) ?: error("ULTRA_KERNEL_MANAGER_CLAIM_ELF_SYMBOL_TABLE")
            require(symbolsTable.kind in setOf(2, 11) && symbolsTable.stride == 24L &&
                symbolsTable.size % 24L == 0L) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_SYMBOL_TABLE" }
            val symbolBytes = elf.slice(symbolsTable.fileAt, symbolsTable.size)
            val stringsTable = raw.getOrNull(symbolsTable.link) ?: error("ULTRA_KERNEL_MANAGER_CLAIM_ELF_STRING_TABLE")
            val stringBytes = elf.slice(stringsTable.fileAt, stringsTable.size)
            val rel = Elf(tableBytes)
            val sym = Elf(symbolBytes)
            for (at in tableBytes.indices step 24) {
                val target = rel.number(at, 1048576)
                val information = rel.wide(at + 8)
                val kind = information.toInt()
                val addend = rel.wide(at + 16)
                require(target % 8 == 0 && inside(target, 8, 2) && targets.add(target)) {
                    "ULTRA_KERNEL_MANAGER_CLAIM_ELF_RELOCATION_TARGET"
                }
                if (kind == 1027) {
                    require(information ushr 32 == 0L && addend in 0..1048576L && inside(addend.toInt(), 1, 1)) {
                        "ULTRA_KERNEL_MANAGER_CLAIM_ELF_RELATIVE"
                    }
                    relocations += Relocation(target, 1, addend.toInt())
                } else {
                    require(kind in setOf(257, 1025, 1026) && addend == 0L) {
                        "ULTRA_KERNEL_MANAGER_CLAIM_ELF_RELOCATION_KIND"
                    }
                    val index = information ushr 32
                    require(index < symbolBytes.size / 24) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_SYMBOL_INDEX" }
                    val symbolAt = index.toInt() * 24
                    require(sym.half(symbolAt + 6) == 0) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_LOCAL_IMPORT" }
                    val name = elf.string(stringBytes, sym.word(symbolAt))
                    require(name.isNotEmpty()) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_IMPORT_NAME" }
                    val type = symbolBytes[symbolAt + 4].toInt() and 15
                    val data = name in dataSignatures
                    // Undefined ELF imports may be NOTYPE; the reference and target BTF
                    // supply the object identity and complete size, never the symbol name alone.
                    require(type == 0 || type == if (data) 1 else 2) {
                        "ULTRA_KERNEL_MANAGER_CLAIM_IMPORT_TYPE:$name"
                    }
                    val slot = importNames.getOrPut(name) {
                        val offset = if (data) symbols.memoryOffsetsOf(name).singleOrNull()
                            else symbols.offsetOf(name)
                        if (offset == null) error("ULTRA_KERNEL_MANAGER_CLAIM_IMPORT_MISSING:$name")
                        val size = if (data) dataSizes.getValue(name) else 4L
                        require(offset >= 0 && offset % 4 == 0 && size in 1..Int.MAX_VALUE.toLong() &&
                            (offset.toLong() + size <= imageSize || data && bss.let { (start, stop) ->
                                offset >= start && offset.toLong() + size <= stop
                            })) {
                            "ULTRA_KERNEL_MANAGER_CLAIM_IMPORT_RANGE:$name"
                        }
                        imports += offset
                        imports.lastIndex
                    }
                    relocations += Relocation(target, 2, slot)
                }
            }
        }
        val memoryBytes = align(sections.last().offset + sections.last().memory, 16)
        // Match the native materializer: every import is used and each relocation
        // owns a distinct aligned 8-byte writable destination, with no fixed array.
        require(imports.size <= relocations.size && relocations.size <= memoryBytes / 8) {
            "ULTRA_KERNEL_MANAGER_CLAIM_ELF_RELOCATION_COUNT"
        }
        val profileSections = raw.filter { elf.string(nameBytes, it.name) == ".msu.task_profile.layout" }
        require(if ("msu_task_profile_compiled_layout" in layouts)
            profileSections.size == 1 && profileSections.single().size == 104L else profileSections.isEmpty()) {
            "ULTRA_KERNEL_TASK_PROFILE_SECTION"
        }
        val runtimeSections = raw.filter { elf.string(nameBytes, it.name) == ".msu.runtime.layout" }
        require(if (runtimeLayout != null) runtimeSections.size == 1 else runtimeSections.isEmpty()) {
            "ULTRA_KERNEL_RUNTIME_LAYOUT_SECTION"
        }
        runtimeLayout?.let { layout ->
            require(relocations.none { it.target.toLong() < layout.offset.toLong() + layout.bytes &&
                it.target.toLong() + 8 > layout.offset }) { "ULTRA_KERNEL_RUNTIME_LAYOUT_RELOCATION" }
        }
        val suProfileSections = raw.filter { elf.string(nameBytes, it.name) == ".msu.su-runtime.profiles" }
        require(if (runtimeProfiles != null) suProfileSections.size == 1 else suProfileSections.isEmpty()) {
            "ULTRA_KERNEL_SU_RUNTIME_PROFILES_SECTION"
        }
        runtimeProfiles?.let { profiles ->
            require(relocations.none { it.target.toLong() < profiles.offset.toLong() + profiles.bytes &&
                it.target.toLong() + 8 > profiles.offset }) { "ULTRA_KERNEL_SU_RUNTIME_PROFILES_RELOCATION" }
        }
        val sessionSections = raw.filter { elf.string(nameBytes, it.name) == ".msu.session.abi" }
        require(if ("msu_su_session_abi_words" in layouts) sessionSections.size == 1 else sessionSections.isEmpty()) {
            "ULTRA_KERNEL_SESSION_ABI_SECTION"
        }
        sessionSections.singleOrNull()?.let { section ->
            require(section.kind == 1 && section.flags == 2L && section.size == 96L &&
                exports["msu_su_session_abi_words"]?.toLong() == section.address &&
                relocations.none { it.target.toLong() < section.address + section.size && it.target.toLong() + 8 > section.address }) {
                "ULTRA_KERNEL_SESSION_ABI_READONLY"
            }
        }
        val bootSections = raw.filter { elf.string(nameBytes, it.name) == ".msu.boot_events.abi" }
        require(if ("msu_boot_events_abi_words" in layouts) bootSections.size == 1 else bootSections.isEmpty()) {
            "ULTRA_KERNEL_BOOT_EVENTS_ABI_SECTION"
        }
        bootSections.singleOrNull()?.let { section ->
            require(section.kind == 1 && section.flags == 2L && section.size == 152L &&
                exports["msu_boot_events_abi_words"]?.toLong() == section.address &&
                relocations.none { it.target.toLong() < section.address + section.size && it.target.toLong() + 8 > section.address }) {
                "ULTRA_KERNEL_BOOT_EVENTS_ABI_READONLY"
            }
        }
        val ksuSections = raw.filter { elf.string(nameBytes, it.name) == ".msu.ksu-compat.layout" }
        require(if ("msu_ksu_compat_layout" in layouts) ksuSections.size == 1 else ksuSections.isEmpty()) {
            "ULTRA_KERNEL_KSU_COMPAT_ABI_SECTION"
        }
        ksuSections.singleOrNull()?.let { section ->
            require(section.kind == 1 && section.flags == 2L && section.size == 56L &&
                exports["msu_ksu_compat_layout"]?.toLong() == section.address &&
                relocations.none { it.target.toLong() < section.address + section.size && it.target.toLong() + 8 > section.address }) {
                "ULTRA_KERNEL_KSU_COMPAT_ABI_READONLY"
            }
        }
        val suTargetSections = raw.filter { elf.string(nameBytes, it.name) == ".msu.su-runtime.target" }
        require(if ("msu_su_runtime_target_facts" in layouts) suTargetSections.size == 1 else suTargetSections.isEmpty()) {
            "ULTRA_KERNEL_SU_RUNTIME_TARGET_SECTION"
        }
        if (runtimeProfiles != null) require("msu_su_runtime_target_facts" in layouts) {
            "ULTRA_KERNEL_SU_RUNTIME_TARGET_MISSING"
        }
        require(runtimeProfiles == null || runtimeLayout != null) { "ULTRA_KERNEL_SU_RUNTIME_LAYOUT_MISSING" }
        suTargetSections.singleOrNull()?.let { target ->
            require(relocations.none { it.target.toLong() < target.address + target.size &&
                it.target.toLong() + 8 > target.address }) { "ULTRA_KERNEL_SU_RUNTIME_TARGET_RELOCATION" }
        }
        require(importNames.keys.containsAll(dataSignatures.keys)) { "ULTRA_KERNEL_RESIDENT_REFERENCE_DATA_IMPORTS" }
        return Payload(sections, imports, relocations, entry, memoryBytes, kernelMemoryBytes,
            exports, layouts, importNames.keys.toSet(), residentHost, runtimeLayout, runtimeProfiles)
    }

    private fun verifySessionAbi(payload: Payload, facts: UltraKernelManagerClaimPayload.SessionAbiFacts?,
        kernel: ByteArray, symbols: UltraKernelSymbols) {
        val words = payload.layouts["msu_su_session_abi_words"]
        require((words != null) == (facts != null)) { "ULTRA_KERNEL_SESSION_ABI_REFERENCE" }
        if (words == null || facts == null) return
        val section = payload.sections.single { it.name == ".msu.session.abi" }
        require(section.flags == 1 && section.offset == facts.offset && section.file.size == facts.bytes &&
            facts.bytes == 96 && words.toList() == facts.words &&
            UltraKernelSpec.sha256(section.file) == facts.compiledSha256 &&
            section.file.indices.step(4).map { UltraKernelA64.le32Bits(section.file, it) } == facts.words) {
            "ULTRA_KERNEL_SESSION_ABI_COMPILED_IDENTITY"
        }
        for ((name, callback) in facts.callbacks) {
            val code = payload.sections.singleOrNull { it.flags and 4 != 0 && callback.offset >= it.offset &&
                callback.offset.toLong() + 4 <= it.offset.toLong() + it.file.size }
                ?: error("ULTRA_KERNEL_SESSION_ABI_CALLBACK_ENTRY:$name")
            if (facts.cfiMode == "kcfi") require(callback.offset - code.offset >= 4 &&
                UltraKernelA64.le32Bits(code.file, callback.offset - code.offset - 4) == callback.kcfiPrefix &&
                callback.kcfiPrefix == callback.objectKcfiType) { "ULTRA_KERNEL_SESSION_ABI_CALLBACK_PREFIX:$name" }
        }
        UltraKernelResidentAbi.verifySession(kernel, symbols, facts)
    }

    private fun verifyBootEventsAbi(payload: Payload, facts: UltraKernelManagerClaimPayload.BootEventsAbiFacts?,
        kernel: ByteArray, symbols: UltraKernelSymbols) {
        val words = payload.layouts["msu_boot_events_abi_words"]
        require((words != null) == (facts != null)) { "ULTRA_KERNEL_BOOT_EVENTS_ABI_REFERENCE" }
        if (words == null || facts == null) return
        val section = payload.sections.single { it.name == ".msu.boot_events.abi" }
        require(section.flags == 1 && section.offset == facts.offset && section.file.size == facts.bytes &&
            facts.bytes == 152 && words.toList() == facts.words &&
            UltraKernelSpec.sha256(section.file) == facts.compiledSha256 &&
            section.file.indices.step(4).map { UltraKernelA64.le32Bits(section.file, it) } == facts.words) {
            "ULTRA_KERNEL_BOOT_EVENTS_ABI_COMPILED_IDENTITY"
        }
        require(UltraKernelResidentAbi.targetConfig(kernel)["CONFIG_KPROBES"] == "y") {
            "ULTRA_KERNEL_BOOT_EVENTS_KPROBES_REQUIRED"
        }
        for ((type, layout) in facts.layouts) {
            val actual = UltraKernelBtf.selectedStructure(kernel, symbols, type, layout.members.keys)
                ?: error("ULTRA_KERNEL_BOOT_EVENTS_ABI_BTF_REQUIRED:$type")
            require(actual.bytes == layout.bytes && actual.members == layout.members) {
                "ULTRA_KERNEL_BOOT_EVENTS_ABI_TARGET_EXACT:$type"
            }
        }
        UltraKernelResidentAbi.verifyCfi(kernel, facts.cfiMode, facts.normalizeIntegers)
        val signatures = facts.callbacks.values.map { it.signature }.toSet()
        val targetTypes = if (facts.cfiMode == "kcfi") UltraKernelBtf.functionTypeids(kernel, symbols, signatures) else emptyMap()
        for ((name, callback) in facts.callbacks) {
            val code = payload.sections.singleOrNull { it.flags and 4 != 0 && callback.offset >= it.offset &&
                callback.offset.toLong() + 4 <= it.offset.toLong() + it.file.size }
                ?: error("ULTRA_KERNEL_BOOT_EVENTS_ABI_CALLBACK_ENTRY:$name")
            require(UltraKernelBtf.memberSignature(kernel, symbols, callback.member.substringBefore('.'),
                callback.member.substringAfter('.')) == "*" + callback.signature) {
                "ULTRA_KERNEL_BOOT_EVENTS_ABI_CALLBACK_TARGET:$name"
            }
            if (facts.cfiMode == "kcfi") require(callback.offset - code.offset >= 4 &&
                UltraKernelA64.le32Bits(code.file, callback.offset - code.offset - 4) == callback.kcfiPrefix &&
                callback.kcfiPrefix == callback.objectKcfiType && targetTypes[callback.signature] == setOf(callback.kcfiPrefix)) {
                "ULTRA_KERNEL_BOOT_EVENTS_ABI_CALLBACK_KCFI:$name"
            }
        }
    }

    private fun verifyKsuCompatAbi(payload: Payload, facts: UltraKernelManagerClaimPayload.KsuCompatAbiFacts?,
        kernel: ByteArray, symbols: UltraKernelSymbols) {
        val words = payload.layouts["msu_ksu_compat_layout"]
        require((words != null) == (facts != null)) { "ULTRA_KERNEL_KSU_COMPAT_ABI_REFERENCE" }
        if (words == null || facts == null) return
        val section = payload.sections.single { it.name == ".msu.ksu-compat.layout" }
        require(section.flags == 1 && section.offset == facts.offset && section.file.size == facts.bytes &&
            facts.bytes == 56 && words.toList() == facts.words &&
            UltraKernelSpec.sha256(section.file) == facts.compiledSha256 &&
            section.file.indices.step(4).map { UltraKernelA64.le32Bits(section.file, it) } == facts.words) {
            "ULTRA_KERNEL_KSU_COMPAT_ABI_COMPILED_IDENTITY"
        }
        for ((type, layout) in facts.layouts) {
            val actual = UltraKernelBtf.selectedStructure(kernel, symbols, type, layout.members.keys)
                ?: error("ULTRA_KERNEL_KSU_COMPAT_ABI_BTF_REQUIRED:$type")
            require(actual.bytes == layout.bytes && actual.members == layout.members) {
                "ULTRA_KERNEL_KSU_COMPAT_ABI_TARGET_EXACT:$type"
            }
        }
        UltraKernelResidentAbi.verifyCfi(kernel, facts.cfiMode, facts.normalizeIntegers)
        val signatures = facts.callbacks.values.map { it.signature }.toSet()
        val targetTypes = if (facts.cfiMode == "kcfi") UltraKernelBtf.functionTypeids(kernel, symbols, signatures) else emptyMap()
        for ((name, callback) in facts.callbacks) {
            val code = payload.sections.singleOrNull { it.flags and 4 != 0 && callback.offset >= it.offset &&
                callback.offset.toLong() + 4 <= it.offset.toLong() + it.file.size }
                ?: error("ULTRA_KERNEL_KSU_COMPAT_ABI_CALLBACK_ENTRY:$name")
            require(UltraKernelBtf.memberSignature(kernel, symbols, callback.member.substringBefore('.'),
                callback.member.substringAfter('.')) == "*" + callback.signature) {
                "ULTRA_KERNEL_KSU_COMPAT_ABI_CALLBACK_TARGET:$name"
            }
            if (facts.cfiMode == "kcfi") require(callback.offset - code.offset >= 4 &&
                UltraKernelA64.le32Bits(code.file, callback.offset - code.offset - 4) == callback.kcfiPrefix &&
                callback.kcfiPrefix == callback.objectKcfiType && targetTypes[callback.signature] == setOf(callback.kcfiPrefix)) {
                "ULTRA_KERNEL_KSU_COMPAT_ABI_CALLBACK_KCFI:$name"
            }
        }
    }

    private fun bindRuntimeLayout(payload: Payload, facts: UltraKernelManagerClaimPayload.RuntimeLayoutFacts?,
        kernel: ByteArray, symbols: UltraKernelSymbols): Payload {
        val table = payload.runtimeLayout
        require((table != null) == (facts != null)) { "ULTRA_KERNEL_RUNTIME_LAYOUT_REFERENCE" }
        if (table == null || facts == null) return payload
        require(table.offset == facts.offset && table.bytes == facts.bytes && facts.words.size * 4 == table.bytes &&
            facts.fields.isNotEmpty() && facts.fields.size <= 1024 &&
            facts.fields.map { it.key }.distinct().size == facts.fields.size) { "ULTRA_KERNEL_RUNTIME_LAYOUT_REFERENCE_RANGE" }
        val section = payload.sections.single { it.name == ".msu.runtime.layout" }
        require(section.flags == 3 && section.offset == table.offset && section.file.size == table.bytes &&
            UltraKernelSpec.sha256(section.file) == facts.compiledSha256 &&
            section.file.indices.step(4).map { UltraKernelA64.le32Bits(section.file, it) } == facts.words) {
            "ULTRA_KERNEL_RUNTIME_LAYOUT_COMPILED_IDENTITY"
        }
        val words = facts.words.toIntArray()
        require(words.size == 6 + facts.fields.size * 3 && words.take(6) ==
            listOf(0x4d53554c, 1, table.bytes, facts.fields.size, 0, 0)) { "ULTRA_KERNEL_RUNTIME_LAYOUT_HEADER" }
        val absent = facts.fields.indices.filter { words[6 + it * 3] == -1 }.mapTo(linkedSetOf()) { facts.fields[it].key }
        val keys = facts.fields.mapTo(linkedSetOf()) { it.key }
        val queries = facts.fields.associate { it.key to it.targetKeys() }
        val optionalQueries = queries.filter { (key, names) -> key in absent || names.size > 1 }.values.flatten().toSet()
        val btf = UltraKernelBtf.abi(kernel, symbols, queries.values.flatten().toSet(), optionalQueries)
        val accessor = if (btf == null) UltraKernelAccessorRuntimeLayout.resolve(kernel, symbols, keys) else null
        val selected = btf?.let { layout -> queries.mapValues { (key, names) ->
            val present = names.filter { it in layout.fields }
            require(present.size <= 1) { "ULTRA_KERNEL_RUNTIME_LAYOUT_MEMBER_AMBIGUOUS:$key" }
            present.singleOrNull()
        } }.orEmpty()
        val actual = btf?.let { layout -> UltraKernelLayout(selected.mapNotNull { (key, name) ->
            name?.let { key to layout.fields.getValue(it) }
        }.toMap()) } ?: requireNotNull(accessor).layout
        val signatures = accessor?.memberSignatures ?: run {
            val present = selected.filterKeys { it !in absent }
            val targetSignatures = UltraKernelBtf.signatures(kernel, symbols, emptySet(), present.values.filterNotNull().toSet())
                ?: error("ULTRA_KERNEL_RUNTIME_LAYOUT_TYPE_SIGNATURE_EVIDENCE")
            present.mapNotNull { (key, name) -> name?.let { key to targetSignatures.getValue(it) } }.toMap()
        }
        val bindings = linkedMapOf<String, UltraKernelResidentAbi.BoundField>()
        for ((index, field) in facts.fields.withIndex()) {
            val at = 6 + index * 3
            val offset = words[at]; val width = words[at + 1]; val owner = words[at + 2]
            if (offset == -1) {
                require(width == 0 && owner == 0 && field.signature == null && field.alignment == 0 &&
                    actual.fields[field.key] == null) { "ULTRA_KERNEL_RUNTIME_LAYOUT_OPTIONAL:${field.key}" }
                continue
            }
            val target = actual.fields[field.key] ?: error("ULTRA_KERNEL_RUNTIME_LAYOUT_FIELD:${field.key}:" +
                (accessor?.missing?.get(field.key) ?: "BTF_FIELD_MISSING"))
            require(signatures[field.key] != null) { "ULTRA_KERNEL_RUNTIME_LAYOUT_TYPE_SIGNATURE_EVIDENCE:${field.key}:${target.source}" }
            val alignment = field.alignment
            require(offset >= 0 && width > 0 && owner >= offset.toLong() + width &&
                alignment in 1..4096 && alignment and (alignment - 1) == 0 && offset % alignment == 0 &&
                target.widthBytes == width && target.offsetBytes % alignment == 0 &&
                target.ownerSizeBytes != null && target.ownerSizeBytes >= target.offsetBytes.toLong() + width &&
                field.signature != null && signatures[field.key] == field.signature) {
                "ULTRA_KERNEL_RUNTIME_LAYOUT_TARGET:${field.key}"
            }
            words[at] = target.offsetBytes
            words[at + 2] = target.ownerSizeBytes
            bindings[field.key] = UltraKernelResidentAbi.BoundField(offset, width, owner, target)
        }
        // Publish ready only after every slot was derived and checked. The original ELF remains immutable.
        words[4] = 1
        val bound = buffer(words.size * 4).apply { words.forEach { putInt(it) } }.array()
        return payload.copy(sections = payload.sections.map { if (it === section) it.copy(file = bound) else it },
            boundFields = bindings)
    }

    private fun bindRuntimeProfiles(payload: Payload, facts: UltraKernelManagerClaimPayload.RuntimeProfilesFacts?,
        kernel: ByteArray, symbols: UltraKernelSymbols, records: List<UltraKernelPlan.PatchRecord>): Payload {
        val table = payload.runtimeProfiles
        require((table != null) == (facts != null)) { "ULTRA_KERNEL_SU_RUNTIME_PROFILES_REFERENCE" }
        if (table == null || facts == null) return payload
        require(symbols.imageSize == kernel.size && table.offset == facts.offset && table.bytes == facts.bytes &&
            table.bytes == 372 && facts.words.size == 93 && facts.names == listOf("do_execveat_common",
                "__arm64_sys_newfstatat", "__arm64_sys_faccessat", "__arm64_sys_statx", "__arm64_sys_faccessat2")) {
            "ULTRA_KERNEL_SU_RUNTIME_PROFILES_REFERENCE_RANGE"
        }
        val section = payload.sections.single { it.name == ".msu.su-runtime.profiles" }
        require(section.flags == 3 && section.offset == table.offset && section.file.size == table.bytes &&
            UltraKernelSpec.sha256(section.file) == facts.compiledSha256 &&
            section.file.indices.step(4).map { UltraKernelA64.le32Bits(section.file, it) } == facts.words) {
            "ULTRA_KERNEL_SU_RUNTIME_PROFILES_COMPILED_IDENTITY"
        }
        val words = facts.words.toIntArray()
        val filename = facts.filename
        val compiledTargetFacts = payload.layouts["msu_su_runtime_target_facts"]
        require(words.take(5) == listOf(0x4d535550, 1, table.bytes, 5, 0) && words[5] in 0..1 &&
            words[6] == filename.offset && words[7] == 0 && filename.offset >= 0 && filename.bytes == 8 &&
            filename.ownerBytes >= filename.offset.toLong() + filename.bytes && filename.alignment == 8 &&
            filename.offset % filename.alignment == 0 && filename.signature.isNotEmpty() &&
            facts.targetFacts.size == 9 && compiledTargetFacts?.toList() == facts.targetFacts &&
            facts.targetFacts.take(4) == listOf(1, filename.ownerBytes, filename.offset, filename.bytes) &&
            facts.targetFacts[4] > 0 && facts.targetFacts[5] >= 0 && facts.targetFacts[6] == 8 &&
            facts.targetFacts[5].toLong() + facts.targetFacts[6] <= facts.targetFacts[4] &&
            facts.targetFacts.takeLast(2) == listOf(0, 0) && facts.execAbi.startsWith("*f") &&
            facts.execAbi.endsWith(")") && facts.execAbi.length <= 65536 &&
            facts.userArgPtrBytes in setOf(8, 16) && facts.flagsRegister == 2 + 2 * facts.userArgPtrBytes / 8 &&
            facts.registersSignature.startsWith("a") && facts.registersSignature.length <= 65536) {
            "ULTRA_KERNEL_SU_RUNTIME_PROFILES_HEADER"
        }
        for ((index, name) in facts.names.withIndex()) {
            val nameBytes = name.toByteArray(Charsets.US_ASCII)
            val at = 32 + index * 68
            require(nameBytes.size < 40 && (0 until 40).all {
                section.file[at + it] == if (it < nameBytes.size) nameBytes[it] else 0.toByte()
            } && (18 + index * 17..24 + index * 17).all { words[it] == 0 }) {
                "ULTRA_KERNEL_SU_RUNTIME_PROFILES_UNBOUND:$index"
            }
        }
        UltraKernelResidentAbi.verifyRuntimeCfi(kernel, words[5] == 1)
        val target = UltraKernelBtf.abi(kernel, symbols, setOf("filename.name", "pt_regs.__size", "pt_regs.regs"))
            ?: error("ULTRA_KERNEL_SU_RUNTIME_PROFILES_BTF_REQUIRED")
        val actual = target.fields["filename.name"] ?: error("ULTRA_KERNEL_SU_RUNTIME_PROFILES_FILENAME_MISSING")
        val execName = if (symbols.offsetOf("do_execveat_common") != null) "do_execveat_common" else "__do_execve_file"
        if (execName != "do_execveat_common") require(symbols.offsetOf(execName) != null &&
            UltraKernelExecveEntry.resolve(symbols, kernel).offsetOf("do_execveat_common") == symbols.offsetOf(execName)) {
            "ULTRA_KERNEL_SU_RUNTIME_PROFILES_LEGACY_EXEC_WRAPPERS"
        }
        val signatures = UltraKernelBtf.signatures(kernel, symbols, setOf(execName), setOf("filename.name", "pt_regs.regs"))
            ?: error("ULTRA_KERNEL_SU_RUNTIME_PROFILES_BTF_REQUIRED")
        require(actual.widthBytes == filename.bytes && actual.offsetBytes >= 0 &&
            actual.offsetBytes % filename.alignment == 0 && actual.ownerSizeBytes != null &&
            actual.ownerSizeBytes >= actual.offsetBytes.toLong() + filename.bytes &&
            signatures["filename.name"] == filename.signature) {
            "ULTRA_KERNEL_SU_RUNTIME_PROFILES_FILENAME"
        }
        require(target.fields["pt_regs.__size"]?.widthBytes == facts.targetFacts[4] &&
            target.fields["pt_regs.regs"]?.offsetBytes == facts.targetFacts[5] &&
            signatures["pt_regs.regs"] == facts.registersSignature) { "ULTRA_KERNEL_SU_RUNTIME_PROFILES_REGISTERS" }
        val expectedExec = facts.execAbi.removePrefix("*")
        val actualExec = signatures.getValue(execName)
        require(if (execName == "do_execveat_common") actualExec == expectedExec else
            actualExec.startsWith(expectedExec.dropLast(1)) && actualExec.endsWith(")") &&
                actualExec.length > expectedExec.length) { "ULTRA_KERNEL_SU_RUNTIME_PROFILES_EXEC_ABI:$execName" }
        fun patchedBytes(offset: Int, length: Int): ByteArray {
            require(offset >= 0 && offset.toLong() + length <= kernel.size) {
                "ULTRA_KERNEL_SU_RUNTIME_PROFILES_ENTRY_RANGE"
            }
            val bytes = kernel.copyOfRange(offset, offset + length)
            for (record in records) {
                val start = maxOf(offset.toLong(), record.offset)
                val end = minOf(offset.toLong() + length, record.offset + record.replacement.size)
                if (start >= end) continue
                require(record.original.size == record.replacement.size &&
                    record.offset + record.replacement.size <= kernel.size) { "ULTRA_KERNEL_SU_RUNTIME_PROFILES_PATCH_RANGE" }
                record.replacement.copyInto(bytes, (start - offset).toInt(), (start - record.offset).toInt(),
                    (end - record.offset).toInt())
            }
            return bytes
        }
        val targetNames = listOf(execName) + facts.names.drop(1)
        for ((index, name) in targetNames.withIndex()) {
            val offsets = symbols.offsetsOf(name)
            require(offsets.size <= 1) { "ULTRA_KERNEL_SU_RUNTIME_PROFILES_ENTRY_AMBIGUOUS:$name" }
            val offset = offsets.singleOrNull()
            if (offset == null && index >= 3) continue
            require(offset != null && offset >= 0 && offset % 4 == 0 && offset.toLong() + 24 <= kernel.size) {
                "ULTRA_KERNEL_SU_RUNTIME_PROFILES_ENTRY:$name"
            }
            val entry = patchedBytes(offset, 24)
            val nameBytes = ByteArray(40).apply { name.toByteArray(Charsets.US_ASCII).copyInto(this) }
            for (word in 0 until 10) words[8 + index * 17 + word] = UltraKernelA64.le32Bits(nameBytes, word * 4)
            for (word in 0 until 6) words[19 + index * 17 + word] = UltraKernelA64.le32Bits(entry, word * 4)
            if (index > 0 && words[5] == 1) {
                require(offset >= 4) { "ULTRA_KERNEL_SU_RUNTIME_PROFILES_CFI_RANGE:$name" }
                words[18 + index * 17] = UltraKernelA64.le32Bits(patchedBytes(offset - 4, 4), 0)
            }
        }
        words[6] = actual.offsetBytes
        words[4] = 1
        val bound = buffer(words.size * 4).apply { words.forEach { putInt(it) } }.array()
        val targetFactsSection = payload.sections.single { it.name == ".msu.su-runtime.target" }
        val boundTargetFacts = facts.targetFacts.toIntArray().apply {
            this[1] = actual.ownerSizeBytes
            this[2] = actual.offsetBytes
        }
        val targetBytes = buffer(36).apply { boundTargetFacts.forEach { putInt(it) } }.array()
        return payload.copy(sections = payload.sections.map {
            when (it) {
                section -> it.copy(file = bound)
                targetFactsSection -> it.copy(file = targetBytes)
                else -> it
            }
        })
    }

    private fun verifyTaskProfile(elf: Payload, facts: UltraKernelManagerClaimPayload.TaskProfileFacts?,
        kernel: ByteArray, symbols: UltraKernelSymbols, profileSymbols: UltraKernelSymbols): Payload {
        val words = elf.layouts["msu_task_profile_compiled_layout"]
        require((words != null) == (facts != null)) { "ULTRA_KERNEL_TASK_PROFILE_REFERENCE" }
        if (words == null || facts == null) {
            require(elf.exports.keys.intersect(UltraKernelManagerClaimPayload.taskProfileFunctions.keys).isEmpty()) {
                "ULTRA_KERNEL_TASK_PROFILE_LAYOUT_MISSING"
            }
            return elf
        }
        require(words.size == 26 && words.toList() == facts.words &&
            UltraKernelSpec.sha256(buffer(104).apply { words.forEach { putInt(it) } }.array()) == facts.layoutSha256 &&
            facts.imports.keys.all(elf.importNames::contains) &&
            elf.importNames.intersect(UltraKernelManagerClaimPayload.taskProfileFunctions.keys).isEmpty() &&
            facts.entries.keys == UltraKernelManagerClaimPayload.taskProfileFunctions.keys) { "ULTRA_KERNEL_TASK_PROFILE_ACTUAL_LAYOUT" }
        require(!facts.verified || facts.missing.isEmpty() && facts.targetSha256 == UltraKernelSpec.sha256(kernel)) {
            "ULTRA_KERNEL_TASK_PROFILE_TARGET"
        }
        for ((name, prefix) in facts.entries + facts.callbackEntries + facts.implementation?.entries.orEmpty()) {
            if (name in facts.entries || name in facts.implementation?.entries.orEmpty()) require(elf.exports[name] == prefix.entry) { "ULTRA_KERNEL_TASK_PROFILE_ENTRY:$name" }
            require(elf.sections.any { it.flags and 4 != 0 && prefix.entry >= it.offset &&
                prefix.entry.toLong() + 4 <= it.offset.toLong() + it.file.size }) { "ULTRA_KERNEL_TASK_PROFILE_ENTRY_RANGE:$name" }
            if (facts.cfiMode == "kcfi") {
                val section = elf.sections.singleOrNull { it.flags and 4 != 0 && prefix.entry - 4 >= it.offset &&
                    prefix.entry.toLong() + 4 <= it.offset.toLong() + it.file.size }
                    ?: error("ULTRA_KERNEL_TASK_PROFILE_CFI_RANGE:$name")
                require(UltraKernelA64.le32Bits(section.file, prefix.entry - section.offset - 4) == prefix.typeid) {
                    "ULTRA_KERNEL_TASK_PROFILE_CFI_PREFIX:$name"
                }
            }
        }
        if (facts.verified) {
            val begin = "IKCFG_ST".toByteArray(Charsets.US_ASCII)
            val end = "IKCFG_ED".toByteArray(Charsets.US_ASCII)
            fun positions(marker: ByteArray) = (0..kernel.size - marker.size).filter { at ->
                marker.indices.all { kernel[at + it] == marker[it] }
            }
            val start = positions(begin).singleOrNull()?.plus(begin.size)
                ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_CONFIG")
            val finish = positions(end).singleOrNull() ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_CONFIG")
            require(finish > start && finish - start <= 1048576) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_CONFIG" }
            val output = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(kernel, start, finish - start)).use { input ->
                val bytes = ByteArray(8192)
                while (true) {
                    val count = input.read(bytes)
                    if (count < 0) break
                    require(output.size() + count <= 4 * 1048576) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_CONFIG_SIZE" }
                    output.write(bytes, 0, count)
                }
            }
            require(UltraKernelSpec.sha256(output.toByteArray()) == facts.configSha256) {
                "ULTRA_KERNEL_TASK_PROFILE_TARGET_CONFIG_IDENTITY"
            }
            val actual = String(output.toByteArray(), Charsets.US_ASCII).lineSequence()
                .filter { it.startsWith("CONFIG_") && '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            require(facts.config.all { (key, value) -> value == (actual[key] ?: "n") } &&
                facts.dispatcherAbi.isNotEmpty() && facts.callbackEntries.isNotEmpty() &&
                UltraKernelBtf.signatures(kernel, symbols, facts.dispatcherAbi.keys, emptySet()) == facts.dispatcherAbi) {
                "ULTRA_KERNEL_TASK_PROFILE_DISPATCHER_TARGET"
            }
        }
        val implementation = facts.implementation ?: return elf
        for (table in listOf(implementation.trace, implementation.target, implementation.marker)) {
            require(elf.layouts[table.symbol]?.toList() == table.words) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_COMPILED_TABLE:${table.symbol}"
            }
            if (table.writable) {
                val section = elf.sections.singleOrNull { it.name == table.section }
                    ?: error("ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_TARGET_SECTION:${table.symbol}")
                require(section.offset == table.offset && section.flags == 3 && section.file.size == table.words.size * 4 &&
                    UltraKernelSpec.sha256(section.file) == table.sha256) {
                    "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_TARGET_SECTION_IDENTITY:${table.symbol}"
                }
            }
        }
        if (!implementation.present) return elf
        val proof = UltraKernelTaskProfileTarget.derive(kernel, profileSymbols, facts)
        require(proof.complete && proof.workMember in 0..1 && proof.workBit in 0..62 && proof.markerWords.size == 28) {
            "ULTRA_KERNEL_TASK_PROFILE_TARGET_PROOFS:" + proof.missing.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
        val targetBytes = UltraKernelTaskProfileTarget.targetBytes(proof)
        val markerBytes = UltraKernelTaskProfileTarget.markerBytes(proof)
        // Publish both tables only after every proof is bound to this Image. Packaged ELF bytes stay immutable.
        return elf.copy(sections = elf.sections.map { section ->
            when (section.name) {
                implementation.target.section -> section.copy(file = targetBytes)
                implementation.marker.section -> section.copy(file = markerBytes)
                else -> section
            }
        })
    }

    /** Match build-owned retained-function evidence to the exact runtime ELF bytes. */
    internal fun verifyRetainedFunctions(bytes: ByteArray,
        expected: Map<String, UltraKernelManagerClaimPayload.RetainedFunction>) {
        val elf = Elf(bytes)
        require(bytes.size >= 64 && bytes.take(6) == listOf(0x7f, 0x45, 0x4c, 0x46, 2, 1).map { it.toByte() } &&
            elf.half(16) == 3 && elf.half(18) == 183 && elf.half(58) == 64) {
            "ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_HEADER"
        }
        val at = elf.number(40, bytes.size)
        val count = elf.half(60)
        require(count > 0 && count <= (bytes.size - at) / 64) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_SECTIONS" }
        data class Entry(val name: Int, val kind: Int, val flags: Long, val address: Long,
            val file: Long, val bytes: Long, val link: Int, val stride: Long)
        val sections = List(count) { index ->
            val pos = at + index * 64
            Entry(elf.word(pos), elf.word(pos + 4), elf.wide(pos + 8), elf.wide(pos + 16),
                elf.wide(pos + 24), elf.wide(pos + 32), elf.word(pos + 40), elf.wide(pos + 56))
        }
        val names = sections.getOrNull(elf.half(62)) ?: error("ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_NAMES")
        val namesBytes = elf.slice(names.file, names.bytes)
        val table = sections.singleOrNull { it.kind == 2 } ?: error("ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_SYMTAB")
        require(table.stride == 24L && table.bytes % 24L == 0L) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_SYMTAB" }
        val strings = sections.getOrNull(table.link) ?: error("ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_STRINGS")
        val stringsBytes = elf.slice(strings.file, strings.bytes)
        val symbolBytes = elf.slice(table.file, table.bytes)
        val symbols = Elf(symbolBytes)
        val found = mutableSetOf<String>()
        for (pos in symbolBytes.indices step 24) {
            val name = elf.string(stringsBytes, symbols.word(pos))
            val wanted = expected[name] ?: continue
            require(found.add(name) && (symbolBytes[pos + 4].toInt() and 15) == 2) {
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_FUNCTION:$name"
            }
            val section = sections.getOrNull(symbols.half(pos + 6))
                ?: error("ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_FUNCTION_SECTION:$name")
            val address = symbols.number(pos + 8, 1048576)
            val length = symbols.number(pos + 16, 1048576)
            require(section.kind == 1 && section.flags and 7L == 6L &&
                elf.string(namesBytes, section.name) == wanted.section && address == wanted.entry && length == wanted.bytes &&
                address % 4 == 0 && length > 0 && address >= section.address &&
                address.toLong() + length <= section.address + section.bytes) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_RANGE:$name" }
            elf.slice(section.file + address - section.address, length.toLong())
            wanted.typeid?.let { typeid ->
                require(address - section.address >= 4 &&
                    UltraKernelA64.le32Bits(elf.slice(section.file + address - section.address - 4, 4), 0) == typeid) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_PREFIX:$name"
                }
            }
        }
        require(found == expected.keys) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_ELF_MISSING" }
    }

    private class Elf(private val bytes: ByteArray) {
        private fun view(at: Int, length: Int): ByteBuffer {
            require(at >= 0 && at <= bytes.size && length <= bytes.size - at) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_RANGE" }
            return ByteBuffer.wrap(bytes, at, length).order(ByteOrder.LITTLE_ENDIAN)
        }
        fun half(at: Int) = view(at, 2).short.toInt() and 0xffff
        fun word(at: Int) = view(at, 4).int
        fun wide(at: Int) = view(at, 8).long
        fun number(at: Int, limit: Int): Int {
            val value = wide(at)
            require(value in 0..limit.toLong()) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_NUMBER" }
            return value.toInt()
        }
        fun slice(at: Long, length: Long): ByteArray {
            require(at >= 0 && at <= bytes.size && length >= 0 && length <= bytes.size - at) {
                "ULTRA_KERNEL_MANAGER_CLAIM_ELF_FILE_RANGE"
            }
            return bytes.copyOfRange(at.toInt(), (at + length).toInt())
        }
        fun string(table: ByteArray, at: Int): String {
            require(at in table.indices) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_STRING_RANGE" }
            var end = at
            while (end < table.size && table[end] != 0.toByte()) end++
            require(end < table.size) { "ULTRA_KERNEL_MANAGER_CLAIM_ELF_STRING_END" }
            return table.copyOfRange(at, end).toString(Charsets.UTF_8)
        }
    }

    private fun buffer(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    private fun bufferAt(bytes: ByteArray, offset: Int) =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN)
    private fun align(value: Int, alignment: Int) = (value + alignment - 1) and -alignment
}
