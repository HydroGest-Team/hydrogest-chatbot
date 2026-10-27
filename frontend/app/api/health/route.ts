import { NextResponse } from "next/server";

export async function GET() {
  const baseUrl = process.env.CHATBOT_API_URL ?? "http://localhost:8080";

  try {
    const response = await fetch(`${baseUrl}/health`, {
      cache: "no-store",
      signal: AbortSignal.timeout(4000),
    });

    if (!response.ok) {
      return NextResponse.json(
        { status: "degraded", backendStatus: response.status },
        { status: 502 },
      );
    }

    return NextResponse.json({ status: "ok" });
  } catch {
    return NextResponse.json(
      { status: "offline", message: "No se pudo conectar con el backend." },
      { status: 503 },
    );
  }
}
