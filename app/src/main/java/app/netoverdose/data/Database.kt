package app.netoverdose.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.netoverdose.domain.EventType
import app.netoverdose.domain.OpenTask
import app.netoverdose.domain.StepSource
import app.netoverdose.domain.TaskStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) override val id: Long = 0L,
    val title: String,
    val difficulty: Int,
    override val windowStart: Long,
    override val deadline: Long,
    override val status: TaskStatus,
    val remindOnWindowStart: Boolean,
    val createdAt: Long,
    val startedAt: Long?,
    val closedAt: Long?
) : OpenTask

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val displayName: String,
    val mime: String,
    val sizeBytes: Long,
    val storedPath: String
)

@Entity(
    tableName = "steps",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val text: String,
    val done: Boolean,
    val source: StepSource,
    val orderIndex: Int
)

/** Journal is append-only: rows survive task deletion, so no foreign key here. */
@Entity(tableName = "events", indices = [Index("taskId")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val type: EventType,
    val at: Long,
    val payload: String?
)

/**
 * Friction notes (stage B). hits counts how many times the author ran into the situation;
 * the list is ordered by it so the review at the end of stage B starts from real frequency.
 */
@Entity(tableName = "ideas")
data class IdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val text: String,
    val createdAt: Long,
    val situation: String = "",
    val hits: Int = 1,
    val lastHitAt: Long = 0L
)

class Converters {
    @TypeConverter fun statusToString(value: TaskStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): TaskStatus = TaskStatus.valueOf(value)
    @TypeConverter fun sourceToString(value: StepSource): String = value.name
    @TypeConverter fun stringToSource(value: String): StepSource = StepSource.valueOf(value)
    @TypeConverter fun eventToString(value: EventType): String = value.name
    @TypeConverter fun stringToEvent(value: String): EventType = EventType.valueOf(value)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status IN ('PLANNED','IN_PROGRESS') ORDER BY deadline ASC")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status IN ('PLANNED','IN_PROGRESS') ORDER BY deadline ASC")
    suspend fun openOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'POSTPONED' ORDER BY deadline ASC")
    fun observePostponed(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY deadline ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY deadline ASC")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Insert suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM tasks") suspend fun clear()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments")
    suspend fun all(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE taskId = :taskId")
    suspend fun forTask(taskId: Long): List<AttachmentEntity>

    @Insert suspend fun insert(attachment: AttachmentEntity): Long
}

@Dao
interface StepDao {
    @Query("SELECT * FROM steps WHERE taskId = :taskId ORDER BY orderIndex ASC, id ASC")
    fun observe(taskId: Long): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps ORDER BY taskId ASC, orderIndex ASC")
    suspend fun all(): List<StepEntity>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM steps WHERE taskId = :taskId")
    suspend fun maxOrder(taskId: Long): Int

    @Query("SELECT * FROM steps WHERE id = :id")
    suspend fun get(id: Long): StepEntity?

    @Insert suspend fun insert(step: StepEntity): Long
    @Insert suspend fun insertAll(steps: List<StepEntity>)
    @Update suspend fun update(step: StepEntity)
    @Query("DELETE FROM steps WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface EventDao {
    @Insert suspend fun insert(event: EventEntity)
    @Query("SELECT * FROM events ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventEntity>>
    @Query("SELECT * FROM events ORDER BY at ASC")
    suspend fun all(): List<EventEntity>
    @Query("DELETE FROM events") suspend fun clear()
}

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas ORDER BY hits DESC, lastHitAt DESC, createdAt DESC")
    fun observe(): Flow<List<IdeaEntity>>

    @Query("SELECT COUNT(*) FROM ideas")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM ideas ORDER BY hits DESC, id ASC")
    suspend fun all(): List<IdeaEntity>

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun get(id: Long): IdeaEntity?

    @Insert suspend fun insert(idea: IdeaEntity): Long
    @Update suspend fun update(idea: IdeaEntity)
    @Query("DELETE FROM ideas WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM ideas") suspend fun clear()
}

/** v1 -> v2: ideas table added. Manual migration, no destructive fallback. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ideas` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
    }
}

/**
 * v2 -> v3: situation, hits, lastHitAt added to ideas.
 * The table is rebuilt so the new NOT NULL columns carry no SQL DEFAULT,
 * which keeps the result identical to the schema Room generates from the entity.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ideas_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`situation` TEXT NOT NULL, " +
                "`hits` INTEGER NOT NULL, " +
                "`lastHitAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `ideas_new` (`id`, `text`, `createdAt`, `situation`, `hits`, `lastHitAt`) " +
                "SELECT `id`, `text`, `createdAt`, '', 1, `createdAt` FROM `ideas`"
        )
        db.execSQL("DROP TABLE `ideas`")
        db.execSQL("ALTER TABLE `ideas_new` RENAME TO `ideas`")
    }
}

@Database(
    entities = [
        TaskEntity::class,
        AttachmentEntity::class,
        StepEntity::class,
        EventEntity::class,
        IdeaEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun attachments(): AttachmentDao
    abstract fun steps(): StepDao
    abstract fun events(): EventDao
    abstract fun ideas(): IdeaDao
}
