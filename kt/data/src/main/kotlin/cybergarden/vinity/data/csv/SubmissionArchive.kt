package cybergarden.vinity.data.csv

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readBytes

object SubmissionArchive {

    fun zip(directory: Path, files: List<String>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            files.map(directory::resolve).filter { it.exists() }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.fileName.toString()))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
        bytes.toByteArray()
    }
}
