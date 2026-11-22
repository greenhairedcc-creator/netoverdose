package app.netoverdose.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import app.netoverdose.R
import app.netoverdose.domain.EventType
import app.netoverdose.domain.ProgressTask
import app.netoverdose.domain.StepSource
import app.netoverdose.domain.TaskStatus
import app.netoverdose.notify.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class StoredAttachment(
    val displayName: String,
    val mime: String,
    val sizeBytes: Long,
    val storedPath: String
)

data class TaskItem(val task: TaskEntity, val attachment: AttachmentEntity?)

data class ImportResult(val inserted: Int, val failed: Int)

data class RestoreResult(val tasks: Int, val files: Int, val ideas: Int)

fun TaskEntity.toProgress(): ProgressTask =
    ProgressTask(id, difficulty, windowStart, deadline, status, startedAt, closedAt)

/** Emits a fresh "now" so remaining time and waveforms stay live. */
fun nowTicker(periodMillis: Long = 30_000L): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMillis)
    }
}

class TaskRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val reminders: Reminders
) {

    private val dayMillis = 86_400_000L
    private val hourMillis = 3_600_000L

    val openTasks: Flow<List<TaskItem>> =
        combine(db.tasks().observeOpen(), db.attachments().observeAll()) { tasks, attachments ->
            tasks.map { task -> TaskItem(task, attachments.firstOrNull { it.taskId == task.id }) }
        }

    val ideas: Flow<List<IdeaEntity>> = db.ideas().observe()

    val ideasCount: Flow<Int> = db.ideas().observeCount()

    val postponedTasks: Flow<List<TaskItem>> =
        combine(db.tasks().observePostponed(), db.attachments().observeAll()) { tasks, attachments ->
            tasks.map { task -> TaskItem(task, attachments.firstOrNull { it.taskId == task.id }) }
        }

    /** Everything including DONE, for the horizon and stats screens. */
    val allTasks: Flow<List<TaskItem>> =
        combine(db.tasks().observeAll(), db.attachments().observeAll()) { tasks, attachments ->
            tasks.map { task -> TaskItem(task, attachments.firstOrNull { it.taskId == task.id }) }
        }

    fun steps(taskId: Long): Flow<List<StepEntity>> = db.steps().observe(taskId)

    /** Notes for the horizon calendar day sheet. */
    val notes: Flow<List<NoteEntity>> = db.notes().observeAll()

    /** Full append-only journal, newest first, for the journal screen. */
    val allEvents: Flow<List<EventEntity>> = db.events().observeAllDesc()

    suspend fun notesBetween(from: Long, to: Long): List<NoteEntity> = withContext(Dispatchers.IO) {
        db.notes().between(from, to)
    }

    suspend fun addNote(at: Long, text: String) = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext
        db.notes().insert(NoteEntity(at = at, text = trimmed.take(400)))
    }

    suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        db.notes().delete(id)
    }

    fun observeTask(id: Long): Flow<TaskItem?> =
        combine(db.tasks().observe(id), db.attachments().observeAll()) { task, attachments ->
            task?.let { TaskItem(it, attachments.firstOrNull { a -> a.taskId == it.id }) }
        }

    suspend fun openTasksOnce(): List<TaskEntity> = withContext(Dispatchers.IO) {
        db.tasks().openOnce()
    }

    suspend fun importShared(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var inserted = 0
        var failed = 0
        for (uri in uris) {
            try {
                val stored = copyToPrivateStorage(uri)
                val now = System.currentTimeMillis()
                createTask(
                    title = stored.displayName,
                    difficulty = 3,
                    deadline = now + 3 * dayMillis,
                    remind = true,
                    stored = stored
                )
                inserted++
            } catch (error: Exception) {
                failed++
            }
        }
        ImportResult(inserted, failed)
    }

    suspend fun attach(uri: Uri): StoredAttachment = withContext(Dispatchers.IO) {
        copyToPrivateStorage(uri)
    }

    suspend fun createTask(
        title: String,
        difficulty: Int,
        deadline: Long,
        remind: Boolean,
        stored: StoredAttachment?
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = TaskEntity(
            title = title,
            difficulty = difficulty.coerceIn(1, 5),
            windowStart = now,
            deadline = deadline,
            status = TaskStatus.PLANNED,
            remindOnWindowStart = remind,
            createdAt = now,
            startedAt = null,
            closedAt = null
        )
        val id = db.withTransaction {
            val newId = db.tasks().insert(entity)
            if (stored != null) {
                db.attachments().insert(
                    AttachmentEntity(
                        taskId = newId,
                        displayName = stored.displayName,
                        mime = stored.mime,
                        sizeBytes = stored.sizeBytes,
                        storedPath = stored.storedPath
                    )
                )
            }
            db.events().insert(EventEntity(taskId = newId, type = EventType.CREATED, at = now, payload = null))
            newId
        }
        reminders.schedule(entity.copy(id = id))
        id
    }

    suspend fun start(id: Long) = withContext(Dispatchers.IO) {
        val task = db.tasks().get(id) ?: return@withContext
        if (task.status == TaskStatus.IN_PROGRESS || task.status == TaskStatus.DONE) return@withContext
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.tasks().update(task.copy(status = TaskStatus.IN_PROGRESS, startedAt = now))
            db.events().insert(EventEntity(taskId = id, type = EventType.STARTED, at = now, payload = null))
        }
    }

    suspend fun close(id: Long) = withContext(Dispatchers.IO) {
        val task = db.tasks().get(id) ?: return@withContext
        if (task.status == TaskStatus.DONE) return@withContext
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.tasks().update(task.copy(status = TaskStatus.DONE, closedAt = now))
            db.events().insert(EventEntity(taskId = id, type = EventType.CLOSED, at = now, payload = null))
        }
        reminders.cancel(id)
    }

    suspend fun postpone(id: Long) = withContext(Dispatchers.IO) {
        val task = db.tasks().get(id) ?: return@withContext
        if (task.status == TaskStatus.DONE) return@withContext
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.tasks().update(task.copy(status = TaskStatus.POSTPONED))
            db.events().insert(EventEntity(taskId = id, type = EventType.POSTPONED, at = now, payload = null))
        }
        reminders.cancel(id)
    }

    /** Mass postpone from the plan list's selection bar. */
    suspend fun postponeAll(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { postpone(it) }
    }

    /** Mass delete from the plan list's selection bar. */
    suspend fun deleteAll(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { delete(it) }
    }

    /** Starts a 15-minute sprint; the wave shows the countdown instead of the window. */
    suspend fun startSprint(id: Long, minutes: Int = 15) = withContext(Dispatchers.IO) {
        val task = db.tasks().get(id) ?: return@withContext
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.tasks().update(
                task.copy(sprintStartedAt = now, sprintEndsAt = now + minutes * 60_000L)
            )
            db.events().insert(EventEntity(taskId = id, type = EventType.STARTED, at = now, payload = "sprint"))
        }
    }

    suspend fun stopSprint(id: Long) = withContext(Dispatchers.IO) {
        val task = db.tasks().get(id) ?: return@withContext
        db.tasks().update(task.copy(sprintStartedAt = null, sprintEndsAt = null))
    }

    /** Persists a new manual order, given the ids top to bottom. */
    suspend fun reorder(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                val task = db.tasks().get(id) ?: return@forEachIndexed
                if (task.orderIndex != index) db.tasks().update(task.copy(orderIndex = index))
            }
        }
    }

    /** Wipes every table and every attachment file on disk. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        val attachments = db.attachments().all()
        reminders.cancelAll()
        db.withTransaction {
            db.tasks().clear()
            db.events().clear()
            db.ideas().clear()
            db.notes().clear()
        }
        attachments.forEach { attachment ->
            runCatching { File(context.filesDir, attachment.storedPath).parentFile?.deleteRecursively() }
        }
    }

    /** Distinct file extensions present in the data, used by the pool breakdown. */
    suspend fun extensionGroups(): Map<String, List<ProgressTask>> = withContext(Dispatchers.IO) {
        val attachments = db.attachments().all().associateBy { it.taskId }
        db.tasks().all().groupBy { task ->
            val name = attachments[task.id]?.displayName ?: return@groupBy "—"
            val dot = name.lastIndexOf('.')
            if (dot in 1 until name.length - 1) name.substring(dot + 1).uppercase().take(5) else "—"
        }.mapValues { (_, list) -> list.map { it.toProgress() } }
    }

    /** How many times tasks of each extension were postponed, for the chronic-delay bar. */
    suspend fun postponedCountsByExtension(): Map<String, Int> = withContext(Dispatchers.IO) {
        val attachments = db.attachments().all().associateBy { it.taskId }
        val taskExt = db.tasks().all().associate { task ->
            val name = attachments[task.id]?.displayName ?: ""
            val dot = name.lastIndexOf('.')
            task.id to if (dot in 1 until name.length - 1) name.substring(dot + 1).uppercase().take(5) else "—"
        }
        db.events().all()
            .filter { it.type == EventType.POSTPONED }
            .groupingBy { taskExt[it.taskId] ?: "—" }
            .eachCount()
    }

    suspend fun reopen(id: Long) = withContext(Dispatchers.IO) {
        val task = db.tasks().get(id) ?: return@withContext
        val now = System.currentTimeMillis()
        val restored = task.copy(status = TaskStatus.PLANNED, closedAt = null)
        db.withTransaction {
            db.tasks().update(restored)
            db.events().insert(EventEntity(taskId = id, type = EventType.REOPENED, at = now, payload = null))
        }
        reminders.schedule(restored)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val attachments = db.attachments().forTask(id)
        db.withTransaction { db.tasks().delete(id) }
        reminders.cancel(id)
        attachments.forEach { attachment ->
            runCatching { File(context.filesDir, attachment.storedPath).parentFile?.deleteRecursively() }
        }
    }

    suspend fun addIdea(text: String, situation: String) = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        db.ideas().insert(
            IdeaEntity(
                text = trimmed.take(140),
                createdAt = now,
                situation = situation.trim().take(400),
                hits = 1,
                lastHitAt = now
            )
        )
    }

    /** Marks one more occurrence of the same friction. */
    suspend fun bumpIdea(id: Long) = withContext(Dispatchers.IO) {
        val idea = db.ideas().get(id) ?: return@withContext
        db.ideas().update(idea.copy(hits = idea.hits + 1, lastHitAt = System.currentTimeMillis()))
    }

    suspend fun addStep(taskId: Long, text: String) = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext
        val order = db.steps().maxOrder(taskId) + 1
        db.steps().insert(
            StepEntity(
                taskId = taskId,
                text = trimmed.take(160),
                done = false,
                source = StepSource.USER,
                orderIndex = order
            )
        )
    }

    suspend fun toggleStep(stepId: Long) = withContext(Dispatchers.IO) {
        val step = db.steps().get(stepId) ?: return@withContext
        val updated = step.copy(done = !step.done)
        db.withTransaction {
            db.steps().update(updated)
            if (updated.done) {
                db.events().insert(
                    EventEntity(
                        taskId = step.taskId,
                        type = EventType.STEP_DONE,
                        at = System.currentTimeMillis(),
                        payload = null
                    )
                )
            }
        }
    }

    suspend fun deleteStep(stepId: Long) = withContext(Dispatchers.IO) {
        db.steps().delete(stepId)
    }

    suspend fun deleteIdea(id: Long) = withContext(Dispatchers.IO) {
        db.ideas().delete(id)
    }

    suspend fun exportTo(uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        val bundle = ExportBundle(
            tasks = db.tasks().all(),
            attachments = db.attachments().all(),
            steps = db.steps().all(),
            events = db.events().all(),
            ideas = db.ideas().all()
        )
        ExportWriter.write(context, uri, bundle)
    }

    /** Restores an export: replaces current data, keeps ids, re-homes attachment files. */
    suspend fun restoreFrom(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val payload = ExportReader.read(context, uri)
        val bundle = payload.decoded.bundle
        val previous = db.attachments().all()
        reminders.cancelAll()

        var files = 0
        db.withTransaction {
            db.tasks().clear()
            db.events().clear()
            db.ideas().clear()

            bundle.tasks.forEach { db.tasks().insert(it) }
            bundle.attachments.forEach { attachment ->
                val zipPath = payload.decoded.attachmentZipPaths[attachment.id]
                val stored = zipPath?.let { payload.storedPaths[it] }
                if (stored != null) {
                    val size = File(context.filesDir, stored).length()
                    db.attachments().insert(attachment.copy(storedPath = stored, sizeBytes = size))
                    files++
                }
            }
            bundle.steps.forEach { db.steps().insert(it) }
            bundle.events.forEach { db.events().insert(it) }
            bundle.ideas.forEach { db.ideas().insert(it) }
        }

        previous.forEach { attachment ->
            runCatching { File(context.filesDir, attachment.storedPath).parentFile?.deleteRecursively() }
        }
        reminders.reschedule(db.tasks().openOnce())
        RestoreResult(tasks = bundle.tasks.size, files = files, ideas = bundle.ideas.size)
    }

    suspend fun resetDemoData(): Unit = withContext(Dispatchers.IO) {
        val attachments = db.attachments().all()
        reminders.cancelAll()
        db.withTransaction {
            db.tasks().clear()
            db.events().clear()
            db.ideas().clear()
        }
        attachments.forEach { attachment ->
            runCatching { File(context.filesDir, attachment.storedPath).parentFile?.deleteRecursively() }
        }
        seedDemo(System.currentTimeMillis())
    }

    fun fileOf(attachment: AttachmentEntity): File = File(context.filesDir, attachment.storedPath)

    private suspend fun seedDemo(now: Long) {
        val titles = context.resources.getStringArray(R.array.demo_titles)
        val note = context.getString(R.string.demo_attachment_note)

        val open = listOf(
            DemoOpen(titles[0], "lab6-ospf.pkt", 4, -26, 76),
            DemoOpen(titles[1], "essay-etika.docx", 2, -6, 18),
            DemoOpen(titles[2], "seminar-bd.pptx", 3, -2, 96),
            DemoOpen(titles[3], "zadachi-ryady.pdf", 4, -1, 120),
            DemoOpen(titles[4], "trebovaniya.pdf", 5, 0, 144)
        )
        open.forEach { item ->
            val created = now + item.startHours * hourMillis
            val entity = TaskEntity(
                title = item.title,
                difficulty = item.difficulty,
                windowStart = created,
                deadline = now + item.deadlineHours * hourMillis,
                status = TaskStatus.PLANNED,
                remindOnWindowStart = true,
                createdAt = created,
                startedAt = null,
                closedAt = null
            )
            val id = db.tasks().insert(entity)
            writeDemoAttachment(id, item.fileName, note)
            db.events().insert(EventEntity(taskId = id, type = EventType.CREATED, at = created, payload = null))
            reminders.schedule(entity.copy(id = id))
        }

        val closed = listOf(
            DemoClosed(titles[5], "lab5-vlan.pkt", 4, 1, 2.1, 1.2),
            DemoClosed(titles[6], "doklad-filosofia.docx", 2, 3, 1.4, 3.5),
            DemoClosed(titles[7], "fizika-optika.pdf", 3, 5, -0.3, 2.2),
            DemoClosed(titles[8], "referat-seti.docx", 2, 7, 3.1, 1.6),
            DemoClosed(titles[9], "lab4-static.pkt", 4, 9, 1.8, 4.1),
            DemoClosed(titles[10], "english-talk.pptx", 2, 12, 0.9, 2.8)
        )
        closed.forEach { item ->
            val closedAt = now - item.daysAgo * dayMillis
            val deadline = closedAt + (item.reserveDays * dayMillis).toLong()
            val windowStart = deadline - 4 * dayMillis
            val startedAt = windowStart + (item.delayHours * hourMillis).toLong()
            val entity = TaskEntity(
                title = item.title,
                difficulty = item.difficulty,
                windowStart = windowStart,
                deadline = deadline,
                status = TaskStatus.DONE,
                remindOnWindowStart = true,
                createdAt = windowStart,
                startedAt = startedAt,
                closedAt = closedAt
            )
            val id = db.tasks().insert(entity)
            writeDemoAttachment(id, item.fileName, note)
            db.events().insert(EventEntity(taskId = id, type = EventType.CREATED, at = windowStart, payload = null))
            db.events().insert(EventEntity(taskId = id, type = EventType.STARTED, at = startedAt, payload = null))
            db.events().insert(EventEntity(taskId = id, type = EventType.CLOSED, at = closedAt, payload = null))
        }

        val ideaTexts = context.resources.getStringArray(R.array.demo_ideas)
        val situations = context.resources.getStringArray(R.array.demo_idea_situations)
        val hits = listOf(4, 2)
        ideaTexts.forEachIndexed { index, text ->
            val created = now - (index + 3) * dayMillis
            db.ideas().insert(
                IdeaEntity(
                    text = text,
                    createdAt = created,
                    situation = situations.getOrElse(index) { "" },
                    hits = hits.getOrElse(index) { 1 },
                    lastHitAt = now - index * dayMillis
                )
            )
        }
    }

    private suspend fun writeDemoAttachment(taskId: Long, displayName: String, note: String) {
        val relativeDir = "attachments/${UUID.randomUUID()}"
        val dir = File(context.filesDir, relativeDir)
        if (!dir.mkdirs() && !dir.isDirectory) return
        val file = File(dir, displayName)
        // Pad the placeholder so the demo shows a realistic size instead of "0 КБ".
        val padding = (64 + (displayName.length * 37) % 900) * 1024
        val written = runCatching {
            file.outputStream().use { out ->
                out.write(note.toByteArray(Charsets.UTF_8))
                out.write(ByteArray(padding))
            }
        }.isSuccess
        if (!written || !file.exists()) return
        db.attachments().insert(
            AttachmentEntity(
                taskId = taskId,
                displayName = displayName,
                mime = "text/plain",
                sizeBytes = file.length(),
                storedPath = "$relativeDir/$displayName"
            )
        )
    }

    private data class DemoOpen(
        val title: String,
        val fileName: String,
        val difficulty: Int,
        val startHours: Long,
        val deadlineHours: Long
    )

    private data class DemoClosed(
        val title: String,
        val fileName: String,
        val difficulty: Int,
        val daysAgo: Long,
        val reserveDays: Double,
        val delayHours: Double
    )

    private fun copyToPrivateStorage(uri: Uri): StoredAttachment {
        val resolver = context.contentResolver
        var displayName = "file"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val safeName = ExportJson.safeFileName(displayName)
        val relativeDir = "attachments/${UUID.randomUUID()}"
        val dir = File(context.filesDir, relativeDir)
        val target = File(dir, safeName)
        try {
            if (!dir.mkdirs() && !dir.isDirectory) error("cannot create attachment directory")
            val input = resolver.openInputStream(uri) ?: error("cannot open input stream")
            input.use { source -> FileOutputStream(target).use { sink -> source.copyTo(sink) } }
            if (size <= 0L) size = target.length()
            return StoredAttachment(
                displayName = displayName,
                mime = resolver.getType(uri) ?: "application/octet-stream",
                sizeBytes = size,
                storedPath = "$relativeDir/$safeName"
            )
        } catch (error: Exception) {
            runCatching { dir.deleteRecursively() }
            throw error
        }
    }
}
