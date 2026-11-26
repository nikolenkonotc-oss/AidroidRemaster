# Contributing

Thanks for your interest in Aidroid Remaster.

## Before contributing

- Keep changes focused and explain the user-visible impact.
- Do not commit API keys, signing keys, local config, or personal data.
- Do not add a cloud provider as an implicit fallback.
- New tools must define typed input/output, validation, risk level, approval requirements, and verifiable results.
- Include tests for domain logic where practical.

## Development

Use JDK 17 and Android SDK Platform 36. Run:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

## Pull requests

Describe the change, testing performed, privacy implications, and any known limitations.
