package com.hydrogest.chatbot.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hydrogest.chatbot.rag.ChunkResultado;
import com.hydrogest.chatbot.rag.ChunkRepository;
import com.hydrogest.chatbot.rag.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la evaluación de retrieval con {@code --evaluar} o una consulta
 * individual con {@code --pregunta}.
 */
@Component
public class EvaluarRunner implements ApplicationRunner {

    private static final int TOP_K = 5;

    private final EmbeddingService embeddings;
    private final ChunkRepository repository;
    private final ObjectMapper objectMapper;
    private final Path preguntasPath;

    public EvaluarRunner(
            EmbeddingService embeddings,
            ChunkRepository repository,
            ObjectMapper objectMapper,
            @Value("${chatbot.evaluacion-path:../evaluacion/preguntas.json}")
                    String preguntasPath) {
        this.embeddings = embeddings;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.preguntasPath = Path.of(preguntasPath);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("pregunta")) {
            ejecutarPregunta(obtenerPregunta(args));
        } else if (args.containsOption("evaluar")) {
            ejecutarEvaluacion();
        }
    }

    private static String obtenerPregunta(ApplicationArguments args) {
        List<String> valores = args.getOptionValues("pregunta");
        List<String> argumentos = args.getNonOptionArgs();
        List<String> partes = new ArrayList<>();
        if (valores != null) {
            partes.addAll(valores);
        }
        partes.addAll(argumentos);
        if (partes.isEmpty()) {
            throw new IllegalArgumentException("--pregunta debe contener texto.");
        }
        return String.join(" ", partes);
    }

    private void ejecutarPregunta(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            throw new IllegalArgumentException("--pregunta debe contener texto.");
        }
        System.out.println("Pregunta: " + pregunta);
        imprimirResultados(buscar(pregunta));
    }

    private void ejecutarEvaluacion() {
        List<Pregunta> preguntas = cargarPreguntas();
        int totalAlcance = 0;
        int[] aciertos = new int[3];
        List<Double> similitudesEnAlcance = new ArrayList<>();
        List<Double> similitudesFueraAlcance = new ArrayList<>();
        List<Fallo> fallos = new ArrayList<>();

        for (int indice = 0; indice < preguntas.size(); indice++) {
            Pregunta pregunta = preguntas.get(indice);
            validarPregunta(pregunta, indice + 1);
            List<ChunkResultado> resultados = buscar(pregunta.pregunta());

            System.out.printf("%n%d. %s%n", indice + 1, pregunta.pregunta());
            imprimirResultados(resultados);

            if ("en_alcance".equals(pregunta.tipo())) {
                totalAlcance++;
                double similitud = resultados.isEmpty() ? 0.0 : resultados.get(0).similitud();
                similitudesEnAlcance.add(similitud);
                Integer posicion = encontrarPrimerArchivo(resultados, pregunta.archivosEsperados());
                if (posicion == null) {
                    System.out.println(
                            "  Resultado: FALLA (no aparece un archivo esperado en top-5)");
                    imprimirNota(pregunta);
                    fallos.add(new Fallo(pregunta, resultados));
                } else {
                    if (posicion <= 1) {
                        aciertos[0]++;
                    }
                    if (posicion <= 3) {
                        aciertos[1]++;
                    }
                    if (posicion <= 5) {
                        aciertos[2]++;
                    }
                    String banda = posicion == 1
                            ? "puesto 1"
                            : posicion <= 3 ? "top-3" : "top-5";
                    System.out.printf(
                            "  Resultado: acierto en %s (puesto %d)%n", banda, posicion);
                }
            } else if ("fuera_de_alcance".equals(pregunta.tipo())) {
                double similitud = resultados.isEmpty() ? 0.0 : resultados.get(0).similitud();
                similitudesFueraAlcance.add(similitud);
                ChunkResultado mejor = resultados.isEmpty() ? null : resultados.get(0);
                System.out.printf(
                        "  Fuera de alcance: mejor similitud=%.4f, id=%s%n",
                        similitud,
                        mejor == null ? "sin resultados" : mejor.chunk().id());
            }
        }

        System.out.println("\n=== Resumen ===");
        System.out.printf("Aciertos top-1: %s%n", formatearTasa(aciertos[0], totalAlcance));
        System.out.printf("Aciertos top-3: %s%n", formatearTasa(aciertos[1], totalAlcance));
        System.out.printf("Aciertos top-5: %s%n", formatearTasa(aciertos[2], totalAlcance));
        System.out.println("\nSimilitud del puesto 1:");
        System.out.println("  en_alcance: " + formatearEstadisticas(similitudesEnAlcance));
        System.out.println("  fuera_de_alcance: "
                + formatearEstadisticas(similitudesFueraAlcance));
        System.out.println("\nPreguntas fallidas:");
        if (fallos.isEmpty()) {
            System.out.println("  Ninguna.");
        } else {
            for (Fallo fallo : fallos) {
                System.out.println("\n- " + fallo.pregunta().pregunta());
                imprimirNota(fallo.pregunta());
                imprimirResultados(fallo.resultados());
            }
        }
    }

    private List<Pregunta> cargarPreguntas() {
        try {
            return objectMapper.readValue(
                    Files.readString(preguntasPath),
                    new TypeReference<List<Pregunta>>() {});
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException(
                    "No se pudo leer '" + preguntasPath.toAbsolutePath() + "': "
                            + error.getMessage(), error);
        }
    }

    private List<ChunkResultado> buscar(String pregunta) {
        return repository.buscar(embeddings.embedQuery(pregunta), TOP_K);
    }

    private static void validarPregunta(Pregunta pregunta, int numero) {
        if (pregunta == null || pregunta.pregunta() == null || pregunta.pregunta().isBlank()) {
            throw new IllegalArgumentException(
                    "La pregunta " + numero + " no tiene un campo 'pregunta' válido.");
        }
        if (!"en_alcance".equals(pregunta.tipo())
                && !"fuera_de_alcance".equals(pregunta.tipo())) {
            throw new IllegalArgumentException(
                    "Tipo inválido en la pregunta " + numero + ": " + pregunta.tipo());
        }
    }

    private static Integer encontrarPrimerArchivo(
            List<ChunkResultado> resultados, List<String> archivosEsperados) {
        Set<String> esperados = new HashSet<>(archivosEsperados == null
                ? List.of()
                : archivosEsperados);
        for (int indice = 0; indice < resultados.size(); indice++) {
            String archivo = resultados.get(indice).chunk().archivo();
            int separador = archivo.lastIndexOf('.');
            String base = separador > 0 ? archivo.substring(0, separador) : archivo;
            if (esperados.contains(base)) {
                return indice + 1;
            }
        }
        return null;
    }

    private static void imprimirResultados(List<ChunkResultado> resultados) {
        for (int indice = 0; indice < resultados.size(); indice++) {
            ChunkResultado resultado = resultados.get(indice);
            System.out.printf(
                    "  %d. %s (similitud=%.4f)%n",
                    indice + 1,
                    resultado.chunk().id(),
                    resultado.similitud());
        }
    }

    private static void imprimirNota(Pregunta pregunta) {
        if (pregunta.nota() != null && !pregunta.nota().isBlank()) {
            System.out.println("  Nota: " + pregunta.nota());
        }
    }

    private static String formatearTasa(int aciertos, int total) {
        double porcentaje = total == 0 ? 0.0 : (double) aciertos / total * 100.0;
        return String.format("%d/%d (%.1f%%)", aciertos, total, porcentaje);
    }

    private static String formatearEstadisticas(List<Double> valores) {
        if (valores.isEmpty()) {
            return "sin datos";
        }
        double suma = 0.0;
        double minimo = Double.POSITIVE_INFINITY;
        double maximo = Double.NEGATIVE_INFINITY;
        for (double valor : valores) {
            suma += valor;
            minimo = Math.min(minimo, valor);
            maximo = Math.max(maximo, valor);
        }
        return String.format(
                "mínimo=%.4f, promedio=%.4f, máximo=%.4f",
                minimo,
                suma / valores.size(),
                maximo);
    }

    private record Pregunta(
            String pregunta,
            String tipo,
            @JsonProperty("archivos_esperados")
            List<String> archivosEsperados,
            String nota) {}

    private record Fallo(Pregunta pregunta, List<ChunkResultado> resultados) {}
}
