package com.noori.companion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onSpeechResult: (String) -> Unit,
    private val onSpeechError: (String) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("hi", "IN"))
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("en", "IN"))
                }
                selectSoftFemaleVoice()
                // सॉफ्ट, मीठी और नैचुरल आवाज़ के लिए हल्की धीमी गति और नैचुरल पिच
                tts?.setPitch(1.04f)
                tts?.setSpeechRate(0.92f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = onSpeakingStateChanged(true)
                    override fun onDone(utteranceId: String?) = onSpeakingStateChanged(false)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = onSpeakingStateChanged(false)
                })
                ttsReady = true
            }
        }, "com.google.android.tts")
    }

    private fun selectSoftFemaleVoice() {
        val voices = tts?.voices ?: return
        val hindiVoices = voices.filter {
            it.locale.language == "hi" && it.quality >= Voice.QUALITY_NORMAL
        }
        // सबसे पहले Google की Neural / Network Female Hindi वॉइस चुनें
        val best = hindiVoices.firstOrNull { v ->
            val n = v.name.lowercase()
            (n.contains("network") || v.quality >= Voice.QUALITY_HIGH) &&
                (n.contains("hia") || n.contains("hic") || n.contains("hie") || n.contains("female") || n.contains("-f-"))
        } ?: hindiVoices.firstOrNull { v ->
            val n = v.name.lowercase()
            n.contains("hia") || n.contains("hic") || n.contains("female") || n.contains("-f-")
        } ?: hindiVoices.maxByOrNull { it.quality }

        if (best != null) {
            tts?.voice = best
        }
    }

    fun updateVoiceTuning(pitch: Float, rate: Float) {
        tts?.setPitch(pitch.coerceIn(0.8f, 1.3f))
        tts?.setSpeechRate(rate.coerceIn(0.75f, 1.2f))
    }

    fun speak(text: String) {
        if (!ttsReady) return
        // इमोजी और सिंबल हटाएं ताकि आवाज़ बिल्कुल साफ और नैचुरल बोले
        val clean = text
            .replace(Regex("[\\p{So}\\p{Cn}*#]"), " ")
            .replace("!", ", ")
            .replace("...", ", ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isNotEmpty()) {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "noori_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        onSpeakingStateChanged(false)
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onSpeechError("Voice Recognition उपलब्ध नहीं है।")
            return
        }
        stopSpeaking()
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onListeningStateChanged(true)
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() = onListeningStateChanged(false)
                override fun onError(error: Int) {
                    onListeningStateChanged(false)
                    onSpeechError("आवाज़ साफ नहीं आई, फिर बोलें।")
                }
                override fun onResults(results: Bundle?) {
                    onListeningStateChanged(false)
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (!spoken.isNullOrEmpty()) onSpeechResult(spoken)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
