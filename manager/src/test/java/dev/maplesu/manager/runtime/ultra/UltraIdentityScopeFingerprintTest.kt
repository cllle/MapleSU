package dev.maplesu.manager.runtime.ultra

import org.junit.Assert.*
import org.junit.Test

class UltraIdentityScopeFingerprintTest {
    private val owner = UltraRuntimeClient.ManagerOwnerIdentity(10123, 7L, "host-one")

    @Test fun ownerCacheBelongsToFullUidEpochHostBootAndActivation() {
        fun fingerprint(value: UltraRuntimeClient.ManagerOwnerIdentity? = owner,
                        boot: String? = "boot-one", activation: Int = 1) =
            UltraInstalledIdentityProbe.identityFingerprint(null, value, boot, activation)
        val first = fingerprint()
        assertNotNull(first)
        assertEquals(first, fingerprint())
        assertNotEquals(first, fingerprint(owner.copy(fullUid = 110123)))
        assertNotEquals(first, fingerprint(owner.copy(epoch = 8)))
        assertNotEquals(first, fingerprint(owner.copy(hostInstance = "host-two")))
        assertNotEquals(first, fingerprint(boot = "boot-two"))
        assertNotEquals(first, fingerprint(activation = 2))
        assertNull(fingerprint(value = null))
        assertNull(fingerprint(boot = null))
    }

    @Test fun keyCacheAlsoBelongsToOwnerBootAndActivation() {
        fun fingerprint(value: UltraRuntimeClient.ManagerOwnerIdentity? = owner,
                        boot: String? = "boot-one", activation: Int = 1) =
            UltraInstalledIdentityProbe.identityFingerprint("a".repeat(48), value, boot, activation)
        val first = fingerprint()
        assertNotNull(first)
        assertEquals(first, fingerprint())
        assertNotEquals(first, fingerprint(owner.copy(fullUid = 110123)))
        assertNotEquals(first, fingerprint(owner.copy(epoch = 8)))
        assertNotEquals(first, fingerprint(owner.copy(hostInstance = "host-two")))
        assertNotEquals(first, fingerprint(value = null))
        assertNotEquals(first, fingerprint(boot = "boot-two"))
        assertNotEquals(first, fingerprint(activation = 2))
        assertNull(fingerprint(boot = null))
    }
}
