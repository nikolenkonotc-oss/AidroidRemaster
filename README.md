# Aidroid Remaster

Personal-first Android AI agent. Local-first, provider-agnostic, user-controlled.

## Project status

**Foundation / milestone 0.1.0-alpha.1**

This repository currently contains the Android project skeleton and a minimal Compose launch screen. Provider adapters, agent tools, memory persistence, and local inference are not implemented yet.

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
