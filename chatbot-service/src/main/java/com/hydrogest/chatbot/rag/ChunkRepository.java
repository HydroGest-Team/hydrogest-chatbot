package com.hydrogest.chatbot.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio para la tabla {@code chunk}.
 *
 * <p>Todas las operaciones usan {@link JdbcTemplate} con consultas parametrizadas.
 * La búsqueda vectorial emplea el operador de distancia coseno {@code <=>} de pgvector
 * y devuelve la similitud como {@code 1 - distancia}, ordenado de mayor a menor similitud.
 */
@Repository
public class ChunkRepository {

    private static final Logger log = LoggerFactory.getLogger(ChunkRepository.class);

    private final JdbcTemplate jdbc;

    public ChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------------------
    // Mapper de fila (sin la columna embedding)
    // -------------------------------------------------------------------------

    private static final RowMapper<Chunk> CHUNK_ROW_MAPPER = (rs, rowNum) -> new Chunk(
            rs.getString("id"),
            rs.getString("archivo"),
            rs.getString("documento"),
            rs.getString("seccion"),
            rs.getString("actualizacion"),
            rs.getInt("orden"),
            rs.getString("texto")
    );

    // -------------------------------------------------------------------------
    // Operaciones principales
    // -------------------------------------------------------------------------

    /**
     * Elimina todos los registros de la tabla chunk (reinicio completo del índice).
     */
    @Transactional
    public void reiniciar() {
        int filas = jdbc.update("DELETE FROM chunk");
        log.info("ChunkRepository.reiniciar(): {} filas eliminadas", filas);
    }

    /**
     * Inserta un chunk junto con su vector de embedding.
     *
     * @param chunk     Metadatos y texto del fragmento.
     * @param embedding Vector de embedding (debe tener longitud = EMBEDDING_DIM).
     */
    @Transactional
    public void insertar(Chunk chunk, float[] embedding) {
        String vectorLiteral = toVectorLiteral(embedding);
        jdbc.update(
                """
                INSERT INTO chunk (id, archivo, documento, seccion, actualizacion, orden, texto, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (id) DO UPDATE
                    SET archivo       = EXCLUDED.archivo,
                        documento     = EXCLUDED.documento,
                        seccion       = EXCLUDED.seccion,
                        actualizacion = EXCLUDED.actualizacion,
                        orden         = EXCLUDED.orden,
                        texto         = EXCLUDED.texto,
                        embedding     = EXCLUDED.embedding
                """,
                chunk.id(),
                chunk.archivo(),
                chunk.documento(),
                chunk.seccion(),
                chunk.actualizacion(),
                chunk.orden(),
                chunk.texto(),
                vectorLiteral
        );
    }

    /**
     * Reemplaza el índice completo dentro de una única transacción.
     */
    @Transactional
    public void reemplazarTodo(List<Chunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    "La cantidad de chunks y embeddings debe coincidir.");
        }

        reiniciar();
        List<Object[]> parametros = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            parametros.add(new Object[] {
                    chunk.id(),
                    chunk.archivo(),
                    chunk.documento(),
                    chunk.seccion(),
                    chunk.actualizacion(),
                    chunk.orden(),
                    chunk.texto(),
                    toVectorLiteral(embeddings.get(i))
            });
        }

        jdbc.batchUpdate(
                """
                INSERT INTO chunk (id, archivo, documento, seccion, actualizacion, orden, texto, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector)
                """,
                parametros);
        log.info("ChunkRepository.reemplazarTodo(): {} chunks insertados", chunks.size());
    }

    /**
     * Cuenta el número total de chunks almacenados.
     *
     * @return Número de filas en la tabla chunk.
     */
    public int contar() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM chunk", Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Busca los {@code k} chunks más cercanos al vector de consulta usando distancia coseno.
     *
     * <p>Devuelve similitud = 1 − distancia, de mayor a menor.
     *
     * @param embeddingConsulta Vector de la consulta (float[]).
     * @param k                 Número máximo de resultados a devolver.
     * @return Lista de {@link ChunkResultado} ordenada por similitud descendente.
     */
    public List<ChunkResultado> buscar(float[] embeddingConsulta, int k) {
        String vectorLiteral = toVectorLiteral(embeddingConsulta);
        return jdbc.query(
                """
                SELECT id, archivo, documento, seccion, actualizacion, orden, texto,
                       1 - (embedding <=> ?::vector) AS similitud
                FROM chunk
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> new ChunkResultado(
                        CHUNK_ROW_MAPPER.mapRow(rs, rowNum),
                        rs.getDouble("similitud")
                ),
                vectorLiteral,
                vectorLiteral,
                k
        );
    }

    // -------------------------------------------------------------------------
    // Utilidades internas
    // -------------------------------------------------------------------------

    /**
     * Convierte un array de floats al literal de vector que acepta PostgreSQL/pgvector:
     * {@code [0.1,0.2,...,0.n]}.
     */
    private static String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(embedding[i]));
        }
        return literal.append(']').toString();
    }
}
