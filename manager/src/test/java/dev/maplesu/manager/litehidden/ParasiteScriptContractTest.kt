package dev.maplesu.manager.litehidden

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * root 脚本的**外壳契约**：这些脚本真正的解释器是设备上的 `sh`，Kotlin 单测跑不了设备 shell，
 * 所以这里把「会静默假失败 / 会把旧库写回宿主」的写法固化成断言。
 *
 * 2026-10-31 真机事故（`.analysis/parasite-prod-check/`）：`hashLine()` 把已经带引号的
 * `"$S"` 又包了一层单引号 → `sha256sum '"$S"'` 找不到文件、返回空 → `backup()` 只写出
 * 第一个 `.orig` 就 `BACKUP_SHA` 失败；而失败码映射把它显示成「改写原生库失败」。
 * 同一处 bug 还会让 restore/verify 全部假失败。
 */
class ParasiteScriptContractTest {

    private val facts = HostFacts(
        packageName = "com.example.host",
        isSystem = false,
        abis = listOf("arm64-v8a"),
        nativeLibraryDir = "/data/app/~~x==/com.example.host-y==/lib/arm64",
        extractNativeLibs = true,
        libFiles = listOf(HostLibFile("liba.so", 10), HostLibFile("libb.so", 20)),
    )
    private val targets = listOf("liba.so", "libb.so")

    private fun manifest(): HostManifest = HostManifest(
        packageName = facts.packageName,
        libraryDir = facts.nativeLibraryDir,
        label = "u:object_r:apk_data_file:s0",
        loaderName = ParasitePaths.LOADER_NAME,
        loaderSha256 = "a".repeat(64),
        files = listOf(HostBackupFile("liba.so", "b".repeat(64), "c".repeat(64), "755", 1000, 1000)),
    )

    @Test
    fun hostStartupUsesAnActivationNonceAndLiveHostIdentity() {
        val script = ParasiteScripts.hostStartupCheck(facts.copy(uid = 10123), "a".repeat(32))
        assertTrue(script.contains("am force-stop"))
        assertTrue(script.contains("host-start.challenge"))
        assertTrue(script.contains("host-start.proof"))
        assertTrue(script.contains("boot_id"))
        assertTrue(script.contains("am start"))
        assertTrue(script.contains("nonce=${"a".repeat(32)}"))
        assertTrue(script.contains("while"))
        val start = ParasiteScripts.daemonStart()
        assertTrue(start.contains("--host-start"))
        assertTrue(start.contains("START_PID=\$!"))
        assertTrue(start.contains("[ \"\$P\" = \"\${START_PID:-}\" ] || return 1"))
        assertTrue(start.contains("/proc/\$HP/status"))
        assertTrue(start.contains("/proc/\$HP/maps"))
        assertTrue(start.contains("/proc/\$HP/stat"))
        assertTrue(start.indexOf("host_proof") < start.indexOf("PARASITE_STARTED"))
    }

    @Test
    fun hashExpressionsExpandInsteadOfBeingQuotedLiterally() {
        val scripts = mapOf(
            "backup" to ParasiteScripts.backup(facts, targets),
            "restore" to ParasiteScripts.restore(
                facts.packageName,
                facts.nativeLibraryDir,
                "u:object_r:apk_data_file:s0",
            ),
            "verify" to ParasiteScripts.verifyEmbedded(manifest()),
        )
        scripts.forEach { (name, script) ->
            // 单引号包住 "$S" 会让变量不展开 → sha 永远为空（真机事故根因）。
            assertFalse("$name 不允许单引号包住路径表达式", script.contains("sha256sum '"))
            assertTrue("$name 必须至少有一处展开式 sha", script.contains("sha256sum \"\$"))
        }
        val backup = scripts.getValue("backup")
        assertTrue(backup.contains("sha256sum \"\$S\" 2>/dev/null"))
        assertTrue(backup.contains("sha256sum \"\$D/\$F.orig\" 2>/dev/null"))
        val restore = scripts.getValue("restore")
        assertTrue(restore.contains("sha256sum \"\$T\" 2>/dev/null"))
    }

    @Test
    fun backupInitializesTrustedRecoveryBeforeHostBackup() {
        val script = ParasiteScripts.backup(facts, targets)
        assertTrue(script.contains("R='${ParasitePaths.RECOVERY}'"))
        assertTrue(script.contains("[ ! -L \"\$R\" ]"))
        assertTrue(script.contains("BACKUP_RECOVERY_UNTRUSTED"))
        assertTrue(script.contains("stat -c '%u' \"\$R\""))
        val initialized = script.indexOf("chmod 700 \"\$R\"")
        assertTrue(initialized >= 0)
        assertTrue(initialized < script.indexOf("mkdir -p \"\$D\""))
        assertTrue(script.indexOf("700 0") < script.indexOf("cp -p"))
    }

    @Test
    fun backupWritesCompletionMarkerOnlyAfterAllFilesAndRestoreRequiresIt() {
        val backup = ParasiteScripts.backup(facts, targets)
        val markerIndex = backup.indexOf(ParasitePaths.BACKUP_MARKER_NAME)
        val okIndex = backup.indexOf(ParasiteMarkers.BACKUP_OK)
        assertTrue("备份必须落完成标记", markerIndex >= 0)
        assertTrue("完成标记必须在 BACKUP_OK 之前", markerIndex < okIndex)
        assertTrue(
            "标记必须写进备份目录（回滚/恢复据此判定完整性）",
            backup.contains("MK='${ParasitePaths.backupMarkerPath(facts.packageName)}'"),
        )

        val restore = ParasiteScripts.restore(facts.packageName, facts.nativeLibraryDir, "")
        assertTrue(restore.contains(ParasitePaths.BACKUP_MARKER_NAME))
        assertTrue(restore.contains("RESTORE_INCOMPLETE_BACKUP"))
        // 半截备份绝不能进入逐文件写回流程。
        assertTrue(
            "完整性闸门必须在 for 循环之前",
            restore.indexOf("RESTORE_INCOMPLETE_BACKUP") < restore.indexOf("for S in"),
        )
    }

    @Test
    fun guardKeepsCompleteBackupAndRemovesPartialOne() {
        val guard = ParasiteScripts.backupGuard(facts.packageName)
        assertTrue(guard.contains(ParasitePaths.BACKUP_MARKER_NAME))
        assertTrue(guard.contains(ParasitePaths.MANIFEST_NAME))
        assertTrue(guard.contains("PARASITE_STALE_BACKUP"))
        assertTrue("半截备份必须清掉，避免以后被当成恢复材料", guard.contains("rm -rf"))
        assertTrue(
            "清理只能发生在没有清单/标记的分支",
            guard.indexOf("PARASITE_STALE_BACKUP") < guard.indexOf("rm -rf"),
        )
    }

    @Test
    fun backupDirProbeRequiresCompleteBackupAndCleansPartial() {
        val probe = ParasiteScripts.backupDirExists(facts.packageName)
        assertTrue(probe.contains("PARASITE_BACKUP_PROBE"))
        assertTrue(probe.contains(ParasitePaths.BACKUP_MARKER_NAME))
        assertTrue(probe.contains(ParasitePaths.MANIFEST_NAME))
        assertTrue(probe.contains("PARASITE_NO"))
        assertTrue(probe.contains("rm -rf"))
        assertTrue(
            "只有完整备份才回 YES",
            probe.indexOf("PARASITE_YES") < probe.indexOf("rm -rf"),
        )
    }

    @Test
    fun cleanupCheckpointIsProtectedBoundAndGloballyGuardsActivation() {
        val receipt = ParasiteScripts.cleanupReceipt(facts.packageName, "LITE", true)
        assertTrue(receipt == "MAPLESU_CLEANUP_V1 mode=LITE host=${facts.packageName} restored=1")
        val begin = ParasiteScripts.beginCleanup(facts.packageName, "LITE", true)
        val read = ParasiteScripts.readCleanupCheckpoint()
        val finish = ParasiteScripts.finishCleanup(facts.packageName, "LITE", true)
        listOf(begin, read, finish).forEach {
            assertTrue(it.contains("700 0"))
            assertTrue(it.contains("[ ! -L"))
        }
        assertTrue(begin.contains("chmod 600"))
        assertTrue(begin.contains("chown 0:0"))
        assertTrue(begin.indexOf("mv -f") < begin.indexOf(ParasiteMarkers.CLEANUP_READY))
        assertTrue(read.contains("600 0"))
        assertTrue(finish.indexOf(ParasitePaths.IDENTITY_TEMP) < finish.indexOf("rm -f"))
        assertTrue(ParasiteScripts.backupGuard("com.other.host").contains(ParasitePaths.CLEANUP_CHECKPOINT))
        assertFalse(ParasitePaths.CLEANUP_CHECKPOINT.startsWith(ParasitePaths.hostBackupDir(facts.packageName)))
    }

    @Test
    fun cleanupScriptsReadBackAbsenceBeforeReportingCompletion() {
        val backup = ParasiteScripts.removeBackup(facts.packageName)
        assertTrue(backup.indexOf("[ ! -e") < backup.indexOf(ParasiteMarkers.BACKUP_GONE))
        assertTrue(backup.contains("[ ! -L"))
        assertTrue(backup.contains("exit 87"))
        val identity = ParasiteScripts.removeCleanupIdentity()
        assertTrue(identity.contains("[ ! -e '${ParasitePaths.IDENTITY_TEMP}' ]"))
        assertTrue(identity.contains("[ ! -L '${ParasitePaths.IDENTITY_TEMP}' ]"))
    }

    @Test
    fun rollbackRestorationChecksEveryOriginalBeforeAnyMutationAndVerifiesTailCleanup() {
        val expected = mapOf("liba.so" to "a".repeat(64), "libb.so" to "b".repeat(64))
        val script = ParasiteScripts.restore(facts.packageName, facts.nativeLibraryDir, "", expected)
        assertTrue(script.contains("RESTORE_EXPECTED_SHA"))
        assertTrue(script.contains("RESTORE_EXPECTED_COUNT"))
        assertTrue(script.indexOf("RESTORE_EXPECTED_COUNT") < script.indexOf("RESTORED=0"))
        assertTrue(script.indexOf("RESTORE_CLEANUP") < script.indexOf(ParasiteMarkers.RESTORE_OK))
        assertTrue(script.contains("[ ! -L \"\$T\" ]"))
        assertTrue(script.contains("exit 48"))
    }

    @Test
    fun rollbackRetirementPreservesWholeBackupInProtectedSameFilesystemQuarantine() {
        val expected = mapOf("liba.so" to "a".repeat(64))
        val nonce = "a".repeat(32)
        val retire = ParasiteScripts.retireRestoredBackup(facts.packageName, expected, nonce)
        val verify = ParasiteScripts.verifyRetiredBackup(facts.packageName, expected, nonce)
        assertTrue(retire.contains("host-quarantine-rollback-$nonce"))
        assertTrue(retire.contains("stat -c '%d'"))
        assertTrue(retire.contains("stat -c '%s %a %u %g'"))
        assertTrue(retire.contains("700 0") && retire.contains("600 0"))
        assertTrue(retire.contains("count="))
        assertTrue(retire.indexOf("BEFORE=") < retire.indexOf("mv "))
        assertTrue(retire.indexOf("AFTER=") < retire.indexOf(ParasiteMarkers.BACKUP_RETIRED))
        assertFalse(retire.contains("rm -"))
        assertFalse(verify.contains("mv ") || verify.contains("mkdir ") || verify.contains("rm -"))
        assertTrue(verify.contains("RESTORE_EXPECTED_SHA") && verify.contains("AFTER="))
        assertFalse(retire.contains(ParasitePaths.IDENTITY_FILE) || retire.contains("PARASITE_CLEANUP_BEGIN"))
    }

    @Test
    fun daemonStartScriptIsIdempotentThroughPidFile() {
        val script = ParasiteScripts.daemonStart()
        assertTrue(script.contains("liteweb.pid"))
        assertTrue(script.contains("PARASITE_ALIVE"))
        assertTrue(script.contains("kill -0"))
        assertFalse(script.contains("pkill"))
    }

    @Test
    fun daemonStartUsesClasspathEnvAndNeverAClassNameMaskingDashD() {
        val script = ParasiteScripts.daemonStart()
        // `app_process` 只把 `--nice-name=` 之类当开关，`-Djava.class.path=` 会被当成主类名。
        assertFalse(script.contains("-Djava.class.path="))
        assertTrue(script.contains("CLASSPATH=\"\$APK\" nohup /system/bin/app_process /system/bin "))
        assertTrue(script.contains("--nice-name=${ParasitePaths.DAEMON_NICE_NAME}"))
        assertTrue(script.contains(ParasitePaths.DAEMON_CLASS))
        assertTrue("daemon 失败要留下日志", script.contains("liteweb.log"))
    }

    // ------------------------------------------------------------------ daemon 停止（真机缺陷 1）

    @Test
    fun daemonStopMatchesBothRealWorldArgvForms() {
        val stop = ParasiteScripts.daemonStop()
        // 形态 (1)：app_process 原生 argv（--nice-name 在参数里）。
        assertTrue(stop.contains("[ \"\$A0\" = \"/system/bin/app_process\" ]"))
        assertTrue(stop.contains("--nice-name=\$NAME"))
        // 形态 (2)：`--nice-name` 改写 argv[0] 后的纯 nice-name（Android 16 真机就是这个形态，
        // 旧实现只用形态 (1) 做预过滤 → 永远不会命中 → daemon 杀不掉却报成功）。
        assertTrue(stop.contains("elif [ \"\$A0\" = \"\$NAME\" ]"))
        assertTrue(stop.contains("NAME='" + ParasitePaths.DAEMON_NICE_NAME + "'"))
        // 仍然禁止 pkill -f：脚本自己的 argv 里也有这个名字。
        assertFalse(stop.contains("pkill"))
    }

    @Test
    fun daemonStopOnlyReportsStoppedAfterProcessAndPortVerification() {
        val stop = ParasiteScripts.daemonStop()
        // 端口取 daemon 自己的配置（webuiPort= 或 port=），并以监听 socket 为判据。
        assertTrue(stop.contains("webui.conf"))
        assertTrue(stop.contains("webuiPort="))
        assertTrue(stop.contains("/proc/net/tcp"))
        assertTrue(stop.contains("0A"))
        // 失败路径必须有明确标记 + 非 0 退出（上层据此判失败，不再无条件成功）。
        assertTrue(stop.contains("PARASITE_FAIL code=" + ParasiteCodes.DAEMON_STOP_FAILED))
        assertTrue(stop.contains("exit 1"))
        // 顺序：先自证，通过后才 echo STOPPED；失败标记在 STOPPED 之后（互斥分支）。
        val stoppedAt = stop.indexOf("echo \"PARASITE_STOPPED")
        assertTrue("STOPPED 只能在验证通过的分支里输出", stoppedAt > stop.indexOf("LISTEN"))
        assertTrue(stop.indexOf("PARASITE_FAIL") > stoppedAt)
    }

    @Test
    fun daemonStopNeverEndsWithoutVerification() {
        val stop = ParasiteScripts.daemonStop()
        // 旧实现最后一句就是 `echo PARASITE_STOPPED`（无验证）——这里固化"最后是失败分支"。
        val tail = stop.lines().filter { it.isNotBlank() }.takeLast(2).joinToString("\n")
        assertTrue("脚本最后必须是失败分支（成功分支在循环里 exit 0）", tail.contains("exit 1"))
    }

    @Test
    fun injectRunsAppProcessWithAbsoluteInterpreterAndQuotedArguments() {
        val script = ParasiteScripts.inject(
            facts = facts,
            targets = targets,
            managerApkPath = "/data/app/~~x==/dev.maplesu.manager-y==/base.apk",
            loaderSourcePath = "${facts.nativeLibraryDir}/${ParasitePaths.LOADER_NAME}",
            label = "u:object_r:apk_data_file:s0",
        )
        assertTrue(script.contains("CLASSPATH=\"\$APK\" exec /system/bin/app_process /system/bin "))
        assertTrue(script.contains(ParasitePaths.PATCH_MAIN))
        assertTrue(script.contains("--files 'liba.so:libb.so'"))
        assertTrue(script.contains("--label 'u:object_r:apk_data_file:s0'"))
    }
}
