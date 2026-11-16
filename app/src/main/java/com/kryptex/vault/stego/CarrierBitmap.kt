package com.kryptex.vault.stego

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.sqrt

/** The picture could not be decoded, or is too large to process safely. */
class UnsupportedCarrierException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A small picture to show the user, and the size the full picture will have once embedded into. */
class CarrierPreview(val width: Int, val height: Int, val scaledDown: Boolean, val thumbnail: Bitmap)

/**
 * An image as opaque, un-premultiplied-safe sRGB pixels, ready for [StegoEngine].
 *
 * Android stores translucent bitmaps premultiplied, which would silently destroy low bits, so
 * everything is flattened onto white and forced to alpha 255 here. Output is always PNG, the
 * only widely supported lossless format.
 */
class CarrierBitmap(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixel array does not match ${width}x$height" }
    }

    val pixelCount: Int get() = pixels.size

    fun copy() = CarrierBitmap(width, height, pixels.copyOf())

    /** An opaque ARGB bitmap with exactly these pixels. */
    fun toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.setHasAlpha(false) // an RGB PNG without an alpha channel, like most screenshots and exports
        return bitmap
    }

    /** Lossless PNG bytes. */
    fun toPng(): ByteArray {
        val bitmap = toBitmap()
        try {
            val out = ByteArrayOutputStream(pixels.size / 2)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encoding failed" }
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        /** Limit for loading a picture that is *read* (no resizing is allowed, it would destroy the payload). */
        const val MAX_EXTRACT_PIXELS = 40_000_000

        /** Wraps an existing bitmap; transparency is flattened onto white. */
        fun fromBitmap(source: Bitmap): CarrierBitmap {
            var argb = source
            val converted = source.config != Bitmap.Config.ARGB_8888
            if (converted) argb = source.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw UnsupportedCarrierException("cannot convert the picture to ARGB_8888")

            var flattened: Bitmap? = null
            try {
                val drawn = if (argb.hasAlpha()) {
                    flattened = Bitmap.createBitmap(argb.width, argb.height, Bitmap.Config.ARGB_8888).also {
                        Canvas(it).apply {
                            drawColor(Color.WHITE)
                            drawBitmap(argb, 0f, 0f, null)
                        }
                    }
                    flattened
                } else {
                    argb
                }
                val pixels = IntArray(drawn.width * drawn.height)
                drawn.getPixels(pixels, 0, drawn.width, 0, 0, drawn.width, drawn.height)
                for (i in pixels.indices) pixels[i] = pixels[i] or OPAQUE
                return CarrierBitmap(drawn.width, drawn.height, pixels)
            } finally {
                flattened?.recycle()
                if (converted) argb.recycle()
            }
        }

        /**
         * Loads a picture to **hide data in it**. Pictures above [CapacityCalculator.MAX_CARRIER_PIXELS]
         * are scaled down to bound memory; the saved carrier is the scaled picture.
         */
        fun decodeForEmbedding(context: Context, uri: Uri): CarrierBitmap =
            decode(ImageDecoder.createSource(context.contentResolver, uri), maxPixels = CapacityCalculator.MAX_CARRIER_PIXELS, resize = true)

        /** Loads a picture to **read data from it**, bit-exact: never resized. */
        fun decodeForExtraction(context: Context, uri: Uri): CarrierBitmap =
            decode(ImageDecoder.createSource(context.contentResolver, uri), maxPixels = MAX_EXTRACT_PIXELS, resize = false)

        fun decodeForExtraction(bytes: ByteArray): CarrierBitmap =
            decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), maxPixels = MAX_EXTRACT_PIXELS, resize = false)

        /** Size a picture of [width] x [height] is decoded at when it must not exceed [maxPixels]. */
        internal fun scaledSize(width: Int, height: Int, maxPixels: Int): Pair<Int, Int> {
            val total = width.toLong() * height
            if (total <= maxPixels) return width to height
            val scale = sqrt(maxPixels.toDouble() / total)
            return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
        }

        /**
         * What [decodeForEmbedding] will produce for [uri], found out cheaply: a small [thumbnail]
         * and the final size, without keeping the full pixel array in memory.
         */
        fun previewForEmbedding(context: Context, uri: Uri, maxEdge: Int = 640): CarrierPreview {
            var originalWidth = 0
            var originalHeight = 0
            val thumbnail = try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    originalWidth = info.size.width
                    originalHeight = info.size.height
                    val longest = maxOf(originalWidth, originalHeight)
                    if (longest > maxEdge) {
                        val scale = maxEdge.toDouble() / longest
                        decoder.setTargetSize(
                            (originalWidth * scale).toInt().coerceAtLeast(1),
                            (originalHeight * scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
            } catch (e: Exception) {
                throw UnsupportedCarrierException("this file is not a picture the app can read", e)
            }
            val (width, height) = scaledSize(originalWidth, originalHeight, CapacityCalculator.MAX_CARRIER_PIXELS)
            return CarrierPreview(width, height, scaledDown = width != originalWidth || height != originalHeight, thumbnail = thumbnail)
        }

        private fun decode(source: ImageDecoder.Source, maxPixels: Int, resize: Boolean): CarrierBitmap {
            val bitmap = try {
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    val total = info.size.width.toLong() * info.size.height
                    if (total > maxPixels) {
                        if (!resize) throw UnsupportedCarrierException("picture has $total pixels, more than the $maxPixels supported")
                        val (w, h) = scaledSize(info.size.width, info.size.height, maxPixels)
                        decoder.setTargetSize(w, h)
                    }
                }
            } catch (e: UnsupportedCarrierException) {
                throw e
            } catch (e: Exception) {
                throw UnsupportedCarrierException("this file is not a picture the app can read", e)
            }
            try {
                return fromBitmap(bitmap)
            } finally {
                bitmap.recycle()
            }
        }

        private const val OPAQUE = 0xFF shl 24
    }
}
