# FRIDAY Phase 2 backend (web research + AI answers)

This backend searches the public web with Tavily, passes the retrieved excerpts to an AI model, and returns a concise answer plus source URLs to the Android app.

## Required server environment variables

- `TAVILY_API_KEY`: create a key at https://tavily.com/
- `OPENAI_API_KEY`: create a key at https://platform.openai.com/api-keys
- `OPENAI_MODEL`: optional; defaults to `gpt-4o-mini`
- `FRIDAY_ACCESS_TOKEN`: a long random secret used to protect your backend from public use. Enter the same secret in the Android app.

Keep both keys in the hosting provider's secret/environment-variable settings. **Never add them to the APK, Kotlin source, GitHub repository, or a public chat.** Usage may incur charges and is subject to provider quotas.

## Run locally

```bash
cd backend
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
# Set TAVILY_API_KEY and OPENAI_API_KEY in your shell environment
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Health check: `GET /health` (shows whether required services and access protection are configured, never the secrets).  
Chat endpoint: `POST /chat` with JSON `{"message":"latest technology news","language":"te-en"}` and header `X-FRIDAY-TOKEN: your-secret`.

## Deploy on Render

1. Put this project in a private GitHub repository.
2. In Render, create a new **Blueprint** from that repository. Select the included `backend/render.yaml`.
3. In the Render service's **Environment** page, add `TAVILY_API_KEY`, `OPENAI_API_KEY`, `FRIDAY_ACCESS_TOKEN` (use a long random secret), and optionally `OPENAI_MODEL`.
4. Deploy. Open `https://YOUR-SERVICE.onrender.com/health`; all three configuration flags should be `true`.
5. Copy the service base URL (without `/health`) into FRIDAY's **FRIDAY ONLINE CONNECTION (HTTPS)** field. Enter the same `FRIDAY_ACCESS_TOKEN` in the private token field. Tap **Save online connection**. The Android app adds `/chat` automatically.

Free hosting can sleep when idle and may have cold starts or usage limits. This is not guaranteed to be an always-on free service.
