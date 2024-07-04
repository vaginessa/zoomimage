package com.github.panpf.zoomimage.sample.ui.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.compose.subsampling.SubsamplingState
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.sample.resources.Res
import com.github.panpf.zoomimage.sample.resources.ic_info
import com.github.panpf.zoomimage.sample.resources.ic_more_vert
import com.github.panpf.zoomimage.sample.resources.ic_rotate_right
import com.github.panpf.zoomimage.sample.resources.ic_zoom_in
import com.github.panpf.zoomimage.sample.resources.ic_zoom_out
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
fun ZoomImageTool(
    zoomableState: ZoomableState,
    subsamplingState: SubsamplingState,
    infoDialogState: MyDialogState,
    imageUri: String,
) {
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(NavigationBarDefaults.windowInsets)) {
        ZoomImageMinimap(
            imageUri = imageUri,
            zoomableState = zoomableState,
            subsamplingState = subsamplingState,
        )

        Column(
            modifier = Modifier
                .padding(20.dp)
                .align(Alignment.BottomEnd)
                .wrapContentHeight()
                .width(164.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val inspectionMode = LocalInspectionMode.current
            var moreShow by remember { mutableStateOf(inspectionMode) }
            AnimatedVisibility(
                visible = moreShow,
                enter = slideInHorizontally(initialOffsetX = { it * 2 }),
                exit = slideOutHorizontally(targetOffsetX = { it * 2 }),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val moveKeyboardState = rememberMoveKeyboardState()
                    LaunchedEffect(Unit) {
                        snapshotFlow { zoomableState.containerSize }.collect { size ->
                            moveKeyboardState.maxStep = Offset(size.width / 20f, size.height / 20f)
                        }
                    }
                    LaunchedEffect(Unit) {
                        moveKeyboardState.moveFlow.collect {
                            zoomableState.offset(zoomableState.transform.offset + it * -1f)
                        }
                    }
                    MoveKeyboard(
                        state = moveKeyboardState,
                        modifier = Modifier.size(100.dp)
                    )

                    Spacer(modifier = Modifier.size(6.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilledIconButton(
                            onClick = {
                                coroutineScope.launch {
                                    zoomableState.scale(
                                        targetScale = zoomableState.transform.scaleX - 0.5f,
                                        animated = true
                                    )
                                }
                            },
                            modifier = Modifier.size(30.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colorScheme.tertiary,
                                contentColor = colorScheme.onTertiary,
                            )
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_zoom_out),
                                contentDescription = "zoom out",
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                        )

                        FilledIconButton(
                            onClick = {
                                coroutineScope.launch {
                                    zoomableState.scale(
                                        targetScale = zoomableState.transform.scaleX + 0.5f,
                                        animated = true
                                    )
                                }
                            },
                            modifier = Modifier.size(30.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colorScheme.tertiary,
                                contentColor = colorScheme.onTertiary,
                            )
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_zoom_in),
                                contentDescription = "zoom in",
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(6.dp))

                    Slider(
                        value = zoomableState.transform.scaleX,
                        valueRange = zoomableState.minScale..zoomableState.maxScale,
                        onValueChange = {
                            coroutineScope.launch {
                                zoomableState.scale(targetScale = it, animated = true)
                            }
                        },
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = colorScheme.onTertiary,
                            activeTrackColor = colorScheme.tertiary,
                        ),
                    )

                    Spacer(modifier = Modifier.size(6.dp))
                }
            }

            ButtonPad(infoDialogState, zoomableState) {
                moreShow = !moreShow
            }
        }
    }
}

@Composable
private fun ButtonPad(
    infoDialogState: MyDialogState,
    zoomableState: ZoomableState,
    onClickMore: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()
    Row(Modifier.background(colorScheme.tertiary, RoundedCornerShape(50))) {
        IconButton(
            onClick = {
                coroutineScope.launch {
                    zoomableState.rotate((zoomableState.transform.rotation + 90).roundToInt())
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_rotate_right),
                contentDescription = "Rotate",
                tint = colorScheme.onTertiary
            )
        }

        IconButton(
            onClick = {
                coroutineScope.launch {
                    zoomableState.switchScale(animated = true)
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            val zoomIn by remember {
                derivedStateOf {
                    zoomableState.getNextStepScale() > zoomableState.transform.scaleX
                }
            }
            val description = if (zoomIn) {
                "zoom in"
            } else {
                "zoom out"
            }
            val icon = if (zoomIn) {
                painterResource(Res.drawable.ic_zoom_in)
            } else {
                painterResource(Res.drawable.ic_zoom_out)
            }
            Icon(
                painter = icon,
                contentDescription = description,
                tint = colorScheme.onTertiary
            )
        }

        IconButton(
            onClick = { infoDialogState.showing = !infoDialogState.showing },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_info),
                contentDescription = "Info",
                tint = colorScheme.onTertiary
            )
        }

        IconButton(
            onClick = { onClickMore() },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_more_vert),
                contentDescription = "More",
                tint = colorScheme.onTertiary
            )
        }
    }
}