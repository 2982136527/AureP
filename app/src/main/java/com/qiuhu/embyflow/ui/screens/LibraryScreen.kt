package com.qiuhu.embyflow.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.qiuhu.embyflow.data.settings.LibraryFilterSpec
import com.qiuhu.embyflow.data.settings.isActive
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
import com.qiuhu.embyflow.ui.components.ContextMenuAction
import com.qiuhu.embyflow.ui.components.MediaPosterCard
import com.qiuhu.embyflow.ui.components.MediaPosterCardStyle
import com.qiuhu.embyflow.ui.components.PosterContextMenu
import com.qiuhu.embyflow.ui.components.PixelCatAsyncImage
import com.qiuhu.embyflow.ui.components.SoftUiAccent
import com.qiuhu.embyflow.ui.components.SoftUiScrim
import com.qiuhu.embyflow.ui.components.SoftUiSurfacePressed
import com.qiuhu.embyflow.ui.components.SoftUiSurfaceStyle
import com.qiuhu.embyflow.ui.components.hapticPressScale
import com.qiuhu.embyflow.ui.components.softUiRaisedSurface
import com.qiuhu.embyflow.ui.components.softUiSurface
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    isTabActive: Boolean,
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
    restoredScrollIndex: Int,
    restoredScrollOffset: Int,
    onSelectLibrary: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSelectLibrarySortMode: (String) -> Unit,
    libraryFilter: LibraryFilterSpec = LibraryFilterSpec(),
    availableGenres: List<String> = emptyList(),
    onUpdateLibraryFilter: (LibraryFilterSpec) -> Unit = {},
    libraryColumnCount: Int = 3,
    onUpdateLibraryColumnCount: (Int) -> Unit = {},
    onOpenMedia: (MediaItem) -> Unit,
    onTogglePlayed: (MediaItem) -> Unit = {},
    scrollToTopSignal: Int = 0,
    onGridScrollChanged: (index: Int, offset: Int) -> Unit,
) {
    val isCompactLayout = layoutMode == "紧凑信息流"
    val isLargeLayout = layoutMode == "大图优先"
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) listState.animateScrollToItem(0)
    }
    val columnCount = libraryColumnCount
    val gridHorizontalSpacing = 8.dp
    val gridVerticalSpacing = 14.dp
    val gridHorizontalPadding = 16.dp
    val gridBottomPadding = when {
        isCompactLayout -> 112.dp
        else -> 120.dp
    }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var filterSheetExpanded by remember { mutableStateOf(false) }
    var contextMenuMedia by remember { mutableStateOf<MediaItem?>(null) }
    val sortArrowRotation by animateFloatAsState(
        targetValue = if (sortMenuExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "librarySortArrowRotation",
    )
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = context.imageLoader
    val hasMoreLibraryItems = libraryItems.size < libraryTotalCount
    val loadMoreThreshold = 6 // rows from the end to trigger load-more
    val posterAspectRatio = LibraryPosterAspectRatio
    val gridHorizontalSpacingPx = with(density) { gridHorizontalSpacing.roundToPx() }
    // Not a State — URL tracking is purely internal; writing to it must not trigger recomposition.
    val warmedImageUrls = remember(selectedLibraryId) { mutableSetOf<String>() }
    // Total items in the LazyColumn (for scroll restoration bounds check)
    val totalListItems = when {
        !isServerConnected -> 1
        else -> {
            var count = 1 // sort/filter row
            if (libraries.isNotEmpty()) count += 1 // selector row
            if (libraryFilter.isActive) count += 1 // filter chips
            count += if (libraryItems.isEmpty()) 1 else libraryItems.chunked(columnCount).size
            if (isAppending) count += 1
            count
        }
    }

    // Restore scroll position only when library changes or tab activates.
    // restoredScrollIndex/Offset are NOT keys — including them would restart the
    // effect on every scroll, killing fling momentum.
    LaunchedEffect(
        isTabActive,
        selectedLibraryId,
        totalListItems,
    ) {
        if (!isTabActive) return@LaunchedEffect
        if (selectedLibraryId.isNullOrBlank()) return@LaunchedEffect
        if (totalListItems <= 0) return@LaunchedEffect
        if (listState.isScrollInProgress) return@LaunchedEffect
        val targetIndex = restoredScrollIndex.coerceIn(0, totalListItems - 1)
        listState.scrollToItem(targetIndex, restoredScrollOffset.coerceAtLeast(0))
    }

    // Save scroll position when scrolling stops — not on every frame during fling.
    LaunchedEffect(
        isTabActive,
        listState,
        selectedLibraryId,
    ) {
        if (!isTabActive) return@LaunchedEffect
        if (selectedLibraryId.isNullOrBlank()) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    val index = listState.firstVisibleItemIndex
                    val offset = listState.firstVisibleItemScrollOffset
                    onGridScrollChanged(index, offset)
                }
            }
    }

    DisposableEffect(isTabActive, selectedLibraryId) {
        onDispose {
            if (!selectedLibraryId.isNullOrBlank()) {
                onGridScrollChanged(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                )
            }
        }
    }

    LaunchedEffect(
        isTabActive,
        listState,
        libraryItems.size,
        libraryTotalCount,
        isRefreshing,
        isAppending,
        isServerConnected,
    ) {
        if (!isTabActive) return@LaunchedEffect
        if (!isServerConnected) return@LaunchedEffect

        snapshotFlow {
            val layoutInfo = listState.layoutInfo
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
        isTabActive,
        listState,
        libraryItems,
        selectedLibraryId,
        posterAspectRatio,
        gridHorizontalSpacingPx,
    ) {
        if (!isTabActive) return@LaunchedEffect
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex
        }
            .distinctUntilChanged()
            .collect { lastVisibleRowIndex ->
                if (libraryItems.isEmpty()) return@collect
                val viewportWidthPx = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                if (viewportWidthPx <= 0) return@collect
                val itemWidthPx = ((viewportWidthPx - gridHorizontalSpacingPx * (columnCount - 1)) / columnCount.toFloat())
                    .roundToInt()
                    .coerceAtLeast(1)
                val itemHeightPx = (itemWidthPx / posterAspectRatio).roundToInt().coerceAtLeast(1)
                // lastVisibleRowIndex is a row index; convert to item index
                val lastVisibleItemIndex = ((lastVisibleRowIndex + 1) * columnCount - 1)
                    .coerceAtMost(libraryItems.size - 1)
                val prefetchStart = (lastVisibleItemIndex + 1)
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
                warmedImageUrls.addAll(upcomingUrls)
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialBackground),
    ) {
        var pinchAccumulator by remember { mutableStateOf(0f) }
        val transformableState = rememberTransformableState { _, _, zoomChange ->
            pinchAccumulator += zoomChange - 1f
            if (pinchAccumulator > 0.3f) {
                onUpdateLibraryColumnCount((columnCount - 1).coerceAtLeast(2))
                pinchAccumulator = 0f
            } else if (pinchAccumulator < -0.3f) {
                onUpdateLibraryColumnCount((columnCount + 1).coerceAtMost(5))
                pinchAccumulator = 0f
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .transformable(state = transformableState),
            contentPadding = PaddingValues(
                start = gridHorizontalPadding,
                end = gridHorizontalPadding,
                top = 12.dp,
                bottom = gridBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(gridVerticalSpacing),
        ) {
            if (!isServerConnected) {
                item(key = "library-connection-state") {
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
                if (libraries.isNotEmpty()) {
                    item(key = "library-selector-row") {
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
                                items(
                                    items = libraries,
                                    key = { library -> library.id },
                                    contentType = { "selector-card" },
                                ) { library ->
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

                item(key = "library-sort-filter-row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val filterCount = libraryFilter.toActiveChipLabels().size
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { filterSheetExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "筛选",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (libraryFilter.isActive) EditorialAccent else EditorialTextSecondary,
                                fontWeight = FontWeight.Medium,
                            )
                            if (filterCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(EditorialAccent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = filterCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(14.dp)
                                .background(EditorialTextSecondary.copy(alpha = 0.25f)),
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { sortMenuExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isRefreshing) "正在整理" else librarySortMode,
                                style = MaterialTheme.typography.labelLarge,
                                color = EditorialTextSecondary,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = EditorialTextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = sortArrowRotation },
                            )
                        }
                    }
                }

                if (libraryFilter.isActive) {
                    item(key = "library-active-filters") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            items(
                                items = libraryFilter.toActiveChipLabels(),
                                key = { it },
                            ) { label ->
                                EditorialCard(
                                    shape = RoundedCornerShape(999.dp),
                                    color = EditorialAccent.copy(alpha = 0.14f),
                                    onClick = { onUpdateLibraryFilter(libraryFilter.removeByLabel(label)) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EditorialAccent,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                if (libraryItems.isEmpty()) {
                    item(key = "library-empty-state") {
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
                    val rows = libraryItems.chunked(columnCount)
                    items(
                        items = rows,
                        key = { row -> row.first().id },
                    ) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gridHorizontalSpacing),
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    MediaPosterCard(
                                        media = item,
                                        compact = isCompactLayout,
                                        style = MediaPosterCardStyle.Library,
                                        titleBelow = true,
                                        topRightLabel = item.cardEpisodeBadgeLabel(),
                                        onClick = { onOpenMedia(item) },
                                        onLongClick = { contextMenuMedia = item },
                                    )
                                    PosterContextMenu(
                                        expanded = contextMenuMedia?.id == item.id,
                                        onDismissRequest = { contextMenuMedia = null },
                                        actions = listOf(
                                            ContextMenuAction(
                                                label = "播放",
                                                icon = Icons.Rounded.PlayArrow,
                                                onClick = { onOpenMedia(item) },
                                            ),
                                            ContextMenuAction(
                                                label = if (item.played) "标记为未看" else "标记为已看",
                                                icon = if (item.played) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                onClick = { onTogglePlayed(item) },
                                            ),
                                        ),
                                    )
                                }
                            }
                            // Fill remaining columns with spacers if last row is incomplete
                            repeat(columnCount - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (isAppending) {
                    item(key = "library-append-indicator") {
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

        AnimatedVisibility(
            visible = sortMenuExpanded && isServerConnected,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                slideInVertically(animationSpec = tween(durationMillis = 260)) { it / 8 },
            exit = fadeOut(animationSpec = tween(durationMillis = 140)) +
                slideOutVertically(animationSpec = tween(durationMillis = 220)) { it / 10 },
        ) {
            LibrarySortSheet(
                selectedMode = librarySortMode,
                onDismiss = { sortMenuExpanded = false },
                onSelect = { mode ->
                    sortMenuExpanded = false
                    onSelectLibrarySortMode(mode)
                },
            )
        }

        AnimatedVisibility(
            visible = filterSheetExpanded && isServerConnected,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                slideInVertically(animationSpec = tween(durationMillis = 260)) { it / 8 },
            exit = fadeOut(animationSpec = tween(durationMillis = 140)) +
                slideOutVertically(animationSpec = tween(durationMillis = 220)) { it / 10 },
        ) {
            LibraryFilterSheet(
                availableGenres = availableGenres,
                currentFilter = libraryFilter,
                onDismiss = { filterSheetExpanded = false },
                onApply = { newFilter ->
                    filterSheetExpanded = false
                    onUpdateLibraryFilter(newFilter)
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
            .background(SoftUiScrim)
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
    val highlightColor = SoftUiAccent
    val cardAspectRatio = 16f / 9f
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFF2E4D0) else EditorialSurface,
        animationSpec = tween(durationMillis = 220),
        label = "librarySelectorBackground",
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) highlightColor else EditorialTextPrimary,
        animationSpec = tween(durationMillis = 220),
        label = "librarySelectorTitleColor",
    )
    Column(
        modifier = Modifier
            .width(cardWidth)
            .hapticPressScale(interactionSource)
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
                    .softUiSurface(
                        shape = shape,
                        style = if (selected) SoftUiSurfaceStyle.Inset else SoftUiSurfaceStyle.Raised,
                        color = backgroundColor,
                    ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SoftUiSurfacePressed),
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
                color = titleColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilterSheet(
    availableGenres: List<String>,
    currentFilter: LibraryFilterSpec,
    onDismiss: () -> Unit,
    onApply: (LibraryFilterSpec) -> Unit,
) {
    var selectedGenres by remember(currentFilter) { mutableStateOf(currentFilter.genres.toSet()) }
    var selectedYears by remember(currentFilter) { mutableStateOf(currentFilter.years.toSet()) }
    var unplayedOnly by remember(currentFilter) { mutableStateOf(currentFilter.unplayedOnly) }
    var favoritesOnly by remember(currentFilter) { mutableStateOf(currentFilter.favoritesOnly) }
    val currentYear = java.time.Year.now().value
    val yearOptions = remember { (currentYear downTo (currentYear - 12)).map { it.toString() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        EditorialCard(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = EditorialSurfaceStrong,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "内容筛选",
                        style = MaterialTheme.typography.titleLarge,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    EditorialIconButton(
                        icon = Icons.Rounded.Close,
                        onClick = onDismiss,
                    )
                }

                if (availableGenres.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "类型",
                            style = MaterialTheme.typography.titleSmall,
                            color = EditorialTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            availableGenres.forEach { genre ->
                                val isSelected = genre in selectedGenres
                                EditorialCard(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isSelected) EditorialAccent.copy(alpha = 0.18f) else EditorialChip.copy(alpha = 0.45f),
                                    onClick = {
                                        selectedGenres = if (isSelected) selectedGenres - genre else selectedGenres + genre
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) EditorialAccent else EditorialTextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "年份",
                        style = MaterialTheme.typography.titleSmall,
                        color = EditorialTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        yearOptions.forEach { year ->
                            val isSelected = year in selectedYears
                            EditorialCard(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isSelected) EditorialAccent.copy(alpha = 0.18f) else EditorialChip.copy(alpha = 0.45f),
                                onClick = {
                                    selectedYears = if (isSelected) selectedYears - year else selectedYears + year
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = year,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) EditorialAccent else EditorialTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterToggleRow(
                        label = "仅未观看",
                        description = "只显示还没看过的",
                        checked = unplayedOnly,
                        onToggle = { unplayedOnly = it },
                    )
                    FilterToggleRow(
                        label = "仅收藏",
                        description = "只显示已收藏的内容",
                        checked = favoritesOnly,
                        onToggle = { favoritesOnly = it },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EditorialCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color = EditorialChip.copy(alpha = 0.45f),
                        onClick = {
                            selectedGenres = emptySet()
                            selectedYears = emptySet()
                            unplayedOnly = false
                            favoritesOnly = false
                        },
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text(
                            text = "清除筛选",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleSmall,
                            color = EditorialTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    EditorialCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color = EditorialAccent,
                        onClick = {
                            onApply(
                                LibraryFilterSpec(
                                    genres = selectedGenres.toList(),
                                    years = selectedYears.toList(),
                                    unplayedOnly = unplayedOnly,
                                    favoritesOnly = favoritesOnly,
                                ),
                            )
                        },
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text(
                            text = "应用",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = EditorialTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = EditorialTextSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SoftUiAccent,
                checkedTrackColor = EditorialAccent.copy(alpha = 0.3f),
                uncheckedThumbColor = EditorialTextSecondary,
                uncheckedTrackColor = EditorialChip.copy(alpha = 0.45f),
            ),
        )
    }
}

private fun LibraryFilterSpec.toActiveChipLabels(): List<String> = buildList {
    addAll(genres)
    addAll(years)
    if (unplayedOnly) add("未观看")
    if (favoritesOnly) add("已收藏")
}

private fun LibraryFilterSpec.removeByLabel(label: String): LibraryFilterSpec = when (label) {
    "未观看" -> copy(unplayedOnly = false)
    "已收藏" -> copy(favoritesOnly = false)
    else -> copy(
        genres = genres - label,
        years = years - label,
    )
}
