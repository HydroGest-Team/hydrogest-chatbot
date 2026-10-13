"""Ingesta de conocimiento: chunking, embeddings y persistencia vectorial."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.services.chunker import ChunkingError, build_chunks
from app.services.embeddings import EmbeddingError, embed_documents
from app.services.vector_store import agregar_chunks, contar, reiniciar_coleccion


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Lee y divide el conocimiento de HydroGest.")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="solo lee y muestra los chunks; no llama APIs ni escribe archivos",
    )
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    try:
        chunks, warnings = build_chunks()
    except ChunkingError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 1

    if not args.dry_run:
        try:
            embeddings = embed_documents([chunk["texto"] for chunk in chunks])
            reiniciar_coleccion()
            agregar_chunks(chunks, embeddings)
            stored_count = contar()
        except (EmbeddingError, ValueError, RuntimeError) as exc:
            print(f"[ERROR] No se pudo completar la ingesta: {exc}", file=sys.stderr)
            return 1
        dimension = len(embeddings[0]) if embeddings else 0
        print(f"Chunks guardados: {stored_count}")
        print(f"Dimensión de los vectores: {dimension}")
        if stored_count != len(chunks):
            print(
                f"[ERROR] Chroma contiene {stored_count} chunks, se esperaban {len(chunks)}.",
                file=sys.stderr,
            )
            return 1
        print("Verificación de cantidad: OK")
        return 0

    files = {chunk["metadata"]["archivo"] for chunk in chunks}
    counts = [len(chunk["texto"].split()) for chunk in chunks]
    average = sum(counts) / len(counts) if counts else 0

    print(f"Archivos: {len(files)}")
    print(f"Chunks: {len(chunks)}")
    print(
        "Palabras por chunk: "
        f"mínimo={min(counts) if counts else 0}, "
        f"promedio={average:.1f}, "
        f"máximo={max(counts) if counts else 0}"
    )
    print("\nEjemplos:")
    for index, chunk in enumerate(chunks[:3], start=1):
        print(f"\n--- Chunk de ejemplo {index}: {chunk['id']} ---")
        print(chunk["texto"])
        print(f"Metadata: {chunk['metadata']}")

    print("\nAdvertencias:")
    if warnings:
        for warning in warnings:
            print(f"- {warning}")
    else:
        print("- Ninguna.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
