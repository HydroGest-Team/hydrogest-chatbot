from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
def health() -> dict[str, str]:
    """Endpoint público de verificación de estado. No exige llave."""
    return {"status": "ok"}
