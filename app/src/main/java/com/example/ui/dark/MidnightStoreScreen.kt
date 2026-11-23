package com.example.ui.dark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppInstallStatus
import com.example.model.AppItemUiState
import com.example.model.StoreFilter
import com.example.ui.theme.PStoreTheme
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidnightStoreScreen(
    apps: List<AppItemUiState>,
    selectedFilter: StoreFilter,
    isRefreshing: Boolean,
    lastCheckedAtMillis: Long,
    onSelectFilter: (StoreFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (AppItemUiState) -> Unit,
    onOpenApp: (AppItemUiState) -> Unit,
    onInstallOrUpdate: (AppItemUiState) -> Unit,
    onCancelDownload: (AppItemUiState) -> Unit,
    onCopyAppInfo: (AppItemUiState) -> Unit,
    onRetryApp: (AppItemUiState) -> Unit,
    onUninstallApp: (AppItemUiState) -> Unit,
    bottomNavSpacerDp: Int = 104,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    val context = LocalContext.current

    val eligibleApps = remember(apps) {
        apps.filter { it.catalogApp.showInLauncher || (it.catalogApp.isSelf && it.updateAvailable) }
    }

    val filteredApps = remember(eligibleApps, selectedFilter) {
        when (selectedFilter) {
            StoreFilter.ALL -> eligibleApps
            StoreFilter.UPDATES -> eligibleApps.filter { it.installStatus == AppInstallStatus.UPDATE_AVAILABLE }
            StoreFilter.NOT_INSTALLED -> eligibleApps.filter { it.installStatus == AppInstallStatus.NOT_INSTALLED }
        }
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
            item(key = "store_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.nav_store),
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Formatters.formatRelativeChecked(context, lastCheckedAtMillis, withSyncPrefix = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }

                    MidnightRefreshButton(
                        isRefreshing = isRefreshing,
                        onClick = onRefresh
                    )
                }
            }

            item(key = "store_filters") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChipButton(
                        label = stringResource(R.string.filter_all),
                        selected = selectedFilter == StoreFilter.ALL,
                        onClick = { onSelectFilter(StoreFilter.ALL) },
                        testTag = "filter_all"
                    )
                    FilterChipButton(
                        label = stringResource(R.string.filter_updates),
                        selected = selectedFilter == StoreFilter.UPDATES,
                        onClick = { onSelectFilter(StoreFilter.UPDATES) },
                        testTag = "filter_updates"
                    )
                    FilterChipButton(
                        label = stringResource(R.string.filter_not_installed),
                        selected = selectedFilter == StoreFilter.NOT_INSTALLED,
                        onClick = { onSelectFilter(StoreFilter.NOT_INSTALLED) },
                        testTag = "filter_not_installed"
                    )
                }
            }

            if (filteredApps.isEmpty()) {
                item(key = "store_empty") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp)
                        ) {
                            Text(
                                text = if (selectedFilter == StoreFilter.UPDATES) {
                                    stringResource(R.string.empty_no_updates_title)
                                } else {
                                    stringResource(R.string.empty_filter_title)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primaryText,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (selectedFilter == StoreFilter.UPDATES) {
                                    stringResource(R.string.empty_no_updates_subtitle)
                                } else {
                                    stringResource(R.string.empty_filter_subtitle)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { item ->
                    MidnightUpdateCard(
                        item = item,
                        onClick = { onOpenDetail(item) },
                        onOpen = { onOpenApp(item) },
                        onInstallOrUpdate = { onInstallOrUpdate(item) },
                        onCancelDownload = { onCancelDownload(item) },
                        onCopyInfo = { onCopyAppInfo(item) },
                        onRetry = { onRetryApp(item) },
                        onUninstall = { onUninstallApp(item) },
                        showReleaseNotesPreview = true
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = PStoreTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.accent else colors.card,
        border = if (selected) null else BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) colors.onAccent else colors.secondaryText
            )
        }
    }
}
