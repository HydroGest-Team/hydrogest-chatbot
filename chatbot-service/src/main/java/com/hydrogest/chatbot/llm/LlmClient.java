package com.hydrogest.chatbot.llm;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

import com.hydrogest.chatbot.config.ChatbotProperties;

/**
 * Cliente de generación de respuestas con Gemini.
 */
@Service
public class LlmClient {

    private static final double TEMPERATURA = 0.2;
    private static final int MAX_TOKENS = 512;

    private final ChatModel chatModel;
    private final ChatbotProperties properties;

    public LlmClient(ChatModel chatModel, ChatbotProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    public String generar(String sistema, String contexto, String pregunta) {
        validarTexto(sistema, "sistema");
        validarTexto(contexto, "contexto");
        validarTexto(pregunta, "pregunta");

        GoogleGenAiChatOptions opciones = GoogleGenAiChatOptions.builder()
                .model(properties.getLlmModel())
                .temperature(TEMPERATURA)
                .candidateCount(1)
                .maxOutputTokens(MAX_TOKENS)
                .build();
        Prompt prompt = new Prompt(
                List.of(new SystemMessage(sistema), new UserMessage(
                        "<contexto>\n" + contexto + "\n</contexto>\n\n"
                                + "<pregunta>\n" + pregunta + "\n</pregunta>")),
                opciones);

        try {
            ChatResponse respuesta = chatModel.call(prompt);
            if (respuesta == null || respuesta.getResult() == null
                    || respuesta.getResult().getOutput() == null) {
                throw new LlmBlockedException("Gemini no devolvió contenido utilizable.");
            }
            String texto = respuesta.getResult().getOutput().getText();
            if (texto == null || texto.isBlank()) {
                throw new LlmBlockedException("Gemini bloqueó o dejó vacía la respuesta.");
            }
            return texto.trim();
        } catch (LlmException error) {
            throw error;
        } catch (RuntimeException error) {
            throw clasificar(error);
        }
    }

    private static void validarTexto(String texto, String nombre) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El " + nombre + " no puede estar vacío.");
        }
    }

    private static LlmException clasificar(Throwable error) {
        if (contiene(error, "429", "rate limit", "resource exhausted", "too many requests")) {
            return new LlmRateLimitException(
                    "Gemini rechazó la solicitud por límite de tasa.", error);
        }
        if (esTimeout(error) || contiene(error, "timeout", "timed out", "deadline exceeded")) {
            return new LlmTimeoutException("La solicitud a Gemini excedió el tiempo límite.", error);
        }
        if (contiene(error, "blocked", "block reason", "safety", "recitation", "prohibited")) {
            return new LlmBlockedException("Gemini bloqueó la respuesta por sus filtros.", error);
        }
        return new LlmException("No se pudo generar la respuesta con Gemini.", error);
    }

    private static boolean esTimeout(Throwable error) {
        Throwable actual = error;
        while (actual != null) {
            if (actual instanceof TimeoutException || actual instanceof SocketTimeoutException
                    || actual instanceof IOException) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }

    private static boolean contiene(Throwable error, String... fragmentos) {
        Throwable actual = error;
        while (actual != null) {
            String mensaje = String.valueOf(actual.getMessage()).toLowerCase(Locale.ROOT);
            for (String fragmento : fragmentos) {
                if (mensaje.contains(fragmento)) {
                    return true;
                }
            }
            actual = actual.getCause();
        }
        return false;
    }

    public static class LlmException extends RuntimeException {
        public LlmException(String mensaje) {
            super(mensaje);
        }

        public LlmException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    public static final class LlmRateLimitException extends LlmException {
        public LlmRateLimitException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    public static final class LlmTimeoutException extends LlmException {
        public LlmTimeoutException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    public static final class LlmBlockedException extends LlmException {
        public LlmBlockedException(String mensaje) {
            super(mensaje);
        }

        public LlmBlockedException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
