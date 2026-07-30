# Daakia CallKit — Android SDK

[![Maven Central](https://img.shields.io/maven-central/v/ai.daakia/callkit-core?label=Maven%20Central)](https://central.sonatype.com/artifact/ai.daakia/callkit-core)
[![API docs](https://javadoc.io/badge2/ai.daakia/callkit-core/API%20docs.svg)](https://javadoc.io/doc/ai.daakia/callkit-core)
[![License](https://img.shields.io/badge/license-Proprietary-blue)](LICENSE)

Native Android SDK for Daakia-backed incoming call signaling: device token registration,
FCM push handling, full-screen incoming call notifications, call lifecycle events with
killed-state webhook fallback, and optional pre-built incoming-call screens (Jetpack Compose
or classic XML Views) with multiple host-themeable preset styles.

This repository is the **public home** for the SDK — documentation, licence, and issue
tracking. The SDK itself is proprietary and its source is not public; releases are published
to Maven Central.

## Installation

```kotlin
dependencies {
    implementation("ai.daakia:callkit-ui-compose:0.1.0")
    // or, for XML Views apps:
    implementation("ai.daakia:callkit-ui-views:0.1.0")
    // or, if you build your own incoming-call UI:
    implementation("ai.daakia:callkit-core:0.1.0")
}
```

| Artifact | Description |
|---|---|
| `ai.daakia:callkit-core` | All SDK functionality except UI. No UI-toolkit dependency. |
| `ai.daakia:callkit-ui-compose` | Jetpack Compose incoming-call preset screens. Depends on `callkit-core`. |
| `ai.daakia:callkit-ui-views` | Classic XML Views incoming-call preset screens, for apps that don't want the Compose dependency. Depends on `callkit-core`. |

> The `0.x` line is not API-frozen — the public surface may still change before `1.0.0`.

## API at a glance

Everything goes through `DaakiaCallKit`.

```kotlin
// Application.onCreate()
DaakiaCallKit.initialize(context, DaakiaCallKitConfig(baseUrl = "https://…", secret = "…"))
DaakiaIncomingCallUi.install(style = IncomingCallStyle.CLASSIC)   // optional pre-built UI

// Receiving: forward FCM messages (or register DaakiaMessagingService instead)
override fun onMessageReceived(message: RemoteMessage) {
    if (DaakiaCallKit.handleRemoteMessage(message)) return
}

// Placing calls (suspend)
DaakiaCallKit.registerDevice(username = "alice", fcmToken = token)
DaakiaCallKit.startCallByUsername(username = "bob", call = IncomingCallData(callId, title = "Alice"))

// Call lifecycle
DaakiaCallKit.consumeLaunchEvent(intent)                          // ACCEPTED (launches your app)
DaakiaCallKit.callEvents.collect { event -> /* INCOMING, DECLINED, ENDED, TIMED_OUT */ }
DaakiaCallKit.sendCallEvent(meetingUid, CallEventAction.JOIN)
DaakiaCallKit.configureCallEventFallback(setOf(CallEventAction.ACCEPT))  // works with the app dead
```

> `ACCEPTED` comes from `consumeLaunchEvent`; every other event from `callEvents`. Handling
> accept in both double-fires — see [docs/call-events.md](docs/call-events.md).

## Documentation

- **[Getting started](docs/getting-started.md)** — empty app to a ringing call, step by step.
- **[Handling call events](docs/call-events.md)** — `ACCEPTED` vs the event flow, the replay
  buffer, deduplication, and lock-screen handling. The part most people get wrong.
- **[The incoming-call screen](docs/call-screen-ui.md)** — preset UI modules, the three styles,
  theming, and building your own call Activity.
- **[Choosing the right API](docs/choosing-apis.md)** — which API to use when.
- **[Troubleshooting](docs/troubleshooting.md)** — calls that don't ring, layer by layer.
- **[Permissions](docs/permissions.md)** — what the SDK declares and why.
- [Client onboarding checklist](docs/client-handoff.md) — for the handoff before integration starts.

### API reference

Generated from KDoc and hosted on javadoc.io, versioned to match each release:

- [`callkit-core`](https://javadoc.io/doc/ai.daakia/callkit-core/latest/)
- [`callkit-ui-compose`](https://javadoc.io/doc/ai.daakia/callkit-ui-compose/latest/)
- [`callkit-ui-views`](https://javadoc.io/doc/ai.daakia/callkit-ui-views/latest/)

## Requirements

- minSdk 23
- The host app provides its own Firebase project/config; the SDK consumes the host's FCM messages.

## Support

Found a bug, or stuck on an integration? [Open an issue](../../issues/new/choose).
[CONTRIBUTING.md](CONTRIBUTING.md) covers what makes a report we can act on.

Please don't include your `secret`, backend URL, `google-services.json`, or any credential in
an issue — redact them before pasting logs.

**Found a security vulnerability?** Don't open an issue — see [SECURITY.md](SECURITY.md) for the
private reporting route.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

Proprietary commercial software. See [LICENSE](LICENSE).

Redistribution of the SDK, or use beyond the terms in the licence, requires written consent
from Daakia Private Limited.
