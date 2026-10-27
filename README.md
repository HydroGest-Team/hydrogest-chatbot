# hydrogest-chatbot

Servicio backend para el chatbot de HydroGest basado en **Spring Boot 3**, **Spring AI** (Google GenAI Chat y Embeddings) y **PostgreSQL**. Incluye además el prototipo original en Python conservado para referencia en `prototipo-python/`.

---

## Estructura del Repositorio

```text
hydrogest-chatbot/
├── chatbot-service/                        # Servicio backend principal en Spring Boot
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/hydrogest/chatbot/
│   │   │   │   ├── ChatbotServiceApplication.java
│   │   │   │   ├── config/
│   │   │   │   │   ├── ChatbotProperties.java    # Validación y mapeo de variables
│   │   │   │   │   └── CorsConfig.java           # Configuración de CORS con ALLOWED_ORIGINS
│   │   │   │   ├── controller/
│   │   │   │   │   └── HealthController.java     # Endpoint público GET /health
│   │   │   │   ├── runner/
│   │   │   │   │   └── ProbarGeminiRunner.java   # Comando --probar-gemini
│   │   │   │   └── security/
│   │   │   │       └── ApiKeyAuthFilter.java     # Filtro de autenticación X-API-Key
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── application-local.properties.example
│   │   └── test/
│   │       └── java/com/hydrogest/chatbot/
│   │           ├── ChatbotServiceApplicationTests.java
│   │           └── HealthAndSecurityTests.java
│   ├── application-local.properties.example      # Plantilla de configuración local
│   ├── mvnw / mvnw.cmd                          # Maven Wrapper multiplataforma
│   └── pom.xml                                  # Dependencias de Spring Boot y Spring AI
├── prototipo-python/                            # Prototipo original en Python (FastAPI/Chroma)
│   ├── app/
│   ├── scripts/
│   ├── docs/
│   ├── requirements.txt
│   └── .env.example
├── conocimiento/                                # Base de conocimiento Markdown (raíz)
├── evaluacion/                                  # Banco de preguntas de evaluación (raíz)
├── .gitignore
├── LICENSE
└── README.md
```

---

## 1. Chatbot Service (Spring Boot)

### Requisitos Previos

- **Java 25** (o superior).
- Clave de API de Google Gemini ([Google AI Studio](https://aistudio.google.com/)).
- Maven Wrapper incluido (`./mvnw` o `.\mvnw.cmd`), no requiere instalación global de Maven.

### Configuración y Variables de Entorno

Spring Boot carga las propiedades desde variables de entorno del sistema o desde un archivo local `application-local.properties` (ignorado en git).

Copia la plantilla `application-local.properties.example` como `application-local.properties` dentro de `chatbot-service/`:

```properties
# Google Gemini API (Obligatoria: la app falla al iniciar si falta)
GEMINI_API_KEY=tu_gemini_api_key

# Seguridad del Servicio (Obligatoria: validada en el header X-API-Key)
SERVICE_API_KEY=tu_service_api_key

# Modelos de Gemini
EMBEDDING_MODEL=gemini-embedding-001
LLM_MODEL=gemini-3.8-flash
EMBEDDING_DIM=1536

# Base de Datos PostgreSQL
DB_URL=jdbc:postgresql://localhost:5432/hydrogest
DB_USER=postgres
DB_PASSWORD=tu_password

# CORS: orígenes permitidos separados por comas (sin valor, no habilita CORS)
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8000

# Límites de POST /chat por ventana de un minuto
RATE_LIMIT_PER_SESSION=10
RATE_LIMIT_PER_IP=30

# Parámetros RAG y Base de Conocimiento
RAG_TOP_K=4
UMBRAL_SIMILITUD=0.65
CONOCIMIENTO_DIR=conocimiento
```

> **Validación de inicio:** La aplicación valida en el arranque la existencia de `GEMINI_API_KEY` y `SERVICE_API_KEY`. Si alguna de ellas falta, la aplicación se detiene de inmediato mostrando un mensaje explicativo claro en la consola.

### Tabla de Propiedades

| Propiedad / Variable | Tipo | Obligatoria | Valor por Defecto | Descripción |
|---|---|---|---|---|
| `GEMINI_API_KEY` | String | **Sí** | — | Clave de API para acceder a Google Gemini. |
| `SERVICE_API_KEY` | String | **Sí** | — | Clave requerida en el header `X-API-Key` para endpoints protegidos. |
| `EMBEDDING_MODEL` | String | No | `gemini-embedding-001` | Modelo para generar vectores de embedding. |
| `LLM_MODEL` | String | No | `gemini-3.8-flash` | Modelo de lenguaje generativo. |
| `EMBEDDING_DIM` | Integer | No | `1536` | Dimensión vectorial de los embeddings. |
| `DB_URL` | String | No | `jdbc:postgresql://localhost:5432/hydrogest` | URL JDBC de conexión a PostgreSQL. |
| `DB_USER` | String | No | `postgres` | Usuario de PostgreSQL. |
| `DB_PASSWORD` | String | No | *(vacío)* | Contraseña de PostgreSQL. |
| `ALLOWED_ORIGINS` | String | No | *(vacío)* | Orígenes permitidos por la política CORS. |
| `RATE_LIMIT_PER_SESSION` | Integer | No | `10` | Peticiones por minuto permitidas por `session_id`. |
| `RATE_LIMIT_PER_IP` | Integer | No | `30` | Peticiones por minuto permitidas por IP. |
| `RAG_TOP_K` | Integer | No | `4` | Cantidad de chunks más relevantes a recuperar. |
| `UMBRAL_SIMILITUD` | Double | No | `0.65` | Umbral mínimo de similitud para retrieval. |
| `CONOCIMIENTO_DIR` | String | No | `conocimiento` | Ruta a los documentos Markdown de conocimiento. |

---

### Ejecución y Comandos

Desde la carpeta `chatbot-service/`:

#### 1. Probar conexión y modelos de Gemini (`--probar-gemini`)
Ejecuta la prueba de generación de un embedding (imprimiendo su dimensión) y realiza una llamada simple al LLM:

```bash
# En Windows (PowerShell / CMD)
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--probar-gemini

# En Linux / macOS
./mvnw spring-boot:run -Dspring-boot.run.arguments=--probar-gemini
```

Salida esperada:
```text
Iniciando verificación de conexión y modelos de Google Gemini con Spring AI...

1. Generando embedding con modelo: gemini-embedding-001
   Frase de prueba: «Prueba de generación de embedding para el chatbot HydroGest.»
   [OK] Dimensión del vector generado: 3072

2. Llamando al LLM con modelo: gemini-3.8-flash
   Prompt: «Responde con una palabra: hola»
   [OK] Respuesta del LLM: Hola

Prueba de Gemini finalizada exitosamente.
```

#### 2. Iniciar el servidor web
```bash
# En Windows (PowerShell / CMD)
.\mvnw.cmd spring-boot:run

# En Linux / macOS
./mvnw spring-boot:run
```
El servidor iniciará en `http://localhost:8080`.

#### 3. Ejecutar pruebas automatizadas
```bash
.\mvnw.cmd test
```

---

### Endpoints y Seguridad

- **`GET /health`** (Público, sin clave):
  Verifica que el servicio esté activo y respondiendo.
  ```bash
  curl -i http://localhost:8080/health
  ```
  Respuesta:
  ```json
  {
    "status": "ok"
  }
  ```

- **Filtro de Autenticación (`X-API-Key`)**:
  Los endpoints protegidos requieren el encabezado `X-API-Key`.
  - Si falta o es incorrecto: responde `401 Unauthorized` con
    `{"error":{"codigo":"UNAUTHORIZED","mensaje":"..."}}`.

- **Chat (`POST /chat`)**:
  Requiere `X-API-Key` y recibe `{ "session_id": "...", "mensaje": "..." }`.
  `mensaje` debe tener entre 1 y 500 caracteres. La respuesta exitosa contiene
  `{ "respuesta": "...", "fuentes": [...] }`. Los límites de peticiones son en
  memoria y se aplican por sesión y por IP.

- **Actuator Health:**
  Disponible en `http://localhost:8080/actuator/health`.

---

## 2. Prototipo en Python

El prototipo original en FastAPI se encuentra preservado sin modificaciones en `prototipo-python/`.

Para ejecutarlo:
1. Navega a `prototipo-python/`.
2. Activa el entorno virtual (`source .venv/bin/activate` o `.venv\Scripts\Activate.ps1`).
3. Instala dependencias: `pip install -r requirements.txt`.
4. Inicia FastAPI: `uvicorn app.main:app --reload`.
