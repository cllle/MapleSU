package dev.maplesu.manager.ultrakernel

import android.content.Context
import dev.maplesu.image.BootImageInspector
import dev.maplesu.image.KernelCompression
import dev.maplesu.image.UltraInitBootProducer
import java.io.File
import java.io.RandomAccessFile

/**
 * Builds from the target kernel's own symbols, layout and generated instructions when an explicit
 * credential is available. Recommended assets do not decide whether that target can be generated.
 * A receipt-bound generated image is restored through the existing recovery path before generation.
 * Explicit asset selections and calls without a credential retain the legacy asset contract.
 *
 * This class never opens a partition.
 */
internal class UltraKernelFlow internal constructor(
    private val workspace: File,
    private val assets: UltraKernelAssetSource,
    private val vivoVrFilter: Boolean = false,
    private val managerClaimAssets: UltraKernelAssetSource? = null,
    private val moduleArtifact: (File) -> UltraInitBootProducer.Artifact? = { null },
) {
    internal constructor(context: Context) : this(
        dev.maplesu.manager.compat.UltraStateStorage.flash(context.filesDir),
        UltraKernelContextAssets(context),
        listOf(android.os.Build.MANUFACTURER, android.os.Build.BRAND).any {
            it.equals("vivo", true) || it.equals("iqoo", true)
        },
        UltraKernelContextAssets(context),
        { source ->
            if (BootImageInspector.inspect(source).kernelSize == 0L) initBootArtifact(context)
            else {
                val kmi = dev.maplesu.image.KernelIdentity.kmiOf(UltraKernelPlan.readKernel(source))
                if (kmi.matches(Regex("android[0-9]+-[0-9]+\\.[0-9]+"))) initBootArtifact(context, kmi) else null
            }
        },
    )

    private val builder get() = UltraKernelBuilder(workspace)
    private val freshSpecGenerator by lazy {
        UltraKernelSpecGenerator(vivoVrFilter = vivoVrFilter, managerClaimAssets = managerClaimAssets)
    }
    private fun specGenerator(deployment: UltraKernelDeploymentNamespace?): UltraKernelSpecGenerator {
        if (deployment != null) synchronized(this) { selectedDeploymentNamespace = deployment }
        val retained = deployment ?: synchronized(this) { selectedDeploymentNamespace }
        return if (retained == null) freshSpecGenerator else UltraKernelSpecGenerator(
            vivoVrFilter = vivoVrFilter, managerClaimAssets = managerClaimAssets, deploymentNamespace = retained)
    }
    private var selectedDeploymentNamespace: UltraKernelDeploymentNamespace? = null

    @Synchronized
    private fun withInitBootDeployment(base: File, artifact: UltraInitBootProducer.Artifact,
        recoveredDeployment: UltraKernelDeploymentNamespace?): UltraInitBootProducer.Artifact {
        val retained = UltraInitBootProducer.readDeploymentNamespace(base)?.let(UltraKernelDeploymentNamespace::decode)
        check(retained == null || recoveredDeployment == null ||
            retained.record().contentEquals(recoveredDeployment.record())) { "ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_CONFLICT" }
        (retained ?: recoveredDeployment)?.let { selectedDeploymentNamespace = it }
        if (!artifact.supportsDeploymentNamespace) {
            check(retained != null || selectedDeploymentNamespace == null) { "ULTRA_KERNEL_DEPLOYMENT_SUPPORT_MISSING" }
            return artifact
        }
        val deployment = retained ?: selectedDeploymentNamespace ?: UltraKernelDeploymentNamespace.create().also {
            selectedDeploymentNamespace = it
        }
        return artifact.withDeploymentNamespace(deployment.record())
    }

    companion object {
        /** Current-device default; an offline image for another device supplies its selected KMI. */
        fun initBootArtifact(context: Context): UltraInitBootProducer.Artifact? {
            val kmi = dev.maplesu.image.KernelIdentity.kmiOfRelease(android.system.Os.uname().release)
            if (!kmi.matches(Regex("android[0-9]+-[0-9]+\\.[0-9]+"))) return null
            return initBootArtifact(context, kmi)
        }

        fun initBootArtifact(context: Context, pairedBoot: File): UltraInitBootProducer.Artifact? =
            UltraInitBootCatalog.resolve(context.filesDir, { path -> context.assets.open(path) }, pairedBoot,
                UltraKernelContextAssets(context))

        fun availableInitBootKmis(context: Context): List<String> =
            UltraInitBootCatalog.availableKmis(context.filesDir) { path -> context.assets.open(path) }

        fun initBootArtifact(context: Context, kmi: String): UltraInitBootProducer.Artifact? =
            UltraInitBootCatalog.resolveKmi(context.filesDir, { path -> context.assets.open(path) }, kmi)

        /** Compare installed payload and parameter bytes, never the generic bundle's absent key hash. */
        fun initBootUpdateRequired(image: File, artifact: UltraInitBootProducer.Artifact,
                                   credential: UltraKernelCredential): Boolean {
            check(UltraInitBootProducer.recognizes(image)) { "ULTRA_KERNEL_INIT_BOOT_CANDIDATE_UNRECOGNIZED" }
            val ramdisk = dev.maplesu.image.RamdiskInspector.inspect(image)
            check(!ramdisk.malformed) { "ULTRA_KERNEL_INIT_BOOT_RAMDISK_INVALID" }
            val deployment = UltraInitBootProducer.readDeploymentNamespace(image)
            val parameter = artifact.credentialMode == "module-parameter"
            val bound = if (parameter) credential.withMetadataDisplacement(artifact.metadataDisplacement) else credential
            val secret = bound.copySecret()
            return try {
                val expected = if (parameter) artifact.withCredential(secret) else artifact
                val init = ramdisk.entries.singleOrNull { it.path == "init" }
                val bridge = ramdisk.entries.singleOrNull { it.path == ".maplesu_ultra/bridge.ko" }
                val identityChanged = if (parameter) {
                    val entry = ramdisk.entries.singleOrNull { it.path == ".maplesu_ultra/credential" }
                    entry == null || !entry.isRegularFile || entry.permissions() != 0x180 ||
                        entry.size != UltraKernelCredential.LENGTH.toLong() || entry.sha256 != expected.credentialSha256
                } else UltraKernelSpec.sha256(secret) != expected.credentialSha256
                init?.sha256 != expected.handoffSha256 || bridge?.sha256 != expected.bridgeSha256 || identityChanged ||
                    artifact.supportsDeploymentNamespace && deployment == null
            } finally { secret.fill(0) }
        }
    }

    /**
     * Ordered, pre-validated assets for [release]; `null` means "this kernel build has no pinned
     * asset" and keeps the legacy Lite path. A malformed asset for a release that *is* declared
     * throws (fail closed) instead of silently downgrading to another release.
     */
    fun selectForRelease(release: String): UltraKernelSelection? = select(release, loadCatalogs())

    /** Source target ids from current and older callers share the target generator. */
    fun prepareTarget(
        base: File,
        credential: UltraKernelCredential,
        variantId: String? = null,
        onNotice: (String) -> Unit = {},
    ): Preparation = prepareTarget(base, credential, variantId, null, onNotice)

    fun prepareTarget(
        base: File,
        credential: UltraKernelCredential,
        variantId: String?,
        initBootArtifact: UltraInitBootProducer.Artifact?,
        onNotice: (String) -> Unit = {},
    ): Preparation = prepareTarget(base, credential, variantId, initBootArtifact, null, onNotice)

    internal fun prepareTarget(
        base: File,
        credential: UltraKernelCredential,
        variantId: String?,
        initBootArtifact: UltraInitBootProducer.Artifact?,
        recoveredDeployment: UltraKernelDeploymentNamespace?,
        onNotice: (String) -> Unit = {},
    ): Preparation {
        requireBase(base)
        val carrier = BootImageInspector.inspect(base)
        val legacyModule = initBootArtifact?.isKmiComponent != true && carrier.kernelSize > 0L &&
            UltraKernelLegacyModuleRoute.required(UltraKernelPlan.readKernel(base))
        if (carrier.kernelSize == 0L || legacyModule || initBootArtifact?.isKmiComponent == true) {
            check(variantId == null || variantId == "generated" || carrier.kernelSize > 0L && variantId == freshSpecGenerator.recipeId) {
                "ULTRA_KERNEL_INIT_BOOT_VARIANT_UNSUPPORTED"
            }
            val artifactSource = requireNotNull(initBootArtifact ?: moduleArtifact(base)) {
                "ULTRA_KERNEL_INIT_BOOT_ARTIFACT_REQUIRED:select-KMI-and-packaged-component"
            }
            val recoveryWork = if (artifactSource.isKmiComponent && carrier.kernelSize > 0L)
                File(workspace, "component-recovery-${java.util.UUID.randomUUID()}") else null
            try {
                val recovered = recoveryWork?.let { recoverModuleBase(base, it) }
                val moduleBase = recovered?.image ?: base
                val retainedDeployment = recovered?.deploymentNamespace
                check(retainedDeployment == null || recoveredDeployment == null ||
                    retainedDeployment.record().contentEquals(recoveredDeployment.record())) {
                    "ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_CONFLICT"
                }
                if (recovered != null) onNotice("已恢复当前 Ultra 镜像，正在准备模块桥接")
                val artifact = if (artifactSource.isKmiComponent && carrier.kernelSize == 0L) artifactSource
                else UltraInitBootTargetResolver.resolve(artifactSource,
                    if (carrier.kernelSize > 0L) moduleBase else artifactSource.targetBoot,
                    managerClaimAssets ?: assets)
                val deploymentArtifact = withInitBootDeployment(base, artifact, retainedDeployment ?: recoveredDeployment)
                val parameter = deploymentArtifact.credentialMode == "module-parameter"
                val bound = if (parameter) credential.withMetadataDisplacement(deploymentArtifact.metadataDisplacement) else credential
                val boundArtifact = if (parameter) {
                    val secret = bound.copySecret()
                    try { deploymentArtifact.withCredential(secret) } finally { secret.fill(0) }
                } else deploymentArtifact
                val candidate = builder.generateInitBoot(moduleBase, boundArtifact, bound,
                    installationInput = base, installationInputSha256 = carrier.sha256,
                    normalizationSource = recovered?.source)
                return Preparation(null, candidate, candidate.bootSha256 == candidate.baseBootSha256,
                    verification = "experimental", experimental = true, planOrigin = when {
                        artifactSource.isKmiComponent && carrier.kernelSize > 0L -> "KMI_BOOT_MODULE"
                        artifactSource.isKmiComponent -> "INIT_BOOT_KMI_COMPONENT"
                        legacyModule -> "LEGACY_CFI_MODULE"
                        else -> "INIT_BOOT_BRIDGE"
                    }, credential = bound)
            } finally { recoveryWork?.deleteRecursively() }
        }
        if (recoveredDeployment != null) synchronized(this) { selectedDeploymentNamespace = recoveredDeployment }
        if (variantId == null || variantId == "generated" || variantId == UltraKernelSpecGenerator().recipeId) {
            return prepare(base, credential, onNotice)
        }
        val selection = selectForRelease(releaseOf(base))
        return prepare(base, requireNotNull(withChosenVariant(selection, variantId, onNotice)),
            { credential }, onNotice)
    }

    /**
     * Generate directly for [base] and [credential], preserving builder boundaries and read-back.
     * A null credential explicitly requests the legacy packaged-asset path.
     *
     * @throws UltraKernelCredentialRequiredException when the release has no asset directory and no
     *         credential was supplied; the message names the release, every known release, and the
     *         fact that a 48 byte Ultra root key is what is missing.
     * @throws UltraKernelReleaseUnsupportedException (supertype) for callers written before the
     *         generator existed.
     */
    fun prepare(
        base: File,
        credential: UltraKernelCredential?,
        onNotice: (String) -> Unit = {},
    ): Preparation {
        requireBase(base)
        if (credential != null && BootImageInspector.inspect(base).kernelSize == 0L)
            return prepareTarget(base, credential, "generated", null, onNotice)
        val release = releaseOf(base)
        check(release.isNotEmpty()) { "ULTRA_KERNEL_BASE_RELEASE_UNREADABLE" }
        if (credential != null) {
            if (UltraKernelLegacyModuleRoute.required(UltraKernelPlan.readKernel(base)))
                return prepareTarget(base, credential, "generated", null, onNotice)
            prepareRetainedGenerated(base, { credential }, onNotice)?.let { return it }
            return prepareGenerated(base, release, credential, onNotice)
        }
        val catalogs = loadCatalogs()
        val selection = select(release, catalogs)
        if (selection != null) return prepare(base, selection, { credential }, onNotice)
        throw UltraKernelCredentialRequiredException(release, knownReleases(catalogs))
    }

    /**
     * Preserve the selected variant and every asset gate before considering local generation.
     * A different complete base of the same series must use the
     * generator. A selected asset's digest failure and an explicit k21 choice keep their failures;
     * an unrelated rejected asset does not disable valid k22 generation or identity binding.
     * A valid k22 asset is also rebound to the explicitly prepared device identity.
     */
    fun prepare(
        base: File,
        selection: UltraKernelSelection,
        credentials: () -> UltraKernelCredential?,
        onNotice: (String) -> Unit,
    ): Preparation {
        selection.rejected.forEach { onNotice("已跳过无效内置补丁：$it") }
        if (selection.usable.any { it.id == UltraKernelSpecGenerator().recipeId }) {
            prepareRetainedGenerated(base, credentials, onNotice)?.let { return it }
        }
        return try {
            prepareAssets(base, selection, onNotice, credentials)
        } catch (error: TemplateBaseMismatch) {
            if (selection.usable.none { it.id == UltraKernelSpecGenerator().recipeId }) throw error
            val pristine = error.base
            val release = releaseOf(pristine)
            onNotice("内置补丁的完整基座与当前内核不同，按当前镜像独立生成补丁")
            prepareGenerated(pristine, release, credentials(), onNotice, deploymentNamespace = error.deploymentNamespace)
        }
    }

    private fun prepareRetainedGenerated(base: File, credentials: () -> UltraKernelCredential?,
        onNotice: (String) -> Unit): Preparation? {
        val recovery = UltraKernelOriginalRecovery(assets)
        val recovered = recovery.recover(base, workspace, emptyList(),
            File(workspace, "generated-recovery"), generatedOnly = true)
            ?: recovery.recoverPinned(base, File(workspace, "pinned-recovery")) ?: return null
        onNotice("已识别当前 Ultra 镜像，正在准备更新")
        return prepareGenerated(recovered.image, recovered.kernelRelease, credentials(),
            onNotice, installed = base, deploymentNamespace = recovered.deploymentNamespace)
    }

    private fun recoverModuleBase(base: File, output: File): UltraKernelOriginalRecovery.Recovered? {
        val recovery = UltraKernelOriginalRecovery(assets)
        fun restore(input: File, directory: File): UltraKernelOriginalRecovery.Recovered? {
            if (!UltraInitBootProducer.recognizes(input))
                recovery.recoverImageBoundExactInput(input, directory)?.let { return it }
            return recovery.recover(input, workspace, emptyList(), directory)
                ?: recovery.recover(input, workspace, emptyList(), directory, generatedOnly = true)
        }
        val restored = restore(base, File(output, "carrier")) ?: return null
        if (!UltraInitBootProducer.recognizes(base)) return restored
        // A module's first recorded input can itself be an older raw patch.
        val raw = restore(restored.image, File(output, "raw")) ?: return restored
        check(restored.deploymentNamespace == null || raw.deploymentNamespace == null ||
            restored.deploymentNamespace.record().contentEquals(raw.deploymentNamespace.record())) {
            "ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_CONFLICT"
        }
        return UltraKernelOriginalRecovery.Recovered(raw.image, raw.source, raw.kernelRelease,
            raw.deploymentNamespace ?: restored.deploymentNamespace)
    }

    /**
     * Generate a plan for [base] without building anything.
     *
     * Exists so a caller can show the origin, the site evidence and the derived digests *before*
     * deciding to write a 100 MiB candidate. The result is always
     * [UltraKernelPlan.SupportLevel.EXPERIMENTAL]; nothing here writes a partition.
     */
    fun generatePlan(base: File, credential: UltraKernelCredential): UltraKernelPlan {
        requireBase(base)
        val recovery = UltraKernelOriginalRecovery(assets)
        val recovered = recovery.generatedKernelWithDeployment(base, workspace)
        val raw = recovered?.kernel ?: UltraKernelPlan.readKernel(base)
        return specGenerator(recovered?.deploymentNamespace).generate(raw, credential)
    }

    /**
     * Generate for this target and send the result through the existing builder.
     */
    private fun prepareGenerated(
        base: File,
        release: String,
        credential: UltraKernelCredential?,
        onNotice: (String) -> Unit,
        installed: File? = null,
        deploymentNamespace: UltraKernelDeploymentNamespace? = null,
    ): Preparation {
        if (credential == null) {
            // Not a downgrade and not a random key: the credential is the device's Ultra root key,
            // and a kernel built for a different one would accept nobody.
            throw UltraKernelCredentialRequiredException(release, emptyList())
        }
        onNotice("正在根据目标内核生成补丁：$release")
        val kernel = UltraKernelPlan.readKernel(base)
        val plan = specGenerator(deploymentNamespace).generate(kernel, credential)
        val boundCredential = requireNotNull(plan.credential)
        val evidence = plan.evidence
        evidence?.let { evidence ->
            onNotice(
                "已生成 ${plan.records.size} 条记录，站点与参考构建一致 ${evidence.summary}" +
                    if (evidence.referenceMatch) "" else "（该 release 的代码体未经验证）",
            )
        }
        plan.derived?.let { derived ->
            onNotice("内核摘要现算：${derived.patchedKernelSha256.take(16)}…，改动 ${derived.changedBytes} B")
        }
        val report = BootImageInspector.inspect(base)
        val specText = plan.specText(boundCredential)
        val asset = UltraKernelGeneratedAsset.of(
            plan = plan,
            imageSizeBytes = report.fileSize,
            ramdiskSize = report.ramdiskSize,
            specText = specText,
        )
        val spec = UltraKernelSpec.parse(specText)
        val inspection = builder.inspectBase(installed ?: base, asset, spec)
        if (inspection.kernelSha256 == asset.expectedKernelSha256 || inspection.alreadyPatched) {
            return Preparation(
                asset, null, true,
                planOrigin = UltraKernelPlan.Origin.GENERATED.name,
                siteMatch = evidence?.summary.orEmpty(),
                referenceMatch = evidence?.referenceMatch ?: false,
                credential = boundCredential,
            )
        }
        return Preparation(
            asset, builder.generate(base, asset, spec), false,
            planOrigin = UltraKernelPlan.Origin.GENERATED.name,
            siteMatch = evidence?.summary.orEmpty(),
            referenceMatch = evidence?.referenceMatch ?: false,
            credential = boundCredential,
        )
    }

    /**
     * Release-indexed entry point: identify [base]'s kernel release, select that release's
     * assets and build the first one that passes every gate. Asset-only; see the overload taking a
     * credential for the generation path.
     *
     * @throws UltraKernelCredentialRequiredException when the release has no asset directory and no
     *         legacy variant.
     */
    fun prepare(base: File, onNotice: (String) -> Unit = {}): Preparation =
        prepare(base, null, onNotice)

    /** Kernel release of a boot image, read without touching any partition. */
    fun releaseOf(image: File): String {
        val report = BootImageInspector.inspect(image)
        check(report.kernelSize in 1L..KernelCompression.MAX_KERNEL_BYTES.toLong()) {
            "ULTRA_KERNEL_BASE_NO_KERNEL"
        }
        val container = ByteArray(report.kernelSize.toInt())
        RandomAccessFile(image, "r").use { input ->
            input.seek(report.kernelOffset)
            input.readFully(container)
        }
        val raw = KernelCompression.decode(container, report.compression, KernelCompression.MAX_KERNEL_BYTES)
        return UltraKernelSpec.releaseOf(raw)
    }

    /** Byte-exact copy of the boot image the installer service just dumped (`images/imported.img`). */
    fun backupCurrentBoot(): File {
        val filesDir = workspace.parentFile ?: File(".")
        val imported = File(File(filesDir, "images"), "imported.img")
        check(imported.isFile) { "ULTRA_KERNEL_BASE_UNAVAILABLE" }
        check(workspace.isDirectory || workspace.mkdirs()) { "ULTRA_KERNEL_WORKSPACE" }
        val backup = File(workspace, "boot-current.img")
        val pending = File(workspace, "boot-current.pending")
        imported.inputStream().use { input ->
            pending.outputStream().use { output ->
                input.copyTo(output, 64 * 1024)
                output.fd.sync()
            }
        }
        check(pending.renameTo(backup)) { "ULTRA_KERNEL_BACKUP_FAILED" }
        return backup
    }

    fun inspect(base: File, asset: UltraKernelPatchAsset): UltraKernelBuilder.BaseInspection =
        builder.inspectBase(base, asset, specFor(asset))

    fun build(base: File, asset: UltraKernelPatchAsset): UltraKernelBuilder.Candidate =
        builder.generate(base, asset, specFor(asset))

    /** An already-patched source still needs committing when migrating from another root. */
    fun candidateForInstall(base: File, preparation: Preparation): UltraKernelBuilder.Candidate {
        preparation.candidate?.let { return it }
        check(preparation.alreadyInstalled) { "ULTRA_KERNEL_CANDIDATE_MISSING" }
        val asset = requireNotNull(preparation.variant)
        val spec = if (asset is UltraKernelGeneratedAsset) {
            UltraKernelSpec.parse(asset.plan.specText(requireNotNull(preparation.credential)))
        } else specFor(asset)
        val inspection = builder.inspectBase(base, asset, spec)
        check(inspection.alreadyPatched || inspection.kernelSha256 == asset.expectedKernelSha256) {
            "ULTRA_KERNEL_CANDIDATE_CHANGED"
        }
        return UltraKernelBuilder.Candidate(
            image = base, bootSha256 = inspection.bootSha256, baseBootSha256 = inspection.bootSha256,
            kernelSha256 = inspection.kernelSha256, kernelRelease = inspection.kernelRelease,
            patches = asset.patches, differingBytes = 0,
            knownImage = inspection.bootSha256 == asset.knownBaseImageBootSha256,
            verification = asset.verification, source = asset.source, strictBase = false,
            expectedPinned = asset.generated == null && inspection.kernelSha256 == asset.expectedKernelSha256,
        )
    }

    /**
     * Picks the first asset from [selection] whose base is pristine and whose build passes every
     * pinned digest. Returns [Preparation.alreadyInstalled] when the base already carries the
     * asset's patched kernel; throws with all collected reasons when nothing can be written.
     *
     * [build] only writes into the app's private workspace, so trying the fallback after a failed
     * default build cannot touch `boot`.
     */
    fun prepare(
        base: File,
        selection: UltraKernelSelection,
        onNotice: (String) -> Unit,
    ): Preparation = prepareAssets(base, selection, onNotice)

    private fun prepareAssets(
        base: File,
        selection: UltraKernelSelection,
        onNotice: (String) -> Unit,
        credentials: (() -> UltraKernelCredential?)? = null,
        deploymentNamespace: UltraKernelDeploymentNamespace? = null,
    ): Preparation {
        requireBase(base)
        val deployment = deploymentNamespace ?: synchronized(this) { selectedDeploymentNamespace }
        if (deployment != null) synchronized(this) { selectedDeploymentNamespace = deployment }
        val reasons = ArrayList(selection.rejected)
        var templateMismatches = 0
        var first = true
        for (asset in selection.usable) {
            if (!first) onNotice("推荐内核校验未通过，已回退到「${asset.label}」")
            first = false
            asset.warning?.let(onNotice)
            var bindingIdentity = false
            try {
                check(deployment == null || asset.id == UltraKernelSpecGenerator().recipeId) {
                    "ULTRA_KERNEL_DEPLOYMENT_SUPPORT_MISSING"
                }
                val spec = specFor(asset)
                val inspection = builder.inspectBase(base, asset, spec)
                if (inspection.kernelSha256 == asset.expectedKernelSha256 || inspection.alreadyPatched) {
                    val embedded = UltraKernelCredential.of(spec.key)
                    bindingIdentity = asset.id == UltraKernelSpecGenerator().recipeId && credentials != null
                    val requested = if (bindingIdentity) credentials?.invoke() else null
                    if (requested != null && requested != embedded || vivoVrFilter ||
                        bindingIdentity && managerClaimAssets != null || deployment != null) {
                        val original = UltraKernelOriginalRecovery(assets).recover(base, workspace,
                            listOf(File(workspace, "base-stock.img")), File(workspace, "identity-recovery"))
                            ?: error("ULTRA_KERNEL_ORIGINAL_REQUIRED")
                        return prepareGenerated(original.image, original.kernelRelease, requested ?: embedded,
                            onNotice, installed = base, deploymentNamespace = original.deploymentNamespace ?: deployment)
                    }
                    return Preparation(asset, null, true, credential = embedded)
                }
                if (!KernelSeries.same(inspection.kernelRelease, asset.kernelRelease)) {
                    reasons += "${asset.label}：基座内核 release ${inspection.kernelRelease.ifEmpty { "unreadable" }}" +
                        " 与资产 ${asset.kernelRelease} 不同系列（${KernelSeries.detail(inspection.kernelRelease, asset.kernelRelease)}）"
                    continue
                }
                val crossRelease = KernelSeries.isCrossRelease(
                    inspection.kernelRelease,
                    asset.kernelRelease,
                )
                if (asset.baseKernelSha256.isNotEmpty() && !inspection.baseKernelPinned) {
                    reasons += "${asset.label}：ULTRA_KERNEL_TEMPLATE_BASE_MISMATCH:${asset.id}"
                    templateMismatches++
                    continue
                }
                if (crossRelease) {
                    onNotice(
                        "基座内核 ${inspection.kernelRelease} 与资产 ${asset.kernelRelease} 同属 " +
                            "${KernelSeries.of(inspection.kernelRelease)} 系列，继续校验补丁原字节、" +
                            "完整产物摘要与容器回读",
                    )
                }

                if (!inspection.pristine) {
                    reasons += "${asset.label}：内核基线与补丁记录不一致（${inspection.mismatchedRecords} 条）"
                    if (crossRelease) templateMismatches++
                    continue
                }
                // Verify the complete packaged output before rebinding a supported recipe.
                val candidate = builder.generate(base, asset, spec)
                val embedded = UltraKernelCredential.of(spec.key)
                bindingIdentity = asset.id == UltraKernelSpecGenerator().recipeId && credentials != null
                val requested = if (bindingIdentity) credentials?.invoke() else null
                if (requested != null && requested != embedded || vivoVrFilter ||
                    bindingIdentity && managerClaimAssets != null || deployment != null) {
                    onNotice("内置补丁校验通过，按本机安装身份生成独立候选")
                    return prepareGenerated(base, inspection.kernelRelease, requested ?: embedded, onNotice,
                        deploymentNamespace = deployment)
                }
                return Preparation(asset, candidate, false, credential = embedded)
            } catch (error: Exception) {
                if (bindingIdentity) throw error
                reasons += "${asset.label}：${error.message.orEmpty()}"
            }
        }
        // Older releases could capture a patched kernel as a ramdisk-clean "original".
        // Restore only a pinned or receipt-proven preimage, then apply the current asset.
        UltraKernelOriginalRecovery(assets)
            .recover(base, workspace, listOf(File(workspace, "base-stock.img")),
                File(workspace, "original-recovery"))?.let { recovered ->
            onNotice("识别到旧版 Ultra 修补镜像，已校验并恢复原始字节，再生成当前内核（原备份保留）")
            return prepareAssets(recovered.image, selection, onNotice, credentials, recovered.deploymentNamespace ?: deployment)
        }
        val message = "ULTRA_KERNEL_ALL_VARIANTS_FAILED:${reasons.joinToString(" | ")}"
        if (selection.usable.isNotEmpty() &&
            templateMismatches == selection.usable.size) {
            throw TemplateBaseMismatch(message, base, deployment)
        }
        throw IllegalStateException(message)
    }

    private class TemplateBaseMismatch(message: String, val base: File,
        val deploymentNamespace: UltraKernelDeploymentNamespace?) : IllegalStateException(message)

    internal class Preparation(
        val variant: UltraKernelPatchAsset?,
        val candidate: UltraKernelBuilder.Candidate?,
        val alreadyInstalled: Boolean,
        val verification: String = variant?.verification.orEmpty(),
        val warning: String? = variant?.warning,
        val experimental: Boolean = variant?.experimental ?: false,
        /** `GENERATED` when this preparation came from the on-device recipe renderer, else `""`. */
        val planOrigin: String = "",
        /** `"18/18"`: records that still carried the reference build's pristine bytes. */
        val siteMatch: String = "",
        /** True only when every site matched the reference build; never promotes [supportLevel]. */
        val referenceMatch: Boolean = false,
        /** Identity embedded in this candidate. Memory-only; never included in logs or receipts. */
        val credential: UltraKernelCredential? = null,
    ) {
        /** True when no shipped asset produced this candidate; the UI must warn unconditionally. */
        val generated: Boolean get() = planOrigin == UltraKernelPlan.Origin.GENERATED.name

        /**
         * What this preparation may claim. A generated one is `EXPERIMENTAL` even when
         * [referenceMatch] is true - matching a reference build is not the same as having booted.
         */
        val supportLevel: String
            get() = if (generated || planOrigin == "LEGACY_CFI_MODULE" || candidate?.partition == "init_boot") {
                UltraKernelPlan.SupportLevel.EXPERIMENTAL.name
            } else {
                UltraKernelPlan.SupportLevel.ASSET_DECIDES.name
            }
    }

    private class Catalogs(
        val releases: UltraKernelReleaseCatalog,
        val legacy: UltraKernelCatalog,
    )

    private fun loadCatalogs(): Catalogs = Catalogs(
        releases = UltraKernelReleaseCatalog.load(assets),
        legacy = UltraKernelCatalog.loadOrEmpty(assets),
    )

    private fun knownReleases(catalogs: Catalogs): List<String> =
        (catalogs.releases.knownReleases() + catalogs.legacy.knownReleases()).distinct().sorted()

    /**
     * Resolves [release] to the assets of its kernel **series** (`6.6.50` -> the `6.6` assets),
     * legacy manifest last, and `null` when the series ships nothing (the caller may generate).
     *
     * A shipped asset is authoritative and is never refused because of its series: the tier list
     * below only guards the **generation hand-off**, i.e. the case where nothing exists to try.
     * There, and only there, two refusals happen - both explicit, neither a "support level":
     *
     *  - a release that does not parse as `digits.digits…` (empty, `AMBIGUOUS`) is refused with
     *    `ULTRA_KERNEL_RELEASE_UNSUPPORTED`, because no series can be derived and guessing one would
     *    be the silent downgrade this class exists to prevent;
     *  - a parsed series the recipe has no tier for (`6.7`, `7.0`) is refused with
     *    `ULTRA_KERNEL_SERIES_MISMATCH:<release>:known=[…]` - the user's rule is "同系列不同 release
     *    才套用，未知系列不许猜".
     */
    private fun select(release: String, catalogs: Catalogs): UltraKernelSelection? {
        val releaseAssets = catalogs.releases.assetsFor(release)
        if (releaseAssets.isNotEmpty()) {
            val selection = selectUsable(releaseAssets) { asset ->
                try {
                    loadReleaseSpec(assets, asset)
                    null
                } catch (error: Exception) {
                    error.message ?: error.javaClass.simpleName
                }
            }
            check(selection.usable.isNotEmpty()) {
                "ULTRA_KERNEL_NO_USABLE_ASSET:$release:${selection.rejected.joinToString(" | ")}"
            }
            return selection
        }
        // A declared-but-broken release directory must not fall through to the legacy manifest:
        // that would silently ship a different patch set than the one reviewed for this release.
        catalogs.releases.rejection(release)?.let { reason ->
            throw IllegalStateException("ULTRA_KERNEL_RELEASE_ASSET_INVALID:$release:$reason")
        }

        val ordered = catalogs.legacy.orderedForRelease(release)
        if (ordered.isNotEmpty()) {
            val selection = selectUsable(ordered) { variant ->
                try {
                    UltraKernelCatalog.spec(assets, variant)
                    null
                } catch (error: Exception) {
                    error.message ?: error.javaClass.simpleName
                }
            }
            check(selection.usable.isNotEmpty()) {
                "ULTRA_KERNEL_NO_USABLE_VARIANT:${selection.rejected.joinToString(" | ")}"
            }
            return selection
        }
        // Nothing to try for this release: the caller's next step would be on-device generation.
        // Refuse a series nobody has rendered instead of letting the recipe guess at it.
        if (KernelSeries.of(release) == null) {
            throw UltraKernelReleaseUnsupportedException(
                release.trim().ifEmpty { KernelSeries.UNREADABLE },
                knownReleases(catalogs),
            )
        }
        KernelSeries.requireSupported(release)
        return null
    }

    private fun specFor(asset: UltraKernelPatchAsset): UltraKernelSpec = when (asset) {
        is UltraKernelReleaseAsset -> loadReleaseSpec(assets, asset)
        is UltraKernelVariant -> UltraKernelCatalog.spec(assets, asset)
        else -> throw IllegalStateException("ULTRA_KERNEL_ASSET_UNKNOWN:${asset.id}")
    }

    private fun requireBase(base: File) {
        check(base.isFile && base.length() in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) {
            "ULTRA_KERNEL_BASE_INVALID"
        }
    }
}

/** Preserve only an explicitly chosen historical asset at the external compatibility boundary. */
internal fun withChosenVariant(
    selection: UltraKernelSelection?,
    variantId: String?,
    onNotice: (String) -> Unit,
): UltraKernelSelection? {
    if (variantId == null) return selection
    val chosen = selection?.usable?.firstOrNull { it.id == variantId }
        ?: error("ULTRA_KERNEL_VARIANT_UNAVAILABLE:$variantId")
    onNotice("已按你的选择使用「${chosen.label}」")
    return UltraKernelSelection(listOf(chosen), selection.rejected, chosen.id)
}
