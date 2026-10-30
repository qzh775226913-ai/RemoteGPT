# First connection guide

For RemoteGPT 1.0.67. [中文说明](USAGE.md) · [Download APK](https://github.com/qzh775226913-ai/RemoteGPT/releases/download/v1.0.67/RemoteGPT-1.0.67-Android-universal-debug.apk)

RemoteGPT provides an Android graphical interface for Codex running on a host you can access over SSH. Configure the Codex account and model service on that host first. The app does not perform remote Codex account login for you.

## 1. Check the remote environment

Use another SSH client to confirm that the intended system user can log in, use SFTP and access the project directory. Configure working Codex CLI access for that same user, then check:

```sh
command -v codex
codex --version
codex app-server --help
```

Default persistent mode needs Python 3.8+ and the following standard-library capabilities:

```sh
python3 -c 'import sys, sqlite3, fcntl, socket; assert sys.version_info >= (3, 8); assert hasattr(socket, "AF_UNIX"); print("Python OK")'
```

These commands check the environment; they do not test a model request. Confirm Codex works on the host before connecting the phone. Non-interactive SSH can have a different PATH from an interactive terminal.

The app starts App Server automatically. Persistent mode writes its supervisor and conversation data to `~/.remote-gpt`; the SSH user's home directory must be writable. You do not need to open an additional model-service HTTP port.

Remote-system compatibility:

- **Linux:** the primary and recommended remote environment.
- **macOS:** compatible when SSH/SFTP, POSIX `sh`, the Codex requirements above, and a writable home directory are available; persistent mode also requires the Python environment. Automated testing has not covered a real macOS host yet.
- **Native Windows:** not currently supported because the app relies on POSIX shell behavior, permissions, `fcntl` and Unix sockets. WSL or a Linux VM can be used if it exposes SSH/SFTP separately and meets every requirement.
- **NAS appliances and containers:** may work when they meet every requirement and provide a persistent, writable home directory, but are not individually validated.

## 2. Install and add a connection

Download the APK from the project release page and install it on Android 8 or newer. If Android prompts you, allow installation from the browser or file manager used for this installation; you can revoke that permission afterward.

Open the remote-control menu and select “添加连接” (Add connection).

| Field | Value |
| --- | --- |
| Host and port | An SSH address reachable from the phone; port 22 by default |
| Username | The system user whose Codex setup you checked above |
| Authentication | Password or private key; provide the passphrase for an encrypted key |
| Remote project directory | Absolute path, or select it later with the directory browser |
| Startup command | `codex app-server` by default |
| Persistent session | Enabled by default; requires the Python environment above |

If Codex is not found, use its absolute path from `command -v codex`. For example:

```text
/home/me/.local/bin/codex app-server
```

Verify the SSH host key fingerprint through a trusted channel before accepting it. When using a Tailscale address, connect the phone to the appropriate tailnet and ensure network policy allows SSH access. Tailscale connectivity and Tailscale SSH identity authentication are separate configurations; ordinary SSH still needs its configured authentication.

## 3. Complete your first conversation

1. Browse to the project directory.
2. Create a conversation, or select and resume a historical one.
3. Type a message and tap Send. Browsing or resuming history alone does not send a message.
4. Read the remote response. If Codex requests approval or asks a structured question, respond using the buttons or form.

A real remote response is the completion check. An SSH connection or successful environment diagnostic alone does not prove the model service works.

Use the model panel to choose the remote model, reasoning effort and speed. Model metadata refreshes every 30 seconds while connected. Send a message to apply selection changes to the next turn. Permissions also take effect with the next message.

Use the system picker to attach images and files. Each message supports up to 4 images (20 MiB each) and 8 other files (100 MiB each). Image decoding depends on the phone. Images can be previewed and saved from the conversation.

## 4. Troubleshoot

| Symptom | Check |
| --- | --- |
| SSH authentication failed | Host, port, username and authentication; identity and policy if using Tailscale SSH |
| Host key changed | Confirm whether the server key actually changed before trusting it |
| Codex not found | Installation for the same user, absolute startup path and non-interactive SSH environment |
| Python missing or too old | Full Python 3.8+ available as `python3` to non-interactive SSH |
| Cannot install Python | Explicitly disable persistent mode in the connection settings; remote execution across disconnects is then not guaranteed and some recovery/maintenance functions are unavailable |
| SFTP or supervisor write failed | SFTP support, home-directory permissions and disk space |
| App Server reports no login | Complete Codex account setup as the same SSH user, then reconnect |
| Connected, but model requests fail | Remote model service connectivity, account permissions, quota and configuration |

Persistent recovery depends on the remote supervisor remaining alive. Device notifications, background operation, host reboots and process termination require separate real-device/service validation. See the [acceptance boundaries (Chinese)](ACCEPTANCE.md).

## 5. Report a problem

Open an [issue](https://github.com/qzh775226913-ai/RemoteGPT/issues) with the app version, Android version, remote Codex version, reproduction steps and exact error text. Redact host addresses, usernames, private paths and conversation content from screenshots. Never include passwords, private keys, tokens or account configuration files.

Use and redistribution are governed by the [project license](../LICENSE).

## 6. Formula rendering

Explicit inline `\(…\)` and `$…$`, and display `\[…\]` and `$$…$$` TeX in remote prose are typeset locally with native fonts. Common fractions, scripts, Greek symbols, roots and matrices are supported. Long expressions scroll horizontally; long-press a display formula to copy its original TeX. Full-message copy retains the original source.

Code, escaped markers and ordinary currency stay literal. Incomplete, unsupported, excessive or failed expressions remain readable source. No macro definitions, file access, remote resource loading or code execution is enabled. Whether the model emits formula markers depends on its actual response; this feature does not change the model or remote configuration.

## 7. Tables and light surfaces

Standard Markdown tables use a native grid. Wide tables scroll horizontally as one unit; longer cells wrap within bounded column widths. Copy preserves the original table text. Appended rows retain the horizontal reading position. Code blocks, space-aligned prose and unsupported or oversized tables remain literal. Light mode separates the soft gray-white page, white replies and slightly darker input and table-header surfaces.
