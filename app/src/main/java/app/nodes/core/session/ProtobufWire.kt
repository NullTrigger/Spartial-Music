/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.session

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The protobuf subset the Listen Together protocol actually uses.
 *
 * The Metrolist server family - the reference Python server and the Cloudflare
 * Worker - hand-rolls the same subset instead of generating classes from a
 * `.proto`, so this file mirrors it field for field: varint tags, wire type 0
 * for integers and booleans, wire type 2 for strings, bytes, embedded messages
 * and repeated fields, and wire type 5 for `float`. proto3 default values are
 * omitted, which is why every writer here returns an empty array for zero
 * except [float], which the room state always writes.
 *
 * Keeping it this small means no protobuf runtime and no code generation
 * plugin in the build: the app already has everything it needs in the JDK.
 */
internal object Pb {

    fun concat(vararg chunks: ByteArray): ByteArray {
        val total = chunks.sumOf { it.size }
        val out = ByteArrayOutputStream(total)
        chunks.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** Unsigned LEB128, matching the server's `varint()`. */
    fun varint(value: Long): ByteArray {
        require(value >= 0L) { "Listen Together varints are unsigned" }
        val out = ByteArrayOutputStream(10)
        var remaining = value
        while (true) {
            val byte = (remaining % 128).toInt()
            remaining /= 128
            out.write(if (remaining != 0L) byte or 0x80 else byte)
            if (remaining == 0L) break
        }
        return out.toByteArray()
    }

    private fun tag(field: Int, wire: Int): ByteArray = varint((field.toLong() shl 3) or wire.toLong())

    fun int(field: Int, value: Long): ByteArray =
        if (value == 0L) EMPTY else concat(tag(field, 0), varint(value))

    fun bool(field: Int, value: Boolean): ByteArray =
        if (!value) EMPTY else concat(tag(field, 0), byteArrayOf(1))

    fun string(field: Int, value: String?): ByteArray {
        if (value.isNullOrEmpty()) return EMPTY
        val data = value.toByteArray(Charsets.UTF_8)
        return concat(tag(field, 2), varint(data.size.toLong()), data)
    }

    fun bytes(field: Int, value: ByteArray?): ByteArray {
        if (value.isNullOrEmpty()) return EMPTY
        return concat(tag(field, 2), varint(value.size.toLong()), value)
    }

    fun message(field: Int, value: ByteArray?): ByteArray = bytes(field, value)

    /** Always written, even at zero: `RoomState.volume` is non-nullable. */
    fun float(field: Int, value: Float): ByteArray = concat(tag(field, 5), floatLE(value))

    fun floatOpt(field: Int, value: Float): ByteArray =
        if (value == 0f) EMPTY else float(field, value)

    private fun floatLE(value: Float): ByteArray {
        val bits = value.toRawBits()
        return byteArrayOf(
            (bits and 0xFF).toByte(),
            ((bits shr 8) and 0xFF).toByte(),
            ((bits shr 16) and 0xFF).toByte(),
            ((bits shr 24) and 0xFF).toByte(),
        )
    }

    private val EMPTY = ByteArray(0)
}

/**
 * Field reader for the same subset.
 *
 * Unknown fields are skipped rather than rejected: the server grows the
 * protocol over time (capabilities, suggestions, chat) and an older build must
 * keep working against a newer worker instead of dropping the room.
 */
internal class PbFields(private val data: ByteArray) {

    private class Value(val wire: Int, val number: Long, val bytes: ByteArray)

    private val byField: Map<Int, List<Value>> = run {
        val out = HashMap<Int, MutableList<Value>>()
        var i = 0
        while (i < data.size) {
            val (tagValue, tagNext) = readVarint(i)
            i = tagNext
            val field = (tagValue ushr 3).toInt()
            val wire = (tagValue and 0x7).toInt()
            val value = when (wire) {
                0 -> {
                    val (v, next) = readVarint(i)
                    i = next
                    Value(wire, v, ByteArray(0))
                }

                1 -> {
                    if (i + 8 > data.size) throw IllegalArgumentException("truncated 64-bit field")
                    var bits = 0L
                    for (k in 0 until 8) bits = bits or ((data[i + k].toLong() and 0xFF) shl (8 * k))
                    i += 8
                    Value(wire, bits, ByteArray(0))
                }

                2 -> {
                    val (length, next) = readVarint(i)
                    i = next
                    val end = i + length.toInt()
                    if (end > data.size) throw IllegalArgumentException("truncated length-delimited field")
                    val slice = data.copyOfRange(i, end)
                    i = end
                    Value(wire, length, slice)
                }

                5 -> {
                    if (i + 4 > data.size) throw IllegalArgumentException("truncated 32-bit field")
                    val bits = (data[i].toInt() and 0xFF) or
                        ((data[i + 1].toInt() and 0xFF) shl 8) or
                        ((data[i + 2].toInt() and 0xFF) shl 16) or
                        ((data[i + 3].toInt() and 0xFF) shl 24)
                    i += 4
                    Value(wire, bits.toLong() and 0xFFFFFFFFL, ByteArray(0))
                }

                else -> throw IllegalArgumentException("unsupported wire type $wire")
            }
            out.getOrPut(field) { mutableListOf() }.add(value)
        }
        out
    }

    private fun readVarint(from: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var cursor = from
        while (cursor < data.size) {
            val byte = data[cursor++].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result to cursor
            shift += 7
            if (shift > 63) throw IllegalArgumentException("varint too long")
        }
        throw IllegalArgumentException("truncated varint")
    }

    fun string(field: Int): String =
        byField[field]?.lastOrNull { it.wire == 2 }?.bytes?.toString(Charsets.UTF_8).orEmpty()

    fun number(field: Int): Long =
        byField[field]?.lastOrNull { it.wire == 0 }?.number ?: 0L

    fun boolean(field: Int): Boolean = number(field) != 0L

    fun float(field: Int): Float =
        byField[field]?.lastOrNull { it.wire == 5 }?.number?.toInt()?.let(java.lang.Float::intBitsToFloat) ?: 0f

    fun raw(field: Int): ByteArray? =
        byField[field]?.lastOrNull { it.wire == 2 }?.bytes

    fun repeated(field: Int): List<ByteArray> =
        byField[field]?.filter { it.wire == 2 }?.map { it.bytes }.orEmpty()
}

/**
 * The message envelope: `{1: type, 2: payload, 3: compressed}`.
 *
 * The server gzips a payload only above [COMPRESSION_THRESHOLD] bytes and only
 * when compression actually shrinks it, so a decoder must read the flag rather
 * than assume either shape.
 */
internal object Envelope {

    private const val COMPRESSION_THRESHOLD = 100

    fun encode(type: String, payload: ByteArray = ByteArray(0)): ByteArray {
        if (payload.size > COMPRESSION_THRESHOLD) {
            val packed = gzip(payload)
            if (packed.size < payload.size) {
                return Pb.concat(Pb.string(1, type), Pb.bytes(2, packed), Pb.bool(3, true))
            }
        }
        return Pb.concat(Pb.string(1, type), Pb.bytes(2, payload))
    }

    fun decode(data: ByteArray): Pair<String, ByteArray> {
        val fields = PbFields(data)
        val type = fields.string(1)
        val raw = fields.raw(2) ?: ByteArray(0)
        return type to if (fields.boolean(3)) gunzip(raw) else raw
    }

    private fun gzip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size)
        GZIPOutputStream(out).use { it.write(input) }
        return out.toByteArray()
    }

    private fun gunzip(input: ByteArray): ByteArray {
        GZIPInputStream(input.inputStream()).use { source ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }
}
