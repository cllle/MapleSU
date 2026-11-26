package dev.maplesu.manager.ultrakernel

internal data class UltraKernelLayoutField(
    val offsetBytes: Int,
    val widthBytes: Int,
    val ownerSizeBytes: Int?,
    val source: String,
    val parserVersion: Int = 1,
)

internal data class UltraKernelLayout(val fields: Map<String, UltraKernelLayoutField>) {
    fun offset(name: String): Int? = fields[name]?.offsetBytes
}

/** Member locations from the target Image's own BTF, never from its release string. */
internal object UltraKernelBtf {
    fun parameterSignature(kernel: ByteArray, symbols: UltraKernelSymbols,
                           function: String, parameter: Int): String? =
        readAbi(kernel, symbols) { parameterSignature(function, parameter) }

    fun typedefSignature(kernel: ByteArray, symbols: UltraKernelSymbols, name: String): String? =
        readAbi(kernel, symbols) { typedefSignature(name) }

    fun typeSignature(kernel: ByteArray, symbols: UltraKernelSymbols, name: String): String? =
        readAbi(kernel, symbols) { namedSignature(name) }

    fun typeSignatures(kernel: ByteArray, symbols: UltraKernelSymbols, names: Set<String>): Map<String, String>? =
        readAbi(kernel, symbols) {
            names.sorted().associateWith { namedSignature(it) ?: throw Invalid("TYPE_MISSING:$it") }
        }

    fun selectedStructure(kernel: ByteArray, symbols: UltraKernelSymbols, name: String,
        members: Set<String>): UltraKernelResidentAbi.CompiledLayout? =
        readAbi(kernel, symbols) { selectedStructure(name, members) }

    fun verifyFunctionMarker(kernel: ByteArray, symbols: UltraKernelSymbols, name: String,
        expected: String, typeid: Int?) {
        readAbi(kernel, symbols) { verifyFunctionMarker(symbols, name, expected, typeid) }
            ?: error("ULTRA_KERNEL_PROCATTR_MARKER_BTF_REQUIRED")
    }

    fun variableSignatures(kernel: ByteArray, symbols: UltraKernelSymbols,
                           names: Set<String>): Map<String, String>? = readAbi(kernel, symbols) {
        names.sorted().associateWith { variableSignature(it) ?: throw Invalid("VARIABLE_MISSING:$it") }
    }

    fun dataObjectSizes(kernel: ByteArray, symbols: UltraKernelSymbols,
                        signatures: Map<String, String>): Map<String, Long>? = readAbi(kernel, symbols) {
        signatures.toSortedMap().mapValues { (name, expected) -> dataObjectSize(name, expected) }
    }

    fun enumValue(kernel: ByteArray, symbols: UltraKernelSymbols, name: String): Long? =
        readAbi(kernel, symbols) { enumValue(name) }

    fun integerVariable(kernel: ByteArray, symbols: UltraKernelSymbols, name: String): Long? =
        readAbi(kernel, symbols) { integerVariable(symbols, name) }

    private fun <T> readAbi(kernel: ByteArray, symbols: UltraKernelSymbols, read: Reader.() -> T): T? {
        val start = symbols.offsetOf("__start_BTF") ?: return null
        return try {
            Reader(kernel, start, symbols.offsetOf("__stop_BTF") ?: kernel.size).read()
        } catch (error: Invalid) {
            throw UltraKernelGenerateException(UltraKernelGenerateCodes.STRUCT_OFFSET, "BTF_ABI_${error.message}")
        }
    }

    fun functionSignature(kernel: ByteArray, symbols: UltraKernelSymbols, name: String): String? {
        val start = symbols.offsetOf("__start_BTF") ?: return null
        return try {
            Reader(kernel, start, symbols.offsetOf("__stop_BTF") ?: kernel.size).functionSignature(name)
        } catch (error: Invalid) {
            throw UltraKernelGenerateException(UltraKernelGenerateCodes.STRUCT_OFFSET, "BTF_ABI_${error.message}")
        }
    }

    fun memberSignature(kernel: ByteArray, symbols: UltraKernelSymbols,
                        owner: String, member: String): String? {
        val start = symbols.offsetOf("__start_BTF") ?: return null
        return try {
            Reader(kernel, start, symbols.offsetOf("__stop_BTF") ?: kernel.size).memberSignature(owner, member)
        } catch (error: Invalid) {
            throw UltraKernelGenerateException(UltraKernelGenerateCodes.STRUCT_OFFSET, "BTF_ABI_${error.message}")
        }
    }

    /** One target parse for the complete imported-function/callback contract. */
    fun signatures(kernel: ByteArray, symbols: UltraKernelSymbols, functions: Set<String>,
                   members: Set<String>): Map<String, String>? {
        val start = symbols.offsetOf("__start_BTF") ?: return null
        return try {
            val reader = Reader(kernel, start, symbols.offsetOf("__stop_BTF") ?: kernel.size)
            val result = linkedMapOf<String, String>()
            for (key in functions.sorted()) result[key] = reader.functionSignature(key)
                ?: throw Invalid("FUNCTION_MISSING:$key")
            for (key in members.sorted()) {
                val separator = key.indexOf('.')
                requireBtf(separator > 0 && separator < key.lastIndex, "CALLBACK_QUERY:$key")
                result[key] = reader.memberSignature(key.substring(0, separator), key.substring(separator + 1))
                    ?: throw Invalid("CALLBACK_MISSING:$key")
            }
            result
        } catch (error: Invalid) {
            throw UltraKernelGenerateException(UltraKernelGenerateCodes.STRUCT_OFFSET, "BTF_ABI_${error.message}")
        }
    }

    fun functionParameterCount(kernel: ByteArray, symbols: UltraKernelSymbols, name: String): Int? {
        val start = symbols.offsetOf("__start_BTF") ?: return null
        return try {
            Reader(kernel, start, symbols.offsetOf("__stop_BTF") ?: kernel.size).functionParameterCount(name)
        } catch (error: Invalid) {
            throw UltraKernelGenerateException(UltraKernelGenerateCodes.STRUCT_OFFSET, "BTF_ABI_${error.message}")
        }
    }

    /** Actual target callbacks, rather than a compiler-version guess, anchor KCFI type IDs. */
    fun callbackTypeids(kernel: ByteArray, symbols: UltraKernelKallsymsTable,
                        callbacks: Map<String, String>): Map<String, Set<Int>> {
        val start = symbols.offsetOf("__start_BTF") ?: error("ULTRA_KERNEL_RESIDENT_ABI_BTF_REQUIRED")
        return try {
            Reader(kernel, start, symbols.offsetOf("__stop_BTF") ?: kernel.size)
                .callbackTypeids(symbols, callbacks)
        } catch (error: Invalid) {
            throw UltraKernelGenerateException(UltraKernelGenerateCodes.STRUCT_OFFSET, "BTF_CFI_${error.message}")
        }
    }

    fun managementCallbackTypeids(kernel: ByteArray, symbols: UltraKernelKallsymsTable,
        callbacks: Map<String, String>, origins: Map<String, UltraKernelResidentAbi.CallbackOrigin>): Map<String, Set<Int>> =
        readAbi(kernel, symbols) { managementCallbackTypeids(symbols, callbacks, origins) }
            ?: error("ULTRA_KERNEL_MANAGEMENT_ABI_BTF_REQUIRED")

    fun sessionCallbackTypeids(kernel: ByteArray, symbols: UltraKernelSymbols,
        callbacks: Map<String, String>): Map<String, Set<Int>> =
        readAbi(kernel, symbols) { sessionCallbackTypeids(symbols, callbacks) }
            ?: error("ULTRA_KERNEL_SESSION_ABI_BTF_REQUIRED")

    fun functionTypeids(kernel: ByteArray, symbols: UltraKernelSymbols,
        signatures: Set<String>): Map<String, Set<Int>> =
        readAbi(kernel, symbols) { functionTypeids(symbols, signatures) }
            ?: error("ULTRA_KERNEL_FUNCTION_ABI_BTF_REQUIRED")

    /** Named embedded members and complete sizes needed by the resident payload.
     * Optional means absent in this target, never a zero offset substitution. */
    fun abi(kernel: ByteArray, symbols: UltraKernelSymbols, needed: Set<String>,
            optional: Set<String> = emptySet()): UltraKernelLayout? {
        val start = symbols.offsetOf("__start_BTF") ?: return null
        return try {
            Reader(kernel, start, symbols.offsetOf("__stop_BTF") ?: kernel.size).abi(needed, optional)
        } catch (error: Invalid) {
            throw UltraKernelGenerateException(UltraKernelGenerateCodes.STRUCT_OFFSET, "BTF_ABI_${error.message}")
        }
    }

    fun layout(kernel: ByteArray, symbols: UltraKernelSymbols, needed: Set<String>? = null): UltraKernelLayout? {
        if (needed?.isEmpty() == true) return UltraKernelLayout(emptyMap())
        val start = symbols.offsetOf("__start_BTF") ?: return null
        return try {
            Reader(kernel, start, symbols.offsetOf("__stop_BTF") ?: kernel.size).layout(needed)
        } catch (error: Invalid) {
            throw UltraKernelGenerateException(UltraKernelGenerateCodes.STRUCT_OFFSET, "BTF_${error.message}")
        }
    }

    private class Invalid(reason: String) : IllegalStateException(reason)
    private fun requireBtf(valid: Boolean, reason: String) { if (!valid) throw Invalid(reason) }

    private class Reader(val data: ByteArray, val base: Int, val limit: Int) {
        private data class Type(val name: Int, val kind: Int, val count: Int,
                                val value: Int, val bits: Boolean, val extra: Int)
        private data class Field(val offset: Int, val type: Int, val width: Long)
        private val types = ArrayList<Type>()
        private val structs = HashMap<String, MutableList<Int>>()
        private val named = HashMap<String, MutableList<Int>>()
        private val strings: Int
        private val stringEnd: Int

        private fun u32(at: Int): Int {
            requireBtf(at >= base && at.toLong() + 4 <= limit, "TRUNCATED")
            return UltraKernelA64.le32Bits(data, at)
        }

        private fun range(start: Long, size: Long): Int {
            requireBtf(start >= base && size >= 0 && start + size <= limit, "BOUNDS")
            return start.toInt()
        }

        private fun name(offset: Int): String {
            requireBtf(offset >= 0 && offset < stringEnd - strings, "STRING")
            val start = strings + offset
            var end = start
            while (end < stringEnd && data[end] != 0.toByte()) end++
            requireBtf(end < stringEnd, "STRING")
            return String(data, start, end - start, Charsets.US_ASCII)
        }

        init {
            requireBtf(base >= 0 && limit <= data.size && limit.toLong() - base >= 24, "HEADER")
            requireBtf(u32(base) == 0x0001EB9F, "HEADER")
            val header = u32(base + 4).toLong()
            requireBtf(header >= 24, "HEADER")
            val origin = base.toLong() + header
            val typeSize = u32(base + 12).toLong()
            val typeOffset = u32(base + 8)
            val stringOffset = u32(base + 16)
            requireBtf(typeOffset >= 0 && stringOffset >= 0, "BOUNDS")
            var at = range(origin + typeOffset, typeSize)
            val end = at + typeSize.toInt()
            val stringSize = u32(base + 20).toLong()
            strings = range(origin + stringOffset, stringSize)
            stringEnd = strings + stringSize.toInt()
            requireBtf(stringSize > 0 && data[strings] == 0.toByte() &&
                (end <= strings || stringEnd <= at), "SECTIONS")
            types += Type(0, 0, 0, 0, false, 0)
            while (at < end) {
                requireBtf(end - at >= 12, "TYPE_RECORD")
                val info = u32(at + 4)
                val type = Type(u32(at), (info ushr 24) and 31, info and 65535,
                    u32(at + 8), info < 0, at + 12)
                val extra = when (type.kind) {
                    1, 14, 17 -> 4
                    3 -> 12
                    4, 5, 15, 19 -> type.count * 12
                    6, 13 -> type.count * 8
                    2, 7, 8, 9, 10, 11, 12, 16, 18 -> 0
                    else -> throw Invalid("KIND")
                }
                requireBtf(extra <= end - type.extra, "TYPE_RECORD")
                val typeName = name(type.name)
                if (type.kind == 4 && typeName.isNotEmpty())
                    structs.getOrPut(typeName) { ArrayList() } += types.size
                if (type.kind in setOf(4, 5, 8) && typeName.isNotEmpty())
                    named.getOrPut(typeName) { ArrayList() } += types.size
                types += type
                at = type.extra + extra
            }
        }

        private fun type(id: Int): Type {
            requireBtf(id > 0 && id < types.size, "TYPE_ID")
            return types[id]
        }

        private fun resolved(id: Int): Int {
            var at = id
            repeat(64) {
                val t = type(at)
                if (t.kind !in setOf(8, 9, 10, 11, 18)) return at
                at = t.value
            }
            throw Invalid("TYPE_CYCLE")
        }

        private fun size(id: Int, depth: Int = 0): Long {
            requireBtf(depth < 32, "TYPE_CYCLE")
            val t = type(resolved(id))
            return when (t.kind) {
                2 -> 8L
                1, 4, 5, 6, 16, 19 -> t.value.toLong().also { requireBtf(it >= 0, "TYPE_SIZE") }
                3 -> {
                    val element = size(u32(t.extra), depth + 1)
                    val count = u32(t.extra + 8).toLong()
                    requireBtf(count >= 0 && (count == 0L || element <= Long.MAX_VALUE / count), "ARRAY_SIZE")
                    element * count
                }
                else -> throw Invalid("FIELD_TYPE")
            }
        }

        private fun field(id: Int, wanted: String, prefix: Int = 0, depth: Int = 0): Field? {
            requireBtf(depth < 16, "MEMBER_CYCLE")
            val t = type(resolved(id))
            requireBtf(t.kind == 4 || t.kind == 5, "MEMBER_TYPE")
            val found = ArrayList<Field>()
            repeat(t.count) { index ->
                val at = t.extra + index * 12
                val memberName = name(u32(at))
                val memberType = u32(at + 4)
                val encoded = u32(at + 8)
                var bits = if (t.bits) (encoded and 0xFFFFFF).toLong() else encoded.toLong() and 0xFFFFFFFFL
                val nested = if (memberName.isEmpty() && type(resolved(memberType)).kind in 4..5)
                    field(memberType, wanted, depth = depth + 1) else null
                if (memberName == wanted || nested != null) {
                    val storage = size(memberType)
                    val encodedWidth = if (t.bits) encoded ushr 24 else 0
                    var widthBits = if (encodedWidth != 0) encodedWidth.toLong() else storage * 8
                    val valueType = type(resolved(memberType))
                    if (valueType.kind == 1) {
                        val encoding = u32(valueType.extra)
                        val intBits = encoding and 0xFF
                        val intOffset = (encoding ushr 16) and 0xFF
                        requireBtf(intBits > 0 && intBits + intOffset <= 128 &&
                            intBits + intOffset <= storage * 8, "MEMBER_INT")
                        if (t.bits) {
                            requireBtf(intOffset == 0 && intBits >= 8 && (intBits and (intBits - 1)) == 0, "MEMBER_INT")
                            requireBtf(encodedWidth <= intBits, "MEMBER_BITS")
                            widthBits = if (encodedWidth != 0) encodedWidth.toLong() else intBits.toLong()
                        } else {
                            bits += intOffset
                            widthBits = intBits.toLong()
                        }
                    } else if (encodedWidth != 0) {
                        requireBtf(valueType.kind in setOf(6, 19) && encodedWidth <= storage * 8, "MEMBER_BITS")
                    }
                    requireBtf(bits % 8 == 0L && widthBits > 0 && widthBits % 8 == 0L, "MEMBER_BITS")
                    requireBtf(storage > 0 && bits + widthBits <= t.value.toLong() * 8, "MEMBER_BOUNDS")
                    val relative = bits / 8 + (nested?.offset ?: 0)
                    requireBtf(prefix.toLong() + relative <= Int.MAX_VALUE, "MEMBER_OFFSET")
                    found += Field(prefix + relative.toInt(), nested?.type ?: memberType, nested?.width ?: (widthBits / 8))
                }
            }
            requireBtf(found.size <= 1, "MEMBER_AMBIGUOUS:$wanted")
            return found.singleOrNull()
        }

        private fun selectedField(id: Int, wanted: String, ancestors: Set<Int> = emptySet()): Field? {
            val ownerId = resolved(id)
            requireBtf(ancestors.size < 32 && ownerId !in ancestors, "SELECTED_MEMBER_CYCLE:$wanted")
            val owner = type(ownerId)
            requireBtf(owner.kind in 4..5 && owner.value > 0, "SELECTED_MEMBER_TYPE:$wanted")
            val found = ArrayList<Field>()
            repeat(owner.count) { index ->
                val at = owner.extra + index * 12
                val label = name(u32(at))
                val memberType = u32(at + 4)
                val encoded = u32(at + 8)
                val bits = if (owner.bits) (encoded and 0xffffff).toLong() else encoded.toLong() and 0xffffffffL
                val bitfield = owner.bits && encoded ushr 24 != 0
                val nestedType = type(resolved(memberType))
                val nested = if (label.isEmpty() && nestedType.kind in 4..5)
                    selectedField(memberType, wanted, ancestors + ownerId) else null
                if (label != wanted && nested == null) return@repeat
                requireBtf(!bitfield && bits % 8 == 0L, "SELECTED_MEMBER_BITS:$wanted")
                val storage = size(memberType)
                requireBtf(storage > 0 && bits + storage * 8 <= owner.value.toLong() * 8,
                    "SELECTED_MEMBER_BOUNDS:$wanted")
                if (nested == null && nestedType.kind == 1) {
                    val encoding = u32(nestedType.extra)
                    requireBtf((encoding ushr 16) and 0xff == 0 && (encoding and 0xff).toLong() == storage * 8,
                        "SELECTED_MEMBER_INT:$wanted")
                }
                val offset = bits / 8 + (nested?.offset ?: 0)
                val width = nested?.width ?: storage
                requireBtf(offset <= Int.MAX_VALUE && width > 0 && offset + width <= owner.value,
                    "SELECTED_MEMBER_BOUNDS:$wanted")
                found += Field(offset.toInt(), nested?.type ?: memberType, width)
            }
            requireBtf(found.size <= 1, "SELECTED_MEMBER_AMBIGUOUS:$wanted")
            return found.singleOrNull()
        }

        fun selectedStructure(wanted: String, members: Set<String>): UltraKernelResidentAbi.CompiledLayout {
            requireBtf(wanted.isNotEmpty() && members.isNotEmpty() && members.none(String::isEmpty), "SELECTED_STRUCTURE_QUERY")
            val candidates = named[wanted].orEmpty().map(::resolved).distinct()
                .filter { type(it).kind == 4 && type(it).value > 0 }
            requireBtf(candidates.isNotEmpty(), "SELECTED_STRUCTURE_MISSING:$wanted")
            val values = candidates.map { owner ->
                val fields = members.sorted().associateWith { member ->
                    val value = selectedField(owner, member) ?: throw Invalid("SELECTED_MEMBER_MISSING:$wanted.$member")
                    requireBtf(value.width <= Int.MAX_VALUE, "SELECTED_MEMBER_WIDTH:$wanted.$member")
                    UltraKernelResidentAbi.CompiledMember(value.offset, value.width.toInt(), signature(value.type))
                }
                UltraKernelResidentAbi.CompiledLayout(type(owner).value, signature(owner), fields)
            }
            val selected = values.first()
            requireBtf(values.all { it.bytes == selected.bytes && it.members == selected.members },
                "SELECTED_STRUCTURE_AMBIGUOUS:$wanted")
            return selected
        }

        fun verifyFunctionMarker(symbols: UltraKernelSymbols, wanted: String, expected: String, typeid: Int?) {
            requireBtf(symbols is UltraKernelKallsymsTable, "FUNCTION_MARKER_SYMBOL_SOURCE:$wanted")
            fun boundary(name: String) = symbols.offsetsOf(name).singleOrNull()
                ?: throw Invalid("FUNCTION_MARKER_BOUNDARY:$name")
            val entry = symbols.offsetsOf(wanted).singleOrNull() ?: throw Invalid("FUNCTION_MARKER_SYMBOL:$wanted")
            val start = boundary("_stext")
            val end = boundary("_etext")
            requireBtf(start >= 0 && start < end && end <= data.size && entry >= start &&
                entry.toLong() + 4 <= end && entry % 4 == 0 && functionSignature(wanted) == expected,
                "FUNCTION_MARKER_IDENTITY:$wanted")
            if (typeid != null) requireBtf(entry >= start + 4 && UltraKernelA64.le32Bits(data, entry - 4) == typeid,
                "FUNCTION_MARKER_KCFI:$wanted")
        }

        private fun member(owner: Int, wanted: String, bytes: Int): Field {
            val f = field(owner, wanted) ?: throw Invalid("MEMBER_MISSING:$wanted")
            requireBtf(f.width == bytes.toLong(), "MEMBER_SIZE:$wanted")
            return f
        }

        fun abi(needed: Set<String>, optional: Set<String>): UltraKernelLayout {
            requireBtf(optional.all { it in needed }, "OPTIONAL_QUERY")
            val result = linkedMapOf<String, UltraKernelLayoutField>()
            for (query in needed) {
                val parts = query.split('.')
                requireBtf(parts.size >= 2 && parts.none(String::isEmpty), "QUERY:$query")
                val candidates = named[parts.first()].orEmpty().map(::resolved).distinct()
                    .filter { type(it).kind in 4..5 && type(it).value >= 0 &&
                        (type(it).value > 0 || parts.size == 2 && parts[1] == "__size") }
                if (candidates.isEmpty() && query in optional) continue
                requireBtf(candidates.isNotEmpty(), "TYPE_MISSING:${parts.first()}")
                val values = candidates.map { owner ->
                    if (parts.size == 2 && parts[1] == "__size") {
                        UltraKernelLayoutField(0, type(owner).value, type(owner).value, "btf")
                    } else {
                        var cursor = owner
                        var offset = 0L
                        var width = 0L
                        var present = true
                        for ((index, memberName) in parts.drop(1).withIndex()) {
                            val value = field(cursor, memberName)
                            if (value == null) { present = false; break }
                            offset += value.offset
                            width = value.width
                            cursor = resolved(value.type)
                            if (index != parts.size - 2)
                                requireBtf(type(cursor).kind in 4..5, "EMBEDDED_TYPE:$query")
                        }
                        if (!present) null else {
                            requireBtf(width in 1..Int.MAX_VALUE.toLong() && offset >= 0 &&
                                offset + width <= type(owner).value, "MEMBER_BOUNDS:$query")
                            UltraKernelLayoutField(offset.toInt(), width.toInt(), type(owner).value, "btf")
                        }
                    }
                }.distinct()
                requireBtf(values.size == 1, "TYPE_AMBIGUOUS:$query")
                val value = values.single()
                if (value == null) requireBtf(query in optional, "MEMBER_MISSING:$query")
                else result[query] = value
            }
            return UltraKernelLayout(result)
        }

        fun functionParameterCount(wanted: String): Int? {
            val functions = types.filter { it.kind == 12 && name(it.name) == wanted }
            val values = functions.map {
                val prototype = type(resolved(it.value))
                requireBtf(prototype.kind == 13, "FUNCTION_PROTO:$wanted")
                prototype.count
            }.distinct()
            requireBtf(values.size <= 1, "FUNCTION_AMBIGUOUS:$wanted")
            return values.singleOrNull()
        }

        fun parameterSignature(wanted: String, parameter: Int): String? {
            requireBtf(parameter >= 0, "PARAMETER_INDEX:$wanted")
            val values = types.filter { it.kind == 12 && name(it.name) == wanted }.map {
                val prototype = type(resolved(it.value))
                requireBtf(prototype.kind == 13 && parameter < prototype.count,
                    "PARAMETER_MISSING:$wanted:$parameter")
                signature(u32(prototype.extra + parameter * 8 + 4))
            }.distinct()
            requireBtf(values.size <= 1, "PARAMETER_AMBIGUOUS:$wanted:$parameter")
            return values.singleOrNull()
        }

        fun typedefSignature(wanted: String): String? {
            val values = named[wanted].orEmpty().filter { type(it).kind == 8 }
                .map { signature(it) }.distinct()
            requireBtf(values.size <= 1, "TYPEDEF_AMBIGUOUS:$wanted")
            return values.singleOrNull()
        }

        fun namedSignature(wanted: String): String? {
            val values = named[wanted].orEmpty().map { signature(it) }.distinct()
            requireBtf(values.size <= 1, "NAMED_TYPE_AMBIGUOUS:$wanted")
            return values.singleOrNull()
        }

        fun variableSignature(wanted: String): String? {
            val values = types.filter { it.kind == 14 && name(it.name) == wanted }
                .map { signature(it.value) }.distinct()
            requireBtf(values.size <= 1, "VARIABLE_AMBIGUOUS:$wanted")
            return values.singleOrNull()
        }

        fun dataObjectSize(wanted: String, expected: String): Long {
            requireBtf(expected.isNotEmpty() && expected.length <= 65536, "VARIABLE_SIGNATURE:$wanted")
            val variables = types.filter { it.kind == 14 && name(it.name) == wanted }
            if (variables.isNotEmpty()) {
                requireBtf(variables.map { signature(it.value) }.distinct() == listOf(expected),
                    "VARIABLE_TYPE:$wanted")
                val sizes = variables.map { size(it.value) }.distinct()
                requireBtf(sizes.size == 1 && sizes.single() > 0, "VARIABLE_SIZE:$wanted")
                return sizes.single()
            }
            // Vendor Images may omit VAR records while retaining the complete type
            // graph. The bound reference declaration supplies symbol/type identity;
            // compare its full canonical shape and derive size from actual target BTF.
            val kinds = when (expected.first()) {
                'i' -> setOf(1)
                '*' -> setOf(2)
                'a' -> setOf(3)
                's' -> setOf(4)
                'u' -> setOf(5)
                'e' -> setOf(6, 19)
                else -> throw Invalid("VARIABLE_OBJECT_TYPE:$wanted")
            }
            val label = if (expected.first() in "su") expected.indexOf(':').let { at ->
                requireBtf(at > 0, "VARIABLE_OBJECT_TYPE:$wanted")
                expected.substring(1, at)
            } else null
            val candidates = types.indices.filter { index ->
                val record = types[index]
                record.kind in kinds && (label == null || name(record.name) == label)
            }
            val shapes = candidates.mapNotNull { index ->
                try { index to signature(index) } catch (error: Invalid) {
                    if (label != null) throw error
                    null // Unrelated anonymous graphs need not fit this object's signature budget.
                }
            }.toMap()
            requireBtf(label == null || label.isEmpty() || shapes.values.distinct() == listOf(expected),
                "VARIABLE_OBJECT_AMBIGUOUS:$wanted")
            val sizes = shapes.filterValues { it == expected }.keys.map { size(it) }.distinct()
            requireBtf(sizes.size == 1 && sizes.single() > 0, "VARIABLE_OBJECT_MISSING:$wanted")
            return sizes.single()
        }

        fun enumValue(wanted: String): Long? {
            val values = linkedSetOf<Long>()
            for (record in types.filter { it.kind == 6 || it.kind == 19 }) {
                val stride = if (record.kind == 6) 8 else 12
                repeat(record.count) { index ->
                    val at = record.extra + index * stride
                    if (name(u32(at)) == wanted) {
                        val low = u32(at + 4).toLong() and 0xffffffffL
                        values += if (record.kind == 19) low or (u32(at + 8).toLong() shl 32) else low
                    }
                }
            }
            requireBtf(values.size <= 1, "ENUM_AMBIGUOUS:$wanted")
            return values.singleOrNull()
        }

        fun integerVariable(symbols: UltraKernelSymbols, wanted: String): Long? {
            val variables = types.filter { it.kind == 14 && name(it.name) == wanted }
            if (variables.isEmpty()) return null
            val records = variables.map { type(resolved(it.value)) }.distinct()
            requireBtf(records.size == 1, "VARIABLE_AMBIGUOUS:$wanted")
            val scalar = records.single()
            requireBtf(scalar.kind == 1 && scalar.value == 4 &&
                u32(scalar.extra) and 0xffffff == 32, "VARIABLE_SCALAR:$wanted")
            val offset = symbols.offsetsOf(wanted).singleOrNull()
                ?: throw Invalid("VARIABLE_ADDRESS:$wanted")
            requireBtf(offset >= 0 && offset.toLong() + 4 <= data.size, "VARIABLE_BOUNDS:$wanted")
            return UltraKernelA64.le32Bits(data, offset).toLong() and 0xffffffffL
        }

        private fun sessionReferences() {
            fun reference(id: Int) = requireBtf(id >= 0 && id < types.size, "TYPE_ID")
            for (record in types.drop(1)) when (record.kind) {
                2, 8, 9, 10, 11, 12, 14, 17, 18 -> reference(record.value)
                3 -> { reference(u32(record.extra)); reference(u32(record.extra + 4)) }
                4, 5 -> repeat(record.count) { reference(u32(record.extra + it * 12 + 4)) }
                13 -> {
                    reference(record.value)
                    repeat(record.count) { reference(u32(record.extra + it * 8 + 4)) }
                }
                15 -> repeat(record.count) { reference(u32(record.extra + it * 12)) }
            }
        }

        private fun sessionMember(owner: String, member: String): Field {
            val fields = named[owner].orEmpty().map(::resolved).distinct()
                .filter { type(it).kind in 4..5 }.mapNotNull { field(it, member) }
            requireBtf(fields.isNotEmpty() && fields.map {
                Triple(it.offset, it.width, signature(it.type, semantic = true))
            }.distinct().size == 1, "SESSION_MEMBER_AMBIGUOUS:$owner.$member")
            return fields.first()
        }

        private fun sessionPreserves(word: Int, register: Int): Boolean {
            if (word == 0xd503201f.toInt() || word and 0xffe0001f.toInt() == 0xd4200000.toInt()) return true
            if (word and 0xff000010.toInt() == 0x54000000 ||
                word and 0x7e000000 == 0x34000000 || word and 0x7e000000 == 0x36000000) return true
            if (word and 0x3f000000 == 0x39000000) // Scalar unsigned load/store; no base writeback.
                return word ushr 22 and 3 == 0 || word and 31 != register
            if (word and 0xffe00c00.toInt() == 0xb8400000.toInt() ||
                word and 0xffe00c00.toInt() == 0xf8400000.toInt()) return word and 31 != register
            if (word and 0x1f800000 == 0x12800000 || word and 0x1f000000 == 0x11000000 ||
                word and 0x1f000000 == 0x0a000000 || word and 0x1f000000 == 0x0b000000)
                return word and 31 != register
            return false
        }

        private fun sessionLoad(before: Int, start: Int, register: Int, offset: Int): Pair<Int, Int>? {
            var at = before - 4
            while (at >= start) {
                val word = UltraKernelA64.le32Bits(data, at)
                if (word and 0xffc00000.toInt() == 0xf9400000.toInt() && word and 31 == register) {
                    return if ((word ushr 10 and 4095) * 8 == offset) at to (word ushr 5 and 31) else null
                }
                if (!sessionPreserves(word, register)) return null
                at -= 4
            }
            return null
        }

        private fun sessionArgument(from: Int, until: Int, source: Int, argument: Int, call: Int): Boolean {
            if (source == 31) return false
            val aliases = IntArray(32) { it }
            var at = from
            while (at < until) {
                val word = UltraKernelA64.le32Bits(data, at)
                val destination = word and 31
                when {
                    word and 0xffe0ffe0.toInt() == 0xaa0003e0.toInt() ->
                        aliases[destination] = aliases[word ushr 16 and 31]
                    word and 0x3f000000 == 0x39000000 ->
                        if (word ushr 22 and 3 != 0) aliases[destination] = -1
                    word and 0x1f800000 == 0x12800000 || word and 0x1f000000 == 0x11000000 ||
                        word and 0x1f000000 == 0x0a000000 || word and 0x1f000000 == 0x0b000000 ->
                        if (destination != 31) aliases[destination] = -1
                    word and 0x7e000000 == 0x34000000 -> {
                        val encoded = word ushr 5 and 0x7ffff
                        val delta = (encoded shl 13) shr 11
                        if (at.toLong() + delta < call.toLong() + 4) return false
                    }
                    word == 0xd503201f.toInt() -> Unit
                    else -> return false
                }
                at += 4
            }
            return aliases[argument] == source
        }

        private fun sessionPointerPreserves(word: Int, register: Int): Boolean {
            if (word and 0xfc000000.toInt() == 0x94000000.toInt() ||
                word and 0xfffffc1f.toInt() == 0xd63f0000.toInt()) return register in 19..29
            if (word and 0xffe0001f.toInt() == 0xd4200000.toInt()) return true
            if (word and 0xfc000000.toInt() == 0x14000000 ||
                word and 0xff000010.toInt() == 0x54000000 ||
                word and 0x7e000000 == 0x34000000 || word and 0x7e000000 == 0x36000000) return true
            if (word in setOf(0xd503201f.toInt(), 0xd503233f.toInt(), 0xd50323bf.toInt(),
                    0xd503245f.toInt(), 0xd503249f.toInt(), 0xd50324df.toInt()) ||
                word and 0xfffff09f.toInt() == 0xd503309f.toInt()) return register != 30
            if (word and 0xfff00000.toInt() == 0xd5300000.toInt()) return word and 31 != register
            if (word and 0xfff00000.toInt() == 0xd5100000.toInt()) return true
            if (word and 0x3f000000 == 0x39000000)
                return word ushr 22 and 3 == 0 || word and 31 != register
            if (word and 0x3f000000 == 0x38000000) {
                val writeback = word and 0x00200000 == 0 && (word ushr 10 and 3) in setOf(1, 3)
                return (!writeback || word ushr 5 and 31 != register) &&
                    (word ushr 22 and 3 == 0 || word and 31 != register)
            }
            if (word and 0x3e000000 == 0x28000000) {
                val writeback = (word ushr 23 and 3) in setOf(1, 3)
                return (!writeback || word ushr 5 and 31 != register) &&
                    (word and 0x00400000 == 0 ||
                        (word and 31 != register && word ushr 10 and 31 != register))
            }
            if (word and 0x1c000000 == 0x10000000 || word and 0x0e000000 == 0x0a000000)
                return word and 31 != register
            return false
        }

        /** Every native predecessor must retain a typed caller argument; loops add no assumptions. */
        private fun sessionPointerArgument(entry: Int, end: Int, before: Int, register: Int,
            parameters: Set<Int>): Boolean {
            if (register == 31 || parameters.isEmpty() || end - entry > 262144) return false
            val predecessors = hashMapOf<Int, MutableList<Int>>()
            val successors = hashMapOf<Int, MutableList<Int>>()
            fun edge(from: Int, target: Int) {
                if (target >= entry && target < end && target % 4 == 0) {
                    predecessors.getOrPut(target) { arrayListOf() }.add(from)
                    successors.getOrPut(from) { arrayListOf() }.add(target)
                }
            }
            var at = entry
            while (at < end) {
                val word = UltraKernelA64.le32Bits(data, at)
                when {
                    word and 0xfc000000.toInt() == 0x14000000 ->
                        edge(at, at + ((word shl 6) shr 4))
                    word and 0xff000010.toInt() == 0x54000000 ||
                        word and 0x7e000000 == 0x34000000 -> {
                        edge(at, at + (((word ushr 5 and 0x7ffff) shl 13) shr 11))
                        edge(at, at + 4)
                    }
                    word and 0x7e000000 == 0x36000000 -> {
                        edge(at, at + (((word ushr 5 and 0x3fff) shl 18) shr 16))
                        edge(at, at + 4)
                    }
                    word and 0xfffffc1f.toInt() == 0xd65f0000.toInt() ||
                        word and 0xfffffc1f.toInt() == 0xd61f0000.toInt() -> Unit
                    else -> edge(at, at + 4)
                }
                at += 4
            }
            // Dead native blocks may branch into a live loop. They are not
            // predecessors of this caller unless the function entry reaches them.
            val reachable = hashSetOf<Int>()
            val forward = java.util.ArrayDeque<Int>()
            forward.add(entry)
            while (forward.isNotEmpty()) {
                val position = forward.removeFirst()
                if (!reachable.add(position)) continue
                for (target in successors[position].orEmpty()) forward.add(target)
            }
            if (before !in reachable) return false
            val pending = java.util.ArrayDeque<Pair<Int, Int>>()
            val visited = hashSetOf<Pair<Int, Int>>()
            pending.add(before to register)
            var reachedEntry = false
            while (pending.isNotEmpty()) {
                val state = pending.removeFirst()
                if (!visited.add(state)) continue
                if (visited.size > 65536) return false
                val (position, value) = state
                if (position == entry) {
                    if (value !in parameters) return false
                    reachedEntry = true
                    continue
                }
                val incoming = predecessors[position]?.filter { it in reachable } ?: return false
                if (incoming.isEmpty()) return false
                for (previous in incoming) {
                    val word = UltraKernelA64.le32Bits(data, previous)
                    val source = if (word and 0xffe0ffe0.toInt() == 0xaa0003e0.toInt() &&
                        word and 31 == value) word ushr 16 and 31 else {
                        if (!sessionPointerPreserves(word, value)) return false
                        value
                    }
                    if (source == 31) return false
                    pending.add(previous to source)
                }
            }
            return reachedEntry
        }

        private fun sessionCallTypeid(call: Int, functionRegister: Int): Int? {
            if (functionRegister in setOf(16, 17, 31) || call < 24) return null
            fun word(back: Int) = UltraKernelA64.le32Bits(data, call - back)
            if (word(24) and 0xfffffc1f.toInt() != 0xb85fc010.toInt() ||
                word(24) ushr 5 and 31 != functionRegister || word(12) != 0x6b11021f ||
                word(8) != 0x54000040 || word(4) and 0xffe0001f.toInt() != 0xd4200000.toInt() ||
                (word(4) ushr 5 and 65535) != (0x8000 or (17 shl 5) or functionRegister)) return null
            var value = 0
            var known = 0
            for (back in listOf(20, 16)) {
                val move = word(back)
                if (move and 31 != 17 || move ushr 31 != 0) return null
                val shift = (move ushr 21 and 3) * 16
                if (shift > 16) return null
                val immediate = (move ushr 5 and 65535) shl shift
                when (move and 0x7f800000) {
                    0x12800000 -> { value = immediate.inv(); known = -1 }
                    0x52800000 -> { value = immediate; known = -1 }
                    0x72800000 -> {
                        val mask = 65535 shl shift
                        value = (value and mask.inv()) or immediate
                        known = known or mask
                    }
                    else -> return null
                }
            }
            return if (known == -1) value else null
        }

        /** Native caller guards prove the type actually used for each callback member. */
        fun sessionCallbackTypeids(symbols: UltraKernelSymbols,
            callbacks: Map<String, String>): Map<String, Set<Int>> {
            requireBtf(symbols is UltraKernelKallsymsTable && callbacks.isNotEmpty() &&
                callbacks.values.all { it.startsWith("f") && it.length <= 65536 }, "SESSION_CALLER_SOURCE")
            sessionReferences()
            val sources = mapOf(
                "file_operations.unlocked_ioctl" to listOf("vfs_ioctl", "do_vfs_ioctl", "__arm64_sys_ioctl"),
                "file_operations.compat_ioctl" to listOf("compat_ptr_ioctl", "__arm64_compat_sys_ioctl"),
                "file_operations.flush" to listOf("filp_close"),
                "file_operations.release" to listOf("__fput"),
                "callback_head.func" to listOf("rcu_do_batch", "srcu_invoke_callbacks", "rcu_core"),
                "work_struct.func" to listOf("process_one_work", "process_scheduled_works", "worker_thread", "rescuer_thread"))
            requireBtf(sources.keys.containsAll(callbacks.keys), "SESSION_CALLER_MEMBERS")
            fun boundary(name: String) = symbols.offsetsOf(name).distinct().singleOrNull()
                ?: throw Invalid("SESSION_CALLER_BOUNDARY:$name")
            val textStart = boundary("_stext")
            val textEnd = boundary("_etext")
            requireBtf(textStart >= 0 && textStart < textEnd && textEnd <= data.size, "SESSION_CALLER_TEXT")
            val result = linkedMapOf<String, Set<Int>>()
            for ((key, expected) in callbacks) {
                val owner = key.substringBefore('.')
                val member = key.substringAfter('.')
                val callback = sessionMember(owner, member)
                requireBtf(callback.width == 8L && signature(callback.type) == "*" + expected,
                    "SESSION_CALLER_MEMBER:$key")
                val loaded = if (key == "work_struct.func") sessionMember("worker", "current_func") else callback
                requireBtf(loaded.width == 8L && signature(loaded.type, semantic = true) ==
                    signature(callback.type, semantic = true), "SESSION_CALLER_SEMANTIC_TYPE:$key")
                val fileOperations = if (owner == "file_operations") sessionMember("file", "f_op") else null
                if (fileOperations != null) requireBtf(fileOperations.width == 8L &&
                    signature(fileOperations.type) == "*sfile_operations",
                    "SESSION_CALLER_FILE_OPERATIONS")
                val ids = linkedSetOf<Int>()
                var matches = 0
                for (caller in sources.getValue(key)) {
                    val declarations = types.indices.filter { types[it].kind == 12 && name(types[it].name) == caller }
                    if (declarations.isEmpty()) continue
                    requireBtf(declarations.map { signature(it, semantic = true) }.distinct().size == 1,
                        "SESSION_CALLER_DECLARATION:$caller")
                    val workerParameters = if (key == "work_struct.func") {
                        val alternatives = declarations.map { declaration ->
                            val prototype = type(resolved(type(declaration).value))
                            requireBtf(prototype.kind == 13, "SESSION_CALLER_PROTOTYPE:$caller")
                            (0 until minOf(prototype.count, 8)).filterTo(linkedSetOf()) { parameter ->
                                signature(u32(prototype.extra + parameter * 8 + 4), semantic = true) == "*sworker"
                            }
                        }.distinct()
                        requireBtf(alternatives.size == 1, "SESSION_CALLER_PARAMETER:$caller")
                        alternatives.single()
                    } else emptySet()
                    if (key == "work_struct.func" && workerParameters.isEmpty()) continue
                    for (entry in symbols.offsetsOf(caller).distinct()) {
                        requireBtf(entry >= textStart && entry % 4 == 0 && symbols.nameAt(entry) == caller,
                            "SESSION_CALLER_ADDRESS:$caller")
                        val end = symbols.nextOffset(entry) ?: throw Invalid("SESSION_CALLER_EXTENT:$caller")
                        requireBtf(end > entry && end <= textEnd, "SESSION_CALLER_EXTENT:$caller")
                        var call = entry
                        while (call.toLong() + 4 <= end) {
                            val instruction = UltraKernelA64.le32Bits(data, call)
                            if (instruction and 0xfffffc1f.toInt() == 0xd63f0000.toInt()) {
                                val register = instruction ushr 5 and 31
                                val load = sessionLoad(call, entry, register, loaded.offset)
                                if (load != null) {
                                    val parent = if (fileOperations == null) null else
                                        sessionLoad(load.first, entry, load.second, fileOperations.offset)
                                    val origin = if (fileOperations != null) parent else load
                                    val argument = if (key == "file_operations.release") 1 else 0
                                    val typedSource = if (key == "work_struct.func")
                                        sessionPointerArgument(entry, end, load.first, load.second, workerParameters)
                                    else origin != null &&
                                        sessionArgument(origin.first, call - 24, origin.second, argument, call)
                                    if (typedSource) {
                                        val typeid = sessionCallTypeid(call, register)
                                            ?: throw Invalid("SESSION_CALLER_GUARD:$key:$caller:$call")
                                        ids += typeid
                                        matches++
                                    }
                                }
                            }
                            call += 4
                        }
                    }
                }
                requireBtf(matches > 0 && ids.size == 1, "SESSION_CALLER_TYPEID_MISSING_OR_AMBIGUOUS:$key")
                result[key] = ids
            }
            return result
        }

        fun functionTypeids(symbols: UltraKernelSymbols,
            signatures: Set<String>): Map<String, Set<Int>> {
            fun boundary(name: String) = symbols.offsetsOf(name).distinct().singleOrNull()
                ?: throw Invalid("FUNCTION_TYPEID_BOUNDARY:$name")
            val textStart = boundary("_stext")
            val textEnd = boundary("_etext")
            requireBtf(textStart >= 0 && textStart < textEnd && textEnd <= data.size,
                "FUNCTION_TYPEID_SECTION_BOUNDS")
            requireBtf(signatures.all { it.startsWith("f") && it.length <= 65536 }, "FUNCTION_TYPEID_SIGNATURE")
            val result = signatures.sorted().associateWith { linkedSetOf<Int>() }
            val prototypes = hashMapOf<Int, String>()
            fun candidateSignature(index: Int): String = prototypes.getOrPut(types[index].value) {
                try { signature(index) } catch (error: Invalid) {
                    if (error.message != "SIGNATURE_SIZE") throw error
                    // A longer canonical signature cannot equal any bounded request.
                    ""
                }
            }
            val functions = types.indices.filter { types[it].kind == 12 }
            val namedFunctions = functions.groupBy { name(types[it].name) }
            for (index in functions) {
                val function = types[index]
                val expected = candidateSignature(index)
                val ids = result[expected] ?: continue
                val functionName = name(function.name)
                val addresses = symbols.offsetsOf(functionName).distinct()
                requireBtf(addresses.size <= 1, "FUNCTION_TYPEID_ADDRESS_AMBIGUOUS:$functionName")
                val entry = addresses.singleOrNull() ?: continue
                // Do not turn a synthesized LTO alias into a target type-ID anchor.
                if (symbols.nameAt(entry) != functionName) continue
                if (entry.toLong() < textStart.toLong() + 4 ||
                    entry.toLong() + 4 > textEnd || entry % 4 != 0) continue
                requireBtf(namedFunctions.getValue(functionName).all {
                    candidateSignature(it) == expected
                }, "FUNCTION_TYPEID_FUNCTION_AMBIGUOUS:$functionName")
                ids += UltraKernelA64.le32Bits(data, entry - 4)
            }
            for ((expected, ids) in result)
                requireBtf(ids.isNotEmpty(), "FUNCTION_TYPEID_MISSING:$expected")
            return result
        }

        fun callbackTypeids(symbols: UltraKernelKallsymsTable,
                            callbacks: Map<String, String>): Map<String, Set<Int>> {
            fun boundary(name: String) = symbols.offsetsOf(name).singleOrNull()
                ?: throw Invalid("BOUNDARY:$name")
            val roStart = boundary("__start_rodata")
            val roEnd = boundary("__end_rodata")
            val textStart = boundary("_stext")
            val textEnd = boundary("_etext")
            requireBtf(textStart >= 0 && textEnd <= roStart && roStart < roEnd && roEnd <= data.size,
                "SECTION_BOUNDS")
            val layout = abi(callbacks.keys, emptySet())
            val opsBytes = abi(setOf("fsnotify_ops.__size"), emptySet())
                .fields.getValue("fsnotify_ops.__size").widthBytes
            val tables = listOf("inotify_fsnotify_ops", "fanotify_fsnotify_ops", "dnotify_fsnotify_ops")
                .flatMap(symbols::offsetsOf).distinct()
            requireBtf(tables.isNotEmpty(), "OPS_MISSING")
            val result = linkedMapOf<String, Set<Int>>()
            for ((key, expected) in callbacks) {
                requireBtf(expected.startsWith("*f"), "CALLBACK_TYPE:$key")
                val field = layout.fields.getValue(key)
                requireBtf(field.widthBytes == 8 && field.offsetBytes % 8 == 0, "CALLBACK_FIELD:$key")
                val ids = linkedSetOf<Int>()
                for (table in tables) {
                    requireBtf(table >= roStart && table.toLong() + opsBytes <= roEnd, "OPS_RO_BOUNDS")
                    val pointer = java.nio.ByteBuffer.wrap(data, table + field.offsetBytes, 8)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN).long
                    if (pointer == 0L) continue
                    val offset = pointer - symbols.textBase
                    requireBtf(java.lang.Long.compareUnsigned(pointer, symbols.textBase) >= 0 &&
                        offset >= textStart.toLong() + 4 && offset + 4 <= textEnd && offset % 4 == 0L,
                        "CALLBACK_TEXT:$key")
                    val entry = offset.toInt()
                    val functions = types.indices.filter { types[it].kind == 12 &&
                        symbols.offsetOf(name(types[it].name)) == entry }
                    requireBtf(functions.isNotEmpty() && functions.all { signature(it) == expected.drop(1) },
                        "CALLBACK_FUNCTION:$key")
                    ids += UltraKernelA64.le32Bits(data, entry - 4)
                }
                requireBtf(ids.size == 1, "CALLBACK_TYPEID_MISSING_OR_AMBIGUOUS:$key")
                result[key] = ids
            }
            return result
        }

        fun managementCallbackTypeids(symbols: UltraKernelKallsymsTable,
            callbacks: Map<String, String>, origins: Map<String, UltraKernelResidentAbi.CallbackOrigin>): Map<String, Set<Int>> {
            fun boundary(name: String) = symbols.offsetsOf(name).singleOrNull()
                ?: throw Invalid("BOUNDARY:$name")
            val textStart = boundary("_stext")
            val textEnd = boundary("_etext")
            val dataStart = boundary("_data")
            val dataEnd = boundary("_edata")
            requireBtf(textStart >= 0 && textStart < textEnd && textEnd <= dataStart &&
                dataStart < dataEnd && dataEnd <= data.size, "MANAGEMENT_SECTION_BOUNDS")
            fun nativeTypeid(entry: Int, expected: String, key: String): Int {
                requireBtf(entry >= textStart + 4 && entry.toLong() + 4 <= textEnd && entry % 4 == 0,
                    "MANAGEMENT_CALLBACK_TEXT:$key")
                val functions = types.indices.filter { types[it].kind == 12 &&
                    symbols.offsetsOf(name(types[it].name)).singleOrNull() == entry }
                requireBtf(functions.isNotEmpty() && functions.all { signature(it) == expected.removePrefix("*") },
                    "MANAGEMENT_CALLBACK_FUNCTION:$key")
                return UltraKernelA64.le32Bits(data, entry - 4)
            }
            val result = linkedMapOf<String, Set<Int>>()
            for ((key, origin) in origins) {
                val expected = callbacks.getValue(key)
                val ids = linkedSetOf<Int>()
                when (origin.kind) {
                    "tracepoint" -> {
                        val stub = "__probestub_$key"
                        for (entry in symbols.offsetsOf(stub)) ids += nativeTypeid(entry, expected, key)
                    }
                    "work" -> {
                        val layout = abi(setOf("work_struct.func", "work_struct.__size"), emptySet()).fields
                        val field = layout.getValue("work_struct.func")
                        val bytes = layout.getValue("work_struct.__size").widthBytes
                        requireBtf(field.widthBytes == 8, "WORK_CALLBACK_POINTER")
                        for (variable in types.filter { it.kind == 14 }) {
                            val target = type(resolved(variable.value))
                            if (target.kind != 4 || name(target.name) != "work_struct") continue
                            for (at in symbols.offsetsOf(name(variable.name))) {
                                if (at < dataStart || at.toLong() + bytes > dataEnd) continue
                                val pointer = java.nio.ByteBuffer.wrap(data, at + field.offsetBytes, 8)
                                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).long
                                if (pointer == 0L) continue
                                val offset = pointer - symbols.textBase
                                requireBtf(java.lang.Long.compareUnsigned(pointer, symbols.textBase) >= 0 &&
                                    offset in 0..Int.MAX_VALUE.toLong(), "WORK_CALLBACK_ADDRESS:$key")
                                ids += nativeTypeid(offset.toInt(), expected, key)
                            }
                        }
                    }
                    "rcu" -> {
                        requireBtf(key == "msu_uid_snapshot_reclaim_rcu" && origin.source == "call_rcu" &&
                            origin.parameterIndex == 1 && origin.secondarySource == "rcu_callback_t", "RCU_CALLBACK_ORIGIN:$key")
                        for (index in types.indices.filter { types[it].kind == 12 && signature(it) == expected.removePrefix("*") })
                            for (entry in symbols.offsetsOf(name(types[index].name)))
                                if (entry >= textStart + 4 && entry.toLong() + 4 <= textEnd && entry % 4 == 0)
                                    ids += nativeTypeid(entry, expected, key)
                    }
                    "internal" -> continue // Proven by the same payload object's call-site type, not this kernel.
                    else -> throw Invalid("MANAGEMENT_CALLBACK_ORIGIN:$key")
                }
                requireBtf(ids.size == 1, "MANAGEMENT_CALLBACK_TYPEID_MISSING_OR_AMBIGUOUS:$key")
                result[key] = ids
            }
            return result
        }

        /** Byte-for-byte encoding shared with scripts/policy_btf_abi.py. */
        private fun signature(index: Int, depth: Int = 0, pointer: Boolean = false,
            semantic: Boolean = false): String {
            if (index == 0) return "v"
            requireBtf(depth < 32, "SIGNATURE_CYCLE")
            var id = index
            var record: Type? = null
            val qualifiers = StringBuilder()
            for (ignored in 0 until 64) {
                if (id == 0) {
                    requireBtf(qualifiers.length < 65536, "SIGNATURE_SIZE")
                    return qualifiers.toString() + "v"
                }
                val candidate = type(id)
                if (candidate.kind !in setOf(8, 9, 10, 11, 18)) { record = candidate; break }
                if (semantic) when (candidate.kind) {
                    9 -> qualifiers.append("V")
                    10 -> qualifiers.append("C")
                    11 -> qualifiers.append("R")
                    18 -> { val tag = name(candidate.name); qualifiers.append("T${tag.length}:$tag:") }
                }
                requireBtf(qualifiers.length <= 65536, "SIGNATURE_SIZE")
                id = candidate.value
            }
            val t = record ?: throw Invalid("SIGNATURE_MODIFIER_CYCLE")
            fun nested(next: Int, indirect: Boolean = false) = signature(next, depth + 1, indirect, semantic)
            fun number(value: Int) = Integer.toUnsignedString(value)
            val text = StringBuilder()
            fun append(value: String) {
                requireBtf(text.length.toLong() + value.length <= 65536, "SIGNATURE_SIZE")
                text.append(value)
            }
            append(qualifiers.toString())
            when (t.kind) {
                1 -> append("i${number(t.value)}:${number(u32(t.extra))}")
                2 -> { append("*"); append(nested(t.value, true)) }
                3 -> { append("a${number(u32(t.extra + 8))}:"); append(nested(u32(t.extra))) }
                4, 5 -> {
                    val typeName = name(t.name)
                    append((if (t.kind == 4) "s" else "u") + typeName)
                    if (!pointer || typeName.isEmpty()) {
                        append(":${number(t.value)}{")
                        repeat(t.count) { at ->
                            val fieldAt = t.extra + at * 12
                            append("${number(u32(fieldAt + 8))}=")
                            append(nested(u32(fieldAt + 4))); append(";")
                        }
                        append("}")
                    }
                }
                6, 19 -> {
                    append("e${number(t.value)}" + if (t.bits) "s{" else "u{")
                    val stride = if (t.kind == 6) 8 else 12
                    repeat(t.count) { at ->
                        val fieldAt = t.extra + at * stride
                        val low = u32(fieldAt + 4).toLong() and 0xffffffffL
                        val value = if (t.kind == 19) low or (u32(fieldAt + 8).toLong() shl 32) else low
                        append(name(u32(fieldAt)) + "=" + java.lang.Long.toUnsignedString(value) + ";")
                    }
                    append("}")
                }
                7 -> append((if (t.bits) "u" else "s") + name(t.name))
                12 -> append(nested(t.value))
                13 -> {
                    append("f"); append(nested(t.value)); append("(")
                    repeat(t.count) { at -> append(nested(u32(t.extra + at * 8 + 4))); append(";") }
                    append(")")
                }
                else -> throw Invalid("SIGNATURE_KIND:${t.kind}")
            }
            return text.toString()
        }

        fun functionSignature(wanted: String): String? {
            val values = types.indices.filter { types[it].kind == 12 && name(types[it].name) == wanted }
                .map { signature(it) }.distinct()
            requireBtf(values.size <= 1, "FUNCTION_AMBIGUOUS:$wanted")
            return values.singleOrNull()
        }

        fun memberSignature(owner: String, member: String): String? {
            val candidates = named[owner].orEmpty().map(::resolved).distinct()
                .filter { type(it).kind in 4..5 }
            val values = candidates.map { original ->
                var cursor = original
                var value: Field? = null
                for (part in member.split('.')) {
                    value = field(cursor, part)
                    if (value == null) break
                    cursor = resolved(value.type)
                }
                value?.let { signature(it.type) }
            }.distinct()
            requireBtf(values.size <= 1, "CALLBACK_AMBIGUOUS:$owner.$member")
            return values.singleOrNull()
        }

        private fun counter(owner: Int, wanted: String): Field {
            var current = field(owner, wanted) ?: throw Invalid("MEMBER_MISSING:$wanted")
            var offset = current.offset
            repeat(16) {
                val t = type(resolved(current.type))
                if (t.kind == 1) {
                    val encoding = u32(t.extra)
                    requireBtf(t.value in setOf(4, 8) && current.width == t.value.toLong() &&
                        encoding and 0xff == t.value * 8 && (encoding ushr 16) and 0xff == 0,
                        "COUNTER_TYPE:$wanted")
                    requireBtf(offset % t.value == 0 && offset.toLong() + t.value <= type(owner).value,
                        "COUNTER_BOUNDS:$wanted")
                    return Field(offset, current.type, t.value.toLong())
                }
                requireBtf(t.kind == 4, "COUNTER_TYPE:$wanted")
                val nested = listOf("refs", "counter").mapNotNull { field(current.type, it) }
                requireBtf(nested.size == 1, "COUNTER_TYPE:$wanted")
                current = nested.single()
                val next = offset.toLong() + current.offset
                requireBtf(next <= Int.MAX_VALUE, "MEMBER_OFFSET")
                offset = next.toInt()
            }
            throw Invalid("COUNTER_CYCLE:$wanted")
        }

        fun layout(needed: Set<String>?): UltraKernelLayout? {
            val required = (needed ?: setOf(UltraKernelStructOffsets.CRED, UltraKernelStructOffsets.COMM,
                UltraKernelStructOffsets.SUID, UltraKernelStructOffsets.SGID, UltraKernelStructOffsets.CRED_EUID,
                UltraKernelStructOffsets.SECCOMP_MODE)).map {
                if (it == UltraKernelStructOffsets.SECCOMP) UltraKernelStructOffsets.SECCOMP_MODE else it
            }.toMutableSet()
            if (UltraKernelStructOffsets.SUID in required || UltraKernelStructOffsets.SGID in required)
                required += setOf(UltraKernelStructOffsets.SUID, UltraKernelStructOffsets.SGID,
                    UltraKernelStructOffsets.CRED_EUID)
            if ("group_info.usage" in required) required += "cred.group_info"
            if (required.any { it.startsWith("cred.") }) required += UltraKernelStructOffsets.CRED
            // BTF can retain equivalent compilation-unit copies with different type IDs.
            // Follow each task's actual cred pointer, then require all observed layouts to agree.
            val tasks = structs["task_struct"].orEmpty().filter { type(it).value > 0 && type(it).count > 0 }
            requireBtf(tasks.isNotEmpty(), "STRUCT_MISSING:task_struct")
            val layouts = tasks.map { taskLayout(it, required, needed != null) }.distinct()
            requireBtf(layouts.size == 1, "STRUCT_AMBIGUOUS:task_struct")
            return layouts.single()
        }

        private fun taskLayout(task: Int, required: Set<String>, explicit: Boolean): UltraKernelLayout {
            fun described(offset: Int, width: Int, owner: Int) =
                UltraKernelLayoutField(offset, width, type(owner).value, "btf")
            val fields = linkedMapOf<String, UltraKernelLayoutField>()
            if (UltraKernelStructOffsets.CRED in required) {
                val cred = member(task, "cred", 8)
                val pointer = type(resolved(cred.type))
                requireBtf(pointer.kind == 2, "CRED_POINTER")
                val credType = resolved(pointer.value)
                val credRecord = type(credType)
                requireBtf(credRecord.kind == 4 && name(credRecord.name) == "cred" &&
                    credRecord.value > 0, "CRED_POINTER")
                requireBtf(cred.offset % 8 == 0, "TASK_LAYOUT")
                fields[UltraKernelStructOffsets.CRED] = described(cred.offset, 8, task)
                for (name in listOf(UltraKernelStructOffsets.SUID, UltraKernelStructOffsets.SGID,
                    UltraKernelStructOffsets.CRED_EUID, "cred.uid").filter { it in required }) {
                    fields[name] = described(member(credType, name.substringAfter('.'), 4).offset, 4, credType)
                }
                if (UltraKernelStructOffsets.CRED_SECUREBITS in required) {
                    val securebits = member(credType, "securebits", 4)
                    val scalar = type(resolved(securebits.type))
                    requireBtf(scalar.kind == 1 && scalar.value == 4 && u32(scalar.extra) == 32,
                        "SECUREBITS_TYPE")
                    requireBtf(securebits.offset % 4 == 0, "SECUREBITS_ALIGNMENT")
                    fields[UltraKernelStructOffsets.CRED_SECUREBITS] = described(securebits.offset, 4, credType)
                }
                if ("cred.usage" in required) {
                    val usage = counter(credType, "usage")
                    fields["cred.usage"] = described(usage.offset, usage.width.toInt(), credType)
                }
                if ("cred.group_info" in required) {
                    val groups = member(credType, "group_info", 8)
                    val groupPointer = type(resolved(groups.type))
                    requireBtf(groupPointer.kind == 2, "GROUP_POINTER")
                    val groupType = resolved(groupPointer.value)
                    val groupRecord = type(groupType)
                    requireBtf(groupRecord.kind == 4 && name(groupRecord.name) == "group_info" &&
                        groupRecord.value > 0 && groups.offset % 8 == 0, "GROUP_POINTER")
                    fields["cred.group_info"] = described(groups.offset, 8, credType)
                    if ("group_info.usage" in required) {
                        val usage = counter(groupType, "usage")
                        fields["group_info.usage"] = described(usage.offset, usage.width.toInt(), groupType)
                    }
                }
                if (UltraKernelStructOffsets.SUID in required) {
                    val suid = fields.getValue(UltraKernelStructOffsets.SUID).offsetBytes
                    val sgid = fields.getValue(UltraKernelStructOffsets.SGID).offsetBytes
                    val euid = fields.getValue(UltraKernelStructOffsets.CRED_EUID).offsetBytes
                    requireBtf(suid % 4 == 0 && sgid == suid + 4 && euid % 4 == 0 &&
                        euid != suid && euid != sgid, "CRED_LAYOUT")
                }
            }
            if (UltraKernelStructOffsets.COMM in required) {
                val comm = member(task, "comm", 16)
                val commType = type(resolved(comm.type))
                requireBtf(commType.kind == 3 && u32(commType.extra + 8) == 16 &&
                    type(resolved(u32(commType.extra))).kind == 1 && size(u32(commType.extra)) == 1L, "COMM_TYPE")
                requireBtf(comm.offset % 4 == 0, "TASK_LAYOUT")
                fields[UltraKernelStructOffsets.COMM] = described(comm.offset, 16, task)
            }
            if (UltraKernelStructOffsets.SECCOMP_MODE in required) {
                val seccomp = field(task, "seccomp")
                requireBtf(seccomp != null || !explicit, "MEMBER_MISSING:seccomp")
                seccomp?.let {
                    val mode = member(it.type, "mode", 4)
                    val offset = it.offset.toLong() + mode.offset
                    requireBtf(offset + 4 <= type(task).value, "MEMBER_BOUNDS")
                    fields[UltraKernelStructOffsets.SECCOMP_MODE] = described(offset.toInt(), 4, task)
                }
            }
            return UltraKernelLayout(fields)
        }
    }
}
