package com.qiuhu.embyflow.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.imageLoader
import coil.request.ImageRequest
import com.qiuhu.embyflow.model.cardEpisodeBadgeLabel
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.hasBackdropImage
import com.qiuhu.embyflow.model.isEpisode
import com.qiuhu.embyflow.ui.components.MediaPosterCornerBadge
import com.qiuhu.embyflow.ui.components.PixelCatAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val TodayBackground = Color(0xFFF5F1EA)
private val TodaySurface = Color(0xFFFBF7F1)
private val TodaySurfaceStrong = Color(0xFFF0E7DA)
private val TodayTextPrimary = Color(0xFF161310)
private val TodayTextSecondary = Color(0xFF6F685F)
private val TodayCardShadow = Color(0x22000000)
private val TodayChip = Color(0xFFE8DECF)
private val TodayAction = Color(0xFFF4EEE5)
private const val TodayHighlightsAutoScrollResumeDelayMillis = 2200L
private const val TodayHighlightsAutoScrollPollingMillis = 120L
private val TodayHighlightsAutoScrollSpeed = 18.dp
private const val TodayHeroPrefetchCount = 6

@Composable
fun HomeScreen(
    serverName: String,
    userName: String,
    layoutMode: String,
    heroItems: List<MediaItem>,
    highlightItems: List<MediaItem>,
    continueWatchingItems: List<MediaItem>,
    libraries: List<MediaItem>,
    isServerConnected: Boolean,
    hasConfiguredServer: Boolean,
    onOpenMedia: (MediaItem) -> Unit,
    onOpenLibrary: (MediaItem) -> Unit,
) {
    val heroRotationPool = remember(heroItems) {
        heroItems
            .distinctBy { it.id }
            .filter { it.hasBackdropImage() }
    }
    val highlights = highlightItems.take(8)
    val isLargeLayout = layoutMode == "大图优先"
    val isCompactLayout = layoutMode == "紧凑信息流"
    val resumeItems = continueWatchingItems.take(
        when {
            isCompactLayout -> 8
            isLargeLayout -> 4
            else -> 6
        },
    )
    val heroWidthFraction = when {
        isCompactLayout -> 0.72f
        isLargeLayout -> 1f
        else -> 0.9f
    }
    val heroCardHeight = when {
        isCompactLayout -> 200.dp
        isLargeLayout -> 276.dp
        else -> 236.dp
    }
    val resumeCardWidth = when {
        isCompactLayout -> 236.dp
        isLargeLayout -> 344.dp
        else -> 292.dp
    }
    val resumeCardHeight = when {
        isCompactLayout -> 188.dp
        isLargeLayout -> 256.dp
        else -> 220.dp
    }
    val libraryPreviewCount = when {
        isCompactLayout -> 6
        isLargeLayout -> 4
        else -> 5
    }
    val rowSpacing = when {
        isCompactLayout -> 12.dp
        isLargeLayout -> 20.dp
        else -> 16.dp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TodayBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 18.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                TodayHeader(
                    serverName = serverName,
                    userName = userName,
                    isServerConnected = isServerConnected,
                    hasConfiguredServer = hasConfiguredServer,
                )
            }

            if (!isServerConnected) {
                item {
                    TodayInfoCard(
                        title = if (hasConfiguredServer) "还没有连接到媒体服务器" else "还没有添加服务器",
                        description = if (hasConfiguredServer) {
                            "检查服务器地址、账号、密码或当前网络后，再回来刷新首页。"
                        } else {
                            "前往设置添加 Emby 服务器后，这里会显示首页推荐、继续观看和媒体库。"
                        },
                    )
                }
                if (hasConfiguredServer) {
                    item {
                        TodayInfoCard(
                            title = "已保存服务器，等待重新连接",
                            description = "设置页里可以切换其他服务器，或者回到当前服务器重新建立连接。",
                        )
                    }
                }
            } else {
                item {
                    TodayHeroCarousel(
                        items = heroRotationPool,
                        cardWidthFraction = heroWidthFraction,
                        cardHeight = heroCardHeight,
                        onOpenMedia = onOpenMedia,
                    )
                }

                if (highlights.isNotEmpty()) {
                    item {
                        TodaySectionHeader(
                            title = "今日活动进行时",
                        )
                    }

                    item {
                        TodayHighlightsRow(
                            items = highlights,
                            itemSpacing = 8.dp,
                            onOpenMedia = onOpenMedia,
                        )
                    }
                }

                item {
                    TodaySectionHeader(
                        title = "继续观看",
                    )
                }

                if (resumeItems.isEmpty()) {
                    item {
                        TodayInfoCard(
                            title = "还没有继续观看内容",
                            description = "开始播放后，断点续播会出现在这里。",
                        )
                    }
                } else {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                        ) {
                            items(resumeItems) { item ->
                                TodayFeatureCard(
                                    media = item,
                                    modifier = Modifier.width(resumeCardWidth),
                                    height = resumeCardHeight,
                                    transparentFooter = true,
                                    preferTitleLogo = true,
                                    onClick = { onOpenMedia(item) },
                                )
                            }
                        }
                    }
                }

                item {
                    TodaySectionHeader(
                        title = "媒体库",
                    )
                }

                items(libraries.take(libraryPreviewCount)) { item ->
                    TodayLibraryRow(
                        media = item,
                        onClick = { onOpenLibrary(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayHighlightsRow(
    items: List<MediaItem>,
    itemSpacing: Dp,
    onOpenMedia: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return

    val canAutoScroll = items.size > 1
    val density = LocalDensity.current
    val autoScrollSpeedPx = with(density) { TodayHighlightsAutoScrollSpeed.toPx() }
    val initialIndex = remember(items) {
        if (!canAutoScroll) {
            0
        } else {
            val midpoint = Int.MAX_VALUE / 2
            midpoint - (midpoint % items.size)
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val interactionScope = rememberCoroutineScope()
    var lastUserInteractionAt by remember(items) { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var isAutoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(canAutoScroll, listState) {
        if (!canAutoScroll) return@LaunchedEffect
        while (isActive) {
            if (listState.isScrollInProgress && !isAutoScrolling) {
                lastUserInteractionAt = SystemClock.uptimeMillis()
            }
            delay(TodayHighlightsAutoScrollPollingMillis)
        }
    }

    LaunchedEffect(canAutoScroll, listState, lastUserInteractionAt, autoScrollSpeedPx) {
        if (!canAutoScroll) return@LaunchedEffect
        val idleDuration = SystemClock.uptimeMillis() - lastUserInteractionAt
        val remainingDelay = TodayHighlightsAutoScrollResumeDelayMillis - idleDuration
        if (remainingDelay > 0L) {
            delay(remainingDelay)
        }

        isAutoScrolling = true
        try {
            listState.scroll {
                var previousFrameNanos = withFrameNanos { it }
                while (isActive) {
                    val frameNanos = withFrameNanos { it }
                    val deltaSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                    previousFrameNanos = frameNanos
                    scrollBy(autoScrollSpeedPx * deltaSeconds)
                }
            }
        } finally {
            isAutoScrolling = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val itemWidth = (maxWidth - (itemSpacing * 2)) / 3
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (canAutoScroll) {
                items(
                    count = Int.MAX_VALUE,
                    key = { it },
                ) { index ->
                    val item = items[index % items.size]
                    TodayPosterFeatureCard(
                        media = item,
                        modifier = Modifier.width(itemWidth),
                        onPress = {
                            lastUserInteractionAt = SystemClock.uptimeMillis()
                            interactionScope.launch {
                                listState.stopScroll()
                            }
                        },
                        onClick = { onOpenMedia(item) },
                    )
                }
            } else {
                items(items) { item ->
                    TodayPosterFeatureCard(
                        media = item,
                        modifier = Modifier.width(itemWidth),
                        onPress = {
                            lastUserInteractionAt = SystemClock.uptimeMillis()
                        },
                        onClick = { onOpenMedia(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayPosterFeatureCard(
    media: MediaItem,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val imageUrl = media.primaryImageUrl ?: media.backdropImageUrl
    val badgeLabel = media.cardEpisodeBadgeLabel()

    Column(
        modifier = modifier
            .pointerInput(onClick, onPress) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        if (tryAwaitRelease()) {
                            onClick()
                        }
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(18.dp, shape, clip = false, ambientColor = TodayCardShadow, spotColor = TodayCardShadow)
                .clip(shape)
                .background(Brush.verticalGradient(media.colors)),
        ) {
            PixelCatAsyncImage(
                model = imageUrl,
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.labelMedium,
                color = TodayTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = media.subtitle.ifBlank { media.meta },
                style = MaterialTheme.typography.labelSmall,
                color = TodayTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TodayHeroCarousel(
    items: List<MediaItem>,
    cardWidthFraction: Float,
    cardHeight: Dp,
    onOpenMedia: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val density = LocalDensity.current
    var rotationOrder by remember(items) {
        mutableStateOf(items.shuffledForHeroRotation())
    }
    var activeIndex by remember(items) {
        mutableIntStateOf(0)
    }
    var warmedHeroUrls by remember(items) { mutableStateOf(setOf<String>()) }
    val activeItem = rotationOrder.getOrElse(activeIndex) { items.first() }

    LaunchedEffect(items) {
        rotationOrder = items.shuffledForHeroRotation()
        activeIndex = 0
        while (true) {
            delay(4800)
            if (rotationOrder.size <= 1) continue

            val nextIndex = activeIndex + 1
            if (nextIndex < rotationOrder.size) {
                activeIndex = nextIndex
            } else {
                val previousItemId = rotationOrder
                    .getOrNull(activeIndex)
                    ?.id
                    ?: items.firstOrNull()?.id
                rotationOrder = items.shuffledForHeroRotation(previousId = previousItemId)
                activeIndex = 0
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val targetWidthPx = with(density) { (maxWidth * cardWidthFraction).roundToPx().coerceAtLeast(1) }
        val targetHeightPx = with(density) { cardHeight.roundToPx().coerceAtLeast(1) }

        LaunchedEffect(rotationOrder, activeIndex, targetWidthPx, targetHeightPx) {
            val upcomingUrls = buildList {
                val limit = minOf(TodayHeroPrefetchCount, rotationOrder.size)
                repeat(limit) { offset ->
                    val media = rotationOrder.getOrNull((activeIndex + offset) % rotationOrder.size)
                    val imageUrl = media?.backdropImageUrl ?: media?.primaryImageUrl
                    if (!imageUrl.isNullOrBlank()) {
                        add(imageUrl)
                    }
                }
            }.filterNot { warmedHeroUrls.contains(it) }

            if (upcomingUrls.isEmpty()) return@LaunchedEffect

            upcomingUrls.forEach { imageUrl ->
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(targetWidthPx, targetHeightPx)
                        .memoryCacheKey(imageUrl)
                        .diskCacheKey(imageUrl)
                        .build(),
                )
            }
            warmedHeroUrls = warmedHeroUrls + upcomingUrls
        }

        AnimatedContent(
            targetState = activeItem,
            transitionSpec = {
                (
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = 650,
                            easing = FastOutSlowInEasing,
                        ),
                    ) +
                        slideInHorizontally(
                            animationSpec = tween(
                                durationMillis = 650,
                                easing = FastOutSlowInEasing,
                            ),
                            initialOffsetX = { it / 16 },
                        ) +
                        scaleIn(
                            animationSpec = tween(
                                durationMillis = 650,
                                easing = FastOutSlowInEasing,
                            ),
                            initialScale = 0.985f,
                        )
                    ) togetherWith (
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = 520,
                            easing = FastOutSlowInEasing,
                        ),
                    ) +
                        slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = 520,
                                easing = FastOutSlowInEasing,
                            ),
                            targetOffsetX = { -it / 18 },
                        ) +
                        scaleOut(
                            animationSpec = tween(
                                durationMillis = 520,
                                easing = FastOutSlowInEasing,
                            ),
                            targetScale = 1.015f,
                        )
                    )
            },
            label = "today-hero-rotation",
        ) { item ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TodayFeatureCard(
                    media = item,
                    modifier = Modifier.fillMaxWidth(cardWidthFraction),
                    height = cardHeight,
                    imageContentScale = ContentScale.Crop,
                    transparentFooter = true,
                    preferTitleLogo = true,
                    onClick = { onOpenMedia(item) },
                )
            }
        }
    }
}

@Composable
private fun TodayHeader(
    serverName: String,
    userName: String,
    isServerConnected: Boolean,
    hasConfiguredServer: Boolean,
) {
    val statusText = when {
        isServerConnected -> "已连接 · $userName"
        hasConfiguredServer -> "等待连接 · $userName"
        else -> "尚未配置服务器"
    }
    val statusColor = when {
        isServerConnected -> Color(0xFF76C85F)
        hasConfiguredServer -> Color(0xFFD09A55)
        else -> Color(0xFFC6B6A3)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = TodayCardShadow, spotColor = TodayCardShadow)
                    .clip(RoundedCornerShape(18.dp))
                    .background(TodaySurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = serverName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TodayTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TodayTextSecondary,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(8.dp, CircleShape, clip = false, ambientColor = TodayCardShadow, spotColor = TodayCardShadow)
                    .clip(CircleShape)
                    .background(TodaySurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = TodayTextPrimary,
                )
            }
        }
    }
}

@Composable
private fun TodaySectionHeader(
    title: String,
    subtitle: String = "",
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TodayTextPrimary,
            fontWeight = FontWeight.Black,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TodayTextSecondary,
            )
        }
    }
}

@Composable
private fun TodayFeatureCard(
    media: MediaItem,
    modifier: Modifier = Modifier,
    height: Dp,
    imageContentScale: ContentScale = ContentScale.Crop,
    adaptCardToImage: Boolean = false,
    transparentFooter: Boolean = false,
    preferTitleLogo: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val imageUrl = media.backdropImageUrl ?: media.primaryImageUrl
    val badgeLabel = media.cardEpisodeBadgeLabel()
    var imageAspectRatio by remember(imageUrl) { mutableStateOf(16f / 9f) }
    val cardSizeModifier = if (adaptCardToImage) {
        Modifier
            .fillMaxWidth()
            .aspectRatio(imageAspectRatio)
    } else {
        Modifier
            .fillMaxWidth()
            .height(height)
    }

    Box(
        modifier = modifier
            .then(cardSizeModifier)
            .shadow(22.dp, shape, clip = false, ambientColor = TodayCardShadow, spotColor = TodayCardShadow)
            .clip(shape)
            .background(Brush.verticalGradient(media.colors))
            .clickable(onClick = onClick),
    ) {
        PixelCatAsyncImage(
            model = imageUrl,
            contentDescription = media.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = imageContentScale,
            onSuccess = { state ->
                if (adaptCardToImage) {
                    val width = state.result.drawable.intrinsicWidth
                    val heightPx = state.result.drawable.intrinsicHeight
                    if (width > 0 && heightPx > 0) {
                        imageAspectRatio = width.toFloat() / heightPx.toFloat()
                    }
                }
            },
        )

        if (!badgeLabel.isNullOrBlank()) {
            MediaPosterCornerBadge(
                text = badgeLabel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0x1A000000),
                            Color(0xB8000000),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            TodayCardFooter(
                media = media,
                transparent = transparentFooter,
                preferTitleLogo = preferTitleLogo,
            )
        }
    }
}

@Composable
private fun TodayCardFooter(
    media: MediaItem,
    compact: Boolean = false,
    transparent: Boolean = false,
    preferTitleLogo: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (transparent) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(if (compact) 14.dp else 16.dp))
                        .background(if (compact) Color(0x66FFFFFF) else Color(0x73FFFFFF))
                }
            )
            .padding(
                horizontal = if (transparent) 0.dp else if (compact) 12.dp else 14.dp,
                vertical = if (transparent) 0.dp else if (compact) 7.dp else 8.dp,
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (preferTitleLogo && media.isEpisode && !media.seriesTitleLogoUrl.isNullOrBlank()) {
                TodayCardLogo(
                    model = media.seriesTitleLogoUrl,
                    contentDescription = media.seriesName.ifBlank { media.title },
                    compact = compact,
                )
                Text(
                    text = media.title,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                TodayCardTitle(
                    media = media,
                    compact = compact,
                    preferTitleLogo = preferTitleLogo,
                )
            }
            Text(
                text = media.subtitle.ifBlank { media.meta },
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TodayCardTitle(
    media: MediaItem,
    compact: Boolean = false,
    preferTitleLogo: Boolean = false,
) {
    if (preferTitleLogo && media.titleLogoUrl != null) {
        TodayCardLogo(
            model = media.titleLogoUrl,
            contentDescription = media.title,
            compact = compact,
        )
        return
    }

    Text(
        text = media.title,
        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TodayCardLogo(
    model: Any?,
    contentDescription: String?,
    compact: Boolean,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxWidth(if (compact) 0.74f else 0.82f)
            .heightIn(
                min = if (compact) 24.dp else 28.dp,
                max = if (compact) 30.dp else 38.dp,
            ),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        loading = {},
        success = { SubcomposeAsyncImageContent() },
        error = {
            Text(
                text = contentDescription.orEmpty(),
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun TodayInfoCard(
    title: String,
    description: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp), clip = false, ambientColor = TodayCardShadow, spotColor = TodayCardShadow)
            .clip(RoundedCornerShape(28.dp))
            .background(TodaySurface)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TodaySurfaceStrong),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = TodayTextPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TodayTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TodayTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun TodayLibraryRow(
    media: MediaItem,
    onClick: () -> Unit,
) {
    val imageUrl = media.backdropImageUrl ?: media.primaryImageUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(14.dp, RoundedCornerShape(24.dp), clip = false, ambientColor = TodayCardShadow, spotColor = TodayCardShadow)
            .clip(RoundedCornerShape(24.dp))
            .background(TodaySurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(media.colors)),
                contentAlignment = Alignment.Center,
            ) {
                PixelCatAsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TodayTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = media.summary.ifBlank { media.subtitle },
                    style = MaterialTheme.typography.bodySmall,
                    color = TodayTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(TodayChip)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "打开",
                        style = MaterialTheme.typography.labelLarge,
                        color = TodayTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TodayTextSecondary,
                )
            }
        }
    }
}

private fun List<MediaItem>.pickNextRandom(
    previousId: String,
): MediaItem {
    if (size <= 1) return first()
    val candidates = filterNot { it.id == previousId }
    return if (candidates.isNotEmpty()) candidates.random() else random()
}

private fun List<MediaItem>.shuffledForHeroRotation(
    previousId: String? = null,
): List<MediaItem> {
    if (size <= 1) return this
    val shuffled = shuffled().toMutableList()
    if (previousId == null) {
        return shuffled
    }
    val firstCandidateIndex = shuffled.indexOfFirst { it.id != previousId }
    if (firstCandidateIndex > 0) {
        val firstCandidate = shuffled.removeAt(firstCandidateIndex)
        shuffled.add(0, firstCandidate)
    }
    return shuffled
}
