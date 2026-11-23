package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppActionState
import com.example.model.AppInstallStatus
import com.example.model.AppItemUiState
import com.example.model.AutoUpdateMode
import com.example.model.ReleaseInfo
import com.example.ui.components.AppBrandIcon
import com.example.ui.components.ErrorActionBox
import com.example.ui.components.OlderVersionsDialog
import com.example.ui.dark.extractReleaseError
import com.example.ui.theme.PStoreTheme
import com.example.util.Formatters
import com.example.util.VersionComparator

@Composable
fun AppDetailScreen(
    item: AppItemUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onInstallOrUpdate: (ReleaseInfo?) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenApp: () -> Unit,
    onUninstallApp: () -> Unit,
    onRetryRelease: () -> Unit,
    onOpenGitHub: () -> Unit,
    onCycleAutoUpdate: () -> Unit,
    onLoadOlderReleases: () -> Unit,
    onCopyAppInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val colors = PStoreTheme.colors
    var showOlderVersions by remember { mutableStateOf(false) }

    if (showOlderVersions) {
        OlderVersionsDialog(
            item = item,
            onInstallRelease = { release -> onInstallOrUpdate(release) },
            onRetry = onLoadOlderReleases,
            onDismiss = { showOlderVersions = false }
        )
    }

    val cardRadius = if (colors.isDark) 20.dp else 22.dp
    val cardBorder = if (colors.isDark) BorderStroke(1.dp, colors.cardBorder) else null
    val cardElevation = if (colors.isDark) 0.dp else 2.dp
    val buttonRadius = if (colors.isDark) 16.dp else 20.dp

    val displayName = if (item.catalogApp.isSelf) {
        stringResource(R.string.pstore_this_app)
    } else {
        item.catalogApp.displayName
    }
    val installedVer = VersionComparator.cleanVersion(item.installedVersionName)
    val latestVer = item.latestRelease?.displayVersion.orEmpty()
    val sizeStr = item.latestRelease?.let { Formatters.formatFileSize(it.apkSize) }.orEmpty()

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Back button (44dp)
            item(key = "detail_back") {
                Surface(
                    onClick = onNavigateBack,
                    shape = if (colors.isDark) RoundedCornerShape(14.dp) else CircleShape,
                    color = colors.card,
                    border = if (colors.isDark) BorderStroke(1.dp, colors.cardBorder) else null,
                    shadowElevation = if (colors.isDark) 0.dp else 2.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("detail_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = colors.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Hero header: 84dp icon + 26sp app name + "Installed vX · Latest vY"
            item(key = "detail_hero") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    AppBrandIcon(
                        app = item.catalogApp,
                        size = 84.dp,
                        cornerRadius = 24.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val versionSummary = when {
                        item.isInstalled && latestVer.isNotBlank() -> {
                            stringResource(R.string.status_installed_latest, installedVer, latestVer)
                        }
                        item.isInstalled && installedVer.isNotBlank() -> {
                            "Installed v$installedVer"
                        }
                        latestVer.isNotBlank() -> {
                            stringResource(R.string.status_not_installed_latest, latestVer)
                        }
                        else -> stringResource(R.string.status_not_installed)
                    }
                    Text(
                        text = versionSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText
                    )
                }
            }

            // Primary action row OR active download progress / error
            item(key = "detail_actions") {
                when (val action = item.actionState) {
                    is AppActionState.Downloading -> {
                        val pct = (action.progress * 100).toInt().coerceIn(0, 100)
                        Surface(
                            shape = RoundedCornerShape(cardRadius),
                            color = colors.card,
                            border = cardBorder,
                            shadowElevation = cardElevation,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.status_downloading_detail,
                                            Formatters.formatFileSizeAlwaysMb(action.downloadedBytes),
                                            Formatters.formatFileSizeAlwaysMb(action.totalBytes)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.primaryText
                                    )
                                    Surface(
                                        onClick = onCancelDownload,
                                        shape = CircleShape,
                                        color = colors.neutralPillBg,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("detail_cancel_download")
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
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { action.progress },
                                    color = colors.accent,
                                    trackColor = colors.segmentedTrack,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.accent
                                )
                            }
                        }
                    }

                    is AppActionState.Verifying,
                    is AppActionState.Installing -> {
                        Surface(
                            shape = RoundedCornerShape(cardRadius),
                            color = colors.card,
                            border = cardBorder,
                            shadowElevation = cardElevation,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
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
                                    text = if (action is AppActionState.Verifying) {
                                        stringResource(R.string.status_verifying)
                                    } else {
                                        stringResource(R.string.status_installing)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.accent
                                )
                            }
                        }
                    }

                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val primaryLabel = when (item.installStatus) {
                                    AppInstallStatus.UPDATE_AVAILABLE -> {
                                        if (sizeStr.isNotBlank()) {
                                            stringResource(R.string.action_update_size, sizeStr)
                                        } else {
                                            stringResource(R.string.action_update)
                                        }
                                    }
                                    AppInstallStatus.NOT_INSTALLED -> {
                                        if (sizeStr.isNotBlank()) {
                                            stringResource(R.string.action_install_size, sizeStr)
                                        } else {
                                            stringResource(R.string.action_install)
                                        }
                                    }
                                    AppInstallStatus.UP_TO_DATE -> stringResource(R.string.status_up_to_date)
                                }
                                val primaryEnabled = when (item.installStatus) {
                                    AppInstallStatus.UPDATE_AVAILABLE -> item.latestRelease != null
                                    AppInstallStatus.NOT_INSTALLED -> item.latestRelease != null
                                    AppInstallStatus.UP_TO_DATE -> false
                                }

                                Button(
                                    onClick = { onInstallOrUpdate(null) },
                                    enabled = primaryEnabled,
                                    shape = RoundedCornerShape(buttonRadius),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent,
                                        disabledContainerColor = colors.neutralPillBg,
                                        disabledContentColor = colors.secondaryText
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 50.dp)
                                        .testTag("detail_primary_action_button")
                                ) {
                                    Text(
                                        text = primaryLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }

                                if (item.isInstalled && !item.catalogApp.isSelf) {
                                    OutlinedButton(
                                        onClick = onOpenApp,
                                        shape = RoundedCornerShape(buttonRadius),
                                        border = BorderStroke(1.dp, colors.cardBorder),
                                        modifier = Modifier
                                            .heightIn(min = 50.dp)
                                            .testTag("detail_open_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_open),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.primaryText
                                        )
                                    }
                                }
                            }

                            if (action is AppActionState.SignatureMismatch) {
                                ErrorActionBox(
                                    message = action.message,
                                    isSignatureMismatch = true,
                                    onRetry = { onInstallOrUpdate(null) },
                                    onUninstall = onUninstallApp
                                )
                            } else if (action is AppActionState.Error) {
                                ErrorActionBox(
                                    message = action.message,
                                    isSignatureMismatch = false,
                                    onRetry = { onInstallOrUpdate(null) }
                                )
                            } else {
                                val releaseErr = extractReleaseError(item.releaseStatus)
                                if (releaseErr != null && item.latestRelease == null) {
                                    ErrorActionBox(
                                        message = releaseErr,
                                        isSignatureMismatch = false,
                                        onRetry = onRetryRelease
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // "What's new in vX" card
            item(key = "detail_whats_new") {
                val release = item.latestRelease
                val bullets = remember(release?.body) {
                    Formatters.parseReleaseBullets(release?.body.orEmpty())
                }
                Surface(
                    shape = RoundedCornerShape(cardRadius),
                    color = colors.card,
                    border = cardBorder,
                    shadowElevation = cardElevation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (latestVer.isNotBlank()) {
                                    stringResource(R.string.section_whats_new_in, latestVer)
                                } else {
                                    stringResource(R.string.section_whats_new)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primaryText,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.from_github_release),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (bullets.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_release_notes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryText
                            )
                        } else {
                            bullets.forEach { bullet ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.accent,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = bullet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.secondaryText,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Info card with divider-separated rows + "Copy app info" button
            item(key = "detail_info_card") {
                Surface(
                    shape = RoundedCornerShape(cardRadius),
                    color = colors.card,
                    border = cardBorder,
                    shadowElevation = cardElevation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DetailInfoRow(
                            label = stringResource(R.string.info_package_name),
                            value = item.packageName,
                            onClick = null
                        )
                        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 18.dp))
                        DetailInfoRow(
                            label = stringResource(R.string.info_source),
                            value = item.catalogApp.fullRepoName,
                            valueAccent = true,
                            onClick = onOpenGitHub
                        )
                        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 18.dp))
                        val autoUpdateText = when (item.autoUpdateMode) {
                            AutoUpdateMode.WIFI_ONLY -> stringResource(R.string.auto_update_wifi)
                            AutoUpdateMode.ALWAYS -> stringResource(R.string.auto_update_always)
                            AutoUpdateMode.OFF -> stringResource(R.string.auto_update_off)
                        }
                        DetailInfoRow(
                            label = stringResource(R.string.info_auto_update),
                            value = autoUpdateText,
                            valueAccent = true,
                            onClick = onCycleAutoUpdate,
                            testTag = "detail_auto_update_row"
                        )
                        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 18.dp))
                        DetailInfoRow(
                            label = stringResource(R.string.info_older_versions),
                            value = stringResource(R.string.action_see_all),
                            valueAccent = true,
                            showChevron = true,
                            onClick = {
                                onLoadOlderReleases()
                                showOlderVersions = true
                            },
                            testTag = "detail_older_versions_row"
                        )
                        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 18.dp))

                        // Copy app info button inside info card
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            OutlinedButton(
                                onClick = onCopyAppInfo,
                                shape = RoundedCornerShape(buttonRadius),
                                border = BorderStroke(1.dp, colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .testTag("detail_copy_app_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy),
                                    tint = colors.primaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_copy_app_info),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.primaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    valueAccent: Boolean = false,
    showChevron: Boolean = false,
    onClick: (() -> Unit)?,
    testTag: String? = null
) {
    val colors = PStoreTheme.colors
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .let { if (testTag != null) it.testTag(testTag) else it }
        .padding(horizontal = 18.dp, vertical = 14.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = rowModifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (valueAccent) colors.accent else colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showChevron) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
