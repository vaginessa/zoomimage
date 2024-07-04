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

package com.github.panpf.zoomimage.sample.ui.examples.view

import com.github.panpf.zoomimage.sample.R as CommonR
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.github.panpf.tools4a.view.ktx.animTranslate
import com.github.panpf.zoomimage.ZoomImageView
import com.github.panpf.zoomimage.sample.appSettings
import com.github.panpf.zoomimage.sample.databinding.FragmentZoomViewBinding
import com.github.panpf.zoomimage.sample.ui.base.view.BaseBindingFragment
import com.github.panpf.zoomimage.sample.ui.widget.view.StateView
import com.github.panpf.zoomimage.sample.ui.widget.view.ZoomImageMinimapView
import com.github.panpf.zoomimage.sample.util.collectWithLifecycle
import com.github.panpf.zoomimage.sample.util.repeatCollectWithLifecycle
import com.github.panpf.zoomimage.subsampling.TileAnimationSpec
import com.github.panpf.zoomimage.util.Logger
import com.github.panpf.zoomimage.util.toShortString
import com.github.panpf.zoomimage.view.zoom.OnViewLongPressListener
import com.github.panpf.zoomimage.view.zoom.ScrollBarSpec
import com.github.panpf.zoomimage.view.zoom.ZoomAnimationSpec
import com.github.panpf.zoomimage.zoom.AlignmentCompat
import com.github.panpf.zoomimage.zoom.ContentScaleCompat
import com.github.panpf.zoomimage.zoom.ReadMode
import com.github.panpf.zoomimage.zoom.ScalesCalculator
import com.github.panpf.zoomimage.zoom.valueOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

abstract class BaseZoomImageViewFragment<ZOOM_VIEW : ZoomImageView> :
    BaseBindingFragment<FragmentZoomViewBinding>() {

    abstract val sketchImageUri: String

    private var zoomView: ZOOM_VIEW? = null

    abstract fun createZoomImageView(context: Context): ZOOM_VIEW

    open fun onViewCreated(
        binding: FragmentZoomViewBinding,
        zoomView: ZOOM_VIEW,
        savedInstanceState: Bundle?
    ) {

    }

    override fun getNavigationBarInsetsView(binding: FragmentZoomViewBinding): View {
        return binding.toolsLayout
    }

    final override fun onViewCreated(
        binding: FragmentZoomViewBinding,
        savedInstanceState: Bundle?
    ) {
        val zoomImageView = createZoomImageView(binding.root.context)
        this.zoomView = zoomImageView
        binding.contentLayout.addView(
            zoomImageView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        zoomImageView.apply {
            onViewLongPressListener = OnViewLongPressListener { _, _ ->
                ZoomImageViewInfoDialogFragment().apply {
                    arguments = ZoomImageViewInfoDialogFragment
                        .buildArgs(zoomImageView, sketchImageUri)
                        .toBundle()
                }.show(childFragmentManager, null)
            }
            appSettings.logLevel.collectWithLifecycle(viewLifecycleOwner) {
                logger.level = Logger.level(it)
            }
            appSettings.scrollBarEnabled.collectWithLifecycle(viewLifecycleOwner) {
                scrollBar = if (it) ScrollBarSpec.Default else null
            }
            zoomable.apply {
                appSettings.contentScale.collectWithLifecycle(viewLifecycleOwner) {
                    contentScaleState.value = ContentScaleCompat.valueOf(it)
                }
                appSettings.alignment.collectWithLifecycle(viewLifecycleOwner) {
                    alignmentState.value = AlignmentCompat.valueOf(it)
                }
                appSettings.threeStepScale.collectWithLifecycle(viewLifecycleOwner) {
                    threeStepScaleState.value = it
                }
                appSettings.rubberBandScale.collectWithLifecycle(viewLifecycleOwner) {
                    rubberBandScaleState.value = it
                }
                appSettings.scalesMultiple
                    .collectWithLifecycle(viewLifecycleOwner) {
                        val scalesMultiple = appSettings.scalesMultiple.value.toFloat()
                        val scalesCalculatorName = appSettings.scalesCalculator.value
                        scalesCalculatorState.value = if (scalesCalculatorName == "Dynamic") {
                            ScalesCalculator.dynamic(scalesMultiple)
                        } else {
                            ScalesCalculator.fixed(scalesMultiple)
                        }
                    }
                appSettings.scalesCalculator
                    .collectWithLifecycle(viewLifecycleOwner) {
                        val scalesMultiple = appSettings.scalesMultiple.value.toFloat()
                        val scalesCalculatorName = appSettings.scalesCalculator.value
                        scalesCalculatorState.value = if (scalesCalculatorName == "Dynamic") {
                            ScalesCalculator.dynamic(scalesMultiple)
                        } else {
                            ScalesCalculator.fixed(scalesMultiple)
                        }
                    }
                appSettings.limitOffsetWithinBaseVisibleRect
                    .collectWithLifecycle(viewLifecycleOwner) {
                        limitOffsetWithinBaseVisibleRectState.value = it
                    }
                appSettings.readModeEnabled.collectWithLifecycle(viewLifecycleOwner) {
                    val sizeType = if (appSettings.readModeAcceptedBoth.value) {
                        ReadMode.SIZE_TYPE_HORIZONTAL or ReadMode.SIZE_TYPE_VERTICAL
                    } else if (appSettings.horizontalPagerLayout.value) {
                        ReadMode.SIZE_TYPE_HORIZONTAL
                    } else {
                        ReadMode.SIZE_TYPE_VERTICAL
                    }
                    readModeState.value =
                        if (appSettings.readModeEnabled.value) ReadMode(sizeType = sizeType) else null
                }
                appSettings.readModeAcceptedBoth.collectWithLifecycle(
                    viewLifecycleOwner
                ) {
                    val sizeType = if (appSettings.readModeAcceptedBoth.value) {
                        ReadMode.SIZE_TYPE_HORIZONTAL or ReadMode.SIZE_TYPE_VERTICAL
                    } else if (appSettings.horizontalPagerLayout.value) {
                        ReadMode.SIZE_TYPE_VERTICAL
                    } else {
                        ReadMode.SIZE_TYPE_HORIZONTAL
                    }
                    readModeState.value =
                        if (appSettings.readModeEnabled.value) ReadMode(sizeType = sizeType) else null
                }
                appSettings.animateScale.collectWithLifecycle(viewLifecycleOwner) {
                    val durationMillis = if (appSettings.animateScale.value) {
                        (if (appSettings.slowerScaleAnimation.value) 3000 else 300)
                    } else {
                        0
                    }
                    animationSpecState.value =
                        ZoomAnimationSpec.Default.copy(durationMillis = durationMillis)
                }
                appSettings.slowerScaleAnimation.collectWithLifecycle(
                    viewLifecycleOwner
                ) {
                    val durationMillis = if (appSettings.animateScale.value) {
                        (if (appSettings.slowerScaleAnimation.value) 3000 else 300)
                    } else {
                        0
                    }
                    animationSpecState.value =
                        ZoomAnimationSpec.Default.copy(durationMillis = durationMillis)
                }
                appSettings.disabledGestureType
                    .collectWithLifecycle(viewLifecycleOwner) {
                        disabledGestureTypeState.value = it.toInt()
                    }
            }
            subsampling.apply {
                appSettings.showTileBounds.collectWithLifecycle(viewLifecycleOwner) {
                    showTileBoundsState.value = it
                }
                appSettings.tileAnimation.collectWithLifecycle(viewLifecycleOwner) {
                    tileAnimationSpecState.value =
                        if (it) TileAnimationSpec.Default else TileAnimationSpec.None
                }
                appSettings.pausedContinuousTransformType.collectWithLifecycle(
                    viewLifecycleOwner
                ) {
                    pausedContinuousTransformTypeState.value = it.toInt()
                }
                appSettings.disabledBackgroundTiles.collectWithLifecycle(
                    viewLifecycleOwner
                ) {
                    disabledBackgroundTilesState.value = it
                }
            }
        }

        binding.minimapView.setZoomImageView(zoomImageView)

        binding.rotate.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                zoomImageView.zoomable.rotate(zoomImageView.zoomable.transformState.value.rotation.roundToInt() + 90)
            }
        }

        binding.zoom.apply {
            setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val nextStepScale = zoomImageView.zoomable.getNextStepScale()
                    zoomImageView.zoomable.scale(nextStepScale, animated = true)
                }
            }
            zoomImageView.zoomable.transformState
                .repeatCollectWithLifecycle(viewLifecycleOwner, Lifecycle.State.STARTED) {
                    val zoomIn =
                        zoomImageView.zoomable.getNextStepScale() > zoomImageView.zoomable.transformState.value.scaleX
                    if (zoomIn) {
                        setImageResource(CommonR.drawable.ic_zoom_in)
                    } else {
                        setImageResource(CommonR.drawable.ic_zoom_out)
                    }
                }
        }

        binding.info.setOnClickListener {
            ZoomImageViewInfoDialogFragment().apply {
                arguments = ZoomImageViewInfoDialogFragment
                    .buildArgs(zoomImageView, sketchImageUri)
                    .toBundle()
            }.show(childFragmentManager, null)
        }

        binding.linearScaleSlider.apply {
            var changing = false
            listOf(
                zoomImageView.zoomable.minScaleState,
                zoomImageView.zoomable.maxScaleState
            ).merge()
                .repeatCollectWithLifecycle(viewLifecycleOwner, Lifecycle.State.STARTED) {
                    val minScale = zoomImageView.zoomable.minScaleState.value
                    val maxScale = zoomImageView.zoomable.maxScaleState.value
                    val scale = zoomImageView.zoomable.transformState.value.scaleX
                    if (minScale < maxScale) {
                        valueFrom = minScale
                        valueTo = maxScale
                        val step = (valueTo - valueFrom) / 9
                        stepSize = step
                        changing = true
                        value = valueFrom + ((scale - valueFrom) / step).toInt() * step
                        changing = false
                    }
                }
            zoomImageView.zoomable.transformState
                .repeatCollectWithLifecycle(viewLifecycleOwner, Lifecycle.State.STARTED) {
                    val minScale = zoomImageView.zoomable.minScaleState.value
                    val maxScale = zoomImageView.zoomable.maxScaleState.value
                    val scale = it.scaleX
                    if (!changing && scale in minScale..maxScale && minScale < maxScale) {
                        val step = (valueTo - valueFrom) / 9
                        changing = true
                        value = valueFrom + ((scale - valueFrom) / step).toInt() * step
                        changing = false
                    }
                }
            addOnChangeListener { _, value, _ ->
                if (!changing) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        zoomImageView.zoomable.scale(targetScale = value, animated = true)
                    }
                }
            }
        }

        binding.moveKeyboard.moveFlow.collectWithLifecycle(viewLifecycleOwner) {
            val offset = zoomImageView.zoomable.transformState.value.offset
            zoomImageView.zoomable.offset(offset + it * -1f)
        }

        binding.more.apply {
            binding.extraLayout.isVisible = false

            setOnClickListener {
                if (zoomImageView.zoomable.minScaleState.value >= zoomImageView.zoomable.maxScaleState.value) {
                    return@setOnClickListener
                }
                if (binding.extraLayout.isVisible) {
                    binding.extraLayout.animTranslate(
                        fromXDelta = 0f,
                        toXDelta = binding.extraLayout.width.toFloat(),
                        fromYDelta = 0f,
                        toYDelta = 0f,
                        listener = object : Animation.AnimationListener {
                            override fun onAnimationStart(animation: Animation?) {
                            }

                            override fun onAnimationEnd(animation: Animation?) {
                                binding.extraLayout.isVisible = false
                            }

                            override fun onAnimationRepeat(animation: Animation?) {
                            }
                        }
                    )
                } else {
                    binding.extraLayout.isVisible = true
                    binding.extraLayout.animTranslate(
                        fromXDelta = binding.extraLayout.width.toFloat(),
                        toXDelta = 0f,
                        fromYDelta = 0f,
                        toYDelta = 0f,
                    )
                }
            }
        }

        binding.zoomOut.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val targetScale = zoomImageView.zoomable.transformState.value.scaleX - 0.5f
                zoomImageView.zoomable.scale(targetScale = targetScale, animated = true)
            }
        }

        binding.zoomIn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val targetScale = zoomImageView.zoomable.transformState.value.scaleX + 0.5f
                zoomImageView.zoomable.scale(targetScale = targetScale, animated = true)
            }
        }

        zoomImageView.zoomable.transformState
            .repeatCollectWithLifecycle(viewLifecycleOwner, Lifecycle.State.STARTED) {
                updateInfo(zoomImageView, binding)
            }

        loadData()
    }

    protected fun loadData() {
        loadData(binding!!, zoomView!!, sketchImageUri)
    }

    private fun loadData(
        binding: FragmentZoomViewBinding,
        zoomView: ZOOM_VIEW,
        sketchImageUri: String
    ) {
        loadImage(zoomView, binding.stateView)
        loadMinimap(binding.minimapView, sketchImageUri)
    }

    abstract fun loadImage(zoomView: ZOOM_VIEW, stateView: StateView)

    abstract fun loadMinimap(
        minimapView: ZoomImageMinimapView,
        sketchImageUri: String
    )

    @SuppressLint("SetTextI18n")
    private fun updateInfo(
        zoomImageView: ZoomImageView,
        binding: FragmentZoomViewBinding
    ) {
        binding.infoHeaderText.text = """
                scale: 
                offset: 
                rotation: 
            """.trimIndent()
        binding.infoContentText.text =
            zoomImageView.zoomable.transformState.value.run {
                """
                ${scale.toShortString()}
                ${offset.toShortString()}
                ${rotation.roundToInt()}
            """.trimIndent()
            }
    }
}