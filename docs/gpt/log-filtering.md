# AI Chat Log Filtering Guide

All AI-related log messages are now prefixed with **GPT-** for easy filtering.

## Log Tags

All AI chat components use tags starting with "GPT-":

- `GPT-CactusManager` - AI SDK operations (download, initialization, generation)
- `GPT-AiChatViewModel` - Chat state management, message handling
- `GPT-AiChatFragment` - UI interactions, user input

## Filter All AI Logs

To see **only** AI-related logs:

```bash
adb logcat | grep GPT
```

## Filter by Component

### CactusManager (Model operations)
```bash
adb logcat | grep GPT-CactusManager
```

Example output:
```
GPT-CactusManager: Status: DOWNLOADING - Downloading model: qwen3-0.6
GPT-CactusManager: Model download complete: qwen3-0.6
GPT-CactusManager: Status: LOADING - Loading model: qwen3-0.6
GPT-CactusManager: Model initialized: qwen3-0.6 (context: 4096)
GPT-CactusManager: Status: READY - Model ready: qwen3-0.6
```

### AiChatViewModel (State & Messages)
```bash
adb logcat | grep GPT-AiChatViewModel
```

Example output:
```
GPT-AiChatViewModel: Starting AI initialization...
GPT-AiChatViewModel: AI initialization complete
GPT-AiChatViewModel: Sending message: Hello
GPT-AiChatViewModel: Response generated: 245 characters
```

### AiChatFragment (UI Events)
```bash
adb logcat | grep GPT-AiChatFragment
```

Example output:
```
GPT-AiChatFragment: AI Chat fragment created
GPT-AiChatFragment: Sending message: Hello
GPT-AiChatFragment: File selected: document.pdf
GPT-AiChatFragment: AI Settings clicked
```

## Combined Filters

### All GPT logs with timestamps
```bash
adb logcat -v time | grep GPT
```

### GPT logs with priority (Info, Debug, Error)
```bash
adb logcat GPT-*:I *:S
```

### Save GPT logs to file
```bash
adb logcat | grep GPT > gpt-logs.txt
```

### Real-time monitoring with colors (if supported)
```bash
adb logcat | grep --color=always GPT
```

## Log Levels

The AI components use these log levels:

- **Log.d()** - Debug information (status updates)
- **Log.i()** - Important milestones (download complete, model ready)
- **Log.e()** - Errors (download failed, initialization error)

## Filter by Log Level

### Errors only
```bash
adb logcat GPT-*:E *:S
```

### Info and above (no debug)
```bash
adb logcat GPT-*:I *:S
```

### All levels
```bash
adb logcat GPT-*:V *:S
```

## Common Log Sequences

### Normal Initialization
```
GPT-AiChatViewModel: Starting AI initialization...
GPT-CactusManager: Status: DOWNLOADING - Downloading model: qwen3-0.6
GPT-CactusManager: Model download complete: qwen3-0.6
GPT-CactusManager: Status: LOADING - Loading model: qwen3-0.6
GPT-CactusManager: Model initialized: qwen3-0.6 (context: 4096)
GPT-CactusManager: Status: READY - Model ready: qwen3-0.6
GPT-AiChatViewModel: AI initialization complete
```

### Message Generation
```
GPT-AiChatViewModel: Sending message: Tell me a joke
GPT-CactusManager: Status: GENERATING - Generating response...
GPT-CactusManager: Status: READY - Response complete
GPT-AiChatViewModel: Response generated: 156 characters
```

### Error Scenario
```
GPT-AiChatViewModel: Starting AI initialization...
GPT-CactusManager: Status: DOWNLOADING - Downloading model: qwen3-0.6
GPT-CactusManager: Error downloading model
GPT-CactusManager: Status: ERROR - Download failed: Network error
GPT-AiChatViewModel: Failed to initialize AI
```

## Tips

1. **Start logging before opening AI chat**:
   ```bash
   adb logcat | grep GPT > ai-session.log &
   # Then use the app
   # Ctrl+C to stop
   ```

2. **Monitor specific events**:
   ```bash
   adb logcat | grep -E "GPT.*download|GPT.*complete|GPT.*error"
   ```

3. **Check if AI is ready**:
   ```bash
   adb logcat | grep "Status: READY"
   ```

4. **Debug slow responses**:
   ```bash
   adb logcat -v time | grep -E "GPT.*Generating|GPT.*complete"
   ```

## Quick Commands

### Start monitoring (run in terminal)
```bash
# Basic
adb logcat | grep GPT

# With timestamps
adb logcat -v time | grep GPT

# Save to file
adb logcat | grep GPT | tee gpt-$(date +%Y%m%d-%H%M%S).log

# Clear old logs first
adb logcat -c && adb logcat | grep GPT
```

### One-time status check
```bash
# Last 100 GPT logs
adb logcat -d | grep GPT | tail -100

# Check current status
adb logcat -d | grep "GPT.*Status" | tail -5
```

---

**Pro Tip**: Keep a terminal open with `adb logcat | grep GPT` running while testing the AI chat feature. You'll see exactly what's happening behind the scenes!
