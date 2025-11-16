# LinguaLens 📸

[](https://www.google.com/search?q=https://github.com/pklatka/lingualens)
[](https://opensource.org/licenses/MIT)

LinguaLens is a mobile language-learning companion built for Android with Jetpack Compose. Point your camera at any object, and the app will use a powerful on-device machine learning model to detect the object and show you its name - both in your native language and a target language you select.

It's a fun, interactive way to build your vocabulary by associating words with the real world around you.

## ✨ Features

- **Real-Time Object Detection:** Uses an on-device ML model to find and identify objects in the live camera feed.
- **Instant Translation:** Translates the detected object's name into one of several target languages, also using an on-device ML model.
- **Interactive Overlays:** Displays bounding boxes and dual-language labels (native + translated) directly on the camera view.
- **Language Selector:** Easily switch your target translation language from a dropdown menu.
- **Save for Later:** Tap on a detected object to capture the image and translation.
- **Favorites Gallery:** A dedicated screen to browse all your saved image-translations, persisted locally using a **Room** database.
  
## 🛠️ Tech Stack & Architecture

This project is a modern Android application built entirely in **Kotlin** and leverages the latest Jetpack libraries.

- **UI:** **Jetpack Compose** (for a modern, declarative UI).
- **Navigation:** **Compose Navigation** (for handling screen transitions).
- **Camera:** **CameraX** (for a lifecycle-aware camera preview and analysis stream).
- **Machine Learning:** On-device ML models for:
  - Object Detection
  - Translation
- **Database:** **Room** (for persisting saved translations in the "Favorites" list).
- **Permissions:** Built-in **Activity Result APIs** (to handle the camera permission request).
- **Architecture:** Follows recommended Android architecture principles (MVVM-inspired).

## 🚀 Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable version)
- A physical Android device (recommended for camera use) or an emulator
- Android SDK version 26+

### Installation & Build

1.  **Clone the repository:**
```bash
    git clone https://github.com/pklatka/lingualens.git
```

2.  **Open in Android Studio:**
- Open Android Studio.
- Select "Open an existing project".
- Navigate to and select the cloned `lingualens` directory.

3.  **Build & Run:**
- Let Android Studio sync Gradle dependencies.
- Run the app on your physical device or an emulator.
- **Note:** The app will request Camera permission on the first launch. This is required for the core functionality.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
