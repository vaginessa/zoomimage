@file:Suppress("NOTHING_TO_INLINE")

package com.github.panpf.zoomimage.util

import com.github.panpf.zoomimage.util.internal.lerp
import com.github.panpf.zoomimage.util.internal.packInts
import com.github.panpf.zoomimage.util.internal.unpackInt1
import com.github.panpf.zoomimage.util.internal.unpackInt2
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Constructs an [IntSizeCompat] from width and height [Int] values.
 */
fun IntSizeCompat(width: Int, height: Int): IntSizeCompat = IntSizeCompat(packInts(width, height))

/**
 * A two-dimensional size class used for measuring in [Int] pixels.
 */
// todo Unit tests
@JvmInline
value class IntSizeCompat internal constructor(@PublishedApi internal val packedValue: Long) {

    /**
     * The horizontal aspect of the size in [Int] pixels.
     */
    val width: Int
        get() = unpackInt1(packedValue)

    /**
     * The vertical aspect of the size in [Int] pixels.
     */
    val height: Int
        get() = unpackInt2(packedValue)

    inline operator fun component1(): Int = width

    inline operator fun component2(): Int = height

    /**
     * Returns an IntSizeCompat scaled by multiplying [width] and [height] by [other]
     */
    operator fun times(other: Int): IntSizeCompat =
        IntSizeCompat(width = width * other, height = height * other)

    /**
     * Returns an IntSizeCompat scaled by dividing [width] and [height] by [other]
     */
    operator fun div(other: Int): IntSizeCompat =
        IntSizeCompat(width = width / other, height = height / other)

    override fun toString(): String = "$width x $height"

    companion object {
        /**
         * IntSize with a zero (0) width and height.
         */
        val Zero = IntSizeCompat(width = 0, height = 0)
    }
}

/**
 * Returns an [IntSizeCompat] with [size]'s [IntSizeCompat.width] and [IntSizeCompat.height]
 * multiplied by [this].
 */
operator fun Int.times(size: IntSizeCompat) = size * this

/**
 * Convert a [IntSizeCompat] to a [IntRectCompat].
 */
fun IntSizeCompat.toIntRect(): IntRectCompat {
    return IntRectCompat(IntOffsetCompat.Zero, this)
}

/**
 * Returns the [IntOffsetCompat] of the center of the rect from the point of [0, 0]
 * with this [IntSizeCompat].
 */
val IntSizeCompat.center: IntOffsetCompat
    get() = IntOffsetCompat(x = width / 2, y = height / 2)

// temporary while PxSize is transitioned to Size
fun IntSizeCompat.toSize(): SizeCompat = SizeCompat(width.toFloat(), height.toFloat())


fun IntSizeCompat.toShortString(): String = "${width}x$height"

fun IntSizeCompat.isEmpty(): Boolean = width <= 0 || height <= 0

fun IntSizeCompat.isNotEmpty(): Boolean = width > 0 && height > 0

operator fun IntSizeCompat.times(scaleFactor: ScaleFactorCompat): IntSizeCompat {
    return IntSizeCompat(
        width = (width * scaleFactor.scaleX).roundToInt(),
        height = (height * scaleFactor.scaleY).roundToInt()
    )
}

operator fun IntSizeCompat.div(scaleFactor: ScaleFactorCompat): IntSizeCompat {
    return IntSizeCompat(
        width = (width / scaleFactor.scaleX).roundToInt(),
        height = (height / scaleFactor.scaleY).roundToInt()
    )
}

operator fun IntSizeCompat.times(scale: Float): IntSizeCompat =
    IntSizeCompat(
        width = (this.width * scale).roundToInt(),
        height = (this.height * scale).roundToInt()
    )

operator fun IntSizeCompat.div(scale: Float): IntSizeCompat =
    IntSizeCompat(
        width = (this.width / scale).roundToInt(),
        height = (this.height / scale).roundToInt()
    )

fun IntSizeCompat.toOffset(): OffsetCompat = OffsetCompat(x = width.toFloat(), y = height.toFloat())

fun IntSizeCompat.toIntOffset(): IntOffsetCompat = IntOffsetCompat(x = width, y = height)

fun IntSizeCompat.rotate(rotation: Int): IntSizeCompat {
    return if (rotation % 180 == 0) this else IntSizeCompat(width = height, height = width)
}

fun IntSizeCompat.isSameAspectRatio(other: IntSizeCompat, delta: Float = 0f): Boolean {
    val selfScale = this.width / this.height.toFloat()
    val otherScale = other.width / other.height.toFloat()
    if (selfScale.compareTo(otherScale) == 0) {
        return true
    }
    if (delta != 0f && abs(selfScale - otherScale) <= delta) {
        return true
    }
    return false
}

/**
 * Linearly interpolate between two [IntSizeCompat]s.
 *
 * The [fraction] argument represents position on the timeline, with 0.0 meaning
 * that the interpolation has not started, returning [start] (or something
 * equivalent to [start]), 1.0 meaning that the interpolation has finished,
 * returning [stop] (or something equivalent to [stop]), and values in between
 * meaning that the interpolation is at the relevant point on the timeline
 * between [start] and [stop]. The interpolation can be extrapolated beyond 0.0 and
 * 1.0, so negative values and values greater than 1.0 are valid.
 */
fun lerpCompat(start: IntSizeCompat, stop: IntSizeCompat, fraction: Float): IntSizeCompat =
    IntSizeCompat(
        lerp(start.width, stop.width, fraction),
        lerp(start.height, stop.height, fraction)
    )

/**
 * Returns a copy of this IntOffset instance optionally overriding the
 * x or y parameter
 */
fun IntSizeCompat.copy(width: Int = this.width, height: Int = this.height) =
    IntSizeCompat(width = width, height = height)