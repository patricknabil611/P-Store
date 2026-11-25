package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ThemeMode
import com.example.ui.components.AddAppDialog
import com.example.ui.components.HideAppConfirmDialog
import com.example.ui.components.UnknownAppsPermissionDialog
import com.example.ui.dark.MidnightDockShell
import com.example.ui.dark.MidnightHomeScreen
import com.example.ui.dark.MidnightStoreScreen
import com.example.ui.dark.MidnightTab
import com.example.ui.light.CleanShelfScreen
import com.example.ui.light.ShelfTab
import com.example.ui.screens.AppDetailScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.UiMessage
import com.example.worker.UpdateCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val _pendingOpenAppKey = MutableStateFlow<String?>(null)
    private val pendingOpenAppKey: StateFlow<String?> = _pendingOpenAppKey.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractNotificationDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            val deepLinkKey by pendingOpenAppKey.collectAsStateWithLifecycle()
            PStoreRoot(
                deepLinkAppKey = deepLinkKey,
                onConsumeDeepLink = { _pendingOpenAppKey.value = null },
                onUpdateSystemBars = { isDark ->
                    enableEdgeToEdge(
                        statusBarStyle = if (isDark) {
                            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                            )
                        },
                        navigationBarStyle = if (isDark) {
                            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                            )
                        }
                    )
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNotificationDeepLink(intent)
    }

    private fun extractNotificationDeepLink(intent: Intent?) {
        val key = intent?.getStringExtra(UpdateCheckWorker.EXTRA_OPEN_APP_KEY)
        if (!key.isNullOrBlank()) {
            _pendingOpenAppKey.value = key.lowercase()
            intent.removeExtra(UpdateCheckWorker.EXTRA_OPEN_APP_KEY)
        }
    }
}

@Composable
fun PStoreRoot(
    deepLinkAppKey: String? = null,
    onConsumeDeepLink: () -> Unit = {},
    onUpdateSystemBars: (Boolean) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val hiddenApps by viewModel.hiddenApps.collectAsStateWithLifecycle()
    val rateLimitNotice by viewModel.rateLimitNotice.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recentUpdates by viewModel.recentUpdates.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val storeFilter by viewModel.storeFilter.collectAsStateWithLifecycle()
    val isUpdatingAll by viewModel.isUpdatingAll.collectAsStateWithLifecycle()
    val updatingCurrent by viewModel.updatingAllCurrentIndex.collectAsStateWithLifecycle()
    val updatingTotal by viewModel.updatingAllTotal.collectAsStateWithLifecycle()
    val showPermissionDialog by viewModel.showUnknownAppsPermissionDialog.collectAsStateWithLifecycle()

    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    LaunchedEffect(isDarkTheme) {
        onUpdateSystemBars(isDarkTheme)
    }

    // Request POST_NOTIFICATIONS on Android 13+ when notifications are enabled
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* handled gracefully */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Resume pending install after returning from Android's "Install unknown apps" settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.resumeAfterPermissionCheck()
                viewModel.refreshInstalledStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.uiMessages.collectLatest { msg ->
            when (msg) {
                is UiMessage.Snackbar -> snackbarHostState.showSnackbar(msg.message)
            }
        }
    }

    // Navigation state shared across theme switches (keyed by stable app id / repoKey)
    var selectedDetailAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var midnightTab by rememberSaveable { mutableStateOf(MidnightTab.HOME) }
    var shelfTab by rememberSaveable { mutableStateOf(ShelfTab.MY_APPS) }
    var shelfSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var showAddAppDialog by rememberSaveable { mutableStateOf(false) }
    var pendingHideAppId by rememberSaveable { mutableStateOf<String?>(null) }

    // Open target app detail page when launched from a "New app available" notification
    LaunchedEffect(deepLinkAppKey, apps) {
        if (!deepLinkAppKey.isNullOrBlank()) {
            val target = apps.firstOrNull {
                it.id.equals(deepLinkAppKey, ignoreCase = true) ||
                    (it.packageName.isNotBlank() && it.packageName.equals(deepLinkAppKey, ignoreCase = true))
            }
            if (target != null) {
                selectedDetailAppId = target.id
                shelfSettingsOpen = false
                onConsumeDeepLink()
            }
        }
    }

    val selfAppState = remember(apps) { apps.firstOrNull { it.catalogApp.isSelf } }
    val updateCount = remember(apps) { apps.count { it.updateAvailable && it.latestRelease != null } }

    MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (showPermissionDialog) {
                UnknownAppsPermissionDialog(
                    onOpenSettings = { viewModel.openUnknownAppsSettings() },
                    onDismiss = { viewModel.dismissPermissionDialog() }
                )
            }

            if (showAddAppDialog) {
                AddAppDialog(
                    onAddApp = { name, pkg, repo, color, icon ->
                        viewModel.addApp(name, pkg, repo, color, icon)
                    },
                    onDismiss = { showAddAppDialog = false }
                )
            }

            val pendingHideItem = pendingHideAppId?.let { id ->
                apps.firstOrNull { it.id == id }
            }
            if (pendingHideItem != null) {
                HideAppConfirmDialog(
                    item = pendingHideItem,
                    onConfirmHide = {
                        if (selectedDetailAppId == pendingHideItem.id) {
                            selectedDetailAppId = null
                        }
                        viewModel.hideApp(pendingHideItem)
                    },
                    onDismiss = { pendingHideAppId = null }
                )
            }

            val detailItem = selectedDetailAppId?.let { id ->
                apps.firstOrNull { it.id == id }
            }

            when {
                detailItem != null -> {
                    AppDetailScreen(
                        item = detailItem,
                        snackbarHostState = snackbarHostState,
                        onNavigateBack = { selectedDetailAppId = null },
                        onInstallOrUpdate = { specificRelease ->
                            viewModel.onInstallOrUpdateClicked(detailItem, specificRelease)
                        },
                        onCancelDownload = { viewModel.cancelDownload(detailItem) },
                        onOpenApp = { viewModel.openApp(detailItem) },
                        onUninstallApp = { viewModel.uninstallApp(detailItem) },
                        onRetryRelease = { viewModel.refreshSingleApp(detailItem.id) },
                        onOpenGitHub = { viewModel.openGitHubReleases(detailItem) },
                        onCycleAutoUpdate = { viewModel.cyclePerAppAutoUpdate(detailItem) },
                        onLoadOlderReleases = { viewModel.loadOlderReleases(detailItem) },
                        onCopyAppInfo = { viewModel.copyAppInfo(detailItem.catalogApp) },
                        onHideApp = { pendingHideAppId = detailItem.id }
                    )
                }

                // DARK MODE — "Midnight Dock" (Launcher-first, floating bottom nav)
                isDarkTheme -> {
                    MidnightDockShell(
                        selectedTab = midnightTab,
                        updateCount = updateCount,
                        snackbarHostState = snackbarHostState,
                        onSelectTab = { midnightTab = it }
                    ) {
                        when (midnightTab) {
                            MidnightTab.HOME -> {
                                MidnightHomeScreen(
                                    apps = apps,
                                    isRefreshing = isRefreshing,
                                    isUpdatingAll = isUpdatingAll,
                                    updatingCurrent = updatingCurrent,
                                    updatingTotal = updatingTotal,
                                    lastCheckedAtMillis = settings.lastCheckedAtMillis,
                                    rateLimitNotice = rateLimitNotice,
                                    onRefresh = { viewModel.refreshReleases() },
                                    onAddApp = { showAddAppDialog = true },
                                    onOpenApp = { viewModel.openApp(it) },
                                    onInstallOrUpdate = { viewModel.onInstallOrUpdateClicked(it) },
                                    onCancelDownload = { viewModel.cancelDownload(it) },
                                    onUpdateAll = { viewModel.updateAllApps() },
                                    onOpenDetail = { selectedDetailAppId = it.id },
                                    onCopyAppInfo = { viewModel.copyAppInfo(it.catalogApp) },
                                    onHideApp = { pendingHideAppId = it.id },
                                    onRetryApp = { viewModel.refreshSingleApp(it.id) },
                                    onUninstallApp = { viewModel.uninstallApp(it) }
                                )
                            }

                            MidnightTab.STORE -> {
                                MidnightStoreScreen(
                                    apps = apps,
                                    selectedFilter = storeFilter,
                                    isRefreshing = isRefreshing,
                                    lastCheckedAtMillis = settings.lastCheckedAtMillis,
                                    rateLimitNotice = rateLimitNotice,
                                    onSelectFilter = { viewModel.setStoreFilter(it) },
                                    onRefresh = { viewModel.refreshReleases() },
                                    onAddApp = { showAddAppDialog = true },
                                    onOpenDetail = { selectedDetailAppId = it.id },
                                    onOpenApp = { viewModel.openApp(it) },
                                    onInstallOrUpdate = { viewModel.onInstallOrUpdateClicked(it) },
                                    onCancelDownload = { viewModel.cancelDownload(it) },
                                    onCopyAppInfo = { viewModel.copyAppInfo(it.catalogApp) },
                                    onHideApp = { pendingHideAppId = it.id },
                                    onRetryApp = { viewModel.refreshSingleApp(it.id) },
                                    onUninstallApp = { viewModel.uninstallApp(it) }
                                )
                            }

                            MidnightTab.SETTINGS -> {
                                SettingsScreen(
                                    settings = settings,
                                    selfAppState = selfAppState,
                                    hiddenApps = hiddenApps,
                                    totalAppsCount = apps.size,
                                    snackbarHostState = null,
                                    showBackButton = false,
                                    onNavigateBack = { midnightTab = MidnightTab.HOME },
                                    onSelectTheme = { viewModel.setThemeMode(it) },
                                    onToggleAutoCheck = { viewModel.setAutoCheckEnabled(it) },
                                    onSelectInterval = { viewModel.setCheckInterval(it) },
                                    onSelectDownloadOver = { viewModel.setDownloadOver(it) },
                                    onToggleNotifications = { viewModel.setNotificationsEnabled(it) },
                                    onSaveGitHubToken = { viewModel.saveGitHubToken(it) },
                                    onClearGitHubToken = { viewModel.clearGitHubToken() },
                                    onUnhideApp = { viewModel.unhideApp(it) },
                                    onCopyAllAppsInfo = { viewModel.copyAllAppsInfo() },
                                    onClearCache = { viewModel.clearDownloadCache() },
                                    onCheckPStoreUpdate = { viewModel.checkForPStoreUpdate() },
                                    onInstallPStoreUpdate = { viewModel.onInstallOrUpdateClicked(it) },
                                    bottomPaddingDp = 112
                                )
                            }
                        }
                    }
                }

                // LIGHT MODE — "Clean Shelf" (Segmented tabs, list rows, pushed Settings)
                shelfSettingsOpen -> {
                    SettingsScreen(
                        settings = settings,
                        selfAppState = selfAppState,
                        hiddenApps = hiddenApps,
                        totalAppsCount = apps.size,
                        snackbarHostState = snackbarHostState,
                        showBackButton = true,
                        onNavigateBack = { shelfSettingsOpen = false },
                        onSelectTheme = { viewModel.setThemeMode(it) },
                        onToggleAutoCheck = { viewModel.setAutoCheckEnabled(it) },
                        onSelectInterval = { viewModel.setCheckInterval(it) },
                        onSelectDownloadOver = { viewModel.setDownloadOver(it) },
                        onToggleNotifications = { viewModel.setNotificationsEnabled(it) },
                        onSaveGitHubToken = { viewModel.saveGitHubToken(it) },
                        onClearGitHubToken = { viewModel.clearGitHubToken() },
                        onUnhideApp = { viewModel.unhideApp(it) },
                        onCopyAllAppsInfo = { viewModel.copyAllAppsInfo() },
                        onClearCache = { viewModel.clearDownloadCache() },
                        onCheckPStoreUpdate = { viewModel.checkForPStoreUpdate() },
                        onInstallPStoreUpdate = { viewModel.onInstallOrUpdateClicked(it) }
                    )
                }

                else -> {
                    CleanShelfScreen(
                        apps = apps,
                        recentUpdates = recentUpdates,
                        selectedTab = shelfTab,
                        isRefreshing = isRefreshing,
                        isUpdatingAll = isUpdatingAll,
                        updatingCurrent = updatingCurrent,
                        updatingTotal = updatingTotal,
                        lastCheckedAtMillis = settings.lastCheckedAtMillis,
                        rateLimitNotice = rateLimitNotice,
                        snackbarHostState = snackbarHostState,
                        onSelectTab = { shelfTab = it },
                        onRefresh = { viewModel.refreshReleases() },
                        onAddApp = { showAddAppDialog = true },
                        onOpenSettings = { shelfSettingsOpen = true },
                        onOpenApp = { viewModel.openApp(it) },
                        onInstallOrUpdate = { viewModel.onInstallOrUpdateClicked(it) },
                        onCancelDownload = { viewModel.cancelDownload(it) },
                        onUpdateAll = { viewModel.updateAllApps() },
                        onOpenDetail = { selectedDetailAppId = it.id },
                        onCopyAppInfo = { viewModel.copyAppInfo(it.catalogApp) },
                        onHideApp = { pendingHideAppId = it.id },
                        onRetryApp = { viewModel.refreshSingleApp(it.id) },
                        onUninstallApp = { viewModel.uninstallApp(it) }
                    )
                }
            }
        }
    }
}
