package com.hydrogest.chatbot.api;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hydrogest.chatbot.controller.ChatController.RateLimitExceededException;
import com.hydrogest.chatbot.llm.LlmClient.LlmException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException error) {
        String mensaje = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .collect(Collectors.joining(" "));
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", mensaje);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformedRequest() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "El cuerpo de la solicitud debe ser JSON válido.");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> rateLimit() {
        return response(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Se alcanzó el límite de peticiones. Intenta de nuevo más tarde.");
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ApiError> llmUnavailable() {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE",
                "El servicio de respuestas no está disponible.");
    }

    private static ResponseEntity<ApiError> response(
            HttpStatus status, String codigo, String mensaje) {
        return ResponseEntity.status(status).body(
                new ApiError(new ApiError.Detalle(codigo, mensaje)));
    }
}
