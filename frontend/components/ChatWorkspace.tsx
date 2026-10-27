"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";

type Source = { archivo: string; seccion: string };
type ChatResponse = { respuesta: string; fuentes: Source[] };
type Health = "checking" | "online" | "offline";

function Icon({ name }: { name: "grid" | "chat" | "book" | "settings" | "send" | "pulse" }) {
  const paths = {
    grid: "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z",
    chat: "M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v7a2.5 2.5 0 0 1-2.5 2.5H11l-4.5 4v-4h0a2.5 2.5 0 0 1-2.5-2.5z",
    book: "M5 4.5A2.5 2.5 0 0 1 7.5 2H19v17H7.5A2.5 2.5 0 0 0 5 21.5zm0 0v17m0 0A2.5 2.5 0 0 1 7.5 19H19",
    settings: "M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zm8 3.5-2-.7a6.7 6.7 0 0 0-.6-1.5l.9-1.9-1.7-1.7-1.9.9a6.7 6.7 0 0 0-1.5-.6L12.5 4h-1l-.7 2a6.7 6.7 0 0 0-1.5.6l-1.9-.9-1.7 1.7.9 1.9a6.7 6.7 0 0 0-.6 1.5l-2 .7v1l2 .7c.1.5.3 1 .6 1.5l-.9 1.9 1.7 1.7 1.9-.9c.5.3 1 .5 1.5.6l.7 2h1l.7-2c.5-.1 1-.3 1.5-.6l1.9.9 1.7-1.7-.9-1.9c.3-.5.5-1 .6-1.5l2-.7z",
    send: "m21 3-7.5 18-3.7-7.8L2 9.5zM9.8 13.2 21 3",
    pulse: "M3 12h4l2-7 5 14 2-7h5",
  };
  return (
    <svg aria-hidden="true" className="icon" viewBox="0 0 24 24">
      <path d={paths[name]} />
    </svg>
  );
}

export default function ChatWorkspace() {
  const [sessionId, setSessionId] = useState("web-session");
  const [message, setMessage] = useState("");
  const [answer, setAnswer] = useState<ChatResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [health, setHealth] = useState<Health>("checking");

  const statusLabel = useMemo(
    () => ({ checking: "Comprobando", online: "Backend conectado", offline: "Backend desconectado" })[health],
    [health],
  );

  useEffect(() => {
    setSessionId("web-" + Math.random().toString(36).slice(2, 10));
    fetch("/api/health")
      .then((response) => {
        setHealth(response.ok ? "online" : "offline");
      })
      .catch(() => setHealth("offline"));
  }, []);

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = message.trim();
    if (!trimmed || trimmed.length > 500) return;

    setLoading(true);
    setError("");
    try {
      const response = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ session_id: sessionId, mensaje: trimmed }),
      });
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload?.error?.mensaje ?? "No se pudo procesar la pregunta.");
      }
      setAnswer(payload as ChatResponse);
      setMessage("");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Error de conexión.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">H</span><span>HydroGest</span></div>
        <nav className="nav" aria-label="Navegación principal">
          <a className="nav-item" href="#resumen"><Icon name="grid" />Resumen</a>
          <a className="nav-item active" href="#asistente"><Icon name="chat" />Asistente</a>
          <a className="nav-item" href="#conocimiento"><Icon name="book" />Conocimiento</a>
          <a className="nav-item" href="#configuracion"><Icon name="settings" />Configuración</a>
        </nav>
        <div className="sidebar-footer"><span className={`status-dot ${health}`} />{statusLabel}</div>
      </aside>

      <section className="content">
        <header className="topbar">
          <div><p className="eyebrow">Centro de atención</p><h1>Asistente HydroGest</h1></div>
          <div className="user-chip"><span className="avatar">HG</span><span>Operador</span><span className="chevron">⌄</span></div>
        </header>

        <div className="dashboard" id="asistente">
          <div className="welcome-card">
            <div><p className="eyebrow light">Asistente de servicio</p><h2>¿En qué podemos ayudarte?</h2><p>Consulta información de HydroGest y recibe respuestas claras basadas en nuestra base de conocimiento.</p></div>
            <div className="welcome-orb">✦</div>
          </div>
          <div className="metrics">
            <article className="metric-card"><span className="metric-icon blue"><Icon name="pulse" /></span><div><span>Estado del servicio</span><strong>{health === "online" ? "Operativo" : health === "checking" ? "Verificando" : "Sin conexión"}</strong></div></article>
            <article className="metric-card"><span className="metric-icon green"><Icon name="chat" /></span><div><span>Sesión activa</span><strong>Conectada</strong></div></article>
            <article className="metric-card"><span className="metric-icon amber"><Icon name="book" /></span><div><span>Fuentes visibles</span><strong>{answer?.fuentes.length ?? 0}</strong></div></article>
          </div>
          <section className="chat-card">
            <div className="card-heading"><div><h2>Conversación</h2><p>Sesión {sessionId}</p></div><span className="live-badge"><span /> En vivo</span></div>
            <div className="messages" aria-live="polite">
              {!answer && !error && <div className="empty-state"><div className="empty-icon"><Icon name="chat" /></div><h3>Inicia una conversación</h3><p>Escribe una pregunta para consultar al asistente.</p></div>}
              {answer && <div className="message-row"><div className="avatar bot">H</div><div className="bubble"><p>{answer.respuesta}</p>{answer.fuentes.length > 0 && <div className="sources"><strong>Fuentes consultadas</strong>{answer.fuentes.map((source) => <span key={`${source.archivo}-${source.seccion}`}>{source.archivo} · {source.seccion}</span>)}</div>}</div></div>}
              {error && <div className="error-box" role="alert">{error}</div>}
              {loading && <div className="message-row"><div className="avatar bot">H</div><div className="bubble typing"><span /><span /><span /></div></div>}
            </div>
            <form className="composer" onSubmit={sendMessage}><div className="input-wrap"><input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Escribe tu pregunta..." maxLength={500} aria-label="Pregunta" /><span>{message.length}/500</span></div><button type="submit" disabled={loading || !message.trim()} aria-label="Enviar pregunta"><Icon name="send" /></button></form>
          </section>
          <p className="privacy-note">Las consultas se procesan de forma segura. No compartas información sensible.</p>
        </div>
      </section>
    </main>
  );
}
