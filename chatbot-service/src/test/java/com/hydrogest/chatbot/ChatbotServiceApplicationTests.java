package com.hydrogest.chatbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"chatbot.gemini-api-key=test-gemini-key-12345",
		"chatbot.service-api-key=test-service-key-12345",
		"spring.ai.google.genai.api-key=test-gemini-key-12345",
		"spring.ai.google.genai.embedding.api-key=test-gemini-key-12345"
})
class ChatbotServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
