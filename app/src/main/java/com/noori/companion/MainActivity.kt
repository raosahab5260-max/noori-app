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

        val prefs = getSharedPreferences("noori_prefs", Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        if (lastCrash != null) {
            prefs.edit().remove("last_crash").apply()
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            prefs.edit().putString("last_crash", throwable.stackTraceToString().take(800)).apply()
            defaultHandler?.uncaughtException(thread, throwable)
        }

        setContent {
            NooriApp(lastCrash = lastCrash)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            val prefs = getSharedPreferences("noori_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("floating_enabled", false) && Settings.canDrawOverlays(this)) {
                val svc = Intent(this, FloatingCompanionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
                else startService(svc)
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
fun NooriApp(lastCrash: String? = null) {
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
        mutableStateListOf<ChatMessage>().apply {
            if (!lastCrash.isNullOrBlank()) {
                add(ChatMessage(text = "पिछला क्रैश कारण:\n$lastCrash", isUser = false, timeLabel = nowLabel()))
            }
            add(
                ChatMessage(
                    text = "Hello! Main Noori hoon 💜 Tum mujhse kuch bhi puch sakte ho, ya bolkar WhatsApp/Instagram/YouTube khulwa sakte ho ✨",
                    isUser = false,
                    timeLabel = nowLabel()
                )
            )
        }
    }

    var voiceManager by remember { mutableStateOf<VoiceManager?>(null) }

    fun sendUserMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        messages.add(ChatMessage(text = clean, isUser = true, timeLabel = nowLabel()))

        val localAction = PhoneAssistant.tryParseLocalCommand(clean)
        if (localAction != null) {
            pendingAction = localAction
            return
        }

        val lower = clean.lowercase()
        if (lower.contains("time") || lower.contains("kitne baje") || clean.contains("समय") || clean.contains("बजे")) {
            val timeReply = PhoneAssistant.getCurrentTimeText()
            messages.add(ChatMessage(text = timeReply, isUser = false, timeLabel = nowLabel()))
            if (voiceEnabled) voiceManager?.speak(timeReply)
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
                    if (voiceEnabled) voiceManager?.speak(result.reply) else nooriState = NooriState.IDLE
                }
                is AIResult.Error -> {
                    messages.add(ChatMessage(text = result.message, isUser = false, timeLabel = nowLabel()))
                    nooriState = NooriState.IDLE
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val vm = try {
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
        } catch (_: Exception) {
            null
        }
        voiceManager = vm
        onDispose {
            try {
                vm?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) voiceManager?.startListening()
        else messages.add(ChatMessage(text = "Microphone अनुमति ज़रूरी है।", isUser = false, timeLabel = nowLabel()))
    }

    fun triggerMic() {
        if (nooriState == NooriState.SPEAKING) voiceManager?.stopSpeaking()
        else {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) voiceManager?.startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0514))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF140B29))
                .border(1.dp, Color(0xFF4C1D95), RoundedCornerShape(24.dp))
