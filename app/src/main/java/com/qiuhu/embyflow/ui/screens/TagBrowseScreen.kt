package com.qiuhu.embyflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.MediaTag
import com.qiuhu.embyflow.model.cardEpisodeBadgeLabel
import com.qiuhu.embyflow.ui.components.EditorialBackground
import com.qiuhu.embyflow.ui.components.EditorialCard
import com.qiuhu.embyflow.ui.components.EditorialIconButton
import com.qiuhu.embyflow.ui.components.EditorialTextPrimary
import com.qiuhu.embyflow.ui.components.EditorialTextSecondary
import com.qiuhu.embyflow.ui.components.MediaPosterCard
import com.qiuhu.embyflow.ui.components.MediaPosterCardStyle

@Composable
fun TagBrowseScreen(
    tag: MediaTag,
    items: List<MediaItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
) {
    MediaBrowseScreen(
        title = tag.label,
        subtitle = when (tag.type) {
            com.qiuhu.embyflow.model.MediaTagType.Genre -> "标签分类"
            com.qiuhu.embyflow.model.MediaTagType.Year -> "年份分类"
        },
        items = items,
        isLoading = isLoading,
        errorMessage = errorMessage,
        emptyMessage = "这个标签下还没有内容",
        loadingMessage = "正在加载这个标签下的内容",
        onBack = onBack,
        onOpenMedia = onOpenMedia,
        columns = 2,
        cardCompact = true,
        titleBelow = false,
    )
}

@Composable
fun MediaBrowseScreen(
    title: String,
    subtitle: String? = null,
    items: List<MediaItem>,
    isLoading: Boolean,
    errorMessage: String?,
    emptyMessage: String,
    loadingMessage: String,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    columns: Int,
    cardCompact: Boolean,
    titleBelow: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorialIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    modifier = Modifier.size(44.dp),
                    onClick = onBack,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Black,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EditorialTextSecondary,
                        )
                    }
                }
            }
        }

        when {
            isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                    ) {
                        Text(
                            text = loadingMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EditorialTextSecondary,
                        )
                    }
                }
            }

            errorMessage != null -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EditorialTextSecondary,
                        )
                    }
                }
            }

            items.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                    ) {
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EditorialTextSecondary,
                        )
                    }
                }
            }

            else -> {
                gridItems(items) { item ->
                    MediaPosterCard(
                        media = item,
                        compact = cardCompact,
                        style = MediaPosterCardStyle.Library,
                        topRightLabel = item.cardEpisodeBadgeLabel(),
                        titleBelow = titleBelow,
                        onClick = { onOpenMedia(item) },
                    )
                }
            }
        }
    }
}
