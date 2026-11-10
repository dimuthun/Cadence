package com.cadence.platform.storage

import android.content.Context
import android.net.Uri
import com.cadence.core.coroutines.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Reads and writes text documents picked with the Storage Access Framework. */
@Singleton
class DocumentStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun writeText(uri: Uri, text: String) = withContext(ioDispatcher) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't open the file for writing")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** Reads at most [MAX_BYTES]; larger files are rejected as not being a Cadence backup. */
    suspend fun readText(uri: Uri): String = withContext(ioDispatcher) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open the file")
        stream.use {
            val bytes = it.readNBytesCompat(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) throw IOException("The file is too large to be a Cadence backup")
            bytes.toString(Charsets.UTF_8)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
