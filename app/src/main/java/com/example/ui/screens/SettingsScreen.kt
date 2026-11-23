package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.example.R
import com.example.catalog.AppCatalog
import com.example.model.AppItemUiState
import com.example.model.CheckIntervalHours
import com.example.model.NetworkPreference
import com.example.model.SettingsUiState
import com.example.model.ThemeMode
import com.example.ui.components.AppBrandIcon
import com.example.ui.theme.PStoreTheme

@Composable
fun SettingsScreen(
    settings: SettingsUiState,
    selfAppState: AppItemUiState?,
    snackbarHostState: SnackbarHostState?,
    showBackButton: Boolean,
    onNavigateBack: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    onSelectInterval: (CheckIntervalHours) -> Unit,
    onSelectDownloadOver: (NetworkPreference) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSaveGitHubToken: (String) -> Unit,
    onClearGitHubToken: () -> Unit,
    onCopyAllAppsInfo: () -> Unit,
    onClearCache: () -> Unit,
    onCheckPStoreUpdate: () -> Unit,
    onInstallPStoreUpdate: (AppItemUiState) -> Unit,
    bottomPaddingDp: Int = 32,
    modifier: Modifier = Modifier
) {
    if (showBackButton) {
        BackHandler { onNavigateBack() }
    }

    val colors = PStoreTheme.colors
    val context = LocalContext.current
    var tokenInput by rememberSaveable { mutableStateOf("") }

    val cardRadius = if (colors.isDark) 20.dp else 22.dp
    val cardBorder = if (colors.isDark) BorderStroke(1.dp, colors.cardBorder) else null
    val cardElevation = if (colors.isDark) 0.dp else 2.dp
    val buttonRadius = if (colors.isDark) 14.dp else 20.dp

    val (myVersionName, myVersionCode) = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            (info.versionName ?: "1.0") to PackageInfoCompat.getLongVersionCode(info)
        } catch (_: Exception) {
            "1.0" to 1L
        }
    }

    val contentBody: @Composable (Modifier) -> Unit = { contentModifier ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 14.dp,
                bottom = bottomPaddingDp.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = contentModifier.fillMaxSize()
        ) {
            // Header
            item(key = "settings_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (showBackButton) {
                        Surface(
                            onClick = onNavigateBack,
                            shape = if (colors.isDark) RoundedCornerShape(14.dp) else CircleShape,
                            color = colors.card,
                            border = cardBorder,
                            shadowElevation = cardElevation,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("settings_back_button")
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
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primaryText
                    )
                }
            }

            // 1. Appearance: Theme (System / Light / Dark)
            item(key = "settings_appearance") {
                SettingsSectionHeader(text = stringResource(R.string.settings_section_appearance))
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(cardRadius),
                    color = colors.card,
                    border = cardBorder,
                    shadowElevation = cardElevation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_theme_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_theme_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OptionPill(
                                label = stringResource(R.string.theme_system),
                                selected = settings.themeMode == ThemeMode.SYSTEM,
                                onClick = { onSelectTheme(ThemeMode.SYSTEM) },
                                testTag = "theme_system",
                                modifier = Modifier.weight(1f)
                            )
                            OptionPill(
                                label = stringResource(R.string.theme_light),
                                selected = settings.themeMode == ThemeMode.LIGHT,
                                onClick = { onSelectTheme(ThemeMode.LIGHT) },
                                testTag = "theme_light",
                                modifier = Modifier.weight(1f)
                            )
                            OptionPill(
                                label = stringResource(R.string.theme_dark),
                                selected = settings.themeMode == ThemeMode.DARK,
                                onClick = { onSelectTheme(ThemeMode.DARK) },
                                testTag = "theme_dark",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Updates & Network
            item(key = "settings_updates") {
                SettingsSectionHeader(text = stringResource(R.string.settings_section_updates))
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(cardRadius),
                    color = colors.card,
                    border = cardBorder,
                    shadowElevation = cardElevation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Auto-check toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_auto_check),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = stringResource(R.string.settings_auto_check_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = settings.autoCheckEnabled,
                                onCheckedChange = onToggleAutoCheck,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onAccent,
                                    checkedTrackColor = colors.accent
                                ),
                                modifier = Modifier.testTag("toggle_auto_check")
                            )
                        }

                        if (settings.autoCheckEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.settings_check_interval),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.secondaryText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OptionPill(
                                    label = stringResource(R.string.interval_6h),
                                    selected = settings.checkInterval == CheckIntervalHours.H6,
                                    onClick = { onSelectInterval(CheckIntervalHours.H6) },
                                    testTag = "interval_6h",
                                    modifier = Modifier.weight(1f)
                                )
                                OptionPill(
                                    label = stringResource(R.string.interval_12h),
                                    selected = settings.checkInterval == CheckIntervalHours.H12,
                                    onClick = { onSelectInterval(CheckIntervalHours.H12) },
                                    testTag = "interval_12h",
                                    modifier = Modifier.weight(1f)
                                )
                                OptionPill(
                                    label = stringResource(R.string.interval_24h),
                                    selected = settings.checkInterval == CheckIntervalHours.H24,
                                    onClick = { onSelectInterval(CheckIntervalHours.H24) },
                                    testTag = "interval_24h",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = colors.divider)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Download over: Wi-Fi only / Any network
                        Text(
                            text = stringResource(R.string.settings_download_over),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OptionPill(
                                label = stringResource(R.string.network_wifi_only),
                                selected = settings.downloadOver == NetworkPreference.WIFI_ONLY,
                                onClick = { onSelectDownloadOver(NetworkPreference.WIFI_ONLY) },
                                testTag = "network_wifi_only",
                                modifier = Modifier.weight(1f)
                            )
                            OptionPill(
                                label = stringResource(R.string.network_any),
                                selected = settings.downloadOver == NetworkPreference.ANY_NETWORK,
                                onClick = { onSelectDownloadOver(NetworkPreference.ANY_NETWORK) },
                                testTag = "network_any",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = colors.divider)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Notifications toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_notifications),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = stringResource(R.string.settings_notifications_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = settings.notificationsEnabled,
                                onCheckedChange = onToggleNotifications,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onAccent,
                                    checkedTrackColor = colors.accent
                                ),
                                modifier = Modifier.testTag("toggle_notifications")
                            )
                        }
                    }
                }
            }

            // 3. Optional GitHub Token (stored encrypted)
            item(key = "settings_github_token") {
                SettingsSectionHeader(text = stringResource(R.string.settings_section_github))
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(cardRadius),
                    color = colors.card,
                    border = cardBorder,
                    shadowElevation = cardElevation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_github_token_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primaryText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_github_token_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        if (settings.hasGitHubToken) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.settings_github_token_saved),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.success
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.settings_github_token_placeholder),
                                    color = colors.secondaryText
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_token_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    onSaveGitHubToken(tokenInput)
                                    tokenInput = ""
                                },
                                enabled = tokenInput.isNotBlank(),
                                shape = RoundedCornerShape(buttonRadius),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("save_github_token_button")
                            ) {
                                Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold)
                            }
                            if (settings.hasGitHubToken) {
                                OutlinedButton(
                                    onClick = {
                                        tokenInput = ""
                                        onClearGitHubToken()
                                    },
                                    shape = RoundedCornerShape(buttonRadius),
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .testTag("clear_github_token_button")
                                ) {
                                    Text(stringResource(R.string.action_clear), color = colors.primaryText)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Copy all apps info & Download Cache
            item(key = "settings_tools") {
                SettingsSectionHeader(text = stringResource(R.string.settings_section_tools))
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(cardRadius),
                    color = colors.card,
                    border = cardBorder,
                    shadowElevation = cardElevation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.action_copy_all_apps_info),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_copy_all_desc, AppCatalog.apps.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onCopyAllAppsInfo,
                            shape = RoundedCornerShape(buttonRadius),
                            border = BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                                .testTag("copy_all_apps_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy),
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_copy_all_apps_info),
                                color = colors.primaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = colors.divider)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CleaningServices,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_cache_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_cache_size, settings.cacheSizeFormatted),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onClearCache,
                                shape = RoundedCornerShape(buttonRadius),
                                border = BorderStroke(1.dp, colors.cardBorder),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("clear_cache_button")
                            ) {
                                Text(stringResource(R.string.action_clear), color = colors.primaryText)
                            }
                        }
                    }
                }
            }

            // 5. About P Store & Check for P Store update
            item(key = "settings_about") {
                SettingsSectionHeader(text = stringResource(R.string.settings_section_about))
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(cardRadius),
                    color = colors.card,
                    border = cardBorder,
                    shadowElevation = cardElevation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val selfCatalog = selfAppState?.catalogApp
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selfCatalog != null) {
                                AppBrandIcon(
                                    app = selfCatalog,
                                    size = 48.dp,
                                    cornerRadius = 14.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = stringResource(R.string.settings_version_line, myVersionName, myVersionCode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                                Text(
                                    text = context.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (selfAppState != null && selfAppState.updateAvailable && selfAppState.latestRelease != null) {
                            Button(
                                onClick = { onInstallPStoreUpdate(selfAppState) },
                                shape = RoundedCornerShape(buttonRadius),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .testTag("install_pstore_update_button")
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_pstore_update_ready,
                                        selfAppState.latestRelease.displayVersion
                                    ),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedButton(
                            onClick = onCheckPStoreUpdate,
                            shape = RoundedCornerShape(buttonRadius),
                            border = BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                                .testTag("check_pstore_update_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_check_pstore_update),
                                color = colors.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (snackbarHostState != null) {
        Scaffold(
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            contentBody(
                Modifier
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }
    } else {
        contentBody(modifier)
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    val colors = PStoreTheme.colors
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = colors.secondaryText,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun OptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.accent else colors.neutralPillBg,
        modifier = modifier
            .heightIn(min = 44.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) colors.onAccent else colors.primaryText,
                maxLines = 1
            )
        }
    }
}
