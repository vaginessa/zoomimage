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
package com.github.panpf.zoomimage

import com.github.panpf.zoomimage.util.IntSizeCompat
import com.github.panpf.zoomimage.util.ScaleFactorCompat
import com.github.panpf.zoomimage.util.internal.format
import com.github.panpf.zoomimage.util.times
import kotlin.math.max

data class ReadMode(
    val acceptedImageSizeType: AcceptedImageSizeType = AcceptedImageSizeType.Both,
    val decider: Decider = Decider.Default
) {

    fun accept(contentSize: IntSizeCompat, containerSize: IntSizeCompat): Boolean {
        val acceptedImageSizeTypeMatched = when (acceptedImageSizeType) {
            AcceptedImageSizeType.OnlyHorizontal -> contentSize.width > contentSize.height
            AcceptedImageSizeType.OnlyVertical -> contentSize.width < contentSize.height
            else -> true
        }
        return if (acceptedImageSizeTypeMatched)
            decider.should(contentSize = contentSize, containerSize = containerSize) else false
    }

    companion object {
        val Default = ReadMode(acceptedImageSizeType = AcceptedImageSizeType.Both, decider = Decider.Default)
    }

    enum class AcceptedImageSizeType {
        Both, OnlyHorizontal, OnlyVertical
    }

    interface Decider {

        fun should(contentSize: IntSizeCompat, containerSize: IntSizeCompat): Boolean

        companion object {
            val Default = LongImageDecider()
        }
    }

    class LongImageDecider(
        val sameDirectionMultiple: Float = 2.5f,
        val notSameDirectionMultiple: Float = 5.0f,
    ) : Decider {

        override fun should(contentSize: IntSizeCompat, containerSize: IntSizeCompat): Boolean {
            val fillScale = max(
                containerSize.width / contentSize.width.toFloat(),
                containerSize.height / contentSize.height.toFloat()
            )
            val filledSrcSize = contentSize.times(ScaleFactorCompat(fillScale))
            val maxScaleMultiple = max(
                filledSrcSize.width / containerSize.width.toFloat(),
                filledSrcSize.height / containerSize.height.toFloat()
            )
            val sameDirection = isSameDirection(srcSize = contentSize, dstSize = containerSize)
            val minMultiple = if (sameDirection) sameDirectionMultiple else notSameDirectionMultiple
            return maxScaleMultiple.format(1) >= minMultiple.format(1)
        }

        override fun toString(): String {
            return "LongImageDecider(same=$sameDirectionMultiple,notSame=$notSameDirectionMultiple)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as LongImageDecider
            if (sameDirectionMultiple != other.sameDirectionMultiple) return false
            if (notSameDirectionMultiple != other.notSameDirectionMultiple) return false
            return true
        }

        override fun hashCode(): Int {
            var result = sameDirectionMultiple.hashCode()
            result = 31 * result + notSameDirectionMultiple.hashCode()
            return result
        }

        private fun isSameDirection(srcSize: IntSizeCompat, dstSize: IntSizeCompat): Boolean {
            val srcAspectRatio = srcSize.width.toFloat().div(srcSize.height).format(2)
            val dstAspectRatio = dstSize.width.toFloat().div(dstSize.height).format(2)
            return (srcAspectRatio == 1.0f || dstAspectRatio == 1.0f)
                    || (srcAspectRatio > 1.0f && dstAspectRatio > 1.0f)
                    || (srcAspectRatio < 1.0f && dstAspectRatio < 1.0f)
        }
    }
}