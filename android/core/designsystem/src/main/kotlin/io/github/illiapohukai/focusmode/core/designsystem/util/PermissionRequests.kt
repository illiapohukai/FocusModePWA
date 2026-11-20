package io.github.illiapohukai.focusmode.core.designsystem.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Whether the system would show a rationale (used to infer "permanently denied"). */
fun Context.notificationRationale(): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val activity = findActivity() ?: return null
    return activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
}

/**
 * Contextual notification-permission request. Returns a launcher; the callback receives whether it
 * was granted and the rationale flag right after the dialog. On Android 12 and lower there is no
 * runtime permission and the callback reports granted immediately.
 */
@Composable
fun rememberNotificationPermissionRequest(onResult: (granted: Boolean, rationale: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted, context.notificationRationale() ?: false)
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else onResult(true, false)
    }
}
