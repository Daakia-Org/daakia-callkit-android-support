package ai.daakia.callkit.sample.ui

import ai.daakia.callkit.model.CallEventAction
import ai.daakia.callkit.model.DaakiaPlatform
import ai.daakia.callkit.sample.config.CallUiModule
import ai.daakia.callkit.ui.IncomingCallStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: SampleUiState,
    actions: HomeActions,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Daakia CallKit Testbed") }) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusSection(state)
            SettingsSection(state, actions)
            RegistrationSection(state, actions)
            PlaceCallSection(actions)
            CallEventSection(actions)
            FallbackSection(actions)
            ControlSection(state, actions)
            IncomingCallScreenSection(state, actions)
            LogSection(state, actions)
        }
    }
}

/** Callbacks the screen invokes — everything the ViewModel exposes, plus the two UI-preview launchers. */
data class HomeActions(
    val applyConfig: (baseUrl: String, secret: String) -> Unit,
    val clearConfigOverride: () -> Unit,
    val fetchFcmToken: () -> Unit,
    val registerDevice: (username: String) -> Unit,
    val getRegisteredDevice: (username: String) -> Unit,
    val startCallByUsername: (target: String, callId: String, caller: String, body: String, receiverId: String) -> Unit,
    val startCallByToken: (
        token: String,
        platform: DaakiaPlatform,
        callId: String,
        caller: String,
        body: String,
        receiverId: String,
    ) -> Unit,
    val sendCallEvent: (meetingUid: String, action: CallEventAction, metadata: String) -> Unit,
    val configureFallback: (actions: Set<CallEventAction>, metadata: String) -> Unit,
    val clearFallback: () -> Unit,
    val clearSentCache: () -> Unit,
    val endCall: (callId: String) -> Unit,
    val openFullScreenIntentSettings: () -> Unit,
    val requestNotificationPermission: () -> Unit,
    val setCallUi: (module: CallUiModule, style: IncomingCallStyle) -> Unit,
    val previewCall: (module: CallUiModule, style: IncomingCallStyle) -> Unit,
    val clearLog: () -> Unit,
)

@Composable
private fun StatusSection(state: SampleUiState) {
    SectionCard(title = "Status") {
        StatusRow(
            label = "SDK",
            value = if (state.sdkInitialized) "initialized" else "not initialized",
            ok = state.sdkInitialized,
        )
        StatusRow(
            label = "Backend",
            value = state.baseUrl.ifBlank { "(unset)" } + if (state.usingBuildDefaults) " (build default)" else " (saved)",
            ok = state.baseUrl.isNotBlank(),
        )
        StatusRow(
            label = "Full-screen intent",
            value = if (state.canUseFullScreenIntent) "allowed" else "blocked (heads-up only)",
            ok = state.canUseFullScreenIntent,
        )
    }
}

@Composable
private fun SettingsSection(
    state: SampleUiState,
    actions: HomeActions,
) {
    SectionCard(
        title = "Configuration",
        subtitle = "Defaults come from the gitignored local.properties; overrides save on-device. The secret is never logged or shown.",
    ) {
        // Re-initialize the editable fields whenever the committed config changes (apply / reset),
        // so Reset to default restores the base URL and returns the secret to its masked state.
        key(state.baseUrl, state.usingBuildDefaults) {
            var baseUrl by rememberSaveable { mutableStateOf(state.baseUrl) }
            var secret by rememberSaveable { mutableStateOf("") }
            LabeledField(label = "Base URL", value = baseUrl, onValueChange = { baseUrl = it })
            Spacer(Modifier.height(8.dp))
            SecretField(
                label = "Secret / license key",
                value = secret,
                onValueChange = { secret = it },
                configured = state.secretConfigured,
            )
            Spacer(Modifier.height(12.dp))
            ButtonRow(
                primaryLabel = "Apply & initialize",
                onPrimary = { actions.applyConfig(baseUrl.trim(), secret.trim()) },
                secondaryLabel = "Reset to default",
                onSecondary = actions.clearConfigOverride,
                secondaryEnabled = !state.usingBuildDefaults,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                when {
                    !state.secretConfigured -> "No secret set — enter one to initialize."
                    state.usingBuildDefaults -> "Using the built-in default secret (hidden and inaccessible). Tap edit to replace it."
                    else -> "Using a saved secret (hidden). Tap edit to replace it."
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RegistrationSection(
    state: SampleUiState,
    actions: HomeActions,
) {
    var username by rememberSaveable { mutableStateOf("") }
    SectionCard(
        title = "Device registration",
        subtitle = "Register this device's FCM token so calls placed to the username reach it.",
    ) {
        LabeledField(
            label = "Username",
            value = username,
            onValueChange = { username = it },
            placeholder = "who this device registers as",
        )
        state.fcmToken?.let {
            Spacer(Modifier.height(8.dp))
            Text("Token: ${it.take(28)}…", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        ButtonRow(
            primaryLabel = "Register device",
            onPrimary = { actions.registerDevice(username.trim()) },
            secondaryLabel = "Fetch FCM token",
            onSecondary = actions.fetchFcmToken,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { actions.getRegisteredDevice(username.trim()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Look up registered device") }
    }
}

@Composable
private fun PlaceCallSection(actions: HomeActions) {
    var target by rememberSaveable { mutableStateOf("") }
    var callId by rememberSaveable { mutableStateOf("") }
    var callerName by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var receiverId by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf(DaakiaPlatform.ANDROID) }

    SectionCard(
        title = "Place a call",
        subtitle = "Triggers a push through the backend. The target device rings if it is registered.",
    ) {
        LabeledField(
            label = "Meeting UID / callId",
            value = callId,
            onValueChange = { callId = it },
            placeholder = "auto-generated if left blank",
        )
        Spacer(Modifier.height(8.dp))
        LabeledField(
            label = "Caller name (title)",
            value = callerName,
            onValueChange = { callerName = it },
            placeholder = "shown as the call title",
        )
        Spacer(Modifier.height(8.dp))
        LabeledField(
            label = "Body",
            value = body,
            onValueChange = { body = it },
            placeholder = "notification body text",
        )
        Spacer(Modifier.height(8.dp))
        LabeledField(
            label = "Receiver id (optional)",
            value = receiverId,
            onValueChange = { receiverId = it },
            placeholder = "backend id of the callee",
        )

        Spacer(Modifier.height(16.dp))
        Text("By username", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LabeledField(
            label = "Target username",
            value = target,
            onValueChange = { target = it },
            placeholder = "username to call",
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { actions.startCallByUsername(target.trim(), callId.trim(), callerName.trim(), body.trim(), receiverId.trim()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start call by username") }

        Spacer(Modifier.height(16.dp))
        Text("By device token", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LabeledField(
            label = "Device token",
            value = token,
            onValueChange = { token = it },
            placeholder = "FCM/APNs token of the target device",
        )
        Spacer(Modifier.height(8.dp))
        EnumDropdown(
            label = "Platform",
            options = DaakiaPlatform.entries,
            selected = platform,
            optionLabel = { it.name },
            onSelected = { platform = it },
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                actions.startCallByToken(
                    token.trim(),
                    platform,
                    callId.trim(),
                    callerName.trim(),
                    body.trim(),
                    receiverId.trim(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start call by token") }
    }
}

@Composable
private fun CallEventSection(actions: HomeActions) {
    var meetingUid by rememberSaveable { mutableStateOf("") }
    var action by rememberSaveable { mutableStateOf(CallEventAction.JOIN) }
    var metadata by rememberSaveable { mutableStateOf("") }
    SectionCard(
        title = "Report a call event",
        subtitle = "Sends a webhook to the backend. Deduped per (meetingUid, action).",
    ) {
        LabeledField(
            label = "Meeting UID",
            value = meetingUid,
            onValueChange = { meetingUid = it },
            placeholder = "callId of the call",
        )
        Spacer(Modifier.height(8.dp))
        EnumDropdown(
            label = "Action",
            options = CallEventAction.entries,
            selected = action,
            optionLabel = { it.value },
            onSelected = { action = it },
        )
        Spacer(Modifier.height(8.dp))
        LabeledField(
            label = "Metadata (JSON)",
            value = metadata,
            onValueChange = { metadata = it },
            placeholder = "{\"key\":\"value\"}",
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { actions.sendCallEvent(meetingUid.trim(), action, metadata) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Send call event") }
    }
}

@Composable
private fun FallbackSection(actions: HomeActions) {
    var selected by rememberSaveable(
        stateSaver =
            listSaver(
                save = { set -> set.map(CallEventAction::name) },
                restore = { names -> names.map(CallEventAction::valueOf).toSet() },
            ),
    ) { mutableStateOf(setOf(CallEventAction.ACCEPT, CallEventAction.REJECT, CallEventAction.TIMEOUT)) }
    var metadata by rememberSaveable { mutableStateOf("") }
    SectionCard(
        title = "Killed-state fallback",
        subtitle = "WorkManager reports these actions even when the app process is dead.",
    ) {
        MultiSelectChips(
            options = CallEventAction.entries,
            selected = selected,
            optionLabel = { it.value },
            onToggle = { selected = if (it in selected) selected - it else selected + it },
        )
        Spacer(Modifier.height(8.dp))
        LabeledField(
            label = "Metadata (JSON)",
            value = metadata,
            onValueChange = { metadata = it },
            placeholder = "{\"key\":\"value\"}",
        )
        Spacer(Modifier.height(12.dp))
        ButtonRow(
            primaryLabel = "Configure fallback",
            onPrimary = { actions.configureFallback(selected, metadata) },
            secondaryLabel = "Clear fallback",
            onSecondary = actions.clearFallback,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = actions.clearSentCache, modifier = Modifier.fillMaxWidth()) {
            Text("Clear sent-event dedupe cache")
        }
    }
}

@Composable
private fun ControlSection(
    state: SampleUiState,
    actions: HomeActions,
) {
    var callId by rememberSaveable { mutableStateOf("") }
    SectionCard(
        title = "Local control & permissions",
        subtitle = "endCall stops a ringing call locally (e.g. caller hung up).",
    ) {
        LabeledField(
            label = "callId to end",
            value = callId,
            onValueChange = { callId = it },
            placeholder = "blank = end any ringing call",
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { actions.endCall(callId.trim()) }, modifier = Modifier.fillMaxWidth()) {
            Text("End ringing call")
        }
        Spacer(Modifier.height(8.dp))
        ButtonRow(
            primaryLabel = "Request notifications",
            onPrimary = actions.requestNotificationPermission,
            secondaryLabel = "Full-screen settings",
            onSecondary = actions.openFullScreenIntentSettings,
        )
        if (!state.canUseFullScreenIntent) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Full-screen intents are blocked; calls show as heads-up notifications only.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun IncomingCallScreenSection(
    state: SampleUiState,
    actions: HomeActions,
) {
    SectionCard(
        title = "Incoming call screen",
        subtitle = "Shown over the lock screen for real calls. Applied immediately and remembered across launches.",
    ) {
        EnumDropdown(
            label = "UI module",
            options = CallUiModule.entries,
            selected = state.callUiModule,
            optionLabel = { it.name },
            onSelected = { actions.setCallUi(it, state.callUiStyle) },
        )
        Spacer(Modifier.height(8.dp))
        EnumDropdown(
            label = "Style",
            options = IncomingCallStyle.entries,
            selected = state.callUiStyle,
            optionLabel = { it.name },
            onSelected = { actions.setCallUi(state.callUiModule, it) },
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { actions.previewCall(state.callUiModule, state.callUiStyle) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Preview call screen (demo payload)") }
    }
}

@Composable
private fun LogSection(
    state: SampleUiState,
    actions: HomeActions,
) {
    SectionCard(title = "Activity log") {
        TextButton(onClick = actions.clearLog) { Text("Clear log") }
        Spacer(Modifier.height(8.dp))
        LogConsole(log = state.log)
    }
}

@Composable
private fun ButtonRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    secondaryEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onSecondary,
            enabled = secondaryEnabled,
            modifier = Modifier.weight(1f),
        ) { Text(secondaryLabel) }
        Button(onClick = onPrimary, modifier = Modifier.weight(1f)) { Text(primaryLabel) }
    }
}
