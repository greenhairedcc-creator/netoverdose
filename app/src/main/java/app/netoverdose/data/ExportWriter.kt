package app.netoverdose.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(val tasks: Int, val files: Int)

/** Writes export.json plus files/ into a single ZIP chosen through SAF. */
object ExportWriter {

    suspend fun write(context: Context, uri: Uri, bundle: ExportBundle): ExportResult =
        withContext(Dispatchers.IO) {
            val json = ExportJson.encode(bundle, System.currentTimeMillis())
            var files = 0
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("cannot open output stream")
            stream.use { raw ->
                ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                    zip.putNextEntry(ZipEntry("export.json"))
                    zip.write(json.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    val used = HashSet<String>()
                    bundle.attachments.forEach { attachment ->
                        val source = File(context.filesDir, attachment.storedPath)
                        if (!source.exists() || !source.isFile) return@forEach
                        var entryName = ExportJson.zipPathFor(attachment)
                        var suffix = 1
                        while (!used.add(entryName)) {
                            if (suffix >= 1000) return@forEach
                            entryName = "files/${attachment.taskId}/${attachment.id}_" +
                                ExportJson.safeFileName(attachment.displayName)
                            suffix++
                        }
                        zip.putNextEntry(ZipEntry(entryName))
                        source.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        files++
                    }
                }
            }
            ExportResult(tasks = bundle.tasks.size, files = files)
        }
}
