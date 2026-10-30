package com.hydrogest.chatbot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"chatbot.gemini-api-key=test-gemini-key-12345",
		"chatbot.service-api-key=test-service-key-12345",
		"spring.ai.google.genai.api-key=test-gemini-key-12345",
		"spring.ai.google.genai.embedding.api-key=test-gemini-key-12345"
})
class HealthAndSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthEndpointShouldBePublicAndReturnStatusOk() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void protectedEndpointWithoutApiKeyShouldReturn401() throws Exception {
		mockMvc.perform(post("/chat"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.codigo").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.error.mensaje")
						.value("Falta el encabezado de autenticación X-API-Key."));
	}

	@Test
	void protectedEndpointWithInvalidApiKeyShouldReturn401() throws Exception {
		mockMvc.perform(post("/chat")
						.header("X-API-Key", "clave-invalida"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.codigo").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.error.mensaje")
						.value("X-API-Key inválida o no autorizada."));
	}

	@Test
	void protectedEndpointWithValidApiKeyShouldPassFilter() throws Exception {
		mockMvc.perform(post("/chat")
						.header("X-API-Key", "test-service-key-12345"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.codigo").value("INVALID_REQUEST"));
	}
}
