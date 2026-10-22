# RemoteGPT — Android GUI for Codex over SSH

English · [简体中文](README.md)

Use a native **Android graphical interface** to work with Codex running on your remote computer: browse projects and conversations, read responses, handle approvals, answer structured questions, and transfer attachments. Built for Android users who already use Codex CLI and want to access remote work away from their desk.

Codex and its account configuration stay on the remote host. The phone provides touch controls, task status and file transfer. This is an independent client, unaffiliated with OpenAI.

**[Download Android APK](https://github.com/qzh775226913-ai/RemoteGPT/releases/download/v1.0.55/RemoteGPT-1.0.55-Android-universal-debug.apk)** · [First connection guide](docs/USAGE.en.md) · [Release notes and checksums](https://github.com/qzh775226913-ai/RemoteGPT/releases/latest) · [Report an issue](https://github.com/qzh775226913-ai/RemoteGPT/issues)

Current version: **1.0.55 / versionCode 56**. Android 8+, package `app.remotessh`, approximately 3.8 MB.

## Graphical controls for remote work

| Task | Android interface |
| --- | --- |
| Open projects and conversations | Browse remote directories and conversation history; keep separate drafts and attachments per conversation |
| Follow ongoing work | Read output in a conversation, view task status, add input or queue the next message |
| Respond to requests | Use buttons for approvals and forms for structured questions |
| Choose models and permissions | Select the model, reasoning effort, speed and permissions in panels; remote model metadata refreshes every 30 seconds while connected |
| Transfer attachments | Pick files with the system picker, preview and save images, and download files |

Lists, buttons and forms reduce command entry, terminal scrolling and context switching on a small screen. Initial setup still requires a working SSH and remote Codex environment. The English documentation describes the app; it does not imply a fully English-localized interface.

## Get started

1. Configure working Codex CLI access for your SSH user. The host needs SSH/SFTP; default persistent mode also requires Python 3.8+.
2. Install the APK, add an SSH connection, and verify the host key fingerprint.
3. Browse to a project, create or resume a conversation, and manually send your first message.

See the [connection guide](docs/USAGE.en.md) for field examples and troubleshooting. The app communicates over SSH and does not require an additional HTTP port for its model service.

## Task status example

<img src="docs/screenshots/live-status.jpg" alt="RemoteGPT running-task status in a device-specific status area" width="420">

This image shows one device's status area. Notifications, bubbles and background behavior depend on Android version, permissions and the device manufacturer.

## Requirements and limits

| Component | Requirement |
| --- | --- |
| Phone | Android 8 or newer with network access to the SSH host |
| Host | Unix environment, SSH/SFTP, and a configured Codex CLI supporting `app-server` for the connecting user |
| Persistent mode | Python 3.8+ with sqlite3, fcntl and Unix sockets; writable home directory |

Default persistent mode keeps remote work alive across SSH disconnections and supports output and pending-approval recovery on reconnect. Host or supervisor termination can interrupt recovery. Available models, account permissions and service charges depend on the remote configuration. Automated tests do not establish compatibility with every device or live model service; see the [acceptance boundaries (Chinese)](docs/ACCEPTANCE.md).

## Development and license

Native Android Views, Java 17, JSch SSH/SFTP, stdio JSON-RPC, and a Python standard-library supervisor. Build and test instructions are in the [development guide (Chinese)](docs/DEVELOPMENT.md).

[Chinese documentation](README.md) · [Issue tracker](https://github.com/qzh775226913-ai/RemoteGPT/issues) · [License](LICENSE)

Normal use is free under the custom project license. Commercial reuse and distribution of modified versions are subject to the license terms. Codex service charges are separate.
