package com.maksimowiczm.foodyou.ai

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Only a fully verified file is renamed into the active model path. */
internal class GemmaModelStore(private val directory: File) {
    val model = File(directory, GEMMA_FILE)
    private val partial = File(directory, "$GEMMA_FILE.part")
    private val mutableState = MutableStateFlow(snapshot())
    val state: StateFlow<ModelDownloadState> = mutableState

    private fun snapshot() = ModelDownloadState(
        bytes = if (model.isFile && model.length() == GEMMA_SIZE) GEMMA_SIZE else partial.length(),
        ready = model.isFile && model.length() == GEMMA_SIZE,
    )

    suspend fun download() = withContext(Dispatchers.IO) {
        if (state.value.ready) return@withContext
        directory.mkdirs()
        mutableState.value = snapshot().copy(running = true)
        try {
            if (partial.length() > GEMMA_SIZE) partial.delete()
            val offset = partial.length()
            require(directory.usableSpace >= GEMMA_SIZE - offset + 256L * 1024 * 1024) {
                "Nicht genügend Speicher: Für das Modell werden etwa 3,66 GB benötigt."
            }
            if (offset < GEMMA_SIZE) transfer(offset)
            currentCoroutineContext().ensureActive()
            mutableState.value = mutableState.value.copy(verifying = true)
            val valid = verifyModelFile(partial, GEMMA_SIZE, GEMMA_SHA256)
            if (!valid) {
                partial.delete()
                error("Die Modellprüfung ist fehlgeschlagen. Bitte erneut herunterladen.")
            }
            currentCoroutineContext().ensureActive()
            check(partial.renameTo(model)) { "Das geprüfte Modell konnte nicht gespeichert werden." }
            mutableState.value = snapshot()
        } catch (e: CancellationException) {
            mutableState.value = snapshot().copy(message = "Download pausiert.")
            throw e
        } catch (e: Exception) {
            // Never expose network exception messages (which can contain signed redirect URLs).
            mutableState.value = snapshot().copy(message = when (e) {
                is IllegalArgumentException, is IllegalStateException -> e.message
                else -> "Download fehlgeschlagen. Verbindung prüfen und erneut versuchen."
            })
        } finally {
            mutableState.value = mutableState.value.copy(running = false, verifying = false)
        }
    }

    private suspend fun transfer(offset: Long) {
        val connection = URL(GEMMA_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val code = connection.responseCode
            val append = acceptsDownloadResponse(code, connection.getHeaderField("Content-Range"), offset)
            require(code == 200 || code == 206) { "Modellserver nicht erreichbar (HTTP $code)." }
            connection.inputStream.use { input ->
                java.io.FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytes = if (append) offset else 0L
                    var lastUpdate = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        check(bytes <= GEMMA_SIZE) { "Unerwartete Modelldateigröße. Bitte erneut versuchen." }
                        output.write(buffer, 0, count)
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 200) {
                            mutableState.value = mutableState.value.copy(bytes = bytes)
                            lastUpdate = now
                        }
                    }
                }
            }
            mutableState.value = mutableState.value.copy(bytes = partial.length())
            check(partial.length() == GEMMA_SIZE) { "Download unvollständig. Bitte fortsetzen." }
        } finally { connection.disconnect() }
    }

    fun delete() {
        check(!state.value.running)
        val deleted = (!model.exists() || model.delete()) && (!partial.exists() || partial.delete())
        mutableState.value = snapshot().copy(message = if (deleted) null else "Modell konnte nicht gelöscht werden.")
    }
}

/** A server ignoring Range must restart the file, never append duplicate bytes. */
internal fun acceptsDownloadResponse(code: Int, range: String?, offset: Long): Boolean {
    if (code == 206) {
        require(range?.startsWith("bytes $offset-") == true && range.endsWith("/$GEMMA_SIZE")) {
            "Ungültige Fortsetzung des Downloads. Bitte erneut versuchen."
        }
        return offset > 0
    }
    return false
}

internal suspend fun verifyModelFile(file: File, size: Long, sha256: String): Boolean {
    if (file.length() != size) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(256 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == sha256
}
