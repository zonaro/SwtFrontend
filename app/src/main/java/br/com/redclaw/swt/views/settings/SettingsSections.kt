/*
 * SwtFrontend - native Android launcher frontend for RetroArch/libretro cores.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.swt.views.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.redclaw.swt.R
import br.com.redclaw.swt.dashboard.DashboardService
import br.com.redclaw.swt.dashboard.SelfHostedPrefs
import br.com.redclaw.swt.scraping.SwtPrefs
import br.com.redclaw.swt.theme.AccentManager
import br.com.redclaw.swt.theme.ThemeManager
import org.json.JSONArray

// ══════════════════════════════════════════════════════════════════════════════
// ROM Folders Section
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun RomsSection(context: Context) {
    var folders by remember { mutableStateOf(loadRomFolders(context)) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        // Take persistable permission.
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) { /* some providers don't support it */ }

        val uriString = uri.toString()
        if (uriString !in folders) {
            folders = folders + uriString
            saveRomFolders(context, folders)
        }
    }

    SectionHeader(title = stringResource(R.string.settings_section_roms))

    Text(
        text = stringResource(R.string.settings_roms_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    if (folders.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_roms_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    } else {
        folders.forEach { uriString ->
            RomFolderRow(
                uriString = uriString,
                onDelete = {
                    // Revoke persistable permission.
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(uriString),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    } catch (_: Exception) { /* best effort */ }

                    folders = folders - uriString
                    saveRomFolders(context, folders)
                },
            )
        }
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Button(
            onClick = { safLauncher.launch(null) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_roms_add))
        }
    }
}

@Composable
private fun RomFolderRow(uriString: String, onDelete: () -> Unit) {
    val uri = Uri.parse(uriString)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = uri.lastPathSegment ?: uriString,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.dialog_cancel),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun loadRomFolders(context: Context): List<String> {
    val prefs = context.getSharedPreferences("swt_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString("pref_rom_folders", null) ?: return emptyList()
    return try {
        JSONArray(json).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveRomFolders(context: Context, folders: List<String>) {
    val arr = JSONArray()
    folders.forEach { arr.put(it) }
    context.getSharedPreferences("swt_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("pref_rom_folders", arr.toString())
        .apply()
}

// ══════════════════════════════════════════════════════════════════════════════
// Appearance Section
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun AppearanceSection(context: Context) {
    var isLight by remember { mutableStateOf(ThemeManager.isLight(context)) }
    var accentLabel by remember { mutableStateOf(AccentManager.getCurrentAccentLabel(context)) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var selectedAccentIndex by remember { mutableIntStateOf(
        AccentManager.options.indexOfFirst { it.key == AccentManager.getCurrentAccentKey(context) }.coerceAtLeast(0)
    ) }

    SectionHeader(title = stringResource(R.string.settings_section_appearance))

    // Theme row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_theme_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (isLight) stringResource(R.string.settings_theme_light) else stringResource(R.string.settings_theme_dark),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Button(
            onClick = {
                isLight = ThemeManager.toggle(context)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.settings_theme_label))
        }
    }

    // Accent row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_accent_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = accentLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Button(
            onClick = { showAccentDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.settings_accent_label))
        }
    }

    // Accent picker dialog
    if (showAccentDialog) {
        val labels = AccentManager.options.map { context.getString(it.labelRes) }
        AlertDialog(
            onDismissRequest = { showAccentDialog = false },
            title = { Text(stringResource(R.string.settings_accent_label)) },
            text = {
                Column {
                    labels.forEachIndexed { index, label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val chosen = AccentManager.options[index]
                                    AccentManager.setAccent(context, chosen.key)
                                    accentLabel = AccentManager.getAccentLabel(context, chosen.key)
                                    selectedAccentIndex = index
                                    showAccentDialog = false
                                    (context as? android.app.Activity)?.recreate()
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = index == selectedAccentIndex,
                                onClick = {
                                    val chosen = AccentManager.options[index]
                                    AccentManager.setAccent(context, chosen.key)
                                    accentLabel = AccentManager.getAccentLabel(context, chosen.key)
                                    selectedAccentIndex = index
                                    showAccentDialog = false
                                    (context as? android.app.Activity)?.recreate()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAccentDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Providers Section (IGDB / TheGamesDB / SteamGridDB)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ProvidersSection(context: Context) {
    var igdbClientId by remember { mutableStateOf(SwtPrefs.igdbClientId(context).orEmpty()) }
    var igdbClientSecret by remember { mutableStateOf(SwtPrefs.igdbClientSecret(context).orEmpty()) }
    var tgdbKey by remember { mutableStateOf(SwtPrefs.theGamesDbApiKey(context).orEmpty()) }
    var sgdbKey by remember { mutableStateOf(SwtPrefs.sgdbApiKey(context).orEmpty()) }
    var showSavedDialog by remember { mutableStateOf(false) }

    SectionHeader(title = stringResource(R.string.settings_section_providers))

    OutlinedTextField(
        value = igdbClientId,
        onValueChange = { igdbClientId = it },
        label = { Text(stringResource(R.string.settings_igdb_client_id)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )

    OutlinedTextField(
        value = igdbClientSecret,
        onValueChange = { igdbClientSecret = it },
        label = { Text(stringResource(R.string.settings_igdb_client_secret)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )

    OutlinedTextField(
        value = tgdbKey,
        onValueChange = { tgdbKey = it },
        label = { Text(stringResource(R.string.settings_tgdb_api_key)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )

    OutlinedTextField(
        value = sgdbKey,
        onValueChange = { sgdbKey = it },
        label = { Text(stringResource(R.string.settings_sgdb_api_key)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Button(
            onClick = {
                val id = igdbClientId.trim()
                val secret = igdbClientSecret.trim()
                val tgdb = tgdbKey.trim()
                val sgdb = sgdbKey.trim()

                SwtPrefs.saveIgdbCredentials(context, id, secret, id.isNotBlank() && secret.isNotBlank())
                SwtPrefs.saveTheGamesDbKey(context, tgdb, tgdb.isNotBlank())
                SwtPrefs.saveSgdbKey(context, sgdb, sgdb.isNotBlank())
                showSavedDialog = true
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.settings_save))
        }
    }

    if (showSavedDialog) {
        AlertDialog(
            onDismissRequest = { showSavedDialog = false },
            title = { Text(stringResource(R.string.settings_save)) },
            confirmButton = {
                TextButton(onClick = { showSavedDialog = false }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// RetroAchievements Section
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun RetroAchievementsSection(context: Context) {
    val raPrefs = remember { context.getSharedPreferences("swt_ra", Context.MODE_PRIVATE) }
    var raLoggedIn by remember { mutableStateOf(raPrefs.getBoolean("ra_logged_in", false)) }
    var raUsername by remember { mutableStateOf("") }
    var raPassword by remember { mutableStateOf("") }
    var raStatus by remember {
        mutableStateOf(
            if (raLoggedIn) {
                val username = raPrefs.getString("ra_username", null)
                if (!username.isNullOrBlank()) {
                    context.getString(R.string.settings_ra_status_logged_in, username)
                } else {
                    context.getString(R.string.settings_ra_status_not_logged_in)
                }
            } else {
                context.getString(R.string.settings_ra_status_not_logged_in)
            }
        )
    }

    SectionHeader(title = stringResource(R.string.settings_section_ra))

    OutlinedTextField(
        value = raUsername,
        onValueChange = { raUsername = it },
        label = { Text(stringResource(R.string.settings_ra_username)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )

    OutlinedTextField(
        value = raPassword,
        onValueChange = { raPassword = it },
        label = { Text(stringResource(R.string.settings_ra_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Button(
            onClick = {
                val username = raUsername.trim()
                if (username.isEmpty()) {
                    raStatus = context.getString(R.string.settings_ra_status_not_logged_in)
                    return@Button
                }
                raPrefs.edit()
                    .putString("ra_username", username)
                    .putBoolean("ra_logged_in", true)
                    .apply()
                raUsername = ""
                raPassword = ""
                raLoggedIn = true
                raStatus = context.getString(R.string.settings_ra_status_logged_in, username)
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(R.string.settings_ra_login))
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = {
                raPrefs.edit()
                    .putBoolean("ra_logged_in", false)
                    .remove("ra_username")
                    .apply()
                raLoggedIn = false
                raStatus = context.getString(R.string.settings_ra_status_not_logged_in)
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(R.string.settings_ra_logout))
        }
    }

    Text(
        text = raStatus,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Dashboard Section
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun DashboardSection(context: Context) {
    val initial = remember { SelfHostedPrefs.load(context) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var portText by remember { mutableStateOf(initial.port.toString()) }

    SectionHeader(title = stringResource(R.string.settings_section_dashboard))

    // Switch row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_dashboard_enabled),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = { isChecked ->
                val port = portText.toIntOrNull() ?: 7120
                val updated = initial.copy(enabled = isChecked, port = port)
                SelfHostedPrefs.save(context, updated)
                enabled = isChecked
                if (isChecked) {
                    DashboardService.start(context)
                } else {
                    DashboardService.stop(context)
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }

    // Port row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_dashboard_port),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number,
            ),
            modifier = Modifier.width(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }

    // Open dashboard button
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Button(
            onClick = {
                val port = portText.toIntOrNull() ?: 7120
                val url = "http://127.0.0.1:$port"
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) { /* no browser */ }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_dashboard_open))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Colors Download Section
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ColorsDownloadSection(context: Context) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Button(
            onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=nintendo+switch+color+palettes+hex"),
                        ),
                    )
                } catch (_: Exception) { /* no browser */ }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.settings_colors_download))
        }
    }
}
