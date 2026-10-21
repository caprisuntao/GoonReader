package app.goonreader.ui.navigation

/** Non-tab destinations. Kept together so the route string is never typed twice. */
object Routes {
    const val WORK_DETAILS = "work/{workId}"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val STORAGE = "storage"
    const val DOWNLOAD_QUEUE = "downloads"
    const val STATISTICS = "statistics"
    const val COMMENTS = "comments/{workId}/{chapterId}"
    const val BOOKMARKS = "bookmarks/{workId}"

    /**
     * Chapter id is optional: the details screen passes the tapped chapter, while anything else
     * (History, Updates) can open the reader at a specific chapter. Progress is optional too,
     * and only bookmarks use it, to land on the saved spot instead of the stored one.
     */
    const val READER = "reader/{workId}?chapterId={chapterId}&progress={progress}"

    fun workDetails(workId: String): String = "work/$workId"

    fun reader(workId: String, chapterId: String? = null, progress: Float? = null): String {
        val query = buildList {
            chapterId?.let { add("chapterId=$it") }
            progress?.let { add("progress=${it.coerceIn(0f, 1f)}") }
        }
        return if (query.isEmpty()) "reader/$workId" else "reader/$workId?" + query.joinToString("&")
    }

    fun comments(workId: String, chapterId: String): String = "comments/$workId/$chapterId"

    fun bookmarks(workId: String): String = "bookmarks/$workId"
}
