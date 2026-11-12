<img width="1672" height="941" alt="image" src="https://github.com/user-attachments/assets/9bcc43fe-7313-47c6-bf96-bb710ea795e8" />



<p align="center">
  <img src="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/App_icon_nobg.png" width="160" alt="iTube App Icon" />
</p>

# <p align="center">iTube</p>

<p align="center">
  <b>The Unified Android Experience for Video, Shorts & Music</b><br>
  An open-source, ad-free streaming client uniting YouTube discovery with an iOS-styled music ecosystem.
</p>

<p align="center">
  <a href="https://github.com/d0x-dev/iTube/releases"><img src="https://img.shields.io/github/v/release/d0x-dev/iTube?label=Release&style=flat-square&color=FF0000" alt="Latest release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square" alt="License: GPL v3" /></a>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
</p>

---

## Preview

![iTube player preview](https://github.com/user-attachments/assets/eefb57f5-8384-4aa0-8bf2-a47e41231c12)

---

## 📱 Screen Showcase

> [!NOTE]
> *Swipe or scroll horizontally across the slide gallery to explore the native interface.*

| 🎬 **Home Feed** | ⚡ **Video Player** | 📱 **iShorts** | 🎵 **iMusic Feed** | 🎧 **iMusic Player** | 📚 **Library** |
| :---: | :---: | :---: | :---: | :---: | :---: |
| <a href="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iTube_Screen.png"><img src="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iTube_Screen.png" width="240" alt="iTube Home Screen" /></a> | <a href="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iTube_Player.png"><img src="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iTube_Player.png" width="240" alt="iTube Video Player Screen" /></a> | <a href="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iShorts_Screen.png"><img src="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iShorts_Screen.png" width="240" alt="iShorts Screen" /></a> | <a href="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iMusic_Screen.png"><img src="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iMusic_Screen.png" width="240" alt="iMusic Feed Screen" /></a> | <a href="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iMusic_Player.png"><img src="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/iMusic_Player.png" width="240" alt="iMusic Full Player Screen" /></a> | <a href="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/Library_Screen.png"><img src="https://raw.githubusercontent.com/d0x-dev/Storage/main/itube/Library_Screen.png" width="240" alt="Library Screen" /></a> |

---

## ✨ Features

- **Browse & Search**: Explore trending videos, music, channels, playlists, and albums with rich suggestions.
- **Full-Featured Video Player**: Adjustable resolution up to 4K, custom playback speeds (0.25× to 2.0×), live comments, chapters, and background playback.
- **iShorts**: Swipeable vertical video player with long-press 2× fast forward, audio extraction, and quality control.
- **iMusic & Companion Video**: iOS-inspired music player featuring live synchronized lyrics, split history/upcoming queue, audio visualizer, and a seamless switch to companion video mode.
- **Ultra-Fast Parallel Downloader**: Multi-threaded 2 MB HTTP range chunk engine with automatic retry to bypass CDN bandwidth throttles on 5G/Gigabit connections.
- **Customizable Appearance**: Light, Dark, and true AMOLED themes with adaptive color palettes extracted from media artwork.
- **Account Support**: Secure authentication to access subscriptions, liked videos, and playlists.

---

## 📥 Installation

Download the latest release APK from [GitHub Releases](https://github.com/d0x-dev/iTube/releases):

```text
iTube_v{TAG}_signed.apk
```

For instance, download `iTube_v1.0.0_signed.apk` for the `1.0.0` release.

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 36
- JDK 17 or higher

### Build Instructions
```bash
# Clone the repository
git clone https://github.com/d0x-dev/iTube.git
cd iTube

# Build debug APK (Linux/macOS)
./gradlew assembleDebug

# Build debug APK (Windows)
gradlew.bat assembleDebug
```

The output APK will be generated at `app/build/outputs/apk/debug/iTube.apk`.

---

## 🏗️ Architecture & Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 & custom design tokens
- **Audio / Video Core**: [AndroidX Media3](https://developer.android.com/guide/topics/media/media3) (ExoPlayer) with custom `OkHttpDataSource`
- **Networking**: [OkHttp 5](https://square.github.io/okhttp/) & Kotlin Coroutines
- **Image Loading**: [Coil 3](https://coil-kt.github.io/coil/)
- **Target OS**: Android 8.0+ (API level 26+)

---

## 📄 License

iTube is distributed under the [GNU General Public License v3.0](LICENSE).
