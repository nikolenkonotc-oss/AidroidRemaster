# System Architecture — draft 0.1

## Architectural direction

Aidroid Remaster is a personal-first Android agent with a local-first privacy posture and provider-agnostic AI connectivity.

The first repository iteration intentionally uses one Gradle app module. Code is organized by responsibility so stable boundaries can later become Gradle modules without premature module overhead.

## Logical layers

- `presentation`: Compose screens, navigation, design system.
- `application`: user-facing use cases and orchestration.
- `ai/provider`: provider contracts, adapters, model metadata.
- `ai/routing`: policy-driven model selection.
- `agent`: planning, tool calls, approvals, execution, verification.
- `memory`: conversation history and explicitly managed long-term memory.
- `platform`: Android APIs, SAF, permissions, lifecycle.
- `data`: Room, DataStore, credential storage, repositories.

## Non-negotiable principles

1. Local and cloud models are explicit choices governed by user policy.
2. No silent cloud fallback for private or local-only work.
3. Model output is untrusted input; tool calls are validated before execution.
4. A tool action is not successful until its result is verified.
5. Secrets are never committed to source control or written to ordinary logs.
6. Long-term memory is user-reviewable and user-controllable.
7. Android integrations use official APIs and clear user consent.

## Initial dependency direction

Presentation -> Application -> Domain contracts

Infrastructure implements domain contracts. Domain logic must not depend on Android UI or a specific AI provider.

## Planned package roots

`app.aidroid.remaster.presentation`
`app.aidroid.remaster.application`
`app.aidroid.remaster.domain`
`app.aidroid.remaster.data`
`app.aidroid.remaster.platform`
