"""Persistencia de chunks y embeddings en ChromaDB."""

from __future__ import annotations

import os
from collections.abc import Sequence
from typing import Any

import chromadb

COLLECTION_NAME = "conocimiento_hidrogest"
DEFAULT_CHROMA_PATH = "./data/chroma"


def _collection() -> Any:
    client = chromadb.PersistentClient(
        path=os.getenv("CHROMA_PATH", DEFAULT_CHROMA_PATH),
    )
    return client.get_or_create_collection(
        name=COLLECTION_NAME,
        configuration={"hnsw": {"space": "cosine"}},
        embedding_function=None,
    )


def reiniciar_coleccion() -> None:
    """Elimina y recrea la colección para que la ingesta sea idempotente."""

    client = chromadb.PersistentClient(
        path=os.getenv("CHROMA_PATH", DEFAULT_CHROMA_PATH),
    )
    try:
        client.delete_collection(COLLECTION_NAME)
    except Exception as exc:
        if "does not exist" not in str(exc).lower() and "not found" not in str(exc).lower():
            raise RuntimeError(f"No se pudo reiniciar la colección: {exc}") from exc
    client.get_or_create_collection(
        name=COLLECTION_NAME,
        configuration={"hnsw": {"space": "cosine"}},
        embedding_function=None,
    )


def agregar_chunks(chunks: Sequence[dict[str, Any]], embeddings: Sequence[Sequence[float]]) -> None:
    """Guarda chunks con sus embeddings explícitos y metadata asociada."""

    if len(chunks) != len(embeddings):
        raise ValueError("La cantidad de chunks y embeddings debe coincidir.")
    if not chunks:
        return

    _collection().add(
        ids=[str(chunk["id"]) for chunk in chunks],
        documents=[str(chunk["texto"]) for chunk in chunks],
        metadatas=[dict(chunk["metadata"]) for chunk in chunks],
        embeddings=[list(embedding) for embedding in embeddings],
    )


def contar() -> int:
    """Devuelve la cantidad de chunks almacenados."""

    return int(_collection().count())


def buscar(embedding_consulta: Sequence[float], k: int) -> list[dict[str, Any]]:
    """Busca por embedding y convierte distancia coseno a similitud."""

    if not embedding_consulta:
        raise ValueError("El embedding de consulta no puede estar vacío.")
    if k <= 0:
        raise ValueError("k debe ser un entero positivo.")

    collection = _collection()
    available = collection.count()
    if not available:
        return []

    result = collection.query(
        query_embeddings=[list(embedding_consulta)],
        n_results=min(k, available),
        include=["documents", "metadatas", "distances"],
    )
    ids = result.get("ids", [[]])[0]
    documents = result.get("documents", [[]])[0]
    metadatas = result.get("metadatas", [[]])[0]
    distances = result.get("distances", [[]])[0]
    return [
        {
            "id": result_id,
            "texto": document,
            "metadata": metadata or {},
            "similitud": max(0.0, min(1.0, 1.0 - float(distance))),
        }
        for result_id, document, metadata, distance in zip(
            ids, documents, metadatas, distances, strict=True
        )
    ]
