package com.example.ui.dark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.PStoreTheme

enum class MidnightTab {
    HOME,
    STORE,
    SETTINGS
}

@Composable
fun MidnightDockShell(
    selectedTab: MidnightTab,
    updateCount: Int,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (MidnightTab) -> Unit,
    content: @Composable () -> Unit
) {
    val colors = PStoreTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        content()

        // Snackbar positioned cleanly above floating nav dock
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
        )

        // Floating rounded bottom navigation dock (22dp radius, card color, 1dp border)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.cardBorder),
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .testTag("midnight_floating_nav")
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                DockNavItem(
                    label = stringResource(R.string.nav_home),
                    icon = Icons.Rounded.GridView,
                    selected = selectedTab == MidnightTab.HOME,
                    showBadge = false,
                    onClick = { onSelectTab(MidnightTab.HOME) },
                    testTag = "nav_home"
                )
                DockNavItem(
                    label = stringResource(R.string.nav_store),
                    icon = Icons.Rounded.Download,
                    selected = selectedTab == MidnightTab.STORE,
                    showBadge = updateCount > 0,
                    onClick = { onSelectTab(MidnightTab.STORE) },
                    testTag = "nav_store"
                )
                DockNavItem(
                    label = stringResource(R.string.nav_settings),
                    icon = Icons.Rounded.Settings,
                    selected = selectedTab == MidnightTab.SETTINGS,
                    showBadge = false,
                    onClick = { onSelectTab(MidnightTab.SETTINGS) },
                    testTag = "nav_settings"
                )
            }
        }
    }
}

@Composable
private fun DockNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    showBadge: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = PStoreTheme.colors
    val tint = if (selected) colors.primaryText else colors.secondaryText

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            if (showBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint
        )
    }
}
