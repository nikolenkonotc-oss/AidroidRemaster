# Roadmap

## Complete

- [x] Repository skeleton, CI and Android app entry point
- [x] Aurora design system, navigation shell and visual transition pass
- [x] Local Room-backed multi-chat persistence and existing cloud sync
- [x] Provider contracts and encrypted credential storage
- [x] Provider adapters: OpenAI Responses, OpenAI-compatible chat, Anthropic Messages, Gemini generateContent
- [x] Model discovery for OpenAI-compatible endpoints, Anthropic, Gemini and Ollama
- [x] First agent runtime with per-chat turn serialization and persisted assistant messages
- [x] Safe built-in calculator and current date/time tools
- [x] Provider and agent configuration screens with reasoning-mode selection

## Next

- [ ] Streaming responses and cancel/retry controls
- [ ] Per-model capability discovery and provider-specific reasoning validation
- [ ] On-device GGUF inference through an Android-native llama.cpp runtime
- [ ] MCP client and external tool connectors with user approval
- [ ] Built-in web search and user-consented file/document tools
- [ ] Agent profiles, task runs and inspectable activity log
- [ ] User-controlled short-term and long-term memory
- [ ] Model routing policies and fallback rules (never silently route local-only work to cloud)
- [ ] Multimodal attachment handling and image/document input

See docs/PROVIDERS_AND_AGENTS.md for connection setup and the current support matrix.
