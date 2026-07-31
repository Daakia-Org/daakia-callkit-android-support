# Contributing

Thanks for taking the time. This repository works a little differently from most, so it is worth
thirty seconds to read how.

## What this repository is

This is the **public home** for the Daakia CallKit Android SDK: documentation, the changelog, the
licence, and issue tracking. The SDK itself is proprietary — **its source is not public and lives
in a separate private repository.** Releases are published to Maven Central as
`ai.daakia:callkit-core`, `ai.daakia:callkit-ui-compose`, and `ai.daakia:callkit-ui-views`.

So the most useful things you can contribute are **bug reports, integration questions, and feature
requests** — not code.

## Pull requests are not accepted

- **SDK code** — the source is not in this repository, so there is nothing here to patch.
- **Documentation** — the files under `docs/` are maintained in the SDK repository alongside the
  code they describe, and copied here. A PR against them would be overwritten on the next release.
  Please open an issue instead; doc bugs are treated as real bugs and are usually quick to fix.

We would rather you spent five minutes on a good issue than an hour on a PR we cannot merge.

## Reporting a bug

**Security vulnerabilities do not go here.** See [SECURITY.md](SECURITY.md) — reports must be
private, because issues in this repository are public the moment you file them.

For everything else, [open an issue](../../issues/new/choose) and use the bug report template.

Before you do, [docs/troubleshooting.md](docs/troubleshooting.md) walks through calls that don't
ring, layer by layer — push, then notification, then full-screen intent, then OEM behaviour. It
resolves most reports, and the layer it fails at is the single most useful thing you can tell us if
it doesn't.

A good report has:

- **The SDK version and module** — `callkit-core`, `callkit-ui-compose`, or `callkit-ui-views`.
- **Device manufacturer and Android version.** This matters more than it looks: OEM battery
  managers on Xiaomi/MIUI, Oppo, Vivo and Samsung are the most common cause of calls not ringing,
  and the fix differs per vendor.
- **App state when it happened** — foreground, background, or killed. The killed-state path is
  entirely different code.
- **What you expected, and what happened instead.**
- **Relevant logcat**, with credentials removed.

### Redact credentials first

Never paste your `secret`, backend URL, `google-services.json`, FCM registration tokens, or device
tokens into an issue. These are live production credentials, and this tracker is public and
indexed. Deleting the comment afterwards does not undo it — treat anything you post as permanently
public, and rotate a credential you have exposed.

## Asking a question

Questions go in **[Discussions → Q&A](../../discussions/new?category=q-a)**, not the issue tracker.
Answers there stay searchable for the next person who hits the same thing, and you don't have to
decide up front whether what you're seeing is a bug.

These usually answer it faster than we can, though:

- [docs/getting-started.md](docs/getting-started.md) — empty app to a ringing call, step by step.
- [docs/call-events.md](docs/call-events.md) — `ACCEPTED` versus the event flow, the replay buffer,
  and deduplication. This is the part most integrations get wrong, and it is worth reading in full
  before concluding that events are misbehaving.
- [docs/choosing-apis.md](docs/choosing-apis.md) — for the API pairs that look redundant.

## Requesting a feature

Use the feature request template. The most persuasive requests describe **the problem you hit in
your own integration**, not the API you would like added — that gives us room to solve it in a way
that fits the rest of the SDK.

Note that the `0.x` line is not API-frozen: the public surface may still change before `1.0.0`, so
now is the cheapest time for a well-argued API change to land.

## Commercial licensing and credentials

Licensing, onboarding, backend URLs and customer secrets are handled by Daakia directly, not
through this repository. See [daakia.co.in](https://www.daakia.co.in).
