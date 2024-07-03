package com.github.panpf.zoomimage.sample.ui.gallery

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.github.panpf.zoomimage.sample.data.builtinImages
import com.github.panpf.zoomimage.sample.ui.gridCellsMinSize
import com.github.panpf.zoomimage.sample.ui.model.Photo

@Composable
actual fun Screen.LocalPhotoPage() {
    val navigator = LocalNavigator.current!!
    PhotoList(
        gridCellsMinSize = gridCellsMinSize,
        initialPageStart = 0,
        pageSize = 80,
        load = { pageStart: Int, _: Int ->
            if (pageStart == 0) {
                builtinImages().map {
                    Photo(
                        originalUrl = it.uri,
                        mediumUrl = it.uri,
                        thumbnailUrl = it.uri,
                        width = it.size.width,
                        height = it.size.height,
                    )
                }
            } else {
                emptyList()
            }
        },
        calculateNextPageStart = { currentPageStart: Int, loadedPhotoSize: Int ->
            currentPageStart + loadedPhotoSize
        },
        onClick = { photos1, _, index ->
            val params = buildPhotoPagerParams(photos1, index)
            navigator.push(PhotoPagerScreen(params))
        }
    )
}