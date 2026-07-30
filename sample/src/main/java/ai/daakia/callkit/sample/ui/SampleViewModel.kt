package ai.daakia.callkit.sample.ui

import ai.daakia.callkit.DaakiaCallKit
import ai.daakia.callkit.model.CallEvent
import ai.daakia.callkit.model.CallEventAction
import ai.daakia.callkit.model.CallEventType
import ai.daakia.callkit.model.Caller
import ai.daakia.callkit.model.DaakiaPlatform
import ai.daakia.callkit.model.IncomingCallData
import ai.daakia.callkit.sample.SampleCallUi
import ai.daakia.callkit.sample.config.CallUiModule
import ai.daakia.callkit.sample.config.SampleConfig
import ai.daakia.callkit.ui.IncomingCallStyle
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Drives the sample testbed: every button maps to one [DaakiaCallKit] API, and every result,
 * error, and lifecycle event is appended to an on-screen log so each SDK feature can be
 * verified on a device.
 */
class SampleViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val config = SampleConfig(application)

    private val _state = MutableStateFlow(SampleUiState())
    val state: StateFlow<SampleUiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                sdkInitialized = config.isComplete,
                baseUrl = config.baseUrl,
                usingBuildDefaults = !config.hasSavedOverride,
                secretConfigured = config.secret.isNotBlank(),
                canUseFullScreenIntent = safeCanUseFullScreenIntent(),
                callUiModule = config.incomingCallModule,
                callUiStyle = config.incomingCallStyle,
            )
        }
        if (!config.isComplete) {
            log("SDK not initialized — set the base URL and secret in Settings.", LogLevel.ERROR)
        } else {
            log("SDK initialized for ${config.baseUrl}", LogLevel.INFO)
        }
        observeCallEvents()
    }

    // region status & configuration

    fun refreshStatus() {
        _state.update {
            it.copy(
                sdkInitialized = config.isComplete,
                baseUrl = config.baseUrl,
                usingBuildDefaults = !config.hasSavedOverride,
                secretConfigured = config.secret.isNotBlank(),
                canUseFullScreenIntent = safeCanUseFullScreenIntent(),
            )
        }
    }

    /**
     * Persists new credentials and (re-)initializes the SDK. A blank field keeps the currently
     * effective value, so the user can change the base URL without re-entering the secret — and
     * the default secret is never surfaced in the UI to be re-typed.
     */
    fun applyConfig(
        baseUrl: String,
        secret: String,
    ) {
        val effectiveBaseUrl = baseUrl.ifBlank { config.baseUrl }
        val effectiveSecret = secret.ifBlank { config.secret }
        if (effectiveBaseUrl.isBlank() || effectiveSecret.isBlank()) {
            log("Base URL and secret are both required.", LogLevel.ERROR)
            return
        }
        config.save(effectiveBaseUrl, effectiveSecret)
        runCatching {
            DaakiaCallKit.initialize(getApplication(), config.toDaakiaConfig()!!)
        }.onSuccess {
            log("SDK re-initialized for ${config.baseUrl}", LogLevel.SUCCESS)
        }.onFailure { error ->
            log("Failed to initialize SDK: ${error.message}", LogLevel.ERROR)
        }
        refreshStatus()
    }

    /** Drops the saved override and immediately re-initializes the SDK on the build-time defaults. */
    fun clearConfigOverride() {
        config.clearOverride()
        val restored = config.toDaakiaConfig()
        if (restored != null) {
            runCatching { DaakiaCallKit.initialize(getApplication(), restored) }
                .onSuccess { log("Reset to the built-in default. SDK re-initialized for ${config.baseUrl}.", LogLevel.SUCCESS) }
                .onFailure { log("Reset done, but re-initialize failed: ${it.message}", LogLevel.ERROR) }
        } else {
            log("Reset to defaults, but no built-in default is set — enter credentials to initialize.", LogLevel.INFO)
        }
        refreshStatus()
    }

    // endregion

    // region device registration

    @Suppress("DEPRECATION")
    private suspend fun currentFcmToken(): String = FirebaseMessaging.getInstance().token.await()

    fun fetchFcmToken() {
        viewModelScope.launch {
            runCatching { currentFcmToken() }
                .onSuccess { token ->
                    _state.update { it.copy(fcmToken = token) }
                    log("FCM token: $token", LogLevel.SUCCESS)
                }.onFailure { log("Failed to get FCM token: ${it.message}", LogLevel.ERROR) }
        }
    }

    fun registerDevice(username: String) {
        if (!guardReady() || !guardNotBlank("Username", username)) return
        viewModelScope.launch {
            val token = _state.value.fcmToken ?: runCatching { currentFcmToken() }.getOrNull()
            if (token == null) {
                log("No FCM token available — fetch it first.", LogLevel.ERROR)
                return@launch
            }
            runCatching { DaakiaCallKit.registerDevice(username, token) }
                .onSuccess { record ->
                    config.registeredUsername = username
                    log("Registered '$username' (id=${record.id}, platform=${record.platform})", LogLevel.SUCCESS)
                }.onFailure { log("registerDevice failed: ${it.message}", LogLevel.ERROR) }
        }
    }

    fun getRegisteredDevice(username: String) {
        if (!guardReady() || !guardNotBlank("Username", username)) return
        viewModelScope.launch {
            runCatching { DaakiaCallKit.getRegisteredDevice(username) }
                .onSuccess { record ->
                    log("Device for '${record.username}': ${record.platform} token=${record.token.take(16)}…", LogLevel.SUCCESS)
                }.onFailure { log("getRegisteredDevice failed: ${it.message}", LogLevel.ERROR) }
        }
    }

    // endregion

    // region placing calls

    fun startCallByUsername(
        targetUsername: String,
        callId: String,
        callerName: String,
        body: String,
        receiverId: String?,
    ) {
        if (!guardReady() || !guardNotBlank("Target username", targetUsername)) return
        val call = buildCall(callId, callerName, body, receiverId)
        viewModelScope.launch {
            runCatching { DaakiaCallKit.startCallByUsername(targetUsername, call) }
                .onSuccess {
                    log(
                        "startCallByUsername -> success=${it.success} ${it.message}",
                        if (it.success) LogLevel.SUCCESS else LogLevel.ERROR,
                    )
                }.onFailure { log("startCallByUsername failed: ${it.message}", LogLevel.ERROR) }
        }
    }

    fun startCallByToken(
        token: String,
        platform: DaakiaPlatform,
        callId: String,
        callerName: String,
        body: String,
        receiverId: String?,
    ) {
        if (!guardReady() || !guardNotBlank("Device token", token)) return
        val call = buildCall(callId, callerName, body, receiverId)
        viewModelScope.launch {
            runCatching { DaakiaCallKit.startCallByToken(token, platform, call) }
                .onSuccess {
                    log(
                        "startCallByToken -> success=${it.success} ${it.message}",
                        if (it.success) LogLevel.SUCCESS else LogLevel.ERROR,
                    )
                }.onFailure { log("startCallByToken failed: ${it.message}", LogLevel.ERROR) }
        }
    }

    private fun buildCall(
        callId: String,
        callerName: String,
        body: String,
        receiverId: String?,
    ): IncomingCallData =
        IncomingCallData(
            callId = callId.ifBlank { "call-${System.currentTimeMillis()}" },
            title = callerName.ifBlank { "Caller" },
            body = body.ifBlank { IncomingCallData.DEFAULT_BODY },
            sender = Caller(uid = "caller", userName = callerName.ifBlank { "Caller" }),
            receiverId = receiverId?.takeIf { it.isNotBlank() },
        )

    // endregion

    // region call events & fallback webhook

    fun sendCallEvent(
        meetingUid: String,
        action: CallEventAction,
        metadataJson: String,
    ) {
        if (!guardReady() || !guardNotBlank("Meeting UID", meetingUid)) return
        val metadata = parseMetadata(metadataJson) ?: return
        viewModelScope.launch {
            runCatching { DaakiaCallKit.sendCallEvent(meetingUid, action, metadata) }
                .onSuccess { log("sendCallEvent(${action.value}) delivered for $meetingUid", LogLevel.SUCCESS) }
                .onFailure { log("sendCallEvent failed: ${it.message}", LogLevel.ERROR) }
        }
    }

    fun configureFallback(
        actions: Set<CallEventAction>,
        metadataJson: String,
    ) {
        val metadata = parseMetadata(metadataJson) ?: return
        runCatching { DaakiaCallKit.configureCallEventFallback(actions, metadata) }
            .onSuccess {
                if (actions.isEmpty()) {
                    log("Fallback cleared (empty action set).", LogLevel.INFO)
                } else {
                    log("Fallback configured for ${actions.joinToString { it.value }}", LogLevel.SUCCESS)
                }
            }.onFailure { log("configureCallEventFallback failed: ${it.message}", LogLevel.ERROR) }
    }

    fun clearFallback() {
        DaakiaCallKit.clearCallEventFallback()
        log("Killed-state fallback disabled.", LogLevel.INFO)
    }

    fun clearSentCache() {
        DaakiaCallKit.clearSentCallEventCache()
        log("Sent-event dedupe cache cleared.", LogLevel.INFO)
    }

    // endregion

    // region local call control & permissions

    fun endCall(callId: String?) {
        DaakiaCallKit.endCall(callId?.takeIf { it.isNotBlank() })
        log("endCall(${callId?.takeIf { it.isNotBlank() } ?: "any ringing call"}) requested.", LogLevel.INFO)
    }

    fun openFullScreenIntentSettings() {
        val opened = DaakiaCallKit.openFullScreenIntentSettings()
        log(if (opened) "Opened full-screen-intent settings." else "Full-screen-intent settings unavailable on this OS.", LogLevel.INFO)
    }

    /**
     * Selects and installs the incoming-call (lock-screen) screen used for real calls. Persisted,
     * so it also applies on the next app launch. The preview button re-installs the same choice.
     */
    fun setCallUi(
        module: CallUiModule,
        style: IncomingCallStyle,
    ) {
        config.incomingCallModule = module
        config.incomingCallStyle = style
        SampleCallUi.install(module, style)
        _state.update { it.copy(callUiModule = module, callUiStyle = style) }
        log("Incoming-call screen set to $module / $style", LogLevel.INFO)
    }

    // endregion

    // region events & log

    private fun observeCallEvents() {
        viewModelScope.launch {
            val backlog = DaakiaCallKit.callEvents.replayCache.size
            DaakiaCallKit.callEvents
                .drop(backlog)
                // ACCEPTED is surfaced through consumeLaunchEvent in MainActivity (killed-state path).
                .filter { it.type != CallEventType.ACCEPTED }
                .collect { logCallEvent(it, viaLaunch = false) }
        }
    }

    /** Called by MainActivity for the accept-from-launch (killed-state) recovery path. */
    fun onLaunchEvent(event: CallEvent) = logCallEvent(event, viaLaunch = true)

    private fun logCallEvent(
        event: CallEvent,
        viaLaunch: Boolean,
    ) {
        val source = if (viaLaunch) " (via launch intent)" else ""
        val name = event.call.callerDisplayName
        val reason = event.reason?.let { " reason=$it" } ?: ""
        log("Call ${event.type}$source: $name [${event.call.callId}]$reason", LogLevel.EVENT)
    }

    fun clearLog() {
        _state.update { it.copy(log = emptyList()) }
    }

    private fun log(
        message: String,
        level: LogLevel,
    ) {
        _state.update { current ->
            current.copy(log = (listOf(LogEntry.now(message, level)) + current.log).take(MAX_LOG_LINES))
        }
    }

    // endregion

    // region helpers

    private fun guardReady(): Boolean {
        if (!config.isComplete) {
            log("SDK is not initialized. Configure credentials in Settings first.", LogLevel.ERROR)
            return false
        }
        return true
    }

    private fun guardNotBlank(
        label: String,
        value: String,
    ): Boolean {
        if (value.isBlank()) {
            log("$label is required.", LogLevel.ERROR)
            return false
        }
        return true
    }

    /** Parses the metadata text field: blank -> null map; invalid JSON -> logs and returns null. */
    private fun parseMetadata(raw: String): Map<String, String>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyMap()
        return runCatching {
            val json = JSONObject(trimmed)
            buildMap {
                json.keys().forEach { key -> put(key, json.getString(key)) }
            }
        }.getOrElse {
            log("Metadata must be a flat JSON object of strings.", LogLevel.ERROR)
            null
        }
    }

    private fun safeCanUseFullScreenIntent(): Boolean = runCatching { DaakiaCallKit.canUseFullScreenIntent() }.getOrDefault(true)

    // endregion

    private companion object {
        const val MAX_LOG_LINES = 200
    }
}
