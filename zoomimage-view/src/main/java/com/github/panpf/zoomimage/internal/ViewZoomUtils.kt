/*
 * Copyright (C) 2022 panpf <panpfpanpf@outlook.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.panpf.zoomimage.internal

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.widget.ImageView
import com.github.panpf.zoomimage.ReadModeDecider
import com.github.panpf.zoomimage.Size
import com.github.panpf.zoomimage.Transform
import com.github.panpf.zoomimage.isNotEmpty
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val MATRIX_VALUES = FloatArray(9)

/**
 * @param whichValue Example: [Matrix.MSCALE_X]
 */
internal fun Matrix.getValue(whichValue: Int): Float {
    requiredMainThread()
    getValues(MATRIX_VALUES)
    return MATRIX_VALUES[whichValue]
}

internal fun Matrix.getScale(): Float {
    requiredMainThread()
    getValues(MATRIX_VALUES)
    val scaleX: Float = MATRIX_VALUES[Matrix.MSCALE_X]
    val skewY: Float = MATRIX_VALUES[Matrix.MSKEW_Y]
    return sqrt(scaleX.toDouble().pow(2.0) + skewY.toDouble().pow(2.0)).toFloat()
}

internal fun Matrix.getRotateDegrees(): Int {
    requiredMainThread()
    getValues(MATRIX_VALUES)
    val skewX: Float = MATRIX_VALUES[Matrix.MSKEW_X]
    val scaleX: Float = MATRIX_VALUES[Matrix.MSCALE_X]
    val degrees = (atan2(skewX.toDouble(), scaleX.toDouble()) * (180 / Math.PI)).roundToInt()
    return when {
        degrees < 0 -> abs(degrees)
        degrees > 0 -> 360 - degrees
        else -> 0
    }
}

internal fun Matrix.getTranslation(point: PointF) {
    requiredMainThread()
    getValues(MATRIX_VALUES)
    point.x = MATRIX_VALUES[Matrix.MTRANS_X]
    point.y = MATRIX_VALUES[Matrix.MTRANS_Y]
}

internal fun Matrix.getTranslation(): PointF {
    requiredMainThread()
    getValues(MATRIX_VALUES)
    val point = PointF()
    point.x = MATRIX_VALUES[Matrix.MTRANS_X]
    point.y = MATRIX_VALUES[Matrix.MTRANS_Y]
    return point
}

internal fun reverseRotateRect(rect: Rect, rotateDegrees: Int, drawableSize: Size) {
    require(rotateDegrees % 90 == 0) {
        "rotateDegrees must be an integer multiple of 90"
    }
    when (rotateDegrees) {
        90 -> {
            val bottom = rect.bottom
            rect.bottom = rect.left
            rect.left = rect.top
            rect.top = rect.right
            rect.right = bottom
            rect.top = drawableSize.height - rect.top
            rect.bottom = drawableSize.height - rect.bottom
        }

        180 -> {
            var right = rect.right
            rect.right = rect.left
            rect.left = right
            right = rect.bottom
            rect.bottom = rect.top
            rect.top = right
            rect.top = drawableSize.height - rect.top
            rect.bottom = drawableSize.height - rect.bottom
            rect.left = drawableSize.width - rect.left
            rect.right = drawableSize.width - rect.right
        }

        270 -> {
            val bottom = rect.bottom
            rect.bottom = rect.right
            rect.right = rect.top
            rect.top = rect.left
            rect.left = bottom
            rect.left = drawableSize.width - rect.left
            rect.right = drawableSize.width - rect.right
        }
    }
}

internal fun rotatePoint(point: PointF, rotateDegrees: Int, drawableSize: Size) {
    require(rotateDegrees % 90 == 0) {
        "rotateDegrees must be an integer multiple of 90"
    }
    when (rotateDegrees) {
        90 -> {
            point.x = drawableSize.height - point.y
            point.y = point.x
        }

        180 -> {
            point.x = drawableSize.width - point.x
            point.y = drawableSize.height - point.y
        }

        270 -> {
            point.x = point.y
            point.y = drawableSize.width - point.x
        }
    }
}

internal fun ImageView.ScaleType.computeTransform(srcSize: Size, dstSize: Size): Transform {
    val scaleFactor = this.computeScaleFactor(srcSize, dstSize)
    val translation = this.computeScaleTranslation(srcSize, dstSize)
    return Transform(scaleFactor, translation)
}

internal fun ImageView.ScaleType.computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
    val widthScale = dstSize.width / srcSize.width.toFloat()
    val heightScale = dstSize.height / srcSize.height.toFloat()
    val fillMaxDimension = max(widthScale, heightScale)
    val fillMinDimension = min(widthScale, heightScale)
    return when (this) {
        ImageView.ScaleType.CENTER -> ScaleFactor(scaleX = 1.0f, scaleY = 1.0f)

        ImageView.ScaleType.CENTER_CROP -> {
            ScaleFactor(scaleX = fillMaxDimension, scaleY = fillMaxDimension)
        }

        ImageView.ScaleType.CENTER_INSIDE -> {
            if (srcSize.width <= dstSize.width && srcSize.height <= dstSize.height) {
                ScaleFactor(scaleX = 1.0f, scaleY = 1.0f)
            } else {
                ScaleFactor(scaleX = fillMinDimension, scaleY = fillMinDimension)
            }
        }

        ImageView.ScaleType.FIT_START,
        ImageView.ScaleType.FIT_CENTER,
        ImageView.ScaleType.FIT_END -> {
            ScaleFactor(scaleX = fillMinDimension, scaleY = fillMinDimension)
        }

        ImageView.ScaleType.FIT_XY -> {
            ScaleFactor(scaleX = widthScale, scaleY = heightScale)
        }

        ImageView.ScaleType.MATRIX -> ScaleFactor(1.0f, 1.0f)
        else -> ScaleFactor(scaleX = 1.0f, scaleY = 1.0f)
    }
}

internal fun ImageView.ScaleType.computeScaleTranslation(
    srcSize: Size,
    dstSize: Size
): Translation {
    val scaleFactor = this.computeScaleFactor(srcSize = srcSize, dstSize = dstSize)
    val scaledSrcSize = srcSize.times(scaleFactor)
    return when (this) {
        ImageView.ScaleType.CENTER -> Translation(
            translationX = (dstSize.width - scaledSrcSize.width) / 2.0f,
            translationY = (dstSize.height - scaledSrcSize.height) / 2.0f
        )

        ImageView.ScaleType.CENTER_CROP -> Translation(
            translationX = (dstSize.width - scaledSrcSize.width) / 2.0f,
            translationY = (dstSize.height - scaledSrcSize.height) / 2.0f
        )

        ImageView.ScaleType.CENTER_INSIDE -> Translation(
            translationX = (dstSize.width - scaledSrcSize.width) / 2.0f,
            translationY = (dstSize.height - scaledSrcSize.height) / 2.0f
        )

        ImageView.ScaleType.FIT_START -> Translation(
            translationX = 0.0f,
            translationY = 0.0f
        )

        ImageView.ScaleType.FIT_CENTER -> Translation(
            translationX = (dstSize.width - scaledSrcSize.width) / 2.0f,
            translationY = (dstSize.height - scaledSrcSize.height) / 2.0f
        )

        ImageView.ScaleType.FIT_END -> Translation(
            translationX = dstSize.width - scaledSrcSize.width.toFloat(),
            translationY = dstSize.height - scaledSrcSize.height.toFloat()
        )

        ImageView.ScaleType.FIT_XY -> Translation(
            translationX = 0.0f,
            translationY = 0.0f
        )

        ImageView.ScaleType.MATRIX -> Translation(
            translationX = 0.0f,
            translationY = 0.0f
        )

        else -> Translation(
            translationX = 0.0f,
            translationY = 0.0f
        )
    }
}

internal fun ReadModeDecider.computeTransform(
    scaleType: ImageView.ScaleType,
    srcSize: Size,
    dstSize: Size
): Transform {
    if (scaleType != ImageView.ScaleType.FIT_CENTER
        || !should(srcSize = srcSize, dstSize = dstSize)
    ) {
        return Transform.EMPTY
    }

    val widthScale = dstSize.width / srcSize.width.toFloat()
    val heightScale = dstSize.height / srcSize.height.toFloat()
    val fillMaxDimension = max(widthScale, heightScale)
    return Transform(
        scaleX = fillMaxDimension,
        scaleY = fillMaxDimension,
        translateX = 0.0f,
        translateY = 0.0f
    )
}

internal fun computeSupportScales(
    scaleType: ImageView.ScaleType,
    drawableSize: Size,
    imageSize: Size,
    viewSize: Size,
    readModeDecider: ReadModeDecider?
): FloatArray {
    if (scaleType == ImageView.ScaleType.FIT_XY) {
        return floatArrayOf(1.0f, 4.0f, 8.0f)
    }

    val drawableToViewWidthScale = viewSize.width / drawableSize.width.toFloat()
    val drawableToViewHeightScale = viewSize.height / drawableSize.height.toFloat()
    // The width or height of the drawable fills the view
    val fullShowScale = min(drawableToViewWidthScale, drawableToViewHeightScale)
    // The width and height of drawable fill the view at the same time
    val fillViewScale = max(drawableToViewWidthScale, drawableToViewHeightScale)
    // Enlarge drawable to the same size as its original image
    val originShowScale = if (imageSize.isNotEmpty) {
        val drawableToImageWidthScale = imageSize.width / drawableSize.width.toFloat()
        val drawableToImageHeightScale = imageSize.height / drawableSize.height.toFloat()
        max(drawableToImageWidthScale, drawableToImageHeightScale)
    } else {
        1.0f
    }
    val drawableThanViewLarge =
        drawableSize.width > viewSize.width || drawableSize.height > viewSize.height
    val drawableAspectRatio = drawableSize.width.toFloat().div(drawableSize.height).format(2)
    val viewAspectRatio = viewSize.width.toFloat().div(viewSize.height).format(2)
    val sameDirection = drawableAspectRatio == 1.0f
            || viewAspectRatio == 1.0f
            || (drawableAspectRatio > 1.0f && viewAspectRatio > 1.0f)
            || (drawableAspectRatio < 1.0f && viewAspectRatio < 1.0f)
    val baseScale = scaleType.computeScaleFactor(srcSize = drawableSize, dstSize = viewSize).scaleX
    @Suppress("UnnecessaryVariable") val minScale = baseScale

    /*
     * mediumScale 通常用于双击后的缩放比例；
     * imageSize 不为空，通常意味着当前开启了子采样功能，因此需要在双击后缩放到图片的原始大小以查看原始图片
     */
    val defaultMediumScale = if (imageSize.isNotEmpty) minScale * 2f else minScale * 4f
    val mediumScale = when (scaleType) {
        ImageView.ScaleType.CENTER_CROP ->
            floatArrayOf(originShowScale, defaultMediumScale).maxOrNull()!!

        ImageView.ScaleType.CENTER_INSIDE ->
            if (drawableThanViewLarge) {
                // same as fitCenter
                if (sameDirection) {
                    floatArrayOf(originShowScale, defaultMediumScale).maxOrNull()!!
                } else {
                    floatArrayOf(originShowScale, fillViewScale, defaultMediumScale).maxOrNull()!!
                }
            } else {
                // same as center
                floatArrayOf(originShowScale, fullShowScale, defaultMediumScale).maxOrNull()!!
            }

        ImageView.ScaleType.FIT_START ->
            if (sameDirection) {
                floatArrayOf(originShowScale, defaultMediumScale).maxOrNull()!!
            } else {
                floatArrayOf(originShowScale, fillViewScale, defaultMediumScale).maxOrNull()!!
            }

        ImageView.ScaleType.FIT_CENTER -> {
            if (readModeDecider?.should(srcSize = drawableSize, dstSize = viewSize) == true) {
                floatArrayOf(originShowScale, fillViewScale).maxOrNull()!!
            } else if (sameDirection) {
                floatArrayOf(originShowScale, defaultMediumScale).maxOrNull()!!
            } else {
                floatArrayOf(originShowScale, fillViewScale, defaultMediumScale).maxOrNull()!!
            }
        }

        ImageView.ScaleType.FIT_END ->
            if (sameDirection) {
                floatArrayOf(originShowScale, defaultMediumScale).maxOrNull()!!
            } else {
                floatArrayOf(originShowScale, fillViewScale, defaultMediumScale).maxOrNull()!!
            }

        // CENTER, MATRIX
        else -> floatArrayOf(originShowScale, fullShowScale, defaultMediumScale).maxOrNull()!!
    }

    val maxScale = floatArrayOf(mediumScale * 2f, minScale * 8f).maxOrNull()!!

    return floatArrayOf(minScale, mediumScale, maxScale)
}