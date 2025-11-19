# AI Chat Settings Implementation

## Overview
This document describes the settings system for the AI Chat feature and recent improvements.

## Recent Fixes (2025-11-18)

### 1. Settings Now Apply Automatically ✅

**Problem:** When users changed settings (like disabling "show thinking"), the changes weren't being applied. The model continued with the old settings.

**Solution:** Modified `AiChatViewModel.updateSettings()` to:
- Automatically detect when model, context, GPU, or CPU settings change
- Unload the current model
- Reinitialize with new settings
- Show progress message: "⚙️ Applying new settings and reloading AI model..."

**Code Location:** `app/src/main/java/offgrid/geogram/ai/AiChatViewModel.kt:68-98`

### 2. "Show Thinking" Toggle Now Works ✅

**Problem:** The "show thinking" setting was saved but not used during response generation.

**Solution:** Implemented thinking content filtering:
- Added `filterThinkingContent()` method that removes `<think>`, `<thinking>`, and `<reasoning>` tags
- Modified token streaming to filter thinking content in real-time when `showThinking` is disabled
- Handles incomplete tags during streaming (hides content until closing tag appears)

**Code Location:**
- `AiChatViewModel.kt:258-281` - Token filtering logic
- `AiChatViewModel.kt:380-407` - Filter implementation

### 3. Friendly Robot Icon ✅

**Problem:** The AI chat icon was a generic chat bubble, not a robot.

**Solution:** Created a friendly robot icon with:
- Antenna with circular tip
- Rounded head with friendly circular eyes
- Smiling mouth
- Simple rectangular body
- Two arms
- Power button detail on chest

**Code Location:** `app/src/main/res/drawable/ic_robot.xml`

## Settings Available

### Model Selection
- **qwen3-0.6** (default) - Fast, 400MB
- **gemma3-270m** - Faster, 150MB
- **qwen3-1.7** - Better quality, 1GB

Changing model triggers automatic reinitialization.

### Generation Parameters
- **Max Tokens** (0-2048): Maximum response length
- **Temperature** (0.0-1.0): Creativity level (0.0 = deterministic, 1.0 = creative)

### Hardware Settings
- **Context Size**: 2048, 4096, or 8192 tokens
- **GPU Layers**: Auto, CPU Only, 16/32 layers, All layers
- **CPU Threads**: 1-8 threads for CPU inference

Changing these triggers automatic reinitialization.

### Display Settings
- **Show Thinking**: Toggle to show/hide reasoning tokens
  - When enabled: Shows full AI reasoning process
  - When disabled: Filters out thinking/reasoning content

### System Prompt
- Default: "You are a helpful AI assistant integrated into Geogram, an offline-first messaging app. Be concise, friendly, and helpful. Keep responses brief unless asked for details."
- Can be customized (stored in settings, UI editing coming soon)

## Settings Persistence

Settings are stored in SharedPreferences: `ai_chat_settings`

**Key mappings:**
- `model_name` → modelName
- `max_tokens` → maxTokens
- `context_size` → contextSize
- `temperature` → temperature
- `gpu_layers` → gpuLayers
- `cpu_threads` → cpuThreads
- `show_thinking` → showThinking
- `system_prompt` → systemPrompt

**Code:** `app/src/main/java/offgrid/geogram/ai/AiChatPreferences.kt`

## Architecture

```
AiChatFragment (UI)
    ↓
AiChatViewModel (State & Logic)
    ↓
CactusManager (AI SDK Wrapper)
    ↓
Cactus AI SDK (Model Inference)
```

### Settings Flow

1. User opens settings dialog (`AiSettingsDialog`)
2. User modifies settings and clicks "Save"
3. `AiChatViewModel.updateSettings()` is called
4. Settings are saved to SharedPreferences
5. If model/context/GPU/CPU changed:
   - Current model is unloaded
   - New model is downloaded (if needed)
   - New model is initialized with updated settings
6. Settings are immediately applied to new generations

## Testing

To test the settings:

1. **Open AI Chat** - Click the robot icon in the toolbar
2. **Open Settings** - Click the settings gear icon
3. **Test Show Thinking Toggle:**
   - Enable "Show Thinking"
   - Ask a complex question
   - Observe reasoning tokens (if model supports them)
   - Disable "Show Thinking"
   - Ask another question
   - Reasoning should be hidden

4. **Test Model Change:**
   - Change to a different model
   - Click "Save"
   - Observe: "⚙️ Applying new settings and reloading AI model..."
   - Wait for model to reload
   - New responses use the new model

5. **Test Other Settings:**
   - Adjust temperature, max tokens, etc.
   - These apply immediately to the next generation
   - No reinitialization needed

## Known Limitations

1. **GPU/CPU Parameters:** Currently stored but not passed to Cactus SDK (SDK doesn't support them yet). Architecture is ready for when support is added.

2. **Thinking Tag Detection:** We filter common thinking tags (`<think>`, `<thinking>`, `<reasoning>`). If a model uses different tags, they won't be filtered.

3. **Model Switch Time:** Switching models requires redownload and reinitialization, which can take time depending on model size and network speed.

## Future Improvements

- [ ] Add UI for editing system prompt
- [ ] Support custom thinking tag patterns
- [ ] Show download progress when switching models
- [ ] Add model size indicators in settings
- [ ] Implement model preloading
- [ ] Add temperature presets (Creative, Balanced, Precise)
- [ ] Support for GPU/CPU parameters when SDK adds support

## Debugging

All AI-related logs use the `GPT-` prefix. To filter logs:

```bash
adb logcat | grep GPT
```

Relevant tags:
- `GPT-AiChatViewModel` - Settings, messages, generation
- `GPT-CactusManager` - Model operations, SDK calls
- `GPT-AiChatFragment` - UI interactions

Example log output when settings change:
```
GPT-AiChatViewModel: Settings updated: model=qwen3-0.6, maxTokens=512, temp=0.7, showThinking=false
GPT-AiChatViewModel: Model changed, reinitializing...
GPT-CactusManager: Model unloaded
GPT-CactusManager: Downloading model: qwen3-0.6
GPT-CactusManager: Loading model into memory...
GPT-CactusManager: Model ready: qwen3-0.6
```
