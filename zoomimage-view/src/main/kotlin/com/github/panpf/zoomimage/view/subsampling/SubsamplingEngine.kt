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

package com.github.panpf.zoomimage.view.subsampling

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleEventObserver
import com.github.panpf.zoomimage.subsampling.ImageInfo
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.TileAnimationSpec
import com.github.panpf.zoomimage.subsampling.TileBitmapCache
import com.github.panpf.zoomimage.subsampling.TileBitmapCacheSpec
import com.github.panpf.zoomimage.subsampling.TileSnapshot
import com.github.panpf.zoomimage.subsampling.internal.CreateTileDecoderException
import com.github.panpf.zoomimage.subsampling.internal.TileBitmapCacheHelper
import com.github.panpf.zoomimage.subsampling.internal.TileDecoder
import com.github.panpf.zoomimage.subsampling.internal.TileManager
import com.github.panpf.zoomimage.subsampling.internal.TileManager.Companion.DefaultPausedContinuousTransformTypes
import com.github.panpf.zoomimage.subsampling.internal.calculatePreferredTileSize
import com.github.panpf.zoomimage.subsampling.internal.checkNewPreferredTileSize
import com.github.panpf.zoomimage.subsampling.internal.createTileDecoder
import com.github.panpf.zoomimage.subsampling.internal.toIntroString
import com.github.panpf.zoomimage.util.IntOffsetCompat
import com.github.panpf.zoomimage.util.IntRectCompat
import com.github.panpf.zoomimage.util.IntSizeCompat
import com.github.panpf.zoomimage.util.Logger
import com.github.panpf.zoomimage.util.ioCoroutineDispatcher
import com.github.panpf.zoomimage.util.isEmpty
import com.github.panpf.zoomimage.util.toShortString
import com.github.panpf.zoomimage.view.zoom.ZoomableEngine
import com.github.panpf.zoomimage.zoom.ContinuousTransformType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Engines that control subsampling
 *
 * @see com.github.panpf.zoomimage.view.test.subsampling.SubsamplingEngineTest
 */
class SubsamplingEngine(val zoomableEngine: ZoomableEngine) {

    private var coroutineScope: CoroutineScope? = null
    private var imageSourceFactory: ImageSource.Factory? = null
    private var tileManager: TileManager? = null
    private var tileDecoder: TileDecoder? = null
    private val tileBitmapCacheSpec = TileBitmapCacheSpec()
    private var tileBitmapCacheHelper = TileBitmapCacheHelper(tileBitmapCacheSpec)
    private var resetTileDecoderJob: Job? = null
    private val refreshTilesFlow = MutableSharedFlow<String>()
    private val preferredTileSizeState = MutableStateFlow(IntSizeCompat.Zero)
    private val contentSizeState = MutableStateFlow(IntSizeCompat.Zero)
    private val stoppedLifecycleObserver = LifecycleEventObserver { _, _ ->
        val lifecycle = lifecycle ?: return@LifecycleEventObserver
        val stopped = !lifecycle.currentState.isAtLeast(STARTED)
        this@SubsamplingEngine.stoppedState.value = stopped
        if (stopped) {
            tileManager?.clean("stopped")
        }
        coroutineScope?.launch {
            refreshTilesFlow.emit(if (stopped) "stopped" else "started")
        }
    }

    val logger: Logger = zoomableEngine.logger
    val view: View = zoomableEngine.view

    var imageKey: String? = null


    /* *********************************** Configurable properties ****************************** */

    /**
     * Set up the TileBitmap memory cache container
     */
    val tileBitmapCacheState = MutableStateFlow<TileBitmapCache?>(null)

    /**
     * If true, disabled TileBitmap memory cache
     */
    val disabledTileBitmapCacheState = MutableStateFlow(false)

    /**
     * The animation spec for tile animation
     */
    val tileAnimationSpecState = MutableStateFlow(TileAnimationSpec.Default)

    /**
     * A continuous transform type that needs to pause loading
     */
    val pausedContinuousTransformTypesState =
        MutableStateFlow(DefaultPausedContinuousTransformTypes)

    /**
     * Disabling the background tile, which saves memory and improves performance, but when switching sampleSize,
     * the basemap will be exposed, the user will be able to perceive a choppy switching process,
     * and the user experience will be reduced
     */
    val disabledBackgroundTilesState = MutableStateFlow(false)

    /**
     * If true, subsampling stops and free loaded tiles, which are reloaded after restart
     */
    val stoppedState = MutableStateFlow(false)

    /**
     * The stopped property controller, which can automatically stop and restart with the help of Lifecycle
     */
    var lifecycle: Lifecycle? = null
        set(value) {
            if (field != value) {
                field?.removeObserver(stoppedLifecycleObserver)
                field = value
                if (view.isAttachedToWindow) {
                    value?.addObserver(stoppedLifecycleObserver)
                }
            }
        }

    /**
     * If true, the bounds of each tile is displayed
     */
    val showTileBoundsState = MutableStateFlow(false)


    /* *********************************** Information properties ******************************* */

    private val _imageInfoState = MutableStateFlow<ImageInfo?>(null)
    private val _readyState = MutableStateFlow(false)
    private val _foregroundTilesState = MutableStateFlow<List<TileSnapshot>>(emptyList())
    private val _backgroundTilesState = MutableStateFlow<List<TileSnapshot>>(emptyList())
    private val _sampleSizeState = MutableStateFlow(0)
    private val _imageLoadRectState = MutableStateFlow(IntRectCompat.Zero)
    private val _tileGridSizeMapState = MutableStateFlow<Map<Int, IntOffsetCompat>>(emptyMap())

    /**
     * The information of the image, including width, height, format, etc
     */
    val imageInfoState: StateFlow<ImageInfo?> = _imageInfoState

    /**
     * Tile grid size map, key is sample size, value is tile grid size
     */
    val tileGridSizeMapState: StateFlow<Map<Int, IntOffsetCompat>> = _tileGridSizeMapState

    /**
     * Whether the image is ready for subsampling
     */
    val readyState: StateFlow<Boolean> = _readyState

    /**
     * The sample size of the image
     */
    val sampleSizeState: StateFlow<Int> = _sampleSizeState

    /**
     * The image load rect
     */
    val imageLoadRectState: StateFlow<IntRectCompat> = _imageLoadRectState

    /**
     * Foreground tiles, all tiles corresponding to the current sampleSize, this list will be updated when the sampleSize changes, when the loading state of any of the tiles and the progress of the animation changes
     */
    val foregroundTilesState: StateFlow<List<TileSnapshot>> = _foregroundTilesState

    /**
     * Background tiles to avoid revealing the basemap during the process of switching sampleSize to load a new tile, the background tile will be emptied after the new tile is fully loaded and the transition animation is complete, the list of background tiles contains only tiles within the currently loaded area
     */
    val backgroundTilesState: StateFlow<List<TileSnapshot>> = _backgroundTilesState


    init {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                onAttachToWindow()
            }

            override fun onViewDetachedFromWindow(v: View) {
                onDetachFromWindow()
            }
        })
        if (view.isAttachedToWindow) {
            onAttachToWindow()
        }
    }

    /* ********************************* Interact with consumers ******************************** */

    /**
     * Set up an image source from which image tile are loaded
     */
    fun setImageSource(imageSource: ImageSource.Factory?): Boolean {
        if (this.imageSourceFactory == imageSource) return false
        logger.d { "SubsamplingEngine. setImageSource. '${this.imageSourceFactory?.key}' -> '${imageSource?.key}'" }
        clean("setImageSource")
        this.imageSourceFactory = imageSource
        imageKey = imageSource?.key
        if (view.isAttachedToWindow) {
            resetTileDecoder("setImageSource")
        }
        return true
    }

    /**
     * Set up an image source from which image tile are loaded
     */
    fun setImageSource(imageSource: ImageSource?): Boolean {
        return setImageSource(imageSource?.let { ImageSource.WrapperFactory(it) })
    }


    /* *************************************** Internal ***************************************** */

    private fun onAttachToWindow() {
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        this.coroutineScope = coroutineScope

        coroutineScope.launch {
            preferredTileSizeState.collect {
                resetTileManager("preferredTileSizeChanged")
            }
        }
        coroutineScope.launch {
            contentSizeState.collect {
                resetTileDecoder("contentSizeChanged")
            }
        }
        coroutineScope.launch {
            tileBitmapCacheState.collect {
                tileBitmapCacheSpec.tileBitmapCache = it
            }
        }
        coroutineScope.launch {
            disabledTileBitmapCacheState.collect {
                tileBitmapCacheSpec.disabled = it
            }
        }
        coroutineScope.launch {
            tileAnimationSpecState.collect {
                tileManager?.tileAnimationSpec = it
            }
        }
        coroutineScope.launch {
            pausedContinuousTransformTypesState.collect {
                tileManager?.pausedContinuousTransformTypes = it
            }
        }
        coroutineScope.launch {
            disabledBackgroundTilesState.collect {
                tileManager?.disabledBackgroundTiles = it
            }
        }

        coroutineScope.launch {
            // Changes in viewSize cause a large chain reaction that can cause large memory fluctuations.
            // View size animations cause frequent changes in viewSize, so a delayed reset avoids this problem
            @Suppress("OPT_IN_USAGE")
            zoomableEngine.containerSizeState.debounce(80).collect {
                val oldPreferredTileSize = preferredTileSizeState.value
                val newPreferredTileSize = calculatePreferredTileSize(it)
                val checkPassed = checkNewPreferredTileSize(
                    oldPreferredTileSize = oldPreferredTileSize,
                    newPreferredTileSize = newPreferredTileSize
                )
                logger.d {
                    "SubsamplingEngine. reset preferredTileSize. " +
                            "oldPreferredTileSize=$oldPreferredTileSize, " +
                            "newPreferredTileSize=$newPreferredTileSize, " +
                            "checkPassed=$checkPassed. " +
                            "'${imageKey}'"
                }
                if (checkPassed) {
                    preferredTileSizeState.value = newPreferredTileSize
                }
            }
        }
        coroutineScope.launch {
            zoomableEngine.contentSizeState.collect {
                contentSizeState.value = it
            }
        }

        coroutineScope.launch {
            readyState.collect { ready ->
                val imageInfo = imageInfoState.value
                val imageSize = if (ready && imageInfo != null)
                    imageInfo.size else IntSizeCompat.Zero
                zoomableEngine.contentOriginSizeState.value = imageSize
            }
        }
        coroutineScope.launch {
            imageInfoState.collect { imageInfo ->
                val ready = readyState.value
                val imageSize = if (ready && imageInfo != null)
                    imageInfo.size else IntSizeCompat.Zero
                zoomableEngine.contentOriginSizeState.value = imageSize
            }
        }

        coroutineScope.launch {
            refreshTilesFlow.collect {
                refreshTiles(
                    contentVisibleRect = zoomableEngine.contentVisibleRectState.value,
                    scale = zoomableEngine.transformState.value.scaleX,
                    rotation = zoomableEngine.transformState.value.rotation.roundToInt(),
                    continuousTransformType = zoomableEngine.continuousTransformTypeState.value,
                    caller = it
                )
            }
        }
        coroutineScope.launch {
            zoomableEngine.transformState.collect {
                refreshTiles(
                    contentVisibleRect = zoomableEngine.contentVisibleRectState.value,
                    scale = zoomableEngine.transformState.value.scaleX,
                    rotation = zoomableEngine.transformState.value.rotation.roundToInt(),
                    continuousTransformType = zoomableEngine.continuousTransformTypeState.value,
                    caller = "transformChanged"
                )
            }
        }
        coroutineScope.launch {
            zoomableEngine.continuousTransformTypeState.collect {
                refreshTiles(
                    contentVisibleRect = zoomableEngine.contentVisibleRectState.value,
                    scale = zoomableEngine.transformState.value.scaleX,
                    rotation = zoomableEngine.transformState.value.rotation.roundToInt(),
                    continuousTransformType = zoomableEngine.continuousTransformTypeState.value,
                    caller = "continuousTransformTypeChanged"
                )
            }
        }

        lifecycle?.addObserver(stoppedLifecycleObserver)
    }

    private fun onDetachFromWindow() {
        val coroutineScope = this.coroutineScope
        if (coroutineScope != null) {
            coroutineScope.cancel("onDetachFromWindow")
            this.coroutineScope = null
        }

        clean("onViewDetachedFromWindow")
        lifecycle?.removeObserver(stoppedLifecycleObserver)
    }

    private fun resetTileDecoder(caller: String) {
        cleanTileManager("resetTileDecoder:$caller")
        cleanTileDecoder("resetTileDecoder:$caller")

        val imageSourceFactory = imageSourceFactory
        val contentSize = contentSizeState.value
        if (imageSourceFactory == null || contentSize.isEmpty()) {
            logger.d {
                "SubsamplingEngine. resetTileDecoder:$caller. failed. " +
                        "imageSource=${imageSourceFactory}, " +
                        "contentSize=${contentSize.toShortString()}. " +
                        "'${imageKey}'"
            }
            return
        }

        resetTileDecoderJob = coroutineScope?.launch(Dispatchers.Main) {
            val result = withContext(ioCoroutineDispatcher()) {
                val imageSource = imageSourceFactory.create()
                createTileDecoder(
                    logger = logger,
                    imageSource = imageSource,
                    thumbnailSize = contentSize,
                )
            }
            val newTileDecoder = result.getOrNull()
            if (newTileDecoder == null) {
                val exception = result.exceptionOrNull()!! as CreateTileDecoderException
                this@SubsamplingEngine._imageInfoState.value = exception.imageInfo
                val level = if (exception.skipped) Logger.Level.Debug else Logger.Level.Error
                val type = if (exception.skipped) "skipped" else "error"
                logger.log(level) {
                    "SubsamplingEngine. resetTileDecoder:$caller. $type, ${exception.message}. " +
                            "contentSize: ${contentSize.toShortString()}, " +
                            "imageInfo: ${exception.imageInfo?.toShortString()}. " +
                            "'${imageKey}'"
                }
                return@launch
            }

            val imageInfo = newTileDecoder.imageInfo
            logger.d {
                "SubsamplingEngine. resetTileDecoder:$caller. success. " +
                        "contentSize=${contentSize.toShortString()}, " +
                        "imageInfo=${imageInfo.toShortString()}. " +
                        "'${imageKey}'"
            }
            this@SubsamplingEngine.tileDecoder = newTileDecoder
            this@SubsamplingEngine._imageInfoState.value = imageInfo
            resetTileManager(caller)
        }
    }

    private fun resetTileManager(caller: String) {
        cleanTileManager(caller)

        val tileDecoder = tileDecoder
        val imageInfo = imageInfoState.value
        val preferredTileSize = preferredTileSizeState.value
        val contentSize = contentSizeState.value
        if (tileDecoder == null || imageInfo == null || preferredTileSize.isEmpty() || contentSize.isEmpty()) {
            logger.d {
                "SubsamplingEngine. resetTileManager:$caller. failed. " +
                        "contentSize=${contentSize.toShortString()}, " +
                        "preferredTileSize=${preferredTileSize.toShortString()}, " +
                        "tileDecoder=${tileDecoder}, " +
                        "'${imageKey}'"
            }
            return
        }

        val tileManager = TileManager(
            logger = logger,
            tileDecoder = tileDecoder,
            tileBitmapConvertor = null,
            preferredTileSize = preferredTileSize,
            contentSize = contentSize,
            tileBitmapCacheHelper = tileBitmapCacheHelper,
            imageInfo = imageInfo,
            onTileChanged = { manager ->
                if (this@SubsamplingEngine.tileManager == manager) {
                    _backgroundTilesState.value = manager.backgroundTiles
                    _foregroundTilesState.value = manager.foregroundTiles
                }
            },
            onSampleSizeChanged = { manager ->
                if (this@SubsamplingEngine.tileManager == manager) {
                    _sampleSizeState.value = manager.sampleSize
                }
            },
            onImageLoadRectChanged = { manager ->
                if (this@SubsamplingEngine.tileManager == manager) {
                    _imageLoadRectState.value = manager.imageLoadRect
                }
            }
        )
        tileManager.pausedContinuousTransformTypes =
            this@SubsamplingEngine.pausedContinuousTransformTypesState.value
        tileManager.disabledBackgroundTiles =
            this@SubsamplingEngine.disabledBackgroundTilesState.value
        tileManager.tileAnimationSpec = this@SubsamplingEngine.tileAnimationSpecState.value

        _tileGridSizeMapState.value = tileManager.sortedTileGridMap.associate { entry ->
            entry.sampleSize to entry.tiles.last().coordinate
                .let { IntOffsetCompat(it.x + 1, it.y + 1) }
        }
        logger.d {
            "SubsamplingEngine. resetTileManager:$caller. success. " +
                    "imageInfo=${imageInfo.toShortString()}. " +
                    "preferredTileSize=${preferredTileSize.toShortString()}, " +
                    "tileGridMap=${tileManager.sortedTileGridMap.toIntroString()}. " +
                    "'${imageKey}'"
        }
        this@SubsamplingEngine.tileManager = tileManager
        refreshReadyState("resetTileManager:$caller")
    }

    private fun refreshTiles(
        contentVisibleRect: IntRectCompat,
        scale: Float,
        rotation: Int,
        @ContinuousTransformType continuousTransformType: Int,
        caller: String,
    ) {
        val tileManager = tileManager ?: return
        if (stoppedState.value) {
            logger.d { "SubsamplingEngine. refreshTiles:$caller. interrupted, stopped. '${imageKey}'" }
            return
        }
        tileManager.refreshTiles(
            scale = scale,
            contentVisibleRect = contentVisibleRect,
            rotation = rotation,
            continuousTransformType = continuousTransformType,
            caller = caller
        )
    }

    private fun refreshReadyState(caller: String) {
        val newReady = imageInfoState.value != null && tileManager != null && tileDecoder != null
        logger.d { "SubsamplingEngine. refreshReadyState:$caller. ready=$newReady. '${imageKey}'" }
        _readyState.value = newReady
        coroutineScope?.launch {
            refreshTilesFlow.emit("refreshReadyState:$caller")
        }
    }

    private fun cleanTileDecoder(caller: String) {
        val resetTileDecoderJob1 = this@SubsamplingEngine.resetTileDecoderJob
        if (resetTileDecoderJob1 != null && resetTileDecoderJob1.isActive) {
            resetTileDecoderJob1.cancel("cleanTileDecoder:$caller")
            this@SubsamplingEngine.resetTileDecoderJob = null
        }
        val tileDecoder = this@SubsamplingEngine.tileDecoder
        if (tileDecoder != null) {
            logger.d { "SubsamplingEngine. cleanTileDecoder:$caller. '${imageKey}'" }
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(ioCoroutineDispatcher()) {
                tileDecoder.close()
            }
            this@SubsamplingEngine.tileDecoder = null
            refreshReadyState("cleanTileDecoder:$caller")
        }
        _imageInfoState.value = null
    }

    private fun cleanTileManager(caller: String) {
        val tileManager = this@SubsamplingEngine.tileManager
        if (tileManager != null) {
            logger.d { "SubsamplingEngine. cleanTileManager:$caller. '${imageKey}'" }
            tileManager.clean("cleanTileManager:$caller")
            this@SubsamplingEngine.tileManager = null
            _tileGridSizeMapState.value = emptyMap()
            _foregroundTilesState.value = emptyList()
            _backgroundTilesState.value = emptyList()
            _sampleSizeState.value = 0
            _imageLoadRectState.value = IntRectCompat.Zero
            refreshReadyState("cleanTileManager:$caller")
        }
    }

    private fun clean(caller: String) {
        cleanTileDecoder("clean:$caller")
        cleanTileManager("clean:$caller")
    }
}