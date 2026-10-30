package com.hydrogest.chatbot.rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Lee los documentos Markdown de conocimiento y genera un chunk por cada
 * sección {@code ##}. Cada chunk incluye el título del documento y de la
 * sección para conservar el contexto durante la recuperación.
 */
@Service
public class ChunkerService {

    private static final int MAX_PALABRAS_POR_CHUNK = 350;

    private static final Pattern COMENTARIO_HTML = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern LINEA_ACTUALIZACION =
            Pattern.compile("(?m)^[ \\t]*Última actualización:[ \\t]*(.*)$");
    private static final Pattern TITULO_DOCUMENTO =
            Pattern.compile("(?m)^#[ \\t]+(.+)$");
    private static final Pattern TITULO_SECCION =
            Pattern.compile("(?m)^##[ \\t]+(.+)$");

    private final Path carpeta;

    public ChunkerService(@Value("${chatbot.conocimiento-dir:../conocimiento}") String carpeta) {
        this.carpeta = Path.of(carpeta);
    }

    public List<Chunk> generarChunks() {
        if (!Files.isDirectory(carpeta)) {
            throw new IllegalStateException(
                    "No existe la carpeta de conocimiento: " + carpeta.toAbsolutePath());
        }

        List<Path> archivos;
        try (Stream<Path> listado = Files.list(carpeta)) {
            archivos = listado
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (archivos.isEmpty()) {
            throw new IllegalStateException(
                    "No hay archivos .md en " + carpeta.toAbsolutePath());
        }

        List<Chunk> chunks = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path archivo : archivos) {
            for (Chunk chunk : procesarArchivo(archivo)) {
                if (!ids.add(chunk.id())) {
                    throw new IllegalStateException("ID de chunk duplicado: " + chunk.id());
                }
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    private List<Chunk> procesarArchivo(Path archivo) {
        String nombre = archivo.getFileName().toString();
        String base = nombre.substring(0, nombre.length() - ".md".length());

        String crudo;
        try {
            crudo = Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String texto = COMENTARIO_HTML.matcher(crudo.replace("\r\n", "\n")).replaceAll("");
        if (texto.toUpperCase(Locale.ROOT).contains("[PENDIENTE]")) {
            throw new IllegalStateException(
                    nombre + " todavía contiene [PENDIENTE]. No se ingiere.");
        }

        var matcher = LINEA_ACTUALIZACION.matcher(texto);
        String actualizacion = matcher.find() ? matcher.group(1).trim() : "";
        texto = LINEA_ACTUALIZACION.matcher(texto).replaceAll("");

        var tituloDocumento = TITULO_DOCUMENTO.matcher(texto);
        if (!tituloDocumento.find() || tituloDocumento.group(1).trim().isBlank()) {
            throw new IllegalStateException(
                    nombre + " no tiene título de documento (línea que empiece con '# ').");
        }
        String documento = tituloDocumento.group(1).trim();

        var titulosSeccion = TITULO_SECCION.matcher(texto);
        List<int[]> rangosSeccion = new ArrayList<>();
        List<String> titulos = new ArrayList<>();
        while (titulosSeccion.find()) {
            titulos.add(titulosSeccion.group(1).trim());
            rangosSeccion.add(new int[] {titulosSeccion.end(), titulosSeccion.start()});
        }

        if (rangosSeccion.isEmpty()) {
            throw new IllegalStateException(nombre + " no tiene secciones '## '.");
        }

        List<Chunk> resultado = new ArrayList<>();
        int orden = 0;
        for (int i = 0; i < rangosSeccion.size(); i++) {
            String titulo = titulos.get(i);
            int inicioCuerpo = rangosSeccion.get(i)[0];
            int finCuerpo = i + 1 < rangosSeccion.size()
                    ? rangosSeccion.get(i + 1)[1]
                    : texto.length();
            String contenido = texto.substring(inicioCuerpo, finCuerpo).trim();
            if (titulo.isBlank()) {
                throw new IllegalStateException(nombre + ": el título de la sección está vacío.");
            }
            if (contenido.isBlank()) {
                throw new IllegalStateException(
                        nombre + ": la sección '" + titulo + "' está vacía.");
            }

            List<String> partes = dividir(contenido);
            for (int parteIndex = 0; parteIndex < partes.size(); parteIndex++) {
                orden++;
                String id = base + "::" + slug(titulo)
                        + (partes.size() > 1 ? "-parte-" + (parteIndex + 1) : "");
                String textoChunk = "Documento: " + documento
                        + "\nSección: " + titulo + "\n\n" + partes.get(parteIndex);

                resultado.add(new Chunk(
                        id,
                        nombre,
                        documento,
                        titulo,
                        actualizacion,
                        orden,
                        textoChunk));
            }
        }
        return resultado;
    }

    private List<String> dividir(String contenido) {
        if (contarPalabras(contenido) <= MAX_PALABRAS_POR_CHUNK) {
            return List.of(contenido);
        }

        List<String> partes = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        int palabrasActual = 0;
        for (String parrafo : contenido.split("\n\\s*\n")) {
            int palabras = contarPalabras(parrafo);
            if (palabrasActual > 0
                    && palabrasActual + palabras > MAX_PALABRAS_POR_CHUNK) {
                partes.add(actual.toString().trim());
                actual.setLength(0);
                palabrasActual = 0;
            }
            actual.append(parrafo).append("\n\n");
            palabrasActual += palabras;
        }
        if (actual.length() > 0) {
            partes.add(actual.toString().trim());
        }
        return partes;
    }

    static int contarPalabras(String texto) {
        String limpio = texto.trim();
        return limpio.isEmpty() ? 0 : limpio.split("\\s+").length;
    }

    static String slug(String titulo) {
        String slug = Normalizer.normalize(titulo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-+$", "");
        }
        return slug;
    }
}
