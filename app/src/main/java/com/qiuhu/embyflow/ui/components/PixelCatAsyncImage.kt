package com.qiuhu.embyflow.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

@Composable
fun PixelCatAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        loading = {
            PixelCatPlaceholder(
                modifier = Modifier.fillMaxSize(),
            )
        },
        error = {
            PixelCatPlaceholder(
                modifier = Modifier.fillMaxSize(),
            )
        },
        success = { state ->
            onSuccess?.invoke(state)
            SubcomposeAsyncImageContent()
        },
    )
}
