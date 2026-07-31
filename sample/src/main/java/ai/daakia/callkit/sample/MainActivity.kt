package ai.daakia.callkit.sample

import ai.daakia.callkit.DaakiaCallKit
import ai.daakia.callkit.model.CallEventType
import ai.daakia.callkit.sample.config.CallUiModule
import ai.daakia.callkit.sample.ui.HomeActions
import ai.daakia.callkit.sample.ui.HomeScreen
import ai.daakia.callkit.sample.ui.SampleViewModel
import ai.daakia.callkit.sample.ui.theme.DaakiaSampleTheme
import ai.daakia.callkit.ui.IncomingCallStyle
import ai.daakia.callkit.ui.compose.DaakiaIncomingCallActivity
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daakia.callkit.ui.views.DaakiaIncomingCallActivity as ViewsIncomingCallActivity

/**
 * Host activity for the testbed. Beyond rendering the [HomeScreen] it demonstrates the two
 * lifecycle touch-points a real host implements:
 *
 * - [DaakiaCallKit.consumeLaunchEvent] to recover an accept that happened while the app was
 *   dead (the killed-state path), and
 * - showing over the lock screen when launched from an accepted call.
 *
 * Ongoing call events (incoming/declined/ended/timed-out) are observed in [SampleViewModel].
 */
class MainActivity : ComponentActivity() {
    private val viewModel: SampleViewModel by viewModels()

    /** Set while [DemoCallActivity] is being opened for an accepted call — see [onStop]. */
    private var handingOffToCallScreen = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // A denied permission is not fatal; the SDK falls back to a heads-up notification.
            viewModel.refreshStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunch(intent)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            DaakiaSampleTheme {
                HomeScreen(state = state, actions = buildActions())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunch(intent)
    }

    override fun onStop() {
        super.onStop()
        // Stopping because the demo call screen took over is not a reason to stop showing over
        // the keyguard: clearing the flag mid-handoff lets the keyguard slide back in front of
        // the task before the call screen's own flags apply. DemoCallActivity keeps the task
        // over the lock screen from here; the next ordinary onStop resets us.
        if (handingOffToCallScreen) {
            handingOffToCallScreen = false
            return
        }
        applyLockScreenVisibility(false)
    }

    private fun handleLaunch(intent: Intent) {
        val event = DaakiaCallKit.consumeLaunchEvent(intent)
        applyLockScreenVisibility(overLockScreen = event != null)
        event?.let(viewModel::onLaunchEvent)

        // An accepted call means the user chose to join. A real host would hand off to its
        // audio/video stack here; until the Daakia VC SDK exists we open a stand-in in-call
        // screen so the sample shows the full accept-to-in-call journey. The accept usually
        // comes from the lock screen, so we keep this activity over the keyguard until that
        // screen — which shows over it in turn — has taken over.
        if (event?.type == CallEventType.ACCEPTED) {
            handingOffToCallScreen = true
            startActivity(
                DemoCallActivity.intent(
                    context = this,
                    callerName = event.call.callerDisplayName,
                    callId = event.call.callId,
                ),
            )
        }
    }

    /**
     * When launched from an accepted call, show this screen *over* the lock screen and wake the
     * device — like a phone call — so the user lands straight in the app. We deliberately do not
     * call `KeyguardManager.requestDismissKeyguard`: on a secured device that forces the PIN/
     * pattern/password prompt before the app appears. A real host can request a full unlock later,
     * only when it needs to leave the call screen for content that must stay behind the keyguard.
     */
    private fun applyLockScreenVisibility(overLockScreen: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(overLockScreen)
            setTurnScreenOn(overLockScreen)
        }
    }

    private fun buildActions(): HomeActions =
        HomeActions(
            applyConfig = viewModel::applyConfig,
            clearConfigOverride = viewModel::clearConfigOverride,
            fetchFcmToken = viewModel::fetchFcmToken,
            registerDevice = viewModel::registerDevice,
            getRegisteredDevice = viewModel::getRegisteredDevice,
            startCallByUsername = viewModel::startCallByUsername,
            startCallByToken = viewModel::startCallByToken,
            sendCallEvent = viewModel::sendCallEvent,
            configureFallback = viewModel::configureFallback,
            clearFallback = viewModel::clearFallback,
            clearSentCache = viewModel::clearSentCache,
            endCall = viewModel::endCall,
            openFullScreenIntentSettings = viewModel::openFullScreenIntentSettings,
            requestNotificationPermission = ::requestNotificationPermission,
            setCallUi = viewModel::setCallUi,
            previewCall = ::previewCallScreen,
            clearLog = viewModel::clearLog,
        )

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Installs the chosen module + style and opens its call screen with a demo payload. */
    private fun previewCallScreen(
        module: CallUiModule,
        style: IncomingCallStyle,
    ) {
        SampleCallUi.install(module, style)
        val activity =
            when (module) {
                CallUiModule.COMPOSE -> DaakiaIncomingCallActivity::class.java
                CallUiModule.VIEWS -> ViewsIncomingCallActivity::class.java
            }
        startActivity(
            Intent(this, activity)
                .putExtra(EXTRA_PAYLOAD, demoPayloadJson()),
        )
    }

    private fun demoPayloadJson(): String =
        """
        {"type":"incoming_call","callId":"preview-${System.currentTimeMillis()}",
         "title":"Incoming call","body":"Respond to join or decline the call",
         "sender":"{\"uid\":\"caller\",\"userName\":\"Jane Doe\"}"}
        """.trimIndent()

    private companion object {
        /**
         * Mirrors the SDK's internal `IncomingCallService.EXTRA_PAYLOAD` so the preview can hand
         * the call screen a demo payload directly — only for the preview buttons; real calls are
         * launched by the SDK.
         */
        const val EXTRA_PAYLOAD = "ai.daakia.callkit.extra.PAYLOAD"
    }
}
