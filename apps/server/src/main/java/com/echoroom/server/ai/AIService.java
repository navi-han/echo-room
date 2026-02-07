package com.echoroom.server.ai;

public interface AIService {

    AIReply reply(AIRequest request);

    record AIRequest(String roomId, String userId, String prompt) {
    }

    record AIReply(String text) {
    }
}
