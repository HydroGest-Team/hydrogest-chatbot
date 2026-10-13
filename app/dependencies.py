import secrets
from fastapi import HTTPException, Security, status
from fastapi.security import APIKeyHeader

from app.config import settings

api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


async def verify_api_key(api_key: str | None = Security(api_key_header)) -> str:
    """
    Dependencia de seguridad reutilizable para FastAPI.
    Verifica que el header 'X-API-Key' esté presente y coincida con SERVICE_API_KEY.
    """
    if not api_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Falta el encabezado de autenticación X-API-Key.",
        )

    if not secrets.compare_digest(api_key, settings.service_api_key):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="X-API-Key inválida o no autorizada.",
        )

    return api_key
