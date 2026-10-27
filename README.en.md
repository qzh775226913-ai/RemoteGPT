# RemoteGPT

English · [简体中文](README.md)

Use a native Android interface to work with remote Codex over SSH. Codex execution and account login stay on the host.

**[Download APK · 1.0.65](https://github.com/qzh775226913-ai/RemoteGPT/releases/download/v1.0.65/RemoteGPT-1.0.65-Android-universal-debug.apk)** · [Changelog](CHANGELOG.md) · [Issues](https://github.com/qzh775226913-ai/RemoteGPT/issues)

Current version: **1.0.65 / versionCode 67**. The phone requires Android 8+.

## Screens

<img src="docs/screenshots/table-conversation.png" alt="Synthetic formula and table example" width="250"> <img src="docs/screenshots/device-multi-task.jpg" alt="Device screenshot of multiple task notifications" width="250">

Left: synthetic formula and table example. Right: device notification example; appearance varies by device.

## Features

- Browse projects and history; keep drafts, attachments and status per conversation.
- Add input or queue a message during execution; respond to approvals and question forms.
- Select remote models, reasoning effort, speed and permissions.
- Transfer files, preview images and browse them continuously.
- Read formulas and Markdown tables; copying preserves source text.
- Persistent mode retains remote tasks across SSH disconnections and restores them on reconnect.
- A foreground service keeps the connection active, with per-conversation notifications; exact behavior depends on the phone.

## Compatibility

| Device | Support |
| --- | --- |
| Android phone | Android 8 or newer |
| Linux server | Primary and recommended remote environment |
| macOS host | Compatible when it meets the Unix requirements below; no automated validation on a real macOS host yet |
| Native Windows host | Not currently supported; use WSL or a Linux VM that the phone can reach over SSH/SFTP |

The remote host needs SSH command execution, SFTP, POSIX `sh`, writable home and project directories, and a Codex CLI with `app-server` support. The same SSH user must already be logged in to Codex. Default persistent mode also needs Python 3.8+ with SQLite, `fcntl` and Unix sockets. You can disable persistence if Python is unavailable, but execution across disconnects and full recovery are then not guaranteed.

The phone only needs access to the host's SSH port, directly or through a network such as Tailscale. No extra model-service port is required.

## Get started

1. Install and log in to Codex as the intended SSH user; verify `codex --version` and `codex app-server --help`.
2. Confirm that user can run SSH commands, use SFTP, and read and write the home and project directories.
3. Install the APK, add the SSH connection and verify the host fingerprint through a trusted channel.
4. Choose a project, create or resume a conversation, and send a message.

If non-interactive SSH cannot find Codex, configure its absolute path as the startup command. See the [full setup and troubleshooting guide](docs/USAGE.en.md).

## License

[Source-available license](LICENSE): use, modification and public collaboration are permitted. Non-commercial redistribution must retain attribution and license notices. Commercial reuse requires written permission. Independent client, unaffiliated with OpenAI. English documentation does not imply an English-localized interface.

[Contributing](CONTRIBUTING.md) · [Third-party licenses](docs/THIRD_PARTY.md)
