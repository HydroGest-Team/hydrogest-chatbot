package com.hydrogest.chatbot.api;

public record ApiError(Detalle error) {

    public record Detalle(String codigo, String mensaje) {}
}
