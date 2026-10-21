package app.goonreader.ui.reader

import app.goonreader.data.prefs.PageAnimation

/**
 * The reader's only view of the renderer (PRD 10.10).
 *
 * The screen must not touch the WebView: a custom paginated engine has to be droppable in here
 * without the UI layer changing. `show` is the only place a document enters the engine.
 */
interface ReaderEngine {

    /**
     * Replaces the document and applies [restoreProgress] (0..1) once layout is ready.
     * [paged] switches the reading axis from vertical to one viewport width per page.
     */
    fun show(html: String, restoreProgress: Float, paged: Boolean)

    /** Scroll mode: scrolls vertically by [dy] pixels, positive is downwards. */
    fun scrollBy(dy: Float)

    /** Paged mode: moves one page; false when there is no page left in that direction. */
    fun turnPage(forward: Boolean, animation: PageAnimation): Boolean

    fun scrollToProgress(progress: Float)

    /** 0..1; 0 before the document is ready, 1 when the whole chapter fits on screen. */
    fun progress(): Float

    /** Scales the rendered page while a pinch is in progress; 1 restores it. */
    fun previewZoom(scale: Float)

    /** Highlights paragraph [index] (TTS), null clears it. */
    fun highlight(index: Int?)

    fun destroy()
}
