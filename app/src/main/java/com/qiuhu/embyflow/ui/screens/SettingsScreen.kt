package com.qiuhu.embyflow.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import com.qiuhu.embyflow.data.settings.AppSettings
import com.qiuhu.embyflow.data.settings.PLAYER_MODE_COMPATIBILITY
import com.qiuhu.embyflow.data.settings.PLAYER_MODE_STANDARD
import com.qiuhu.embyflow.data.settings.PLAYER_MODE_SYSTEM
import com.qiuhu.embyflow.model.ServerProfile
import com.qiuhu.embyflow.model.ServerProfilesState
import com.qiuhu.embyflow.model.ServerSnapshot
import com.qiuhu.embyflow.model.displayName
import com.qiuhu.embyflow.ui.components.EditorialAccent
import com.qiuhu.embyflow.ui.components.EditorialBackground
import com.qiuhu.embyflow.ui.components.EditorialCard
import com.qiuhu.embyflow.ui.components.EditorialChip
import com.qiuhu.embyflow.ui.components.EditorialIconButton
import com.qiuhu.embyflow.ui.components.EditorialPill
import com.qiuhu.embyflow.ui.components.EditorialSurface
import com.qiuhu.embyflow.ui.components.EditorialSurfaceStrong
import com.qiuhu.embyflow.ui.components.EditorialTextPrimary
import com.qiuhu.embyflow.ui.components.EditorialTextSecondary
import com.qiuhu.embyflow.ui.components.FloatingNavBarHeight
import com.qiuhu.embyflow.ui.components.FloatingNavBarOuterPadding
import com.qiuhu.embyflow.ui.components.FloatingNavBarSheetClearance
import kotlinx.coroutines.delay

private const val SettingsSheetExitDurationMillis = 220

private object DotMaskVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            text = AnnotatedString("•".repeat(text.text.length)),
            offsetMapping = OffsetMapping.Identity,
        )
    }
}

private enum class SettingsSheetKey {
    Server,
    Account,
    Version,
    Player,
    Subtitle,
    Layout,
}

@Composable
fun SettingsScreen(
    server: ServerSnapshot,
    settings: AppSettings,
    serverProfilesState: ServerProfilesState,
    isServerConnected: Boolean,
    onUpdatePlayerMode: (String) -> Unit,
    onUpdateSubtitleMode: (String) -> Unit,
    onUpdateLayoutMode: (String) -> Unit,
    onUpdateShowLibraryCardTitle: (Boolean) -> Unit,
    onSaveServerProfile: (ServerProfile) -> Unit,
    onDeleteServerProfile: (String) -> Unit,
    onActivateServerProfile: (String) -> Unit,
) {
    var activeSheet by rememberSaveable { mutableStateOf<SettingsSheetKey?>(null) }
    var renderedSheetModel by remember { mutableStateOf<SettingsSheetModel?>(null) }
    var serverEditorVisible by remember { mutableStateOf(false) }
    var editingServerProfile by remember { mutableStateOf<ServerProfile?>(null) }

    val activeProfile = serverProfilesState.activeProfile
    val hasConfiguredServer = activeProfile != null
    val displayServerName = when {
        isServerConnected -> server.serverName
        hasConfiguredServer -> activeProfile!!.displayName()
        else -> "未配置服务器"
    }
    val displayUserName = when {
        isServerConnected -> server.userName
        hasConfiguredServer -> activeProfile!!.username
        else -> "未配置"
    }
    val displayServerVersion = when {
        isServerConnected -> server.serverVersion
        hasConfiguredServer -> "等待连接"
        else -> "未连接"
    }
    val topCardSubtitle = when {
        isServerConnected -> "当前账号 · $displayUserName"
        hasConfiguredServer -> "已保存账号 · $displayUserName"
        else -> "点击下方服务器卡片开始添加"
    }
    val topCardPill = when {
        isServerConnected -> "版本 $displayServerVersion"
        hasConfiguredServer -> "等待连接"
        else -> "尚未连接"
    }
    val accountRole = when {
        isServerConnected -> "已连接"
        hasConfiguredServer -> "已保存账号"
        else -> "未配置"
    }
    val syncStatus = when {
        isServerConnected -> "已完成"
        hasConfiguredServer -> "等待连接"
        else -> "尚未开始"
    }
    val activeServerLabel = when {
        hasConfiguredServer -> activeProfile!!.displayName()
        else -> "未配置"
    }

    val sheetModel = when (activeSheet) {
        SettingsSheetKey.Server -> SettingsSheetModel(
            title = if (serverEditorVisible) {
                if (editingServerProfile == null) "新增服务器" else "编辑服务器"
            } else {
                "服务器"
            },
            subtitle = if (serverEditorVisible) {
                "保存后会立即切换并重新连接"
            } else {
                "管理连接节点、切换默认服务器"
            },
            customContent = {
                if (serverEditorVisible) {
                    ServerEditorContent(
                        initialProfile = editingServerProfile,
                        onCancel = {
                            serverEditorVisible = false
                            editingServerProfile = null
                        },
                        onSave = { profile ->
                            onSaveServerProfile(profile)
                            activeSheet = null
                        },
                    )
                } else {
                    ServerManagerContent(
                        connectedServerName = displayServerName,
                        connectedUserName = displayUserName,
                        connectionLabel = when {
                            isServerConnected -> "当前连接"
                            hasConfiguredServer -> "当前默认"
                            else -> "尚未配置"
                        },
                        connectionSummary = when {
                            isServerConnected -> "已连接到当前 Emby 服务器"
                            hasConfiguredServer -> "服务器已保存，等待下次连接"
                            else -> "新增服务器后就能在这里切换连接"
                        },
                        profilesState = serverProfilesState,
                        onAdd = {
                            editingServerProfile = null
                            serverEditorVisible = true
                        },
                        onEdit = { profile ->
                            editingServerProfile = profile
                            serverEditorVisible = true
                        },
                        onActivate = { profile ->
                            onActivateServerProfile(profile.id)
                            activeSheet = null
                        },
                        onDelete = { profile ->
                            onDeleteServerProfile(profile.id)
                        },
                    )
                }
            },
        )

        SettingsSheetKey.Account -> SettingsSheetModel(
            title = "账号",
            subtitle = "当前登录账户",
            details = listOf(
                "用户名" to displayUserName,
                "状态" to accountRole,
                "同步" to syncStatus,
            ),
        )

        SettingsSheetKey.Version -> SettingsSheetModel(
            title = "版本信息",
            subtitle = "当前客户端与服务端版本",
            details = listOf(
                "服务端" to displayServerVersion,
                "客户端" to "Editorial Preview",
                "播放策略" to settings.playerMode,
            ),
        )

        SettingsSheetKey.Player -> SettingsSheetModel(
            title = "播放策略",
            subtitle = "调整解码回退、缓冲长度与起播速度",
            options = listOf(
                PLAYER_MODE_COMPATIBILITY,
                PLAYER_MODE_STANDARD,
                PLAYER_MODE_SYSTEM,
            ),
            selectedOption = settings.playerMode,
            onSelect = {
                onUpdatePlayerMode(it)
                activeSheet = null
            },
        )

        SettingsSheetKey.Subtitle -> SettingsSheetModel(
            title = "字幕策略",
            subtitle = "控制字幕自动匹配优先级",
            options = listOf("双语优先", "原语言优先", "仅外挂字幕", "关闭自动匹配"),
            selectedOption = settings.subtitleMode,
            onSelect = {
                onUpdateSubtitleMode(it)
                activeSheet = null
            },
        )

        SettingsSheetKey.Layout -> SettingsSheetModel(
            title = "界面风格",
            subtitle = "切换首页信息密度",
            options = listOf("编辑卡片流", "大图优先", "紧凑信息流"),
            selectedOption = settings.layoutMode,
            onSelect = {
                onUpdateLayoutMode(it)
                activeSheet = null
            },
        )

        null -> null
    }

    LaunchedEffect(sheetModel) {
        if (sheetModel != null) {
            renderedSheetModel = sheetModel
        } else {
            delay(SettingsSheetExitDurationMillis.toLong())
            renderedSheetModel = null
            serverEditorVisible = false
            editingServerProfile = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialBackground),
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.displaySmall,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "连接、播放、字幕与网络偏好",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EditorialTextSecondary,
                    )
                }
            }

            item {
                EditorialCard(
                    shape = RoundedCornerShape(30.dp),
                    color = EditorialSurfaceStrong,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = displayServerName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = EditorialTextPrimary,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                text = topCardSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = EditorialTextSecondary,
                            )
                            EditorialPill(text = topCardPill, color = EditorialChip)
                        }
                        EditorialIconButton(
                            icon = Icons.Rounded.Person,
                            modifier = Modifier
                                .padding(start = 14.dp)
                                .size(58.dp),
                            onClick = { activeSheet = SettingsSheetKey.Account },
                        )
                    }
                }
            }

            item {
                SettingsGroup(
                    title = "同步与账号",
                    rows = listOf(
                        SettingsRow("服务器", activeServerLabel, Icons.Rounded.Storage, highlight = true) {
                            activeSheet = SettingsSheetKey.Server
                        },
                        SettingsRow("账号", displayUserName, Icons.Rounded.Person) { activeSheet = SettingsSheetKey.Account },
                        SettingsRow("版本", displayServerVersion, Icons.Rounded.Memory) { activeSheet = SettingsSheetKey.Version },
                    ),
                )
            }

            item {
                SettingsGroup(
                    title = "播放与偏好",
                    rows = listOf(
                        SettingsRow("播放策略", settings.playerMode, Icons.Rounded.PlayArrow) { activeSheet = SettingsSheetKey.Player },
                        SettingsRow("字幕策略", settings.subtitleMode, Icons.Rounded.Subtitles) { activeSheet = SettingsSheetKey.Subtitle },
                        SettingsRow("界面风格", settings.layoutMode, Icons.Rounded.Tune) { activeSheet = SettingsSheetKey.Layout },
                    ),
                )
            }

            item {
                SettingsToggleGroup(
                    title = "资料库",
                    rows = listOf(
                        SettingsToggleRow(
                            label = "显示分区卡标题",
                            description = "关闭后只保留封面背景",
                            icon = Icons.Rounded.CollectionsBookmark,
                            checked = settings.showLibraryCardTitle,
                            onToggle = onUpdateShowLibraryCardTitle,
                        ),
                    ),
                )
            }
        }

        SettingsSheet(
            model = renderedSheetModel,
            visible = sheetModel != null,
            onDismiss = {
                activeSheet = null
            },
        )
    }
}

private data class SettingsRow(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val highlight: Boolean = false,
    val onClick: () -> Unit,
)

private data class SettingsSheetModel(
    val title: String,
    val subtitle: String,
    val details: List<Pair<String, String>> = emptyList(),
    val options: List<String> = emptyList(),
    val selectedOption: String? = null,
    val onSelect: ((String) -> Unit)? = null,
    val customContent: (@Composable () -> Unit)? = null,
)

private data class SettingsToggleRow(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val checked: Boolean,
    val onToggle: (Boolean) -> Unit,
)

@Composable
private fun SettingsGroup(
    title: String,
    rows: List<SettingsRow>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = EditorialTextPrimary,
            fontWeight = FontWeight.Black,
        )
        EditorialCard(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = row.onClick)
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EditorialIconButton(
                                icon = row.icon,
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = if (row.highlight) EditorialSurfaceStrong else EditorialChip,
                                onClick = row.onClick,
                            )
                            Text(
                                text = row.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = EditorialTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (row.highlight) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(EditorialAccent),
                                )
                            }
                            Text(
                                text = row.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = EditorialTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = EditorialTextSecondary,
                            )
                        }
                    }

                    if (index != rows.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .padding(start = 54.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(EditorialChip),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleGroup(
    title: String,
    rows: List<SettingsToggleRow>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = EditorialTextPrimary,
            fontWeight = FontWeight.Black,
        )
        EditorialCard(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { row.onToggle(!row.checked) }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EditorialIconButton(
                                icon = row.icon,
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = EditorialChip,
                                onClick = { row.onToggle(!row.checked) },
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = row.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = EditorialTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = row.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EditorialTextSecondary,
                                )
                            }
                        }

                        Switch(
                            checked = row.checked,
                            onCheckedChange = row.onToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EditorialAccent,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = EditorialChip,
                            ),
                        )
                    }

                    if (index != rows.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .padding(start = 54.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(EditorialChip),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSheet(
    model: SettingsSheetModel?,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (model == null) return

    val visibilityState = remember(model) { MutableTransitionState(false) }
    val density = LocalDensity.current
    val navigationBarBottomInset = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val sheetBottomPadding =
        navigationBarBottomInset +
            FloatingNavBarHeight +
            (FloatingNavBarOuterPadding * 2) +
            FloatingNavBarSheetClearance +
            8.dp

    LaunchedEffect(visible, model) {
        visibilityState.targetState = visible
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 160)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EditorialTextPrimary.copy(alpha = 0.18f))
                    .clickable(onClick = onDismiss),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = visibilityState,
                modifier = Modifier.fillMaxWidth(),
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = FastOutSlowInEasing,
                    ),
                ) +
                    slideInVertically(
                        animationSpec = tween(
                            durationMillis = 320,
                            easing = FastOutSlowInEasing,
                        ),
                        initialOffsetY = { it },
                    ) +
                    scaleIn(
                        animationSpec = tween(
                            durationMillis = 320,
                            easing = FastOutSlowInEasing,
                        ),
                        initialScale = 0.97f,
                    ),
                exit = fadeOut(
                    animationSpec = tween(durationMillis = 160),
                ) +
                    slideOutVertically(
                        animationSpec = tween(
                            durationMillis = SettingsSheetExitDurationMillis,
                            easing = FastOutSlowInEasing,
                        ),
                        targetOffsetY = { it },
                    ) +
                    scaleOut(
                        animationSpec = tween(
                            durationMillis = SettingsSheetExitDurationMillis,
                            easing = FastOutSlowInEasing,
                        ),
                        targetScale = 0.99f,
                    ),
            ) {
                EditorialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = sheetBottomPadding),
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
                                    text = model.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = EditorialTextPrimary,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    text = model.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EditorialTextSecondary,
                                )
                            }
                            EditorialIconButton(
                                icon = Icons.Rounded.ChevronRight,
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                onClick = onDismiss,
                            )
                        }

                        if (model.customContent != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 480.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                model.customContent.invoke()
                            }
                        } else if (model.options.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                model.options.forEach { option ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(
                                                if (option == model.selectedOption) {
                                                    EditorialSurfaceStrong
                                                } else {
                                                    EditorialChip.copy(alpha = 0.45f)
                                                },
                                            )
                                            .clickable { model.onSelect?.invoke(option) }
                                            .padding(horizontal = 14.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = EditorialTextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (option == model.selectedOption) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = EditorialAccent,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                model.details.forEach { (label, value) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EditorialTextSecondary,
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EditorialTextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerManagerContent(
    connectedServerName: String,
    connectedUserName: String,
    connectionLabel: String,
    connectionSummary: String,
    profilesState: ServerProfilesState,
    onAdd: () -> Unit,
    onEdit: (ServerProfile) -> Unit,
    onActivate: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
) {
    val orderedProfiles = remember(profilesState.profiles, profilesState.activeProfileId) {
        profilesState.profiles.sortedByDescending { it.id == profilesState.activeProfileId }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditorialCard(
            shape = RoundedCornerShape(24.dp),
            color = EditorialSurfaceStrong,
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorialPill(text = connectionLabel, color = EditorialChip)
                Text(
                    text = connectedServerName,
                    style = MaterialTheme.typography.titleLarge,
                    color = EditorialTextPrimary,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "账号 · $connectedUserName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EditorialTextSecondary,
                )
                Text(
                    text = connectionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorialTextSecondary,
                )
            }
        }

        if (orderedProfiles.isEmpty()) {
            EditorialCard(
                shape = RoundedCornerShape(24.dp),
                color = EditorialSurface,
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "还没有保存任何服务器",
                        style = MaterialTheme.typography.titleMedium,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "新增一个 Emby 节点后，这里就可以直接切换连接。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EditorialTextSecondary,
                    )
                }
            }
        } else {
            orderedProfiles.forEach { profile ->
                ServerProfileCard(
                    profile = profile,
                    isActive = profile.id == profilesState.activeProfileId,
                    onEdit = { onEdit(profile) },
                    onActivate = { onActivate(profile) },
                    onDelete = { onDelete(profile) },
                )
            }
        }

        EditorialCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAdd,
            shape = RoundedCornerShape(24.dp),
            color = EditorialChip.copy(alpha = 0.62f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorialIconButton(
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = EditorialSurface,
                    onClick = onAdd,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "新增服务器",
                        style = MaterialTheme.typography.bodyLarge,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "添加新的 Emby 地址、账号和密码",
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorialTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerProfileCard(
    profile: ServerProfile,
    isActive: Boolean,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isActive) null else onActivate,
        shape = RoundedCornerShape(24.dp),
        color = if (isActive) EditorialSurfaceStrong else EditorialSurface,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = profile.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = profile.serverUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EditorialTextSecondary,
                    )
                    Text(
                        text = "账号 · ${profile.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorialTextSecondary,
                    )
                }
                if (isActive) {
                    EditorialPill(text = "已连接", color = EditorialAccent.copy(alpha = 0.18f))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerActionChip(
                    modifier = Modifier.weight(1f),
                    label = if (isActive) "当前服务器" else "切换连接",
                    icon = if (isActive) Icons.Rounded.Done else Icons.Rounded.Storage,
                    accent = isActive,
                    onClick = if (isActive) ({}) else onActivate,
                )
                ServerActionChip(
                    modifier = Modifier.weight(1f),
                    label = "编辑",
                    icon = Icons.Rounded.Edit,
                    onClick = onEdit,
                )
                ServerActionChip(
                    modifier = Modifier.weight(1f),
                    label = "删除",
                    icon = Icons.Rounded.DeleteOutline,
                    destructive = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun ServerActionChip(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        destructive -> Color(0x14B93A32)
        accent -> EditorialAccent.copy(alpha = 0.14f)
        else -> EditorialChip.copy(alpha = 0.58f)
    }
    val contentColor = when {
        destructive -> Color(0xFF9C3A31)
        accent -> EditorialTextPrimary
        else -> EditorialTextPrimary
    }
    val resolvedContentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.42f)
    val resolvedBackground = if (enabled) background else background.copy(alpha = 0.42f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(resolvedBackground)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedContentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = resolvedContentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ServerEditorContent(
    initialProfile: ServerProfile?,
    onCancel: () -> Unit,
    onSave: (ServerProfile) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var name by rememberSaveable(initialProfile?.id ?: "new-name") {
        mutableStateOf(initialProfile?.name.orEmpty())
    }
    var serverUrl by rememberSaveable(initialProfile?.id ?: "new-url") {
        mutableStateOf(initialProfile?.serverUrl.orEmpty())
    }
    var username by rememberSaveable(initialProfile?.id ?: "new-username") {
        mutableStateOf(initialProfile?.username.orEmpty())
    }
    var password by rememberSaveable(initialProfile?.id ?: "new-password") {
        mutableStateOf(initialProfile?.password.orEmpty())
    }

    val saveEnabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "留空时会自动使用服务器地址作为名称。",
            style = MaterialTheme.typography.bodySmall,
            color = EditorialTextSecondary,
        )

        ServerField(
            label = "名称",
            value = name,
            placeholder = "例如：家里 NAS",
            imeAction = ImeAction.Next,
            onValueChange = { name = it },
        )
        ServerField(
            label = "服务器地址",
            value = serverUrl,
            placeholder = "http://192.168.6.230:8899",
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Uri,
            onValueChange = { serverUrl = it },
        )
        ServerField(
            label = "用户名",
            value = username,
            placeholder = "输入 Emby 用户名",
            imeAction = ImeAction.Next,
            onValueChange = { username = it },
        )
        ServerField(
            label = "密码",
            value = password,
            placeholder = "输入 Emby 密码",
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Text,
            isPassword = true,
            onValueChange = { password = it },
            onSubmit = { focusManager.clearFocus() },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerActionChip(
                modifier = Modifier.weight(1f),
                label = "取消",
                icon = Icons.Rounded.ChevronRight,
                onClick = onCancel,
            )
            ServerActionChip(
                modifier = Modifier.weight(1f),
                label = "保存并连接",
                icon = Icons.Rounded.Done,
                enabled = saveEnabled,
                accent = true,
                onClick = {
                    onSave(
                        ServerProfile(
                            id = initialProfile?.id.orEmpty(),
                            name = name,
                            serverUrl = serverUrl,
                            username = username,
                            password = password,
                        ),
                    )
                },
            )
        }

        if (!saveEnabled) {
            Text(
                text = "服务器地址、用户名和密码都要填写。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9C3A31),
            )
        }
    }
}

@Composable
private fun ServerField(
    label: String,
    value: String,
    placeholder: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = EditorialTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = EditorialTextPrimary,
                fontWeight = FontWeight.SemiBold,
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = imeAction,
                keyboardType = keyboardType,
                autoCorrectEnabled = !isPassword,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            cursorBrush = SolidColor(EditorialAccent),
            visualTransformation = if (isPassword) DotMaskVisualTransformation else VisualTransformation.None,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(EditorialChip.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EditorialTextSecondary,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
