package com.notionquiz.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notionquiz.generator.domain.QuizSet;
import com.notionquiz.generator.dto.QuizItemResponse;
import com.notionquiz.generator.repository.QuizSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QuizPersistenceServiceTest {

    @Mock
    private QuizSetRepository quizSetRepository;

    private QuizPersistenceService quizPersistenceService;

    @BeforeEach
    void setUp() {
        quizPersistenceService = new QuizPersistenceService(quizSetRepository, new ObjectMapper());
    }

    @Test
    void parseQuizItemsJson_returnsQuizItems() {
        String quizJson = """
            [
              {
                "question": "질문",
                "choices": ["A", "B", "C", "D"],
                "answer": "A"
              }
            ]
            """;

        List<QuizItemResponse> result = quizPersistenceService.parseQuizItemsJson("page-1", quizJson);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getQuestion()).isEqualTo("질문");
        assertThat(result.getFirst().getChoices()).containsExactly("A", "B", "C", "D");
        assertThat(result.getFirst().getAnswer()).isEqualTo("A");
    }

    @Test
    void findLatestQuizzes_returnsParsedLatestQuizSet() {
        String quizJson = """
            [
              {
                "question": "최근 퀴즈",
                "choices": ["1", "2", "3", "4"],
                "answer": "2"
              }
            ]
            """;
        QuizSet latestQuizSet = new QuizSet("page-1", "hash", "source", quizJson);
        given(quizSetRepository.findTopByPageIdOrderByCreatedAtDesc("page-1"))
            .willReturn(Optional.of(latestQuizSet));

        Optional<List<QuizItemResponse>> result = quizPersistenceService.findLatestQuizzes("page-1");

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().getFirst().getQuestion()).isEqualTo("최근 퀴즈");
    }
}
