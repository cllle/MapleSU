"""Source-only regression checks for the completed-jailbreak home refresh path."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "manager/src/main/java"


def source(path):
    return (JAVA / path).read_text(encoding="utf-8-sig")


class JailbreakHomeRefreshTest(unittest.TestCase):
    def test_completed_attempt_notifies_home_before_reboot(self):
        dialog = source("dev/maplesu/manager/jailbreak/JailbreakSelectionDialog.kt")
        completion = dialog.split("val completed = withContext(Dispatchers.IO)", 1)[1]
        completion = completion.split("val restarted = withContext(Dispatchers.IO)", 1)[0]
        self.assertIn("completed.stage == JailbreakStatus.STAGE_DAEMON_READY", completion)
        self.assertIn("completed.failure == null", completion)
        self.assertIn("task.runId == runId", completion)
        self.assertIn("onActivated()", completion)
        home = source("com/maplesu/core/ui/screen/home/HomeScreen.kt")
        self.assertIn("onActivated = viewModel::refreshAfterJailbreak", home)

    def test_success_refresh_queues_and_waits_for_activation(self):
        view_model = source("com/maplesu/core/ui/viewmodel/HomeViewModel.kt")
        self.assertIn("fun refreshAfterJailbreak()", view_model)
        refresh = view_model.split("fun refreshAfterJailbreak()", 1)[1].split("fun refresh()", 1)[0]
        self.assertIn("viewModelScope.launch", refresh)
        self.assertNotIn("refreshFlight.launch", refresh)
        self.assertIn("ModeRuntime.awaitActivated(", refresh)
        self.assertIn("checkEnvironmentPresence()", refresh)
        self.assertIn("probeAndPublish(", refresh)
        self.assertIn("LiteHiddenRuntime.needsRealRead()", refresh)
        self.assertIn("attemptsDone < LiteStartupStatusPolicy.ROOT_RETRY_ATTEMPTS", refresh)


    def test_startup_hidden_retry_has_a_total_attempt_limit(self):
        view_model = source("com/maplesu/core/ui/viewmodel/HomeViewModel.kt")
        startup = view_model.split("// P0：确实失败", 1)[1].split("private suspend fun probeAndPublish", 1)[0]
        condition = startup.split("while (", 1)[1].split(") {", 1)[0]
        self.assertIn("attemptsDone < LiteStartupStatusPolicy.ROOT_RETRY_ATTEMPTS &&", condition)
        self.assertLess(condition.index("attemptsDone <"), condition.index("LiteStartupStatusPolicy.shouldRetryRoot("))
        self.assertIn("LiteHiddenRuntime.needsRealRead()", condition)

    def test_completed_probe_checks_revision_before_publishing(self):
        view_model = source("com/maplesu/core/ui/viewmodel/HomeViewModel.kt")
        probe = view_model.split("private suspend fun probeAndPublish", 1)[1].split("private fun resolvedFailure", 1)[0]
        self.assertIn("val detectionChanged = revision != ModeRuntime.detectionRevision.value", probe)
        self.assertIn("val state = if (detectionChanged)", probe)
        self.assertIn('resolvedFailure(HomeEnvironmentFailure.BUSY, "detection_revision_changed")', probe)
        self.assertLess(probe.index("if (detectionChanged)"), probe.index("when (checked)"))
        self.assertLess(probe.index("when (checked)"), probe.index("publishState(state, owner)"))
        publish = view_model.split("private suspend fun publishState(", 1)[1].split("private fun HomeUiState.", 1)[0]
        self.assertLess(publish.index("publishMutex.withLock"), publish.index("if (!owner.isCurrent(environmentOwner))"))
        self.assertLess(publish.index("if (!owner.isCurrent(environmentOwner))"), publish.index("_uiState.update"))

    def test_failed_full_probe_rebuilds_local_activation_facts(self):
        view_model = source("com/maplesu/core/ui/viewmodel/HomeViewModel.kt")
        failure = view_model.split("private fun resolvedFailure(", 1)[1].split("private fun publishState", 1)[0]
        self.assertIn("buildState(probeRoot = false", failure)
        self.assertIn("afterEnvironmentProbeFailure(local, failure)", failure)
        state = source("com/maplesu/core/ui/screen/home/HomeUiState.kt")
        self.assertIn("internal fun HomeUiState.afterEnvironmentProbeFailure(", state)
        fallback = state.split("internal fun HomeUiState.afterEnvironmentProbeFailure(", 1)[1].split("/**", 1)[0]
        self.assertIn("environmentMode = local.environmentMode", fallback)
        self.assertIn("hiddenConfirming = local.hiddenConfirming", fallback)
        self.assertIn("environmentFailure = failure", fallback)
        self.assertNotIn("isRootAvailable = true", fallback)
        self.assertNotIn("isManager = true", fallback)


if __name__ == "__main__":
    unittest.main()
