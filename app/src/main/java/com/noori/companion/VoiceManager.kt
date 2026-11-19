package com.noori.companion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val hindiLocale = Locale("hi", "IN")
                val res = tts?.setLanguage(hindiLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("en", "IN"))
                }
                selectFemaleVoiceIfAvailable()
                tts?.setPitch(1.08f)
                tts?.setSpeechRate(0.98f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = onSpeakingStateChanged(true)
                    override fun onDone(utteranceId: String?) = onSpeakingStateChanged(false)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = onSpeakingStateChanged(false)
                })
                ttsReady = true
            }
        }
    }

    fun updateVoiceTuning(pitch: Float, rate: Float) {
        tts?.setPitch(pitch.coerceIn(0.7f, 1.5f))
        tts?.setSpeechRate(rate.coerceIn(0.7f, 1.5f))
    }

    private fun selectFemaleVoiceIfAvailable() {
        val voices = tts?.voices ?: return
        val preferred = voices.firstOrNull { voice ->
            val lang = voice.locale.language
            val name = voice.name.lowercase()
            (lang == "hi" || lang == "en") &&
                (name.contains("female") || name.contains("-f-") || name.contains("hia") || name.contains("hif"))
        }
        if (preferred != null) tts?.voice = preferred
    }

    fun speak(text: String) {
        if (!ttsReady) return
        val cleanText = text.replace("*", "").replace("#", "").trim()
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "noori_utterance_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        onSpeakingStateChanged(false)
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onSpeechError("आपके फ़ोन में Voice Recognition उपलब्ध नहीं है।")
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
                    onSpeechError("आवाज़ साफ सुनाई नहीं दी, कृपया फिर से बोलें।")
                }

                override fun onResults(results: Bundle?) {
                    onListeningStateChanged(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim()
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
