package app.netoverdose.data

import app.netoverdose.domain.EventType
import app.netoverdose.domain.StepSource
import app.netoverdose.domain.TaskStatus
import org.json.JSONArray
import org.json.JSONObject

data class ExportBundle(
    val tasks: List<TaskEntity>,
    val attachments: List<AttachmentEntity>,
    val steps: List<StepEntity>,
    val events: List<EventEntity>,
    val ideas: List<IdeaEntity>
)

data class DecodedExport(
    val bundle: ExportBundle,
    val attachmentZipPaths: Map<Long, String>
)

/**
 * JSON codec kept free of Android imports so it can be unit tested on the JVM.
 * Production uses the platform org.json, tests use the org.json:json artifact.
 */
object ExportJson {

    const val SCHEMA_VERSION = 3

    fun safeFileName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .ifBlank { "file" }

    fun zipPathFor(attachment: AttachmentEntity): String =
        "files/${attachment.taskId}/${safeFileName(attachment.displayName)}"

    fun encode(bundle: ExportBundle, exportedAt: Long): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", exportedAt)

        root.put("tasks", JSONArray().apply {
            bundle.tasks.forEach { task ->
                put(JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("difficulty", task.difficulty)
                    put("windowStart", task.windowStart)
                    put("deadline", task.deadline)
                    put("status", task.status.name)
                    put("remindOnWindowStart", task.remindOnWindowStart)
                    put("createdAt", task.createdAt)
                    putNullable("startedAt", task.startedAt)
                    putNullable("closedAt", task.closedAt)
                })
            }
        })

        root.put("attachments", JSONArray().apply {
            bundle.attachments.forEach { attachment ->
                put(JSONObject().apply {
                    put("id", attachment.id)
                    put("taskId", attachment.taskId)
                    put("displayName", attachment.displayName)
                    put("mime", attachment.mime)
                    put("sizeBytes", attachment.sizeBytes)
                    put("storedPath", attachment.storedPath)
                    put("zipPath", zipPathFor(attachment))
                })
            }
        })

        root.put("steps", JSONArray().apply {
            bundle.steps.forEach { step ->
                put(JSONObject().apply {
                    put("id", step.id)
                    put("taskId", step.taskId)
                    put("text", step.text)
                    put("done", step.done)
                    put("source", step.source.name)
                    put("orderIndex", step.orderIndex)
                })
            }
        })

        root.put("events", JSONArray().apply {
            bundle.events.forEach { event ->
                put(JSONObject().apply {
                    put("id", event.id)
                    put("taskId", event.taskId)
                    put("type", event.type.name)
                    put("at", event.at)
                    if (event.payload == null) put("payload", JSONObject.NULL)
                    else put("payload", event.payload)
                })
            }
        })

        root.put("ideas", JSONArray().apply {
            bundle.ideas.forEach { idea ->
                put(JSONObject().apply {
                    put("id", idea.id)
                    put("text", idea.text)
                    put("createdAt", idea.createdAt)
                    put("situation", idea.situation)
                    put("hits", idea.hits)
                    put("lastHitAt", idea.lastHitAt)
                })
            }
        })

        return root.toString(2)
    }

    fun decode(text: String): DecodedExport {
        val root = JSONObject(text)
        val version = root.optInt("schemaVersion", 1)
        require(version in 1..SCHEMA_VERSION) { "unsupported schemaVersion: $version" }

        val tasks = ArrayList<TaskEntity>()
        root.requireArray("tasks").forEachObject { obj ->
            tasks.add(
                TaskEntity(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    difficulty = obj.getInt("difficulty"),
                    windowStart = obj.getLong("windowStart"),
                    deadline = obj.getLong("deadline"),
                    status = TaskStatus.valueOf(obj.getString("status")),
                    remindOnWindowStart = obj.optBoolean("remindOnWindowStart", true),
                    createdAt = obj.getLong("createdAt"),
                    startedAt = obj.longOrNull("startedAt"),
                    closedAt = obj.longOrNull("closedAt")
                )
            )
        }

        val attachments = ArrayList<AttachmentEntity>()
        val zipPaths = HashMap<Long, String>()
        root.requireArray("attachments").forEachObject { obj ->
            val id = obj.getLong("id")
            val entity = AttachmentEntity(
                id = id,
                taskId = obj.getLong("taskId"),
                displayName = obj.getString("displayName"),
                mime = obj.optString("mime", "application/octet-stream"),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                storedPath = obj.optString("storedPath", "")
            )
            attachments.add(entity)
            zipPaths[id] = obj.optString("zipPath", zipPathFor(entity))
        }

        val steps = ArrayList<StepEntity>()
        root.optJSONArray("steps")?.forEachObject { obj ->
            steps.add(
                StepEntity(
                    id = obj.getLong("id"),
                    taskId = obj.getLong("taskId"),
                    text = obj.getString("text"),
                    done = obj.optBoolean("done", false),
                    source = StepSource.valueOf(obj.optString("source", StepSource.USER.name)),
                    orderIndex = obj.optInt("orderIndex", 0)
                )
            )
        }

        val events = ArrayList<EventEntity>()
        root.requireArray("events").forEachObject { obj ->
            events.add(
                EventEntity(
                    id = obj.getLong("id"),
                    taskId = obj.getLong("taskId"),
                    type = EventType.valueOf(obj.getString("type")),
                    at = obj.getLong("at"),
                    payload = if (obj.isNull("payload")) null else obj.optString("payload").ifEmpty { null }
                )
            )
        }

        val ideas = ArrayList<IdeaEntity>()
        root.optJSONArray("ideas")?.forEachObject { obj ->
            val createdAt = obj.getLong("createdAt")
            ideas.add(
                IdeaEntity(
                    id = obj.getLong("id"),
                    text = obj.getString("text"),
                    createdAt = createdAt,
                    situation = obj.optString("situation", ""),
                    hits = obj.optInt("hits", 1).coerceAtLeast(1),
                    lastHitAt = obj.optLong("lastHitAt", createdAt)
                )
            )
        }

        return DecodedExport(ExportBundle(tasks, attachments, steps, events, ideas), zipPaths)
    }

    private fun JSONObject.putNullable(key: String, value: Long?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun JSONObject.requireArray(key: String): JSONArray =
        getJSONArray(key)

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (index in 0 until length()) block(getJSONObject(index))
    }
}
