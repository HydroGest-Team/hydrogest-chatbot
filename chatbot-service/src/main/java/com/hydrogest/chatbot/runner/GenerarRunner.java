package com.hydrogest.chatbot.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hydrogest.chatbot.rag.RagService;
import com.hydrogest.chatbot.rag.RagService.Fuente;
import com.hydrogest.chatbot.rag.RagService.Respuesta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Genera respuestas para el banco completo de evaluación.
 */
@Component
public class GenerarRunner implements ApplicationRunner {

    private final RagService rag;
    private final ObjectMapper objectMapper;
    private final Path preguntasPath;

    public GenerarRunner(
            RagService rag,
            ObjectMapper objectMapper,
            @Value("${chatbot.evaluacion-path:../evaluacion/preguntas.json}")
                    String preguntasPath) {
        this.rag = rag;
        this.objectMapper = objectMapper;
        this.preguntasPath = Path.of(preguntasPath);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("generar")) {
            return;
        }
        List<Pregunta> preguntas = cargarPreguntas();
        for (int i = 0; i < preguntas.size(); i++) {
            Pregunta pregunta = preguntas.get(i);
            if (pregunta.pregunta() == null || pregunta.pregunta().isBlank()) {
                throw new IllegalArgumentException("La pregunta " + (i + 1) + " está vacía.");
            }
            Respuesta respuesta = rag.responder(pregunta.pregunta());
            System.out.printf("%n%d. %s%nRespuesta: %s%n", i + 1,
                    pregunta.pregunta(), respuesta.respuesta());
            System.out.printf("Similitud máxima: %.4f%n", respuesta.similitudMaxima());
            System.out.println("Fuentes:");
            if (respuesta.fuentes().isEmpty()) {
                System.out.println("  Ninguna.");
            } else {
                for (Fuente fuente : respuesta.fuentes()) {
                    System.out.printf("  - %s :: %s%n", fuente.archivo(), fuente.seccion());
                }
            }
            if (pregunta.datosClave() != null && !pregunta.datosClave().isEmpty()) {
                System.out.println("Datos clave: " + String.join(" | ", pregunta.datosClave()));
            }
            if (pregunta.nota() != null && !pregunta.nota().isBlank()) {
                System.out.println("Nota: " + pregunta.nota());
            }
        }
    }

    private List<Pregunta> cargarPreguntas() {
        try {
            return objectMapper.readValue(Files.readString(preguntasPath),
                    new TypeReference<List<Pregunta>>() {});
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException(
                    "No se pudo leer '" + preguntasPath.toAbsolutePath() + "': "
                            + error.getMessage(), error);
        }
    }

    private record Pregunta(
            String pregunta,
            @JsonProperty("datos_clave") List<String> datosClave,
            String nota) {}
}
