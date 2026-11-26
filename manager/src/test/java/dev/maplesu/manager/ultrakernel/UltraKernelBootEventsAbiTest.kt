package dev.maplesu.manager.ultrakernel

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class UltraKernelBootEventsAbiTest {
    private fun report(mode: String = "none"): JsonObject {
        val definitions = linkedMapOf(
            "file_operations" to (320 to linkedMapOf("owner" to (0 to 8), "read" to (16 to 8),
                "read_iter" to (24 to 8), "release" to (112 to 8))),
            "kprobe" to (128 to linkedMapOf("symbol_name" to (40 to 8), "pre_handler" to (56 to 8))),
            "kiocb" to (64 to linkedMapOf("ki_filp" to (0 to 8), "ki_pos" to (8 to 8),
                "ki_complete" to (16 to 8), "ki_flags" to (24 to 4))),
            "iov_iter" to (40 to linkedMapOf("count" to (8 to 8))),
            "pt_regs" to (272 to linkedMapOf("regs" to (0 to 248))))
        val callbacks = mapOf("boot_read" to "file_operations.read", "boot_read_iter" to "file_operations.read_iter",
            "boot_release" to "file_operations.release", "boot_read_pre" to "kprobe.pre_handler")
        val signature = "fi8:64(*sfixture;)"
        val words = mutableListOf(0x4d534245, 1, 152, 1)
        val layouts = buildJsonObject {
            for ((type, definition) in definitions) {
                val alignment = if (type == "pt_regs") 16 else 8
                words += definition.first; words += alignment
                put(type, buildJsonObject {
                    put("bytes", definition.first); put("alignment", alignment)
                    put("signature", "s$type:${definition.first}{fixture}")
                    put("members", buildJsonObject {
                        for ((member, field) in definition.second) {
                            words += field.first; words += field.second
                            put(member, buildJsonObject {
                                put("offset", field.first); put("bytes", field.second)
                                put("signature", if ("$type.$member" in callbacks.values) "*$signature" else "i8:64")
                            })
                        }
                    })
                })
            }
        }
        return buildJsonObject {
            put("schemaVersion", 1); put("symbol", "msu_boot_events_abi_words")
            put("section", ".msu.boot_events.abi"); put("sectionFlags", 2)
            put("offset", 4096); put("bytes", 152); put("compiledSha256", "0".repeat(64))
            put("values", JsonArray(words.map(::JsonPrimitive))); put("layouts", layouts)
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
        assertThrows(IllegalArgumentException::class.java) { UltraKernelManagerClaimPayload.decodeBootEventsAbi(value) }
    }

    @Test fun decodesNativeObjectsAndCallbacks() {
        val actual = UltraKernelManagerClaimPayload.decodeBootEventsAbi(report())
        assertEquals(38, actual.words.size)
        assertEquals(setOf("file_operations", "kprobe", "kiocb", "iov_iter", "pt_regs"), actual.layouts.keys)
        assertEquals(4, actual.callbacks.size)
        assertEquals("kcfi", UltraKernelManagerClaimPayload.decodeBootEventsAbi(report("kcfi")).cfiMode)
    }

    @Test fun rejectsMutatedRawOwnerAndMemberWords() {
        val valid = report()
        for (index in listOf(3, 8, 18, 24, 32, 36)) {
            val words = valid.getValue("values").jsonArray.toMutableList()
            words[index] = JsonPrimitive(words[index].jsonPrimitive.int + 1)
            reject(JsonObject(valid + ("values" to JsonArray(words))))
        }
    }

    @Test fun rejectsMissingCallbacksAndLegacyCfi() {
        val valid = report()
        reject(JsonObject(valid + ("callbacks" to JsonObject(valid.getValue("callbacks").jsonObject - "boot_read_pre"))))
        reject(report("legacy-lto-cfi"))
    }

    @Test fun rejectsWrongObjectKcfiPrefix() {
        val valid = report("kcfi")
        val callbacks = valid.getValue("callbacks").jsonObject
        val bad = JsonObject(callbacks.getValue("boot_read_iter").jsonObject + ("objectKcfiType" to JsonPrimitive(456)))
        reject(JsonObject(valid + ("callbacks" to JsonObject(callbacks + ("boot_read_iter" to bad)))))
    }
}
