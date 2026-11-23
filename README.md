# FRIDAY Android – Phase 2 (Web Search + AI Answers)

This Android app builds on the Phase 1 voice assistant and can connect to a separately hosted FRIDAY backend for current web research and AI-generated spoken answers.

## Phase 2 capabilities in the source
- Tap-to-talk speech input and text-to-speech replies
- Existing local time, date, battery, reminder and app-opening commands
- Secure HTTPS backend URL and private FRIDAY access-token settings in the app
- General web research through the backend (Tavily)
- AI answer generation through the backend (OpenAI)
- Source URLs returned with answers

**Important:** Online answers will not work until the backend is deployed and configured with `TAVILY_API_KEY`, `OPENAI_API_KEY`, and `FRIDAY_ACCESS_TOKEN`. Keep provider keys on the server; never put them in the APK. API and hosting usage may incur charges.

## Open in Android Studio
1. Extract this ZIP.
2. Choose **File > Open** and select the inner `FRIDAY_Android_Project` folder (the folder containing `settings.gradle`).
3. Trust the project if prompted and allow Gradle sync to finish.
4. Build using **Build > Generate App Bundles or APKs > Generate APKs**.

## Backend setup
See `backend/README.md` for local setup and Render deployment steps. After deployment, set the HTTPS service base URL and matching private access token in FRIDAY's online connection settings.

## Current limitations
- This is not an always-listening wake-word assistant. "Hey FRIDAY" while the screen is off is not implemented in this phase.
- Live web answers require a working internet connection, configured backend, valid provider keys, and available API quota.
- This source package has not been validated by a Gradle build in this environment; build and test it in Android Studio.
