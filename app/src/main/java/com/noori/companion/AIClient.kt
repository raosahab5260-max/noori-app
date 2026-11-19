package com.noori.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class AIProvider(val label: String) {
    GEMINI("Gemini AI (Free Tier)"),
    OPENAI("OpenAI (API Credits)")
}

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val timeLabel: String = ""
)

sealed class AIResult {
    data class Success(
        val reply: String,
        val extractedMemory: String? = null,
        val actionTag: String? = null
    ) : AIResult()

    data class Error(val message: String) : AIResult()
}

object AIClient {
    private val GEMINI_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-3.7-flash",
        "gemini-flash-latest"
    )
    private const val OPENAI_MODEL = "gpt-4o-mini"

    private fun buildSystemPrompt(memories: List<MemoryItem>, mood: String): String {
        val memoryBlock = if (memories.isEmpty()) {
            "No saved memories yet."
        } else {
            memories.joinToString("\n") { "- ${it.fact}" }
        }

        return """
You are NOORI, a caring, intelligent, playful, and slightly teasing female AI companion for Android.
Current mood style: $mood.
Speak naturally in Hindi or Hinglish (matching the user's style), never robotic.
Answer any question the user asks clearly, warmly, and concisely (2 to 4 sentences unless asked for detail).

Saved user memories:
$memoryBlock

Optional tags you may append at the VERY END of your reply:
1) If the user shares a helpful non-sensitive personal fact or preference, append: [SAVE_MEMORY: short fact]
2) If the user asks to open an app, set an alarm, set a timer, or open phone settings, append ONE tag:
   - [ACTION: OPEN_APP:AppName]
   - [ACTION: SET_ALARM:HH-MM]
   - [ACTION: SET_TIMER:Minutes]
   - [ACTION: OPEN_SETTINGS:wifi|bluetooth|display|sound|main]
""".trim()
    }

    suspend fun sendMessage(
        provider: AIProvider,
        geminiKey: String,
        openAiKey: String,
        mood: String,
        history: List<ChatMessage>,
        memories: List<MemoryItem>,
        userMessage: String
    ): AIResult = withContext(Dispatchers.IO) {
        val sysPrompt = buildSystemPrompt(memories, mood)
        when (provider) {
            AIProvider.GEMINI -> callGeminiWithFallback(geminiKey.trim(), sysPrompt, history, userMessage)
            AIProvider.OPENAI -> callOpenAI(openAiKey.trim(), sysPrompt, history, userMessage)
        }
    }

    private fun parseTags(rawReply: String): AIResult.Success {
        val memRegex = Regex("""\[SAVE_MEMORY:\s*(.+?)\]""")
        val actRegex = Regex("""\[ACTION:\s*(.+?)\]""")
        val extractedMem = memRegex.find(rawReply)?.groupValues?.getOrNull(1)?.trim()
        val extractedAct = actRegex.find(rawReply)?.groupValues?.getOrNull(1)?.trim()
        var cleanReply = memRegex.replace(rawReply, "")
        cleanReply = actRegex.replace(cleanReply, "").trim()
        return AIResult.Success(cleanReply, extractedMem, extractedAct)
    }

    private fun callGeminiWithFallback(
        apiKey: String,
        sysPrompt: String,
        history: List<ChatMessage>,
        userMessage: String
    ): AIResult {
        if (apiKey.isEmpty()) {
            return AIResult.Error("कृपया Settings (⚙️) में जाकर अपनी Gemini API Key डालें।")
        }

        var lastError: AIResult.Error? = null
        for (model in GEMINI_MODELS) {
            val res = callGeminiModel(model, apiKey, sysPrompt, history, userMessage)
            if (res is AIResult.Success) return res
            if (res is AIResult.Error) {
                lastError = res
                val msg = res.message.lowercase()
                if (!msg.contains("404") && !msg.contains("503") && !msg.contains("timeout")) {
                    return res
                }
            }
        }
        return lastError ?: AIResult.Error("Gemini से जवाब नहीं मिल पाया।")
    }

    private fun callGeminiModel(
        model: String,
        apiKey: String,
        sysPrompt: String,
        history: List<ChatMessage>,
        userMessage: String
    ): AIResult {
        var conn: HttpURLConnection? = null
        return try {
            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey)
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
            }

            val contentsArray = JSONArray()
            history.takeLast(12).forEach { msg ->
                contentsArray.put(
                    JSONObject()
                        .put("role", if (msg.isUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                )
            }
            contentsArray.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
            )

            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysPrompt))))
                .put("contents", contentsArray)

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                return AIResult.Error("Gemini Error ($code): ${msg ?: "API Key या इंटरनेट जांचें।"}")
            }

            val raw = JSONObject(text).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text")?.trim()

            if (raw.isNullOrEmpty()) AIResult.Error("Noori जवाब नहीं बना पाई, फिर से पूछें।")
            else parseTags(raw)
        } catch (e: Exception) {
            AIResult.Error("इंटरनेट समस्या: ${e.localizedMessage ?: "Unknown"}")
        } finally {
            conn?.disconnect()
        }
    }

    private fun callOpenAI(
        apiKey: String,
        sysPrompt: String,
        history: List<ChatMessage>,
        userMessage: String
    ): AIResult {
        if (apiKey.isEmpty()) {
            return AIResult.Error("कृपया Settings (⚙️) में जाकर अपनी OpenAI API Key डालें।")
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
            }

            val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sysPrompt))
            history.takeLast(12).forEach { msg ->
                msgs.put(JSONObject().put("role", if (msg.isUser) "user" else "assistant").put("content", msg.text))
            }
            msgs.put(JSONObject().put("role", "user").put("content", userMessage))

            val body = JSONObject().put("model", OPENAI_MODEL).put("messages", msgs)
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (code !in 200..299) {
                return AIResult.Error("OpenAI Error ($code): अपनी API Key या क्रेडिट्स जांचें।")
            }

            val raw = JSONObject(text).optJSONArray("choices")
                ?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content")?.trim()

            if (raw.isNullOrEmpty()) AIResult.Error("Noori जवाब नहीं बना पाई, फिर से पूछें।")
            else parseTags(raw)
        } catch (e: Exception) {
            AIResult.Error("इंटरनेट समस्या: ${e.localizedMessage ?: "Unknown"}")
        } finally {
            conn?.disconnect()
        }
    }
}
