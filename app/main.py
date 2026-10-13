from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routes.health import router as health_router

app = FastAPI(
    title="hidrogest-chatbot",
    description="Servicio backend para el chatbot de HydroGest",
    version="0.1.0",
)

# Configuración de CORS restringida únicamente a ALLOWED_ORIGINS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Registro de rutas públicas
app.include_router(health_router)
