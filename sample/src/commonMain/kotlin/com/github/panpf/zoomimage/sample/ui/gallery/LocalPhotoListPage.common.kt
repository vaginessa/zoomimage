package com.github.panpf.zoomimage.sample.ui.gallery

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

expect fun localPhotoListPermission(): Any?

@Composable
expect fun LocalPhotoListPage(screen: Screen)