package com.hydrogest.chatbot.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "chatbot")
public class ChatbotProperties {

    private String geminiApiKey;
    private String embeddingModel = "gemini-embedding-001";
    private String llmModel = "gemini-3.8-flash";
    private int embeddingDim = 1536;
    private String dbUrl = "jdbc:postgresql://localhost:5432/hydrogest";
    private String dbUser = "postgres";
    private String dbPassword = "";
    private String serviceApiKey;
    private int ragTopK = 4;
    private double umbralSimilitud = 0.65;
    private String conocimientoDir = "../conocimiento";
    private String allowedOrigins = "http://localhost:3000,http://localhost:8000";
    private int rateLimitPerSession = 10;
    private int rateLimitPerIp = 30;

    @PostConstruct
    public void validate() {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            String errorMsg = "\n" +
                    "======================================================================\n" +
                    "[ERROR DE CONFIGURACIÓN]\n" +
                    "La variable de entorno o propiedad 'GEMINI_API_KEY' es obligatoria y no está configurada.\n" +
                    "Por favor define GEMINI_API_KEY en tus variables de entorno o en application-local.properties\n" +
                    "(puedes guiarte con application-local.properties.example).\n" +
                    "======================================================================\n";
            System.err.println(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        if (serviceApiKey == null || serviceApiKey.trim().isEmpty()) {
            String errorMsg = "\n" +
                    "======================================================================\n" +
                    "[ERROR DE CONFIGURACIÓN]\n" +
                    "La variable de entorno o propiedad 'SERVICE_API_KEY' es obligatoria y no está configurada.\n" +
                    "Por favor define SERVICE_API_KEY en tus variables de entorno o en application-local.properties\n" +
                    "(puedes guiarte con application-local.properties.example).\n" +
                    "======================================================================\n";
            System.err.println(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    public List<String> getAllowedOriginsList() {
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public int getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(int embeddingDim) {
        this.embeddingDim = embeddingDim;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public void setDbUrl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public String getServiceApiKey() {
        return serviceApiKey;
    }

    public void setServiceApiKey(String serviceApiKey) {
        this.serviceApiKey = serviceApiKey;
    }

    public int getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(int ragTopK) {
        this.ragTopK = ragTopK;
    }

    public double getUmbralSimilitud() {
        return umbralSimilitud;
    }

    public void setUmbralSimilitud(double umbralSimilitud) {
        this.umbralSimilitud = umbralSimilitud;
    }

    public String getConocimientoDir() {
        return conocimientoDir;
    }

    public void setConocimientoDir(String conocimientoDir) {
        this.conocimientoDir = conocimientoDir;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public int getRateLimitPerSession() {
        return rateLimitPerSession;
    }

    public void setRateLimitPerSession(int rateLimitPerSession) {
        this.rateLimitPerSession = rateLimitPerSession;
    }

    public int getRateLimitPerIp() {
        return rateLimitPerIp;
    }

    public void setRateLimitPerIp(int rateLimitPerIp) {
        this.rateLimitPerIp = rateLimitPerIp;
    }
}
