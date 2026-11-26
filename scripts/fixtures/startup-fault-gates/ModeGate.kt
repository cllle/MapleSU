package dev.maplesu.manager.workmode.gating
import dev.maplesu.manager.runtime.ultra.Fixture
object ModeComponentIds { const val APP_ULTRA_AUTOPAIR = "startup" }
object ModeGateFormat { enum class Reason { NOT_APPLICABLE } }
object ModeGate {
    fun allow(id: String) { Fixture.gate = "allow" }
    fun skip(id: String, reason: ModeGateFormat.Reason) { Fixture.gate = "skip" }
}
