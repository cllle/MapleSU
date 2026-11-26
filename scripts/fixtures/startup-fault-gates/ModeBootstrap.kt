package dev.maplesu.manager.workmode.boot
import dev.maplesu.manager.runtime.ultra.Fixture
object ModeBootstrap { fun retryEnvironmentDetection() { Fixture.calls += "retry-detection" } }
