package io.github.pheobesouthwood.moonanenglish.data

import android.content.Context
import io.github.pheobesouthwood.moonanenglish.domain.PromptDefaults
import io.github.pheobesouthwood.moonanenglish.domain.ProviderDefaults
import io.github.pheobesouthwood.moonanenglish.domain.StoredAppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class AppStore(context: Context) {
    private val stateFile = File(context.filesDir, "app-state.json")
    private val mutex = Mutex()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): StoredAppState = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!stateFile.exists()) defaults()
            else runCatching { json.decodeFromString<StoredAppState>(stateFile.readText()) }
                .getOrElse { defaults() }
                .withRequiredDefaults()
        }
    }

    suspend fun save(state: StoredAppState) = withContext(Dispatchers.IO) {
        mutex.withLock { writeAtomic(json.encodeToString(state)) }
    }

    suspend fun replaceFromBackup(encodedState: String): StoredAppState = withContext(Dispatchers.IO) {
        val decoded = json.decodeFromString<StoredAppState>(encodedState).withRequiredDefaults()
        require(decoded.formatVersion == 1) { "不支持的备份版本：${decoded.formatVersion}" }
        mutex.withLock { writeAtomic(json.encodeToString(decoded)) }
        decoded
    }

    fun stateFileForBackup(): File = stateFile

    private fun writeAtomic(content: String) {
        stateFile.parentFile?.mkdirs()
        val temporary = File(stateFile.parentFile, "${stateFile.name}.tmp")
        temporary.writeText(content)
        check(temporary.renameTo(stateFile) || temporary.copyTo(stateFile, overwrite = true).let { temporary.delete(); true })
    }

    private fun defaults() = StoredAppState(
        providers = ProviderDefaults.profiles,
        prompts = PromptDefaults.allDefaults().values.toList(),
    )

    private fun StoredAppState.withRequiredDefaults(): StoredAppState {
        val providerIds = providers.map { it.id }.toSet()
        val stageNames = prompts.map { it.stage }.toSet()
        return copy(
            providers = providers + ProviderDefaults.profiles.filterNot { it.id in providerIds },
            prompts = prompts + PromptDefaults.allDefaults().values.filterNot { it.stage in stageNames },
        )
    }
}
