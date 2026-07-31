package ai.daakia.callkit.sample.config

import ai.daakia.callkit.DaakiaCallKitConfig
import ai.daakia.callkit.model.StaleCallBehavior
import ai.daakia.callkit.sample.BuildConfig
import ai.daakia.callkit.ui.IncomingCallStyle
import android.content.Context
import androidx.core.content.edit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Single source of truth for the sample's backend credentials and a couple of remembered
 * inputs, so nothing sensitive is ever hard-coded in source.
 *
 * Precedence: a value saved from the in-app Settings screen wins; otherwise the [BuildConfig]
 * default injected from the gitignored `local.properties` at build time is used. When both are
 * blank the SDK is not initialized until the user enters credentials in Settings.
 */
class SampleConfig(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Backend base URL: saved override, else the `DAAKIA_BASE_URL` build default. */
    val baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DAAKIA_BASE_URL

    /** Customer secret: saved override, else the `DAAKIA_SECRET` build default. Never logged. */
    val secret: String
        get() = prefs.getString(KEY_SECRET, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DAAKIA_SECRET

    /** Whether [baseUrl] and [secret] are both present, i.e. the SDK can be initialized. */
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && secret.isNotBlank()

    /** `true` once the user overrode credentials in the Settings screen (vs. build defaults). */
    val hasSavedOverride: Boolean
        get() = prefs.contains(KEY_BASE_URL) || prefs.contains(KEY_SECRET)

    /**
     * The last username [ai.daakia.callkit.DaakiaCallKit.registerDevice] succeeded for, so the
     * messaging service can re-register the device when FCM rotates the token.
     */
    var registeredUsername: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) =
            prefs.edit {
                if (value == null) {
                    remove(KEY_USERNAME)
                } else {
                    putString(
                        KEY_USERNAME,
                        value,
                    )
                }
            }

    /**
     * The UI module that renders the full-screen incoming-call (lock-screen) screen. Applied by
     * [ai.daakia.callkit.sample.SampleCallUi] at startup and whenever the user changes it.
     */
    var incomingCallModule: CallUiModule
        get() =
            prefs
                .getString(KEY_UI_MODULE, null)
                ?.let { runCatching { CallUiModule.valueOf(it) }.getOrNull() }
                ?: CallUiModule.COMPOSE
        set(value) = prefs.edit { putString(KEY_UI_MODULE, value.name) }

    /** The preset style used for the incoming-call screen. */
    var incomingCallStyle: IncomingCallStyle
        get() =
            prefs
                .getString(KEY_UI_STYLE, null)
                ?.let { runCatching { IncomingCallStyle.valueOf(it) }.getOrNull() }
                ?: IncomingCallStyle.CLASSIC
        set(value) = prefs.edit { putString(KEY_UI_STYLE, value.name) }

    /** Persists credentials entered in the Settings screen. */
    fun save(
        baseUrl: String,
        secret: String,
    ) {
        prefs
            .edit {
                putString(KEY_BASE_URL, baseUrl.trim())
                    .putString(KEY_SECRET, secret.trim())
            }
    }

    /** Clears the saved override so the build defaults apply again. */
    fun clearOverride() {
        prefs
            .edit {
                remove(KEY_BASE_URL)
                    .remove(KEY_SECRET)
            }
    }

    /** The SDK config, or `null` when credentials are incomplete. */
    fun toDaakiaConfig(callTimeout: Duration = 30.seconds): DaakiaCallKitConfig? =
        if (isComplete) {
            DaakiaCallKitConfig(
                baseUrl = baseUrl,
                secret = secret,
                callTimeout = callTimeout,
                // A device that is offline when a call is placed gets the push on reconnect,
                // which can be hours later. Anything older than this window is not rung;
                // the user gets a missed-call notification instead.
                staleCallWindow = STALE_CALL_WINDOW,
                staleCallBehavior = StaleCallBehavior.MISSED_NOTIFICATION,
            )
        } else {
            null
        }

    private companion object {
        /** How old a call push may be and still ring. */
        val STALE_CALL_WINDOW = 60.seconds

        const val PREFS_NAME = "daakia_sample_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_SECRET = "secret"
        const val KEY_USERNAME = "registered_username"
        const val KEY_UI_MODULE = "incoming_call_ui_module"
        const val KEY_UI_STYLE = "incoming_call_ui_style"
    }
}
