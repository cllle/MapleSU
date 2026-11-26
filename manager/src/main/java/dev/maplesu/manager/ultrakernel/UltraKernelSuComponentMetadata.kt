package dev.maplesu.manager.ultrakernel

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.*

internal object UltraKernelSuComponentMetadata {
    private val objects = setOf("su_install_registry", "su_profile_apply", "su_profile_namespace", "su_profile_execution", "su_profile_resolve")
    private val probes = setOf("manager_su_component_facts", "manager_su_profile_facts", "manager_su_namespace_facts", "manager_su_component_callback_types")
    private val baseTypes = mapOf(
        "profile" to ("msu_su_profile" to listOf("uid", "gid", "groups_count", "groups", "inheritable", "permitted", "effective", "bounding", "ambient", "selinux_domain", "namespace_mode", "flags", "use_default", "non_root", "umount_modules", "template_name")),
        "installMember" to ("msu_su_install_member" to listOf("package")),
        "verifiedInstall" to ("msu_su_verified_install" to listOf("full_uid", "installation_id", "pin", "members", "member_count")),
        "savedGrant" to ("msu_su_saved_grant" to listOf("full_uid", "decision", "installation_id", "profile")),
        "registrySource" to ("msu_su_registry_source" to listOf("owner", "pin_current", "put_install", "read_grant", "validate_profile", "check_closed", "quiesce", "put_owner", "validate_producer")),
        "revokeReceipt" to ("msu_su_revoke_receipt" to listOf("generation", "full_uid", "inflight")),
        "lease" to ("msu_su_lease" to listOf("registry", "entry", "snapshot", "task", "mm", "cred", "generation", "exec_id", "invalidated")),
        "registry" to ("msu_su_registry" to listOf("lock", "host", "controller", "next_generation", "source", "count", "closed", "producer", "gateway_binding", "gateway_bound", "gateway_retired", "entries")),
        "update" to ("msu_su_update" to listOf("registry", "records", "expected_revision", "count", "shutdown")),
        "installEntry" to ("msu_su_install_entry" to listOf("full_uid", "users", "generation", "revoked", "busy", "cancelled", "granted", "installation", "profile")),
        "namespaceReceipt" to ("msu_su_namespace_receipt" to listOf("mode", "effects", "error", "operation_error", "close_error", "cwd_error", "validation_error")),
        "executionReceipt" to ("msu_su_execution_receipt" to listOf("phase", "credential_effects", "task_effects", "previous_seccomp_mode", "mount_namespace", "error", "completed")),
        "profileExecution" to ("msu_su_profile_execution" to listOf("lease", "before", "effects", "adopted")),
    )
    private val baseFunctionOwners = mapOf(
        "msu_su_registry_create" to "su_install_registry",
        "msu_su_registry_refresh" to "su_install_registry",
        "msu_su_registry_rows" to "su_install_registry",
        "msu_su_registry_update_begin" to "su_install_registry",
        "msu_su_registry_update_finish" to "su_install_registry",
        "msu_su_registry_revoke" to "su_install_registry",
        "msu_su_registry_revoke_package" to "su_install_registry",
        "msu_su_registry_shutdown" to "su_install_registry",
        "msu_su_registry_destroy_quiesced" to "su_install_registry",
        "msu_su_read_current" to "su_install_registry",
        "msu_su_lease_begin" to "su_install_registry",
        "msu_su_lease_validate" to "su_install_registry",
        "msu_su_lease_profile" to "su_install_registry",
        "msu_su_lease_end" to "su_install_registry",
        "msu_su_apply_current" to "su_install_registry",
        "msu_su_registry_bind_gateway" to "su_install_registry",
        "msu_su_registry_prepare_kernel" to "su_install_registry",
        "msu_su_registry_commit_kernel" to "su_install_registry",
        "msu_su_registry_cancel_kernel" to "su_install_registry",
        "msu_su_profile_check" to "su_profile_apply",
        "msu_su_profile_apply_held" to "su_profile_apply",
        "msu_su_profile_namespace_supported" to "su_profile_namespace",
        "msu_su_profile_namespace_apply_held" to "su_profile_namespace",
        "msu_su_profile_check_execution" to "su_profile_execution",
        "msu_su_apply_current_detailed" to "su_profile_execution",
        "msu_su_profile_resolve_root" to "su_profile_resolve",
    )
    private val callbacks = mapOf(
        "su.reader" to ("su_install_registry" to "msu_su_read_current"),
        "su.apply" to ("su_install_registry" to "msu_su_apply_current"),
        "su.lease.validate" to ("su_install_registry" to "msu_su_lease_validate"),
        "su.uid.prepare.validate" to ("su_install_registry" to "msu_su_kernel_producer_validate"),
        "su.fence.check_closed" to ("su_install_registry" to "msu_su_fence_check_closed"),
        "su.fence.begin" to ("su_install_registry" to "msu_su_fence_begin"),
        "su.fence.revoke" to ("su_install_registry" to "msu_su_fence_revoke"),
        "su.fence.finish" to ("su_install_registry" to "msu_su_fence_finish"),
        "su.fence.retire" to ("su_install_registry" to "msu_su_fence_retire"),
        "su.profile.validate" to ("su_profile_apply" to "msu_su_profile_validate_held"),
        "su.profile.adopt" to ("su_profile_apply" to "msu_su_profile_adopt"),
    )
    private val origins = mapOf(
        "su.uid.prepare.validate" to Origin("manager_uid_gateway", "msu_uid_gateway_prepare_kernel", 7, "su_install_registry", "msu_su_kernel_producer_validate"),
        "su.uid.commit.validate" to Origin("manager_uid_gateway", "msu_uid_gateway_commit_kernel", 5, "su_install_registry", "msu_su_kernel_producer_validate"),
        "su.profile.validate" to Origin("manager_management", "msu_credential_replace_profile_current", 1, "su_profile_apply", "msu_su_profile_validate_held"),
        "su.profile.adopt" to Origin("manager_management", "msu_credential_replace_profile_current", 2, "su_profile_apply", "msu_su_profile_adopt"),
        "su.task.validate" to Origin("manager_task_state", "msu_task_state_release_current", 0, "su_install_registry", "msu_su_lease_validate"),
    )
    private val baseRelocations = mapOf(
        "su_install_registry" to mapOf(
            "msu_su_registry_bind_gateway" to setOf("msu_uid_gateway_bind_reader", "msu_su_fence_check_closed", "msu_su_fence_begin", "msu_su_fence_revoke", "msu_su_fence_finish", "msu_su_fence_retire"),
            "msu_su_registry_prepare_kernel" to setOf("msu_uid_gateway_prepare_kernel", "msu_su_kernel_producer_validate"),
            "msu_su_registry_commit_kernel" to setOf("msu_uid_gateway_commit_kernel", "msu_su_kernel_producer_validate"),
            "msu_su_registry_cancel_kernel" to setOf("msu_uid_gateway_cancel_kernel"),
            "msu_su_registry_refresh" to setOf("msu_su_profile_resolve_root"),
            "msu_su_apply_current" to setOf("msu_su_apply_current_detailed"),
        ),
        "su_profile_apply" to mapOf(
            "msu_su_profile_apply_held" to setOf("msu_credential_replace_profile_current", "msu_su_profile_validate_held", "msu_su_profile_adopt"),
        ),
        "su_profile_execution" to mapOf(
            "msu_su_apply_current_detailed" to setOf("msu_su_profile_apply_held", "msu_task_state_release_current", "msu_su_lease_validate", "msu_su_profile_namespace_apply_held"),
        ),
    )
    private val factNames = setOf("schema", "registryMaxUids", "installationIdBytes", "profileGroups", "profileContextBytes", "installMaxMembers", "capLastCap", "configMultiuser", "namespaceApplied", "namespaceChanged", "namespaceCwdRestored", "namespaceRootPrivate", "namespaceCompleted", "phaseAdmission", "phaseSupport", "phaseCredentials", "phaseTask", "phaseNamespace", "phaseFinalValidate")
    private val sourceCallbacks = setOf("pin_current", "put_install", "read_grant", "validate_profile", "check_closed", "quiesce", "put_owner", "validate_producer")
    private val fenceMembers = setOf("check_closed", "begin", "revoke", "finish", "retire")
    private val namespaceTypes = mapOf(
        "ptRegs" to NamespaceType("pt_regs", mapOf("regs" to 10, "sp" to 12, "pstate" to 13), 9),
        "task" to NamespaceType("task_struct", mapOf("fs" to 14, "files" to 15, "nsproxy" to 16), null),
        "fs" to NamespaceType("fs_struct", mapOf("root" to 17, "pwd" to 18), 28),
        "path" to NamespaceType("path", mapOf("mnt" to 20, "dentry" to 21), 19),
        "nsproxy" to NamespaceType("nsproxy", mapOf("mnt_ns" to 22), 27),
        "files" to NamespaceType("files_struct", mapOf("count" to 23), 29),
        "procNsOperations" to NamespaceType("proc_ns_operations", emptyMap(), 26),
    )
    private data class Origin(val objectName: String, val source: String, val index: Int, val definitionObject: String, val definition: String)
    private data class NamespaceType(val structure: String, val fields: Map<String, Int>, val sizeWord: Int?)

    fun verify(report: JsonObject, manifest: JsonObject, payload: JsonObject, reference: JsonObject,
               elf: ByteArray, mode: String, normalize: Boolean) {
        Verifier(report, manifest, payload, reference, elf, mode, normalize).verify()
    }

    private class Verifier(val report: JsonObject, val manifest: JsonObject, val payload: JsonObject,
                           val reference: JsonObject, val elf: ByteArray, val mode: String, val normalize: Boolean) {
        private val revision = report["schemaVersion"]?.jsonPrimitive?.intOrNull
        private val savedProfile = revision == 4
        private val metadataFields = listOf("name", "explicit_profile", "allow_su", "non_root_use_default", "template_present")
        private var runtimeLayoutBound = false
        private val types: Map<String, Pair<String, List<String>>>
            get() {
                val saved = if (!savedProfile) baseTypes else baseTypes + mapOf(
                    "profile" to (baseTypes.getValue("profile").first to (baseTypes.getValue("profile").second + metadataFields)),
                    "installEntry" to (baseTypes.getValue("installEntry").first to (baseTypes.getValue("installEntry").second + "saved_profile")))
                return if (!runtimeLayoutBound) saved else saved + mapOf(
                    "installEntry" to (saved.getValue("installEntry").first to (saved.getValue("installEntry").second + "source_invalidated")),
                    "executionReceipt" to (saved.getValue("executionReceipt").first to (saved.getValue("executionReceipt").second + "task_profile")))
            }
        private val relocations: Map<String, Map<String, Set<String>>>
            get() = if (!runtimeLayoutBound) baseRelocations else baseRelocations + mapOf(
                "su_profile_execution" to mapOf(
                    "msu_su_apply_current_detailed" to setOf("msu_su_lease_begin", "msu_su_apply_held_stages", "msu_su_lease_end"),
                    "msu_su_apply_held_detailed" to setOf("msu_su_lease_validate", "msu_su_apply_held_stages"),
                    "msu_su_apply_held_stages" to setOf("msu_su_profile_apply_held", "msu_task_state_release_current", "msu_task_profile_apply_current", "msu_su_lease_validate", "msu_su_profile_namespace_apply_held")))
        private val functionOwners = if (!savedProfile) baseFunctionOwners else
            baseFunctionOwners + ("msu_su_lease_saved_profile" to "su_install_registry")
        private fun obj(owner: JsonObject, key: String) = owner.getValue(key).jsonObject
        private fun text(owner: JsonObject, key: String) = owner.getValue(key).jsonPrimitive.content
        private fun number(owner: JsonObject, key: String) = owner.getValue(key).jsonPrimitive.long
        private fun flag(owner: JsonObject, key: String) = owner.getValue(key).jsonPrimitive.boolean
        private fun same(a: JsonObject, b: JsonObject) = a["bytes"] == b["bytes"] && a["sha256"] == b["sha256"]
        private fun path(item: JsonObject) = text(item, "path").replace('\\', '/')
        private fun identity(value: JsonElement?): JsonObject {
            val item = value?.jsonObject ?: error("ULTRA_KERNEL_SU_COMPONENT_IDENTITY")
            require(path(item).isNotBlank() && number(item, "bytes") > 0 &&
                text(item, "sha256").matches(Regex("[0-9a-f]{64}"))) { "ULTRA_KERNEL_SU_COMPONENT_IDENTITY" }
            return item
        }
        private fun dependencyIdentity(value: JsonElement): JsonObject {
            val item = value.jsonObject
            if (!savedProfile || item["bytes"]?.jsonPrimitive?.longOrNull != 0L) return identity(value)
            val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            require(path(item).isNotBlank() && text(item, "sha256") == empty &&
                item["archivePath"]?.jsonPrimitive?.content == "build-dependencies/files/$empty.bin") {
                "ULTRA_KERNEL_SU_COMPONENT_DEPENDENCY_IDENTITY"
            }
            return item
        }
        private fun selected(value: JsonElement?) = identity(value).also { item ->
            val matches = obj(manifest, "inputs").entries.filter {
                val key = it.key.replace('\\', '/')
                path(item) == key || path(item).endsWith("/$key")
            }
            val longest = if (savedProfile) matches.maxOfOrNull { it.key.replace('\\', '/').length } else null
            val candidates = if (savedProfile) matches.filter { it.key.replace('\\', '/').length == longest } else matches
            require(candidates.singleOrNull()?.value == item["sha256"]) { "ULTRA_KERNEL_SU_COMPONENT_SELECTED_SOURCE" }
        }
        private fun keys(actual: Set<String>, expected: Set<String>, complete: Boolean, kind: String) {
            require(expected.containsAll(actual) && (!complete || actual == expected)) { "ULTRA_KERNEL_SU_COMPONENT_$kind" }
        }
        private fun signatures(owner: JsonObject, key: String) = obj(owner, key).mapValues { it.value.jsonPrimitive.content }
        private fun missing(owner: JsonObject, requireReason: Boolean) {
            val reasons = owner.getValue("missing").jsonArray
            require((!requireReason || reasons.isNotEmpty()) && reasons.all { it.jsonPrimitive.content.isNotBlank() }) {
                "ULTRA_KERNEL_SU_COMPONENT_DIAGNOSTIC"
            }
        }
        private fun arrays(key: String, expected: Set<String>, complete: Boolean): Map<String, JsonObject> {
            val list = report.getValue(key).jsonArray.map(::identity)
            val rows = list.associateBy { path(it).substringAfterLast('/') }
            require(rows.size == list.size) { "ULTRA_KERNEL_SU_COMPONENT_ARRAY_ALIAS:$key" }
            keys(rows.keys, expected, complete, "ARRAY:$key")
            return rows
        }
        private fun memberKey(prefix: String, member: String) =
            prefix + member.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

        fun verify() {
            require(revision in setOf(3, 4) && text(report, "schema") == "maplesu-su-provider-abi/$revision" &&
                mode in setOf("none", "kcfi")) { "ULTRA_KERNEL_SU_COMPONENT_SCHEMA" }
            require(listOf("metadataComplete", "providerComplete", "factoryBound", "lifecycleQualified", "targetQualified", "suReady")
                .all { !flag(report, it) }) { "ULTRA_KERNEL_SU_COMPONENT_QUALIFICATION_UNBOUND" }
            val complete = flag(report, "kernelMetadataComplete")
            val selection = obj(report, "sourceSelection")
            require(text(selection, "schema") == "maplesu-p2-su-component-source-selection/1" &&
                flag(selection, "nativeComponentSourcesStable") &&
                listOf("sourceOwnerFrozen", "adoptedToProduction", "providerComplete", "factoryBound", "targetQualified").all { !flag(selection, it) }) {
                "ULTRA_KERNEL_SU_COMPONENT_SOURCE_STATUS"
            }
            selected(selection["identity"])
            val core = obj(selection, "currentCoreSourceSelection")
            val actualCore = obj(obj(reference, "coreLifecycleAbi"), "sourceSelection")
            val coreSuccessors = setOf("runtimeSuccessor", "taskProfileSuccessor", "runtimeLayoutSourceSelection")
            val baselineCore = if (savedProfile) JsonObject(actualCore.filterKeys { it !in coreSuccessors }) else actualCore
            require(number(core, "lifecycleRevision") == 2L && core == baselineCore) { "ULTRA_KERNEL_SU_COMPONENT_CORE_SOURCE" }
            if (savedProfile) {
                val freeze = obj(obj(reference, "managementAbi"), "upstreamSourceFreeze")
                val task = reference["taskProfileAbi"]?.jsonObject?.get("implementation")?.jsonObject
                    ?.get("sourceSuccessor")?.jsonObject
                require((actualCore["runtimeSuccessor"] != null) == (freeze["coreRuntimeEvolution"] != null) &&
                    (actualCore["taskProfileSuccessor"] != null) == (task != null) &&
                    (actualCore["runtimeLayoutSourceSelection"] != null) == (freeze["runtimeLayoutEvolution"] != null)) {
                    "ULTRA_KERNEL_SU_COMPONENT_CORE_SUCCESSOR_SET"
                }
                actualCore["runtimeSuccessor"]?.let { successor ->
                    require(successor == freeze["coreRuntimeEvolution"] &&
                        same(selected(successor.jsonObject["identity"]), selected(obj(freeze, "coreRuntimeEvolution")["identity"]))) {
                        "ULTRA_KERNEL_SU_COMPONENT_CORE_RUNTIME_SUCCESSOR"
                    }
                }
                actualCore["taskProfileSuccessor"]?.let { successor ->
                    require(same(selected(successor), selected(task?.get("identity")))) {
                        "ULTRA_KERNEL_SU_COMPONENT_CORE_TASK_SUCCESSOR"
                    }
                }
                actualCore["runtimeLayoutSourceSelection"]?.let { successor ->
                    require(same(selected(successor), selected(obj(freeze, "runtimeLayoutEvolution")["identity"]))) {
                        "ULTRA_KERNEL_SU_COMPONENT_CORE_LAYOUT_SUCCESSOR"
                    }
                }
            }
            val provenance = obj(selection, "ownerProvenance")
            selected(provenance["sourceSelectionIdentity"])
            val historicalCore = selected(provenance["componentCoreFreezeIdentity"])
            val compatibleCore = if (number(core, "sourceFileCount") == 54L) {
                val evolution = obj(obj(obj(reference, "managementAbi"), "upstreamSourceFreeze"), "coreLifecycleEvolution")
                obj(evolution, "predecessorEvolution").also { previous ->
                    require(text(previous, "schema") == "maplesu-core-lifecycle-source-evolution/1" &&
                        number(previous, "lifecycleRevision") == 2L && !flag(previous, "productionWritten") &&
                        same(selected(previous["selectionIdentity"]), selected(core["previousCoreSelectionIdentity"])) &&
                        same(selected(previous["freezeIdentity"]), selected(core["previousCoreFreezeIdentity"]))) {
                        "ULTRA_KERNEL_SU_COMPONENT_CORE_CHANNEL_PREDECESSOR"
                    }
                }
            } else core
            require(same(historicalCore, selected(compatibleCore["freezeIdentity"])) ||
                same(historicalCore, selected(compatibleCore["previousCoreFreezeIdentity"]))) { "ULTRA_KERNEL_SU_COMPONENT_CORE_PREDECESSOR" }
            require(provenance.getValue("evidence").jsonArray.isNotEmpty()) { "ULTRA_KERNEL_SU_COMPONENT_OWNER_EVIDENCE_MISSING" }
            for (value in provenance.getValue("evidence").jsonArray) {
                val row = value.jsonObject
                require(same(identity(row["ownerIdentity"]), selected(row["snapshotIdentity"]))) {
                    "ULTRA_KERNEL_SU_COMPONENT_OWNER_EVIDENCE"
                }
            }
            val root = "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/"
            val expectedPaths = objects.flatMap { listOf("$root$it.c", "$root$it.h") }.toSet()
            val rows = selection.getValue("files").jsonArray.map { it.jsonObject }
            require(rows.size == expectedPaths.size && rows.map { text(it, "logicalPath") }.toSet() == expectedPaths &&
                selection.getValue("nativeTUs").jsonArray.map { it.jsonPrimitive.content }.toSet() == objects.map { "$root$it.c" }.toSet() &&
                selection.getValue("nativeTUs").jsonArray.size == objects.size) { "ULTRA_KERNEL_SU_COMPONENT_SOURCE_PATHS" }
            val parentSnapshots = rows.associate { row ->
                val logical = text(row, "logicalPath")
                val after = if (savedProfile) identity(row["snapshotIdentity"]) else selected(row["snapshotIdentity"])
                val source = identity(row["ownerSourceIdentity"])
                require(same(source, after) && path(source).endsWith("/$logical") && path(after).endsWith("/$logical")) {
                    "ULTRA_KERNEL_SU_COMPONENT_AFTER_IMAGE"
                }
                logical.substringAfterLast('/') to after
            }
            val snapshots = if (savedProfile) successorSources(selection, rows, parentSnapshots) else parentSnapshots
            require(snapshots.values.map(::path).toSet().size == rows.size) { "ULTRA_KERNEL_SU_COMPONENT_SOURCE_ALIAS" }
            val history = obj(selection, "readerBindingHistory")
            require(!flag(history, "qualified") && same(selected(history["sourceIdentity"]), identity(history["ownerIdentity"]))) {
                "ULTRA_KERNEL_SU_COMPONENT_READER_HISTORY"
            }
            require(path(identity(history["sourceIdentity"])).substringAfterLast('/') == "su_native_reader_abi.h" &&
                path(identity(history["ownerIdentity"])).endsWith("/runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/su_native_reader_abi.h")) {
                "ULTRA_KERNEL_SU_COMPONENT_READER_SOURCE"
            }
            selected(history["selectionIdentity"])
            val binding = obj(report, "readerBinding")
            require(!flag(binding, "available") && !flag(binding, "metadataComplete") &&
                same(identity(binding["sourceIdentity"]), identity(history["sourceIdentity"])) && text(binding, "reason").isNotBlank()) {
                "ULTRA_KERNEL_SU_COMPONENT_READER_UNQUALIFIED"
            }

            val names = objects + probes
            val sources = arrays("sourceIdentities", names.map { "$it.c" }.toSet(), complete)
            val objectIds = arrays("objectIdentities", names.map { "$it.o" }.toSet(), complete)
            val commands = arrays("commandIdentities", names.map { ".$it.o.cmd" }.toSet(), complete)
            val receipts = arrays("dependencyReceiptIdentities", names.map { "$it.json" }.toSet(), complete)
            val dependencies = report.getValue("inlineDependencies").jsonArray.map(::dependencyIdentity)
            for ((name, item) in sources) if (name in snapshots) require(same(item, snapshots.getValue(name)) &&
                dependencies.any { same(it, item) }) { "ULTRA_KERNEL_SU_COMPONENT_ACTUAL_SOURCE:$name" }
            if (complete) require(dependencies.isNotEmpty() && snapshots.values.all { source -> dependencies.any { same(source, it) } }) {
                "ULTRA_KERNEL_SU_COMPONENT_DEPENDENCIES"
            }
            val facts = obj(report, "compiledFacts").mapValues { it.value.jsonPrimitive.long }
            val profileFacts = obj(report, "profileCompiledFacts").mapValues { it.value.jsonPrimitive.long }
            verifyLayouts(facts, profileFacts, obj(report, "compiledLayouts"), complete)
            val functions = signatures(report, "localFunctionAbi")
            keys(functions.keys, functionOwners.keys, complete, "FUNCTIONS")
            require(functions.values.all { it.startsWith("f") && it.length <= 65536 }) { "ULTRA_KERNEL_SU_COMPONENT_FUNCTION_ABI" }
            val sourceAbi = signatures(report, "sourceCallbackAbi")
            keys(sourceAbi.keys, sourceCallbacks, complete, "SOURCE_CALLBACKS")
            for ((name, signature) in sourceAbi) require(signature.startsWith("*f") &&
                signature == obj(obj(obj(report, "compiledLayouts"), "registrySource"), "members")[name]?.jsonObject?.get("signature")?.jsonPrimitive?.content) {
                "ULTRA_KERNEL_SU_COMPONENT_SOURCE_CALLBACK:$name"
            }
            sourceAbi["validate_profile"]?.let { require(it == "*" + functions.getValue("msu_su_profile_check_execution")) {
                "ULTRA_KERNEL_SU_COMPONENT_VALIDATE_PROFILE"
            } }
            val originRows = obj(report, "callbackOrigins")
            keys(originRows.keys, origins.keys, complete, "CALLBACK_ORIGINS")
            val cfi = obj(report, "callbackCfi")
            for ((name, value) in originRows) {
                val row = value.jsonObject
                val expected = origins.getValue(name)
                require(text(row, "kind") == "internal" && text(row, "object") == expected.objectName &&
                    text(row, "source") == expected.source && number(row, "parameterIndex") == expected.index.toLong() &&
                    text(row, "definition") == expected.definition && text(row, "signature").startsWith("*f")) {
                    "ULTRA_KERNEL_SU_COMPONENT_CALLBACK_ORIGIN:$name"
                }
                val proof = cfi["callbacks"]?.jsonObject?.values?.map { it.jsonObject }
                    ?.singleOrNull { it["function"]?.jsonPrimitive?.content == expected.definition }
                proof?.let { require(same(identity(it["objectIdentity"]), objectIds.getValue("${expected.definitionObject}.o"))) {
                    "ULTRA_KERNEL_SU_COMPONENT_CALLBACK_OBJECT:$name"
                } }
                val definition = functions[expected.definition] ?: proof?.get("signature")?.jsonPrimitive?.content
                require(definition == null && !complete || text(row, "signature") == "*$definition") {
                    "ULTRA_KERNEL_SU_COMPONENT_CALLBACK_DEFINITION:$name"
                }
                val coreOrigin = obj(obj(reference, "coreLifecycleAbi"), "callbackParameters")[name.removePrefix("su.")]?.jsonObject
                require(coreOrigin == null && !complete || coreOrigin?.get("signature") == row["signature"]) {
                    "ULTRA_KERNEL_SU_COMPONENT_CORE_CALLBACK:$name"
                }
            }
            val fence = obj(report, "readerFenceAbi")
            keys(fence.keys, fenceMembers, complete, "READER_FENCE")
            for ((name, value) in fence) {
                val row = value.jsonObject
                val callback = callbacks.getValue("su.fence.$name").second
                require(text(row, "definition") == callback && text(row, "signature").startsWith("*f")) {
                    "ULTRA_KERNEL_SU_COMPONENT_READER_FENCE:$name"
                }
                cfi["callbacks"]?.jsonObject?.get("su.fence.$name")?.jsonObject?.let {
                    require(text(row, "signature") == "*" + text(it, "signature")) { "ULTRA_KERNEL_SU_COMPONENT_FENCE_ABI:$name" }
                }
                val coreFence = obj(obj(reference, "coreLifecycleAbi"), "readerFenceAbi")[name]
                require(coreFence == null && !complete || coreFence == row["signature"]) { "ULTRA_KERNEL_SU_COMPONENT_CORE_FENCE:$name" }
            }
            verifyRelocations(obj(report, "callbackRelocations"), complete)
            verifyCfi(cfi, functions, objectIds, complete)
            verifyImports(functions, complete)
            val native = obj(report, "nativeNamespace")
            verifyNamespace(native, facts, objectIds, complete)
            require(!complete || flag(native, "metadataComplete")) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_COMPLETE" }
            verifyEntries(obj(report, "entrypoints"), functions, cfi, complete)
            val config = obj(report, "config")
            val configKeys = setOf("CONFIG_MULTIUSER", "CONFIG_PREEMPT_RCU", "CONFIG_TREE_RCU", "CONFIG_REFCOUNT_FULL", "CONFIG_DEBUG_SPINLOCK",
                "CONFIG_NAMESPACES", "CONFIG_CFI", "CONFIG_CFI_CLANG", "CONFIG_CFI_ICALL_NORMALIZE_INTEGERS")
            keys(config.keys, configKeys, complete, "CONFIG")
            val actualConfig = obj(obj(reference, "managementAbi"), "config")
            require(config.all { (key, value) -> value.jsonPrimitive.content in setOf("y", "n") && value == (actualConfig[key] ?: JsonPrimitive("n")) }) {
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE"
            }
            if (config.isNotEmpty()) require(facts["configMultiuser"] == if (text(config, "CONFIG_MULTIUSER") == "y") 1L else 0L) {
                "ULTRA_KERNEL_SU_COMPONENT_MULTIUSER"
            }
            val objectNames = objectIds.keys.map { it.removeSuffix(".o") }.toSet()
            require(sources.keys.map { it.removeSuffix(".c") }.toSet().containsAll(objectNames) &&
                objectNames.containsAll(commands.keys.map { it.removePrefix(".").removeSuffix(".o.cmd") }) &&
                objectNames.containsAll(receipts.keys.map { it.removeSuffix(".json") })) { "ULTRA_KERNEL_SU_COMPONENT_OBJECT_SOURCE" }
            missing(report, true)
        }

        private fun successorSources(selection: JsonObject, rows: List<JsonObject>, parents: Map<String, JsonObject>): Map<String, JsonObject> {
            val metadata = obj(report, "profileMetadata")
            require(text(report, "previousReportSchema") == "maplesu-su-provider-abi/3" &&
                text(metadata, "schema") == "maplesu-su-profile-metadata-abi/1" &&
                metadata.getValue("storedFields").jsonArray.map { it.jsonPrimitive.content } == metadataFields &&
                flag(metadata, "savedAndEffectiveDistinct") && !flag(metadata, "consumerApplied") && !flag(metadata, "targetQualified") &&
                text(metadata, "savedAccessor") == "msu_su_lease_saved_profile" && text(metadata, "savedAccessorLifetime") == "held lease only") {
                "ULTRA_KERNEL_SU_COMPONENT_PROFILE_METADATA"
            }
            val freeze = obj(obj(reference, "managementAbi"), "upstreamSourceFreeze")
            val successor = obj(freeze, "suProfileMetadataEvolution")
            require(text(successor, "schema") == "maplesu-p2-su-profile-metadata-source-successor/1" &&
                listOf("sourceOwnerFrozen", "productionWritten", "adoptedToProduction", "fullSuComplete", "targetQualified", "suReady")
                    .all { !flag(successor, it) } && same(selected(successor["identity"]), selected(metadata["sourceSelection"])) &&
                number(successor, "sourcePathCount") == 2L && number(successor, "totalSelectedInputs") == 10L &&
                flag(successor, "savedAndEffectiveProfilesDistinct") &&
                successor.getValue("profileMetadataFields").jsonArray.map { it.jsonPrimitive.content } ==
                    listOf("name[256]", "explicit_profile", "allow_su", "non_root_use_default", "template_present")) {
                "ULTRA_KERNEL_SU_COMPONENT_PROFILE_SUCCESSOR"
            }
            fun archive(value: JsonElement?): JsonObject {
                val item = identity(value)
                val name = path(item)
                if (name.startsWith(".tmp/") || name.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(name)) return selected(item)
                val base = path(identity(successor["identity"])).substringBeforeLast('/')
                return selected(JsonObject(item.toMutableMap().apply { put("path", JsonPrimitive("$base/$name")) }))
            }
            val parent = obj(successor, "parentSelection")
            val parentArchive = identity(parent["identity"])
            val selectedParent = selected(selection["identity"])
            val parentSource = obj(parent, "sourceIdentity")
            require(same(parentArchive, selectedParent) &&
                text(parentArchive, "origin").replace('\\', '/') == path(selectedParent) &&
                text(parentSource, "path").replace('\\', '/') == path(selectedParent) &&
                parentSource["sha256"] == selectedParent["sha256"]) {
                "ULTRA_KERNEL_SU_COMPONENT_PROFILE_PARENT"
            }
            val units = obj(successor, "translationUnits")
            require(flag(units, "su_install_registry.c") && number(units, "unchangedTuCount") == 4L && number(units, "newTuCount") == 0L) {
                "ULTRA_KERNEL_SU_COMPONENT_PROFILE_TUS"
            }
            val root = "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/"
            val changed = successor.getValue("files").jsonArray.map { it.jsonObject }
            val changedByPath = changed.associateBy { text(it, "logicalPath") }
            require(changed.size == 2 && changedByPath.keys == setOf(root + "su_install_registry.c", root + "su_install_registry.h")) {
                "ULTRA_KERNEL_SU_COMPONENT_PROFILE_PATHS"
            }
            val current = parents.toMutableMap()
            for ((logical, row) in changedByPath) {
                val name = logical.substringAfterLast('/')
                val before = selected(row["before"])
                val expectedBeforePath = path(selected(successor["identity"])).substringBeforeLast('/') + "/sources/su10-before/$name"
                require(identity(row["ownerBefore"]) == parents.getValue(name) &&
                    (path(before) == expectedBeforePath || expectedBeforePath.endsWith("/" + path(before))) &&
                    same(before, parents.getValue(name)) &&
                    same(archive(row["ownerFrozen"]), selected(row["after"]))) { "ULTRA_KERNEL_SU_COMPONENT_PROFILE_GENERATION:$name" }
                current[name] = selected(row["after"])
            }
            val retained = successor.getValue("retainedInputs").jsonArray.map { it.jsonObject }
            require(retained.size == 8 && retained.map { text(it, "logicalPath") }.toSet() ==
                rows.map { text(it, "logicalPath") }.toSet() - changedByPath.keys) { "ULTRA_KERNEL_SU_COMPONENT_PROFILE_RETAINED" }
            for (row in retained) require(same(selected(row["identity"]), parents.getValue(text(row, "logicalPath").substringAfterLast('/')))) {
                "ULTRA_KERNEL_SU_COMPONENT_PROFILE_RETAINED_GENERATION"
            }
            val runtime = freeze["runtimeLayoutEvolution"]?.jsonObject ?: return current
            require(text(runtime, "schema") == "maplesu-claim-runtime-layout-source-selection/1" && flag(runtime, "sourceOwnerFrozen") &&
                !flag(runtime, "targetQualified") && !flag(runtime, "deviceValidated") &&
                same(selected(runtime["identity"]), selected(obj(obj(reference, "coreLifecycleAbi"), "sourceSelection")["runtimeLayoutSourceSelection"]))) {
                "ULTRA_KERNEL_SU_COMPONENT_RUNTIME_SUCCESSOR"
            }
            val runtimeRows = runtime.getValue("files").jsonArray.map { it.jsonObject }
            require(runtimeRows.map { text(it, "logicalPath") }.toSet().size == runtimeRows.size) { "ULTRA_KERNEL_SU_COMPONENT_RUNTIME_ALIAS" }
            val expectedPaths = rows.map { text(it, "logicalPath") }.toSet()
            val runtimeSu = runtimeRows.filter { text(it, "logicalPath") in expectedPaths }
            require(runtimeSu.map { text(it, "logicalPath") }.toSet().containsAll(setOf(
                root + "su_install_registry.c", root + "su_profile_execution.c", root + "su_profile_execution.h"))) {
                "ULTRA_KERNEL_SU_COMPONENT_RUNTIME_LAYOUT_FIELDS"
            }
            for (row in runtimeSu) {
                val name = text(row, "logicalPath").substringAfterLast('/')
                require(same(selected(row["before"]), current.getValue(name)) &&
                    path(selected(row["after"])).substringAfterLast('/') == name) { "ULTRA_KERNEL_SU_COMPONENT_RUNTIME_GENERATION:$name" }
                current[name] = selected(row["after"])
            }
            runtimeLayoutBound = true
            return current
        }

        private fun verifyLayouts(facts: Map<String, Long>, profileFacts: Map<String, Long>, layouts: JsonObject, complete: Boolean) {
            val expected = factNames.toMutableSet()
            val expectedProfile = mutableSetOf("schema")
            for ((prefix, type) in types) {
                val into = if (prefix == "profileExecution" || savedProfile && prefix == "profile") expectedProfile else expected
                into += prefix + "Bytes"; into += prefix + "Align"
                for (member in type.second) { into += memberKey(prefix, member) + "Offset"; into += memberKey(prefix, member) + "Bytes" }
            }
            require((facts.isEmpty() && !complete || facts.keys == expected) &&
                (profileFacts.isEmpty() && !complete || profileFacts.keys == expectedProfile)) { "ULTRA_KERNEL_SU_COMPONENT_FACT_FIELDS" }
            val constants = mapOf("schema" to 1L, "registryMaxUids" to 4095L, "installationIdBytes" to 32L,
                "profileGroups" to 32L, "profileContextBytes" to 64L, "installMaxMembers" to 128L,
                "namespaceApplied" to 1L, "namespaceChanged" to 2L, "namespaceCwdRestored" to 4L,
                "namespaceRootPrivate" to 8L, "namespaceCompleted" to 16L, "phaseAdmission" to 1L,
                "phaseSupport" to 2L, "phaseCredentials" to 3L, "phaseTask" to 4L, "phaseNamespace" to 5L, "phaseFinalValidate" to 6L)
            if (facts.isNotEmpty()) require(constants.all { (key, value) -> facts[key] == value } &&
                facts.getValue("capLastCap") in 0L..63L && facts.getValue("configMultiuser") in 0L..1L) { "ULTRA_KERNEL_SU_COMPONENT_FACT_VALUES" }
            if (profileFacts.isNotEmpty()) require(profileFacts["schema"] == 1L) { "ULTRA_KERNEL_SU_COMPONENT_PROFILE_FACTS" }
            keys(layouts.keys, types.keys, complete, "LAYOUTS")
            for ((prefix, value) in layouts) {
                val type = types.getValue(prefix)
                val compiled = if (prefix == "profileExecution" || savedProfile && prefix == "profile") profileFacts else facts
                val layout = value.jsonObject
                val bytes = number(layout, "bytes")
                val align = compiled.getValue(prefix + "Align")
                require(bytes > 0 && bytes <= Int.MAX_VALUE && bytes == compiled[prefix + "Bytes"] &&
                    align > 0 && align and (align - 1) == 0L && bytes % align == 0L) { "ULTRA_KERNEL_SU_COMPONENT_LAYOUT:$prefix" }
                val members = obj(layout, "members")
                require(members.keys == type.second.toSet()) { "ULTRA_KERNEL_SU_COMPONENT_MEMBERS:$prefix" }
                val signature = "s${type.first}:$bytes{" + members.values.map { it.jsonObject }.sortedBy { number(it, "offset") }.joinToString("") {
                    val offset = number(it, "offset"); val size = number(it, "bytes")
                    require(offset >= 0 && size > 0 && offset <= bytes && size <= bytes - offset && text(it, "signature").isNotEmpty()) {
                        "ULTRA_KERNEL_SU_COMPONENT_MEMBER_RANGE:$prefix"
                    }
                    "${offset * 8}=${text(it, "signature")};"
                } + "}"
                require(text(layout, "signature") == signature) { "ULTRA_KERNEL_SU_COMPONENT_LAYOUT_SIGNATURE:$prefix" }
                for ((name, field) in members) require(number(field.jsonObject, "offset") == compiled[memberKey(prefix, name) + "Offset"] &&
                    number(field.jsonObject, "bytes") == compiled[memberKey(prefix, name) + "Bytes"]) { "ULTRA_KERNEL_SU_COMPONENT_LAYOUT_FACT:$prefix.$name" }
            }
        }

        private fun verifyRelocations(actual: JsonObject, complete: Boolean) {
            keys(actual.keys, relocations.keys, complete, "RELOCATIONS")
            for ((objectName, value) in actual) {
                val expected = relocations.getValue(objectName)
                val callers = value.jsonObject
                keys(callers.keys, expected.keys, true, "RELOCATION_CALLERS")
                for ((caller, entries) in callers) {
                    val targets = entries.jsonObject
                    keys(targets.keys, expected.getValue(caller), true, "RELOCATION_TARGETS")
                    for ((target, rows) in targets) require(rows.jsonArray.isNotEmpty() && rows.jsonArray.all {
                        val row = it.jsonObject
                        number(row, "offset") >= 0 && number(row, "type") > 0 && text(row, "section").isNotBlank() &&
                            text(row, "resolvedTarget") == target && row["symbol"] is JsonPrimitive && row["addend"]?.jsonPrimitive?.longOrNull != null
                    }) { "ULTRA_KERNEL_SU_COMPONENT_RELOCATION:$objectName.$caller.$target" }
                }
            }
        }

        private fun verifyCfi(cfi: JsonObject, functions: Map<String, String>, objectsByName: Map<String, JsonObject>, complete: Boolean) {
            require(cfi.isNotEmpty() || !complete) { "ULTRA_KERNEL_SU_COMPONENT_CFI_MISSING" }
            if (cfi.isEmpty()) return
            require(text(cfi, "mode") == mode && flag(cfi, "normalizeIntegers") == normalize) { "ULTRA_KERNEL_SU_COMPONENT_CFI_MODE" }
            val names = objects + probes + setOf("manager_management", "manager_uid_gateway", "manager_task_state", "manager_core_uid_facts")
            val policies = obj(cfi, "objectPolicies")
            require(policies.keys == names && policies.values.all {
                val policy = it.jsonObject
                flag(policy, "kcfiEnabled") == (mode == "kcfi") && flag(policy, "kcfiOptionPresent") == (mode == "kcfi") &&
                    flag(policy, "normalizeIntegers") == normalize
            }) { "ULTRA_KERNEL_SU_COMPONENT_CFI_POLICIES" }
            val cfiFunctions = obj(cfi, "functions")
            val cfiCallbacks = obj(cfi, "callbacks")
            keys(cfiFunctions.keys, functionOwners.keys, complete, "CFI_FUNCTIONS")
            keys(cfiCallbacks.keys, callbacks.keys, complete, "CFI_CALLBACKS")
            fun entry(row: JsonObject, name: String, owner: String, signature: String?) {
                require(text(row, "function") == name && text(row, "signature").startsWith("f") &&
                    (signature == null || text(row, "signature") == signature) && flag(row, "verified") &&
                    same(identity(row["objectIdentity"]), objectsByName.getValue("$owner.o")) &&
                    same(identity(row["typeProbeObjectIdentity"]), objectsByName.getValue("manager_su_component_callback_types.o"))) {
                    "ULTRA_KERNEL_SU_COMPONENT_CFI_SOURCE:$name"
                }
                val extra = setOf("typeidSymbol", "typeid", "prefixOffset", "functionOffset", "prefixWord")
                if (mode == "kcfi") {
                    val id = number(row, "typeid"); val at = number(row, "functionOffset")
                    require(id in 0..0xffffffffL && text(row, "typeidSymbol") == "__kcfi_typeid_$name" && at >= 4 && at % 4 == 0L &&
                        number(row, "prefixOffset") == at - 4 && number(row, "prefixWord") == id) { "ULTRA_KERNEL_SU_COMPONENT_CFI_PREFIX:$name" }
                } else require(row.keys.intersect(extra).isEmpty()) { "ULTRA_KERNEL_SU_COMPONENT_CFI_DISABLED:$name" }
            }
            for ((name, row) in cfiFunctions) entry(row.jsonObject, name, functionOwners.getValue(name), functions.getValue(name))
            for ((role, row) in cfiCallbacks) {
                val (owner, name) = callbacks.getValue(role)
                entry(row.jsonObject, name, owner, functions[name])
                cfiFunctions[name]?.let { require(it == row) { "ULTRA_KERNEL_SU_COMPONENT_CFI_SHARED:$role" } }
            }
        }

        private fun verifyImports(functions: Map<String, String>, complete: Boolean) {
            val importsList = payload.getValue("imports").jsonArray.map { it.jsonPrimitive.content }
            val imports = importsList.toSet()
            val internal = functionOwners.keys + obj(obj(reference, "coreLifecycleAbi"), "localFunctionAbi").keys +
                setOf("msu_uid_gateway_acquire", "msu_uid_snapshot_lookup", "msu_uid_snapshot_put")
            require(imports.size == importsList.size && imports.intersect(internal).isEmpty()) { "ULTRA_KERNEL_SU_COMPONENT_INTERNAL_IMPORT" }
            val undefined = report["objectUndefinedSymbols"]?.jsonObject
            require(undefined != null || !complete) { "ULTRA_KERNEL_SU_COMPONENT_UNDEFINED_MISSING" }
            undefined?.let { rows ->
                keys(rows.keys, objects, complete, "UNDEFINED_OBJECTS")
                val names = mutableSetOf<String>()
                for ((_, value) in rows) {
                    val values = value.jsonArray.map { it.jsonPrimitive.content }
                    require(values.toSet().size == values.size && values.all(String::isNotBlank)) { "ULTRA_KERNEL_SU_COMPONENT_UNDEFINED_NAMES" }
                    names += values
                }
                val actual = report["finalImports"]?.jsonObject
                require(actual != null || !complete) { "ULTRA_KERNEL_SU_COMPONENT_FINAL_IMPORT_MISSING" }
                actual?.let { entries -> require(entries.keys == names.intersect(imports) && entries.all { (name, value) ->
                    value == obj(reference, "functionAbi")[name] || value == obj(reference, "dataAbi")[name]
                }) { "ULTRA_KERNEL_SU_COMPONENT_FINAL_IMPORT_ABI" } }
            }
            require(functionOwners.keys.containsAll(functions.keys)) { "ULTRA_KERNEL_SU_COMPONENT_LOCAL_FUNCTIONS" }
        }

        private fun verifyNamespace(native: JsonObject, facts: Map<String, Long>, objectIds: Map<String, JsonObject>, complete: Boolean) {
            require(!flag(native, "targetQualified")) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_UNQUALIFIED" }
            val nativeComplete = flag(native, "metadataComplete")
            val compiledFacts = native["compiledFacts"]?.jsonObject.orEmpty()
            require(compiledFacts.isNotEmpty() || !nativeComplete) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_FACTS_MISSING" }
            if (compiledFacts.isNotEmpty()) require(compiledFacts.keys == setOf("schema", "nativeSource", "nativeVerified") &&
                compiledFacts.getValue("schema").jsonPrimitive.int == 1 && compiledFacts.getValue("nativeSource").jsonPrimitive.int in 0..1 &&
                compiledFacts.getValue("nativeVerified").jsonPrimitive.int == 0) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_FACTS" }
            val values = native["layoutValues"]?.jsonArray?.map { it.jsonPrimitive.long }
            require(values != null || !nativeComplete) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_LAYOUT_MISSING" }
            if (values != null) {
                require(compiledFacts["nativeSource"]?.jsonPrimitive?.int == 1 && number(native, "layoutSchema") == 1L &&
                    number(native, "layoutWords") == 31L && number(native, "layoutWordBytes") == 8L && values.size == 31 && values.all { it >= 0 } &&
                    values[0] == 1L && text(native, "symbol") == "msu_su_namespace_native_layout" &&
                    text(native, "sectionName") == ".msu.su_namespace.layout" && number(native, "sectionFlags") == 2L &&
                    same(identity(native["objectIdentity"]), objectIds.getValue("su_profile_namespace.o"))) {
                    "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_LAYOUT"
                }
                val bytes = ByteBuffer.allocate(values.size * 8).order(ByteOrder.LITTLE_ENDIAN)
                values.forEach(bytes::putLong)
                require(UltraKernelSpec.sha256(bytes.array()) == text(native, "compiledLayoutSha256") &&
                    values[1] == facts["namespaceReceiptBytes"]) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_LAYOUT_HASH" }
                for ((index, member) in types.getValue("namespaceReceipt").second.withIndex()) require(
                    values[index + 2] == facts[memberKey("namespaceReceipt", member) + "Offset"]) {
                    "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_RECEIPT:$member"
                }
            }
            val layouts = obj(native, "compiledLayouts")
            val targets = obj(native, "targetLayouts")
            keys(layouts.keys, namespaceTypes.keys, nativeComplete, "NAMESPACE_COMPILED_LAYOUTS")
            keys(targets.keys, namespaceTypes.keys, nativeComplete, "NAMESPACE_TARGET_LAYOUTS")
            for ((name, value) in layouts) {
                val type = namespaceTypes.getValue(name)
                val layout = value.jsonObject
                val bytes = number(layout, "bytes")
                val members = obj(layout, "members")
                require(values != null && bytes > 0 && bytes <= Int.MAX_VALUE && members.keys == type.fields.keys &&
                    (type.sizeWord == null || bytes == values[type.sizeWord]) && type.fields.all { (member, index) ->
                        val field = members.getValue(member).jsonObject
                        val offset = number(field, "offset"); val size = number(field, "bytes")
                        offset == values[index] && size > 0 && offset <= bytes && size <= bytes - offset && text(field, "signature").isNotBlank()
                    }) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_LAYOUT:$name" }
                require(text(layout, "signature").startsWith("s${type.structure}:$bytes{") && text(layout, "signature").endsWith("}")) {
                    "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_LAYOUT_SIGNATURE:$name"
                }
                targets[name]?.let { require(it == value) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_TARGET_LAYOUT:$name" } }
                if (name == "ptRegs") require(number(members.getValue("regs").jsonObject, "bytes") % 31 == 0L &&
                    number(members.getValue("regs").jsonObject, "bytes") / 31 == values[11]) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_REGISTERS" }
            }
            require(layouts.keys.containsAll(targets.keys)) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_LAYOUT_ORDER" }
            val functions = obj(native, "functionAbi")
            val data = obj(native, "dataAbi")
            keys(functions.keys, setOf("__arm64_sys_setns", "__arm64_sys_unshare", "__arm64_sys_close", "path_mount"), nativeComplete, "NAMESPACE_FUNCTIONS")
            keys(data.keys, setOf("init_cred", "init_pid_ns", "mntns_operations"), nativeComplete, "NAMESPACE_DATA")
            fun marker(item: JsonObject, name: String, function: Boolean): JsonObject? {
                require(text(item, "signature").isNotBlank() && text(item, "declarationSignature") == "*" + text(item, "signature") &&
                    (!nativeComplete || flag(item, "verified"))) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_ABI:$name" }
                val mark = obj(item, "marker")
                if (!flag(mark, "available")) {
                    require(!nativeComplete && !flag(item, "verified") && text(mark, "reason").isNotBlank() &&
                        text(mark, if (function) "function" else "name") == name) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_MARKER_UNAVAILABLE:$name" }
                    return null
                }
                require(text(mark, if (function) "function" else "name") == name &&
                    number(mark, "symbolIndex") >= 0 && text(mark, "section").isNotBlank()) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_MARKER:$name" }
                val at = text(mark, "address").toULong(); val start = text(mark, "sectionAddress").toULong()
                val size = number(mark, if (function) "functionBytes" else "bytes")
                val capacity = number(mark, "sectionBytes")
                require(at >= start && size > 0 && capacity > 0 && at - start <= capacity.toULong() &&
                    size.toULong() <= capacity.toULong() - (at - start)) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_MARKER_RANGE:$name" }
                val flags = number(mark, "sectionFlags")
                require(if (function) flags and 4L != 0L && at % 4uL == 0uL && number(mark, "fileOffset") >= 0
                    else flags and 2L != 0L && flags and 4L == 0L) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_MARKER_FLAGS:$name" }
                return mark
            }
            for ((name, value) in functions) {
                val item = value.jsonObject
                val mark = marker(item, name, true) ?: continue
                require(text(item, "signature").startsWith("f")) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_FUNCTION:$name" }
                reference["functionAbi"]?.jsonObject?.get(name)?.let { require(it == item["signature"]) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_REFERENCE:$name" } }
                if (mode == "kcfi" && (flag(item, "verified") || item["cfi"] != null)) {
                    val cfi = obj(item, "cfi")
                    val id = number(cfi, "declarationTypeid"); val at = text(mark, "address").toULong()
                    require(text(cfi, "mode") == "kcfi" && text(cfi, "typeidSymbol") == "__kcfi_typeid_$name" && id in 0..0xffffffffL &&
                        flag(cfi, "referencePrefixVerified") && number(cfi, "referencePrefixWord") == id && number(mark, "prefixWord") == id &&
                        at >= 4uL && text(mark, "prefixAddress").toULong() == at - 4uL) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_KCFI:$name" }
                } else require(item["cfi"] == null) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_CFI_DISABLED:$name" }
            }
            for ((name, value) in data) {
                val item = value.jsonObject
                val mark = marker(item, name, false) ?: continue
                val word = when (name) { "init_cred" -> 24; "init_pid_ns" -> 25; else -> 26 }
                require(values != null && number(mark, "bytes") == values[word]) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_DATA_SIZE:$name" }
                reference["dataAbi"]?.jsonObject?.get(name)?.let { require(it == item["signature"]) { "ULTRA_KERNEL_SU_COMPONENT_NAMESPACE_DATA_ABI:$name" } }
            }
            require(!complete || nativeComplete) { "ULTRA_KERNEL_SU_COMPONENT_NATIVE_COMPLETE" }
            missing(native, false)
        }

        private fun verifyEntries(entries: JsonObject, functions: Map<String, String>, cfi: JsonObject, complete: Boolean) {
            val entriesComplete = flag(report, "entrypointsComplete")
            keys(entries.keys, functions.keys, complete || entriesComplete, "ENTRYPOINTS")
            for ((name, value) in entries) {
                val entry = value.jsonObject
                if (!flag(entry, "available")) {
                    require(!entriesComplete && entry.keys == setOf("available", "reason") && text(entry, "reason").isNotBlank()) {
                        "ULTRA_KERNEL_SU_COMPONENT_ENTRY_UNAVAILABLE:$name"
                    }
                    continue
                }
                val address = number(entry, "entryOffset"); val file = number(entry, "fileOffset")
                val bytes = number(entry, "bytes"); val start = number(entry, "sectionOffset"); val capacity = number(entry, "sectionBytes")
                require(address >= start && address % 4 == 0L && start >= 0 && capacity > 0 && bytes > 0 &&
                    address - start <= capacity && bytes <= capacity - (address - start) && file >= 0 && file <= elf.size &&
                    bytes <= elf.size - file && text(entry, "sectionName").isNotBlank()) { "ULTRA_KERNEL_SU_COMPONENT_ENTRY_RANGE:$name" }
                val prefixFields = setOf("prefixOffset", "prefixWord", "typeid", "prefixVerified")
                if (mode == "kcfi") {
                    val id = number(obj(obj(cfi, "functions"), name), "typeid")
                    require(id in 0..0xffffffffL && address - start >= 4 && file >= 4 && number(entry, "prefixOffset") == address - 4 &&
                        flag(entry, "prefixVerified") && number(entry, "prefixWord") == id && number(entry, "typeid") == id &&
                        (UltraKernelA64.le32Bits(elf, (file - 4).toInt()).toLong() and 0xffffffffL) == id) { "ULTRA_KERNEL_SU_COMPONENT_ENTRY_KCFI:$name" }
                } else require(entry.keys.intersect(prefixFields).isEmpty()) { "ULTRA_KERNEL_SU_COMPONENT_ENTRY_CFI_DISABLED:$name" }
            }
            require(!entriesComplete || entries.isNotEmpty() && entries.keys == functionOwners.keys && entries.values.all { flag(it.jsonObject, "available") }) {
                "ULTRA_KERNEL_SU_COMPONENT_ENTRIES_COMPLETE"
            }
        }
    }
}

