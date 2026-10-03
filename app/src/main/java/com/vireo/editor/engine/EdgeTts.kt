package com.vireo.editor.engine

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Natural, human-sounding text-to-speech using Microsoft's free neural voices -
 * the same engine behind Edge "Read Aloud".
 *
 * Why this over [TtsEngine] (Android's built-in):
 *  - 106 curated neural voices vs whatever the handset ships with
 *  - genuinely human prosody instead of the robotic default
 *  - identical output on every device, so a project sounds the same everywhere
 *  - 10 Indian languages including Telugu, Hindi, Tamil, Kannada
 *  - no API key, no sign-up, no captcha, no quota dashboard
 *
 * Needs network. [TtsEngine] stays in the app as the offline fallback.
 *
 * Output is a 24 kHz mono MP3, which Media3 mixes onto the audio track directly.
 */
class EdgeTts {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    /**
     * Render [text] with [voiceId] and return the finished MP3.
     *
     * @param rate   percentage change in speed, -50..100 (0 = natural)
     * @param pitch  semitone-ish percentage change, -50..50 (0 = natural)
     * @param volume percentage change, -50..50 (0 = natural)
     */
    suspend fun synthesize(
        context: Context,
        text: String,
        voiceId: String = EdgeVoices.DEFAULT,
        rate: Int = 0,
        pitch: Int = 0,
        volume: Int = 0,
        fileName: String = "vo_${System.currentTimeMillis()}"
    ): Result<File> = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nothing to say"))

        // Long scripts are chunked: the service drops very long single requests.
        val chunks = chunk(clean, MAX_CHARS)
        val sink = ByteArrayOutputStream()

        for ((index, part) in chunks.withIndex()) {
            val audio = runCatching { requestAudio(part, voiceId, rate, pitch, volume) }
                .getOrElse { return@withContext Result.failure(it) }
            if (audio.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Voice service returned no audio for part ${index + 1}")
                )
            }
            sink.write(audio)
        }

        val out = File(context.cacheDir, "$fileName.mp3")
        runCatching { out.writeBytes(sink.toByteArray()) }
            .fold({ Result.success(out) }, { Result.failure(it) })
    }

    /** Short sample so the picker can audition a voice before committing. */
    suspend fun preview(context: Context, voiceId: String): Result<File> =
        synthesize(
            context = context,
            text = previewLine(voiceId),
            voiceId = voiceId,
            fileName = "preview_${voiceId.replace('-', '_')}"
        )

    private fun previewLine(voiceId: String): String = when {
        voiceId.startsWith("hi-IN") -> "नमस्ते, मैं आपकी आवाज़ हूँ। चलिए आपका वीडियो बनाते हैं।"
        voiceId.startsWith("te-IN") -> "నమస్కారం, నేను మీ గాత్రం. మీ వీడియో సిద్ధం చేద్దాం."
        voiceId.startsWith("ta-IN") -> "வணக்கம், நான் உங்கள் குரல். உங்கள் வீடியோவை உருவாக்குவோம்."
        voiceId.startsWith("kn-IN") -> "ನಮಸ್ಕಾರ, ನಾನು ನಿಮ್ಮ ಧ್ವನಿ. ನಿಮ್ಮ ವೀಡಿಯೊ ಮಾಡೋಣ."
        voiceId.startsWith("ml-IN") -> "നമസ്കാരം, ഞാൻ നിങ്ങളുടെ ശബ്ദമാണ്. നിങ്ങളുടെ വീഡിയോ ഉണ്ടാക്കാം."
        else -> "Hi, this is how your voiceover will sound. Let's make something worth watching."
    }

    // ---------------------------------------------------------------- internals

    private suspend fun requestAudio(
        text: String,
        voiceId: String,
        rate: Int,
        pitch: Int,
        volume: Int
    ): ByteArray {
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val done = CompletableDeferred<Result<ByteArray>>()
        val buffer = ByteArrayOutputStream()

        val request = Request.Builder()
            .url(socketUrl())
            .header("Origin", ORIGIN)
            .header("User-Agent", USER_AGENT)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val socket = http.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(configMessage())
                webSocket.send(ssmlMessage(requestId, text, voiceId, rate, pitch, volume))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Text frames carry control paths; "turn.end" means the render finished.
                if (text.contains("Path:turn.end")) {
                    if (!done.isCompleted) done.complete(Result.success(buffer.toByteArray()))
                    webSocket.close(1000, null)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary frame: [2-byte big-endian header length][header text][audio bytes]
                val raw = bytes.toByteArray()
                if (raw.size < 2) return
                val headerLength = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
                val start = 2 + headerLength
                if (start >= raw.size) return
                val header = String(raw, 2, headerLength, Charsets.UTF_8)
                if (!header.contains("Path:audio")) return
                buffer.write(raw, start, raw.size - start)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!done.isCompleted) {
                    val code = response?.code
                    val hint = when (code) {
                        403 -> "Voice service refused the connection (403). Try again in a moment."
                        429 -> "Voice service is rate limiting. Wait a few seconds and retry."
                        else -> t.message ?: "Could not reach the voice service"
                    }
                    done.complete(Result.failure(IllegalStateException(hint, t)))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!done.isCompleted) done.complete(Result.success(buffer.toByteArray()))
            }
        })

        val result = withTimeoutOrNull(TIMEOUT_MS) { done.await() }
        socket.cancel()
        return result?.getOrThrow()
            ?: throw IllegalStateException("Voice service timed out")
    }

    private fun socketUrl(): String =
        "$ENDPOINT?TrustedClientToken=$TRUSTED_TOKEN" +
            "&Sec-MS-GEC=${secMsGec()}" +
            "&Sec-MS-GEC-Version=$GEC_VERSION" +
            "&ConnectionId=${UUID.randomUUID().toString().replace("-", "")}"

    /**
     * Anti-abuse token the endpoint requires: SHA-256 of the current Windows
     * file-time rounded down to a 5-minute boundary, concatenated with the
     * public trusted-client token, uppercase hex.
     */
    private fun secMsGec(): String {
        var ticks = (System.currentTimeMillis() / 1000L + WINDOWS_EPOCH_OFFSET) * 10_000_000L
        ticks -= ticks % 3_000_000_000L
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$ticks$TRUSTED_TOKEN".toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun timestamp(): String {
        val fmt = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(System.currentTimeMillis())
    }

    private fun configMessage(): String =
        "X-Timestamp:${timestamp()}\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n\r\n" +
            """{"context":{"synthesis":{"audio":{"metadataoptions":""" +
            """{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},""" +
            """"outputFormat":"$OUTPUT_FORMAT"}}}}"""

    private fun ssmlMessage(
        requestId: String,
        text: String,
        voiceId: String,
        rate: Int,
        pitch: Int,
        volume: Int
    ): String {
        val locale = voiceId.substringBeforeLast('-').let {
            val parts = voiceId.split("-")
            if (parts.size >= 2) "${parts[0]}-${parts[1]}" else "en-US"
        }
        val ssml = """<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$locale'>""" +
            """<voice name='$voiceId'>""" +
            """<prosody rate='${signed(rate)}%' pitch='${signed(pitch)}%' volume='${signed(volume)}%'>""" +
            escape(text) +
            """</prosody></voice></speak>"""

        return "X-RequestId:$requestId\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${timestamp()}Z\r\n" +
            "Path:ssml\r\n\r\n" +
            ssml
    }

    private fun signed(value: Int): String = if (value >= 0) "+$value" else "$value"

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /** Split on sentence boundaries so joined MP3 chunks don't clip mid-word. */
    private fun chunk(text: String, limit: Int): List<String> {
        if (text.length <= limit) return listOf(text)
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        // Keep the delimiter attached to the sentence it ends.
        val sentences = Regex("(?<=[.!?。！？])\\s+|(?<=\\n)").split(text).filter { it.isNotBlank() }
        for (sentence in sentences) {
            val piece = if (sentence.length > limit) sentence.chunked(limit) else listOf(sentence)
            for (bit in piece) {
                if (current.length + bit.length + 1 > limit && current.isNotEmpty()) {
                    parts += current.toString().trim()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(bit.trim())
            }
        }
        if (current.isNotBlank()) parts += current.toString().trim()
        return parts
    }

    companion object {
        private const val ENDPOINT =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val TRUSTED_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val GEC_VERSION = "1-130.0.2849.68"
        private const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
        private const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        private const val WINDOWS_EPOCH_OFFSET = 11_644_473_600L
        private const val MAX_CHARS = 2_000
        private const val TIMEOUT_MS = 90_000L
    }
}
