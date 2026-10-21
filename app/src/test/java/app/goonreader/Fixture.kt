package app.goonreader

/** Loads a saved AO3 page from `src/test/resources/fixtures`. Tests never hit the network. */
object Fixture {
    fun load(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture '$name'. Re-capture with tools/capture-fixtures.ps1."
        }.bufferedReader().use { it.readText() }
}
