import os
import sys

from dotenv import load_dotenv

load_dotenv()


def _require_env(name: str) -> str:
    """Return the value of an environment variable or exit with a clear error."""
    value = os.getenv(name)
    if not value:
        print(
            f"ERROR: La variable de entorno '{name}' es obligatoria.\n"
            f"Cópiala de .env.example → .env y añade tu clave.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return value


class Settings:
    """Configuración centralizada del proyecto.  Se lee una sola vez al importar."""

    app_name: str = os.getenv("APP_NAME", "HydroGest Chatbot")
    app_env: str = os.getenv("APP_ENV", "development")
    gemini_api_key: str = _require_env("GEMINI_API_KEY")
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "gemini-embedding-exp-03-07")


settings = Settings()
