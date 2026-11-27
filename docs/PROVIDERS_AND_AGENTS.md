# Connecting models and agents

## Provider support in this iteration

| Provider | API used | Model discovery | Tool calls | Reasoning parameter |
|---|---|---|---|---|
| OpenAI | Responses API | GET /v1/models | Native function tools | reasoning.effort when enabled |
| OpenAI-compatible | POST /v1/chat/completions | GET /v1/models | Function/tool calls when supported by server | reasoning_effort when enabled |
| Anthropic | POST /v1/messages | GET /v1/models | tool_use / tool_result | Adaptive thinking + output_config.effort on current models; legacy budget_tokens for older models |
| Google Gemini | generateContent | GET /v1beta/models | Function calling | thinkingLevel on Gemini 3+; thinkingBudget on Gemini 2.5 and earlier |
| Ollama | OpenAI-compatible chat endpoint | GET /api/tags | Depends on model and Ollama version | Provider/model dependent |

## Connection setup

1. Open Models and tap Add provider.
2. Choose the provider type.
3. Enter its base URL and exact model ID. Use the model discovery button to test connectivity and populate the model list.
4. Enter the API key for hosted services. Leave it empty for a local Ollama server without authentication.
5. Save the provider and set it as active in the Agent section.
6. Choose Fast, Balanced or Deep reasoning. Native reasoning is off by default. Enable it only when the selected model documents support for the provider's reasoning parameters.

Hosted service API keys are created and managed in the relevant provider account. Aidroid stores a supplied key encrypted using Android Keystore. It is not uploaded to Firebase.

## Custom OpenAI-compatible endpoints

Choose OpenAI-compatible API, then enter:
- Base URL, for example https://your-provider.example/v1
- The exact model identifier returned by GET /v1/models
- The API key required by that server

The adapter uses the Chat Completions schema. A server that only implements part of that schema may not support tools or reasoning parameters. Disable the native reasoning option for models that do not document it.

## Ollama on a computer or another device

Run Ollama on the host computer, expose its local API to the trusted LAN, and make sure the phone and host are on the same network. In Aidroid choose Ollama and enter the host's LAN address, such as:

http://192.168.1.20:11434

Then use the model discovery button. Do not enter 127.0.0.1 for another computer: loopback always points to the Android device.

## llama.cpp server and GGUF

A running llama.cpp server exposes OpenAI-compatible endpoints. Choose OpenAI-compatible API, use the server's address (commonly port 8080 by default (or the port configured at startup)), and discover or enter the model ID exposed by /v1/models.

The reasoning toggle is intentionally opt-in because reasoning parameters differ between model families. Gemini 3+ uses thinkingLevel, while Gemini 2.5 and earlier use thinkingBudget. Current Claude models use adaptive thinking with output_config.effort; legacy Claude models use the older thinking budget format.\n\nThis connects to a model already loaded by a server. It does not download a GGUF file or embed the llama.cpp runtime in the Android app. Direct on-device GGUF support is a separate native Android milestone.

## Agent tools

The first runtime provides a safe calculator and current device date/time. The model may request these tools, but the app validates every requested name against the enabled registry and executes only registered functions. The runtime has a five-round tool-call limit per response. No shell command, arbitrary code, or remote action is executed.

MCP servers, app integrations, and a persistent tool execution audit log are not enabled in this iteration. They must be added as explicit connectors with permission scopes and confirmation for consequential actions.


## Agent tools

The Agent workspace includes optional tools. Each connector is opt-in:

- Web search works without API keys or registration through DuckDuckGo and Mojeek public HTML search pages. The agent can call `web_search` for links/snippets and `web_open` to visit a public HTTP/HTTPS page and extract its title, description and readable text. An optional Brave Search key is used only as a fallback if both anonymous sources fail; it is encrypted with Android Keystore.
- Image analysis sends user-selected JPEG, PNG or WebP attachments to the active vision-capable model. It does not scan the gallery automatically.
- File analysis reads only files the user attached through Android's document picker. Text, Markdown, CSV, JSON, XML, HTML, PDF, DOCX, XLSX and PPTX are supported in this iteration. PDF and Office text extraction runs locally before any extracted content is sent to the active model. Extracted content is bounded by size/page/context limits.
- Image generation and editing use the OpenAI Image API through a separately configured OpenAI provider key. The active chat model can be from a different provider. Generated images are stored as normal chat attachments. Editing never overwrites the original.
- Code execution requires a user-configured Piston server URL. Python and JavaScript are the only exposed languages. Each run shows the language and complete code in an approval dialog. The server request applies short run/compile timeouts and memory caps. Use a trusted, isolated Piston service; submitted code and standard input are sent to that server.
- Email drafts and calendar events use Android intents only after a separate approval. Email is opened as a draft and is not sent by Aidroid. Calendar events open the system event form and are not saved until the user confirms inside the calendar app.
- Voice input requests the Android microphone permission only after the user taps the microphone button. On-device speech recognition is preferred when the device provides it; otherwise Android may use the configured recognition service. Voice output uses Android TextToSpeech.

Tool calls are validated against the enabled registry. User approval expires after two minutes. Closing the confirmation dialog is equivalent to declining the action.

## Current limitations

- Image analysis depends on the active provider model supporting vision input. A text-only model cannot analyse attached images.
- Image generation/editing is currently routed through an OpenAI API key even when another provider is selected for chat.
- Office document extraction is text-based; it does not preserve complex layout, formulas, charts or slide visuals. Image-only/scanned PDFs need a separate OCR capability.
- The Piston server is external to Aidroid. It must be controlled by the user or a trusted provider and configured with its own sandbox/container restrictions. Aidroid sends source code only after the in-app approval.
- Email/calendar connectors open system apps; there is no background mail sending, direct calendar database access or silent event creation.
- Search providers are public web interfaces rather than stable APIs. They may change their HTML, throttle requests or show bot checks; the app falls back between sources and reports when none work. Page reading accepts public HTTP/HTTPS HTML or text only, limits response sizes and redirects, rejects local/private IP targets, and does not execute page scripts or download documents. PDF and Office URLs are not parsed by the web reader in this iteration.
- MCP client discovery, scoped plugin APIs and persistent tool execution audit history remain future work.
