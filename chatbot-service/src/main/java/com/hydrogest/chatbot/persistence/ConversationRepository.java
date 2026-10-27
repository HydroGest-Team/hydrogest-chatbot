package com.hydrogest.chatbot.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.hydrogest.chatbot.config.ChatbotProperties;
import com.hydrogest.chatbot.rag.RagService;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

@Repository
public class ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationRepository.class);

    private final ChatbotProperties properties;
    private final DynamoDbTable<ConversationItem> table;

    public ConversationRepository(
            ChatbotProperties properties,
            DynamoDbEnhancedClient enhancedClient) {
        this.properties = properties;
        this.table = enhancedClient.table(
                properties.getDynamodbTable(), TableSchema.fromBean(ConversationItem.class));
    }

    public void guardar(String sessionId, String pregunta, RagService.Respuesta respuesta,
            long latenciaMs) {
        try {
            ConversationItem item = new ConversationItem();
            Instant ahora = Instant.now();
            item.setSessionId(sessionId);
            item.setTimestamp(ahora.toString());
            item.setPregunta(pregunta);
            item.setRespuesta(respuesta.respuesta());
            item.setFuentes(respuesta.fuentes().stream()
                    .map(fuente -> fuente.archivo() + " :: " + fuente.seccion())
                    .toList());
            item.setSimilitudMaxima(respuesta.similitudMaxima());
            item.setUsoLlm(respuesta.usoLlm());
            item.setLatenciaMs(latenciaMs);
            item.setTtl(ahora.plus(properties.getDynamodbTtlDays(), ChronoUnit.DAYS)
                    .getEpochSecond());
            table.putItem(item);
        } catch (Exception error) {
            log.warn("No se pudo guardar el intercambio en DynamoDB: {}",
                    error.getMessage());
        }
    }

    public void crearTablaSiEstaHabilitado() {
        if (!properties.isDynamodbCreateTable()) {
            return;
        }
        try {
            table.createTable();
            log.info("Tabla DynamoDB '{}' creada o ya existente.", properties.getDynamodbTable());
        } catch (ResourceInUseException error) {
            log.debug("La tabla DynamoDB '{}' ya existe.", properties.getDynamodbTable());
        } catch (Exception error) {
            log.warn("No se pudo crear la tabla DynamoDB '{}': {}",
                    properties.getDynamodbTable(), error.getMessage());
        }
    }
}
