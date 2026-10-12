# hydrogest-chatbot

HydroGest - Servicio de chatbot RAG (FastAPI + Gemini + Chroma)

## Estructura base

```text
hidrogest-chatbot/
├── app/
│   ├── main.py
│   ├── config.py
│   ├── routes/
│   │   └── health.py
│   └── services/
├── conocimiento/
├── scripts/
│   └── probar_gemini.py
├── data/
├── .env
├── .env.example
├── .gitignore
├── requirements.txt
└── README.md
```

## Uso rápido

```bash
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload
```

Probar salud del servicio:

```bash
curl http://127.0.0.1:8000/health
```

Probar API key de Gemini:

```bash
python scripts/probar_gemini.py
```
