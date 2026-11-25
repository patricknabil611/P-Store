package com.example.ui.light

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.catalog.AppCatalog
import com.example.model.AppActionState
import com.example.model.AppInstallStatus
import com.example.model.AppItemUiState
import com.example.model.RecentUpdateRecord
import com.example.model.ReleaseStatus
import com.example.ui.components.AppBrandIcon
import com.example.ui.components.AppContextMenu
import com.example.ui.components.EmptyCatalogCard
import com.example.ui.components.ErrorActionBox
import com.example.ui.components.NewAppBadge
import com.example.ui.components.RateLimitNoticeBanner
import com.example.ui.dark.extractReleaseError
import com.example.ui.theme.PStoreTheme
import com.example.util.Formatters
import com.example.util.VersionComparator

enum class ShelfTab {
    MY_APPS,
    UPDATES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanShelfScreen(
    apps: List<AppItemUiState>,
    recentUpdates: List<RecentUpdateRecord>,
    selectedTab: ShelfTab,
    isRefreshing: Boolean,
    isUpdatingAll: Boolean,
    updatingCurrent: Int,
    updatingTotal: Int,
    lastCheckedAtMillis: Long,
    rateLimitNotice: String?,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (ShelfTab) -> Unit,
    onRefresh: () -> Unit,
    onAddApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenApp: (AppItemUiState) -> Unit,
    onInstallOrUpdate: (AppItemUiState) -> Unit,
    onCancelDownload: (AppItemUiState) -> Unit,
    onUpdateAll: () -> Unit,
    onOpenDetail: (AppItemUiState) -> Unit,
    onCopyAppInfo: (AppItemUiState) -> Unit,
    onHideApp: (AppItemUiState) -> Unit,
    onRetryApp: (AppItemUiState) -> Unit,
    onUninstallApp: (AppItemUiState) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors

    val launcherApps = remember(apps) { apps.filter { it.catalogApp.showInLauncher } }
    val pendingUpdates = remember(apps) {
        apps.filter { it.updateAvailable && it.latestRelease != null }
    }
    val activeInstallItems = remember(apps) {
        apps.filter {
            it.actionState is AppActionState.Downloading ||
                it.actionState is AppActionState.Verifying ||
                it.actionState is AppActionState.Installing
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Header: "P Store" (30sp Space Grotesk bold) + round 44dp white Refresh & Settings buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.primaryText
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onAddApp,
                        shape = CircleShape,
                        color = colors.accent,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("shelf_add_app_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.cd_add_app),
                                tint = colors.onAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    ShelfRoundIconButton(
                        isSpinning = isRefreshing,
                        enabled = !isRefreshing,
                        onClick = onRefresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                        icon = Icons.Rounded.Refresh,
                        testTag = "shelf_refresh_button"
                    )
                    ShelfRoundIconButton(
                        isSpinning = false,
                        enabled = true,
                        onClick = onOpenSettings,
                        contentDescription = stringResource(R.string.cd_settings),
                        icon = Icons.Rounded.Settings,
                        testTag = "shelf_settings_button"
                    )
                }
            }

            // Segmented control: "My apps" | "Updates (N)"
            ShelfSegmentedControl(
                selectedTab = selectedTab,
                updateCount = pendingUpdates.size,
                onSelectTab = onSelectTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                when (selectedTab) {
                    ShelfTab.MY_APPS -> {
                        ShelfMyAppsContent(
                            launcherApps = launcherApps,
                            isRefreshing = isRefreshing,
                            pendingUpdateCount = pendingUpdates.size,
                            isUpdatingAll = isUpdatingAll,
                            updatingCurrent = updatingCurrent,
                            updatingTotal = updatingTotal,
                            lastCheckedAtMillis = lastCheckedAtMillis,
                            rateLimitNotice = rateLimitNotice,
                            onUpdateAll = onUpdateAll,
                            onOpenApp = onOpenApp,
                            onInstallOrUpdate = onInstallOrUpdate,
                            onCancelDownload = onCancelDownload,
                            onOpenDetail = onOpenDetail,
                            onCopyAppInfo = onCopyAppInfo,
                            onHideApp = onHideApp,
                            onRetryApp = onRetryApp,
                            onUninstallApp = onUninstallApp
                        )
                    }
                    ShelfTab.UPDATES -> {
                        ShelfUpdatesContent(
                            allApps = apps,
                            activeInstallItems = activeInstallItems,
                            pendingUpdates = pendingUpdates,
                            recentUpdates = recentUpdates,
                            lastCheckedAtMillis = lastCheckedAtMillis,
                            rateLimitNotice = rateLimitNotice,
                            onInstallOrUpdate = onInstallOrUpdate,
                            onCancelDownload = onCancelDownload,
                            onOpenDetail = onOpenDetail,
                            onOpenApp = onOpenApp,
                            onCopyAppInfo = onCopyAppInfo,
                            onHideApp = onHideApp,
                            onUninstallApp = onUninstallApp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfRoundIconButton(
    isSpinning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String
) {
    val colors = PStoreTheme.colors
    val rotation = if (isSpinning) {
        val transition = rememberInfiniteTransition(label = "shelf_spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "angle"
        )
        angle
    } else 0f

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = colors.card,
        shadowElevation = 2.dp,
        modifier = Modifier
            .size(44.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isSpinning) colors.accent else colors.primaryText,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }
    }
}

@Composable
private fun ShelfSegmentedControl(
    selectedTab: ShelfTab,
    updateCount: Int,
    onSelectTab: (ShelfTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.segmentedTrack,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SegmentTabItem(
                label = stringResource(R.string.tab_my_apps),
                selected = selectedTab == ShelfTab.MY_APPS,
                onClick = { onSelectTab(ShelfTab.MY_APPS) },
                testTag = "segment_my_apps",
                modifier = Modifier.weight(1f)
            )
            SegmentTabItem(
                label = stringResource(R.string.tab_updates_count, updateCount),
                selected = selectedTab == ShelfTab.UPDATES,
                onClick = { onSelectTab(ShelfTab.UPDATES) },
                testTag = "segment_updates",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.card else colors.segmentedTrack,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .heightIn(min = 44.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) colors.primaryText else colors.secondaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ShelfMyAppsContent(
    launcherApps: List<AppItemUiState>,
    isRefreshing: Boolean,
    pendingUpdateCount: Int,
    isUpdatingAll: Boolean,
    updatingCurrent: Int,
    updatingTotal: Int,
    lastCheckedAtMillis: Long,
    rateLimitNotice: String?,
    onUpdateAll: () -> Unit,
    onOpenApp: (AppItemUiState) -> Unit,
    onInstallOrUpdate: (AppItemUiState) -> Unit,
    onCancelDownload: (AppItemUiState) -> Unit,
    onOpenDetail: (AppItemUiState) -> Unit,
    onCopyAppInfo: (AppItemUiState) -> Unit,
    onHideApp: (AppItemUiState) -> Unit,
    onRetryApp: (AppItemUiState) -> Unit,
    onUninstallApp: (AppItemUiState) -> Unit
) {
    val colors = PStoreTheme.colors
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        if (!rateLimitNotice.isNullOrBlank()) {
            item(key = "shelf_rate_limit") {
                RateLimitNoticeBanner(message = rateLimitNotice)
            }
        }
        if (pendingUpdateCount > 0 || isUpdatingAll) {
            item(key = "shelf_update_banner") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.bannerBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shelf_update_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = stringResource(R.string.cd_updates_icon),
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when {
                                    isUpdatingAll -> stringResource(R.string.updating_progress, updatingCurrent, updatingTotal)
                                    pendingUpdateCount == 1 -> stringResource(R.string.updates_ready_banner_one)
                                    else -> stringResource(R.string.updates_ready_banner_many, pendingUpdateCount)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.bannerText
                            )
                        }

                        if (!isUpdatingAll) {
                            TextButton(
                                onClick = onUpdateAll,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("shelf_update_all_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.update_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Single white card containing all launcher apps with subtle row dividers, or empty state
        if (launcherApps.isEmpty() && !isRefreshing) {
            item(key = "shelf_empty_catalog") {
                EmptyCatalogCard()
            }
        } else if (launcherApps.isNotEmpty()) {
            item(key = "shelf_apps_card") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = colors.card,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        launcherApps.forEachIndexed { index, item ->
                            ShelfAppRow(
                                item = item,
                                onRowClick = { onOpenDetail(item) },
                                onOpen = { onOpenApp(item) },
                                onInstallOrUpdate = { onInstallOrUpdate(item) },
                                onCancelDownload = { onCancelDownload(item) },
                                onCopyInfo = { onCopyAppInfo(item) },
                                onHide = { onHideApp(item) },
                                onRetry = { onRetryApp(item) },
                                onUninstall = { onUninstallApp(item) }
                            )
                            if (index < launcherApps.lastIndex) {
                                HorizontalDivider(
                                    color = colors.divider,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Small centered footer: "Synced with GitHub · checked X min ago"
        item(key = "shelf_sync_footer") {
            Text(
                text = Formatters.formatRelativeChecked(context, lastCheckedAtMillis, withSyncPrefix = true),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfAppRow(
    item: AppItemUiState,
    onRowClick: () -> Unit,
    onOpen: () -> Unit,
    onInstallOrUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onCopyInfo: () -> Unit,
    onHide: () -> Unit,
    onRetry: () -> Unit,
    onUninstall: () -> Unit
) {
    val colors = PStoreTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }
    val isNew = remember(item.catalogApp.firstDiscoveredAtMillis) { item.isNewlyDiscovered() }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onRowClick,
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("shelf_row_${item.id}")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppBrandIcon(
                    app = item.catalogApp,
                    size = 52.dp,
                    cornerRadius = 15.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.catalogApp.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isNew) {
                            NewAppBadge()
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    val (statusText, isHighlight) = buildShelfStatusText(item)
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isHighlight) colors.accent else colors.secondaryText,
                        fontWeight = if (isHighlight) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                ShelfPillActionButton(
                    item = item,
                    onOpen = onOpen,
                    onInstallOrUpdate = onInstallOrUpdate,
                    onCancelDownload = onCancelDownload
                )
            }

            when (val action = item.actionState) {
                is AppActionState.SignatureMismatch -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    ErrorActionBox(
                        message = action.message,
                        isSignatureMismatch = true,
                        onRetry = onInstallOrUpdate,
                        onUninstall = onUninstall
                    )
                }
                is AppActionState.Error -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    ErrorActionBox(
                        message = action.message,
                        isSignatureMismatch = false,
                        onRetry = onInstallOrUpdate
                    )
                }
                else -> {
                    val releaseErr = extractReleaseError(item.releaseStatus)
                    if (releaseErr != null && item.latestRelease == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ErrorActionBox(
                            message = releaseErr,
                            isSignatureMismatch = false,
                            onRetry = onRetry
                        )
                    }
                }
            }
        }

        AppContextMenu(
            expanded = menuExpanded,
            item = item,
            onDismiss = { menuExpanded = false },
            onOpen = onOpen,
            onUpdate = onInstallOrUpdate,
            onCopyInfo = onCopyInfo,
            onHide = onHide
        )
    }
}

@Composable
private fun ShelfPillActionButton(
    item: AppItemUiState,
    onOpen: () -> Unit,
    onInstallOrUpdate: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val colors = PStoreTheme.colors
    when (val action = item.actionState) {
        is AppActionState.Downloading -> {
            val pct = (action.progress * 100).toInt().coerceIn(0, 100)
            Surface(
                onClick = onCancelDownload,
                shape = RoundedCornerShape(20.dp),
                color = colors.bannerBackground,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("shelf_cancel_${item.packageName}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { action.progress },
                        color = colors.accent,
                        trackColor = colors.divider,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent
                    )
                }
            }
        }
        is AppActionState.CheckingUpdate,
        is AppActionState.Verifying,
        is AppActionState.Installing -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp)
            ) {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        else -> {
            when (item.installStatus) {
                AppInstallStatus.UP_TO_DATE -> {
                    Button(
                        onClick = onOpen,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.neutralPillBg,
                            contentColor = colors.primaryText
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("shelf_open_${item.packageName}")
                    ) {
                        Text(
                            text = stringResource(R.string.action_open),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                AppInstallStatus.UPDATE_AVAILABLE -> {
                    Button(
                        onClick = onInstallOrUpdate,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        ),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("shelf_update_${item.packageName}")
                    ) {
                        Text(
                            text = stringResource(R.string.action_update),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                AppInstallStatus.NOT_INSTALLED -> {
                    Button(
                        onClick = onInstallOrUpdate,
                        enabled = item.latestRelease != null,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        ),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("shelf_install_${item.packageName}")
                    ) {
                        Text(
                            text = stringResource(R.string.action_install),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun buildShelfStatusText(item: AppItemUiState): Pair<String, Boolean> {
    if (item.actionState is AppActionState.CheckingUpdate) {
        return stringResource(R.string.status_checking_update) to true
    }
    val installedVer = VersionComparator.cleanVersion(item.effectiveInstalledVersion)
    val release = item.latestRelease
    return when {
        item.releaseStatus is ReleaseStatus.Loading && release == null -> {
            stringResource(R.string.status_checking) to false
        }
        item.updateAvailable && release != null -> {
            stringResource(R.string.status_ver_available, release.displayVersion) to true
        }
        item.isInstalled -> {
            val ver = installedVer.ifBlank { release?.displayVersion ?: "" }
            val text = if (ver.isNotBlank()) {
                stringResource(R.string.status_ver_up_to_date, ver)
            } else {
                stringResource(R.string.status_up_to_date)
            }
            text to false
        }
        else -> {
            stringResource(R.string.status_not_installed) to false
        }
    }
}

@Composable
private fun ShelfUpdatesContent(
    allApps: List<AppItemUiState>,
    activeInstallItems: List<AppItemUiState>,
    pendingUpdates: List<AppItemUiState>,
    recentUpdates: List<RecentUpdateRecord>,
    lastCheckedAtMillis: Long,
    rateLimitNotice: String?,
    onInstallOrUpdate: (AppItemUiState) -> Unit,
    onCancelDownload: (AppItemUiState) -> Unit,
    onOpenDetail: (AppItemUiState) -> Unit,
    onOpenApp: (AppItemUiState) -> Unit,
    onCopyAppInfo: (AppItemUiState) -> Unit,
    onHideApp: (AppItemUiState) -> Unit,
    onUninstallApp: (AppItemUiState) -> Unit
) {
    val colors = PStoreTheme.colors
    val context = LocalContext.current
    val activeIdSet = remember(activeInstallItems) {
        activeInstallItems.map { it.id }.toSet()
    }
    val remainingPending = remember(pendingUpdates, activeIdSet) {
        pendingUpdates.filterNot { it.id in activeIdSet }
    }
    val featuredNotesApp = remember(activeInstallItems, pendingUpdates) {
        activeInstallItems.firstOrNull { !it.latestRelease?.body.isNullOrBlank() }
            ?: pendingUpdates.firstOrNull { !it.latestRelease?.body.isNullOrBlank() }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        if (!rateLimitNotice.isNullOrBlank()) {
            item(key = "updates_rate_limit") {
                RateLimitNoticeBanner(message = rateLimitNotice)
            }
        }

        // 1. Installing section (active downloads / installs)
        if (activeInstallItems.isNotEmpty()) {
            item(key = "installing_label") {
                Text(
                    text = stringResource(R.string.section_installing),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.secondaryText
                )
            }

            items(activeInstallItems, key = { "active_${it.id}" }) { item ->
                ShelfInstallingCard(
                    item = item,
                    onCancel = { onCancelDownload(item) }
                )
            }
        }

        // 2. What's new card for active / featured pending update
        if (featuredNotesApp != null && featuredNotesApp.latestRelease != null) {
            item(key = "shelf_whats_new_card") {
                val release = featuredNotesApp.latestRelease
                val bullets = remember(release.body) { Formatters.parseReleaseBullets(release.body) }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = colors.card,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.section_whats_new),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primaryText
                            )
                            Text(
                                text = "${featuredNotesApp.catalogApp.displayName} v${release.displayVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (bullets.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_release_notes),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        } else {
                            bullets.forEach { bullet ->
                                Text(
                                    text = "• $bullet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.secondaryText,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Other pending updates (including "P Store (this app)" when available)
        if (remainingPending.isNotEmpty()) {
            item(key = "pending_label") {
                Text(
                    text = stringResource(R.string.section_pending_updates),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.secondaryText
                )
            }

            item(key = "pending_card") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = colors.card,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        remainingPending.forEachIndexed { index, item ->
                            ShelfPendingUpdateRow(
                                item = item,
                                onRowClick = { onOpenDetail(item) },
                                onOpen = { onOpenApp(item) },
                                onUpdate = { onInstallOrUpdate(item) },
                                onCopyInfo = { onCopyAppInfo(item) },
                                onHide = { onHideApp(item) },
                                onUninstall = { onUninstallApp(item) }
                            )
                            if (index < remainingPending.lastIndex) {
                                HorizontalDivider(
                                    color = colors.divider,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (activeInstallItems.isEmpty()) {
            item(key = "no_pending_updates") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = colors.card,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.empty_no_updates_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.empty_no_updates_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 4. Recently updated history (persisted locally)
        item(key = "recent_label") {
            Text(
                text = stringResource(R.string.section_recently_updated),
                style = MaterialTheme.typography.titleSmall,
                color = colors.secondaryText
            )
        }

        item(key = "recent_card") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.card,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (recentUpdates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_recent_updates),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText,
                        modifier = Modifier.padding(18.dp)
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        recentUpdates.forEachIndexed { index, record ->
                            val catalogApp = allApps.firstOrNull {
                                (record.repoKey.isNotBlank() && it.id == record.repoKey) ||
                                    (record.packageName.isNotBlank() && it.packageName == record.packageName)
                            }?.catalogApp ?: if (record.packageName == AppCatalog.SELF_PACKAGE_NAME) AppCatalog.selfApp else null
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                if (catalogApp != null) {
                                    AppBrandIcon(
                                        app = catalogApp,
                                        size = 34.dp,
                                        cornerRadius = 10.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Text(
                                    text = "${record.displayName} → v${record.version}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Formatters.formatShortDate(record.updatedAtMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                            if (index < recentUpdates.lastIndex) {
                                HorizontalDivider(
                                    color = colors.divider,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Last checked footer on Updates tab
        item(key = "updates_sync_footer") {
            Text(
                text = Formatters.formatRelativeChecked(context, lastCheckedAtMillis, withSyncPrefix = false),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ShelfInstallingCard(
    item: AppItemUiState,
    onCancel: () -> Unit
) {
    val colors = PStoreTheme.colors
    val title = if (item.catalogApp.isSelf) {
        stringResource(R.string.pstore_this_app)
    } else {
        item.catalogApp.displayName
    }
    val installedVer = VersionComparator.cleanVersion(item.effectiveInstalledVersion)
    val action = item.actionState
    val targetVer = when (action) {
        is AppActionState.Downloading -> action.targetVersion
        is AppActionState.Installing -> action.targetVersion
        else -> item.latestRelease?.displayVersion ?: ""
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.card,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shelf_installing_card_${item.packageName}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppBrandIcon(
                    app = item.catalogApp,
                    size = 48.dp,
                    cornerRadius = 14.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primaryText
                    )
                    Text(
                        text = if (installedVer.isNotBlank() && targetVer.isNotBlank()) {
                            stringResource(R.string.status_update_arrow, installedVer, targetVer)
                        } else if (targetVer.isNotBlank()) {
                            "v$targetVer"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                }

                if (action is AppActionState.Downloading) {
                    Surface(
                        onClick = onCancel,
                        shape = CircleShape,
                        color = colors.neutralPillBg,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("shelf_installing_cancel_${item.packageName}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_cancel_download),
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (action) {
                is AppActionState.Downloading -> {
                    val pct = (action.progress * 100).toInt().coerceIn(0, 100)
                    LinearProgressIndicator(
                        progress = { action.progress },
                        color = colors.accent,
                        trackColor = colors.segmentedTrack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(
                                R.string.status_downloading_detail,
                                Formatters.formatFileSizeAlwaysMb(action.downloadedBytes),
                                Formatters.formatFileSizeAlwaysMb(action.totalBytes)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accent
                        )
                    }
                }
                is AppActionState.Verifying -> {
                    LinearProgressIndicator(
                        color = colors.accent,
                        trackColor = colors.segmentedTrack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.status_verifying),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                }
                is AppActionState.Installing -> {
                    LinearProgressIndicator(
                        color = colors.accent,
                        trackColor = colors.segmentedTrack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.status_installing),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent
                    )
                }
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfPendingUpdateRow(
    item: AppItemUiState,
    onRowClick: () -> Unit,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    onCopyInfo: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit
) {
    val colors = PStoreTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }
    val isNew = remember(item.catalogApp.firstDiscoveredAtMillis) { item.isNewlyDiscovered() }
    val title = if (item.catalogApp.isSelf) {
        stringResource(R.string.pstore_this_app)
    } else {
        item.catalogApp.displayName
    }
    val installedVer = VersionComparator.cleanVersion(item.effectiveInstalledVersion)
    val release = item.latestRelease
    val sizeStr = release?.let { Formatters.formatFileSize(it.apkSize) }.orEmpty()
    val isCheckingUpdate = item.actionState is AppActionState.CheckingUpdate
    val subtitle = when {
        isCheckingUpdate -> stringResource(R.string.status_checking_update)
        release != null && sizeStr.isNotBlank() -> {
            stringResource(R.string.status_update_arrow_size, installedVer, release.displayVersion, sizeStr)
        }
        release != null -> {
            stringResource(R.string.status_update_arrow, installedVer, release.displayVersion)
        }
        else -> ""
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onRowClick,
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppBrandIcon(
                    app = item.catalogApp,
                    size = 48.dp,
                    cornerRadius = 14.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isNew) {
                            NewAppBadge()
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accent
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                if (isCheckingUpdate) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp)
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onUpdate,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        ),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("pending_update_btn_${item.id}")
                    ) {
                        Text(
                            text = stringResource(R.string.action_update),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (item.actionState is AppActionState.SignatureMismatch) {
                Spacer(modifier = Modifier.height(10.dp))
                ErrorActionBox(
                    message = item.actionState.message,
                    isSignatureMismatch = true,
                    onRetry = onUpdate,
                    onUninstall = onUninstall
                )
            } else if (item.actionState is AppActionState.Error) {
                Spacer(modifier = Modifier.height(10.dp))
                ErrorActionBox(
                    message = item.actionState.message,
                    isSignatureMismatch = false,
                    onRetry = onUpdate
                )
            }
        }

        AppContextMenu(
            expanded = menuExpanded,
            item = item,
            onDismiss = { menuExpanded = false },
            onOpen = onOpen,
            onUpdate = onUpdate,
            onCopyInfo = onCopyInfo,
            onHide = onHide
        )
    }
}
