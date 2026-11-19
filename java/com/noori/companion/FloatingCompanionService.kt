package com.noori.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.view.View
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

    private val breatheRunnable = object : Runnable {
        override fun run() {
            avatarView?.animate()
                ?.scaleX(if (pulseUp) 1.05f else 1.0f)
                ?.scaleY(if (pulseUp) 1.05f else 1.0f)
                ?.setDuration(900)
                ?.start()
            pulseUp = !pulseUp
            handler.postDelayed(this, 950)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val memoryManager = MemoryManager(this)
        val prefs = getSharedPreferences("noori_prefs", Context.MODE_PRIVATE)

        voiceManager = VoiceManager(
            context = this,
            onSpeakingStateChanged = { speaking ->
                handler.post {
                    statusBadge?.text = if (speaking) "🔊 बोल रही हूँ" else "💜 Noori"
                }
            },
            onListeningStateChanged = { listening ->
                handler.post {
                    statusBadge?.text = if (listening) "🎤 सुन रही हूँ..." else "💜 Noori"
                }
            },
            onSpeechResult = { spoken ->
                handler.post { statusBadge?.text = "✨ सोच रही हूँ..." }
                val lower = spoken.lowercase()
                if (lower.contains("time") || spoken.contains("समय") || spoken.contains("बजे")) {
                    val t = PhoneAssistant.getCurrentTimeText()
                    voiceManager?.speak(t)
                    return@VoiceManager
                }

                serviceScope.launch {
                    val provider = if (prefs.getString("ai_provider", "GEMINI") == "OPENAI")
                        AIProvider.OPENAI else AIProvider.GEMINI
                    val result = AIClient.sendMessage(
                        provider = provider,
                        geminiKey = prefs.getString("gemini_api_key", "") ?: "",
                        openAiKey = prefs.getString("openai_api_key", "") ?: "",
                        mood = prefs.getString("noori_mood", "Caring & Playful") ?: "Caring & Playful",
                        history = emptyList(),
                        memories = memoryManager.getMemories(),
                        userMessage = spoken
                    )
                    when (result) {
                        is AIResult.Success -> {
                            if (!result.extractedMemory.isNullOrEmpty()) {
                                memoryManager.addMemory(result.extractedMemory)
                            }
                            if (!result.actionTag.isNullOrEmpty()) {
                                val act = PhoneAssistant.parseActionTag(result.actionTag)
                                if (act != null) {
                                    val msg = PhoneAssistant.executeAction(this@FloatingCompanionService, act)
                                    voiceManager?.speak(msg)
                                    return@launch
                                }
                            }
                            voiceManager?.speak(result.reply)
                        }
                        is AIResult.Error -> voiceManager?.speak(result.message)
                    }
                }
            },
            onSpeechError = { err ->
                handler.post { statusBadge?.text = "🎤 फिर बोलें" }
            }
        )

        showOverlayBubble()
        handler.post(breatheRunnable)
    }

    private fun startAsForeground() {
        val channelId = "noori_floating_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "NOORI Home Companion",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Noori होम स्क्रीन पर सक्रिय है 💜")
            .setContentText("बोलने के लिए बबल पर टैप करें")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun showOverlayBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 260
        }

        val density = resources.displayMetrics.density
        val sizePx = (72 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val frame = FrameLayout(this)
        val avatar = ImageView(this).apply {
            setImageResource(R.drawable.noori_hero)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((3f * density).toInt(), Color.parseColor("#A855F7"))
            }
            clipToOutline = true
        }
        avatarView = avatar
        frame.addView(avatar, FrameLayout.LayoutParams(sizePx, sizePx))

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC1E1038"))
            }
            setOnClickListener { stopSelf() }
        }
        val closeSize = (22 * density).toInt()
        frame.addView(closeBtn, FrameLayout.LayoutParams(closeSize, closeSize, Gravity.TOP or Gravity.END))

        val micBadge = TextView(this).apply {
            text = "🎤"
            textSize = 11f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#9333EA"))
            }
            setOnClickListener { voiceManager?.startListening() }
        }
        val micSize = (24 * density).toInt()
        frame.addView(micBadge, FrameLayout.LayoutParams(micSize, micSize, Gravity.BOTTOM or Gravity.END))

        val badge = TextView(this).apply {
            text = "💜 Noori"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.parseColor("#CC140B29"))
                setStroke((1 * density).toInt(), Color.parseColor("#A855F7"))
            }
        }
        statusBadge = badge

        root.addView(frame)
        root.addView(badge)

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        avatar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        voiceManager?.startListening()
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = root
        windowManager?.addView(root, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(breatheRunnable)
        serviceScope.cancel()
        voiceManager?.destroy()
        floatingView?.let { windowManager?.removeView(it) }
        floatingView = null
    }
}
