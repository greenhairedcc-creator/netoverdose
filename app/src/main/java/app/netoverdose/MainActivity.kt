package app.netoverdose

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
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
        // Both system bars are painted by the same edge-to-edge call so the status bar can
        // never flash a colour of its own while a tab is being drawn. Dark scrims are off:
        // the app draws its own gradient behind the bars (see AppRoot).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        // Only the first creation may consume ACTION_SEND. After a recreation (theme, font
        // scale, locale, process death) the system can hand the original intent back, and
        // re-importing it would silently duplicate the task.
        if (savedInstanceState == null) handleShare(intent, app)

        setContent {
            NetoverdoseTheme(app.designController) {
                val palette = LocalDesign.current
                // The status/nav bar icons follow the design tokens, not the ROM theme.
                LaunchedEffect(palette.dark) {
                    enableEdgeToEdge(
                        statusBarStyle = if (palette.dark)
                            SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = if (palette.dark)
                            SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    )
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
                    // Non-suspending and never lost: the value waits for MainScreen even when
                    // no collector exists yet, i.e. while the splash is still on screen.
                    app.postShare(stored)
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

    /**
     * Pulls the shared URIs out of an intent (archive version, unchanged).
     */
    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            )
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.filterNotNull() ?: emptyList()
        else -> emptyList()
    }
}
