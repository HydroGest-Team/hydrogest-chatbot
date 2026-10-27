import { NextRequest, NextResponse } from "next/server";

type ChatRequest = {
  session_id?: unknown;
  mensaje?: unknown;
};

export async function POST(request: NextRequest) {
  const baseUrl = process.env.CHATBOT_API_URL ?? "http://localhost:8080";
  const apiKey = process.env.CHATBOT_API_KEY;

  if (!apiKey) {
    return NextResponse.json(
      {
        error: {
          codigo: "BFF_CONFIG_ERROR",
          mensaje: "El BFF no tiene configurada la llave del servicio.",
        },
      },
      { status: 503 },
    );
  }

  let body: ChatRequest;
  try {
    body = (await request.json()) as ChatRequest;
  } catch {
    return NextResponse.json(
      { error: { codigo: "INVALID_REQUEST", mensaje: "El cuerpo no es JSON válido." } },
      { status: 400 },
    );
  }

  try {
    const response = await fetch(`${baseUrl}/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-API-Key": apiKey,
      },
      body: JSON.stringify(body),
      cache: "no-store",
      signal: AbortSignal.timeout(30000),
    });
    const payload = await response.json();
    return NextResponse.json(payload, { status: response.status });
  } catch {
    return NextResponse.json(
      {
        error: {
          codigo: "BACKEND_UNAVAILABLE",
          mensaje: "No se pudo conectar con el backend.",
        },
      },
      { status: 503 },
    );
  }
}
