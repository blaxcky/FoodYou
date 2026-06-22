package com.maksimowiczm.foodyou.app.infrastructure.backup

import android.content.Context
import androidx.room.execSQL
import androidx.room.useWriterConnection
import com.maksimowiczm.foodyou.app.infrastructure.room.DATABASE_NAME
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.common.auth.SessionRepository
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.OpenFoodFactsCredentialsRepository
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory

/** The only supported full backup format. Its payload is authenticated before any app file changes. */
internal class FullBackupManager(
    private val context: Context,
    private val database: FoodYouDatabase,
    private val masterCrypto: MasterCrypto,
    private val sessionRepository: SessionRepository,
    private val fddbCredentials: FddbCredentialsRepository,
    private val openFoodFactsCredentials: OpenFoodFactsCredentialsRepository,
) {
    suspend fun export(password: CharArray, output: OutputStream) {
        require(password.size >= 8) { "Das Passwort muss mindestens 8 Zeichen lang sein." }
        database.useWriterConnection { it.execSQL("PRAGMA wal_checkpoint(FULL)") }
        val stage = createTempDirectory("fybackup-export-").toFile()
        try {
            copyStateTo(stage)
            File(stage, SENSITIVE_FILE).writeText(Json.encodeToString(sensitiveState()))
            val plain = File(stage, "archive.zip")
            writeArchive(stage, plain)
            encrypt(plain, password, output)
        } finally {
            stage.deleteRecursively()
            password.fill('\u0000')
        }
    }

    suspend fun import(password: CharArray, input: InputStream) {
        val stage = createTempDirectory("fybackup-import-").toFile()
        try {
            val archive = File(stage, "archive.zip")
            decrypt(input, password, archive)
            val restored = File(stage, "restored")
            readArchive(archive, restored)
            validate(restored)
            val sensitive = Json.decodeFromString<SensitiveState>(File(restored, SENSITIVE_FILE).readText())
            // This is keystore encrypted and is deliberately outside the replaced app state.
            BackupBootstrap.save(context, masterCrypto, sensitive)
            replaceState(restored)
        } finally {
            stage.deleteRecursively()
            password.fill('\u0000')
        }
    }

    private suspend fun sensitiveState() = SensitiveState(
        session = sessionRepository.observeSession().first(),
        fddb = fddbCredentials.loadCredentials()?.let { Credentials(it.first, it.second) },
        openFoodFacts = openFoodFactsCredentials.loadCredentials()?.let { Credentials(it.first, it.second) },
    )

    private fun copyStateTo(root: File) {
        copyRecursively(context.getDatabasePath(DATABASE_NAME), File(root, "database/$DATABASE_NAME"))
        copyRecursively(context.filesDir, File(root, "files"))
        copyRecursively(File(context.applicationInfo.dataDir, "shared_prefs"), File(root, "shared_prefs"))
    }

    private fun replaceState(root: File) {
        database.close()
        val rollback = File(root, "rollback").also(File::mkdirs)
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        val files = context.filesDir
        val preferences = File(context.applicationInfo.dataDir, "shared_prefs")
        copyRecursively(databaseFile, File(rollback, "database/$DATABASE_NAME"))
        copyRecursively(files, File(rollback, "files"))
        copyRecursively(preferences, File(rollback, "shared_prefs"))
        try {
            deleteDatabaseSidecars(databaseFile)
            replace(File(root, "database/$DATABASE_NAME"), databaseFile)
            replaceDirectory(File(root, "files"), files)
            replaceDirectory(File(root, "shared_prefs"), preferences)
        } catch (failure: Exception) {
            replace(File(rollback, "database/$DATABASE_NAME"), databaseFile)
            replaceDirectory(File(rollback, "files"), files)
            replaceDirectory(File(rollback, "shared_prefs"), preferences)
            throw failure
        }
    }

    private fun validate(root: File) {
        require(File(root, SENSITIVE_FILE).isFile) { "Die Sicherung enthält keinen sensiblen Zustand." }
        val db = File(root, "database/$DATABASE_NAME")
        require(db.isFile) { "Die Sicherung enthält keine Datenbank." }
    }

    private fun writeArchive(root: File, target: File) {
        val entries = root.walkTopDown().filter { it.isFile && it.name != "archive.zip" }.toList()
        java.util.zip.ZipOutputStream(BufferedOutputStream(target.outputStream())).use { zip ->
            val manifest = entries.joinToString("\n") { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                "$path\t${sha256(file)}\t${file.length()}"
            }
            zip.putNextEntry(java.util.zip.ZipEntry(MANIFEST)); zip.write(manifest.encodeToByteArray()); zip.closeEntry()
            entries.forEach { file ->
                zip.putNextEntry(java.util.zip.ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    private fun readArchive(archive: File, root: File) {
        val actual = mutableMapOf<String, File>()
        var manifest: String? = null
        java.util.zip.ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && isSafePath(entry.name)) { "Ungültiger Pfad in der Sicherung." }
                if (entry.name == MANIFEST) manifest = zip.readBytes().decodeToString()
                else {
                    val file = File(root, entry.name).canonicalFile
                    require(file.path.startsWith(root.canonicalPath + File.separator)) { "Ungültiger Pfad in der Sicherung." }
                    file.parentFile!!.mkdirs(); file.outputStream().use { zip.copyTo(it) }; actual[entry.name] = file
                }
            }
        }
        val expected = manifest?.lineSequence()?.filter { it.isNotBlank() }?.associate {
            val parts = it.split('\t'); require(parts.size == 3 && isSafePath(parts[0])) { "Ungültiges Manifest." }; parts[0] to parts[1]
        } ?: error("Manifest fehlt.")
        require(expected.keys == actual.keys && expected.all { (path, hash) -> sha256(actual.getValue(path)) == hash }) { "Die Integritätsprüfung der Sicherung ist fehlgeschlagen." }
    }

    private fun encrypt(source: File, password: CharArray, output: OutputStream) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes); val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce)) }
        java.io.DataOutputStream(BufferedOutputStream(output)).use { out ->
            out.writeUTF(MAGIC); out.writeInt(FORMAT_VERSION); out.write(salt); out.write(nonce)
            javax.crypto.CipherOutputStream(out, cipher).use { encrypted -> source.inputStream().use { it.copyTo(encrypted) } }
        }
    }

    private fun decrypt(input: InputStream, password: CharArray, target: File) {
        java.io.DataInputStream(BufferedInputStream(input)).use { data ->
            require(data.readUTF() == MAGIC) { "Dies ist keine FoodYou-Sicherung." }
            require(data.readInt() == FORMAT_VERSION) { "Diese Sicherungsversion wird nicht unterstützt." }
            val salt = data.readNBytes(16); val nonce = data.readNBytes(12)
            require(salt.size == 16 && nonce.size == 12) { "Unvollständiger Sicherungsheader." }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce)) }
            javax.crypto.CipherInputStream(data, cipher).use { encrypted -> target.outputStream().use { encrypted.copyTo(it) } }
        }
    }

    private fun key(password: CharArray, salt: ByteArray) = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, 210_000, 256)).let { javax.crypto.spec.SecretKeySpec(it.encoded, "AES") }
    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun isSafePath(path: String) = path.isNotEmpty() && !path.startsWith('/') && !path.contains("\\") && path.split('/').none { it == "." || it == ".." || it.isEmpty() }
    private fun copyRecursively(from: File, to: File) { if (from.exists()) from.copyRecursively(to, overwrite = true) }
    private fun replace(from: File, to: File) { to.parentFile?.mkdirs(); val tmp = File(to.parentFile, "${to.name}.restore"); from.copyTo(tmp, true); if (to.exists()) to.delete(); require(tmp.renameTo(to)) }
    private fun deleteDatabaseSidecars(database: File) { File(database.parentFile, "${database.name}-wal").delete(); File(database.parentFile, "${database.name}-shm").delete() }
    private fun replaceDirectory(from: File, to: File) { if (!from.exists()) return; if (to.exists() && !to.deleteRecursively()) error("Lokaler Zustand konnte nicht ersetzt werden."); if (!from.copyRecursively(to, true)) error("Sicherung konnte nicht wiederhergestellt werden.") }
    private companion object { const val MAGIC = "FYBACKUP"; const val FORMAT_VERSION = 1; const val MANIFEST = "manifest.tsv"; const val SENSITIVE_FILE = "sensitive.json" }
}

@Serializable internal data class SensitiveState(val session: com.maksimowiczm.foodyou.common.auth.Session?, val fddb: Credentials?, val openFoodFacts: Credentials?)
@Serializable internal data class Credentials(val login: String, val password: String)
