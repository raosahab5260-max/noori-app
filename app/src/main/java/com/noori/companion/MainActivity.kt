package com.noori.companion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NooriApp() }
    }

    override fun onStop() {
        super.onStop()
        try {
            val prefs = getSharedPreferences("noori_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("floating_enabled", false) && Settings.canDrawOverlays(this)) {
                val svc = Intent(this, FloatingCompanionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
            }
        } catch (_: Exception) {}
    }
}

@Composable
fun NooriApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("noori_prefs", Context.MODE_PRIVATE) }
    val memoryManager = remember { MemoryManager(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var apiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voice_enabled", true)) }
    var unlockGreeting by remember { mutableStateOf(prefs.getBoolean("unlock_greeting", true)) }
    var floatingEnabled by remember { mutableStateOf(prefs.getBoolean("floating_enabled", false)) }

    var showSettings by remember { mutableStateOf(false) }
    var showSmartMode by remember { mutableStateOf(false) }
    var showMood by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingPhoneAction?>(null) }

    var memories by remember { mutableStateOf(memoryManager.getMemories()) }
    var inputText by remember { mutableStateOf("") }
    var nooriState by remember { mutableStateOf(NooriState.IDLE) }

    fun nowLabel(): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    val messages = remember {
        mutableStateListOf(
            ChatMessage(text = "Hello Ansh! Main Noori hoon 💜 Tum mujhse kuch bhi baat kar sakte ho ✨", isUser = false)
        )
    }
    var voiceManager by remember { mutableStateOf<VoiceManager?>(null) }

    fun sendUserMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        messages.add(ChatMessage(text = clean, isUser = true))

        val local = PhoneAssistant.tryParseLocalCommand(clean)
        if (local != null) { pendingAction = local; return }

        val lower = clean.lowercase()
        if (lower.contains("time") || clean.contains("समय") || clean.contains("बजे")) {
            val t = PhoneAssistant.getCurrentTimeText()
            messages.add(ChatMessage(text = t, isUser = false))
            if (voiceEnabled) voiceManager?.speak(t)
            return
        }

        nooriState = NooriState.THINKING
        scope.launch {
            when (val res = AIClient.sendMessage(apiKey, messages.dropLast(1), memories, clean)) {
                is AIResult.Success -> {
                    if (!res.extractedMemory.isNullOrEmpty() && memoryManager.addMemory(res.extractedMemory)) {
                        memories = memoryManager.getMemories()
                    }
                    if (!res.actionTag.isNullOrEmpty()) pendingAction = PhoneAssistant.parseActionTag(res.actionTag)
                    messages.add(ChatMessage(text = res.reply, isUser = false))
                    if (voiceEnabled) voiceManager?.speak(res.reply) else nooriState = NooriState.IDLE
                }
                is AIResult.Error -> {
                    messages.add(ChatMessage(text = res.message, isUser = false))
                    nooriState = NooriState.IDLE
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val vm = try {
            VoiceManager(
                context,
                onSpeakingStateChanged = { s -> nooriState = if (s) NooriState.SPEAKING else NooriState.IDLE },
                onListeningStateChanged = { l -> nooriState = if (l) NooriState.LISTENING else NooriState.IDLE },
                onSpeechResult = { sendUserMessage(it) },
                onSpeechError = { err -> messages.add(ChatMessage(text = err, isUser = false)); nooriState = NooriState.IDLE }
            )
        } catch (_: Exception) { null }
        voiceManager = vm
        onDispose { try { vm?.destroy() } catch (_: Exception) {} }
    }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) voiceManager?.startListening() else messages.add(ChatMessage(text = "Microphone अनुमति ज़रूरी है।", isUser = false))
    }

    fun triggerMic() {
        if (nooriState == NooriState.SPEAKING) voiceManager?.stopSpeaking()
        else {
            val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (ok) voiceManager?.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0514)).statusBarsPadding().navigationBarsPadding()
    ) {
        // Top Photo-1 Hero Banner
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(225.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0xFF6B21A8), RoundedCornerShape(22.dp))
            ) {
                NooriHeroScene(state = nooriState, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xBB090414), Color.Transparent))))

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Noori", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                        Text("Your AI Companion 💜", color = Color(0xFFE9D5FF), fontSize = 11.sp)
                    }
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xAA1F1035)).border(1.dp, Color(0xFF9333EA), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            when (nooriState) {
                                NooriState.IDLE -> "💜 Noori Online"
                                NooriState.LISTENING -> "🎤 सुन रही हूँ..."
                                NooriState.THINKING -> "✨ सोच रही हूँ..."
                                NooriState.SPEAKING -> "🔊 बोल रही हूँ..."
                            },
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium
                        )
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                    }
                }

                Box(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 22.dp).width(155.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Color(0xAA170B2B)).border(1.dp, Color(0xFF7E22CE), RoundedCornerShape(14.dp)).padding(10.dp)
                ) {
                    Text("Main Noori hoon 🌙\nTum mujhse kuch bhi baat kar sakte ho... Main hamesha tumhare saath hoon 💜✨", color = Color(0xFFF3E8FF), fontSize = 10.sp, lineHeight = 14.sp)
                }
            }

            Box(
                modifier = Modifier.align(Alignment.BottomCenter).size(56.dp).shadow(14.dp, CircleShape, ambientColor = Color(0xFFA855F7), spotColor = Color(0xFFA855F7))
                    .clip(CircleShape).background(Brush.radialGradient(if (nooriState == NooriState.LISTENING) listOf(Color(0xFF22D3EE), Color(0xFF0891B2)) else listOf(Color(0xFFC084FC), Color(0xFF6B21A8))))
                    .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape).clickable { triggerMic() },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (nooriState == NooriState.SPEAKING) Icons.Default.Stop else Icons.Default.Mic, "Mic", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        // Today Divider
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
            Text("Today", color = Color(0xFFD8B4FE), fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A0F30)).border(1.dp, Color(0xFF4C1D95), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 2.dp))
        }

        // Chat List
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(messages, key = { it.id }) { msg ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                    if (!msg.isUser) {
                        Image(painter = painterResource(id = R.drawable.noori_hero), contentDescription = "Noori", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, Color(0xFFA855F7), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Column(horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
                        Box(
                            modifier = Modifier.widthIn(max = 265.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (msg.isUser) Color(0xFF7E22CE) else Color(0xFF1B1030))
                                .border(1.dp, if (msg.isUser) Color(0xFFC084FC) else Color(0xFF4C1D95), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(msg.text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp) }
                        Text(if (msg.isUser) "${nowLabel()} ✓✓" else nowLabel(), color = Color(0xFF9F8EC7), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp))
                    }
                }
            }
        }

        // Input Row
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF241242)).border(1.dp, Color(0xFF7E22CE), CircleShape).clickable { sendUserMessage("Hi Noori 💜") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Favorite, "Heart", tint = Color(0xFFE879F9), modifier = Modifier.size(20.dp))
            }
            OutlinedTextField(
                value = inputText, onValueChange = { inputText = it },
                placeholder = { Text("Noori se baat karo...", color = Color(0xFF9F8EC7), fontSize = 13.sp) },
                trailingIcon = { IconButton(onClick = { inputText += " 😊" }) { Icon(Icons.Default.SentimentSatisfiedAlt, "Emoji", tint = Color(0xFFC084FC)) } },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFA855F7), unfocusedBorderColor = Color(0xFF3B246B), focusedContainerColor = Color(0xFF140B29), unfocusedContainerColor = Color(0xFF140B29))
            )
            IconButton(onClick = { val m = inputText; inputText = ""; sendUserMessage(m) }, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF9333EA))) {
                Icon(Icons.Default.Send, "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Bottom 4 Pills matching Photo 1
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF140B29)).border(1.dp, Color(0xFF4C1D95), RoundedCornerShape(24.dp)).padding(6.dp)) {
            NavPill(Icons.Default.Mic, "Voice Talk", nooriState == NooriState.LISTENING) { triggerMic() }
            NavPill(Icons.Default.Psychology, "Smart Mode", showSmartMode) { showSmartMode = true }
            NavPill(Icons.Default.Star, "Noori Mood", showMood) { showMood = true }
            NavPill(Icons.Default.Settings, "Settings", showSettings) { showSettings = true }
        }
    }

    pendingAction?.let { act ->
        AlertDialog(
            onDismissRequest = { pendingAction = null }, containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text(act.title) }, text = { Text(act.description) },
            confirmButton = { Button(onClick = { messages.add(ChatMessage(text = PhoneAssistant.executeAction(context, act), isUser = false)); pendingAction = null }) { Text("हाँ, करें") } },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("रद्द करें") } }
        )
    }

    if (showSmartMode) {
        var memOn by remember { mutableStateOf(memoryManager.isMemoryEnabled()) }
        AlertDialog(
            onDismissRequest = { showSmartMode = false }, containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
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
                                IconButton(onClick = { memoryManager.deleteMemory(item.id); memories = memoryManager.getMemories() }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFF472B6))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { memoryManager.clearAll(); memories = emptyList() }) { Text("Clear All", color = Color(0xFFF472B6)) }
                }
            },
            confirmButton = { TextButton(onClick = { showSmartMode = false }) { Text("बंद करें") } }
        )
    }

    if (showMood) {
        AlertDialog(
            onDismissRequest = { showMood = false }, containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text("⭐ Quick Phone Actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showMood = false; pendingAction = PendingPhoneAction.OpenApp("WhatsApp") }, modifier = Modifier.fillMaxWidth()) { Text("💬 WhatsApp खोलें") }
                    Button(onClick = { showMood = false; pendingAction = PendingPhoneAction.OpenApp("Instagram") }, modifier = Modifier.fillMaxWidth()) { Text("📸 Instagram खोलें") }
                    Button(onClick = { showMood = false; pendingAction = PendingPhoneAction.OpenApp("YouTube") }, modifier = Modifier.fillMaxWidth()) { Text("▶️ YouTube खोलें") }
                    Button(onClick = { showMood = false; pendingAction = PendingPhoneAction.SetAlarm(7, 0) }, modifier = Modifier.fillMaxWidth()) { Text("⏰ सुबह 7:00 का अलार्म") }
                }
            },
            confirmButton = { TextButton(onClick = { showMood = false }) { Text("बंद करें") } }
        )
    }

    if (showSettings) {
        var draftKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showSettings = false }, containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text("⚙️ Settings (₹0 Free)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = draftKey, onValueChange = { draftKey = it }, label = { Text("Free Gemini API Key") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Voice Output", fontSize = 12.sp); Switch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it; prefs.edit().putBoolean("voice_enabled", it).apply() })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Cent
