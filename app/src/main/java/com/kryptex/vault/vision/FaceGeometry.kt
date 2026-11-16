package com.kryptex.vault.vision

import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * The proportions of one face: ten ratios between points that do not move when the person
 * speaks, smiles or blinks (eye corners and the nose), each divided by the distance between the
 * outer eye corners. That makes the numbers independent of how far away the face is, how the phone
 * is held, and whether the camera image is mirrored or sideways; see [FaceGeometry.measure].
 *
 * This is a *rough* check, not a biometric in the sense of a fingerprint sensor. Face proportions
 * from a 478-point landmark model separate clearly different faces, but look-alikes and relatives
 * may match, and an unusual angle or light may make the owner's own face miss. The app says so.
 *
 * The values are personal data. They are never logged ([toString] hides them), stored only
 * encrypted inside the gesture key slot (see `VaultKeyManager`), and wiped with [wipe] when done.
 */
class FaceDescriptor internal constructor(source: FloatArray) {
    private val values: FloatArray = source.copyOf()

    init {
        require(source.size == FaceGeometry.DIMENSIONS) { "a face descriptor has ${FaceGeometry.DIMENSIONS} values" }
    }

    fun copy() = FaceDescriptor(values)

    /** Zeroes the numbers. A wiped descriptor matches nothing. */
    fun wipe() = values.fill(0f)

    /**
     * How far apart two faces' proportions are: the root-mean-square of each ratio's relative
     * difference measured in units of its expected measurement noise. 0 = identical; about 1 =
     * typical noise between two readings of the same face; 2 or more = a visibly different face.
     * Infinite if either side is wiped or unusable.
     */
    fun distanceTo(other: FaceDescriptor): Float {
        var sum = 0f
        for (i in 0 until FaceGeometry.DIMENSIONS) {
            val a = values[i]
            val b = other.values[i]
            // A real ratio is always positive; zero (wiped), NaN or negative is no face at all.
            if (!(a > 0f) || !(b > 0f)) return Float.POSITIVE_INFINITY
            val z = (a - b) / ((a + b) / 2f) / FaceGeometry.TOLERANCE[i]
            sum += z * z
        }
        val distance = sqrt(sum / FaceGeometry.DIMENSIONS)
        return if (distance.isNaN()) Float.POSITIVE_INFINITY else distance
    }

    /** Fails closed: anything that is not clearly within [FaceGeometry.MATCH_THRESHOLD] is no match. */
    fun matches(other: FaceDescriptor): Boolean = distanceTo(other) <= FaceGeometry.MATCH_THRESHOLD

    /** `version(1) || count(1) || float32 x count`, big-endian. Encrypt before storing. */
    fun toBytes(): ByteArray =
        ByteBuffer.allocate(FaceGeometry.ENCODED_BYTES).apply {
            put(FaceGeometry.FORMAT_VERSION)
            put(FaceGeometry.DIMENSIONS.toByte())
            for (value in values) putFloat(value)
        }.array()

    override fun equals(other: Any?): Boolean = other is FaceDescriptor && values.contentEquals(other.values)
    override fun hashCode(): Int = values.contentHashCode()
    override fun toString(): String = "FaceDescriptor(${values.size} values)"

    companion object {
        /** Null for anything that is not exactly what [toBytes] writes: wrong size or version, NaN, out-of-range ratios. */
        fun fromBytes(bytes: ByteArray): FaceDescriptor? {
            if (bytes.size != FaceGeometry.ENCODED_BYTES) return null
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != FaceGeometry.FORMAT_VERSION) return null
            if (buffer.get().toInt() != FaceGeometry.DIMENSIONS) return null
            val values = FloatArray(FaceGeometry.DIMENSIONS) { buffer.getFloat() }
            return if (values.all { FaceGeometry.isPlausibleRatio(it) }) FaceDescriptor(values) else null
        }

        /** Per-ratio median: a single odd frame (a twitch, a tracking glitch) cannot move it. Null for no samples. */
        fun median(samples: List<FaceDescriptor>): FaceDescriptor? {
            if (samples.isEmpty()) return null
            val result = FloatArray(FaceGeometry.DIMENSIONS)
            val column = FloatArray(samples.size)
            for (d in result.indices) {
                for (i in samples.indices) column[i] = samples[i].values[d]
                column.sort()
                val mid = column.size / 2
                result[d] = if (column.size % 2 == 1) column[mid] else (column[mid - 1] + column[mid]) / 2f
            }
            return FaceDescriptor(result)
        }

        /** Per-ratio mean of two readings, used to blend the two enrolment passes into one template. */
        fun average(a: FaceDescriptor, b: FaceDescriptor): FaceDescriptor =
            FaceDescriptor(FloatArray(FaceGeometry.DIMENSIONS) { (a.values[it] + b.values[it]) / 2f })
    }
}

/**
 * Measures a face's proportions from MediaPipe's landmarks.
 *
 * Only distances between points are used, and only between points that stay put: the four eye
 * corners and five points down the nose. Mouth, jaw, brows and the face outline are left out on
 * purpose, because speaking, smiling and turning the head move them. Distances in 3D (x, y and
 * MediaPipe's depth estimate) do not change when the head turns, which a flat picture would.
 *
 * **The tolerances and the threshold below are provisional.** They are reasoned from typical
 * landmark noise (a pixel or two on a face about 100 pixels wide) and typical differences
 * between people (5-10 % per ratio); they have not been tuned on real faces and phones.
 */
object FaceGeometry {
    const val DIMENSIONS = 10
    internal const val FORMAT_VERSION: Byte = 1
    internal const val ENCODED_BYTES = 2 + 4 * DIMENSIONS

    /** An upper bound for stored templates; the key slot refuses anything larger. */
    const val MAX_TEMPLATE_BYTES = 64

    /** [FaceDescriptor.distanceTo] up to this counts as the same face. */
    const val MATCH_THRESHOLD = 1.5f

    /**
     * Expected relative measurement noise of each ratio (see [measure] for the order). Depth-heavy
     * and short segments are noisier, so they count for less.
     */
    internal val TOLERANCE = floatArrayOf(
        0.05f, // inner-eye distance
        0.04f, // eye width
        0.05f, // nose root to inner eye corners
        0.07f, // nose root to nose tip
        0.09f, // nose tip to base of the nose
        0.06f, // nose width
        0.05f, // nose tip to inner eye corners
        0.05f, // nose tip to outer eye corners
        0.08f, // base of the nose to the nostril wings
        0.05f, // nose root to base of the nose
    )

    /**
     * Outer-eye-corner distances below this (3 % of the frame height) are a collapsed mesh or a
     * face too far away to measure, not a reading worth keeping.
     */
    private const val MIN_SCALE = 0.03f

    private const val MIN_RATIO = 0.02f
    private const val MAX_RATIO = 4f

    internal fun isPlausibleRatio(value: Float): Boolean = value in MIN_RATIO..MAX_RATIO

    /**
     * The template to save from the two passes of setting a gesture up: their average, provided both
     * were measured and show the same face. Null otherwise, so a template is never built from one
     * unreadable pass or from two different people. Neither input is changed or wiped.
     */
    fun enrolmentTemplate(first: FaceDescriptor?, second: FaceDescriptor?): FaceDescriptor? {
        if (first == null || second == null || !first.matches(second)) return null
        return FaceDescriptor.average(first, second)
    }

    /**
     * Whether [probe] (the face seen just now) fits [stored] (a template as written by
     * [FaceDescriptor.toBytes]). Fails closed: no probe, or a template that cannot be read, is a
     * mismatch. The decoded copy of the template is wiped; [stored] and [probe] stay the caller's.
     */
    fun matchesStored(stored: ByteArray, probe: FaceDescriptor?): Boolean {
        if (probe == null) return false
        val template = FaceDescriptor.fromBytes(stored) ?: return false
        return try {
            template.matches(probe)
        } finally {
            template.wipe()
        }
    }

    /** @return null when the mesh is incomplete, collapsed or contains non-finite numbers. */
    fun measure(lm: FaceLandmarks): FaceDescriptor? {
        if (lm.count < FaceMesh.MIN_LANDMARKS_FOR_GEOMETRY) return null

        val scale = lm.distance(FaceMesh.RIGHT_EYE_OUTER, FaceMesh.LEFT_EYE_OUTER)
        if (!(scale >= MIN_SCALE) || !scale.isFinite()) return null

        fun ratio(a: Int, b: Int) = lm.distance(a, b) / scale
        fun pair(a1: Int, b1: Int, a2: Int, b2: Int) = (ratio(a1, b1) + ratio(a2, b2)) / 2f

        with(FaceMesh) {
            val values = floatArrayOf(
                ratio(RIGHT_EYE_INNER, LEFT_EYE_INNER),
                pair(RIGHT_EYE_OUTER, RIGHT_EYE_INNER, LEFT_EYE_INNER, LEFT_EYE_OUTER),
                pair(NOSE_BRIDGE, RIGHT_EYE_INNER, NOSE_BRIDGE, LEFT_EYE_INNER),
                ratio(NOSE_BRIDGE, NOSE_TIP),
                ratio(NOSE_TIP, SUBNASALE),
                ratio(NOSTRIL_RIGHT, NOSTRIL_LEFT),
                pair(NOSE_TIP, RIGHT_EYE_INNER, NOSE_TIP, LEFT_EYE_INNER),
                pair(NOSE_TIP, RIGHT_EYE_OUTER, NOSE_TIP, LEFT_EYE_OUTER),
                pair(SUBNASALE, NOSTRIL_RIGHT, SUBNASALE, NOSTRIL_LEFT),
                ratio(NOSE_BRIDGE, SUBNASALE),
            )
            return if (values.all { isPlausibleRatio(it) }) FaceDescriptor(values) else null
        }
    }
}
