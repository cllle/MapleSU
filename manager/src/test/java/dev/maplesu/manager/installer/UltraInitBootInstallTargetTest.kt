package dev.maplesu.manager.installer

import dev.maplesu.image.UltraInitBootProducer
import dev.maplesu.manager.workmode.Hashes
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UltraInitBootInstallTargetTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun aComponentDoesNotRequireAPairedBootOrAnExactBuildRelease() {
        val artifact = component()
        assertNull(artifact.targetBoot)
        UltraInitBootInstallTarget.requireCompatible(artifact, "6.6.89-android15-8-g01234567")
        UltraInitBootInstallTarget.requireCompatible(artifact, "6.6.110-android15-9-gabcdef01")
    }

    @Test fun aDifferentLiveKmiIsLeftToTheActualKernelLoader() {
        UltraInitBootInstallTarget.requireCompatible(component(), "6.1.99-android14-11-g01234567")
    }

    @Test fun anUnreadableLiveReleaseDoesNotVetoASelectedComponent() {
        UltraInitBootInstallTarget.requireCompatible(component(), "")
    }

    @Test fun anExplicitLegacyArtifactRetainsItsExactInputContract() {
        val boot = temporary.newFile().apply { writeText("exact legacy input") }
        val constructor = UltraInitBootProducer.Artifact::class.java.getDeclaredConstructor(
            File::class.java, String::class.java, String::class.java, String::class.java,
            String::class.java, ByteArray::class.java, ByteArray::class.java).apply { isAccessible = true }
        val artifact = constructor.newInstance(boot, Hashes.sha256(boot), "legacy", "", "", ByteArray(0), ByteArray(0))
        UltraInitBootInstallTarget.requireCompatible(artifact, "", Hashes.sha256(boot), boot.length())
        assertThrows(IllegalStateException::class.java) {
            UltraInitBootInstallTarget.requireCompatible(artifact, "", "a".repeat(64), boot.length())
        }
    }

    private fun component(): UltraInitBootProducer.Artifact {
        val identity = UltraInitBootProducer.KmiComponent::class.java.getDeclaredConstructor(
            String::class.java, String::class.java, Int::class.javaPrimitiveType, String::class.java,
            String::class.java, String::class.java).apply { isAccessible = true }
            .newInstance("android15-6.6", "arm64", 4096, "kcfi", "maplesu-runtime-facts/1", "a".repeat(64))
        return UltraInitBootProducer.Artifact::class.java.getDeclaredMethod("component",
            UltraInitBootProducer.KmiComponent::class.java, String::class.java, ByteArray::class.java,
            ByteArray::class.java, String::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(null, identity, "6.6.1-android15-1-fixture", ByteArray(0), ByteArray(0), null, 4064)
            as UltraInitBootProducer.Artifact
    }
}
