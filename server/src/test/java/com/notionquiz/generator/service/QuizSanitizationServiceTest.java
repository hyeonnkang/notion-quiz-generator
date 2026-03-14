package com.notionquiz.generator.service;

import com.notionquiz.generator.dto.QuizItemResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizSanitizationServiceTest {

    private final QuizSanitizationService quizSanitizationService = new QuizSanitizationService();

    @Test
    void sanitizeQuizItems_removesDuplicateQuestionsAndInvalidItems() {
        QuizItemResponse first = quizItem(
            " 핵심 개념은 무엇인가? ",
            List.of("선택지 A", "선택지 B", "선택지 C", "선택지 D"),
            "선택지 A"
        );
        QuizItemResponse duplicateQuestion = quizItem(
            "핵심   개념은 무엇인가?",
            List.of("다른 A", "다른 B", "다른 C", "다른 D"),
            "다른 B"
        );
        QuizItemResponse missingChoices = quizItem(
            "선택지가 부족한 문제",
            List.of("A", "B", "C"),
            "A"
        );
        QuizItemResponse answerNotInChoices = quizItem(
            "정답이 보기와 불일치",
            List.of("A", "B", "C", "D"),
            "정답"
        );
        QuizItemResponse second = quizItem(
            "두 번째 핵심 내용은?",
            List.of("옵션 1", "옵션 2", "옵션 3", "옵션 4"),
            "옵션 2"
        );
        QuizItemResponse third = quizItem(
            "세 번째 핵심 내용은?",
            List.of("값 1", "값 2", "값 3", "값 4"),
            "값 4"
        );

        List<QuizItemResponse> result = quizSanitizationService.sanitizeQuizItems(
            List.of(first, duplicateQuestion, missingChoices, answerNotInChoices, second, third)
        );

        assertThat(result).hasSize(3);
        assertThat(result).extracting(QuizItemResponse::getQuestion)
            .containsExactly("핵심 개념은 무엇인가?", "두 번째 핵심 내용은?", "세 번째 핵심 내용은?");
        assertThat(result.getFirst().getAnswer()).isEqualTo("선택지 A");
    }

    @Test
    void sanitizeQuizItems_keepsOnlyFirstThreeValidItems() {
        List<QuizItemResponse> result = quizSanitizationService.sanitizeQuizItems(List.of(
            quizItem("문항 1", List.of("A1", "B1", "C1", "D1"), "A1"),
            quizItem("문항 2", List.of("A2", "B2", "C2", "D2"), "B2"),
            quizItem("문항 3", List.of("A3", "B3", "C3", "D3"), "C3"),
            quizItem("문항 4", List.of("A4", "B4", "C4", "D4"), "D4")
        ));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(QuizItemResponse::getQuestion)
            .containsExactly("문항 1", "문항 2", "문항 3");
    }

    private QuizItemResponse quizItem(String question, List<String> choices, String answer) {
        QuizItemResponse quizItem = new QuizItemResponse();
        quizItem.setQuestion(question);
        quizItem.setChoices(choices);
        quizItem.setAnswer(answer);
        return quizItem;
    }
}
