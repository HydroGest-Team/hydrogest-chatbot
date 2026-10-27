package com.hydrogest.chatbot.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(ChatbotProperties properties) {
        var builder = DynamoDbClient.builder()
                .region(Region.of(properties.getDynamodbRegion()));
        if (properties.getDynamodbEndpoint() != null
                && !properties.getDynamodbEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getDynamodbEndpoint()));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();
    }
}
