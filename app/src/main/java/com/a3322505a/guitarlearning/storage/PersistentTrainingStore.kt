package com.a3322505a.guitarlearning.storage

import android.content.Context
import android.content.SharedPreferences
import com.a3322505a.guitarlearning.training.QuestionType
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/** Small abstraction that makes process-death persistence testable without an Android device. */
interface PreferenceBackend {
    fun getString(key: String, defaultValue: String? = null): String?
    fun putString(key: String, value: String)
}

private class SharedPreferencesBackend(
    private val preferences: SharedPreferences,
) : PreferenceBackend {
    override fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

/** SharedPreferences-backed store; records are serialized as one versioned Properties document. */
class PersistentTrainingStore(
    private val backend: PreferenceBackend,
) : TrainingStore {
    constructor(context: Context, name: String = "guitar_learning_v01") : this(
        SharedPreferencesBackend(context.getSharedPreferences(name, Context.MODE_PRIVATE)),
    )

    private var snapshot: StorageSnapshot = readSnapshot()

    override fun loadState(): StorageState = snapshot.state

    override fun saveState(state: StorageState) {
        snapshot = snapshot.copy(state = state)
        persist()
    }

    override fun loadSettings(): Settings = snapshot.settings

    override fun saveSettings(settings: Settings) {
        snapshot = snapshot.copy(settings = settings)
        persist()
    }

    override fun upsertKnowledgeItem(item: KnowledgeItem) {
        snapshot = snapshot.copy(
            knowledgeItems = snapshot.knowledgeItems.toMutableMap().apply { put(item.id, item) },
        )
        persist()
    }

    override fun findKnowledgeItem(id: String): KnowledgeItem? = snapshot.knowledgeItems[id]

    override fun loadKnowledgeItems(): List<KnowledgeItem> = snapshot.knowledgeItems.values.toList()

    override fun saveProgress(progress: Progress) {
        snapshot = snapshot.copy(
            progress = snapshot.progress.toMutableMap().apply {
                put(progress.knowledgeItemId, progress)
            },
        )
        persist()
    }

    override fun loadProgress(knowledgeItemId: String): Progress? = snapshot.progress[knowledgeItemId]

    override fun loadProgress(): List<Progress> = snapshot.progress.values.toList()

    override fun saveSession(session: Session) {
        snapshot = snapshot.copy(
            sessions = snapshot.sessions.toMutableMap().apply { put(session.id, session) },
        )
        persist()
    }

    override fun loadSessions(): List<Session> = snapshot.sessions.values.toList()

    private fun persist() {
        val properties = Properties()
        properties.setProperty("version", snapshot.state.version.toString())
        properties.setProperty(
            "settings.selectedStrings",
            snapshot.settings.selectedStrings.sorted().joinToString(","),
        )
        properties.setProperty("settings.fretStart", snapshot.settings.fretStart.toString())
        properties.setProperty("settings.fretEnd", snapshot.settings.fretEnd.toString())
        properties.setProperty("settings.notationMode", snapshot.settings.notationMode.name)
        properties.setProperty("settings.naturalOnly", snapshot.settings.naturalOnly.toString())

        properties.setProperty("knowledge.count", snapshot.knowledgeItems.size.toString())
        snapshot.knowledgeItems.values.forEachIndexed { index, item ->
            val prefix = "knowledge.$index."
            properties.setProperty(prefix + "id", item.id)
            properties.setProperty(prefix + "questionType", item.questionType.name)
            properties.setProperty(prefix + "string", item.string?.toString().orEmpty())
            properties.setProperty(prefix + "fret", item.fret?.toString().orEmpty())
            properties.setProperty(prefix + "note", item.note)
            properties.setProperty(prefix + "solfege", item.solfege)
            properties.setProperty(prefix + "degree", item.degree.toString())
            properties.setProperty(prefix + "status", item.status.name)
        }

        properties.setProperty("progress.count", snapshot.progress.size.toString())
        snapshot.progress.values.forEachIndexed { index, item ->
            val prefix = "progress.$index."
            properties.setProperty(prefix + "knowledgeItemId", item.knowledgeItemId)
            properties.setProperty(prefix + "attempts", item.attempts.toString())
            properties.setProperty(prefix + "correct", item.correct.toString())
            properties.setProperty(prefix + "streak", item.streak.toString())
            properties.setProperty(prefix + "lastSeenAt", item.lastSeenAt?.toString().orEmpty())
            properties.setProperty(prefix + "mastery", item.mastery.name)
            properties.setProperty(
                prefix + "recentResults",
                item.recentResults.joinToString(",") { if (it) "1" else "0" },
            )
            properties.setProperty(prefix + "seenDays", item.seenDays.sorted().joinToString(","))
        }

        properties.setProperty("session.count", snapshot.sessions.size.toString())
        snapshot.sessions.values.forEachIndexed { index, item ->
            val prefix = "session.$index."
            properties.setProperty(prefix + "id", item.id)
            properties.setProperty(prefix + "startedAt", item.startedAt.toString())
            properties.setProperty(prefix + "endedAt", item.endedAt?.toString().orEmpty())
            properties.setProperty(prefix + "questionCount", item.questionCount.toString())
            properties.setProperty(prefix + "correctCount", item.correctCount.toString())
        }

        val writer = StringWriter()
        properties.store(writer, "GuitarLearning V0.2.1")
        backend.putString(STORAGE_KEY, writer.toString())
    }

    private fun readSnapshot(): StorageSnapshot {
        val raw = backend.getString(STORAGE_KEY) ?: return StorageSnapshot()
        return runCatching { decode(raw) }.getOrElse { StorageSnapshot() }
    }

    private fun decode(raw: String): StorageSnapshot {
        val properties = Properties()
        properties.load(StringReader(raw))
        val settings = Settings(
            selectedStrings = properties.getProperty("settings.selectedStrings", "1,2,3,4,5,6")
                .split(",")
                .mapNotNull { it.toIntOrNull() }
                .toSet(),
            fretStart = properties.getProperty("settings.fretStart", "0").toInt(),
            fretEnd = properties.getProperty("settings.fretEnd", "12").toInt(),
            notationMode = enumValueOrDefault(
                properties.getProperty("settings.notationMode"),
                NotationMode.FIXED_SOLFEGE,
            ),
            naturalOnly = properties.getProperty("settings.naturalOnly", "true").toBoolean(),
        )
        return StorageSnapshot(
            state = StorageState(properties.getProperty("version", "1").toInt()),
            settings = settings,
            knowledgeItems = decodeKnowledgeItems(properties),
            progress = decodeProgress(properties),
            sessions = decodeSessions(properties),
        )
    }

    private fun decodeKnowledgeItems(properties: Properties): Map<String, KnowledgeItem> {
        val count = properties.getProperty("knowledge.count", "0").toInt()
        return (0 until count).mapNotNull { index ->
            val prefix = "knowledge.$index."
            val id = properties.getProperty(prefix + "id") ?: return@mapNotNull null
            val type = enumOrNull<QuestionType>(properties.getProperty(prefix + "questionType"))
                ?: return@mapNotNull null
            val note = properties.getProperty(prefix + "note") ?: return@mapNotNull null
            val solfege = properties.getProperty(prefix + "solfege") ?: return@mapNotNull null
            KnowledgeItem(
                id = id,
                questionType = type,
                string = properties.getProperty(prefix + "string").toNullableInt(),
                fret = properties.getProperty(prefix + "fret").toNullableInt(),
                note = note,
                solfege = solfege,
                degree = properties.getProperty(prefix + "degree", "0").toIntOrNull() ?: 0,
                status = enumValueOrDefault(
                    properties.getProperty(prefix + "status"),
                    MasteryStatus.UNLEARNED,
                ),
            )
        }.associateBy { it.id }
    }

    private fun decodeProgress(properties: Properties): Map<String, Progress> {
        val count = properties.getProperty("progress.count", "0").toInt()
        return (0 until count).mapNotNull { index ->
            val prefix = "progress.$index."
            val id = properties.getProperty(prefix + "knowledgeItemId")
                ?: return@mapNotNull null
            Progress(
                knowledgeItemId = id,
                attempts = properties.getProperty(prefix + "attempts", "0").toInt(),
                correct = properties.getProperty(prefix + "correct", "0").toInt(),
                streak = properties.getProperty(prefix + "streak", "0").toInt(),
                lastSeenAt = properties.getProperty(prefix + "lastSeenAt").toNullableLong(),
                mastery = enumValueOrDefault(
                    properties.getProperty(prefix + "mastery"),
                    MasteryStatus.UNLEARNED,
                ),
                recentResults = properties.getProperty(prefix + "recentResults")
                    .orEmpty()
                    .split(",")
                    .mapNotNull { when (it) { "1" -> true; "0" -> false; else -> null } },
                seenDays = properties.getProperty(prefix + "seenDays")
                    .orEmpty()
                    .split(",")
                    .filter { it.isNotEmpty() }
                    .toSet(),
            )
        }.associateBy { it.knowledgeItemId }
    }

    private fun decodeSessions(properties: Properties): Map<String, Session> {
        val count = properties.getProperty("session.count", "0").toInt()
        return (0 until count).mapNotNull { index ->
            val prefix = "session.$index."
            val id = properties.getProperty(prefix + "id") ?: return@mapNotNull null
            Session(
                id = id,
                startedAt = properties.getProperty(prefix + "startedAt", "0").toLong(),
                endedAt = properties.getProperty(prefix + "endedAt").toNullableLong(),
                questionCount = properties.getProperty(prefix + "questionCount", "0").toInt(),
                correctCount = properties.getProperty(prefix + "correctCount", "0").toInt(),
            )
        }.associateBy { it.id }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        enumOrNull<T>(value) ?: default

    private fun String?.toNullableInt(): Int? = this?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    private fun String?.toNullableLong(): Long? = this?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    private data class StorageSnapshot(
        val state: StorageState = StorageState(),
        val settings: Settings = Settings(),
        val knowledgeItems: Map<String, KnowledgeItem> = emptyMap(),
        val progress: Map<String, Progress> = emptyMap(),
        val sessions: Map<String, Session> = emptyMap(),
    )

    private companion object {
        const val STORAGE_KEY = "v01.storage"
    }
}

