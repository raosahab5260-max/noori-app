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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
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
        setContent {
            NooriApp()
        }
    }

    override fun onStop() {
        super.onStop()
        val prefs = getSharedPreferences("noori_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("floating_enabled", false) && Settings.canDrawOverlays(this)) {
            val svc = Intent(this, FloatingCompanionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
        }
    }
}

@Composable
fun NooriApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("noori_prefs", Context.MODE_PRIVATE) }
    val memoryManager = remember { MemoryManager(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var provider by remember {
        mutableStateOf(
            if (prefs.getString("ai_provider", "GEMINI") == "OPENAI") AIProvider.OPENAI else AIProvider.GEMINI
        )
    }
    var geminiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var openAiKey by remember { mutableStateOf(prefs.getString("openai_api_key", "") ?: "") }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voice_enabled", true)) }
    var floatingEnabled by remember { mutableStateOf(prefs.getBoolean("floating_enabled", false)) }
    var nooriMood by remember { mutableStateOf(prefs.getString("noori_mood", "Caring & Playful") ?: "Caring & Playful") }

    var showSettings by remember { mutableStateOf(false) }
    var showSmartMode by remember { mutableStateOf(false) }
    var showMoodAndActions by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingPhoneAction?>(null) }

    var memories by remember { mutableStateOf(memoryManager.getMemories()) }
    var inputText by remember { mutableStateOf("") }
    var nooriState by remember { mutableStateOf(NooriState.IDLE) }

    fun nowLabel(): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                text = "Hello! Main Noori hoon 💜 Tum mujhse kuch bhi puch sakte ho, ya bolkar WhatsApp/YouTube khulwa sakte ho ✨",
                isUser = false,
                timeLabel = nowLabel()
            )
        )
    }

    lateinit var voiceManager: VoiceManager

    fun sendUserMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        messages.add(ChatMessage(text = clean, isUser = true, timeLabel = nowLabel()))

        val lower = clean.lowercase()
        if (lower.contains("time") || lower.contains("kitne baje") || clean.contains("समय") || clean.contains("बजे")) {
            val timeReply = PhoneAssistant.getCurrentTimeText()
            messages.add(ChatMessage(text = timeReply, isUser = false, timeLabel = nowLabel()))
            if (voiceEnabled) voiceManager.speak(timeReply)
            return
        }

        nooriState = NooriState.THINKING
        scope.launch {
            val result = AIClient.sendMessage(
                provider = provider,
                geminiKey = geminiKey,
                openAiKey = openAiKey,
                mood = nooriMood,
                history = messages.dropLast(1),
                memories = memories,
                userMessage = clean
            )
            when (result) {
                is AIResult.Success -> {
                    if (!result.extractedMemory.isNullOrEmpty() && memoryManager.addMemory(result.extractedMemory)) {
                        memories = memoryManager.getMemories()
                    }
                    if (!result.actionTag.isNullOrEmpty()) {
                        pendingAction = PhoneAssistant.parseActionTag(result.actionTag)
                    }
                    messages.add(ChatMessage(text = result.reply, isUser = false, timeLabel = nowLabel()))
                    if (voiceEnabled) voiceManager.speak(result.reply) else nooriState = NooriState.IDLE
                }
                is AIResult.Error -> {
                    messages.add(ChatMessage(text = result.message, isUser = false, timeLabel = nowLabel()))
                    nooriState = NooriState.IDLE
                }
            }
        }
    }

    voiceManager = remember {
        VoiceManager(
            context = context,
            onSpeakingStateChanged = { speaking -> nooriState = if (speaking) NooriState.SPEAKING else NooriState.IDLE },
            onListeningStateChanged = { listening -> nooriState = if (listening) NooriState.LISTENING else NooriState.IDLE },
            onSpeechResult = { spoken -> sendUserMessage(spoken) },
            onSpeechError = { err ->
                messages.add(ChatMessage(text = err, isUser = false, timeLabel = nowLabel()))
                nooriState = NooriState.IDLE
            }
        )
    }

    DisposableEffect(Unit) { onDispose { voiceManager.destroy() } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) voiceManager.startListening()
        else messages.add(ChatMessage(text = "Microphone अनुमति ज़रूरी है।", isUser = false, timeLabel = nowLabel()))
    }

    fun triggerMic() {
        if (nooriState == NooriState.SPEAKING) voiceManager.stopSpeaking()
        else {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) voiceManager.startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0514))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Hero Banner matching uploaded image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0xFF6B21A8), RoundedCornerShape(22.dp))
            ) {
                NooriHeroScene(state = nooriState, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(colors = listOf(Color(0xBB090414), Color.Transparent)))
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text("Noori", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                        Text("Your AI Companion 💜", color = Color(0xFFE9D5FF), fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xAA1F1035))
                            .border(1.dp, Color(0xFF9333EA), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = when (nooriState) {
                                NooriState.IDLE -> "💜 Noori Online"
                                NooriState.LISTENING -> "🎤 सुन रही हूँ..."
                                NooriState.THINKING -> "✨ सोच रही हूँ..."
                                NooriState.SPEAKING -> "🔊 बोल रही हूँ..."
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 22.dp)
                        .width(155.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xAA170B2B))
                        .border(1.dp, Color(0xFF7E22CE), RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Main Noori hoon 🌙\nTum mujhse kuch bhi baat kar sakte ho... Main hamesha tumhare saath hoon 💜✨",
                        color = Color(0xFFF3E8FF),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(56.dp)
                    .shadow(14.dp, CircleShape, ambientColor = Color(0xFFA855F7), spotColor = Color(0xFFA855F7))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (nooriState == NooriState.LISTENING)
                                listOf(Color(0xFF22D3EE), Color(0xFF0891B2))
                            else listOf(Color(0xFFC084FC), Color(0xFF6B21A8))
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable { triggerMic() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (nooriState == NooriState.SPEAKING) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Mic",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Today Divider
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Today",
                color = Color(0xFFD8B4FE),
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A0F30))
                    .border(1.dp, Color(0xFF4C1D95), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // Chat List
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!msg.isUser) {
                        Image(
                            painter = painterResource(id = R.drawable.noori_hero),
                            contentDescription = "Noori",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, Color(0xFFA855F7), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Column(horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 265.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (msg.isUser) Color(0xFF7E22CE) else Color(0xFF1B1030))
                                .border(1.dp, if (msg.isUser) Color(0xFFC084FC) else Color(0xFF4C1D95), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(text = msg.text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                        if (msg.timeLabel.isNotEmpty()) {
                            Text(
                                text = if (msg.isUser) "${msg.timeLabel} ✓✓" else msg.timeLabel,
                                color = Color(0xFF9F8EC7),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Input Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF241242))
                    .border(1.dp, Color(0xFF7E22CE), CircleShape)
                    .clickable { sendUserMessage("Hi Noori 💜") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Heart", tint = Color(0xFFE879F9), modifier = Modifier.size(20.dp))
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Noori se baat karo...", color = Color(0xFF9F8EC7), fontSize = 13.sp) },
                trailingIcon = {
                    IconButton(onClick = { inputText += " 😊" }) {
                        Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription = "Emoji", tint = Color(0xFFC084FC))
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFA855F7),
                    unfocusedBorderColor = Color(0xFF3B246B),
                    focusedContainerColor = Color(0xFF140B29),
                    unfocusedContainerColor = Color(0xFF140B29)
                )
            )

            IconButton(
                onClick = {
                    val msg = inputText
                    inputText = ""
                    sendUserMessage(msg)
                },
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF9333EA))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Exact 4 Bottom Navigation Pills from Reference Image
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF140B29))
                .border(1.dp, Color(0xFF4C1D95), RoundedCornerShape(24.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavPill(Icons.Default.Mic, "Voice Talk", active = nooriState == NooriState.LISTENING) { triggerMic() }
            NavPill(Icons.Default.Psychology, "Smart Mode", active = showSmartMode) { showSmartMode = true }
            NavPill(Icons.Default.Star, "Noori Mood", active = showMoodAndActions) { showMoodAndActions = true }
            NavPill(Icons.Default.Settings, "Settings", active = showSettings) { showSettings = true }
        }
    }

    // Pending Action Confirmation
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            containerColor = Color(0xFF1A1033),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE9D5FF),
            title = { Text(action.title) },
            text = { Text(action.description) },
            confirmButton = {
                Button(onClick = {
                    val res = PhoneAssistant.executeAction(context, action)
                    messages.add(ChatMessage(text = res, isUser = false, timeLabel = nowLabel()))
                    pendingAction = null
                }) { Text("हाँ, करें") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("रद्द करें", color = Color(0xFFC084FC)) }
            }
        )
    }

    // Smart Mode Dialog (Memory Manager + AI Provider info)
    if (showSmartMode) {
        var newFact by remember { mutableStateOf("") }
        var editingItem by remember { mutableStateOf<MemoryItem?>(null) }
        var editText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSmartMode = false },
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
                                memories = memoryManager.getMemories()
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
                                        memories = memoryManager.getMemories()
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
                                    IconButton(onClick = { memoryManager.deleteMemory(item.id); memories = memoryManager.getMemories() }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF472B6), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSmartMode = false }) { Text("बंद करें", color = Color(0xFFC084FC)) }
            }
        )
    }

    // Noori Mood + Phone Assistant Dialog
    if (showMoodAndActions) {
        val moods = listOf("Caring & Playful", "Sweet & Romantic", "Funny & Teasing", "Smart Assistant")
        AlertDialog(
            onDismissRequest = { showMoodAndActions = false },
            containerColor = Color(0xFF1A1033),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE9D5FF),
            title = { Text("⭐ Noori Mood & Quick Actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Noori का मूड चुनें:", fontSize = 12.sp)
                    moods.forEach { m ->
                        Button(
                            onClick = {
                                nooriMood = m
                                prefs.edit().putString("noori_mood", m).apply()
                                showMoodAndActions = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (nooriMood == m) Color(0xFF9333EA) else Color(0xFF26174A)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(m) }
                    }
                    Divider(color = Color(0xFF4C1D95))
                    Button(onClick = { showMoodAndActions = false; pendingAction = PendingPhoneAction.OpenApp("WhatsApp") }, modifier = Modifier.fillMaxWidth()) { Text("💬 WhatsApp खोलें") }
                    Button(onClick = { showMoodAndActions = false; pendingAction = PendingPhoneAction.OpenApp("YouTube") }, modifier = Modifier.fillMaxWidth()) { Text("▶️ YouTube खोलें") }
                    Button(onClick = { showMoodAndActions = false; pendingAction = PendingPhoneAction.SetAlarm(7, 0) }, modifier = Modifier.fillMaxWidth()) { Text("⏰ सुबह 7:00 का अलार्म") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoodAndActions = false }) { Text("बंद करें", color = Color(0xFFC084FC)) }
            }
        )
    }

    // Settings Dialog (Gemini + OpenAI + Voice + Home Screen Floating Overlay)
    if (showSettings) {
        var draftGemini by remember { mutableStateOf(geminiKey) }
        var draftOpenAi by remember { mutableStateOf(openAiKey) }
        var voicePitch by remember { mutableStateOf(prefs.getFloat("voice_pitch", 1.08f)) }
        val hasOverlayPerm = Settings.canDrawOverlays(context)
        val hasMicPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Color(0xFF1A1033),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE9D5FF),
            title = { Text("⚙️ Settings (Gemini + OpenAI)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AIProvider.entries.forEach { p ->
                            Button(
                                onClick = {
                                    provider = p
                                    prefs.edit().putString("ai_provider", p.name).apply()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (provider == p) Color(0xFF9333EA) else Color(0xFF26174A)
                                ),
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
                        Switch(checked = voiceEnabled, onCheckedChange = {
                            voiceEnabled = it
                            prefs.edit().putBoolean("voice_enabled", it).apply()
                        })
                    }
                    Text("Voice Pitch: ${String.format("%.2f", voicePitch)}", fontSize = 11.sp)
                    Slider(
                        value = voicePitch,
                        onValueChange = {
                            voicePitch = it
                            prefs.edit().putFloat("voice_pitch", it).apply()
                            voiceManager.updateVoiceTuning(it, 0.98f)
                        },
                        valueRange = 0.8f..1.4f
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Home Screen Floating Noori", fontSize = 12.sp)
                        Switch(
                            checked = floatingEnabled,
                            onCheckedChange = { wantOn ->
                                if (wantOn && !Settings.canDrawOverlays(context)) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    )
                                } else {
                                    floatingEnabled = wantOn
                                    prefs.edit().putBoolean("floating_enabled", wantOn).apply()
                                    val svc = Intent(context, FloatingCompanionService::class.java)
                                    if (wantOn) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc)
                                        else context.startService(svc)
                                    } else {
                                        context.stopService(svc)
                                    }
                                }
                            }
                        )
                    }
                    Text(
                        text = "Permissions: Mic ${if (hasMicPerm) "✓" else "✕"} • Overlay ${if (hasOverlayPerm) "✓" else "✕"}",
                        fontSize = 10.sp,
                        color = Color(0xFFC084FC)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    geminiKey = draftGemini.trim()
                    openAiKey = draftOpenAi.trim()
                    prefs.edit()
                        .putString("gemini_api_key", geminiKey)
                        .putString("openai_api_key", openAiKey)
                        .apply()
                    showSettings = false
                }) { Text("सेव करें") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("बंद करें", color = Color(0xFFC084FC)) }
            }
        )
    }
}

@Composable
private fun RowScope.NavPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
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
