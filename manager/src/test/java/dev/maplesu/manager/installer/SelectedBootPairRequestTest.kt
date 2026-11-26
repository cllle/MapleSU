package dev.maplesu.manager.installer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Executes the pending value and predicates used by the actual picker and header callbacks. */
class SelectedBootPairRequestTest {
    private val source = "content://images/init-A"
    private val paired = "content://images/boot-B"
    private val generation = 7
    private fun recreate(request: SelectedBootPairRequest): SelectedBootPairRequest {
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(request) }
        }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as SelectedBootPairRequest }
    }

    @Test fun aRecreatedPickerRetainsTheResultUntilTheSameSourceHeaderIsInitBoot() {
        val launched = recreate(SelectedBootPairRequest(source, generation))
        val pending = requireNotNull(launched.receive(paired, source, generation, allowed = true))
        assertEquals(paired, pending.resultUri)
        assertNull(pending.selectedUri(source, generation, initBoot = false, allowed = true))
        val restoredResult = recreate(pending)
        assertEquals(paired, restoredResult.selectedUri(source, generation, initBoot = true, allowed = true))
    }

    @Test fun reselectingTheSameUriInvalidatesItsPreviousGeneration() {
        val oldRequest = SelectedBootPairRequest(source, generation)
        val nextGeneration = generation + 1
        assertFalse(oldRequest.matches(source, nextGeneration))
        assertNull(oldRequest.receive(paired, source, nextGeneration, allowed = true))
        val newRequest = SelectedBootPairRequest(source, nextGeneration)
        assertTrue(newRequest.matches(source, nextGeneration))
        assertEquals(paired, newRequest.receive(paired, source, nextGeneration, allowed = true)
            ?.selectedUri(source, nextGeneration, initBoot = true, allowed = true))
    }

    @Test fun switchingAwayAndBackToTheSameSourceRejectsTheOldPicker() {
        val request = SelectedBootPairRequest(source, generation)
        assertNull(request.receive(paired, "content://images/init-C", generation + 1, allowed = true))
        assertNull(request.receive(paired, source, generation + 2, allowed = true))
    }

    @Test fun switchingModesAwayAndBackDoesNotReviveTheOldRequest() {
        val request = SelectedBootPairRequest(source, generation)
        assertNull(request.receive(paired, source, generation + 1, allowed = false))
        assertNull(request.receive(paired, source, generation + 2, allowed = true))
    }

    @Test fun changingTheSourceWhileTheResultWaitsForAHeaderRejectsIt() {
        val pending = requireNotNull(SelectedBootPairRequest(source, generation)
            .receive(paired, source, generation, allowed = true))
        assertNull(pending.selectedUri(source, generation + 1, initBoot = true, allowed = true))
        assertNull(pending.selectedUri("content://images/init-C", generation, initBoot = true, allowed = true))
    }

    @Test fun installationOrSwitchingBlocksBothResultReceptionAndHeaderApplication() {
        val request = SelectedBootPairRequest(source, generation)
        assertNull(request.receive(paired, source, generation, allowed = false))
        val pending = requireNotNull(request.receive(paired, source, generation, allowed = true))
        assertNull(pending.selectedUri(source, generation, initBoot = true, allowed = false))
        assertEquals(paired, pending.resultUri)
    }

    @Test fun cancellingOnlyConsumesThePendingRequest() {
        val request = SelectedBootPairRequest(source, generation)
        assertNull(request.receive(null, source, generation, allowed = true))
        assertNull(request.resultUri)
    }
}
