package com.a3322505a.guitarlearning.learning

import android.content.Context
import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "learner_snapshot")
data class SnapshotEntity(@PrimaryKey val id: Int = 1, val revision: Long, val json: String)

@Entity(tableName = "attempts")
data class AttemptEntity(@PrimaryKey val taskId: String, val learnerId: String, val sessionId: String, val nodeId: String, val at: Long, val json: String)

@Entity(tableName = "skill_evidence")
data class EvidenceEntity(@PrimaryKey val id: String, val taskId: String, val skillId: String, val correct: Boolean, val independent: Boolean)

@Entity(tableName = "node_progress")
data class NodeEntity(@PrimaryKey val nodeId: String, val masteredAt: Long?, val needsReview: Boolean, val retainedOn: String?)

@Entity(tableName = "learning_sessions")
data class SessionEntity(@PrimaryKey val id: String, val startedAt: Long, val endedAt: Long?)

@Dao
interface LearningDao {
    @Query("SELECT * FROM learner_snapshot WHERE id = 1") fun snapshot(): SnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveSnapshot(value: SnapshotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveAttempts(values: List<AttemptEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveEvidence(values: List<EvidenceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveNodes(values: List<NodeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveSessions(values: List<SessionEntity>)
    @Query("SELECT COUNT(*) FROM attempts") fun attemptCount(): Int
    @Query("DELETE FROM attempts") fun clearAttempts()
    @Query("DELETE FROM skill_evidence") fun clearEvidence()
    @Query("DELETE FROM node_progress") fun clearNodes()
    @Query("DELETE FROM learning_sessions") fun clearSessions()
}

@Database(entities = [SnapshotEntity::class, AttemptEntity::class, EvidenceEntity::class, NodeEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao
    companion object {
        fun open(context: Context, name: String = "learning-v2.db"): LearningDatabase =
            Room.databaseBuilder(context.applicationContext, LearningDatabase::class.java, name).build()
        // No destructive migration fallback: an unknown schema must fail while preserving data.
    }
}

object LearningCodec {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    fun encode(state: LearnerState): String = json.encodeToString(state)
    fun decode(text: String): LearnerState {
        val state = json.decodeFromString<LearnerState>(text)
        require(state.schemaVersion == 1) { "学习档案版本不受支持。" }
        require(state.revision >= 0 && state.learnerId.isNotBlank())
        require(Curriculum.nodes.any { it.id == state.currentNode })
        require(state.attempts.map { it.task.id }.distinct().size == state.attempts.size)
        require(state.sessions.map { it.id }.distinct().size == state.sessions.size)
        require(state.attempts.all { a -> a.ordinal > 0 && state.sessions.any { it.id == a.sessionId } })
        require(state.sessionId == null || state.sessions.any { it.id == state.sessionId && it.endedAt == null })
        require(state.active == null || state.sessionId != null)
        return state
    }
}

interface LearningRepository {
    fun load(): LearnerState
    fun commit(previous: LearnerState, next: LearnerState): LearnerState
    fun restore(previous: LearnerState, backup: String): LearnerState
}

class RoomLearningRepository(private val db: LearningDatabase) : LearningRepository {
    override fun load(): LearnerState = db.learningDao().snapshot()?.let { LearningCodec.decode(it.json) } ?: LearnerState()

    override fun commit(previous: LearnerState, next: LearnerState): LearnerState = write(previous, next, false)

    override fun restore(previous: LearnerState, backup: String): LearnerState {
        val validated = LearningCodec.decode(backup)
        return write(previous, validated, true)
    }

    private fun write(previous: LearnerState, next: LearnerState, restoring: Boolean): LearnerState {
        val saved = next.copy(revision = previous.revision + 1)
        val encoded = LearningCodec.encode(saved)
        db.runInTransaction {
            val dao = db.learningDao()
            check((dao.snapshot()?.revision ?: 0L) == previous.revision) { "学习记录已变化，请重新读取后重试。" }
            if (restoring) { dao.clearAttempts(); dao.clearEvidence(); dao.clearNodes(); dao.clearSessions() }
            val changed = if (restoring) saved.attempts else saved.attempts.filter { a -> previous.attempts.firstOrNull { it.task.id == a.task.id } != a }
            dao.saveAttempts(changed.map { AttemptEntity(it.task.id, saved.learnerId, it.sessionId, it.task.nodeId, it.at, LearningCodec.json.encodeToString(it)) })
            dao.saveEvidence(changed.filter { it.firstCorrect != null }.map { EvidenceEntity("${it.task.id}:${it.task.skillId}", it.task.id, it.task.skillId, it.firstCorrect == true, it.independent) })
            dao.saveNodes(saved.progress.map { (id, p) -> NodeEntity(id, p.masteredAt, p.needsReview, p.retainedOn) })
            dao.saveSessions(saved.sessions.map { SessionEntity(it.id, it.startedAt, it.endedAt) })
            dao.saveSnapshot(SnapshotEntity(revision = saved.revision, json = encoded))
        }
        return saved
    }
}
