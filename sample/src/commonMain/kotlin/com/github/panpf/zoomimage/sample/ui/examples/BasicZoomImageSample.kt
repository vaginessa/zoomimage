package com.github.panpf.zoomimage.sample.ui.examples

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.github.panpf.sketch.LocalPlatformContext
import com.github.panpf.sketch.SingletonSketch
import com.github.panpf.sketch.painter.asPainter
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.ImageResult
import com.github.panpf.sketch.request.execute
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.sample.image.PhotoPalette
import com.github.panpf.zoomimage.sample.ui.components.MyPageState
import com.github.panpf.zoomimage.sample.ui.components.PageState
import com.github.panpf.zoomimage.sample.ui.test.sketchImageUriToZoomImageImageSource
import com.github.panpf.zoomimage.sketch.SketchTileBitmapCache

@Composable
fun BasicZoomImageSample(sketchImageUri: String, photoPaletteState: MutableState<PhotoPalette>) {
    BaseZoomImageSample(
        sketchImageUri = sketchImageUri,
        photoPaletteState = photoPaletteState
    ) { contentScale, alignment, state, scrollBar, onLongClick ->
        val context = LocalPlatformContext.current
        val sketch = SingletonSketch.get(context)
        LaunchedEffect(Unit) {
            state.subsampling.tileBitmapCache = SketchTileBitmapCache(sketch)
        }

        var myLoadState by remember { mutableStateOf<MyPageState>(MyPageState.None) }
        var imagePainter: Painter? by remember { mutableStateOf(null) }
        LaunchedEffect(sketchImageUri) {
            myLoadState = MyPageState.Loading
            val imageResult = ImageRequest(context, sketchImageUri).execute()
            myLoadState = if (imageResult is ImageResult.Success) {
                MyPageState.None
            } else {
                MyPageState.Error()
            }
            imagePainter = imageResult.image?.asPainter()

            val imageSource = sketchImageUriToZoomImageImageSource(
                sketch = sketch,
                imageUri = sketchImageUri,
                http2ByteArray = false
            )
            state.subsampling.setImageSource(imageSource)
        }

        val imagePainter1 = imagePainter
        if (imagePainter1 != null) {
            ZoomImage(
                painter = imagePainter1,
                contentDescription = "view image",
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
                state = state,
                scrollBar = scrollBar,
                onLongPress = {
                    onLongClick.invoke()
                }
            )
        }

        PageState(state = myLoadState)
    }
}