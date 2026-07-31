# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-07-31

**The public API is frozen.** Everything under `ai.daakia.callkit` that is not `internal` is
stable for the `1.x` line: it will not change incompatibly, additions ship as minor versions and
fixes as patches.

The guarantee is **source** compatibility: your code keeps compiling against later `1.x`
releases. It is not binary compatibility — upgrading means recompiling, which is what an Android
app does anyway. Concretely, `DaakiaCallKitConfig` is a data class, so adding a configuration
parameter changes the signature of its generated `copy()`; code compiled against an older `1.x`
and run against a newer one without recompiling can fail on that.

Upgrading from `0.1.0` is a version bump — nothing was removed or renamed, and every new
configuration parameter has a default. One caveat if you are on Kotlin: `CallEventType` gained a
`STALE` constant, so an exhaustive `when` over it stops compiling until you add a branch. That is
a compile error, not a runtime surprise.

### Added

- Stale-call guard. A device that is offline when a call is placed receives the push on
  reconnect — FCM queues it — so a call from an hour ago used to ring as if it had just come
  in. `DaakiaCallKitConfig.staleCallWindow` (60 seconds by default) now caps how old a push may
  be and still ring, and `staleCallBehavior` decides what happens otherwise:
  `MISSED_NOTIFICATION` (default) posts a dismissible missed-call notification,
  `IGNORE` drops it, `RING` keeps the previous behaviour. Age is measured from FCM's
  server-side `sentTime` where available and the payload's `callTimestamp` otherwise, and the
  guard fails open — an unusable timestamp or a device clock that puts the call in the future
  counts as fresh, because suppressing a real call is worse than ringing a stale one. No
  `call-timeout` webhook is sent for a stale call; the backend closed that call long ago.
- `CallEventType.STALE`, emitted when a call is suppressed by the guard, with the call's age in
  `CallEvent.reason`.

### Changed

- The sample app moved to the public support repo,
  [daakia-callkit-android-support](https://github.com/Daakia-Org/daakia-callkit-android-support/tree/main/sample),
  where it consumes `ai.daakia:*` from Maven Central exactly as a host app does. Nothing about
  the published artifacts changes. CI still builds it against the SDK working tree before
  release, so an API break is caught before it reaches Central.

### Documentation

- The API reference is published to GitHub Pages on each release, at
  [daakia-org.github.io/daakia-callkit-android-support](https://daakia-org.github.io/daakia-callkit-android-support/),
  with all three modules in one site and the guides linked from the landing page.
  [javadoc.io](https://javadoc.io/doc/ai.daakia/callkit-core) remains the per-version archive —
  Pages carries the latest release only.
- Removed the "source" link from every declaration in the API reference. It pointed into the
  SDK repository, which is private, so each link 404'd for anyone reading the published site.
- Releases are announced on the public support repo, so integrators have somewhere to watch for
  new versions.
- Pointed the API-reference links in `README.md` and `docs/getting-started.md` at
  [javadoc.io](https://javadoc.io/doc/ai.daakia/callkit-core), and listed the two UI modules
  alongside core. The old `daakia-org.github.io` links were 404s — GitHub Pages was never
  enabled for this repo, so javadoc.io is the API-docs home.
- Updated the rest of the docs for the `0.1.0` Maven Central release: replaced the `<version>`
  placeholders in the `README.md` and `docs/call-screen-ui.md` install snippets with `0.1.0`,
  dropped the `getting-started.md` note telling readers to build from `mavenLocal()` because the
  SDK wasn't published yet, replaced the README pre-release banner with an "API not frozen" note,
  and pointed the README "API at a glance" section at `docs/getting-started.md` instead of
  promising docs that already shipped. Added Maven Central and javadoc.io badges to the README.

## [0.1.0] - 2026-07-28

First public release. Full parity with the `daakia_callkit_flutter` plugin on Android, minus
the Firestore call-state store (dropped in both SDKs; a socket-based sync replaces it later).

Published as `ai.daakia:callkit-core`, `ai.daakia:callkit-ui-compose`, and
`ai.daakia:callkit-ui-views`. The `0.x` line is not API-frozen — the public surface may still
change before `1.0.0`.

### Documentation

- Rewrote the integration docs around the event-handling model, which was the biggest source
  of integration confusion. New [docs/call-events.md](docs/call-events.md) explains the two
  delivery channels (`consumeLaunchEvent` for `ACCEPTED`, the `callEvents` flow for everything
  else), the replay buffer and why it floods late subscribers, deduplication, and conditional
  lock-screen handling. New [docs/choosing-apis.md](docs/choosing-apis.md) is a decision guide
  for the API pairs that look redundant (`DaakiaMessagingService` vs `handleRemoteMessage`,
  `sendCallEvent` vs `configureCallEventFallback`, `endCall` vs `sendCallEvent(END)`, etc.).
  `getting-started.md` Step 9 was corrected — the old version handled `ACCEPTED` on both
  channels, which double-joins the call — and gained a lock-screen step. `docs/index.html`
  and `README.md` now link the full guide set.
- Completed the doc set ported from the Flutter SDK's `doc/` directory. New
  [docs/call-screen-ui.md](docs/call-screen-ui.md) covers the incoming-call screen end to end:
  choosing between `callkit-ui-compose` and `callkit-ui-views`, the three preset styles,
  `DaakiaCallTheme` field by field, and — previously documented only in KDoc — the full
  `IncomingCallUi` contract for hosts building their own call Activity, with a complete
  worked implementation and the four mistakes that path invites. New
  [docs/troubleshooting.md](docs/troubleshooting.md) replaces the symptom table in
  `getting-started.md` with layer-by-layer diagnosis (push → notification → full-screen intent
  → OEM), noting that the SDK logs nothing by design, and documents OEM battery managers
  per vendor. New [docs/client-handoff.md](docs/client-handoff.md) is the Android-only
  onboarding checklist: what a client provides, what they get back, and why `config_name` is
  fixed to `prod` on Android. `android-setup`, `firebase-setup`, and `usage` were not ported
  as separate pages — `getting-started.md` and `permissions.md` already cover them.

### Changed

- The published POM's project, licence, and SCM URLs now point at the public support repo,
  [`daakia-callkit-android-support`](https://github.com/Daakia-Org/daakia-callkit-android-support),
  which carries the licence, the integration guides, and issue tracking. The SDK repository is
  private, so the previous URLs returned 404 for anyone resolving the artifact from Maven
  Central — including the licence link. Maven Central validates that these fields are present
  and well-formed, not that they resolve, so this would have shipped silently; POMs are
  immutable, so a dead link in 0.1.0 would have been permanent.
- Published artifacts no longer include a sources jar. The SDK is proprietary, and a sources
  jar on Maven Central ships the full implementation — including everything under `internal/` —
  to anyone who downloads it. Consumers still get complete KDoc from the javadoc jar, both in
  the IDE and on javadoc.io. Sources can be added in a later release if integrators need
  step-through debugging; the reverse is not possible, since published artifacts are immutable.
- UI module restructure ahead of the Compose UI work: `callkit-ui` renamed to `callkit-ui-compose`
  (artifact `ai.daakia:callkit-ui-compose`, namespace `ai.daakia.callkit.ui.compose`), and a
  new `callkit-ui-views` module added (artifact `ai.daakia:callkit-ui-views`) for XML Views
  host apps that don't want the Compose dependency. Both will ship preset incoming-call
  styles on a shared theming layer. No module was published under the old name.
- `DaakiaMessagingService` now overrides `onNewToken` (deprecated no-op hook, kept for
  legacy token-based registration) and documents `onRegistered` as the modern
  registration callback, following the FID-based registration introduced in
  firebase-messaging 25.1.0.
- Toolchain/dependency refresh: Gradle 9.6.1, appcompat 1.7.1, material 1.14.0;
  sample app now targets SDK 37.
- API polish: the public surface of all three modules was reviewed against a
  `javap -public` dump of the release AARs (binary-compatibility-validator still cannot
  produce one under AGP built-in Kotlin — retried on AGP 9.2.1 / Kotlin 2.3.0). Resulting
  cleanups: the internal `onIncomingCall` sink
  moved off `DaakiaCallKit` onto `CallKitRuntime`, and `Caller.toWireJsonString` is now
  `@JvmSynthetic` so this internal helper no longer appears on Java consumers'
  `IncomingCallDataKt`. No Kotlin-visible API changed.

### Added

- Sample app: accepting a call now opens a stand-in in-call screen (`DemoCallActivity`)
  instead of dropping the user on the home screen, so the testbed demonstrates the full
  accept-to-in-call journey. It shows a caller avatar/name, a connecting → connected
  transition with an elapsed-call timer, and mute/leave controls, and it shows over the
  lock screen (`showWhenLocked`/`turnScreenOn`) for accepts from a locked device. The
  screen labels itself a demo throughout — CallKit only signals the call, and connecting
  the media stays the host app's choice, so nothing here carries real audio or video.
- Sample app rebuilt as a full SDK testbed: a Material 3 / Compose, MVVM
  (`SampleViewModel`) reference integration exercising every public API — device registration,
  `startCallByUsername`/`startCallByToken`, `sendCallEvent`, killed-state fallback config, the
  sent-event cache, `endCall`, the full-screen-intent permission helpers, live `callEvents`
  observation, and `consumeLaunchEvent` — with an on-screen activity log and both UI-preset
  previews. Push intake uses a sample-owned `SampleFirebaseMessagingService` that forwards to
  `DaakiaCallKit.handleRemoteMessage` and re-registers on token rotation (the primary
  integration path), rather than the drop-in `DaakiaMessagingService`. Backend credentials are
  read from the gitignored `local.properties` into `BuildConfig` (`DAAKIA_BASE_URL` /
  `DAAKIA_SECRET`) and can be overridden at runtime from an in-app Settings screen — no secret
  is hard-coded in source. See the
  [sample README](https://github.com/Daakia-Org/daakia-callkit-android-support/blob/main/sample/README.md).
- `DaakiaCallKit.endCall(callId)` — stops a ringing incoming call: silences the ringtone and
  vibration, removes the notification, closes the call screen, and emits
  `CallEventType.ENDED`. Closes a parity gap with the Flutter SDK, whose exported
  `DaakiaNotificationService.dismissIncomingCallNotification` reached the same native path.
  Without it a host had no way to handle the caller hanging up before the callee answered —
  the device rang until the timeout and reported a `call-timeout` that never happened. The
  service-side end path already existed and was tested; only the public entry point was
  missing. `ENDED` was likewise a `CallEventType` the SDK never emitted until now.
  Ending is purely local and reports nothing to the backend — use `sendCallEvent` with
  `CallEventAction.END` for that.
- `docs/getting-started.md` — end-to-end integration guide taking an empty app to a ringing
  incoming call: dependency choice, Firebase wiring, initialization, push forwarding, call
  screen styles, the notification permission, device registration, a single-device
  "call yourself" verification step, accept handling on both the running and killed-state
  paths, and a troubleshooting table. Every Kotlin snippet was compile-checked against the
  real API from a consumer module.
- Dokka 2.2.0 API reference for all three published modules, aggregated into one site by
  `./gradlew dokkaGenerate` and deployed to GitHub Pages under `/api/` by a new
  `docs.yml` workflow (the site root is reserved for the handwritten integration guide).
  Declarations link back to their source line on GitHub, and Android types link out to
  developer.android.com. `failOnWarning` is on, so an unresolved KDoc `[reference]` fails
  the build; CI runs `dokkaGenerate` on every PR. This also makes the javadoc jar published
  to Maven Central real documentation instead of the empty placeholder it was before.
- Outgoing call operations on the facade, completing the public API:
  `DaakiaCallKit.registerDevice(username, fcmToken)`,
  `getRegisteredDevice(username, platform = ANDROID)`,
  `startCallByUsername(username, call, message = call.body)`,
  `startCallByToken(token, platform, call, message = call.body)` and
  `sendCallEvent(meetingUid, action, metadata = null)`. All are `suspend`, backed by a
  shared `BackendClient` built from the initialized config, and always send
  `config_name = prod` per the backend contract. `sendCallEvent` dedupes against the same
  sent-event cache the killed-state fallback worker uses, so an action already reported for
  a `meetingUid` is never webhooked twice.

- XML Views incoming-call UI (`ai.daakia:callkit-ui-views`): `DaakiaIncomingCallUi.install(style, theme)`
  registers a full-screen call screen built on plain Android Views, for host apps that don't
  want the Compose dependency footprint — at full parity with `callkit-ui-compose`'s three
  preset styles (`CLASSIC`, `AURORA` with drifting glow blobs drawn on a custom `View`, `PULSE`
  with animated radiating rings and a breathing accept button). Shares `DaakiaCallTheme` and
  `IncomingCallUi` with `callkit-ui-compose`.
- Compose incoming-call UI (`ai.daakia:callkit-ui-compose`): `DaakiaIncomingCallUi.install(style, theme)`
  registers a full-screen Compose call screen shown over the lock screen, with three preset
  styles — `CLASSIC` (full-screen gradient), `AURORA` (drifting glow blobs with a floating
  glass action bar), `PULSE` (animated rings) —
  accept/decline actions, and close-on-ring-end behavior ported from the Flutter plugin's
  `IncomingCallActivity`.
- Shared UI contract in core (`ai.daakia.callkit.ui`): `IncomingCallUi` (register a custom
  full-screen call Activity, read the launch payload, report accept/decline, observe ring end),
  `DaakiaCallTheme` (colors and labels shared by all presets and both UI modules), and the
  `IncomingCallStyle` enum.
- `IncomingCallPayload.callerDisplayName` — best-effort caller name for call UIs
  (sender userName → callerName → title → "Unknown").
- `DaakiaCallKit.callEvents: SharedFlow<CallEvent>` — call lifecycle events with a replay
  buffer, so events emitted before the host subscribes (e.g. accept from the notification)
  are delivered to late subscribers.
- `DaakiaCallKit.consumeLaunchEvent(intent): CallEvent?` — one-shot recovery of the call
  event carried by the launch intent on the accept-from-killed-state path.
- `DaakiaCallKit.configureCallEventFallback(actions, metadata)`,
  `clearCallEventFallback()`, `clearSentCallEventCache()` — public control of the
  killed-state fallback webhook and the sent-event dedupe cache.
- Internal Ktor `BackendClient` (OkHttp engine) implementing the five backend endpoints
  (device-token register/get, notification trigger by username/by-token, call-event
  webhook) with the `secret` header and `success == 1` detection, wire-identical to the
  Flutter SDK. Covered by MockEngine unit tests ported 1:1 from the Dart suite.
- `DaakiaBackendException` — public exception carrying the backend failure message and,
  for HTTP-level failures, the status code.
- Notification pipeline: after `DaakiaCallKit.initialize(context, config)`, consumed call
  pushes ring — a foreground service posts a CallStyle notification (silent channel, SDK-owned
  ringtone + vibration that stop instantly on answer), schedules the ring timeout, emits call
  events, and launches the host app on accept.
- Killed-state fallback webhook as WorkManager work: unique per `meetingUid::action`,
  network-constrained, exponential backoff, deduped via a persisted sent-event cache
  (ordered, bounded to 100 entries).
- `DaakiaCallKit.canUseFullScreenIntent()` / `openFullScreenIntentSettings()` for the
  Android 14+ full-screen intent permission.
- Manifest declarations (`INTERNET`, `POST_NOTIFICATIONS`, `VIBRATE`,
  `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) and
  the ringing service; each permission documented in `docs/permissions.md`.
- Push intake: `DaakiaCallKit.handleRemoteMessage(message): Boolean` consumes Daakia
  incoming-call pushes (strict `incoming_call` detection, including the legacy nested
  `type` shape delivered as a JSON string) and leaves everything else to the host.
  Optional `DaakiaMessagingService` for hosts without their own messaging service —
  opt-in via manifest, never auto-registered.
- `DaakiaConfigName` enum (`DEV`/`PROD`) — the backend only accepts these `config_name`
  values, so the trigger APIs take the enum instead of a free string.
- `IncomingCallData` — typed builder for the call-trigger `data` payload. Guarantees the
  fields the receiving device needs to ring (`type`, `callId`, `callTimestamp`, `body`,
  `title`), encodes `sender` the way receivers expect, and accepts host extras via
  `extraData` with reserved-key collision checking.

- Public data models in `ai.daakia.callkit.model`: `IncomingCallPayload` (with
  `fromMap` parsing that tolerates the legacy nested `type` payload shape), `Caller`,
  `CallEvent`/`CallEventType`, `CallEventAction`, `DeviceTokenRecord`, `PushResult`,
  and `DaakiaPlatform`.
- `DaakiaCallKitConfig` (`baseUrl`, `secret`, `callTimeout` — default 30s) with a
  secret-redacting `toString()`.
- Project bootstrap: `callkit-core` and `callkit-ui` library modules, sample app,
  ktlint, explicit API mode, Maven Central publishing setup, CI workflows.
