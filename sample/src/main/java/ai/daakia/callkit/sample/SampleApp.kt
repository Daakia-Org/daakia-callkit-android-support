package ai.daakia.callkit.sample

import ai.daakia.callkit.DaakiaCallKit
import ai.daakia.callkit.sample.config.SampleConfig
import android.app.Application

/**
 * Initializes the SDK once, from credentials resolved by [SampleConfig] (build defaults from
 * the gitignored `local.properties`, or a saved on-device override). When credentials are
 * missing the SDK is left uninitialized until the user enters them in the in-app Settings
 * screen — no secret is ever hard-coded here.
 */
class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = SampleConfig(this)
        config.toDaakiaConfig()?.let { DaakiaCallKit.initialize(context = this, config = it) }

        // Register the incoming-call screen from the user's saved module + style choice, so real
        // calls (over the lock screen) use the same screen the "Incoming call screen" section shows.
        SampleCallUi.install(config.incomingCallModule, config.incomingCallStyle)
    }
}
