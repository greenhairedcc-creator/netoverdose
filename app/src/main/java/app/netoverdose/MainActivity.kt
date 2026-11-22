package app.netoverdose

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import app.netoverdose.data.ImportResult
import app.netoverdose.design.LocalDesign
import app.netoverdose.ui.AppRoot
import app.netoverdose.ui.components.StarfallSplash
import app.netoverdose.ui.theme.NetoverdoseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NetoverdoseApp
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Only the first creation may consume ACTION_SEND. After a recreation (theme, font
        // scale, locale, process death) the system can hand the original intent back, and
        // re-importing it would silently duplicate the task.
        if (savedInstanceState == null) handleShare(intent, app)

        setContent {
            NetoverdoseTheme(app.designController) {
                val palette = LocalDesign.current
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !palette.dark
                    controller.isAppearanceLightNavigationBars = !palette.dark
                }
                // Cold boot shows the ASCII starfall once; a configuration change keeps the app.
                var splashDone by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    if (splashDone) {
                        AppRoot(app)
                    } else {
                        StarfallSplash(onFinished = { splashDone = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent, application as NetoverdoseApp)
    }

    /**
     * A shared file no longer creates a task on the spot (A2): it is copied into private
     * storage and handed to the intake panel, which is the only place that may persist it.
     * Extra files from ACTION_SEND_MULTIPLE have no form to live in, so they still go
     * through the old fire-and-forget import.
     */
    private fun handleShare(intent: Intent?, app: NetoverdoseApp) {
        val uris = sharedUris(intent ?: return)
        if (uris.isEmpty()) return
        // Consume the intent so a later recreation does not import the same files again.
        setIntent(Intent(Intent.ACTION_MAIN))
        lifecycleScope.launch {
            val first = uris.first()
            val prepared = runCatching { app.repository.prepareShare(first) }
            prepared.fold(
                onSuccess = { stored ->
                    app.pendingShare.emit(stored)
                    val extra = uris.drop(1)
                    if (extra.isNotEmpty()) emitImportMessage(app, app.repository.importShared(extra))
                },
                onFailure = { emitImportMessage(app, app.repository.importShared(uris)) }
            )
        }
    }

    private suspend fun emitImportMessage(app: NetoverdoseApp, result: ImportResult) {
        val text = when {
            result.inserted > 0 && result.failed == 0 ->
                app.getString(R.string.msg_import_ok, result.inserted)
            result.inserted == 0 ->
                app.getString(R.string.msg_import_fail, result.failed)
            else ->
                app.getString(R.string.msg_import_mixed, result.inserted, result.failed)
        }
        app.messages.emit(text)
    }

    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.filterNotNull() ?: emptyList()
        else -> emptyList()
    }
}
