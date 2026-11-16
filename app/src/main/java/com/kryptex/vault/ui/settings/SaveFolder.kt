package com.kryptex.vault.ui.settings

import com.kryptex.vault.data.media.CarrierMediaStore

/** The photo-library roots Kryptex may save carrier pictures under. Mirrors [CarrierMediaStore.relativePath]. */
val SAVE_FOLDER_ROOTS = listOf("Pictures", "DCIM")

private val FORBIDDEN_IN_NAME = Regex("""[\\:*?"<>|\u0000-\u001F]""")

/** Splits a stored folder such as `Pictures/Trips/2026` into its root and the sub-folder path (possibly empty). */
internal fun splitSaveFolder(folder: String): Pair<String, String> {
    val segments = folder.trim().trim('/').split('/').filter { it.isNotBlank() }
    val root = segments.firstOrNull()?.takeIf { it in SAVE_FOLDER_ROOTS } ?: SAVE_FOLDER_ROOTS.first()
    val sub = if (segments.firstOrNull() in SAVE_FOLDER_ROOTS) segments.drop(1) else emptyList()
    return root to sub.joinToString("/")
}

/**
 * Builds the folder setting from a [root] and a free-text [sub]-folder, or returns null if it is
 * not acceptable: an unknown root, `.`/`..` segments, or characters MediaStore refuses.
 */
internal fun buildSaveFolder(root: String, sub: String): String? {
    if (root !in SAVE_FOLDER_ROOTS) return null
    val segments = sub.trim().split('/').map { it.trim() }.filter { it.isNotEmpty() }
    if (segments.any { it == "." || it == ".." || FORBIDDEN_IN_NAME.containsMatchIn(it) || it.length > 64 }) return null
    val folder = (listOf(root) + segments).joinToString("/")
    return folder.takeIf { runCatching { CarrierMediaStore.relativePath(it) }.isSuccess }
}
