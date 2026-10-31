package com.smartmanager.ai.models

import android.content.Context
import android.net.Uri
import com.smartmanager.ai.net.NetworkGate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadState(val modelId: String, val file: String, val doneBytes: Long, val totalBytes: Long, val error: String? = null, val finished: Boolean = false)

/** Installs, verifies, imports and removes models under filesDir/models/<id>/. Downloads only on explicit request and only if the network master switch is on. */
@Singleton
class ModelManager @Inject constructor(@ApplicationContext private val ctx: Context, private val gate: NetworkGate) {
    private val root get() = File(ctx.filesDir, "models").apply { mkdirs() }
    private val http = OkHttpClient()
    private val _progress = MutableStateFlow<DownloadState?>(null)
    val progress: StateFlow<DownloadState?> = _progress
    private val _revision = MutableStateFlow(0)
    /** Increments whenever the installed set changes (UI refresh). */
    val revision: StateFlow<Int> = _revision

    fun dir(id: String) = File(root, id)
    fun isInstalled(e: ModelEntry) = e.files.all { f -> File(dir(e.id), f.name).let { it.exists() && it.length() == f.sizeBytes } }
    fun installedBytes(e: ModelEntry) = e.files.sumOf { File(dir(e.id), it.name).takeIf { f -> f.exists() }?.length() ?: 0L }
    fun freeBytes() = ctx.filesDir.usableSpace

    suspend fun download(e: ModelEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            gate.require("model download: ${e.title}")
            if (freeBytes() < e.totalBytes * 12 / 10) error("Not enough free space (${e.totalBytes / 1_000_000} MB needed)")
            val d = dir(e.id).apply { mkdirs() }
            for (f in e.files) {
                val target = File(d, f.name)
                if (target.exists() && target.length() == f.sizeBytes && verify(target, f)) continue
                val tmp = File(d, f.name + ".part")
                http.newCall(Request.Builder().url(f.url).build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code} for ${f.name}")
                    val body = r.body ?: error("empty body")
                    tmp.outputStream().use { out -> body.byteStream().copyToWithProgress(out) { _progress.value = DownloadState(e.id, f.name, it, f.sizeBytes) } }
                }
                if (tmp.length() != f.sizeBytes) { tmp.delete(); error("Size mismatch for ${f.name}") }
                if (!verify(tmp, f)) { tmp.delete(); error("SHA-256 mismatch for ${f.name} — file discarded") }
                tmp.renameTo(target)
            }
            _progress.value = DownloadState(e.id, "", e.totalBytes, e.totalBytes, finished = true); _revision.value++
            Unit
        }.onFailure { _progress.value = DownloadState(e.id, "", 0, e.totalBytes, error = it.message) }
    }

    /** Offline installation: copy a file the user selected (SAF) into the model folder under its catalog name. */
    suspend fun importFile(e: ModelEntry, fileName: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val f = e.files.firstOrNull { it.name == fileName } ?: error("$fileName is not part of ${e.title}")
            val d = dir(e.id).apply { mkdirs() }
            val tmp = File(d, f.name + ".part")
            ctx.contentResolver.openInputStream(uri)!!.use { it.copyTo(tmp.outputStream()) }
            if (tmp.length() != f.sizeBytes) { tmp.delete(); error("Wrong file size (${tmp.length()} ≠ ${f.sizeBytes})") }
            if (!verify(tmp, f)) { tmp.delete(); error("SHA-256 mismatch — not the expected file") }
            tmp.renameTo(File(d, f.name)); _revision.value++
            Unit
        }
    }

    fun remove(e: ModelEntry) { dir(e.id).deleteRecursively(); _revision.value++ }

    private fun verify(file: File, f: ModelFile): Boolean {
        val expected = f.sha256 ?: return true
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { i -> val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) } == expected
    }

    private inline fun InputStream.copyToWithProgress(out: java.io.OutputStream, onProgress: (Long) -> Unit) {
        val buf = ByteArray(1 shl 16); var total = 0L; var lastReport = 0L
        while (true) { val n = read(buf); if (n < 0) break; out.write(buf, 0, n); total += n; if (total - lastReport > 512 * 1024) { onProgress(total); lastReport = total } }
        onProgress(total)
    }
}
