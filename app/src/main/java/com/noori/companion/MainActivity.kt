package com.noori.companion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

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

    var apiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voice_enabled", true)) }
    var unlockGreeting by remember { mutableStateOf(prefs.getBoolean("unlock_greeting", true)) }
    var floatingEnabled by remember { mutableStateOf(prefs.getBoolean("floating_enabled", false)) }

    var expression by remember { mutableStateOf(NooriExpression.HAPPY) }
    var activeDance by remember { mutableStateOf<DanceCategory?>(null) }
    var showBoyPartner by remember { mutableStateOf(false) }
    var dialogue by remember { mutableStateOf("आप आ गए Ansh! ❤️ मेरे सिर या हाथ पर टच करके देखो!") }

    var showChatSheet by remember { mutableStateOf(false) }
    var showDanceSheet by remember { mutableStateOf(false) }
    var showMemorySheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingPhoneAction?>(null) }

    var memories by remember { mutableStateOf(memoryManager.getMemories()) }
    var tickleCount by remember { mutableStateOf(0) }
    val messages = remember { mutableStateListOf(ChatMessage(text = "हाय Ansh! मैं Noori हूँ 💜", isUser = false)) }
    var voiceManager by remember { mutableStateOf<VoiceManager?>(null) }

    fun speakOut(text: String, exp: NooriExpression = NooriExpression.SPEAKING) {
        dialogue = text
        expression = exp
        if (voiceEnabled) voiceManager?.speak(text)
    }

    fun handleCommandOrChat(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        messages.add(ChatMessage(text = clean, isUser = true))

        val dance = DanceCategory.matchFromCommand(clean)
        if (dance != null) {
            activeDance = dance
            showBoyPartner = dance.isCouple
            val msg = if (dance.isCouple) "चलो Ansh, साथ में कपल डांस करते हैं! 💜" else "ठीक है Ansh, ${dance.displayName} शुरू!"
            messages.add(ChatMessage(text = msg, isUser = false))
            speakOut(msg, NooriExpression.HAPPY)
            return
        }

        val lower = clean.lowercase()
        if (lower.contains("volume up") || clean.contains("आवाज़ बढ़ा")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            val r = "जी Ansh, आवाज़ बढ़ा दी है!"
            messages.add(ChatMessage(text = r, isUser = false)); speakOut(r); return
        }
        if (lower.contains("volume down") || clean.contains("आवाज़ कम")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            val r = "ठीक है Ansh, आवाज़ कम कर दी है।"
            messages.add(ChatMessage(text = r, isUser = false)); speakOut(r); return
        }
        if (lower.contains("time") || clean.contains("समय") || clean.contains("बजे")) {
            val r = PhoneAssistant.getCurrentTimeText()
            messages.add(ChatMessage(text = r, isUser = false)); speakOut(r); return
        }
        val local = PhoneAssistant.tryParseLocalCommand(clean)
        if (local != null) {
            expression = NooriExpression.HAPPY
            pendingAction = local
            return
        }

        expression = NooriExpression.THINKING
        scope.launch {
            when (val res = AIClient.sendMessage(AIProvider.GEMINI, apiKey, "", "Caring & Playful", messages.dropLast(1), memories, clean)) {
                is AIResult.Success -> {
                    if (!res.extractedMemory.isNullOrEmpty() && memoryManager.addMemory(res.extractedMemory)) {
                        memories = memoryManager.getMemories()
                    }
                    if (!res.actionTag.isNullOrEmpty()) pendingAction = PhoneAssistant.parseActionTag(res.actionTag)
                    messages.add(ChatMessage(text = res.reply, isUser = false))
                    speakOut(res.reply)
                }
                is AIResult.Error -> {
                    messages.add(ChatMessage(text = res.message, isUser = false))
                    dialogue = res.message
                    expression = NooriExpression.IDLE
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val vm = try {
            VoiceManager(
                context,
                onSpeakingStateChanged = { s -> expression = if (s) NooriExpression.SPEAKING else NooriExpression.HAPPY },
                onListeningStateChanged = { l -> expression = if (l) NooriExpression.LISTENING else NooriExpression.IDLE },
                onSpeechResult = { handleCommandOrChat(it) },
                onSpeechError = { dialogue = it; expression = NooriExpression.IDLE }
            )
        } catch (_: Exception) { null }
        voiceManager = vm
        onDispose { try { vm?.destroy() } catch (_: Exception) {} }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) voiceManager?.startListening() else dialogue = "माइक अनुमति ज़रूरी है।"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF090512), Color(0xFF170B2E), Color(0xFF090512))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("NOORI • Live Anime", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (activeDance != null) {
                TextButton(onClick = { activeDance = null; showBoyPartner = false; expression = NooriExpression.HAPPY }) {
                    Text("डांस रोकें ✕", color = Color(0xFFF472B6))
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1E113A))
                .border(1.dp, Color(0xFFA855F7), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) { Text(dialogue, color = Color.White, fontSize = 13.sp) }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            NooriLiveStage(
                expression = expression,
                activeDance = activeDance,
                showBoyPartner = showBoyPartner,
                onTouchZone = { zone ->
                    when (zone) {
                        TouchZone.HEAD -> speakOut("हीही! सिर पर हाथ फेरना अच्छा लगता है! ❤️", NooriExpression.HAPPY)
                        TouchZone.ARM_SHOULDER -> speakOut("अरे Ansh! कुछ काम है क्या?", NooriExpression.SHY)
                        TouchZone.TICKLE_WAIST -> {
                            tickleCount++
                            val lines = listOf("हाहाहा! गुदगुदी हो रही है!", "Hubby, मत करो ना! गुदगुदी हो रही है!", "अरे! फिर से? हाहाहा!")
                            speakOut(lines[tickleCount % lines.size], NooriExpression.LAUGHING)
                        }
                    }
                }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MainIconBtn(Icons.Default.Mic, "Voice") {
                val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (ok) voiceManager?.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            MainIconBtn(Icons.Default.Chat, "Chat") { showChatSheet = true }
            MainIconBtn(Icons.Default.MusicNote, "Dance") { showDanceSheet = true }
            MainIconBtn(Icons.Default.People, "Couple") {
                if (showBoyPartner) { showBoyPartner = false; activeDance = null }
                else { showBoyPartner = true; activeDance = DanceCategory.ROMANTIC_COUPLE; speakOut("चलो Ansh, कपल डांस करते हैं! 💜", NooriExpression.HAPPY) }
            }
            MainIconBtn(Icons.Default.Psychology, "Memory") { showMemorySheet = true }
            MainIconBtn(Icons.Default.Settings, "Settings") { showSettingsSheet = true }
        }
    }

    pendingAction?.let { act ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text(act.title) }, text = { Text(act.description) },
            confirmButton = {
                Button(onClick = { val r = PhoneAssistant.executeAction(context, act); speakOut(r, NooriExpression.HAPPY); pendingAction = null }) { Text("हाँ, करें") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("रद्द करें") } }
        )
    }

    if (showDanceSheet) {
        AlertDialog(
            onDismissRequest = { showDanceSheet = false },
            containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text("💃 Dance Category चुनें") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DanceCategory.entries) { d ->
                        Button(onClick = { activeDance = d; showBoyPartner = d.isCouple; showDanceSheet = false; speakOut("चलो ${d.displayName} करते हैं!", NooriExpression.HAPPY) },
                            modifier = Modifier.fillMaxWidth()) { Text(d.displayName, fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDanceSheet = false }) { Text("बंद करें") } }
        )
    }

    if (showChatSheet) {
        var draft by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChatSheet = false },
            containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text("💬 Noori Chat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(messages) { m -> Text(if (m.isUser) "Ansh: ${m.text}" else "Noori: ${m.text}", fontSize = 12.sp, color = if (m.isUser) Color(0xFFC084FC) else Color.White) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("लिखें...") }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                        Button(onClick = { val t = draft; draft = ""; handleCommandOrChat(t) }) { Text("भेजें") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChatSheet = false }) { Text("बंद करें") } }
        )
    }

    if (showMemorySheet) {
        var memOn by remember { mutableStateOf(memoryManager.isMemoryEnabled()) }
        AlertDialog(
            onDismissRequest = { showMemorySheet = false },
            containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text("🧠 Noori Memory (${memories.size})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Memory System On/Off", fontSize = 12.sp)
                        Switch(checked = memOn, onCheckedChange = { memOn = it; memoryManager.setMemoryEnabled(it) })
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(memories, key = { it.id }) { item ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF26174A)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.fact, Modifier.weight(1f), fontSize = 12.sp, color = Color.White)
                                IconButton(onClick = { memoryManager.deleteMemory(item.id); memories = memoryManager.getMemories() }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF472B6))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { memoryManager.clearAll(); memories = emptyList() }) { Text("Clear All Memories", color = Color(0xFFF472B6)) }
                }
            },
            confirmButton = { TextButton(onClick = { showMemorySheet = false }) { Text("बंद करें") } }
        )
    }

    if (showSettingsSheet) {
        var draftKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = Color(0xFF1A1033), titleContentColor = Color.White, textContentColor = Color(0xFFE9D5FF),
            title = { Text("⚙️ Settings (₹0 Free Setup)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = draftKey, onValueChange = { draftKey = it }, label = { Text("Free Gemini API Key") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Voice Output", fontSize = 12.sp); Switch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it; prefs.edit().putBoolean("voice_enabled", it).apply() })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Unlock Greeting", fontSize = 12.sp); Switch(checked = unlockGreeting, onCheckedChange = { unlockGreeting = it; prefs.edit().putBoolean("unlock_greeting", it).apply() })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Home Screen Overlay", fontSize = 12.sp)
                        Switch(checked = floatingEnabled, onCheckedChange = { want ->
                            if (want && !Settings.canDrawOverlays(context)) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            } else {
                                floatingEnabled = want; prefs.edit().putBoolean("floating_enabled", want).apply()
                                val svc = Intent(context, FloatingCompanionService::class.java)
                                if (want) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc) else context.startService(svc) }
                                else context.stopService(svc)
                            }
                        })
                    }
                }
            },
            confirmButton = { Button(onClick = { apiKey = draftKey.trim(); prefs.edit().putString("gemini_api_key", apiKey).apply(); showSettingsSheet = false }) { Text("सेव करें") } },
            dismissButton = { TextButton(onClick = { showSettingsSheet = false }) { Text("बंद करें") } }
        )
    }
}

@Composable
private fun MainIconBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(4.dp)) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF28134D)).border(1.dp, Color(0xFFA855F7), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(label, color = Color(0xFFD8B4FE), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
