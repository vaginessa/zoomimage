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

package com.github.panpf.zoomimage.compose.util

import kotlin.math.pow
import kotlin.math.round

/**
 * Format the float number to the specified number of decimal places
 *
 * @see com.github.panpf.zoomimage.compose.common.test.util.ComposeOtherUtilsTest.testFormat
 */
internal fun Float.format(newScale: Int): Float {
    return if (this.isNaN()) {
        this
    } else {
        val multiplier = 10.0.pow(newScale)
        (round(this * multiplier) / multiplier).toFloat()
    }
}

/**
 * Convert the object to a hexadecimal string
 *
 * @see com.github.panpf.zoomimage.compose.common.test.util.ComposeOtherUtilsTest.testToHexString
 */
internal fun Any.toHexString(): String = this.hashCode().toString(16)