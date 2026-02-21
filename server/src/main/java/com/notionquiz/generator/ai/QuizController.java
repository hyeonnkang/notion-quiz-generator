package com.notionquiz.generator.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuizController {

    private final ChatClient chatClient;

    public QuizController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/api/quiz")
    public String sendMessage() {
        return chatClient.prompt("HTTP를 한 문장으로 설명해줘.").call().content();
    }
}