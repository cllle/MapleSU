package dev.maplesu.manager.ultrakernel

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class UltraKernelKsuCompatAbiTest {
    private fun report(compat: Boolean = true, mode: String = "none"): JsonObject {
        val signature = "fi8:64(*sfixture;)"
        val fields = linkedMapOf("owner" to (0 to 8), "unlocked_ioctl" to (72 to 8), "release" to (112 to 8))
        if (compat) fields["compat_ioctl"] = 80 to 8
        val callbacks = linkedMapOf("msu_ksu_ioctl" to "file_operations.unlocked_ioctl",
            "msu_ksu_release" to "file_operations.release", "msu_ksu_install_resume" to "callback_head.func")
        if (compat) callbacks["msu_ksu_compat_ioctl"] = "file_operations.compat_ioctl"
        val words = listOf(1, 320, 8, 0, 8, 72, 8, if (compat) 80 else -1, if (compat) 8 else 0, 112, 8, 16, 8, 8)
        fun layout(type: String, bytes: Int, members: Map<String, Pair<Int, Int>>) = buildJsonObject {
            put("bytes", bytes); put("signature", "s$type:$bytes{fixture}")
            if (type == "file_operations") put("alignment", 8)
            put("members", buildJsonObject {
                for ((name, field) in members) put(name, buildJsonObject {
                    put("offset", field.first); put("bytes", field.second)
                    put("signature", if ("$type.$name" in callbacks.values) "*$signature" else "*smodule")
                })
            })
        }
        return buildJsonObject {
            put("schemaVersion", 1); put("symbol", "msu_ksu_compat_layout")
            put("section", ".msu.ksu-compat.layout"); put("sectionFlags", 2)
            put("offset", 4096); put("bytes", 56); put("compiledSha256", "0".repeat(64))
            put("values", JsonArray(words.map(::JsonPrimitive)))
            put("layouts", buildJsonObject {
                put("file_operations", layout("file_operations", 320, fields))
                put("callback_head", layout("callback_head", 16, mapOf("func" to (8 to 8))))
            })
            put("cfiPolicy", buildJsonObject { put("mode", mode); put("normalizeIntegers", false) })
            put("callbacks", buildJsonObject {
                for ((name, member) in callbacks) put(name, buildJsonObject {
                    put("symbol", name); put("member", member); put("signature", signature); put("offset", 64)
                    put("kcfiPrefix", if (mode == "kcfi") JsonPrimitive(123) else JsonNull)
                    put("objectKcfiType", if (mode == "kcfi") JsonPrimitive(123) else JsonNull)
                    put("objectKcfiTypeSource", "actual-object-entry-prefix")
                })
            })
        }
    }
    private fun reject(value: JsonObject) {
        assertThrows(IllegalArgumentException::class.java) { UltraKernelManagerClaimPayload.decodeKsuCompatAbi(value) }
    }
    @Test fun decodesExactNativeAndCompatCallbackSets() {
        assertEquals(4, UltraKernelManagerClaimPayload.decodeKsuCompatAbi(report()).callbacks.size)
        assertEquals(3, UltraKernelManagerClaimPayload.decodeKsuCompatAbi(report(compat = false)).callbacks.size)
        assertEquals("kcfi", UltraKernelManagerClaimPayload.decodeKsuCompatAbi(report(mode = "kcfi")).cfiMode)
    }
    @Test fun rejectsMutatedFopsAndTaskWorkLayouts() {
        val valid = report()
        for (index in listOf(0, 1, 2, 3, 5, 7, 9, 11, 12, 13)) {
            val words = valid.getValue("values").jsonArray.toMutableList()
            words[index] = JsonPrimitive(words[index].jsonPrimitive.int + 1)
            reject(JsonObject(valid + ("values" to JsonArray(words))))
        }
    }
    @Test fun rejectsUnverifiedOrMissingCallbacks() {
        val valid = report()
        reject(JsonObject(valid + ("callbacks" to JsonObject(valid.getValue("callbacks").jsonObject - "msu_ksu_install_resume"))))
        reject(report(mode = "legacy-lto-cfi"))
    }
    @Test fun rejectsCallbackTypeMismatch() {
        val valid = report(mode = "kcfi")
        val callbacks = valid.getValue("callbacks").jsonObject
        val bad = JsonObject(callbacks.getValue("msu_ksu_ioctl").jsonObject + ("objectKcfiType" to JsonPrimitive(456)))
        reject(JsonObject(valid + ("callbacks" to JsonObject(callbacks + ("msu_ksu_ioctl" to bad)))))
    }
}
