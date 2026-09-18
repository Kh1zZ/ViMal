# ViMal 🔊🎬

> **Video Audio Loudness Normalizer for Android**  
> Pure Kotlin • Offline & Private • ITU-R BS.1770-4 / EBU R128 • Zero Video Re-encoding

[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B-brightgreen.svg?style=flat-square)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat-square)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.01-blue.svg?style=flat-square)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2F%20MVI-orange.svg?style=flat-square)](https://developer.android.com/topic/architecture)
[![Build](https://img.shields.io/badge/Build-Passing%20(53s)-success.svg?style=flat-square)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg?style=flat-square)](LICENSE)

---

## 📖 Overview

**ViMal** is a native, offline Android utility designed to standardize and normalize audio loudness across video files. Powered by an internal **pure Kotlin implementation of ITU-R BS.1770-4 / EBU R128**, ViMal eliminates the annoying volume disparity between different video sources and social media platforms—without needing an internet connection and without degrading video quality.

### 🌟 Why ViMal?
Most mobile video editors either:
1. Re-encode the entire video, causing quality degradation and slow export times.
2. Rely on simple peak normalization, which doesn't reflect actual perceived human loudness (LUFS).
3. Send video data to external servers.

**ViMal fixes this:**
- **Zero Video Re-encoding (Stream Copy)**: The original video track is preserved bit-for-bit via Android native `MediaExtractor` and `MediaMuxer`. Only the audio stream is calibrated and re-muxed.
- **True Perceptual Loudness (LUFS)**: Uses K-weighting pre-filters, RLB weighting, and gated blocks compliant with EBU R128 standards.
- **100% Offline & Private**: All processing happens entirely on your device hardware using hardware-accelerated `MediaCodec`.

---

## ✨ Features

- 🎯 **Target Platform Presets**:
  - **YouTube**: `-14.0 LUFS` (True Peak max: `-1.0 dBTP`)
  - **TikTok**: `-16.0 LUFS` (True Peak max: `-1.0 dBTP`)
  - **Instagram Reels / Stories**: `-14.0 LUFS` (True Peak max: `-1.0 dBTP`)
  - **WhatsApp Status**: `-16.0 LUFS` (Optimized for WhatsApp compression)
  - **Custom Target**: Fine-tune loudness target between `-30.0 LUFS` and `-6.0 LUFS`.
- ⚡ **2-Pass Normalization Pipeline**:
  - **Pass 1 (Analyze)**: Scans audio frames, measures integrated loudness (LUFS), and detects peak amplitude.
  - **Pass 2 (Calibrate & Mux)**: Computes required linear gain, applies sample scaling, encodes audio, and muxes back with original video.
- 🎨 **Modern Minimalist UI**:
  - Built with **Jetpack Compose** & **Material 3**.
  - Sleek dark-tech aesthetic with high-contrast typography and subtle micro-interactions.
  - Real-time progress breakdown (*Analyzing audio...* → *Applying gain...* → *Muxing video...*).
- 🌐 **Bi-lingual Support**: English & Bahasa Indonesia (supports Android 13+ per-app language preferences).

---

## 🏗️ Architecture

ViMal follows **Clean Architecture** principles structured as a multi-module Gradle project:

```
ViMal/
├── core/
│   ├── domain/               # Pure Kotlin business entities, use cases & strategy interfaces
│   │   ├── model/            # VideoInfo, LoudnessPreset, NormalizeProgress, NormalizeResult
│   │   ├── strategy/         # VideoProcessingStrategy (CopyStrategy & TranscodeStrategy)
│   │   └── usecase/          # GetVideoInfoUseCase, NormalizeVideoUseCase
│   ├── data/                 # Data layer & audio/video processing engine
│   │   ├── audio/            # EbuR128Calculator (Pure Kotlin ITU-R BS.1770-4 engine)
│   │   │                     # AudioNormalizer (MediaCodec 2-Pass pipeline)
│   │   ├── video/            # VideoCopyHelper (MediaExtractor + MediaMuxer stream copy)
│   │   │                     # OutputFileManager (MediaStore & Scoped Storage manager)
│   │   └── repo/             # MediaNormalizerRepositoryImpl
│   └── ui/                   # Design system & reusable Compose components
│       ├── theme/            # Color, Type, Theme tokens
│       └── components/       # PresetSelector, NormalizerProgressBar, VideoMetadataCard, DashedDropZone
├── feature/
│   └── normalizer/           # Feature UI & MVI presentation layer
│       ├── NormalizerUiState.kt & NormalizerEvent.kt
│       ├── NormalizerViewModel.kt
│       └── NormalizerScreen.kt
└── app/                      # Application orchestrator, navigation graph & manifest
```

---

## 🛠️ Tech Stack & Requirements

| Specification | Details |
|---|---|
| **Min SDK** | Android 10 (API 29) |
| **Target SDK** | Android 15 (API 35) |
| **JDK** | Java 17 |
| **Kotlin** | 2.0.21 |
| **UI Toolkit** | Jetpack Compose (BOM 2024.10.01) |
| **Architecture** | MVI (Model-View-Intent) + Clean Architecture |
| **Media APIs** | Android MediaCodec, MediaExtractor, MediaMuxer, AudioTrack |
| **Dependency Injection**| Manual Constructor Injection / Factory Pattern |

---

## 🚀 Getting Started & Local Build

### Prerequisites
- Android Studio Ladybug (2024.2+) or Android Command Line Tools
- JDK 17 installed and set to `JAVA_HOME`
- Android SDK Platforms `34` and `35`

### 1. Clone the Repository
```bash
git clone https://github.com/Kh1zZ/ViMal.git
cd ViMal
```

### 2. Run Local Unit Tests (Fast & Lightweight)
Untuk pengembangan lokal, kamu hanya perlu menjalankan debug unit test (memakan waktu ~4 detik):
```powershell
.\gradlew.bat test
```

### 3. Automated Cloud Release Build (GitHub Actions)
Build release APK (optimasi R8, minifikasi kode, dan signing) dilakukan secara otomatis di GitHub Actions setiap kali ada `git push` ke branch `main` atau tag versi (`v*`).

Kamu tidak perlu build release secara lokal di komputer:
```bash
git push origin main
```
Setelah push, download file APK release yang sudah di-sign langsung dari tab **Actions** atau halaman **Releases** di GitHub!

---

## 🔐 Release Signing & CI/CD

ViMal's `app/build.gradle.kts` is configured to support both local release builds and automated GitHub Actions CI/CD releases.

### Local Signing Fallback
If no production release keystore is supplied, the local release build automatically signs the APK using Android's standard debug key so that developers can immediately install and test the optimized release binary without manual key creation.

### Production Release Signing
To sign release builds with your custom production key:
1. Generate your release keystore:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias vimal -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Place `release.keystore` in the project root (automatically git-ignored) or set environment variables:
   - `KEYSTORE_PATH`: Path to `.keystore` or `.jks` file
   - `KEYSTORE_PASSWORD`: Keystore password
   - `KEY_ALIAS`: Key alias name
   - `KEY_PASSWORD`: Key password

---

## 🗺️ Roadmap

- [x] **V1 MVP**:
  - [x] Pure Kotlin EBU R128 / ITU-R BS.1770-4 loudness calculator
  - [x] 2-Pass MediaCodec audio normalization engine
  - [x] Lossless video stream copy via MediaMuxer
  - [x] Standard presets (YouTube, TikTok, Instagram, WhatsApp, Custom)
  - [x] Jetpack Compose Material 3 UI with dark-tech theme
  - [x] Dual language support (EN / ID)
  - [x] R8 Proguard minification (~2.6 MB release APK)
- [ ] **Phase 2**:
  - [ ] Interactive audio waveform preview
  - [ ] Batch processing (multiple videos simultaneously)
  - [ ] Standalone audio export (normalize MP3 / M4A / WAV directly)
- [ ] **Phase 3**:
  - [ ] FFmpeg-Kit fallback transcode strategy for exotic video/audio containers (MKV, AVI, FLAC, AC3)

---

## 📄 License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
