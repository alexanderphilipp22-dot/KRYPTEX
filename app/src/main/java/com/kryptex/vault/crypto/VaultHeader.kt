package com.kryptex.vault.crypto

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.util.zip.CRC32

/** Thrown when `vault.hdr` is truncated, damaged or from an unknown format version. */
class CorruptHeaderException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Key slot unlocked by the master passphrase. */
data class PassphraseSlot(
    val params: Argon2Params,
    val salt: ByteArray,
    /** `AesGcm.seal(KEK_passphrase, DEK)`. */
    val wrappedDek: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is PassphraseSlot && params == other.params &&
            salt.contentEquals(other.salt) && wrappedDek.contentEquals(other.wrappedDek)

    override fun hashCode(): Int = 31 * (31 * params.hashCode() + salt.contentHashCode()) + wrappedDek.contentHashCode()
}

/** Key slot unlocked by a gesture sequence on this device. */
data class GestureSlot(
    /** Number of gestures in the sequence; drives the progress dots. Authenticated via the slot AAD. */
    val steps: Int,
    val params: Argon2Params,
    val salt: ByteArray,
    /** `Keystore.wrap(AesGcm.seal(K_gesture, DEK))` - two layers. */
    val wrappedDek: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is GestureSlot && steps == other.steps && params == other.params &&
            salt.contentEquals(other.salt) && wrappedDek.contentEquals(other.wrappedDek)

    override fun hashCode(): Int =
        31 * (31 * (31 * steps + params.hashCode()) + salt.contentHashCode()) + wrappedDek.contentHashCode()
}

/**
 * Everything needed *before* the database can be opened. Holds no secret in the clear: the
 * DEK exists only wrapped inside the slots.
 */
data class VaultHeader(
    val createdAtEpochMs: Long,
    val passphraseSlot: PassphraseSlot,
    val gestureSlot: GestureSlot?,
    /** Consecutive failed gesture unlocks; incremented *before* each attempt is evaluated. */
    val failedGestureAttempts: Int,
)

/**
 * Binary codec, big-endian:
 * ```
 * "KRXH" | u8 version | u8 flags(bit0 = gesture slot) | i64 createdAt | i32 failedAttempts
 * passphrase slot: i32 mem | i32 iter | i32 par | u8 saltLen | salt | u16 wrapLen | wrapped
 * [gesture slot:   u8 steps | same fields as above]
 * u32 crc32 over everything before it
 * ```
 */
object VaultHeaderCodec {
    const val FORMAT_VERSION = 1
    private val MAGIC = byteArrayOf('K'.code.toByte(), 'R'.code.toByte(), 'X'.code.toByte(), 'H'.code.toByte())
    private const val FLAG_GESTURE = 0x01
    private const val MAX_SALT = 64
    private const val MAX_WRAPPED = 1024

    fun encode(header: VaultHeader): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write(MAGIC)
        out.writeByte(FORMAT_VERSION)
        out.writeByte(if (header.gestureSlot != null) FLAG_GESTURE else 0)
        out.writeLong(header.createdAtEpochMs)
        out.writeInt(header.failedGestureAttempts)
        writeSlot(out, header.passphraseSlot.params, header.passphraseSlot.salt, header.passphraseSlot.wrappedDek)
        header.gestureSlot?.let {
            out.writeByte(it.steps)
            writeSlot(out, it.params, it.salt, it.wrappedDek)
        }
        out.flush()

        val body = bytes.toByteArray()
        val crc = CRC32().apply { update(body) }.value
        return body + ByteArray(4) { i -> (crc ushr (24 - 8 * i)).toByte() }
    }

    fun decode(data: ByteArray): VaultHeader {
        if (data.size < MAGIC.size + 4) throw CorruptHeaderException("header too short")
        val body = data.copyOfRange(0, data.size - 4)
        val storedCrc = data.copyOfRange(data.size - 4, data.size)
            .fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        val actualCrc = CRC32().apply { update(body) }.value
        if (storedCrc != actualCrc) throw CorruptHeaderException("header checksum mismatch")

        try {
            val input = DataInputStream(body.inputStream())
            val magic = ByteArray(MAGIC.size).also(input::readFully)
            if (!magic.contentEquals(MAGIC)) throw CorruptHeaderException("bad magic")
            val version = input.readUnsignedByte()
            if (version != FORMAT_VERSION) throw CorruptHeaderException("unsupported header version $version")
            val flags = input.readUnsignedByte()
            val createdAt = input.readLong()
            val failed = input.readInt()
            if (failed < 0) throw CorruptHeaderException("negative attempt counter")

            val passphrase = readSlot(input).let { PassphraseSlot(it.params, it.salt, it.wrapped) }
            val gesture = if (flags and FLAG_GESTURE != 0) {
                val steps = input.readUnsignedByte()
                readSlot(input).let { GestureSlot(steps, it.params, it.salt, it.wrapped) }
            } else {
                null
            }
            if (input.available() != 0) throw CorruptHeaderException("trailing bytes")
            return VaultHeader(createdAt, passphrase, gesture, failed)
        } catch (e: EOFException) {
            throw CorruptHeaderException("header truncated", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptHeaderException("invalid header field", e)
        }
    }

    private class RawSlot(val params: Argon2Params, val salt: ByteArray, val wrapped: ByteArray)

    private fun writeSlot(out: DataOutputStream, params: Argon2Params, salt: ByteArray, wrapped: ByteArray) {
        require(salt.size <= MAX_SALT && wrapped.size <= MAX_WRAPPED) { "slot field too large" }
        out.writeInt(params.memoryKiB)
        out.writeInt(params.iterations)
        out.writeInt(params.parallelism)
        out.writeByte(salt.size)
        out.write(salt)
        out.writeShort(wrapped.size)
        out.write(wrapped)
    }

    private fun readSlot(input: DataInputStream): RawSlot {
        val params = Argon2Params(input.readInt(), input.readInt(), input.readInt())
        val saltLen = input.readUnsignedByte()
        if (saltLen < Argon2Kdf.MIN_SALT_BYTES || saltLen > MAX_SALT) throw CorruptHeaderException("bad salt length")
        val salt = ByteArray(saltLen).also(input::readFully)
        val wrapLen = input.readUnsignedShort()
        if (wrapLen < AesGcm.OVERHEAD_BYTES || wrapLen > MAX_WRAPPED) throw CorruptHeaderException("bad wrapped length")
        val wrapped = ByteArray(wrapLen).also(input::readFully)
        return RawSlot(params, salt, wrapped)
    }
}
