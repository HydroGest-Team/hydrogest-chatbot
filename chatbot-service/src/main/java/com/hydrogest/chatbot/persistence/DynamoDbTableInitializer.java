package com.hydrogest.chatbot.persistence;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

@Component
public class DynamoDbTableInitializer {

    private final ConversationRepository repository;

    public DynamoDbTableInitializer(ConversationRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void initialize() {
        repository.crearTablaSiEstaHabilitado();
    }
}
