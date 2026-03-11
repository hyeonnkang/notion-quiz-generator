package com.notionquiz.generator.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class QuizGenerateResponse {
    private String pageId;
    private List<QuizItemResponse> quizzes;
}
