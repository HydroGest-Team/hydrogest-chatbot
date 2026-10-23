"""
Script de verificación de conexión y modelos de Google Gemini.

1. Genera el embedding de una frase de prueba e imprime la dimensión del vector.
2. Realiza una llamada simple al LLM ("Responde con una palabra: hola") e imprime la respuesta.

Los modelos y credenciales se leen desde las variables de entorno (.env).
"""

import os
import sys

from dotenv import load_dotenv
from google import genai

load_dotenv()


def main() -> None:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("[ERROR] GEMINI_API_KEY no está configurada en .env", file=sys.stderr)
        raise SystemExit(1)

    embedding_model = os.getenv("EMBEDDING_MODEL", "gemini-embedding-001")
    llm_model = os.getenv("LLM_MODEL", "gemini-3.8-flash")

    print(f"Iniciando cliente Gemini con SDK oficial 'google-genai'...")
    client = genai.Client(api_key=api_key)

    # 1. Embedding de prueba
    frase_prueba = "Prueba de generación de embedding para el chatbot HydroGest."
    print(f"\n1. Generando embedding con modelo: {embedding_model}")
    print(f"   Frase de prueba: «{frase_prueba}»")

    try:
        emb_res = client.models.embed_content(
            model=embedding_model,
            contents=frase_prueba,
        )
        vector = emb_res.embeddings[0].values
        print(f"   [OK] Dimensión del vector generado: {len(vector)}")
    except Exception as e:
        print(f"   [ERROR] Error al generar embedding: {e}", file=sys.stderr)
        raise SystemExit(1)

    # 2. Llamada simple al LLM
    prompt = "Responde con una palabra: hola"
    print(f"\n2. Llamando al LLM con modelo: {llm_model}")
    print(f"   Prompt: «{prompt}»")

    try:
        chat_res = client.models.generate_content(
            model=llm_model,
            contents=prompt,
        )
        print(f"   [OK] Respuesta del LLM: {chat_res.text.strip()}\n")
    except Exception as e:
        print(f"   [ERROR] Error al consultar el LLM: {e}", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
