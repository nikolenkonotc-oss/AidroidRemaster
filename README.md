# Aidroid Remaster

Personal-first Android AI agent. Local-first, provider-agnostic, user-controlled.

## Project status

**Aurora UI, local chat persistence, attachments and account/profile foundation / milestone 0.2.0-alpha.9**

The account area supports a device-local profile with editable name, username, status, bio and avatar. Google sign-in is wired through Android Credential Manager; add the OAuth Web client ID as described in `docs/GOOGLE_AUTH_SETUP.md`. Cloud account verification and profile/chat synchronization are intentionally not enabled until a backend is added.

The app includes a Compose navigation shell, Aurora light/dark themes, animated transitions, Room-backed multi-chat history, local photo/file/camera attachments, an adaptive launcher icon, and animated message actions. Provider adapters, AI responses, agent tools, long-term memory, and local inference are not implemented yet.

## Decisions

- Application ID: `app.aidroid.remaster`
- License: Apache-2.0
- Minimum Android version: Android 12 (API 31)
- UI: Kotlin + Jetpack Compose + Material 3
- Build: Gradle Kotlin DSL, version catalog
- Initial structure: one Android app module; architecture boundaries are introduced in code before splitting modules

## Build locally

Requirements:
- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- Gradle 8.13 (or generate and commit the Gradle Wrapper)

From the repository root:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses the `.debug` application ID suffix, so it can be installed alongside a future release build.

## Security principles

- No API keys in source control.
- No silent cloud fallback for private/local tasks.
- Cloud access must follow explicit user policy and approval settings.
- Android system integrations must use official APIs and user-consented flows.
- Tool execution will require typed inputs, risk classification, approval where needed, and verifiable results.

## Planned milestones

1. Repository foundation and CI.
2. Design system and navigation shell.
3. Provider contracts and secure credential storage.
4. Streaming chat and conversation persistence.
5. Model router and capability profiles.
6. Agent runtime, tools, approvals, and activity log.
7. Memory layers with user review and control.
8. llama.cpp local inference integration.

## License

Apache License 2.0. See [LICENSE](LICENSE).

## Alpha 0.2.0-alpha.6
Chat messages can now include local file, photo-picker, and camera attachments. Attachments are copied into private app storage and remain linked to the saved message.


## Alpha.6 — launcher icon and animations

See `docs/ANIMATION_ICON_ALPHA6.md` for the adaptive crystal icon, conversation loading-state fix, message animations, and navigation motion updates.


## Alpha 0.2.0-alpha.7 — UI polish

- Refined Aurora Light colors and glass surfaces; removed the hard-edged hero sheen layer.
- Replaced the attachment popup with an animated Material 3 bottom sheet.
- Added animated inline feedback for attachment, open-file, send, and delete errors.
- Added per-message copy and delete actions; deleting a message also removes its private attachment files.
- Added clickable HTTP/HTTPS links and animated expand/collapse for long messages.
- New messages animate on insertion; message deletion and list movement use lazy-item animations.
- Send operation now keeps the draft and attachments when the database write fails.

See `docs/UI_POLISH_ALPHA7.md` for details.
