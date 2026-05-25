package com.qiuhu.embyflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.cardEpisodeBadgeLabel
import com.qiuhu.embyflow.ui.SearchState
import com.qiuhu.embyflow.ui.components.EditorialBackground
import com.qiuhu.embyflow.ui.components.EditorialCard
import com.qiuhu.embyflow.ui.components.EditorialChip
import com.qiuhu.embyflow.ui.components.EditorialIconButton
import com.qiuhu.embyflow.ui.components.MediaPosterCornerBadge
import com.qiuhu.embyflow.ui.components.EditorialPill
import com.qiuhu.embyflow.ui.components.EditorialSurface
import com.qiuhu.embyflow.ui.components.EditorialSurfaceStrong
import com.qiuhu.embyflow.ui.components.EditorialTextPrimary
import com.qiuhu.embyflow.ui.components.EditorialTextSecondary
import com.qiuhu.embyflow.ui.components.PixelCatAsyncImage
import com.qiuhu.embyflow.ui.components.SoftUiSurfaceStyle
import com.qiuhu.embyflow.ui.components.SoftUiSurfacePressed

@Composable
fun SearchOverlay(
    state: SearchState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onSelectRecentQuery: (String) -> Unit,
    onCommitSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "搜索",
                            style = MaterialTheme.typography.displaySmall,
                            color = EditorialTextPrimary,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "搜索影片、剧集、演员与媒体库内容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EditorialTextSecondary,
                        )
                    }
                    EditorialIconButton(
                        icon = Icons.Rounded.Close,
                        modifier = Modifier.size(46.dp),
                        onClick = onDismiss,
                    )
                }
            }

            item {
                EditorialCard(
                    shape = RoundedCornerShape(24.dp),
                    surfaceStyle = SoftUiSurfaceStyle.Inset,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = EditorialTextSecondary,
                        )
                        BasicTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = EditorialTextPrimary,
                            ),
                            cursorBrush = SolidColor(EditorialTextPrimary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val query = state.query.trim()
                                    if (query.isNotBlank()) {
                                        onCommitSearch(query)
                                    }
                                    focusManager.clearFocus()
                                },
                            ),
                            decorationBox = { innerTextField ->
                                if (state.query.isEmpty()) {
                                    Text(
                                        text = "搜索片名、演员、分类、工作室",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = EditorialTextSecondary,
                                    )
                                }
                                innerTextField()
                            },
                        )
                    }
                }
            }

            if (state.query.isBlank()) {
                if (state.recentQueries.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "最近搜索",
                                style = MaterialTheme.typography.titleSmall,
                                color = EditorialTextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            EditorialCard(
                                shape = RoundedCornerShape(999.dp),
                                color = EditorialSurfaceStrong,
                                onClick = onClearRecentSearches,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "清空",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EditorialTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    items(
                        items = state.recentQueries,
                        key = { query -> query },
                    ) { query ->
                        SearchRecentQueryCard(
                            query = query,
                            onClick = { onSelectRecentQuery(query) },
                        )
                    }
                }

                item {
                    SearchHintCard()
                }
            } else {
                item {
                    SearchResultHeader(
                        query = state.query,
                        resultCount = state.results.size,
                        isLoading = state.isLoading,
                    )
                }

                when {
                    state.isLoading -> {
                        item {
                            SearchStateCard(
                                title = "正在搜索",
                                detail = "从 Emby 服务器拉取匹配内容…",
                            )
                        }
                    }

                    state.errorMessage != null -> {
                        item {
                            SearchStateCard(
                                title = "搜索失败",
                                detail = state.errorMessage,
                            )
                        }
                    }

                    state.results.isEmpty() -> {
                        item {
                            SearchStateCard(
                                title = "没有找到内容",
                                detail = "试试更短的关键词，或者直接搜演员名与系列名。",
                            )
                        }
                    }

                    else -> {
                        items(
                            items = state.results,
                            key = { media -> media.id },
                        ) { media ->
                            SearchResultCard(
                                media = media,
                                onClick = {
                                    val query = state.query.trim()
                                    if (query.isNotBlank()) {
                                        onCommitSearch(query)
                                    }
                                    onOpenMedia(media)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultHeader(
    query: String,
    resultCount: Int,
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isLoading) "搜索中" else "搜索结果",
            style = MaterialTheme.typography.titleSmall,
            color = EditorialTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        EditorialPill(
            text = if (isLoading) query else "${resultCount} 条结果",
            color = EditorialChip,
        )
    }
}

@Composable
private fun SearchRecentQueryCard(
    query: String,
    onClick: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = EditorialSurfaceStrong,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = EditorialTextSecondary,
            )
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                color = EditorialTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    media: MediaItem,
    onClick: () -> Unit,
) {
    val posterShape = RoundedCornerShape(16.dp)
    val badgeLabel = media.cardEpisodeBadgeLabel()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        EditorialCard(
            modifier = Modifier
                .width(104.dp)
                .height(160.dp),
            shape = posterShape,
            color = EditorialSurfaceStrong,
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SoftUiSurfacePressed),
            ) {
                PixelCatAsyncImage(
                    model = media.primaryImageUrl,
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                if (!badgeLabel.isNullOrBlank()) {
                    MediaPosterCornerBadge(
                        text = badgeLabel,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    )
                }
            }
        }

        EditorialCard(
            modifier = Modifier
                .weight(1f)
                .height(160.dp),
            shape = RoundedCornerShape(22.dp),
            color = EditorialSurface,
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = EditorialTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = media.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EditorialTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = media.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorialTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = media.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorialTextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchHintCard() {
    EditorialCard(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "搜索建议",
                style = MaterialTheme.typography.titleMedium,
                color = EditorialTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "可以直接搜片名、演员名、系列名或工作室名称。输入后会实时从 Emby 返回结果。",
                style = MaterialTheme.typography.bodyMedium,
                color = EditorialTextSecondary,
            )
        }
    }
}

@Composable
private fun SearchStateCard(
    title: String,
    detail: String,
) {
    EditorialCard(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = EditorialTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = EditorialTextSecondary,
            )
        }
    }
}
