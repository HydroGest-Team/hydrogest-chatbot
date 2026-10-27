package com.hydrogest.chatbot.persistence;

import java.util.List;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class ConversationItem {

    private String sessionId;
    private String timestamp;
    private String pregunta;
    private String respuesta;
    private List<String> fuentes;
    private Double similitudMaxima;
    private Boolean usoLlm;
    private Long latenciaMs;
    private Long ttl;

    @DynamoDbPartitionKey
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @DynamoDbSortKey
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getPregunta() {
        return pregunta;
    }

    public void setPregunta(String pregunta) {
        this.pregunta = pregunta;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }

    public List<String> getFuentes() {
        return fuentes;
    }

    public void setFuentes(List<String> fuentes) {
        this.fuentes = fuentes;
    }

    public Double getSimilitudMaxima() {
        return similitudMaxima;
    }

    public void setSimilitudMaxima(Double similitudMaxima) {
        this.similitudMaxima = similitudMaxima;
    }

    public Boolean getUsoLlm() {
        return usoLlm;
    }

    public void setUsoLlm(Boolean usoLlm) {
        this.usoLlm = usoLlm;
    }

    public Long getLatenciaMs() {
        return latenciaMs;
    }

    public void setLatenciaMs(Long latenciaMs) {
        this.latenciaMs = latenciaMs;
    }

    public Long getTtl() {
        return ttl;
    }

    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }
}
