package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppItemUiState
import com.example.model.OlderReleasesState
import com.example.model.ReleaseInfo
import com.example.ui.theme.PStoreTheme
import com.example.util.Formatters
import com.example.util.VersionComparator

/**
 * Small "NEW" pill badge for apps discovered within the last 7 days.
 */
@Composable
fun NewAppBadge(modifier: Modifier = Modifier) {
    val colors = PStoreTheme.colors
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colors.accent,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.badge_new),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            ),
            color = colors.onAccent,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

/**
 * Subtle banner displayed when GitHub rate-limit (403) is hit while keeping the cached list visible.
 */
@Composable
fun RateLimitNoticeBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("rate_limit_notice_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Empty state card shown when no discovered apps exist yet.
 */
@Composable
fun EmptyCatalogCard(modifier: Modifier = Modifier) {
    val colors = PStoreTheme.colors
    Surface(
        shape = RoundedCornerShape(if (colors.isDark) 20.dp else 22.dp),
        color = colors.card,
        border = if (colors.isDark) BorderStroke(1.dp, colors.cardBorder) else null,
        shadowElevation = if (colors.isDark) 0.dp else 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_catalog_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 30.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.empty_catalog_message),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Long-press context menu for tiles and rows: Open, Update/Install, Copy app info, Hide from P Store.
 */
@Composable
fun AppContextMenu(
    expanded: Boolean,
    item: AppItemUiState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    onCopyInfo: () -> Unit,
    onHide: (() -> Unit)? = null
) {
    val colors = PStoreTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = colors.card
    ) {
        if (item.isInstalled && item.packageName.isNotBlank()) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_open),
                        color = colors.primaryText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onOpen()
                },
                modifier = Modifier.testTag("menu_open_${item.id}")
            )
        }
        if (item.latestRelease != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (item.isInstalled) {
                            stringResource(R.string.action_update)
                        } else {
                            stringResource(R.string.action_install)
                        },
                        color = colors.accent
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onUpdate()
                },
                modifier = Modifier.testTag("menu_update_${item.id}")
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.action_copy_app_info),
                    color = colors.primaryText
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onCopyInfo()
            },
            modifier = Modifier.testTag("menu_copy_${item.id}")
        )
        if (!item.catalogApp.isSelf && onHide != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_hide_from_pstore),
                        color = colors.error
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onHide()
                },
                modifier = Modifier.testTag("menu_hide_${item.id}")
            )
        }
    }
}

/**
 * Friendly modal prompting the user to grant "Install unknown apps" permission.
 */
@Composable
fun UnknownAppsPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PStoreTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.permission_install_unknown_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryText
            )
        },
        text = {
            Text(
                text = stringResource(R.string.permission_install_unknown_message),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent
                ),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("permission_open_settings_button")
            ) {
                Text(stringResource(R.string.action_open_settings), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("permission_cancel_button")
            ) {
                Text(stringResource(R.string.action_cancel), color = colors.secondaryText)
            }
        }
    )
}

/**
 * Inline error banner with Retry button and optional Uninstall button (for signature mismatch).
 */
@Composable
fun ErrorActionBox(
    message: String,
    isSignatureMismatch: Boolean,
    onRetry: () -> Unit,
    onUninstall: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.errorContainer,
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSignatureMismatch && onUninstall != null) {
                    Button(
                        onClick = onUninstall,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.error,
                            contentColor = colors.onAccent
                        ),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("signature_uninstall_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_uninstall), fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("error_retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_retry),
                        color = colors.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Dialog displaying all GitHub releases for an app with rollback "Install" actions.
 */
@Composable
fun OlderVersionsDialog(
    item: AppItemUiState,
    onInstallRelease: (ReleaseInfo) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PStoreTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = {
            Text(
                text = stringResource(R.string.older_versions_title, item.catalogApp.displayName),
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryText
            )
        },
        text = {
            when (val state = item.olderReleasesState) {
                is OlderReleasesState.Idle,
                is OlderReleasesState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.older_versions_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText
                        )
                    }
                }

                is OlderReleasesState.Error -> {
                    ErrorActionBox(
                        message = state.message,
                        isSignatureMismatch = false,
                        onRetry = onRetry
                    )
                }

                is OlderReleasesState.Success -> {
                    if (state.releases.isEmpty()) {
                        Text(
                            text = stringResource(R.string.older_versions_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                        ) {
                            items(state.releases, key = { it.tagName }) { release ->
                                val isInstalledThis = item.isInstalled &&
                                    VersionComparator.areTagsEqual(
                                        installedVersion = item.effectiveInstalledVersion,
                                        githubTagName = release.tagName
                                    )
                                val sizeStr = Formatters.formatFileSize(release.apkSize)
                                val meta = listOf(release.publishedAt, sizeStr)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "v${release.displayVersion}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.primaryText
                                        )
                                        if (meta.isNotBlank()) {
                                            Text(
                                                text = meta,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.secondaryText
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (isInstalledThis) {
                                        Text(
                                            text = stringResource(R.string.older_versions_current),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.success,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                onDismiss()
                                                onInstallRelease(release)
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.6f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .heightIn(min = 44.dp)
                                                .testTag("rollback_install_${release.tagName}")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.older_versions_rollback),
                                                color = colors.accent,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = colors.divider)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("older_versions_close_button")
            ) {
                Text(stringResource(R.string.action_close), color = colors.accent)
            }
        }
    )
}
