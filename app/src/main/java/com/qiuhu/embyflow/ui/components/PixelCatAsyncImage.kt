package com.qiuhu.embyflow.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

@Composable
fun PixelCatAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
) {
    val painter = rememberAsyncImagePainter(
        model = model,
        contentScale = contentScale,
        onSuccess = { state ->
            onSuccess?.invoke(state)
        },
    )

    Box(modifier = modifier) {
        if (painter.state !is AsyncImagePainter.State.Success) {
            PixelCatPlaceholder(
                modifier = Modifier.fillMaxSize(),
            )
        }

        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            alignment = alignment,
        )
    }
}
