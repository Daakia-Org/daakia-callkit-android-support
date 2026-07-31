# Daakia CallKit — sample app

A complete, runnable integration of the `ai.daakia:callkit-*` SDK. Every section of its one screen
maps to one part of the public API, and every SDK result and call event is appended to an on-screen
log — so you can watch exactly what the SDK does on a real device before wiring it into your own
app.

Two ways to use it:

- **Read it.** It is a worked example of the integration described in
  [docs/getting-started.md](../docs/getting-started.md) — push intake, device registration, call
  events, the killed-state path, and the incoming-call screen.
- **Run it.** Point it at your own Daakia credentials and place a real call to yourself. If
  something misbehaves in your app, reproducing it here first tells you whether the problem is the
  SDK or your integration — and a bug report that reproduces here is one we can act on immediately.

It consumes the SDK from Maven Central exactly as your app will. There is no privileged access to
SDK internals anywhere in this project.

## Requirements

- Android Studio, and an Android device or emulator on API 23+ (the SDK's `minSdk`)
- JDK 21 — Gradle auto-provisions it if you don't have it, so you can usually ignore this
- A Firebase project with Cloud Messaging enabled
- A Daakia backend URL and customer `secret` — contact [Daakia](https://www.daakia.co.in) if you don't
  have these yet

## Setup

**1. Firebase.** Put your own `google-services.json` in `sample/`. It is gitignored, and the
`google-services.ci.json` placeholder committed here only exists so the build can be verified in
CI — it will not deliver push messages.

**2. Backend credentials.** Add these to `local.properties` in the repository root (gitignored,
never commit it):

```properties
DAAKIA_BASE_URL=https://<your-daakia-backend>
DAAKIA_SECRET=<your-customer-secret>
```

You can skip this and type them into the app's **Configuration** section at runtime instead; they
persist on-device. Either way the secret is masked in the UI and never written to the log.

**3. Build and run.**

```bash
./gradlew :sample:installDebug      # or :sample:assembleDebug for just the APK
```

## Copying from this app

Most of it transfers directly. Two things do not:

**Take one UI module, not both.** This app depends on `callkit-ui-compose` *and*
`callkit-ui-views` so its style picker can preview presets from both toolkits on-device. **Your app
must pick exactly one** — each registers an incoming-call Activity, and whichever
`DaakiaIncomingCallUi.install()` runs last wins, so shipping both makes the outcome depend on
initialisation order. See [Never add both](../docs/call-screen-ui.md).

```kotlin
implementation("ai.daakia:callkit-ui-compose:0.1.0")   // Compose apps
// or
implementation("ai.daakia:callkit-ui-views:0.1.0")     // XML Views apps
```

Either one brings `callkit-core` in transitively — don't declare it alongside them.

**`DemoCallActivity` is a placeholder.** It opens when a call is accepted and says so on screen. Its
mute button and timer are cosmetic and there is no media of any kind. CallKit signals the call;
joining it is your app's job — handle the `ACCEPTED` event and open your own call UI with whatever
audio/video stack you already use.

## How it is built

- **MVVM + Compose (Material 3).** [`SampleViewModel`](src/main/java/ai/daakia/callkit/sample/ui/SampleViewModel.kt)
  owns the UI state and calls the SDK; [`HomeScreen`](src/main/java/ai/daakia/callkit/sample/ui/HomeScreen.kt)
  is a stateless render of it.
- **No secrets in source.** [`SampleConfig`](src/main/java/ai/daakia/callkit/sample/config/SampleConfig.kt)
  resolves the backend `baseUrl`/`secret` from `BuildConfig` (injected from the gitignored
  `local.properties` at build time), with an on-device override from the in-app Configuration
  section.
- **Push intake via the app's own service.**
  [`SampleFirebaseMessagingService`](src/main/java/ai/daakia/callkit/sample/push/SampleFirebaseMessagingService.kt)
  forwards every message to `DaakiaCallKit.handleRemoteMessage()` and re-registers the device on
  token rotation — the path most integrations use. If your app has no messaging service of its own,
  you can register the drop-in `ai.daakia.callkit.push.DaakiaMessagingService` instead and skip this
  file entirely. See [choosing-apis.md](../docs/choosing-apis.md).

## What each section exercises

| Section | SDK surface |
|---|---|
| Status | `canUseFullScreenIntent`, init state |
| Configuration | `DaakiaCallKit.initialize` (re-init on apply) |
| Device registration | `registerDevice`, `getRegisteredDevice`, FCM token retrieval |
| Place a call | `startCallByUsername`, `startCallByToken` (with `IncomingCallData`) |
| Report a call event | `sendCallEvent` (per-action dedupe) |
| Killed-state fallback | `configureCallEventFallback`, `clearCallEventFallback`, `clearSentCallEventCache` |
| Local control & permissions | `endCall`, `openFullScreenIntentSettings`, notification permission |
| Incoming call screen | `DaakiaIncomingCallUi.install` — pick the module (Compose/Views) + style used for real calls; persisted and previewable |
| Activity log | `callEvents` flow + `consumeLaunchEvent` (accept-from-killed-state) |
| Demo in-call screen | `DemoCallActivity`, opened on accept — a labelled placeholder for your own call UI (no real media) |

## Verifying a real call end-to-end (single device)

You can call yourself — no second device needed.

1. Enter credentials (or ship them via `local.properties`) and confirm **Status → SDK:
   initialized**.
2. Grant notifications and, on Android 14+, allow full-screen intents.
3. In **Device registration**, set a username, fetch the token, and **Register device**.
4. In **Place a call**, set **Target username** to that same username and **Start call by
   username** — the device should ring with the installed call screen. Watch the log for the
   `INCOMING` / `ACCEPTED` / `DECLINED` / `TIMED_OUT` events. Accepting opens `DemoCallActivity`.
   **Leave** returns to the app.
5. To exercise the killed-state path, configure the fallback, swipe the app away, then place a call
   and accept it — the app relaunches into the demo in-call screen and the log shows the accept
   `via launch intent`.

If it doesn't ring, [docs/troubleshooting.md](../docs/troubleshooting.md) diagnoses it layer by
layer. OEM battery managers on Xiaomi/MIUI, Oppo, Vivo and Samsung are the most common cause.
