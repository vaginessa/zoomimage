/*
 * Copyright (C) 2023 panpf <panpfpanpf@outlook.com>
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

package com.github.panpf.zoomimage.compose.subsampling

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.github.panpf.zoomimage.subsampling.BitmapFrom
import com.github.panpf.zoomimage.subsampling.SkiaTileBitmap

/**
 * [ComposeTileBitmap] implementation based on [SkiaTileBitmap]
 *
 * @see com.github.panpf.zoomimage.compose.nonandroid.test.subsampling.ComposeSkiaTileBitmapTest
 */
class ComposeSkiaTileBitmap constructor(
    private val tileBitmap: SkiaTileBitmap,
) : ComposeTileBitmap {

    override val key: String = tileBitmap.key

    override val bitmapFrom: BitmapFrom = tileBitmap.bitmapFrom

    override val bitmap: ImageBitmap = tileBitmap.bitmap.asComposeImageBitmap()

    override val width: Int
        get() = tileBitmap.width

    override val height: Int
        get() = tileBitmap.height

    override val byteCount: Long
        get() = tileBitmap.byteCount

    override val isRecycled: Boolean
        get() = tileBitmap.isRecycled

    override fun setIsDisplayed(displayed: Boolean) {
        tileBitmap.setIsDisplayed(displayed)
    }

    override fun recycle() {
        tileBitmap.recycle()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ComposeSkiaTileBitmap
        return tileBitmap == other.tileBitmap
    }

    override fun hashCode(): Int {
        return tileBitmap.hashCode()
    }

    override fun toString(): String {
        return "ComposeSkiaTileBitmap($tileBitmap)"
    }
}