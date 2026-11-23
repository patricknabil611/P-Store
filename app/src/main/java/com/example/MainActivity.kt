package com.example

import android.Manifest
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
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PStoreRoot(
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
}

@Composable
fun PStoreRoot(
    onUpdateSystemBars: (Boolean) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val apps by viewModel.apps.collectAsStateWithLifecycle()
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

    // Navigation state shared across theme switches
    var selectedDetailPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var midnightTab by rememberSaveable { mutableStateOf(MidnightTab.HOME) }
    var shelfTab by rememberSaveable { mutableStateOf(ShelfTab.MY_APPS) }
    var shelfSettingsOpen by rememberSaveable { mutableStateOf(false) }

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

            val detailItem = selectedDetailPackage?.let { pkg ->
                apps.firstOrNull { it.packageName == pkg }
            }

            when {
                detailItem != null -> {
                    AppDetailScreen(
                        item = detailItem,
                        snackbarHostState = snackbarHostState,
                        onNavigateBack = { selectedDetailPackage = null },
                        onInstallOrUpdate = { specificRelease ->
                            viewModel.onInstallOrUpdateClicked(detailItem, specificRelease)
                        },
                        onCancelDownload = { viewModel.cancelDownload(detailItem) },
                        onOpenApp = { viewModel.openApp(detailItem) },
                        onUninstallApp = { viewModel.uninstallApp(detailItem) },
                        onRetryRelease = { viewModel.refreshSingleApp(detailItem.packageName) },
                        onOpenGitHub = { viewModel.openGitHubReleases(detailItem) },
                        onCycleAutoUpdate = { viewModel.cyclePerAppAutoUpdate(detailItem) },
                        onLoadOlderReleases = { viewModel.loadOlderReleases(detailItem) },
                        onCopyAppInfo = { viewModel.copyAppInfo(detailItem.catalogApp) }
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
                                    onRefresh = { viewModel.refreshReleases() },
                                    onOpenApp = { viewModel.openApp(it) },
                                    onInstallOrUpdate = { viewModel.onInstallOrUpdateClicked(it) },
                                    onCancelDownload = { viewModel.cancelDownload(it) },
                                    onUpdateAll = { viewModel.updateAllApps() },
                                    onOpenDetail = { selectedDetailPackage = it.packageName },
                                    onCopyAppInfo = { viewModel.copyAppInfo(it.catalogApp) },
                                    onRetryApp = { viewModel.refreshSingleApp(it.packageName) },
                                    onUninstallApp = { viewModel.uninstallApp(it) }
                                )
                            }

                            MidnightTab.STORE -> {
                                MidnightStoreScreen(
                                    apps = apps,
                                    selectedFilter = storeFilter,
                                    isRefreshing = isRefreshing,
                                    lastCheckedAtMillis = settings.lastCheckedAtMillis,
                                    onSelectFilter = { viewModel.setStoreFilter(it) },
                                    onRefresh = { viewModel.refreshReleases() },
                                    onOpenDetail = { selectedDetailPackage = it.packageName },
                                    onOpenApp = { viewModel.openApp(it) },
                                    onInstallOrUpdate = { viewModel.onInstallOrUpdateClicked(it) },
                                    onCancelDownload = { viewModel.cancelDownload(it) },
                                    onCopyAppInfo = { viewModel.copyAppInfo(it.catalogApp) },
                                    onRetryApp = { viewModel.refreshSingleApp(it.packageName) },
                                    onUninstallApp = { viewModel.uninstallApp(it) }
                                )
                            }

                            MidnightTab.SETTINGS -> {
                                SettingsScreen(
                                    settings = settings,
                                    selfAppState = selfAppState,
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
                        snackbarHostState = snackbarHostState,
                        onSelectTab = { shelfTab = it },
                        onRefresh = { viewModel.refreshReleases() },
                        onOpenSettings = { shelfSettingsOpen = true },
                        onOpenApp = { viewModel.openApp(it) },
                        onInstallOrUpdate = { viewModel.onInstallOrUpdateClicked(it) },
                        onCancelDownload = { viewModel.cancelDownload(it) },
                        onUpdateAll = { viewModel.updateAllApps() },
                        onOpenDetail = { selectedDetailPackage = it.packageName },
                        onCopyAppInfo = { viewModel.copyAppInfo(it.catalogApp) },
                        onRetryApp = { viewModel.refreshSingleApp(it.packageName) },
                        onUninstallApp = { viewModel.uninstallApp(it) }
                    )
                }
            }
        }
    }
}
