# ChatGPT Browser Companion

An Android browser companion for Samsung Galaxy phones. It is a standalone WebView browser with tabs, navigation, bookmarks, page text extraction, and a user-invoked page handoff to ChatGPT.

## Build the APK

Open this folder in Android Studio and build `app` > `assembleDebug`, or push it to `jlc3718-source/Chatgpt`. The included GitHub Actions workflow builds a debug APK on each push to `main` and publishes it as a workflow artifact.

The app uses the Android System WebView and Android SDK only. It has no subscription, analytics, or third-party automation backend. It does not claim to provide remote control of Samsung Internet or a private ChatGPT connection; Android does not expose those capabilities to a regular app. The app can hand the current page's readable text to ChatGPT through Android's share sheet.

## User controls and privacy

- Sign in to websites inside the app's browser view.
- Browsing data is stored by Android WebView on the device. Clear it from the app's menu.
- Page handoff runs only when the user taps **Share page with ChatGPT**.
- The app does not send credentials, cookies, browsing history, or page content to a service of its own.
- The initial build intentionally does not automate form submissions or clicks.

## Install

Download `app-debug.apk` from the successful `Android APK` workflow's Artifacts section. On the phone, open the APK and allow installation from that source if Android asks.
