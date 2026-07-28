package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    var autoCopy by remember { mutableStateOf(viewModel.prefs.autoCopy) }
    var autoPaste by remember { mutableStateOf(viewModel.prefs.autoPaste) }
    var hapticFeedback by remember { mutableStateOf(viewModel.prefs.hapticFeedback) }
    var language by remember { mutableStateOf(viewModel.prefs.language) }

    var showLanguageMenu by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<String?>(null) } // "privacy", "terms", "about"

    val languages = listOf("English", "Spanish", "French", "German", "Japanese")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "App Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Floating Overlay Section
            SettingsSectionHeader(title = "2FA Floating Bubble Overlay")

            Spacer(modifier = Modifier.height(10.dp))

            val isOverlayEnabled by viewModel.isOverlayEnabled.collectAsState()
            val context = LocalContext.current

            SettingsToggleCard(
                icon = Icons.Default.Shield,
                title = "2FA ON / Floating Bubble",
                subtitle = "Keep floating 2FA bubble active over other apps",
                checked = isOverlayEnabled,
                testTag = "settings_overlay_switch",
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    viewModel.toggleOverlay(enabled)
                }
            )

            if (isOverlayEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                                viewModel.showBubble()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = CyberCyan)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Show Floating Bubble", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Reveal bubble on screen if hidden", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("Show 👁", fontWeight = FontWeight.Bold, color = CyberCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Appearance Section
            SettingsSectionHeader(title = "Appearance & Preferences")

            Spacer(modifier = Modifier.height(10.dp))

            SettingsToggleCard(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                subtitle = "Toggle dark theme canvas",
                checked = isDarkMode,
                testTag = "settings_dark_mode_switch",
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsToggleCard(
                icon = Icons.Default.ContentCopy,
                title = "Auto Copy",
                subtitle = "Copy newly generated TOTP code automatically",
                checked = autoCopy,
                testTag = "settings_auto_copy_switch",
                onCheckedChange = {
                    autoCopy = it
                    viewModel.prefs.autoCopy = it
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsToggleCard(
                icon = Icons.Default.ContentPaste,
                title = "Auto Paste",
                subtitle = "Auto-detect Base32 secrets in clipboard",
                checked = autoPaste,
                testTag = "settings_auto_paste_switch",
                onCheckedChange = {
                    autoPaste = it
                    viewModel.prefs.autoPaste = it
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsToggleCard(
                icon = Icons.Default.Vibration,
                title = "Haptic Feedback",
                subtitle = "Vibrate on code generation and copy",
                checked = hapticFeedback,
                testTag = "settings_haptic_switch",
                onCheckedChange = {
                    hapticFeedback = it
                    viewModel.prefs.hapticFeedback = it
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Language & Localization
            SettingsSectionHeader(title = "Language")

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageMenu = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = CyberCyan)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("App Language", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Current: $language", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Box {
                        Text(text = language, fontWeight = FontWeight.Bold, color = CyberCyan)
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false }
                        ) {
                            languages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang) },
                                    onClick = {
                                        language = lang
                                        viewModel.prefs.language = lang
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Legal & About
            SettingsSectionHeader(title = "Legal & Security")

            Spacer(modifier = Modifier.height(10.dp))

            SettingsNavCard(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy Policy",
                testTag = "settings_privacy_policy",
                onClick = { activeDialog = "privacy" }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsNavCard(
                icon = Icons.Default.Description,
                title = "Terms of Service",
                testTag = "settings_terms",
                onClick = { activeDialog = "terms" }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsNavCard(
                icon = Icons.Default.Info,
                title = "About 2FA Generate",
                testTag = "settings_about",
                onClick = { activeDialog = "about" }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Logout
            Button(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("settings_logout_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout Account", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (activeDialog != null) {
        val title = when (activeDialog) {
            "privacy" -> "Privacy Policy"
            "terms" -> "Terms of Service"
            else -> "About 2FA Generate"
        }
        val contentText = when (activeDialog) {
            "privacy" -> "2FA Generate prioritizes user security. All Base32 secret keys are encrypted on-device using Android KeyStore hardware modules. Secrets are never transmitted in plain text to any server."
            "terms" -> "By using 2FA Generate, you agree to store RFC 6238 compatible TOTP keys responsibly. You remain responsible for keeping backup copies of your recovery keys."
            else -> "2FA Generate v1.0\nBuilt with Kotlin, Material Design 3, Room, Android KeyStore, and Firebase Realtime Database."
        }

        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(contentText, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = CyberCyan
    )
}

@Composable
fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ElectricBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = ElectricBlue)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = CyberCyan
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
fun SettingsNavCard(
    icon: ImageVector,
    title: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CyberCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = CyberCyan)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))

            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
