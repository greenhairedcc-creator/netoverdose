package app.netoverdose.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ImportPayload(
    val decoded: DecodedExport,
    val storedPaths: Map<String, String>
)

/**
 * Reads a ZIP produced by ExportWriter. Attachment bytes land in private storage,
 * export.json is decoded by the same codec used for writing.
 */
object ExportReader {

    suspend fun read(context: Context, uri: Uri): ImportPayload = withContext(Dispatchers.IO) {
        val created = ArrayList<File>()
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("cannot open input stream")
            val paths = HashMap<String, String>()
            var json: String? = null

            input.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        if (!entry.isDirectory) {
                            when {
                                name == "export.json" ->
                                    json = zip.readBytes().toString(Charsets.UTF_8)

                                name.startsWith("files/") -> {
                                    // Guard against zip slip: keep only the last path segment.
                                    val safe = ExportJson.safeFileName(name)
                                    val relative = "attachments/${UUID.randomUUID()}/$safe"
                                    val target = File(context.filesDir, relative)
                                    val parent = target.parentFile ?: error("bad target path")
                                    if (!parent.mkdirs() && !parent.isDirectory) error("cannot create dir")
                                    created.add(parent)
                                    FileOutputStream(target).use { sink -> zip.copyTo(sink) }
                                    paths[name] = relative
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }

            val text = json ?: error("export.json not found in archive")
            ImportPayload(ExportJson.decode(text), paths)
        } catch (error: Exception) {
            created.forEach { dir -> runCatching { dir.deleteRecursively() } }
            throw error
        }
    }

    private fun ZipInputStream.readBytes(): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        copyTo(buffer)
        return buffer.toByteArray()
    }
}
