package com.hydrogest.chatbot.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "session_id es obligatorio.")
        @Size(max = 100, message = "session_id no puede superar 100 caracteres.")
        String session_id,
        @NotBlank(message = "mensaje es obligatorio.")
        @Size(max = 500, message = "mensaje debe tener entre 1 y 500 caracteres.")
        String mensaje) {}
