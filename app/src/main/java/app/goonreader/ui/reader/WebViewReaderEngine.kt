package app.goonreader.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import app.goonreader.data.prefs.PageAnimation
import kotlin.math.roundToInt

/**
 * [ReaderEngine] backed by a WebView (PRD 5).
 *
 * Chapter HTML is sanitised before it gets here (scripts, event handlers and `javascript:`
 * links are stripped), but script is still enabled in the view: it is the only way to move the
 * text-to-speech highlight without reloading the document. No JavascriptInterface is exposed and
 * every navigation is blocked, so the page itself has nothing to run.
 *
 * Scrolling is driven by the screen's gesture handler rather than by WebView touch, so tap zones
 * and the brightness swipe get first refusal on every gesture.
 *
 * Paged mode is CSS multi-column: one column per viewport width, so a page is one viewport of
 * horizontal scroll. `shortcut: one WebView renders one page at a time, so the slide animation
 * moves the incoming page only; a true two-page slide needs the custom engine.`
 */
class WebViewReaderEngine(private val webView: ReaderWebView) : ReaderEngine {

    private var ready = false
    private var paged = false
    private var pendingRestore = 0f
    private var highlight: Int? = null

    /**
     * Scroll deltas arrive in floats but [WebView] scrolls in whole pixels. Keeping the
     * remainder means a slow drag still moves instead of being rounded away every frame —
     * which is exactly the sticky feeling a high-refresh screen makes obvious.
     */
    private var scrollRemainder = 0f

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(background: Int = Color.BLACK) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        // The chapter is one long document scrolled by the screen, not a Compose list. Without
        // this request Android 15+ may keep the WebView's layer at its "normal" 60 Hz even when
        // the panel is 120 Hz. The request is API 35+, so it is guarded.
        if (Build.VERSION.SDK_INT >= 35) {
            webView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        }
        webView.setBackgroundColor(background)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isClickable = false
        webView.isLongClickable = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                ready = true
                if (pendingRestore > 0f) {
                    val target = pendingRestore
                    view.post { scrollToProgress(target) }
                }
                highlight?.let { view.post { applyHighlight(it) } }
            }

            /** Nothing in the reader navigates; a stray link must not replace the chapter. */
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
    }

    override fun show(html: String, restoreProgress: Float, paged: Boolean) {
        ready = false
        this.paged = paged
        pendingRestore = restoreProgress
        scrollRemainder = 0f
        // A chapter can replace a page-turn animation that is still running.
        webView.animate().cancel()
        webView.alpha = 1f
        webView.translationX = 0f
        webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
    }

    override fun scrollBy(dy: Float) {
        if (!ready || paged) return
        scrollRemainder += dy
        val whole = scrollRemainder.toInt()
        if (whole == 0) return
        scrollRemainder -= whole
        webView.scrollBy(0, whole)
    }

    override fun turnPage(forward: Boolean, animation: PageAnimation): Boolean {
        if (!ready || !paged) return false
        val width = webView.width
        if (width <= 0) return false

        val page = (webView.pageOffset() / width.toFloat()).roundToInt()
        val lastPage = (maxPageScroll() / width.toFloat()).toInt()
        val target = (page + if (forward) 1 else -1).coerceIn(0, lastPage)
        if (target == page) return false

        // Jump first, then animate the new page in. Both are synchronous, so the caller can read
        // progress() right after and store the page the user is actually looking at.
        webView.animate().cancel()
        // A slide interrupted by None/Fade must not leave the page offset. Reset both before
        // choosing the animation so every path starts from a clean transform.
        webView.alpha = 1f
        webView.translationX = 0f
        webView.scrollTo(target * width, 0)
        when (animation) {
            PageAnimation.None -> Unit
            PageAnimation.Fade -> {
                webView.alpha = 0f
                webView.animate()
                    .alpha(1f)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(PAGE_ANIMATION_MS)
                    .start()
            }
            PageAnimation.Slide -> {
                val offset = width * if (forward) 0.28f else -0.28f
                webView.translationX = offset
                webView.animate()
                    .translationX(0f)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(PAGE_ANIMATION_MS)
                    .start()
            }
        }
        return true
    }

    override fun scrollToProgress(progress: Float) {
        if (!ready) return
        scrollRemainder = 0f
        val fraction = progress.coerceIn(0f, 1f)
        if (paged) {
            val width = webView.width.coerceAtLeast(1)
            val page = (fraction * maxPageScroll() / width).roundToInt()
            webView.scrollTo(page * width, 0)
        } else {
            webView.scrollTo(0, (fraction * maxVerticalScroll()).roundToInt())
        }
    }

    override fun progress(): Float {
        if (!ready) return 0f
        if (paged) {
            val range = webView.pageRange() - webView.width
            if (range <= 0) return 1f
            return (webView.pageOffset().toFloat() / range).coerceIn(0f, 1f)
        }
        val range = (webView.scrollRange() - webView.scrollExtent()).toFloat().coerceAtLeast(1f)
        return (webView.scrollOffset().toFloat() / range).coerceIn(0f, 1f)
    }

    override fun previewZoom(scale: Float) {
        webView.pivotX = webView.width / 2f
        webView.pivotY = webView.height / 2f
        webView.scaleX = scale
        webView.scaleY = scale
    }

    override fun highlight(index: Int?) {
        highlight = index
        if (ready) applyHighlight(index)
    }

    override fun destroy() {
        webView.animate().cancel()
        webView.stopLoading()
        webView.destroy()
    }

    private fun applyHighlight(index: Int?) {
        val target = index ?: -1
        webView.evaluateJavascript(
            "(function(i){var els=document.querySelectorAll('[data-p]');" +
                "for(var k=0;k<els.length;k++){els[k].classList.toggle('tts-active',k===i)}" +
                "if(i>=0&&i<els.length){els[i].scrollIntoView({block:'center',behavior:'smooth'})}})($target)",
            null,
        )
    }

    /** Both values are view pixels, so no density conversion is involved. */
    private fun maxVerticalScroll(): Float =
        (webView.scrollRange() - webView.scrollExtent()).toFloat().coerceAtLeast(1f)

    private fun maxPageScroll(): Float =
        (webView.pageRange() - webView.width).toFloat().coerceAtLeast(0f)

    private companion object {
        /** Base URL so relative links in the text resolve; the reader never follows them. */
        const val BASE_URL = "https://archiveofourown.org/"

        /** Long enough to read as a page turn, short enough not to sit between the user and text. */
        const val PAGE_ANIMATION_MS = 210L
    }
}
