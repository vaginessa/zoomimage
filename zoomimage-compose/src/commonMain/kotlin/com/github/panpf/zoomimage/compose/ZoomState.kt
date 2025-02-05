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

package com.github.panpf.zoomimage.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.github.panpf.zoomimage.compose.subsampling.SubsamplingState
import com.github.panpf.zoomimage.compose.subsampling.rememberSubsamplingState
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.compose.zoom.rememberZoomableState
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.util.Logger
import com.github.panpf.zoomimage.util.Logger.Level

/**
 * Creates and remember a [ZoomState]
 *
 * @see com.github.panpf.zoomimage.compose.common.test.ZoomStateTest.testRememberZoomState
 */
@Composable
fun rememberZoomState(logLevel: Level? = null): ZoomState {
    val logger = rememberZoomImageLogger(level = logLevel)
    val zoomableState = rememberZoomableState(logger)
    val subsamplingState = rememberSubsamplingState(zoomableState)
    return remember(logger, zoomableState, subsamplingState) {
        ZoomState(logger, zoomableState, subsamplingState)
    }
}

/**
 * Used to control the state of scaling, translation, rotation, and subsampling
 *
 * @see com.github.panpf.zoomimage.compose.common.test.ZoomStateTest
 */
@Stable
open class ZoomState(
    /**
     * Used to print log
     */
    val logger: Logger,

    /**
     * Used to control the state of scaling, translation, and rotation
     */
    val zoomable: ZoomableState,

    /**
     * Used to control the state of subsampling
     */
    val subsampling: SubsamplingState,
) {

    /**
     * Set up an image source from which image tile are loaded
     */
    fun setImageSource(imageSource: ImageSource.Factory?): Boolean {
        return subsampling.setImageSource(imageSource)
    }

    /**
     * Set up an image source from which image tile are loaded
     */
    fun setImageSource(imageSource: ImageSource?): Boolean {
        return subsampling.setImageSource(imageSource)
    }
}