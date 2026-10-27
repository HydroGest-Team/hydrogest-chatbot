# HydroGest Chatbot

Chatbot de HydroGest con arquitectura RAG. El repositorio contiene el servicio
backend en Spring Boot, una interfaz web minimalista inspirada en SB Admin y el
prototipo Python original conservado como referencia.

## Arquitectura

```text
Navegador
   |
   v
Next.js + BFF (localhost:3000)
   |  /api/health y /api/chat
   v
Spring Boot (localhost:8080)
   |-- PostgreSQL + pgvector
   |-- Gemini (embeddings y generación)
   `-- DynamoDB Local o DynamoDB en AWS
```

El navegador nunca recibe `SERVICE_API_KEY`. El BFF mantiene la llave en el
servidor y la envía al backend mediante `X-API-Key`.

## Estructura

```text
hydrogest-chatbot/
├── chatbot-service/                 # API Spring Boot 3 / Java 25
│   ├── src/main/java/               # RAG, API, seguridad y persistencia
│   ├── src/main/resources/          # configuración y migraciones Flyway
│   └── pom.xml
├── frontend/                        # Next.js, React, TypeScript y BFF
│   ├── app/                         # página y rutas /api
│   ├── components/                  # interfaz SB Admin del asistente
│   └── package.json
├── conocimiento/                    # documentos Markdown para la ingesta
├── evaluacion/                      # preguntas de evaluación del retrieval
├── prototipo-python/                # prototipo FastAPI/Chroma original
├── docker-compose.yml               # PostgreSQL/pgvector y DynamoDB Local
└── README.md
```

## Requisitos

- Java 25 (`JAVA_HOME` debe apuntar al JDK usado por el proyecto).
- Node.js 20 o superior y npm.
- Docker Desktop, para PostgreSQL/pgvector y DynamoDB Local.
- Una clave de Gemini para embeddings y generación. Las pruebas de interfaz
  descritas abajo no llaman a Gemini.

## Servicios locales

Desde la raíz del repositorio:

```powershell
docker compose up -d postgres dynamodb-local
```

El backend usa PostgreSQL en `localhost:5432` y DynamoDB Local en
`localhost:8000`. Los contenedores pueden comprobarse con:

```powershell
docker compose ps
```

## Configuración del backend

Copia la plantilla y completa los valores locales:

```powershell
Copy-Item chatbot-service\application-local.properties.example `
  chatbot-service\application-local.properties
```

Variables principales:

| Variable | Obligatoria | Valor local sugerido | Uso |
|---|---:|---|---|
| `GEMINI_API_KEY` | Sí | clave de Google AI Studio | Embeddings y LLM |
| `SERVICE_API_KEY` | Sí | clave local propia | Autenticación del backend |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/hydrogest` | PostgreSQL |
| `DB_USER` | No | `postgres` | Usuario de PostgreSQL |
| `DB_PASSWORD` | No | según Docker | Contraseña de PostgreSQL |
| `ALLOWED_ORIGINS` | No | `http://localhost:3000` | CORS |
| `DYNAMODB_ENDPOINT` | No | `http://localhost:8000` | DynamoDB Local |
| `DYNAMODB_CREATE_TABLE` | No | `true` | Crear tabla local al iniciar |
| `DYNAMODB_TABLE` | No | `chatbot_conversaciones` | Tabla de conversaciones |
| `AWS_REGION` | No | `us-east-1` | Región del cliente AWS |
| `RAG_TOP_K` | No | `4` | Chunks recuperados por pregunta |
| `UMBRAL_SIMILITUD` | No | `0.65` | Umbral mínimo de retrieval |

Para DynamoDB Local se aceptan credenciales ficticias `local`. En AWS no se
deben colocar llaves en el código: el SDK usa el proveedor estándar y el rol
IAM de la aplicación.

## Ejecutar el backend

Desde `chatbot-service`:

```powershell
.\mvnw.cmd spring-boot:run
```

El servicio queda disponible en `http://localhost:8080`.

### Endpoints

#### `GET /health`

Endpoint público para comprobar disponibilidad:

```powershell
Invoke-WebRequest http://localhost:8080/health
```

Respuesta:

```json
{"status":"ok"}
```

#### `POST /chat`

Requiere `X-API-Key` y un cuerpo JSON con `session_id` y `mensaje`. El mensaje
debe tener entre 1 y 500 caracteres.

```powershell
$headers = @{ "X-API-Key" = "tu_service_api_key" }
$body = '{"session_id":"demo-1","mensaje":"¿Cómo puedo obtener información?"}'
Invoke-RestMethod http://localhost:8080/chat -Method Post `
  -Headers $headers -ContentType "application/json" -Body $body
```

Respuesta exitosa:

```json
{
  "respuesta": "...",
  "fuentes": [
    {"archivo": "tramites.md", "seccion": "Información general"}
  ]
}
```

Los errores siguen el formato:

```json
{"error":{"codigo":"UNAUTHORIZED","mensaje":"..."}}
```

Los límites se aplican en memoria por `session_id` y por IP. Las conversaciones
se guardan de forma best-effort en DynamoDB con timestamp, fuentes, similitud,
uso del LLM, latencia y TTL. Un fallo de DynamoDB no interrumpe una respuesta
válida del chat.

## Ejecutar el frontend y BFF

Configura las variables del BFF:

```powershell
Copy-Item frontend\.env.example frontend\.env.local
```

Edita `frontend\.env.local`:

```properties
CHATBOT_API_URL=http://localhost:8080
CHATBOT_API_KEY=la_misma_clave_de_SERVICE_API_KEY
```

Instala dependencias e inicia Next.js desde `frontend`:

```powershell
npm install
npm run dev
```

Abre `http://localhost:3000`. La interfaz incluye:

- Sidebar y navegación tipo SB Admin.
- Estado del backend y métricas básicas de la sesión.
- Chat responsive con contador de caracteres.
- Visualización de las fuentes devueltas por el backend.
- BFF `/api/health`, que reenvía el health check sin llamar a Gemini.
- BFF `/api/chat`, que protege la API key y conserva los códigos de error.

La llamada real al backend solo se produce al enviar una pregunta válida. Para
validar conectividad sin Gemini, usa el indicador de estado o envía un mensaje
vacío mediante el BFF: debe llegar al backend y devolver `400` por validación.

## Ingesta y evaluación RAG

Los runners del backend permiten ejecutar las tareas de migración:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--ingestar
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--evaluar --pregunta `"texto`""
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--generar
```

La ingesta procesa `conocimiento/`, genera embeddings de 1536 dimensiones y
almacena los chunks en PostgreSQL/pgvector. `--generar` sí llama al LLM y por
eso requiere cuota disponible de Gemini; no forma parte de la prueba de
conectividad visual.

## Pruebas y validación

Backend:

```powershell
cd chatbot-service
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
```

Frontend:

```powershell
cd frontend
npm run build
npm audit --omit=dev
```

La validación de integración realizada sin Gemini comprueba:

1. `GET http://localhost:3000/api/health` devuelve `200` y
   `{"status":"ok"}` cuando Spring Boot está activo.
2. `POST http://localhost:3000/api/chat` llega al filtro del backend.
3. Una solicitud autenticada con mensaje vacío devuelve `400` y
   `error.codigo = "INVALID_REQUEST"`.
4. La interfaz se renderiza sin errores de hidratación en el navegador.

## Prototipo Python

`prototipo-python/` conserva la implementación original con FastAPI, Chroma y
los scripts de evaluación. Para ejecutarlo:

```powershell
cd prototipo-python
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## Seguridad y secretos

- No subas `.env`, `application-local.properties` ni `frontend/.env.local`.
- Nunca expongas `CHATBOT_API_KEY` en componentes cliente ni en el navegador.
- En AWS usa roles IAM y el proveedor estándar del SDK.
- No registres API keys ni incluyas trazas internas en respuestas HTTP.
