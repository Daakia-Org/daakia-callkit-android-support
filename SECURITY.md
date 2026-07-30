# Security Policy

## Supported versions

Daakia CallKit is pre-1.0. Only the latest published version receives security fixes.

| Version | Supported |
|---|---|
| 0.1.x | Yes |
| < 0.1.0 | No |

Released versions on Maven Central are immutable and are never withdrawn or overwritten. A fix
always ships as a new version — check the [changelog](CHANGELOG.md) and upgrade.

## Reporting a vulnerability

**Do not open a public issue for a security vulnerability.** Issues in this repository are public
the moment they are filed, including to search engines.

Use one of these instead:

1. **[Report a vulnerability privately](https://github.com/Daakia-Org/daakia-callkit-android-support/security/advisories/new)**
   — preferred. GitHub's private reporting keeps the whole thread confidential until a fix ships,
   and lets us credit you when it does.
2. **Email <info@daakia.co.in>** with `SECURITY` in the subject, if you would rather not use GitHub.

Please include: the SDK version and module, what an attacker can achieve, the steps to reproduce,
and the device and Android version if it is platform-specific.

We aim to acknowledge a report within five working days. Please give us reasonable time to ship a
fix before disclosing publicly.

## Redact credentials before you send anything

This applies to security reports, bug reports, and anything pasted into a discussion.

Never include your Daakia `secret`, backend URL, `google-services.json`, FCM registration tokens,
or device tokens. These are live production credentials — a leaked `secret` lets someone place
calls as your application.

If you have already exposed a credential publicly, treat it as compromised and contact Daakia to
have it rotated. Deleting the comment is not enough; public content is cached and indexed.

## Scope

**In scope** — anything in the published `ai.daakia:callkit-*` artifacts:

- Leakage of the `secret`, device tokens, or FCM tokens through logs, crash reports, backups, or
  inter-app surfaces.
- An exported component (Activity, Service, Receiver) that another installed app can abuse to
  place, accept, or spoof a call.
- Weaknesses in how call payloads from FCM are validated or trusted.
- Bypassing lock-screen or full-screen-intent restrictions in a way Android does not intend.

**Out of scope:**

- Vulnerabilities in the Daakia backend or in your own application code. Report backend issues to
  Daakia directly via [daakia.ai](https://daakia.ai).
- Anything requiring a rooted device, a malicious OS build, or physical access to an unlocked
  device.
- OEM battery managers or aggressive process-killing preventing calls from ringing. This is a
  well-known Android behaviour, not a vulnerability — see
  [docs/troubleshooting.md](docs/troubleshooting.md).
- Reports produced solely by an automated scanner, with no demonstrated impact.

## A note on the SDK source

The SDK is proprietary and its source is not public, so you cannot audit it directly. The published
artifacts are **PGP-signed** on Maven Central — every release carries a `.asc` signature you can
verify against the signing key before trusting an artifact.
