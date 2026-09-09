package io.github.pheobesouthwood.moonanenglish.data

import android.content.Context
import android.net.Uri
import io.github.pheobesouthwood.moonanenglish.domain.StoredAppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupManifest(
    val formatVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val sessionCount: Int,
    val providerCount: Int,
    val imageCount: Int,
    val stateSha256: String,
    val warning: String = "明文备份：包含 API 密钥、答题图片和完整历史。",
)

data class BackupPreview(val manifest: BackupManifest, val stateJson: String, val imageEntries: Map<String, ByteArray>)

class BackupManager(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(uri: Uri, state: StoredAppState) = withContext(Dispatchers.IO) {
        val stateJson = json.encodeToString(state)
        val imageFiles = File(context.filesDir, "images").listFiles()?.filter { it.isFile }.orEmpty()
        val manifest = BackupManifest(
            exportedAtEpochMs = System.currentTimeMillis(),
            sessionCount = state.sessions.size,
            providerCount = state.providers.size,
            imageCount = imageFiles.size,
            stateSha256 = sha256(stateJson.toByteArray()),
        )
        context.contentResolver.openOutputStream(uri, "w").use { stream ->
            requireNotNull(stream) { "无法创建备份文件" }
            ZipOutputStream(stream.buffered()).use { zip ->
                zip.put("WARNING.txt", manifest.warning.toByteArray())
                zip.put("manifest.json", json.encodeToString(manifest).toByteArray())
                zip.put("app-state.json", stateJson.toByteArray())
                imageFiles.forEach { zip.put("images/${it.name}", it.readBytes()) }
            }
        }
    }

    suspend fun preview(uri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val entries = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri).use { source ->
            requireNotNull(source) { "无法读取备份" }
            ZipInputStream(source.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(!entry.name.contains("..") && !entry.name.startsWith('/')) { "备份路径不安全" }
                    val output = ByteArrayOutputStream()
                    zip.copyTo(output)
                    entries[entry.name] = output.toByteArray()
                    entry = zip.nextEntry
                }
            }
        }
        val manifestBytes = requireNotNull(entries["manifest.json"]) { "备份缺少 manifest.json" }
        val stateBytes = requireNotNull(entries["app-state.json"]) { "备份缺少 app-state.json" }
        val manifest = json.decodeFromString<BackupManifest>(manifestBytes.decodeToString())
        require(manifest.formatVersion == 1) { "不支持的备份版本" }
        require(sha256(stateBytes) == manifest.stateSha256) { "备份校验失败" }
        BackupPreview(manifest, stateBytes.decodeToString(), entries.filterKeys { it.startsWith("images/") })
    }

    suspend fun restore(preview: BackupPreview, store: AppStore): StoredAppState = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            preview.imageEntries.forEach { (name, bytes) -> File(staging, name.substringAfterLast('/')).writeBytes(bytes) }
            val restored = json.decodeFromString<StoredAppState>(preview.stateJson)
            require(restored.sessions.size == preview.manifest.sessionCount) { "备份记录数量不一致" }
            val imageRoot = File(context.filesDir, "images")
            val oldRoot = File(context.filesDir, "images-before-restore")
            oldRoot.deleteRecursively()
            if (imageRoot.exists()) check(imageRoot.renameTo(oldRoot))
            check(staging.renameTo(imageRoot))
            try {
                val saved = store.replaceFromBackup(preview.stateJson)
                oldRoot.deleteRecursively()
                saved
            } catch (failure: Throwable) {
                imageRoot.deleteRecursively()
                if (oldRoot.exists()) oldRoot.renameTo(imageRoot)
                throw failure
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun ZipOutputStream.put(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name)); write(bytes); closeEntry()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
