package com.github.panpf.zoomimage.sample.image

import com.github.panpf.sketch.Image
import com.github.panpf.sketch.PainterImage
import com.github.panpf.sketch.painter.ResizePainter
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.util.toSketchSize
import com.github.panpf.zoomimage.sample.ui.util.toIntSize

actual val Image.realSize: Size
    get() = if (this is PainterImage) {
        val painter = painter
        if (painter is ResizePainter) {
            painter.painter.intrinsicSize.toIntSize().toSketchSize()
        } else {
            Size(width, height)
        }
    } else {
        Size(width, height)
    }