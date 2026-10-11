# Project maintenance instructions

Read `docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, `docs/ACCEPTANCE.md` and the relevant module before changing behavior. Current documents are authoritative; `docs/history/` is archival evidence only. Do not infer a new feature from an earlier research suggestion.

## Change boundaries

- Keep the native Android Java stack and existing user-approved UI. Do not mix a refactor with a model/provider change, dependency upgrade, release or remote deployment.
- Use the module registry in `tools/project_structure.json`. New functionality belongs in a focused module, not complete new workflows inside MainActivity or RemoteApp. Do not increase file budgets to avoid extraction.
- All conversation cache and temporary selection changes go through ConversationStore on the main thread. New per-thread fields must be added to ConversationState capture/apply and checked for cross-thread leakage, replay and exception recovery.
- Reuse RemoteApp.request for thread-scoped callbacks and connection generation checks. Worker I/O must not mutate Views or publish temporary background selection. Dialogs own and release their listeners.
- Preserve Vault/SharedPreferences formats and keys, package ID and signing identity unless a separately authorized migration includes recovery. Never print credentials or commit personal environment settings.
- Keep each completed stage independently committed and record the initial recovery commit. Do not destroy other work with reset/clean. Baseline and recovery steps are in docs/ROLLBACK.md.

## Verification and documentation

- Use `python tools/verify.py` with the development Python environment for full acceptance. It owns a synthetic loopback SSH fixture; do not use real Codex credentials, call a model, or restart production tasks for these checks.
- Full acceptance requires zero failed/error/skipped Android tests, passing Python/structure/icon checks, APK build and zero blocking Lint findings. Local unit/python-only success is explicitly partial.
- Update requirements and acceptance when behavior changes, architecture/registry when responsibilities change, and a dated `docs/verification/` record with actual outcomes and limits for delivery.
- Do not claim phone, OEM notification, Tailscale or real-model compatibility from mocks or Robolectric. Keep outstanding device/service acceptance visible.
- Generated icons come from `tools/icon_spec.json`; use the generator and its `--check` mode.
