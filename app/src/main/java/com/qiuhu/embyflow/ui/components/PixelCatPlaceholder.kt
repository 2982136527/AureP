package com.qiuhu.embyflow.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.qiuhu.embyflow.R

@Composable
fun PixelCatPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(
                        EditorialChip.copy(alpha = 0.78f),
                        EditorialSurfaceStrong,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.46f)
                .padding(10.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
