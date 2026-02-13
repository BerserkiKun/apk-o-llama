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

**APK-o-Llama** is a professional-grade Burp Suite extension that combines static APK security analysis with local Ollama LLM capabilities. Designed specifically for mobile application security testers and Android bug bounty hunters, this tool transforms traditional static analysis by adding AI-powered vulnerability assessment and report generation directly within Burp Suite's interface.

## Key Highlights

- **Comprehensive APK static analysis** — Decompiled APK scanning for 50+ security issues
- **Local LLM processing** via Ollama — No data leaves your machine, zero API costs
- **AI-generated vulnerability reports** — Professional bug bounty-style write-ups for each finding
- **Multi-finding batch processing** — Analyze multiple vulnerabilities simultaneously
- **Real-time AI status tracking** — Visual feedback for pending/in-progress/completed analysis
- **Click-to-retry interface** — One-click retry for failed or timed-out AI requests
- **Severity-based color coding** — CRITICAL 🔴, HIGH 🟠, MEDIUM 🟡, LOW 🟢
- **Confidence scoring** — Machine-learning based confidence metrics (0-100%)

## Deep Burp Suite Integration

The extension integrates seamlessly into Burp Suite's ecosystem:

- **Dedicated "APK-o-Llama" Tab**: Central dashboard for APK analysis and AI results
- **Split-pane Interface**: Left panel for finding details, right panel for AI-generated reports
- **Sortable Findings Table**: Multi-column sorting by severity, confidence, and AI status
- **Context-Aware UI**: Dynamic button states based on selection and AI request status

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

**Cryptographic Issues** (HIGH/MEDIUM):
- DES, RC4, MD5, SHA1 usage
- ECB encryption mode
- Hardcoded keys and IVs
- Insecure random number generation

**Manifest Misconfigurations** (CRITICAL/HIGH):
- Debuggable applications in production
- Exported components without permissions
- Cleartext traffic allowed
- Backup enabled exposing sensitive data
- Task hijacking vulnerabilities

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
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │                    OllamaClient                             │  │
│  │    HTTP client with timeout/retry handling                  │  │
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

APK-o-Llama is pre-configured with specialized Ollama model optimized for security analysis:

### Default Model
- **Model**: `qwen2.5-coder:7b` - Specialized for code analysis and technical writing
- **Custom Model Support**: Modify `OllamaClient.java` to use any Ollama-compatible model

### Configuration Options
| Parameter | Default | Range |
|-----------|---------|-------|
| **Temperature** | 0.7 | 0.0 - 2.0 |
| **Max Tokens** | 2000 | 128 - 4096 |
| **Connect Timeout** | 17,500ms | Configurable |
| **Read Timeout** | 52,500ms | Configurable |
| **Max Retries** | 3 | 0 - 10 |
| **Concurrent Requests** | 1 | 1 - 10 (modifiable) |

### Model Compatibility
- Supports any Ollama-compatible model
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

## Key Features

### Security & Privacy
- 🔒 **100% Local Processing**: All AI analysis runs on your machine via Ollama
- 🚫 **Zero Data Exfiltration**: No API calls to external services
- 🔐 **No API Keys Required**: Free local LLM, no monthly subscriptions
- 📁 **Offline Capable**: Works completely offline after model download
- 🛡️ **Enterprise-Ready**: Safe for sensitive/confidential APK analysis

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

### Multi-Finding Batch Processing
- Select 10+ findings simultaneously
- Submit single batch request
- Track progress via progress bar: `AI Analysis: 3/10 (Failed: 1)`
- Retry all failed with one click
- Cancel in-progress requests

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

## Screenshots
<img width="1470" height="923" alt="Screenshot 2026-02-12 at 9 20 41GÇ»PM" src="https://github.com/user-attachments/assets/bf5a2661-0d10-4d85-a71b-e37ced63530f" />
<img width="1469" height="923" alt="Screenshot 2026-02-12 at 9 22 58GÇ»PM" src="https://github.com/user-attachments/assets/acd77dbf-5543-41d1-a106-ff9fe6ee289f" />

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
