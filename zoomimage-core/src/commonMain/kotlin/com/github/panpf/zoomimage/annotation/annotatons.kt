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

package com.github.panpf.zoomimage.annotation

/**
 * Marks declarations that should only be called from the main thread.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.SOURCE)
internal annotation class MainThread

/**
 * Marks declarations that should only be called from a worker thread (on platforms that have
 * multiple threads).
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.SOURCE)
internal annotation class WorkerThread

/**
 * Marks a statement that can be called from any thread (on platforms that have
 *  * multiple threads).
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.SOURCE)
internal annotation class AnyThread

@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.ANNOTATION_CLASS
)
internal annotation class IntRange(
    /** Smallest value, inclusive  */
    val from: Long = Long.MIN_VALUE,
    /** Largest value, inclusive  */
    val to: Long = Long.MAX_VALUE,
)

/**
 * Denotes that the annotated element of integer type, represents
 * a logical type and that its value should be one of the explicitly
 * named constants. If the IntDef#flag() attribute is set to true,
 * multiple constants can be combined.
 *
 * Example:
 * ```
 * @Retention(SOURCE)
 * @IntDef({NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS})
 * public @interface NavigationMode {}
 * public static final int NAVIGATION_MODE_STANDARD = 0;
 * public static final int NAVIGATION_MODE_LIST = 1;
 * public static final int NAVIGATION_MODE_TABS = 2;
 * ...
 * public abstract void setNavigationMode(@NavigationMode int mode);
 *
 * @NavigationMode
 * public abstract int getNavigationMode();
 * ```
 *
 * For a flag, set the flag attribute:
 * ```
 * @IntDef(
 * flag = true,
 * value = {NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS}
 * )
 * ```
 *
 * @see LongDef
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class IntDef(
    /** Defines the allowed constants for this element  */
    vararg val value: Int = [],
    /** Defines whether the constants can be used as a flag, or just as an enum (the default)  */
    val flag: Boolean = false,
    /**
     * Whether any other values are allowed. Normally this is
     * not the case, but this allows you to specify a set of
     * expected constants, which helps code completion in the IDE
     * and documentation generation and so on, but without
     * flagging compilation warnings if other values are specified.
     */
    val open: Boolean = false
)