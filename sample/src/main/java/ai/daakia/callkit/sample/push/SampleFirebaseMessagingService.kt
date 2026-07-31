package ai.daakia.callkit.sample.push

import ai.daakia.callkit.DaakiaCallKit
import ai.daakia.callkit.sample.config.SampleConfig
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The sample's own [FirebaseMessagingService], demonstrating the primary SDK integration path:
 * forward every push to [DaakiaCallKit.handleRemoteMessage] and let the SDK consume the ones
 * that are Daakia incoming calls.
 *
 * Only one service in the app receives `MESSAGING_EVENT`, so a host that already has its own
 * messaging service (this one) must *not* also register the drop-in
 * `ai.daakia.callkit.push.DaakiaMessagingService` — that ready-made service is for hosts with
 * no messaging service of their own.
 */
class SampleFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        // Consumed => it was a Daakia call push; the SDK is already ringing it.
        if (DaakiaCallKit.handleRemoteMessage(message)) return

        // Not a Daakia push: the host handles it however it likes. The sample just logs it.
        Log.d(TAG, "Non-Daakia push received: ${message.data}")
    }

    /**
     * FCM rotated the registration token. Re-register the device so calls placed to the last
     * known username keep reaching it. Requires the SDK to already be initialized (it is, from
     * [ai.daakia.callkit.sample.SampleApp] when credentials are configured).
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val username = SampleConfig(this).registeredUsername ?: return
        scope.launch {
            runCatching { DaakiaCallKit.registerDevice(username, token) }
                .onSuccess { Log.d(TAG, "Re-registered rotated token for $username") }
                .onFailure { Log.w(TAG, "Failed to re-register rotated token", it) }
        }
    }

    private companion object {
        const val TAG = "SampleFcm"
    }
}
