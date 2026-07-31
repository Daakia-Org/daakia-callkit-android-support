package ai.daakia.callkit.sample

import ai.daakia.callkit.sample.ui.theme.DaakiaSampleTheme
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Placeholder in-call screen the testbed opens after a call is accepted.
 *
 * [DaakiaCallKit][ai.daakia.callkit.DaakiaCallKit] is a *signaling* SDK: it rings the device,
 * reports accept/decline, and stops there — it never carries audio or video. Connecting the media
 * is the host app's job, with whatever stack it already uses (WebRTC, LiveKit, Jitsi, Agora, or
 * the Daakia VC SDK once it ships).
 *
 * This screen is a stand-in for that step so the testbed can demonstrate the whole
 * accept-to-in-call journey instead of dropping the user back on the home screen. Nothing here is
 * real: there is no media session, and the mute button and call timer are cosmetic. The screen
 * labels itself as a demo throughout so nobody mistakes it for a working call.
 */
class DemoCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showOverLockScreen()

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty().ifBlank { "Unknown" }
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()

        setContent {
            DaakiaSampleTheme {
                DemoCallScreen(
                    callerName = callerName,
                    callId = callId,
                    onLeave = { finish() },
                )
            }
        }
    }

    /**
     * A call is usually accepted straight off the lock screen, and — like every other calling app
     * — the in-call screen has to appear there rather than behind the keyguard. This mirrors what
     * the SDK's own incoming-call activity does: the API 27+ calls plus the legacy window flags,
     * which still carry the behaviour on older releases and on OEM skins that honour only those.
     * The manifest declares `showWhenLocked`/`turnScreenOn` too, so the window is created with
     * them already set instead of racing the first frame.
     *
     * We deliberately do not dismiss the keyguard: the user joined a call, not the whole device.
     * They can unlock later if they want to leave this screen for the rest of the app.
     */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            // Real call UIs hold the screen on while the user is in a call.
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
    }

    companion object {
        private const val EXTRA_CALLER_NAME = "ai.daakia.callkit.sample.extra.CALLER_NAME"
        private const val EXTRA_CALL_ID = "ai.daakia.callkit.sample.extra.CALL_ID"

        /** Builds the intent that opens the demo in-call screen for an accepted call. */
        fun intent(
            context: Context,
            callerName: String,
            callId: String,
        ): Intent =
            Intent(context, DemoCallActivity::class.java)
                .putExtra(EXTRA_CALLER_NAME, callerName)
                .putExtra(EXTRA_CALL_ID, callId)
    }
}

@Composable
private fun DemoCallScreen(
    callerName: String,
    callId: String,
    onLeave: () -> Unit,
) {
    var connected by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // Mimic the join handshake: a real VC SDK would flip this when its media session goes live.
    LaunchedEffect(Unit) {
        delay(2_000.milliseconds)
        connected = true
    }

    // Cosmetic call timer, started at "connected". Ticks off elapsedRealtime rather than counting
    // delays so it stays accurate if the device sleeps or a frame is dropped.
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            elapsedSeconds = (elapsedMillis / 1_000L).toInt()
            delay((1_000L - elapsedMillis % 1_000L).milliseconds)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = SCREEN_BACKGROUND) {
        // One flowing Column rather than a Box with Center/BottomCenter children: absolute
        // alignments overlap once the free height shrinks, and Android 15 forces edge-to-edge, so
        // safeDrawing claims more of the screen than it does on older releases.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            DemoBadge()

            // Remote-participant "video" stand-in — avatar + name + call state. Takes the slack
            // between the badge and the controls, so it stays centred without overlapping either.
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(name = callerName)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (connected) formatDuration(elapsedSeconds) else "Connecting…",
                    color = if (connected) CONNECTED_GREEN else MUTED_TEXT,
                    fontSize = 15.sp,
                )
                if (callId.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = "Call ID: $callId", color = FAINT_TEXT, fontSize = 12.sp)
                }
            }

            DemoNotice()
            Spacer(Modifier.height(28.dp))
            ControlBar(
                micOn = micOn,
                onToggleMic = { micOn = !micOn },
                onLeave = onLeave,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Pill at the top of the screen: the first thing that says "none of this is real". */
@Composable
private fun DemoBadge(modifier: Modifier = Modifier) {
    Text(
        text = "DEMO SCREEN · NO REAL AUDIO OR VIDEO",
        color = BADGE_TEXT,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(BADGE_BACKGROUND)
                .border(1.dp, BADGE_BORDER, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** Spells out whose job the media is, so the placeholder isn't mistaken for an SDK feature. */
@Composable
private fun DemoNotice() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NOTICE_BACKGROUND)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Placeholder for your call UI",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text =
                "Daakia CallKit signals the call and tells you it was accepted. Connecting it is " +
                    "your app's choice — open your own call screen on the ACCEPTED event and join " +
                    "with whatever stack you already use.",
            color = MUTED_TEXT,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Avatar(name: String) {
    val initials =
        name
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }

    Box(
        modifier =
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Mute and leave only. There is deliberately no camera toggle: this screen owns no media, and a
 * camera button implies a video pipeline the sample does not have.
 */
@Composable
private fun ControlBar(
    micOn: Boolean,
    onToggleMic: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top,
    ) {
        ControlButton(
            icon = if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff,
            label = if (micOn) "Mute" else "Unmute",
            contentDescription = if (micOn) "Mute microphone (demo only)" else "Unmute microphone (demo only)",
            active = micOn,
            onClick = onToggleMic,
        )
        Spacer(Modifier.width(40.dp))
        ControlButton(
            icon = Icons.Filled.CallEnd,
            label = "Leave",
            contentDescription = "Leave the demo call",
            background = LEAVE_RED,
            onClick = onLeave,
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = true,
    background: Color? = null,
) {
    val target = background ?: if (active) CONTROL_IDLE else CONTROL_TOGGLED
    val color by animateColorAsState(targetValue = target, label = "controlBackground")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(text = label, color = MUTED_TEXT, fontSize = 12.sp)
    }
}

/** `mm:ss`, widening to `h:mm:ss` once the demo call passes an hour. */
private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private val SCREEN_BACKGROUND = Color(0xFF10131A)
private val CONNECTED_GREEN = Color(0xFF4CAF50)
private val MUTED_TEXT = Color(0xFFB0B6C0)
private val FAINT_TEXT = Color(0xFF6B7280)
private val NOTICE_BACKGROUND = Color(0xFF1B1F29)
private val BADGE_BACKGROUND = Color(0x33F4B740)
private val BADGE_BORDER = Color(0x66F4B740)
private val BADGE_TEXT = Color(0xFFF4B740)
private val CONTROL_IDLE = Color(0xFF2A2F3A)
private val CONTROL_TOGGLED = Color(0xFF4A4F5A)
private val LEAVE_RED = Color(0xFFE53935)
