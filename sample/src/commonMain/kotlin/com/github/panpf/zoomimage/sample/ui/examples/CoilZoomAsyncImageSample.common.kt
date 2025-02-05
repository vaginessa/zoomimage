package com.github.panpf.zoomimage.sample.ui.examples

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest.Builder
import coil3.request.crossfade
import coil3.size.Precision
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.coil.CoilModelToImageSource
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.sample.image.CoilComposeResourceToImageSource
import com.github.panpf.zoomimage.sample.image.PhotoPalette
import com.github.panpf.zoomimage.sample.ui.components.MyPageState
import com.github.panpf.zoomimage.sample.ui.components.PageState
import com.github.panpf.zoomimage.sample.ui.model.Photo
import com.github.panpf.zoomimage.sample.util.sketchUri2CoilModel
import kotlinx.collections.immutable.toImmutableList

expect fun platformCoilModelToImageSource(): List<CoilModelToImageSource>?

@Composable
fun CoilZoomAsyncImageSample(
    photo: Photo,
    photoPaletteState: MutableState<PhotoPalette>,
    pageSelected: Boolean,
) {
    BaseZoomImageSample(
        photo = photo,
        photoPaletteState = photoPaletteState,
        createZoomState = {
            val extensionsModelToImageSources = remember {
                platformCoilModelToImageSource().orEmpty()
                    .plus(CoilComposeResourceToImageSource())
                    .toImmutableList()
            }
            rememberCoilZoomState(modelToImageSources = extensionsModelToImageSources)
        },
        pageSelected = pageSelected,
    ) { contentScale, alignment, zoomState, scrollBar, onLongClick ->
        var myLoadState by remember { mutableStateOf<MyPageState>(MyPageState.None) }
        val context = LocalPlatformContext.current
        val request = remember(key1 = photo) {
            val model = sketchUri2CoilModel(context, photo.originalUrl)
            Builder(context).apply {
                data(model)
                crossfade(true)
                precision(Precision.EXACT)
                listener(
                    onStart = {
                        myLoadState = MyPageState.Loading
                    },
                    onError = { _, _ ->
                        myLoadState = MyPageState.Error()
                    },
                    onSuccess = { _, _ ->
                        myLoadState = MyPageState.None
                    }
                )
            }.build()
        }
        CoilZoomAsyncImage(
            model = request,
            contentDescription = "view image",
            contentScale = contentScale,
            alignment = alignment,
            modifier = Modifier.fillMaxSize(),
            zoomState = zoomState,
            scrollBar = scrollBar,
            onLongPress = {
                onLongClick.invoke()
            }
        )

        PageState(state = myLoadState)
    }
}