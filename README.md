# Achivity

**Achivity** is a lightweight, secure, and native Android content-blocking application built in Kotlin. Designed to help users maintain focus and block access to adult and restricted websites system-wide.

## 🚀 Key Features

- **System-Wide Content Blocking:** Seamlessly intercepts and blocks adult and restricted web content across all major browsers.
- **Custom Accessibility Service:** Utilizes Android's Accessibility APIs to monitor URL changes and active window content reliably in real-time.
- **Anti-Uninstall Protection (Device Admin):** Integrates Device Administrator policies to prevent unauthorized tampering or uninstallation attempts.
- **Smart App Whitelisting:** Built-in exception handling and whitelisting specifically tailored for essential financial and utility applications (such as GPay, Navi, etc.) to ensure seamless daily transactions without disruption.
- **Lightweight Architecture:** Engineered with a clean 3-layer structural design focused on low battery consumption and high performance.

---

## 🛠️ Tech Stack

- **Language:** [100% Kotlin](https://kotlinlang.org/)
- **Environment:** Android Studio
- **Core APIs:** Android Accessibility Service, Device Administration API

---

## ⚙️ Required Permissions & Restricted Settings

Because **Achivity** operates as a system-wide focus and content blocker, it requires high-level system permissions that Android typically restricts for standard user apps. During setup, you will need to grant and enable the following settings:

1. **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`):**
   - **Purpose:** Allows the app to read window content and monitor active URLs in real-time to detect restricted or adult domains.
   - **Restricted Setting Note:** On modern Android versions (Android 13+), newly side-loaded applications have accessibility restricted by default. You must manually allow restricted settings for Achivity in your device's **App Info > Menu (three dots) > Allow restricted settings**, and then enable the Accessibility Service.

2. **Device Administrator / Uninstall Protection (`BIND_DEVICE_ADMIN`):**
   - **Purpose:** Grants device administrator privileges to prevent the app from being easily uninstalled or bypassed during active focus sessions.

3. **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`):**
   - **Purpose:** Enables the app to instantly display blocking screens or overlay alerts whenever a restricted domain is accessed.

---

## 📂 Project Structure

```text
Achivity/
├── app/                  # Main application source code and UI logic
├── gradle/               # Gradle wrapper and dependency versions
├── build.gradle.kts      # Project-level build configuration
└── settings.gradle.kts   # Module settings and repositories
