package com.hydrogest.chatbot.api;

import java.util.List;

public record ChatResponse(String respuesta, List<Fuente> fuentes) {

    public record Fuente(String archivo, String seccion) {}
}
