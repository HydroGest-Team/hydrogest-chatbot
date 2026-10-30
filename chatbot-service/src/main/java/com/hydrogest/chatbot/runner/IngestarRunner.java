package com.hydrogest.chatbot.runner;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.hydrogest.chatbot.rag.Chunk;
import com.hydrogest.chatbot.rag.ChunkRepository;
import com.hydrogest.chatbot.rag.ChunkerService;
import com.hydrogest.chatbot.rag.EmbeddingService;

/**
 * Ejecuta la ingesta completa con {@code --ingestar}; {@code --dry-run} solo
 * procesa y muestra los chunks, sin llamar a Gemini ni escribir en PostgreSQL.
 */
@Component
public class IngestarRunner implements ApplicationRunner {

    private final ChunkerService chunker;
    private final EmbeddingService embeddings;
    private final ChunkRepository repository;

    public IngestarRunner(
            ChunkerService chunker,
            EmbeddingService embeddings,
            ChunkRepository repository) {
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("ingestar")) {
            return;
        }

        List<Chunk> chunks = chunker.generarChunks();
        boolean dryRun = args.containsOption("dry-run");

        System.out.println("=== INGESTA DE CONOCIMIENTO ===");
        System.out.println("Chunks generados: " + chunks.size());
        if (dryRun) {
            System.out.println("Modo --dry-run: no se llamó a Gemini ni se escribió en PostgreSQL.");
            return;
        }

        List<float[]> vectors = embeddings.embedDocuments(
                chunks.stream().map(Chunk::texto).toList());
        int dimension = vectors.isEmpty() ? 0 : vectors.get(0).length;
        System.out.println("Dimensión de embeddings: " + dimension);

        repository.reemplazarTodo(chunks, vectors);
        int almacenados = repository.contar();
        System.out.println("Chunks almacenados: " + almacenados);
        if (almacenados != chunks.size()) {
            throw new IllegalStateException(
                    "El conteo en la base (" + almacenados
                            + ") no coincide con los chunks generados (" + chunks.size() + ").");
        }
        System.out.println("[OK] Ingesta completada y conteo verificado.");
    }
}
