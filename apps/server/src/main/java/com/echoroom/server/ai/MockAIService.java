package com.echoroom.server.ai;

import org.springframework.stereotype.Service;

@Service
public class MockAIService implements AIService {

    @Override
    public AIReply reply(AIRequest request) {
        String prompt = request.prompt() == null ? "" : request.prompt().trim();
        String text = prompt.isEmpty()
            ? "[mock-ai] Ping received."
            : "[mock-ai] " + prompt;
        return new AIReply(text);
    }
}
