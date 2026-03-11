package com.notionquiz.generator.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class QuizItemResponse {
    private String question;
    private List<String> choices;
    private String answer;
}
