package com.hydrogest.chatbot.rag;

/**
 * Representa un fragmento (chunk) de documento almacenado en la tabla {@code chunk}.
 *
 * @param id           Identificador único (PK textual, ej. SHA-256 del texto).
 * @param archivo      Nombre del archivo fuente (ej. "tarifas.md").
 * @param documento    Título o nombre del documento.
 * @param seccion      Sección o encabezado dentro del documento.
 * @param actualizacion Fecha/versión de la última actualización del documento.
 * @param orden        Posición ordinal del chunk dentro del documento.
 * @param texto        Contenido textual del chunk (NOT NULL).
 */
public record Chunk(
        String id,
        String archivo,
        String documento,
        String seccion,
        String actualizacion,
        int    orden,
        String texto
) {}
