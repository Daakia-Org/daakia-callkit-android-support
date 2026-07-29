# Client onboarding checklist

Use this during onboarding, **before** a developer starts integrating. It covers what the client
sends Daakia, what Daakia sets up, and what the client gets back.

This page is deliberately separate from the coding docs — a developer joining an
already-onboarded project can skip it entirely and start at
[getting-started.md](getting-started.md).

> Android only. iOS onboarding (APNs keys, VoIP credentials, sandbox vs production) is a separate
> process with a different checklist.

---

## 1. What the client provides

Firebase belongs to the client, always — the SDK never ships its own Firebase configuration, it
consumes the host app's. So Daakia needs enough access to push into the client's project.

| Item | Why it's needed |
|---|---|
| **Firebase project ID** | Identifies the project calls are pushed into |
| **Android package name** | Must match the Android app registered in that Firebase project |
| **Firebase service account `key.json`** | Lets the Daakia backend send FCM messages on the client's behalf |

Ask for them in one structured message:

```text
Project name:
Firebase project ID:
Android package name:
Firebase service account key.json:  (attached)
```

> **The service account key is a credential.** Send it over a channel you'd be comfortable sending a
> production database password over — not email, not a ticket comment. It grants the ability to push
> notifications to every user of the app. Store it in the client's secret manager on receipt.

## 2. What Daakia sets up

On receipt, Daakia completes the backend-side configuration:

- FCM delivery wired to the client's Firebase project
- the client's push configuration registered under their customer record
- a customer `secret` issued

## 3. What the client gets back

| Delivered | Used as |
|---|---|
| Backend **`baseUrl`** | `DaakiaCallKitConfig(baseUrl = ...)` — host only, no path |
| Customer **`secret`** | `DaakiaCallKitConfig(secret = ...)` — sent as the `secret` header on every request |
| Confirmation that backend setup is complete | Green light to start integrating |

The `secret` is a production credential and must not be committed. The sample app shows the
expected pattern: read it from a gitignored `local.properties` into `BuildConfig`, never a string
literal in source. For production apps, prefer fetching it from your own authenticated backend at
runtime over shipping it in the APK at all — anything in an APK can be extracted.

## 4. Environments

On Android the SDK always sends `config_name: prod`. It is fixed in the backend contract and is not
configurable from the SDK — there is no Android sandbox mode to request or misconfigure.

(The `dev` value exists in the contract for iOS, where APNs sandbox and production are genuinely
different environments. It does not apply here.)

To test against a non-production backend, point `baseUrl` at it. That's the whole mechanism.

## 5. Validate before integration starts

Confirm every line before a developer writes code — each one is a day lost if it's discovered later:

- [ ] Firebase project exists and the client has admin access to it
- [ ] The Android app is registered in that Firebase project
- [ ] The package name in Firebase **exactly** matches the app's `applicationId`
- [ ] `google-services.json` has been downloaded and added to the app module
- [ ] The service account key was received and stored securely
- [ ] Daakia backend setup is confirmed complete
- [ ] `baseUrl` and `secret` have been issued to the client
- [ ] The client has at least one Samsung and one Xiaomi test device available

That last one isn't bureaucracy. OEM battery managers are where incoming calls actually break, and
finding out during the client's UAT rather than during development is expensive. See
[troubleshooting.md](troubleshooting.md#oem-battery-management).

## 6. Set expectations about scope

Worth stating explicitly during onboarding, because it's the most common misunderstanding:

**Daakia CallKit signals calls. It does not carry audio or video.** It delivers the push, rings the
device, shows the incoming-call screen, and reports what the user chose. Connecting the actual media
session after the user accepts is the client's app, using whatever call SDK they already have.

Clients who expect a working voice call out of the box will be disappointed at demo time. Say it in
week one. [call-screen-ui.md](call-screen-ui.md) draws the line in detail.

---

## Next

Once every box in §5 is ticked, hand the developer [getting-started.md](getting-started.md).
