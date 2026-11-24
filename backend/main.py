
import os
import hmac
from typing import List

import requests
from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel, Field


app = FastAPI(title="FRIDAY Web Research Backend", version="3.0.0")


# Environment variables
TAVILY_API_KEY = os.getenv("TAVILY_API_KEY", "").strip()
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "").strip()
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.6-flash").strip()
FRIDAY_ACCESS_TOKEN = os.getenv("FRIDAY_ACCESS_TOKEN", "").strip()


# Request and response models
class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    language: str = "te-en"


class ChatResponse(BaseModel):
    reply: str
    sources: List[str] = Field(default_factory=list)


# Health check
@app.get("/health")
def health():
    return {
        "status": "ok",
        "web_search_configured": bool(TAVILY_API_KEY),
        "ai_configured": bool(GEMINI_API_KEY),
        "ai_provider": "Gemini",
        "app_access_protection_configured": bool(FRIDAY_ACCESS_TOKEN),
    }


# Tavily web search
def search_web(query: str):
    if not TAVILY_API_KEY:
        raise HTTPException(
            status_code=503,
            detail="FRIDAY web search is not configured."
        )

    try:
        response = requests.post(
            "https://api.tavily.com/search",
            json={
                "api_key": TAVILY_API_KEY,
                "query": query,
                "search_depth": "advanced",
                "max_results": 5,
                "include_answer": False,
                "include_raw_content": False,
            },
            timeout=25,
        )

        response.raise_for_status()
        data = response.json()

        results = data.get("results", [])

        if not results:
            return [], []

        context = []
        sources = []

        for item in results[:5]:
            title = item.get("title", "Untitled page")
            url = item.get("url", "")
            content = item.get("content", "")

            context.append(
                f"TITLE: {title}\n"
                f"URL: {url}\n"
                f"EXCERPT: {content}"
            )

            if url:
                sources.append(url)

        return context, sources

    except requests.RequestException as exc:
        if isinstance(exc, requests.HTTPError) and exc.response is not None:
            print(
                "TAVILY ERROR:",
                exc.response.status_code,
                exc.response.text[:500]
            )
        else:
            print("TAVILY CONNECTION ERROR:", repr(exc))

        raise HTTPException(
            status_code=502,
            detail="FRIDAY could not retrieve current web search results."
        )


# Gemini AI answer generation
def generate_answer(
    message: str,
    language: str,
    search_context: list,
    sources: list
) -> str:

    if not GEMINI_API_KEY:
        raise HTTPException(
            status_code=503,
            detail="Gemini API key is not configured on the backend."
        )

    language_instruction = (
        "If the user writes Telugu or mixed Telugu-English, reply naturally "
        "in a Telugu-English mix using simple spoken Telugu in Telugu script "
        "where appropriate, with familiar English technical terms. "
        "If the user writes only English, reply in clear English. "
        "Keep the answer concise enough to speak aloud."
    )

    system_prompt = (
        "You are FRIDAY, a helpful personal voice assistant. "
        "Answer the user's question using the supplied fresh web search "
        "excerpts whenever relevant. "
        "Do not invent current facts, scores, prices, weather, dates, "
        "or breaking news. "
        "If the excerpts do not establish an answer, say that the available "
        "sources do not confirm it. "
        "Clearly distinguish reported facts from uncertainty. "
        "Include a short Sources list with the supplied URLs. "
        + language_instruction
    )

    user_content = (
        f"USER QUESTION:\n{message}\n\n"
        "FRESH WEB SEARCH EXCERPTS:\n"
        + (
            "\n\n".join(search_context)
            if search_context
            else "No search results were returned."
        )
        + "\n\nSOURCE URLS:\n"
        + (
            "\n".join(sources)
            if sources
            else "No source URLs available."
        )
    )

    url = (
        "https://generativelanguage.googleapis.com/v1beta/"
        f"models/{GEMINI_MODEL}:generateContent"
    )

    try:
        response = requests.post(
            url,
            headers={
                "x-goog-api-key": GEMINI_API_KEY,
                "Content-Type": "application/json",
            },
            json={
                "systemInstruction": {
                    "parts": [
                        {"text": system_prompt}
                    ]
                },
                "contents": [
                    {
                        "role": "user",
                        "parts": [
                            {"text": user_content}
                        ],
                    }
                ],
                "generationConfig": {
                    "temperature": 0.2,
                    "maxOutputTokens": 1024,
                },
            },
            timeout=55,
        )

        response.raise_for_status()
        data = response.json()

        candidates = data.get("candidates", [])

        if not candidates:
            print(
                "GEMINI EMPTY RESPONSE:",
                str(data)[:1000]
            )
            raise HTTPException(
                status_code=502,
                detail="Gemini did not return an answer."
            )

        parts = candidates[0].get("content", {}).get("parts", [])

        answer = "\n".join(
            part["text"]
            for part in parts
            if "text" in part
        ).strip()

        if not answer:
            print(
                "GEMINI EMPTY TEXT:",
                str(data)[:1000]
            )
            raise HTTPException(
                status_code=502,
                detail="Gemini returned an empty answer."
            )

        return answer

    except requests.HTTPError as exc:
        status = (
            exc.response.status_code
            if exc.response is not None
            else 502
        )

        error_body = (
            exc.response.text[:1000]
            if exc.response is not None
            else str(exc)
        )

        print("GEMINI API ERROR:", status, error_body)

        if status == 429:
            raise HTTPException(
                status_code=429,
                detail="Gemini free-tier quota or rate limit reached. Try again later."
            )

        if status in (400, 401, 403, 404):
            raise HTTPException(
                status_code=502,
                detail=f"Gemini API configuration error (HTTP {status}). Check the API key and model."
            )

        raise HTTPException(
            status_code=502,
            detail="Gemini AI service is temporarily unavailable."
        )

    except requests.RequestException as exc:
        print("GEMINI CONNECTION ERROR:", repr(exc))
        raise HTTPException(
            status_code=502,
            detail="FRIDAY could not connect to Gemini. Please try again."
        )


# Main chat endpoint
@app.post("/chat", response_model=ChatResponse)
def chat(
    request: ChatRequest,
    x_friday_token: str = Header(default="")
):

    if (
        not FRIDAY_ACCESS_TOKEN
        or not hmac.compare_digest(
            x_friday_token,
            FRIDAY_ACCESS_TOKEN
        )
    ):
        raise HTTPException(
            status_code=401,
            detail="FRIDAY backend access token is missing or incorrect."
        )

    message = request.message.strip()

    if not message:
        raise HTTPException(
            status_code=400,
            detail="Message cannot be empty."
        )

    search_context, sources = search_web(message)

    reply = generate_answer(
        message,
        request.language,
        search_context,
        sources
    )

    return ChatResponse(
        reply=reply,
        sources=sources[:5]
    )
