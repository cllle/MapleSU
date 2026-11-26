package dev.maplesu.manager.modules

import dev.maplesu.manager.modules.webui.ModuleWebUiSessions
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit

private const val IMAGE = "sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e"
private val OLD = "a".repeat(64)
private val NEW = "b".repeat(64)
private const val ID = "fixture_module"
private const val OTHER = "unrelated_module"

private class DockerShell(private val output: File) : AutoCloseable {
    private var sequence = 0
    private val container: String
    init {
        output.mkdirs()
        val started = ProcessBuilder("docker", "run", "--rm", "-d", "--pull", "never", "--network", "none",
            "--read-only", "--tmpfs", "/work:rw,exec,size=32m", "--entrypoint", "/bin/sh", IMAGE,
            "-c", "exec sleep 180").start()
        container = started.inputStream.bufferedReader().readText().trim()
        val errors = started.errorStream.bufferedReader().readText()
        check(started.waitFor(30, TimeUnit.SECONDS) && started.exitValue() == 0 && container.length == 64) { errors }
    }
    fun execute(script: String): ModuleScriptOutput {
        val prefix = "command-${++sequence}"
        File(output, "$prefix.sh").writeText(script)
        val stdout = File(output, "$prefix.stdout")
        val stderr = File(output, "$prefix.stderr")
        val process = ProcessBuilder("docker", "exec", "-i", container, "/bin/sh", "-s")
            .redirectOutput(stdout).redirectError(stderr).start()
        process.outputStream.bufferedWriter().use { it.write(script); it.write("\n") }
        check(process.waitFor(30, TimeUnit.SECONDS)) { "Docker shell fixture timed out" }
        return ModuleScriptOutput(process.exitValue(), stdout.readText(), stderr.readText())
    }
    fun checked(script: String): String = execute(script).let {
        check(it.isSuccess) { "Fixture setup failed ${it.code}: ${it.err}" }; it.out
    }
    override fun close() {
        ProcessBuilder("docker", "rm", "-f", container).redirectErrorStream(true).start().also {
            it.inputStream.bufferedReader().readText(); it.waitFor(30, TimeUnit.SECONDS)
        }
    }
}

private class Case(private val shell: DockerShell, name: String, initialLive: Boolean = true) {
    val base = "/work/$name"
    val root = "$base/modules"
    private val runner = ModuleScriptRunner(shell::execute)
    val tree = RootModuleTree(runner, root, "$base/transactions")
    init {
        shell.checked("""
            set -eu
            mkdir -p '$root' '${root}_update' '$base/transactions' '$base/source' '$base/maplesu/bin'
            printf 'id=$ID\nversionCode=2\n' > '$base/source/module.prop'
            printf 'candidate' > '$base/source/value'
            cat > '$base/maplesu/bin/maplesu-tools' <<'TOOL'
            #!/bin/sh
            set -eu
            case "${'$'}{1:-}" in
                module-initrc)
                    [ "${'$'}#" = 1 ] || exit 64
                    printf 'module-initrc\n' >> '$base/tool-calls'
                    exit 0 ;;
                module-sync) shift ;;
                *) exit 64 ;;
            esac
            if [ "${'$'}{1:-}" = --tree ]; then shift; fi
            for target do
                if [ -f '$base/fail-after-rename' ] && [ "${'$'}target" = '$root' ] &&
                    [ -d '$root/$ID' ] && [ ! -e '${root}_update/$ID' ]; then
                    printf 'FIXTURE_SYNC_FAILED\n' >&2
                    exit 74
                fi
                sync -f "${'$'}target"
            done
            TOOL
            cat > '$base/maplesu/bin/busybox' <<'BUSYBOX'
            #!/bin/sh
            set -eu
            [ "${'$'}{1:-}" = flock ] || exit 64
            shift
            exec flock "${'$'}@"
            BUSYBOX
            chmod 0755 '$base/maplesu/bin/maplesu-tools' '$base/maplesu/bin/busybox'
        """.trimIndent() + "\n" + if (initialLive) """
            mkdir '$root/$ID'
            printf 'id=$ID\nversionCode=1\n' > '$root/$ID/module.prop'
            printf 'original' > '$root/$ID/value'
            printf '%s\n' '$OLD' > '$root/$ID/.maplesu-ultra'
            """.trimIndent() else "")
    }
    fun prepare() = tree.prepareInstall(ID, "$base/source", NEW, OLD, emptyList(), true)
    fun value() = shell.checked("cat '$root/$ID/value'")
    fun setup(script: String) = shell.checked(script)
    fun privateRun(script: String): String {
        val method = RootModuleTree::class.java.getDeclaredMethod("run", String::class.java, Boolean::class.javaPrimitiveType,
            kotlin.jvm.functions.Function1::class.java, kotlin.jvm.functions.Function1::class.java)
        method.isAccessible = true
        return try { method.invoke(tree, script, false, { _: String -> }, { _: String -> }) as String }
        catch (error: InvocationTargetException) { throw requireNotNull(error.cause) }
    }
}

private class Pages : AutoCloseable {
    var target = 0
    var other = 0
    private val a = requireNotNull(ModuleWebUiSessions.subscribe(ID, ModuleWebUiSessions.revision(ID)) { target++ })
    private val b = requireNotNull(ModuleWebUiSessions.subscribe(OTHER, ModuleWebUiSessions.revision(OTHER)) { other++ })
    override fun close() { a.close(); b.close() }
}

private fun failure(block: () -> Unit): Throwable = try { block(); error("Expected root command failure") }
    catch (error: IllegalStateException) { check(error.message?.startsWith("MODULE_TREE_ROOT_FAILED") == true) { error }; error }

fun main(args: Array<String>) {
    val output = File(args.single())
    var passed = 0
    fun test(name: String, body: () -> Unit) { body(); passed++; println("PASS $name") }
    DockerShell(File(output, "shell")).use { shell ->
        test("pendingPrepareMarkersAndSnapshotsKeepPagesThenActivationRetiresOnlyTarget") {
            val fixture = Case(shell, "activation")
            Pages().use { pages ->
                fixture.prepare()
                check(fixture.value() == "original")
                fixture.tree.marker(ID, "disable", true)
                fixture.tree.marker(ID, "disable", false)
                check(fixture.tree.snapshot(listOf(ID)).getValue(ID).directory)
                check(pages.target == 0 && pages.other == 0)
                fixture.tree.activateInstall(ID, NEW)
                check(fixture.value() == "candidate")
                check(pages.target == 1 && pages.other == 0)
            }
        }
        test("rollbackRestoresOriginalTreeAndRetiresTargetOnce") {
            val fixture = Case(shell, "rollback")
            fixture.prepare()
            fixture.tree.activateInstall(ID, NEW)
            Pages().use { pages ->
                fixture.tree.recover(ID, OLD)
                check(fixture.value() == "original")
                check(pages.target == 1 && pages.other == 0)
            }
        }
        test("realRemovalRetiresOnlyItsLivePage") {
            val fixture = Case(shell, "removal")
            Pages().use { pages ->
                fixture.tree.remove(ID, OLD)
                fixture.setup("test ! -e '${fixture.root}/$ID'")
                check(pages.target == 1 && pages.other == 0)
            }
        }
        test("renameBeforeSyncFailureStillRetiresAndPreservesFailure") {
            val fixture = Case(shell, "sync_failure", initialLive = false)
            fixture.prepare()
            fixture.setup("touch '${fixture.base}/fail-after-rename'")
            Pages().use { pages ->
                val error = failure { fixture.tree.activateInstall(ID, NEW) }
                check(error.message!!.contains("exit=74") && error.message!!.contains("FIXTURE_SYNC_FAILED"))
                check(!error.message!!.contains("MAPLESU_TREE_CHANGED"))
                check(fixture.value() == "candidate")
                check(pages.target == 1 && pages.other == 0)
            }
        }
        test("preRenameFailureKeepsPagesAndOriginalTree") {
            val fixture = Case(shell, "admission_failure")
            fixture.prepare()
            Pages().use { pages ->
                failure { fixture.tree.activateInstall(ID, "c".repeat(64)) }
                check(fixture.value() == "original")
                check(pages.target == 0 && pages.other == 0)
            }
        }
        test("absentRemovalAndNestedRemovalDoNotRetireLivePage") {
            val fixture = Case(shell, "absent")
            Pages().use { pages ->
                fixture.privateRun("set -eu\nremove_tree '${fixture.root}/absent_module'")
                fixture.setup("mkdir '${fixture.root}/$ID/nested'")
                fixture.privateRun("set -eu\nremove_tree '${fixture.root}/$ID/nested'")
                check(fixture.value() == "original")
                check(pages.target == 0 && pages.other == 0)
            }
        }
    }
    println("PASSED $passed real-shell cases")
}
