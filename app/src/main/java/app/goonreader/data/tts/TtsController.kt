package app.goonreader.data.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.goonreader.data.prefs.ReaderPreferences
import app.goonreader.data.prefs.ReaderSettings
import app.goonreader.data.reader.ReaderText
import app.goonreader.data.repo.ChapterRepository
import app.goonreader.data.repo.RepoResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class TtsStatus { Idle, Preparing, Playing, Paused, Error }

/** One chapter in the read-aloud queue, with the label the notification shows. */
data class TtsChapter(val id: String, val label: String)

/** A voice the engine offers, labelled by locale because engine voice names are not readable. */
data class TtsVoice(val name: String, val label: String)

data class TtsState(
    val status: TtsStatus = TtsStatus.Idle,
    val workId: String = "",
    val workTitle: String = "",
    val chapterId: String? = null,
    val chapterLabel: String = "",
    val paragraph: Int = 0,
    val paragraphCount: Int = 0,
    val speed: Float = 1f,
    val voice: String = "",
    val voices: List<TtsVoice> = emptyList(),
    val sleepMinutes: Int? = null,
    val error: String? = null,
) {
    val isActive: Boolean
        get() = status == TtsStatus.Playing || status == TtsStatus.Paused || status == TtsStatus.Preparing
}

/**
 * Read-aloud playback (PRD 10.7).
 *
 * The controller owns the engine, the paragraph queue and the chapter fetch, so playback and
 * "continue into the next chapter" keep working when the reader screen is not composed. The
 * notification's foreground service is only a handle on that state ([TtsService]).
 *
 * Pausing stops the engine and remembers the paragraph; resuming re-reads that paragraph from
 * its start, because [TextToSpeech] has no pause.
 */
@Singleton
class TtsController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapters: ChapterRepository,
    preferences: ReaderPreferences,
) {

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var settings = ReaderSettings()
    private var engine: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var pending: (() -> Unit)? = null

    private var workId = ""
    private var queue: List<TtsChapter> = emptyList()
    private var index = 0
    private var texts: List<String> = emptyList()

    /** Where inside the first chapter playback starts, as a 0..1 fraction of its paragraphs. */
    private var startAt = 0f

    /** Every utterance gets a fresh id so late callbacks from a stopped one are ignored. */
    private var utterance = 0
    private var loadJob: Job? = null
    private var sleepJob: Job? = null

    init {
        scope.launch {
            preferences.settings.collect { stored ->
                settings = stored
                applySettings()
                _state.update { it.copy(speed = stored.ttsSpeed, voice = stored.ttsVoice) }
            }
        }
    }

    /** Starts at [startIndex] of [workId]'s chapter list, replacing any current playback. */
    fun start(
        workId: String,
        workTitle: String,
        chapters: List<TtsChapter>,
        startIndex: Int,
        startAt: Float = 0f,
    ) {
        if (chapters.isEmpty() || startIndex !in chapters.indices) return
        this.workId = workId
        this.queue = chapters
        this.index = startIndex
        this.startAt = startAt.coerceIn(0f, 1f)
        texts = emptyList()
        loadJob?.cancel()
        _state.update {
            it.copy(
                status = TtsStatus.Preparing,
                workId = workId,
                workTitle = workTitle,
                chapterId = null,
                chapterLabel = "",
                paragraph = 0,
                paragraphCount = 0,
                error = null,
            )
        }
        context.startForegroundService(Intent(context, TtsService::class.java))
        withEngine(::loadChapter)
    }

    fun warmUp() = withEngine { }

    fun toggle() {
        when (_state.value.status) {
            TtsStatus.Playing -> pause()
            TtsStatus.Paused -> resume()
            TtsStatus.Idle -> Unit
            TtsStatus.Preparing -> Unit
            TtsStatus.Error -> Unit
        }
    }

    fun pause() {
        engine?.stop()
        _state.update { it.copy(status = TtsStatus.Paused) }
    }

    fun nextParagraph() {
        if (!_state.value.isActive) return
        speak(_state.value.paragraph + 1)
    }

    fun previousParagraph() {
        if (!_state.value.isActive) return
        speak(_state.value.paragraph - 1)
    }

    fun setSleep(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        _state.update { it.copy(sleepMinutes = minutes) }
        if (minutes != null) {
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                stop()
            }
        }
    }

    fun stop() {
        loadJob?.cancel()
        sleepJob?.cancel()
        sleepJob = null
        utterance++
        engine?.stop()
        val voices = _state.value.voices
        _state.value = TtsState(
            speed = settings.ttsSpeed,
            voice = settings.ttsVoice,
            voices = voices,
        )
    }

    private fun resume() {
        if (texts.isEmpty()) return
        speak(_state.value.paragraph)
    }

    private fun loadChapter() {
        val chapter = queue.getOrNull(index) ?: return
        loadJob?.cancel()
        _state.update { it.copy(status = TtsStatus.Preparing, error = null) }
        loadJob = scope.launch {
            when (val result = chapters.getChapter(workId, chapter.id)) {
                is RepoResult.Ok -> {
                    texts = ReaderText.annotate(result.value.bodyHtml).paragraphs
                    if (texts.isEmpty()) {
                        fail("This chapter has no text to read.")
                        return@launch
                    }
                    _state.update {
                        it.copy(
                            chapterId = chapter.id,
                            chapterLabel = chapter.label,
                            paragraphCount = texts.size,
                            paragraph = 0,
                        )
                    }
                    // Start where the reader was looking, not at the top of the chapter.
                    val first = (startAt * texts.size).toInt().coerceIn(0, texts.lastIndex)
                    startAt = 0f
                    speak(first)
                }
                is RepoResult.Failed -> fail("Couldn't load the next chapter.")
            }
        }
    }

    private fun speak(index: Int) {
        if (texts.isEmpty()) return
        if (index >= texts.size) {
            advanceChapter()
            return
        }
        if (index < 0) return
        val engine = engine ?: return
        utterance++
        val id = "tts$utterance"
        _state.update {
            it.copy(status = TtsStatus.Playing, paragraph = index, paragraphCount = texts.size)
        }
        // Engines cap one utterance, and the cap includes the text only, not markup.
        val text = texts[index].take(TextToSpeech.getMaxSpeechInputLength())
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun advanceChapter() {
        if (index + 1 >= queue.size) {
            stop()
            return
        }
        index++
        loadChapter()
    }

    private fun fail(message: String) {
        utterance++
        engine?.stop()
        _state.update { it.copy(status = TtsStatus.Error, error = message) }
    }

    private fun withEngine(action: () -> Unit) {
        when {
            failed -> fail("Text-to-speech is not available on this device.")
            ready -> action()
            else -> {
                pending = action
                if (engine == null) {
                    engine = TextToSpeech(context) { status -> scope.launch { onInit(status) } }
                }
            }
        }
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            fail("Text-to-speech is not available on this device.")
            return
        }
        ready = true
        val engine = engine ?: return
        val language = engine.setLanguage(Locale.getDefault())
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        engine.setOnUtteranceProgressListener(listener)
        loadVoices(engine)
        applySettings()
        val action = pending
        pending = null
        action?.invoke()
    }

    private fun loadVoices(engine: TextToSpeech) {
        val voices = engine.voices.orEmpty()
            .distinctBy { it.locale.toLanguageTag() }
            .map { TtsVoice(it.name, it.locale.displayName) }
            .sortedBy { it.label }
        _state.update { it.copy(voices = voices) }
    }

    private fun applySettings() {
        val engine = engine ?: return
        if (!ready) return
        engine.setSpeechRate(settings.ttsSpeed)
        if (settings.ttsVoice.isEmpty()) {
            engine.voice = engine.defaultVoice
        } else {
            engine.voices?.firstOrNull { it.name == settings.ttsVoice }?.let { engine.voice = it }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (utteranceId != "tts$utterance") return
            speak(_state.value.paragraph + 1)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit

        override fun onError(utteranceId: String?) {
            if (utteranceId != "tts$utterance") return
            fail("The speech engine reported an error.")
        }
    }
}
