package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.catalog.AppCatalog
import com.example.catalog.AppIconType
import com.example.catalog.BrandColorPreset
import com.example.model.AppItemUiState
import com.example.ui.theme.PStoreTheme

/**
 * Dialog for adding a new GitHub Releases app to P Store.
 */
@Composable
fun AddAppDialog(
    onAddApp: (
        displayName: String,
        packageName: String,
        repoInput: String,
        colorPreset: BrandColorPreset,
        iconType: AppIconType
    ) -> String?,
    onDismiss: () -> Unit
) {
    val colors = PStoreTheme.colors
    var displayName by rememberSaveable { mutableStateOf("") }
    var packageName by rememberSaveable { mutableStateOf("") }
    var githubRepo by rememberSaveable { mutableStateOf("") }
    var selectedColorIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedIconIndex by rememberSaveable { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val colorPresets = AppCatalog.colorPresets
    val iconOptions = AppIconType.customSelectable
    val chosenPreset = colorPresets.getOrElse(selectedColorIndex) { colorPresets.first() }
    val chosenIcon = iconOptions.getOrElse(selectedIconIndex) { AppIconType.APPS_GRID }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.add_app_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryText
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.add_app_display_name)) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.add_app_display_name_hint),
                            color = colors.secondaryText
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_app_name_input")
                )

                OutlinedTextField(
                    value = packageName,
                    onValueChange = {
                        packageName = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.add_app_package_name)) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.add_app_package_name_hint),
                            color = colors.secondaryText
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_app_package_input")
                )

                OutlinedTextField(
                    value = githubRepo,
                    onValueChange = {
                        githubRepo = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.add_app_github_repo)) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.add_app_github_repo_hint),
                            color = colors.secondaryText
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_app_repo_input")
                )

                // Color swatch picker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.add_app_color_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryText
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        colorPresets.forEachIndexed { index, preset ->
                            val swatchColor = Color(if (colors.isDark) preset.darkColor else preset.lightColor)
                            val isSelected = index == selectedColorIndex
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, colors.primaryText, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selectedColorIndex = index }
                                    .testTag("add_app_color_${preset.name.lowercase()}")
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = preset.name,
                                        tint = if (colors.isDark) colors.darkGlyphOnBrand else colors.lightGlyphOnBrand,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Icon glyph picker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.add_app_icon_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryText
                    )
                    val activeAccent = Color(if (colors.isDark) chosenPreset.darkColor else chosenPreset.lightColor)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        iconOptions.forEachIndexed { index, iconType ->
                            val isSelected = index == selectedIconIndex
                            Surface(
                                onClick = { selectedIconIndex = index },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) activeAccent else colors.neutralPillBg,
                                border = if (isSelected) null else BorderStroke(1.dp, colors.cardBorder),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("add_app_icon_${iconType.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AppCatalogGlyph(
                                        iconType = iconType,
                                        tint = if (isSelected) {
                                            if (colors.isDark) colors.darkGlyphOnBrand else colors.lightGlyphOnBrand
                                        } else {
                                            colors.primaryText
                                        },
                                        contentDescription = iconType.name,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        modifier = Modifier.testTag("add_app_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val err = onAddApp(displayName, packageName, githubRepo, chosenPreset, chosenIcon)
                    if (err == null) {
                        onDismiss()
                    } else {
                        errorMessage = err
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent
                ),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("add_app_confirm_button")
            ) {
                Text(stringResource(R.string.action_add_app), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("add_app_cancel_button")
            ) {
                Text(stringResource(R.string.action_cancel), color = colors.secondaryText)
            }
        }
    )
}

/**
 * Confirmation dialog before hiding an app from P Store.
 */
@Composable
fun HideAppConfirmDialog(
    item: AppItemUiState,
    onConfirmHide: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PStoreTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        icon = {
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.hide_app_title, item.catalogApp.displayName),
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryText
            )
        },
        text = {
            Text(
                text = stringResource(R.string.hide_app_message, item.catalogApp.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmHide()
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.error,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("confirm_hide_app_button")
            ) {
                Text(stringResource(R.string.action_hide), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("cancel_hide_app_button")
            ) {
                Text(stringResource(R.string.action_cancel), color = colors.secondaryText)
            }
        }
    )
}
