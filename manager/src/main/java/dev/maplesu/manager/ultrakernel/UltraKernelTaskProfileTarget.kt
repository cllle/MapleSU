package dev.maplesu.manager.ultrakernel

import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Same-generation implementation facts and facts derived from the current input Image. */
internal object UltraKernelTaskProfileTarget {
    const val PROOF_SCHEMA = "maplesu-task-profile-target-proof/2"
    const val SLOT_CONTRACT = "fixed-kernelsu-bit63-layout-compatibility"
    val proofBits = linkedMapOf("slot63" to 1, "rcu" to 2, "bitops" to 4,
        "cfi" to 8, "syscallTracepoint" to 16, "cloneExec" to 32)
    data class Table(val symbol: String, val section: String, val offset: Int, val fileOffset: Int,
        val words: List<Int>, val sha256: String, val writable: Boolean)
    data class Implementation(val present: Boolean, val entries: Map<String, UltraKernelManagerClaimPayload.CallbackPrefix>,
        val signatures: Map<String, String>, val sourceSha256: String, val trace: Table, val target: Table,
        val marker: Table, val nativeAbi: JsonObject, val normalizeIntegers: Boolean,
        val legacyCfi: JsonObject? = null)
    data class Proof(val targetSha256: String, val configSha256: String, val proofSha256: String,
        val workMember: Int, val workBit: Int, val markerWords: List<Int>, val evidence: JsonObject,
        val missing: Map<String, String>) {
        val complete get() = missing.isEmpty()
    }
    private fun identity(value: JsonElement?): JsonObject = (value?.jsonObject
        ?: error("ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_IDENTITY")).also {
        require(!it["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
            (it["bytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0 &&
            it["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_IDENTITY"
        }
    }
    private fun dependencyIdentity(value: JsonElement): JsonObject = value.jsonObject.also {
        if (it["bytes"]?.jsonPrimitive?.longOrNull != 0L) identity(it) else {
            val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            require(!it["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                it["sha256"]?.jsonPrimitive?.content == empty &&
                it["archivePath"]?.jsonPrimitive?.content == "build-dependencies/files/$empty.bin") {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_DEPENDENCY_IDENTITY"
            }
        }
    }
    private fun same(a: JsonObject, b: JsonObject) = a["bytes"] == b["bytes"] && a["sha256"] == b["sha256"]
    private fun words(values: JsonElement?) = values!!.jsonArray.map {
        val word = it.jsonPrimitive.long
        require(word in Int.MIN_VALUE.toLong()..0xffffffffL) { "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_WORD" }
        word.toInt()
    }
    private fun bytes(words: List<Int>) = ByteBuffer.allocate(words.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        .apply { words.forEach { putInt(it) } }.array()

    /** A real legacy module supplies its own implementation facts. A raw claim
     * with disabled LTO/CFI is neither its owner nor an execution prerequisite. */
    fun parseLegacyModule(report: JsonObject): Implementation {
        require(report["schema"]?.jsonPrimitive?.content == "maplesu-init-bridge-task-profile-abi/1" &&
            report["implementationOrigin"]?.jsonPrimitive?.content == "normal-module-legacy-cfi" &&
            listOf("originalModuleWritten", "targetQualified", "deviceQualified").all {
                report[it]?.jsonPrimitive?.booleanOrNull == false
            }) { "ULTRA_KERNEL_TASK_PROFILE_MODULE_SCHEMA" }
        val policy = report.getValue("cfiPolicy").jsonObject
        require(policy["mode"]?.jsonPrimitive?.content == "legacy-lto-cfi") { "ULTRA_KERNEL_TASK_PROFILE_MODULE_MODE" }
        val successor = report.getValue("sourceSuccessor").jsonObject
        val source = identity(report["sourceSuccessorIdentity"]).getValue("sha256").jsonPrimitive.content
        val requiredSources = setOf("manager_task_state.c", "manager_task_state.h", "manager_su_runtime_target.c", "manager_su_runtime_factory.c")
            .map { "runtime/ultra/identity/kernel/$it" }.toSet()
        val rows = successor.getValue("files").jsonArray.map { it.jsonObject }
        val selected = report.getValue("sources").jsonObject
        require(successor["schema"]?.jsonPrimitive?.content == "maplesu-task-profile-source-successor/1" &&
            successor["sourceOwnerFrozen"]?.jsonPrimitive?.booleanOrNull == true &&
            successor["productionWritten"]?.jsonPrimitive?.booleanOrNull == false && rows.size == 4 &&
            rows.map { it.getValue("logicalPath").jsonPrimitive.content }.toSet() == requiredSources && selected.keys == requiredSources) {
            "ULTRA_KERNEL_TASK_PROFILE_MODULE_SOURCES"
        }
        for (row in rows) {
            val name = row.getValue("logicalPath").jsonPrimitive.content
            val actual = selected.getValue(name).jsonObject
            require(same(identity(row["after"]), actual) &&
                same(identity(row["after"]), identity(actual["selected"]))) { "ULTRA_KERNEL_TASK_PROFILE_MODULE_SOURCE:$name" }
        }
        val commands = report.getValue("provenance").jsonObject.getValue("actualLegacyCommands").jsonArray
        require(commands.size >= 5 && commands.all { item -> item.jsonObject.let {
            identity(it["identity"])
            it["legacyEnabled"]?.jsonPrimitive?.booleanOrNull == true &&
                it["lto"]?.jsonPrimitive?.booleanOrNull == true && it["crossDso"]?.jsonPrimitive?.booleanOrNull == true
        } }) { "ULTRA_KERNEL_TASK_PROFILE_MODULE_ACTUAL_CFI" }
        val functions = report.getValue("functions").jsonObject
        val required = setOf("msu_task_profile_trace_bind", "msu_task_profile_trace_open", "msu_task_profile_trace_close",
            "msu_task_profile_trace_ready", "msu_task_profile_sys_enter", "msu_task_profile_marker_entry", "msu_task_profile_marker_return")
        require(functions.keys == required) { "ULTRA_KERNEL_TASK_PROFILE_MODULE_FUNCTIONS" }
        val signatures = functions.mapValues { (_, value) -> value.jsonObject.getValue("signature").jsonPrimitive.content.also {
            require(it.startsWith("*f") && it.length <= 65536) { "ULTRA_KERNEL_TASK_PROFILE_MODULE_SIGNATURE" }
        }.removePrefix("*") }
        val entries = functions.mapValues { (_, value) -> UltraKernelManagerClaimPayload.CallbackPrefix(
            value.jsonObject.getValue("offset").jsonPrimitive.int, 0) }
        val tables = report.getValue("tables").jsonObject
        fun table(name: String, section: String, count: Int, writable: Boolean): Table {
            val item = tables.getValue(name).jsonObject
            val values = words(item["values"])
            require(item["section"]?.jsonPrimitive?.content == section && values.size == count &&
                item["bytes"]?.jsonPrimitive?.intOrNull == count * 4 &&
                item["writable"]?.jsonPrimitive?.booleanOrNull == writable &&
                item["sha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(bytes(values))) {
                "ULTRA_KERNEL_TASK_PROFILE_MODULE_TABLE:$name"
            }
            if (writable) require(values.take(3) == listOf(if (count == 34) 0x4d535450 else 0x4d53544d, 1, count * 4) &&
                values.drop(3).all { it == 0 }) { "ULTRA_KERNEL_TASK_PROFILE_MODULE_UNBOUND:$name" }
            return Table(name, section, item.getValue("offset").jsonPrimitive.int,
                item.getValue("fileOffset").jsonPrimitive.int, values, item.getValue("sha256").jsonPrimitive.content, writable)
        }
        val native = report.getValue("nativeAbi").jsonObject
        val alignment = report.getValue("nativeAlignment").jsonObject
        val values = listOf("listHeadBytes", "listHeadAlignment", "rwlockBytes", "rwlockAlignment")
            .map { native.getValue(it).jsonPrimitive.int }
        require(alignment["independentlyMeasuredFromBridgeBuild"]?.jsonPrimitive?.booleanOrNull == true &&
            words(alignment["values"]) == values && native.getValue("types").jsonObject.keys == setOf("list_head", "rwlock_t") &&
            report.getValue("nativeTypes").jsonObject == JsonObject(native.getValue("types").jsonObject.mapValues {
                it.value.jsonObject.getValue("signature")
            })) { "ULTRA_KERNEL_TASK_PROFILE_MODULE_NATIVE_ABI" }
        return Implementation(true, entries, signatures, source,
            table("msu_task_profile_trace_compiled_layout", ".msu.task_profile.trace-layout", 12, false),
            table("msu_task_profile_target", ".msu.task_profile.target", 34, true),
            table("msu_task_profile_marker_target", ".msu.task_profile.marker-target", 28, true),
            native, policy.getValue("normalizeIntegers").jsonPrimitive.boolean, report.getValue("legacyCfi").jsonObject)
    }
    fun parseImplementation(report: JsonObject, parent: JsonObject, reference: JsonObject,
        coreInputs: Map<String, JsonObject>, elf: ByteArray, mode: String, normalize: Boolean): Implementation {
        require(report["schema"]?.jsonPrimitive?.content == "maplesu-task-profile-implementation/1" &&
            report["metadataComplete"]?.jsonPrimitive?.booleanOrNull == true &&
            report["targetQualified"]?.jsonPrimitive?.booleanOrNull == false &&
            report["deviceQualified"]?.jsonPrimitive?.booleanOrNull == false &&
            report["qualificationStage"]?.jsonPrimitive?.content == "per-input-image-binding" &&
            report["targetProofSchema"]?.jsonPrimitive?.content == PROOF_SCHEMA &&
            report["slot63Contract"]?.jsonPrimitive?.content == SLOT_CONTRACT &&
            report["present"] == parent["implementationPresent"] &&
            report["sourceSelectionIdentity"] == parent["sourceSelectionIdentity"]) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_SCHEMA"
        }
        val successor = report.getValue("sourceSuccessor").jsonObject
        require(successor["schema"]?.jsonPrimitive?.content == "maplesu-task-profile-source-successor/1" &&
            successor["productionWritten"]?.jsonPrimitive?.booleanOrNull == false &&
            successor["sourceOwnerFrozen"]?.jsonPrimitive?.booleanOrNull == true &&
            same(identity(successor["identity"]), identity(report["sourceSuccessorIdentity"])) &&
            same(identity(successor["parentCoreSelection"]), identity(parent["sourceSelectionIdentity"]))) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_SUCCESSOR"
        }
        identity(successor["sourceFreeze"])
        val runtime = reference.getValue("managementAbi").jsonObject.getValue("upstreamSourceFreeze")
            .jsonObject.getValue("runtimeLayoutEvolution").jsonObject
        require(same(identity(successor["parentRuntimeLayoutSelection"]), identity(runtime["identity"]))) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_RUNTIME_PARENT"
        }
        val rows = successor.getValue("files").jsonArray.map { it.jsonObject }
        val selected = rows.associateBy { it.getValue("logicalPath").jsonPrimitive.content }
        val prefix = "runtime/ultra/identity/kernel/"
        require(rows.size == selected.size && selected.keys == setOf("manager_task_state.c", "manager_task_state.h",
            "manager_su_runtime_target.c", "manager_su_runtime_factory.c").map { prefix + it }.toSet()) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_SOURCE_SET"
        }
        for ((logical, row) in selected) {
            val before = identity(row["before"]); val after = identity(row["after"])
            require(after.getValue("path").jsonPrimitive.content.replace('\\', '/').endsWith("/" + logical.substringAfterLast('/'))) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_SOURCE_PATH:$logical"
            }
            coreInputs[logical]?.let { core ->
                require(same(after, identity(core["nativeCopyIdentity"] ?: core["after"])) &&
                    same(before, identity(core["previousTaskProfileInput"]))) {
                    "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_CORE_CHAIN:$logical"
                }
            }
        }
        val objects = report.getValue("objects").jsonObject
        require(objects.keys == setOf("manager_task_state", "manager_su_runtime_target", "manager_su_runtime_factory")) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_OBJECT_SET"
        }
        for ((name, value) in objects) {
            val item = value.jsonObject; val logical = prefix + name + ".c"
            require(item["logicalPath"]?.jsonPrimitive?.content == logical &&
                same(identity(item["sourceIdentity"]), identity(selected.getValue(logical)["after"])) &&
                same(identity(item["configIdentity"]), identity(parent["configIdentity"]))) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_OBJECT_SOURCE:$name"
            }
            for (key in listOf("objectIdentity", "commandIdentity", "dependencyReceiptIdentity")) identity(item[key])
            val dependencies = item.getValue("dependencies").jsonArray.map(::dependencyIdentity)
            require(listOf(item["sourceIdentity"], parent["headerIdentity"], parent["configIdentity"]).all { dep ->
                dependencies.any { same(it, identity(dep)) } }) { "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_DEPENDENCIES:$name" }
            val policy = item.getValue("cfiPolicy").jsonObject
            require(policy["kcfiEnabled"]?.jsonPrimitive?.booleanOrNull == (mode == "kcfi") &&
                policy["normalizeIntegers"]?.jsonPrimitive?.booleanOrNull == normalize) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_CFI:$name"
            }
        }
        fun declaration(item: JsonObject, name: String) {
            require(item["method"]?.jsonPrimitive?.content == "same-object-BTF-VAR-ELF-relocation" &&
                item["bytes"]?.jsonPrimitive?.intOrNull == 8 &&
                (item["offset"]?.jsonPrimitive?.intOrNull ?: -1) >= 0 &&
                (item["btfTypeId"]?.jsonPrimitive?.intOrNull ?: 0) > 0) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_DECLARATION:$name"
            }
            for (key in listOf("sourceIdentity", "objectIdentity", "commandIdentity", "dependencyReceiptIdentity")) identity(item[key])
            val reloc = item.getValue("relocations").jsonArray.single().jsonObject
            require(reloc["symbol"]?.jsonPrimitive?.content == name && reloc["type"]?.jsonPrimitive?.intOrNull == 257 &&
                reloc["addend"]?.jsonPrimitive?.longOrNull == 0L) { "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_DECLARATION_RELOCATION:$name" }
        }
        val functions = report.getValue("functions").jsonObject
        val required = setOf("msu_task_profile_trace_bind", "msu_task_profile_trace_open", "msu_task_profile_trace_close",
            "msu_task_profile_trace_ready", "msu_task_profile_sys_enter", "msu_task_profile_marker_entry", "msu_task_profile_marker_return")
        require(functions.keys == required) { "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_FUNCTION_SET" }
        val signatures = linkedMapOf<String, String>()
        val entries = functions.mapValues { (name, value) ->
            val item = value.jsonObject; val signature = item.getValue("signature").jsonPrimitive.content
            require(signature.startsWith("*f") && signature.length <= 65536 &&
                item["signature"] == item["declarationSignature"] &&
                objects.getValue("manager_task_state").jsonObject.all { (key, expected) -> item[key] == expected }) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_FUNCTION_TYPE:$name"
            }
            declaration(item.getValue("declaration").jsonObject, name)
            require(item.getValue("declaration").jsonObject["signature"] == item["signature"]) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_DECLARED_TYPE:$name"
            }
            val typeid = if (mode != "kcfi") 0 else item.getValue("typeid").jsonPrimitive.long.also {
                require(it in 0..0xffffffffL && item["typeid"] == item["declarationTypeid"]) {
                    "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_FUNCTION_CFI:$name"
                }
            }.toInt()
            signatures[name] = signature.removePrefix("*")
            UltraKernelManagerClaimPayload.CallbackPrefix(item.getValue("entryOffset").jsonPrimitive.int, typeid)
        }
        declaration(report.getValue("callbackRegistrationReference").jsonObject, "msu_task_profile_sys_enter")
        val registrations = report.getValue("callbackRegistrationReferences").jsonObject
        require(registrations.keys == setOf("msu_task_profile_sys_enter", "msu_task_profile_marker_entry", "msu_task_profile_marker_return")) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_CALLBACK_SET"
        }
        for ((name, value) in registrations) {
            declaration(value.jsonObject, name)
            require(value.jsonObject["signature"]?.jsonPrimitive?.content == "*" + signatures.getValue(name)) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_CALLBACK_TYPE:$name"
            }
        }
        val contracts = report.getValue("sourceContracts").jsonObject
        val requiredContracts = mapOf(
            "msu_task_profile_trace_bind" to setOf("lookup(\"__tracepoint_sys_enter\")", "mutex_lock", "mutex_unlock"),
            "msu_task_profile_trace_open" to setOf("msu_task_profile_target_ready()", "tracepoint_probe_register_prio", "msu_task_profile_sys_enter"),
            "msu_task_profile_trace_close" to setOf("smp_store_release", "tracepoint_probe_unregister", "tracepoint_synchronize_unregister"),
            "msu_task_profile_sys_enter" to setOf("msu_task_profile_trace_ready()", "test_bit(MSU_TASK_PROFILE_DISABLE_ROOT_BIT"),
            "msu_task_profile_apply_current" to setOf("test_and_set_bit", "rcu_read_lock()", "rcu_read_unlock()", "thread_head", "thread_node"))
        for ((name, expressions) in requiredContracts) {
            val contract = contracts.getValue(name).jsonObject
            require(contract["function"]?.jsonPrimitive?.content == name &&
                (contract["sourceBytes"]?.jsonPrimitive?.intOrNull ?: 0) > 0 &&
                contract["sourceSha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true &&
                contract.getValue("requiredExpressions").jsonArray.map { it.jsonPrimitive.content }.containsAll(expressions)) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_SOURCE_CONTRACT:$name"
            }
        }
        val nativeAbi = report.getValue("nativeAbi").jsonObject
        require(nativeAbi["schema"]?.jsonPrimitive?.content == "maplesu-task-profile-native-abi/1" &&
            nativeAbi["targetQualified"]?.jsonPrimitive?.booleanOrNull == false &&
            nativeAbi["deviceQualified"]?.jsonPrimitive?.booleanOrNull == false &&
            same(identity(nativeAbi["providerObjectIdentity"]), identity(parent["objectIdentity"])) &&
            same(identity(nativeAbi["providerCommandIdentity"]), identity(parent["commandIdentity"])) &&
            same(identity(nativeAbi["providerDependencyReceiptIdentity"]), identity(parent["dependencyReceiptIdentity"]))) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_NATIVE_ABI_SOURCE"
        }
        val alignment = nativeAbi.getValue("alignmentFacts").jsonObject
        val alignmentValues = words(alignment["values"])
        require(alignment["symbol"]?.jsonPrimitive?.content == "msu_task_profile_native_type_facts" &&
            alignmentValues == listOf(nativeAbi.getValue("listHeadBytes").jsonPrimitive.int,
                nativeAbi.getValue("listHeadAlignment").jsonPrimitive.int, nativeAbi.getValue("rwlockBytes").jsonPrimitive.int,
                nativeAbi.getValue("rwlockAlignment").jsonPrimitive.int) &&
            nativeAbi["listHeadType"]?.jsonPrimitive?.content == "list_head" &&
            nativeAbi["rwlockType"]?.jsonPrimitive?.content == "rwlock_t") {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_NATIVE_ALIGNMENT"
        }
        for (key in listOf("sourceIdentity", "objectIdentity", "commandIdentity", "dependencyReceiptIdentity")) identity(alignment[key])
        val nativeTypes = nativeAbi.getValue("types").jsonObject
        require(nativeTypes.keys.containsAll(setOf("list_head", "rwlock_t")) &&
            alignment.getValue("typeSignatures").jsonObject.all { (name, value) -> nativeTypes[name]?.jsonObject?.get("signature") == value } &&
            nativeAbi.getValue("lockConfig").jsonObject.keys == setOf("CONFIG_SMP", "CONFIG_QUEUED_RWLOCKS", "CONFIG_PARAVIRT_SPINLOCKS",
                "CONFIG_DEBUG_SPINLOCK", "CONFIG_DEBUG_LOCK_ALLOC", "CONFIG_PREEMPT_RT") &&
            nativeAbi.getValue("lockConfig").jsonObject.values.all { it.jsonPrimitive.content in setOf("y", "n") }) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_NATIVE_TYPES"
        }
        val lifecycle = report.getValue("relocations").jsonObject.getValue("lifecycle").jsonObject
        for ((name, callers) in mapOf("msu_task_profile_trace_open" to setOf("msu_su_runtime_post_fs_data"),
            "msu_task_profile_trace_close" to setOf("msu_su_runtime_post_fs_data", "msu_su_runtime_close",
                "msu_su_runtime_revoke_install", "msu_su_runtime_discarded"))) {
            require(lifecycle.getValue(name).jsonArray.flatMap { it.jsonObject.getValue("callers").jsonArray }
                .map { it.jsonPrimitive.content }.containsAll(callers)) { "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_LIFECYCLE:$name" }
        }
        require(report.getValue("relocations").jsonObject.getValue("targetBind").jsonObject
            .getValue("msu_task_profile_trace_bind").jsonArray.isNotEmpty()) { "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_TARGET_BIND" }
        fun table(key: String, name: String, section: String, count: Int, writable: Boolean): Table {
            val item = report.getValue(key).jsonObject; val data = words(item["values"])
            val at = item.getValue("fileOffset").jsonPrimitive.int
            require(item["symbol"]?.jsonPrimitive?.content == name && item["section"]?.jsonPrimitive?.content == section &&
                item["bytes"]?.jsonPrimitive?.intOrNull == count * 4 && data.size == count &&
                item["writable"]?.jsonPrimitive?.booleanOrNull == writable && at >= 0 &&
                at.toLong() + count * 4 <= elf.size && elf.copyOfRange(at, at + count * 4).contentEquals(bytes(data)) &&
                item["sha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(bytes(data))) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_TABLE:$name"
            }
            if (writable) require(data.take(3) == listOf(if (count == 34) 0x4d535450 else 0x4d53544d, 1, count * 4) &&
                data.drop(3).all { it == 0 } && item["initiallyUnbound"]?.jsonPrimitive?.booleanOrNull == true) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_UNBOUND:$name"
            }
            return Table(name, section, item.getValue("offset").jsonPrimitive.int, at, data,
                item.getValue("sha256").jsonPrimitive.content, writable)
        }
        val trace = table("traceLayout", "msu_task_profile_trace_compiled_layout", ".msu.task_profile.trace-layout", 12, false)
        val target = table("targetBinding", "msu_task_profile_target", ".msu.task_profile.target", 34, true)
        val marker = table("markerTargetBinding", "msu_task_profile_marker_target", ".msu.task_profile.marker-target", 28, true)
        val targetReport = report.getValue("targetBinding").jsonObject
        require(targetReport["requiredProofMask"]?.jsonPrimitive?.intOrNull == 63 &&
            targetReport["targetProofSchema"]?.jsonPrimitive?.content == PROOF_SCHEMA &&
            targetReport["slot63Contract"]?.jsonPrimitive?.content == SLOT_CONTRACT &&
            targetReport.getValue("proofBits").jsonObject.mapValues { it.value.jsonPrimitive.int } == proofBits) {
            "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_PROOF_SCHEMA"
        }
        return Implementation(report.getValue("present").jsonPrimitive.boolean, entries, signatures,
            identity(report["sourceSuccessorIdentity"]).getValue("sha256").jsonPrimitive.content,
            trace, target, marker, nativeAbi, normalize)
    }

    fun targetBytes(proof: Proof): ByteArray {
        require(proof.complete && proof.workMember in 0..1 && proof.workBit in 0..62) {
            "ULTRA_KERNEL_TASK_PROFILE_TARGET_INCOMPLETE"
        }
        fun digest(value: String): ByteArray {
            require(value.matches(Regex("[0-9a-f]{64}"))) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_DIGEST" }
            return ByteArray(32) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
        return ByteBuffer.allocate(136).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x4d535450); putInt(1); putInt(136); putInt(0); putInt(63); putInt(0)
            putInt(63); putInt(proof.workMember); putInt(proof.workBit); putInt(0)
            put(digest(proof.targetSha256)); put(digest(proof.configSha256)); put(digest(proof.proofSha256))
            putInt(12, 1)
        }.array()
    }

    fun markerBytes(proof: Proof): ByteArray {
        require(proof.complete && proof.markerWords.size == 28 &&
            proof.markerWords.take(4) == listOf(0x4d53544d, 1, 112, 1)) {
            "ULTRA_KERNEL_TASK_PROFILE_MARKER_INCOMPLETE"
        }
        return bytes(proof.markerWords)
    }

    private fun targetConfig(kernel: ByteArray): Pair<ByteArray, Map<String, String>> {
        fun marker(text: String): Int {
            val bytes = text.toByteArray(Charsets.US_ASCII)
            return (0..kernel.size - bytes.size).filter { at -> bytes.indices.all { kernel[at + it] == bytes[it] } }
                .singleOrNull() ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_CONFIG_MARKER:$text")
        }
        val start = marker("IKCFG_ST") + 8; val end = marker("IKCFG_ED")
        require(end > start && end - start <= 1048576) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_CONFIG_RANGE" }
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(kernel, start, end - start)).use { input ->
            val block = ByteArray(8192)
            while (true) {
                val size = input.read(block)
                if (size < 0) break
                require(output.size() + size <= 4 * 1048576) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_CONFIG_SIZE" }
                output.write(block, 0, size)
            }
        }
        val bytes = output.toByteArray()
        val config = bytes.toString(Charsets.US_ASCII).lineSequence().filter { it.startsWith("CONFIG_") && '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        return bytes to config
    }

    fun derive(kernel: ByteArray, symbols: UltraKernelSymbols,
        facts: UltraKernelManagerClaimPayload.TaskProfileFacts): Proof =
        derive(kernel, symbols, requireNotNull(facts.implementation), facts.cfiMode)

    /** Claim ELF and init_boot module consume exactly the same per-Image proof. */
    fun derive(kernel: ByteArray, symbols: UltraKernelSymbols, impl: Implementation,
        cfiMode: String): Proof {
        require(cfiMode in setOf("none", "kcfi", "legacy-lto-cfi")) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_CFI_MODE" }
        require(symbols.imageSize == kernel.size && impl.present) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_IMPLEMENTATION" }
        val missing = linkedMapOf<String, String>()
        val evidence = linkedMapOf<String, JsonElement>()
        val sha = UltraKernelSpec.sha256(kernel)
        val configInput = targetConfig(kernel)
        val configSha = UltraKernelSpec.sha256(configInput.first)
        val config = configInput.second
        fun enabled(key: String) = config[key] == "y"
        fun proof(name: String, action: () -> JsonObject) {
            try { evidence[name] = action() }
            catch (error: Exception) { missing[name] = error.message ?: error.javaClass.simpleName }
        }
        fun json(values: Map<String, String>) = JsonObject(values.mapValues { JsonPrimitive(it.value) })
        val needed = setOf("task_struct.__size", "task_struct.stack", "task_struct.thread_info", "task_struct.tasks", "task_struct.thread_node",
            "thread_info.flags", "thread_info.syscall_work", "signal_struct.thread_head", "pt_regs.__size", "pt_regs.regs",
            "pt_regs.syscallno", "tracepoint.__size", "tracepoint.funcs", "tracepoint_func.__size", "tracepoint_func.func",
            "tracepoint_func.data", "kretprobe.__size", "kretprobe.handler", "kretprobe.entry_handler", "kretprobe.kp.addr",
            "kretprobe.maxactive", "kretprobe.nmissed")
        val layout = UltraKernelBtf.abi(kernel, symbols, needed, setOf("thread_info.syscall_work"))
            ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_BTF")
        fun field(name: String) = layout.fields[name] ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_FIELD:$name")
        val taskInfo = field("task_struct.thread_info")
        val flags = field("thread_info.flags")
        val taskFlags = taskInfo.offsetBytes + flags.offsetBytes
        var workMember = -1; var workBit = -1
        var markerWords = emptyList<Int>()
        proof("slot63") {
            require(listOf("CONFIG_ARM64", "CONFIG_64BIT", "CONFIG_THREAD_INFO_IN_TASK").all(::enabled) &&
                flags.widthBytes == 8 && flags.offsetBytes % 8 == 0 && taskFlags % 8 == 0 &&
                taskInfo.widthBytes == flags.ownerSizeBytes &&
                UltraKernelBtf.memberSignature(kernel, symbols, "thread_info", "flags") == "i8:64") {
                "ULTRA_KERNEL_TASK_PROFILE_TARGET_FLAGS64"
            }
            val explicit = UltraKernelBtf.enumValue(kernel, symbols, "TIF_KSU_DISABLE_ESCAPE_WITH_ROOT")
                ?: UltraKernelBtf.integerVariable(kernel, symbols, "TIF_KSU_DISABLE_ESCAPE_WITH_ROOT")
            require(explicit == null || explicit == 63L) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_KSU_BIT" }
            buildJsonObject { put("contract", SLOT_CONTRACT); put("rootBit", 63); put("flagsOffset", taskFlags)
                put("flagsBytes", flags.widthBytes); put("globalUnusedBitProof", false)
                explicit?.let { put("explicitTargetBit", it) } }
        }
        proof("rcu") {
            val head = field("signal_struct.thread_head"); val node = field("task_struct.thread_node")
            val list = UltraKernelBtf.selectedStructure(kernel, symbols, "list_head", setOf("next", "prev"))
                ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_LIST_BTF")
            require(taskInfo.widthBytes == flags.ownerSizeBytes && head.widthBytes == 16 && node.widthBytes == 16 &&
                list.bytes == 16 && list.members.getValue("next").offset == 0 && list.members.getValue("prev").offset == 8 &&
                list.members.values.all { it.bytes == 8 } && head.offsetBytes % 8 == 0 && node.offsetBytes % 8 == 0) {
                "ULTRA_KERNEL_TASK_PROFILE_TARGET_RCU_EMBEDDED_LIST"
            }
            buildJsonObject { put("embeddedThreadInfo", true); put("threadHeadOffset", head.offsetBytes)
                put("threadNodeOffset", node.offsetBytes); put("implementationSourceSha256", impl.sourceSha256) }
        }
        proof("bitops") {
            val abi = impl.nativeAbi
            val types = abi.getValue("types").jsonObject
            val expected = types.mapValues { it.value.jsonObject.getValue("signature").jsonPrimitive.content }
            require(types.keys.containsAll(setOf("list_head", "rwlock_t")) &&
                UltraKernelBtf.typeSignatures(kernel, symbols, expected.keys) == expected &&
                abi["listHeadBytes"]?.jsonPrimitive?.intOrNull == 16 &&
                abi["listHeadAlignment"]?.jsonPrimitive?.intOrNull == 8 &&
                abi["rwlockBytes"] == types.getValue("rwlock_t").jsonObject["bytes"]) {
                "ULTRA_KERNEL_TASK_PROFILE_TARGET_NATIVE_LOCK_ABI"
            }
            val alignment = abi.getValue("rwlockAlignment").jsonPrimitive.int
            require(alignment in 1..4096 && alignment and (alignment - 1) == 0 &&
                (symbols.memoryOffsetOf("tasklist_lock") ?: -1).let { it >= 0 && it % alignment == 0 } &&
                abi.getValue("lockConfig").jsonObject.all { (key, value) -> value.jsonPrimitive.content == (config[key] ?: "n") } &&
                !enabled("CONFIG_PREEMPT_RT")) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_NATIVE_LOCK_CONFIG" }
            val work = field(if (enabled("CONFIG_GENERIC_ENTRY")) "thread_info.syscall_work" else "thread_info.flags")
            require(flags.widthBytes == 8 && work.widthBytes == 8 && work.offsetBytes % 8 == 0) {
                "ULTRA_KERNEL_TASK_PROFILE_TARGET_ATOMIC_WORD"
            }
            buildJsonObject { put("wordBits", 64); put("nativeAbiSha256", UltraKernelSpec.sha256(abi.toString().toByteArray()))
                put("implementationSourceSha256", impl.sourceSha256) }
        }
        proof("cfi") {
            if (cfiMode == "legacy-lto-cfi") {
                require(UltraKernelLegacyModuleRoute.required(kernel) &&
                    impl.normalizeIntegers == enabled("CONFIG_CFI_ICALL_NORMALIZE_INTEGERS")) {
                    "ULTRA_KERNEL_TASK_PROFILE_TARGET_LEGACY_MODE"
                }
                val legacy = requireNotNull(impl.legacyCfi) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_LEGACY_PROOF" }
                require(legacy["schema"]?.jsonPrimitive?.content == "maplesu-task-profile-legacy-cfi/1" &&
                    legacy["relocationOwner"]?.jsonPrimitive?.content == "normal-kernel-module-loader" &&
                    listOf("moduleLoaded", "runtimeOwnerQualified", "deviceQualified").all {
                        legacy[it]?.jsonPrimitive?.booleanOrNull == false
                    }) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_LEGACY_OWNER" }
                val callers = legacy.getValue("callerTypeSets").jsonObject
                require(callers.isNotEmpty()) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_LEGACY_CALLERS" }
                for ((caller, types) in callers) {
                    val ids = types.jsonArray.map { it.jsonPrimitive.content }.toSet()
                    require(ids.isNotEmpty() && ids.all { it.matches(Regex("[0-9a-f]{16}")) } &&
                        UltraKernelLegacyCfi.slowpathCallsites(kernel, symbols, caller).map { it.typeIdHex }.toSet() == ids) {
                        "ULTRA_KERNEL_TASK_PROFILE_TARGET_LEGACY_CALLER:$caller"
                    }
                }
                val members = legacy.getValue("members").jsonObject
                val callsites = legacy.getValue("callbackCallsites").jsonObject
                val required = setOf("msu_task_profile_sys_enter", "msu_task_profile_marker_entry", "msu_task_profile_marker_return")
                require(members.keys == required && callsites.keys == required) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_LEGACY_CALLBACKS" }
                for ((name, element) in members) {
                    val member = element.jsonObject
                    val id = member.getValue("typeIdHex").jsonPrimitive.content
                    require(id.matches(Regex("[0-9a-f]{16}")) && member["symbol"]?.jsonPrimitive?.content == name &&
                        member["proof"]?.jsonPrimitive?.content == "actual-relocated-module-checker-path" &&
                        member["runtimeOwnerQualified"]?.jsonPrimitive?.booleanOrNull == false &&
                        callsites.getValue(name).jsonArray.any { call -> call.jsonObject.let {
                            it["typeIdHex"]?.jsonPrimitive?.content == id &&
                                id in callers.getValue(it.getValue("caller").jsonPrimitive.content).jsonArray.map { value -> value.jsonPrimitive.content }
                        } }) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_LEGACY_TYPE64:$name" }
                }
                for ((member, callback) in mapOf("handler" to "msu_task_profile_marker_return", "entry_handler" to "msu_task_profile_marker_entry"))
                    require(UltraKernelBtf.memberSignature(kernel, symbols, "kretprobe", member) == "*" + impl.signatures.getValue(callback)) {
                        "ULTRA_KERNEL_TASK_PROFILE_TARGET_LEGACY_MARKER_TYPE:$member"
                    }
            } else UltraKernelResidentAbi.verifyCfi(kernel, cfiMode, impl.normalizeIntegers)
            if (cfiMode == "kcfi") {
                val required = impl.entries.filterKeys { it in setOf("msu_task_profile_sys_enter",
                    "msu_task_profile_marker_entry", "msu_task_profile_marker_return") }
                val target = UltraKernelBtf.functionTypeids(kernel, symbols, required.keys.map { impl.signatures.getValue(it) }.toSet())
                require(required.all { (name, entry) -> target[impl.signatures.getValue(name)] == setOf(entry.typeid) }) {
                    "ULTRA_KERNEL_TASK_PROFILE_TARGET_CALLBACK_KCFI"
                }
            }
            buildJsonObject { put("mode", cfiMode); put("normalizeIntegers", impl.normalizeIntegers)
                put("callbackCount", 3)
                impl.legacyCfi?.let { put("legacyModuleChecker", it) } }
        }
        proof("syscallTracepoint") {
            require(enabled("CONFIG_HAVE_SYSCALL_TRACEPOINTS") && enabled("CONFIG_KRETPROBES") &&
                !enabled("CONFIG_PREEMPT_RT")) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_TRACE_CONFIG" }
            val trace = impl.trace.words
            val regs = field("pt_regs.__size"); val args = field("pt_regs.regs"); val number = field("pt_regs.syscallno")
            require(trace.size == 12 && trace[0] == 1 && trace[2] == 1 && trace[3] == regs.widthBytes &&
                trace[5] == args.offsetBytes && trace[6] == 8 && trace[7] == number.offsetBytes && trace[8] == number.widthBytes &&
                trace[11] == 0) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_REGS_LAYOUT" }
            val rawSymbols = symbols as? UltraKernelKallsymsTable
                ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_SYSCALL_SYMBOL_SOURCE")
            val syscallTable = symbols.offsetsOf("sys_call_table").singleOrNull()
                ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_SYSCALL_TABLE")
            val syscallEnd = symbols.nextOffset(syscallTable)
                ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_SYSCALL_TABLE_END")
            require(syscallTable >= 0 && syscallTable % 8 == 0 && syscallEnd <= kernel.size &&
                syscallEnd > syscallTable && syscallEnd - syscallTable <= 8192 &&
                trace[9] in 0 until (syscallEnd - syscallTable) / 8 && trace[10] in 0 until (syscallEnd - syscallTable) / 8) {
                "ULTRA_KERNEL_TASK_PROFILE_TARGET_SYSCALL_TABLE_RANGE"
            }
            for ((nr, function) in listOf(trace[9] to "__arm64_sys_execve", trace[10] to "__arm64_sys_execveat")) {
                val entry = symbols.offsetsOf(function).singleOrNull()
                    ?: error("ULTRA_KERNEL_TASK_PROFILE_TARGET_SYSCALL_FUNCTION:$function")
                val pointer = ByteBuffer.wrap(kernel, syscallTable + nr * 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
                require(pointer == rawSymbols.textBase + entry) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_SYSCALL_NUMBER:$function" }
            }
            val generic = enabled("CONFIG_GENERIC_ENTRY")
            val member = field(if (generic) "thread_info.syscall_work" else "thread_info.flags")
            val branch = UltraKernelTaskProfileCode.workBit(kernel, symbols, taskInfo.offsetBytes + member.offsetBytes, generic)
            workMember = branch.member; workBit = branch.bit
            val abi = impl.nativeAbi; val rwBytes = abi.getValue("rwlockBytes").jsonPrimitive.int
            val rwAlign = abi.getValue("rwlockAlignment").jsonPrimitive.int
            val tasks = field("task_struct.tasks"); val funcs = field("tracepoint.funcs")
            val callback = field("tracepoint_func.func"); val data = field("tracepoint_func.data")
            val handler = field("kretprobe.handler"); val entry = field("kretprobe.entry_handler"); val address = field("kretprobe.kp.addr")
            val maxactive = field("kretprobe.maxactive"); val missed = field("kretprobe.nmissed")
            require(tasks.widthBytes == 16 && listOf(funcs, callback, data, handler, entry, address).all { it.widthBytes == 8 && it.offsetBytes % 8 == 0 } &&
                maxactive.widthBytes == 4 && missed.widthBytes == 4) { "ULTRA_KERNEL_TASK_PROFILE_TARGET_MARKER_FIELDS" }
            markerWords = listOf(0x4d53544d, 1, 112, 1, field("task_struct.__size").widthBytes, tasks.offsetBytes, tasks.widthBytes,
                field("tracepoint.__size").widthBytes, funcs.offsetBytes, funcs.widthBytes,
                field("tracepoint_func.__size").widthBytes, callback.offsetBytes, callback.widthBytes, data.offsetBytes, data.widthBytes,
                field("kretprobe.__size").widthBytes, handler.offsetBytes, handler.widthBytes, address.offsetBytes, address.widthBytes,
                maxactive.offsetBytes, maxactive.widthBytes, missed.offsetBytes, missed.widthBytes,
                rwBytes, rwAlign, entry.offsetBytes, entry.widthBytes)
            buildJsonObject { put("workMember", if (generic) "syscall_work" else "flags"); put("workBit", workBit)
                put("dispatcher", branch.function); put("branchOffset", branch.branch); put("traceReference", branch.traceReference)
                put("functionSha256", branch.functionSha256); put("registrationPendingRuntimeOpen", true) }
        }
        proof("cloneExec") {
            require(field("task_struct.stack").widthBytes == 8 &&
                field("task_struct.stack").offsetBytes % 8 == 0 &&
                UltraKernelBtf.memberSignature(kernel, symbols, "task_struct", "stack") == "*v") {
                "ULTRA_KERNEL_TASK_PROFILE_TARGET_STACK_POINTER"
            }
            val paths = UltraKernelTaskProfileCode.prove(kernel, symbols, taskFlags, field("task_struct.__size").widthBytes, field("task_struct.stack").offsetBytes)
            buildJsonObject { put("scope", "actual-architecture-copy-and-exec-reset-paths")
                put("functions", json(paths.functions)); put("calls", JsonArray(paths.calls.map(::JsonPrimitive)))
                put("stores", JsonArray(paths.stores.map(::JsonPrimitive))) }
        }
        val document = buildJsonObject { put("schema", PROOF_SCHEMA); put("slot63Contract", SLOT_CONTRACT)
            put("targetSha256", sha); put("configSha256", configSha); put("implementationSourceSha256", impl.sourceSha256)
            put("proofs", JsonObject(evidence)); put("missing", json(missing)) }
        return Proof(sha, configSha, UltraKernelSpec.sha256(document.toString().toByteArray(Charsets.UTF_8)),
            workMember, workBit, markerWords, document, missing)
    }
}
