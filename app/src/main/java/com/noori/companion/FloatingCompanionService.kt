package com.noori.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingCompanionService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    private var statusBadge: TextView? = null
    private var avatarView: ImageView? = null
    private var voiceManager: VoiceManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var pulseUp = true
    private var lastGreetingIdx = -1

    private val greetings = listOf(
        "आप आ गए! ❤️",
        "हेलो Ansh! आज कैसे हो?",
        "मैं आपका इंतज़ार कर रही थी!",
        "चलो, आज कुछ नया करते हैं!"
    )

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                val prefs = getSharedPreferences("noori_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("unlock_greeting", true)) {
                    var idx = (greetings.indices).random()
                    if (idx == lastGreetingIdx) idx = (idx + 1) % greetings.size
                    lastGreetingIdx = idx
                    val msg = greetings[idx]
                    statusBadge?.text = msg
                    voiceManager?.speak(msg)
                }
            }
        }
    }

    private val breatheRunnable = object : Runnable {
        override fun run() {
            avatarView?.animate()?.scaleX(if (pulseUp) 1.03f else 1.0f)?.scaleY(if (pulseUp) 1.03f else 1.0f)?.setDuration(950)?.start()
            pulseUp = !pulseUp
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))

        val memoryManager = MemoryManager(this)
        val prefs = getSharedPreferences("noori_prefs", Context.MODE_PRIVATE)

        voiceManager = VoiceManager(
            context = this,
            onSpeakingStateChanged = { s -> handler.post { statusBadge?.text = if (s) "🔊 बोल रही हूँ" else "💜 Noori" } },
            onListeningStateChanged = { l -> handler.post { statusBadge?.text = if (l) "🎤 सुन रही हूँ..." else "💜 Noori" } },
            onSpeechResult = { spoken ->
                val local = PhoneAssistant.tryParseLocalCommand(spoken)
                if (local != null) {
                    val reply = PhoneAssistant.executeAction(this, local)
                    voiceManager?.speak(reply)
                    return@VoiceManager
                }
                serviceScope.launch {
                    val res = AIClient.sendMessage(
                        prefs.getString("gemini_api_key", "") ?: "",
                        emptyList(),
                        memoryManager.getMemories(),
                        spoken
                    )
                    when (res) {
                        is AIResult.Success -> voiceManager?.speak(res.reply)
                        is AIResult.Error -> voiceManager?.speak(res.message)
                    }
                }
            },
            onSpeechError = { handler.post { statusBadge?.text = "🎤 फिर बोलें" } }
        )

        showOverlayBubble()
        handler.post(breatheRunnable)
    }

    private fun startAsForeground() {
        val ch = "noori_live_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(ch, "NOORI Live Companion", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, ch)
            .setContentTitle("Noori होम स्क्रीन पर है 💜")
            .setContentText("बात करने के लिए टैप करें")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(101, n)
    }

    private fun showOverlayBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            flag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 36; y = 220 }

        val d = resources.displayMetrics.density
        val sizeW = (92 * d).toInt()
        val sizeH = (164 * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val frame = FrameLayout(this)

        val avatar = ImageView(this).apply {
            setImageResource(R.drawable.noori_ref)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                cornerRadius = 16 * d
                setStroke((2f * d).toInt(), Color.parseColor("#A855F7"))
            }
            clipToOutline = true
        }
        avatarView = avatar
        frame.addView(avatar, FrameLayout.LayoutParams(sizeW, sizeH))

        val closeBtn = TextView(this).apply {
            text = "✕"; textSize = 10f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#CC1E1038")) }
            setOnClickListener { stopSelf() }
        }
        frame.addView(closeBtn, FrameLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt(), Gravity.TOP or Gravity.END))

        val badge = TextView(this).apply {
            text = "💜 Noori"; textSize = 10f; setTextColor(Color.WHITE)
            setPadding((8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 12 * d; setColor(Color.parseColor("#CC140B29")); setStroke((1 * d).toInt(), Color.parseColor("#A855F7")) }
        }
        statusBadge = badge
        root.addView(frame); root.addView(badge)

        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var moved = false
        avatar.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = ix + dx; params.y = iy + dy; windowManager?.updateViewLayout(root, params); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) voiceManager?.startListening(); true }
                else -> false
            }
        }
        floatingView = root
        windowManager?.addView(root, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(unlockReceiver) } catch (_: Exception) {}
        handler.removeCallbacks(breatheRunnable)
        serviceScope.cancel()
        voiceManager?.destroy()
        floatingView?.let { windowManager?.removeView(it) }
    }
}
