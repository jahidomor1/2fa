package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GoldAccent
import com.example.viewmodel.MainViewModel

@Composable
fun FloatingBubbleOverlay(
    viewModel: MainViewModel,
    onAddClicked: () -> Unit
) {
    val isEnabled by viewModel.isOverlayEnabled.collectAsState()
    val totpItems by viewModel.totpItems.collectAsState()

    var isMenuExpanded by remember { mutableStateOf(false) }

    if (!isEnabled) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = isMenuExpanded,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    modifier = Modifier
                        .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .testTag("floating_bubble_menu")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "2FA Quick Bubble",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )

                        // 1. Add
                        BubbleMenuItem(
                            icon = Icons.Default.Add,
                            label = "Add Secret Key",
                            tint = CyberCyan,
                            testTag = "bubble_action_add",
                            onClick = {
                                isMenuExpanded = false
                                onAddClicked()
                            }
                        )

                        // 2. New Code
                        BubbleMenuItem(
                            icon = Icons.Default.FlashOn,
                            label = "New Code & Copy",
                            tint = ElectricBlue,
                            testTag = "bubble_action_new_code",
                            onClick = {
                                isMenuExpanded = false
                                if (totpItems.isNotEmpty()) {
                                    viewModel.generateFreshCodeAndCopy(totpItems.first())
                                } else {
                                    onAddClicked()
                                }
                            }
                        )

                        // 3. Hide Bubble
                        BubbleMenuItem(
                            icon = Icons.Default.VisibilityOff,
                            label = "👁 Hide Floating Bubble",
                            tint = GoldAccent,
                            testTag = "bubble_action_hide",
                            onClick = {
                                isMenuExpanded = false
                                viewModel.hideBubble()
                            }
                        )

                        // 4. Remove
                        BubbleMenuItem(
                            icon = Icons.Default.Delete,
                            label = "Remove Selected Key",
                            tint = MaterialTheme.colorScheme.error,
                            testTag = "bubble_action_remove",
                            onClick = {
                                isMenuExpanded = false
                                if (totpItems.isNotEmpty()) {
                                    viewModel.deleteKey(totpItems.first().keyEntity.id)
                                }
                            }
                        )
                    }
                }
            }

            // Main Floating Bubble Button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CyberCyan)
                    .clickable { isMenuExpanded = !isMenuExpanded }
                    .testTag("floating_bubble_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "2FA Overlay Bubble",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun BubbleMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
