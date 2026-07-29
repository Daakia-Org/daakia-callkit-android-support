# Getting started

This guide takes an empty Android app to a ringing incoming call. Budget about 20 minutes.

At the end you will have an app that registers its device with Daakia, rings with a
full-screen call screen over the lock screen when a call arrives, and tells you when the
user accepted.

Every code block here is complete — you can paste it as-is. Nothing is abbreviated with `...`.

## Before you start

You need four things:

- **A Firebase project** with your app's package name registered, and its `google-services.json`.
  The SDK does not ship Firebase configuration — it consumes yours. If you have never done
  this, do it at [console.firebase.google.com](https://console.firebase.google.com) first:
  create a project, then *Add app → Android*, enter your `applicationId`, and download the file.
- **Your Daakia `baseUrl` and `secret`**, issued by Daakia.
- **`minSdk` 23 or higher** in your app module.
- **A real device.** Emulators can receive pushes, but lock-screen and full-screen-intent
  behavior is only trustworthy on hardware.

## Step 1 — Add the dependency

Pick one artifact based on how you want the incoming-call screen to look:

| If you want | Use |
|---|---|
| Ready-made call screens, and your app uses Compose | `ai.daakia:callkit-ui-compose` |
| Ready-made call screens, and your app uses XML Views | `ai.daakia:callkit-ui-views` |
| To build your own call screen | `ai.daakia:callkit-core` |

Both UI artifacts pull in `callkit-core`, so you never add it alongside them. This guide uses
`callkit-ui-compose`; everything except Step 5 is identical for the other two.

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("ai.daakia:callkit-ui-compose:0.1.0")
}
```

You do **not** need to add `firebase-messaging` — the SDK exposes it as an `api` dependency,
so it arrives transitively.

> **Pre-release note.** The SDK is not on Maven Central yet. Until it is, build it locally
> from the SDK repo with `./gradlew publishToMavenLocal`, add `mavenLocal()` to the
> `dependencyResolutionManagement { repositories { ... } }` block in your `settings.gradle.kts`,
> and depend on `ai.daakia:callkit-ui-compose:0.1.0-SNAPSHOT`.

## Step 2 — Add Firebase to your app

Put `google-services.json` in your **app module** directory (`app/google-services.json`, next
to `build.gradle.kts` — not in the project root).

Then apply the Google Services plugin:

```kotlin
// settings.gradle.kts — only if you don't already resolve plugins from Google's repo
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts (project root)
plugins {
    id("com.google.gms.google-services") version "4.5.0" apply false
}
```

```kotlin
// app/build.gradle.kts
plugins {
    id("com.google.gms.google-services")
}
```

Sync. If Gradle complains that `google-services.json` is missing, it is in the wrong directory.

## Step 3 — Initialize the SDK

Create an `Application` class. `initialize` must run before anything else touches the SDK,
which is why it goes here rather than in an Activity.

```kotlin
// app/src/main/java/com/example/myapp/MyApp.kt
package com.example.myapp

import ai.daakia.callkit.DaakiaCallKit
import ai.daakia.callkit.DaakiaCallKitConfig
import ai.daakia.callkit.ui.IncomingCallStyle
import ai.daakia.callkit.ui.compose.DaakiaIncomingCallUi
import android.app.Application

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        DaakiaCallKit.initialize(
            context = this,
            config = DaakiaCallKitConfig(
                baseUrl = "https://your-daakia-backend.example.com",
                secret = "your-customer-secret",
            ),
        )

        // Step 5 explains the styles. Registers the SDK's full-screen call screen.
        DaakiaIncomingCallUi.install(style = IncomingCallStyle.CLASSIC)
    }
}
```

Register it in your manifest with `android:name`:

```xml
<!-- app/src/main/AndroidManifest.xml -->
<application
    android:name=".MyApp"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:theme="@style/Theme.MyApp">

    <activity
        android:name=".MainActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

### What you do *not* need to add

Coming from the Flutter plugin — or from most Android SDKs — you would expect a list of
manifest entries here. There isn't one. The SDK's own manifest declares the permissions
(`INTERNET`, `POST_NOTIFICATIONS`, `VIBRATE`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`), the ringing foreground service, and the full-screen
call Activity. Manifest merging pulls all of it into your app.

See [permissions.md](permissions.md) for what each permission is for.

## Step 4 — Receive call pushes

The SDK needs FCM messages forwarded to it. There are two ways; pick the one that matches
your app.

### If your app has no `FirebaseMessagingService` of its own

Declare the SDK's ready-made one. No code required.

```xml
<!-- app/src/main/AndroidManifest.xml, inside <application> -->
<service
    android:name="ai.daakia.callkit.push.DaakiaMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

### If your app already has a `FirebaseMessagingService`

Do **not** declare the SDK's service — Android delivers `MESSAGING_EVENT` to only one service,
and yours would stop receiving messages. Forward from your own instead:

```kotlin
// Your existing service
override fun onMessageReceived(message: RemoteMessage) {
    if (DaakiaCallKit.handleRemoteMessage(message)) return  // was a Daakia call push
    // your own push handling continues here
}
```

`handleRemoteMessage` returns `true` only for Daakia call pushes and leaves everything else
untouched, so it is safe to call on every message.

## Step 5 — Choose a call screen style

`install()` in Step 3 registers the SDK's call screen. Three presets ship, all themeable:

```kotlin
DaakiaIncomingCallUi.install(style = IncomingCallStyle.CLASSIC)  // full-screen gradient
DaakiaIncomingCallUi.install(style = IncomingCallStyle.AURORA)   // drifting glow blobs
DaakiaIncomingCallUi.install(style = IncomingCallStyle.PULSE)    // animated radiating rings
```

To match your brand, pass a theme. Every field is optional:

```kotlin
DaakiaIncomingCallUi.install(
    style = IncomingCallStyle.AURORA,
    theme = DaakiaCallTheme(
        backgroundStartColor = 0xFF1A1033.toInt(),
        backgroundEndColor = 0xFF3B1E6E.toInt(),
        acceptButtonColor = 0xFF00C853.toInt(),
        acceptLabel = "Join",
        declineLabel = "Dismiss",
    ),
)
```

If you use `callkit-ui-views`, the import is `ai.daakia.callkit.ui.views.DaakiaIncomingCallUi`
and everything else is identical. If you use `callkit-core` alone, skip `install()` and build your
own call Activity against `IncomingCallUi` — [call-screen-ui.md](call-screen-ui.md) walks through
it with a complete working example.

## Step 6 — Ask for the notification permission

On Android 13+ the user must grant `POST_NOTIFICATIONS` or **calls cannot ring at all**. The
SDK declares the permission but cannot request it for you — only your Activity can.

```kotlin
// app/src/main/java/com/example/myapp/MainActivity.kt
package com.example.myapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // Calls will not ring. Tell the user why and offer to retry.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
```

## Step 7 — Register the device

Daakia routes calls to a username. Register this device's FCM token against the username your
app signs in as — after login, and again whenever the token rotates.

`registerDevice` is a `suspend` function, so call it from a coroutine:

```kotlin
import ai.daakia.callkit.DaakiaCallKit
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// inside MainActivity, e.g. after sign-in
lifecycleScope.launch {
    val token = FirebaseMessaging.getInstance().token.await()
    DaakiaCallKit.registerDevice(username = "alice", fcmToken = token)
}
```

`await()` needs `org.jetbrains.kotlinx:kotlinx-coroutines-play-services`. Without it, use
`FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> ... }` and launch the
coroutine inside the listener.

Token rotation is handled by overriding `onRegistered` (or the deprecated `onNewToken`) in a
subclass of `DaakiaMessagingService`, calling `registerDevice` again with the new token.

## Step 8 — Make it ring

This is the moment that tells you the whole chain works. You do not need a second device —
register a username and place a call to yourself.

```kotlin
import ai.daakia.callkit.model.IncomingCallData

lifecycleScope.launch {
    DaakiaCallKit.startCallByUsername(
        username = "alice",                   // the username you registered in Step 7
        call = IncomingCallData(
            callId = "test-${System.currentTimeMillis()}",
            title = "Test Caller",
        ),
    )
}
```

> **Lock your screen before the call arrives.** Android only launches a full-screen intent
> when the device is locked or the screen is off. If your app is open in the foreground you
> will get a heads-up notification instead of the full call screen — that is correct Android
> behavior, not a bug. To see the real thing, trigger the call and immediately press the power
> button, or wire the trigger to a button and lock the phone within a second or two.

You should get a ringing notification with Accept and Decline actions, a ringtone, and
vibration — and the full call screen over your lock screen.

If nothing happens, jump to [Troubleshooting](#troubleshooting) below.

## Step 9 — Handle the call events

This is the step people get wrong, so follow it exactly — and read
**[call-events.md](call-events.md)** for the full reasoning behind it. The one rule that keeps
you out of trouble:

> **`ACCEPTED` is delivered by `consumeLaunchEvent`. Every other event is delivered by the
> `callEvents` flow.** Never handle `ACCEPTED` in both, or you'll join the call twice.

Why the split exists: accepting a call must reach your app **even if its process is dead**, so
it arrives as an Activity launch intent, not through an in-memory stream. Decline, timeout, and
end only matter while your app is alive, so they flow through `callEvents`.

Here is the complete, correct wiring:

```kotlin
import ai.daakia.callkit.model.CallEvent
import ai.daakia.callkit.model.CallEventType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 1. An accept that launched us — works even from a killed process.
    handleLaunch(intent)

    // 2. Live events while we're running: INCOMING / DECLINED / TIMED_OUT / ENDED.
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            val backlog = DaakiaCallKit.callEvents.replayCache.size
            DaakiaCallKit.callEvents
                .drop(backlog)                                   // skip replayed history (see below)
                .filter { it.type != CallEventType.ACCEPTED }    // accept comes via the intent
                .collect(::handleCallEvent)
        }
    }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleLaunch(intent)
}

private fun handleLaunch(intent: Intent) {
    DaakiaCallKit.consumeLaunchEvent(intent)?.let(::handleCallEvent)
}

private fun handleCallEvent(event: CallEvent) {
    when (event.type) {
        CallEventType.ACCEPTED  -> joinYourCall(event.call.callId)
        CallEventType.DECLINED  -> { /* user declined */ }
        CallEventType.TIMED_OUT -> { /* nobody answered */ }
        CallEventType.ENDED     -> { /* cancelled by endCall — see Step 11 */ }
        CallEventType.INCOMING  -> Unit
    }
}
```

Two things in that code you must not skip:

- **`consumeLaunchEvent` in `onNewIntent` too**, not just `onCreate`. If your Activity is
  already open when the accept arrives, it comes through `onNewIntent`. It is one-shot — it
  strips the extras, so a screen rotation won't re-deliver the call.
- **`drop(replayCache.size)`.** `callEvents` replays its last 16 events to every new collector,
  so a freshly opened Activity would otherwise get flooded with events from calls that already
  finished. Dropping the buffered backlog means you only react to events from *now* on. (This
  trade-off and a production-grade dedupe alternative are in
  [call-events.md](call-events.md#4-the-replay-buffer-why-you-see-old-events).)

## Step 9b — Surface the app over the lock screen on accept

When the user accepts **while the phone is locked**, the SDK launches your launcher Activity —
but a normal Activity opens *behind* the keyguard, so it looks like nothing happened. Opt in to
showing over the lock screen, but do it **only for an accept**:

```kotlin
override fun onStop() {
    super.onStop()
    applyLockScreenVisibility(false)   // don't linger over the lock on the next reveal
}

private fun handleLaunch(intent: Intent) {
    val event = DaakiaCallKit.consumeLaunchEvent(intent)
    applyLockScreenVisibility(overLockScreen = event != null)   // true only for an accept
    event?.let(::handleCallEvent)
}

private fun applyLockScreenVisibility(overLockScreen: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
    setShowWhenLocked(overLockScreen)
    setTurnScreenOn(overLockScreen)
    if (overLockScreen) {
        (getSystemService(KEYGUARD_SERVICE) as KeyguardManager)
            .requestDismissKeyguard(this, null)
    }
}
```

**Do not set `showWhenLocked` unconditionally.** If you do, your Activity pops over the lock
screen whenever it becomes visible — including when a *declined or timed-out* call finishes and
uncovers it, which looks like the app opening itself on reject. Gating it on `event != null`
and clearing it in `onStop` fixes that. See
[call-events.md §6](call-events.md#6-making-the-app-show-over-the-lock-screen-on-accept).

## Step 10 — Report call outcomes to the backend

If your app runs the actual call (joining a meeting, hanging up), tell Daakia:

```kotlin
import ai.daakia.callkit.model.CallEventAction

// sendCallEvent is suspend, like the other backend calls
lifecycleScope.launch {
    DaakiaCallKit.sendCallEvent(meetingUid = callId, action = CallEventAction.JOIN)
}
```

Accept, decline, and timeout can also be reported **while your app is not running at all** —
useful when the caller needs to know the call was rejected even though the callee's app was
killed. Enable it once, next to `initialize`:

```kotlin
DaakiaCallKit.configureCallEventFallback(
    actions = setOf(
        CallEventAction.ACCEPT,
        CallEventAction.REJECT,
        CallEventAction.TIMEOUT,
    ),
)
```

Each `callId` + action pair is delivered at most once, so the in-app and killed-state paths
never double-report.

## Step 11 — Stop a call the caller cancelled

If the caller hangs up before the callee answers, nothing stops the ringing on its own — the
device rings for the full timeout and then reports a `call-timeout` that never really happened.
When your app learns the call was cancelled, end it:

```kotlin
DaakiaCallKit.endCall(callId)
```

This silences the ringtone and vibration, removes the notification, closes the call screen if
it is showing, and emits `CallEventType.ENDED`. It is not a `suspend` function.

Pass the `callId`. Without it, `endCall()` ends whatever is ringing right now — which may be a
newer call that started while the cancellation was in flight.

How your app learns about the cancellation is up to your backend. A common shape is a second
push, handled before you forward to the SDK:

```kotlin
override fun onMessageReceived(message: RemoteMessage) {
    if (message.data["type"] == "call_cancelled") {
        DaakiaCallKit.endCall(message.data["callId"])
        return
    }
    if (DaakiaCallKit.handleRemoteMessage(message)) return
    // your own push handling
}
```

`endCall` is purely local — it tells the SDK to stop ringing and reports nothing to the
backend. If the backend also needs to know, use `sendCallEvent` with `CallEventAction.END`.

## Troubleshooting

The quick table below covers what usually goes wrong during first setup. For layer-by-layer
diagnosis — including the `adb` commands to find which layer is failing, and OEM battery managers —
see [troubleshooting.md](troubleshooting.md).

| Symptom | Most likely cause |
|---|---|
| Nothing rings at all | Notification permission not granted (Step 6). Check your app's notification settings. |
| Heads-up notification, but no full call screen | Screen was unlocked. Lock it and retry — see the note in Step 8. |
| Still no full call screen on a locked device | Android 14+ revoked the full-screen intent grant. Check `DaakiaCallKit.canUseFullScreenIntent()` and send the user to `DaakiaCallKit.openFullScreenIntentSettings()`. |
| Works, then stops after the phone idles | OEM battery manager (common on Xiaomi, Oppo, Vivo, Samsung). Exempt your app from battery optimization in system settings. |
| `startCallByUsername` throws `DaakiaBackendException` | Wrong `baseUrl`/`secret`, or the username has no registered device — run Step 7 first. |
| `IllegalStateException: DaakiaCallKit.initialize(...) must be called first` | Your `Application` class is missing `android:name` in the manifest (Step 3). |
| Non-Daakia pushes stopped arriving | You declared `DaakiaMessagingService` while already having your own service. Use the forwarding approach in Step 4 instead. |
| Phone keeps ringing after the caller hung up | You are not calling `DaakiaCallKit.endCall(callId)` on cancellation — see Step 11. |
| A burst of old events appears when the app opens | The `callEvents` replay buffer being replayed. Add `drop(replayCache.size)` — see Step 9 and [call-events.md](call-events.md#4-the-replay-buffer-why-you-see-old-events). |
| Every accept joins the call twice | You're handling `ACCEPTED` in both `consumeLaunchEvent` and the flow. Filter it out of the flow — see Step 9. |
| The app opens by itself when a call is declined or times out | You set `showWhenLocked` unconditionally. Gate it on an accept — see Step 9b. |

## Where to go next

- **[call-events.md](call-events.md)** — the complete guide to `ACCEPTED` vs the event flow, the
  replay buffer, deduplication, and lock-screen handling. Read this if anything in Step 9 felt unclear.
- **[choosing-apis.md](choosing-apis.md)** — which API to use when, for the pairs that look redundant.
- [call-screen-ui.md](call-screen-ui.md) — the preset styles, theming, and building your own call screen
- [troubleshooting.md](troubleshooting.md) — full diagnosis guide when calls don't ring
- [permissions.md](permissions.md) — every permission the SDK declares and why
- [API reference](https://javadoc.io/doc/ai.daakia/callkit-core/latest/) — generated from source
