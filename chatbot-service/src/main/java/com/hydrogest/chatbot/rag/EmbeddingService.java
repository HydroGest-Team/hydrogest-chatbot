package com.hydrogest.chatbot.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.stereotype.Service;

import com.hydrogest.chatbot.config.ChatbotProperties;

/**
 * Genera embeddings de documentos con Gemini en lotes pequeños.
 */
@Service
public class EmbeddingService {

    private static final int TAMANO_LOTE = 16;
    private static final int MAX_REINTENTOS = 4;
    private static final long BACKOFF_INICIAL_MS = 1_000L;

    private final EmbeddingModel embeddingModel;
    private final ChatbotProperties properties;

    public EmbeddingService(EmbeddingModel embeddingModel, ChatbotProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    public List<float[]> embedDocuments(List<String> textos) {
        if (textos == null || textos.isEmpty()) {
            return List.of();
        }
        if (textos.stream().anyMatch(texto -> texto == null || texto.isBlank())) {
            throw new IllegalArgumentException(
                    "Todos los textos de documentos deben ser cadenas no vacías.");
        }

        List<float[]> embeddings = new ArrayList<>(textos.size());
        for (int inicio = 0; inicio < textos.size(); inicio += TAMANO_LOTE) {
            int fin = Math.min(inicio + TAMANO_LOTE, textos.size());
            List<String> lote = textos.subList(inicio, fin);
            embeddings.addAll(embedWithRetry(
                    lote, GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT));
            System.out.printf("Embeddings: %d/%d%n", fin, textos.size());
        }

        if (embeddings.size() != textos.size()) {
            throw new EmbeddingException(
                    "Gemini devolvió " + embeddings.size()
                            + " embeddings para " + textos.size() + " documentos.");
        }
        return embeddings;
    }

    public float[] embedQuery(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(
                    "El texto de consulta debe ser una cadena no vacía.");
        }
        List<float[]> embeddings = embedWithRetry(
                List.of(texto), GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_QUERY);
        return embeddings.get(0);
    }

    private List<float[]> embedWithRetry(
            List<String> textos, GoogleGenAiTextEmbeddingOptions.TaskType tipoTarea) {
        RuntimeException ultimoError = null;
        for (int intento = 0; intento <= MAX_REINTENTOS; intento++) {
            try {
                GoogleGenAiTextEmbeddingOptions opciones =
                        GoogleGenAiTextEmbeddingOptions.builder()
                                .model(properties.getEmbeddingModel())
                                .taskType(tipoTarea)
                                .dimensions(properties.getEmbeddingDim())
                                .build();
                EmbeddingResponse respuesta =
                        embeddingModel.call(new EmbeddingRequest(textos, opciones));
                if (respuesta == null || respuesta.getResults() == null
                        || respuesta.getResults().size() != textos.size()) {
                    throw new EmbeddingException(
                            "Gemini devolvió una cantidad inesperada de embeddings.");
                }

                List<float[]> resultado = new ArrayList<>(textos.size());
                for (Embedding embedding : respuesta.getResults()) {
                    float[] vector = embedding.getOutput();
                    validarYNormalizar(vector);
                    resultado.add(vector);
                }
                return resultado;
            } catch (RuntimeException error) {
                ultimoError = error;
                if (intento >= MAX_REINTENTOS || !esReintentable(error)) {
                    throw new EmbeddingException(
                            "No se pudo generar el embedding: " + error.getMessage(), error);
                }
                long espera = BACKOFF_INICIAL_MS * (1L << intento)
                        + ThreadLocalRandom.current().nextLong(250L);
                System.err.printf(
                        Locale.ROOT,
                        "[ADVERTENCIA] Error transitorio al generar embeddings; "
                                + "reintentando en %d ms (%d/%d).%n",
                        espera,
                        intento + 1,
                        MAX_REINTENTOS);
                dormir(espera);
            }
        }
        throw new EmbeddingException("No se pudo generar el embedding.", ultimoError);
    }

    private void validarYNormalizar(float[] vector) {
        if (vector == null || vector.length != properties.getEmbeddingDim()) {
            throw new EmbeddingException(
                    "Gemini devolvió un vector de dimensión "
                            + (vector == null ? 0 : vector.length)
                            + "; se esperaba " + properties.getEmbeddingDim() + ".");
        }

        double norma = 0.0;
        for (float valor : vector) {
            norma += (double) valor * valor;
        }
        norma = Math.sqrt(norma);
        if (!Double.isFinite(norma) || norma == 0.0) {
            throw new EmbeddingException("Gemini devolvió un vector no normalizable.");
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norma);
        }
    }

    private static boolean esReintentable(Throwable error) {
        Throwable actual = error;
        while (actual != null) {
            String mensaje = String.valueOf(actual.getMessage()).toLowerCase(Locale.ROOT);
            String nombre = actual.getClass().getName().toLowerCase(Locale.ROOT);
            if (mensaje.contains("429")
                    || mensaje.contains("rate limit")
                    || mensaje.contains("resource exhausted")
                    || mensaje.contains("timeout")
                    || mensaje.contains("temporar")
                    || mensaje.contains("unavailable")
                    || mensaje.contains("connection")
                    || nombre.contains("retryable")) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException(
                    "La espera para reintentar embeddings fue interrumpida.", interrupted);
        }
    }

    public static class EmbeddingException extends RuntimeException {
        public EmbeddingException(String mensaje) {
            super(mensaje);
        }

        public EmbeddingException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
