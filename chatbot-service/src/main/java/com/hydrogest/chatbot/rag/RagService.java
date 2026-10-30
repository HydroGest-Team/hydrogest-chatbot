package com.hydrogest.chatbot.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.hydrogest.chatbot.config.ChatbotProperties;
import com.hydrogest.chatbot.llm.LlmClient;

/**
 * Orquesta retrieval y generación para un turno independiente.
 */
@Service
public class RagService {

    private static final String MENSAJE_SIN_INFORMACION =
            "No tengo esa información. Puedes llamar al 7844-1000 o ir a la oficina.";

    private final EmbeddingService embeddings;
    private final ChunkRepository repository;
    private final LlmClient llmClient;
    private final String sistema;
    private final ChatbotProperties properties;

    public RagService(
            EmbeddingService embeddings,
            ChunkRepository repository,
            LlmClient llmClient,
            ChatbotProperties properties) {
        this.embeddings = embeddings;
        this.repository = repository;
        this.llmClient = llmClient;
        this.properties = properties;
        this.sistema = cargarSistema();
    }

    public Respuesta responder(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            throw new IllegalArgumentException("La pregunta no puede estar vacía.");
        }

        List<ChunkResultado> resultados =
                repository.buscar(embeddings.embedQuery(pregunta), properties.getRagTopK());
        double similitudMaxima = resultados.isEmpty() ? 0.0 : resultados.get(0).similitud();
        List<Fuente> fuentes = resultados.stream()
                .map(resultado -> new Fuente(
                        resultado.chunk().archivo(), resultado.chunk().seccion()))
                .toList();

        if (resultados.isEmpty() || similitudMaxima < properties.getUmbralSimilitud()) {
            return new Respuesta(MENSAJE_SIN_INFORMACION, fuentes, similitudMaxima, false);
        }

        String contexto = construirContexto(resultados);
        String respuesta = llmClient.generar(sistema, contexto, pregunta);
        return new Respuesta(respuesta, fuentes, similitudMaxima, true);
    }

    private static String construirContexto(List<ChunkResultado> resultados) {
        StringBuilder contexto = new StringBuilder();
        for (int i = 0; i < resultados.size(); i++) {
            Chunk chunk = resultados.get(i).chunk();
            contexto.append("Fuente ").append(i + 1).append(":\n")
                    .append("Archivo: ").append(chunk.archivo()).append('\n')
                    .append("Sección: ").append(chunk.seccion()).append('\n')
                    .append(chunk.texto()).append("\n\n");
        }
        return contexto.toString().trim();
    }

    private static String cargarSistema() {
        ClassPathResource recurso = new ClassPathResource("prompts/sistema.md");
        try (InputStream entrada = recurso.getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "No se pudo cargar prompts/sistema.md.", error);
        }
    }

    public record Respuesta(
            String respuesta,
            List<Fuente> fuentes,
            double similitudMaxima,
            boolean usoLlm) {}

    public record Fuente(String archivo, String seccion) {}
}
