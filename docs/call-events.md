# Handling call events

This is the part of the SDK that trips people up most, so read it once end to end. By the
end you will know exactly **which API to use, and when** — and why the two that look
redundant are not.

If you only remember one thing:

> **Only an accepted call ever opens your app.** Decline, timeout, and remote-cancel do
> **not** launch anything. They are notifications *to* your already-running app, nothing more.

---

## 1. The mental model

When a call rings, the SDK owns the ringing phase: the foreground service, the CallStyle
notification, the ringtone, the full-screen call screen. Your app does nothing during this
phase. You only get involved when something *happens* to the call:

| The user / caller… | Does the SDK open your app? | How you find out |
|---|---|---|
| Call starts ringing (`INCOMING`) | No | `callEvents` flow (and the SDK's own call screen shows) |
| **Accepts** (`ACCEPTED`) | **Yes — launches your launcher Activity** | `consumeLaunchEvent(intent)` |
| Declines (`DECLINED`) | No | `callEvents` flow |
| Nobody answers (`TIMED_OUT`) | No | `callEvents` flow |
| Caller cancels, you call `endCall` (`ENDED`) | No | `callEvents` flow |

That single "Yes" row is the whole reason there are two APIs instead of one. Accept has to
reach your app **even when your process is dead**, so it can't be delivered through an
in-memory stream — it arrives as an Activity launch intent. Everything else only matters
while your app is alive, so it flows through a normal event stream.

---

## 2. The two delivery channels

### `DaakiaCallKit.callEvents` — the live event stream

A `SharedFlow<CallEvent>` that emits **every** event type while your process is alive.
Collect it from an Activity or a lifecycle-aware scope.

```kotlin
DaakiaCallKit.callEvents.collect { event ->
    // event.type, event.call (the payload), event.reason
}
```

Use it for `INCOMING`, `DECLINED`, `TIMED_OUT`, `ENDED`.

### `DaakiaCallKit.consumeLaunchEvent(intent)` — the accept handoff

Reads the call out of the **intent that launched your Activity**. Returns non-null only when
the SDK started your app because of a call. Call it from `onCreate` and `onNewIntent`.

```kotlin
DaakiaCallKit.consumeLaunchEvent(intent)?.let { event ->
    // event.type is ACCEPTED (see note below), event.call is the accepted call
}
```

Use it for `ACCEPTED`.

> **What types can `consumeLaunchEvent` return?**
> - With a registered call Activity — either a UI module (`callkit-ui-compose` /
>   `callkit-ui-views`) or your own via `IncomingCallUi.registerActivity`: only `ACCEPTED`.
>   The ringing full-screen intent opens that registered Activity, so the only reason it
>   launches *your launcher* is an accept.
> - In notification-only mode (`callkit-core` with no registered Activity): it can also return
>   `INCOMING`, because with no call screen to open, the ringing full-screen intent falls back
>   to opening your launcher directly.

---

## 3. Why you must not use both channels for `ACCEPTED`

`ACCEPTED` arrives on **both** channels at once — the launch intent *and* the live flow. If
you handle it in both places you will join the call twice. This is the single most common
mistake, and the old examples caused it.

The rule that removes the ambiguity:

> **`ACCEPTED` → `consumeLaunchEvent` only. Filter it out of the flow.**

Accept is guaranteed to launch your app with the intent (both the notification-button path and
the call-screen path call into the same host-launch code), so the intent is always the
reliable source. The flow's copy is redundant — drop it.

---

## 4. The replay buffer (why you see "old" events)

`callEvents` is backed by a **replay buffer of the last 16 events**. This is deliberate: the
ringing service emits `INCOMING` *before* any Activity exists to hear it, so late subscribers
need the recent history replayed to them.

The side effect: **every time a new collector subscribes, the whole buffer is replayed at
once.** If your app process stays alive across several test calls, a freshly opened Activity
will log a burst of every event from every prior call — all with the same timestamp. That is
not stale logcat output; it is the buffer being replayed into your new collector.

Two consequences:

- **Clearing logcat does not clear the buffer.** It lives in your process memory. Only a real
  process death (force-stop) clears it.
- **You must skip the replayed backlog** so you don't react to calls that already finished.

The fix is to drop whatever is already buffered at the moment you subscribe, and only react to
events that arrive afterward:

```kotlin
val backlog = DaakiaCallKit.callEvents.replayCache.size
DaakiaCallKit.callEvents
    .drop(backlog)
    .collect(::handleCallEvent)
```

> **Trade-off:** because `repeatOnLifecycle` re-subscribes each time you return to `STARTED`,
> `drop(backlog)` also skips events that arrived while your app was backgrounded. For an
> accept that's fine (accept foregrounds you via the intent anyway). If you must reliably
> catch a decline/timeout that happened while backgrounded, dedupe by call identity instead
> of dropping (see [§7](#7-production-deduplication)).

---

## 5. The recommended pattern (copy this)

This is the complete, correct wiring: `consumeLaunchEvent` owns `ACCEPTED`; the flow owns
everything else, with the backlog dropped and `ACCEPTED` filtered out so nothing double-fires.

```kotlin
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Accept that launched us (works even from a dead process).
        handleLaunch(intent)

        // 2. Live events while we're running — INCOMING / DECLINED / ENDED / TIMED_OUT.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val backlog = DaakiaCallKit.callEvents.replayCache.size
                DaakiaCallKit.callEvents
                    .drop(backlog)                                   // skip replayed history
                    .filter { it.type != CallEventType.ACCEPTED }    // accept comes via the intent
                    .collect(::handleCallEvent)
            }
        }

        setContent { /* your UI */ }
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
            CallEventType.ACCEPTED  -> joinCall(event.call.callId)
            CallEventType.INCOMING  -> Unit
            CallEventType.DECLINED  -> Unit
            CallEventType.TIMED_OUT -> Unit
            CallEventType.ENDED     -> Unit
        }
    }
}
```

Result: `ACCEPTED` fires exactly once (from the intent); `INCOMING` / `DECLINED` / `ENDED` /
`TIMED_OUT` fire once each (from the flow).

---

## 6. Making the app show over the lock screen on accept

When the user accepts a call **while the phone is locked**, the SDK launches your launcher
Activity — but a normal launcher Activity opens *behind* the keyguard. The user taps Accept
and… nothing visible happens until they unlock. To surface your app over the lock screen you
must opt in at runtime.

**Do it conditionally.** This is the trap that bites everyone: if you set `showWhenLocked`
unconditionally in `onCreate`, your Activity will pop over the lock screen *any time it becomes
visible* — including when a **declined or timed-out** call finishes and reveals your Activity
underneath the SDK's call screen. It looks like the app "opens itself" on reject/timeout.

Set the flag **only when an accept launched you**, and clear it when the Activity stops:

```kotlin
override fun onStop() {
    super.onStop()
    applyLockScreenVisibility(false)   // don't linger over the lock for the next reveal
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

> **Better still, in a real app:** don't make your *launcher* Activity the lock-screen surface
> at all. Route the accepted call to a dedicated call/meeting Activity that owns
> `showWhenLocked`. Keeping the flag off your launcher avoids the whole class of reveal bugs.

---

## 7. Production deduplication

`drop(backlog)` is enough for a demo, but a hardened app should dedupe by **call identity**,
because a real `callId` (the meeting UID) is unique per call. Track the `callId` + `type`
pairs you've handled and ignore repeats — this survives process death, backgrounding, and
replay without dropping anything you actually need.

```kotlin
private val handled = mutableSetOf<Pair<String, CallEventType>>()

private fun handleCallEvent(event: CallEvent) {
    if (!handled.add(event.call.callId to event.type)) return   // already processed
    // …dispatch…
}
```

(In the sample app, the test "Start Call" button reuses a fixed `callId`, so per-`callId`
dedupe would collapse every test into one — that's a test artifact, not a real-world concern.)

---

## 8. What none of this catches: the killed-state case

The `callEvents` flow only delivers while your **process is alive**. If your app is fully
killed and the user *declines* or lets the call *time out* from the notification, there is no
process to receive the event — and unlike accept, decline/timeout don't start one.

If your **backend** needs to know about those outcomes regardless, enable the killed-state
webhook fallback. The SDK reports the action over WorkManager even with no running process:

```kotlin
DaakiaCallKit.configureCallEventFallback(
    actions = setOf(CallEventAction.ACCEPT, CallEventAction.REJECT, CallEventAction.TIMEOUT),
)
```

See [choosing-apis.md](choosing-apis.md) for how this differs from `sendCallEvent`, and
[getting-started.md](getting-started.md) for where it fits in the overall flow.

---

## Summary

- **`ACCEPTED`** → `consumeLaunchEvent(intent)` in `onCreate`/`onNewIntent`. Nothing else.
- **Everything else** → the `callEvents` flow, with the replay backlog dropped and `ACCEPTED`
  filtered out.
- **Lock screen** → set `showWhenLocked` only when an accept launched you; clear it on stop.
- **Backend needs killed-state outcomes** → `configureCallEventFallback`.
