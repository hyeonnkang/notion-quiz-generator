package com.notionquiz.generator.service;

import com.notionquiz.generator.dto.QuizItemResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class QuizSanitizationService {

    public static final int TARGET_QUIZ_COUNT = 3;

    private static final int MIN_CHOICES_COUNT = 4;

    public List<QuizItemResponse> sanitizeQuizItems(List<QuizItemResponse> quizItems) {
        if (quizItems == null || quizItems.isEmpty()) {
            return List.of();
        }

        List<QuizItemResponse> sanitizedItems = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();

        for (QuizItemResponse quizItem : quizItems) {
            Optional<QuizItemResponse> sanitizedItem = sanitizeQuizItem(quizItem);
            if (sanitizedItem.isEmpty()) {
                continue;
            }

            String questionKey = normalizeForComparison(sanitizedItem.get().getQuestion());
            if (!seenQuestions.add(questionKey)) {
                continue;
            }

            sanitizedItems.add(sanitizedItem.get());
            if (sanitizedItems.size() == TARGET_QUIZ_COUNT) {
                break;
            }
        }

        return sanitizedItems;
    }

    public boolean hasRequiredQuizCount(List<QuizItemResponse> quizItems) {
        return quizItems != null && quizItems.size() >= TARGET_QUIZ_COUNT;
    }

    private Optional<QuizItemResponse> sanitizeQuizItem(QuizItemResponse quizItem) {
        if (quizItem == null) {
            return Optional.empty();
        }

        String sanitizedQuestion = normalizeForDisplay(quizItem.getQuestion());
        String sanitizedAnswer = normalizeForDisplay(quizItem.getAnswer());
        List<String> sanitizedChoices = sanitizeChoices(quizItem.getChoices());

        if (sanitizedQuestion.isBlank() || sanitizedAnswer.isBlank() || sanitizedChoices.size() < MIN_CHOICES_COUNT) {
            return Optional.empty();
        }

        boolean answerExistsInChoices = sanitizedChoices.stream()
            .anyMatch(choice -> choice.equals(sanitizedAnswer));
        if (!answerExistsInChoices) {
            return Optional.empty();
        }

        QuizItemResponse sanitizedItem = new QuizItemResponse();
        sanitizedItem.setQuestion(sanitizedQuestion);
        sanitizedItem.setChoices(sanitizedChoices);
        sanitizedItem.setAnswer(sanitizedAnswer);
        return Optional.of(sanitizedItem);
    }

    private List<String> sanitizeChoices(List<String> choices) {
        if (choices == null || choices.isEmpty()) {
            return List.of();
        }

        return choices.stream()
            .filter(Objects::nonNull)
            .map(this::normalizeForDisplay)
            .filter(choice -> !choice.isBlank())
            .toList();
    }

    private String normalizeForDisplay(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeForComparison(String value) {
        return normalizeForDisplay(value).toLowerCase(Locale.ROOT);
    }
}
