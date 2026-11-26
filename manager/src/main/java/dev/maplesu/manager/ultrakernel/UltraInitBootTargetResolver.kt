package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.UltraInitBootProducer
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

/** Rebinds a native module template from the paired input Image, without kernel sources. */
internal object UltraInitBootTargetResolver {
    private val abiConfig = setOf("ARM64", "64BIT", "COMPAT", "MODULES", "MODVERSIONS", "MODULE_UNLOAD",
        "SMP", "PREEMPT", "PREEMPT_RT", "PREEMPT_DYNAMIC", "PREEMPT_BUILD", "DEBUG_SPINLOCK",
        "DEBUG_LOCK_ALLOC", "LOCKDEP", "CFI", "CFI_CLANG", "CFI_ICALL_NORMALIZE_INTEGERS",
        "SHADOW_CALL_STACK", "THREAD_INFO_IN_TASK", "GENERIC_ENTRY", "SECCOMP", "SECCOMP_FILTER",
        "ARCH_HAS_SYSCALL_WRAPPER", "ARM64_VA_BITS", "RANDSTRUCT_FULL", "RANDSTRUCT_PERFORMANCE",
        "DEBUG_CREDENTIALS", "SECURITY", "SECURITY_SELINUX", "AUDIT",
        "REFCOUNT_FULL", "SCHED_CORE", "VMAP_STACK", "ARM64_4K_PAGES", "ARM64_16K_PAGES", "ARM64_64K_PAGES")
    // Objects copied/embedded by value retain their complete native shape. Pointer-only objects use the runtime table.
    private val localTypes = setOf("module", "kprobe", "pt_regs", "file_operations", "kiocb", "iov_iter",
        "mutex", "spinlock_t", "raw_spinlock_t", "work_struct", "callback_head", "fsnotify_mark",
        "fsnotify_ops", "path", "list_head", "hlist_node", "user_arg_ptr")
    private val profileNames = listOf("do_execveat_common", "__arm64_sys_newfstatat", "__arm64_sys_faccessat",
        "__arm64_sys_statx", "__arm64_sys_faccessat2")
    private const val directAliasPrefix = "msu_direct_target_"
    private val directV2Targets = setOf("__arm64_sys_execve", "__flush_icache_range", "__module_address",
        "__module_text_address", "__put_cred", "__vfs_getxattr", "__vmalloc_node_range", "__vmalloc_node_range_noprof",
        "aarch64_insn_patch_text", "abort_creds", "alloc_uid", "caches_clean_inval_pou", "cancel_work", "commit_creds",
        "copy_from_kernel_nofault", "copy_from_user_nofault", "copy_to_kernel_nofault", "copy_to_user_nofault",
        "core_kernel_text", "execmem_alloc", "execmem_alloc_rw", "execmem_free", "file_open_root", "filp_open",
        "find_task_by_pid_ns", "fortify_panic", "free_nsproxy", "fsnotify_add_mark", "fsnotify_alloc_group",
        "fsnotify_destroy_group", "fsnotify_destroy_mark", "fsnotify_get_group", "fsnotify_get_mark", "fsnotify_init_mark",
        "fsnotify_put_group", "fsnotify_put_mark", "fsnotify_wait_marks_destroyed", "get_mm_exe_file", "get_task_cred",
        "get_task_exe_file", "getname_kernel", "groups_alloc", "groups_sort", "is_subdir", "iterate_dir", "kernel_read",
        "module_alloc", "module_memfree", "our_mnt", "override_creds", "path_get", "path_mount", "path_umount",
        "prepare_creds", "putname", "revert_creds", "schedule_on_each_cpu", "seccomp_filter_release",
        "security_cred_getsecid", "security_secctx_to_secid", "set_cred_ucounts", "set_groups", "set_memory_nx", "set_memory_ro", "set_memory_rw",
        "set_memory_x", "sha256", "sync_icache_aliases", "synchronize_rcu_tasks", "task_work_add", "vfree",
        "vfs_getxattr", "vfs_setxattr", "vfs_write", "vmalloc", "vmalloc_noprof", "vmalloc_user", "vmalloc_user_noprof")
    private data class DirectCalls(val version: Int, val targetsByImport: Map<String, String>, val markerSha256: String?) {
        val imports: Set<String> get() = targetsByImport.keys
        val targets: Set<String> get() = targetsByImport.values.toSet()
        fun evidenceMatches(evidence: JsonObject): Boolean {
            val declared = evidence["directImports"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return false
            if (declared.distinct().size != declared.size || declared.toSet() != imports) return false
            if (version != 2) return "directImportsV2" !in evidence && "directMarkerSha256" !in evidence
            return evidence["directMarkerSha256"]?.jsonPrimitive?.content == markerSha256 &&
                evidence["directImportsV2"] == JsonArray(targetsByImport.map { (alias, target) ->
                    buildJsonObject { put("alias", alias); put("target", target) }
                })
        }
    }

    fun resolve(template: UltraInitBootProducer.Artifact, pairedBoot: File,
        claimAssets: UltraKernelAssetSource? = null): UltraInitBootProducer.Artifact {
        val targetHash = UltraKernelSpec.sha256(pairedBoot)
        if (template.isKmiComponent) {
            check(UltraKernelSpec.sha256(pairedBoot) == targetHash) { "ULTRA_INIT_INPUT_CHANGED" }
            // Loading and the runtime producer validate the actual target; this is no Image/BTF proof.
            return template
        }
        val module = Module(template.bridgeBytes())
        if (targetHash == template.targetBootSha256 && template.credentialMode != "module-parameter" && !module.hasTaskProfile()) return template
        check(template.credentialMode == "module-parameter") { "ULTRA_INIT_DYNAMIC_CREDENTIAL_TEMPLATE" }
        val sourceKernel = UltraKernelPlan.readKernel(template.targetBoot)
        check(UltraKernelSpec.sha256(sourceKernel) == template.kernelSha256) { "ULTRA_INIT_TEMPLATE_CHANGED" }
        val sourceSymbols = UltraKernelKallsymsTable.decode(sourceKernel)
        val legacy = UltraKernelLegacyModuleRoute.required(sourceKernel)
        val directCalls = module.legacyDirectCalls(legacy)
        val directImports = directCalls.imports
        val directTargets = directCalls.targets
        val legacyTaskProfile = if (legacy && module.hasTaskProfile()) legacyTaskProfileFacts(template, module) else null
        val legacyCallerTypes = if (legacyTaskProfile != null) legacyTaskProfile.report.getValue("legacyTarget").jsonObject
            .getValue("callerTypeSets").jsonObject.mapValues { (_, ids) -> ids.jsonArray.map { it.jsonPrimitive.content }.toSet() }
            else if (legacy) legacyCallerTypes(template) else null
        if (module.hasTaskProfile() && targetHash == template.targetBootSha256 &&
            template.targetInputSha256["taskProfileProof"] != null) {
            val payload = if (legacyTaskProfile == null) UltraKernelManagerClaimPayload.load(requireNotNull(claimAssets) {
                "ULTRA_INIT_TASK_PROFILE_CLAIM_ASSETS_REQUIRED"
            }, template.kernelRelease) else null
            val facts = payload?.referenceAbi?.taskProfile
            val implementation = legacyTaskProfile?.implementation ?: requireNotNull(facts?.implementation) {
                "ULTRA_INIT_TASK_PROFILE_IMPLEMENTATION_REQUIRED"
            }
            val mode = if (legacyTaskProfile != null) "legacy-lto-cfi" else requireNotNull(facts).cfiMode
            val proof = UltraKernelTaskProfileTarget.derive(sourceKernel, sourceSymbols, implementation, mode)
            val generationMatches = if (legacyTaskProfile != null)
                template.targetInputSha256["taskProfileModule"] == legacyTaskProfile.report.getValue("bundledModuleSha256").jsonPrimitive.content
                else template.targetInputSha256["taskProfileClaim"] == UltraKernelSpec.sha256(requireNotNull(payload).elf)
            check(proof.complete && template.targetInputSha256["taskProfileProof"] == proof.proofSha256 &&
                template.targetInputSha256["taskProfileSource"] == implementation.sourceSha256 && generationMatches) {
                "ULTRA_INIT_TASK_PROFILE_REBOUND_PROOF"
            }
            module.verifyTaskProfile(implementation, mode, null, proof)
            check(UltraKernelSpec.sha256(pairedBoot) == targetHash) { "ULTRA_INIT_INPUT_CHANGED" }
            return template
        }
        val session = if (module.hasSession()) sessionFacts(template, module) else null
        if (targetHash == template.targetBootSha256 && !module.hasTaskProfile() && session == null) {
            check(UltraKernelSpec.sha256(pairedBoot) == targetHash) { "ULTRA_INIT_INPUT_CHANGED" }
            return template
        }
        val (contractFile, contract, manifest) = templateContract(template)
        val loaderAbi = sameKmiLoaderEvidence(template, module, sourceKernel, sourceSymbols, legacy, contract, manifest, directImports)
        fun verifyInputs() {
            check(UltraKernelSpec.sha256(pairedBoot) == targetHash &&
                UltraKernelSpec.sha256(template.targetBoot) == template.targetBootSha256 &&
                UltraKernelSpec.sha256(contractFile) == manifest.getValue("targetContractSha256").jsonPrimitive.content &&
                (loaderAbi == null || UltraKernelSpec.sha256(File(template.targetBoot.parentFile,
                    manifest.getValue("abiEvidence").jsonPrimitive.content)) == manifest.getValue("abiEvidenceSha256").jsonPrimitive.content)) {
                "ULTRA_INIT_INPUT_CHANGED"
            }
        }
        if (session != null) verifySession(sourceKernel, sourceSymbols, session, legacyCallerTypes, loaderAbi != null)
        if (targetHash == template.targetBootSha256 && !module.hasTaskProfile()) {
            verifyInputs()
            return template
        }
        val targetKernel = UltraKernelPlan.readKernel(pairedBoot)
        val targetKernelHash = UltraKernelSpec.sha256(targetKernel)
        val targetRelease = UltraKernelSpec.releaseOf(targetKernel)
        check(targetRelease.isNotEmpty()) { "ULTRA_INIT_TARGET_RELEASE" }
        val targetSymbols = UltraKernelKallsymsTable.decode(targetKernel)
        check(UltraKernelLegacyModuleRoute.required(targetKernel) == legacy) { "ULTRA_INIT_TARGET_CFI_MODE" }
        for (name in directTargets) {
            for ((kernel, symbols) in listOf(sourceKernel to sourceSymbols, targetKernel to targetSymbols)) {
                val entry = unique(symbols, name)
                check(entry >= 0 && entry % 4 == 0 && entry <= kernel.size - 4) { "ULTRA_INIT_LEGACY_DIRECT_TARGET:$name" }
            }
        }
        val sourceConfig = configuration(sourceKernel)
        val targetConfig = configuration(targetKernel)
        verifyTargetConfiguration(sourceConfig, targetConfig)
        if (loaderAbi != null) check(kernelSeries(targetRelease) == loaderAbi.getValue("targetSeries").jsonPrimitive.content) {
            "ULTRA_INIT_TARGET_KMI_SERIES"
        }
        val embeddedTypes = localTypes + if (session != null) setOf("group_info") else emptySet()
        if (loaderAbi == null) {
            val sourceTypes = UltraKernelBtf.typeSignatures(sourceKernel, sourceSymbols, embeddedTypes)
                ?: error("ULTRA_INIT_TEMPLATE_BTF_REQUIRED")
            val targetTypes = UltraKernelBtf.typeSignatures(targetKernel, targetSymbols, embeddedTypes)
                ?: error("ULTRA_INIT_TARGET_BTF_REQUIRED")
            for (type in embeddedTypes) check(sourceTypes.getValue(type) == targetTypes.getValue(type)) {
                "ULTRA_INIT_TARGET_LOCAL_ABI:$type"
            }
        }
        if (session != null) verifySession(targetKernel, targetSymbols, session, legacyCallerTypes, loaderAbi != null)
        val modversions = targetConfig["CONFIG_MODVERSIONS"] == "y"
        val sourceExports = exports(sourceKernel, sourceSymbols, modversions)
        val targetExports = exports(targetKernel, targetSymbols, modversions)
        val versions = module.versions()
        check(versions.isNotEmpty() == modversions) { "ULTRA_INIT_TARGET_CRC_REQUIRED" }
        if (modversions) check(versions["module_layout"]?.second == sourceExports["module_layout"]?.crc &&
            sourceExports["module_layout"]?.crc == targetExports["module_layout"]?.crc) {
            "ULTRA_INIT_TARGET_MODULE_OBJECT_ABI"
        }
        val namespaces = module.tags().filter { it.startsWith("import_ns=") }.map { it.substring(10) }.toSet()
        val unprotected = if (targetConfig["CONFIG_MODULE_SIG_PROTECT"] == "y") unprotected(targetKernel, targetSymbols) else null
        val ordinaryImports = module.imports() - directImports
        val changed = (versions.keys - directImports).filter { sourceExports[it]?.crc != targetExports[it]?.crc }.toSet()
        if (loaderAbi != null) check(modversions && "module_layout" in versions &&
            ordinaryImports.all { it in versions } && changed.isEmpty()) { "ULTRA_INIT_TARGET_KMI_IMPORT_CRC" }
        val sourceChanged = if (changed.isEmpty()) emptyMap() else
            UltraKernelBtf.signatures(sourceKernel, sourceSymbols, changed, emptySet()).orEmpty()
        val targetChanged = if (changed.isEmpty()) emptyMap() else
            UltraKernelBtf.signatures(targetKernel, targetSymbols, changed, emptySet()).orEmpty()
        for (name in ordinaryImports + (versions.keys - directImports)) {
            val target = targetExports[name] ?: error("ULTRA_INIT_TARGET_EXPORT_MISSING:$name")
            val source = sourceExports[name] ?: error("ULTRA_INIT_TEMPLATE_EXPORT_MISSING:$name")
            check(target.namespace.isEmpty() || target.namespace in namespaces) { "ULTRA_INIT_TARGET_IMPORT_NAMESPACE:$name" }
            if (name in ordinaryImports && unprotected != null) check(name in unprotected) {
                "ULTRA_INIT_TARGET_PROTECTED_IMPORT:$name"
            }
            versions[name]?.let { (at, original) ->
                check(original == source.crc) { "ULTRA_INIT_TEMPLATE_CRC:$name" }
                if (target.crc != source.crc) {
                    val signature = sourceChanged[name]
                    check(signature != null && signature == targetChanged[name]) {
                        "ULTRA_INIT_TARGET_IMPORT_ABI:$name"
                    }
                }
                module.put32(at, target.crc)
            }
        }
        // Private native imports keep their actual typed resolver; the loader route does not claim target BTF.
        val prototypes = contract.getValue("prototypes").jsonObject
        if (loaderAbi == null) {
            val privateSignatures = UltraKernelBtf.signatures(targetKernel, targetSymbols, prototypes.keys, emptySet())
                ?: error("ULTRA_INIT_TARGET_BTF_REQUIRED")
            for ((name, signature) in prototypes) check(privateSignatures[name] == signature.jsonPrimitive.content) {
                "ULTRA_INIT_TARGET_PRIVATE_ABI:$name"
            }
        }
        val optionalPrivateNames = if (loaderAbi == null) emptySet() else contract.getValue("optionalPrivateSymbols").jsonArray
            .map { it.jsonPrimitive.content }.toSet()
        val privateNames = if (loaderAbi == null) prototypes.keys else {
            val required = contract.getValue("privateSymbols").jsonArray.map { it.jsonPrimitive.content }
            val optional = contract.getValue("optionalPrivateSymbols").jsonArray.map { it.jsonPrimitive.content }
            check(required.isNotEmpty() && required.distinct().size == required.size &&
                optional.distinct().size == optional.size && required.none { it in optional }) { "ULTRA_INIT_TEMPLATE_PRIVATE_SYMBOLS" }
            (required + optional.filter { targetSymbols.offsetsOf(it).isNotEmpty() }).toSet()
        }
        for (name in privateNames - directTargets) {
            if (legacy) {
                if (name !in optionalPrivateNames || sourceSymbols.offsetsOf(name).isNotEmpty())
                    UltraKernelLegacyCfi.canonicalEntry(sourceKernel, sourceSymbols, name)
                UltraKernelLegacyCfi.canonicalEntry(targetKernel, targetSymbols, name)
            } else if (targetConfig["CONFIG_CFI_CLANG"] == "y" || targetConfig["CONFIG_CFI"] == "y") {
                val a = unique(sourceSymbols, name); val b = unique(targetSymbols, name)
                check(a >= 4 && b >= 4 && word(sourceKernel, a - 4) == word(targetKernel, b - 4)) {
                    "ULTRA_INIT_TARGET_PRIVATE_CFI:$name"
                }
            }
        }
        if (legacy) {
            check(contract.getValue("cfiMode").jsonPrimitive.content == "legacy-lto-cfi") { "ULTRA_INIT_TARGET_LEGACY_CONTRACT" }
            for ((caller, ids) in requireNotNull(legacyCallerTypes)) check(
                UltraKernelLegacyCfi.slowpathCallsites(targetKernel, targetSymbols, caller).map { it.typeIdHex }.toSet() == ids) {
                "ULTRA_INIT_TARGET_LEGACY_TYPE:$caller"
            }
        } else if (targetConfig["CONFIG_CFI_CLANG"] == "y" || targetConfig["CONFIG_CFI"] == "y")
            check(initCfi(targetKernel, targetSymbols) == contract.getValue("initKcfi").jsonPrimitive.content.toLong().toInt()) {
                "ULTRA_INIT_TARGET_INIT_CFI"
            }
        if (loaderAbi == null) module.bindRuntime(targetKernel, targetSymbols)
        val page = pageBytes(targetKernel, targetConfig)
        if (loaderAbi == null) module.bindSdk(targetKernel, targetSymbols, targetConfig, page)
        else check(page == pageBytes(sourceKernel, sourceConfig)) { "ULTRA_INIT_TARGET_KMI_PAGE" }
        module.bindProfiles(sourceKernel, sourceSymbols, targetKernel, targetSymbols, loaderAbi != null)
        val taskProfileInputs = if (module.hasTaskProfile()) {
            val payload = if (legacyTaskProfile == null) UltraKernelManagerClaimPayload.load(requireNotNull(claimAssets) {
                "ULTRA_INIT_TASK_PROFILE_CLAIM_ASSETS_REQUIRED"
            }, targetRelease) else null
            val facts = payload?.referenceAbi?.taskProfile
            val implementation = legacyTaskProfile?.implementation ?: requireNotNull(facts?.implementation) {
                "ULTRA_INIT_TASK_PROFILE_IMPLEMENTATION_REQUIRED"
            }
            val (report, metadataHash) = if (legacyTaskProfile != null) legacyTaskProfile.report to legacyTaskProfile.sha256
                else taskProfileFacts(template, module, requireNotNull(payload), implementation)
            val mode = if (legacyTaskProfile != null) "legacy-lto-cfi" else requireNotNull(facts).cfiMode
            val proof = UltraKernelTaskProfileTarget.derive(targetKernel, targetSymbols, implementation, mode)
            check(proof.complete) { "ULTRA_INIT_TASK_PROFILE_TARGET_PROOFS:" +
                proof.missing.entries.joinToString(";") { "${it.key}=${it.value}" } }
            module.verifyTaskProfile(implementation, mode, report, null)
            module.bindTaskProfile(implementation, proof, mode)
            mapOf("taskProfileMetadata" to metadataHash, "taskProfileProof" to proof.proofSha256,
                "taskProfileSource" to implementation.sourceSha256) +
                if (legacyTaskProfile != null) mapOf("taskProfileModule" to report.getValue("bundledModuleSha256").jsonPrimitive.content)
                else mapOf("taskProfileClaim" to UltraKernelSpec.sha256(requireNotNull(payload).elf))
        } else emptyMap()
        val magic = vermagic(targetKernel, targetRelease)
        module.verifyOrSetVermagic(magic, versions.isNotEmpty())
        val sourcePage = pageBytes(sourceKernel, sourceConfig)
        val displacement = Math.addExact(template.metadataDisplacement, page - sourcePage)
        val abiInputs = if (loaderAbi != null) mapOf("abiEvidence" to manifest.getValue("abiEvidenceSha256").jsonPrimitive.content)
        else {
            val targetBtfStart = unique(targetSymbols, "__start_BTF")
            val targetBtfEnd = unique(targetSymbols, "__stop_BTF")
            check(targetBtfStart >= 0 && targetBtfEnd > targetBtfStart && targetBtfEnd <= targetKernel.size) { "ULTRA_INIT_TARGET_BTF_BOUNDS" }
            mapOf("btf" to UltraKernelSpec.sha256(targetKernel.copyOfRange(targetBtfStart, targetBtfEnd)))
        }
        val inputs = mapOf("templateBridge" to template.bridgeSha256,
            "templateContract" to UltraKernelSpec.sha256(contractFile),
            "config" to UltraKernelSpec.sha256(configBytes(targetKernel)),
            "moduleSymvers" to UltraKernelSpec.sha256(targetExports.toSortedMap().entries.joinToString("") {
                (name, value) -> "${Integer.toUnsignedString(value.crc, 16)}\t$name\t${value.namespace}\n"
            }.toByteArray(Charsets.US_ASCII))) + abiInputs + taskProfileInputs
        verifyInputs()
        val resolved = template.forTarget(pairedBoot, targetHash, targetRelease, targetKernelHash,
            module.bytes, inputs, page, displacement, magic, targetConfig["CONFIG_MODVERSIONS"] == "y")
        return if (legacyTaskProfile != null) resolved.withTaskProfileMetadata(legacyTaskProfile.encoded) else resolved
    }

    private fun templateContract(template: UltraInitBootProducer.Artifact): Triple<File, JsonObject, JsonObject> {
        val base = template.targetBoot.parentFile.canonicalFile
        val manifest = Json.parseToJsonElement(File(base, "manifest.json").readText()).jsonObject
        check(manifest["bridgeSha256"]?.jsonPrimitive?.content == template.bridgeSha256) { "ULTRA_INIT_TEMPLATE_CONTRACT_MODULE" }
        val (file, contract) = retainedJson(base, manifest, "targetContract")
        return Triple(file, contract, manifest)
    }

    private fun retainedJson(base: File, manifest: JsonObject, key: String): Pair<File, JsonObject> {
        val name = manifest[key]?.jsonPrimitive?.content ?: error("ULTRA_INIT_TEMPLATE_RETAINED_REQUIRED:$key")
        check(name.matches(Regex("[A-Za-z0-9_.-]+"))) { "ULTRA_INIT_TEMPLATE_RETAINED_PATH:$key" }
        val file = File(base, name).canonicalFile
        check(file.parentFile == base && file.length() in 1L..1048576L) { "ULTRA_INIT_TEMPLATE_RETAINED_FILE:$key" }
        val encoded = file.readBytes()
        check(encoded.size in 1..1048576 && UltraKernelSpec.sha256(encoded) == manifest.getValue(key + "Sha256").jsonPrimitive.content) {
            "ULTRA_INIT_TEMPLATE_RETAINED_CHANGED:$key"
        }
        return file to Json.parseToJsonElement(encoded.toString(Charsets.UTF_8)).jsonObject
    }

    private fun kernelSeries(release: String): String = Regex("^(\\d+\\.\\d+)\\.").find(release)?.groupValues?.get(1)
        ?: error("ULTRA_INIT_TARGET_KMI_RELEASE")

    private fun sameKmiLoaderEvidence(template: UltraInitBootProducer.Artifact, module: Module, source: ByteArray,
        sourceSymbols: UltraKernelSymbols, legacy: Boolean, contract: JsonObject, manifest: JsonObject, direct: Set<String>): JsonObject? {
        val declared = contract["abiEvidence"]?.jsonObject ?: return null
        if (declared["method"]?.jsonPrimitive?.content != "same-kmi-module-loader") return null
        val (_, evidence) = retainedJson(template.targetBoot.parentFile.canonicalFile, manifest, "abiEvidence")
        check(template.targetInputSha256["abiEvidence"] == manifest.getValue("abiEvidenceSha256").jsonPrimitive.content) {
            "ULTRA_INIT_TEMPLATE_KMI_EVIDENCE_BINDING"
        }
        val directCalls = module.legacyDirectCalls(legacy)
        val extra = setOf("bridgeSha256", "ordinaryImportsSha256", "directImports", "compiledNativeLayout") +
            if (directCalls.version == 2) setOf("directImportsV2", "directMarkerSha256") else emptySet()
        check(legacy && declared.all { (key, value) -> evidence[key] == value } && evidence.keys == declared.keys + extra &&
            evidence["schema"]?.jsonPrimitive?.content == "maplesu-same-kmi-loader-abi/1" &&
            evidence["targetAbiQualified"]?.jsonPrimitive?.booleanOrNull == false &&
            evidence["privatePrototypeMethod"]?.jsonPrimitive?.content == "runtime-native-typed-cfi-resolver") {
            "ULTRA_INIT_TEMPLATE_KMI_EVIDENCE"
        }
        val config = configuration(source)
        val release = UltraKernelSpec.releaseOf(source)
        val series = kernelSeries(release)
        check(config["CONFIG_MODVERSIONS"] == "y" && evidence["targetBootSha256"]?.jsonPrimitive?.content == template.targetBootSha256 &&
            evidence["targetKernelSha256"]?.jsonPrimitive?.content == template.kernelSha256 &&
            evidence["targetConfigSha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(configBytes(source)) &&
            evidence["targetRelease"]?.jsonPrimitive?.content == release && release == template.kernelRelease &&
            evidence["targetSeries"]?.jsonPrimitive?.content == series && evidence["buildSeries"]?.jsonPrimitive?.content == series &&
            kernelSeries(evidence.getValue("buildRelease").jsonPrimitive.content) == series &&
            evidence["targetVermagic"]?.jsonPrimitive?.content == vermagic(source, release) &&
            evidence["bridgeSha256"]?.jsonPrimitive?.content == template.bridgeSha256 &&
            evidence["targetSymversSha256"]?.jsonPrimitive?.content == template.targetInputSha256["moduleSymvers"] &&
            evidence["buildSymversSha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true &&
            UltraKernelSpec.sha256(module.bytes) == template.bridgeSha256) { "ULTRA_INIT_TEMPLATE_KMI_IDENTITY" }
        val ordinary = module.imports() - direct
        val versions = module.versions()
        val crc = evidence.getValue("moduleLayoutCrc").jsonPrimitive.content
        check(crc.matches(Regex("[0-9a-f]{8}")) && versions["module_layout"]?.second == Integer.parseUnsignedInt(crc, 16) &&
            ordinary.all { it in versions } && direct == directCalls.imports && directCalls.evidenceMatches(evidence) &&
            evidence["ordinaryImportsSha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(
                ordinary.sorted().joinToString("") { "$it\n" }.toByteArray(Charsets.US_ASCII))) {
            "ULTRA_INIT_TEMPLATE_KMI_IMPORTS"
        }
        val exports = exports(source, sourceSymbols, true)
        val namespaces = module.tags().filter { it.startsWith("import_ns=") }.map { it.substring(10) }.toSet()
        for (name in ordinary + (versions.keys - direct)) {
            val entry = exports[name] ?: error("ULTRA_INIT_TEMPLATE_EXPORT_MISSING:$name")
            check(versions[name]?.second == entry.crc && (entry.namespace.isEmpty() || entry.namespace in namespaces)) {
                "ULTRA_INIT_TEMPLATE_KMI_IMPORT_CRC:$name"
            }
        }
        val layout = evidence.getValue("compiledNativeLayout").jsonObject
        check(layout["method"]?.jsonPrimitive?.content == "same-kmi-ddk-compiled-native-tables" &&
            layout["derivedModuleSha256"]?.jsonPrimitive?.content == template.bridgeSha256 &&
            layout["sourceModuleSha256"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{64}")) == true &&
            layout["targetKernelSha256"]?.jsonPrimitive?.content == template.kernelSha256 &&
            layout["compilerLayoutIsTargetBtf"]?.jsonPrimitive?.booleanOrNull == false) { "ULTRA_INIT_TEMPLATE_KMI_NATIVE_EVIDENCE" }
        module.verifyCompiledNativeLayouts(layout)
        return evidence
    }

    private fun verifySession(kernel: ByteArray, symbols: UltraKernelSymbols,
        facts: UltraKernelManagerClaimPayload.SessionAbiFacts, callers: Map<String, Set<String>>?, loaderAbi: Boolean) {
        if (!loaderAbi) {
            UltraKernelResidentAbi.verifySession(kernel, symbols, facts, callers)
            return
        }
        check(facts.cfiMode == "legacy-lto-cfi" && UltraKernelLegacyModuleRoute.required(kernel) && !callers.isNullOrEmpty() &&
            ("compat_ioctl" in facts.fileOperations.members) == (configuration(kernel)["CONFIG_COMPAT"] == "y")) {
            "ULTRA_INIT_KMI_SESSION_CFI"
        }
        for ((caller, expected) in callers) check(expected.isNotEmpty() &&
            UltraKernelLegacyCfi.slowpathCallsites(kernel, symbols, caller).map { it.typeIdHex }.toSet() == expected) {
            "ULTRA_INIT_KMI_SESSION_CALLER:$caller"
        }
    }

    private fun verifyTargetConfiguration(sourceConfig: Map<String, String>, targetConfig: Map<String, String>) {
        for (option in abiConfig) check(sourceConfig["CONFIG_$option"].orEmpty().ifEmpty { "n" } ==
            targetConfig["CONFIG_$option"].orEmpty().ifEmpty { "n" }) { "ULTRA_INIT_TARGET_CONFIG_ABI:CONFIG_$option" }
        check(targetConfig["CONFIG_MODULES"] == "y" && targetConfig["CONFIG_ARM64"] == "y") { "ULTRA_INIT_TARGET_MODULES" }
        check(targetConfig["CONFIG_MODULE_SIG_FORCE"] != "y") { "ULTRA_INIT_TARGET_SIGNED_MODULE_REQUIRED" }
    }

    private data class LegacyTaskProfileModule(val report: JsonObject, val encoded: ByteArray,
        val sha256: String, val implementation: UltraKernelTaskProfileTarget.Implementation)

    private fun legacyTaskProfileFacts(template: UltraInitBootProducer.Artifact, module: Module): LegacyTaskProfileModule {
        val retained = template.taskProfileMetadataBytes()
        val encoded = if (retained != null) {
            check(retained.size in 1..1048576 && UltraKernelSpec.sha256(retained) ==
                template.targetInputSha256["taskProfileMetadata"]) { "ULTRA_INIT_LEGACY_TASK_PROFILE_RETAINED_IDENTITY" }
            retained
        } else {
            val base = template.targetBoot.parentFile.canonicalFile
            val manifest = Json.parseToJsonElement(File(base, "manifest.json").readText()).jsonObject
            check(manifest.getValue("bridgeSha256").jsonPrimitive.content == template.bridgeSha256) {
                "ULTRA_INIT_LEGACY_TASK_PROFILE_TEMPLATE"
            }
            val name = manifest.getValue("taskProfileAbi").jsonPrimitive.content
            check(name.matches(Regex("[A-Za-z0-9_.-]+"))) { "ULTRA_INIT_LEGACY_TASK_PROFILE_PATH" }
            val file = File(base, name).canonicalFile
            check(file.parentFile == base && file.length() in 1L..1048576L) { "ULTRA_INIT_LEGACY_TASK_PROFILE_FILE" }
            file.readBytes().also {
                check(UltraKernelSpec.sha256(it) == manifest.getValue("taskProfileAbiSha256").jsonPrimitive.content) {
                    "ULTRA_INIT_LEGACY_TASK_PROFILE_METADATA_CHANGED"
                }
            }
        }
        val report = Json.parseToJsonElement(encoded.toString(Charsets.UTF_8)).jsonObject
        if (retained == null) check(report.getValue("legacyTarget").jsonObject["targetSha256"]?.jsonPrimitive?.content == template.kernelSha256) {
            "ULTRA_INIT_LEGACY_TASK_PROFILE_TARGET_IMAGE"
        }
        val expectedModule = template.targetInputSha256["templateBridge"] ?: template.bridgeSha256
        check(report["bundledModuleSha256"]?.jsonPrimitive?.content == expectedModule &&
            report["bundledModuleBytes"]?.jsonPrimitive?.intOrNull == module.bytes.size) {
            "ULTRA_INIT_LEGACY_TASK_PROFILE_GENERATION"
        }
        val implementation = UltraKernelTaskProfileTarget.parseLegacyModule(report)
        module.verifyLegacyTaskProfile(implementation)
        return LegacyTaskProfileModule(report, encoded, UltraKernelSpec.sha256(encoded), implementation)
    }

    private fun legacyCallerTypes(template: UltraInitBootProducer.Artifact): Map<String, Set<String>> {
        val base = template.targetBoot.parentFile.canonicalFile
        val manifest = Json.parseToJsonElement(File(base, "manifest.json").readText()).jsonObject
        check(manifest.getValue("bridgeSha256").jsonPrimitive.content == template.bridgeSha256) {
            "ULTRA_INIT_LEGACY_TEMPLATE_MODULE"
        }
        val path = manifest.getValue("targetContract").jsonPrimitive.content
        check(path.matches(Regex("[A-Za-z0-9_.-]+"))) { "ULTRA_INIT_LEGACY_CONTRACT_PATH" }
        val file = File(base, path).canonicalFile
        check(file.parentFile == base && file.length() in 1L..1048576L &&
            UltraKernelSpec.sha256(file) == manifest.getValue("targetContractSha256").jsonPrimitive.content) {
            "ULTRA_INIT_LEGACY_CONTRACT_CHANGED"
        }
        val contract = Json.parseToJsonElement(file.readText()).jsonObject
        check(contract.getValue("cfiMode").jsonPrimitive.content == "legacy-lto-cfi" &&
            contract.getValue("inputs").jsonObject.getValue("kernelImage").jsonObject
                .getValue("sha256").jsonPrimitive.content == template.kernelSha256) {
            "ULTRA_INIT_LEGACY_CONTRACT_IMAGE"
        }
        return contract.getValue("legacyCfi").jsonObject.getValue("callerTypeSets").jsonObject.mapValues { (_, value) ->
            value.jsonArray.map { it.jsonPrimitive.content.also { id ->
                check(id.matches(Regex("[0-9a-f]{16}"))) { "ULTRA_INIT_LEGACY_TYPE64" }
            } }.toSet()
        }.also { check("do_one_initcall" in it && "process_one_work" in it) { "ULTRA_INIT_LEGACY_CALLERS" } }
    }

    private fun taskProfileFacts(template: UltraInitBootProducer.Artifact, module: Module,
        payload: UltraKernelManagerClaimPayload, implementation: UltraKernelTaskProfileTarget.Implementation): Pair<JsonObject, String> {
        val base = template.targetBoot.parentFile.canonicalFile
        val manifest = Json.parseToJsonElement(File(base, "manifest.json").readText()).jsonObject
        check(manifest.getValue("bridgeSha256").jsonPrimitive.content == template.bridgeSha256) {
            "ULTRA_INIT_TASK_PROFILE_TEMPLATE_MODULE"
        }
        val name = manifest["taskProfileAbi"]?.jsonPrimitive?.content
            ?: error("ULTRA_INIT_TASK_PROFILE_METADATA_REQUIRED")
        check(name.matches(Regex("[A-Za-z0-9_.-]+"))) { "ULTRA_INIT_TASK_PROFILE_METADATA_PATH" }
        val file = File(base, name).canonicalFile
        check(file.parentFile == base && file.length() in 1L..1048576L) { "ULTRA_INIT_TASK_PROFILE_METADATA_FILE" }
        val encoded = file.readBytes()
        val hash = UltraKernelSpec.sha256(encoded)
        check(hash == manifest.getValue("taskProfileAbiSha256").jsonPrimitive.content) {
            "ULTRA_INIT_TASK_PROFILE_METADATA_CHANGED"
        }
        val report = Json.parseToJsonElement(encoded.toString(Charsets.UTF_8)).jsonObject
        check(report["schema"]?.jsonPrimitive?.content == "maplesu-init-bridge-task-profile-abi/1" &&
            report["originalModuleWritten"]?.jsonPrimitive?.booleanOrNull == false &&
            report["targetQualified"]?.jsonPrimitive?.booleanOrNull == false &&
            report["deviceQualified"]?.jsonPrimitive?.booleanOrNull == false &&
            report["bundledModuleSha256"]?.jsonPrimitive?.content == template.bridgeSha256 &&
            report["bundledModuleBytes"]?.jsonPrimitive?.intOrNull == module.bytes.size &&
            report["claimSha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(payload.elf) &&
            report["sourceSuccessorIdentity"]?.jsonObject?.get("sha256")?.jsonPrimitive?.content == implementation.sourceSha256) {
            "ULTRA_INIT_TASK_PROFILE_METADATA_GENERATION"
        }
        val successor = report.getValue("sourceSuccessor").jsonObject
        check(successor["schema"]?.jsonPrimitive?.content == "maplesu-task-profile-source-successor/1" &&
            successor["productionWritten"]?.jsonPrimitive?.booleanOrNull == false &&
            successor["sourceOwnerFrozen"]?.jsonPrimitive?.booleanOrNull == true) { "ULTRA_INIT_TASK_PROFILE_SOURCE_SUCCESSOR" }
        val sources = report.getValue("sources").jsonObject
        val rows = successor.getValue("files").jsonArray.map { it.jsonObject }
        val required = setOf("manager_task_state.c", "manager_task_state.h", "manager_su_runtime_target.c", "manager_su_runtime_factory.c")
            .map { "runtime/ultra/identity/kernel/$it" }.toSet()
        check(rows.size == 4 && rows.map { it.getValue("logicalPath").jsonPrimitive.content }.toSet() == required &&
            sources.keys == required) { "ULTRA_INIT_TASK_PROFILE_SOURCE_SET" }
        for (row in rows) {
            val path = row.getValue("logicalPath").jsonPrimitive.content
            val selected = row.getValue("after").jsonObject
            val actual = sources.getValue(path).jsonObject
            check(actual["sha256"] == selected["sha256"] && actual["bytes"] == selected["bytes"] &&
                actual["selected"]?.jsonObject?.get("sha256") == selected["sha256"] &&
                actual["selected"]?.jsonObject?.get("bytes") == selected["bytes"]) { "ULTRA_INIT_TASK_PROFILE_SELECTED_SOURCE:$path" }
        }
        val types = implementation.nativeAbi.getValue("types").jsonObject.mapValues { it.value.jsonObject.getValue("signature") }
        check(report.getValue("nativeTypes").jsonObject == JsonObject(types)) { "ULTRA_INIT_TASK_PROFILE_NATIVE_TYPES" }
        val alignment = report.getValue("nativeAlignment").jsonObject
        val expectedAlignment = listOf("listHeadBytes", "listHeadAlignment", "rwlockBytes", "rwlockAlignment")
            .map { implementation.nativeAbi.getValue(it).jsonPrimitive.int }
        check(alignment["independentlyMeasuredFromBridgeBuild"]?.jsonPrimitive?.booleanOrNull == true &&
            alignment.getValue("values").jsonArray.map { it.jsonPrimitive.int } == expectedAlignment &&
            alignment.getValue("listHeadAlignment").jsonPrimitive.int == expectedAlignment[1] &&
            alignment.getValue("rwlockAlignment").jsonPrimitive.int == expectedAlignment[3]) { "ULTRA_INIT_TASK_PROFILE_NATIVE_ALIGNMENT" }
        for (key in listOf("bridge", "taskObject", "taskCommand", "sourceSelection", "strictBuild", "derivedObject", "claimReference", "claimElf",
            "targetConfig", "buildConfig", "nativeProbeSource", "nativeProbeObject", "nativeProbeCommand", "derivedNativeProbe")) {
            val identity = report.getValue("provenance").jsonObject.getValue(key).jsonObject
            check(identity.getValue("bytes").jsonPrimitive.long > 0 &&
                identity.getValue("sha256").jsonPrimitive.content.matches(Regex("[0-9a-f]{64}"))) { "ULTRA_INIT_TASK_PROFILE_PROVENANCE:$key" }
        }
        return report to hash
    }

    private fun sessionFacts(template: UltraInitBootProducer.Artifact, module: Module): UltraKernelManagerClaimPayload.SessionAbiFacts {
        val base = template.targetBoot.parentFile.canonicalFile
        val manifestFile = File(base, "manifest.json")
        val manifest = Json.parseToJsonElement(manifestFile.readText()).jsonObject
        check(manifest.getValue("bridgeSha256").jsonPrimitive.content == template.bridgeSha256) {
            "ULTRA_INIT_TEMPLATE_SESSION_MODULE"
        }
        fun retained(field: String): Pair<JsonObject, String> {
            val path = manifest.getValue(field).jsonPrimitive.content
            check(path.matches(Regex("[A-Za-z0-9_.-]+"))) { "ULTRA_INIT_TEMPLATE_SESSION_PATH" }
            val file = File(base, path).canonicalFile
            check(file.parentFile == base && file.length() in 1L..1048576L) { "ULTRA_INIT_TEMPLATE_SESSION_FILE" }
            val hash = UltraKernelSpec.sha256(file)
            check(hash == manifest.getValue(field + "Sha256").jsonPrimitive.content) {
                "ULTRA_INIT_TEMPLATE_SESSION_CHANGED:$field"
            }
            return Json.parseToJsonElement(file.readText()).jsonObject to hash
        }
        val (report, reportHash) = retained("sessionAbi")
        val (sources, _) = retained("bundleSources")
        val proof = report.getValue("bridgeProvenance").jsonObject
        check(proof.getValue("schemaVersion").jsonPrimitive.int == 1 &&
            proof.getValue("method").jsonPrimitive.content in setOf("same-tu-object-derived-btf", "same-final-module-derived-btf") &&
            proof.getValue("originalModuleWritten").jsonPrimitive.content == "false" &&
            report.getValue("bundledModuleSha256").jsonPrimitive.content == template.bridgeSha256) {
            "ULTRA_INIT_TEMPLATE_SESSION_PROVENANCE"
        }
        for (field in listOf("bridge", "sessionObject", "sessionCommand", "sourceSelection", "strictBuild", "derivedObject", "claimBuilder")) {
            val identity = proof.getValue(field).jsonObject
            check(identity.getValue("bytes").jsonPrimitive.long > 0 &&
                identity.getValue("sha256").jsonPrimitive.content.matches(Regex("[0-9a-f]{64}"))) {
                "ULTRA_INIT_TEMPLATE_SESSION_SOURCE:$field"
            }
        }
        val inputs = sources.getValue("inputs").jsonObject
        val bridge = inputs.getValue("bridge").jsonObject
        val abi = inputs.getValue("session_abi").jsonObject
        check(bridge.getValue("sha256").jsonPrimitive.content == proof.getValue("bridge").jsonObject.getValue("sha256").jsonPrimitive.content &&
            bridge.getValue("bytes").jsonPrimitive.long == proof.getValue("bridge").jsonObject.getValue("bytes").jsonPrimitive.long &&
            bridge.getValue("bundledSha256").jsonPrimitive.content == template.bridgeSha256 &&
            bridge.getValue("bundledBytes").jsonPrimitive.int == module.bytes.size &&
            abi.getValue("bundledSha256").jsonPrimitive.content == reportHash) { "ULTRA_INIT_TEMPLATE_SESSION_BUNDLE" }
        val expected = setOf("runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/su_exec_session.c",
            "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/su_exec_session.h")
        val selected = proof.getValue("sources").jsonObject
        check(selected.keys == expected) { "ULTRA_INIT_TEMPLATE_SESSION_SOURCE_SET" }
        for ((name, element) in selected) {
            val source = element.jsonObject
            val original = source.getValue("selected").jsonObject
            check(source.getValue("bytes").jsonPrimitive.long > 0 &&
                source.getValue("bytes") == original.getValue("bytes") &&
                source.getValue("sha256") == original.getValue("sha256") &&
                source.getValue("sha256").jsonPrimitive.content.matches(Regex("[0-9a-f]{64}"))) {
                "ULTRA_INIT_TEMPLATE_SESSION_SELECTED:$name"
            }
        }
        val facts = UltraKernelManagerClaimPayload.decodeSessionAbi(report)
        module.verifySession(facts)
        return facts
    }

    private data class Export(val crc: Int, val namespace: String)
    private fun exports(kernel: ByteArray, symbols: UltraKernelSymbols, modversions: Boolean): Map<String, Export> {
        val config = configuration(kernel)
        val relative = config["CONFIG_HAVE_ARCH_PREL32_RELOCATIONS"] == "y"
        val stride = if (relative) 12 else 24
        val pointers = if (relative) null else UltraKernelImagePointers(kernel, symbols)
        fun stringAt(at: Int, nullable: Boolean = false): String {
            val offset = if (relative) Math.addExact(at, word(kernel, at)) else {
                val reader = requireNotNull(pointers)
                if (nullable && reader.isNull(at)) return ""
                reader.pointer(at) ?: error("ULTRA_INIT_TARGET_EXPORT_ADDRESS")
            }
            return cstring(kernel, offset, kernel.size)
        }
        val result = linkedMapOf<String, Export>()
        for (suffix in listOf("", "_gpl")) {
            val start = unique(symbols, "__start___ksymtab$suffix")
            val end = unique(symbols, "__stop___ksymtab$suffix")
            val crcs = if (modversions) unique(symbols, "__start___kcrctab$suffix") else 0
            val crcEnd = if (modversions) unique(symbols, "__stop___kcrctab$suffix") else 0
            check(start in 0..kernel.size && end in start..kernel.size && start % (stride / 3) == 0 &&
                (end - start) % stride == 0 && (!modversions || crcs in 0..kernel.size &&
                crcEnd in crcs..kernel.size && (end - start) / stride * 4 == crcEnd - crcs)) {
                "ULTRA_INIT_TARGET_EXPORT_BOUNDS"
            }
            for (at in start until end step stride) {
                val name = stringAt(at + stride / 3)
                val namespace = stringAt(at + stride / 3 * 2, nullable = true)
                val crcAt = crcs + (at - start) / stride * 4
                val crc = if (!modversions) 0 else if (config["CONFIG_MODULE_REL_CRCS"] == "y")
                    word(kernel, Math.addExact(crcAt, word(kernel, crcAt))) else word(kernel, crcAt)
                check(unique(symbols, "__ksymtab_$name") == at && name.matches(Regex("[A-Za-z0-9_.$]+")) &&
                    result.put(name, Export(crc, namespace)) == null) {
                    "ULTRA_INIT_TARGET_EXPORT_NAME:$name"
                }
            }
        }
        return result
    }
    private fun unprotected(kernel: ByteArray, symbols: UltraKernelSymbols): Set<String> {
        val start = unique(symbols, "gki_unprotected_symbols")
        val end = symbols.nextOffset(start) ?: error("ULTRA_INIT_TARGET_UNPROTECTED_BOUND")
        check(end > start && end - start <= 4 * 1024 * 1024) { "ULTRA_INIT_TARGET_UNPROTECTED_BOUND" }
        val values = kernel.copyOfRange(start, end).toString(Charsets.US_ASCII).split('\u0000').filter { it.isNotEmpty() }
        check(values.isNotEmpty() && values == values.distinct().sorted() &&
            values.all { it.matches(Regex("[A-Za-z0-9_.$]{1,256}")) }) { "ULTRA_INIT_TARGET_UNPROTECTED_TABLE" }
        return values.toSet()
    }
    private fun initCfi(kernel: ByteArray, symbols: UltraKernelSymbols): Int {
        val start = unique(symbols, "do_one_initcall")
        val end = symbols.nextOffset(start) ?: error("ULTRA_INIT_TARGET_INITCALL_BOUND")
        val values = linkedSetOf<Int>()
        for (at in start + 4 until end - 24 step 4) {
            val lo = word(kernel, at); val hi = word(kernel, at + 4)
            if (word(kernel, at - 4) == 0xb85fc270.toInt() &&
                (lo and 0xffe0001f.toInt()) == 0x72800011 && (hi and 0xffe0001f.toInt()) == 0x72a00011 &&
                word(kernel, at + 8) == 0x6b11021f && word(kernel, at + 12) == 0x54000040 &&
                (word(kernel, at + 16) and 0xffe0001f.toInt()) == 0xd4200000.toInt() &&
                word(kernel, at + 20) == 0xd63f0260.toInt()) values +=
                ((lo ushr 5) and 65535) or (((hi ushr 5) and 65535) shl 16)
        }
        return values.singleOrNull() ?: error("ULTRA_INIT_TARGET_INITCALL_CFI")
    }
    private fun unique(symbols: UltraKernelSymbols, name: String): Int =
        symbols.offsetsOf(name).singleOrNull() ?: error("ULTRA_INIT_TARGET_SYMBOL:$name")
    private fun word(data: ByteArray, at: Int): Int {
        check(at >= 0 && at.toLong() + 4 <= data.size) { "ULTRA_INIT_BINARY_BOUNDS" }
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(at)
    }
    private fun cstring(data: ByteArray, at: Int, limit: Int): String {
        check(at >= 0 && at < limit && limit <= data.size) { "ULTRA_INIT_STRING_BOUNDS" }
        var end = at
        while (end < limit && data[end] != 0.toByte()) {
            check(data[end].toInt() in 32..126 && end - at < 512) { "ULTRA_INIT_STRING_ENCODING" }; end++
        }
        check(end < limit) { "ULTRA_INIT_STRING_UNTERMINATED" }
        return data.copyOfRange(at, end).toString(Charsets.US_ASCII)
    }
    private fun configBytes(kernel: ByteArray): ByteArray {
        fun find(marker: String, from: Int): Int {
            val bytes = marker.toByteArray(Charsets.US_ASCII)
            for (at in from..kernel.size - bytes.size) if (bytes.indices.all { bytes[it] == kernel[at + it] }) return at
            return -1
        }
        val begin = find("IKCFG_ST", 0)
        check(begin >= 0 && find("IKCFG_ST", begin + 1) < 0) { "ULTRA_INIT_TARGET_CONFIG_REQUIRED" }
        val end = find("IKCFG_ED", begin + 8)
        check(end > begin + 8 && end - begin <= 1024 * 1024) { "ULTRA_INIT_TARGET_CONFIG_BOUNDS" }
        return ByteArrayOutputStream().also { output ->
            GZIPInputStream(ByteArrayInputStream(kernel, begin + 8, end - begin - 8)).use { input ->
                val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break
                    check(output.size() + count <= 4 * 1024 * 1024) { "ULTRA_INIT_TARGET_CONFIG_SIZE" }
                    output.write(buffer, 0, count)
                }
            }
        }.toByteArray()
    }
    private fun configuration(kernel: ByteArray): Map<String, String> = configBytes(kernel).toString(Charsets.US_ASCII)
        .lineSequence().filter { it.startsWith("CONFIG_") && '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    private fun pageBytes(kernel: ByteArray, config: Map<String, String>): Int {
        val flags = ByteBuffer.wrap(kernel).order(ByteOrder.LITTLE_ENDIAN).getLong(24)
        val code = ((flags ushr 1) and 3).toInt()
        val configured = listOf(4, 16, 64).filter { config["CONFIG_ARM64_${it}K_PAGES"] == "y" }
            .singleOrNull()?.times(1024) ?: error("ULTRA_INIT_TARGET_PAGE_CONFIG")
        val header = when (code) { 0 -> configured; 1 -> 4096; 2 -> 16384; else -> 65536 }
        check(header == configured) { "ULTRA_INIT_TARGET_PAGE_MISMATCH" }; return header
    }
    private fun vermagic(kernel: ByteArray, release: String): String {
        val needle = "$release ".toByteArray(Charsets.US_ASCII); val found = linkedSetOf<String>()
        for (at in 0..kernel.size - needle.size) if (needle.indices.all { kernel[at + it] == needle[it] }) {
            runCatching { cstring(kernel, at, minOf(kernel.size, at + 512)) }.getOrNull()?.let {
                if (it.endsWith(" aarch64")) found += it
            }
        }
        return found.singleOrNull() ?: error("ULTRA_INIT_TARGET_VERMAGIC")
    }

    private class Module(source: ByteArray) {
        var bytes = source.clone(); private set
        private val elf get() = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private data class Section(val index: Int, val header: Int, val name: String, val type: Int,
            val flags: Long, val offset: Int, val size: Int, val link: Int, val info: Int, val entry: Int, val address: Long)
        private data class Symbol(val name: String, val section: Int, val binding: Int,
            val type: Int, val value: Long, val size: Long)
        private val sections: List<Section>
        private val symbols: List<Symbol>
        init {
            check(bytes.size >= 64 && bytes.copyOfRange(0, 7).contentEquals(byteArrayOf(127,69,76,70,2,1,1)) &&
                elf.getShort(16).toInt() == 1 && elf.getShort(18).toInt() == 183) { "ULTRA_INIT_TEMPLATE_ELF" }
            val table = bounded(elf.getLong(40), 0); val count = elf.getShort(60).toInt() and 65535
            val namesIndex = elf.getShort(62).toInt() and 65535
            check(elf.getShort(58).toInt() == 64 && count > 0 && namesIndex in 0 until count) { "ULTRA_INIT_TEMPLATE_SECTIONS" }
            bounded(table.toLong(), count.toLong() * 64)
            val stringsHeader = table + namesIndex * 64
            val strings = bounded(elf.getLong(stringsHeader + 24), elf.getLong(stringsHeader + 32))
            val stringLimit = strings + elf.getLong(stringsHeader + 32).toInt()
            sections = (0 until count).map { index ->
                val at = table + index * 64; val type = elf.getInt(at + 4)
                val offset = if (type == 8) 0 else bounded(elf.getLong(at + 24), elf.getLong(at + 32))
                check(elf.getLong(at + 32) in 0..Int.MAX_VALUE.toLong() && elf.getLong(at + 56) in 0..Int.MAX_VALUE.toLong()) {
                    "ULTRA_INIT_TEMPLATE_SECTION_SIZE"
                }
                Section(index, at, cstring(bytes, strings + elf.getInt(at), stringLimit), type, elf.getLong(at + 8),
                    offset, elf.getLong(at + 32).toInt(), elf.getInt(at + 40), elf.getInt(at + 44), elf.getLong(at + 56).toInt(), elf.getLong(at + 16))
            }
            val symtab = section(".symtab")
            check(symtab.type == 2 && symtab.entry == 24 && symtab.size % 24 == 0 && symtab.link in sections.indices) {
                "ULTRA_INIT_TEMPLATE_SYMBOLS"
            }
            val stringsSection = sections[symtab.link]
            check(stringsSection.type == 3) { "ULTRA_INIT_TEMPLATE_SYMBOL_STRINGS" }
            symbols = (0 until symtab.size / 24).map { index ->
                val at = symtab.offset + index * 24
                Symbol(cstring(bytes, stringsSection.offset + elf.getInt(at), stringsSection.offset + stringsSection.size),
                    elf.getShort(at + 6).toInt() and 65535, (bytes[at + 4].toInt() and 255) ushr 4,
                    bytes[at + 4].toInt() and 15, elf.getLong(at + 8), elf.getLong(at + 16))
            }
        }
        private fun bounded(at: Long, size: Long): Int {
            check(at >= 0 && size >= 0 && at <= bytes.size && size <= bytes.size - at) { "ULTRA_INIT_TEMPLATE_BOUNDS" }; return at.toInt()
        }
        private fun section(name: String): Section = sections.filter { it.name == name }.singleOrNull()
            ?: error("ULTRA_INIT_TEMPLATE_SECTION:$name")
        fun hasTaskProfile(): Boolean = sections.any { it.name.startsWith(".msu.task_profile.") } ||
            symbols.any { it.name == "msu_task_profile_target" || it.name == "msu_task_profile_marker_target" }

        private fun profileSymbol(name: String, kind: Int): Triple<Symbol, Section, Int> {
            val symbol = symbols.filter { it.name == name }.singleOrNull()
                ?: error("ULTRA_INIT_TASK_PROFILE_SYMBOL:$name")
            check(symbol.type == kind && symbol.section in 1 until sections.size && symbol.size > 0) {
                "ULTRA_INIT_TASK_PROFILE_SYMBOL_TYPE:$name"
            }
            val section = sections[symbol.section]
            val offset = symbol.value - section.address
            check(section.type == 1 && offset >= 0 && symbol.size <= section.size &&
                offset <= section.size - symbol.size && offset % 4 == 0L) { "ULTRA_INIT_TASK_PROFILE_SYMBOL_BOUNDS:$name" }
            return Triple(symbol, section, section.offset + offset.toInt())
        }

        fun verifyLegacyTaskProfile(implementation: UltraKernelTaskProfileTarget.Implementation) {
            val legacy = requireNotNull(implementation.legacyCfi) { "ULTRA_INIT_TASK_PROFILE_LEGACY_PROOF_REQUIRED" }
            check(legacy["mode"]?.jsonPrimitive?.content == "legacy-lto-cfi" &&
                legacy["relocationOwner"]?.jsonPrimitive?.content == "normal-kernel-module-loader" &&
                listOf("moduleLoaded", "runtimeOwnerQualified", "deviceQualified").all {
                    legacy[it]?.jsonPrimitive?.booleanOrNull == false
                }) { "ULTRA_INIT_TASK_PROFILE_LEGACY_OWNER" }
            val (owner, ownerSection, _) = profileSymbol("__this_module", 1)
            check(owner.size > 0 && ownerSection.name == ".gnu.linkonce.this_module") { "ULTRA_INIT_TASK_PROFILE_LEGACY_MODULE" }
            val checker = legacy.getValue("checker").jsonObject
            val (checkSymbol, checkSection, checkAt) = profileSymbol("__cfi_check", 2)
            check(checkSection.flags and 4L != 0L && checker["section"]?.jsonPrimitive?.content == checkSection.name &&
                checker["offset"]?.jsonPrimitive?.longOrNull == checkSymbol.value &&
                checker["bytes"]?.jsonPrimitive?.longOrNull == checkSymbol.size &&
                checker["sha256"]?.jsonPrimitive?.content == UltraKernelSpec.sha256(bytes.copyOfRange(checkAt, checkAt + checkSymbol.size.toInt()))) {
                "ULTRA_INIT_TASK_PROFILE_LEGACY_CHECKER_CHANGED"
            }
            val normal = legacy.getValue("normalModule").jsonObject
            check(normal["mode"]?.jsonPrimitive?.content == "legacy-lto-cfi" &&
                listOf("moduleLoaded", "runtimeOwnerQualified", "deviceQualified").all {
                    normal[it]?.jsonPrimitive?.booleanOrNull == false
                } && normal.getValue("members").jsonArray.map { it.jsonObject.getValue("symbol").jsonPrimitive.content }.toSet() ==
                    setOf("init_module", "msu_legacy_kernel_probe_work")) { "ULTRA_INIT_TASK_PROFILE_LEGACY_NORMAL_ROUTE" }
            val members = legacy.getValue("members").jsonObject
            check(members.keys == setOf("msu_task_profile_sys_enter", "msu_task_profile_marker_entry", "msu_task_profile_marker_return")) {
                "ULTRA_INIT_TASK_PROFILE_LEGACY_MEMBERS"
            }
            fun location(value: JsonObject): Pair<Section, Int> {
                val index = value.getValue("sectionIndex").jsonPrimitive.int
                check(index in 1 until sections.size) { "ULTRA_INIT_TASK_PROFILE_LEGACY_SECTION" }
                val code = sections[index]
                val offset = value.getValue("offset").jsonPrimitive.int
                check(code.flags and 4L != 0L && offset >= 0 && offset % 4 == 0 && offset <= code.size - 4 &&
                    value["section"]?.jsonPrimitive?.content == code.name &&
                    value["fileOffset"]?.jsonPrimitive?.intOrNull == code.offset + offset) { "ULTRA_INIT_TASK_PROFILE_LEGACY_LOCATION" }
                return code to offset
            }
            for ((name, element) in members) {
                val member = element.jsonObject
                check(member["symbol"]?.jsonPrimitive?.content == name &&
                    member["typeIdHex"]?.jsonPrimitive?.content?.matches(Regex("[0-9a-f]{16}")) == true &&
                    member["proof"]?.jsonPrimitive?.content == "actual-relocated-module-checker-path" &&
                    member["runtimeOwnerQualified"]?.jsonPrimitive?.booleanOrNull == false) { "ULTRA_INIT_TASK_PROFILE_LEGACY_TYPE64:$name" }
                val canonical = member.getValue("canonical").jsonObject
                val (code, offset) = location(canonical)
                val prefix = word(bytes, code.offset + offset)
                val branchOffset = offset + if (prefix == 0xd503245f.toInt() || prefix == 0xd50324df.toInt()) 4 else 0
                val (branchCode, branchAt) = location(canonical.getValue("branch").jsonObject)
                check(code.index == branchCode.index && branchAt == branchOffset) { "ULTRA_INIT_TASK_PROFILE_LEGACY_BRANCH_LOCATION:$name" }
                val branch = word(bytes, code.offset + branchOffset)
                check(branch and 0xfc000000.toInt() == 0x14000000) { "ULTRA_INIT_TASK_PROFILE_LEGACY_CANONICAL:$name" }
                val (body, bodyOffset) = location(canonical.getValue("body").jsonObject)
                val relocations = sections.filter { it.type == 4 && it.info == code.index }.flatMap { relocation ->
                    check(relocation.link == section(".symtab").index && relocation.entry == 24 && relocation.size % 24 == 0) {
                        "ULTRA_INIT_TASK_PROFILE_LEGACY_BRANCH_RELOCATIONS:$name"
                    }
                    (relocation.offset until relocation.offset + relocation.size step 24).filter {
                        elf.getLong(it) == branchOffset.toLong() + code.address
                    }
                }
                check(relocations.size <= 1) { "ULTRA_INIT_TASK_PROFILE_LEGACY_BRANCH_OVERLAP:$name" }
                if (relocations.isEmpty()) check(body.index == code.index &&
                    branchOffset.toLong() + (branch shl 6 shr 4).toLong() == bodyOffset.toLong()) {
                    "ULTRA_INIT_TASK_PROFILE_LEGACY_CANONICAL_BODY:$name"
                } else {
                    val at = relocations.single()
                    val info = elf.getLong(at + 8)
                    val index = info ushr 32
                    check(info and 0xffffffffL == 282L && index < symbols.size) { "ULTRA_INIT_TASK_PROFILE_LEGACY_JUMP26:$name" }
                    val target = symbols[index.toInt()]
                    check(target.section == body.index && Math.subtractExact(Math.addExact(target.value, elf.getLong(at + 16)), body.address) == bodyOffset.toLong()) {
                        "ULTRA_INIT_TASK_PROFILE_LEGACY_CANONICAL_BODY:$name"
                    }
                }
            }
        }

        fun verifyTaskProfile(implementation: UltraKernelTaskProfileTarget.Implementation, mode: String,
            report: JsonObject?, bound: UltraKernelTaskProfileTarget.Proof?) {
            check(implementation.present && mode in setOf("none", "kcfi", "legacy-lto-cfi")) { "ULTRA_INIT_TASK_PROFILE_IMPLEMENTATION" }
            if (mode == "legacy-lto-cfi") verifyLegacyTaskProfile(implementation)
            report?.let {
                check(it.getValue("cfiPolicy").jsonObject.getValue("mode").jsonPrimitive.content == mode &&
                    it.getValue("cfiPolicy").jsonObject.getValue("normalizeIntegers").jsonPrimitive.boolean == implementation.normalizeIntegers &&
                    it.getValue("functions").jsonObject.keys == implementation.entries.keys) { "ULTRA_INIT_TASK_PROFILE_CFI_POLICY" }
            }
            for ((name, expected) in implementation.entries) {
                val (symbol, code, at) = profileSymbol(name, 2)
                check(code.flags and 4L != 0L) { "ULTRA_INIT_TASK_PROFILE_FUNCTION_CODE:$name" }
                if (mode == "kcfi") check(at >= code.offset + 4 && word(bytes, at - 4) == expected.typeid) {
                    "ULTRA_INIT_TASK_PROFILE_FUNCTION_KCFI:$name"
                }
                report?.getValue("functions")?.jsonObject?.getValue(name)?.jsonObject?.let { fact ->
                    check(fact.getValue("offset").jsonPrimitive.long == symbol.value &&
                        fact.getValue("bytes").jsonPrimitive.long == symbol.size && fact.getValue("section").jsonPrimitive.content == code.name &&
                        fact.getValue("signature").jsonPrimitive.content.removePrefix("*") == implementation.signatures.getValue(name) &&
                        fact.getValue("sha256").jsonPrimitive.content == UltraKernelSpec.sha256(bytes.copyOfRange(at, at + symbol.size.toInt())) &&
                        (mode != "kcfi" || fact.getValue("typeid").jsonPrimitive.long.toInt() == expected.typeid)) {
                        "ULTRA_INIT_TASK_PROFILE_FUNCTION_FACT:$name"
                    }
                }
            }
            for (name in listOf("msu_task_profile_sys_enter", "msu_task_profile_marker_entry", "msu_task_profile_marker_return")) {
                val (pointer, storage, at) = profileSymbol(name + "_type", 1)
                check(pointer.size == 8L && (storage.flags and 7L) == 3L) { "ULTRA_INIT_TASK_PROFILE_CALLBACK_STORAGE:$name" }
                val relocations = mutableListOf<Pair<Section, Int>>()
                for (relocation in sections.filter { it.type == 4 && it.info == pointer.section }) {
                    check(relocation.link == section(".symtab").index && relocation.entry == 24 && relocation.size % 24 == 0) {
                        "ULTRA_INIT_TASK_PROFILE_CALLBACK_RELOCATIONS:$name"
                    }
                    for (entry in relocation.offset until relocation.offset + relocation.size step 24) {
                        val offset = elf.getLong(entry)
                        if (offset >= pointer.value && offset < pointer.value + pointer.size) relocations += relocation to entry
                    }
                }
                check(relocations.size == 1) { "ULTRA_INIT_TASK_PROFILE_CALLBACK_RELOCATION_COUNT:$name" }
                val (relocation, entry) = relocations.single()
                val info = elf.getLong(entry + 8)
                val index = info ushr 32
                check(elf.getLong(entry) == pointer.value && (info and 0xffffffffL) == 257L && index < symbols.size) {
                    "ULTRA_INIT_TASK_PROFILE_CALLBACK_ABS64:$name"
                }
                if (mode == "legacy-lto-cfi") {
                    val target = symbols[index.toInt()]
                    val canonical = requireNotNull(implementation.legacyCfi).getValue("members").jsonObject.getValue(name)
                        .jsonObject.getValue("canonical").jsonObject
                    check(target.section in 1 until sections.size && target.section == canonical.getValue("sectionIndex").jsonPrimitive.int &&
                        Math.subtractExact(Math.addExact(target.value, elf.getLong(entry + 16)), sections[target.section].address) == canonical.getValue("offset").jsonPrimitive.long) {
                        "ULTRA_INIT_TASK_PROFILE_CALLBACK_CANONICAL:$name"
                    }
                } else check(elf.getLong(entry + 16) == 0L && symbols[index.toInt()].name == name) {
                    "ULTRA_INIT_TASK_PROFILE_CALLBACK_ABS64:$name"
                }
                report?.getValue("callbacks")?.jsonObject?.getValue(name)?.jsonObject?.let { fact ->
                    val reference = fact.getValue("relocation").jsonObject
                    check(fact.getValue("symbol").jsonPrimitive.content == pointer.name &&
                        fact.getValue("signature").jsonPrimitive.content.removePrefix("*") == implementation.signatures.getValue(name) &&
                        fact.getValue("section").jsonPrimitive.content == storage.name &&
                        fact.getValue("offset").jsonPrimitive.long == pointer.value && fact.getValue("fileOffset").jsonPrimitive.int == at &&
                        reference.getValue("section").jsonPrimitive.content == relocation.name &&
                        reference.getValue("offset").jsonPrimitive.long == pointer.value && reference.getValue("type").jsonPrimitive.int == 257 &&
                        reference.getValue("addend").jsonPrimitive.long == elf.getLong(entry + 16) &&
                        reference.getValue("symbol").jsonPrimitive.content == symbols[index.toInt()].name) {
                        "ULTRA_INIT_TASK_PROFILE_CALLBACK_FACT:$name"
                    }
                }
            }
            for (table in listOf(implementation.trace, implementation.target, implementation.marker)) {
                val (symbol, storage, at) = profileSymbol(table.symbol, 1)
                check(storage.name == table.section && storage.size == table.words.size * 4 &&
                    symbol.size == storage.size.toLong() && at == storage.offset &&
                    (storage.flags and 7L) == (if (table.writable) 3L else 2L)) { "ULTRA_INIT_TASK_PROFILE_TABLE_STORAGE:${table.symbol}" }
                val actual = bytes.copyOfRange(at, at + storage.size)
                val expected = when {
                    bound == null || !table.writable -> ByteBuffer.allocate(table.words.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                        .apply { table.words.forEach { putInt(it) } }.array()
                    table.symbol == implementation.target.symbol -> UltraKernelTaskProfileTarget.targetBytes(bound)
                    else -> UltraKernelTaskProfileTarget.markerBytes(bound)
                }
                check(actual.contentEquals(expected)) { "ULTRA_INIT_TASK_PROFILE_TABLE_BYTES:${table.symbol}" }
                report?.getValue("tables")?.jsonObject?.getValue(table.symbol)?.jsonObject?.let { fact ->
                    check(fact.getValue("section").jsonPrimitive.content == storage.name &&
                        fact.getValue("offset").jsonPrimitive.long == symbol.value &&
                        fact.getValue("fileOffset").jsonPrimitive.int == at && fact.getValue("bytes").jsonPrimitive.int == storage.size &&
                        fact.getValue("sha256").jsonPrimitive.content == UltraKernelSpec.sha256(actual)) {
                        "ULTRA_INIT_TASK_PROFILE_TABLE_FACT:${table.symbol}"
                    }
                }
            }
            val ranges = listOf(implementation.trace, implementation.target, implementation.marker).map { table ->
                val (_, storage, at) = profileSymbol(table.symbol, 1)
                at until at + storage.size
            }.sortedBy { it.first }
            check(ranges.zipWithNext().all { (left, right) -> left.last < right.first }) { "ULTRA_INIT_TASK_PROFILE_TABLE_OVERLAP" }
            for (name in implementation.entries.keys) {
                val (symbol, _, at) = profileSymbol(name, 2)
                val from = at - if (mode == "kcfi") 4 else 0
                check(ranges.none { from <= it.last && at + symbol.size > it.first }) {
                    "ULTRA_INIT_TASK_PROFILE_CODE_TABLE_OVERLAP:$name"
                }
            }
        }

        fun bindTaskProfile(implementation: UltraKernelTaskProfileTarget.Implementation,
            proof: UltraKernelTaskProfileTarget.Proof, mode: String) {
            // Both complete table byte arrays exist before modifying the private module copy.
            val target = UltraKernelTaskProfileTarget.targetBytes(proof)
            val marker = UltraKernelTaskProfileTarget.markerBytes(proof)
            val targetAt = profileSymbol(implementation.target.symbol, 1).third
            val markerAt = profileSymbol(implementation.marker.symbol, 1).third
            target.copyInto(bytes, targetAt); marker.copyInto(bytes, markerAt)
            verifyTaskProfile(implementation, mode, null, proof)
        }

        fun hasSession(): Boolean = sections.any { it.name == ".msu.session.abi" } || symbols.any {
            it.name == "msu_su_session_abi_words" || it.name.startsWith("msu_su_exec_session_")
        }
        fun verifySession(facts: UltraKernelManagerClaimPayload.SessionAbiFacts) {
            fun symbol(name: String, type: Int): Triple<Symbol, Section, Int> {
                val symbol = symbols.filter { it.name == name }.singleOrNull()
                    ?: error("ULTRA_INIT_TEMPLATE_SESSION_SYMBOL:$name")
                check(symbol.type == type && symbol.section in sections.indices && symbol.section > 0) {
                    "ULTRA_INIT_TEMPLATE_SESSION_SYMBOL_TYPE:$name"
                }
                val section = sections[symbol.section]
                val at = symbol.value - section.address
                check(section.type == 1 && at >= 0 && symbol.size > 0 &&
                    symbol.size <= section.size && at <= section.size - symbol.size && symbol.value % 4 == 0L) {
                    "ULTRA_INIT_TEMPLATE_SESSION_SYMBOL_BOUNDS:$name"
                }
                return Triple(symbol, section, section.offset + at.toInt())
            }
            val (marker, data, start) = symbol("msu_su_session_abi_words", 1)
            check(data.name == ".msu.session.abi" && (data.flags and 7L) == 2L &&
                marker.value == facts.offset.toLong() && marker.size == facts.bytes.toLong() &&
                UltraKernelSpec.sha256(bytes.copyOfRange(start, start + facts.bytes)) == facts.compiledSha256 &&
                facts.words.indices.all { word(bytes, start + it * 4) == facts.words[it] }) {
                "ULTRA_INIT_TEMPLATE_SESSION_MARKER"
            }
            for ((name, callback) in facts.callbacks) {
                val (entry, code, at) = symbol(name, 2)
                check((code.flags and 4L) != 0L && entry.value == callback.offset.toLong()) {
                    "ULTRA_INIT_TEMPLATE_SESSION_CALLBACK_ENTRY:$name"
                }
                if (facts.cfiMode == "kcfi") check(at >= code.offset + 4 &&
                    word(bytes, at - 4) == callback.kcfiPrefix && callback.kcfiPrefix == callback.objectKcfiType) {
                    "ULTRA_INIT_TEMPLATE_SESSION_CALLBACK_KCFI:$name"
                }
            }
        }
        fun imports(): Set<String> = symbols.filter { it.name.isNotEmpty() && it.section == 0 && it.binding != 2 }.map { it.name }.toSet()
        fun legacyDirectImports(legacy: Boolean): Set<String> = legacyDirectCalls(legacy).imports
        fun legacyDirectCalls(legacy: Boolean): DirectCalls {
            val markers = sections.filter { it.name == ".msu_direct_calls" }
            if (markers.isEmpty()) {
                check(symbols.none { it.name.startsWith(directAliasPrefix) }) { "ULTRA_INIT_LEGACY_DIRECT_UNDECLARED" }
                return DirectCalls(0, emptyMap(), null)
            }
            val v1Names = listOf("file_open_root", "getname_kernel", "groups_alloc", "putname")
            val v1Encoded = ("MAPLESU_DIRECT_V1\u0000" + v1Names.joinToString("\u0000") + "\u0000\u0000")
                .toByteArray(Charsets.US_ASCII)
            val marker = markers.singleOrNull()
            check(legacy && marker != null && marker.type == 1) {
                "ULTRA_INIT_LEGACY_DIRECT_MARKER"
            }
            val encoded = bytes.copyOfRange(marker.offset, marker.offset + marker.size)
            val version = if (encoded.contentEquals(v1Encoded)) 1 else 2
            val targetsByImport = if (version == 1) v1Names.associateWith { it } else {
                val fields = encoded.toString(Charsets.US_ASCII).split('\u0000')
                check(encoded.all { it.toInt() in 0..127 } && fields.size >= 5 &&
                    fields.first() == "MAPLESU_DIRECT_V2" && fields.takeLast(2) == listOf("", "") &&
                    (fields.size - 3) % 2 == 0) { "ULTRA_INIT_LEGACY_DIRECT_MARKER" }
                val pairs = fields.drop(1).dropLast(2).chunked(2).map { it[0] to it[1] }
                val targets = pairs.map { it.second }
                check(targets.distinct().size == targets.size && targets == targets.sorted() &&
                    pairs.all { (alias, target) -> target in directV2Targets && alias == directAliasPrefix + target }) {
                    "ULTRA_INIT_LEGACY_DIRECT_MARKER"
                }
                pairs.toMap()
            }
            val names = targetsByImport.keys
            check(symbols.none { it.name.startsWith(directAliasPrefix) && it.name !in names }) {
                "ULTRA_INIT_LEGACY_DIRECT_UNDECLARED"
            }
            val symtab = sections.filter { it.type == 2 }.singleOrNull()
            check(symtab != null && symtab.name == ".symtab") { "ULTRA_INIT_LEGACY_DIRECT_SYMBOL_TABLE" }
            val selected = linkedMapOf<Int, String>()
            val seen = mutableSetOf<String>()
            symbols.forEachIndexed { index, symbol ->
                if (symbol.name in names) {
                    check(seen.add(symbol.name) && symbol.section == 0 && symbol.binding == 1 && symbol.type in setOf(0, 2)) {
                        "ULTRA_INIT_LEGACY_DIRECT_SYMBOL:${symbol.name}"
                    }
                    selected[index] = symbol.name
                }
            }
            if (version == 2) check(seen == names) { "ULTRA_INIT_LEGACY_DIRECT_SYMBOL_REQUIRED" }
            val calls = selected.values.associateWith { 0 }.toMutableMap()
            if (version == 2) check(sections.none { it.type in setOf(4, 9) && it.link != symtab.index }) {
                "ULTRA_INIT_LEGACY_DIRECT_RELOCATIONS"
            }
            for (relocation in sections.filter { it.type in setOf(4, 9) && it.link == symtab.index }) {
                check(relocation.type == 4 && relocation.entry == 24 && relocation.size % 24 == 0 &&
                    relocation.info in sections.indices) { "ULTRA_INIT_LEGACY_DIRECT_RELOCATIONS" }
                val code = sections[relocation.info]
                for (at in relocation.offset until relocation.offset + relocation.size step 24) {
                    val info = elf.getLong(at + 8)
                    val name = if ((info ushr 32) < symbols.size.toLong()) selected[(info ushr 32).toInt()] else null
                    if (name == null) continue
                    val kind = info and 0xffffffffL
                    val offset = elf.getLong(at)
                    check(kind in setOf(282L, 283L) && elf.getLong(at + 16) == 0L && code.type == 1 &&
                        code.flags and 4L != 0L && offset >= 0 && offset % 4 == 0L && offset <= code.size.toLong() - 4) {
                        "ULTRA_INIT_LEGACY_DIRECT_RELOCATION:$name"
                    }
                    check(word(bytes, code.offset + offset.toInt()) and 0xfc000000.toInt() ==
                        if (kind == 283L) 0x94000000.toInt() else 0x14000000) { "ULTRA_INIT_LEGACY_DIRECT_OPCODE:$name" }
                    calls[name] = calls.getValue(name) + 1
                }
            }
            check(calls.isNotEmpty() && calls.values.all { it > 0 }) { "ULTRA_INIT_LEGACY_DIRECT_CALL_REQUIRED" }
            return DirectCalls(version, if (version == 2) targetsByImport else calls.keys.associateWith { it },
                UltraKernelSpec.sha256(encoded))
        }
        fun put32(at: Int, value: Int) { bounded(at.toLong(), 4); elf.putInt(at, value) }
        private fun tags(section: Section): List<String> {
            val values = mutableListOf<String>(); var at = section.offset
            while (at < section.offset + section.size) { val value = cstring(bytes, at, section.offset + section.size); at += value.length + 1
                if (value.isNotEmpty()) values += value }
            return values
        }
        fun tags(): List<String> = tags(section(".modinfo"))
        fun versions(): Map<String, Pair<Int, Int>> {
            val output = linkedMapOf<String, Pair<Int, Int>>()
            sections.singleOrNull { it.name == "__versions" }?.let { versions ->
                check(versions.type == 1 && versions.size % 64 == 0) { "ULTRA_INIT_TEMPLATE_VERSIONS" }
                for (at in versions.offset until versions.offset + versions.size step 64) {
                    check(elf.getInt(at + 4) == 0) { "ULTRA_INIT_TEMPLATE_CRC_WIDTH" }
                    val name = cstring(bytes, at + 8, at + 64)
                    check(name.isNotEmpty() && output.put(name, at to word(bytes, at)) == null) { "ULTRA_INIT_TEMPLATE_VERSION_DUPLICATE" }
                }
            }
            val crcs = sections.singleOrNull { it.name == "__version_ext_crcs" }
            val names = sections.singleOrNull { it.name == "__version_ext_names" }
            check((crcs == null) == (names == null)) { "ULTRA_INIT_TEMPLATE_EXT_VERSIONS" }
            if (crcs != null && names != null) {
                val labels = tags(names)
                check(crcs.type == 1 && crcs.size == labels.size * 4) { "ULTRA_INIT_TEMPLATE_EXT_VERSION_COUNTS" }
                labels.forEachIndexed { index, name ->
                    val at = crcs.offset + index * 4
                    check(output.put(name, at to word(bytes, at)) == null) { "ULTRA_INIT_TEMPLATE_VERSION_DUPLICATE" }
                }
            }
            return output
        }
        fun bindRuntime(kernel: ByteArray, symbols: UltraKernelSymbols) {
            val table = section(".msu.runtime.layout"); val labelsSection = section(".msu.runtime.names")
            check(table.type == 1 && (table.flags and 3L) == 3L && table.size >= 24 &&
                word(bytes, table.offset) == 0x4d53554c && word(bytes, table.offset + 4) == 1 &&
                word(bytes, table.offset + 8) == table.size && word(bytes, table.offset + 16) in 0..1 &&
                word(bytes, table.offset + 20) == 0) { "ULTRA_INIT_TEMPLATE_RUNTIME_HEADER" }
            val labels = tags(labelsSection)
            check(labels.size == word(bytes, table.offset + 12) && labels.distinct().size == labels.size &&
                table.size == 24 + labels.size * 12) { "ULTRA_INIT_TEMPLATE_RUNTIME_NAMES" }
            val alignments = section(".msu.runtime.alignment")
            check(alignments.type == 1 && alignments.size == labels.size * 4) { "ULTRA_INIT_TEMPLATE_RUNTIME_ALIGNMENT" }
            val aliases = if (sections.none { it.name == ".msu.runtime.aliases" }) emptyMap() else {
                val alias = section(".msu.runtime.aliases")
                val encoded = "vfsmount.mnt_user_ns=vfsmount.mnt_userns\u0000\u0000".toByteArray(Charsets.US_ASCII)
                check(alias.type == 1 && (alias.flags and 3L) == 2L && alias.size == encoded.size &&
                    bytes.copyOfRange(alias.offset, alias.offset + alias.size).contentEquals(encoded) &&
                    "vfsmount.mnt_user_ns" in labels) { "ULTRA_INIT_TEMPLATE_RUNTIME_ALIASES" }
                mapOf("vfsmount.mnt_user_ns" to "vfsmount.mnt_userns")
            }
            val active = labels.filterIndexed { index, _ -> word(bytes, table.offset + 24 + index * 12) != -1 }
                .associateWith { aliases[it] ?: it }
            val target = UltraKernelBtf.abi(kernel, symbols, active.values.toSet()) ?: error("ULTRA_INIT_TARGET_BTF_REQUIRED")
            put32(table.offset + 16, 0)
            labels.forEachIndexed { index, name ->
                val at = table.offset + 24 + index * 12; val width = word(bytes, at + 4)
                val alignment = word(bytes, alignments.offset + index * 4)
                if (word(bytes, at) == -1) check(width == 0 && word(bytes, at + 8) == 0 && alignment == 0) {
                    "ULTRA_INIT_TEMPLATE_ABSENT_SLOT:$name"
                } else {
                    val field = target.fields.getValue(active.getValue(name)); val owner = requireNotNull(field.ownerSizeBytes)
                    check(field.widthBytes == width && field.offsetBytes >= 0 && field.offsetBytes <= owner - width &&
                        alignment > 0 && (alignment and (alignment - 1)) == 0 && field.offsetBytes % alignment == 0) {
                        "ULTRA_INIT_TARGET_RUNTIME_WIDTH:$name"
                    }
                    put32(at, field.offsetBytes); put32(at + 8, owner)
                }
            }
            put32(table.offset + 16, 1)
        }
        fun bindSdk(kernel: ByteArray, symbols: UltraKernelSymbols, config: Map<String, String>, page: Int) {
            val table = section(".msu.init_bridge.runtime_layout"); val at = table.offset
            check(table.type == 1 && (table.flags and 3L) == 3L && table.size == 92 && word(bytes, at) == 0x4d534942 &&
                word(bytes, at + 4) == 1 && word(bytes, at + 8) == 92 && word(bytes, at + 12) in 0..1 &&
                word(bytes, at + 16) == 1 && word(bytes, at + 20) == 76) { "ULTRA_INIT_TEMPLATE_SDK_HEADER" }
            val fields = UltraKernelBtf.abi(kernel, symbols, setOf("task_struct.cred", "cred.suid", "cred.securebits", "cred.usage",
                "kprobe.addr", "kprobe.symbol_name", "kprobe.pre_handler", "pt_regs.pc") +
                if (config["CONFIG_SECCOMP"] == "y") setOf("task_struct.seccomp.mode") else emptySet())
                ?: error("ULTRA_INIT_TARGET_SDK_BTF")
            fun field(name: String) = fields.fields.getValue(name)
            fun set(index: Int, value: Int) = put32(at + 16 + index * 4, value)
            set(2, page); set(3, requireNotNull(field("task_struct.cred").ownerSizeBytes)); set(4, requireNotNull(field("cred.usage").ownerSizeBytes))
            for ((name, index) in mapOf("task_struct.cred" to 5, "cred.suid" to 6, "cred.securebits" to 7, "cred.usage" to 8,
                "kprobe.addr" to 11, "kprobe.symbol_name" to 12, "kprobe.pre_handler" to 13, "pt_regs.pc" to 14)) set(index, field(name).offsetBytes)
            set(9, field("cred.usage").widthBytes); set(10, requireNotNull(field("kprobe.addr").ownerSizeBytes))
            check(field("cred.securebits").offsetBytes == field("cred.suid").offsetBytes + 24) { "ULTRA_INIT_TARGET_SAVED_ID_WINDOW" }
            set(18, if (config["CONFIG_SECCOMP"] == "y") field("task_struct.seccomp.mode").offsetBytes else 0)
            put32(at + 12, 1)
        }
        fun verifyCompiledNativeLayouts(evidence: JsonObject) {
            for ((name, key) in mapOf(".msu.runtime.layout" to "runtimeTableSha256",
                ".msu.init_bridge.runtime_layout" to "sdkTableSha256", ".msu.su-runtime.profiles" to "profileTableSha256")) {
                val table = section(name)
                check(table.type == 1 && table.flags and 3L == 3L && table.size >= 24 &&
                    evidence[key]?.jsonPrimitive?.content == UltraKernelSpec.sha256(bytes.copyOfRange(table.offset, table.offset + table.size))) {
                    "ULTRA_INIT_TEMPLATE_KMI_TABLE:$name"
                }
                val magic = when (name) {
                    ".msu.runtime.layout" -> 0x4d53554c
                    ".msu.init_bridge.runtime_layout" -> 0x4d534942
                    else -> 0x4d535550
                }
                val ready = if (name == ".msu.init_bridge.runtime_layout") 12 else 16
                check(word(bytes, table.offset) == magic && word(bytes, table.offset + 4) == 1 &&
                    word(bytes, table.offset + 8) == table.size && word(bytes, table.offset + ready) == 1) {
                    "ULTRA_INIT_TEMPLATE_KMI_TABLE_HEADER:$name"
                }
            }
        }
        fun bindProfiles(source: ByteArray, sourceSymbols: UltraKernelSymbols, target: ByteArray,
                         targetSymbols: UltraKernelSymbols, loaderAbi: Boolean = false) {
            val table = section(".msu.su-runtime.profiles"); val at = table.offset
            check(table.type == 1 && (table.flags and 3L) == 3L && table.size == 372 &&
                word(bytes, at) == 0x4d535550 && word(bytes, at + 4) == 1 &&
                word(bytes, at + 8) == 372 && word(bytes, at + 12) == 5 &&
                word(bytes, at + 16) in 0..1 && word(bytes, at + 20) in 0..1 && word(bytes, at + 28) == 0) {
                "ULTRA_INIT_TEMPLATE_PROFILE_HEADER"
            }
            val cfi = word(bytes, at + 20) == 1
            val available = profileNames.filter { targetSymbols.offsetsOf(it).isNotEmpty() }.toSet()
            check(profileNames.take(3).all { it in available }) { "ULTRA_INIT_TARGET_PROFILE_REQUIRED" }
            val sourceAvailable = profileNames.filter { sourceSymbols.offsetsOf(it).isNotEmpty() }.toSet()
            val sourceSignatures = if (loaderAbi) emptyMap() else UltraKernelBtf.signatures(source, sourceSymbols, sourceAvailable, emptySet())
                ?: error("ULTRA_INIT_TEMPLATE_BTF_REQUIRED")
            val targetSignatures = if (loaderAbi) emptyMap() else UltraKernelBtf.signatures(target, targetSymbols, available, emptySet())
                ?: error("ULTRA_INIT_TARGET_BTF_REQUIRED")
            val filenameOffset = if (loaderAbi) word(bytes, at + 24).also {
                check(it >= 0 && it % 8 == 0) { "ULTRA_INIT_TARGET_FILENAME_ABI" }
            } else {
                val filename = UltraKernelBtf.abi(target, targetSymbols, setOf("filename.name"))
                    ?.fields?.getValue("filename.name") ?: error("ULTRA_INIT_TARGET_FILENAME_REQUIRED")
                check(filename.widthBytes == 8 && filename.offsetBytes % 8 == 0) { "ULTRA_INIT_TARGET_FILENAME_ABI" }
                filename.offsetBytes
            }
            put32(at + 16, 0); put32(at + 24, filenameOffset)
            profileNames.forEachIndexed { index, name ->
                val entry = at + 32 + index * 68
                val encoded = name.toByteArray(Charsets.US_ASCII)
                check((0 until 40).all { bytes[entry + it] == if (it < encoded.size) encoded[it] else 0.toByte() }) {
                    "ULTRA_INIT_TEMPLATE_PROFILE_NAME"
                }
                if (name !in available) {
                    for (word in 0 until 7) put32(entry + 40 + word * 4, 0)
                } else {
                    if (!loaderAbi) {
                        val expected = sourceSignatures[name] ?: sourceSignatures.getValue(profileNames[1])
                        check(targetSignatures.getValue(name) == expected) { "ULTRA_INIT_TARGET_PROFILE_ABI:$name" }
                    }
                    val targetAt = unique(targetSymbols, name)
                    check(targetAt >= 4 && targetAt % 4 == 0 && targetAt + 24 <= target.size) {
                        "ULTRA_INIT_TARGET_PROFILE_BOUNDS:$name"
                    }
                    put32(entry + 40, if (index > 0 && cfi) word(target, targetAt - 4) else 0)
                    for (instruction in 0 until 6) put32(entry + 44 + instruction * 4, word(target, targetAt + instruction * 4))
                }
            }
            put32(at + 16, 1)
        }
        fun verifyOrSetVermagic(target: String, hasVersions: Boolean) {
            val info = section(".modinfo"); val values = tags(info)
            val magic = values.filter { it.startsWith("vermagic=") }.singleOrNull()?.substring(9)
                ?: error("ULTRA_INIT_TEMPLATE_VERMAGIC")
            if (hasVersions) {
                check(magic.substringAfter(' ', "") == target.substringAfter(' ', "")) { "ULTRA_INIT_TARGET_VERMAGIC_ABI" }
                return
            }
            val updated = values.map { if (it.startsWith("vermagic=")) "vermagic=$target" else it }
                .joinToString("\u0000", postfix = "\u0000").toByteArray(Charsets.US_ASCII)
            val offset = (bytes.size + 7) and -8
            bytes = bytes.copyOf(Math.addExact(offset, updated.size)); updated.copyInto(bytes, offset)
            elf.putLong(info.header + 24, offset.toLong()); elf.putLong(info.header + 32, updated.size.toLong())
        }
    }
}
