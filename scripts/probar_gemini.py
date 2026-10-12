import os

import google.generativeai as genai
from dotenv import load_dotenv

load_dotenv()


def main() -> None:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise SystemExit("GEMINI_API_KEY no está configurada.")

    genai.configure(api_key=api_key)
    model = genai.GenerativeModel(os.getenv("GEMINI_MODEL", "gemini-1.5-flash"))
    response = model.generate_content("Responde únicamente con: OK")
    print((response.text or "").strip())


if __name__ == "__main__":
    main()
