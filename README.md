# Aidroid Remaster

Personal-first Android AI agent. Local-first, provider-agnostic, user-controlled.

## Project status

**Aurora UI, local chat persistence, attachments, Google accounts and Firebase sync / milestone 0.2.0-alpha.10**

The account area supports a local profile and an explicit Google account chooser through Android Credential Manager. When a Firebase project is configured, Firebase Authentication verifies the Google identity and Cloud Firestore synchronizes profile fields and text chat history across devices. Custom profile avatars use Firebase Storage. See `docs/GOOGLE_AUTH_SETUP.md` for one-time project setup.

Local mode remains available without registration. Room is still used for immediate local access. Chat and message deletion markers are synchronized to prevent deleted conversations from reappearing on another device. Binary chat attachments remain local to the device in this iteration.

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
- Gradle 8.13 (or use the committed wrapper)

From the repository root:

```bash
gradlew :app:testDebugUnitTest
gradlew :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses the `.debug` application ID suffix, so it can be installed alongside a future release build.

## Security principles

- No service-account credentials or private server secrets in the Android app.
- Firebase user data is scoped by authenticated Firebase UID and protected by owner-only Firestore / Storage rules.
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

## UI and interaction milestones

Earlier alpha notes are retained in the `docs/` directory.
