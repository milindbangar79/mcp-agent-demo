package com.milind.employeeagent.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employee")
public class EmployeeAgentController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeAgentController.class);

    private final ChatClient chatClient;

    public EmployeeAgentController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** Example: {"question": "Give me the details of employee E001"} */
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@RequestBody AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(new AskResponse("question must not be blank"));
        }
        log.info("Received delegated employee question");
        return ResponseEntity.ok(new AskResponse(chatClient.prompt().user(request.question()).call().content()));
    }

    public record AskRequest(String question) {}

    public record AskResponse(String answer) {}
}
