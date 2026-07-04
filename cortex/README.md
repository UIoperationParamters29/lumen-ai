# Cortex

**Your keys. Your mind.**

Cortex is a BYOK (Bring Your Own Key) AI chat application for Android, built with 100% native Kotlin and Jetpack Compose. Stream thoughts, search the web, and stay in flow.

![Cortex](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

## Features

- **Multi-Gateway Support** — Connect to any OpenAI-compatible API (OpenAI, z.ai, DeepSeek, custom gateways). Switch between gateways per chat.
- **Token Streaming** — Watch responses arrive in real-time, token by token. Smooth, fast, reliable.
- **Thinking Panels** — See the model's reasoning process with collapsible thinking panels. Supports `reasoning_content` from DeepSeek, GLM, and other reasoning models.
- **Web Search** — Ground responses with live web results. Toggle per-message. Supports:
  - DuckDuckGo (free, no API key, works out of the box)
  - Exa (neural search)
  - Firecrawl (deep crawl)
  - SearXNG (self-hosted)
- **Markdown Rendering** — Full markdown support with syntax-highlighted code blocks, tables, lists, blockquotes, and links.
- **Secure Storage** — API keys stored with Android Keystore-backed EncryptedSharedPreferences.
- **Custom Markdown Engine** — Pure Compose markdown renderer, no WebView, maximum performance.
- **z.ai-Inspired Dark Theme** — Futuristic blue/cyan/green orb aesthetic on pure dark backgrounds.

## Download

Download the latest APK from the [Releases page](../../releases).

### Installation

1. Download the `.apk` file to your Android device
2. Open the file (you may need to enable "Install from unknown sources" in Settings)
3. Tap **Install**
4. Open **Cortex** and add your gateway in Settings

## Quick Start

1. **Add a Gateway** — Go to Settings → Gateways → Add Gateway
   - Name: `My Gateway`
   - Base URL: `https://api.openai.com/v1` (or your OpenAI-compatible endpoint)
   - API Key: `sk-...`
   - Tap **Test Connection** to verify
2. **Start Chatting** — Tap **New Chat** on the home screen
3. **Toggle Web Search** — Tap the globe icon in the chat top bar
4. **Switch Models** — Tap the tune icon to pick from available models
5. **View Thinking** — Tap the psychology icon to show/hide reasoning panels

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 100% |
| UI | Jetpack Compose + Material 3 |
| Networking | OkHttp 4.12 (manual SSE parsing) |
| Storage | JSON file store + EncryptedSharedPreferences |
| Serialization | kotlinx.serialization |
| Markdown | Custom Compose renderer (no WebView) |
| HTML Parsing | Jsoup (for DDG search) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

## Build from Source

```bash
git clone https://github.com/UIoperationParamters29/lumen-ai.git
cd lumen-ai
./gradlew assembleRelease
```

The APK will be at `app/build/outputs/apk/release/app-release.apk`.

## Project Structure

```
app/src/main/kotlin/com/cortex/app/
├── CortexApp.kt              # Application class, DI setup
├── MainActivity.kt           # Single-activity Compose host
├── data/
│   ├── model/                # Data models (Gateway, Chat, Message, API types)
│   ├── store/                # FileStore — JSON file persistence
│   ├── prefs/                # SettingsStore — encrypted preferences
│   ├── remote/               # GatewayClient (SSE streaming), WebSearchProvider
│   └── repo/                 # ChatRepository, GatewayRepository, SettingsRepository
├── ui/
│   ├── theme/                # Color, Type, Theme (z.ai dark palette)
│   ├── nav/                  # Navigation graph
│   ├── chatlist/             # Chat list screen + ViewModel
│   ├── chat/                 # Chat screen, message bubbles, thinking panel
│   ├── settings/             # Settings screen (gateways, web search, behavior)
│   └── components/           # MarkdownText, CodeBlock (custom Compose renderer)
```

## Configuration

### Web Search Providers

| Provider | API Key Required | Notes |
|----------|-----------------|-------|
| DuckDuckGo | No | Free, HTML scraping, works immediately |
| Exa | Yes | Neural search, high quality |
| Firecrawl | Yes | Deep crawl + extract |
| SearXNG | No (instance URL) | Self-hosted meta-search |

### Supported Gateways

Any OpenAI-compatible API that supports:
- `GET /v1/models` — list available models
- `POST /v1/chat/completions` — chat with streaming (`stream: true`)

Tested with:
- z.ai gateway (`api.gateway.orgn.com`)
- OpenAI
- DeepSeek
- Custom OpenAI-compatible gateways

## Privacy

- All API keys are stored locally with Android Keystore encryption
- No telemetry, no analytics, no tracking
- Web search queries go directly to the configured provider
- Chat history is stored locally on-device

## License

MIT License — see [LICENSE](LICENSE).

## Credits

Built with native Kotlin + Jetpack Compose. Inspired by z.ai's clean, minimal aesthetic.
