package com.github.panpf.zoomimage.compose.coil2.core.test

import android.content.Context
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import coil.ImageLoader
import com.github.panpf.zoomimage.coil.CoilModelToImageSource
import com.github.panpf.zoomimage.coil.CoilModelToImageSourceImpl
import com.github.panpf.zoomimage.compose.rememberZoomImageLogger
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.subsampling.ImageSource.Factory
import com.github.panpf.zoomimage.test.TestLifecycle
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class CoilZoomStateTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun testRememberCoilZoomState() {
        rule.setContent {
            TestLifecycle {
                val zoomState1 = rememberCoilZoomState()
                assertEquals(
                    expected = "CoilZoomAsyncImage",
                    actual = zoomState1.logger.tag
                )
                assertEquals(
                    expected = listOf(CoilModelToImageSourceImpl()).joinToString { it::class.qualifiedName!! },
                    actual = zoomState1.modelToImageSources.joinToString { it::class.qualifiedName!! }
                )

                val logger = rememberZoomImageLogger("Test")
                val modelToImageSources = remember {
                    listOf(TestCoilModelToImageSource()).toImmutableList()
                }
                val zoomState2 = rememberCoilZoomState(
                    modelToImageSources = modelToImageSources,
                    logger = logger
                )
                assertEquals(
                    expected = "Test",
                    actual = zoomState2.logger.tag
                )
                assertEquals(
                    expected = listOf(
                        TestCoilModelToImageSource(),
                        CoilModelToImageSourceImpl()
                    ).joinToString { it::class.qualifiedName!! },
                    actual = zoomState2.modelToImageSources.joinToString { it::class.qualifiedName!! }
                )
            }
        }
    }

    class TestCoilModelToImageSource : CoilModelToImageSource {

        override fun dataToImageSource(
            context: Context,
            imageLoader: ImageLoader,
            model: Any
        ): Factory? {
            return null
        }
    }
}