package com.noori.companion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun RowScope.NavPill(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) Color(0xFF581C87) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 7.dp, horizontal = 3.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Color.White else Color(0xFFC084FC), modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
fun ActionConfirmDialog(
    action: PendingPhoneAction,
    context: Context,
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1033),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE9D5FF),
        title = { Text(action.title) },
        text = { Text(action.description) },
        confirmButton = {
            Button(onClick = { onResult(PhoneAssistant.executeAction(context, action)) }) { Text("हाँ, करें") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("रद्द करें", color = Color(0xFFC084FC)) }
        }
    )
}

@Composable
fun SmartModeDialog(
    provider: AIProvider,
    memories: List<MemoryItem>,
    memoryManager: MemoryManager,
    onMemoriesChanged: (List<MemoryItem>) -> Unit,
    onDismiss: () -> Unit
) {
    var newFact by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<MemoryItem?>(null) }
    var editText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1033),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE9D5FF),
        title = { Text("🧠 Smart Mode • Memory (${memories.size})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Active AI: ${provider.label}", fontSize = 11.sp, color = Color(0xFFC084FC))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newFact,
                        onValueChange = { newFact = it },
                        placeholder = { Text("नई बात जोड़ें...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Button(onClick = {
                        if (memoryManager.addMemory(newFact, allowSensitive = true)) {
                            onMemoriesChanged(memoryManager.getMemories())
                            newFact = ""
                        }
                    }) { Text("जोड़ें") }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(memories, key = { it.id }) { item ->
                        if (editingItem?.id == item.id) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                TextButton(onClick = {
                                    memoryManager.updateMemory(item.id, editText)
                                    onMemoriesChanged(memoryManager.getMemories())
                                    editingItem = null
                                }) { Text("सेव") }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF26174A)).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.fact, modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.White)
                                IconButton(onClick = { editingItem = item; editText = item.fact }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFFC084FC), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = {
                                    memoryManager.deleteMemory(item.id)
                                    onMemoriesChanged(memoryManager.getMemories())
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF472B6), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("बंद करें", color = Color(0xFFC084FC)) } }
    )
}

@Composable
fun MoodAndActionsDialog(
    nooriMood: String,
    onMoodSelect: (String) -> Unit,
    onQuickAction: (PendingPhoneAction) -> Unit,
    onDismiss: () -> Unit
) {
    val moods = listOf("Caring & Playful", "Sweet & Romantic", "Funny & Teasing", "Smart Assistant")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1033),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE9D5FF),
        title = { Text("⭐ Noori Mood & Quick Actions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Noori का मूड चुनें:", fontSize = 12.sp)
                moods.forEach { m ->
                    Button(
                        onClick = { onMoodSelect(m) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (nooriMood == m) Color(0xFF9333EA) else Color(0xFF26174A)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(m) }
                }
                Divider(color = Color(0xFF4C1D95))
                Button(onClick = { onQuickAction(PendingPhoneAction.OpenApp("WhatsApp")) }, modifier = Modifier.fillMaxWidth()) { Text("💬 WhatsApp खोलें") }
                Button(onClick = { onQuickAction(PendingPhoneAction.OpenApp("Instagram")) }, modifier = Modifier.fillMaxWidth()) { Text("📸 Instagram खोलें") }
                Button(onClick = { onQuickAction(PendingPhoneAction.OpenApp("YouTube")) }, modifier = Modifier.fillMaxWidth()) { Text("▶️ YouTube खोलें") }
                Button(onClick = { onQuickAction(PendingPhoneAction.SetAlarm(7, 0)) }, modifier = Modifier.fillMaxWidth()) { Text("⏰ सुबह 7:00 का अलार्म") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("बंद करें", color = Color(0xFFC084FC)) } }
    )
}

@Composable
fun NooriSettingsDialog(
    context: Context,
    prefs: SharedPreferences,
    provider: AIProvider,
    geminiKey: String,
    openAiKey: String,
    voiceEnabled: Boolean,
    floatingEnabled: Boolean,
    voiceManager: VoiceManager?,
    onProviderChange: (AIProvider) -> Unit,
    onVoiceChange: (Boolean) -> Unit,
    onFloatingChange: (Boolean) -> Unit,
    onSaveKeys: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var draftGemini by remember { mutableStateOf(geminiKey) }
    var draftOpenAi by remember { mutableStateOf(openAiKey) }
    var voicePitch by remember { mutableStateOf(prefs.getFloat("voice_pitch", 1.08f)) }
    val hasOverlayPerm = Settings.canDrawOverlays(context)
    val hasMicPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1033),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE9D5FF),
        title = { Text("⚙️ Settings (Gemini + OpenAI)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AIProvider.entries.forEach { p ->
                        Button(
                            onClick = { onProviderChange(p) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (provider == p) Color(0xFF9333EA) else Color(0xFF26174A)),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (p == AIProvider.GEMINI) "Gemini" else "OpenAI", fontSize = 11.sp) }
                    }
                }
                OutlinedTextField(
                    value = draftGemini,
                    onValueChange = { draftGemini = it },
                    label = { Text("Gemini API Key (Free Tier)", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = draftOpenAi,
                    onValueChange = { draftOpenAi = it },
                    label = { Text("OpenAI API Key (Paid)", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Female Voice Output", fontSize = 12.sp)
                    Switch(checked = voiceEnabled, onCheckedChange = onVoiceChange)
                }
                Text("Voice Pitch: ${String.format("%.2f", voicePitch)}", fontSize = 11.sp)
                Slider(
                    value = voicePitch,
                    onValueChange = {
                        voicePitch = it
                        prefs.edit().putFloat("voice_pitch", it).apply()
                        voiceManager?.updateVoiceTuning(it, 0.98f)
                    },
                    valueRange = 0.8f..1.4f
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Home Screen Floating Noori", fontSize = 12.sp)
                    Switch(
                        checked = floatingEnabled,
                        onCheckedChange = { wantOn ->
                            if (wantOn && !Settings.canDrawOverlays(context)) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            } else {
                                onFloatingChange(wantOn)
                                val svc = Intent(context, FloatingCompanionService::class.java)
                                if (wantOn) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc)
                                    else context.startService(svc)
                                } else context.stopService(svc)
                            }
                        }
                    )
                }
                Text("Permissions: Mic ${if (hasMicPerm) "✓" else "✕"} • Overlay ${if (hasOverlayPerm) "✓" else "✕"}", fontSize = 10.sp, color = Color(0xFFC084FC))
            }
        },
        confirmButton = { Button(onClick = { onSaveKeys(draftGemini.trim(), draftOpenAi.trim()) }) { Text("सेव करें") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("बंद करें", color = Color(0xFFC084FC)) } }
    )
}
