# Choosing the right API

Several `DaakiaCallKit` APIs look like they overlap. They don't — each solves a distinct case.
This page is the decision guide for the pairs people most often confuse.

---

## Receiving pushes: `DaakiaMessagingService` vs `handleRemoteMessage`

Both get Daakia call pushes into the SDK. Pick based on whether your app already has its own
Firebase messaging service.

| Your situation | Use | Why |
|---|---|---|
| No `FirebaseMessagingService` of your own | Declare **`DaakiaMessagingService`** in the manifest | Zero code; the SDK owns the service |
| You already have a `FirebaseMessagingService` | Call **`handleRemoteMessage(message)`** from it | Android delivers `MESSAGING_EVENT` to only **one** service — declaring the SDK's would silence yours |

**Never do both.** If you declare `DaakiaMessagingService` *and* have your own service, one of
them stops receiving messages. When in doubt, forward with `handleRemoteMessage`:

```kotlin
override fun onMessageReceived(message: RemoteMessage) {
    if (DaakiaCallKit.handleRemoteMessage(message)) return   // was a Daakia call push
    // your own push handling
}
```

`handleRemoteMessage` returns `true` only for Daakia call pushes, so it's safe on every message.

---

## Finding out about a call: `callEvents` vs `consumeLaunchEvent`

This pair is covered in depth in [call-events.md](call-events.md). The short version:

| | `callEvents` (flow) | `consumeLaunchEvent(intent)` |
|---|---|---|
| Delivers | Every type, **while your process is alive** | The **accept** that launched your app (works from a dead process) |
| Backed by | In-memory replay buffer (last 16) | The Activity's launch intent (one-shot) |
| Use for | `INCOMING`, `DECLINED`, `TIMED_OUT`, `ENDED` | `ACCEPTED` |

They are not alternatives — you need **both**, each for its own event types. Using both for
`ACCEPTED` double-fires; see [call-events.md §3](call-events.md#3-why-you-must-not-use-both-channels-for-accepted).

---

## Reporting to the backend: `sendCallEvent` vs `configureCallEventFallback`

Both report call outcomes to the Daakia webhook. The difference is **who drives the call** and
**whether your app is running**.

| | `sendCallEvent(...)` | `configureCallEventFallback(...)` |
|---|---|---|
| When it fires | You call it explicitly, in code | Automatically, on accept/decline/timeout from the SDK's own notification |
| Requires a running process | Yes — it's a `suspend` call | **No** — runs via WorkManager even if the app is killed |
| Reports which actions | Whatever you pass (e.g. `JOIN`, `END`) | The subset you enable from `{ACCEPT, REJECT, TIMEOUT}` |
| Typical use | Your app runs the call — report `JOIN` when the user is in, `END` when they hang up | Guarantee the backend hears accept/reject/timeout even when nobody's home |

Use them together: `configureCallEventFallback` covers the SDK-driven ringing outcomes;
`sendCallEvent` covers the lifecycle your own app code drives afterward. They dedupe against a
shared cache (each `callId` + action is sent at most once), so they never double-report.

```kotlin
// Once, next to initialize(): guarantee the ringing outcomes reach the backend.
DaakiaCallKit.configureCallEventFallback(
    actions = setOf(CallEventAction.ACCEPT, CallEventAction.REJECT, CallEventAction.TIMEOUT),
)

// Later, when your app drives the call itself:
lifecycleScope.launch {
    DaakiaCallKit.sendCallEvent(meetingUid = callId, action = CallEventAction.JOIN)
}
```

---

## Ending a call: `endCall` vs `sendCallEvent(END)`

Both involve a call ending, but they act on completely different things.

| | `endCall(callId)` | `sendCallEvent(callId, END)` |
|---|---|---|
| Acts on | The **local device** — stops ringtone, vibration, notification, call screen | The **backend** — reports an `END` webhook |
| Talks to the backend? | No, purely local | Yes, that's its whole job |
| Suspend? | No | Yes |
| Use when | The caller cancelled and you need to stop the ringing on this device | You need to tell the backend the call ended |

They're complementary, not interchangeable. When a caller cancels a ringing call you usually
call **`endCall(callId)`** to silence this device (otherwise it rings until timeout and reports
a `call-timeout` that never really happened). If the backend also needs to know, additionally
call `sendCallEvent(callId, CallEventAction.END)`.

> Always pass the `callId` to `endCall`. `endCall()` with no argument ends whatever is ringing
> *right now* — which may be a newer call that started while the cancellation was in flight.

---

## Placing a call: `startCallByUsername` vs `startCallByToken`

| | `startCallByUsername` | `startCallByToken` |
|---|---|---|
| You give it | A username | A device push token + platform |
| Backend does | Looks up the username's registered devices | Pushes straight to that token |
| Use when | The normal case — you know who to call | You already hold the target's token (e.g. from `getRegisteredDevice`) and want to skip the lookup |

Both take the same `IncomingCallData` and return the same `PushResult`. Start with
`startCallByUsername` unless you have a specific reason to target a raw token.
