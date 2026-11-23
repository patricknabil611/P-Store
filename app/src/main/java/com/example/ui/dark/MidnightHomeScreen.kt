package com.example.ui.dark

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppActionState
import com.example.model.AppInstallStatus
import com.example.model.AppItemUiState
import com.example.model.ReleaseStatus
import com.example.ui.components.AppBrandIcon
import com.example.ui.components.AppContextMenu
import com.example.ui.components.ErrorActionBox
import com.example.ui.theme.PStoreTheme
import com.example.util.Formatters
import com.example.util.VersionComparator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidnightHomeScreen(
    apps: List<AppItemUiState>,
    isRefreshing: Boolean,
    isUpdatingAll: Boolean,
    updatingCurrent: Int,
    updatingTotal: Int,
    lastCheckedAtMillis: Long,
    onRefresh: () -> Unit,
    onOpenApp: (AppItemUiState) -> Unit,
    onInstallOrUpdate: (AppItemUiState) -> Unit,
    onCancelDownload: (AppItemUiState) -> Unit,
    onUpdateAll: () -> Unit,
    onOpenDetail: (AppItemUiState) -> Unit,
    onCopyAppInfo: (AppItemUiState) -> Unit,
    onRetryApp: (AppItemUiState) -> Unit,
    onUninstallApp: (AppItemUiState) -> Unit,
    bottomNavSpacerDp: Int = 104,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    val context = LocalContext.current

    val launcherApps = remember(apps) { apps.filter { it.catalogApp.showInLauncher } }
    val launcherRows = remember(launcherApps) { launcherApps.chunked(3) }

    // Updates list shows all launcher apps, plus P Store ONLY when it has a newer version available
    val updateListApps = remember(apps) {
        apps.filter { it.catalogApp.showInLauncher || (it.catalogApp.isSelf && it.updateAvailable) }
            .sortedByDescending { it.updateAvailable }
    }
    val readyUpdatesCount = remember(updateListApps) {
        updateListApps.count { it.updateAvailable && it.latestRelease != null }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = bottomNavSpacerDp.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Greeting + "P Store" + 44dp bordered refresh button
            item(key = "midnight_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Formatters.formatGreetingResId()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.primaryText
                        )
                    }

                    MidnightRefreshButton(
                        isRefreshing = isRefreshing,
                        onClick = onRefresh
                    )
                }
            }

            // LAUNCH section label
            item(key = "launch_header") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.section_launch),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        letterSpacing = 1.4.sp
                    ),
                    color = colors.secondaryText
                )
            }

            // 3-column grid of launch tiles (116dp tall, 22dp radius)
            items(launcherRows.size, key = { rowIndex -> "launch_row_$rowIndex" }) { rowIndex ->
                val rowApps = launcherRows[rowIndex]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowApps.forEach { item ->
                        MidnightLaunchTile(
                            item = item,
                            onTap = {
                                if (item.isInstalled) {
                                    onOpenApp(item)
                                } else {
                                    onInstallOrUpdate(item)
                                }
                            },
                            onOpen = { onOpenApp(item) },
                            onUpdate = { onInstallOrUpdate(item) },
                            onCopyInfo = { onCopyAppInfo(item) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // UPDATES · N READY + Update all link
            item(key = "updates_header") {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (readyUpdatesCount > 0) {
                            stringResource(R.string.section_updates_ready, readyUpdatesCount)
                        } else {
                            stringResource(R.string.section_updates_none)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            letterSpacing = 1.3.sp
                        ),
                        color = colors.secondaryText
                    )

                    if (isUpdatingAll) {
                        Text(
                            text = stringResource(R.string.updating_progress, updatingCurrent, updatingTotal),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accent
                        )
                    } else if (readyUpdatesCount > 0) {
                        TextButton(
                            onClick = onUpdateAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("midnight_update_all_button")
                        ) {
                            Text(
                                text = stringResource(R.string.update_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accent
                            )
                        }
                    }
                }
            }

            // App update cards
            items(updateListApps, key = { it.packageName }) { item ->
                MidnightUpdateCard(
                    item = item,
                    onClick = { onOpenDetail(item) },
                    onOpen = { onOpenApp(item) },
                    onInstallOrUpdate = { onInstallOrUpdate(item) },
                    onCancelDownload = { onCancelDownload(item) },
                    onCopyInfo = { onCopyAppInfo(item) },
                    onRetry = { onRetryApp(item) },
                    onUninstall = { onUninstallApp(item) }
                )
            }

            // Last checked footer
            item(key = "midnight_footer") {
                Text(
                    text = Formatters.formatRelativeChecked(context, lastCheckedAtMillis, withSyncPrefix = false),
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
}

@Composable
fun MidnightRefreshButton(
    isRefreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    val rotation = if (isRefreshing) {
        val transition = rememberInfiniteTransition(label = "midnight_refresh_spin")
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
        enabled = !isRefreshing,
        shape = RoundedCornerShape(14.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier
            .size(44.dp)
            .testTag("midnight_refresh_button")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.cd_refresh),
                tint = if (isRefreshing) colors.accent else colors.primaryText,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MidnightLaunchTile(
    item: AppItemUiState,
    onTap: () -> Unit,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    onCopyInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(item.catalogApp.darkTileTint),
            border = BorderStroke(
                1.dp,
                Color(item.catalogApp.brandColorDark).copy(alpha = if (item.isInstalled) 0.28f else 0.14f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .clip(RoundedCornerShape(22.dp))
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = { menuExpanded = true }
                )
                .alpha(if (item.isInstalled) 1f else 0.62f)
                .testTag("launch_tile_${item.packageName}")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                AppBrandIcon(
                    app = item.catalogApp,
                    size = 52.dp,
                    cornerRadius = 15.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.catalogApp.displayName,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isInstalled) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.action_install),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent
                    )
                }
            }
        }

        AppContextMenu(
            expanded = menuExpanded,
            item = item,
            onDismiss = { menuExpanded = false },
            onOpen = onOpen,
            onUpdate = onUpdate,
            onCopyInfo = onCopyInfo
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MidnightUpdateCard(
    item: AppItemUiState,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onInstallOrUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onCopyInfo: () -> Unit,
    onRetry: () -> Unit,
    onUninstall: () -> Unit,
    showReleaseNotesPreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }
    val title = if (item.catalogApp.isSelf) {
        stringResource(R.string.pstore_this_app)
    } else {
        item.catalogApp.displayName
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
                .testTag("midnight_card_${item.packageName}")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppBrandIcon(
                        app = item.catalogApp,
                        size = 44.dp,
                        cornerRadius = 13.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = buildMidnightStatusLine(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.updateAvailable) colors.accent else colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    MidnightCardActionSlot(
                        item = item,
                        onInstallOrUpdate = onInstallOrUpdate,
                        onCancelDownload = onCancelDownload
                    )
                }

                if (showReleaseNotesPreview && !item.latestRelease?.body.isNullOrBlank()) {
                    val bullets = remember(item.latestRelease?.body) {
                        Formatters.parseReleaseBullets(item.latestRelease?.body.orEmpty()).take(2)
                    }
                    if (bullets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        bullets.forEach { bullet ->
                            Text(
                                text = "• $bullet",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Error feedback inline
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
        }

        AppContextMenu(
            expanded = menuExpanded,
            item = item,
            onDismiss = { menuExpanded = false },
            onOpen = onOpen,
            onUpdate = onInstallOrUpdate,
            onCopyInfo = onCopyInfo
        )
    }
}

@Composable
private fun MidnightCardActionSlot(
    item: AppItemUiState,
    onInstallOrUpdate: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val colors = PStoreTheme.colors
    when (val action = item.actionState) {
        is AppActionState.Downloading -> {
            val pct = (action.progress * 100).toInt().coerceIn(0, 100)
            Surface(
                onClick = onCancelDownload,
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("cancel_ring_${item.packageName}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { action.progress },
                        color = colors.accent,
                        trackColor = colors.cardBorder,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        is AppActionState.Verifying,
        is AppActionState.Installing -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp)
            ) {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        is AppActionState.InstalledSuccess -> {
            Text(
                text = stringResource(R.string.status_installed_success),
                style = MaterialTheme.typography.labelLarge,
                color = colors.success,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        else -> {
            when (item.installStatus) {
                AppInstallStatus.UPDATE_AVAILABLE -> {
                    Button(
                        onClick = onInstallOrUpdate,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        ),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("update_btn_${item.packageName}")
                    ) {
                        Text(
                            text = stringResource(R.string.action_update),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                AppInstallStatus.UP_TO_DATE -> {
                    Text(
                        text = stringResource(R.string.status_latest_check_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.success,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
                AppInstallStatus.NOT_INSTALLED -> {
                    Button(
                        onClick = onInstallOrUpdate,
                        enabled = item.latestRelease != null,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        ),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("install_btn_${item.packageName}")
                    ) {
                        Text(
                            text = stringResource(R.string.action_install),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun buildMidnightStatusLine(item: AppItemUiState): String {
    val action = item.actionState
    if (action is AppActionState.Downloading) {
        val dl = Formatters.formatFileSizeAlwaysMb(action.downloadedBytes)
        val total = Formatters.formatFileSizeAlwaysMb(action.totalBytes)
        return stringResource(R.string.status_downloading_detail, dl, total)
    }
    if (action is AppActionState.Verifying) return stringResource(R.string.status_verifying)
    if (action is AppActionState.Installing) return stringResource(R.string.status_installing)

    val release = item.latestRelease
    val installedVer = VersionComparator.cleanVersion(item.installedVersionName)
    return when {
        item.releaseStatus is ReleaseStatus.Loading && release == null -> {
            stringResource(R.string.status_checking)
        }
        item.updateAvailable && release != null -> {
            val sizeStr = Formatters.formatFileSize(release.apkSize)
            if (sizeStr.isNotBlank()) {
                stringResource(R.string.status_update_arrow_size, installedVer, release.displayVersion, sizeStr)
            } else {
                stringResource(R.string.status_update_arrow, installedVer, release.displayVersion)
            }
        }
        item.isInstalled -> {
            val ver = installedVer.ifBlank { release?.displayVersion ?: "" }
            if (ver.isNotBlank()) {
                stringResource(R.string.status_up_to_date_ver, ver)
            } else {
                stringResource(R.string.status_up_to_date)
            }
        }
        release != null -> {
            val sizeStr = Formatters.formatFileSize(release.apkSize)
            if (sizeStr.isNotBlank()) {
                stringResource(R.string.status_not_installed_ver_size, release.displayVersion, sizeStr)
            } else {
                stringResource(R.string.status_not_installed_ver, release.displayVersion)
            }
        }
        else -> stringResource(R.string.status_not_installed)
    }
}

fun extractReleaseError(status: ReleaseStatus): String? = when (status) {
    is ReleaseStatus.NoInternet -> status.message
    is ReleaseStatus.RateLimit -> status.message
    is ReleaseStatus.NoApkAsset -> status.message
    is ReleaseStatus.RepoNotFound -> status.message
    is ReleaseStatus.Error -> status.message
    else -> null
}
