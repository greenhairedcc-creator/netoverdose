package app.netoverdose

import app.netoverdose.data.AttachmentEntity
import app.netoverdose.data.EventEntity
import app.netoverdose.data.ExportBundle
import app.netoverdose.data.ExportJson
import app.netoverdose.data.IdeaEntity
import app.netoverdose.data.NoteEntity
import app.netoverdose.data.StepEntity
import app.netoverdose.data.TaskEntity
import app.netoverdose.domain.EventType
import app.netoverdose.domain.StepSource
import app.netoverdose.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage A5 requirement: an export reads back into the same objects. */
class ExportJsonTest {

    private val now = 1_800_000_000_000L

    private fun sample(): ExportBundle = ExportBundle(
        tasks = listOf(
            TaskEntity(
                id = 1,
                title = "Лабораторная №6 — \"OSPF\"\nпуть C:\\temp\\lab.pkt",
                difficulty = 4,
                windowStart = now - 90_000_000L,
                deadline = now + 270_000_000L,
                status = TaskStatus.IN_PROGRESS,
                remindOnWindowStart = true,
                createdAt = now - 90_000_000L,
                startedAt = now - 80_000_000L,
                closedAt = null,
                orderIndex = 3,
                sprintStartedAt = now - 3_600_000L,
                sprintEndsAt = now - 2_700_000L
            ),
            TaskEntity(
                id = 2,
                title = "Реферат — история сетей 📚",
                difficulty = 2,
                windowStart = now - 400_000_000L,
                deadline = now - 100_000_000L,
                status = TaskStatus.DONE,
                remindOnWindowStart = false,
                createdAt = now - 400_000_000L,
                startedAt = null,
                closedAt = now - 120_000_000L,
                orderIndex = 7
            )
        ),
        attachments = listOf(
            AttachmentEntity(
                id = 7,
                taskId = 1,
                displayName = "lab6-ospf.pkt",
                mime = "application/octet-stream",
                sizeBytes = 86_016L,
                storedPath = "attachments/uuid-a/lab6-ospf.pkt"
            ),
            AttachmentEntity(
                id = 8,
                taskId = 2,
                displayName = "referat/seti.docx",
                mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                sizeBytes = 430_080L,
                storedPath = "attachments/uuid-b/seti.docx"
            )
        ),
        steps = listOf(
            StepEntity(id = 3, taskId = 1, text = "Собрать топологию", done = true, source = StepSource.USER, orderIndex = 0),
            StepEntity(id = 4, taskId = 1, text = "Проверить соседей", done = false, source = StepSource.LLM, orderIndex = 1)
        ),
        events = listOf(
            EventEntity(id = 11, taskId = 1, type = EventType.CREATED, at = now - 90_000_000L, payload = null),
            EventEntity(id = 12, taskId = 1, type = EventType.STARTED, at = now - 80_000_000L, payload = "{\"x\":1}"),
            EventEntity(id = 13, taskId = 2, type = EventType.CLOSED, at = now - 120_000_000L, payload = null)
        ),
        ideas = listOf(
            IdeaEntity(5, "Виджет на локскрин", now - 86_400_000L, "Шёл между парами, надо было видеть дату", 4, now - 3_600_000L),
            IdeaEntity(6, "Тёмная тема", now - 172_800_000L, "", 1, now - 172_800_000L)
        ),
        notes = listOf(
            NoteEntity(id = 21, at = now - 86_400_000L, text = "День без сети — работал над лабораторной"),
            NoteEntity(id = 22, at = now, text = "Пусто после первой пары")
        )
    )

    @Test
    fun roundTripKeepsEveryObject() {
        val original = sample()
        val decoded = ExportJson.decode(ExportJson.encode(original, now))
        assertEquals(original, decoded.bundle)
    }

    @Test
    fun roundTripKeepsIdeaCountersAndSituation() {
        val decoded = ExportJson.decode(ExportJson.encode(sample(), now)).bundle
        assertEquals(4, decoded.ideas[0].hits)
        assertEquals("Шёл между парами, надо было видеть дату", decoded.ideas[0].situation)
        assertEquals(now - 3_600_000L, decoded.ideas[0].lastHitAt)
        assertEquals("", decoded.ideas[1].situation)
    }

    @Test
    fun roundTripKeepsNullFields() {
        val decoded = ExportJson.decode(ExportJson.encode(sample(), now))
        assertNull(decoded.bundle.tasks[0].closedAt)
        assertNull(decoded.bundle.tasks[1].startedAt)
        assertNull(decoded.bundle.events[0].payload)
        assertEquals("{\"x\":1}", decoded.bundle.events[1].payload)
    }

    @Test
    fun roundTripOnEmptyBundle() {
        val empty = ExportBundle(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, ExportJson.decode(ExportJson.encode(empty, now)).bundle)
    }

    @Test
    fun roundTripKeepsPlanOrderAndSprint() {
        val decoded = ExportJson.decode(ExportJson.encode(sample(), now)).bundle.tasks
        assertEquals(3, decoded[0].orderIndex)
        assertEquals(now - 3_600_000L, decoded[0].sprintStartedAt)
        assertEquals(now - 2_700_000L, decoded[0].sprintEndsAt)
        assertEquals(7, decoded[1].orderIndex)
        assertNull(decoded[1].sprintStartedAt)
    }

    @Test
    fun roundTripKeepsDayNotes() {
        val notes = ExportJson.decode(ExportJson.encode(sample(), now)).bundle.notes
        assertEquals(2, notes.size)
        assertEquals("День без сети — работал над лабораторной", notes[0].text)
        assertEquals(now, notes[1].at)
    }

    @Test
    fun schemaVersionIsCurrent() {
        assertEquals(4, ExportJson.SCHEMA_VERSION)
        assertTrue(ExportJson.encode(sample(), now).contains("\"schemaVersion\""))
    }

    @Test
    fun zipPathsAreSanitizedAndStable() {
        val decoded = ExportJson.decode(ExportJson.encode(sample(), now))
        assertEquals("files/1/lab6-ospf.pkt", decoded.attachmentZipPaths[7L])
        assertEquals("files/2/seti.docx", decoded.attachmentZipPaths[8L])
    }

    @Test
    fun versionTwoIdeasGetDefaultCounters() {
        val legacy = """
            {
              "schemaVersion": 2,
              "exportedAt": 1,
              "tasks": [],
              "attachments": [],
              "steps": [],
              "events": [],
              "ideas": [{"id":9,"text":"Старая идея","createdAt":123}]
            }
        """.trimIndent()
        val idea = ExportJson.decode(legacy).bundle.ideas.single()
        assertEquals(1, idea.hits)
        assertEquals("", idea.situation)
        assertEquals(123L, idea.lastHitAt)
    }

    @Test
    fun versionOneWithoutIdeasIsAccepted() {
        val legacy = """
            {
              "schemaVersion": 1,
              "exportedAt": 1,
              "tasks": [{"id":1,"title":"T","difficulty":3,"windowStart":0,"deadline":1,
                         "status":"PLANNED","createdAt":0,"startedAt":null,"closedAt":null}],
              "attachments": [],
              "steps": [],
              "events": []
            }
        """.trimIndent()
        val decoded = ExportJson.decode(legacy)
        assertEquals(1, decoded.bundle.tasks.size)
        assertTrue(decoded.bundle.ideas.isEmpty())
        assertTrue(decoded.bundle.tasks[0].remindOnWindowStart)
    }

    @Test
    fun versionThreeExportGetsV4Defaults() {
        val legacy = """
            {
              "schemaVersion": 3,
              "exportedAt": 1,
              "tasks": [{"id":1,"title":"T","difficulty":3,"windowStart":0,"deadline":1,
                         "status":"PLANNED","createdAt":0,"startedAt":null,"closedAt":null}],
              "attachments": [],
              "steps": [],
              "events": [],
              "ideas": []
            }
        """.trimIndent()
        val decoded = ExportJson.decode(legacy).bundle
        assertEquals(0, decoded.tasks.single().orderIndex)
        assertNull(decoded.tasks.single().sprintStartedAt)
        assertNull(decoded.tasks.single().sprintEndsAt)
        assertTrue(decoded.notes.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedSchemaIsRejected() {
        ExportJson.decode("""{"schemaVersion":99,"tasks":[],"attachments":[],"events":[]}""")
    }

    @Test
    fun fileNameStripsPathsAndControlCharacters() {
        assertEquals("evil.txt", ExportJson.safeFileName("../../evil.txt"))
        assertEquals("a_b.txt", ExportJson.safeFileName("a\u0000b.txt"))
        assertEquals("file", ExportJson.safeFileName("   "))
    }
}
