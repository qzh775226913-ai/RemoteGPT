# RemoteGPT

English · [简体中文](README.md)

[![Verify](https://github.com/qzh775226913-ai/RemoteGPT/actions/workflows/verify.yml/badge.svg)](https://github.com/qzh775226913-ai/RemoteGPT/actions/workflows/verify.yml)
[![Release](https://img.shields.io/github/v/release/qzh775226913-ai/RemoteGPT)](https://github.com/qzh775226913-ai/RemoteGPT/releases/latest)
![Android 8+](https://img.shields.io/badge/Android-8%2B-green)

Native Android GUI for remote Codex: projects, conversations, approvals, question forms and attachments.
Codex execution and account login stay on the host. Transport uses SSH without an extra model HTTP port.
Independent client, unaffiliated with OpenAI. English documentation does not imply an English-localized UI.

**[Download APK · 1.0.65](https://github.com/qzh775226913-ai/RemoteGPT/releases/download/v1.0.65/RemoteGPT-1.0.65-Android-universal-debug.apk)** · [Get started](docs/USAGE.en.md) · [Changelog (Chinese)](CHANGELOG.md) · [Protocol coverage](docs/CODEX_DISPLAY_COVERAGE.md) · [Issues](https://github.com/qzh775226913-ai/RemoteGPT/issues)

Source and public stable release: **1.0.65 / versionCode 67**. Android 8+, package `app.remotessh`, approximately 4.4 MB.
Changes from the historical Tailscale-only versions 1.0.58–1.0.63 are included.

## Screens

<img src="docs/screenshots/table-conversation.png" alt="Synthetic test: conversation, formulas and native table" width="250"> <img src="docs/screenshots/device-multi-task.jpg" alt="User-provided device screenshot of multiple task notifications" width="250">

Left: synthetic screenshot from current source. Right: user-provided device screenshot; app version and device model were not independently verified. Notification appearance depends on Android, permissions and manufacturer.

## Features

- Projects and history; per-conversation drafts, attachments, approvals and status.
- Add input or queue a message during execution; structured question forms and approvals.
- Remote model, reasoning effort and speed discovery; conversation permission selection.
- File transfer; image zoom, gallery paging, bounded cache and adjacent-image preloading.
- Local formulas and native Markdown tables; wide content scrolls, copying preserves source.
- Default persistent mode keeps remote work across SSH disconnections and restores output and pending approvals.

## Get started

1. Configure Codex CLI with `app-server` support for your SSH user on a Unix host with SSH/SFTP. Default persistent mode requires Python 3.8+.
2. Install the APK, add a reachable SSH connection and verify its host key fingerprint.
3. Choose a project, create or resume a conversation and manually send a message.

[Connection guide](docs/USAGE.en.md) · [Acceptance boundaries (Chinese)](docs/ACCEPTANCE.md)

## Downloads

- `.apk`: install directly; debug build using the existing project signing identity.
- `with-license.zip`: the same APK, license notices and corresponding formula-library source.
- `.sha256`: checksums; `LICENSE.txt`: project license for this version.

Matching package and signing identities permit upgrade installation; actual installation requires device testing.
Host or supervisor termination can interrupt recovery. Model access and charges depend on remote configuration.
Image preload latency depends on network, image size and cache budgets.

## Build and verify

Java 17, native Android Views, JSch SSH/SFTP, stdio JSON-RPC and a Python standard-library supervisor.
Requires JDK 17, Android SDK 36 / Build Tools 35.0.0 and Python 3.12.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements-dev.txt
.venv/bin/python tools/verify.py
```

Full verification covers Python/Android tests, structure, generated assets, APK and Lint using synthetic loopback SSH.
[Development (Chinese)](docs/DEVELOPMENT.md) · [Version evidence (Chinese)](docs/verification/2026-10-27-1.0.65.md)

## License and contributions

[Source-available license 1.1](LICENSE), not an OSI open-source license: free use, public collaboration and compliant free redistribution of modifications. Commercial reuse, paid feature access and closed-source delivery require separate permission. Ordinary work and independent outputs carry no fee or revenue share. Chinese license text controls.

[Contributing](CONTRIBUTING.md) · [Third-party licenses](docs/THIRD_PARTY.md) · [Requirements](docs/REQUIREMENTS.md) · [Architecture](docs/ARCHITECTURE.md) · [Recovery](docs/ROLLBACK.md)
