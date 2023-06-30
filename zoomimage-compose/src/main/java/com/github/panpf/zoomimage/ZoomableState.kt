package com.github.panpf.zoomimage

import android.util.Log
import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Velocity
import com.github.panpf.zoomimage.core.internal.calculateNextStepScale
import com.github.panpf.zoomimage.core.internal.computeSupportScales
import com.github.panpf.zoomimage.internal.ScaleFactor
import com.github.panpf.zoomimage.internal.Transform
import com.github.panpf.zoomimage.internal.Translation
import com.github.panpf.zoomimage.internal.computeContainerCentroidByTouchPosition
import com.github.panpf.zoomimage.internal.computeContainerVisibleRect
import com.github.panpf.zoomimage.internal.computeContentInContainerRect
import com.github.panpf.zoomimage.internal.computeContentVisibleRect
import com.github.panpf.zoomimage.internal.computeReadModeTransform
import com.github.panpf.zoomimage.internal.computeScaleTargetTranslation
import com.github.panpf.zoomimage.internal.computeScaleTranslation
import com.github.panpf.zoomimage.internal.computeScrollEdge
import com.github.panpf.zoomimage.internal.computeSupportTranslationBounds
import com.github.panpf.zoomimage.internal.computeTransform
import com.github.panpf.zoomimage.internal.containerCentroidToContentCentroid
import com.github.panpf.zoomimage.internal.contentCentroidToContainerCentroid
import com.github.panpf.zoomimage.internal.rotate
import com.github.panpf.zoomimage.internal.supportReadMode
import com.github.panpf.zoomimage.internal.toScaleFactor
import com.github.panpf.zoomimage.internal.toScaleMode
import com.github.panpf.zoomimage.internal.toShortString
import com.github.panpf.zoomimage.internal.toSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun rememberZoomableState(
    threeStepScaleEnabled: Boolean = false,
    readModeEnabled: Boolean = false,
    readModeDecider: ReadModeDecider = ReadModeDecider.Default,
    debugMode: Boolean = false,
): ZoomableState {
    val state = rememberSaveable(saver = ZoomableState.Saver) {
        ZoomableState()
    }
    state.threeStepScaleEnabled = threeStepScaleEnabled
    state.readModeEnabled = readModeEnabled
    state.readModeDecider = readModeDecider
    state.debugMode = debugMode
    LaunchedEffect(
        state.containerSize,
        state.contentSize,
        state.contentOriginSize,
        state.contentScale,
        state.contentAlignment,
        readModeEnabled,
        readModeDecider,
    ) {
        if (state.contentSize.isUnspecified && state.containerSize.isSpecified) {
            state.contentSize = state.containerSize
        }
        state.reset()
    }
    return state
}

class ZoomableState(
    @FloatRange(from = 0.0) initialScale: Float = 1f,
    @FloatRange(from = 0.0) initialTranslateX: Float = 0f,
    @FloatRange(from = 0.0) initialTranslateY: Float = 0f,
) {

    // todo support click and long press
    // todo support rubber band effect

    private val scaleAnimatable = Animatable(initialScale)
    private val translationXAnimatable = Animatable(initialTranslateX)
    private val translationYAnimatable = Animatable(initialTranslateY)

    /**
     * Initial scale and translate for support
     */
    private var supportInitialTransform: Transform = Transform.Empty

    var containerSize: Size by mutableStateOf(Size.Unspecified)
    var contentSize: Size by mutableStateOf(Size.Unspecified)
    var contentOriginSize: Size by mutableStateOf(Size.Unspecified)
    var contentScale: ContentScale by mutableStateOf(ContentScale.Fit)
    var contentAlignment: Alignment by mutableStateOf(Alignment.Center)
    var threeStepScaleEnabled: Boolean = false
    var debugMode: Boolean = false
    var readModeEnabled: Boolean = false
    var readModeDecider: ReadModeDecider = ReadModeDecider.Default

    var minScale: Float by mutableStateOf(1f)
        private set
    var mediumScale: Float by mutableStateOf(1f)
        private set
    var maxScale: Float by mutableStateOf(1f)
        private set

    /**
     * The current scale value for [ZoomImage]
     */
    @get:FloatRange(from = 0.0)
    val scale: Float by derivedStateOf { scaleAnimatable.value }
    val baseScale: ScaleFactor by derivedStateOf {
        val contentSize = contentSize
        val containerSize = containerSize
        if (containerSize.isUnspecified || containerSize.isEmpty()
            || contentSize.isUnspecified || contentSize.isEmpty()
        ) {
            ScaleFactor(1f, 1f)
        } else {
            contentScale.computeScaleFactor(contentSize, containerSize).toScaleFactor()
        }
    }
    val displayScale: ScaleFactor by derivedStateOf {
        baseScale.times(scale)
    }

    /**
     * The current translation value for [ZoomImage]
     */
    val translation: Translation by derivedStateOf {
        Translation(
            translationX = translationXAnimatable.value,
            translationY = translationYAnimatable.value
        )
    }
    @Suppress("MemberVisibilityCanBePrivate")
    val baseTranslation: Translation by derivedStateOf {
        computeScaleTranslation(
            srcSize = contentSize,
            dstSize = containerSize,
            scale = contentScale,
            alignment = contentAlignment,
        )
    }
    @Suppress("unused")
    val displayTranslation: Translation by derivedStateOf {
        val baseTranslation = baseTranslation
        val translation = translation
        Translation(
            translationX = baseTranslation.translationX + translation.translationX,
            translationY = baseTranslation.translationY + translation.translationY
        )
    }

    val rotation: Float by mutableStateOf(0f)    // todo support rotation
    val transformOrigin = TransformOrigin(0f, 0f)

    val containerVisibleRect: Rect by derivedStateOf {
        computeContainerVisibleRect(containerSize, scale, translation)
    }
    val contentVisibleRect: Rect by derivedStateOf {
        computeContentVisibleRect(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            scale = scale,
            translation = translation,
        )
    }
    val contentInContainerRect: Rect by derivedStateOf {
        computeContentInContainerRect(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
        )
    }

    var translationBounds: Rect? by mutableStateOf(null)
        private set
    val horizontalScrollEdge: Edge by derivedStateOf {
        computeScrollEdge(contentSize, contentVisibleRect, horizontal = true)
    }
    val verticalScrollEdge: Edge by derivedStateOf {
        computeScrollEdge(contentSize, contentVisibleRect, horizontal = false)
    }

    internal suspend fun reset() {
        val contentSize = contentSize
        val contentOriginSize = contentOriginSize
        val containerSize = containerSize
        val contentScale = contentScale
        val contentAlignment = contentAlignment
        if (containerSize.isUnspecified || containerSize.isEmpty()
            || contentSize.isUnspecified || contentSize.isEmpty()
        ) {
            minScale = 1.0f
            mediumScale = 1.0f
            maxScale = 1.0f
            supportInitialTransform = Transform.Empty
        } else {
            val rotatedContentSize = contentSize.rotate(rotation.roundToInt())
            val rotatedContentOriginSize = contentOriginSize.rotate(rotation.roundToInt())
            val scales = computeSupportScales(
                contentSize = rotatedContentSize.toSize(),
                contentOriginSize = rotatedContentOriginSize.toSize(),
                containerSize = containerSize.toSize(),
                scaleMode = contentScale.toScaleMode(),
                baseScale = contentScale.computeScaleFactor(rotatedContentSize, containerSize)
                    .toScaleFactor()
            )
            minScale = scales[0]
            mediumScale = scales[1]
            maxScale = scales[2]
            val readMode = readModeEnabled
                    && contentScale.supportReadMode()
                    && readModeDecider
                .should(srcSize = rotatedContentSize.toSize(), dstSize = containerSize.toSize())
            val baseTransform = computeTransform(
                srcSize = rotatedContentSize,
                dstSize = containerSize,
                scale = contentScale,
                alignment = contentAlignment,
            )
            supportInitialTransform = if (readMode) {
                computeReadModeTransform(
                    srcSize = rotatedContentSize,
                    dstSize = containerSize,
                    scale = contentScale,
                    alignment = contentAlignment,
                ).let {
                    Transform(
                        scaleX = it.scaleX / baseTransform.scaleX,
                        scaleY = it.scaleY / baseTransform.scaleY,
                        translationX = it.translationX / baseTransform.scaleX,
                        translationY = it.translationY / baseTransform.scaleY,
                    )
                }
            } else {
                Transform.Empty
            }
        }
        log {
            "reset. contentSize=$contentSize, " +
                    "contentOriginSize=$contentOriginSize, " +
                    "containerSize=$containerSize, " +
                    "contentScale=$contentScale, " +
                    "contentAlignment=$contentAlignment, " +
                    "minScale=$minScale, " +
                    "mediumScale=$mediumScale, " +
                    "maxScale=$maxScale, " +
                    "supportInitialTransform=$supportInitialTransform"
        }
        scaleAnimatable.snapTo(supportInitialTransform.scaleX)
        translationXAnimatable.snapTo(supportInitialTransform.translationX)
        translationYAnimatable.snapTo(supportInitialTransform.translationY)
        updateTranslationBounds("reset")
    }

    /**
     * Animates scale of [ZoomImage] to given [newScale]
     */
    suspend fun animateScaleTo(
        newScale: Float,
        newScaleContentCentroid: Centroid = Centroid(0.5f, 0.5f),
        animationDurationMillis: Int = AnimationConfig.DefaultDurationMillis,
        animationEasing: Easing = AnimationConfig.DefaultEasing,
        initialVelocity: Float = AnimationConfig.DefaultInitialVelocity,
    ) {
        stopAllAnimation("animateScaleTo")
        val containerSize = containerSize.takeIf { it.isSpecified } ?: return
        val contentSize = contentSize.takeIf { it.isSpecified } ?: return
        val contentScale = contentScale
        val contentAlignment = contentAlignment
        val currentScale = scale
        val currentTranslation = translation

        val animationSpec = tween<Float>(
            durationMillis = animationDurationMillis,
            easing = animationEasing
        )
        val futureTranslationBounds = computeSupportTranslationBounds(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            supportScale = newScale
        )
        val containerCentroid = contentCentroidToContainerCentroid(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            contentCentroid = newScaleContentCentroid
        )
        val targetTranslation = computeScaleTargetTranslation(
            containerSize = containerSize,
            scale = newScale,
            containerCentroid = containerCentroid
        ).let {
            it.copy(
                translationX = it.translationX.coerceIn(futureTranslationBounds.left, futureTranslationBounds.right),
                translationY = it.translationY.coerceIn(futureTranslationBounds.top, futureTranslationBounds.bottom),
            )
        }
        log {
            """animateScaleTo. size: containerSize=${containerSize.toShortString()}, contentSize=${contentSize.toShortString()}
                scale: $currentScale -> $newScale, contentCentroid=${newScaleContentCentroid.toShortString()}, containerCentroid=${containerCentroid.toShortString()}
                translation: ${currentTranslation.toShortString()} -> ${targetTranslation.toShortString()}, bounds=${futureTranslationBounds.toShortString()}
            """.trimIndent()
        }
        clearTranslationBounds("animateScaleTo. before")
        coroutineScope {
            launch {
                scaleAnimatable.animateTo(
                    targetValue = newScale.coerceIn(minScale, maxScale),
                    animationSpec = animationSpec,
                    initialVelocity = initialVelocity,
                ) {
                    log { "animateScaleTo. running. scale=${this.value}, translation=${translation.toShortString()}" }
                }
                updateTranslationBounds("animateScaleTo. end")
                log { "animateScaleTo. end. scale=${scale}, translation=${translation.toShortString()}" }
            }
            launch {
                translationXAnimatable.animateTo(
                    targetValue = targetTranslation.translationX,
                    animationSpec = animationSpec,
                )
            }
            launch {
                translationYAnimatable.animateTo(
                    targetValue = targetTranslation.translationY,
                    animationSpec = animationSpec,
                )
            }
        }
    }

    /**
     * Animates scale of [ZoomImage] to given [newScale]
     */
    suspend fun animateScaleTo(
        newScale: Float,
        touchPosition: Offset,
        animationDurationMillis: Int = AnimationConfig.DefaultDurationMillis,
        animationEasing: Easing = AnimationConfig.DefaultEasing,
        initialVelocity: Float = AnimationConfig.DefaultInitialVelocity,
    ) {
        stopAllAnimation("animateScaleTo")
        val containerSize = containerSize.takeIf { it.isSpecified } ?: return
        val contentSize = contentSize.takeIf { it.isSpecified } ?: return
        val contentScale = contentScale
        val contentAlignment = contentAlignment
        val currentScale = scale
        val currentTranslation = translation
        val containerCentroid = computeContainerCentroidByTouchPosition(
            containerSize = containerSize,
            scale = currentScale,
            translation = currentTranslation,
            touchPosition = touchPosition
        )
        val contentCentroid = containerCentroidToContentCentroid(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            containerCentroid = containerCentroid
        )
        log { "animateScaleTo. newScale=$newScale, touchPosition=${touchPosition.toShortString()}, containerCentroid=${containerCentroid.toShortString()}, contentCentroid=${contentCentroid.toShortString()}" }
        animateScaleTo(
            newScale = newScale,
            newScaleContentCentroid = contentCentroid,
            animationDurationMillis = animationDurationMillis,
            animationEasing = animationEasing,
            initialVelocity = initialVelocity,
        )
    }

    /**
     * Instantly sets scale of [ZoomImage] to given [newScale]
     */
    suspend fun snapScaleTo(
        newScale: Float,
        newScaleContentCentroid: Centroid = Centroid(0.5f, 0.5f)
    ) {
        stopAllAnimation("snapScaleTo")
        val containerSize = containerSize.takeIf { it.isSpecified } ?: return
        val contentSize = contentSize.takeIf { it.isSpecified } ?: return
        val contentScale = contentScale
        val contentAlignment = contentAlignment
        val currentScale = scale
        val currentTranslation = translation

        val futureTranslationBounds = computeSupportTranslationBounds(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            supportScale = newScale
        )
        val containerCentroid = contentCentroidToContainerCentroid(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            contentCentroid = newScaleContentCentroid
        )
        val targetTranslation = computeScaleTargetTranslation(
            containerSize = containerSize,
            scale = newScale,
            containerCentroid = containerCentroid
        ).let {
            it.copy(
                translationX = it.translationX.coerceIn(futureTranslationBounds.left, futureTranslationBounds.right),
                translationY = it.translationY.coerceIn(futureTranslationBounds.top, futureTranslationBounds.bottom),
            )
        }
        log {
            """snapScaleTo. size: containerSize=${containerSize.toShortString()}, contentSize=${contentSize.toShortString()} 
                 scale: $currentScale -> $newScale, contentCentroid=${newScaleContentCentroid.toShortString()}, containerCentroid=${containerCentroid.toShortString()}
                translation: ${currentTranslation.toShortString()} -> ${targetTranslation.toShortString()}, bounds=${futureTranslationBounds.toShortString()}
            """.trimIndent()
        }
        coroutineScope {
            scaleAnimatable.snapTo(
                newScale.coerceIn(
                    minimumValue = minScale,
                    maximumValue = maxScale
                )
            )
            updateTranslationBounds("snapScaleTo")
            translationXAnimatable.snapTo(targetValue = targetTranslation.translationX)
            translationYAnimatable.snapTo(targetValue = targetTranslation.translationY)
        }
    }

    /**
     * Instantly sets scale of [ZoomImage] to given [newScale]
     */
    suspend fun snapScaleTo(newScale: Float, touchPosition: Offset) {
        stopAllAnimation("snapScaleTo")
        val containerSize = containerSize.takeIf { it.isSpecified } ?: return
        val contentSize = contentSize.takeIf { it.isSpecified } ?: return
        val contentScale = contentScale
        val contentAlignment = contentAlignment
        val currentScale = scale
        val currentTranslation = translation
        val containerCentroid = computeContainerCentroidByTouchPosition(
            containerSize = containerSize,
            scale = currentScale,
            translation = currentTranslation,
            touchPosition = touchPosition
        )
        val contentCentroid = containerCentroidToContentCentroid(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            containerCentroid = containerCentroid
        )
        log { "snapScaleTo. newScale=$newScale, touchPosition=${touchPosition.toShortString()}, contentCentroid=${contentCentroid.toShortString()}" }
        snapScaleTo(
            newScale = newScale,
            newScaleContentCentroid = contentCentroid
        )
    }

    fun getNextStepScale(): Float {
        val stepScales = if (threeStepScaleEnabled) {
            floatArrayOf(minScale, mediumScale, maxScale)
        } else {
            floatArrayOf(minScale, mediumScale)
        }
        return calculateNextStepScale(stepScales, scale)
    }

    internal suspend fun dragStart() {
        stopAllAnimation("dragStart")
        log { "drag. start" }
    }

    internal suspend fun drag(
        @Suppress("UNUSED_PARAMETER") change: PointerInputChange,
        dragAmount: Offset
    ) {
        val newTranslation = Offset(
            x = translationXAnimatable.value + dragAmount.x,
            y = translationYAnimatable.value + dragAmount.y
        )
        log { "drag. running. dragAmount=${dragAmount.toShortString()}, newTranslation=${newTranslation.toShortString()}" }
        coroutineScope {
            launch {
                translationXAnimatable.snapTo(newTranslation.x)
                translationYAnimatable.snapTo(newTranslation.y)
            }
        }
    }

    internal suspend fun dragEnd(velocity: Velocity) {
        log { "drag. end. velocity=$velocity" }
        fling(velocity)
    }

    internal fun dragCancel() {
        log { "drag. cancel" }
    }

    internal suspend fun transform(zoomChange: Float, touchCentroid: Offset) {
        stopAllAnimation("transform")
        val currentScale = scale
        val newScale =
            (currentScale * zoomChange).coerceIn(minimumValue = minScale, maximumValue = maxScale)
        val addCentroidOffset = Offset(
            x = (newScale - currentScale) * touchCentroid.x * -1,
            y = (newScale - currentScale) * touchCentroid.y * -1
        )
        val targetTranslation = Offset(
            x = translationXAnimatable.value + addCentroidOffset.x,
            y = translationYAnimatable.value + addCentroidOffset.y
        )
        log { "transform. zoomChange=$zoomChange, touchCentroid=${touchCentroid.toShortString()}, newScale=$newScale, addCentroidOffset=${addCentroidOffset.toShortString()}, targetTranslation=${targetTranslation.toShortString()}" }
        coroutineScope {
            scaleAnimatable.snapTo(newScale)
            updateTranslationBounds("snapScaleTo")
            translationXAnimatable.snapTo(targetValue = targetTranslation.x)
            translationYAnimatable.snapTo(targetValue = targetTranslation.y)
        }
    }

    private suspend fun stopAllAnimation(caller: String) {
        if (scaleAnimatable.isRunning) {
            scaleAnimatable.stop()
            log { "stopAllAnimation. stop scale. scale=$scale" }
            updateTranslationBounds(caller)
        }
        if (translationXAnimatable.isRunning || translationYAnimatable.isRunning) {
            translationXAnimatable.stop()
            translationYAnimatable.stop()
            log { "stopAllAnimation. stop translation. translation=${translation.toShortString()}" }
        }
    }

    private suspend fun fling(velocity: Velocity) = coroutineScope {
        log { "fling. velocity=$velocity, translation=${translation.toShortString()}" }
        launch {
            // todo fling 滚动距离总是很近，不知道为什么，比 View 版的差一倍
            val startX = translationXAnimatable.value
            translationXAnimatable.animateDecay(velocity.x, exponentialDecay()) {
                val translationX = this.value
                val distanceX = translationX - startX
                log { "fling. running. velocity=$velocity, startX=$startX, translationX=$translationX, distanceX=$distanceX" }
            }
        }
        launch {
            val startY = translationYAnimatable.value
            translationYAnimatable.animateDecay(velocity.y, exponentialDecay()) {
                val translationY = this.value
                val distanceY = translationY - startY
                log { "fling. running. velocity=$velocity, startY=$startY, translationY=$translationY, distanceY=$distanceY" }
            }
        }
    }

    override fun toString(): String =
        "MyZoomState(minScale=$minScale, maxScale=$maxScale, scale=$scale, translation=${translation.toShortString()}"

    private fun updateTranslationBounds(caller: String) {
        val containerSize = containerSize.takeIf { it.isSpecified } ?: return
        val contentSize = contentSize.takeIf { it.isSpecified } ?: return
        val contentScale = contentScale
        val contentAlignment = contentAlignment
        val currentScale = scale
        val bounds = computeSupportTranslationBounds(
            containerSize = containerSize,
            contentSize = contentSize,
            contentScale = contentScale,
            contentAlignment = contentAlignment,
            supportScale = currentScale
        )
        this.translationBounds = bounds
        log { "updateTranslationBounds. $caller. bounds=${bounds.toShortString()}, containerSize=${containerSize.toShortString()}, contentSize=${contentSize.toShortString()}, scale=$currentScale" }
        translationXAnimatable.updateBounds(lowerBound = bounds.left, upperBound = bounds.right)
        translationYAnimatable.updateBounds(lowerBound = bounds.top, upperBound = bounds.bottom)
    }

    private fun clearTranslationBounds(@Suppress("SameParameterValue") caller: String) {
        log { "updateTranslationBounds. ${caller}. clear" }
        this.translationBounds = null
        translationXAnimatable.updateBounds(lowerBound = null, upperBound = null)
        translationYAnimatable.updateBounds(lowerBound = null, upperBound = null)
    }

    private fun log(message: () -> String) {
        if (debugMode) {
            Log.d("MyZoomState", message())
        }
    }

    companion object {

        /**
         * The default [Saver] implementation for [ZoomableState].
         */
        val Saver: Saver<ZoomableState, *> = mapSaver(
            save = {
                mapOf(
                    "scale" to it.scale,
                    "translationX" to it.translation.translationX,
                    "translationY" to it.translation.translationY,
                )
            },
            restore = {
                ZoomableState(
                    initialScale = it["scale"] as Float,
                    initialTranslateX = it["translationX"] as Float,
                    initialTranslateY = it["translationY"] as Float,
                )
            }
        )
    }
}