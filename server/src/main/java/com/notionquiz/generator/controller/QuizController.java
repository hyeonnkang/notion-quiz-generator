package com.notionquiz.generator.controller;

import com.notionquiz.generator.service.QuizService;
import com.notionquiz.generator.dto.QuizGenerateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/generate")
    public String generateQuiz(@RequestBody QuizGenerateRequest request) {
        String pageId = request == null ? null : request.getPageId();

        try {
            String result = quizService.generateQuiz(pageId);
            return result;
        } catch (RuntimeException e) {
            throw e;
        }
    }
}
