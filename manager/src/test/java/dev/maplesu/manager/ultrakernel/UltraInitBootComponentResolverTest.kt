package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.UltraInitBootProducer
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UltraInitBootComponentResolverTest {
    @get:Rule val temporary = TemporaryFolder()

    @Suppress("UNCHECKED_CAST")
    @Test fun componentBuildClassificationDoesNotRequireAPairedKernel() {
        val helpers = Class.forName("dev.maplesu.image.UltraInitBootProducerTest")
        val files = helpers.getDeclaredMethod("kmiBundle", String::class.java)
            .apply { isAccessible = true }
            .invoke(null, "6.6.127-4k-g46a034eca005-dirty") as Map<String, ByteArray>
        val directory = temporary.newFolder()
        files.forEach { (path, bytes) -> File(directory, path).writeBytes(bytes) }
        val artifact = UltraInitBootProducer.readArtifact(File(directory, "manifest.json"))
        val input = File(directory, "selected-init-boot.img")
        val baseline = "selected input without a paired kernel".toByteArray()
        input.writeBytes(baseline)
        assertSame(artifact, UltraInitBootTargetResolver.resolve(artifact, input))
        assertArrayEquals(baseline, input.readBytes())
    }
}
