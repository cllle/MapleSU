package dev.maplesu.manager.ultrakernel

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.*

private class DirectReferenceClaims(private val root: File) : UltraKernelAssetSource {
    private fun file(path: String): File {
        check(path == "ultra-manager-claim" || path.startsWith("ultra-manager-claim/"))
        return File(root, path.removePrefix("ultra-manager-claim").removePrefix("/"))
    }
    override fun list(directory: String) = file(directory).list()?.toList().orEmpty()
    override fun read(path: String) = file(path).readBytes()
}

private fun replaceReferenceFact(owner: JsonObject, path: List<String>, value: JsonElement?): JsonObject =
    JsonObject(owner.toMutableMap().apply {
        if (path.size == 1) {
            if (value == null) remove(path.single()) else put(path.single(), value)
        } else put(path.first(), replaceReferenceFact(owner.getValue(path.first()).jsonObject, path.drop(1), value))
    })

private class RepackedReferenceClaims(
    private val base: UltraKernelAssetSource,
    manifest: JsonObject,
    family: String,
    payload: JsonObject,
) : UltraKernelAssetSource {
    private val overlays = mutableMapOf<String, ByteArray>()
    init {
        val decoded = payload.toString().toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().also { bytes ->
            GZIPOutputStream(bytes).use { it.write(decoded) }
        }.toByteArray()
        val chunks = compressed.indices.step(512 * 1024).mapIndexed { index, start ->
            val part = compressed.copyOfRange(start, minOf(start + 512 * 1024, compressed.size))
            val path = "$family/reference-consumer-test-$index.json.gz"
            overlays["ultra-manager-claim/$path"] = part
            buildJsonObject {
                put("path", path); put("bytes", part.size); put("sha256", UltraKernelSpec.sha256(part))
            }
        }
        val original = manifest.getValue("payloads").jsonObject.getValue(family).jsonObject
        val descriptor = JsonObject(original.toMutableMap().apply {
            put("decodedBytes", JsonPrimitive(decoded.size))
            put("decodedSha256", JsonPrimitive(UltraKernelSpec.sha256(decoded)))
            put("gzipBytes", JsonPrimitive(compressed.size))
            put("gzipSha256", JsonPrimitive(UltraKernelSpec.sha256(compressed)))
            put("chunks", JsonArray(chunks))
        })
        val selected = replaceReferenceFact(manifest, listOf("payloads", family), descriptor)
        overlays["ultra-manager-claim/manifest.json"] = selected.toString().toByteArray(Charsets.UTF_8)
    }
    override fun list(directory: String) = base.list(directory)
    override fun read(path: String) = overlays[path] ?: base.read(path)
}

/** Only the fixed test owner executes this fixture; it writes no assets or production sources. */
fun main(args: Array<String>) {
    val root = File(args[0]).canonicalFile
    val phase = args[1]
    check(phase in setOf("red", "green", "abi4-known-red", "abi4-r5", "abi4-r5-load"))
    val family = "android15-6.6"
    val release = "6.6.1"
    val base = DirectReferenceClaims(root)
    val manifestBytes = base.read("ultra-manager-claim/manifest.json")
    check(UltraKernelSpec.sha256(manifestBytes) == "2bc643e4fa67cb26ff3dc6d6256aa004033ae75ab7dda90374f703f79061909a")
    val manifest = Json.parseToJsonElement(manifestBytes.decodeToString()).jsonObject
    if (phase == "red") {
        val failure = runCatching { UltraKernelManagerClaimPayload.load(base, release) }.exceptionOrNull()
        check(failure?.message == "ULTRA_KERNEL_MANAGEMENT_REFERENCE_INCOMPLETE:null") { "REFERENCE_RED_NOT_REPRODUCED:$failure" }
        println("PASS reference-original-consumer-red:$failure")
        return
    }
    val descriptor = manifest.getValue("payloads").jsonObject.getValue(family).jsonObject
    val compressed = ByteArrayOutputStream().also { stream ->
        descriptor.getValue("chunks").jsonArray.forEach { part ->
            stream.write(base.read("ultra-manager-claim/" + part.jsonObject.getValue("path").jsonPrimitive.content))
        }
    }.toByteArray()
    val originalPayload = GZIPInputStream(compressed.inputStream()).use { stream ->
        Json.parseToJsonElement(stream.readBytes().decodeToString()).jsonObject
    }
    val payload = if (phase !in setOf("abi4-r5", "abi4-r5-load")) originalPayload else {
        val receiptBytes = File(args[2]).readBytes()
        check(UltraKernelSpec.sha256(receiptBytes) == args[3]) { "R5_ACTUAL_REFERENCE_RECEIPT_HASH" }
        val actual = Json.parseToJsonElement(receiptBytes.decodeToString()).jsonObject
        check(actual.getValue("claimSha256") == originalPayload.getValue("sha256")) { "R5_ACTUAL_REFERENCE_CLAIM" }
        replaceReferenceFact(originalPayload, listOf("referenceAbi"), actual)
    }
    if (phase in setOf("abi4-known-red", "abi4-r5", "abi4-r5-load")) {
        fun expected(name: String, changed: JsonObject, prefix: String) {
            val failure = runCatching {
                UltraKernelManagerClaimPayload.load(RepackedReferenceClaims(base, manifest, family, changed), release)
            }.exceptionOrNull()
            check(failure?.message?.startsWith(prefix) == true) { "ABI4_NEGATIVE:$name:$failure" }
            println("PASS ABI4_BOUNDARY $name:${failure?.message}")
        }
        if (phase == "abi4-known-red") {
            expected("selected-load-remains-red", payload, "ULTRA_KERNEL_SU_COMPONENT_FACT_FIELDS")
        } else {
            val loaded = UltraKernelManagerClaimPayload.load(RepackedReferenceClaims(base, manifest, family, payload), release)
            check(loaded.referenceAbi?.management?.sourceQualification?.claimSha256 == UltraKernelSpec.sha256(loaded.elf))
            println("PASS reference-real-r5-selected-load")
            if (phase == "abi4-r5-load") return
        }
        val su = listOf("referenceAbi", "suProviderAbi")
        val fields = listOf("name", "explicit_profile", "allow_su", "non_root_use_default", "template_present")
        expected("schema-version-mismatch", replaceReferenceFact(payload, su + "schema", JsonPrimitive("maplesu-su-provider-abi/3")), "ULTRA_KERNEL_SU_COMPONENT_SCHEMA")
        expected("stored-fields-missing", replaceReferenceFact(payload, su + listOf("profileMetadata", "storedFields"),
            JsonArray(fields.dropLast(1).map(::JsonPrimitive))), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_METADATA")
        expected("saved-effective-conflated", replaceReferenceFact(payload, su + listOf("profileMetadata", "savedAndEffectiveDistinct"), JsonPrimitive(false)), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_METADATA")
        expected("profile-target-promoted", replaceReferenceFact(payload, su + listOf("profileMetadata", "targetQualified"), JsonPrimitive(true)), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_METADATA")
        val successor = listOf("referenceAbi", "managementAbi", "upstreamSourceFreeze", "suProfileMetadataEvolution")
        expected("source-successor-count", replaceReferenceFact(payload, successor + "sourcePathCount", JsonPrimitive(3)), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_SUCCESSOR")
        expected("new-tu-promoted", replaceReferenceFact(payload, successor + listOf("translationUnits", "newTuCount"), JsonPrimitive(1)), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_TUS")
        expected("saved-accessor-owner", replaceReferenceFact(payload, su + listOf("profileMetadata", "savedAccessor"), JsonPrimitive("msu_su_lease_profile")), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_METADATA")
        val corePath = listOf("referenceAbi", "coreLifecycleAbi", "sourceSelection")
        val actualCore = payload.getValue("referenceAbi").jsonObject.getValue("coreLifecycleAbi").jsonObject
            .getValue("sourceSelection").jsonObject
        for (key in listOf("runtimeSuccessor", "taskProfileSuccessor", "runtimeLayoutSourceSelection")) {
            expected("core-$key-missing", replaceReferenceFact(payload, corePath + key, null), "ULTRA_KERNEL_SU_COMPONENT_CORE_SUCCESSOR_SET")
        }
        expected("core-runtime-successor-content", replaceReferenceFact(payload,
            corePath + listOf("runtimeSuccessor", "sourceFileCount"),
            JsonPrimitive(actualCore.getValue("runtimeSuccessor").jsonObject.getValue("sourceFileCount").jsonPrimitive.long + 1)),
            "ULTRA_KERNEL_SU_COMPONENT_CORE_RUNTIME_SUCCESSOR")
        for ((key, prefix) in listOf("taskProfileSuccessor" to "ULTRA_KERNEL_SU_COMPONENT_CORE_TASK_SUCCESSOR",
            "runtimeLayoutSourceSelection" to "ULTRA_KERNEL_SU_COMPONENT_CORE_LAYOUT_SUCCESSOR")) {
            expected("core-$key-bytes", replaceReferenceFact(payload, corePath + listOf(key, "bytes"),
                JsonPrimitive(actualCore.getValue(key).jsonObject.getValue("bytes").jsonPrimitive.long + 1)), prefix)
        }
        val parentPath = successor + "parentSelection"
        val parent = payload.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
            .getValue("upstreamSourceFreeze").jsonObject.getValue("suProfileMetadataEvolution").jsonObject
            .getValue("parentSelection").jsonObject
        expected("profile-parent-bytes", replaceReferenceFact(payload, parentPath + listOf("identity", "bytes"),
            JsonPrimitive(parent.getValue("identity").jsonObject.getValue("bytes").jsonPrimitive.long + 1)), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_PARENT")
        expected("profile-parent-sha256", replaceReferenceFact(payload, parentPath + listOf("identity", "sha256"),
            JsonPrimitive("0".repeat(64))), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_PARENT")
        expected("profile-parent-origin", replaceReferenceFact(payload, parentPath + listOf("identity", "origin"),
            JsonPrimitive("D:/different-generation/SU-COMPONENT-SOURCE-SELECTION.json")), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_PARENT")
        expected("profile-parent-source-sha256", replaceReferenceFact(payload, parentPath + listOf("sourceIdentity", "sha256"),
            JsonPrimitive("0".repeat(64))), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_PARENT")
        val originalRows = payload.getValue("referenceAbi").jsonObject.getValue("suProviderAbi").jsonObject
            .getValue("sourceSelection").jsonObject.getValue("files").jsonArray
        fun snapshotChanged(key: String, value: JsonElement): JsonObject {
            val first = originalRows.first().jsonObject
            val changed = replaceReferenceFact(first, listOf("snapshotIdentity", key), value)
            return replaceReferenceFact(payload, su + listOf("sourceSelection", "files"),
                JsonArray(listOf(changed) + originalRows.drop(1)))
        }
        val firstSnapshot = originalRows.first().jsonObject.getValue("snapshotIdentity").jsonObject
        expected("historical-snapshot-bytes", snapshotChanged("bytes", JsonPrimitive(firstSnapshot.getValue("bytes").jsonPrimitive.long + 1)), "ULTRA_KERNEL_SU_COMPONENT_AFTER_IMAGE")
        expected("historical-snapshot-sha256", snapshotChanged("sha256", JsonPrimitive("0".repeat(64))), "ULTRA_KERNEL_SU_COMPONENT_AFTER_IMAGE")
        expected("historical-snapshot-path", snapshotChanged("path", JsonPrimitive(".tmp/different-generation/registry.c")), "ULTRA_KERNEL_SU_COMPONENT_AFTER_IMAGE")
        val beforePath = successor + "files"
        val beforeRows = payload.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
            .getValue("upstreamSourceFreeze").jsonObject.getValue("suProfileMetadataEvolution").jsonObject.getValue("files").jsonArray
        val changedBefore = replaceReferenceFact(beforeRows.first().jsonObject, listOf("before", "bytes"),
            JsonPrimitive(beforeRows.first().jsonObject.getValue("before").jsonObject.getValue("bytes").jsonPrimitive.long + 1))
        expected("historical-before-bytes", replaceReferenceFact(payload, beforePath, JsonArray(listOf(changedBefore) + beforeRows.drop(1))), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_GENERATION")
        val changedOwnerPath = replaceReferenceFact(beforeRows.first().jsonObject, listOf("ownerBefore", "path"),
            JsonPrimitive(".tmp/different-generation/runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/su_install_registry.c"))
        expected("historical-owner-before-path", replaceReferenceFact(payload, beforePath, JsonArray(listOf(changedOwnerPath) + beforeRows.drop(1))), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_GENERATION")
        val wrongArchive = replaceReferenceFact(beforeRows[1].jsonObject, listOf("before", "path"),
            JsonPrimitive(".tmp/p2-host-final-20261118/su-metadata-selection-20261119/evidence/header-evolution/su10-before/su_install_registry.h"))
        expected("historical-before-other-selected-archive", replaceReferenceFact(payload, beforePath, JsonArray(listOf(beforeRows.first(), wrongArchive))), "ULTRA_KERNEL_SU_COMPONENT_PROFILE_GENERATION")
        if (phase == "abi4-r5") {
            val layouts = payload.getValue("referenceAbi").jsonObject.getValue("suProviderAbi").jsonObject.getValue("compiledLayouts").jsonObject
            for ((prefix, member) in listOf("installEntry" to "source_invalidated", "executionReceipt" to "task_profile")) {
                val fieldsPath = su + listOf("compiledLayouts", prefix, "members")
                val fields = layouts.getValue(prefix).jsonObject.getValue("members").jsonObject
                val field = fields.getValue(member).jsonObject
                expected("$prefix-member-missing", replaceReferenceFact(payload, fieldsPath + member, null), "ULTRA_KERNEL_SU_COMPONENT_MEMBERS:$prefix")
                expected("$prefix-member-extra", replaceReferenceFact(payload, fieldsPath, JsonObject(fields + ("extra_field" to field))), "ULTRA_KERNEL_SU_COMPONENT_MEMBERS:$prefix")
                expected("$prefix-member-offset", replaceReferenceFact(payload, fieldsPath + listOf(member, "offset"), JsonPrimitive(field.getValue("offset").jsonPrimitive.long + 1)), "ULTRA_KERNEL_SU_COMPONENT_LAYOUT_SIGNATURE:$prefix")
                expected("$prefix-member-bytes", replaceReferenceFact(payload, fieldsPath + listOf(member, "bytes"), JsonPrimitive(field.getValue("bytes").jsonPrimitive.long + 1)), "ULTRA_KERNEL_SU_COMPONENT_LAYOUT_FACT:$prefix.$member")
                expected("$prefix-member-signature", replaceReferenceFact(payload, fieldsPath + listOf(member, "signature"), JsonPrimitive(field.getValue("signature").jsonPrimitive.content + "x")), "ULTRA_KERNEL_SU_COMPONENT_LAYOUT_SIGNATURE:$prefix")
            }
            val callsPath = su + listOf("callbackRelocations", "su_profile_execution")
            val calls = payload.getValue("referenceAbi").jsonObject.getValue("suProviderAbi").jsonObject
                .getValue("callbackRelocations").jsonObject.getValue("su_profile_execution").jsonObject
            val caller = "msu_su_apply_held_stages"
            val targets = calls.getValue(caller).jsonObject
            val target = "msu_task_profile_apply_current"
            expected("runtime-caller-missing", replaceReferenceFact(payload, callsPath + caller, null), "ULTRA_KERNEL_SU_COMPONENT_RELOCATION_CALLERS")
            expected("runtime-caller-extra", replaceReferenceFact(payload, callsPath, JsonObject(calls + ("extra_caller" to targets))), "ULTRA_KERNEL_SU_COMPONENT_RELOCATION_CALLERS")
            expected("runtime-target-missing", replaceReferenceFact(payload, callsPath + listOf(caller, target), null), "ULTRA_KERNEL_SU_COMPONENT_RELOCATION_TARGETS")
            expected("runtime-target-extra", replaceReferenceFact(payload, callsPath + caller, JsonObject(targets + ("extra_target" to targets.getValue(target)))), "ULTRA_KERNEL_SU_COMPONENT_RELOCATION_TARGETS")
            expected("runtime-target-wrong", replaceReferenceFact(payload, callsPath + caller, JsonObject((targets - target) + ("wrong_target" to targets.getValue(target)))), "ULTRA_KERNEL_SU_COMPONENT_RELOCATION_TARGETS")
            val rows = targets.getValue(target).jsonArray
            val wrongResolved = replaceReferenceFact(rows.first().jsonObject, listOf("resolvedTarget"), JsonPrimitive("wrong_target"))
            expected("runtime-resolved-target-wrong", replaceReferenceFact(payload, callsPath + listOf(caller, target), JsonArray(listOf(wrongResolved) + rows.drop(1))), "ULTRA_KERNEL_SU_COMPONENT_RELOCATION:")
            val runtimePath = listOf("referenceAbi", "managementAbi", "upstreamSourceFreeze", "runtimeLayoutEvolution", "files")
            val runtimeRows = payload.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
                .getValue("upstreamSourceFreeze").jsonObject.getValue("runtimeLayoutEvolution").jsonObject.getValue("files").jsonArray
            val firstRuntime = runtimeRows.first().jsonObject
            val runtimeLogical = firstRuntime.getValue("logicalPath").jsonPrimitive.content
            check(runtimeLogical == "runtime/ultra/identity/kernel/install_records.c") { "R8_REAL_FIRST_RUNTIME_SOURCE_REQUIRED" }
            val shortHash = manifest.getValue("inputs").jsonObject.entries.single {
                it.key.replace('\\', '/') == runtimeLogical
            }.value
            for ((key, value) in listOf("sha256" to shortHash, "path" to JsonPrimitive("D:/missing-source/$runtimeLogical"))) {
                val changed = replaceReferenceFact(firstRuntime, listOf("before", key), value)
                expected("core-runtime-before-$key", replaceReferenceFact(payload, runtimePath, JsonArray(listOf(changed) + runtimeRows.drop(1))), "ULTRA_KERNEL_CORE_SELECTED_SOURCE")
            }
            val predecessorsPath = runtimePath.dropLast(1) + "predecessorInputs"
            val predecessors = payload.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
                .getValue("upstreamSourceFreeze").jsonObject.getValue("runtimeLayoutEvolution").jsonObject.getValue("predecessorInputs").jsonObject
            expected("runtime-predecessor-key-missing", replaceReferenceFact(payload, predecessorsPath + "frame", null), "ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_FIELDS")
            expected("runtime-predecessor-key-extra", replaceReferenceFact(payload, predecessorsPath, JsonObject(predecessors + ("extra" to predecessors.getValue("frame")))), "ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_FIELDS")
            expected("runtime-predecessor-list-as-object", replaceReferenceFact(payload, predecessorsPath + "native", predecessors.getValue("native").jsonArray.first()), "ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_TYPE:native")
            expected("runtime-predecessor-object-as-list", replaceReferenceFact(payload, predecessorsPath + "protocol", JsonArray(listOf(predecessors.getValue("protocol")))), "ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_TYPE:protocol")
            val coreDependencies = payload.getValue("referenceAbi").jsonObject.getValue("coreLifecycleAbi").jsonObject
                .getValue("inlineDependencies").jsonArray
            val emptyCoreIndex = coreDependencies.indexOfFirst { it.jsonObject.getValue("bytes").jsonPrimitive.long == 0L }
            check(emptyCoreIndex >= 0) { "R9_REAL_CORE_ZERO_BYTE_DEPENDENCY_REQUIRED" }
            for ((key, value) in listOf("sha256" to "0".repeat(64), "archivePath" to "build-dependencies/files/wrong.bin")) {
                val changed = JsonArray(coreDependencies.mapIndexed { index, item ->
                    if (index == emptyCoreIndex) replaceReferenceFact(item.jsonObject, listOf(key), JsonPrimitive(value)) else item
                })
                expected("core-empty-dependency-$key", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "inlineDependencies"), changed), "ULTRA_KERNEL_CORE_DEPENDENCY_IDENTITY")
            }
            val dependencies = payload.getValue("referenceAbi").jsonObject.getValue("suProviderAbi").jsonObject
                .getValue("inlineDependencies").jsonArray
            val emptyIndex = dependencies.indexOfFirst { it.jsonObject.getValue("bytes").jsonPrimitive.long == 0L }
            check(emptyIndex >= 0) { "R7_REAL_ZERO_BYTE_DEPENDENCY_REQUIRED" }
            for ((key, value) in listOf("sha256" to "0".repeat(64), "archivePath" to "build-dependencies/files/wrong.bin")) {
                val changed = JsonArray(dependencies.mapIndexed { index, item ->
                    if (index == emptyIndex) replaceReferenceFact(item.jsonObject, listOf(key), JsonPrimitive(value)) else item
                })
                expected("empty-dependency-$key", replaceReferenceFact(payload, su + "inlineDependencies", changed), "ULTRA_KERNEL_SU_COMPONENT_DEPENDENCY_IDENTITY")
            }
            val freezePath = listOf("referenceAbi", "managementAbi", "upstreamSourceFreeze")
            val freeze = payload.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
                .getValue("upstreamSourceFreeze").jsonObject
            fun bump(row: JsonObject, path: List<String>): JsonObject {
                val value = path.fold(row as JsonElement) { owner, key -> owner.jsonObject.getValue(key) }
                return replaceReferenceFact(row, path, JsonPrimitive(value.jsonPrimitive.long + 1))
            }
            fun changeRow(path: List<String>, logical: String, edit: (JsonObject) -> JsonObject): JsonObject {
                val rows = path.fold(payload as JsonElement) { owner, key -> owner.jsonObject.getValue(key) }.jsonArray
                check(rows.count { (it.jsonObject["logicalPath"] ?: it.jsonObject["path"])?.jsonPrimitive?.content == logical } == 1)
                return replaceReferenceFact(payload, path, JsonArray(rows.map {
                    val row = it.jsonObject
                    if ((row["logicalPath"] ?: row["path"])?.jsonPrimitive?.content == logical) edit(row) else row
                }))
            }
            fun runtimeCopy(changed: JsonObject): JsonObject {
                val runtime = changed.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
                    .getValue("upstreamSourceFreeze").jsonObject.getValue("coreRuntimeEvolution")
                val owner = replaceReferenceFact(changed, corePath + "runtimeSuccessor", runtime)
                return replaceReferenceFact(owner, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification",
                    "sourceSelection", "runtime"), runtime)
            }
            val protocol = "runtime/ultra/authorization/request_protocol.h"
            val host = "runtime/ultra/identity/kernel/manager_resident_host.c"
            val layersPath = freezePath + listOf("runtimeLayoutEvolution", "selectedLayers")
            val layers = freeze.getValue("runtimeLayoutEvolution").jsonObject.getValue("selectedLayers").jsonArray
            expected("runtime-layer-order", replaceReferenceFact(payload, layersPath,
                JsonArray(listOf(layers[1], layers[0]) + layers.drop(2))), "ULTRA_KERNEL_RUNTIME_LAYOUT_LAYER_ORDER")
            expected("runtime-layer-identity-bytes", replaceReferenceFact(payload, layersPath,
                JsonArray(listOf(bump(layers.first().jsonObject, listOf("selection", "bytes"))) + layers.drop(1))),
                "ULTRA_KERNEL_RUNTIME_LAYOUT_LAYER_IDENTITY")
            val coreRowsPath = freezePath + listOf("coreLifecycleEvolution", "selectedInputs")
            expected("core-historical-runtime-bytes", changeRow(coreRowsPath, protocol) {
                bump(it, listOf("previousRuntimeLayoutInput", "bytes"))
            }, "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_EDGE:hostDeploymentEvolution.protocolInput")
            expected("runtime-before-not-predecessor", changeRow(runtimePath, protocol) { bump(it, listOf("before", "bytes")) },
                "ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_EDGE")
            expected("host-protocol-predecessor", replaceReferenceFact(payload, freezePath + "hostDeploymentEvolution",
                bump(freeze.getValue("hostDeploymentEvolution").jsonObject, listOf("protocolInput", "before", "bytes"))),
                "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_EDGE:hostDeploymentEvolution.protocolInput")
            val coreRuntimePath = freezePath + listOf("coreRuntimeEvolution", "selectedInputs")
            expected("core-runtime-role-missing", runtimeCopy(changeRow(coreRuntimePath, protocol) {
                replaceReferenceFact(it, listOf("coreSelectedBefore"), null)
            }), "ULTRA_KERNEL_RUNTIME_LAYOUT_CORE_RUNTIME_ROLE_FIELDS")
            expected("core-runtime-root-current", runtimeCopy(changeRow(coreRuntimePath, protocol) {
                bump(it, listOf("rootCurrent", "bytes"))
            }), "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_EDGE:coreRuntimeEvolution")
            expected("core-runtime-frozen-after", runtimeCopy(changeRow(coreRuntimePath, protocol) {
                bump(it, listOf("coreFrozenAfter", "bytes"))
            }), "ULTRA_KERNEL_RUNTIME_LAYOUT_CORE_RUNTIME_ROLE")
            expected("core-runtime-merged-after", runtimeCopy(changeRow(coreRuntimePath, host) {
                bump(it, listOf("mergedAfter", "bytes"))
            }), "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_EDGE:hostRetirementEvolution")
            expected("current-task-parent-selected", changeRow(freezePath + listOf("sdkCurrentTaskEvolution", "files"), protocol) {
                bump(it, listOf("parentSelected", "bytes"))
            }, "ULTRA_KERNEL_RUNTIME_LAYOUT_CURRENT_TASK_PROVENANCE")
            val syncPath = freezePath + listOf("hostSyncEvolution", "files")
            expected("sync-archived-before", changeRow(syncPath, protocol) { bump(it, listOf("archivedBefore", "bytes")) },
                "ULTRA_KERNEL_RUNTIME_LAYOUT_SYNC_PROVENANCE")
            expected("sync-before-edge", changeRow(syncPath, protocol) {
                bump(bump(bump(it, listOf("before", "bytes")), listOf("archivedBefore", "bytes")), listOf("upstreamBefore", "bytes"))
            }, "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_EDGE:hostSyncEvolution")
            val retirementPath = freezePath + listOf("hostRetirementEvolution", "files")
            expected("retirement-archived-before", changeRow(retirementPath, host) { bump(it, listOf("archivedBefore", "bytes")) },
                "ULTRA_KERNEL_RUNTIME_LAYOUT_RETIREMENT_PROVENANCE")
            expected("retirement-before-edge", changeRow(retirementPath, host) {
                bump(bump(it, listOf("before", "bytes")), listOf("archivedBefore", "bytes"))
            }, "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_EDGE:hostRetirementEvolution")
            expected("retirement-final-runtime-edge", changeRow(retirementPath, host) { bump(it, listOf("after", "bytes")) },
                "ULTRA_KERNEL_RUNTIME_LAYOUT_CORE_SUCCESSOR")
            expected("task-profile-runtime-edge", changeRow(coreRowsPath, "runtime/ultra/identity/kernel/manager_task_state.c") {
                bump(it, listOf("previousTaskProfileInput", "bytes"))
            }, "ULTRA_KERNEL_RUNTIME_LAYOUT_CORE_SUCCESSOR")
            expected("host-runtime-source-count", replaceReferenceFact(payload, freezePath + listOf("hostDeploymentEvolution", "sourceFileCount"),
                JsonPrimitive(24)), "ULTRA_KERNEL_DEPLOYMENT_SOURCE_EVOLUTION")
            val hostRowsPath = freezePath + listOf("hostDeploymentEvolution", "selectedInputs")
            expected("host-added-source-preimage", changeRow(hostRowsPath, "runtime/ultra/identity/kernel/manager_installation_state.c") {
                replaceReferenceFact(it, listOf("before"), it.getValue("after"))
            }, "ULTRA_KERNEL_DEPLOYMENT_NEW_SOURCE_PREIMAGE")
            expected("host-existing-source-absent", replaceReferenceFact(payload, freezePath + listOf("hostDeploymentEvolution",
                "previousInputs", "native", "install_records.c"), JsonNull), "ULTRA_KERNEL_DEPLOYMENT_RUNTIME_NEW_PREDECESSOR")
            expected("host-runtime-imports", replaceReferenceFact(payload, freezePath + listOf("hostDeploymentEvolution", "runtimeNativeImports"),
                JsonArray(emptyList())), "ULTRA_KERNEL_DEPLOYMENT_RUNTIME_OBJECTS")
            val managementDependencies = payload.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
                .getValue("dependencies").jsonArray
            val emptyManagementIndex = managementDependencies.indexOfFirst { it.jsonObject.getValue("bytes").jsonPrimitive.long == 0L }
            check(emptyManagementIndex >= 0) { "R10_REAL_MANAGEMENT_ZERO_BYTE_DEPENDENCY_REQUIRED" }
            for ((key, value) in listOf("sha256" to "0".repeat(64), "archivePath" to "build-dependencies/files/wrong.bin")) {
                val changed = JsonArray(managementDependencies.mapIndexed { index, item ->
                    if (index == emptyManagementIndex) replaceReferenceFact(item.jsonObject, listOf(key), JsonPrimitive(value)) else item
                })
                expected("management-empty-dependency-$key", replaceReferenceFact(payload,
                    listOf("referenceAbi", "managementAbi", "dependencies"), changed), "ULTRA_KERNEL_MANAGEMENT_DEPENDENCY_IDENTITY")
            }
            val managementHeader = "runtime/ultra/identity/kernel/manager_management.h"
            val coreOwner = payload.getValue("referenceAbi").jsonObject.getValue("coreLifecycleAbi").jsonObject
                .getValue("sourceSelection").jsonObject.getValue("identity").jsonObject.getValue("path").jsonPrimitive.content
                .replace('\\', '/').substringBeforeLast('/')
            expected("core-historical-copy-other-selected-archive", changeRow(coreRowsPath, managementHeader) {
                replaceReferenceFact(it, listOf("previousRuntimeLayoutInput", "path"),
                    JsonPrimitive("$coreOwner/archive/source/$managementHeader"))
            }, "ULTRA_KERNEL_CORE_HISTORICAL_SOURCE_ROLE")
            expected("core-historical-copy-bytes", changeRow(coreRowsPath, managementHeader) {
                bump(it, listOf("previousRuntimeLayoutInput", "bytes"))
            }, "ULTRA_KERNEL_RUNTIME_LAYOUT_STAGE_EDGE:coreRuntimeEvolution")
            val currentTaskPath = freezePath + listOf("sdkCurrentTaskEvolution", "files")
            expected("current-task-owner-after-bytes", changeRow(currentTaskPath, protocol) { bump(it, listOf("ownerAfter", "bytes")) },
                "ULTRA_KERNEL_RUNTIME_LAYOUT_CURRENT_TASK_OWNER_ARCHIVE")
            expected("current-task-owner-after-other-selected-archive", changeRow(currentTaskPath, protocol) {
                replaceReferenceFact(it, listOf("ownerAfter", "path"), it.getValue("after").jsonObject.getValue("path"))
            }, "ULTRA_KERNEL_RUNTIME_LAYOUT_CURRENT_TASK_OWNER_ARCHIVE")
            expected("current-task-after-other-selected-archive", changeRow(currentTaskPath, protocol) {
                replaceReferenceFact(it, listOf("after", "path"), it.getValue("parentSelected").jsonObject.getValue("path"))
            }, "ULTRA_KERNEL_RUNTIME_LAYOUT_CURRENT_TASK_ARCHIVE_ROLE")
            expected("current-task-owner-receipt-bytes", replaceReferenceFact(payload, freezePath + "sdkCurrentTaskEvolution",
                bump(freeze.getValue("sdkCurrentTaskEvolution").jsonObject,
                    listOf("receipts", "SOURCE-SELECTION.json", "bytes"))), "ULTRA_KERNEL_RUNTIME_LAYOUT_CURRENT_TASK_OWNER_RECEIPT")
            val retainedPath = freezePath + listOf("runtimeLayoutEvolution", "predecessor")
            expected("retained-predecessor-zero-bytes", replaceReferenceFact(payload, retainedPath + "bytes", JsonPrimitive(0)),
                "ULTRA_KERNEL_CORE_SOURCE_IDENTITY")
            expected("retained-predecessor-malformed-sha256", replaceReferenceFact(payload, retainedPath + "sha256", JsonPrimitive("invalid-sha256")),
                "ULTRA_KERNEL_CORE_SOURCE_IDENTITY")
            expected("retained-predecessor-wrong-role", replaceReferenceFact(payload, retainedPath + "path",
                freeze.getValue("hostRetirementEvolution").jsonObject.getValue("identity").jsonObject.getValue("path")),
                "ULTRA_KERNEL_RUNTIME_LAYOUT_PREDECESSOR_ROLE")
            val adoptionPath = freezePath + listOf("coreLifecycleEvolution", "adoptedBetweenPredecessorAndChannel")
            val adoptions = freeze.getValue("coreLifecycleEvolution").jsonObject.getValue("adoptedBetweenPredecessorAndChannel").jsonArray
            check(adoptions.size == 1) { "R12_REAL_SINGLE_READBACK_ADOPTION_REQUIRED" }
            expected("readback-adoption-object", replaceReferenceFact(payload, adoptionPath, adoptions.single()),
                "ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_ADOPTION_ROWS")
            expected("readback-adoption-empty", replaceReferenceFact(payload, adoptionPath, JsonArray(emptyList())),
                "ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_ADOPTION_ROWS")
            expected("readback-adoption-duplicate", replaceReferenceFact(payload, adoptionPath, JsonArray(adoptions + adoptions)),
                "ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_ADOPTION_ROWS")
            expected("readback-adoption-primitive", replaceReferenceFact(payload, adoptionPath, JsonArray(listOf(JsonPrimitive("wrong-row")))),
                "ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_ADOPTION_ROWS")
            val readback = "runtime/ultra/bridge/installed_helper_readback.cpp"
            for (role in listOf("before", "after")) expected("readback-adoption-$role-bytes", changeRow(adoptionPath, readback) {
                bump(it, listOf(role, "bytes"))
            }, "ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_PREDECESSOR")
            val readbackSource = freeze.getValue("coreLifecycleEvolution").jsonObject.getValue("selectedInputs").jsonArray.single {
                it.jsonObject.getValue("path").jsonPrimitive.content == readback
            }.jsonObject.getValue("after").jsonObject
            expected("readback-adoption-other-selected-archive", changeRow(adoptionPath, readback) {
                replaceReferenceFact(it, listOf("after", "path"), readbackSource.getValue("path"))
            }, "ULTRA_KERNEL_CORE_DEPLOYMENT_READBACK_PREDECESSOR")
            for (layout in listOf("taskState", "credentialDomain")) {
                val path = listOf("referenceAbi", "coreLifecycleAbi", layout, "dependencies")
                val rows = payload.getValue("referenceAbi").jsonObject.getValue("coreLifecycleAbi").jsonObject
                    .getValue(layout).jsonObject.getValue("dependencies").jsonArray
                val empty = rows.indexOfFirst { it.jsonObject.getValue("bytes").jsonPrimitive.long == 0L }
                check(empty >= 0) { "R12_REAL_CORE_LAYOUT_EMPTY_DEPENDENCY_REQUIRED:$layout" }
                for ((key, value) in listOf("sha256" to "0".repeat(64), "archivePath" to "build-dependencies/files/wrong.bin")) {
                    val changed = JsonArray(rows.mapIndexed { index, row ->
                        if (index == empty) replaceReferenceFact(row.jsonObject, listOf(key), JsonPrimitive(value)) else row
                    })
                    expected("$layout-empty-dependency-$key", replaceReferenceFact(payload, path, changed), "ULTRA_KERNEL_CORE_DEPENDENCY_IDENTITY")
                }
            }
            for (tail in listOf(listOf("uidSnapshotAbi", "inlineDependencies"),
                listOf("procattrAbi", "dependencies"), listOf("procattrAbi", "factsDependencies"))) {
                val path = listOf("referenceAbi", "managementAbi") + tail
                val rows = payload.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
                    .getValue(tail[0]).jsonObject.getValue(tail[1]).jsonArray
                val empty = rows.indexOfFirst { it.jsonObject.getValue("bytes").jsonPrimitive.long == 0L }
                check(empty >= 0) { "R13_REAL_MANAGEMENT_NESTED_EMPTY_DEPENDENCY_REQUIRED:$tail" }
                for ((key, value) in listOf("sha256" to "0".repeat(64), "archivePath" to "build-dependencies/files/wrong.bin")) {
                    val changed = JsonArray(rows.mapIndexed { index, row ->
                        if (index == empty) replaceReferenceFact(row.jsonObject, listOf(key), JsonPrimitive(value)) else row
                    })
                    expected("${tail.joinToString("-")}-empty-dependency-$key", replaceReferenceFact(payload, path, changed),
                        "ULTRA_KERNEL_MANAGEMENT_DEPENDENCY_IDENTITY")
                }
            }
            val coreLayoutPath = listOf("referenceAbi", "coreLifecycleAbi")
            val coreLayoutReport = payload.getValue("referenceAbi").jsonObject.getValue("coreLifecycleAbi").jsonObject
            val coreLayoutsPath = coreLayoutPath + "compiledLayouts"
            val coreLayouts = coreLayoutReport.getValue("compiledLayouts").jsonObject
            check(coreLayouts.keys == setOf("profile", "serviceDescriptor", "service", "readerFence", "controller", "candidate", "session")) {
                "R13_REAL_CORE_RUNTIME_LAYOUT_FAMILY_REQUIRED"
            }
            expected("core-runtime-session-layout-missing", replaceReferenceFact(payload, coreLayoutsPath + "session", null),
                "ULTRA_KERNEL_CORE_LAYOUT_FIELDS")
            expected("core-runtime-layout-extra", replaceReferenceFact(payload, coreLayoutsPath + "extra", coreLayouts.getValue("session")),
                "ULTRA_KERNEL_CORE_LAYOUT_FIELDS")
            for ((prefix, member) in listOf("service" to "helper_sessions", "session" to "helper")) {
                val fieldsPath = coreLayoutsPath + listOf(prefix, "members")
                val fields = coreLayouts.getValue(prefix).jsonObject.getValue("members").jsonObject
                expected("core-runtime-$prefix-member-missing", replaceReferenceFact(payload, fieldsPath + member, null),
                    "ULTRA_KERNEL_CORE_LAYOUT:$prefix")
                expected("core-runtime-$prefix-member-extra", replaceReferenceFact(payload, fieldsPath + "extra", fields.getValue(member)),
                    "ULTRA_KERNEL_CORE_LAYOUT:$prefix")
            }
            for (key in listOf("sessionBytes", "serviceHelperSessionsOffset")) {
                expected("core-runtime-fact-$key-missing", replaceReferenceFact(payload, coreLayoutPath + listOf("compiledFacts", key), null),
                    "ULTRA_KERNEL_CORE_COMPILED_FACTS")
            }
            expected("core-runtime-fact-extra", replaceReferenceFact(payload, coreLayoutPath + listOf("compiledFacts", "extra"), JsonPrimitive(0)),
                "ULTRA_KERNEL_CORE_COMPILED_FACTS")
            expected("core-runtime-uid-fact-extra", replaceReferenceFact(payload, coreLayoutPath + listOf("uidCompiledFacts", "extra"), JsonPrimitive(0)),
                "ULTRA_KERNEL_CORE_COMPILED_FACTS")
            expected("core-runtime-constant", replaceReferenceFact(payload, coreLayoutPath + listOf("compiledFacts", "capAll"), JsonPrimitive(0)),
                "ULTRA_KERNEL_CORE_COMPILED_FACTS")
            for (alignment in listOf(0, 3)) {
                expected("core-runtime-session-alignment-$alignment", replaceReferenceFact(payload,
                    coreLayoutPath + listOf("compiledFacts", "sessionAlign"), JsonPrimitive(alignment)), "ULTRA_KERNEL_CORE_LAYOUT:session")
            }
            expected("core-runtime-session-layout-size", replaceReferenceFact(payload, coreLayoutsPath + listOf("session", "bytes"), JsonPrimitive(185)),
                "ULTRA_KERNEL_CORE_LAYOUT:session")
            val coreCompiledFacts = coreLayoutReport.getValue("compiledFacts").jsonObject
            for (key in listOf("sessionIdOffset", "sessionIdBytes")) {
                expected("core-runtime-$key-mismatch", replaceReferenceFact(payload, coreLayoutPath + listOf("compiledFacts", key),
                    JsonPrimitive(coreCompiledFacts.getValue(key).jsonPrimitive.int + 1)), "ULTRA_KERNEL_CORE_COMPILED_MEMBER:session.id")
            }
            expected("core-runtime-session-canonical-signature", replaceReferenceFact(payload,
                coreLayoutsPath + listOf("session", "signature"), JsonPrimitive("smsu_management_session:184{}")),
                "ULTRA_KERNEL_CORE_LAYOUT_SIGNATURE:session")
            val idPath = coreLayoutsPath + listOf("session", "members", "id")
            expected("core-runtime-session-overlap", replaceReferenceFact(payload,
                coreLayoutsPath + listOf("session", "members", "cleanup", "offset"), JsonPrimitive(0)),
                "ULTRA_KERNEL_CORE_MEMBER_RANGE:session")
            expected("core-runtime-session-zero-member", replaceReferenceFact(payload,
                coreLayoutsPath + listOf("session", "members", "cleanup", "bytes"), JsonPrimitive(0)),
                "ULTRA_KERNEL_CORE_MEMBER_RANGE:session")
            expected("core-runtime-session-member-outside", replaceReferenceFact(payload,
                coreLayoutsPath + listOf("session", "members", "cleanup", "bytes"), JsonPrimitive(184)),
                "ULTRA_KERNEL_CORE_MEMBER_RANGE:session")
            expected("core-runtime-session-empty-member-signature", replaceReferenceFact(payload, idPath + "signature", JsonPrimitive("")),
                "ULTRA_KERNEL_CORE_MEMBER_RANGE:session")
            val session = coreLayouts.getValue("session").jsonObject
            val sessionSignature = session.getValue("signature").jsonPrimitive.content
            val gatewayTerm = "1152=*smsu_legacy_gateway;"
            val activeTerm = "1448=i1:67108872;"
            check(gatewayTerm in sessionSignature && activeTerm in sessionSignature) { "R13_REAL_SESSION_RUNTIME_MEMBERS_REQUIRED" }
            expected("core-runtime-session-pointer-width", replaceReferenceFact(payload,
                coreLayoutsPath + listOf("session", "members", "service", "bytes"), JsonPrimitive(4)),
                "ULTRA_KERNEL_CORE_SESSION_RUNTIME_LAYOUT")
            expected("core-runtime-session-u64-alignment", replaceReferenceFact(payload, idPath + "offset", JsonPrimitive(160)),
                "ULTRA_KERNEL_CORE_SESSION_RUNTIME_LAYOUT")
            expected("core-runtime-session-full-signature-member-missing", replaceReferenceFact(payload,
                coreLayoutsPath + listOf("session", "signature"), JsonPrimitive(sessionSignature.replace(gatewayTerm, ""))),
                "ULTRA_KERNEL_CORE_LAYOUT_SIGNATURE:session")
            expected("core-runtime-session-full-signature-member-offset", replaceReferenceFact(payload,
                coreLayoutsPath + listOf("session", "signature"), JsonPrimitive(sessionSignature.replace(activeTerm, "1456=i1:67108872;"))),
                "ULTRA_KERNEL_CORE_LAYOUT_SIGNATURE:session")
            expected("core-runtime-session-unselected-member-extra", replaceReferenceFact(payload,
                coreLayoutsPath + listOf("session", "members", "legacy_gateway"),
                session.getValue("members").jsonObject.getValue("service")), "ULTRA_KERNEL_CORE_LAYOUT:session")
            val functionPath = coreLayoutPath + "localFunctionAbi"
            val functions = coreLayoutReport.getValue("localFunctionAbi").jsonObject
            val function = "msu_management_discard_unpublished"
            expected("core-runtime-local-function-missing", replaceReferenceFact(payload, functionPath + function, null),
                "ULTRA_KERNEL_CORE_FUNCTION_ABI")
            expected("core-runtime-local-function-extra", replaceReferenceFact(payload, functionPath + "extra", functions.getValue(function)),
                "ULTRA_KERNEL_CORE_FUNCTION_ABI")
            val callbackPath = coreLayoutPath + listOf("callbackParameters", "uid.commit.validate")
            expected("core-runtime-callback-missing", replaceReferenceFact(payload, callbackPath, null), "ULTRA_KERNEL_SU_COMPONENT_CORE_CALLBACK:su.uid.commit.validate")
            expected("core-runtime-callback-source", replaceReferenceFact(payload, callbackPath + "source", JsonPrimitive(function)),
                "ULTRA_KERNEL_CORE_CALLBACK:uid.commit.validate")
            expected("core-runtime-callback-signature", replaceReferenceFact(payload, callbackPath + "signature", JsonPrimitive("*fvoid()")),
                "ULTRA_KERNEL_SU_COMPONENT_CORE_CALLBACK:su.uid.commit.validate")
            val fencePath = coreLayoutPath + "readerFenceAbi"
            expected("core-runtime-reader-fence-missing", replaceReferenceFact(payload, fencePath + "retire", null),
                "ULTRA_KERNEL_SU_COMPONENT_CORE_FENCE:retire")
            expected("core-runtime-reader-fence-signature", replaceReferenceFact(payload, fencePath + "retire", JsonPrimitive("*fvoid()")),
                "ULTRA_KERNEL_SU_COMPONENT_CORE_FENCE:retire")
            for (flag in listOf("productionWritten", "targetQualified")) {
                val changed = replaceReferenceFact(payload, corePath + listOf("runtimeSuccessor", flag), JsonPrimitive(true))
                expected("core-runtime-source-$flag", replaceReferenceFact(changed,
                    freezePath + listOf("coreRuntimeEvolution", flag), JsonPrimitive(true)), "ULTRA_KERNEL_CORE_RUNTIME_SOURCE")
            }
            check(payload.getValue("referenceAbi").jsonObject.getValue("coreLifecycleAbi").jsonObject
                .getValue("config").jsonObject.getValue("CONFIG_GENERIC_ENTRY").jsonPrimitive.content == "n" &&
                payload.getValue("referenceAbi").jsonObject.getValue("managementAbi").jsonObject
                    .getValue("config").jsonObject["CONFIG_GENERIC_ENTRY"] == null) { "R14_REAL_UNDEFINED_DISABLED_CONFIG_REQUIRED" }
            val explicitDisabledConfig = replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_GENERIC_ENTRY"), JsonPrimitive("n"))
            val explicitDisabledLoad = UltraKernelManagerClaimPayload.load(
                RepackedReferenceClaims(base, manifest, family, explicitDisabledConfig), release)
            check(explicitDisabledLoad.referenceAbi?.management?.sourceQualification?.claimSha256 == UltraKernelSpec.sha256(explicitDisabledLoad.elf))
            println("PASS ABI4_CONFIG_UNDEFINED_DISABLED_EXPLICIT_N")
            expected("core-config-key-missing", replaceReferenceFact(payload,
                listOf("referenceAbi", "coreLifecycleAbi", "config", "CONFIG_GENERIC_ENTRY"), null),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-key-extra", replaceReferenceFact(payload,
                listOf("referenceAbi", "coreLifecycleAbi", "config", "CONFIG_EXTRA"), JsonPrimitive("n")),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-invalid-value", replaceReferenceFact(payload,
                listOf("referenceAbi", "coreLifecycleAbi", "config", "CONFIG_GENERIC_ENTRY"), JsonPrimitive("m")),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-explicit-null", replaceReferenceFact(payload,
                listOf("referenceAbi", "coreLifecycleAbi", "config", "CONFIG_GENERIC_ENTRY"), JsonNull),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-undefined-enabled", replaceReferenceFact(payload,
                listOf("referenceAbi", "coreLifecycleAbi", "config", "CONFIG_GENERIC_ENTRY"), JsonPrimitive("y")),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-source-conflict", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_GENERIC_ENTRY"), JsonPrimitive("y")),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-source-explicit-null", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_GENERIC_ENTRY"), JsonNull),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-source-invalid-value", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_GENERIC_ENTRY"), JsonPrimitive("m")),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-enabled-source-missing", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_SECCOMP"), null),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-enabled-source-disabled", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_SECCOMP"), JsonPrimitive("n")),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-enabled-source-null", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_SECCOMP"), JsonNull),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("su-config-key-missing", replaceReferenceFact(payload,
                listOf("referenceAbi", "suProviderAbi", "config", "CONFIG_NAMESPACES"), null),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG")
            expected("su-config-key-extra", replaceReferenceFact(payload,
                listOf("referenceAbi", "suProviderAbi", "config", "CONFIG_EXTRA"), JsonPrimitive("n")),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG")
            expected("su-config-explicit-null", replaceReferenceFact(payload,
                listOf("referenceAbi", "suProviderAbi", "config", "CONFIG_NAMESPACES"), JsonNull),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-invalid-value", replaceReferenceFact(payload,
                listOf("referenceAbi", "suProviderAbi", "config", "CONFIG_NAMESPACES"), JsonPrimitive("m")),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-source-conflict", replaceReferenceFact(payload,
                listOf("referenceAbi", "suProviderAbi", "config", "CONFIG_NAMESPACES"), JsonPrimitive("n")),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-enabled-source-missing", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_NAMESPACES"), null),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-enabled-source-null", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_NAMESPACES"), JsonNull),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-enabled-source-invalid-value", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_NAMESPACES"), JsonPrimitive("m")),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-enabled-source-disabled", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_NAMESPACES"), JsonPrimitive("n")),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-disabled-source-null", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_CFI"), JsonNull),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-disabled-source-invalid-value", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_CFI"), JsonPrimitive("m")),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-disabled-projection-enabled", replaceReferenceFact(payload,
                listOf("referenceAbi", "suProviderAbi", "config", "CONFIG_CFI"), JsonPrimitive("y")),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG_SOURCE")
            expected("su-config-disabled-projection-missing", replaceReferenceFact(payload,
                listOf("referenceAbi", "suProviderAbi", "config", "CONFIG_CFI"), null),
                "ULTRA_KERNEL_SU_COMPONENT_CONFIG")
            expected("core-config-cfi-enabled-projection-disabled", replaceReferenceFact(payload,
                listOf("referenceAbi", "coreLifecycleAbi", "config", "CONFIG_CFI_CLANG"), JsonPrimitive("n")),
                "ULTRA_KERNEL_CORE_CONFIG")
            expected("core-config-source-boolean", replaceReferenceFact(payload,
                listOf("referenceAbi", "managementAbi", "config", "CONFIG_GENERIC_ENTRY"), JsonPrimitive(false)),
                "ULTRA_KERNEL_CORE_CONFIG")
            val taskImplementationPath = listOf("referenceAbi", "taskProfileAbi", "implementation")
            val taskImplementation = payload.getValue("referenceAbi").jsonObject.getValue("taskProfileAbi").jsonObject
                .getValue("implementation").jsonObject
            for ((name, value) in taskImplementation.getValue("objects").jsonObject) {
                val objectPath = taskImplementationPath + listOf("objects", name)
                val objectReport = value.jsonObject
                val rows = objectReport.getValue("dependencies").jsonArray
                val empty = rows.indexOfFirst { it.jsonObject.getValue("bytes").jsonPrimitive.long == 0L }
                check(empty >= 0) { "R15_REAL_TASK_IMPLEMENTATION_EMPTY_DEPENDENCY_REQUIRED:$name" }
                for ((key, changedValue) in listOf(
                    "sha256" to JsonPrimitive("0".repeat(64)),
                    "archivePath" to JsonPrimitive("build-dependencies/files/wrong.bin"),
                    "path" to JsonPrimitive(""), "archivePath" to null, "bytes" to JsonPrimitive(-1))) {
                    val changedRows = JsonArray(rows.mapIndexed { index, row ->
                        if (index == empty) replaceReferenceFact(row.jsonObject, listOf(key), changedValue) else row
                    })
                    val label = if (changedValue == null) "missing" else "invalid"
                    expected("task-$name-empty-dependency-$key-$label", replaceReferenceFact(payload,
                        objectPath + "dependencies", changedRows), if (key == "bytes")
                        "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_IDENTITY" else
                        "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_DEPENDENCY_IDENTITY")
                }
                for (key in listOf("sourceIdentity", "objectIdentity", "commandIdentity", "dependencyReceiptIdentity", "configIdentity")) {
                    expected("task-$name-$key-zero-bytes", replaceReferenceFact(payload,
                        objectPath + listOf(key, "bytes"), JsonPrimitive(0)),
                        "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_IDENTITY")
                }
            }
            for ((name, value) in taskImplementation.getValue("functions").jsonObject) {
                expected("task-$name-dependency-object-mismatch", replaceReferenceFact(payload,
                    taskImplementationPath + listOf("functions", name, "dependencies"),
                    JsonArray(value.jsonObject.getValue("dependencies").jsonArray.dropLast(1))),
                    "ULTRA_KERNEL_TASK_PROFILE_IMPLEMENTATION_FUNCTION_TYPE:$name")
            }
            val deploymentRowsPath = freezePath + listOf("hostDeploymentEvolution", "selectedInputs")
            for (name in listOf("install_records.c", "install_records.h", "protected_install_kernel.c", "protected_install_kernel.h")) {
                expected("deployment-before-other-core-owner-$name", changeRow(deploymentRowsPath,
                    "runtime/ultra/identity/kernel/$name") { row ->
                    val path = row.getValue("before").jsonObject.getValue("path").jsonPrimitive.content.replace('\\', '/')
                    replaceReferenceFact(row, listOf("before", "path"), JsonPrimitive(path.replace("/deployment54/", "/revision3/")))
                }, "ULTRA_KERNEL_DEPLOYMENT_SOURCE_BEFORE_PATH")
            }
            fun emptyDependencyCases(name: String, reportPath: List<String>, report: JsonObject,
                dependencyPrefix: String, ordinaryPrefix: String, extended: Boolean) {
                val rows = report.getValue("dependencies").jsonArray
                val empty = rows.indexOfFirst { it.jsonObject.getValue("bytes").jsonPrimitive.long == 0L }
                check(empty >= 0) { "R16_REAL_EMPTY_DEPENDENCY_REQUIRED:$name" }
                val cases = listOf("sha256" to JsonPrimitive("0".repeat(64)),
                    "archivePath" to JsonPrimitive("build-dependencies/files/wrong.bin")) +
                    (if (extended) listOf("path" to JsonPrimitive(""), "archivePath" to null, "bytes" to JsonPrimitive(-1)) else emptyList())
                for ((key, changedValue) in cases) {
                    val changed = JsonArray(rows.mapIndexed { index, row ->
                        if (index == empty) replaceReferenceFact(row.jsonObject, listOf(key), changedValue) else row
                    })
                    val label = if (changedValue == null) "missing" else "invalid"
                    expected("$name-empty-dependency-$key-$label", replaceReferenceFact(payload,
                        reportPath + "dependencies", changed), if (key == "bytes") ordinaryPrefix else dependencyPrefix)
                }
            }
            val taskParentPath = listOf("referenceAbi", "taskProfileAbi")
            emptyDependencyCases("task-parent", taskParentPath,
                payload.getValue("referenceAbi").jsonObject.getValue("taskProfileAbi").jsonObject,
                "ULTRA_KERNEL_TASK_PROFILE_DEPENDENCY_IDENTITY", "ULTRA_KERNEL_TASK_PROFILE_IDENTITY", true)
            expected("task-parent-object-zero-bytes", replaceReferenceFact(payload,
                taskParentPath + listOf("objectIdentity", "bytes"), JsonPrimitive(0)), "ULTRA_KERNEL_TASK_PROFILE_IDENTITY")
            val qualificationObjectsPath = listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "objects")
            val qualificationObjects = payload.getValue("referenceAbi").jsonObject.getValue("coreLifecycleAbi").jsonObject
                .getValue("sourceQualification").jsonObject.getValue("objects").jsonObject
            for ((name, value) in qualificationObjects) {
                emptyDependencyCases("source-qualification-$name", qualificationObjectsPath + name, value.jsonObject,
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_DEPENDENCY_IDENTITY", "ULTRA_KERNEL_SOURCE_QUALIFICATION_IDENTITY",
                    name == "manager_resident_host")
            }
            for (key in listOf("source", "object")) expected("source-qualification-$key-zero-bytes", replaceReferenceFact(payload,
                qualificationObjectsPath + listOf("manager_resident_host", key, "bytes"), JsonPrimitive(0)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_IDENTITY")
            for (name in listOf("runtimeLayout", "runtimeProfiles", "sessionAbi")) {
                val report = payload.getValue(name).jsonObject
                emptyDependencyCases("runtime-$name", listOf(name), report,
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_DEPENDENCY_IDENTITY", "ULTRA_KERNEL_RUNTIME_LAYOUT_IDENTITY", true)
                val sourceName = report.getValue("sourceIdentities").jsonObject.keys.first()
                expected("runtime-$name-source-zero-bytes", replaceReferenceFact(payload,
                    listOf(name, "sourceIdentities", sourceName, "bytes"), JsonPrimitive(0)),
                    "ULTRA_KERNEL_RUNTIME_LAYOUT_IDENTITY")
            }
            val descriptorSourcePath = listOf("referenceAbi", "managementAbi", "descriptorAbi", "sourceIdentity")
            val historicalHost = freeze.getValue("hostDeploymentEvolution").jsonObject.getValue("selectedInputs").jsonArray.single {
                it.jsonObject.getValue("logicalPath").jsonPrimitive.content == "runtime/ultra/identity/kernel/manager_resident_host.c"
            }.jsonObject.getValue("after")
            expected("descriptor-historical-host-source", replaceReferenceFact(payload, descriptorSourcePath, historicalHost),
                "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_SOURCE")
            expected("descriptor-source-zero-bytes", replaceReferenceFact(payload, descriptorSourcePath + "bytes", JsonPrimitive(0)),
                "ULTRA_KERNEL_MANAGEMENT_DESCRIPTOR_SOURCE")
            for ((name, value) in qualificationObjects) {
                val dependencies = value.jsonObject.getValue("dependencies").jsonArray
                expected("source-qualification-$name-kernel-config-missing", replaceReferenceFact(payload,
                    qualificationObjectsPath + listOf(name, "dependencies"), JsonArray(dependencies.filterNot {
                        it.jsonObject.getValue("path").jsonPrimitive.content.replace('\\', '/').substringAfterLast('/') == ".config"
                    })), "ULTRA_KERNEL_SOURCE_QUALIFICATION_DEPENDENCIES:$name")
            }
            val hostQualificationPath = qualificationObjectsPath + "manager_resident_host"
            val hostDependencies = qualificationObjects.getValue("manager_resident_host").jsonObject.getValue("dependencies").jsonArray
            val kernelConfigIndex = hostDependencies.indexOfFirst {
                it.jsonObject.getValue("path").jsonPrimitive.content.replace('\\', '/').substringAfterLast('/') == ".config"
            }
            check(kernelConfigIndex >= 0) { "R16_REAL_SOURCE_KERNEL_CONFIG_REQUIRED" }
            val realKernelConfig = hostDependencies[kernelConfigIndex].jsonObject
            expected("source-qualification-kernel-config-duplicate", replaceReferenceFact(payload,
                hostQualificationPath + "dependencies", JsonArray(hostDependencies + realKernelConfig)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_DEPENDENCIES:manager_resident_host")
            val emptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            val wrongGeneration = replaceReferenceFact(replaceReferenceFact(realKernelConfig,
                listOf("sha256"), JsonPrimitive("0".repeat(64))), listOf("archivePath"),
                JsonPrimitive("build-dependencies/files/" + "0".repeat(64) + ".bin"))
            val emptyKernelConfig = replaceReferenceFact(replaceReferenceFact(replaceReferenceFact(realKernelConfig,
                listOf("bytes"), JsonPrimitive(0)), listOf("sha256"), JsonPrimitive(emptyHash)),
                listOf("archivePath"), JsonPrimitive("build-dependencies/files/$emptyHash.bin"))
            for ((label, changed) in listOf(
                "archive-role" to replaceReferenceFact(realKernelConfig, listOf("archivePath"), JsonPrimitive("build-dependencies/files/wrong.bin")),
                "path-role" to replaceReferenceFact(realKernelConfig, listOf("path"), JsonPrimitive("D:/other/.config")),
                "generation" to wrongGeneration, "zero-bytes" to emptyKernelConfig)) {
                expected("source-qualification-kernel-config-$label", replaceReferenceFact(payload,
                    hostQualificationPath + "dependencies", JsonArray(hostDependencies.mapIndexed { index, row ->
                        if (index == kernelConfigIndex) changed else row
                    })), if (label == "zero-bytes") "ULTRA_KERNEL_SOURCE_QUALIFICATION_IDENTITY" else
                    "ULTRA_KERNEL_SOURCE_QUALIFICATION_DEPENDENCIES:manager_resident_host")
            }
            expected("source-qualification-resolver-config-zero-bytes", replaceReferenceFact(payload,
                listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "configIdentity", "bytes"), JsonPrimitive(0)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_IDENTITY")
            val runtimeTarget = "runtime/ultra/identity/kernel/manager_su_runtime_target.c"
            val runtimeSource = freeze.getValue("runtimeLayoutEvolution").jsonObject.getValue("files").jsonArray.single {
                it.jsonObject.getValue("logicalPath").jsonPrimitive.content == runtimeTarget
            }.jsonObject.getValue("after")
            expected("runtime-profile-historical-target-source", replaceReferenceFact(payload,
                listOf("runtimeProfiles", "sourceIdentities", runtimeTarget), runtimeSource), "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCE_PATH")
            val taskSuccessorPath = taskImplementationPath + listOf("sourceSuccessor", "files")
            val taskSuccessorRows = taskImplementation.getValue("sourceSuccessor").jsonObject.getValue("files").jsonArray
            val taskTarget = taskSuccessorRows.single { it.jsonObject.getValue("logicalPath").jsonPrimitive.content == runtimeTarget }.jsonObject
            for ((key, changed) in listOf("bytes" to JsonPrimitive(taskTarget.getValue("before").jsonObject.getValue("bytes").jsonPrimitive.long + 1),
                "sha256" to JsonPrimitive("0".repeat(64)), "path" to JsonPrimitive("D:/other/manager_su_runtime_target.c"))) {
                expected("runtime-profile-successor-before-$key", changeRow(taskSuccessorPath, runtimeTarget) { row ->
                    replaceReferenceFact(row, listOf("before", key), changed)
                }, "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCE_SUCCESSOR:$runtimeTarget")
            }
            expected("runtime-profile-successor-after-path", replaceReferenceFact(payload,
                listOf("runtimeProfiles", "sourceIdentities", runtimeTarget, "path"), JsonPrimitive("D:/other/manager_su_runtime_target.c")),
                "ULTRA_KERNEL_RUNTIME_LAYOUT_SOURCE_PATH")
            // R17 section-relative callback relocations; prior 271 / 1 / 23 cases are unchanged.
            val relocationProofPath = listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification")
            fun relocationFirstRow(path: List<String>, key: String, value: JsonElement?): JsonObject {
                val rows = path.fold(payload as JsonElement) { owner, item -> owner.jsonObject.getValue(item) }.jsonArray
                return replaceReferenceFact(payload, path, JsonArray(listOf(replaceReferenceFact(rows.first().jsonObject, listOf(key), value)) + rows.drop(1)))
            }
            expected("relocation-required-target-manager_resident_host", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_resident_host", "msu_management_init"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:manager_resident_host")
            expected("relocation-symbol-bucket-manager_resident_host", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_resident_host", "msu_management_init"), "symbol", JsonPrimitive("wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:manager_resident_host:msu_management_init")
            expected("relocation-ordinary-blank-symbol-manager_resident_host", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_resident_host", "msu_management_init"), "symbol", JsonPrimitive("")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:manager_resident_host:msu_management_init")
            expected("relocation-required-target-manager_su_runtime_factory", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_su_runtime_factory", "msu_runtime_service_register"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:manager_su_runtime_factory")
            expected("relocation-symbol-bucket-manager_su_runtime_factory", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_su_runtime_factory", "msu_runtime_service_register"), "symbol", JsonPrimitive("wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:manager_su_runtime_factory:msu_runtime_service_register")
            expected("relocation-ordinary-blank-symbol-manager_su_runtime_factory", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_su_runtime_factory", "msu_runtime_service_register"), "symbol", JsonPrimitive("")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:manager_su_runtime_factory:msu_runtime_service_register")
            expected("relocation-required-target-su_observed_install_source", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "su_observed_install_source", "msu_su_registry_create"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:su_observed_install_source")
            expected("relocation-symbol-bucket-su_observed_install_source", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "su_observed_install_source", "msu_su_registry_create"), "symbol", JsonPrimitive("wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:su_observed_install_source:msu_su_registry_create")
            expected("relocation-ordinary-blank-symbol-su_observed_install_source", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "su_observed_install_source", "msu_su_registry_create"), "symbol", JsonPrimitive("")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:su_observed_install_source:msu_su_registry_create")
            expected("relocation-required-target-su_install_registry", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "su_install_registry", "msu_uid_gateway_bind_reader"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:su_install_registry")
            expected("relocation-symbol-bucket-su_install_registry", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "su_install_registry", "msu_uid_gateway_bind_reader"), "symbol", JsonPrimitive("wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:su_install_registry:msu_uid_gateway_bind_reader")
            expected("relocation-ordinary-blank-symbol-su_install_registry", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "su_install_registry", "msu_uid_gateway_bind_reader"), "symbol", JsonPrimitive("")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:su_install_registry:msu_uid_gateway_bind_reader")
            expected("relocation-required-target-su_durable_authority", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "su_durable_authority", "msu_su_modern_authority_list"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:su_durable_authority")
            expected("relocation-symbol-bucket-su_durable_authority", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "su_durable_authority", "msu_su_modern_authority_list"), "symbol", JsonPrimitive("wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:su_durable_authority:msu_su_modern_authority_list")
            expected("relocation-ordinary-blank-symbol-su_durable_authority", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "su_durable_authority", "msu_su_modern_authority_list"), "symbol", JsonPrimitive("")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:su_durable_authority:msu_su_modern_authority_list")
            expected("relocation-required-target-manager_management", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_management", "msu_uid_gateway_discard_host_unpublished"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:manager_management")
            expected("relocation-symbol-bucket-manager_management", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_management", "msu_uid_gateway_discard_host_unpublished"), "symbol", JsonPrimitive("wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:manager_management:msu_uid_gateway_discard_host_unpublished")
            expected("relocation-ordinary-blank-symbol-manager_management", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_management", "msu_uid_gateway_discard_host_unpublished"), "symbol", JsonPrimitive("")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:manager_management:msu_uid_gateway_discard_host_unpublished")
            expected("relocation-required-target-observedSourceCallbacks", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:observedSourceCallbacks")
            expected("relocation-symbol-bucket-observedSourceCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), "symbol", JsonPrimitive("wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-required-target-durableAuthorityCallbacks", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:durableAuthorityCallbacks")
            expected("relocation-symbol-bucket-durableAuthorityCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), "symbol", JsonPrimitive("wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-addend-msu_observed_pin_current", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-owner-msu_observed_pin_current", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_observed_pin_current", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-source-section-msu_observed_pin_current", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-empty-callers-msu_observed_pin_current", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-frame-only-msu_observed_pin_current", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":724,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-addend-msu_observed_put_install", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_put_install"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_install")
            expected("relocation-callback-owner-msu_observed_put_install", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_observed_put_install", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_install")
            expected("relocation-callback-source-section-msu_observed_put_install", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_put_install"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_install")
            expected("relocation-callback-empty-callers-msu_observed_put_install", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_put_install"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_install")
            expected("relocation-callback-frame-only-msu_observed_put_install", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_put_install"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":764,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_install")
            expected("relocation-callback-addend-msu_observed_read_grant", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_read_grant"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_read_grant")
            expected("relocation-callback-owner-msu_observed_read_grant", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_observed_read_grant", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_read_grant")
            expected("relocation-callback-source-section-msu_observed_read_grant", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_read_grant"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_read_grant")
            expected("relocation-callback-empty-callers-msu_observed_read_grant", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_read_grant"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_read_grant")
            expected("relocation-callback-frame-only-msu_observed_read_grant", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_read_grant"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":792,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_read_grant")
            expected("relocation-callback-addend-msu_observed_check_closed", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_check_closed"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_check_closed")
            expected("relocation-callback-owner-msu_observed_check_closed", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_observed_check_closed", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_check_closed")
            expected("relocation-callback-source-section-msu_observed_check_closed", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_check_closed"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_check_closed")
            expected("relocation-callback-empty-callers-msu_observed_check_closed", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_check_closed"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_check_closed")
            expected("relocation-callback-frame-only-msu_observed_check_closed", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_check_closed"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":832,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_check_closed")
            expected("relocation-callback-addend-msu_observed_quiesce", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_quiesce"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_quiesce")
            expected("relocation-callback-owner-msu_observed_quiesce", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_observed_quiesce", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_quiesce")
            expected("relocation-callback-source-section-msu_observed_quiesce", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_quiesce"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_quiesce")
            expected("relocation-callback-empty-callers-msu_observed_quiesce", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_quiesce"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_quiesce")
            expected("relocation-callback-frame-only-msu_observed_quiesce", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_quiesce"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":852,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_quiesce")
            expected("relocation-callback-addend-msu_observed_put_owner", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_put_owner"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_owner")
            expected("relocation-callback-owner-msu_observed_put_owner", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_observed_put_owner", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_owner")
            expected("relocation-callback-source-section-msu_observed_put_owner", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_put_owner"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_owner")
            expected("relocation-callback-empty-callers-msu_observed_put_owner", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_put_owner"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_owner")
            expected("relocation-callback-frame-only-msu_observed_put_owner", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_put_owner"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":892,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_put_owner")
            expected("relocation-callback-addend-msu_observed_validate_producer", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_validate_producer"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_validate_producer")
            expected("relocation-callback-owner-msu_observed_validate_producer", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_observed_validate_producer", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_validate_producer")
            expected("relocation-callback-source-section-msu_observed_validate_producer", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_validate_producer"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_validate_producer")
            expected("relocation-callback-empty-callers-msu_observed_validate_producer", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_validate_producer"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_validate_producer")
            expected("relocation-callback-frame-only-msu_observed_validate_producer", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_validate_producer"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":928,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_validate_producer")
            expected("relocation-callback-addend-msu_durable_grant", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-owner-msu_durable_grant", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_durable_grant", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-source-section-msu_durable_grant", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-empty-callers-msu_durable_grant", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-frame-only-msu_durable_grant", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":64,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-addend-msu_durable_put", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_put"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_put")
            expected("relocation-callback-owner-msu_durable_put", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_durable_put", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_put")
            expected("relocation-callback-source-section-msu_durable_put", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_put"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_put")
            expected("relocation-callback-empty-callers-msu_durable_put", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_put"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_put")
            expected("relocation-callback-frame-only-msu_durable_put", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_put"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":112,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_put")
            expected("relocation-callback-addend-msu_durable_list", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_list"), "addend", JsonPrimitive(5L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_list")
            expected("relocation-callback-owner-msu_durable_list", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_durable_list", "object"), JsonPrimitive("manager_management")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_list")
            expected("relocation-callback-source-section-msu_durable_list", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_list"), "section", JsonPrimitive(".text.wrong_creator")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_list")
            expected("relocation-callback-empty-callers-msu_durable_list", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_list"), "callers", JsonArray(emptyList())),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_list")
            expected("relocation-callback-frame-only-msu_durable_list", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_list"), JsonArray(listOf(Json.parseToJsonElement("{\"section\":\".eh_frame\",\"offset\":140,\"type\":261,\"symbol\":\"\",\"addend\":4,\"callers\":[]}")))),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_list")
            expected("relocation-callback-missing-symbol-observedSourceCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), "symbol", null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-negative-offset-observedSourceCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), "offset", JsonPrimitive(-1L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-unaligned-offset-observedSourceCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), "offset", JsonPrimitive(529L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-outside-caller-observedSourceCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), "offset", JsonPrimitive(676L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-zero-type-observedSourceCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), "type", JsonPrimitive(0)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-target-section-observedSourceCallbacks", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_observed_pin_current", "objectEntry", "section"), JsonPrimitive(".text.wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-frame-type-observedSourceCallbacks", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "observedSourceCallbacks", "msu_observed_pin_current"), Json.parseToJsonElement("[{\"section\":\".text.msu_su_observed_create\",\"offset\":528,\"type\":275,\"symbol\":\"\",\"addend\":4,\"callers\":[\"msu_su_observed_create\"]},{\"section\":\".text.msu_su_observed_create\",\"offset\":532,\"type\":277,\"symbol\":\"\",\"addend\":4,\"callers\":[\"msu_su_observed_create\"]},{\"section\":\".eh_frame\",\"offset\":724,\"type\":283,\"symbol\":\"\",\"addend\":4,\"callers\":[]}]")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:observedSourceCallbacks:msu_observed_pin_current")
            expected("relocation-callback-missing-symbol-durableAuthorityCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), "symbol", null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-negative-offset-durableAuthorityCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), "offset", JsonPrimitive(-1L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-unaligned-offset-durableAuthorityCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), "offset", JsonPrimitive(205L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-outside-caller-durableAuthorityCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), "offset", JsonPrimitive(316L)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-zero-type-durableAuthorityCallbacks", relocationFirstRow(listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), "type", JsonPrimitive(0)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-target-section-durableAuthorityCallbacks", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "retainedFunctions", "msu_durable_grant", "objectEntry", "section"), JsonPrimitive(".text.wrong_target")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-callback-frame-type-durableAuthorityCallbacks", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "durableAuthorityCallbacks", "msu_durable_grant"), Json.parseToJsonElement("[{\"section\":\".text.msu_su_durable_authority_create\",\"offset\":204,\"type\":275,\"symbol\":\"\",\"addend\":4,\"callers\":[\"msu_su_durable_authority_create\"]},{\"section\":\".text.msu_su_durable_authority_create\",\"offset\":208,\"type\":277,\"symbol\":\"\",\"addend\":4,\"callers\":[\"msu_su_durable_authority_create\"]},{\"section\":\".eh_frame\",\"offset\":64,\"type\":283,\"symbol\":\"\",\"addend\":4,\"callers\":[]}]")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:durableAuthorityCallbacks:msu_durable_grant")
            expected("relocation-procattr-symbol-bucket-management", relocationFirstRow(listOf("referenceAbi", "managementAbi", "executionContext", "procattrRelocations", "filp_open"), "symbol", JsonPrimitive("vfs_write")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:procattr:filp_open")
            expected("relocation-procattr-blank-symbol-management", relocationFirstRow(listOf("referenceAbi", "managementAbi", "executionContext", "procattrRelocations", "filp_open"), "symbol", JsonPrimitive("")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:procattr:filp_open")
            expected("relocation-procattr-required-target-management", replaceReferenceFact(payload, listOf("referenceAbi", "managementAbi", "executionContext", "procattrRelocations", "filp_open"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:procattr")
            expected("relocation-procattr-symbol-bucket-uidSnapshotAbi", relocationFirstRow(listOf("referenceAbi", "managementAbi", "uidSnapshotAbi", "executionContext", "procattrRelocations", "filp_open"), "symbol", JsonPrimitive("vfs_write")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:procattr:filp_open")
            expected("relocation-procattr-blank-symbol-uidSnapshotAbi", relocationFirstRow(listOf("referenceAbi", "managementAbi", "uidSnapshotAbi", "executionContext", "procattrRelocations", "filp_open"), "symbol", JsonPrimitive("")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:procattr:filp_open")
            expected("relocation-procattr-required-target-uidSnapshotAbi", replaceReferenceFact(payload, listOf("referenceAbi", "managementAbi", "uidSnapshotAbi", "executionContext", "procattrRelocations", "filp_open"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:procattr")
            expected("relocation-procattr-symbol-bucket-procattrAbi", relocationFirstRow(listOf("referenceAbi", "managementAbi", "procattrAbi", "executionContext", "procattrRelocations", "filp_open"), "symbol", JsonPrimitive("vfs_write")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:procattr:filp_open")
            expected("relocation-procattr-blank-symbol-procattrAbi", relocationFirstRow(listOf("referenceAbi", "managementAbi", "procattrAbi", "executionContext", "procattrRelocations", "filp_open"), "symbol", JsonPrimitive("")),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:procattr:filp_open")
            expected("relocation-procattr-required-target-procattrAbi", replaceReferenceFact(payload, listOf("referenceAbi", "managementAbi", "procattrAbi", "executionContext", "procattrRelocations", "filp_open"), null),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATIONS:procattr")
            val r17OrdinaryRelocations = relocationProofPath.plus("relocations").plus("manager_resident_host")
                .fold(payload as JsonElement) { owner, key -> owner.jsonObject.getValue(key) }.jsonObject
            expected("relocation-additional-row-still-validated", replaceReferenceFact(payload, listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification", "relocations", "manager_resident_host"), JsonObject(r17OrdinaryRelocations + Json.parseToJsonElement("{\"unexpected_target\":[{\"section\":\".text.msu_manager_resident_init\",\"offset\":900,\"type\":283,\"symbol\":\"wrong_target\",\"addend\":0,\"callers\":[\"msu_manager_resident_init\"]}]}").jsonObject)),
                "ULTRA_KERNEL_SOURCE_QUALIFICATION_RELOCATION:manager_resident_host:unexpected_target")
            // R17 copied build-command identity keeps its exact role and content binding.
            expected("bootstrap-command-content-bytes", replaceReferenceFact(payload, listOf("support", "bootstrap", "nativeCommandIdentity", "bytes"), JsonPrimitive(57789)),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY")
            expected("bootstrap-command-content-sha256", replaceReferenceFact(payload, listOf("support", "bootstrap", "nativeCommandIdentity", "sha256"), JsonPrimitive("0".repeat(64))),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY")
            expected("bootstrap-command-role", replaceReferenceFact(payload, listOf("support", "bootstrap", "nativeCommandIdentity", "path"), JsonPrimitive("D:/other/.manager_management.o.cmd")),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY")
            expected("bootstrap-command-zero-byte-identity", replaceReferenceFact(payload, listOf("support", "bootstrap", "nativeCommandIdentity", "bytes"), JsonPrimitive(0)),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_SOURCE_IDENTITY")
            expected("bootstrap-native-policy-kcfiOptionPresent", replaceReferenceFact(payload, listOf("support", "bootstrap", "nativeCfiPolicy", "kcfiOptionPresent"), JsonPrimitive(false)),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY")
            expected("bootstrap-native-policy-kcfiEnabled", replaceReferenceFact(payload, listOf("support", "bootstrap", "nativeCfiPolicy", "kcfiEnabled"), JsonPrimitive(false)),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY")
            expected("bootstrap-native-policy-normalizeIntegers", replaceReferenceFact(payload, listOf("support", "bootstrap", "nativeCfiPolicy", "normalizeIntegers"), JsonPrimitive(true)),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY")
            expected("bootstrap-native-retirement-normalize", replaceReferenceFact(payload, listOf("support", "bootstrap", "retirement", "normalizeIntegers"), JsonPrimitive(true)),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY")
            expected("bootstrap-native-policy-command-missing", replaceReferenceFact(payload, listOf("support", "bootstrap", "nativeCommandIdentity"), null),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY_PAIR")
            expected("bootstrap-native-policy-policy-missing", replaceReferenceFact(payload, listOf("support", "bootstrap", "nativeCfiPolicy"), null),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY_PAIR")
            val r17HostCommandsPath = listOf("referenceAbi", "hostRuntimeAbi", "commandIdentities")
            val r17HostCommands = r17HostCommandsPath.fold(payload as JsonElement) { owner, key -> owner.jsonObject.getValue(key) }.jsonArray
            val r17HostTypeCommand = r17HostCommands.single {
                it.jsonObject.getValue("path").jsonPrimitive.content.replace('\\', '/').substringAfterLast('/') == ".manager_host_runtime_types.o.cmd"
            }
            expected("bootstrap-native-role-missing", replaceReferenceFact(payload, r17HostCommandsPath, JsonArray(r17HostCommands.filter { it != r17HostTypeCommand })),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY")
            expected("bootstrap-native-role-duplicate", replaceReferenceFact(payload, r17HostCommandsPath, JsonArray(r17HostCommands + r17HostTypeCommand)),
                "ULTRA_KERNEL_RESIDENT_BOOTSTRAP_NATIVE_POLICY")
        }
        println("ABI4_BOUNDARY_NEGATIVES=" + (if (phase == "abi4-r5") 379 else 23) + "; ACTUAL_LOAD=" + (if (phase == "abi4-r5") "PASS" else "RED_FACT_FIELDS") + "; targetPlan=false; deviceOperations=0; assetWrites=0")
        return
    }
    val loaded = UltraKernelManagerClaimPayload.load(base, release)
    check(loaded.referenceAbi?.management?.sourceQualification?.claimSha256 == UltraKernelSpec.sha256(loaded.elf))
    println("PASS reference-real-selected-load")
    fun negative(name: String, path: List<String>, value: JsonElement?, errorPrefix: String) {
        val changed = replaceReferenceFact(payload, path, value)
        val failure = runCatching {
            UltraKernelManagerClaimPayload.load(RepackedReferenceClaims(base, manifest, family, changed), release)
        }.exceptionOrNull()
        check(failure?.message?.startsWith(errorPrefix) == true) { "REFERENCE_NEGATIVE:$name:$failure" }
        println("PASS $name:${failure?.message}")
    }
    val management = listOf("referenceAbi", "managementAbi")
    val proof = listOf("referenceAbi", "coreLifecycleAbi", "sourceQualification")
    negative("metadata-incomplete", management + "metadataComplete", JsonPrimitive(false), "ULTRA_KERNEL_MANAGEMENT_REFERENCE_INCOMPLETE:")
    negative("target-flag-promoted", management + "targetQualified", JsonPrimitive(true), "ULTRA_KERNEL_MANAGEMENT_REFERENCE_INCOMPLETE:")
    negative("complete-flag-promoted", management + "complete", JsonPrimitive(true), "ULTRA_KERNEL_MANAGEMENT_REFERENCE_INCOMPLETE:")
    negative("frozen-flag-promoted", management + "sourceFrozen", JsonPrimitive(true), "ULTRA_KERNEL_MANAGEMENT_REFERENCE_INCOMPLETE:")
    negative("missing-fact", management + "missing", JsonArray(listOf(JsonPrimitive("fixture-missing"))), "ULTRA_KERNEL_MANAGEMENT_REFERENCE_INCOMPLETE:")
    negative("claim-hash-mismatch", listOf("referenceAbi", "claimSha256"), JsonPrimitive("0".repeat(64)), "ULTRA_KERNEL_RESIDENT_REFERENCE_IDENTITY")
    negative("source-proof-absent", proof, null, "ULTRA_KERNEL_MANAGEMENT_REFERENCE_INCOMPLETE:")
    negative("source-proof-promoted", proof + "targetQualified", JsonPrimitive(true), "ULTRA_KERNEL_SOURCE_QUALIFICATION_INCOMPLETE:")
    negative("source-generation-mismatch", proof + listOf("sourceSelection", "claimSha256"), JsonPrimitive("0".repeat(64)), "ULTRA_KERNEL_SOURCE_QUALIFICATION_GENERATION")
    negative("context-unqualified", management + listOf("executionContext", "sourceQualified"), JsonPrimitive(false), "ULTRA_KERNEL_SOURCE_QUALIFICATION_CONTEXT:")
    check(UltraKernelSpec.sha256(base.read("ultra-manager-claim/manifest.json")) == UltraKernelSpec.sha256(manifestBytes))
    println("PASS reference-load-negative-matrix:10; deviceOperations=0; assetWrites=0")
}
