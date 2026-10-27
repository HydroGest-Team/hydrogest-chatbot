package com.hydrogest.chatbot.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydrogest.chatbot.api.ChatRequest;
import com.hydrogest.chatbot.api.ChatResponse;
import com.hydrogest.chatbot.api.RateLimitService;
import com.hydrogest.chatbot.rag.RagService;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final RagService ragService;
    private final RateLimitService rateLimitService;

    public ChatController(RagService ragService, RateLimitService rateLimitService) {
        this.ragService = ragService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        if (!rateLimitService.permitir(request.session_id(), ip)) {
            throw new RateLimitExceededException();
        }

        RagService.Respuesta respuesta = ragService.responder(request.mensaje());
        var fuentes = respuesta.fuentes().stream()
                .map(fuente -> new ChatResponse.Fuente(fuente.archivo(), fuente.seccion()))
                .toList();
        return ResponseEntity.ok(new ChatResponse(respuesta.respuesta(), fuentes));
    }

    public static final class RateLimitExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
