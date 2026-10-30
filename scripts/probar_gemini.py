"""
Script de prueba para verificar la conexión con Gemini.

1. Genera el embedding de una frase de prueba e imprime la dimensión del vector.
2. Hace una llamada simple al LLM ("Responde con una palabra: hola")
   e imprime la respuesta.
"""

import os
import sys

from dotenv import load_dotenv
from google import genai

load_dotenv()


def main() -> None:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("ERROR: GEMINI_API_KEY no está configurada en .env", file=sys.stderr)
        raise SystemExit(1)

    client = genai.Client(api_key=api_key)

    # ── 1. Embedding ──────────────────────────────────────────────
    embedding_model = os.getenv("EMBEDDING_MODEL", "gemini-embedding-exp-03-07")
    frase = "La gestión del agua es un recurso vital para la humanidad."

    print(f"Modelo de embedding: {embedding_model}")
    print(f"Frase: «{frase}»\n")

    emb_response = client.models.embed_content(
        model=embedding_model,
        contents=frase,
    )
    vector = emb_response.embeddings[0].values
    print(f"✓ Embedding generado — dimensión del vector: {len(vector)}")

    # ── 2. Llamada al LLM ────────────────────────────────────────
    llm_model = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")

    print(f"\nModelo LLM: {llm_model}")
    print('Prompt: "Responde con una palabra: hola"\n')

    llm_response = client.models.generate_content(
        model=llm_model,
        contents="Responde con una palabra: hola",
    )
    print(f"✓ Respuesta del LLM: {llm_response.text.strip()}")


if __name__ == "__main__":
    main()
