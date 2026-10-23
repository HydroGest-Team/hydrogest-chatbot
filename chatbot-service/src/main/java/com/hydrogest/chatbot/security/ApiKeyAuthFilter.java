package com.hydrogest.chatbot.security;

import com.hydrogest.chatbot.config.ChatbotProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro HTTP que valida el encabezado X-API-Key contra SERVICE_API_KEY.
 * Los endpoints públicos como /health están exentos de validación.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    private final ChatbotProperties properties;

    public ApiKeyAuthFilter(ChatbotProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Permitir preflight CORS sin validar la API Key
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Endpoints públicos y de infraestructura
        if (path.equals("/health") || path.startsWith("/actuator") || path.equals("/error")) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        if (apiKeyHeader == null || apiKeyHeader.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"Falta el encabezado de autenticación X-API-Key.\"}");
            return;
        }

        String configuredKey = properties.getServiceApiKey();
        if (configuredKey == null || !configuredKey.equals(apiKeyHeader.trim())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"X-API-Key inválida o no autorizada.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
