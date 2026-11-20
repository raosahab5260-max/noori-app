package com.noori.companion

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RowScope.NavPill(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
            .background(if (active) Color(0xFF581C87) else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 7.dp, horizontal = 3.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = if (active) Color.White else Color(0xFFC084FC), modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
fun NooriDialogsHost(
    context: Context,
    prefs: SharedPreferences,
    memoryManager: MemoryManager,
    memories: List<MemoryItem>,
    onMemoriesUpdate: (List<MemoryItem>) -> Unit,
    pendingAction: PendingPhoneAction?,
    onActionResult: (String) -> Unit,
    onDismissAction: () -> Unit,
    showSmartMode: Boolean,
    onDismissSmart: () -> Unit,
    showMood: Boolean,
    onPickAction: (PendingPhoneAction) -> Unit,
    onDismissMood: () -> Unit,
    showSettings: Boolean,
    apiKey: String,
    voiceEnabled: Boolean,
    unlockGreeting: Boolean,
    floatingEnabled: Boolean,
    onSaveSettings: (String, Boolean, Boolean, Boolean) -> Unit,
    onDismissSettings: () -> Unit
) {
    pendingAction?.let { act ->
        AlertDialog(
            onDismissRequest = onDismissAction, containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text(act.title) }, text = { Text(act.description) },
            confirmButton = { Button(onClick = { onActionResult(PhoneAssistant.executeAction(context, act)) }) { Text("हाँ, करें") } },
            dismissButton = { TextButton(onClick = onDismissAction) { Text("रद्द करें") } }
        )
    }

    if (showSmartMode) {
        var memOn by remember { mutableStateOf(memoryManager.isMemoryEnabled()) }
        AlertDialog(
            onDismissRequest = onDismissSmart, containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text("🧠 Smart Mode • Memory (${memories.size})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Memory On/Off", fontSize = 12.sp); Switch(checked = memOn, onCheckedChange = { memOn = it; memoryManager.setMemoryEnabled(it) })
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(memories, key = { it.id }) { item ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF26174A)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.fact, Modifier.weight(1f), fontSize = 12.sp, color = Color.White)
                                IconButton(onClick = { memoryManager.deleteMemory(item.id); onMemoriesUpdate(memoryManager.getMemories()) }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFF472B6))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { memoryManager.clearAll(); onMemoriesUpdate(emptyList()) }) { Text("Clear All", color = Color(0xFFF472B6)) }
                }
            },
            confirmButton = { TextButton(onClick = onDismissSmart) { Text("बंद करें") } }
        )
    }

    if (showMood) {
        AlertDialog(
            onDismissRequest = onDismissMood, containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text("⭐ Quick Phone Actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPickAction(PendingPhoneAction.OpenApp("WhatsApp")) }, modifier = Modifier.fillMaxWidth()) { Text("💬 WhatsApp खोलें") }
                    Button(onClick = { onPickAction(PendingPhoneAction.OpenApp("Instagram")) }, modifier = Modifier.fillMaxWidth()) { Text("📸 Instagram खोलें") }
                    Button(onClick = { onPickAction(PendingPhoneAction.OpenApp("YouTube")) }, modifier = Modifier.fillMaxWidth()) { Text("▶️ YouTube खोलें") }
                    Button(onClick = { onPickAction(PendingPhoneAction.SetAlarm(7, 0)) }, modifier = Modifier.fillMaxWidth()) { Text("⏰ सुबह 7:00 का अलार्म") }
                }
            },
            confirmButton = { TextButton(onClick = onDismissMood) { Text("बंद करें") } }
        )
    }

    if (showSettings) {
        var draftKey by remember { mutableStateOf(apiKey) }
        var vOn by remember { mutableStateOf(voiceEnabled) }
        var uOn by remember { mutableStateOf(unlockGreeting) }
        var fOn by remember { mutableStateOf(floatingEnabled) }
        AlertDialog(
            onDismissRequest = onDismissSettings, containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text("⚙️ Settings (₹0 Free)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = draftKey, onValueChange = { draftKey = it }, label = { Text("Free Gemini API Key") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Voice Output", fontSize = 12.sp); Switch(checked = vOn, onCheckedChange = { vOn = it }) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Unlock Greeting", fontSize = 12.sp); Switch(checked = uOn, onCheckedChange = { uOn = it }) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Home Screen Noori (Photo 2)", fontSize = 12.sp)
                        Switch(checked = fOn, onCheckedChange = { want ->
                            if (want && !Settings.canDrawOverlays(context)) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            } else {
                                fOn = want
                                val svc = Intent(context, FloatingCompanionService::class.java)
                                if (want) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc) else context.startService(svc) }
                                else context.stopService(svc)
                            }
                        })
                    }
                }
            },
            confirmButton = { Button(onClick = { onSaveSettings(draftKey.trim(), vOn, uOn, fOn) }) { Text("सेव करें") } },
            dismissButton = { TextButton(onClick = onDismissSettings) { Text("बंद करें") } }
        )
    }
}
