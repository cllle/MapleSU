package dev.maplesu.manager.ultrakernel

import dev.maplesu.manager.compat.StoredInputs
import kotlinx.serialization.json.*

/** Build-owned, position-independent recovery code packaged with the manager. */
internal data class UltraKernelManagerClaimPayload(
    val bootstrap: ByteArray,
    val materializer: ByteArray,
    val elf: ByteArray,
    val referenceAbi: ReferenceAbi? = null,
    val permissions: ByteArray? = null,
    val bootEntryOffset: Int? = null,
    val deploymentBootEntryOffset: Int? = null,
    val runtimeLayout: RuntimeLayoutFacts? = null,
    val runtimeProfiles: RuntimeProfilesFacts? = null,
    val sessionAbi: SessionAbiFacts? = null,
    val bootEventsAbi: BootEventsAbiFacts? = null,
    val ksuCompatAbi: KsuCompatAbiFacts? = null,
) {
    data class RuntimeLayoutField(val type: String, val member: String, val signature: String?, val alignment: Int,
        val key: String = "$type.$member") {
        fun targetKeys(): List<String> {
            val mountNamespace = key == "vfsmount.mnt_user_ns" && type == "vfsmount" &&
                member in setOf("mnt_user_ns", "mnt_userns")
            require(key == "$type.$member" || mountNamespace) { "ULTRA_KERNEL_RUNTIME_LAYOUT_MEMBER:$key" }
            return if (mountNamespace) listOf("vfsmount.mnt_user_ns", "vfsmount.mnt_userns") else listOf("$type.$member")
        }
    }
    data class RuntimeLayoutFacts(val offset: Int, val bytes: Int, val compiledSha256: String,
        val words: List<Int>, val fields: List<RuntimeLayoutField>)
    data class RuntimeProfileFilename(val offset: Int, val bytes: Int, val ownerBytes: Int,
        val signature: String, val alignment: Int)
    data class RuntimeProfilesFacts(val offset: Int, val bytes: Int, val compiledSha256: String,
        val words: List<Int>, val names: List<String>, val filename: RuntimeProfileFilename,
        val targetFacts: List<Int>, val execAbi: String, val userArgPtrBytes: Int, val flagsRegister: Int,
        val registersSignature: String)
    data class SessionCallback(val symbol: String, val member: String, val signature: String,
        val offset: Int, val kcfiPrefix: Int?, val objectKcfiType: Int?)
    data class SessionGid(val offset: Int, val elementBytes: Int, val alignment: Int, val signature: String)
    data class SessionAbiFacts(val offset: Int, val bytes: Int, val compiledSha256: String, val words: List<Int>,
        val groupInfo: UltraKernelResidentAbi.CompiledLayout, val groupAlignment: Int, val gid: SessionGid,
        val fileOperations: UltraKernelResidentAbi.CompiledLayout, val fileOperationsAlignment: Int,
        val callbacks: Map<String, SessionCallback>, val cfiMode: String, val normalizeIntegers: Boolean)
    data class BootEventsAbiFacts(val offset: Int, val bytes: Int, val compiledSha256: String, val words: List<Int>,
        val layouts: Map<String, UltraKernelResidentAbi.CompiledLayout>, val callbacks: Map<String, SessionCallback>,
        val cfiMode: String, val normalizeIntegers: Boolean)
    data class KsuCompatAbiFacts(val offset: Int, val bytes: Int, val compiledSha256: String, val words: List<Int>,
        val layouts: Map<String, UltraKernelResidentAbi.CompiledLayout>, val callbacks: Map<String, SessionCallback>,
        val cfiMode: String, val normalizeIntegers: Boolean)
    data class ReferenceAbi(val functions: Map<String, String>, val callbacks: Map<String, String>,
                            val bootstrapFunctions: Map<String, String>,
                            val dataImports: Set<String>, val cfi: CallbackCfi,
                            val dataSignatures: Map<String, String> = emptyMap(),
                            val management: UltraKernelResidentAbi.ManagementFacts? = null,
                            val managementLayoutSha256: String? = null,
                            val descriptor: DescriptorAbi? = null,
                            val credentialDomainSha256: String? = null,
                            val taskProfile: TaskProfileFacts? = null)
    data class TaskProfileFacts(val words: List<Int>, val layoutSha256: String,
        val verified: Boolean, val missing: Set<String>, val imports: Map<String, String>,
        val entries: Map<String, CallbackPrefix>, val cfiMode: String,
        val targetSha256: String?, val configSha256: String?, val config: Map<String, String>,
        val dispatcherAbi: Map<String, String>, val callbackEntries: Map<String, CallbackPrefix>,
        val implementation: UltraKernelTaskProfileTarget.Implementation? = null) {
        // flags=0 still executes the native marker stage.
        fun supports(flags: Long) = verified && flags and 1L.inv() == 0L
    }
    data class DescriptorAbi(val hostSymbolOffset: Int, val hostBytes: Int,
        val baseOffset: Int, val extendedOffset: Int, val descriptorBytes: Int,
        val sectionName: String, val sectionOffset: Int, val sectionBytes: Int, val sectionFlags: Long,
        val uidOffset: Int? = null, val procattrOffset: Int? = null, val taskOffset: Int? = null)
    data class CallbackCfi(val mode: String, val normalizeIntegers: Boolean,
                           val prefixes: Map<String, CallbackPrefix>)
    data class CallbackPrefix(val entry: Int, val typeid: Int)
    data class SourceQualification(val claimSha256: String, val configSha256: String)
    data class RetainedFunction(val section: String, val entry: Int, val bytes: Int, val typeid: Int?)
    companion object {
        private const val DIRECTORY = "ultra-manager-claim"
        internal val taskProfileFunctions = mapOf(
            "msu_task_profile_flags_supported" to "fi4:16777248(i8:64;)",
            "msu_task_profile_check_current" to "fi4:16777248(*fi4:16777248(*v;);*v;)",
            "msu_task_profile_apply_current" to "fi4:16777248(i8:64;*fi4:16777248(*v;);*v;*smsu_task_profile_receipt;)")
        internal val taskProfileLayoutNames = ("SCHEMA KERNEL_VERSION BUILD_VERIFIED NATIVE_ENABLED GENERIC_ENTRY " +
            "THREAD_INFO_IN_TASK TASK_SIZE TASK_ALIGN TASK_THREAD_INFO_OFFSET TASK_THREAD_INFO_BYTES " +
            "TASK_SIGNAL_OFFSET TASK_SIGNAL_BYTES THREAD_INFO_SIZE THREAD_INFO_ALIGN FLAGS_OFFSET FLAGS_BYTES " +
            "ROOT_BIT WORK_OFFSET WORK_BYTES WORK_BIT SIGNAL_SIZE SIGNAL_ALIGN THREAD_HEAD_OFFSET " +
            "THREAD_HEAD_BYTES THREAD_NODE_OFFSET THREAD_NODE_BYTES").split(' ').map { "MSU_TASK_PROFILE_$it" }

        internal fun parseTaskProfile(report: JsonObject, manifest: JsonObject, payload: JsonObject,
            reference: JsonObject, coreInputs: Map<String, JsonObject>, elf: ByteArray,
            mode: String, normalize: Boolean): TaskProfileFacts {
            fun identity(value: JsonElement?): JsonObject = (value?.jsonObject
                ?: error("ULTRA_KERNEL_TASK_PROFILE_IDENTITY")).also {
                val bytes = it["bytes"]?.jsonPrimitive?.longOrNull
                require(!it["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                    bytes != null && bytes > 0 &&
                    it["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                    "ULTRA_KERNEL_TASK_PROFILE_IDENTITY"
                }
            }
            fun dependencyIdentity(value: JsonElement): JsonObject = value.jsonObject.also {
                if (it["bytes"]?.jsonPrimitive?.longOrNull != 0L) identity(it) else {
                    val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                    require(!it["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                        it["sha256"]?.jsonPrimitive?.content == empty &&
                        it["archivePath"]?.jsonPrimitive?.content == "build-dependencies/files/$empty.bin") {
                        "ULTRA_KERNEL_TASK_PROFILE_DEPENDENCY_IDENTITY"
                    }
                }
            }
            fun same(a: JsonObject, b: JsonObject) = a["bytes"] == b["bytes"] && a["sha256"] == b["sha256"]
            fun selected(path: String, value: JsonElement?) {
                val actual = identity(value)
                val successor = report["implementation"]?.jsonObject?.get("sourceSuccessor")?.jsonObject
                    ?.get("files")?.jsonArray?.map { it.jsonObject }?.singleOrNull {
                        it["logicalPath"]?.jsonPrimitive?.content == path
                    }
                val chosen = successor?.let { identity(it["after"]) }
                    ?: coreInputs[path]?.let { identity(it["nativeCopyIdentity"] ?: it["after"]) }
                require(actual.getValue("path").jsonPrimitive.content.replace('\\', '/').substringAfterLast('/') == path.substringAfterLast('/') &&
                    if (chosen != null) same(actual, chosen) else manifest.getValue("inputs").jsonObject.entries.singleOrNull {
                        StoredInputs.sourcePath(it.key) == path
                    }?.value == actual["sha256"]) { "ULTRA_KERNEL_TASK_PROFILE_SELECTED_SOURCE:$path" }
            }
            require(report["schema"]?.jsonPrimitive?.content == "maplesu-task-profile-abi/1" &&
                report["claimSha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(elf)) {
                "ULTRA_KERNEL_TASK_PROFILE_SCHEMA"
            }
            for ((key, suffix) in mapOf("sourceIdentity" to "c", "headerIdentity" to "h"))
                selected("runtime/ultra/identity/kernel/manager_task_state.$suffix", report[key])
            val selection = identity(report["sourceSelectionIdentity"])
            require(same(selection, identity(reference.getValue("coreLifecycleAbi").jsonObject
                .getValue("sourceSelection").jsonObject["identity"]))) { "ULTRA_KERNEL_TASK_PROFILE_SELECTION" }
            val dependencies = report.getValue("dependencies").jsonArray.map(::dependencyIdentity)
            require(dependencies.isNotEmpty() && listOf("sourceIdentity", "headerIdentity", "configIdentity").all { key ->
                dependencies.any { same(it, identity(report[key])) }
            }) { "ULTRA_KERNEL_TASK_PROFILE_DEPENDENCIES" }
            for (key in listOf("objectIdentity", "commandIdentity", "dependencyReceiptIdentity")) identity(report[key])
            val words = report.getValue("layoutValues").jsonArray.map {
                val word = it.jsonPrimitive.long
                require(word in Int.MIN_VALUE.toLong()..0xffffffffL) { "ULTRA_KERNEL_TASK_PROFILE_WORD" }
                word.toInt()
            }
            require(report["layoutWords"]?.jsonPrimitive?.intOrNull == 26 && words.size == 26 && words[0] == 1 &&
                report.getValue("layoutNames").jsonArray.map { it.jsonPrimitive.content } == taskProfileLayoutNames &&
                report["symbol"]?.jsonPrimitive?.content == "msu_task_profile_compiled_layout" &&
                report["section"]?.jsonPrimitive?.content == ".msu.task_profile.layout" &&
                (2..5).all { words[it] in 0..1 } && words[16] == 63 && words[19] in 0..62) {
                "ULTRA_KERNEL_TASK_PROFILE_LAYOUT"
            }
            val bytes = java.nio.ByteBuffer.allocate(104).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .apply { words.forEach { putInt(it) } }.array()
            val digest = UltraKernelSpec.sha256(bytes)
            val exported = payload.getValue("compiledLayouts").jsonObject.getValue("msu_task_profile_compiled_layout").jsonObject
            require(report["compiledLayoutSha256"]?.jsonPrimitive?.content == digest &&
                exported["bytes"]?.jsonPrimitive?.intOrNull == 104 && report["exportOffset"] == exported["offset"] &&
                exported.getValue("values").jsonArray.map {
                    val word = it.jsonPrimitive.long
                    require(word in Int.MIN_VALUE.toLong()..0xffffffffL) { "ULTRA_KERNEL_TASK_PROFILE_WORD" }
                    word.toInt()
                } == words) {
                "ULTRA_KERNEL_TASK_PROFILE_LAYOUT_IDENTITY"
            }
            val config = report.getValue("config").jsonObject
            require(config.keys.containsAll(setOf("CONFIG_64BIT", "CONFIG_THREAD_INFO_IN_TASK", "CONFIG_GENERIC_ENTRY")) &&
                config.values.all { it.jsonPrimitive.content in setOf("y", "n") } &&
                (words[4] == 1) == (config.getValue("CONFIG_GENERIC_ENTRY").jsonPrimitive.content == "y") &&
                (words[5] == 1) == (config.getValue("CONFIG_THREAD_INFO_IN_TASK").jsonPrimitive.content == "y")) {
                "ULTRA_KERNEL_TASK_PROFILE_CONFIG"
            }
            val verified = words[2] == 1
            val implementationReport = report["implementation"]?.jsonObject
            if (implementationReport == null) {
                require((words[3] == 1) == (verified && words[5] == 1 && config.getValue("CONFIG_64BIT").jsonPrimitive.content == "y")) {
                    "ULTRA_KERNEL_TASK_PROFILE_NATIVE"
                }
            } else {
                require(!verified && report["qualificationStage"]?.jsonPrimitive?.content == "per-input-image-binding" &&
                    report["implementationPresent"]?.jsonPrimitive?.booleanOrNull == (words[3] == 1) &&
                    (words[3] == 1) == (words[5] == 1 && listOf("CONFIG_ARM64", "CONFIG_64BIT",
                        "CONFIG_HAVE_SYSCALL_TRACEPOINTS").all { config[it]?.jsonPrimitive?.content == "y" })) {
                    "ULTRA_KERNEL_TASK_PROFILE_NATIVE_IMPLEMENTATION"
                }
            }
            fun field(offset: Int, size: Int, owner: Int) = offset >= 0 && size > 0 && offset.toLong() + size <= owner
            require(words[6] > 0 && words[12] > 0 && words[20] > 0 &&
                listOf(7, 13, 21).all { words[it] > 0 && words[it] and (words[it] - 1) == 0 } &&
                field(words[10], words[11], words[6]) && field(words[14], words[15], words[12]) &&
                field(words[17], words[18], words[12]) && field(words[22], words[23], words[20]) &&
                field(words[24], words[25], words[6]) &&
                (if (words[5] == 1) words[9] == words[12] && field(words[8], words[9], words[6]) else words[8] == -1 && words[9] == 0) &&
                (!verified || words[3] == 1 && words[15] == 8 && words[18] == 8)) { "ULTRA_KERNEL_TASK_PROFILE_FIELDS" }
            val apis = report.getValue("apis").jsonObject
            require(apis.keys == taskProfileFunctions.keys) { "ULTRA_KERNEL_TASK_PROFILE_APIS" }
            val entries = apis.mapValues { (name, value) ->
                val api = value.jsonObject
                require(api["signature"]?.jsonPrimitive?.content == taskProfileFunctions.getValue(name)) {
                    "ULTRA_KERNEL_TASK_PROFILE_API_TYPE:$name"
                }
                val callers = api.getValue("callers").jsonArray
                require(callers.isNotEmpty()) { "ULTRA_KERNEL_TASK_PROFILE_CALLERS:$name" }
                callers.forEach { caller ->
                    val call = caller.jsonObject
                    selected(call.getValue("logicalPath").jsonPrimitive.content, call["sourceIdentity"])
                    require(same(identity(call["headerIdentity"]), identity(report["headerIdentity"])) &&
                        call["signature"] == api["signature"] &&
                        name in call.getValue("undefinedSymbols").jsonArray.map { it.jsonPrimitive.content }) {
                        "ULTRA_KERNEL_TASK_PROFILE_DECLARATION:$name"
                    }
                    for (key in listOf("objectIdentity", "commandIdentity", "dependencyReceiptIdentity")) identity(call[key])
                    require(call["cfiMode"]?.jsonPrimitive?.content == mode &&
                        call["normalizeIntegers"]?.jsonPrimitive?.booleanOrNull == normalize &&
                        (mode != "kcfi" || call["typeid"] == api["typeid"])) { "ULTRA_KERNEL_TASK_PROFILE_CALLER_CFI:$name" }
                }
                val typeid = if (mode == "kcfi") api.getValue("typeid").jsonPrimitive.long.also {
                    require(it in 0..0xffffffffL) { "ULTRA_KERNEL_TASK_PROFILE_TYPEID:$name" }
                }.toInt() else 0
                CallbackPrefix(api.getValue("entryOffset").jsonPrimitive.int, typeid)
            }
            val imports = report.getValue("nativeImports").jsonObject.mapValues { it.value.jsonPrimitive.content }
            require(imports.all { (name, signature) -> reference.getValue("functionAbi").jsonObject[name]?.jsonPrimitive?.content == signature }) {
                "ULTRA_KERNEL_TASK_PROFILE_IMPORTS"
            }
            val proofs = report.getValue("qualification").jsonObject
            val required = setOf("slot63", "rcu", "bitops", "cfi", "syscallTracepoint", "cloneExec")
            require(required.containsAll(proofs.keys)) { "ULTRA_KERNEL_TASK_PROFILE_PROOFS" }
            val target = report["targetSha256"]?.jsonPrimitive?.content
            val missing = required.filter { key ->
                val proof = proofs[key]?.jsonObject ?: return@filter true
                if (proof["verified"]?.jsonPrimitive?.booleanOrNull != true) return@filter true
                require(target?.matches(Regex("[0-9a-f]{64}")) == true &&
                    proof["targetSha256"]?.jsonPrimitive?.content == target &&
                    proof["sourceSelectionIdentity"] == selection && proof["configIdentity"] == report["configIdentity"] &&
                    proof.getValue("dependencies").jsonArray.isNotEmpty() &&
                    proof.getValue("dependencies").jsonArray.all { dep -> dependencies.any { same(it, identity(dep)) } }) {
                    "ULTRA_KERNEL_TASK_PROFILE_PROOF_BINDING:$key"
                }
                false
            }.toSet()
            require(report.getValue("missing").jsonArray.map { it.jsonPrimitive.content }.toSet() == missing &&
                (!verified || missing.isEmpty())) { "ULTRA_KERNEL_TASK_PROFILE_QUALIFICATION_MISSING" }
            val dispatcherAbi = linkedMapOf<String, String>()
            val callbackEntries = linkedMapOf<String, CallbackPrefix>()
            if (verified) {
                val slot = proofs.getValue("slot63").jsonObject
                val occupied = slot.getValue("occupiedBits").jsonArray.map { it.jsonPrimitive.int }
                require(slot["reservedBit"]?.jsonPrimitive?.intOrNull == 63 &&
                    slot["reservedFor"]?.jsonPrimitive?.content == "MSU_TASK_PROFILE_DISABLE_ROOT_BIT" &&
                    slot["inventoryComplete"]?.jsonPrimitive?.booleanOrNull == true &&
                    slot["reservationEnforced"]?.jsonPrimitive?.booleanOrNull == true &&
                    occupied.isNotEmpty() && occupied.distinct().size == occupied.size && occupied.all { it in 0..62 }) {
                    "ULTRA_KERNEL_TASK_PROFILE_SLOT63"
                }
                val bitops = proofs.getValue("bitops").jsonObject
                require(bitops["wordBits"]?.jsonPrimitive?.intOrNull == 64 &&
                    bitops.getValue("operations").jsonArray.map { it.jsonPrimitive.content }.toSet()
                        .containsAll(setOf("test_bit", "test_and_set_bit"))) { "ULTRA_KERNEL_TASK_PROFILE_BITOPS" }
                val trace = proofs.getValue("syscallTracepoint").jsonObject
                require(trace["workMember"]?.jsonPrimitive?.content == (if (words[4] == 1) "syscall_work" else "flags") &&
                    trace["workBit"]?.jsonPrimitive?.intOrNull == words[19] &&
                    trace["dispatcherRegistered"]?.jsonPrimitive?.booleanOrNull == true &&
                    trace["callbackBound"]?.jsonPrimitive?.booleanOrNull == true &&
                    !trace["dispatcher"]?.jsonPrimitive?.content.isNullOrBlank() &&
                    !trace["callback"]?.jsonPrimitive?.content.isNullOrBlank()) { "ULTRA_KERNEL_TASK_PROFILE_TRACEPOINT" }
                identity(trace["registrationIdentity"])
                val dispatcher = trace.getValue("dispatcher").jsonPrimitive.content
                val signature = trace.getValue("dispatcherSignature").jsonPrimitive.content
                require(signature.startsWith("f") && signature.length <= 65536) { "ULTRA_KERNEL_TASK_PROFILE_DISPATCHER_TYPE" }
                dispatcherAbi[dispatcher] = signature
                val validators = proofs.getValue("cfi").jsonObject.getValue("validators").jsonObject
                require(validators.isNotEmpty()) { "ULTRA_KERNEL_TASK_PROFILE_VALIDATORS" }
                for ((name, value) in validators + (trace.getValue("callback").jsonPrimitive.content to trace.getValue("callbackEntry"))) {
                    val callback = value.jsonObject
                    selected(callback.getValue("logicalPath").jsonPrimitive.content, callback["sourceIdentity"])
                    for (key in listOf("objectIdentity", "commandIdentity", "dependencyReceiptIdentity")) identity(callback[key])
                    require(callback["signature"] == callback["declarationSignature"] &&
                        callback.getValue("signature").jsonPrimitive.content.startsWith("*f") &&
                        (name !in validators || callback["signature"]?.jsonPrimitive?.content == "*fi4:16777248(*v;)") &&
                        (mode != "kcfi" || callback["typeid"] == callback["declarationTypeid"])) {
                        "ULTRA_KERNEL_TASK_PROFILE_CALLBACK_TYPE:$name"
                    }
                    val typeid = if (mode == "kcfi") callback.getValue("typeid").jsonPrimitive.long.also {
                        require(it in 0..0xffffffffL) { "ULTRA_KERNEL_TASK_PROFILE_TYPEID:$name" }
                    }.toInt() else 0
                    callbackEntries[name] = CallbackPrefix(callback.getValue("entryOffset").jsonPrimitive.int, typeid)
                }
                require(proofs.getValue("rcu").jsonObject["embeddedThreadInfo"]?.jsonPrimitive?.booleanOrNull == true &&
                    proofs.getValue("cfi").jsonObject["mode"]?.jsonPrimitive?.content == mode &&
                    proofs.getValue("cfi").jsonObject["normalizeIntegers"]?.jsonPrimitive?.booleanOrNull == normalize &&
                    proofs.getValue("cloneExec").jsonObject["execPreserves63"]?.jsonPrimitive?.booleanOrNull == true &&
                    proofs.getValue("cloneExec").jsonObject["forkFromCallingTask"]?.jsonPrimitive?.booleanOrNull == true) {
                    "ULTRA_KERNEL_TASK_PROFILE_NATIVE_PROOF"
                }
            }
            val implementation = implementationReport?.let {
                UltraKernelTaskProfileTarget.parseImplementation(it, report, reference, coreInputs, elf, mode, normalize)
            }
            return TaskProfileFacts(words, digest, verified, missing, imports, entries, mode, target,
                identity(report["configIdentity"])["sha256"]?.jsonPrimitive?.content,
                config.mapValues { it.value.jsonPrimitive.content }, dispatcherAbi, callbackEntries, implementation)
        }
        private val families = mapOf(
            "5.10" to "android12-5.10", "5.15" to "android13-5.15",
            "6.1" to "android14-6.1", "6.6" to "android15-6.6",
            "6.12" to "android16-6.12", "6.18" to "android17-6.18",
        )

        private fun verifyCoreLifecycle(report: JsonObject, manifest: JsonObject, payload: JsonObject,
                                        reference: JsonObject, elf: ByteArray, mode: String, normalize: Boolean): Map<String, JsonObject> {
            fun identity(value: JsonElement?): JsonObject {
                val item = value?.jsonObject ?: error("ULTRA_KERNEL_CORE_SOURCE_IDENTITY")
                require(!item["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                    (item["bytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0 &&
                    item["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                    "ULTRA_KERNEL_CORE_SOURCE_IDENTITY"
                }
                return item
            }
            fun same(a: JsonObject, b: JsonObject) = a["bytes"] == b["bytes"] && a["sha256"] == b["sha256"]
            fun flag(owner: JsonObject, key: String) = owner.getValue(key).jsonPrimitive.boolean
            fun path(item: JsonObject) = item.getValue("path").jsonPrimitive.content.replace('\\', '/')
            fun selected(value: JsonElement?): JsonObject = identity(value).also { item ->
                val matches = manifest.getValue("inputs").jsonObject.entries.filter {
                    path(item) == it.key.replace('\\', '/') || path(item).endsWith("/" + it.key.replace('\\', '/'))
                }
                val longest = matches.maxOfOrNull { it.key.replace('\\', '/').length }
                require(matches.singleOrNull { it.key.replace('\\', '/').length == longest }?.value == item["sha256"]) {
                    "ULTRA_KERNEL_CORE_SELECTED_SOURCE"
                }
            }
            fun layouts(value: JsonElement?): Map<String, UltraKernelResidentAbi.CompiledLayout> = value?.jsonObject?.mapValues { (_, entry) ->
                val item = entry.jsonObject
                UltraKernelResidentAbi.CompiledLayout(item.getValue("bytes").jsonPrimitive.int,
                    item.getValue("signature").jsonPrimitive.content, item.getValue("members").jsonObject.mapValues { (_, entry) ->
                        val field = entry.jsonObject
                        UltraKernelResidentAbi.CompiledMember(field.getValue("offset").jsonPrimitive.int,
                            field.getValue("bytes").jsonPrimitive.int, field.getValue("signature").jsonPrimitive.content)
                    })
            }.orEmpty()
            require(report["schema"]?.jsonPrimitive?.content == "maplesu-core-lifecycle-abi/1" &&
                report["schemaVersion"]?.jsonPrimitive?.intOrNull == 1 &&
                listOf("metadataComplete", "targetQualified", "taskStateQualified", "profileDomainQualified", "factoryBound", "lifecycleQualified")
                    .all { !flag(report, it) }) { "ULTRA_KERNEL_CORE_QUALIFICATION_UNBOUND" }
            val complete = flag(report, "kernelMetadataComplete")
            val evolution = reference.getValue("managementAbi").jsonObject.getValue("upstreamSourceFreeze").jsonObject
                .getValue("coreLifecycleEvolution").jsonObject
            require(evolution["schema"]?.jsonPrimitive?.content == "maplesu-core-lifecycle-source-evolution/1" &&
                !flag(evolution, "productionWritten")) { "ULTRA_KERNEL_CORE_SOURCE_EVOLUTION" }
            for (key in listOf("selectionIdentity", "freezeIdentity", "baselineIdentity", "changesetIdentity", "parentFreezeIdentity"))
                selected(evolution[key])
            val owner = report.getValue("sourceSelection").jsonObject
            require(flag(owner, "sourceOwnerFrozen") && !flag(owner, "productionWritten") &&
                selected(owner["identity"]) == selected(evolution["selectionIdentity"]) &&
                selected(owner["freezeIdentity"]) == selected(evolution["freezeIdentity"]) &&
                selected(owner["parentFreezeIdentity"]) == selected(evolution["parentFreezeIdentity"])) { "ULTRA_KERNEL_CORE_OWNER_SOURCE" }
            fun sourceRolePath(actual: String, expected: String) = actual == expected ||
                actual.startsWith(".tmp/") && expected.endsWith("/$actual")
            fun historicalCore(value: JsonElement?, logical: String): JsonObject {
                val item = identity(value)
                val root = path(selected(owner["identity"])).substringBeforeLast('/')
                val archive = "$root/archive/source/$logical"
                val original = if (logical.startsWith("runtime/ultra/identity/kernel/"))
                    "$root/selected-native/${logical.substringAfterLast('/')}" else archive
                require(sourceRolePath(path(item), original)) { "ULTRA_KERNEL_CORE_HISTORICAL_SOURCE_ROLE:$logical" }
                selected(JsonObject(item + ("path" to JsonPrimitive(archive))))
                return item
            }
            val runtimeEvolution = reference.getValue("managementAbi").jsonObject.getValue("upstreamSourceFreeze")
                .jsonObject["runtimeLayoutEvolution"]?.jsonObject
            val runtimeRows = runtimeEvolution?.let { runtime ->
                require(runtime["schema"]?.jsonPrimitive?.content == "maplesu-claim-runtime-layout-source-selection/1" &&
                    runtime["sourceOwnerFrozen"]?.jsonPrimitive?.booleanOrNull == true &&
                    runtime["targetQualified"]?.jsonPrimitive?.booleanOrNull == false &&
                    same(selected(runtime["identity"]), selected(owner["runtimeLayoutSourceSelection"])) &&
                    same(selected(runtime["identity"]), selected(evolution["runtimeLayoutSourceSelection"]))) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCE_EVOLUTION"
                }
                val rows = runtime.getValue("files").jsonArray.map { it.jsonObject }
                val mapped = rows.associateBy { it.getValue("logicalPath").jsonPrimitive.content }
                require(rows.isNotEmpty() && mapped.size == rows.size) { "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCE_ROWS" }
                for ((logical, row) in mapped) {
                    require(path(selected(row["after"])).endsWith("/" + logical.substringAfterLast('/'))) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_SELECTED_SOURCE"
                    }
                    row["before"]?.takeUnless { it is JsonNull }?.let(::selected)
                }
                val predecessors = runtime["predecessorInputs"] as? JsonObject
                    ?: error("ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_FIELDS")
                require(predecessors.keys == setOf("native", "support", "private", "su", "protocol", "frame")) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_FIELDS"
                }
                for (key in listOf("native", "support", "private", "su")) {
                    val values = predecessors[key] as? JsonArray
                        ?: error("ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_TYPE:$key")
                    values.forEach { selected(it) }
                }
                for (key in listOf("protocol", "frame")) {
                    val value = predecessors[key] as? JsonObject
                        ?: error("ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_TYPE:$key")
                    selected(value)
                }
                val predecessorFiles = listOf("native", "support", "private", "su").flatMap {
                    predecessors.getValue(it).jsonArray.map { entry -> selected(entry) }
                } + listOf(selected(predecessors["protocol"]), selected(predecessors["frame"]))
                for ((logical, row) in mapped) row["before"]?.takeUnless { it is JsonNull }?.let { value ->
                    val before = selected(value)
                    require(predecessorFiles.any { path(it) == path(before) && same(it, before) }) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_EDGE:$logical"
                    }
                }
                mapped
            }.orEmpty()
            require(runtimeEvolution != null || owner["runtimeLayoutSourceSelection"] == null &&
                evolution["runtimeLayoutSourceSelection"] == null) { "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCE_UNEXPECTED" }
            val ownerRevision = owner["lifecycleRevision"]?.jsonPrimitive?.intOrNull
            val evolutionRevision = evolution["lifecycleRevision"]?.jsonPrimitive?.intOrNull
            val lifecycleRevision = ownerRevision ?: 1
            val sourceFileCount = owner["sourceFileCount"]?.jsonPrimitive?.intOrNull
            val deploymentChannel = lifecycleRevision == 2 && sourceFileCount == 54
            val previousFreeze = "d1fcaf09895cbfea764457c7b946fd05165fabd249bd4e131309f9629c0b8cd1"
            require((owner["lifecycleRevision"] == null || ownerRevision != null) &&
                (evolution["lifecycleRevision"] == null || evolutionRevision != null) &&
                (ownerRevision == null) == (evolutionRevision == null) && lifecycleRevision in 1..2 &&
                (ownerRevision == null || ownerRevision == evolutionRevision) &&
                (lifecycleRevision == 2 ||
                    owner.getValue("freezeIdentity").jsonObject["sha256"]?.jsonPrimitive?.content == previousFreeze &&
                    owner.getValue("freezeIdentity").jsonObject["bytes"]?.jsonPrimitive?.intOrNull == 10733) &&
                (if (ownerRevision == null) owner["sourceFileCount"] == null else
                    if (lifecycleRevision == 2) sourceFileCount in setOf(48, 54) else sourceFileCount == 47)) {
                "ULTRA_KERNEL_CORE_SOURCE_REVISION"
            }
            val runtimeSuccessor = owner["runtimeSuccessor"]?.jsonObject
            val coreRuntimeEvolution = reference.getValue("managementAbi").jsonObject.getValue("upstreamSourceFreeze")
                .jsonObject["coreRuntimeEvolution"]?.jsonObject
            require(runtimeSuccessor == coreRuntimeEvolution) { "ULTRA_KERNEL_CORE_RUNTIME_SOURCE" }
            runtimeSuccessor?.let { successor ->
                require(lifecycleRevision == 2 && successor["schema"]?.jsonPrimitive?.content == "maplesu-core-runtime-source-evolution/1" &&
                    !flag(successor, "productionWritten") && !flag(successor, "targetQualified") &&
                    same(selected(successor["parentCoreSelection"]), selected(owner["identity"]))) {
                    "ULTRA_KERNEL_CORE_RUNTIME_SOURCE"
                }
                selected(successor["identity"])
            }
            for (key in listOf("previousCoreFreezeIdentity", "previousCoreSelectionIdentity")) {
                if (lifecycleRevision == 2) {
                    val previous = selected(owner[key])
                    require(previous == selected(evolution[key])) {
                        "ULTRA_KERNEL_CORE_PREVIOUS_SOURCE:$key"
                    }
                } else require(owner[key] == null && evolution[key] == null) { "ULTRA_KERNEL_CORE_PREVIOUS_SOURCE_UNEXPECTED" }
            }
            val rows = evolution.getValue("selectedInputs").jsonArray.map { it.jsonObject }
            val expectedPaths = setOf(
                "runtime/maplesu-sdk/include/kernel_module_kit_umbrella.h",
                "runtime/maplesu-sdk/include/module_err_def.h",
                "runtime/maplesu-sdk/sdk/include/maplesu_request_identity.h",
                "runtime/maplesu-sdk/sdk/include/maplesu_request_private.h",
                "runtime/maplesu-sdk/sdk/include/maplesu_root_command_private.h",
                "runtime/maplesu-sdk/sdk/include/maplesu_root_private.h",
                "runtime/maplesu-sdk/sdk/include/module_base_kernel_request.h",
                "runtime/maplesu-sdk/sdk/manifest/0023_module_base_kernel_request.o.json",
                "runtime/maplesu-sdk/sdk/manifest/0024_module_base_kernel_mem.o.json",
                "runtime/maplesu-sdk/sdk/manifest/0025_module_base_kernel_func_executor.o.json",
                "runtime/maplesu-sdk/sdk/manifest/0026_module_base_kernel_symbol_kaddr.o.json",
                "runtime/maplesu-sdk/sdk/manifest/0029_module_base_kernel_func_hook.o.json",
                "runtime/maplesu-sdk/sdk/manifest/0060_maplesu_root.o.json",
                "runtime/maplesu-sdk/sdk/manifest/0070_module_root.o.json",
                "runtime/maplesu-sdk/sdk/src/func_hook/module_base_kernel_func_hook.cpp",
                "runtime/maplesu-sdk/sdk/src/func_hook/owner_hook_publication.h",
                "runtime/maplesu-sdk/sdk/src/kernel_mem/module_base_kernel_mem.cpp",
                "runtime/maplesu-sdk/sdk/src/kernel_request/module_base_kernel_request.cpp",
                "runtime/maplesu-sdk/sdk/src/maplesu_core/maplesu_root.cpp",
                "runtime/maplesu-sdk/sdk/src/maplesu_core/maplesu_root_private.h",
                "runtime/maplesu-sdk/sdk/src/maplesu_core/miss_a_native_private.h",
                "runtime/maplesu-sdk/sdk/src/module/idx070/cgroup_v1_tasks_self.h",
                "runtime/maplesu-sdk/sdk/src/module/idx070/cgroup_v2_self.h",
                "runtime/maplesu-sdk/sdk/src/module/idx070/module_root.cpp",
                "runtime/maplesu-sdk/sdk/src/module/idx070/selinux_procattr_util.h",
                "runtime/maplesu-sdk/sdk/src/module_base/executor_restore.cpp",
                "runtime/maplesu-sdk/sdk/src/module_base/module_base_kernel_func_executor.cpp",
                "runtime/maplesu-sdk/sdk/src/module_base/module_base_kernel_symbol_kaddr.cpp",
                "runtime/ultra/authorization/execution_frame.h",
                "runtime/ultra/authorization/kernel_read.cpp",
                "runtime/ultra/authorization/kernel_read_metadata.h",
                "runtime/ultra/authorization/request_context.cpp",
                "runtime/ultra/authorization/request_protocol.h",
                "runtime/ultra/bridge/installed_helper_readback.cpp",
                "runtime/ultra/identity/kernel/manager_credential_domain.c",
                "runtime/ultra/identity/kernel/manager_credential_domain.h",
                "runtime/ultra/identity/kernel/manager_credential_profile.h",
                "runtime/ultra/identity/kernel/manager_management.c",
                "runtime/ultra/identity/kernel/manager_management.h",
                "runtime/ultra/identity/kernel/manager_owner.h",
                "runtime/ultra/identity/kernel/manager_resident_host.c",
                "runtime/ultra/identity/kernel/manager_task_state.c",
                "runtime/ultra/identity/kernel/manager_task_state.h",
                "runtime/ultra/identity/kernel/manager_uid_gateway.c",
                "runtime/ultra/identity/kernel/manager_uid_gateway.h",
                "runtime/ultra/identity/kernel/manager_uid_snapshot.c",
                "runtime/ultra/identity/kernel/manager_uid_snapshot.h") +
                (if (lifecycleRevision == 2) setOf("runtime/maplesu-sdk/sdk/src/func_hook/owner_publication_gate.h") else emptySet()) +
                (if (deploymentChannel) setOf(
                    "runtime/ultra/identity/kernel/manager_claim_bootstrap.c",
                    "runtime/ultra/identity/kernel/manager_claim_bootstrap.h",
                    "runtime/ultra/identity/kernel/manager_claim_loader.h",
                    "runtime/ultra/identity/kernel/manager_deployment_namespace.h",
                    "runtime/ultra/identity/kernel/manager_resident_abi.h",
                    "runtime/ultra/identity/kernel/manager_resident_host.h") else emptySet())
            val taskSuccessor = reference["taskProfileAbi"]?.jsonObject?.get("implementation")?.jsonObject
                ?.get("sourceSuccessor")?.jsonObject
            val taskRows = taskSuccessor?.get("files")?.jsonArray?.map { it.jsonObject }
                ?.associateBy { it.getValue("logicalPath").jsonPrimitive.content }.orEmpty()
            require((evolution["taskProfileSuccessor"] != null) == (taskSuccessor != null)) {
                "ULTRA_KERNEL_TASK_PROFILE_CORE_SUCCESSOR_REFERENCE"
            }
            if (taskSuccessor != null) require(
                same(selected(evolution["taskProfileSuccessor"]), selected(taskSuccessor["identity"])) &&
                same(selected(taskSuccessor["parentRuntimeLayoutSelection"]), selected(runtimeEvolution?.get("identity")))) {
                "ULTRA_KERNEL_TASK_PROFILE_CORE_SUCCESSOR_IDENTITY"
            }
            val sources = rows.associateBy { it.getValue("path").jsonPrimitive.content }
            require(sources.size == rows.size && sources.keys == expectedPaths) { "ULTRA_KERNEL_CORE_SOURCE_INPUTS" }
            val runtimePredecessors = if (runtimeEvolution == null) emptyMap() else {
                val freeze = reference.getValue("managementAbi").jsonObject.getValue("upstreamSourceFreeze").jsonObject
                val layers = runtimeEvolution.getValue("selectedLayers").jsonArray.map { it.jsonObject }
                val selectors = listOf("select_core_sources", "select_su_component_sources", "select_host_deployment_sources",
                    "select_core_runtime_sources", "select_su_component_metadata_sources", "select_host_durable_sources",
                    "select_host_initialization_sources", "select_core_current_task_sources", "select_host_publication_sources",
                    "select_host_sync_sources", "select_host_retirement_sources")
                require(layers.map { it["selector"]?.jsonPrimitive?.content } == selectors) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_LAYER_ORDER"
                }
                val layerOwners = listOf(owner["identity"], reference.getValue("suProviderAbi").jsonObject
                    .getValue("sourceSelection").jsonObject["identity"], freeze.getValue("hostDeploymentEvolution").jsonObject["identity"],
                    freeze.getValue("coreRuntimeEvolution").jsonObject["identity"], freeze.getValue("suProfileMetadataEvolution").jsonObject["identity"],
                    freeze.getValue("hostDurableEvolution").jsonObject["identity"], null,
                    freeze.getValue("sdkCurrentTaskEvolution").jsonObject["identity"], freeze.getValue("hostPublicationEvolution").jsonObject["identity"],
                    freeze.getValue("hostSyncEvolution").jsonObject["identity"], freeze.getValue("hostRetirementEvolution").jsonObject["identity"])
                layers.forEachIndexed { index, layer ->
                    val actual = selected(layer["selection"])
                    layerOwners[index]?.let { require(same(actual, selected(it))) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_LAYER_IDENTITY:${selectors[index]}"
                    } }
                }
                val current = sources.filterKeys { it in runtimeRows }.mapValues { (logical, row) ->
                    historicalCore(row["previousRuntimeLayoutInput"], logical)
                }.toMutableMap()
                fun stage(name: String, schema: String, key: String, count: Int): Pair<JsonObject, Map<String, JsonObject>> {
                    val value = freeze.getValue(name).jsonObject
                    require(value["schema"]?.jsonPrimitive?.content == schema &&
                        value["productionWritten"]?.jsonPrimitive?.booleanOrNull == false &&
                        (value["targetQualified"] == null || value["targetQualified"]?.jsonPrimitive?.booleanOrNull == false)) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE:$name"
                    }
                    selected(value["identity"])
                    val entries = value.getValue(key).jsonArray.map { it.jsonObject }
                    val mapped = entries.associateBy { it.getValue("logicalPath").jsonPrimitive.content }
                    require(mapped.size == count && entries.size == count) { "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_ROWS:$name" }
                    return value to mapped
                }
                fun advance(name: String, entries: Map<String, JsonObject>, beforeKey: String = "before", afterKey: String = "after") {
                    for ((logical, entry) in entries) {
                        val before = entry[beforeKey]?.takeUnless { it is JsonNull }?.let(::selected)
                        val after = selected(entry[afterKey])
                        require(path(after).substringAfterLast('/') == logical.substringAfterLast('/')) {
                            "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_PATH:$name:$logical"
                        }
                        current[logical]?.let { prior ->
                            require(before != null && same(prior, before)) { "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_EDGE:$name:$logical" }
                            current[logical] = after
                        }
                    }
                }
                val (host, hostRows) = stage("hostDeploymentEvolution", "maplesu-host-deployment-source-evolution/1", "selectedInputs", 25)
                require(same(selected(host["corePredecessor"]), selected(owner["identity"]))) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_HOST_PARENT"
                }
                advance("hostDeploymentEvolution", hostRows)
                val protocol = host.getValue("protocolInput").jsonObject
                require(protocol["logicalPath"]?.jsonPrimitive?.content == "runtime/ultra/authorization/request_protocol.h") {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_HOST_PROTOCOL"
                }
                advance("hostDeploymentEvolution.protocolInput", mapOf("runtime/ultra/authorization/request_protocol.h" to protocol))
                val (coreRuntime, coreRuntimeRows) = stage("coreRuntimeEvolution", "maplesu-core-runtime-source-evolution/1", "selectedInputs", 17)
                require(coreRuntime == owner["runtimeSuccessor"] &&
                    same(selected(coreRuntime["parentCoreSelection"]), selected(owner["identity"])) &&
                    same(selected(coreRuntime["parentHostSelection"]), selected(host["identity"]))) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_CORE_RUNTIME_PARENT"
                }
                for (key in listOf("parentCoreAbi", "core73Selection", "core73Freeze")) selected(coreRuntime[key])
                for ((logical, entry) in coreRuntimeRows) {
                    require(entry.keys == setOf("logicalPath", "kind", "rootCurrentRole", "rootCurrent",
                        "coreSelectedBefore", "coreFrozenAfter", "mergedAfter", "ownershipRule")) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_CORE_RUNTIME_ROLE_FIELDS:$logical"
                    }
                    val root = entry["rootCurrent"]?.takeUnless { it is JsonNull }?.let(::selected)
                    val before = entry["coreSelectedBefore"]?.takeUnless { it is JsonNull }?.let(::selected)
                    val frozen = selected(entry["coreFrozenAfter"])
                    val merged = selected(entry["mergedAfter"])
                    require((root == null) == (before == null) &&
                        (logical.startsWith("runtime/ultra/identity/kernel/") || same(frozen, merged))) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_CORE_RUNTIME_ROLE:$logical"
                    }
                }
                advance("coreRuntimeEvolution", coreRuntimeRows, "rootCurrent", "mergedAfter")
                val (currentTask, currentTaskRows) = stage("sdkCurrentTaskEvolution", "maplesu-host-current-task-source-successor/1", "files", 9)
                val taskRoot = path(selected(currentTask["identity"])).substringBeforeLast('/')
                val taskOwner = selected(currentTask["ownerSelection"])
                val taskOwnerRoot = path(taskOwner).substringBeforeLast('/')
                val ownerArchive = selected(currentTask.getValue("receipts").jsonObject["SOURCE-SELECTION.json"])
                require(same(taskOwner, ownerArchive) && path(ownerArchive) == "$taskRoot/archive/receipts/SOURCE-SELECTION.json") {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_CURRENT_TASK_OWNER_RECEIPT"
                }
                for ((logical, entry) in currentTaskRows) {
                    val origin = identity(entry["ownerAfter"])
                    val after = selected(entry["after"])
                    val before = selected(entry["before"])
                    require(sourceRolePath(path(origin), "$taskOwnerRoot/source/$logical") && same(origin, after)) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_CURRENT_TASK_OWNER_ARCHIVE:$logical"
                    }
                    require(path(after) == "$taskRoot/archive/after/$logical" && path(before) == "$taskRoot/archive/before/$logical") {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_CURRENT_TASK_ARCHIVE_ROLE:$logical"
                    }
                }
                for ((logical, entry) in currentTaskRows.filterKeys { it in current }) require(
                    same(selected(entry["before"]), selected(entry["parentSelected"])) &&
                    same(selected(entry["after"]), identity(entry["ownerAfter"])) &&
                    same(selected(entry["before"]), selected(entry["after"]))) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_CURRENT_TASK_PROVENANCE:$logical"
                }
                advance("sdkCurrentTaskEvolution", currentTaskRows)
                val (sync, syncRows) = stage("hostSyncEvolution", "maplesu-host-runtime-sync-source-successor/1", "files", 9)
                require(same(selected(sync["coreSuccessorSelection"]), selected(coreRuntime["identity"]))) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_SYNC_PARENT"
                }
                for ((logical, entry) in syncRows) require(same(selected(entry["before"]), selected(entry["archivedBefore"])) &&
                    same(selected(entry["before"]), selected(entry["upstreamBefore"])) &&
                    same(selected(entry["after"]), selected(entry["upstreamAfter"]))) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_SYNC_PROVENANCE:$logical"
                }
                advance("hostSyncEvolution", syncRows)
                val (retirement, retirementRows) = stage("hostRetirementEvolution", "maplesu-host-retirement-source-successor/1", "files", 4)
                val retiredParent = selected(retirement["parentEffectiveSelection"])
                val retiredCandidate = selected(retirement["parentCandidate"])
                val runtimeParent = identity(runtimeEvolution["predecessor"])
                // The publisher retains this reference; actual predecessor inputs are bound above.
                require(path(runtimeParent) == path(selected(retirement["identity"])).substringBeforeLast('/') +
                    "/EFFECTIVE-SOURCE-SELECTION.json") { "ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_ROLE" }
                require(path(retiredParent).substringBeforeLast('/') == path(selected(sync["identity"])).substringBeforeLast('/') &&
                    path(retiredCandidate).substringBeforeLast('/') == path(retiredParent).substringBeforeLast('/') &&
                    path(runtimeParent).substringBeforeLast('/') == path(selected(retirement["identity"])).substringBeforeLast('/')) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_RETIREMENT_PARENT"
                }
                for ((logical, entry) in retirementRows) require(same(selected(entry["before"]), selected(entry["archivedBefore"]))) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_RETIREMENT_PROVENANCE:$logical"
                }
                advance("hostRetirementEvolution", retirementRows)
                current
            }
            for ((logical, row) in sources) {
                val after = selected(row["after"])
                require(path(after).endsWith("/$logical")) { "ULTRA_KERNEL_CORE_SOURCE_AFTER_PATH" }
                val prior = row.getValue("before").takeUnless { it is JsonNull }?.jsonObject
                if (prior != null) require((prior["bytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0 &&
                    prior["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) { "ULTRA_KERNEL_CORE_SOURCE_PREIMAGE" }
                row["nativeCopyIdentity"]?.let { value ->
                    val native = selected(value)
                    require(same(native, after) && path(native).substringAfterLast('/') == logical.substringAfterLast('/')) {
                        "ULTRA_KERNEL_CORE_NATIVE_SOURCE"
                    }
                }
                val runtime = runtimeRows[logical]
                if (runtime != null) {
                    val task = taskRows[logical]
                    require(same(runtimePredecessors.getValue(logical), selected(runtime["before"])) &&
                        (if (task == null) same(after, selected(runtime["after"])) && row["previousTaskProfileInput"] == null
                        else same(selected(row["previousTaskProfileInput"]), selected(runtime["after"])) &&
                            same(selected(task["before"]), selected(runtime["after"])) && same(after, selected(task["after"])))) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_CORE_SUCCESSOR:$logical"
                    }
                } else require(row["previousRuntimeLayoutInput"] == null) { "ULTRA_KERNEL_RUNTIME_LAYOUT_CORE_UNEXPECTED:$logical" }
            }
            require(sources.values.map { path(identity(it["after"])) }.distinct().size == rows.size) { "ULTRA_KERNEL_CORE_SOURCE_ALIAS" }
            if (deploymentChannel) {
                val predecessor = evolution.getValue("predecessorEvolution").jsonObject
                require(predecessor["schema"] == evolution["schema"] && !flag(predecessor, "productionWritten") &&
                    predecessor["lifecycleRevision"]?.jsonPrimitive?.intOrNull == 2 &&
                    same(selected(predecessor["selectionIdentity"]), selected(evolution["previousCoreSelectionIdentity"])) &&
                    same(selected(predecessor["freezeIdentity"]), selected(evolution["previousCoreFreezeIdentity"]))) {
                    "ULTRA_KERNEL_CORE_DEPLOYMENT_PREDECESSOR"
                }
                for (key in listOf("baselineIdentity", "changesetIdentity", "parentFreezeIdentity",
                    "previousCoreFreezeIdentity", "previousCoreSelectionIdentity")) selected(predecessor[key])
                val oldRows = predecessor.getValue("selectedInputs").jsonArray.map { it.jsonObject }
                val oldSources = oldRows.associateBy { it.getValue("path").jsonPrimitive.content }
                val supportRoot = "runtime/ultra/identity/kernel/"
                val supportPaths = setOf("manager_claim_bootstrap.c", "manager_claim_bootstrap.h", "manager_claim_loader.h",
                    "manager_deployment_namespace.h", "manager_resident_abi.h", "manager_resident_host.h").map { supportRoot + it }.toSet()
                require(oldSources.size == oldRows.size && oldSources.keys == expectedPaths - supportPaths) {
                    "ULTRA_KERNEL_CORE_DEPLOYMENT_PREDECESSOR_PATHS"
                }
                val changed = mutableSetOf<String>()
                for ((logical, old) in oldSources) {
                    val prior = selected(old["after"])
                    require(path(prior).endsWith("/$logical") && old["before"] == sources.getValue(logical)["before"]) {
                        "ULTRA_KERNEL_CORE_DEPLOYMENT_ORIGINAL_BEFORE:$logical"
                    }
                    old["nativeCopyIdentity"]?.let { require(same(selected(it), prior)) {
                        "ULTRA_KERNEL_CORE_DEPLOYMENT_PREDECESSOR_NATIVE:$logical"
                    } }
                    val channelInput = sources.getValue(logical).let { row ->
                        row["previousRuntimeLayoutInput"]?.let { historicalCore(it, logical) } ?: selected(row["after"])
                    }
                    if (!same(prior, channelInput)) changed += logical
                }
                val readbackPath = "runtime/ultra/bridge/installed_helper_readback.cpp"
                val channelPaths = setOf("runtime/maplesu-sdk/sdk/include/maplesu_request_private.h",
                    "runtime/maplesu-sdk/sdk/manifest/0023_module_base_kernel_request.o.json",
                    "runtime/maplesu-sdk/sdk/src/kernel_request/module_base_kernel_request.cpp",
                    "runtime/ultra/authorization/request_protocol.h", supportRoot + "manager_management.c",
                    supportRoot + "manager_resident_host.c")
                require(changed == channelPaths + readbackPath) { "ULTRA_KERNEL_CORE_DEPLOYMENT_CHANGED_PATHS" }
                val adoptedRows = evolution["adoptedBetweenPredecessorAndChannel"] as? JsonArray
                    ?: error("ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_ADOPTION_ROWS")
                require(adoptedRows.size == 1) { "ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_ADOPTION_ROWS" }
                val adopted = adoptedRows.single() as? JsonObject
                    ?: error("ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_ADOPTION_ROWS")
                require(adopted["logicalPath"]?.jsonPrimitive?.content == readbackPath &&
                    same(selected(adopted["before"]), selected(oldSources.getValue(readbackPath)["after"])) &&
                    same(selected(adopted["after"]), selected(sources.getValue(readbackPath)["after"])) &&
                    sourceRolePath(path(identity(adopted["before"])), path(identity(oldSources.getValue(readbackPath)["after"]))) &&
                    sourceRolePath(path(identity(adopted["after"])), path(identity(owner["identity"])).substringBeforeLast('/') +
                        "/archive/production/$readbackPath")) {
                    "ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_PREDECESSOR"
                }
                val channel = evolution.getValue("authenticatedDeployment").jsonObject
                for (key in listOf("ownerDelivery", "ownerSelection", "coreOnlyDiff", "hostAdoptionDiff")) selected(channel[key])
                require(channel["command"]?.jsonPrimitive?.intOrNull == 27 &&
                    channel["namespaceWireBytes"]?.jsonPrimitive?.intOrNull == 24 &&
                    channel["queryViews"]?.jsonPrimitive?.intOrNull == 5 && channel["sourceFileCount"]?.jsonPrimitive?.intOrNull == 54 &&
                    flag(channel, "original48MigrationPredecessorsPreserved") && !flag(channel, "sameTargetQualified")) {
                    "ULTRA_KERNEL_CORE_DEPLOYMENT_CONTRACT"
                }
            }
            val objects = setOf("manager_management", "manager_uid_gateway", "manager_task_state", "manager_credential_domain") +
                (if (lifecycleRevision == 2) setOf("manager_uid_snapshot") else emptySet())
            val probes = setOf("manager_core_facts", "manager_core_uid_facts", "manager_core_callback_types")
            val names = objects + probes
            fun identities(key: String, suffix: String, prefix: String = ""): Map<String, JsonObject> {
                val values = report.getValue(key).jsonArray.map(::identity)
                val mapped = values.associateBy { path(it).substringAfterLast('/') }
                val expected = names.map { prefix + it + suffix }.toSet()
                require(mapped.size == values.size && expected.containsAll(mapped.keys) && (!complete || mapped.keys == expected)) {
                    "ULTRA_KERNEL_CORE_SOURCE_ARRAY:$key"
                }
                return mapped
            }
            val actualSources = identities("sourceIdentities", ".c")
            val objectIds = identities("objectIdentities", ".o")
            val commands = identities("commandIdentities", ".o.cmd", ".")
            val receipts = identities("dependencyReceiptIdentities", ".json")
            fun dependencyIdentity(value: JsonElement): JsonObject {
                val item = value.jsonObject
                return if (runtimeEvolution == null || item["bytes"]?.jsonPrimitive?.longOrNull != 0L) identity(value)
                else item.also {
                    val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                    require(path(it).isNotBlank() && it["sha256"]?.jsonPrimitive?.content == empty &&
                        it["archivePath"]?.jsonPrimitive?.content == "build-dependencies/files/$empty.bin") {
                        "ULTRA_KERNEL_CORE_DEPENDENCY_IDENTITY"
                    }
                }
            }
            val dependencies = report.getValue("inlineDependencies").jsonArray.map(::dependencyIdentity)
            require(dependencies.isNotEmpty() || actualSources.isEmpty() && !complete) { "ULTRA_KERNEL_CORE_DEPENDENCIES" }
            for (name in objects) actualSources["$name.c"]?.let { actual ->
                val selected = identity(sources.getValue("runtime/ultra/identity/kernel/$name.c")["after"])
                require(same(actual, selected) && dependencies.any { same(it, selected) }) { "ULTRA_KERNEL_CORE_ACTUAL_SOURCE:$name" }
            }
            val facts = report.getValue("compiledFacts").jsonObject.mapValues { it.value.jsonPrimitive.int }
            val uidFacts = report.getValue("uidCompiledFacts").jsonObject.mapValues { it.value.jsonPrimitive.int }
            val compiled = layouts(report["compiledLayouts"])
            val functions = report.getValue("localFunctionAbi").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val callbacks = report.getValue("callbackParameters").jsonObject
            val callbackKinds = mapOf("profile.validate" to Triple("manager_management", "msu_credential_replace_profile_current", 1),
                "profile.adopt" to Triple("manager_management", "msu_credential_replace_profile_current", 2),
                "uid.create.validate" to Triple("manager_uid_gateway", "msu_uid_gateway_create_kernel", 1),
                "task.validate" to Triple("manager_task_state", "msu_task_state_release_current", 0)) +
                (if (lifecycleRevision == 2) mapOf(
                    "uid.prepare.validate" to Triple("manager_uid_gateway", "msu_uid_gateway_prepare_kernel", 7),
                    "uid.commit.validate" to Triple("manager_uid_gateway", "msu_uid_gateway_commit_kernel", 5)) else emptyMap())
            require(callbackKinds.keys.containsAll(callbacks.keys) && (!complete || callbacks.keys == callbackKinds.keys)) { "ULTRA_KERNEL_CORE_CALLBACK_FIELDS" }
            val callbackSignatures = callbacks.mapValues { (name, value) ->
                val item = value.jsonObject
                val expected = callbackKinds.getValue(name)
                require(item["object"]?.jsonPrimitive?.content == expected.first && item["source"]?.jsonPrimitive?.content == expected.second &&
                    item["parameterIndex"]?.jsonPrimitive?.intOrNull == expected.third && expected.first + ".o" in objectIds &&
                    item["signature"]?.jsonPrimitive?.content?.startsWith("*f") == true) { "ULTRA_KERNEL_CORE_CALLBACK:$name" }
                UltraKernelResidentAbi.CallbackOrigin("internal", expected.second,
                    item.getValue("signature").jsonPrimitive.content, parameterIndex = expected.third)
            }
            val fence = report.getValue("readerFenceAbi").jsonObject.mapValues { it.value.jsonPrimitive.content }
            UltraKernelResidentAbi.verifyCoreLifecycleMetadata(facts, uidFacts, compiled, functions, callbackSignatures, fence, complete,
                lifecycleRevision, runtimeSuccessor != null, runtimeEvolution != null)
            val functionObjects = mapOf(
                "manager_management" to setOf("msu_runtime_service_register", "msu_runtime_service_bind", "msu_runtime_service_bind_held",
                    "msu_runtime_service_revoke", "msu_runtime_service_alloc", "msu_runtime_service_write", "msu_runtime_service_shared_bool_create",
                    "msu_runtime_service_prepare", "msu_runtime_service_publish", "msu_runtime_service_abort_unexposed",
                    "msu_credential_profile_validate", "msu_credential_replace_profile_current") +
                    (if (lifecycleRevision == 2) setOf("msu_management_discard_unpublished") else emptySet()),
                "manager_uid_gateway" to setOf("msu_uid_gateway_create_kernel", "msu_uid_gateway_bind_reader") +
                    (if (lifecycleRevision == 2) setOf("msu_uid_gateway_prepare_kernel", "msu_uid_gateway_commit_kernel",
                        "msu_uid_gateway_cancel_kernel", "msu_uid_gateway_discard_host_unpublished") else emptySet()),
                "manager_task_state" to setOf("msu_task_state_release_current"),
                "manager_credential_domain" to setOf("msu_credential_domain_supported", "msu_credential_domain_prepare")) +
                (if (lifecycleRevision == 2) mapOf("manager_uid_snapshot" to setOf(
                    "msu_uid_snapshot_state_destroy_quiesced", "msu_uid_snapshot_runtime_discard")) else emptyMap())
            val functionOwner = functionObjects.flatMap { (name, entries) -> entries.map { it to name } }.toMap()
            val cfi = report.getValue("entryCfi").jsonObject
            val cfiFunctions = cfi["functions"]?.jsonObject.orEmpty()
            require(cfi.isNotEmpty() || !complete) { "ULTRA_KERNEL_CORE_CFI_MISSING" }
            if (cfi.isNotEmpty()) {
                require(cfi["mode"]?.jsonPrimitive?.content == mode && flag(cfi, "normalizeIntegers") == normalize) { "ULTRA_KERNEL_CORE_CFI_POLICY" }
                val policies = cfi.getValue("objectPolicies").jsonObject
                require(policies.keys == names && policies.values.all {
                    val item = it.jsonObject
                    flag(item, "kcfiOptionPresent") == (mode == "kcfi") && flag(item, "kcfiEnabled") == (mode == "kcfi") &&
                        flag(item, "normalizeIntegers") == normalize
                }) { "ULTRA_KERNEL_CORE_OBJECT_POLICY" }
                require(functions.keys.containsAll(cfiFunctions.keys) && (!complete || functions.keys == cfiFunctions.keys)) { "ULTRA_KERNEL_CORE_CFI_FUNCTIONS" }
                for ((name, value) in cfiFunctions) {
                    val item = value.jsonObject
                    require(item["function"]?.jsonPrimitive?.content == name && item["signature"]?.jsonPrimitive?.content == functions[name] &&
                        flag(item, "verified") && same(identity(item["objectIdentity"]), objectIds.getValue(functionOwner.getValue(name) + ".o"))) {
                        "ULTRA_KERNEL_CORE_CFI_SIGNATURE:$name"
                    }
                    val keys = setOf("typeidSymbol", "typeid", "functionOffset", "prefixOffset", "prefixWord")
                    if (mode == "kcfi") {
                        val id = item.getValue("typeid").jsonPrimitive.long
                        val at = item.getValue("functionOffset").jsonPrimitive.long
                        require(id in 0..0xffffffffL && at >= 4 && at % 4 == 0L &&
                            item["typeidSymbol"]?.jsonPrimitive?.content == "__kcfi_typeid_$name" &&
                            item.getValue("prefixOffset").jsonPrimitive.long == at - 4 && item.getValue("prefixWord").jsonPrimitive.long == id) {
                            "ULTRA_KERNEL_CORE_CFI_PREFIX:$name"
                        }
                    } else require(item.keys.intersect(keys).isEmpty()) { "ULTRA_KERNEL_CORE_CFI_DISABLED:$name" }
                }
            }
            val entries = report.getValue("entrypoints").jsonObject
            require(functions.keys.containsAll(entries.keys) && (!complete || functions.keys == entries.keys)) { "ULTRA_KERNEL_CORE_ENTRIES" }
            for ((name, value) in entries) {
                val item = value.jsonObject
                if (!flag(item, "available")) {
                    require(item.keys == setOf("available", "reason") && !item["reason"]?.jsonPrimitive?.content.isNullOrBlank()) { "ULTRA_KERNEL_CORE_ENTRY_UNAVAILABLE:$name" }
                    continue
                }
                val at = item.getValue("entryOffset").jsonPrimitive.long
                val file = item.getValue("fileOffset").jsonPrimitive.long
                val bytes = item.getValue("bytes").jsonPrimitive.long
                val section = item.getValue("sectionOffset").jsonPrimitive.long
                val sectionBytes = item.getValue("sectionBytes").jsonPrimitive.long
                require(at >= section && at % 4 == 0L && section >= 0 && sectionBytes > 0 && bytes > 0 &&
                    at - section <= sectionBytes && bytes <= sectionBytes - (at - section) && file >= 0 &&
                    file <= elf.size && bytes <= elf.size - file && !item["sectionName"]?.jsonPrimitive?.content.isNullOrBlank() &&
                    item["binding"]?.jsonPrimitive?.content in setOf("STB_LOCAL", "STB_GLOBAL", "STB_WEAK")) { "ULTRA_KERNEL_CORE_ENTRY_RANGE:$name" }
                val prefixKeys = setOf("prefixOffset", "prefixFileOffset", "prefixWord", "typeid", "prefixVerified")
                if (mode == "kcfi") {
                    val id = cfiFunctions.getValue(name).jsonObject.getValue("typeid").jsonPrimitive.long
                    require(item.keys.containsAll(prefixKeys) && at - section >= 4 && file >= 4 && flag(item, "prefixVerified") &&
                        item.getValue("prefixOffset").jsonPrimitive.long == at - 4 && item.getValue("prefixFileOffset").jsonPrimitive.long == file - 4 &&
                        item.getValue("typeid").jsonPrimitive.long == id && item.getValue("prefixWord").jsonPrimitive.long == id &&
                        UltraKernelA64.le32Bits(elf, (file - 4).toInt()).toLong() and 0xffffffffL == id) { "ULTRA_KERNEL_CORE_ENTRY_PREFIX:$name" }
                } else require(item.keys.intersect(prefixKeys).isEmpty()) { "ULTRA_KERNEL_CORE_ENTRY_CFI_DISABLED:$name" }
            }
            val config = report.getValue("config").jsonObject
            val configKeys = setOf("CONFIG_SECCOMP", "CONFIG_SECCOMP_FILTER", "CONFIG_GENERIC_ENTRY", "CONFIG_THREAD_INFO_IN_TASK",
                "CONFIG_PREEMPT_RT", "CONFIG_SECURITY_SELINUX", "CONFIG_SECURITY", "CONFIG_CFI", "CONFIG_CFI_CLANG", "CONFIG_CFI_ICALL_NORMALIZE_INTEGERS")
            require(config.isEmpty() && !complete || config.keys == configKeys && config.all { (name, value) ->
                value.jsonPrimitive.content in setOf("y", "n") && value == (reference.getValue("managementAbi").jsonObject.getValue("config").jsonObject[name] ?: JsonPrimitive("n"))
            }) { "ULTRA_KERNEL_CORE_CONFIG" }
            fun layout(key: String, name: String, words: Int, expectedNames: String): Pair<JsonObject, IntArray>? {
                val item = report.getValue(key).jsonObject
                if (item.isEmpty()) { require(!complete) { "ULTRA_KERNEL_CORE_LAYOUT_MISSING:$key" }; return null }
                val values = item.getValue("layoutValues").jsonArray.map {
                    val word = it.jsonPrimitive.long
                    require(word in Int.MIN_VALUE.toLong()..0xffffffffL) { "ULTRA_KERNEL_CORE_LAYOUT_WORD:$key" }
                    word.toInt()
                }.toIntArray()
                require(item["layoutSchema"]?.jsonPrimitive?.intOrNull == 1 && item["layoutWords"]?.jsonPrimitive?.intOrNull == words &&
                    item["symbol"]?.jsonPrimitive?.content == name && values.size == words && values[0] == 1 &&
                    item.getValue("layoutNames").jsonArray.map { it.jsonPrimitive.content } == expectedNames.split(' ')) { "ULTRA_KERNEL_CORE_LAYOUT_SCHEMA:$key" }
                val bytes = java.nio.ByteBuffer.allocate(words * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putInt(it) } }.array()
                val digest = UltraKernelSpec.sha256(bytes)
                val exported = payload.getValue("compiledLayouts").jsonObject.getValue(name).jsonObject
                require(item["compiledLayoutSha256"]?.jsonPrimitive?.content == digest && item["exportBytes"]?.jsonPrimitive?.intOrNull == bytes.size &&
                    item["exportOffset"] == exported["offset"] && exported["bytes"]?.jsonPrimitive?.intOrNull == bytes.size &&
                    exported.getValue("values").jsonArray.map {
                        val word = it.jsonPrimitive.long
                        require(word in Int.MIN_VALUE.toLong()..0xffffffffL) { "ULTRA_KERNEL_CORE_LAYOUT_EXPORT_WORD:$key" }
                        word.toInt()
                    } == values.toList()) { "ULTRA_KERNEL_CORE_LAYOUT_EXPORT:$key" }
                val sourceName = if (key == "taskState") "manager_task_state" else "manager_credential_domain"
                val source = identity(sources.getValue("runtime/ultra/identity/kernel/$sourceName.c")["after"])
                val header = identity(sources.getValue("runtime/ultra/identity/kernel/$sourceName.h")["after"])
                require(same(identity(item["sourceIdentity"]), source) && same(identity(item["headerIdentity"]), header) &&
                    same(identity(item["objectIdentity"]), objectIds.getValue("$sourceName.o")) &&
                    same(identity(item["commandIdentity"]), commands.getValue(".$sourceName.o.cmd")) &&
                    same(identity(item["dependencyReceiptIdentity"]), receipts.getValue("$sourceName.json"))) { "ULTRA_KERNEL_CORE_LAYOUT_SOURCE:$key" }
                val deps = item.getValue("dependencies").jsonArray.map(::dependencyIdentity)
                require(deps.isNotEmpty() && deps.any { same(it, source) } && deps.any { same(it, header) }) { "ULTRA_KERNEL_CORE_LAYOUT_DEPENDENCIES:$key" }
                return item to values
            }
            val task = layout("taskState", "msu_task_state_compiled_layout", 53, "MSU_TASK_STATE_SCHEMA MSU_TASK_STATE_KERNEL_VERSION MSU_TASK_STATE_SECCOMP MSU_TASK_STATE_SECCOMP_FILTER MSU_TASK_STATE_GENERIC_ENTRY MSU_TASK_STATE_THREAD_INFO_IN_TASK MSU_TASK_STATE_PREEMPT_RT MSU_TASK_STATE_TASK_SIZE MSU_TASK_STATE_TASK_ALIGN MSU_TASK_STATE_TASK_SECCOMP_OFFSET MSU_TASK_STATE_TASK_SECCOMP_BYTES MSU_TASK_STATE_TASK_SIGHAND_OFFSET MSU_TASK_STATE_TASK_SIGHAND_BYTES MSU_TASK_STATE_TASK_THREAD_INFO_OFFSET MSU_TASK_STATE_TASK_THREAD_INFO_BYTES MSU_TASK_STATE_TASK_STACK_OFFSET MSU_TASK_STATE_TASK_STACK_BYTES MSU_TASK_STATE_SECCOMP_SIZE MSU_TASK_STATE_SECCOMP_ALIGN MSU_TASK_STATE_MODE_OFFSET MSU_TASK_STATE_MODE_BYTES MSU_TASK_STATE_FILTER_COUNT_OFFSET MSU_TASK_STATE_FILTER_COUNT_BYTES MSU_TASK_STATE_FILTER_OFFSET MSU_TASK_STATE_FILTER_BYTES MSU_TASK_STATE_SIGHAND_SIZE MSU_TASK_STATE_SIGHAND_ALIGN MSU_TASK_STATE_SIGLOCK_OFFSET MSU_TASK_STATE_SIGLOCK_BYTES MSU_TASK_STATE_THREAD_INFO_SIZE MSU_TASK_STATE_THREAD_INFO_ALIGN MSU_TASK_STATE_WORK_OFFSET MSU_TASK_STATE_WORK_BYTES MSU_TASK_STATE_WORK_BIT MSU_TASK_STATE_SPINLOCK_SIZE MSU_TASK_STATE_SPINLOCK_ALIGN MSU_TASK_STATE_SPINLOCK_RAW_OFFSET MSU_TASK_STATE_SPINLOCK_RAW_BYTES MSU_TASK_STATE_RAW_SPINLOCK_SIZE MSU_TASK_STATE_RAW_SPINLOCK_ALIGN MSU_TASK_STATE_RAW_LOCK_OFFSET MSU_TASK_STATE_RAW_LOCK_BYTES MSU_TASK_STATE_RAW_MAGIC_OFFSET MSU_TASK_STATE_RAW_MAGIC_BYTES MSU_TASK_STATE_RAW_CPU_OFFSET MSU_TASK_STATE_RAW_CPU_BYTES MSU_TASK_STATE_RAW_OWNER_OFFSET MSU_TASK_STATE_RAW_OWNER_BYTES MSU_TASK_STATE_RAW_DEP_MAP_OFFSET MSU_TASK_STATE_RAW_DEP_MAP_BYTES MSU_TASK_STATE_DISABLED_MODE MSU_TASK_STATE_STRICT_MODE MSU_TASK_STATE_FILTER_MODE")
            val domain = layout("credentialDomain", "msu_credential_domain_compiled_layout", 26, "MSU_CREDENTIAL_DOMAIN_SCHEMA MSU_CREDENTIAL_DOMAIN_KERNEL_VERSION MSU_CREDENTIAL_DOMAIN_BUILD_VERIFIED MSU_CREDENTIAL_DOMAIN_SELINUX MSU_CREDENTIAL_DOMAIN_CRED_SIZE MSU_CREDENTIAL_DOMAIN_CRED_ALIGN MSU_CREDENTIAL_DOMAIN_SECURITY_OFFSET MSU_CREDENTIAL_DOMAIN_SECURITY_BYTES MSU_CREDENTIAL_DOMAIN_BLOB_SIZE MSU_CREDENTIAL_DOMAIN_BLOB_ALIGN MSU_CREDENTIAL_DOMAIN_OSID_OFFSET MSU_CREDENTIAL_DOMAIN_OSID_BYTES MSU_CREDENTIAL_DOMAIN_SID_OFFSET MSU_CREDENTIAL_DOMAIN_SID_BYTES MSU_CREDENTIAL_DOMAIN_EXEC_OFFSET MSU_CREDENTIAL_DOMAIN_EXEC_BYTES MSU_CREDENTIAL_DOMAIN_CREATE_OFFSET MSU_CREDENTIAL_DOMAIN_CREATE_BYTES MSU_CREDENTIAL_DOMAIN_KEYCREATE_OFFSET MSU_CREDENTIAL_DOMAIN_KEYCREATE_BYTES MSU_CREDENTIAL_DOMAIN_SOCKCREATE_OFFSET MSU_CREDENTIAL_DOMAIN_SOCKCREATE_BYTES MSU_CREDENTIAL_DOMAIN_SIZES_SIZE MSU_CREDENTIAL_DOMAIN_SIZES_ALIGN MSU_CREDENTIAL_DOMAIN_LBS_CRED_OFFSET MSU_CREDENTIAL_DOMAIN_LBS_CRED_BYTES")
            domain?.let { (_, values) ->
                require(config.isNotEmpty() && (values[3] == 1) == (config["CONFIG_SECURITY_SELINUX"]?.jsonPrimitive?.content == "y")) {
                    "ULTRA_KERNEL_CORE_DOMAIN_CONFIG"
                }
                UltraKernelResidentAbi.verifyCredentialDomain(values,
                    UltraKernelSpec.sha256(java.nio.ByteBuffer.allocate(104).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putInt(it) } }.array()))
            }
            task?.let { (item, words) ->
                require(config.isNotEmpty() && (2..6).all { words[it] in 0..1 } &&
                    words.sliceArray(50..52).contentEquals(intArrayOf(0, 1, 2)) &&
                    mapOf(2 to "CONFIG_SECCOMP", 3 to "CONFIG_SECCOMP_FILTER", 4 to "CONFIG_GENERIC_ENTRY",
                        5 to "CONFIG_THREAD_INFO_IN_TASK", 6 to "CONFIG_PREEMPT_RT").all { (at, name) ->
                        (words[at] == 1) == (config[name]?.jsonPrimitive?.content == "y")
                    }) { "ULTRA_KERNEL_CORE_TASK_CONFIG" }
                val actual = layouts(item["compiledLayouts"])
                val target = layouts(item["targetLayouts"])
                val types = mapOf("task" to Triple(7, 8, mapOf("seccomp" to (9 to 10), "sighand" to (11 to 12), "thread_info" to (13 to 14), "stack" to (15 to 16))),
                    "seccomp" to Triple(17, 18, mapOf("mode" to (19 to 20), "filter_count" to (21 to 22), "filter" to (23 to 24))),
                    "sighand" to Triple(25, 26, mapOf("siglock" to (27 to 28))),
                    "threadInfo" to Triple(29, 30, mapOf((if (words[4] != 0) "syscall_work" else "flags") to (31 to 32))),
                    "spinlock" to Triple(34, 35, mapOf("rlock" to (36 to 37))),
                    "rawSpinlock" to Triple(38, 39, mapOf("raw_lock" to (40 to 41), "magic" to (42 to 43), "owner_cpu" to (44 to 45), "owner" to (46 to 47), "dep_map" to (48 to 49))))
                val expected = types.filter { words[it.value.first] != -1 }.keys
                require(actual.keys == target.keys && expected.containsAll(actual.keys) && (!complete || actual.keys == expected)) { "ULTRA_KERNEL_CORE_TASK_TYPES" }
                for ((name, observed) in actual) {
                    val shape = types.getValue(name)
                    val fields = shape.third.filter { words[it.value.first] != -1 && words[it.value.second] > 0 }
                    require(observed == target[name] && observed.bytes == words[shape.first] && words[shape.second] > 0 &&
                        observed.members.keys == fields.keys && fields.all { (member, at) ->
                            observed.members.getValue(member).offset == words[at.first] && observed.members.getValue(member).bytes == words[at.second]
                        }) { "ULTRA_KERNEL_CORE_TASK_FIELD:$name" }
                }
            }
            val native = report.getValue("nativeFunctionAbi").jsonObject
            val nativeNames = setOf("security_secctx_to_secid") +
                if (config["CONFIG_SECCOMP_FILTER"]?.jsonPrimitive?.content == "y") setOf("seccomp_filter_release") else emptySet()
            require(nativeNames.containsAll(native.keys) && (!complete || native.keys == nativeNames)) {
                "ULTRA_KERNEL_CORE_NATIVE_FUNCTIONS"
            }
            for ((name, value) in native) {
                val item = value.jsonObject
                val verified = flag(item, "verified")
                require(!complete || verified) { "ULTRA_KERNEL_CORE_NATIVE_INCOMPLETE:$name" }
                if (name == "seccomp_filter_release" && verified) {
                    val traits = item.getValue("bodyTraits").jsonObject
                    require(traits["schema"]?.jsonPrimitive?.content == "maplesu-seccomp-release-body/1" &&
                        traits["symbol"]?.jsonPrimitive?.content == name && flag(traits, "enabled") &&
                        traits["locksTask"]?.jsonPrimitive?.booleanOrNull != null &&
                        traits["requiresPfExiting"]?.jsonPrimitive?.booleanOrNull != null &&
                        (!flag(traits, "locksTask") || flag(traits, "requiresPfExiting")) &&
                        traits["bodySha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true &&
                        traits.getValue("taskFields").jsonArray.map { it.jsonPrimitive.content }.let {
                            it.isNotEmpty() && it.distinct().size == it.size && setOf("flags", "sighand", "seccomp").containsAll(it) && "seccomp" in it
                        } && traits.getValue("seccompFields").jsonArray.map { it.jsonPrimitive.content } == listOf("filter")) {
                        "ULTRA_KERNEL_CORE_SECCOMP_BODY"
                    }
                    for (key in listOf("source", "header", "config")) identity(traits[key])
                }
                if (!verified) require(!item["reason"]?.jsonPrimitive?.content.isNullOrBlank()) { "ULTRA_KERNEL_CORE_NATIVE_REASON:$name" }
                for (key in listOf("declarationSignature", "sourceRequiredSignature", "signature")) item[key]?.let {
                    require(it.jsonPrimitive.content.startsWith("f") && it.jsonPrimitive.content.length <= 65536) {
                        "ULTRA_KERNEL_CORE_NATIVE_SIGNATURE_FORMAT:$name"
                    }
                }
                val signature = item["signature"]?.jsonPrimitive?.content
                if (verified) require(signature != null && signature.startsWith("f") &&
                    signature == item["declarationSignature"]?.jsonPrimitive?.content && signature == item["sourceRequiredSignature"]?.jsonPrimitive?.content &&
                    reference.getValue("functionAbi").jsonObject[name]?.jsonPrimitive?.content.let { it == null || it == signature }) {
                    "ULTRA_KERNEL_CORE_NATIVE_SIGNATURE:$name"
                }
                item["marker"]?.jsonObject?.let { marker ->
                    require(marker.isEmpty() && !verified || marker["function"]?.jsonPrimitive?.content == name) { "ULTRA_KERNEL_CORE_NATIVE_MARKER:$name" }
                    if (marker["available"]?.jsonPrimitive?.booleanOrNull == true) {
                        fun uint(key: String) = marker.getValue(key).jsonPrimitive.content.toULongOrNull() ?: error("ULTRA_KERNEL_CORE_NATIVE_RANGE:$name")
                        val address = uint("address"); val section = uint("sectionAddress"); val bytes = uint("functionBytes"); val sectionBytes = uint("sectionBytes")
                        require(address >= section && address % 4uL == 0uL && bytes > 0uL && address - section <= sectionBytes &&
                            bytes <= sectionBytes - (address - section) && marker.getValue("sectionFlags").jsonPrimitive.long and 4L != 0L &&
                            marker.getValue("fileOffset").jsonPrimitive.long >= 0 && !marker["section"]?.jsonPrimitive?.content.isNullOrBlank()) { "ULTRA_KERNEL_CORE_NATIVE_RANGE:$name" }
                    } else require(!verified && (marker.isEmpty() || marker["available"]?.jsonPrimitive?.booleanOrNull == false &&
                        !marker["reason"]?.jsonPrimitive?.content.isNullOrBlank())) { "ULTRA_KERNEL_CORE_NATIVE_MARKER_UNAVAILABLE:$name" }
                }
                item["cfi"]?.jsonObject?.let { proof ->
                    require(proof["mode"]?.jsonPrimitive?.content == mode && flag(proof, "normalizeIntegers") == normalize &&
                        same(identity(proof["typeProbeObjectIdentity"]), objectIds.getValue("manager_core_callback_types.o")) &&
                        same(identity(proof["typeProbeCommandIdentity"]), commands.getValue(".manager_core_callback_types.o.cmd")) &&
                        same(identity(proof["typeProbeDependencyReceiptIdentity"]), receipts.getValue("manager_core_callback_types.json"))) { "ULTRA_KERNEL_CORE_NATIVE_CFI_SOURCE:$name" }
                    identity(proof["referenceEvidenceIdentity"])
                    val keys = setOf("typeidSymbol", "declarationTypeid", "referencePrefixWord", "referencePrefixVerified")
                    if (mode == "kcfi" && (verified || proof.keys.intersect(keys).isNotEmpty())) {
                        require(proof.keys.containsAll(keys)) { "ULTRA_KERNEL_CORE_NATIVE_PREFIX_FIELDS:$name" }
                        val id = proof.getValue("declarationTypeid").jsonPrimitive.long
                        val marker = item.getValue("marker").jsonObject
                        val address = marker.getValue("address").jsonPrimitive.content.toULong()
                        require(id in 0..0xffffffffL && proof["typeidSymbol"]?.jsonPrimitive?.content == "__kcfi_typeid_$name" &&
                            flag(proof, "referencePrefixVerified") && proof.getValue("referencePrefixWord").jsonPrimitive.long == id &&
                            marker.getValue("prefixWord").jsonPrimitive.long == id && address >= 4uL &&
                            marker.getValue("prefixAddress").jsonPrimitive.content.toULong() == address - 4uL) { "ULTRA_KERNEL_CORE_NATIVE_PREFIX:$name" }
                    } else require(proof.keys.intersect(keys).isEmpty()) { "ULTRA_KERNEL_CORE_NATIVE_CFI_DISABLED:$name" }
                }
                if (verified) require(item["marker"]?.jsonObject?.get("available")?.jsonPrimitive?.booleanOrNull == true && item["cfi"] != null) {
                    "ULTRA_KERNEL_CORE_NATIVE_PROOF:$name"
                }
            }
            val missing = report.getValue("missing").jsonArray
            require(missing.isNotEmpty() && missing.all { !it.jsonPrimitive.content.isBlank() }) { "ULTRA_KERNEL_CORE_DIAGNOSTIC_STATUS" }
            return sources
        }

        private fun verifySuProvider(report: JsonObject, manifest: JsonObject, payload: JsonObject,
                                     reference: JsonObject, elf: ByteArray, mode: String, normalize: Boolean) {
            if (report["schemaVersion"]?.jsonPrimitive?.intOrNull in setOf(3, 4)) {
                UltraKernelSuComponentMetadata.verify(report, manifest, payload, reference, elf, mode, normalize)
                return
            }
            fun identity(value: JsonElement?): JsonObject {
                val item = value?.jsonObject ?: error("ULTRA_KERNEL_SU_SOURCE_IDENTITY")
                require(!item["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                    (item["bytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0 &&
                    item["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                    "ULTRA_KERNEL_SU_SOURCE_IDENTITY"
                }
                return item
            }
            fun same(left: JsonObject, right: JsonObject) = left["sha256"] == right["sha256"] && left["bytes"] == right["bytes"]
            fun selected(value: JsonElement?): JsonObject = identity(value).also { item ->
                val path = item.getValue("path").jsonPrimitive.content.replace('\\', '/')
                require(manifest.getValue("inputs").jsonObject.entries.singleOrNull {
                    path == it.key.replace('\\', '/') || path.endsWith("/" + it.key.replace('\\', '/'))
                }?.value == item["sha256"]) { "ULTRA_KERNEL_SU_SELECTED_SOURCE" }
            }
            fun flag(owner: JsonObject, key: String) = owner.getValue(key).jsonPrimitive.boolean
            val revision = report["schemaVersion"]?.jsonPrimitive?.intOrNull ?: error("ULTRA_KERNEL_SU_SCHEMA")
            require(revision in setOf(1, 2) && report["schema"]?.jsonPrimitive?.content == "maplesu-su-provider-abi/$revision") {
                "ULTRA_KERNEL_SU_SCHEMA"
            }
            // This manifest branch carries diagnostics until an actual SU consumer is integrated.
            require(listOf("metadataComplete", "providerComplete", "factoryBound", "lifecycleQualified", "targetQualified", "suReady")
                .all { !flag(report, it) }) { "ULTRA_KERNEL_SU_QUALIFICATION_UNBOUND" }
            val complete = flag(report, "kernelMetadataComplete")
            val selection = report.getValue("sourceSelection").jsonObject
            selected(selection["identity"])
            require(!flag(selection, "sourceOwnerFrozen") && !flag(selection, "adoptedToProduction")) { "ULTRA_KERNEL_SU_SOURCE_STATUS" }
            val provenance = selection.getValue("ownerProvenance").jsonObject
            for (key in listOf("deliveryIdentity", "sourceSelectionIdentity")) selected(provenance[key])
            require(listOf("productionWritten", "wholeSuComplete", "nativeBindingUsableInProduction").all { !flag(provenance, it) } &&
                !provenance["owner"]?.jsonPrimitive?.content.isNullOrBlank()) { "ULTRA_KERNEL_SU_OWNER_PROVENANCE" }
            val root = "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/"
            val logicalPaths = setOf(root + "kernel/su_install_registry.c", root + "kernel/su_install_registry.h", root + "su_native_reader_abi.h")
            val rows = selection.getValue("files").jsonArray.map { it.jsonObject }
            require(rows.size == logicalPaths.size && rows.map { it.getValue("logicalPath").jsonPrimitive.content }.toSet() == logicalPaths) {
                "ULTRA_KERNEL_SU_SOURCE_PATHS"
            }
            val snapshots = rows.associate { row ->
                val original = identity(row["originalIdentity"])
                val snapshot = selected(row["snapshotIdentity"])
                val logical = row.getValue("logicalPath").jsonPrimitive.content
                require(same(original, snapshot) && original.getValue("path").jsonPrimitive.content.replace('\\', '/').endsWith("/$logical")) {
                    "ULTRA_KERNEL_SU_SOURCE_AFTER_IMAGE"
                }
                logical.substringAfterLast('/') to snapshot
            }
            require(snapshots.values.map { it.getValue("path").jsonPrimitive.content.replace('\\', '/') }.distinct().size == rows.size) {
                "ULTRA_KERNEL_SU_SOURCE_ALIAS"
            }
            val names = setOf("su_install_registry", "manager_su_facts", "manager_su_callback_types")
            fun identities(key: String, suffix: String, expected: Set<String>): Map<String, JsonObject> {
                val values = report.getValue(key).jsonArray.map(::identity)
                val mapped = values.associateBy { it.getValue("path").jsonPrimitive.content.replace('\\', '/').substringAfterLast('/') }
                require(mapped.size == values.size && expected.containsAll(mapped.keys) && (!complete || mapped.keys == expected)) {
                    "ULTRA_KERNEL_SU_SOURCE_ARRAY:$key"
                }
                require(mapped.keys.all { it.endsWith(suffix) }) { "ULTRA_KERNEL_SU_SOURCE_SUFFIX:$key" }
                return mapped
            }
            val sources = identities("sourceIdentities", "", snapshots.keys)
            for ((name, item) in sources) require(same(item, snapshots.getValue(name))) { "ULTRA_KERNEL_SU_SOURCE_GENERATION:$name" }
            val objects = identities("objectIdentities", ".o", names.map { "$it.o" }.toSet())
            val commands = identities("commandIdentities", ".o.cmd", names.map { ".$it.o.cmd" }.toSet())
            val receipts = identities("dependencyReceiptIdentities", ".json", names.map { "$it.json" }.toSet())
            val dependencies = report.getValue("inlineDependencies").jsonArray.map(::identity)
            if (complete || report.getValue("compiledFacts").jsonObject.isNotEmpty()) require(
                sources.keys == snapshots.keys && objects.size == names.size && commands.size == names.size && receipts.size == names.size &&
                    dependencies.isNotEmpty() && snapshots.filterKeys { it != "su_native_reader_abi.h" }.values.all { source ->
                        dependencies.any { same(it, source) }
                    }) { "ULTRA_KERNEL_SU_DEPENDENCIES" }
            val binding = report.getValue("readerBinding").jsonObject
            require(!flag(binding, "available") && !flag(binding, "metadataComplete") &&
                same(identity(binding["sourceIdentity"]), snapshots.getValue("su_native_reader_abi.h")) &&
                !binding["reason"]?.jsonPrimitive?.content.isNullOrBlank()) { "ULTRA_KERNEL_SU_BINDING_FACTS_UNAVAILABLE" }
            val facts = report.getValue("compiledFacts").jsonObject.mapValues { it.value.jsonPrimitive.int }
            val layouts = report.getValue("compiledLayouts").jsonObject.mapValues { (_, value) ->
                val item = value.jsonObject
                UltraKernelResidentAbi.CompiledLayout(item.getValue("bytes").jsonPrimitive.int,
                    item.getValue("signature").jsonPrimitive.content, item.getValue("members").jsonObject.mapValues { (_, value) ->
                        val field = value.jsonObject
                        UltraKernelResidentAbi.CompiledMember(field.getValue("offset").jsonPrimitive.int,
                            field.getValue("bytes").jsonPrimitive.int, field.getValue("signature").jsonPrimitive.content)
                    })
            }
            val functions = report.getValue("localFunctionAbi").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val sourceCallbacks = report.getValue("sourceCallbackAbi").jsonObject.mapValues { it.value.jsonPrimitive.content }
            UltraKernelResidentAbi.verifySuProviderMetadata(facts, layouts, functions, sourceCallbacks, complete, revision)
            val origins = report.getValue("callbackOrigins").jsonObject
            require(origins.isEmpty() && !complete || origins.keys == setOf("su.credential.validate")) { "ULTRA_KERNEL_SU_CALLBACK_NAMESPACE" }
            origins["su.credential.validate"]?.jsonObject?.let { item ->
                require(item["kind"]?.jsonPrimitive?.content == "internal" && item["source"]?.jsonPrimitive?.content == "msu_credential_replace_current" &&
                    item["parameterIndex"]?.jsonPrimitive?.intOrNull == 1 && item["definition"]?.jsonPrimitive?.content == "msu_su_lease_validate" &&
                    item["signature"]?.jsonPrimitive?.content == "*" + functions.getValue("msu_su_lease_validate")) { "ULTRA_KERNEL_SU_VALIDATOR_ORIGIN" }
            }
            val relocations = report["callbackRelocations"]?.jsonObject
            require(relocations != null || !complete) { "ULTRA_KERNEL_SU_VALIDATOR_RELOCATIONS_MISSING" }
            if (relocations != null) require(relocations.keys == setOf("msu_credential_replace_current", "msu_su_lease_validate") &&
                relocations.values.all { "msu_su_apply_current" in it.jsonArray.map { it.jsonPrimitive.content } }) {
                "ULTRA_KERNEL_SU_VALIDATOR_RELOCATIONS"
            }
            val callbacks = mapOf("su.reader" to "msu_su_read_current", "su.apply" to "msu_su_apply_current", "su.credential.validate" to "msu_su_lease_validate")
            val cfi = report.getValue("callbackCfi").jsonObject
            val callbackEntries = cfi["callbacks"]?.jsonObject.orEmpty()
            require(cfi.isNotEmpty() || !complete) { "ULTRA_KERNEL_SU_CFI_MISSING" }
            if (cfi.isNotEmpty()) {
                require(flag(cfi, "supported") && cfi["mode"]?.jsonPrimitive?.content == mode && flag(cfi, "normalizeIntegers") == normalize &&
                    callbacks.keys.containsAll(callbackEntries.keys) && (!complete || callbacks.keys == callbackEntries.keys)) { "ULTRA_KERNEL_SU_CFI_POLICY" }
                val policies = cfi.getValue("objectPolicies").jsonObject
                require(policies.keys == names + "manager_management" && policies.values.all {
                    val policy = it.jsonObject
                    flag(policy, "kcfiOptionPresent") == (mode == "kcfi") && flag(policy, "kcfiEnabled") == (mode == "kcfi") &&
                        flag(policy, "normalizeIntegers") == normalize
                }) { "ULTRA_KERNEL_SU_CFI_OBJECT_POLICIES" }
                for ((key, value) in callbackEntries) {
                    val item = value.jsonObject
                    val name = callbacks.getValue(key)
                    require(item["function"]?.jsonPrimitive?.content == name && item["signature"]?.jsonPrimitive?.content == functions.getValue(name) &&
                        flag(item, "verified") && same(identity(item["objectIdentity"]), objects.getValue("su_install_registry.o")) &&
                        same(identity(item["typeProbeObjectIdentity"]), objects.getValue("manager_su_callback_types.o"))) { "ULTRA_KERNEL_SU_CFI_CALLBACK:$key" }
                    if (mode == "kcfi") {
                        val typeid = item.getValue("typeid").jsonPrimitive.long
                        val entry = item.getValue("functionOffset").jsonPrimitive.long
                        require(typeid in 0..0xffffffffL && item["typeidSymbol"]?.jsonPrimitive?.content == "__kcfi_typeid_$name" &&
                            item.getValue("prefixWord").jsonPrimitive.long == typeid && entry >= 4 && entry % 4 == 0L &&
                            item.getValue("prefixOffset").jsonPrimitive.long == entry - 4) { "ULTRA_KERNEL_SU_CFI_TYPEID:$key" }
                    } else require(listOf("typeidSymbol", "typeid", "functionOffset", "prefixOffset", "prefixWord").all { it !in item }) {
                        "ULTRA_KERNEL_SU_CFI_DISABLED:$key"
                    }
                }
            }
            val imports = payload.getValue("imports").jsonArray.map { it.jsonPrimitive.content }.toSet()
            require(imports.intersect(setOf("msu_credential_replace_current", "msu_uid_gateway_acquire", "msu_uid_snapshot_lookup", "msu_uid_snapshot_put")).isEmpty()) {
                "ULTRA_KERNEL_SU_INTERNAL_IMPORTS"
            }
            val finalImports = report["finalImports"]?.jsonObject
            require(finalImports != null || !complete) { "ULTRA_KERNEL_SU_FINAL_IMPORTS_MISSING" }
            val undefined = report["objectUndefinedSymbols"]?.jsonArray?.map { it.jsonPrimitive.content }
            require(undefined == null && !complete || undefined != null && undefined.distinct().size == undefined.size &&
                undefined.all { it.isNotBlank() }) { "ULTRA_KERNEL_SU_UNDEFINED_SYMBOLS" }
            finalImports?.let { actual ->
                require(actual.keys == requireNotNull(undefined).toSet().intersect(imports) && actual.all { (name, value) ->
                    value == reference.getValue("functionAbi").jsonObject[name] || value == reference.getValue("dataAbi").jsonObject[name]
                }) { "ULTRA_KERNEL_SU_FINAL_IMPORT_ABI" }
            }
            val config = report.getValue("config").jsonObject
            val configKeys = setOf("CONFIG_MULTIUSER", "CONFIG_PREEMPT_RCU", "CONFIG_TREE_RCU", "CONFIG_REFCOUNT_FULL", "CONFIG_DEBUG_SPINLOCK")
            require(config.isEmpty() && !complete || config.keys == configKeys && config.all { (key, value) ->
                value.jsonPrimitive.content in setOf("y", "n") && value == (reference.getValue("managementAbi").jsonObject.getValue("config").jsonObject[key] ?: JsonPrimitive("n"))
            }) { "ULTRA_KERNEL_SU_CONFIG" }
            if (config.isNotEmpty()) require(facts["configMultiuser"] == if (config.getValue("CONFIG_MULTIUSER").jsonPrimitive.content == "y") 1 else 0) {
                "ULTRA_KERNEL_SU_MULTIUSER_FACT"
            }
            val entries = report.getValue("entrypoints").jsonObject
            val entriesComplete = flag(report, "entrypointsComplete")
            require(functions.keys.containsAll(entries.keys) && (!complete || entries.keys == functions.keys)) { "ULTRA_KERNEL_SU_ENTRYPOINTS" }
            for ((name, value) in entries) {
                val entry = value.jsonObject
                if (!flag(entry, "available")) {
                    require(entry.keys == setOf("available", "reason") && !entry["reason"]?.jsonPrimitive?.content.isNullOrBlank()) {
                        "ULTRA_KERNEL_SU_ENTRY_UNAVAILABLE:$name"
                    }
                    continue
                }
                val address = entry.getValue("entryOffset").jsonPrimitive.long
                val file = entry.getValue("fileOffset").jsonPrimitive.long
                val bytes = entry.getValue("bytes").jsonPrimitive.long
                val section = entry.getValue("sectionOffset").jsonPrimitive.long
                val sectionBytes = entry.getValue("sectionBytes").jsonPrimitive.long
                require(address >= section && address % 4 == 0L && bytes > 0 && section >= 0 && sectionBytes > 0 &&
                    address - section <= sectionBytes && bytes <= sectionBytes - (address - section) && file >= 0 &&
                    file <= elf.size && bytes <= elf.size - file && !entry["sectionName"]?.jsonPrimitive?.content.isNullOrBlank() &&
                    entry["binding"]?.jsonPrimitive?.content in setOf("STB_LOCAL", "STB_GLOBAL", "STB_WEAK")) { "ULTRA_KERNEL_SU_ENTRY_RANGE:$name" }
                val callback = callbacks.entries.singleOrNull { it.value == name }?.key
                val prefixKeys = setOf("prefixOffset", "prefixFileOffset", "prefixWord", "typeid", "prefixVerified")
                if (mode == "kcfi" && callback != null) {
                    val present = entry.keys.intersect(prefixKeys)
                    require(present.isEmpty() && !complete && !entriesComplete || present == prefixKeys) { "ULTRA_KERNEL_SU_ENTRY_CFI_MISSING:$name" }
                    if (present.isNotEmpty()) {
                        val typeid = callbackEntries.getValue(callback).jsonObject.getValue("typeid").jsonPrimitive.long
                        require(address - section >= 4 && file >= 4 && entry.getValue("prefixOffset").jsonPrimitive.long == address - 4 &&
                            entry.getValue("prefixFileOffset").jsonPrimitive.long == file - 4 && flag(entry, "prefixVerified") &&
                            entry.getValue("prefixWord").jsonPrimitive.long == typeid && entry.getValue("typeid").jsonPrimitive.long == typeid &&
                            UltraKernelA64.le32Bits(elf, (file - 4).toInt()).toLong() and 0xffffffffL == typeid) { "ULTRA_KERNEL_SU_ENTRY_CFI:$name" }
                    }
                } else require(entry.keys.intersect(prefixKeys).isEmpty()) { "ULTRA_KERNEL_SU_ENTRY_CFI_DISABLED:$name" }
            }
            require((!entriesComplete || entries.isNotEmpty() && entries.keys == functions.keys && entries.values.all { flag(it.jsonObject, "available") }) &&
                report.getValue("missing").jsonArray.isNotEmpty() && report.getValue("missing").jsonArray.all {
                    !it.jsonPrimitive.content.isBlank()
                }) { "ULTRA_KERNEL_SU_DIAGNOSTIC_STATUS" }
        }

        private fun verifySourceQualification(manifest: JsonObject, payload: JsonObject,
            reference: JsonObject, elf: ByteArray, mode: String, normalize: Boolean): SourceQualification? {
            val core = reference["coreLifecycleAbi"]?.jsonObject ?: return null
            val proof = core["sourceQualification"]?.jsonObject ?: return null
            fun flag(owner: JsonObject, name: String) = owner[name]?.jsonPrimitive?.booleanOrNull
            fun identity(value: JsonElement?): JsonObject =
                (value?.jsonObject ?: error("ULTRA_KERNEL_SOURCE_QUALIFICATION_IDENTITY")).also {
                    val size = it["bytes"]?.jsonPrimitive?.longOrNull
                    require(!it["path"]?.jsonPrimitive?.content.isNullOrBlank() && size != null &&
                        size > 0 &&
                        it["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                        "ULTRA_KERNEL_SOURCE_QUALIFICATION_IDENTITY"
                    }
                }
            fun dependencyIdentity(value: JsonElement): JsonObject = value.jsonObject.also {
                if (it["bytes"]?.jsonPrimitive?.longOrNull != 0L) identity(it) else {
                    val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                    require(!it["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                        it["sha256"]?.jsonPrimitive?.content == empty &&
                        it["archivePath"]?.jsonPrimitive?.content == "build-dependencies/files/$empty.bin") {
                        "ULTRA_KERNEL_SOURCE_QUALIFICATION_DEPENDENCY_IDENTITY"
                    }
                }
            }
            fun same(a: JsonObject, b: JsonObject) = a["bytes"] == b["bytes"] && a["sha256"] == b["sha256"]
            fun basename(item: JsonObject) = item.getValue("path").jsonPrimitive.content.replace('\\', '/').substringAfterLast('/')
            val inputs = manifest.getValue("inputs").jsonObject
            fun selected(item: JsonObject) {
                val path = item.getValue("path").jsonPrimitive.content.replace('\\', '/')
                val matches = inputs.entries.filter { (name, _) ->
                    path == name.replace('\\', '/') || path.endsWith("/" + name.replace('\\', '/'))
                }
                val longest = matches.maxOfOrNull { it.key.replace('\\', '/').length }
                require(matches.singleOrNull { it.key.replace('\\', '/').length == longest }?.value == item["sha256"]) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_SELECTED"
                }
            }
            require(proof["schema"]?.jsonPrimitive?.content == "maplesu-runtime-source-qualification/1" &&
                listOf("metadataComplete", "factoryBound", "providerBound", "lifecycleBound").all { flag(proof, it) == true } &&
                listOf("targetQualified", "deviceQualified").all { flag(proof, it) == false } &&
                proof.getValue("missing").jsonArray.isEmpty()) {
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_INCOMPLETE:" + proof["missing"]?.jsonArray?.firstOrNull().toString()
            }
            val management = reference.getValue("managementAbi").jsonObject
            val freeze = management.getValue("upstreamSourceFreeze").jsonObject
            val selection = proof.getValue("sourceSelection").jsonObject
            val digest = UltraKernelSpec.sha256(elf)
            require(selection["claimSha256"]?.jsonPrimitive?.content == digest &&
                same(identity(selection["buildInputs"]), identity(management["buildInputIdentity"])) &&
                selection["host"] == freeze["hostDeploymentEvolution"] &&
                selection["runtime"] == freeze["coreRuntimeEvolution"] && selection["runtime"] is JsonObject) {
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_GENERATION"
            }
            for ((key, schema) in mapOf("host" to "maplesu-host-deployment-source-evolution/1",
                "runtime" to "maplesu-core-runtime-source-evolution/1")) {
                val chosen = selection.getValue(key).jsonObject
                require(chosen["schema"]?.jsonPrimitive?.content == schema && flag(chosen, "targetQualified") == false &&
                    flag(chosen, "productionWritten") == false) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_SELECTION:$key" }
                selected(identity(chosen["identity"]))
                val rows = chosen.getValue("selectedInputs").jsonArray.map { it.jsonObject }
                require(rows.isNotEmpty() && rows.map { it.getValue("logicalPath").jsonPrimitive.content }.distinct().size == rows.size) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_SELECTION_ROWS:$key"
                }
                if (key == "host") rows.forEach { selected(identity(it["after"])) }
                else for (row in rows) {
                    for (role in listOf("rootCurrent", "coreSelectedBefore", "coreFrozenAfter", "mergedAfter"))
                        row[role]?.takeUnless { it is JsonNull }?.let { selected(identity(it)) }
                    require(row["coreFrozenAfter"] is JsonObject && row["mergedAfter"] is JsonObject &&
                        (row["rootCurrent"] is JsonNull) == (row["coreSelectedBefore"] is JsonNull)) {
                        "ULTRA_KERNEL_SOURCE_QUALIFICATION_RUNTIME_ROLES"
                    }
                }
            }
            val policy = proof.getValue("cfiPolicy").jsonObject
            require(flag(policy, "kcfiEnabled") == (mode == "kcfi") &&
                flag(policy, "kcfiOptionPresent") == (mode == "kcfi") && flag(policy, "normalizeIntegers") == normalize) {
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_CFI"
            }
            val config = identity(proof["configIdentity"])
            val reports = listOfNotNull(core, reference["suProviderAbi"]?.jsonObject,
                reference["hostRuntimeAbi"]?.jsonObject, management["uidSnapshotAbi"]?.jsonObject)
            val objects = proof.getValue("objects").jsonObject
            val objectNames = setOf("manager_resident_host", "manager_management", "manager_uid_gateway", "manager_uid_snapshot",
                "manager_su_runtime_factory", "su_observed_install_source", "su_durable_authority", "su_durable_modern",
                "su_install_registry", "su_profile_execution")
            require(objects.keys == objectNames) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_OBJECTS" }
            var kernelConfig = reference["taskProfileAbi"]?.jsonObject?.get("configIdentity")?.let(::identity)
            for ((name, value) in objects) {
                val item = value.jsonObject
                val source = identity(item["source"])
                require(basename(source) == "$name.c" && inputs.entries.any { (path, hash) ->
                    path.replace('\\', '/').substringAfterLast('/') == "$name.c" && hash == source["sha256"]
                }) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_SOURCE:$name" }
                for ((key, array) in mapOf("source" to "sourceIdentities", "object" to "objectIdentities",
                    "command" to "commandIdentities", "dependencyReceipt" to "dependencyReceiptIdentities")) {
                    val actual = identity(item[key])
                    val suffix = when (key) { "source" -> "$name.c"; "object" -> "$name.o";
                        "command" -> ".$name.o.cmd"; else -> "$name.json" }
                    require(basename(actual) == suffix) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_OBJECT_PATH:$name:$key" }
                    val matches = reports.flatMap { it[array]?.jsonArray.orEmpty() }.map { identity(it) }
                        .filter { basename(it) == suffix }
                    require((name == "manager_resident_host" || matches.isNotEmpty()) && matches.all { same(it, actual) }) {
                        "ULTRA_KERNEL_SOURCE_QUALIFICATION_OBJECT_GENERATION:$name:$key"
                    }
                }
                val deps = item.getValue("dependencies").jsonArray.map(::dependencyIdentity)
                val dependencyConfig = deps.singleOrNull { basename(it) == ".config" }?.let(::identity)
                val expectedConfig = kernelConfig
                require(deps.isNotEmpty() && deps.any { same(it, source) } && dependencyConfig != null &&
                    dependencyConfig["archivePath"]?.jsonPrimitive?.content ==
                        "build-dependencies/files/${dependencyConfig.getValue("sha256").jsonPrimitive.content}.bin" &&
                    (expectedConfig == null || same(dependencyConfig, expectedConfig) &&
                        dependencyConfig.getValue("path").jsonPrimitive.content.replace('\\', '/') ==
                            expectedConfig.getValue("path").jsonPrimitive.content.replace('\\', '/'))) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_DEPENDENCIES:$name"
                }
                kernelConfig = dependencyConfig
                require(item["dependencyCount"]?.jsonPrimitive?.intOrNull == deps.size) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_DEPENDENCY_COUNT:$name"
                }
            }
            fun contracts(owner: JsonObject, expected: Set<String>) {
                val contracts = owner.getValue("sourceContracts").jsonObject
                require(contracts.keys.containsAll(expected)) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_CONTRACTS" }
                for ((name, value) in contracts) {
                    val item = value.jsonObject
                    require(item["function"]?.jsonPrimitive?.content == name &&
                        (item["sourceBytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0 &&
                        item["sourceSha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true &&
                        item.getValue("requiredExpressions").jsonArray.isNotEmpty() &&
                        item.getValue("requiredExpressions").jsonArray.all { it.jsonPrimitive.content.isNotBlank() }) {
                        "ULTRA_KERNEL_SOURCE_QUALIFICATION_CONTRACT:$name"
                    }
                }
            }
            fun expressions(owner: JsonObject, name: String, required: Collection<String>) {
                require(owner.getValue("sourceContracts").jsonObject.getValue(name).jsonObject
                    .getValue("requiredExpressions").jsonArray.map { it.jsonPrimitive.content }.containsAll(required)) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_EXPRESSIONS:$name"
                }
            }
            val sourceCallbacks = mapOf("pin_current" to "msu_observed_pin_current", "put_install" to "msu_observed_put_install",
                "read_grant" to "msu_observed_read_grant", "check_closed" to "msu_observed_check_closed",
                "quiesce" to "msu_observed_quiesce", "put_owner" to "msu_observed_put_owner",
                "validate_producer" to "msu_observed_validate_producer")
            val authorityCallbacks = mapOf("read_grant" to "msu_durable_grant", "put_owner" to "msu_durable_put", "list_uids" to "msu_durable_list")
            contracts(proof, setOf("msu_su_observed_create", "msu_su_durable_authority_create",
                "msu_manager_resident_init", "msu_manager_resident_discard", "msu_manager_resident_dispatch",
                "msu_su_runtime_init", "create_entries", "msu_su_runtime_close", "msu_uid_gateway_discard_host_unpublished",
                "msu_uid_snapshot_runtime_discard", "msu_su_lease_begin", "msu_su_registry_destroy", "msu_credential_replace_profile"))
            expressions(proof, "msu_su_observed_create", sourceCallbacks.map { (member, name) -> "source.$member = $name" } +
                listOf("source.validate_profile = msu_su_profile_check_execution", "msu_su_registry_create("))
            expressions(proof, "msu_su_durable_authority_create", authorityCallbacks.map { (member, name) -> "selected.$member = $name" })
            val edges = mapOf(
                "observedSourceCallbacks" to sourceCallbacks.values.toSet(), "durableAuthorityCallbacks" to authorityCallbacks.values.toSet(),
                "manager_resident_host" to setOf("msu_management_init", "msu_su_runtime_init", "msu_su_runtime_close",
                    "msu_management_discard_unpublished", "msu_su_runtime_discarded", "msu_management_delivery_failed"),
                "manager_su_runtime_factory" to setOf("msu_runtime_service_register", "msu_runtime_service_revoke", "msu_uid_gateway_acquire",
                    "msu_su_registry_bind_gateway", "msu_su_observed_create", "msu_su_durable_authority_create", "msu_su_observed_start",
                    "msu_su_durable_authority_load", "msu_su_read_current"),
                "su_observed_install_source" to setOf("msu_su_registry_create", "msu_su_profile_check_execution"),
                "su_install_registry" to setOf("msu_uid_gateway_bind_reader", "msu_uid_gateway_acquire"),
                "su_durable_authority" to setOf("msu_su_modern_authority_list", "msu_su_modern_authority_grant"),
                "manager_management" to setOf("msu_uid_gateway_discard_host_unpublished", "commit_creds"))
            val relocations = proof.getValue("relocations").jsonObject
            val retained = proof.getValue("retainedFunctions").jsonObject
            val callbackOwners = mapOf("observedSourceCallbacks" to ("su_observed_install_source" to "msu_su_observed_create"),
                "durableAuthorityCallbacks" to ("su_durable_authority" to "msu_su_durable_authority_create"))
            fun callbackReference(row: JsonObject, symbol: String, name: String, named: Boolean): Boolean {
                val owner = callbackOwners[name]?.first ?: return false
                if (symbol !in edges.getValue(name)) return false
                val target = retained[symbol]?.jsonObject ?: return false
                val targetEntry = target.getValue("objectEntry").jsonObject
                val targetOffset = targetEntry["offset"]?.jsonPrimitive?.longOrNull ?: return false
                // The producer resolves STT_SECTION + addend to this exact retained function bucket.
                if (target["object"]?.jsonPrimitive?.content != owner || targetOffset < 0 ||
                    targetEntry["section"]?.jsonPrimitive?.content != ".text.$symbol" ||
                    row["addend"]?.jsonPrimitive?.longOrNull != (if (named) 0L else targetOffset)) return false
                val callers = row.getValue("callers").jsonArray
                if (callers.isEmpty()) return row["section"]?.jsonPrimitive?.content == ".eh_frame" &&
                    row["type"]?.jsonPrimitive?.intOrNull == 261
                if (callers.size != 1) return false
                val caller = retained[callers.single().jsonPrimitive.content]?.jsonObject ?: return false
                val entry = caller.getValue("objectEntry").jsonObject
                val start = entry["offset"]?.jsonPrimitive?.longOrNull ?: return false
                val size = entry["bytes"]?.jsonPrimitive?.longOrNull ?: return false
                val offset = row["offset"]?.jsonPrimitive?.longOrNull ?: return false
                return caller["object"]?.jsonPrimitive?.content == owner && entry["section"] == row["section"] &&
                    start >= 0 && size >= 4 && offset >= start && offset - start <= size - 4 && offset % 4 == 0L
            }
            fun verifyRelocations(actual: JsonObject, symbols: Set<String>, name: String) {
                require(actual.keys.containsAll(symbols)) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:$name" }
                for ((symbol, value) in actual) {
                    val rows = value.jsonArray
                    require(symbol.isNotBlank() && rows.isNotEmpty() && rows.all { entry ->
                        val row = entry.jsonObject
                        val target = (row["symbol"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        !row["section"]?.jsonPrimitive?.content.isNullOrBlank() &&
                            (row["offset"]?.jsonPrimitive?.longOrNull ?: -1) >= 0 &&
                            (row["type"]?.jsonPrimitive?.intOrNull ?: 0) > 0 &&
                            row["addend"]?.jsonPrimitive?.longOrNull != null &&
                            row.getValue("callers").jsonArray.all { it.jsonPrimitive.content.isNotBlank() } &&
                            (target == symbol || target == "") &&
                            (if (name in callbackOwners) callbackReference(row, symbol, name, target == symbol) else target == symbol)
                    } && (callbackOwners[name]?.let { (_, creator) -> rows.any {
                        creator in it.jsonObject.getValue("callers").jsonArray.map { caller -> caller.jsonPrimitive.content }
                    } } ?: true)) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:$name:$symbol" }
                }
            }
            for ((name, symbols) in edges) verifyRelocations(relocations.getValue(name).jsonObject, symbols, name)
            val requiredFunctions = setOf("msu_manager_resident_init", "msu_manager_resident_discard", "msu_manager_resident_dispatch",
                "msu_su_runtime_init", "msu_su_runtime_post_fs_data", "msu_su_runtime_close", "msu_su_runtime_discarded",
                "msu_su_observed_create", "msu_su_observed_start", "msu_su_observed_close", "msu_su_durable_authority_create",
                "msu_su_durable_authority_load", "msu_su_registry_bind_gateway", "msu_su_lease_begin", "msu_su_read_current",
                "msu_uid_snapshot_runtime_discard", "msu_uid_gateway_discard_host_unpublished", "msu_uid_gateway_acquire",
                "msu_runtime_service_register", "msu_runtime_service_revoke", "msu_credential_replace_profile_current")
            require(retained.keys.containsAll(requiredFunctions + sourceCallbacks.values + authorityCallbacks.values)) {
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RETAINED"
            }
            val functions = retained.mapValues { (name, value) ->
                val item = value.jsonObject
                require(item["object"]?.jsonPrimitive?.content in objectNames &&
                    item["signature"]?.jsonPrimitive?.content?.let { it.startsWith("f") && it.length <= 65536 } == true) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_FUNCTION:$name"
                }
                for (key in listOf("objectEntry", "finalEntry")) {
                    val entry = item.getValue(key).jsonObject
                    require(!entry["section"]?.jsonPrimitive?.content.isNullOrBlank() &&
                        (entry["offset"]?.jsonPrimitive?.longOrNull ?: -1) >= 0 &&
                        (entry["bytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_ENTRY:$name" }
                }
                val entry = item.getValue("finalEntry").jsonObject
                val typeid = if (mode == "kcfi") item.getValue("finalTypeid").jsonPrimitive.long.also {
                    require(it in 0..0xffffffffL && item["objectTypeid"]?.jsonPrimitive?.longOrNull == it) {
                        "ULTRA_KERNEL_SOURCE_QUALIFICATION_PREFIX:$name"
                    }
                }.toInt() else null
                require(mode == "kcfi" || item["finalTypeid"] == null && item["objectTypeid"] == null) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_PREFIX_DISABLED:$name"
                }
                RetainedFunction(entry.getValue("section").jsonPrimitive.content, entry.getValue("offset").jsonPrimitive.int,
                    entry.getValue("bytes").jsonPrimitive.int, typeid)
            }
            UltraKernelManagerClaimCarrier.verifyRetainedFunctions(elf, functions)
            val callbacks = proof.getValue("callbackAbi").jsonObject
            val callbackDefinitions = sourceCallbacks.mapKeys { "source." + it.key } + authorityCallbacks.mapKeys { "authority." + it.key }
            require(callbacks.keys == callbackDefinitions.keys) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_CALLBACKS" }
            for ((name, value) in callbacks) {
                val callback = value.jsonObject
                val definition = callback.getValue("definition").jsonPrimitive.content
                val signature = callback.getValue("signature").jsonPrimitive.content
                require(definition == callbackDefinitions.getValue(name) && signature.startsWith("*f") && signature.length <= 65536 &&
                    retained[definition]?.jsonObject?.get("signature")?.jsonPrimitive?.content?.let { signature == "*" + it } == true) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_CALLBACK:$name"
                }
            }
            for (report in reports) report["sourceQualification"]?.let {
                require(it == proof) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_REPORT_GENERATION" }
            }
            val nativeReader = reference.getValue("suProviderAbi").jsonObject.getValue("nativeReaderBinding").jsonObject
            require(nativeReader["mode"]?.jsonPrimitive?.content == "same-kernel-C-factory" && flag(nativeReader, "sourceQualified") == true &&
                flag(nativeReader, "targetQualified") == false && flag(nativeReader, "deviceQualified") == false &&
                nativeReader["entry"] == retained["msu_su_read_current"]) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_READER" }
            for (owner in listOfNotNull(management, management["uidSnapshotAbi"]?.jsonObject, management["procattrAbi"]?.jsonObject)) {
                val context = owner.getValue("executionContext").jsonObject
                require(context["schema"]?.jsonPrimitive?.content == "maplesu-sleepable-source-qualification/1" &&
                    flag(context, "sourceQualified") == true && listOf("qualified", "targetQualified", "deviceQualified").all { flag(context, it) == false } &&
                    context.getValue("missing").jsonArray.isEmpty() && context["cfiPolicy"] == policy) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_CONTEXT:" + context["missing"]?.jsonArray?.firstOrNull().toString()
                }
                val contextObjects = context.getValue("objects").jsonObject
                require(contextObjects.keys == setOf("manager_resident_host", "manager_management")) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_CONTEXT_OBJECTS"
                }
                for ((name, value) in contextObjects) for (key in listOf("source", "object", "command", "dependencyReceipt"))
                    require(same(identity(value.jsonObject[key]), identity(objects.getValue(name).jsonObject[key]))) {
                        "ULTRA_KERNEL_SOURCE_QUALIFICATION_CONTEXT_GENERATION:$name:$key"
                    }
                contracts(context, setOf("msu_resident_sleepable", "msu_management_sleepable", "msu_manager_resident_init",
                    "msu_manager_resident_discard", "msu_manager_resident_dispatch", "msu_owner_request", "msu_management_dispatch",
                    "msu_management_discard_unpublished", "msu_management_delivery_failed", "msu_management_procattr", "msu_management_procattr_file"))
                expressions(context, "msu_management_procattr", listOf("/proc/thread-self/attr/current", "/proc/thread-self/attr/exec",
                    "O_WRONLY | O_NOFOLLOW | O_NONBLOCK | O_NOCTTY", "filp_open(", "msu_management_procattr_file(",
                    "msu_management_check(session)", "msu_dynamic_get_current_cred()", "vfs_write(",
                    "written == operation->length ? 0 : -EIO", "fput(file)"))
                expressions(context, "msu_management_procattr_file", listOf("PROC_SUPER_MAGIC", "file_operations, write", "proc_pid_attr_write"))
                val procattrConfigured = listOf("CONFIG_PROC_FS", "CONFIG_SECURITY").all {
                    management.getValue("config").jsonObject[it]?.jsonPrimitive?.content == "y"
                }
                require(flag(context, "procattrConfigured") == procattrConfigured) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_PROCATTR_CONFIG" }
                val procattrRelocations = context.getValue("procattrRelocations").jsonObject
                if (procattrConfigured) verifyRelocations(procattrRelocations,
                    setOf("filp_open", "vfs_write", "fput", "proc_pid_attr_write"), "procattr")
                else require(procattrRelocations.isEmpty()) { "ULTRA_KERNEL_SOURCE_QUALIFICATION_PROCATTR_DISABLED" }
                for (name in listOf("msu_resident_sleepable", "msu_management_sleepable")) require(
                    context.getValue("sourceContracts").jsonObject.getValue(name).jsonObject.getValue("requiredExpressions").jsonArray
                        .map { it.jsonPrimitive.content }.containsAll(listOf("in_task()", "!in_atomic()", "!irqs_disabled()"))) {
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_SLEEPABLE:$name"
                }
            }
            return SourceQualification(digest, config.getValue("sha256").jsonPrimitive.content)
        }

        private fun verifyRuntimeProvenance(report: JsonObject, payload: JsonObject, elf: ByteArray,
            reference: ReferenceAbi?, requiredSources: Set<String>, sectionFlags: Long = 3) {
            fun identity(value: JsonElement?): JsonObject =
                (value?.jsonObject ?: error("ULTRA_KERNEL_RUNTIME_LAYOUT_IDENTITY")).also {
                    val bytes = it["bytes"]?.jsonPrimitive?.longOrNull
                    require(!it["path"]?.jsonPrimitive?.content.isNullOrBlank() && bytes != null &&
                        bytes > 0 &&
                        it["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_IDENTITY"
                    }
                }
            fun dependencyIdentity(value: JsonElement): JsonObject = value.jsonObject.also {
                if (it["bytes"]?.jsonPrimitive?.longOrNull != 0L) identity(it) else {
                    val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                    require(!it["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                        it["sha256"]?.jsonPrimitive?.content == empty &&
                        it["archivePath"]?.jsonPrimitive?.content == "build-dependencies/files/$empty.bin") {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_DEPENDENCY_IDENTITY"
                    }
                }
            }
            fun same(a: JsonObject, b: JsonObject) = a["bytes"] == b["bytes"] && a["sha256"] == b["sha256"]
            val digest = UltraKernelSpec.sha256(elf)
            require(reference != null && report["schemaVersion"]?.jsonPrimitive?.intOrNull == 1 &&
                report["sectionFlags"]?.jsonPrimitive?.longOrNull == sectionFlags &&
                report["claimSha256"]?.jsonPrimitive?.content == digest &&
                payload["sha256"]?.jsonPrimitive?.content == digest && payload["bytes"]?.jsonPrimitive?.intOrNull == elf.size) {
                "ULTRA_KERNEL_RUNTIME_LAYOUT_MANIFEST"
            }
            val selection = identity(report["sourceSelectionIdentity"])
            val evolution = payload.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
                .getValue("upstreamSourceFreeze").jsonObject.getValue("runtimeLayoutEvolution").jsonObject
            require(evolution["schema"]?.jsonPrimitive?.content == "maplesu-claim-runtime-layout-source-selection/1" &&
                evolution["sourceOwnerFrozen"]?.jsonPrimitive?.booleanOrNull == true &&
                evolution["targetQualified"]?.jsonPrimitive?.booleanOrNull == false &&
                same(selection, identity(evolution["identity"]))) { "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCE_SELECTION" }
            val selectedSources = evolution.getValue("files").jsonArray.map { it.jsonObject }.associateBy {
                it.getValue("logicalPath").jsonPrimitive.content
            }
            val taskSuccessors = payload.getValue("referenceAbi").jsonObject["taskProfileAbi"]?.jsonObject
                ?.get("implementation")?.jsonObject?.get("sourceSuccessor")?.jsonObject?.get("files")?.jsonArray
                ?.map { it.jsonObject }?.associateBy { it.getValue("logicalPath").jsonPrimitive.content }.orEmpty()
            val sources = report.getValue("sourceIdentities").jsonObject.mapValues { identity(it.value) }
            require(sources.keys.containsAll(requiredSources)) { "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCES" }
            for ((path, value) in sources) {
                val prior = identity(selectedSources[path]?.get("after"))
                val successor = taskSuccessors[path]
                val chosen = successor?.let {
                    val before = identity(it["before"])
                    require(same(before, prior) && before.getValue("path").jsonPrimitive.content.replace('\\', '/') ==
                        prior.getValue("path").jsonPrimitive.content.replace('\\', '/')) {
                        "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCE_SUCCESSOR:$path"
                    }
                    identity(it["after"])
                } ?: prior
                require(value.getValue("path").jsonPrimitive.content.replace('\\', '/')
                    .endsWith(path.substringAfterLast('/')) && same(value, chosen) &&
                    (successor == null || value.getValue("path").jsonPrimitive.content.replace('\\', '/') ==
                        chosen.getValue("path").jsonPrimitive.content.replace('\\', '/'))) {
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCE_PATH"
                }
            }
            for (key in listOf("objectIdentity", "commandIdentity", "dependencyReceiptIdentity", "configIdentity"))
                identity(report[key])
            val dependencies = report.getValue("dependencies").jsonArray.map(::dependencyIdentity)
            require(dependencies.isNotEmpty() && (sources.values + identity(report["configIdentity"])).all { source ->
                dependencies.any { same(it, source) }
            }) { "ULTRA_KERNEL_RUNTIME_LAYOUT_DEPENDENCIES" }
        }

        private fun runtimeWord(value: JsonElement): Int {
            val number = value.jsonPrimitive.long
            require(number in -1L..0xffffffffL) { "ULTRA_KERNEL_RUNTIME_LAYOUT_WORD" }
            return number.toInt()
        }

        private fun parseRuntimeLayout(report: JsonObject, payload: JsonObject, elf: ByteArray,
            reference: ReferenceAbi?): RuntimeLayoutFacts {
            verifyRuntimeProvenance(report, payload, elf, reference, setOf(
                "runtime/ultra/identity/kernel/manager_dynamic_layout.c", "runtime/ultra/identity/kernel/manager_dynamic_layout.h"))
            require(report["symbol"]?.jsonPrimitive?.content == "msu_runtime_layout" &&
                report["section"]?.jsonPrimitive?.content == ".msu.runtime.layout") { "ULTRA_KERNEL_RUNTIME_LAYOUT_MANIFEST" }
            val offset = report.getValue("offset").jsonPrimitive.int
            val bytes = report.getValue("bytes").jsonPrimitive.int
            val compiledSha256 = report.getValue("compiledSha256").jsonPrimitive.content
            val words = report.getValue("values").jsonArray.map(::runtimeWord)
            val fields = report.getValue("fields").jsonArray.mapIndexed { index, value ->
                val item = value.jsonObject
                val type = item.getValue("type").jsonPrimitive.content
                val member = item.getValue("member").jsonPrimitive.content
                val key = item.getValue("key").jsonPrimitive.content
                val at = 6 + index * 3
                require(type.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) &&
                    member.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) &&
                    item["slot"]?.jsonPrimitive?.intOrNull == index &&
                    key.substringBefore('.') == type &&
                    item["offsetIndex"]?.jsonPrimitive?.intOrNull == at &&
                    item["widthIndex"]?.jsonPrimitive?.intOrNull == at + 1 &&
                    item["ownerSizeIndex"]?.jsonPrimitive?.intOrNull == at + 2 && at + 2 < words.size &&
                    runtimeWord(item.getValue("compiledOffset")) == words[at] &&
                    runtimeWord(item.getValue("compiledBytes")) == words[at + 1] &&
                    runtimeWord(item.getValue("compiledOwnerBytes")) == words[at + 2]) { "ULTRA_KERNEL_RUNTIME_LAYOUT_SLOT:$index" }
                val signature = item["signature"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                val alignment = item.getValue("alignment").jsonPrimitive.int
                require(if (words[at] == -1) words[at + 1] == 0 && words[at + 2] == 0 && signature == null && alignment == 0
                    else words[at] >= 0 && words[at + 1] > 0 && words[at].toLong() + words[at + 1] <= words[at + 2] &&
                        signature != null && signature.length in 1..65536 && alignment in 1..4096 &&
                        alignment and (alignment - 1) == 0 && words[at] % alignment == 0) { "ULTRA_KERNEL_RUNTIME_LAYOUT_FIELD:$type.$member" }
                RuntimeLayoutField(type, member, signature, alignment, key).also { it.targetKeys() }
            }
            require(offset >= 0 && offset % 4 == 0 && bytes in 24..65536 && bytes % 4 == 0 &&
                fields.size in 1..1024 && fields.map { it.key }.distinct().size == fields.size &&
                words.size == 6 + fields.size * 3 && words.size * 4 == bytes &&
                words.take(6) == listOf(0x4d53554c, 1, bytes, fields.size, 0, 0) &&
                compiledSha256.matches(Regex("[0-9a-f]{64}"))) { "ULTRA_KERNEL_RUNTIME_LAYOUT_HEADER" }
            return RuntimeLayoutFacts(offset, bytes, compiledSha256, words, fields)
        }

        private fun parseRuntimeProfiles(report: JsonObject, payload: JsonObject, elf: ByteArray,
            reference: ReferenceAbi?): RuntimeProfilesFacts {
            verifyRuntimeProvenance(report, payload, elf, reference, setOf(
                "runtime/ultra/identity/kernel/manager_su_runtime_target.c", "runtime/ultra/identity/kernel/manager_su_runtime_target_profile.h"))
            require(report["symbol"]?.jsonPrimitive?.content == "msu_su_runtime_profiles" &&
                report["section"]?.jsonPrimitive?.content == ".msu.su-runtime.profiles") { "ULTRA_KERNEL_SU_RUNTIME_PROFILES_MANIFEST" }
            val offset = report.getValue("offset").jsonPrimitive.int
            val bytes = report.getValue("bytes").jsonPrimitive.int
            val digest = report.getValue("compiledSha256").jsonPrimitive.content
            val words = report.getValue("values").jsonArray.map(::runtimeWord)
            val names = report.getValue("names").jsonArray.map { it.jsonPrimitive.content }
            val expectedNames = listOf("do_execveat_common", "__arm64_sys_newfstatat", "__arm64_sys_faccessat",
                "__arm64_sys_statx", "__arm64_sys_faccessat2")
            val filename = report.getValue("filename").jsonObject.let {
                RuntimeProfileFilename(it.getValue("offset").jsonPrimitive.int, it.getValue("bytes").jsonPrimitive.int,
                    it.getValue("ownerBytes").jsonPrimitive.int, it.getValue("signature").jsonPrimitive.content,
                    it.getValue("alignment").jsonPrimitive.int)
            }
            val targetFacts = report.getValue("targetFacts").jsonArray.map(::runtimeWord)
            val execAbi = report.getValue("execAbi").jsonPrimitive.content
            val execLayout = report.getValue("execLayout").jsonObject
            val userArgPtrBytes = execLayout.getValue("userArgPtrBytes").jsonPrimitive.int
            val flagsRegister = execLayout.getValue("flagsRegister").jsonPrimitive.int
            val registersSignature = report.getValue("registersSignature").jsonPrimitive.content
            require(offset >= 0 && offset % 4 == 0 && bytes == 372 && words.size * 4 == bytes &&
                words.take(5) == listOf(0x4d535550, 1, bytes, 5, 0) && words[5] in 0..1 && words[6] == filename.offset &&
                words[7] == 0 && names == expectedNames && digest.matches(Regex("[0-9a-f]{64}")) &&
                filename.offset >= 0 && filename.bytes == 8 && filename.ownerBytes >= filename.offset.toLong() + 8 &&
                filename.signature.isNotEmpty() && filename.signature.length <= 65536 && filename.alignment == 8 &&
                filename.offset % filename.alignment == 0 && targetFacts.size == 9 &&
                targetFacts.take(4) == listOf(1, filename.ownerBytes, filename.offset, filename.bytes) &&
                targetFacts[4] > 0 && targetFacts[5] >= 0 && targetFacts[6] == 8 &&
                targetFacts[5].toLong() + targetFacts[6] <= targetFacts[4] && targetFacts.takeLast(2) == listOf(0, 0) &&
                execAbi.startsWith("*f") && execAbi.endsWith(")") && execAbi.length <= 65536 &&
                userArgPtrBytes in setOf(8, 16) && flagsRegister == 2 + 2 * userArgPtrBytes / 8 &&
                registersSignature.startsWith("a") && registersSignature.length <= 65536) {
                "ULTRA_KERNEL_SU_RUNTIME_PROFILES_HEADER"
            }
            for (index in names.indices) require((18 + index * 17..24 + index * 17).all { words[it] == 0 }) {
                "ULTRA_KERNEL_SU_RUNTIME_PROFILES_UNBOUND:$index"
            }
            return RuntimeProfilesFacts(offset, bytes, digest, words, names, filename, targetFacts, execAbi,
                userArgPtrBytes, flagsRegister, registersSignature)
        }

        private fun parseSessionAbi(report: JsonObject, payload: JsonObject, elf: ByteArray,
            reference: ReferenceAbi?): SessionAbiFacts {
            verifyRuntimeProvenance(report, payload, elf, reference, setOf(
                "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/su_exec_session.c",
                "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/su_exec_session.h"), sectionFlags = 2)
            return decodeSessionAbi(report)
        }

        internal fun decodeSessionAbi(report: JsonObject): SessionAbiFacts {
            require(report["schemaVersion"]?.jsonPrimitive?.intOrNull == 1 &&
                report["sectionFlags"]?.jsonPrimitive?.longOrNull == 2L &&
                report["symbol"]?.jsonPrimitive?.content == "msu_su_session_abi_words" &&
                report["section"]?.jsonPrimitive?.content == ".msu.session.abi") { "ULTRA_KERNEL_SESSION_ABI_MANIFEST" }
            val offset = report.getValue("offset").jsonPrimitive.int
            val bytes = report.getValue("bytes").jsonPrimitive.int
            val digest = report.getValue("compiledSha256").jsonPrimitive.content
            val words = report.getValue("values").jsonArray.map(::runtimeWord)
            require(offset >= 0 && offset % 4 == 0 && bytes == 96 && words.size == 24 &&
                words.take(3) == listOf(0x4d535553, 1, bytes) && digest.matches(Regex("[0-9a-f]{64}"))) {
                "ULTRA_KERNEL_SESSION_ABI_HEADER"
            }
            fun layout(name: String, type: String, expected: Set<String>): Pair<UltraKernelResidentAbi.CompiledLayout, Int> {
                val value = report.getValue(name).jsonObject
                val size = value.getValue("bytes").jsonPrimitive.int
                val alignment = value.getValue("alignment").jsonPrimitive.int
                val signature = value.getValue("signature").jsonPrimitive.content
                val fields = value.getValue("members").jsonObject.mapValues { (_, item) ->
                    item.jsonObject.let { member -> UltraKernelResidentAbi.CompiledMember(
                        member.getValue("offset").jsonPrimitive.int, member.getValue("bytes").jsonPrimitive.int,
                        member.getValue("signature").jsonPrimitive.content) }
                }
                require(size > 0 && alignment in 1..4096 && alignment and (alignment - 1) == 0 && size % alignment == 0 &&
                    signature.startsWith("s$type:$size{") && signature.length <= 65536 && fields.keys == expected &&
                    fields.values.all { it.offset >= 0 && it.bytes > 0 && it.offset.toLong() + it.bytes <= size &&
                        it.signature.isNotEmpty() && it.signature.length <= 65536 }) { "ULTRA_KERNEL_SESSION_ABI_COMPILED_TYPE:$type" }
                return UltraKernelResidentAbi.CompiledLayout(size, signature, fields) to alignment
            }
            val group = layout("groupInfo", "group_info", setOf("usage", "ngroups"))
            val gid = report.getValue("groupInfo").jsonObject.getValue("gid").jsonObject.let {
                SessionGid(it.getValue("offset").jsonPrimitive.int, it.getValue("elementBytes").jsonPrimitive.int,
                    it.getValue("alignment").jsonPrimitive.int, it.getValue("signature").jsonPrimitive.content)
            }
            val members = linkedMapOf("owner" to 14, "unlocked_ioctl" to 16, "flush" to 20, "release" to 22)
            if (words[18] != -1) members["compat_ioctl"] = 18
            else require(words[19] == 0) { "ULTRA_KERNEL_SESSION_ABI_COMPAT_ABSENT" }
            val operations = layout("fileOperations", "file_operations", members.keys)
            require(words[3] == group.first.bytes && words[4] == group.second &&
                words[5] == group.first.members.getValue("usage").offset && words[6] == group.first.members.getValue("usage").bytes &&
                words[7] == group.first.members.getValue("ngroups").offset && words[8] == group.first.members.getValue("ngroups").bytes &&
                words[9] == gid.offset && words[10] == gid.elementBytes && words[11] == gid.alignment &&
                gid.offset >= 0 && gid.offset <= group.first.bytes && gid.elementBytes > 0 &&
                gid.alignment in 1..4096 && gid.alignment and (gid.alignment - 1) == 0 &&
                gid.offset % gid.alignment == 0 && gid.elementBytes % gid.alignment == 0 &&
                gid.signature.startsWith("a0:") && gid.signature.length <= 65536 &&
                group.first.signature.endsWith("${gid.offset.toLong() * 8}=${gid.signature};}") &&
                words[12] == operations.first.bytes && words[13] == operations.second && members.all { (member, at) ->
                    operations.first.members.getValue(member).let { it.offset == words[at] && it.bytes == words[at + 1] }
                }) { "ULTRA_KERNEL_SESSION_ABI_COMPILED_WORDS" }
            val policy = report.getValue("cfiPolicy").jsonObject
            val mode = policy.getValue("mode").jsonPrimitive.content
            val normalized = policy.getValue("normalizeIntegers").jsonPrimitive.boolean
            require(mode in setOf("none", "kcfi", "legacy-lto-cfi")) { "ULTRA_KERNEL_SESSION_ABI_CFI_MODE" }
            val expected = linkedMapOf("msu_su_session_ioctl" to "unlocked_ioctl",
                "msu_su_session_flush" to "flush", "msu_su_session_release" to "release",
                "msu_su_session_free_rcu" to "callback_head.func", "msu_su_session_resume" to "callback_head.func",
                "msu_su_session_exit_work" to "work_struct.func")
            if ("compat_ioctl" in members) expected["msu_su_session_compat_ioctl"] = "compat_ioctl"
            fun typeid(value: JsonElement?): Int? = value?.takeUnless { it is JsonNull }?.jsonPrimitive?.long?.let {
                require(it in 0..0xffffffffL) { "ULTRA_KERNEL_SESSION_ABI_CALLBACK_TYPEID" }; it.toInt()
            }
            val callbacks = report.getValue("callbacks").jsonObject.mapValues { (name, element) ->
                val item = element.jsonObject
                val callback = SessionCallback(item.getValue("symbol").jsonPrimitive.content,
                    item.getValue("member").jsonPrimitive.content, item.getValue("signature").jsonPrimitive.content,
                    item.getValue("offset").jsonPrimitive.int, typeid(item["kcfiPrefix"]), typeid(item["objectKcfiType"]))
                require(callback.symbol == name && expected[name] == callback.member && callback.offset >= 0 &&
                    callback.offset % 4 == 0 && callback.signature.startsWith("f") && callback.signature.length <= 65536 &&
                    (if (mode == "kcfi") callback.kcfiPrefix != null && callback.kcfiPrefix == callback.objectKcfiType &&
                        item["objectKcfiTypeSource"]?.jsonPrimitive?.content == "actual-object-entry-prefix"
                    else callback.kcfiPrefix == null && callback.objectKcfiType == null)) { "ULTRA_KERNEL_SESSION_ABI_CALLBACK:$name" }
                if (callback.member in members) require(operations.first.members.getValue(callback.member).signature == "*" + callback.signature) {
                    "ULTRA_KERNEL_SESSION_ABI_CALLBACK_MEMBER:$name"
                }
                callback
            }
            require(callbacks.keys == expected.keys) { "ULTRA_KERNEL_SESSION_ABI_CALLBACKS" }
            return SessionAbiFacts(offset, bytes, digest, words, group.first, group.second, gid,
                operations.first, operations.second, callbacks, mode, normalized)
        }

        private fun parseBootEventsAbi(report: JsonObject, payload: JsonObject, elf: ByteArray,
            reference: ReferenceAbi?): BootEventsAbiFacts {
            verifyRuntimeProvenance(report, payload, elf, reference, setOf(
                "runtime/ultra/boot/init-bridge/boot_events.c", "runtime/ultra/boot/init-bridge/boot_events.h"), sectionFlags = 2)
            return decodeBootEventsAbi(report)
        }

        internal fun decodeBootEventsAbi(report: JsonObject): BootEventsAbiFacts {
            require(report["symbol"]?.jsonPrimitive?.content == "msu_boot_events_abi_words" &&
                report["section"]?.jsonPrimitive?.content == ".msu.boot_events.abi" &&
                report["sectionFlags"]?.jsonPrimitive?.longOrNull == 2L &&
                report["schemaVersion"]?.jsonPrimitive?.intOrNull == 1) { "ULTRA_KERNEL_BOOT_EVENTS_ABI_MANIFEST" }
            val offset = report.getValue("offset").jsonPrimitive.int
            val bytes = report.getValue("bytes").jsonPrimitive.int
            val digest = report.getValue("compiledSha256").jsonPrimitive.content
            val words = report.getValue("values").jsonArray.map(::runtimeWord)
            require(offset >= 0 && offset % 4 == 0 && bytes == 152 && words.size == 38 &&
                words.take(4) == listOf(0x4d534245, 1, bytes, 1) && digest.matches(Regex("[0-9a-f]{64}"))) {
                "ULTRA_KERNEL_BOOT_EVENTS_ABI_HEADER"
            }
            val definitions = linkedMapOf(
                "file_operations" to (4 to linkedMapOf("owner" to 6, "read" to 8, "read_iter" to 10, "release" to 12)),
                "kprobe" to (14 to linkedMapOf("symbol_name" to 16, "pre_handler" to 18)),
                "kiocb" to (20 to linkedMapOf("ki_filp" to 22, "ki_pos" to 24, "ki_complete" to 26, "ki_flags" to 28)),
                "iov_iter" to (30 to linkedMapOf("count" to 32)), "pt_regs" to (34 to linkedMapOf("regs" to 36)))
            val layouts = report.getValue("layouts").jsonObject.mapValues { (type, element) ->
                val definition = definitions[type] ?: error("ULTRA_KERNEL_BOOT_EVENTS_ABI_TYPE:$type")
                val item = element.jsonObject
                val size = item.getValue("bytes").jsonPrimitive.int
                val alignment = item.getValue("alignment").jsonPrimitive.int
                val signature = item.getValue("signature").jsonPrimitive.content
                val members = item.getValue("members").jsonObject.mapValues { (_, field) ->
                    field.jsonObject.let { UltraKernelResidentAbi.CompiledMember(it.getValue("offset").jsonPrimitive.int,
                        it.getValue("bytes").jsonPrimitive.int, it.getValue("signature").jsonPrimitive.content) }
                }
                require(size > 0 && alignment in 1..4096 && alignment and (alignment - 1) == 0 && size % alignment == 0 &&
                    size == words[definition.first] && alignment == words[definition.first + 1] &&
                    signature.startsWith("s$type:$size{") && signature.length <= 65536 &&
                    members.keys == definition.second.keys && members.all { (member, field) ->
                        val at = definition.second.getValue(member)
                        field.offset >= 0 && field.bytes > 0 && field.offset.toLong() + field.bytes <= size &&
                            field.offset == words[at] && field.bytes == words[at + 1] &&
                            field.signature.isNotEmpty() && field.signature.length <= 65536
                    }) { "ULTRA_KERNEL_BOOT_EVENTS_ABI_COMPILED_TYPE:$type" }
                UltraKernelResidentAbi.CompiledLayout(size, signature, members)
            }
            require(layouts.keys == definitions.keys) { "ULTRA_KERNEL_BOOT_EVENTS_ABI_TYPES" }
            val policy = report.getValue("cfiPolicy").jsonObject
            val mode = policy.getValue("mode").jsonPrimitive.content
            val normalized = policy.getValue("normalizeIntegers").jsonPrimitive.boolean
            require(mode in setOf("none", "kcfi")) { "ULTRA_KERNEL_BOOT_EVENTS_ABI_CFI_MODE" }
            val expected = mapOf("boot_read" to "file_operations.read", "boot_read_iter" to "file_operations.read_iter",
                "boot_release" to "file_operations.release", "boot_read_pre" to "kprobe.pre_handler")
            fun typeid(value: JsonElement?): Int? = value?.takeUnless { it is JsonNull }?.jsonPrimitive?.long?.let {
                require(it in 0..0xffffffffL) { "ULTRA_KERNEL_BOOT_EVENTS_ABI_CALLBACK_TYPEID" }; it.toInt()
            }
            val callbacks = report.getValue("callbacks").jsonObject.mapValues { (name, element) ->
                val item = element.jsonObject
                val callback = SessionCallback(item.getValue("symbol").jsonPrimitive.content,
                    item.getValue("member").jsonPrimitive.content, item.getValue("signature").jsonPrimitive.content,
                    item.getValue("offset").jsonPrimitive.int, typeid(item["kcfiPrefix"]), typeid(item["objectKcfiType"]))
                require(name == callback.symbol && expected[name] == callback.member && callback.offset >= 0 &&
                    callback.offset % 4 == 0 && callback.signature.startsWith("f") && callback.signature.length <= 65536 &&
                    (if (mode == "kcfi") callback.kcfiPrefix != null && callback.kcfiPrefix == callback.objectKcfiType &&
                        item["objectKcfiTypeSource"]?.jsonPrimitive?.content == "actual-object-entry-prefix"
                    else callback.kcfiPrefix == null && callback.objectKcfiType == null) &&
                    layouts.getValue(callback.member.substringBefore('.')).members
                        .getValue(callback.member.substringAfter('.')).signature == "*" + callback.signature) {
                    "ULTRA_KERNEL_BOOT_EVENTS_ABI_CALLBACK:$name"
                }
                callback
            }
            require(callbacks.keys == expected.keys) { "ULTRA_KERNEL_BOOT_EVENTS_ABI_CALLBACKS" }
            return BootEventsAbiFacts(offset, bytes, digest, words, layouts, callbacks, mode, normalized)
        }

        private fun parseKsuCompatAbi(report: JsonObject, payload: JsonObject, elf: ByteArray,
            reference: ReferenceAbi?): KsuCompatAbiFacts {
            verifyRuntimeProvenance(report, payload, elf, reference, setOf(
                "runtime/ultra/identity/kernel/manager_ksu_compat.c", "runtime/ultra/identity/kernel/manager_ksu_compat.h"), sectionFlags = 2)
            return decodeKsuCompatAbi(report)
        }

        internal fun decodeKsuCompatAbi(report: JsonObject): KsuCompatAbiFacts {
            require(report["symbol"]?.jsonPrimitive?.content == "msu_ksu_compat_layout" &&
                report["section"]?.jsonPrimitive?.content == ".msu.ksu-compat.layout" &&
                report["sectionFlags"]?.jsonPrimitive?.longOrNull == 2L &&
                report["schemaVersion"]?.jsonPrimitive?.intOrNull == 1) { "ULTRA_KERNEL_KSU_COMPAT_ABI_MANIFEST" }
            val offset = report.getValue("offset").jsonPrimitive.int
            val bytes = report.getValue("bytes").jsonPrimitive.int
            val digest = report.getValue("compiledSha256").jsonPrimitive.content
            val words = report.getValue("values").jsonArray.map(::runtimeWord)
            require(offset >= 0 && offset % 4 == 0 && bytes == 56 && words.size == 14 && words[0] == 1 &&
                digest.matches(Regex("[0-9a-f]{64}"))) { "ULTRA_KERNEL_KSU_COMPAT_ABI_HEADER" }
            val compat = words[7] != -1 || words[8] != 0
            val fields = linkedMapOf("owner" to 3, "unlocked_ioctl" to 5, "release" to 9)
            if (compat) fields["compat_ioctl"] = 7
            val definitions = linkedMapOf("file_operations" to (1 to fields),
                "callback_head" to (11 to linkedMapOf("func" to 12)))
            val layouts = report.getValue("layouts").jsonObject.mapValues { (type, element) ->
                val definition = definitions[type] ?: error("ULTRA_KERNEL_KSU_COMPAT_ABI_TYPE:$type")
                val item = element.jsonObject
                val size = item.getValue("bytes").jsonPrimitive.int
                val signature = item.getValue("signature").jsonPrimitive.content
                val members = item.getValue("members").jsonObject.mapValues { (_, field) ->
                    field.jsonObject.let { UltraKernelResidentAbi.CompiledMember(it.getValue("offset").jsonPrimitive.int,
                        it.getValue("bytes").jsonPrimitive.int, it.getValue("signature").jsonPrimitive.content) }
                }
                require(size > 0 && size == words[definition.first] && signature.startsWith("s$type:$size{") &&
                    signature.length <= 65536 && members.keys == definition.second.keys && members.all { (member, field) ->
                        val at = definition.second.getValue(member)
                        field.offset >= 0 && field.bytes > 0 && field.offset.toLong() + field.bytes <= size &&
                            field.offset == words[at] && field.bytes == words[at + 1] &&
                            field.signature.isNotEmpty() && field.signature.length <= 65536
                    }) { "ULTRA_KERNEL_KSU_COMPAT_ABI_COMPILED_TYPE:$type" }
                if (type == "file_operations") {
                    val alignment = item.getValue("alignment").jsonPrimitive.int
                    require(alignment == words[2] && alignment in 1..4096 && alignment and (alignment - 1) == 0 &&
                        size % alignment == 0) { "ULTRA_KERNEL_KSU_COMPAT_ABI_ALIGNMENT" }
                }
                UltraKernelResidentAbi.CompiledLayout(size, signature, members)
            }
            require(layouts.keys == definitions.keys) { "ULTRA_KERNEL_KSU_COMPAT_ABI_TYPES" }
            val policy = report.getValue("cfiPolicy").jsonObject
            val mode = policy.getValue("mode").jsonPrimitive.content
            val normalized = policy.getValue("normalizeIntegers").jsonPrimitive.boolean
            require(mode in setOf("none", "kcfi")) { "ULTRA_KERNEL_KSU_COMPAT_ABI_CFI_MODE" }
            val expected = linkedMapOf("msu_ksu_ioctl" to "file_operations.unlocked_ioctl",
                "msu_ksu_release" to "file_operations.release", "msu_ksu_install_resume" to "callback_head.func")
            if (compat) expected["msu_ksu_compat_ioctl"] = "file_operations.compat_ioctl"
            fun typeid(value: JsonElement?): Int? = value?.takeUnless { it is JsonNull }?.jsonPrimitive?.long?.let {
                require(it in 0..0xffffffffL) { "ULTRA_KERNEL_KSU_COMPAT_ABI_CALLBACK_TYPEID" }; it.toInt()
            }
            val callbacks = report.getValue("callbacks").jsonObject.mapValues { (name, element) ->
                val item = element.jsonObject
                val callback = SessionCallback(item.getValue("symbol").jsonPrimitive.content,
                    item.getValue("member").jsonPrimitive.content, item.getValue("signature").jsonPrimitive.content,
                    item.getValue("offset").jsonPrimitive.int, typeid(item["kcfiPrefix"]), typeid(item["objectKcfiType"]))
                require(name == callback.symbol && expected[name] == callback.member && callback.offset >= 0 && callback.offset % 4 == 0 &&
                    callback.signature.startsWith("f") && callback.signature.length <= 65536 &&
                    (if (mode == "kcfi") callback.kcfiPrefix != null && callback.kcfiPrefix == callback.objectKcfiType &&
                        item["objectKcfiTypeSource"]?.jsonPrimitive?.content == "actual-object-entry-prefix"
                    else callback.kcfiPrefix == null && callback.objectKcfiType == null) &&
                    layouts.getValue(callback.member.substringBefore('.')).members
                        .getValue(callback.member.substringAfter('.')).signature == "*" + callback.signature) {
                    "ULTRA_KERNEL_KSU_COMPAT_ABI_CALLBACK:$name"
                }
                callback
            }
            require(callbacks.keys == expected.keys) { "ULTRA_KERNEL_KSU_COMPAT_ABI_CALLBACKS" }
            return KsuCompatAbiFacts(offset, bytes, digest, words, layouts, callbacks, mode, normalized)
        }

        private fun runtimeManifest(manifest: JsonObject, family: String, elf: ByteArray,
            read: (String, Int) -> ByteArray): JsonObject {
            val version = manifest["schemaVersion"]?.jsonPrimitive
            require(version == null || !version.isString && version.intOrNull in setOf(1, 2)) {
                "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_SCHEMA"
            }
            val descriptor = manifest["payloads"]?.jsonObject?.get(family)?.jsonObject
            if (version?.intOrNull != 2) {
                require(descriptor?.get("metadataEncoding") == null) { "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_SCHEMA" }
                return manifest
            }
            require(descriptor != null && descriptor["metadataEncoding"]?.jsonPrimitive?.let {
                it.isString && it.content == "gzip-chunks-v1"
            } == true) { "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_ENCODING" }
            fun bytes(value: JsonElement?, maximum: Int): Int {
                val number = value as? JsonPrimitive
                val count = number?.longOrNull
                require(number != null && !number.isString && count != null && count in 1..maximum.toLong()) {
                    "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_SIZE"
                }
                return count.toInt()
            }
            fun digest(value: JsonElement?): String {
                val hash = value as? JsonPrimitive
                require(hash != null && hash.isString && hash.content.matches(Regex("[0-9a-f]{64}"))) {
                    "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_HASH"
                }
                return hash.content
            }
            val decodedBytes = bytes(descriptor["decodedBytes"], 32 * 1048576)
            val decodedHash = digest(descriptor["decodedSha256"])
            val gzipBytes = bytes(descriptor["gzipBytes"], 4 * 1048576)
            val gzipHash = digest(descriptor["gzipSha256"])
            val payloadBytes = bytes(descriptor["bytes"], 1048576)
            val payloadHash = digest(descriptor["sha256"])
            require(elf.size == payloadBytes && UltraKernelSpec.sha256(elf) == payloadHash) {
                "ULTRA_KERNEL_RESIDENT_REFERENCE_IDENTITY"
            }
            val chunks = descriptor["chunks"] as? JsonArray
            require(chunks != null && chunks.size in 1..8) { "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_CHUNKS" }
            val paths = linkedSetOf<String>()
            val compressed = java.io.ByteArrayOutputStream(gzipBytes)
            for (element in chunks) {
                val chunk = element.jsonObject
                val path = (chunk["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                require(path != null && path.length <= 256 && path.startsWith("$family/") &&
                    path.matches(Regex("[A-Za-z0-9._/-]+")) &&
                    path.split('/').all { it.isNotEmpty() && it != "." && it != ".." } && paths.add(path)) {
                    "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_PATH"
                }
                val size = bytes(chunk["bytes"], 512 * 1024)
                val hash = digest(chunk["sha256"])
                require(size <= gzipBytes - compressed.size()) { "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_GZIP_SIZE" }
                val content = read(path, 1048576)
                require(content.size == size && UltraKernelSpec.sha256(content) == hash) {
                    "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_CHUNK_IDENTITY"
                }
                compressed.write(content)
            }
            val encoded = compressed.toByteArray()
            require(encoded.size == gzipBytes && UltraKernelSpec.sha256(encoded) == gzipHash) {
                "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_GZIP_IDENTITY"
            }
            val output = java.io.ByteArrayOutputStream(minOf(decodedBytes, 65536))
            try {
                java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(encoded)).use { input ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(count <= decodedBytes - output.size()) { "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_DECODED_SIZE" }
                        output.write(buffer, 0, count)
                    }
                }
            } catch (error: java.io.IOException) {
                throw IllegalStateException("ULTRA_KERNEL_MANAGER_CLAIM_METADATA_GZIP", error)
            }
            val decoded = output.toByteArray()
            require(decoded.size == decodedBytes && UltraKernelSpec.sha256(decoded) == decodedHash) {
                "ULTRA_KERNEL_MANAGER_CLAIM_METADATA_DECODED_IDENTITY"
            }
            val payload = Json.parseToJsonElement(decoded.decodeToString(throwOnInvalidSequence = true)).jsonObject
            require(bytes(payload["bytes"], 1048576) == payloadBytes && digest(payload["sha256"]) == payloadHash &&
                payload["metadataEncoding"] == null) { "ULTRA_KERNEL_RESIDENT_REFERENCE_IDENTITY" }
            return JsonObject(manifest.toMutableMap().apply {
                put("payloads", JsonObject(mapOf(family to payload)))
            })
        }

        fun load(source: UltraKernelAssetSource, release: String): UltraKernelManagerClaimPayload {
            val family = families[KernelSeries.of(release)]
                ?: error("ULTRA_KERNEL_MANAGER_CLAIM_FAMILY:$release")
            fun read(path: String, limit: Int): ByteArray = source.read("$DIRECTORY/$path").also {
                require(it.isNotEmpty() && it.size <= limit) {
                    "ULTRA_KERNEL_MANAGER_CLAIM_ASSET_SIZE:$path"
                }
            }
            val elf = read("$family/claim.so", 1048576)
            val manifest = if ("manifest.json" in source.list(DIRECTORY))
                runtimeManifest(Json.parseToJsonElement(String(read("manifest.json", 1048576), Charsets.UTF_8)).jsonObject,
                    family, elf, ::read) else null
            val reference = if (manifest != null) {
                val payload = manifest["payloads"]?.jsonObject?.get(family)?.jsonObject
                payload?.get("referenceAbi")?.jsonObject?.let { abi ->
                    require(payload["sha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(elf) &&
                        abi["claimSha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(elf)) {
                        "ULTRA_KERNEL_RESIDENT_REFERENCE_IDENTITY"
                    }
                    val managementReport = abi["managementAbi"]?.jsonObject
                    require(abi["complete"]?.jsonPrimitive?.booleanOrNull == true) { "ULTRA_KERNEL_RESIDENT_REFERENCE_IDENTITY" }
                    fun signatures(name: String) = abi.getValue(name).jsonObject.mapValues { it.value.jsonPrimitive.content }
                    val cfi = payload["callbackCfi"]?.jsonObject ?: error("ULTRA_KERNEL_RESIDENT_CFI_MISSING")
                    require(cfi["claimSha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(elf) &&
                        cfi["supported"]?.jsonPrimitive?.booleanOrNull == true) { "ULTRA_KERNEL_RESIDENT_CFI_UNSUPPORTED" }
                    val mode = cfi.getValue("mode").jsonPrimitive.content
                    require(mode in setOf("none", "kcfi")) { "ULTRA_KERNEL_RESIDENT_CFI_MODE" }
                    val prefixes = if (mode != "kcfi") emptyMap() else
                        cfi.getValue("callbacks").jsonObject.mapValues { (_, value) ->
                            val item = value.jsonObject
                            require(item["verified"]?.jsonPrimitive?.booleanOrNull == true &&
                                item.getValue("prefixWord") == item.getValue("typeid")) { "ULTRA_KERNEL_RESIDENT_CFI_PREFIX" }
                            CallbackPrefix(item.getValue("entryOffset").jsonPrimitive.int,
                                item.getValue("typeid").jsonPrimitive.long.toInt())
                        }
                    abi["suProviderAbi"]?.jsonObject?.let {
                        verifySuProvider(it, manifest, payload, abi, elf, mode, cfi.getValue("normalizeIntegers").jsonPrimitive.boolean)
                    }
                    val coreInputs = abi["coreLifecycleAbi"]?.jsonObject?.let {
                        verifyCoreLifecycle(it, manifest, payload, abi, elf, mode, cfi.getValue("normalizeIntegers").jsonPrimitive.boolean)
                    }.orEmpty()
                    val taskProfile = abi["taskProfileAbi"]?.jsonObject?.let {
                        parseTaskProfile(it, manifest, payload, abi, coreInputs, elf, mode,
                            cfi.getValue("normalizeIntegers").jsonPrimitive.boolean)
                    }
                    val hostDeploymentInputs = managementReport?.get("upstreamSourceFreeze")?.jsonObject
                        ?.get("hostDeploymentEvolution")?.jsonObject?.let { evolution ->
                            require(coreInputs.isNotEmpty()) { "ULTRA_KERNEL_DEPLOYMENT_CORE_SOURCE_MISSING" }
                            val corePredecessors = coreInputs.mapValues { (_, row) ->
                                row["previousRuntimeLayoutInput"]?.let { prior ->
                                    JsonObject(row + mapOf("after" to prior, "nativeCopyIdentity" to prior))
                                } ?: row
                            }
                            UltraKernelHostDeploymentMetadata.verify(evolution, manifest, corePredecessors,
                                abi.getValue("coreLifecycleAbi").jsonObject.getValue("sourceSelection").jsonObject
                                    .getValue("identity").jsonObject)
                        }.orEmpty()
                    require((managementReport?.get("upstreamSourceFreeze")?.jsonObject?.get("coreLifecycleEvolution") != null) == coreInputs.isNotEmpty()) {
                        "ULTRA_KERNEL_CORE_REFERENCE_MISSING"
                    }
                    val sourceQualification = verifySourceQualification(manifest, payload, abi, elf, mode,
                        cfi.getValue("normalizeIntegers").jsonPrimitive.boolean)
                    val management = abi["managementAbi"]?.jsonObject?.let { facts ->
                        require(facts["schemaVersion"]?.jsonPrimitive?.intOrNull == 1 &&
                            facts["claimSha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(elf)) {
                            "ULTRA_KERNEL_MANAGEMENT_REFERENCE_IDENTITY"
                        }
                        // A verified source proof admits target ABI derivation; it is not target/device acceptance.
                        require(if (sourceQualification == null) facts["complete"]?.jsonPrimitive?.booleanOrNull == true
                            else facts["metadataComplete"]?.jsonPrimitive?.booleanOrNull == true &&
                                facts["complete"]?.jsonPrimitive?.booleanOrNull == false &&
                                facts["sourceFrozen"]?.jsonPrimitive?.booleanOrNull == false &&
                                facts["targetQualified"]?.jsonPrimitive?.booleanOrNull == false &&
                                facts["missing"]?.jsonArray?.isEmpty() == true) {
                            "ULTRA_KERNEL_MANAGEMENT_REFERENCE_INCOMPLETE:" +
                                (facts["missing"]?.jsonArray?.firstOrNull()?.toString() ?: "source-metadata-or-qualification")
                        }
                        fun identity(value: JsonElement?): JsonObject {
                            val item = value?.jsonObject ?: error("ULTRA_KERNEL_MANAGEMENT_SOURCE_IDENTITY")
                            require(!item["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                                (item["bytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0 &&
                                item["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                                "ULTRA_KERNEL_MANAGEMENT_SOURCE_IDENTITY"
                            }
                            return item
                        }
                        fun selectedIdentity(path: String, value: JsonElement?) {
                            val item = identity(value)
                            coreInputs[path]?.let { row ->
                                val chosen = identity(row["nativeCopyIdentity"] ?: row["after"])
                                require(item["bytes"] == chosen["bytes"] && item["sha256"] == chosen["sha256"] &&
                                    item.getValue("path").jsonPrimitive.content.replace('\\', '/').substringAfterLast('/') == path.substringAfterLast('/')) {
                                    "ULTRA_KERNEL_CORE_ACTUAL_SOURCE:$path"
                                }
                                return
                            }
                            val entries = manifest.getValue("inputs").jsonObject.entries.filter {
                                StoredInputs.sourcePath(it.key) == path
                            }
                            val selected = entries.singleOrNull()
                            val selectedPath = selected?.key?.replace('\\', '/')
                            val sourcePath = item.getValue("path").jsonPrimitive.content.replace('\\', '/')
                            require(selected?.value?.jsonPrimitive?.content == item.getValue("sha256").jsonPrimitive.content &&
                                (sourcePath == selectedPath || sourcePath.endsWith("/$selectedPath"))) {
                                "ULTRA_KERNEL_MANAGEMENT_SELECTED_SOURCE:$path"
                            }
                        }
                        selectedIdentity("runtime/ultra/authorization/request_protocol.h", facts["wireIdentity"])
                        selectedIdentity("runtime/ultra/identity/kernel/manager_management.c", facts["sourceIdentity"])
                        val freeze = facts.getValue("upstreamSourceFreeze").jsonObject
                        val generation = freeze["generation"]?.jsonPrimitive?.content
                        require(freeze["schemaVersion"]?.jsonPrimitive?.intOrNull == 1 &&
                            generation in setOf("R08", "R09", "R10")) { "ULTRA_KERNEL_MANAGEMENT_UPSTREAM_FREEZE" }
                        val inputs = manifest.getValue("inputs").jsonObject.entries.associate {
                            it.key.replace('\\', '/') to it.value.jsonPrimitive.content
                        }
                        require(inputs.size == manifest.getValue("inputs").jsonObject.size) {
                            "ULTRA_KERNEL_MANAGEMENT_INPUT_PATHS"
                        }
                        fun packagedIdentity(value: JsonElement?) {
                            val item = identity(value)
                            val path = item.getValue("path").jsonPrimitive.content.replace('\\', '/')
                            val matches = inputs.entries.filter { path == it.key || path.endsWith("/" + it.key) }
                            val longest = matches.maxOfOrNull { it.key.length }
                            require(matches.singleOrNull { it.key.length == longest }?.value ==
                                item.getValue("sha256").jsonPrimitive.content) { "ULTRA_KERNEL_MANAGEMENT_PACKAGED_SOURCE" }
                        }
                        for (key in listOf("sourceFreezeIdentity", "baselineIdentity", "contactIdentity", "changeSetIdentity"))
                            identity(freeze[key])
                        val migration = if (generation == "R10") freeze.getValue("sourceMigration").jsonObject else null
                        val migrated = migration?.getValue("selectedInputs")?.jsonArray?.map { it.jsonObject }.orEmpty()
                        val migrationByPath = migrated.associateBy { it.getValue("oldPath").jsonPrimitive.content }
                        require(migrationByPath.size == migrated.size && migrated.map {
                            it.getValue("newPath").jsonPrimitive.content
                        }.toSet().size == migrated.size) { "ULTRA_KERNEL_MANAGEMENT_SOURCE_MIGRATION_PATHS" }
                        if (migration != null) {
                            packagedIdentity(migration["identity"])
                            for (item in migrated) {
                                for (key in listOf("oldPath", "newPath")) {
                                    val path = item.getValue(key).jsonPrimitive.content
                                    require(path.isNotBlank() && '\\' !in path && !path.startsWith('/') &&
                                        path.split('/').none { it == "." || it == ".." || it.isEmpty() }) {
                                        "ULTRA_KERNEL_MANAGEMENT_SOURCE_MIGRATION_PATH"
                                    }
                                }
                                for (prefix in listOf("before", "after")) require(
                                    item[prefix + "Sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true &&
                                    (item[prefix + "Bytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0) {
                                    "ULTRA_KERNEL_MANAGEMENT_SOURCE_MIGRATION_IDENTITY"
                                }
                            }
                        }
                        fun currentPath(path: String) = migrationByPath[path]?.getValue("newPath")?.jsonPrimitive?.content
                            ?: if (generation == "R10") path else StoredInputs.sourcePath(path)
                        fun currentSource(item: JsonObject): Pair<String, String> {
                            val path = item.getValue("path").jsonPrimitive.content
                            val digest = item.getValue("sha256").jsonPrimitive.content
                            val evolved = migrationByPath[path]
                            if (evolved != null) require(evolved.getValue("beforeSha256").jsonPrimitive.content == digest &&
                                (item["bytes"] == null || item["bytes"] == evolved["beforeBytes"])) {
                                "ULTRA_KERNEL_MANAGEMENT_SOURCE_MIGRATION_BEFORE:$path"
                            }
                            val logical = evolved?.getValue("newPath")?.jsonPrimitive?.content ?: path
                            val currentDigest = evolved?.getValue("afterSha256")?.jsonPrimitive?.content ?: digest
                            val currentBytes = evolved?.get("afterBytes") ?: item["bytes"]
                            val core = coreInputs[logical]
                            val coreAfter = core?.let {
                                val prior = it["before"]?.takeUnless { value -> value is JsonNull }?.jsonObject
                                require(prior != null && prior["sha256"]?.jsonPrimitive?.content == currentDigest &&
                                    (currentBytes == null || prior["bytes"] == currentBytes)) { "ULTRA_KERNEL_CORE_SOURCE_PREDECESSOR:$logical" }
                                identity(it["after"])
                            }
                            val after = if (core?.get("previousRuntimeLayoutInput") != null) coreAfter
                                else hostDeploymentInputs[logical] ?: coreAfter
                            if (after == null) return logical to currentDigest
                            val actual = after.getValue("path").jsonPrimitive.content.replace('\\', '/')
                            val matches = inputs.entries.filter { actual == it.key || actual.endsWith("/" + it.key) }
                            val longest = matches.maxOfOrNull { it.key.length }
                            val selected = matches.singleOrNull { it.key.length == longest }
                            require(selected != null && selected.value == after.getValue("sha256").jsonPrimitive.content) {
                                "ULTRA_KERNEL_MANAGEMENT_CURRENT_SOURCE:$logical"
                            }
                            return selected.key to after.getValue("sha256").jsonPrimitive.content
                        }
                        fun verifyCurrent(item: JsonObject, code: String) {
                            val (path, digest) = currentSource(item)
                            require(inputs[path] == digest) { "$code:$path" }
                        }
                        fun sourceRows(value: JsonElement?, expected: Set<String>, code: String): Map<String, JsonObject> {
                            val rows = value?.jsonArray?.map { identity(it) } ?: error(code)
                            val paths = rows.map { it.getValue("path").jsonPrimitive.content }
                            require(paths.toSet().size == rows.size && rows.size == expected.size &&
                                paths.map(::currentPath).toSet() == expected) { code }
                            return rows.associateBy { it.getValue("path").jsonPrimitive.content }
                        }
                        val owned = freeze.getValue("selectedOwnedInputs").jsonArray.map { it.jsonObject }
                        val ownedPaths = setOf("runtime/ultra/authorization/request_protocol.h",
                            "runtime/ultra/identity/kernel/manager_management.c", "runtime/ultra/identity/kernel/manager_management.h",
                            "runtime/ultra/identity/kernel/manager_resident_host.c")
                        require(owned.size == ownedPaths.size && owned.map {
                            currentPath(it.getValue("path").jsonPrimitive.content)
                        }.toSet() == ownedPaths) { "ULTRA_KERNEL_MANAGEMENT_UPSTREAM_INPUTS" }
                        owned.forEach { verifyCurrent(it, "ULTRA_KERNEL_MANAGEMENT_UPSTREAM_SOURCE") }
                        if (generation in setOf("R09", "R10")) {
                            for (key in listOf("r09SourceFreezeIdentity", "r09ChangeSetIdentity", "r09BaselineIdentity"))
                                identity(freeze[key])
                            val r09 = sourceRows(freeze["r09SelectedInputs"], setOf("runtime/ultra/authorization/request_protocol.h",
                                "runtime/ultra/identity/kernel/manager_management.c", "runtime/ultra/identity/kernel/manager_management.h",
                                "runtime/ultra/identity/kernel/manager_uid_gateway.c", "runtime/ultra/identity/kernel/manager_uid_gateway.h",
                                "runtime/ultra/identity/kernel/manager_uid_snapshot.c", "runtime/ultra/identity/kernel/manager_uid_snapshot.h",
                                "runtime/maplesu-sdk/sdk/include/maplesu_request_private.h",
                                "runtime/maplesu-sdk/sdk/manifest/0023_module_base_kernel_request.o.json",
                                "runtime/maplesu-sdk/sdk/manifest/0025_module_base_kernel_func_executor.o.json",
                                "runtime/maplesu-sdk/sdk/src/kernel_request/module_base_kernel_request.cpp",
                                "runtime/maplesu-sdk/sdk/src/module_base/module_base_kernel_func_executor.cpp"), "ULTRA_KERNEL_MANAGEMENT_R09_INPUTS")
                            val host = "runtime/ultra/identity/kernel/manager_resident_host.c"
                            val context = freeze.getValue("p2SourceEvolution").jsonObject
                            val route = freeze.getValue("p2RouteEvolution").jsonObject
                            identity(context["identity"])
                            identity(route["identity"])
                            if (generation == "R09") {
                                r09.values.forEach { verifyCurrent(it, "ULTRA_KERNEL_MANAGEMENT_R09_SOURCE") }
                            } else {
                                require(freeze["sourceRevision"]?.jsonPrimitive?.content == "R10") {
                                    "ULTRA_KERNEL_MANAGEMENT_R10_REVISION"
                                }
                                for (key in listOf("sourceFreezeIdentity", "parentFreezeIdentity", "baseFreezeIdentity", "baselineIdentity",
                                    "contactIdentity", "changeSetIdentity", "parentChangeSetIdentity", "baseChangeSetIdentity",
                                    "r09SourceFreezeIdentity", "r09ChangeSetIdentity", "r09BaselineIdentity",
                                    "r10SourceFreezeIdentity", "r10ChangeSetIdentity", "r10BaselineIdentity", "r10AdoptionIdentity"))
                                    packagedIdentity(freeze[key])
                                packagedIdentity(context["identity"])
                                packagedIdentity(route["identity"])
                                val r10 = sourceRows(freeze["r10SelectedInputs"], setOf("runtime/ultra/authorization/request_protocol.h",
                                    "runtime/ultra/identity/kernel/manager_management.c", "runtime/ultra/identity/kernel/manager_management.h",
                                    "runtime/maplesu-sdk/sdk/include/maplesu_request_private.h",
                                    "runtime/maplesu-sdk/sdk/manifest/0023_module_base_kernel_request.o.json",
                                    "runtime/maplesu-sdk/sdk/manifest/0070_module_root.o.json",
                                    "runtime/maplesu-sdk/sdk/src/kernel_request/module_base_kernel_request.cpp",
                                    "runtime/maplesu-sdk/sdk/src/module/idx070/module_root.cpp"), "ULTRA_KERNEL_MANAGEMENT_R10_INPUTS")
                                val r10Route = freeze.getValue("p2R10RouteEvolution").jsonObject
                                require(identity(r10Route["identity"]) == identity(freeze["r10AdoptionIdentity"])) {
                                    "ULTRA_KERNEL_MANAGEMENT_R10_ROUTE_IDENTITY"
                                }
                                val contextRows = sourceRows(context["selectedInputs"], setOf(host,
                                    "runtime/ultra/identity/kernel/manager_resident_host.h"), "ULTRA_KERNEL_MANAGEMENT_CONTEXT_INPUTS")
                                sourceRows(route["selectedInputs"], setOf(host), "ULTRA_KERNEL_MANAGEMENT_ROUTE_INPUTS")
                                val routeRows = sourceRows(r10Route["selectedInputs"], setOf(host), "ULTRA_KERNEL_MANAGEMENT_R10_ROUTE_INPUTS")
                                val selected = r09 + contextRows + r10 + routeRows
                                require(migrationByPath.keys == selected.keys) { "ULTRA_KERNEL_MANAGEMENT_SOURCE_MIGRATION_INPUTS" }
                                selected.values.forEach { verifyCurrent(it, "ULTRA_KERNEL_MANAGEMENT_R10_SOURCE") }
                                for (item in owned) {
                                    val path = item.getValue("path").jsonPrimitive.content
                                    require(item["sha256"] == selected[path]?.get("sha256")) {
                                        "ULTRA_KERNEL_MANAGEMENT_R10_OWNED_SOURCE:$path"
                                    }
                                }
                            }
                        }
                        identity(facts["sourceIdentity"])
                        identity(facts["objectIdentity"])
                        identity(facts["commandIdentity"])
                        val dependencies = facts.getValue("dependencies").jsonArray
                        require(dependencies.isNotEmpty()) { "ULTRA_KERNEL_MANAGEMENT_DEPENDENCIES" }
                        fun dependencyIdentity(value: JsonElement) {
                            val item = value.jsonObject
                            if (freeze["runtimeLayoutEvolution"] == null || item["bytes"]?.jsonPrimitive?.longOrNull != 0L) identity(value)
                            else {
                                val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                                require(!item["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                                    item["sha256"]?.jsonPrimitive?.content == empty &&
                                    item["archivePath"]?.jsonPrimitive?.content == "build-dependencies/files/$empty.bin") {
                                    "ULTRA_KERNEL_MANAGEMENT_DEPENDENCY_IDENTITY"
                                }
                            }
                        }
                        dependencies.forEach(::dependencyIdentity)
                        require(facts["compiledLayoutSha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                            "ULTRA_KERNEL_MANAGEMENT_LAYOUT_IDENTITY"
                        }
                        val origins = facts.getValue("callbackOrigins").jsonObject.mapValues { (_, value) ->
                            val origin = value.jsonObject
                            UltraKernelResidentAbi.CallbackOrigin(origin.getValue("kind").jsonPrimitive.content,
                                origin.getValue("source").jsonPrimitive.content,
                                origin.getValue("signature").jsonPrimitive.content,
                                origin["secondarySource"]?.jsonPrimitive?.contentOrNull,
                                origin["parameterIndex"]?.jsonPrimitive?.intOrNull)
                        }
                        val validator = origins["msu_credential_replace_current.validate"]
                            ?: error("ULTRA_KERNEL_MANAGEMENT_VALIDATOR_PROOF")
                        val proof = facts.getValue("internalCallbackProof").jsonObject
                        require(proof["caller"]?.jsonPrimitive?.content == validator.source &&
                            proof["parameterIndex"]?.jsonPrimitive?.intOrNull == validator.parameterIndex &&
                            proof["parameterSignature"]?.jsonPrimitive?.content == validator.signature &&
                            proof["definition"]?.jsonPrimitive?.content == "msu_management_validate_credentials" &&
                            proof["definitionSignature"]?.jsonPrimitive?.content == validator.signature.removePrefix("*") &&
                            proof["objectSha256"]?.jsonPrimitive?.content ==
                                facts.getValue("objectIdentity").jsonObject.getValue("sha256").jsonPrimitive.content) {
                            "ULTRA_KERNEL_MANAGEMENT_VALIDATOR_PROOF"
                        }
                        val shared = facts.getValue("sharedBoolProof").jsonObject
                        for (key in listOf("sourceIdentity", "objectIdentity", "commandIdentity", "dependencyReceiptIdentity"))
                            require(identity(shared[key]) == identity(facts[key])) { "ULTRA_KERNEL_MANAGEMENT_SHARED_SOURCE:$key" }
                        val page = facts.getValue("constants").jsonObject.getValue("PAGE_SHIFT").jsonPrimitive.int
                        require(page in setOf(12, 14, 16) && shared["allocationPages"]?.jsonPrimitive?.intOrNull == 1 &&
                            shared["allocationBytes"]?.jsonPrimitive?.intOrNull == (1 shl page) &&
                            shared["publicValueBytes"]?.jsonPrimitive?.intOrNull == 1 &&
                            shared["createFunction"]?.jsonPrimitive?.content == "msu_shared_bool_create" &&
                            shared["storeFunction"]?.jsonPrimitive?.content == "msu_shared_bool_update" &&
                            shared["createSourceSha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                            "ULTRA_KERNEL_MANAGEMENT_SHARED_LAYOUT"
                        }
                        val permissionImports = shared.getValue("permissionImports").jsonObject
                        val allocatorImports = shared.getValue("allocatorImports").jsonObject
                        require(permissionImports.keys == setOf("set_memory_nx", "set_memory_rw") &&
                            "vfree" in allocatorImports && allocatorImports.size >= 2 && allocatorImports.keys.all {
                                it in setOf("vmalloc", "vmalloc_noprof", "__vmalloc", "__vmalloc_node_range", "vfree")
                            } && (permissionImports + allocatorImports).all { (name, signature) ->
                                signature == abi.getValue("functionAbi").jsonObject[name]
                            }) { "ULTRA_KERNEL_MANAGEMENT_SHARED_IMPORTS" }
                        val uidSnapshot = facts["uidSnapshotAbi"]?.jsonObject?.let { uid ->
                            require(generation in setOf("R09", "R10") && uid["schemaVersion"]?.jsonPrimitive?.intOrNull == 1 &&
                                uid["metadataComplete"]?.jsonPrimitive?.booleanOrNull == true &&
                                (if (sourceQualification != null) uid["targetQualified"]?.jsonPrimitive?.booleanOrNull == false
                                    else uid["targetQualified"]?.jsonPrimitive?.booleanOrNull == true)) {
                                "ULTRA_KERNEL_UID_REFERENCE_INCOMPLETE:" + uid["missing"]?.jsonArray?.firstOrNull().toString()
                            }
                            val names = listOf("manager_uid_snapshot", "manager_uid_gateway")
                            for ((key, suffix) in mapOf("sourceIdentities" to ".c", "objectIdentities" to ".o",
                                "commandIdentities" to ".o.cmd", "dependencyReceiptIdentities" to ".json")) {
                                val values = uid.getValue(key).jsonArray.map { identity(it) }
                                require(values.size == names.size && values.zip(names).all { (item, name) ->
                                    item.getValue("path").jsonPrimitive.content.replace('\\', '/').endsWith(
                                        "/" + (if (key == "commandIdentities") "." else "") + name + suffix)
                                }) { "ULTRA_KERNEL_UID_SOURCE_IDENTITIES:$key" }
                                if (key == "sourceIdentities") for ((item, name) in values.zip(names))
                                    selectedIdentity("runtime/ultra/identity/kernel/$name.c", item)
                            }
                            for (key in listOf("factsObjectIdentity", "factsCommandIdentity", "factsDependencyReceiptIdentity"))
                                identity(uid[key])
                            val inlineDependencies = uid.getValue("inlineDependencies").jsonArray
                            require(inlineDependencies.isNotEmpty()) { "ULTRA_KERNEL_UID_INLINE_DEPENDENCIES" }
                            inlineDependencies.forEach(::dependencyIdentity)
                            val uidOrigins = uid.getValue("callbackOrigins").jsonObject.mapValues { (_, value) ->
                                val origin = value.jsonObject
                                UltraKernelResidentAbi.CallbackOrigin(origin.getValue("kind").jsonPrimitive.content,
                                    origin.getValue("source").jsonPrimitive.content, origin.getValue("signature").jsonPrimitive.content,
                                    origin["secondarySource"]?.jsonPrimitive?.contentOrNull, origin["parameterIndex"]?.jsonPrimitive?.intOrNull)
                            }
                            val uidValidator = uidOrigins["msu_uid_gateway_dispatch.validate"]
                                ?: error("ULTRA_KERNEL_UID_VALIDATOR_PROOF")
                            val callsites = uid.getValue("callbackCallsites").jsonObject
                            val uidObjects = uid.getValue("objectIdentities").jsonArray.map { identity(it) }
                            require(uidValidator.signature == validator.signature &&
                                identity(callsites["sourceIdentity"]) == identity(uid.getValue("sourceIdentities").jsonArray[0]) &&
                                identity(callsites["validatorCallerObjectIdentity"]) == uidObjects[1] &&
                                identity(callsites["validatorDefinitionObjectIdentity"]) == identity(facts["objectIdentity"]) &&
                                callsites["rcuRegistration"]?.jsonPrimitive?.content ==
                                    "call_rcu(&snapshot->reclaim_rcu, msu_uid_snapshot_reclaim_rcu)" &&
                                callsites["workRegistration"]?.jsonPrimitive?.content ==
                                    "INIT_WORK(&snapshot->reclaim_work, msu_uid_snapshot_reclaim_work)") { "ULTRA_KERNEL_UID_CALLBACK_CALLSITES" }
                            val layouts = uid.getValue("compiledLayouts").jsonObject.mapValues { (_, value) ->
                                val layout = value.jsonObject
                                UltraKernelResidentAbi.CompiledLayout(layout.getValue("bytes").jsonPrimitive.int,
                                    layout.getValue("signature").jsonPrimitive.content,
                                    layout.getValue("members").jsonObject.mapValues { (_, member) ->
                                        val field = member.jsonObject
                                        UltraKernelResidentAbi.CompiledMember(field.getValue("offset").jsonPrimitive.int,
                                            field.getValue("bytes").jsonPrimitive.int, field.getValue("signature").jsonPrimitive.content)
                                    })
                            }
                            val finalImports = uid.getValue("finalImports").jsonObject
                            require(finalImports.keys == payload.getValue("imports").jsonArray.map { it.jsonPrimitive.content }.toSet() &&
                                finalImports.all { (name, value) -> value == abi.getValue("functionAbi").jsonObject[name] ||
                                    value == abi.getValue("dataAbi").jsonObject[name] }) { "ULTRA_KERNEL_UID_FINAL_IMPORTS" }
                            UltraKernelResidentAbi.UidSnapshotFacts(uid.getValue("compiledFacts").jsonObject.mapValues { it.value.jsonPrimitive.int },
                                layouts, uid.getValue("config").jsonObject.mapValues { it.value.jsonPrimitive.content }, uidOrigins)
                        }
                        require((uidSnapshot != null) == (generation in setOf("R09", "R10"))) { "ULTRA_KERNEL_UID_REFERENCE_MISSING" }
                        val wire = facts.getValue("wireLayout").jsonObject.mapValues { it.value.jsonPrimitive.int }
                        val views = wire["queryViews"]
                        require(views != null && views in 2..5) { "ULTRA_KERNEL_MANAGEMENT_QUERY_VIEWS" }
                        fun supplemental(key: String, symbol: String, wordCount: Int): UltraKernelResidentAbi.SupplementalFacts {
                            val layout = payload.getValue("compiledLayouts").jsonObject.getValue(symbol).jsonObject
                            val values = layout.getValue("values").jsonArray.map {
                                val word = it.jsonPrimitive.long
                                require(word in Int.MIN_VALUE.toLong()..0xffff_ffffL) { "ULTRA_KERNEL_SUPPLEMENTAL_WORD:$symbol" }
                                word.toInt()
                            }
                            require(values.size == wordCount && values[0] == 1) {
                                "ULTRA_KERNEL_SUPPLEMENTAL_LAYOUT_SCHEMA:$symbol"
                            }
                            val bytes = java.nio.ByteBuffer.allocate(wordCount * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                .apply { values.forEach { putInt(it) } }.array()
                            val digest = UltraKernelSpec.sha256(bytes)
                            require(layout["bytes"]?.jsonPrimitive?.intOrNull == bytes.size &&
                                (layout["sha256"] == null || layout["sha256"]?.jsonPrimitive?.content == digest)) {
                                "ULTRA_KERNEL_SUPPLEMENTAL_LAYOUT_IDENTITY:$symbol"
                            }
                            val item = facts[key]?.jsonObject
                            if (item == null) return UltraKernelResidentAbi.SupplementalFacts(1, wordCount, digest)
                            require(item["schemaVersion"]?.jsonPrimitive?.intOrNull == 1 &&
                                item["layoutSchema"]?.jsonPrimitive?.intOrNull == 1 &&
                                item["layoutWords"]?.jsonPrimitive?.intOrNull == wordCount &&
                                item["compiledLayoutSha256"]?.jsonPrimitive?.content == digest) {
                                "ULTRA_KERNEL_SUPPLEMENTAL_REFERENCE:$key"
                            }
                            val callbackOrigins = item["callbackOrigins"]?.jsonObject?.mapValues { (_, value) ->
                                val origin = value.jsonObject
                                UltraKernelResidentAbi.CallbackOrigin(origin.getValue("kind").jsonPrimitive.content,
                                    origin.getValue("source").jsonPrimitive.content, origin.getValue("signature").jsonPrimitive.content,
                                    origin["secondarySource"]?.jsonPrimitive?.contentOrNull, origin["parameterIndex"]?.jsonPrimitive?.intOrNull)
                            }.orEmpty()
                            if (key == "taskStateAbi" && "task_state" in facts.getValue("enabledFeatures").jsonArray.map { it.jsonPrimitive.content }) {
                                selectedIdentity("runtime/ultra/identity/kernel/manager_task_state.c", item["sourceIdentity"])
                                selectedIdentity("runtime/ultra/identity/kernel/manager_task_state.h", item["headerIdentity"])
                                for (name in listOf("objectIdentity", "commandIdentity", "dependencyReceiptIdentity")) identity(item[name])
                                val dependencies = item.getValue("dependencies").jsonArray
                                require(dependencies.isNotEmpty()) { "ULTRA_KERNEL_TASK_STATE_DEPENDENCIES" }
                                dependencies.forEach(::dependencyIdentity)
                                val origin = callbackOrigins["msu_task_state_release_current.validate"]
                                    ?: error("ULTRA_KERNEL_TASK_STATE_VALIDATOR_PROOF")
                                val proof = item.getValue("internalCallbackProof").jsonObject
                                require(origin.kind == "internal" && origin.source == "msu_task_state_release_current" &&
                                    origin.parameterIndex == 0 && origin.secondarySource == null && origin.signature == validator.signature &&
                                    proof["caller"]?.jsonPrimitive?.content == origin.source &&
                                    proof["parameterIndex"]?.jsonPrimitive?.intOrNull == 0 &&
                                    proof["parameterSignature"]?.jsonPrimitive?.content == origin.signature &&
                                    proof["definition"]?.jsonPrimitive?.content == "msu_management_validate_credentials" &&
                                    proof["definitionSignature"]?.jsonPrimitive?.content == origin.signature.removePrefix("*") &&
                                    identity(proof["callerObjectIdentity"]) == identity(item["objectIdentity"]) &&
                                    identity(proof["definitionObjectIdentity"]) == identity(facts["objectIdentity"])) {
                                    "ULTRA_KERNEL_TASK_STATE_VALIDATOR_PROOF"
                                }
                            }
                            return UltraKernelResidentAbi.SupplementalFacts(1, wordCount, digest,
                                item["config"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
                                item["constants"]?.jsonObject?.mapValues { it.value.jsonPrimitive.int }.orEmpty(),
                                item["typeAlignments"]?.jsonObject?.mapValues { it.value.jsonPrimitive.int }.orEmpty(),
                                item["targetQualified"]?.jsonPrimitive?.booleanOrNull ?: false, callbackOrigins, sourceQualification)
                        }
                        val enabledFeatures = facts.getValue("enabledFeatures").jsonArray.map { it.jsonPrimitive.content }.toSet()
                        val procattrNative = facts["procattrAbi"]?.jsonObject?.takeIf {
                            it["metadataComplete"]?.jsonPrimitive?.booleanOrNull == true || "procattr" in enabledFeatures
                        }?.let { native ->
                            require(generation == "R10" && native["schemaVersion"]?.jsonPrimitive?.intOrNull == 1 &&
                                native["metadataComplete"]?.jsonPrimitive?.booleanOrNull == true &&
                                (if (sourceQualification != null) native["targetQualified"]?.jsonPrimitive?.booleanOrNull == false
                                    else native["targetQualified"]?.jsonPrimitive?.booleanOrNull != null &&
                                        ("procattr" !in enabledFeatures || native["targetQualified"]?.jsonPrimitive?.booleanOrNull == true))) {
                                "ULTRA_KERNEL_PROCATTR_REFERENCE_INCOMPLETE:" + native["missing"]?.jsonArray?.firstOrNull().toString()
                            }
                            for (key in listOf("sourceIdentity", "objectIdentity", "commandIdentity", "dependencyReceiptIdentity"))
                                require(identity(native[key]) == identity(facts[key])) { "ULTRA_KERNEL_PROCATTR_SOURCE:$key" }
                            for ((key, suffix) in mapOf("factsObjectIdentity" to "/manager_management_facts.o",
                                "factsCommandIdentity" to "/.manager_management_facts.o.cmd",
                                "factsDependencyReceiptIdentity" to "/manager_management_facts.json"))
                                require(identity(native[key]).getValue("path").jsonPrimitive.content.replace('\\', '/').endsWith(suffix)) {
                                    "ULTRA_KERNEL_PROCATTR_FACTS_SOURCE:$key"
                                }
                            for (key in listOf("dependencies", "factsDependencies")) {
                                val values = native.getValue(key).jsonArray
                                require(values.isNotEmpty()) { "ULTRA_KERNEL_PROCATTR_DEPENDENCIES:$key" }
                                values.forEach(::dependencyIdentity)
                            }
                            val section = native.getValue("layoutSection").jsonObject
                            require(section["name"]?.jsonPrimitive?.content == ".msu.procattr.layout" &&
                                section["symbolBytes"]?.jsonPrimitive?.intOrNull == 160 &&
                                section.getValue("flags").jsonPrimitive.long and 7L == 2L) {
                                "ULTRA_KERNEL_PROCATTR_LAYOUT_SECTION"
                            }
                            val compiled = native.getValue("compiledFacts").jsonObject.mapValues { it.value.jsonPrimitive.int }
                            val layouts = native.getValue("compiledLayouts").jsonObject.mapValues { (_, value) ->
                                val layout = value.jsonObject
                                UltraKernelResidentAbi.CompiledLayout(layout.getValue("bytes").jsonPrimitive.int,
                                    layout.getValue("signature").jsonPrimitive.content,
                                    layout.getValue("members").jsonObject.mapValues { (_, member) ->
                                        val field = member.jsonObject
                                        UltraKernelResidentAbi.CompiledMember(field.getValue("offset").jsonPrimitive.int,
                                            field.getValue("bytes").jsonPrimitive.int, field.getValue("signature").jsonPrimitive.content)
                                    })
                            }
                            val functions = native.getValue("functionAbi").jsonObject.mapValues { it.value.jsonPrimitive.content }
                            require(functions.all { (name, signature) ->
                                abi.getValue("functionAbi").jsonObject[name]?.jsonPrimitive?.content == signature &&
                                    (name == "proc_pid_attr_write" ||
                                        name in payload.getValue("imports").jsonArray.map { it.jsonPrimitive.content })
                            }) { "ULTRA_KERNEL_PROCATTR_FINAL_IMPORTS" }
                            val marker = native["marker"]?.jsonObject
                            val markerCfi = marker?.get("cfi")?.jsonObject
                            val markerMode = markerCfi?.get("mode")?.jsonPrimitive?.content
                            val markerNormalize = markerCfi?.get("normalizeIntegers")?.jsonPrimitive?.boolean
                            var markerTypeid: Int? = null
                            if (compiled["configured"] == 1) {
                                require(marker != null && markerCfi != null &&
                                    marker["function"]?.jsonPrimitive?.content == "proc_pid_attr_write" &&
                                    marker["signature"]?.jsonPrimitive?.content == functions["proc_pid_attr_write"] &&
                                    markerCfi["verified"]?.jsonPrimitive?.booleanOrNull == true &&
                                    markerMode == mode && markerNormalize == cfi.getValue("normalizeIntegers").jsonPrimitive.boolean) {
                                    "ULTRA_KERNEL_PROCATTR_MARKER_PROOF"
                                }
                                val relocations = marker.getValue("objectRelocations").jsonArray.map { it.jsonObject }
                                require(relocations.isNotEmpty() && relocations.all {
                                    it["symbol"]?.jsonPrimitive?.content == "proc_pid_attr_write" &&
                                        it["section"]?.jsonPrimitive?.content?.startsWith(".rela") == true &&
                                        it["relocatedSection"]?.jsonPrimitive?.content?.startsWith(".text") == true &&
                                        (it["offset"]?.jsonPrimitive?.longOrNull ?: -1) >= 0 &&
                                        (it["type"]?.jsonPrimitive?.intOrNull ?: 0) > 0 &&
                                        it["type"]?.jsonPrimitive?.intOrNull !in setOf(282, 283) &&
                                        !it["caller"]?.jsonPrimitive?.content.isNullOrBlank()
                                }) { "ULTRA_KERNEL_PROCATTR_MARKER_RELOCATIONS" }
                                if (mode == "kcfi") {
                                    for (key in listOf("typeProbeObjectIdentity", "typeProbeCommandIdentity",
                                        "typeProbeDependencyReceiptIdentity", "referenceEvidenceIdentity")) identity(markerCfi[key])
                                    fun typeid(key: String): Long {
                                        val value = markerCfi.getValue(key).jsonPrimitive.long
                                        require(value in 0..0xffffffffL) { "ULTRA_KERNEL_PROCATTR_MARKER_TYPEID:$key" }
                                        return value
                                    }
                                    val declared = typeid("declarationTypeid")
                                    require(declared == typeid("referenceTypeid")) { "ULTRA_KERNEL_PROCATTR_MARKER_CFI_TYPE" }
                                    val observed = markerCfi.getValue("referenceMarker").jsonObject
                                    val address = java.lang.Long.parseUnsignedLong(observed.getValue("address").jsonPrimitive.content)
                                    val prefix = java.lang.Long.parseUnsignedLong(observed.getValue("prefixAddress").jsonPrimitive.content)
                                    require(observed["function"]?.jsonPrimitive?.content == "proc_pid_attr_write" &&
                                        java.lang.Long.compareUnsigned(address, 4) > 0 && prefix == address - 4 &&
                                        observed.getValue("prefixWord").jsonPrimitive.long == declared) {
                                        "ULTRA_KERNEL_PROCATTR_MARKER_REFERENCE"
                                    }
                                    markerTypeid = declared.toInt()
                                } else require(markerCfi["declarationTypeid"] == null && markerCfi["referenceTypeid"] == null) {
                                    "ULTRA_KERNEL_PROCATTR_MARKER_CFI_DISABLED"
                                }
                            } else require(marker == null && functions.isEmpty() && native["writeSignature"] == null) {
                                "ULTRA_KERNEL_PROCATTR_DISABLED_NATIVE_FACTS"
                            }
                            UltraKernelResidentAbi.ProcattrFacts(native.getValue("layoutSchema").jsonPrimitive.int,
                                native.getValue("layoutWords").jsonPrimitive.int,
                                native.getValue("compiledLayoutSha256").jsonPrimitive.content,
                                compiled, layouts, native.getValue("typeAlignments").jsonObject.mapValues { it.value.jsonPrimitive.int },
                                native.getValue("config").jsonObject.mapValues { it.value.jsonPrimitive.content }, functions,
                                native["writeSignature"]?.jsonPrimitive?.content, markerMode, markerNormalize, markerTypeid)
                        }
                        require("procattr" !in enabledFeatures || procattrNative != null) { "ULTRA_KERNEL_PROCATTR_NATIVE_REFERENCE_REQUIRED" }
                        val gateVerified = facts.getValue("gateVerified").jsonPrimitive.boolean
                        if (gateVerified) {
                            require(sourceQualification != null) { "ULTRA_KERNEL_PUBLICATION_SOURCE_QUALIFICATION" }
                            val binding = facts.getValue("publicationGateBinding").jsonObject
                            require(binding["schema"]?.jsonPrimitive?.content == "maplesu-publication-gate-source-binding/1" &&
                                binding["targetQualified"]?.jsonPrimitive?.booleanOrNull == false &&
                                binding["deviceValidated"]?.jsonPrimitive?.booleanOrNull == false &&
                                binding["publicControlBytes"]?.jsonPrimitive?.intOrNull == 64 &&
                                binding["privateControlBytes"]?.jsonPrimitive?.intOrNull == 72) { "ULTRA_KERNEL_PUBLICATION_SOURCE_BINDING" }
                            selectedIdentity("runtime/ultra/identity/kernel/manager_management.c", binding["nativeSource"])
                            val gateInputs = binding.getValue("inputs").jsonArray
                            require(gateInputs.isNotEmpty()) { "ULTRA_KERNEL_PUBLICATION_SOURCE_INPUTS" }
                            gateInputs.forEach { packagedIdentity(it) }
                        } else require(facts["publicationGateBinding"] == null || facts["publicationGateBinding"] is JsonNull) {
                            "ULTRA_KERNEL_PUBLICATION_SOURCE_UNEXPECTED"
                        }
                        val procattr = if (views >= 4) supplemental("procattrAbi", "msu_procattr_compiled_layout", 40) else null
                        val taskState = if (views == 5) supplemental("taskStateAbi", "msu_task_state_compiled_layout", 53) else null
                        require((facts["procattrAbi"] == null || procattr != null) &&
                            (facts["taskStateAbi"] == null || taskState != null)) { "ULTRA_KERNEL_MANAGEMENT_SUPPLEMENTAL_REFERENCE" }
                        UltraKernelResidentAbi.ManagementFacts(facts.getValue("layoutSchema").jsonPrimitive.int,
                            facts.getValue("layoutWords").jsonPrimitive.int,
                            facts.getValue("config").jsonObject.mapValues { it.value.jsonPrimitive.content },
                            facts.getValue("constants").jsonObject.mapValues { it.value.jsonPrimitive.int },
                            facts.getValue("typeAlignments").jsonObject.mapValues { it.value.jsonPrimitive.int },
                            origins, facts.getValue("enabledFeatures").jsonArray.map { it.jsonPrimitive.content }.toSet(),
                            gateVerified,
                            wire, uidSnapshot, procattr, taskState, procattrNative = procattrNative, sourceQualification = sourceQualification)
                    }
                    val dataSignatures = abi["dataAbi"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
                    val dataImports = abi.getValue("dataImports").jsonArray.map { it.jsonPrimitive.content }.toSet()
                    require(management == null || dataSignatures.keys == dataImports) {
                        "ULTRA_KERNEL_MANAGEMENT_DATA_IMPORTS"
                    }
                    val descriptor = if (management == null) null else {
                        val item = managementReport!!.getValue("descriptorAbi").jsonObject
                        require(item["schemaVersion"]?.jsonPrimitive?.intOrNull == 1 &&
                            item["hostSymbol"]?.jsonPrimitive?.content == "host" &&
                            item["objectBtfSha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                            "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_IDENTITY"
                        }
                        val signature = item.getValue("descriptorTypeSignature").jsonPrimitive.content
                        require(signature.startsWith("smaplesu_metadata_owner_v3:80{")) {
                            "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_TYPE"
                        }
                        val descriptorFields = item.getValue("descriptorFields").jsonObject
                        val commonFields = item.getValue("commonFields").jsonObject
                        fun verifyFields(fields: JsonObject, expected: Map<String, Pair<Int, Int>>, ownerBytes: Int, prefix: String) {
                            require(fields.keys == expected.keys) { "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_FIELDS" }
                            for ((name, position) in expected) {
                                val field = fields.getValue(name).jsonObject
                                val wireKey = prefix + name.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } + "Offset"
                                require(field["offset"]?.jsonPrimitive?.intOrNull == position.first &&
                                    field["bytes"]?.jsonPrimitive?.intOrNull == position.second &&
                                    management.wireLayout[wireKey] == position.first &&
                                    position.first + position.second <= ownerBytes &&
                                    !field["signature"]?.jsonPrimitive?.content.isNullOrEmpty()) { "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_MEMBER:$name" }
                            }
                        }
                        verifyFields(descriptorFields, mapOf("common" to (0 to 48), "owner_schema" to (48 to 4),
                            "owner_state" to (52 to 4), "owner_epoch" to (56 to 4), "owner_full_uid" to (60 to 4), "host" to (64 to 16)), 80, "metadata")
                        verifyFields(commonFields, mapOf("magic" to (0 to 4), "version" to (4 to 2), "length" to (6 to 2),
                            "capabilities" to (8 to 8), "kernel_base_displacement" to (16 to 8),
                            "legacy_metadata_displacement" to (24 to 8), "layout_displacement" to (32 to 8),
                            "layout_length" to (40 to 4), "layout_version" to (44 to 4)), 48, "metadataCommon")
                        fun structure(name: String, bytes: Int, fields: JsonObject) = "s$name:$bytes{" +
                            fields.values.map { it.jsonObject }.sortedBy { it.getValue("offset").jsonPrimitive.int }.joinToString("") {
                                "${it.getValue("offset").jsonPrimitive.int * 8}=${it.getValue("signature").jsonPrimitive.content};"
                            } + "}"
                        require(signature == structure("maplesu_metadata_owner_v3", 80, descriptorFields) &&
                            descriptorFields.getValue("common").jsonObject.getValue("signature").jsonPrimitive.content ==
                                structure("maplesu_metadata_v2", 48, commonFields)) {
                            "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_SIGNATURE"
                        }
                        val views = UltraKernelResidentAbi.queryViews(management)
                        val descriptorMembers = listOf(Triple("baseField", "metadata", "baseOffset"),
                            Triple("extendedField", "extended_metadata", "extendedOffset")) +
                            (if (views >= 3) listOf(Triple("uidField", "uid_metadata", "uidOffset")) else emptyList()) +
                            (if (views >= 4) listOf(Triple("procattrField", "procattr_metadata", "procattrOffset")) else emptyList()) +
                            (if (views == 5) listOf(Triple("taskField", "task_metadata", "taskOffset")) else emptyList())
                        require((item["uidField"] != null) == (views >= 3) && (item["uidOffset"] != null) == (views >= 3) &&
                            (item["procattrField"] != null) == (views >= 4) && (item["procattrOffset"] != null) == (views >= 4) &&
                            (item["taskField"] != null) == (views == 5) && (item["taskOffset"] != null) == (views == 5)) {
                            "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_FIELDS"
                        }
                        for ((key, name, offset) in descriptorMembers) {
                            val field = item.getValue(key).jsonObject
                            require(field["name"]?.jsonPrimitive?.content == name &&
                                field["offset"] == item[offset] && field["bytes"]?.jsonPrimitive?.intOrNull == 80 &&
                                field["signature"]?.jsonPrimitive?.content == signature) { "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_FIELD:$name" }
                        }
                        for (key in listOf("sourceIdentity", "objectIdentity", "commandIdentity", "dependencyReceiptIdentity")) {
                            val identity = item.getValue(key).jsonObject
                            require(!identity["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                                (identity["bytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0 &&
                                identity["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                                "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_SOURCE"
                            }
                        }
                        val sourceIdentity = item.getValue("sourceIdentity").jsonObject
                        val hostPath = "runtime/ultra/identity/kernel/manager_resident_host.c"
                        val hostCore = coreInputs[hostPath]
                        val hostCoreSource = hostCore?.let { (it["nativeCopyIdentity"] ?: it.getValue("after")).jsonObject }
                        val hostSource = if (hostCore?.get("previousRuntimeLayoutInput") != null) hostCoreSource
                            else hostDeploymentInputs[hostPath] ?: hostCoreSource
                        require(if (hostSource != null) sourceIdentity["sha256"] == hostSource["sha256"] &&
                            sourceIdentity["bytes"] == hostSource["bytes"] else
                            sourceIdentity["sha256"] == manifest.getValue("inputs").jsonObject.entries.singleOrNull {
                                StoredInputs.sourcePath(it.key) == "runtime/ultra/identity/kernel/manager_resident_host.c"
                            }?.value) { "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_SOURCE" }
                        DescriptorAbi(item.getValue("hostSymbolOffset").jsonPrimitive.int, item.getValue("hostBytes").jsonPrimitive.int,
                            item.getValue("baseOffset").jsonPrimitive.int, item.getValue("extendedOffset").jsonPrimitive.int,
                            item.getValue("descriptorBytes").jsonPrimitive.int, item.getValue("sectionName").jsonPrimitive.content,
                            item.getValue("sectionOffset").jsonPrimitive.int, item.getValue("sectionBytes").jsonPrimitive.int,
                            item.getValue("sectionFlags").jsonPrimitive.long, item["uidOffset"]?.jsonPrimitive?.intOrNull,
                            item["procattrOffset"]?.jsonPrimitive?.intOrNull, item["taskOffset"]?.jsonPrimitive?.intOrNull)
                    }
                    ReferenceAbi(signatures("functionAbi"), signatures("callbackAbi"), signatures("bootstrapFunctionAbi"),
                        dataImports, CallbackCfi(mode, cfi.getValue("normalizeIntegers").jsonPrimitive.boolean, prefixes),
                        dataSignatures, management, abi["managementAbi"]?.jsonObject?.get("compiledLayoutSha256")?.jsonPrimitive?.content, descriptor,
                        abi["coreLifecycleAbi"]?.jsonObject?.get("credentialDomain")?.jsonObject?.get("compiledLayoutSha256")?.jsonPrimitive?.content,
                        taskProfile)
                }
            } else null
            val permissions = manifest?.get("support")?.jsonObject?.get("permissions")?.jsonObject?.let { item ->
                read("permissions.bin", 65536).also { bytes ->
                    require(item["sha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(bytes) &&
                        item["bytes"]?.jsonPrimitive?.intOrNull == bytes.size) { "ULTRA_KERNEL_RESIDENT_PERMISSIONS_IDENTITY" }
                }
            }
            val familySupport = manifest?.get("payloads")?.jsonObject?.get(family)?.jsonObject
                ?.get("support")?.jsonObject
            require(familySupport == null || familySupport.keys == setOf("bootstrap")) {
                "ULTRA_KERNEL_RESIDENT_FAMILY_SUPPORT_KEYS"
            }
            val familyBootstrap = familySupport?.getValue("bootstrap")?.jsonObject
            val bootstrapItem = familyBootstrap ?: manifest?.get("support")?.jsonObject?.get("bootstrap")?.jsonObject
            val bootstrapPath = if (familyBootstrap != null) {
                require(familyBootstrap["path"]?.jsonPrimitive?.content == "$family/bootstrap.bin") {
                    "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_PATH"
                }
                "$family/bootstrap.bin"
            } else "bootstrap.bin"
            val bootstrap = read(bootstrapPath, 65536)
            bootstrapItem?.let { item ->
                val keys = setOf("path", "bytes", "sha256", "entryOffset", "imports", "relocations",
                    "sourceInputsIdentity", "retirement", "bootEntryOffset", "deploymentBootEntryOffset",
                    "deploymentAnchorVersion", "deploymentBindingBytes", "deploymentConfigBytes", "deploymentRecordBytes",
                    "nativeCfiPolicy", "nativeCommandIdentity") +
                    (if (familyBootstrap == null) setOf("compiler11SourceInputsIdentity") else emptySet())
                require(keys.containsAll(item.keys) &&
                    (item["path"] == null || item["path"]?.jsonPrimitive?.content == bootstrapPath) &&
                    item["sha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(bootstrap) &&
                    item["bytes"]?.jsonPrimitive?.intOrNull == bootstrap.size && bootstrap.size % 4 == 0 &&
                    item["entryOffset"]?.jsonPrimitive?.intOrNull == 0 &&
                    item["imports"]?.jsonArray?.isEmpty() == true &&
                    item["relocations"]?.jsonPrimitive?.intOrNull == 0) {
                    "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_IDENTITY"
                }
                fun supportIdentity(value: JsonElement?): JsonObject = (value?.jsonObject
                    ?: error("ULTRA_KERNEL_RESIDENT_BOOTSTRAP_SOURCE_IDENTITY")).also { identity ->
                    require(identity.keys == setOf("path", "bytes", "sha256") &&
                        !identity["path"]?.jsonPrimitive?.content.isNullOrBlank() &&
                        (identity["bytes"]?.jsonPrimitive?.longOrNull ?: 0) > 0 &&
                        identity["sha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true) {
                        "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_SOURCE_IDENTITY"
                    }
                }
                item["compiler11SourceInputsIdentity"]?.let { supportIdentity(it) }
                require((item["nativeCfiPolicy"] != null) == (item["nativeCommandIdentity"] != null)) {
                    "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY_PAIR"
                }
                item["nativeCfiPolicy"]?.jsonObject?.let { policy ->
                    val command = supportIdentity(item["nativeCommandIdentity"])
                    val commands = manifest?.get("payloads")?.jsonObject?.get(family)?.jsonObject
                        ?.get("referenceAbi")?.jsonObject?.get("hostRuntimeAbi")?.jsonObject
                        ?.get("commandIdentities")?.jsonArray
                    require(familyBootstrap != null && reference != null && reference.cfi.mode == "kcfi" &&
                        policy.keys == setOf("kcfiOptionPresent", "kcfiEnabled", "normalizeIntegers") &&
                        policy["kcfiOptionPresent"]?.jsonPrimitive?.booleanOrNull == true &&
                        policy["kcfiEnabled"]?.jsonPrimitive?.booleanOrNull == true &&
                        policy["normalizeIntegers"]?.jsonPrimitive?.booleanOrNull == reference.cfi.normalizeIntegers &&
                        policy["normalizeIntegers"] == item["retirement"]?.jsonObject?.get("normalizeIntegers") &&
                        command.getValue("path").jsonPrimitive.content.replace('\\', '/').substringAfterLast('/') ==
                            ".manager_host_runtime_types.o.cmd" && commands?.count {
                                val actual = supportIdentity(it)
                                actual.getValue("path").jsonPrimitive.content.replace('\\', '/').substringAfterLast('/') ==
                                    ".manager_host_runtime_types.o.cmd" && actual["bytes"] == command["bytes"] && actual["sha256"] == command["sha256"]
                            } == 1) {
                        "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY"
                    }
                }
                item["retirement"]?.jsonObject?.let { retirement ->
                    require(retirement.keys == setOf("sourceSelection", "callbackOffset", "kcfiPrefix",
                            "normalizeIntegers", "deviceQualified")) { "ULTRA_KERNEL_RESIDENT_RETIREMENT_KEYS" }
                    val offset = retirement.getValue("callbackOffset").jsonPrimitive.int
                    val prefix = retirement.getValue("kcfiPrefix").jsonPrimitive.long
                    require(offset >= 4 && offset % 4 == 0 && offset.toLong() + 4 <= bootstrap.size &&
                        prefix in 0..0xffffffffL &&
                        UltraKernelA64.le32Bits(bootstrap, offset - 4) == prefix.toInt() &&
                        retirement["deviceQualified"]?.jsonPrimitive?.booleanOrNull == false &&
                        (reference == null || retirement["normalizeIntegers"]?.jsonPrimitive?.booleanOrNull ==
                            reference.cfi.normalizeIntegers)) { "ULTRA_KERNEL_RESIDENT_RETIREMENT_IDENTITY" }
                }
            }
            require(manifest == null || bootstrapItem != null) { "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_MISSING" }
            val bootEntry = bootstrapItem?.let { item ->
                item["bootEntryOffset"]?.jsonPrimitive?.int?.also { offset ->
                    require(item["sha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(bootstrap) &&
                        item["bytes"]?.jsonPrimitive?.intOrNull == bootstrap.size && offset >= 0 && offset % 4 == 0 &&
                        offset.toLong() + 4 <= bootstrap.size) { "ULTRA_KERNEL_RESIDENT_BOOT_ENTRY_IDENTITY" }
                }
            }
            val deploymentEntry = bootstrapItem?.let { item ->
                val names = listOf("deploymentBootEntryOffset", "deploymentAnchorVersion", "deploymentBindingBytes",
                    "deploymentConfigBytes", "deploymentRecordBytes")
                if (names.none(item::containsKey)) null else {
                    require(bootEntry != null && item["sha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(bootstrap) &&
                        item["bytes"]?.jsonPrimitive?.intOrNull == bootstrap.size &&
                        item.getValue("deploymentAnchorVersion").jsonPrimitive.int == 4 &&
                        item.getValue("deploymentBindingBytes").jsonPrimitive.int == 56 &&
                        item.getValue("deploymentConfigBytes").jsonPrimitive.int == 48 &&
                        item.getValue("deploymentRecordBytes").jsonPrimitive.int == 24) { "ULTRA_KERNEL_DEPLOYMENT_SUPPORT_IDENTITY" }
                    val descriptor = requireNotNull(manifest).getValue("payloads").jsonObject.getValue(family).jsonObject
                        .getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
                        .getValue("descriptorAbi").jsonObject
                    val native = descriptor.getValue("deploymentNamespaceAbi").jsonObject
                    val storageAt = native.getValue("storageOffset").jsonPrimitive.int
                    require(reference?.descriptor != null && native.getValue("configVersion").jsonPrimitive.int == 2 &&
                        native.getValue("configBytes").jsonPrimitive.int == 48 &&
                        native.getValue("recordBytes").jsonPrimitive.int == 24 &&
                        native.getValue("storageBytes").jsonPrimitive.int == 24 && storageAt >= 0 &&
                        storageAt.toLong() + 24 <= descriptor.getValue("hostBytes").jsonPrimitive.int &&
                        native.getValue("compiledFactsSymbol").jsonPrimitive.content == "msu_manager_deployment_host_facts") {
                        "ULTRA_KERNEL_DEPLOYMENT_NATIVE_IDENTITY"
                    }
                    item.getValue("deploymentBootEntryOffset").jsonPrimitive.int.also { offset ->
                        require(offset >= 0 && offset % 4 == 0 && offset.toLong() + 4 <= bootstrap.size) {
                            "ULTRA_KERNEL_DEPLOYMENT_BOOT_ENTRY"
                        }
                    }
                }
            }
            val runtimeLayout = manifest?.get("payloads")?.jsonObject?.get(family)?.jsonObject?.let { payload ->
                payload["runtimeLayout"]?.jsonObject?.let { parseRuntimeLayout(it, payload, elf, reference) }
            }
            val runtimeProfiles = manifest?.get("payloads")?.jsonObject?.get(family)?.jsonObject?.let { payload ->
                payload["runtimeProfiles"]?.jsonObject?.let { parseRuntimeProfiles(it, payload, elf, reference) }
            }
            val sessionAbi = manifest?.get("payloads")?.jsonObject?.get(family)?.jsonObject?.let { payload ->
                payload["sessionAbi"]?.jsonObject?.let { parseSessionAbi(it, payload, elf, reference) }
            }
            val bootEventsAbi = manifest?.get("payloads")?.jsonObject?.get(family)?.jsonObject?.let { payload ->
                payload["bootEventsAbi"]?.jsonObject?.let { parseBootEventsAbi(it, payload, elf, reference) }
            }
            val ksuCompatAbi = manifest?.get("payloads")?.jsonObject?.get(family)?.jsonObject?.let { payload ->
                payload["ksuCompatAbi"]?.jsonObject?.let { parseKsuCompatAbi(it, payload, elf, reference) }
            }
            val materializer = read("materializer.bin", 65536)
            if (manifest != null) {
                val item = manifest["support"]?.jsonObject?.get("materializer")?.jsonObject
                    ?: error("ULTRA_KERNEL_MANAGER_CLAIM_MATERIALIZER_IDENTITY")
                require(item["protocolVersion"]?.jsonPrimitive?.intOrNull == 3 &&
                    item["bytes"]?.jsonPrimitive?.intOrNull == materializer.size &&
                    item["sha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(materializer)) {
                    "ULTRA_KERNEL_MANAGER_CLAIM_MATERIALIZER_IDENTITY"
                }
            }
            return UltraKernelManagerClaimPayload(bootstrap,
                materializer, elf, reference, permissions, bootEntry, deploymentEntry, runtimeLayout, runtimeProfiles, sessionAbi, bootEventsAbi, ksuCompatAbi)
        }
    }
}
