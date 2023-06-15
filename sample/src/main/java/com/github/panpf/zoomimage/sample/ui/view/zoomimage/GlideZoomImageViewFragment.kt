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
package com.github.panpf.zoomimage.sample.ui.view.zoomimage

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.panpf.assemblyadapter.pager.FragmentItemFactory
import com.github.panpf.zoomimage.ZoomImageView
import com.github.panpf.zoomimage.sample.databinding.ZoomImageViewCommonFragmentBinding
import com.github.panpf.zoomimage.sample.databinding.GlideZoomImageViewFragmentBinding
import com.github.panpf.zoomimage.sample.prefsService
import com.github.panpf.zoomimage.sample.util.collectWithLifecycle
import kotlinx.coroutines.flow.merge

class GlideZoomImageViewFragment : BaseZoomImageViewFragment<GlideZoomImageViewFragmentBinding>() {

    private val args by navArgs<CoilZoomImageViewFragmentArgs>()

    override val sketchImageUri: String
        get() = args.imageUri

    override val supportDisabledMemoryCache: Boolean
        get() = true

    override val supportIgnoreExifOrientation: Boolean
        get() = false

    override val supportDisallowReuseBitmap: Boolean
        get() = true

    override fun getCommonBinding(binding: GlideZoomImageViewFragmentBinding): ZoomImageViewCommonFragmentBinding {
        return binding.common
    }

    override fun getZoomImageView(binding: GlideZoomImageViewFragmentBinding): ZoomImageView {
        return binding.glideZoomImageViewImage
    }

    override fun onViewCreated(
        binding: GlideZoomImageViewFragmentBinding,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(binding, savedInstanceState)

        binding.glideZoomImageViewImage.apply {
            listOf(
//                prefsService.disableMemoryCache.stateFlow,
                prefsService.disallowReuseBitmap.stateFlow,
//                prefsService.ignoreExifOrientation.stateFlow,
            ).merge().collectWithLifecycle(viewLifecycleOwner) {
//                subsamplingAbility.disableMemoryCache = prefsService.disableMemoryCache.value
                subsamplingAbility.disallowReuseBitmap = prefsService.disallowReuseBitmap.value
//                subsamplingAbility.ignoreExifOrientation = prefsService.ignoreExifOrientation.value
            }
            listOf(
                prefsService.disableMemoryCache.sharedFlow,
                prefsService.disallowReuseBitmap.sharedFlow,
//                prefsService.ignoreExifOrientation.sharedFlow,
            ).merge().collectWithLifecycle(viewLifecycleOwner) {
                loadData(binding, binding.common, sketchImageUri)
            }
        }
    }

    override fun loadImage(
        binding: GlideZoomImageViewFragmentBinding,
        onCallStart: () -> Unit,
        onCallSuccess: () -> Unit,
        onCallError: () -> Unit
    ) {
        onCallStart()
        Glide.with(this@GlideZoomImageViewFragment)
            .load(sketchUri2GlideModel(binding, args.imageUri))
            .skipMemoryCache(prefsService.disableMemoryCache.value)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    onCallError()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    onCallSuccess()
                    return false
                }
            })
            .into(binding.glideZoomImageViewImage)
    }

    private fun sketchUri2GlideModel(
        binding: GlideZoomImageViewFragmentBinding,
        sketchImageUri: String
    ): Any? {
        return when {
            sketchImageUri.startsWith("asset://") ->
                sketchImageUri.replace("asset://", "file:///android_asset/")

            sketchImageUri.startsWith("android.resource://") -> {
                val resId =
                    sketchImageUri.toUri().getQueryParameters("resId").firstOrNull()?.toIntOrNull()
                if (resId == null) {
                    binding.glideZoomImageViewImage.zoomAbility.logger.w("ZoomImageViewFragment") {
                        "Can't use Subsampling, invalid resource uri: '$sketchImageUri'"
                    }
                }
                resId
            }

            else -> sketchImageUri
        }
    }

    class ItemFactory : FragmentItemFactory<String>(String::class) {

        override fun createFragment(
            bindingAdapterPosition: Int,
            absoluteAdapterPosition: Int,
            data: String
        ): Fragment = GlideZoomImageViewFragment().apply {
            arguments = GlideZoomImageViewFragmentArgs(data).toBundle()
        }
    }
}