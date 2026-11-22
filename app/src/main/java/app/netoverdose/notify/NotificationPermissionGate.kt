package app.netoverdose.notify

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * One shared entry point for turning reminders on (A4). Every switch that can enable
 * notifications - "Ещё", the intake panel and the task card - goes through this, so the
 * permission prompt, the global flag and the work reschedule never drift apart.
 *
 * The caller owns the flag itself: the callback receives true only when the permission is
 * actually held, so a denial leaves the global switch off and the caller can say so.
 */
@Composable
fun rememberNotificationGate(onResult: (granted: Boolean) -> Unit): (enable: Boolean) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }
    val latest = remember(onResult) { onResult }

    return remember(context, launcher, latest) {
        { enable ->
            when {
                !enable -> latest(false)
                Reminders.isPermissionGranted(context) -> latest(true)
                else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
