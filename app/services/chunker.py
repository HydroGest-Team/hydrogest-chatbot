"""Lectura y división de los documentos Markdown de conocimiento."""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Any
from unicodedata import normalize

DEFAULT_CONOCIMIENTO_DIR = "conocimiento"
MAX_WORDS_PER_CHUNK = 350

_HTML_COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)
_TITLE_RE = re.compile(r"^\s*#\s+(.+?)\s*$")
_SECTION_RE = re.compile(r"^\s*##\s+(.+?)\s*$")
_UPDATE_RE = re.compile(r"^\s*Última actualización:\s*(.*?)\s*$", re.IGNORECASE)


class ChunkingError(ValueError):
    """Error de validación de un documento o de sus chunks."""


def _slug(text: str) -> str:
    without_accents = normalize("NFKD", text.replace("ñ", "n").replace("Ñ", "N")).encode(
        "ascii", "ignore"
    ).decode()
    slug = re.sub(r"[^a-zA-Z0-9]+", "-", without_accents.lower()).strip("-")
    return slug


def _words(text: str) -> int:
    return len(re.findall(r"\S+", text))


def _split_body(body: str) -> list[str]:
    paragraphs = [paragraph.strip() for paragraph in re.split(r"\n\s*\n", body)]
    paragraphs = [paragraph for paragraph in paragraphs if paragraph]
    if not paragraphs:
        return []

    parts: list[str] = []
    current: list[str] = []
    current_words = 0

    for paragraph in paragraphs:
        paragraph_words = paragraph.split()
        if len(paragraph_words) > MAX_WORDS_PER_CHUNK:
            if current:
                parts.append("\n\n".join(current))
                current = []
                current_words = 0
            for start in range(0, len(paragraph_words), MAX_WORDS_PER_CHUNK):
                parts.append(" ".join(paragraph_words[start : start + MAX_WORDS_PER_CHUNK]))
            continue

        if current and current_words + len(paragraph_words) > MAX_WORDS_PER_CHUNK:
            parts.append("\n\n".join(current))
            current = []
            current_words = 0
        current.append(paragraph)
        current_words += len(paragraph_words)

    if current:
        parts.append("\n\n".join(current))
    return parts


def _parse_file(path: Path) -> tuple[str, str, list[tuple[str, str]]]:
    try:
        raw_text = path.read_text(encoding="utf-8-sig")
    except OSError as exc:
        raise ChunkingError(f"No se pudo leer '{path}': {exc}") from exc

    text = _HTML_COMMENT_RE.sub("", raw_text).replace("\r\n", "\n")
    if "[PENDIENTE]" in text:
        raise ChunkingError(f"Se encontró '[PENDIENTE]' en el archivo '{path.name}'.")
    lines = text.splitlines()
    title_matches = [match.group(1).strip() for line in lines if (match := _TITLE_RE.match(line))]
    if not title_matches or not title_matches[0]:
        raise ChunkingError(f"El archivo '{path.name}' no tiene un título '#'.")
    if len(title_matches) > 1:
        raise ChunkingError(f"El archivo '{path.name}' contiene más de un título '#'.")

    document = title_matches[0]
    updates = [match.group(1).strip() for line in lines if (match := _UPDATE_RE.match(line))]
    if len(updates) > 1:
        raise ChunkingError(
            f"El archivo '{path.name}' contiene más de una línea 'Última actualización:'."
        )
    update = updates[0] if updates else ""

    sections: list[tuple[str, str]] = []
    current_title: str | None = None
    current_lines: list[str] = []
    for line in lines:
        section_match = _SECTION_RE.match(line)
        if section_match:
            if current_title is not None:
                sections.append((current_title, "\n".join(current_lines)))
            current_title = section_match.group(1).strip()
            current_lines = []
        elif current_title is not None and not _UPDATE_RE.match(line):
            current_lines.append(line)

    if current_title is not None:
        sections.append((current_title, "\n".join(current_lines)))
    if not sections:
        raise ChunkingError(f"El archivo '{path.name}' no contiene ninguna sección '##'.")

    return document, update, sections


def build_chunks(directory: str | os.PathLike[str] | None = None) -> tuple[list[dict[str, Any]], list[str]]:
    """Lee un directorio y devuelve sus chunks junto con advertencias."""

    root = Path(directory or os.getenv("CONOCIMIENTO_DIR", DEFAULT_CONOCIMIENTO_DIR))
    if not root.is_dir():
        raise ChunkingError(f"La carpeta de conocimiento no existe o no es un directorio: '{root}'.")

    files = sorted(root.glob("*.md"), key=lambda path: path.name.lower())
    if not files:
        raise ChunkingError(f"No se encontraron archivos '.md' en '{root}'.")

    chunks: list[dict[str, Any]] = []
    warnings: list[str] = []
    seen_ids: set[str] = set()
    order = 1

    for path in files:
        document, update, sections = _parse_file(path)
        for section, raw_body in sections:
            body = raw_body.strip()
            if not body:
                raise ChunkingError(
                    f"La sección '{section}' del archivo '{path.name}' está vacía."
                )
            bodies = _split_body(body)
            if len(bodies) > 1:
                warnings.append(
                    f"'{path.name}' / '{section}' se dividió en {len(bodies)} chunks por superar "
                    f"{MAX_WORDS_PER_CHUNK} palabras."
                )

            chunk_id = f"{path.stem}::{_slug(section)}"
            for part_index, part in enumerate(bodies):
                current_id = chunk_id if part_index == 0 else f"{chunk_id}::{part_index + 1}"
                if current_id in seen_ids:
                    raise ChunkingError(f"ID de chunk duplicado: '{current_id}'.")
                seen_ids.add(current_id)
                chunk_text = (
                    f"Documento: {document}\n"
                    f"Sección: {section}\n\n"
                    f"{part}"
                )
                chunks.append(
                    {
                        "id": current_id,
                        "texto": chunk_text,
                        "metadata": {
                            "archivo": path.name,
                            "documento": document,
                            "seccion": section,
                            "actualizacion": update,
                            "orden": order,
                        },
                    }
                )
                order += 1

    return chunks, warnings


def load_chunks(directory: str | os.PathLike[str] | None = None) -> list[dict[str, Any]]:
    """Lee todos los Markdown y devuelve únicamente la lista de chunks."""

    chunks, _ = build_chunks(directory)
    return chunks


chunk_documents = load_chunks
