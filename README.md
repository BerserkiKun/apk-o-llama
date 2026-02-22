# APK-o-Llama: AI-Powered APK Security Analysis for Burp Suite

[![Burp Suite Extension](https://img.shields.io/badge/Burp%20Suite-Extension-orange)](https://portswigger.net/burp)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue)](https://github.com/berserkikun/apk-o-llama/releases)
[![Ollama](https://img.shields.io/badge/Ollama-Required-yellow)](https://ollama.com)

## 📋 Table of Contents
- [Overview](#overview)
- [Key Highlights](#key-highlights)
- [Deep Burp Suite Integration](#deep-burp-suite-integration)
- [Security Analysis Engine](#security-analysis-engine)
  - [Static Analyzers](#static-analyzers)
  - [Detection Capabilities](#detection-capabilities)
- [AI Integration Architecture](#ai-integration-architecture)
- [Model Configuration](#model-configuration)
- [Installation Guide](#installation-guide)
  - [Prerequisites](#prerequisites)
  - [Method 1: Pre-compiled Installation](#method-1-pre-compiled-installation-recommended)
  - [Method 2: Custom Build Installation](#method-2-custom-build-installation)
  - [Method 3: Standalone CLI Mode](#method-3-standalone-cli-mode)
- [Key Features](#key-features)
  - [Security & Privacy](#security--privacy)
  - [Analysis Capabilities](#analysis-capabilities)
  - [AI-Powered Reporting](#ai-powered-reporting)
  - [Performance Features](#performance-features)
- [Usage Workflow](#usage-workflow)
  - [APK Analysis](#apk-analysis)
  - [AI-Assisted Vulnerability Reporting](#ai-assisted-vulnerability-reporting)
  - [Multi-Finding Batch Processing](#multi-finding-batch-processing)
  - [Result Visualization](#result-visualization)
- [Screenshots](#screenshots)
- [Support Development](#support-development)
- [Report Issues](#report-issues)
- [Community & Feedback](#community--feedback)

## Overview

**APK-o-Llama** is a professional-grade Burp Suite extension that combines static APK security analysis with enhanced analyzers, comprehensive configuration, a professional UI and your local Ollama LLM capabilities. Designed specifically for mobile application security testers and Android bug bounty hunters, this tool transforms traditional static analysis by adding AI-powered vulnerability assessment and report generation directly within Burp Suite's interface.

## Key Highlights

- **6 Specialized Analyzers** — Comprehensive scanning for 70+ security issues
- **Comprehensive APK static analysis** — Decompiled APK scanning for 50+ security issues
- **Local LLM processing** via Ollama — No data leaves your machine, zero API costs
- **AI-generated vulnerability reports** — Professional bug bounty-style write-ups for each finding
- **Multi-finding batch processing** — Analyze multiple vulnerabilities simultaneously
- **Real-time AI status tracking** — Visual feedback for pending/in-progress/completed analysis
- **Click-to-retry interface** — One-click retry for failed or timed-out AI requests
- **Severity-based color coding** — CRITICAL 🔴, HIGH 🟠, MEDIUM 🟡, LOW 🟢
- **Confidence scoring** — Machine-learning based confidence metrics (0-100%)
- **Persistent Configuration** — Save your settings to file between sessions
- **True AI conversation** — Feel the true AI conversation with Auto context-storing to your local machine up to 20 chats.

## Deep Burp Suite Integration

The extension integrates seamlessly into Burp Suite's ecosystem:

- **Dedicated "APK-o-Llama" Tab**: Central dashboard for APK analysis and AI results
- **Split-pane Interface**: Left panel for finding details, right panel for AI-generated reports
- **Configuration Tab** — Full control over Ollama settings, scan parameters, and system status
- **AI Console Tab** — Standalone AI conversation with context retention
- **Sortable Findings Table**: Multi-column sorting by severity, confidence, and AI status
- **Context-Aware UI**: Dynamic button states based on selection and AI request status
- **Progress Tracking** — Real-time progress bar showing "AI Analysis: 3/10 (Failed: 1)"

## Security Analysis Engine

### Static Analyzers

| Analyzer | File Types | Detection Focus |
|----------|------------|-----------------|
| **SecretScanner** | Java, Kotlin, Smali, XML, Config | Hardcoded API keys, passwords, tokens |
| **CryptographyAnalyzer** | Java, Kotlin, Smali | Weak algorithms, ECB mode, hardcoded keys |
| **ManifestAnalyzer** | AndroidManifest.xml | Debuggable apps, backup enabled, exported components |
| **EnhancedManifestAnalyzer** | AndroidManifest.xml | Dangerous permissions, task hijacking, WebView security |
| **BinaryAnalyzer** | Native libs, assets, certificates | Embedded secrets, private keys, certificates |

### Detection Capabilities

**Secrets & Credentials** (CRITICAL/HIGH):
- AWS Access Keys, Google API Keys, Stripe Keys
- OpenAI/ChatGPT API Keys, GitHub Tokens
- Generic passwords and API keys in code
- High-entropy strings near security keywords
- Expanded keyword list (70+ security terms)

**Cryptographic Issues** (HIGH/MEDIUM):
- DES, RC4, MD5, SHA1 usage
- ECB encryption mode
- Hardcoded keys and IVs
- Insecure random number generation (`new random`)

**Manifest Misconfigurations** (CRITICAL/HIGH):
- Debuggable applications in production
- Exported components without permissions
- Cleartext traffic allowed
- Backup enabled exposing sensitive data
- Task hijacking vulnerabilities
- Dangerous permission analysis (20+ permissions)
- Content provider security checks
- WebView security misconfigurations

**Binary Analysis** (CRITICAL/MEDIUM):
- Embedded RSA private keys
- Certificates in binary files
- Security keywords in binary content

## AI Integration Architecture

```text
┌───────────────────────────────────────────────────────────────────┐
│                        Burp Suite Professional                    │
└─────────────────────────────────┬─────────────────────────────────┘
                                  │
┌─────────────────────────────────▼─────────────────────────────────┐
│                     APK-o-Llama Extension                         │
│                                                                   │
│  ┌──────────────────────────────┐    ┌─────────────────────────┐  │
│  │    FileScanner/RuleEngine    │    |  FindingCollector       │  │
│  │  APK decompilation & analysis│    │  Results aggregation    │  │
│  └──────────────────────────────┘    └─────────────────────────┘  │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │              OllamaRequestManager                           │  │
│  │                                                             │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │  │
│  │  │   Queue     │  │   Retry     │  │   Status    │          │  │
│  │  │  Manager    │  │  Scheduler  │  │   Monitor   │          │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘          │  │
│  │                                                             │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │  │
│  │  │ Cancellation│  │ Rate Limit  │  │   Stale     │          │  │
│  │  │   Handler   │  │   Handler   │  │  Request    │          │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘          │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │                    OllamaClient                             │  │
│  │    HTTP client with timeout/retry handling + Cancellation   │  │
│  └─────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────┬─────────────────────────────────┘
                                  │
┌─────────────────────────────────▼─────────────────────────────────┐
│               Ollama HTTP API (localhost:11434)                   │
└─────────────────────────────────┬─────────────────────────────────┘
                                  │
┌─────────────────────────────────▼─────────────────────────────────┐
│                 Local Large Language Model                        │
│                    qwen2.5-coder:7b                               │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │  ✓ All processing happens locally                           │  │
│  │  ✓ No internet connection required                          │  │
│  │  ✓ No API keys or monthly subscriptions                     │  │
│  │  ✓ Zero data exfiltration - 100% private                    │  │
│  └─────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────┘
```

## Model Configuration

APK-o-Llama has a persistent configuration system with UI management with specialized Ollama model optimized for security analysis:

### Default Model
- **Model**: `qwen2.5-coder:7b` - Specialized for code analysis and technical writing
- **Custom Model Support**: Modify `OllamaClient.java` to use any Ollama-compatible model

### Configuration Options
| Parameter | Default | Range | Persistence |
|-----------|---------|-------|-------------|
| **Ollama Endpoint** | `http://localhost:11434` | Any URL | ✅ Saved |
| **Model Name** | `qwen2.5-coder:7b` | Any Ollama model | ✅ Saved |
| **Connect Timeout** | 17,500ms | Configurable | ✅ Saved |
| **Read Timeout** | 52,500ms | Configurable | ✅ Saved |
| **Max Tokens** | 2000 | 128 - 4096 | ✅ Saved |
| **Entropy Threshold** | 4.5 | 0.0 - 8.0 | ✅ Saved |
| **Max File Size** | 10 MB | Configurable | ✅ Saved |
| **Scan Binary Files** | true | Boolean | ✅ Saved |
| **Entropy Detection** | true | Boolean | ✅ Saved |
| **Debug Mode** | false | Boolean | ✅ Saved |

### Model Compatibility
- Supports any Ollama-compatible model
- Test connection button with model list fetch
- Available models displayed in Configuration tab
- Automatic retry with exponential backoff
- Rate limit and timeout handling
- Cold start detection with extended timeouts

## Installation Guide

### Prerequisites

1. **Ollama**: Install and verify Ollama is running
   ```bash
   # Install Ollama (macOS/Linux)
   curl -fsSL https://ollama.com/install.sh | sh
   
   # Start Ollama service
   ollama serve
   
   # Pull recommended model
   ollama pull qwen2.5-coder:7b
   ```

2. **Java**: OpenJDK 21 or higher
   ```bash
   java -version  # Should be 21+
   ```

3. **Burp Suite**: Professional or Community Edition (2025+)

4. **APK Decompiler**: For standalone usage (jadx, apktool recommended) (Optional)

### Method 1: Pre-compiled Installation (Recommended)

1. **Download**: Get the latest `apk-o-llama.jar` from the [Releases page](https://github.com/berserkikun/apk-o-llama/releases)

2. **Install in Burp**:
   ```bash
   Burp Suite → Extender → Extensions
   Click "Add" → Select "Java" → Choose the JAR file
   ```

3. **Verify Installation**:
   - "APK-o-Llama" tab appears in Burp Suite
   - Check Ollama connection status in your browser at 127.0.0.1:11434

### Method 2: Custom Build Installation

For custom modifications and development:

1. **Clone Repository**:
   ```bash
   git clone https://github.com/berserkikun/apk-o-llama.git
   cd apk-o-llama
   ```

2. **Modify Configuration** (Optional):
   - Edit `OllamaClient.java` for different model/timeouts
   - Modify `SecretScanner.java` for custom regex patterns
   - Adjust `EnhancedManifestAnalyzer.java` for custom permission checks

3. **Build**:
   ```bash
   # Compile with dependencies
   ./build.sh
   ```

4. **Install Custom Build**: Load generated JAR into Burp Suite

### Method 3: Standalone CLI Mode

For users who want to run APK-o-Llama from the command line without Burp Suite, or integrate it into automated CI/CD pipelines. [Note: NO AI in CLI]

1. **Download the Standalone JAR** file from release page.

2. **Make it executable** (optional)
   ```bash
   chmod +x apk-o-llama-v1.1.0-standalone.jar
   ```

3. **Create an alias for easy use** (optional)
   ```bash
   # Add to your .bashrc or .zshrc
   alias apk-ollama='java -jar /path/to/apk-o-llama-v1.1.0-standalone.jar'
   ```

4. **Usage Examples**
   **Single APK Analysis**
    ```bash
    # Analyze a decompiled APK directory
    java -jar apk-o-llama-v1.1.0-standalone.jar ./decompiled-apk-folder/

    # With custom output file
    java -jar apk-o-llama-v1.1.0-standalone.jar ./decompiled-apk-folder/ >> results.json
    ```

  **Batch Processing Multiple APKs**
  ```bash
  # Process multiple decompiled APK directories
  for apk in ./decompiled/*/; do
    java -jar apk-o-llama-v1.1.0-standalone.jar "$apk"
  done
  ```

  **CI/CD Integration (GitHub Actions Example)**
  ```
  - name: Run APK-o-Llama Security Scan
    run: |
      java -jar apk-o-llama-v1.1.0-standalone.jar scan ./app-decompiled/ \
      if [ $? -ne 0 ]; then
        echo "Security issues found!"
        exit 1
      fi
  ```

## Key Features

### Security & Privacy
- 🔒 **100% Local Processing**: All AI analysis runs on your machine via Ollama
- 🚫 **Zero Data Exfiltration**: No API calls to external services
- 🔐 **No API Keys Required**: Free local LLM, no monthly subscriptions
- 📁 **Offline Capable**: Works completely offline after model download
- 🛡️ **Enterprise-Ready**: Safe for sensitive/confidential APK analysis
- ⚠️ **Configuration persistence** with local file storage
- ❤️ **Request cancellation**: to prevent data leaks from stuck processes

### Analysis Capabilities
- 📦 **APK Directory Scanning**: Process decompiled APK folder structures
- 🔍 **Multi-Format Support**: Java, Kotlin, Smali, XML, binary files
- 🎯 **Context-Aware Detection**: Pattern + entropy + keyword proximity
- 📊 **Confidence Scoring**: ML-inspired confidence metrics (0-100%)
- 🏷️ **Severity Classification**: CRITICAL, HIGH, MEDIUM, LOW, INFO
- 🔎 **Line-Accurate Reporting**: Exact file and line number identification
- 🧩 **Comment-Aware Filtering**: Skips commented-out false positives

### AI-Powered Reporting
- 🤖 **Automated Vulnerability Reports**: Bug bounty-style write-ups
- 📝 **Structured Format**: Summary → Technical Details → Impact → Steps to Reproduce → Mitigation
- 🎓 **Professional Tone**: HackerOne/Bugcrowd style language
- ⚡ **Batch Processing**: Analyze 10+ findings simultaneously
- 🔄 **Smart Retry**: One-click retry for failed/timeout requests
- 📊 **Progress Tracking**: Visual feedback for AI analysis progress
- 🎨 **Formatted Display**: Clean text formatting with proper line wrapping
- 🤖 **AI Console**: for custom queries with conversation history
- 😺 **Cancellation**: Cancel in-progress AI requests
- 🔥 **Token**: usage estimation

### Performance Features
- ⚙️ **Thread-Safe Architecture**: ConcurrentHashMap, AtomicInteger for thread safety
- 📦 **Priority Queueing**: FIFO with creation-time priority
- ⏱️ **Exponential Backoff**: Smart retry delays (3.5s → 7s → 14s)
- 🧹 **Stale Request Monitoring**: Auto-timeout stuck requests (53s + 10s grace)
- 🔄 **Graceful Shutdown**: Proper cleanup of thread pools
- 📈 **Memory Efficient**: Stream-based file processing for large directories
- 🎯 **Cancellation Support**: Immediate cancellation of in-progress requests

## Usage Workflow

### APK Analysis
1. **Manually Decompile Target APK**:
   ```bash
   jadx -d output_dir target.apk
   # or
   apktool d target.apk -o output_dir
   ```

2. **Launch Burp Suite** → Navigate to "APK-o-Llama" tab

3. **Select Decompiled Directory**:
   - Click "Browse" or paste path
   - Select the decompiled APK output directory

4. **Start Analysis**:
   - Click "Analyze" button
   - Progress bar shows scan status
   - Findings populate table with severity coloring

### AI-Assisted Vulnerability Reporting
1. **Select Findings**: Click row(s) to analyze (multi-select supported)

2. **Generate AI Report**:
   - Click "Ask Ollama" button
   - Each finding receives structured bug bounty report
   - Status column updates in real-time: Pending → In Progress → Completed

3. **View Results**:
   - Click any finding to view details
   - Left panel: Technical details, evidence, confidence
   - Right panel: AI-generated vulnerability report

4. **Retry Failed Requests**:
   - Failed/timeout requests show red "Click to Retry"
   - Single-click to retry with exponential backoff
    
5. **Dedicated AI Console**
   - A dedicated separate AI Console for AI conversation.
   - Switch to "AI Console" tab for custom queries
   - Ask questions about findings or general security topics
   - Cancel long-running console requests
   - Clear response area with confirmation

6. **Cancel In-Progress Requests**:
   - Select findings with Pending/In Progress status
   - Click "Cancel" to abort analysis

### Multi-Finding Batch Processing
- Select 10+ findings simultaneously
- Submit single batch request
- Track progress via progress bar: `AI Analysis: 3/10 (Failed: 1)`
- Retry all failed with one click
- Cancel in-progress requests

### Configuration Management
1. **Navigate to Configuration Tab**
2. **Configure Ollama Settings**:
   - Set endpoint, model, timeouts
   - Click "Test Connection" to verify and fetch available models
3. **Adjust Scan Parameters**:
   - Entropy threshold, max file size
   - Toggle binary scanning and entropy detection
4. **Save Configuration**:
   - Settings persist in `apkollama.config` file
   - Automatically loaded on next startup

### Version Checking
- Automatic background check on startup
- "New Releases" button turns **yellow** when update available
- Click to view latest version and open GitHub releases page

### Result Visualization
| Severity | Color | Icon | Description |
|----------|-------|------|-------------|
| **CRITICAL** | 🔴 Red | █▓▒░ CRITICAL ░▒▓█ | Immediate attention required |
| **HIGH** | 🟠 Orange | ▓▒░ HIGH ░▒▓ | Serious vulnerability |
| **MEDIUM** | 🟡 Yellow | ▒░ MEDIUM ░▒ | Moderate risk |
| **LOW** | 🟢 Green | ░ LOW ░ | Minor issue |

**Confidence Visualization**:
```
██████░░░░ 60% - Potential false positive
████████░░ 80% - Likely valid
██████████ 90%+ - Confirmed
```

### AI Status Indicators 

| Status | Color | Behavior |
|--------|-------|----------|
| **Not Requested** | Default | - |
| **Pending** | White | Waiting in queue |
| **In Progress** | White | Processing |
| **Completed** | White | ✓ Done |
| **Failed** | 🔴 Red | Click to retry |
| **Timeout** | 🔴 Red | Click to retry |
| **Rate Limited** | 🔴 Red | Click to retry |
| **Cancelled** | Gray | User cancelled |

## Screenshots
<img width="1470" height="921" alt="Screenshot 2026-02-21 at 8 53 31ΓÇ»PM" src="https://github.com/user-attachments/assets/dad15a17-0219-47b6-b943-be98595d0bf5" />
<img width="1470" height="924" alt="Screenshot 2026-02-21 at 9 28 35ΓÇ»PM" src="https://github.com/user-attachments/assets/e8a3252e-b5fd-4d97-a434-8e29610bd909" />
<img width="1470" height="925" alt="Screenshot 2026-02-21 at 8 54 04ΓÇ»PM" src="https://github.com/user-attachments/assets/16459a6e-6af9-480e-818d-006c9da631a3" />
<img width="1470" height="888" alt="Screenshot 2026-02-21 at 9 07 12ΓÇ»PM" src="https://github.com/user-attachments/assets/cf945d69-d25a-47f2-a5c4-df4d7143574b" />
<img width="1470" height="887" alt="Screenshot 2026-02-21 at 9 07 42ΓÇ»PM" src="https://github.com/user-attachments/assets/385a317e-8c39-48ae-b1ee-7b28d52d3d43" />
<img width="1470" height="888" alt="Screenshot 2026-02-21 at 9 08 06ΓÇ»PM" src="https://github.com/user-attachments/assets/8344ef8f-abd0-4b0a-b94e-ddc3a80e8fbd" />


## Support Development

If APK-o-Llama helps your mobile security testing, consider supporting its development:

**⭐ Star the Repository**: Show your support by starring the project on GitHub!

**Support Links**:
- 💰 **PayPal**: [PayPal](https://www.paypal.com/ncp/payment/7Y3836GETVF94)

Your support helps maintain the project, add new analyzers, and improve AI integration.

---

## Report Issues

Found a bug? Have a feature request?

**Bug Reports**:
- Include Burp Suite version
- APK decompiler used (jadx/apktool)
- Ollama version (`ollama --version`)
- Java version
- Steps to reproduce
- Error logs from Burp's Extender → Output/Errors

**Feature Requests**:
- New analyzer suggestions
- Additional regex patterns
- AI prompt improvements
- UI/UX enhancements

## Community & Feedback

APK-o-Llama is built for the mobile security community. Your feedback shapes its future:

- 💡 **Feature Ideas**: What analyzers do you need?
- 🐛 **Bug Reports**: Help make it more stable
- 📚 **Documentation**: What's unclear?
- 🔧 **Contributions**: PRs welcome!

---

<div align="center">

**Built with ❤️ by [BerserkiKun](https://github.com/berserkikun)**

[![GitHub Stars](https://img.shields.io/github/stars/berserkikun/apk-o-llama?style=social)](https://github.com/berserkikun/apk-o-llama/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/berserkikun/apk-o-llama)](https://github.com/berserkikun/apk-o-llama/issues)
[![GitHub Forks](https://img.shields.io/github/forks/berserkikun/apk-o-llama?style=social)](https://github.com/berserkikun/apk-o-llama/network/members)

**⭐ Star this repo if you find it useful for mobile security testing!**

</div>
