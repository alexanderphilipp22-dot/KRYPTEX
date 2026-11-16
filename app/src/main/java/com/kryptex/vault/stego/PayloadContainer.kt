package com.kryptex.vault.stego

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Thrown when decrypted container bytes are not a valid container. */
class MalformedContainerException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * One hidden item: its type, a MIME type and display name, a timestamp and the data itself
 * (text, audio stream, image fragment or any file). It is serialised, optionally deflated, and
 * then encrypted by [StegoCrypto]; it never touches disk in the clear.
 *
 * Layout, big-endian:
 * ```
 * u8 version | u8 flags(bit0 = deflated) | u8 type id | u8 reserved
 * i64 createdAt | u16 mimeLen | mime | u16 nameLen | name | u32 rawLength | u32 storedLength | stored
 * ```
 */
class PayloadContainer(
    val type: PayloadType,
    val mimeType: String,
    val name: String,
    val createdAtEpochMs: Long,
    val data: ByteArray,
) {
    init {
        require(mimeType.toByteArray(Charsets.UTF_8).size <= MAX_MIME_BYTES) { "MIME type too long" }
        require(name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES) { "name too long" }
        require(data.size <= MAX_RAW_BYTES) { "payload larger than $MAX_RAW_BYTES bytes" }
    }

    /** Serialises the container, deflating the data when that makes it smaller. */
    fun encode(compress: Boolean = true): ByteArray {
        val deflated = if (compress && data.size >= MIN_COMPRESSIBLE_BYTES) deflate(data) else null
        val useDeflate = deflated != null && deflated.size < data.size
        val stored = if (useDeflate) deflated else data

        val bytes = ByteArrayOutputStream(stored.size + 64)
        val out = DataOutputStream(bytes)
        out.writeByte(FORMAT_VERSION)
        out.writeByte(if (useDeflate) FLAG_DEFLATED else 0)
        out.writeByte(type.id)
        out.writeByte(0)
        out.writeLong(createdAtEpochMs)
        val mime = mimeType.toByteArray(Charsets.UTF_8)
        out.writeShort(mime.size)
        out.write(mime)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        out.writeShort(nameBytes.size)
        out.write(nameBytes)
        out.writeInt(data.size)
        out.writeInt(stored.size)
        out.write(stored)
        out.flush()
        return bytes.toByteArray()
    }

    override fun equals(other: Any?): Boolean =
        other is PayloadContainer && type == other.type && mimeType == other.mimeType && name == other.name &&
            createdAtEpochMs == other.createdAtEpochMs && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * (31 * type.hashCode() + name.hashCode()) + data.contentHashCode()

    // The content is secret: never print it.
    override fun toString(): String = "PayloadContainer(type=$type, size=${data.size})"

    companion object {
        const val FORMAT_VERSION = 1
        private const val FLAG_DEFLATED = 0x01
        const val MAX_MIME_BYTES = 127
        const val MAX_NAME_BYTES = 255

        /** Upper bound on the decompressed size, so a tiny hostile container cannot inflate to gigabytes. */
        const val MAX_RAW_BYTES = 64 * 1024 * 1024

        private const val MIN_COMPRESSIBLE_BYTES = 64

        fun decode(bytes: ByteArray): PayloadContainer {
            try {
                val input = DataInputStream(bytes.inputStream())
                val version = input.readUnsignedByte()
                if (version != FORMAT_VERSION) throw MalformedContainerException("unsupported container version $version")
                val flags = input.readUnsignedByte()
                if (flags and FLAG_DEFLATED.inv() != 0) throw MalformedContainerException("unknown flags")
                val typeId = input.readUnsignedByte()
                if (input.readUnsignedByte() != 0) throw MalformedContainerException("reserved byte set")
                val type = try {
                    PayloadType.fromId(typeId)
                } catch (e: IllegalArgumentException) {
                    throw MalformedContainerException("unknown payload type", e)
                }
                val createdAt = input.readLong()

                val mimeLen = input.readUnsignedShort()
                if (mimeLen > MAX_MIME_BYTES) throw MalformedContainerException("MIME type too long")
                val mime = ByteArray(mimeLen).also(input::readFully)
                val nameLen = input.readUnsignedShort()
                if (nameLen > MAX_NAME_BYTES) throw MalformedContainerException("name too long")
                val name = ByteArray(nameLen).also(input::readFully)

                val rawLength = input.readInt()
                val storedLength = input.readInt()
                if (rawLength < 0 || rawLength > MAX_RAW_BYTES) throw MalformedContainerException("bad payload size")
                if (storedLength < 0 || storedLength > input.available()) throw MalformedContainerException("bad stored size")

                val stored = ByteArray(storedLength).also(input::readFully)
                if (input.available() != 0) throw MalformedContainerException("trailing bytes")

                val data = if (flags and FLAG_DEFLATED != 0) {
                    inflate(stored, rawLength)
                } else {
                    if (storedLength != rawLength) throw MalformedContainerException("size mismatch")
                    stored
                }
                return PayloadContainer(
                    type = type,
                    mimeType = String(mime, Charsets.UTF_8),
                    name = String(name, Charsets.UTF_8),
                    createdAtEpochMs = createdAt,
                    data = data,
                )
            } catch (e: EOFException) {
                throw MalformedContainerException("container truncated", e)
            } catch (e: IllegalArgumentException) {
                throw MalformedContainerException("invalid container field", e)
            }
        }

        private fun deflate(data: ByteArray): ByteArray {
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
            try {
                deflater.setInput(data)
                deflater.finish()
                val out = ByteArrayOutputStream(data.size / 2 + 16)
                val buffer = ByteArray(8192)
                while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
                return out.toByteArray()
            } finally {
                deflater.end()
            }
        }

        /** Inflates to exactly [expected] bytes; anything shorter or longer is rejected. */
        private fun inflate(stored: ByteArray, expected: Int): ByteArray {
            val inflater = Inflater()
            try {
                inflater.setInput(stored)
                val out = ByteArray(expected)
                var produced = 0
                while (produced < expected) {
                    val n = inflater.inflate(out, produced, expected - produced)
                    if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                    produced += n
                }
                if (produced != expected) throw MalformedContainerException("deflated size mismatch")
                // The stream must end here: more output would mean the declared size lied.
                if (!inflater.finished()) {
                    val probe = ByteArray(1)
                    if (inflater.inflate(probe) != 0 || !inflater.finished()) {
                        throw MalformedContainerException("deflated data longer than declared")
                    }
                }
                return out
            } catch (e: DataFormatException) {
                throw MalformedContainerException("corrupt deflate stream", e)
            } finally {
                inflater.end()
            }
        }
    }
}
