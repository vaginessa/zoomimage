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

import com.github.panpf.zoomimage.internal.format

interface ReadModeDecider {
    fun should(srcSize: Size, dstSize: Size): Boolean
}

class LongImageReadModeDecider(
    val sameDirectionMultiple: Float = 2.5f,
    val notSameDirectionMultiple: Float = 5.0f,
) : ReadModeDecider {

    override fun should(srcSize: Size, dstSize: Size): Boolean =
        isLongImage(srcSize = srcSize, dstSize = dstSize)

    /**
     * Determine whether it is a long image given the image size and target size
     *
     * If the directions of image and target are the same, then the aspect ratio of
     * the two is considered as a long image when the aspect ratio reaches [sameDirectionMultiple] times,
     * otherwise it is considered as a long image when it reaches [notSameDirectionMultiple] times
     */
    private fun isLongImage(
        srcSize: Size, dstSize: Size
    ): Boolean {
        val srcAspectRatio = srcSize.width.toFloat().div(srcSize.height).format(2)
        val dstAspectRatio = dstSize.width.toFloat().div(dstSize.height).format(2)
        val sameDirection = srcAspectRatio == 1.0f
                || dstAspectRatio == 1.0f
                || (srcAspectRatio > 1.0f && dstAspectRatio > 1.0f)
                || (srcAspectRatio < 1.0f && dstAspectRatio < 1.0f)
        val ratioMultiple = if (sameDirection) sameDirectionMultiple else notSameDirectionMultiple
        return if (ratioMultiple > 0) {
            val maxAspectRatio = dstAspectRatio.coerceAtLeast(srcAspectRatio)
            val minAspectRatio = dstAspectRatio.coerceAtMost(srcAspectRatio)
            maxAspectRatio >= (minAspectRatio * ratioMultiple)
        } else {
            false
        }
    }

    override fun toString(): String {
        return "LongImageReadModeDecider(sameDirectionMultiple=$sameDirectionMultiple, notSameDirectionMultiple=$notSameDirectionMultiple)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LongImageReadModeDecider
        if (sameDirectionMultiple != other.sameDirectionMultiple) return false
        if (notSameDirectionMultiple != other.notSameDirectionMultiple) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sameDirectionMultiple.hashCode()
        result = 31 * result + notSameDirectionMultiple.hashCode()
        return result
    }
}