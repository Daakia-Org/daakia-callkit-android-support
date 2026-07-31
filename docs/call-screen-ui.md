# The incoming-call screen

Daakia CallKit rings the device and tells you what the user chose. **It does not carry audio or
video.** Connecting the media once the user accepts is your app's job, with whatever call SDK you
already use.

That split is deliberate, and it's the single most useful thing to understand about this SDK:

| Concern | Owner |
|---|---|
| Push delivery, ringing, the incoming-call screen, accept/decline/timeout | Daakia CallKit |
| The in-call screen and the actual media session | Your app |

This page covers the first half — what rings, and how to make it look like your product.

---

## 1. Three ways to get a call screen

Pick one. They cost you progressively more work and give you progressively more control.

| Option | You add | You get |
|---|---|---|
| **A. A preset UI module** | `callkit-ui-compose` *or* `callkit-ui-views`, plus one `install()` call | A full-screen, lock-screen-capable call screen in one of three themeable styles |
| **B. Your own Activity** | `callkit-core` only, plus an Activity you write against `IncomingCallUi` | Complete control over the screen; the SDK still handles ringing, timeout, and events |
| **C. Nothing** | `callkit-core` only, no Activity registered | A heads-up CallStyle notification with Accept/Decline. No full-screen screen. |

Option C is a real option, not a degraded one — if your calls only ever arrive while the user is
already in your app, a notification may be all you need. It is also the automatic fallback: if the
registered Activity can't be shown for any reason, the notification still rings.

---

## 2. Option A — a preset UI module

### Choosing the module

Both modules are at full parity: the same three styles, the same `install(style, theme)` API, the
same behavior. They differ only in what they drag into your build.

| | `callkit-ui-compose` | `callkit-ui-views` |
|---|---|---|
| Renders with | Jetpack Compose | Classic XML Views |
| Adds to your app | The Compose runtime + UI (a few hundred KB if you don't already use Compose) | Effectively nothing beyond the screens |
| Use it if | Your app already uses Compose, or you don't mind the dependency | Your app is Views-based and you want the smallest footprint |

```kotlin
dependencies {
    implementation("ai.daakia:callkit-ui-compose:1.0.0")
    // or
    implementation("ai.daakia:callkit-ui-views:1.0.0")
}
```

Either one pulls `callkit-core` in transitively — don't declare it separately.

**Never add both.** They register competing Activities, and whichever `install()` runs last wins.
There is no reason to have both in one app.

### Installing

Call `install()` once, right after `DaakiaCallKit.initialize`, in your `Application.onCreate`:

```kotlin
import ai.daakia.callkit.ui.IncomingCallStyle
import ai.daakia.callkit.ui.compose.DaakiaIncomingCallUi   // or ...ui.views.DaakiaIncomingCallUi

DaakiaCallKit.initialize(this, config)
DaakiaIncomingCallUi.install(style = IncomingCallStyle.CLASSIC)
```

The import is the *only* thing that differs between the two modules. Everything below applies
equally to both.

You do not need to declare anything in your manifest — each UI module ships its own Activity
declaration, already configured with `showWhenLocked`, `turnScreenOn`, `excludeFromRecents`, and
`launchMode="singleTop"`.

`install()` may be called again later to change the style or theme; the change takes effect on the
next call that rings. `uninstall()` reverts to Option C.

### The three styles

All three are full-screen and all three take the same theme.

| `IncomingCallStyle` | Looks like |
|---|---|
| `CLASSIC` | Vertical gradient background, centered caller avatar, accept/decline row. The default, and the safe choice. |
| `AURORA` | Drifting, softly-glowing colour blobs behind the avatar, with a floating glass action bar. |
| `PULSE` | Animated rings radiating outward from the caller avatar. |

The sample app has a picker that previews all three on-device — run it before choosing.

### Theming

`DaakiaCallTheme` is shared by every style and both modules. Every field is optional; the defaults
are a dark call screen.

```kotlin
DaakiaIncomingCallUi.install(
    style = IncomingCallStyle.AURORA,
    theme = DaakiaCallTheme(
        backgroundStartColor = 0xFF1A1033.toInt(),
        backgroundEndColor   = 0xFF3B1E6E.toInt(),
        acceptButtonColor    = 0xFF00C853.toInt(),
        acceptLabel          = "Join",
        declineLabel         = "Dismiss",
    ),
)
```

| Field | Applies to |
|---|---|
| `backgroundStartColor` | Top (or centre) of the background gradient |
| `backgroundEndColor` | Bottom (or edge) of the background gradient |
| `primaryTextColor` | Caller name and other prominent text |
| `secondaryTextColor` | Call title, body, button labels |
| `avatarBackgroundColor` | Fill of the caller-initial avatar circle |
| `acceptButtonColor` | Accept button fill |
| `declineButtonColor` | Decline button fill |
| `acceptLabel` / `declineLabel` | Button text — **override these to localize** |

Colours are ARGB ints. In Kotlin, write them as `0xAARRGGBB.toInt()`; the `.toInt()` is required
because the literal overflows a signed `Int` without it.

> **Localization:** the labels are plain strings, not string resources, because they're set from
> code at install time. To localize, pass `getString(R.string.your_accept_label)` — resolve them
> against a context, not at class-initialization time.

---

## 3. Option B — your own incoming-call Activity

Use `callkit-core` alone and build the screen yourself. The SDK still owns the ringtone, the
vibration, the timeout, the notification, and the event pipeline; you own only the pixels.

`ai.daakia.callkit.ui.IncomingCallUi` is the whole contract:

| Member | Purpose |
|---|---|
| `registerActivity(Class)` | Makes your Activity the notification's full-screen-intent target |
| `launchPayload(intent)` | The `IncomingCallPayload` that launched you, or `null` — finish if `null` |
| `isAcceptLaunch(intent)` | The user already accepted from the notification: don't render, just accept and finish |
| `acceptCall(activity)` | Stops the ringing, emits `ACCEPTED`, fires the fallback webhook, launches your app |
| `declineCall(context)` | Stops the ringing, emits `DECLINED` |
| `onCallClosed(context) { callId -> }` | Fires when the ringing ends *elsewhere* (timeout, remote end, accept from the notification) — finish your Activity |

### A complete minimal implementation

```kotlin
class MyIncomingCallActivity : ComponentActivity() {

    private var shownCallId: String? = null
    private var closeSubscription: AutoCloseable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        bindToIntent(intent)
    }

    // launchMode="singleTop": a second call arriving re-delivers here instead of stacking.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindToIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        closeSubscription = IncomingCallUi.onCallClosed(this) { closingCallId ->
            // null means "close every call screen"; otherwise only close if it's ours.
            if (closingCallId == null || closingCallId == shownCallId) finish()
        }
    }

    override fun onStop() {
        closeSubscription?.close()
        closeSubscription = null
        super.onStop()
    }

    private fun bindToIntent(intent: Intent) {
        val payload = IncomingCallUi.launchPayload(intent) ?: run { finish(); return }
        shownCallId = payload.callId.takeIf { it.isNotBlank() }

        // The user tapped Accept on the notification — there is nothing to show.
        if (IncomingCallUi.isAcceptLaunch(intent)) {
            IncomingCallUi.acceptCall(this)
            finish()
            return
        }

        setContent {
            MyCallScreen(
                callerName = payload.callerDisplayName,
                subtitle = payload.body.orEmpty(),
                onAccept = { IncomingCallUi.acceptCall(this); finish() },
                onDecline = { IncomingCallUi.declineCall(this); finish() },
            )
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        // The window flags are deprecated in favour of the calls above, but they are still
        // the only mechanism below API 27 — and minSdk here is 23.
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
    }
}
```

Register it next to `initialize`:

```kotlin
IncomingCallUi.registerActivity(MyIncomingCallActivity::class.java)
```

And declare it in **your** manifest — nothing is contributed for you on this path:

```xml
<activity
    android:name=".MyIncomingCallActivity"
    android:excludeFromRecents="true"
    android:exported="false"
    android:launchMode="singleTop"
    android:showOnLockScreen="true"
    android:showWhenLocked="true"
    android:turnScreenOn="true" />
```

### Four mistakes that are easy to make here

1. **Forgetting `onCallClosed`.** Without it, a call that times out or is cancelled by the caller
   leaves your screen on top forever, with a dead Accept button.
2. **Ignoring `isAcceptLaunch`.** The user already accepted from the notification; rendering the
   screen again asks them to accept a second time.
3. **Omitting `launchMode="singleTop"` / `onNewIntent`.** A second incoming call stacks a second
   Activity instead of replacing the first.
4. **Not finishing when `launchPayload` returns `null`.** You'd show an empty call screen for a
   call that doesn't exist.

### What the payload gives you

`IncomingCallPayload.callerDisplayName` is the field you want for the big name on screen — it
already falls back through the sender's user name, `callerName`, and the title before landing on
`"Unknown"`. Beyond that: `callId`, `sender`, `callerId`, `receiverId`, `callTimestamp`, `body`,
`title`, and `raw` for any custom field your backend added that the model doesn't type yet.

---

## 4. After the user accepts

Accepting stops the ringing and brings your app to the front. **CallKit's job ends there.** Your
app then reads the accept and opens its own in-call screen:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DaakiaCallKit.consumeLaunchEvent(intent)?.let { event ->
        startActivity(Intent(this, MyInCallActivity::class.java)
            .putExtra("callId", event.payload?.callId))
    }
}
```

`consumeLaunchEvent` — not the `callEvents` flow — is how you handle `ACCEPTED`, because accept
frequently happens while your process is dead. Handling it on both channels joins every call twice.
This is the most common integration bug in the SDK; [call-events.md](call-events.md) covers it
properly and you should read it before shipping.

From that point on you're joining a meeting with your own media SDK. The sample app's
`DemoCallActivity` shows the shape of this handoff (with no real media attached).

---

## 5. Keep the three concerns separate

The integrations that stay maintainable keep these apart:

1. **Push intake and ringing** — CallKit, configured once in `Application.onCreate`.
2. **Accept / decline handling** — one place in your launcher Activity.
3. **The in-call session and media** — your own screen, your own SDK.

Collapsing 2 into 3, in particular, is what produces the double-join bug.

---

## See also

- [call-events.md](call-events.md) — `ACCEPTED` vs the event flow. Read before shipping.
- [choosing-apis.md](choosing-apis.md) — which API to use when.
- [getting-started.md](getting-started.md) — the end-to-end setup walkthrough.
- [troubleshooting.md](troubleshooting.md) — when the screen doesn't appear.
