package ai.daakia.callkit.sample.ui

import ai.daakia.callkit.sample.config.CallUiModule
import ai.daakia.callkit.ui.IncomingCallStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Severity of a [LogEntry], used to color the log console. */
enum class LogLevel { INFO, SUCCESS, ERROR, EVENT }

/** One line in the sample's on-screen activity log. */
data class LogEntry(
    val time: String,
    val message: String,
    val level: LogLevel,
) {
    companion object {
        private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

        fun now(
            message: String,
            level: LogLevel,
        ): LogEntry = LogEntry(formatter.format(Date()), message, level)
    }
}

/**
 * Everything the home screen renders that is not transient text-field input (which lives in the
 * composables as `rememberSaveable` state). Owned by [SampleViewModel] so it survives rotation.
 */
data class SampleUiState(
    val sdkInitialized: Boolean = false,
    val baseUrl: String = "",
    val usingBuildDefaults: Boolean = true,
    val secretConfigured: Boolean = false,
    val canUseFullScreenIntent: Boolean = true,
    val fcmToken: String? = null,
    val callUiModule: CallUiModule = CallUiModule.COMPOSE,
    val callUiStyle: IncomingCallStyle = IncomingCallStyle.CLASSIC,
    val log: List<LogEntry> = emptyList(),
)
