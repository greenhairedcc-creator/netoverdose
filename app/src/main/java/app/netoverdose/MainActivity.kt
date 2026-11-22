package app.netoverdose

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import app.netoverdose.design.LocalDesign
import app.netoverdose.ui.AppRoot
import app.netoverdose.ui.theme.NetoverdoseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NetoverdoseApp
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleShare(intent, app)

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
                AppRoot(app)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent, application as NetoverdoseApp)
    }

    private fun handleShare(intent: Intent?, app: NetoverdoseApp) {
        val uris = sharedUris(intent ?: return)
        if (uris.isEmpty()) return
        // Consume the intent so a later recreation does not import the same files again.
        setIntent(Intent(Intent.ACTION_MAIN))
        lifecycleScope.launch {
            val result = app.repository.importShared(uris)
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
