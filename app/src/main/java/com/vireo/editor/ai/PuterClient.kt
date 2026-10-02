package com.vireo.editor.ai

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Puter.js bridge.
 *
 * Puter uses a "user pays" model: the signed-in user's own Puter allocation covers the
 * AI usage, so the app ships with NO API key and the developer pays nothing.
 * Puter is a browser library, so we host it in an offscreen WebView and marshal
 * calls across a JavaScript bridge.
 */
class PuterClient(private val context: Context) {

    private var webView: WebView? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Result<String>>>()
    private val counter = AtomicInteger(0)
    private var ready = CompletableDeferred<Boolean>()

    /** Models Puter exposes. Names follow Puter's own identifiers. */
    companion object {
        val MODELS = listOf(
            "gpt-5-nano" to "GPT-5 nano · fast",
            "gpt-4o-mini" to "GPT-4o mini · balanced",
            "gpt-4o" to "GPT-4o · strong",
            "claude-sonnet-4" to "Claude Sonnet 4 · best writing",
            "claude-opus-4" to "Claude Opus 4 · deepest",
            "google/gemini-2.0-flash-lite-001" to "Gemini 2.0 Flash Lite · cheapest",
            "deepseek-chat" to "DeepSeek · good value"
        )
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onReady(ok: Boolean) {
            if (!ready.isCompleted) ready.complete(ok)
        }

        @JavascriptInterface
        fun onResult(id: String, ok: Boolean, payload: String) {
            pending.remove(id)?.complete(
                if (ok) Result.success(payload)
                else Result.failure(IllegalStateException(payload.ifBlank { "Puter request failed" }))
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun ensureReady(): Boolean = withContext(Dispatchers.Main) {
        if (webView != null && ready.isCompleted) return@withContext ready.await()
        if (webView == null) {
            ready = CompletableDeferred()
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setSupportMultipleWindows(false)
                settings.userAgentString = settings.userAgentString + " VireoApp"
                addJavascriptInterface(Bridge(), "Vireo")
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(m: ConsoleMessage): Boolean = true
                }
                loadUrl("file:///android_asset/puter_bridge.html")
            }
        }
        withTimeoutOrNull(20_000) { ready.await() } ?: false
    }

    /** The WebView must be attached to the UI for Puter's sign-in popup to be usable. */
    fun view(): WebView? = webView

    private suspend fun call(js: (String) -> String, timeoutMs: Long = 180_000): Result<String> {
        if (!ensureReady()) {
            return Result.failure(IllegalStateException("Could not reach Puter. Check your internet connection."))
        }
        val id = "r${counter.incrementAndGet()}"
        val deferred = CompletableDeferred<Result<String>>()
        pending[id] = deferred
        withContext(Dispatchers.Main) { webView?.evaluateJavascript(js(id), null) }
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        pending.remove(id)
        return result ?: Result.failure(IllegalStateException("Puter timed out"))
    }

    private fun esc(s: String): String = org.json.JSONObject.quote(s)

    suspend fun chat(system: String, user: String, model: String = DEFAULT_MODEL): Result<String> =
        call(js = { id -> "vireoChat(${esc(id)}, ${esc(model)}, ${esc(system)}, ${esc(user)});" })

    suspend fun image(prompt: String): Result<Bitmap> =
        call({ id -> "vireoImage(${esc(id)}, ${esc(prompt)});" }).mapCatching { dataUrl ->
            val b64 = dataUrl.substringAfter(",", "")
            require(b64.isNotBlank()) { "Empty image returned" }
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Could not decode image")
        }

    suspend fun signIn(): Result<String> =
        call({ id -> "vireoSignIn(${esc(id)});" }, timeoutMs = 300_000)

    suspend fun currentUser(): String? =
        call({ id -> "vireoWhoAmI(${esc(id)});" }, timeoutMs = 20_000)
            .getOrNull()?.takeIf { it.isNotBlank() }

    suspend fun signOut() { call({ id -> "vireoSignOut(${esc(id)});" }, 15_000) }

    fun release() {
        webView?.destroy()
        webView = null
    }
}
