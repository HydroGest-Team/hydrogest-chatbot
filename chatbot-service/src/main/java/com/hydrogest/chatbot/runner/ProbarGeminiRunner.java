package com.hydrogest.chatbot.runner;

import com.hydrogest.chatbot.config.ChatbotProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runner para ejecutar tareas por comando.
 * El argumento --probar-gemini ejecuta una prueba de embedding y una llamada simple al LLM.
 */
@Component
public class ProbarGeminiRunner implements ApplicationRunner {

    private final ChatbotProperties properties;
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;

    public ProbarGeminiRunner(
            ChatbotProperties properties,
            EmbeddingModel embeddingModel,
            ChatModel chatModel
    ) {
        this.properties = properties;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("probar-gemini")) {
            return;
        }

        System.out.println("Iniciando verificación de conexión y modelos de Google Gemini con Spring AI...");

        // 1. Embedding de prueba
        String frasePrueba = "Prueba de generación de embedding para el chatbot HydroGest.";
        System.out.println("\n1. Generando embedding con modelo: " + properties.getEmbeddingModel());
        System.out.println("   Frase de prueba: «" + frasePrueba + "»");

        try {
            float[] vector = embeddingModel.embed(frasePrueba);
            System.out.println("   [OK] Dimensión del vector generado: " + vector.length);
        } catch (Exception e) {
            System.err.println("   [ERROR] Error al generar embedding: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }

        // 2. Llamada simple al LLM
        String prompt = "Responde con una palabra: hola";
        System.out.println("\n2. Llamando al LLM con modelo: " + properties.getLlmModel());
        System.out.println("   Prompt: «" + prompt + "»");

        try {
            String respuesta = chatModel.call(prompt);
            System.out.println("   [OK] Respuesta del LLM: " + (respuesta != null ? respuesta.trim() : "") + "\n");
        } catch (Exception e) {
            System.err.println("   [ERROR] Error al consultar el LLM: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }

        System.out.println("Prueba de Gemini finalizada exitosamente.");
        System.exit(0);
    }
}
