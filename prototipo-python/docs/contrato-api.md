# Contrato de API - hidrogest-chatbot

Especificación formal del servicio backend `hidrogest-chatbot` desarrollado en Python con FastAPI.

---

## 1. Información General

- **Nombre del servicio:** `hidrogest-chatbot`
- **Framework:** FastAPI
- **Protocolo:** HTTP / JSON
- **Políticas CORS:** Restringido exclusivamente a los orígenes definidos en la variable de entorno `ALLOWED_ORIGINS`.

---

## 2. Autenticación y Seguridad

Los endpoints protegidos (como el futuro `/chat`) requieren autenticación mediante encabezado HTTP:

- **Header:** `X-API-Key`
- **Valor:** Coincidente con `SERVICE_API_KEY` configurada en el servidor.
- **Respuestas de error de autenticación:**
  - `401 Unauthorized`: Si la petición no incluye el header `X-API-Key`.
    ```json
    {
      "detail": "Falta el encabezado de autenticación X-API-Key."
    }
    ```
  - `403 Forbidden`: Si el valor proporcionado en `X-API-Key` no coincide con `SERVICE_API_KEY`.
    ```json
    {
      "detail": "X-API-Key inválida o no autorizada."
    }
    ```

---

## 3. Endpoints

### 3.1 Verificación de Salud (`/health`)

Verifica que el servicio esté activo y respondiendo correctamente.

- **Método:** `GET`
- **Ruta:** `/health`
- **Autenticación:** Ninguna (Público, no exige llave).
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "status": "ok"
  }
  ```

---

### 3.2 Chat / Consulta RAG (`/chat`)

- **Método:** `POST`
- **Ruta:** `/chat`
- **Autenticación:** Requerida vía `X-API-Key`.
- **Cuerpo:**
  ```json
  {
    "session_id": "sesion-123",
    "mensaje": "¿Cuál es el horario de atención?"
  }
  ```
- `session_id` es obligatorio y admite hasta 100 caracteres.
- `mensaje` es obligatorio y admite entre 1 y 500 caracteres.
- **Respuesta exitosa (`200 OK`):**
  ```json
  {
    "respuesta": "....",
    "fuentes": [
      {"archivo": "05-horarios-y-contacto.md", "seccion": "...."}
    ]
  }
- **Errores:**
  - `400`: `{ "error": { "codigo": "INVALID_REQUEST", "mensaje": "..." } }`
  - `401`: `{ "error": { "codigo": "UNAUTHORIZED", "mensaje": "..." } }`
  - `429`: `{ "error": { "codigo": "RATE_LIMIT_EXCEEDED", "mensaje": "..." } }`
  - `503`: `{ "error": { "codigo": "LLM_UNAVAILABLE", "mensaje": "..." } }`
- El límite se mantiene en memoria y se aplica por `session_id` y por IP.

---

## 4. Configuración y Variables de Entorno

El servicio depende de las siguientes variables cargadas desde `.env`:

| Variable | Tipo | Obligatoria | Descripción |
|---|---|---|---|
| `GEMINI_API_KEY` | String | Sí | Llave de API para acceder a Google Gemini (LLM y Embeddings). Falla al iniciar si falta. |
| `SERVICE_API_KEY` | String | Sí | Llave de acceso requerida en el header `X-API-Key` para proteger endpoints internos. Falla al iniciar si falta. |
| `EMBEDDING_MODEL` | String | No | Nombre del modelo para embeddings (ej. `gemini-embedding-exp-03-07`). |
| `LLM_MODEL` | String | No | Nombre del modelo de lenguaje (ej. `gemini-2.0-flash`). |
| `CHROMA_PATH` | String | No | Ruta del directorio persistente para ChromaDB (ej. `./data/chroma`). |
| `ALLOWED_ORIGINS` | String | No | Lista de orígenes permitidos por CORS separados por coma (ej. `http://localhost:3000,http://localhost:8000`). |
