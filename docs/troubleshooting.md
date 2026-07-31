# Troubleshooting

Incoming calls fail in layers: the push has to arrive, the notification has to post, the
full-screen screen has to be allowed to open, and the device has to not be asleep in a way the OEM
decided is permanent. Work down the layers in order — guessing wastes hours here.

> **The SDK does not log.** `callkit-core` writes nothing to logcat by design, so an empty log is
> not a symptom. Everything below diagnoses from the outside.

---

## Start here: find the layer

Run these in order. The first one that fails is your problem.

### 1. Is the push arriving?

```bash
adb logcat -s FirebaseMessaging
```

Place a call and watch. If nothing appears, the message never reached the device — the problem is
your backend, the FCM token, or the network, and nothing in this SDK is involved yet.

Confirm the device is actually registered:

```kotlin
lifecycleScope.launch {
    Log.d("callkit", DaakiaCallKit.getRegisteredDevice("alice").toString())
}
```

If this throws or returns a token that isn't the device's current one, re-run
`registerDevice`. Tokens rotate — see "Calls stop arriving after a while" below.

### 2. Is the notification posting?

```bash
adb shell dumpsys notification --noredact | grep -A 5 daakia_calls_v1
```

The SDK's channel is `daakia_calls_v1` ("Daakia Calls"). If the channel is missing, the SDK was
never initialized. If the channel exists but is `importance=0`, the user (or a previous install)
turned it off — nothing will ring until it's re-enabled in system settings.

### 3. Is the full-screen intent allowed?

```kotlin
Log.d("callkit", "fullScreenIntent=${DaakiaCallKit.canUseFullScreenIntent()}")
```

`false` on Android 14+ means the grant was never given or was revoked. Send the user to settings
with `DaakiaCallKit.openFullScreenIntentSettings()`.

### 4. Is the OEM killing you?

If 1–3 all pass but calls only work while the app is open, skip to
[OEM battery management](#oem-battery-management).

---

## Nothing rings at all

| Check | How |
|---|---|
| Notification permission granted? | Android 13+ requires `POST_NOTIFICATIONS`. The SDK declares it but **cannot request it** — only your Activity can. See getting-started Step 6. |
| SDK initialized? | A missing `android:name=".MyApp"` on `<application>` means your `Application` class never runs. You'd also see `IllegalStateException: DaakiaCallKit.initialize(...) must be called first` on the first API call. |
| Channel enabled? | Step 2 above. |
| Push arriving? | Step 1 above. |
| Two messaging services? | See below. |

## Non-Daakia pushes stopped arriving

You declared `DaakiaMessagingService` in your manifest while your app already has its own
`FirebaseMessagingService`. Android delivers `MESSAGING_EVENT` to exactly **one** service, so one of
them goes silent.

Remove the SDK's service from your manifest and forward from your own instead:

```kotlin
override fun onMessageReceived(message: RemoteMessage) {
    if (DaakiaCallKit.handleRemoteMessage(message)) return   // consumed: Daakia call push
    // your own handling
}
```

`handleRemoteMessage` returns `true` only for Daakia call pushes, so it is safe to call on every
message. See [choosing-apis.md](choosing-apis.md).

## Heads-up notification appears, but no full-screen call screen

In order of likelihood:

1. **The screen was unlocked.** Android only shows a full-screen intent when the device is locked or
   the screen is off. On an unlocked device a heads-up notification is the *correct* behaviour. Lock
   the phone and retry before assuming anything is broken.
2. **No UI module installed.** Notification-only is the fallback when no Activity is registered.
   Confirm you call `DaakiaIncomingCallUi.install(...)` — or `IncomingCallUi.registerActivity(...)`
   if you built your own screen. See [call-screen-ui.md](call-screen-ui.md).
3. **Android 14+ revoked the grant.** Step 3 above.
4. **Your own Activity isn't in the manifest.** On the bring-your-own-UI path nothing is contributed
   for you — see the manifest snippet in [call-screen-ui.md §3](call-screen-ui.md#3-option-b--your-own-incoming-call-activity).

## The call screen appears but never goes away

The caller hung up, the call timed out, or the user accepted from the notification — and your custom
Activity didn't hear about it. You're missing the `onCallClosed` subscription:

```kotlin
override fun onStart() {
    super.onStart()
    closeSubscription = IncomingCallUi.onCallClosed(this) { closingCallId ->
        if (closingCallId == null || closingCallId == shownCallId) finish()
    }
}

override fun onStop() {
    closeSubscription?.close()
    closeSubscription = null
    super.onStop()
}
```

The preset UI modules do this for you; only Option B is affected.

## The phone keeps ringing after the caller hung up

You aren't calling `DaakiaCallKit.endCall(callId)` when your signalling tells you the caller
cancelled. Without it the device rings to the full timeout and then reports a `call-timeout` that
never really happened.

Always pass the `callId`. Bare `endCall()` ends whatever is ringing *right now*, which may be a
newer call that started while the cancellation was in flight.

## An old call rings when the device comes back online

Expected, and configurable. FCM queues a push for an offline device and delivers it on
reconnect, so a call placed an hour ago arrives now.

The SDK will not ring a push older than `staleCallWindow` (60 seconds by default) — it posts a
"Missed call" notification instead and emits a `STALE` event. If you are seeing an old call
*ring*, one of these is true:

- `staleCallBehavior` is set to `RING`.
- `staleCallWindow` is longer than the delay you are testing.
- The push carries no usable timestamp, so the guard failed open and let it through. The SDK
  reads FCM's `sentTime` first and the payload's `callTimestamp` second; if the sender omits
  both, nothing can tell how old the call is.

Conversely, if a **fresh** call is being suppressed, check the device clock. The guard compares
the call's timestamp against local time, so a device set hours ahead makes every call look
stale. Ages are computed in UTC, so time zones themselves are not the problem.

The device-side guard is a backstop. Fix it at the source too by setting a short FCM TTL
(`android.ttl`) on call-invite pushes — see
[call-events.md §9](call-events.md#9-calls-that-arrive-too-late-to-ring).

## Every accept joins the call twice

You're handling `ACCEPTED` on both `consumeLaunchEvent` and the `callEvents` flow. Handle it on
`consumeLaunchEvent` only, and filter it out of the flow. This is the most common bug in the SDK —
[call-events.md §3](call-events.md#3-why-you-must-not-use-both-channels-for-accepted) explains why
both channels legitimately carry it.

## A burst of old events arrives when the app opens

That's the `callEvents` replay buffer doing its job — it exists so a subscriber that starts late
still sees the current call. Drop what's already buffered when you only want new events:

```kotlin
DaakiaCallKit.callEvents
    .drop(DaakiaCallKit.callEvents.replayCache.size)
    .collect { /* ... */ }
```

See [call-events.md §4](call-events.md#4-the-replay-buffer-why-you-see-old-events).

## The app opens by itself when a call is declined or times out

You set `showWhenLocked` / `turnScreenOn` unconditionally on your launcher Activity, so *any* event
surfaces the app over the lock screen. Gate it on an accept — getting-started Step 9b has the
conditional version.

## `DaakiaBackendException` from `startCallByUsername` / `registerDevice`

| Cause | Fix |
|---|---|
| Wrong `baseUrl` | It's the host only, e.g. `https://api.example.com`. The SDK appends `/v2.0/saas/...` itself — don't include a path. |
| Wrong or missing `secret` | Issued by Daakia per customer. Sent as the `secret` header on every request. |
| Username has no registered device | Call `registerDevice` on the *callee's* device first. Calling an unregistered user is a backend-level failure, not a network one. |
| Backend returned `success != 1` | The exception carries the backend's message — read it before assuming it's transport. |

## Calls stop arriving after a while

FCM tokens rotate — on reinstall, on app data clear, on restore to a new device, and occasionally on
their own. A rotated token that was never re-registered means the backend pushes into the void.

Re-register from `onNewToken`:

```kotlin
override fun onNewToken(token: String) {
    super.onNewToken(token)
    scope.launch { DaakiaCallKit.registerDevice(username = currentUser, fcmToken = token) }
}
```

The sample app's `SampleFirebaseMessagingService` shows this.

## OEM battery management

This is where full-screen intents and killed-state delivery actually break in the field, and no
amount of correct code fixes it from inside the app. Xiaomi/MIUI, Oppo/ColorOS, Vivo/FuntouchOS,
and Samsung are the repeat offenders; Xiaomi is the worst.

Symptoms: everything works while the app is open or recently used, then stops after the phone idles
for a while, or after the app is swiped away from recents.

What actually helps, in rough order of impact:

1. **Exempt the app from battery optimization.** Settings → Apps → your app → Battery → Unrestricted.
2. **Xiaomi specifically:** enable *Autostart* for your app, and lock the app in recents (pull down
   on the card). Without Autostart, a killed app receives nothing.
3. **Samsung:** remove the app from *Sleeping apps* / *Deep sleeping apps* under Battery care.
4. **Oppo/Vivo:** allow background running and startup in the app's battery settings.

Ship this as user-facing guidance in your app — a first-run screen that walks the user through it is
the only reliable mitigation. [dontkillmyapp.com](https://dontkillmyapp.com) has per-OEM
instructions worth linking.

Test on at least one Samsung and one Xiaomi device before every release. A Pixel proving the flow
works proves very little.

## Fallback webhooks never reach the backend

`configureCallEventFallback` reports accept/decline/timeout via WorkManager, which honours a network
constraint — a device with no connectivity at the moment of the call reports when it reconnects, not
immediately. That's by design, not a failure.

If they never arrive at all, check:

- the actions you enabled actually include the one you're expecting
  (`configureCallEventFallback(actions = setOf(...))`)
- the same `callId` + action wasn't already sent — they dedupe against a shared cache. During
  testing, `DaakiaCallKit.clearSentCallEventCache()` resets it.
- the OEM isn't killing WorkManager outright (see above)

---

## Still stuck

Reproduce it in the
[sample app](https://github.com/Daakia-Org/daakia-callkit-android-support/tree/main/sample) first.
It exercises every public API with an on-screen
activity log, so it separates "the SDK is misbehaving" from "my integration is wrong" in about five
minutes. If it reproduces there, that's a bug — file it with the sample's log output, the device
make/model, and the Android version.
