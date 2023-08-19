@file:Suppress("NOTHING_TO_INLINE")

package com.github.panpf.zoomimage.util

import com.github.panpf.zoomimage.util.internal.lerp
import com.github.panpf.zoomimage.util.internal.packInts
import com.github.panpf.zoomimage.util.internal.unpackInt1
import com.github.panpf.zoomimage.util.internal.unpackInt2
import kotlin.math.roundToInt

/**
 * Constructs a [IntOffsetCompat] from [x] and [y] position [Int] values.
 */
fun IntOffsetCompat(x: Int, y: Int): IntOffsetCompat = IntOffsetCompat(packInts(x, y))

/**
 * A two-dimensional position using [Int] pixels for units
 */
// todo Unit tests
@JvmInline
value class IntOffsetCompat internal constructor(@PublishedApi internal val packedValue: Long) {

    /**
     * The horizontal aspect of the position in [Int] pixels.
     */
    val x: Int
        get() = unpackInt1(packedValue)

    /**
     * The vertical aspect of the position in [Int] pixels.
     */
    val y: Int
        get() = unpackInt2(packedValue)

    operator fun component1(): Int = x

    operator fun component2(): Int = y

    /**
     * Returns a copy of this IntOffsetCompat instance optionally overriding the
     * x or y parameter
     */
    fun copy(x: Int = this.x, y: Int = this.y) = IntOffsetCompat(x, y)

    /**
     * Subtract a [IntOffsetCompat] from another one.
     */
    inline operator fun minus(other: IntOffsetCompat) =
        IntOffsetCompat(x - other.x, y - other.y)

    /**
     * Add a [IntOffsetCompat] to another one.
     */
    inline operator fun plus(other: IntOffsetCompat) =
        IntOffsetCompat(x + other.x, y + other.y)

    /**
     * Returns a new [IntOffsetCompat] representing the negation of this point.
     */
    inline operator fun unaryMinus() = IntOffsetCompat(-x, -y)

    /**
     * Multiplication operator.
     *
     * Returns an IntOffsetCompat whose coordinates are the coordinates of the
     * left-hand-side operand (an IntOffsetCompat) multiplied by the scalar
     * right-hand-side operand (a Float). The result is rounded to the nearest integer.
     */
    operator fun times(operand: Float): IntOffsetCompat = IntOffsetCompat(
        (x * operand).roundToInt(),
        (y * operand).roundToInt()
    )

    /**
     * Division operator.
     *
     * Returns an IntOffsetCompat whose coordinates are the coordinates of the
     * left-hand-side operand (an IntOffsetCompat) divided by the scalar right-hand-side
     * operand (a Float). The result is rounded to the nearest integer.
     */
    operator fun div(operand: Float): IntOffsetCompat = IntOffsetCompat(
        (x / operand).roundToInt(),
        (y / operand).roundToInt()
    )

    /**
     * Modulo (remainder) operator.
     *
     * Returns an IntOffsetCompat whose coordinates are the remainder of dividing the
     * coordinates of the left-hand-side operand (an IntOffsetCompat) by the scalar
     * right-hand-side operand (an Int).
     */
    operator fun rem(operand: Int) = IntOffsetCompat(x % operand, y % operand)

    override fun toString() = "($x, $y)"

    companion object {
        val Zero = IntOffsetCompat(0, 0)
    }
}


/**
 * Linearly interpolate between two [IntOffsetCompat] parameters
 *
 * The [fraction] argument represents position on the timeline, with 0.0 meaning
 * that the interpolation has not started, returning [start] (or something
 * equivalent to [start]), 1.0 meaning that the interpolation has finished,
 * returning [stop] (or something equivalent to [stop]), and values in between
 * meaning that the interpolation is at the relevant point on the timeline
 * between [start] and [stop]. The interpolation can be extrapolated beyond 0.0 and
 * 1.0, so negative values and values greater than 1.0 are valid (and can
 * easily be generated by curves).
 *
 * Values for [fraction] are usually obtained from an [Animation<Float>], such as
 * an `AnimationController`.
 */
fun lerp(start: IntOffsetCompat, stop: IntOffsetCompat, fraction: Float): IntOffsetCompat {
    return IntOffsetCompat(
        lerp(start.x, stop.x, fraction),
        lerp(start.y, stop.y, fraction)
    )
}

operator fun OffsetCompat.plus(offset: IntOffsetCompat): OffsetCompat =
    OffsetCompat(x + offset.x, y + offset.y)

operator fun OffsetCompat.minus(offset: IntOffsetCompat): OffsetCompat =
    OffsetCompat(x - offset.x, y - offset.y)

operator fun IntOffsetCompat.plus(offset: OffsetCompat): OffsetCompat =
    OffsetCompat(x + offset.x, y + offset.y)

operator fun IntOffsetCompat.minus(offset: OffsetCompat): OffsetCompat =
    OffsetCompat(x - offset.x, y - offset.y)

/**
 * Converts the [IntOffsetCompat] to an [OffsetCompat].
 */
inline fun IntOffsetCompat.toOffset() = OffsetCompat(x.toFloat(), y.toFloat())

/**
 * Round a [OffsetCompat] down to the nearest [Int] coordinates.
 */
inline fun OffsetCompat.round(): IntOffsetCompat = IntOffsetCompat(x.roundToInt(), y.roundToInt())


fun IntOffsetCompat.toShortString(): String = "${x}x${y}"

operator fun IntOffsetCompat.times(scaleFactor: ScaleFactorCompat): IntOffsetCompat {
    return IntOffsetCompat(
        x = (x * scaleFactor.scaleX).roundToInt(),
        y = (y * scaleFactor.scaleY).roundToInt(),
    )
}

operator fun IntOffsetCompat.div(scaleFactor: ScaleFactorCompat): IntOffsetCompat {
    return IntOffsetCompat(
        x = (x / scaleFactor.scaleX).roundToInt(),
        y = (y / scaleFactor.scaleY).roundToInt(),
    )
}

fun IntOffsetCompat.toSize(): SizeCompat = SizeCompat(width = x.toFloat(), height = y.toFloat())

fun IntOffsetCompat.toIntSize(): IntSizeCompat = IntSizeCompat(width = x, height = y)

fun IntOffsetCompat.rotateInSpace(spaceSize: IntSizeCompat, rotation: Int): IntOffsetCompat {
    require(rotation % 90 == 0) { "rotation must be a multiple of 90, rotation: $rotation" }
    return when (rotation % 360) {
        90 -> IntOffsetCompat(x = spaceSize.height - y, y = x)
        180 -> IntOffsetCompat(x = spaceSize.width - x, y = spaceSize.height - y)
        270 -> IntOffsetCompat(x = y, y = spaceSize.width - x)
        else -> this
    }
}

fun IntOffsetCompat.reverseRotateInSpace(spaceSize: IntSizeCompat, rotation: Int): IntOffsetCompat {
    val rotatedSpaceSize = spaceSize.rotate(rotation)
    val reverseRotation = 360 - rotation % 360
    return rotateInSpace(rotatedSpaceSize, reverseRotation)
}