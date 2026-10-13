# hidrogest-chatbot

Esqueleto del servicio backend para el chatbot de HydroGest basado en FastAPI, Google Gemini (`google-genai`) y ChromaDB.

---

## Estructura del Proyecto

```text
hydrogest-chatbot/
├── app/
│   ├── config.py           # Configuración y validación de variables de entorno
│   ├── dependencies.py     # Dependencia reutilizable para validación de X-API-Key
│   ├── main.py             # Instancia de FastAPI y configuración de CORS
│   ├── routes/
│   │   └── health.py       # Endpoint GET /health (público)
│   └── services/           # Lógica de negocio (vacío actualmente)
├── scripts/
│   └── probar_gemini.py    # Script de prueba de embedding y LLM con google-genai
├── docs/
│   └── contrato-api.md     # Contrato formal de la API
├── data/                   # Directorio local para almacenamiento (excluido en git)
├── conocimiento/           # Documentos base de conocimiento (existente)
├── .env.example            # Plantilla de variables de entorno sin secretos
├── .gitignore              # Excluye .env, data/, __pycache__/, entornos virtuales
├── requirements.txt        # Dependencias del proyecto
└── README.md
```

---

## Requisitos Previos

- Python 3.10 o superior.
- Clave de API de Google Gemini ([Google AI Studio](https://aistudio.google.com/)).

---

## Instalación y Configuración

1. **Crear y activar entorno virtual:**
   ```bash
   python -m venv .venv
   # En Windows PowerShell:
   .venv\Scripts\Activate.ps1
   # En Linux / macOS:
   source .venv/bin/activate
   ```

2. **Instalar dependencias:**
   ```bash
   pip install -r requirements.txt
   ```

3. **Configurar variables de entorno:**
   Copia el archivo `.env.example` como `.env` y asigna tus credenciales:
   ```bash
   cp .env.example .env
   ```

   Variables clave:
   - `GEMINI_API_KEY`: Tu clave de Google Gemini (obligatoria).
   - `SERVICE_API_KEY`: Clave para autenticación del servicio vía header `X-API-Key` (obligatoria).
   - `EMBEDDING_MODEL`: Modelo de embedding (ej. `gemini-embedding-exp-03-07`).
   - `LLM_MODEL`: Modelo generativo (ej. `gemini-2.0-flash`).
   - `CHROMA_PATH`: Directorio de persistencia de Chroma (`./data/chroma`).
   - `ALLOWED_ORIGINS`: Lista de orígenes permitidos separados por comas para CORS.

---

## Pruebas y Ejecución

### 1. Probar conexión y modelos de Gemini
Ejecuta el script de verificación para validar que el SDK genera embeddings y responde consultas al LLM:
```bash
python scripts/probar_gemini.py
```

### 2. Iniciar el servicio FastAPI
```bash
uvicorn app.main:app --reload
```

- Endpoint de salud: [http://localhost:8000/health](http://localhost:8000/health) (devuelve `{"status": "ok"}`)
- Documentación interactiva (Swagger): [http://localhost:8000/docs](http://localhost:8000/docs)

---

## Contrato de API

Para más detalles sobre autenticación con `X-API-Key`, CORS y endpoints, consulta [`docs/contrato-api.md`](docs/contrato-api.md).
