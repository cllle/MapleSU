package com.maplesu.core.ui.util

import dev.maplesu.manager.runtime.ultra.NativeReceipt
import dev.maplesu.manager.runtime.ultra.SELinuxBoundary as B
import dev.maplesu.manager.runtime.ultra.UltraRuntimeClient.ManagerOwnerIdentity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val ownerA = ManagerOwnerIdentity(10123, 11, "host-a")

private fun reset() {
    invalidateUltraSELinuxStatus()
    B.key = null
    B.boot = "boot-a"
    B.owner = ownerA
    B.liveOwner = ownerA
    B.ultra = true
    B.hook = false
    B.legacyCalls = 0
    B.hookCalls = 0
    B.statusCalls.set(0)
    B.rootCalls.set(0)
    B.ownerCalls.set(0)
    B.submittedKeys.clear()
    B.status = { NativeReceipt(true, 1, true) }
    B.root = { NativeReceipt(true, result = "Enforcing") }
    android.os.SystemClock.now = 1_000L
}

private fun expect(value: Any?, actual: Any?) {
    check(value == actual) { "expected=$value actual=$actual" }
}

/** Uses a real temporary enforce file through the checker's IO-only overload. */
private fun local(file: File): String {
    val checker = Class.forName("com.maplesu.core.ui.util.SELinuxCheckerKt")
    val method = checker.getDeclaredMethod("getSELinuxStatusRaw", kotlin.jvm.functions.Function0::class.java)
    return method.invoke(null, { file.readText() }) as String
}

private fun worker(action: () -> String): Pair<Thread, AtomicReference<String>> {
    val value = AtomicReference<String>()
    val thread = Thread { value.set(runCatching(action).getOrElse { "THREW:$it" }) }
    thread.start()
    return thread to value
}

private fun await(latch: CountDownLatch) { check(latch.await(3, TimeUnit.SECONDS)) { "barrier timeout" } }
private fun finish(worker: Pair<Thread, AtomicReference<String>>): String {
    worker.first.join(3_000)
    check(!worker.first.isAlive) { "worker did not finish" }
    return worker.second.get()
}

fun main(args: Array<String>) {
    var passed = 0
    fun scenario(name: String, body: () -> Unit) {
        reset()
        body()
        check(B.legacyCalls == 0) { "Ultra check started another Root transport" }
        passed++
        println("PASS $name")
    }
    scenario("owner-no-key-real-entry") {
        val status = getSELinuxStatusRaw()
        println("OWNER_NO_KEY status=$status sdkQueries=${B.statusCalls.get()} legacyCalls=${B.legacyCalls}")
        expect("Enforcing", status)
        expect(1, B.statusCalls.get())
    }
    if (args.contains("--owner-only")) return
    scenario("owner-no-key-sdk-root-fallback") {
        B.status = { NativeReceipt(true) }
        expect("Enforcing", getSELinuxStatusRaw())
        expect(listOf<String?>(null), B.submittedKeys.toList())
    }
    scenario("owner-local-enforce-file-does-not-query-sdk") {
        val file = File.createTempFile("maplesu-enforce-", ".txt")
        try {
            file.writeText("1\n")
            B.status = { error("local file should be sufficient") }
            expect("Enforcing", local(file))
            expect(0, B.statusCalls.get())
            expect(0, B.rootCalls.get())
        } finally { file.delete() }
    }
    scenario("owner-local-permissive-is-not-enforcing") {
        val file = File.createTempFile("maplesu-enforce-", ".txt")
        try { file.writeText("0\n"); expect("Permissive", local(file)) }
        finally { file.delete() }
    }
    scenario("hook-keeps-its-existing-status-route") {
        B.hook = true
        expect("Enforcing", getSELinuxStatusRaw())
        expect(1, B.hookCalls)
        expect(0, B.statusCalls.get())
    }
    scenario("normal-local-file-keeps-its-existing-status-route") {
        B.ultra = false
        B.owner = null
        val file = File.createTempFile("maplesu-enforce-", ".txt")
        try { file.writeText("0\n"); expect("Permissive", local(file)) }
        finally { file.delete() }
        expect(0, B.statusCalls.get())
    }
    scenario("missing-owner-remains-unknown") {
        B.owner = null
        B.liveOwner = null
        expect("Unknown", getSELinuxStatusRaw())
        expect(0, B.statusCalls.get())
    }
    scenario("foreign-fulluid-remains-unknown") {
        B.owner = ownerA.copy(fullUid = 110123)
        B.liveOwner = B.owner
        expect("Unknown", getSELinuxStatusRaw())
        expect(0, B.statusCalls.get())
    }
    scenario("live-owner-mismatch-blocks-sdk-query") {
        B.liveOwner = ownerA.copy(epoch = 12)
        expect("Unknown", getSELinuxStatusRaw())
        expect(0, B.statusCalls.get())
        expect(0, B.rootCalls.get())
    }
    scenario("same-owner-reuses-fresh-known-cache") {
        expect("Enforcing", getSELinuxStatusRaw())
        expect("Enforcing", getSELinuxStatusRaw())
        expect("Enforcing", peekCachedSELinuxStatus())
        expect(1, B.statusCalls.get())
    }
    scenario("key-to-owner-does-not-reuse-cache") {
        B.key = "key-a"
        B.status = { NativeReceipt(true, 0, true) }
        expect("Permissive", getSELinuxStatusRaw())
        expect(0, B.ownerCalls.get())
        B.key = null
        B.status = { NativeReceipt(true, 1, true) }
        expect("Enforcing", getSELinuxStatusRaw())
        expect(2, B.statusCalls.get())
    }
    scenario("owner-to-key-does-not-reuse-cache") {
        expect("Enforcing", getSELinuxStatusRaw())
        B.key = "key-b"
        B.status = { NativeReceipt(true, 0, true) }
        expect("Permissive", getSELinuxStatusRaw())
        expect(2, B.statusCalls.get())
    }
    for ((name, nextOwner) in listOf("epoch" to ownerA.copy(epoch = 12), "host" to ownerA.copy(hostInstance = "host-b"))) {
        scenario("owner-$name-change-isolates-cache-and-token") {
            val token = captureUltraSELinuxObservation()
            expect("Enforcing", getSELinuxStatusRaw())
            B.owner = nextOwner
            B.liveOwner = nextOwner
            check(!rememberUltraSELinuxStatus("Permissive", token))
            expect(null, peekCachedSELinuxStatus())
            expect("Enforcing", getSELinuxStatusRaw())
            expect(2, B.statusCalls.get())
        }
    }
    scenario("boot-change-isolates-cache-and-token") {
        val token = captureUltraSELinuxObservation()
        expect("Enforcing", getSELinuxStatusRaw())
        B.boot = "boot-b"
        check(!rememberUltraSELinuxStatus("Permissive", token))
        expect(null, peekCachedSELinuxStatus())
        expect("Enforcing", getSELinuxStatusRaw())
        expect(2, B.statusCalls.get())
    }
    scenario("key-owner-transition-rejects-late-producer-tokens") {
        val ownerToken = captureUltraSELinuxObservation()
        B.key = "key-a"
        check(!rememberUltraSELinuxStatus("Enforcing", ownerToken))
        val keyToken = captureUltraSELinuxObservation()
        B.key = null
        check(!rememberUltraSELinuxStatus("Permissive", keyToken))
        expect(null, peekCachedSELinuxStatus())
    }
    scenario("revision-invalidation-rejects-late-producer-token") {
        val token = captureUltraSELinuxObservation()
        invalidateUltraSELinuxStatus()
        check(!rememberUltraSELinuxStatus("Permissive", token))
        expect(null, peekCachedSELinuxStatus())
    }
    scenario("concurrent-same-owner-coalesces-sdk-request") {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        B.status = { entered.countDown(); await(release); NativeReceipt(true, 1, true) }
        val first = worker { getSELinuxStatusRaw() }
        await(entered)
        val second = worker { getSELinuxStatusRaw() }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (second.first.state != Thread.State.WAITING && System.nanoTime() < deadline) Thread.sleep(1)
            check(second.first.state == Thread.State.WAITING)
            expect(1, B.statusCalls.get())
        } finally { release.countDown() }
        expect("Enforcing", finish(first))
        expect("Enforcing", finish(second))
    }
    for (change in listOf("epoch", "host", "boot", "revision", "key")) {
        scenario("concurrent-$change-change-discards-old-reply") {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            B.status = { entered.countDown(); await(release); NativeReceipt(true, 1, true) }
            val old = worker { getSELinuxStatusRaw() }
            await(entered)
            when (change) {
                "epoch" -> { B.owner = ownerA.copy(epoch = 12); B.liveOwner = B.owner }
                "host" -> { B.owner = ownerA.copy(hostInstance = "host-b"); B.liveOwner = B.owner }
                "boot" -> B.boot = "boot-b"
                "revision" -> invalidateUltraSELinuxStatus()
                "key" -> B.key = "key-b"
            }
            B.status = { NativeReceipt(true, 0, true) }
            try { expect("Permissive", getSELinuxStatusRaw()) }
            finally { release.countDown() }
            expect("Unknown", finish(old))
            expect("Permissive", peekCachedSELinuxStatus())
            expect(2, B.statusCalls.get())
        }
    }
    scenario("live-owner-change-during-root-command-discards-reply") {
        B.status = { NativeReceipt(true) }
        B.root = { B.liveOwner = ownerA.copy(epoch = 12); NativeReceipt(true, result = "Permissive") }
        expect("Unknown", getSELinuxStatusRaw())
        expect(null, peekCachedSELinuxStatus())
    }
    scenario("live-owner-change-during-status-query-discards-reply") {
        B.status = { B.liveOwner = ownerA.copy(epoch = 12); NativeReceipt(true, 0, true) }
        expect("Unknown", getSELinuxStatusRaw())
        expect(null, peekCachedSELinuxStatus())
        expect(0, B.rootCalls.get())
    }
    scenario("failed-sdk-and-malformed-local-file-stay-unknown") {
        val file = File.createTempFile("maplesu-enforce-", ".txt")
        try {
            file.writeText("denied")
            B.status = { NativeReceipt(false) }
            B.root = { NativeReceipt(false) }
            expect("Unknown", local(file))
            expect(null, peekCachedSELinuxStatus())
            B.status = { NativeReceipt(true, 1, true) }
            expect("Enforcing", getSELinuxStatusRaw())
        } finally { file.delete() }
    }
    scenario("known-cache-expires-after-five-seconds") {
        expect("Enforcing", getSELinuxStatusRaw())
        android.os.SystemClock.now += 5_000
        expect(null, peekCachedSELinuxStatus())
        B.status = { NativeReceipt(true, 0, true) }
        expect("Permissive", getSELinuxStatusRaw())
        expect(2, B.statusCalls.get())
    }
    println("PASS_TOTAL=$passed")
}
