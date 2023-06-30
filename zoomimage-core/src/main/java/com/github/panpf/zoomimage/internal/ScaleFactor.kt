package com.github.panpf.zoomimage.internal

import com.github.panpf.zoomimage.Size
import kotlin.math.roundToInt

/**
 * Holds 2 dimensional scaling factors for horizontal and vertical axes
 */
data class ScaleFactor(
    /**
     * Returns the scale factor to apply along the horizontal axis
     */
    val scaleX: Float,
    /**
     * Returns the scale factor to apply along the vertical axis
     */
    val scaleY: Float
) {

    /**
     * Multiplication operator.
     *
     * Returns a [ScaleFactor] with scale x and y values multiplied by the operand
     */
    operator fun times(operand: Float) = ScaleFactor(scaleX * operand, scaleY * operand)

    /**
     * Division operator.
     *
     * Returns a [ScaleFactor] with scale x and y values divided by the operand
     */
    operator fun div(operand: Float) = ScaleFactor(scaleX / operand, scaleY / operand)

    override fun toString() = "ScaleFactor(${scaleX.format(2)}, ${scaleY.format(2)})"

    companion object {

        /**
         * A ScaleFactor whose [scaleX] and [scaleY] parameters are unspecified. This is a sentinel
         * value used to initialize a non-null parameter.
         * Access to scaleX or scaleY on an unspecified size is not allowed
         */
        val Unspecified = ScaleFactor(scaleX = Float.NaN, scaleY = Float.NaN)

        val Origin = ScaleFactor(scaleX = 1f, scaleY = 1f)
    }
}

/**
 * `false` when this is [ScaleFactor.Unspecified].
 */
inline val ScaleFactor.isSpecified: Boolean
    get() = !scaleX.isNaN() && !scaleY.isNaN()

/**
 * `true` when this is [ScaleFactor.Unspecified].
 */
inline val ScaleFactor.isUnspecified: Boolean
    get() = scaleX.isNaN() || scaleY.isNaN()

/**
 * If this [ScaleFactor] [isSpecified] then this is returned, otherwise [block] is executed
 * and its result is returned.
 */
inline fun ScaleFactor.takeOrElse(block: () -> ScaleFactor): ScaleFactor =
    if (isSpecified) this else block()

/**
 * Multiplication operator with [Size].
 *
 * Return a new [Size] with the width and height multiplied by the [ScaleFactor.scaleX] and
 * [ScaleFactor.scaleY] respectively
 */
operator fun Size.times(scaleFactor: ScaleFactor): Size =
    Size(
        (this.width * scaleFactor.scaleX).roundToInt(),
        (this.height * scaleFactor.scaleY).roundToInt()
    )

/**
 * Multiplication operator with [Size] with reverse parameter types to maintain
 * commutative properties of multiplication
 *
 * Return a new [Size] with the width and height multiplied by the [ScaleFactor.scaleX] and
 * [ScaleFactor.scaleY] respectively
 */
operator fun ScaleFactor.times(size: Size): Size = size * this

/**
 * Division operator with [Size]
 *
 * Return a new [Size] with the width and height divided by [ScaleFactor.scaleX] and
 * [ScaleFactor.scaleY] respectively
 */
operator fun Size.div(scaleFactor: ScaleFactor): Size =
    Size((width / scaleFactor.scaleX).roundToInt(), (height / scaleFactor.scaleY).roundToInt())

/**
 * Linearly interpolate between two [ScaleFactor] parameters
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
fun lerp(start: ScaleFactor, stop: ScaleFactor, fraction: Float): ScaleFactor {
    return ScaleFactor(
        lerp(start.scaleX, stop.scaleX, fraction),
        lerp(start.scaleY, stop.scaleY, fraction)
    )
}

/**
 * Linearly interpolate between [start] and [stop] with [fraction] fraction between them.
 */
private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}

fun ScaleFactor.toShortString(): String = "(${scaleX.format(2)},${scaleY.format(2)})"