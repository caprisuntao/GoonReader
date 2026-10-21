package app.goonreader.ui.library

/**
 * Accepts a pasted AO3 link or a bare work id.
 * Returns null when the input is neither, so the dialog can disable its confirm button.
 */
private val WORK_LINK = Regex("/works/(\\d+)")
private val BARE_ID = Regex("^\\s*(\\d{1,12})\\s*$")

fun parseWorkId(input: String): String? =
    (WORK_LINK.find(input) ?: BARE_ID.find(input))?.groupValues?.get(1)
