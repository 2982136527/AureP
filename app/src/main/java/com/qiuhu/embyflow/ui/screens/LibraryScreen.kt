package com.qiuhu.embyflow.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import com.qiuhu.embyflow.data.settings.LIBRARY_SORT_MODES
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.cardEpisodeBadgeLabel
import com.qiuhu.embyflow.ui.components.EditorialBackground
import com.qiuhu.embyflow.ui.components.EditorialChip
import com.qiuhu.embyflow.ui.components.EditorialCard
import com.qiuhu.embyflow.ui.components.EditorialIconButton
import com.qiuhu.embyflow.ui.components.EditorialAccent
import com.qiuhu.embyflow.ui.components.EditorialShadow
import com.qiuhu.embyflow.ui.components.EditorialSurfaceStrong
import com.qiuhu.embyflow.ui.components.EditorialSurface
import com.qiuhu.embyflow.ui.components.EditorialTextPrimary
import com.qiuhu.embyflow.ui.components.EditorialTextSecondary
import com.qiuhu.embyflow.ui.components.FloatingNavBarHeight
import com.qiuhu.embyflow.ui.components.FloatingNavBarOuterPadding
import com.qiuhu.embyflow.ui.components.FloatingNavBarSheetClearance
import com.qiuhu.embyflow.ui.components.LibraryPosterAspectRatio
import com.qiuhu.embyflow.ui.components.MediaPosterCard
import com.qiuhu.embyflow.ui.components.MediaPosterCardStyle
import com.qiuhu.embyflow.ui.components.PixelCatAsyncImage
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    libraries: List<MediaItem>,
    selectedLibraryId: String?,
    layoutMode: String,
    showLibraryCardTitle: Boolean,
    librarySortMode: String,
    libraryItems: List<MediaItem>,
    libraryTotalCount: Int,
    isRefreshing: Boolean,
    isAppending: Boolean,
    isServerConnected: Boolean,
    hasConfiguredServer: Boolean,
    onSelectLibrary: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSelectLibrarySortMode: (String) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
) {
    val isCompactLayout = layoutMode == "紧凑信息流"
    val isLargeLayout = layoutMode == "大图优先"
    val columnCount = 3
    val gridHorizontalSpacing = 8.dp
    val gridVerticalSpacing = 14.dp
    val gridHorizontalPadding = 16.dp
    val gridBottomPadding = when {
        isCompactLayout -> 112.dp
        else -> 120.dp
    }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val sortArrowRotation by animateFloatAsState(
        targetValue = if (sortMenuExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "librarySortArrowRotation",
    )
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = context.imageLoader
    val gridState = rememberLazyGridState()
    val hasMoreLibraryItems = libraryItems.size < libraryTotalCount
    val loadMoreThreshold = columnCount * 6
    val posterAspectRatio = LibraryPosterAspectRatio
    val gridHorizontalSpacingPx = with(density) { gridHorizontalSpacing.roundToPx() }
    var warmedImageUrls by remember(selectedLibraryId) { mutableStateOf(setOf<String>()) }

    LaunchedEffect(
        gridState,
        libraryItems.size,
        libraryTotalCount,
        isRefreshing,
        isAppending,
        isServerConnected,
    ) {
        if (!isServerConnected) return@LaunchedEffect

        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex to layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalItemCount) ->
                if (
                    hasMoreLibraryItems &&
                    !isRefreshing &&
                    !isAppending &&
                    totalItemCount > 0 &&
                    lastVisibleIndex >= (totalItemCount - loadMoreThreshold).coerceAtLeast(0)
                ) {
                    onLoadMore()
                }
            }
    }

    LaunchedEffect(
        gridState,
        libraryItems,
        selectedLibraryId,
        posterAspectRatio,
        gridHorizontalSpacingPx,
    ) {
        snapshotFlow {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex
        }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (libraryItems.isEmpty()) return@collect
                val gridWidthPx = gridState.layoutInfo.viewportEndOffset - gridState.layoutInfo.viewportStartOffset
                if (gridWidthPx <= 0) return@collect
                val itemWidthPx = ((gridWidthPx - gridHorizontalSpacingPx * (columnCount - 1)) / columnCount.toFloat())
                    .roundToInt()
                    .coerceAtLeast(1)
                val itemHeightPx = (itemWidthPx / posterAspectRatio).roundToInt().coerceAtLeast(1)
                val prefetchStart = (lastVisibleIndex + 1)
                    .coerceAtLeast(0)
                    .coerceAtMost(libraryItems.size)
                val prefetchEnd = (prefetchStart + 12).coerceAtMost(libraryItems.size)
                if (prefetchStart >= prefetchEnd) return@collect
                val upcomingUrls = libraryItems
                    .subList(prefetchStart, prefetchEnd)
                    .mapNotNull { it.primaryImageUrl ?: it.backdropImageUrl }
                    .filterNot { warmedImageUrls.contains(it) }

                if (upcomingUrls.isEmpty()) return@collect

                upcomingUrls.forEach { imageUrl ->
                    imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .size(itemWidthPx, itemHeightPx)
                            .memoryCacheKey(imageUrl)
                            .diskCacheKey(imageUrl)
                            .build(),
                    )
                }
                warmedImageUrls = warmedImageUrls + upcomingUrls
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialBackground),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = gridHorizontalPadding,
                end = gridHorizontalPadding,
                top = 16.dp,
                bottom = gridBottomPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(gridHorizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(gridVerticalSpacing),
        ) {
            if (!isServerConnected) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryConnectionStateCard(
                        title = if (hasConfiguredServer) "当前还没有连接到媒体库" else "还没有添加服务器",
                        description = if (hasConfiguredServer) {
                            "服务器已保存，检查网络、地址或账号密码后，再回来刷新资料库。"
                        } else {
                            "前往设置添加 Emby 服务器后，这里会显示你的分区和封面。"
                        },
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditorialCard(
                            shape = RoundedCornerShape(18.dp),
                            color = EditorialSurface,
                            onClick = { sortMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isRefreshing) "正在整理" else "排序 · $librarySortMode",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EditorialTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = EditorialTextSecondary,
                                    modifier = Modifier.graphicsLayer {
                                        rotationZ = sortArrowRotation
                                    },
                                )
                            }
                        }
                    }
                }

                if (libraries.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val selectorSpacing = 10.dp
                            val selectorCardWidth = (maxWidth - (selectorSpacing * 2)) / 2.5f
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(selectorSpacing),
                            ) {
                                items(libraries) { library ->
                                    LibrarySelectorCard(
                                        library = library,
                                        selected = library.id == selectedLibraryId,
                                        compact = isCompactLayout,
                                        large = isLargeLayout,
                                        cardWidth = selectorCardWidth,
                                        showTitle = showLibraryCardTitle,
                                        onClick = { onSelectLibrary(library.id) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (libraries.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .height(1.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(EditorialChip.copy(alpha = 0.48f)),
                        )
                    }
                }

                if (libraryItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "这个分区还没有可显示的内容",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EditorialTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "切换其他分区，或稍后刷新媒体库。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EditorialTextSecondary,
                                )
                            }
                        }
                    }
                } else {
                    gridItems(
                        items = libraryItems,
                        key = { item -> item.id },
                    ) { item ->
                        MediaPosterCard(
                            media = item,
                            compact = isCompactLayout,
                            style = MediaPosterCardStyle.Library,
                            titleBelow = true,
                            topRightLabel = item.cardEpisodeBadgeLabel(),
                            onClick = { onOpenMedia(item) },
                        )
                    }
                }

                if (isAppending) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(EditorialChip.copy(alpha = 0.72f)),
                            )
                        }
                    }
                }
            }
        }

        if (sortMenuExpanded && isServerConnected) {
            LibrarySortSheet(
                selectedMode = librarySortMode,
                onDismiss = { sortMenuExpanded = false },
                onSelect = { mode ->
                    sortMenuExpanded = false
                    onSelectLibrarySortMode(mode)
                },
            )
        }
    }
}

@Composable
private fun LibraryConnectionStateCard(
    title: String,
    description: String,
) {
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = EditorialTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = EditorialTextSecondary,
            )
        }
    }
}

@Composable
private fun LibrarySortSheet(
    selectedMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val navigationBarBottomInset = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val sortSheetBottomPadding =
        navigationBarBottomInset +
            FloatingNavBarHeight +
            (FloatingNavBarOuterPadding * 2) +
            FloatingNavBarSheetClearance
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialTextPrimary.copy(alpha = 0.16f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        EditorialCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = sortSheetBottomPadding),
            shape = RoundedCornerShape(30.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "内容排序",
                            style = MaterialTheme.typography.headlineSmall,
                            color = EditorialTextPrimary,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    EditorialIconButton(
                        icon = Icons.Rounded.Close,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        onClick = onDismiss,
                    )
                }

                LIBRARY_SORT_MODES.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (mode == selectedMode) EditorialSurfaceStrong else EditorialChip.copy(alpha = 0.45f))
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.bodyLarge,
                                color = EditorialTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = librarySortDescription(mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = EditorialTextSecondary,
                            )
                        }
                        if (mode == selectedMode) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(EditorialAccent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = EditorialAccent,
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(EditorialChip.copy(alpha = 0.8f)),
                )

                Text(
                    text = "排序会重新请求当前分区内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorialTextSecondary,
                )
            }
        }
    }
}

private fun librarySortDescription(mode: String): String = when (mode) {
    "最近更新" -> "优先展示刚入库或最近变动的内容"
    "名称 A-Z" -> "按片名顺序展开，查找会更直接"
    "评分最高" -> "把评分更高的内容提到前面"
    else -> ""
}

@Composable
private fun LibrarySelectorCard(
    library: MediaItem,
    selected: Boolean,
    compact: Boolean,
    large: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp,
    showTitle: Boolean,
    onClick: () -> Unit,
) {
    val imageUrl = library.backdropImageUrl ?: library.primaryImageUrl
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val highlightColor = Color(0xFFD39B5D)
    val cardAspectRatio = 16f / 9f

    Column(
        modifier = Modifier
            .width(cardWidth)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp)
                    .aspectRatio(cardAspectRatio)
                    .shadow(
                        elevation = if (selected) 18.dp else 12.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = if (selected) highlightColor.copy(alpha = 0.18f) else EditorialShadow,
                        spotColor = if (selected) highlightColor.copy(alpha = 0.18f) else EditorialShadow,
                    )
                .clip(shape)
                .background(if (selected) Color(0xFFF2E4D0) else EditorialSurface)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = highlightColor,
                            shape = shape,
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(library.colors)),
                contentAlignment = Alignment.Center,
            ) {
                PixelCatAsyncImage(
                    model = imageUrl,
                    contentDescription = library.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        if (showTitle) {
            Text(
                text = library.title,
                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                color = if (selected) highlightColor else EditorialTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
