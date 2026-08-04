package com.yourorg.generalpurposemcp.domain;

import org.springframework.stereotype.Service;

/**
 * Business logic for greetings. Lives in {@code domain}: no imports from
 * {@code api} or {@code infra}, no framework coupling beyond {@code @Service}.
 */
@Service
public class GreetingService {

    public String greet(String name) {
        if (name == null || name.isBlank()) {
            return "Hello, World!";
        }
        return "Hello, %s! Greetings from general-purpose-mcp.".formatted(name.strip());
    }
}
