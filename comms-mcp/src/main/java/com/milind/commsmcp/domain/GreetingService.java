package com.milind.commsmcp.domain;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public String greet(String name) {
        if (name == null || name.isBlank()) {
            return "Hello, World!";
        }
        return "Hello, %s! Greetings from comms-mcp.".formatted(name.strip());
    }
}
