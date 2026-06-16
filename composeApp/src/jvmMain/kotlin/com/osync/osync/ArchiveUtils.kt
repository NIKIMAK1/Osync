package osync.osync

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveUtils {
    fun createStableArchive(songsDir: File, outputZip: File, onLog: (String) -> Unit) {
        val files = if (songsDir.exists()) {
            songsDir.walkTopDown().filter { it.isFile }.toList()
        } else {
            emptyList()
        }
        outputZip.parentFile?.let { if (!it.exists()) it.mkdirs() }
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            for (file in files) {
                val entryName = file.relativeTo(songsDir).path.replace("\\", "/")
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        onLog("Archive created: ${outputZip.name} (${files.size} files)")
    }

    fun createLazerArchive(osuDir: File, outputZip: File, onLog: (String) -> Unit) {
        val dbFile = File(osuDir, "client.realm")
        val filesDir = File(osuDir, "files")
        val fileList = if (filesDir.exists()) {
            filesDir.walkTopDown().filter { it.isFile }.toList()
        } else {
            emptyList()
        }

        outputZip.parentFile?.let { if (!it.exists()) it.mkdirs() }
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            if (dbFile.exists()) {
                zos.putNextEntry(ZipEntry("client.realm"))
                dbFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
            for (file in fileList) {
                val entryName = "files/" + file.relativeTo(filesDir).path.replace("\\", "/")
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        onLog("Archive created: ${outputZip.name} (${fileList.size} files)")
    }

    fun importStableArchive(songsDir: File, inputZip: File, onLog: (String) -> Unit) {
        if (!songsDir.exists()) songsDir.mkdirs()
        ZipInputStream(inputZip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            var count = 0
            while (entry != null) {
                if (!entry.isDirectory) {
                    val target = File(songsDir, entry.name)
                    if (!target.canonicalPath.startsWith(songsDir.canonicalPath)) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    target.parentFile?.let { if (!it.exists()) it.mkdirs() }
                    FileOutputStream(target).use { out -> copyEntry(zis, out) }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            onLog("Imported files: $count")
        }
    }

    fun importLazerArchive(osuDir: File, inputZip: File, onLog: (String) -> Unit) {
        if (!osuDir.exists()) osuDir.mkdirs()
        val filesDir = File(osuDir, "files")
        if (!filesDir.exists()) filesDir.mkdirs()

        ZipInputStream(inputZip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            var count = 0
            var replacedDb = false
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (entry.name == "client.realm") {
                        val targetDb = File(osuDir, "client.realm")
                        if (targetDb.exists()) targetDb.renameTo(File(osuDir, "client.realm.bak"))
                        FileOutputStream(targetDb).use { out -> copyEntry(zis, out) }
                        replacedDb = true
                    } else if (entry.name.startsWith("files/")) {
                        val rel = entry.name.removePrefix("files/")
                        val target = File(filesDir, rel)
                        if (!target.canonicalPath.startsWith(filesDir.canonicalPath)) {
                            zis.closeEntry()
                            entry = zis.nextEntry
                            continue
                        }
                        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        FileOutputStream(target).use { out -> copyEntry(zis, out) }
                        count++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            if (replacedDb) onLog("Database updated.")
            onLog("Imported files: $count")
        }
    }

    private fun copyEntry(input: ZipInputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
    }
}
