# Daakia CallKit — sample app

A reference integration and manual testbed for the `ai.daakia:callkit-*` SDK. Every screen
section maps to one part of the public API so each feature can be verified on a real device.
The app is **not published** — it exists to dogfood the SDK and to show hosts a clean
integration.

## Architecture

- **MVVM + Compose (Material 3).** [`SampleViewModel`](src/main/java/ai/daakia/callkit/sample/ui/SampleViewModel.kt)
  owns the UI state and calls the SDK; [`HomeScreen`](src/main/java/ai/daakia/callkit/sample/ui/HomeScreen.kt)
  is a stateless render of it. Every SDK result and every `callEvents` emission is appended to
  an on-screen activity log.
- **No secrets in source.** [`SampleConfig`](src/main/java/ai/daakia/callkit/sample/config/SampleConfig.kt)
  resolves the backend `baseUrl`/`secret` from `BuildConfig` (injected from the gitignored
  `local.properties` at build time) with an on-device override saved from the in-app Settings
  section. The secret is masked in the UI and never logged.
- **Push intake via the host's own service.**
  [`SampleFirebaseMessagingService`](src/main/java/ai/daakia/callkit/sample/push/SampleFirebaseMessagingService.kt)
  forwards every message to `DaakiaCallKit.handleRemoteMessage()` and re-registers the device
  on token rotation — the path most integrators use. (A host with no messaging service of its
  own could instead register the drop-in `ai.daakia.callkit.push.DaakiaMessagingService`.)

## Setup

1. **Firebase.** Drop your own `google-services.json` into `sample/` (it is gitignored; a CI
   placeholder is used otherwise). The project it belongs to must have Cloud Messaging enabled.
2. **Backend credentials.** Add to the repo-root `local.properties` (gitignored — never
   committed):

   ```properties
   DAAKIA_BASE_URL=https://<your-daakia-backend>
   DAAKIA_SECRET=<your-customer-secret>
   ```

   You can also leave these out and enter them at runtime in the app's **Configuration**
   section; they persist on-device.
3. **Build & install.**

   ```bash
   ./gradlew :sample:assembleDebug        # or :sample:installDebug onto a device
   ```

## What each section tests

| Section | SDK surface exercised |
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
| Demo in-call screen | `DemoCallActivity`, opened on accept — a labelled placeholder for the host's own audio/video call UI (no real media) |

## Verifying a real call end-to-end (single device)

1. Enter credentials (or ship them via `local.properties`) and confirm **Status → SDK:
   initialized**.
2. Grant notifications and, on Android 14+, allow full-screen intents.
3. In **Device registration**, set a username, fetch the token, and **Register device**.
4. In **Place a call**, set **Target username** to that same username and **Start call by
   username** — the device should ring with the installed call screen. Watch the log for the
   `INCOMING` / `ACCEPTED` / `DECLINED` / `TIMED_OUT` events. Accepting opens the demo in-call
   screen (`DemoCallActivity`). It is a placeholder, and says so on screen: CallKit signals the
   call, but joining the media is your app's choice — open your own call UI on the `ACCEPTED`
   event and connect with whatever stack you already use. The mute button and call timer there
   are cosmetic. **Leave** returns to the app.
5. To exercise the killed-state path, configure the fallback, swipe the app away, then place a
   call and accept it — the app relaunches (into the demo in-call screen) and the log shows the
   accept `via launch intent`.
