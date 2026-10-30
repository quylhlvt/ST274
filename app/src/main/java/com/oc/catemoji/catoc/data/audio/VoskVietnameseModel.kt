package com.oc.catemoji.catoc.data.audio

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.vosk.Model

/** Installs the bundled model once; a cancelled/failed extraction is never marked ready. */
internal object VoskVietnameseModel {
    private const val DIRECTORY = "vosk-model-small-vn-0.4"
    private val installation = Mutex()

    suspend fun open(context: Context): Model = installation.withLock {
        val parent = File(context.noBackupFilesDir, "speech")
        val target = File(parent, DIRECTORY)
        val ready = File(target, ".ready")
        if (!ready.isFile) {
            val staging = File(parent, "$DIRECTORY.installing")
            staging.deleteRecursively()
            check(staging.mkdirs()) { "Cannot create speech model directory" }
            try {
                context.assets.open("vosk/$DIRECTORY.zip").use { asset ->
                    ZipInputStream(asset).use { zip ->
                        val buffer = ByteArray(64 * 1024)
                        var entry = zip.nextEntry
                        while (entry != null) {
                            currentCoroutineContext().ensureActive()
                            require(entry.name.startsWith("$DIRECTORY/")) { "Unexpected model entry" }
                            val relative = entry.name.removePrefix("$DIRECTORY/")
                            val output = File(staging, relative)
                            require(output.canonicalPath == staging.canonicalPath ||
                                output.canonicalPath.startsWith(staging.canonicalPath + File.separator))
                            if (entry.isDirectory) {
                                output.mkdirs()
                            } else {
                                output.parentFile?.mkdirs()
                                output.outputStream().use { stream ->
                                    var count = zip.read(buffer)
                                    while (count != -1) {
                                        currentCoroutineContext().ensureActive()
                                        stream.write(buffer, 0, count)
                                        count = zip.read(buffer)
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                check(File(staging, "am/final.mdl").isFile &&
                    File(staging, "graph/HCLr.fst").isFile && File(staging, "graph/Gr.fst").isFile)
                File(staging, ".ready").writeText(DIRECTORY)
                if (target.exists()) check(target.deleteRecursively())
                check(staging.renameTo(target)) { "Cannot install speech model" }
            } finally {
                staging.deleteRecursively()
            }
        }
        // Caller owns native resources and closes the model on the same worker as decoding.
        Model(target.absolutePath)
    }
}
