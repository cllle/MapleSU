package dev.maplesu.manager.workmode

import dev.maplesu.manager.runtime.ultra.Fixture
import dev.maplesu.manager.workmode.detect.ModeState
import kotlinx.coroutines.flow.MutableStateFlow

enum class WorkMode { LITE, KSU }
class ModeActivation
data class Trace(val id: String, val ok: Boolean?)
data class Detection(val state: ModeState? = null, val trace: List<Trace> = listOf(Trace("S1", false)))
object ModeRuntime {
    var active: WorkMode? = WorkMode.LITE
    var activation = ModeActivation()
    val detectionRevision = MutableStateFlow(0L)
    var detectionComplete = true
    var detectionFailure: Throwable? = null
    var latestDetection: Detection? = Detection()
    suspend fun awaitDetectionSettled(timeout: Long) { Fixture.calls += "await-detection" }
}
