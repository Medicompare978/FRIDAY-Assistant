import os
import hmac
from typing import List

import requests
from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel, Field

app = FastAPI(title="FRIDAY Web Research Backend", version="2.0.0")

TAVILY_API_KEY = os.getenv("TAVILY_API_KEY", "").strip()
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini").strip()
FRIDAY_ACCESS_TOKEN = os.getenv("FRIDAY_ACCESS_TOKEN", "").strip()


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    language: str = "te-en"


class ChatResponse(BaseModel):
    reply: str
    sources: List[str] = Field(default_factory=list)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "web_search_configured": bool(TAVILY_API_KEY),
        "ai_configured": bool(OPENAI_API_KEY),
        "app_access_protection_configured": bool(FRIDAY_ACCESS_TOKEN),
    }


def search_web(query: str):
    if not TAVILY_API_KEY:
        raise HTTPException(
            status_code=503,
            detail="FRIDAY web search is not configured on the server. Add TAVILY_API_KEY in the backend environment settings.",
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
            context.append(f"TITLE: {title}\nURL: {url}\nEXCERPT: {content}")
            if url:
                sources.append(url)
        return context, sources
    except requests.RequestException as exc:
        if isinstance(exc, requests.HTTPError) and exc.response is not None:
            print("TAVILY ERROR:", exc.response.status_code, exc.response.text[:500])
        else:
            print("TAVILY ERROR:", repr(exc))

        raise HTTPException(
            status_code=502,
            detail="FRIDAY could not retrieve current web search results."
        )


def generate_answer(message: str, language: str, search_context: list, sources: list) -> str:
    if not OPENAI_API_KEY:
        raise HTTPException(
            status_code=503,
            detail="FRIDAY web results were found, but the AI answer service is not configured. Add OPENAI_API_KEY on the backend.",
        )

    language_instruction = (
        "Reply naturally in Telugu-English mix (simple spoken Telugu written in Telugu script where appropriate, with familiar English technical terms) if the user writes Telugu or mixed Telugu-English. "
        "If the user writes only English, reply in clear English. Keep the answer concise enough to speak aloud."
    )
    system_prompt = (
        "You are FRIDAY, a helpful personal voice assistant. "
        "Answer the user's question using the supplied fresh web search excerpts whenever relevant. "
        "Do not invent current facts, scores, prices, weather, dates, or breaking news. "
        "If the excerpts do not establish an answer, say that the available sources do not confirm it. "
        "Clearly distinguish reported facts from uncertainty. Include a short 'Sources:' list with the URLs supplied below. "
        + language_instruction
    )
    user_content = (
        f"USER QUESTION:\n{message}\n\nFRESH WEB SEARCH EXCERPTS:\n"
        + ("\n\n".join(search_context) if search_context else "No search results were returned.")
        + "\n\nSOURCE URLS:\n"
        + ("\n".join(sources) if sources else "No source URLs available.")
    )

    try:
        response = requests.post(
            "https://api.openai.com/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {OPENAI_API_KEY}",
                "Content-Type": "application/json",
            },
            json={
                "model": OPENAI_MODEL,
                "temperature": 0.2,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_content},
                ],
            },
            timeout=55,
        )
        response.raise_for_status()
        data = response.json()
        return data["choices"][0]["message"]["content"].strip()
    except requests.RequestException:
        raise HTTPException(status_code=502, detail="FRIDAY's AI answer service is temporarily unavailable. Please try again.")
    except (KeyError, IndexError, TypeError):
        raise HTTPException(status_code=502, detail="FRIDAY received an unreadable response from the AI service.")


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest, x_friday_token: str = Header(default="")):
    if not FRIDAY_ACCESS_TOKEN or not hmac.compare_digest(x_friday_token, FRIDAY_ACCESS_TOKEN):
        raise HTTPException(status_code=401, detail="FRIDAY backend access token is missing or incorrect.")
    message = request.message.strip()
    if not message:
        raise HTTPException(status_code=400, detail="Message cannot be empty.")
    search_context, sources = search_web(message)
    reply = generate_answer(message, request.language, search_context, sources)
    return ChatResponse(reply=reply, sources=sources[:5])
