package app.goonreader.ui.reader

/**
 * Volume-key page turns (PRD 10.3).
 *
 * Key events go to the activity, not to Compose, so the reader that is on screen registers its
 * handler here; MainActivity consumes the keys only while one is registered, which keeps the
 * volume keys normal everywhere else in the app.
 */
object VolumeKeyPager {

    /** Positive is forward (volume down, the reading direction). */
    @Volatile
    var pager: ((Boolean) -> Unit)? = null
}
