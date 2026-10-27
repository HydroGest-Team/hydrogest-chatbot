package com.hydrogest.chatbot.rag;

import java.util.List;

/**
 * Resultado de una búsqueda vectorial: el chunk encontrado junto con su
 * similitud coseno respecto a la consulta (valor en [0, 1]; cuanto mayor, más cercano).
 *
 * @param chunk      El fragmento recuperado.
 * @param similitud  Similitud coseno = 1 − distancia coseno (<=> en pgvector).
 */
public record ChunkResultado(Chunk chunk, double similitud) {}
