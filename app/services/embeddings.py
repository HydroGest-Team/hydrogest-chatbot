"""Generación de embeddings con la API de Gemini."""

from __future__ import annotations

import os
import random
import sys
import time
from collections.abc import Sequence
from typing import Any

from dotenv import load_dotenv
from google import genai
from google.genai import types

load_dotenv()

DEFAULT_EMBEDDING_MODEL = "gemini-embedding-001"
_BATCH_SIZE = 16
_MAX_RETRIES = 4
_INITIAL_BACKOFF_SECONDS = 1.0


class EmbeddingError(RuntimeError):
    """Error al solicitar embeddings a Gemini."""


def _client() -> Any:
    api_key = os.getenv("GEMINI_API_KEY", "").strip()
    if not api_key:
        raise EmbeddingError("GEMINI_API_KEY no está configurada en el entorno.")
    return genai.Client(api_key=api_key)


def _is_retryable(error: Exception) -> bool:
    status_code = getattr(error, "status_code", None) or getattr(error, "code", None)
    if status_code in {408, 409, 429} or (
        isinstance(status_code, int) and status_code >= 500
    ):
        return True
    message = str(error).lower()
    return any(
        marker in message
        for marker in (
            "429",
            "resource exhausted",
            "rate limit",
            "temporar",
            "timeout",
            "unavailable",
            "connection",
            "internal server",
        )
    )


def _embed(
    contents: str | Sequence[str],
    *,
    task_type: str,
    client: Any,
    model: str,
) -> list[list[float]]:
    last_error: Exception | None = None
    for attempt in range(_MAX_RETRIES + 1):
        try:
            response = client.models.embed_content(
                model=model,
                contents=contents,
                config=types.EmbedContentConfig(task_type=task_type),
            )
            embeddings = getattr(response, "embeddings", None)
            if not embeddings:
                raise EmbeddingError("Gemini devolvió una respuesta sin embeddings.")
            values = [list(embedding.values or []) for embedding in embeddings]
            if any(not vector for vector in values):
                raise EmbeddingError("Gemini devolvió un embedding vacío.")
            return values
        except Exception as exc:
            last_error = exc
            if attempt >= _MAX_RETRIES or not _is_retryable(exc):
                raise EmbeddingError(f"No se pudo generar el embedding: {exc}") from exc
            delay = _INITIAL_BACKOFF_SECONDS * (2**attempt) + random.uniform(0, 0.25)
            print(
                f"[ADVERTENCIA] Error transitorio al generar embeddings; "
                f"reintentando en {delay:.1f}s ({attempt + 1}/{_MAX_RETRIES}).",
                file=sys.stderr,
            )
            time.sleep(delay)
    raise EmbeddingError(f"No se pudo generar el embedding: {last_error}")


def embed_documents(textos: list[str]) -> list[list[float]]:
    """Genera embeddings de recuperación para documentos, en lotes pequeños."""

    if not textos:
        return []
    if any(not isinstance(texto, str) or not texto.strip() for texto in textos):
        raise ValueError("Todos los textos de documentos deben ser cadenas no vacías.")

    client = _client()
    model = os.getenv("EMBEDDING_MODEL", DEFAULT_EMBEDDING_MODEL).strip()
    vectors: list[list[float]] = []
    total = len(textos)
    for start in range(0, total, _BATCH_SIZE):
        batch = textos[start : start + _BATCH_SIZE]
        vectors.extend(
            _embed(
                batch,
                task_type="RETRIEVAL_DOCUMENT",
                client=client,
                model=model,
            )
        )
        print(f"Embeddings: {min(start + len(batch), total)}/{total}")
    if len(vectors) != total:
        raise EmbeddingError(
            f"Gemini devolvió {len(vectors)} embeddings para {total} documentos."
        )
    return vectors


def embed_query(texto: str) -> list[float]:
    """Genera un embedding de recuperación para una consulta."""

    if not isinstance(texto, str) or not texto.strip():
        raise ValueError("El texto de consulta debe ser una cadena no vacía.")
    model = os.getenv("EMBEDDING_MODEL", DEFAULT_EMBEDDING_MODEL).strip()
    return _embed(
        texto,
        task_type="RETRIEVAL_QUERY",
        client=_client(),
        model=model,
    )[0]
