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

package com.github.panpf.zoomimage.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.fromAsset
import com.github.panpf.zoomimage.subsampling.fromByteArray
import com.github.panpf.zoomimage.subsampling.fromContent
import com.github.panpf.zoomimage.subsampling.fromFile
import com.github.panpf.zoomimage.subsampling.fromResource
import com.github.panpf.zoomimage.subsampling.toFactory
import okio.Path.Companion.toPath
import java.io.File
import java.net.URL

/**
 * Convert the glide model to [ImageSource.Factory]
 */
interface GlideModelToImageSource {
    suspend fun modelToImageSource(context: Context, glide: Glide, model: Any): ImageSource.Factory?
}

/**
 * Default implementation of [GlideModelToImageSource]
 *
 * @see com.github.panpf.zoomimage.core.glide.test.GlideModelToImageSourceImplTest
 */
class GlideModelToImageSourceImpl : GlideModelToImageSource {

    override suspend fun modelToImageSource(context: Context, glide: Glide, model: Any): ImageSource.Factory? {
        val uri = when (model) {
            is String -> android.net.Uri.parse(model)
            is android.net.Uri -> model
            else -> null
        }
        return when {
            model is GlideUrl -> {
                GlideHttpImageSource.Factory(glide, model)
            }

            model is URL -> {
                GlideHttpImageSource.Factory(glide, GlideUrl(model))
            }

            uri != null && (uri.scheme == "http" || uri.scheme == "https") -> {
                GlideHttpImageSource.Factory(glide, model.toString())
            }

            uri != null && uri.scheme == "content" -> {
                ImageSource.fromContent(context, uri).toFactory()
            }

            // file:///android_asset/image.jpg
            uri != null && uri.scheme == "file" && uri.pathSegments.firstOrNull() == "android_asset" -> {
                val assetFileName = uri.pathSegments.drop(1).joinToString("/")
                ImageSource.fromAsset(context, assetFileName).toFactory()
            }

            // /sdcard/xxx.jpg
            uri != null && uri.scheme?.takeIf { it.isNotEmpty() } == null
                    && uri.authority?.takeIf { it.isNotEmpty() } == null
                    && uri.path?.startsWith("/") == true -> {
                ImageSource.fromFile(uri.path!!.toPath()).toFactory()
            }

            // file:///sdcard/xxx.jpg
            uri != null && uri.scheme == "file"
                    && uri.authority?.takeIf { it.isNotEmpty() } == null
                    && uri.path?.startsWith("/") == true -> {
                ImageSource.fromFile(uri.path!!.toPath()).toFactory()
            }

            model is File -> {
                ImageSource.fromFile(model).toFactory()
            }

            model is Int -> {
                ImageSource.fromResource(context, model).toFactory()
            }

            // android.resource://example.package.name/drawable/image
            uri != null && uri.scheme == "android.resource" && uri.pathSegments.size == 2 -> {
                val packageName = uri.authority?.takeIf { it.isNotEmpty() } ?: context.packageName
                val resources = context.packageManager.getResourcesForApplication(packageName)
                val (type, name) = uri.pathSegments
                //noinspection DiscouragedApi: Necessary to support resource URIs.
                val id = resources.getIdentifier(name, type, packageName)
                ImageSource.fromResource(resources, id).toFactory()
            }

            // android.resource://example.package.name/4125123
            uri != null && uri.scheme == "android.resource" && uri.pathSegments.size == 1 -> {
                val packageName = uri.authority?.takeIf { it.isNotEmpty() } ?: context.packageName
                val resources = context.packageManager.getResourcesForApplication(packageName)
                val id = uri.pathSegments.first().toInt()
                ImageSource.fromResource(resources, id).toFactory()
            }

            model is ByteArray -> {
                ImageSource.fromByteArray(model).toFactory()
            }

            else -> {
                null
            }
        }
    }
}