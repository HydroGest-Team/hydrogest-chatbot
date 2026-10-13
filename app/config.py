import os
import sys
from dataclasses import dataclass, field
from dotenv import load_dotenv

load_dotenv()


def _require_env(name: str) -> str:
    """Obtiene el valor de una variable de entorno obligatoria o termina con un error claro."""
    val = os.getenv(name)
    if not val or not val.strip():
        print(
            f"\n[ERROR DE CONFIGURACIÓN]\n"
            f"La variable de entorno '{name}' es obligatoria y no está configurada.\n"
            f"Por favor, revisa tu archivo .env (puedes guiarte con .env.example) y define '{name}'.\n",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return val.strip()


def _get_allowed_origins() -> list[str]:
    """Convierte la variable ALLOWED_ORIGINS en una lista de cadenas."""
    raw = os.getenv("ALLOWED_ORIGINS", "http://localhost:3000,http://localhost:8000")
    origins = [item.strip() for item in raw.split(",") if item.strip()]
    return origins if origins else ["http://localhost:3000", "http://localhost:8000"]


@dataclass(frozen=True)
class Settings:
    """Configuración centralizada de la aplicación."""

    gemini_api_key: str = field(default_factory=lambda: _require_env("GEMINI_API_KEY"))
    service_api_key: str = field(default_factory=lambda: _require_env("SERVICE_API_KEY"))
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "gemini-embedding-001")
    llm_model: str = os.getenv("LLM_MODEL", "gemini-3.8-flash")
    chroma_path: str = os.getenv("CHROMA_PATH", "./data/chroma")
    allowed_origins: list[str] = field(default_factory=_get_allowed_origins)


settings = Settings()
