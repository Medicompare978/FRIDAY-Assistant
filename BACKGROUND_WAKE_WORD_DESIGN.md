# FRIDAY – Screen-locked voice architecture

## Target behaviour
The phone display may be OFF/locked while the phone remains powered on and connected to mobile data. The user says “Hey FRIDAY”, then asks a question. FRIDAY speaks the answer.

## Important Android constraints
- A normal Activity or SpeechRecognizer session is not a reliable always-on wake-word engine. The current starter's microphone button is tap-to-talk only.
- The preferred Android integration is a user-selected `VoiceInteractionService` / assistant role. Android may keep the selected service alive for hotword support, but actual low-power DSP hotword support and enrollment are device-dependent.
- If the device does not expose a usable hardware hotword path, a local wake-word engine in a microphone foreground service is the fallback. It needs explicit microphone permission, a persistent notification, and user-visible start/stop controls. It may consume more battery.
- Android 12+ restricts starting foreground services from the background. Android 14+ adds microphone foreground-service type and while-in-use permission restrictions. Start the listening session from a visible user action or through the supported assistant role; do not silently start microphone capture at boot.
- Force-stop, power-off, revoked permissions, OEM battery killing, or microphone privacy controls can stop listening. No app can promise wake-word detection in those states.

## Privacy requirements
1. Wake-word detection must run locally on the phone.
2. Do not stream or upload ambient microphone audio.
3. Only after the wake word, capture the user's request and send text (not ambient audio) to the online backend where possible.
4. Show a persistent listening notification and provide a clear Stop Listening action.
5. Keep all provider API keys on the backend, never in the APK.

## Implementation milestones
1. Build and install the current tap-to-talk Android shell; verify Telugu/English speech recognition and TTS on the target phone.
2. Verify microphone foreground-service behaviour while the screen is locked, with an obvious notification and stop control.
3. Check whether the target phone supports the selected assistant role and hardware hotword detection. Implement the VoiceInteractionService path if supported.
4. If hardware hotword is unavailable, integrate and benchmark an on-device wake-word model. Do not substitute cloud-based continuous listening.
5. Add command routing: local alarms/reminders, media controls, app launch, and online AI/news/cricket lookups.
6. Test Doze, lock screen, mobile-data-only, reboot, battery usage, permission revocation, and OEM battery management.

## Current project status
This ZIP is a source starter, not a compiled APK. It currently includes tap-to-talk, TTS, local time/date/battery replies, relative reminders, browser search, YouTube/Settings launch, and an optional backend hook. Always-on wake word, true online AI provider integration, live news/cricket data, and streaming music are not implemented yet. A successful Gradle build and device test have not been performed in this environment.
