# Permissions

`callkit-core`'s manifest declares the permissions below; manifest merging adds them to your
app automatically. This page explains why each exists and what you must still do at runtime.

| Permission | Why the SDK needs it | Runtime action needed |
|---|---|---|
| `INTERNET` | Backend calls: device-token registration, call triggers, call-event webhooks. | None. |
| `POST_NOTIFICATIONS` | Posting the incoming-call notification (Android 13+). | **Yes** — request it at runtime; without the grant, calls cannot be shown. |
| `VIBRATE` | Vibration while a call rings. | None. |
| `USE_FULL_SCREEN_INTENT` | Showing the incoming-call screen over the lock screen. | On Android 14+ the user can revoke it. Check `DaakiaCallKit.canUseFullScreenIntent()` and offer `DaakiaCallKit.openFullScreenIntentSettings()` from your app's settings. |
| `FOREGROUND_SERVICE` | The ringing phase runs in a foreground service so the system does not kill it. | None. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Foreground-service type of the ringing service. `mediaPlayback` because the service plays the looping ringtone; the `phoneCall` type would additionally require `MANAGE_OWN_CALLS`. | None. |

## Requesting the notification permission

```kotlin
if (Build.VERSION.SDK_INT >= 33 &&
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
) {
    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
}
```

## Full-screen intent on Android 14+

Apps installed from Play with a call use case usually keep the full-screen intent grant, but
users can revoke it under *Settings → Apps → Special app access → Full screen notifications*.
When revoked, incoming calls degrade to a heads-up notification. Recommended pattern:

```kotlin
if (!DaakiaCallKit.canUseFullScreenIntent()) {
    // Explain why, then:
    DaakiaCallKit.openFullScreenIntentSettings()
}
```

## What the SDK deliberately does not declare

- `MANAGE_OWN_CALLS`, telecom integration — the SDK renders call UX itself; it does not
  register with Telecom.
- `WAKE_LOCK` — the full-screen intent and foreground service cover the ringing path.
- Firebase/FCM permissions — Firebase belongs to the host app; the SDK only consumes
  forwarded messages.
