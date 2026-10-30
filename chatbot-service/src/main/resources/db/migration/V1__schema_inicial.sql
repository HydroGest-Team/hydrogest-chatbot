-- V1__schema_inicial.sql
-- Esquema inicial: extensión pgvector + tabla chunk
-- Sin índice vectorial (volumen pequeño ~60 filas, no necesario)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS chunk (
    id           TEXT        NOT NULL,
    archivo      TEXT        NOT NULL,
    documento    TEXT        NOT NULL,
    seccion      TEXT        NOT NULL DEFAULT '',
    actualizacion TEXT       NOT NULL DEFAULT '',
    orden        INTEGER     NOT NULL DEFAULT 0,
    texto        TEXT        NOT NULL,
    embedding    vector(1536) NOT NULL,
    CONSTRAINT chunk_pkey PRIMARY KEY (id)
);
