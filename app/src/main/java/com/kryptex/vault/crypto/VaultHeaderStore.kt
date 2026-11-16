package com.kryptex.vault.crypto

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persists the vault header in app-private storage. Writes go to a temp file that is fsynced and
 * then renamed over the target, so a crash leaves either the old or the new header, never a mix.
 */
class VaultHeaderStore(private val directory: File, private val name: String = DEFAULT_NAME) {
    private val target get() = File(directory, name)
    private val temp get() = File(directory, "$name.tmp")

    fun exists(): Boolean = target.isFile

    /** @return the header, or null if no vault exists yet. @throws CorruptHeaderException if damaged. */
    @Synchronized
    fun read(): VaultHeader? {
        val file = target
        if (!file.isFile) return null
        return VaultHeaderCodec.decode(file.readBytes())
    }

    @Synchronized
    fun write(header: VaultHeader) {
        directory.mkdirs()
        val bytes = VaultHeaderCodec.encode(header)
        FileOutputStream(temp).use { stream ->
            stream.write(bytes)
            stream.flush()
            stream.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Synchronized
    fun delete() {
        if (temp.exists()) temp.delete()
        if (target.exists() && !target.delete()) throw IOException("could not delete vault header")
    }

    companion object {
        const val DEFAULT_NAME = "vault.hdr"
    }
}
