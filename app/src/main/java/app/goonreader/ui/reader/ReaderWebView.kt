package app.goonreader.ui.reader

import android.content.Context
import android.webkit.WebView

/**
 * WebView that exposes the scroll metrics Compose needs.
 *
 * `computeVerticalScrollRange/Extent/Offset` are protected on WebView, and deriving the same
 * numbers from `contentHeight * scale` depends on density semantics that are easy to get wrong.
 * Subclassing is the supported way to read them.
 */
class ReaderWebView(context: Context) : WebView(context) {

    fun scrollRange(): Int = computeVerticalScrollRange()

    fun scrollExtent(): Int = computeVerticalScrollExtent()

    fun scrollOffset(): Int = computeVerticalScrollOffset()

    /** Paged mode scrolls horizontally, one viewport per page. */
    fun pageOffset(): Int = computeHorizontalScrollOffset()

    /** Total horizontal content width; equals the view width when the columns failed to lay out. */
    fun pageRange(): Int = computeHorizontalScrollRange()
}
