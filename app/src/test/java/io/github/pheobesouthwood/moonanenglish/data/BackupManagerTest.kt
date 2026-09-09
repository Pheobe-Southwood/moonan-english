package io.github.pheobesouthwood.moonanenglish.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.pheobesouthwood.moonanenglish.domain.AiProtocol
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import io.github.pheobesouthwood.moonanenglish.domain.StoredAppState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    @Test
    fun plaintextBackupRoundTripsApiKeyAndManifest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = BackupManager(context)
        val destination = File(context.cacheDir, "full-backup.zip")
        val state = StoredAppState(providers = listOf(
            ProviderProfile("p", "Provider", AiProtocol.OPENAI_CHAT_COMPLETIONS, "https://example.test/v1", apiKey = "visible-secret")
        ))
        manager.export(Uri.fromFile(destination), state)
        val preview = manager.preview(Uri.fromFile(destination))
        assertTrue(preview.stateJson.contains("visible-secret"))
        assertEquals(1, preview.manifest.providerCount)
        assertTrue(preview.manifest.warning.contains("明文"))
    }
}
