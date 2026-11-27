# System Architecture — provider and agent iteration

## Architectural direction

Aidroid Remaster is a personal-first Android agent with a local-first privacy posture and provider-agnostic AI connectivity. The Compose shell remains in the existing application module while provider, agent and platform responsibilities live in dedicated packages.

## Implemented packages

- ai/AgentContracts.kt: provider types, normalized messages, tool calls, model metadata and reasoning modes.
- ai/ProviderManager.kt: provider configuration, active model and agent preferences.
- ai/SecureCredentialStore.kt: API-key encryption with AES-GCM and Android Keystore.
- ai/ProviderHttpClient.kt: adapters for OpenAI Responses, OpenAI-compatible Chat Completions, Anthropic Messages, Gemini generateContent and Ollama model discovery.
- ai/AgentRunner.kt: conversation context assembly, sequential per-chat turns, bounded tool-call loop and assistant-message persistence.
- ai/BuiltInTools.kt: allow-listed arithmetic and device-local date/time tools.
- ai/ProviderScreens.kt: Aurora-themed provider and agent settings screens.

## Provider architecture

The internal contract normalizes messages, tool calls, model IDs and reasoning modes. Each provider adapter owns its authentication headers, endpoint paths, request schema and response parsing. The OpenAI-compatible adapter is intentionally separate from native adapters because custom servers do not all implement every OpenAI feature identically.

Local server connectivity:
- Ollama model discovery uses /api/tags; inference uses its OpenAI-compatible /v1/chat/completions endpoint.
- llama.cpp server uses its OpenAI-compatible /v1/models and /v1/chat/completions endpoints.
- A local HTTP endpoint is accepted only for loopback or private/local network hosts. Cloud endpoints must use HTTPS.
- On Android, 127.0.0.1 means the phone itself. A server on another computer needs that computer's LAN address and must be reachable over the same network.

## Agent execution

1. Save a user message to Room immediately.
2. Load a bounded recent conversation context and the selected provider.
3. Send normalized tool definitions to the provider.
4. Validate every returned tool name against the enabled local registry.
5. Execute only registered local tools; append tool results to the in-memory turn.
6. Stop after a bounded number of tool rounds and persist the final assistant message to Room.
7. Queue normal chat synchronization through the existing repository.

Tool output is treated as untrusted data. There is no shell, arbitrary code execution, filesystem write, or automatic URL-fetch tool in this iteration. External actions require a separately designed connector and approval boundary.

## Security and privacy

- Provider API keys are encrypted with an AES-GCM key held by Android Keystore.
- API keys are not stored in Room, chat history, Firebase, or app logs.
- Local HTTP is limited in provider configuration to loopback/private/local network endpoints.
- There is no automatic cloud fallback from a local provider.
- Provider choice is explicit and user-controlled.

## Current limitations

- Chat generation currently uses complete HTTP responses, not token streaming.
- The local connection option targets an Ollama or llama.cpp HTTP server. Loading GGUF directly inside the Android process requires a separate native inference integration and is not included yet.
- Attachments remain stored locally; file and image content is not yet sent to providers.
- Built-in tools are limited to safe arithmetic and current device date/time. MCP, web search, app integrations, external REST tools, approval dialogs and execution audit history remain planned.
- Reasoning effort is provider/model dependent. Native reasoning is opt-in per provider/model to avoid sending unsupported parameters. Claude and Gemini adapters use model-family-specific reasoning controls and preserve native thought/signature blocks through tool cycles.

## References

- OpenAI Responses API: https://developers.openai.com/api/docs/guides/migrate-to-responses
- OpenAI function calling: https://developers.openai.com/api/docs/guides/function-calling
- Anthropic Messages API: https://platform.claude.com/docs/en/api/messages
- Gemini API: https://ai.google.dev/api/generate-content
- Ollama API: https://docs.ollama.com/api
- llama.cpp server: https://github.com/ggml-org/llama.cpp/tree/master/tools/server

## Planned package roots

app.aidroid.remaster.ai
app.aidroid.remaster.agent
app.aidroid.remaster.application
app.aidroid.remaster.domain
app.aidroid.remaster.data
app.aidroid.remaster.platform
