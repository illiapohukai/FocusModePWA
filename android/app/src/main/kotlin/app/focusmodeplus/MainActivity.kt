package app.focusmodeplus

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.notifications.DeepLinkAuthenticator
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    @Inject lateinit var routes: DeepLinkAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val motion by appViewModel.motion.collectAsStateWithLifecycle()
            FocusModeTheme(motion) {
                FocusApp(appViewModel, window)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Only routes this app put into its own notifications navigate (v22: tagged per install, see
     * [DeepLinkAuthenticator]); any other caller just opens the app. Every route is re-validated.
     */
    private fun handleIntent(intent: Intent?) {
        routes.routeFrom(intent)?.let(appViewModel::onDeepLink)
    }
}
