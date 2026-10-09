
# PauseAI — AI-Powered Scam Detection and Digital Safety

PauseAI is an Android application designed to help users identify potential scams, suspicious links, and fraudulent payment messages through AI-powered notification analysis.

## Problem Statement

Online scams and fraudulent messages are increasingly common. People may receive fake payment confirmations, suspicious links, and messages designed to steal personal or financial information. Users need a convenient way to recognize potential threats before interacting with them.

## Our Solution

PauseAI analyzes supported Android notifications and identifies potentially suspicious messages using AI-based scam detection and URL risk analysis. When a high-risk notification is detected, the app can alert the user so they can take extra care before acting.

## Key Features

- **AI Scam Detection:** Analyzes notification text to identify potential scam messages.
- **High-Risk Alerts:** Warns users when suspicious activity is detected.
- **Link Risk Analysis:** Checks URLs for potentially dangerous characteristics.
- **VirusTotal Integration:** Supports checking URLs against VirusTotal threat intelligence.
- **Payment Safety Warnings:** Encourages users to independently verify suspicious payment claims.
- **Scam History:** Helps users review previously analyzed notifications.
- **Protection Controls:** Provides settings for protection and link/payment safety features.
- **Notification Filtering:** Filters certain notification summaries to reduce duplicate or irrelevant analysis.

## Technology Stack

- **Frontend:** Flutter and Dart
- **Android Integration:** Kotlin and Android Notification Listener Service
- **Backend:** Python and Flask
- **AI/ML:** Hugging Face text-classification model
- **URL Security:** VirusTotal API integration

## How It Works

1. The user enables notification access for PauseAI on their Android device.
2. The app receives supported notifications through Android's notification-listener mechanism.
3. Notification content is sent to the configured Python backend for analysis.
4. The backend evaluates the message for potential scam indicators.
5. URLs can undergo additional risk checks, including VirusTotal analysis when configured.
6. The app displays the analysis and provides a warning when appropriate.

## Project Structure

- `android/` — Android platform integration and native notification handling.
- `lib/` — Flutter application interface and app logic.
- `ai/` — Python backend, AI detection, and URL-checking modules.
- `pubspec.yaml` — Flutter dependencies and project configuration.

## Current Limitations

- The Python backend must be running and reachable by the Android app for server-based AI analysis.
- Backend connectivity depends on the configured network address and network setup.
- Notification access must be granted by the user.
- Scam detection is not guaranteed to identify every fraudulent message or avoid every false positive.
- VirusTotal checks depend on valid configuration, API availability, and service limits.

## Safety Disclaimer

PauseAI is intended as a digital-safety aid. Its results are not a guarantee that a message, link, or payment is safe or fraudulent. Always verify payment claims independently, avoid sharing sensitive information with untrusted sources, and exercise caution before opening links or transferring money.

## Project Goal

Our goal is to make digital communication safer by giving users timely, AI-assisted information that helps them recognize potential scams and make more informed decisions.

