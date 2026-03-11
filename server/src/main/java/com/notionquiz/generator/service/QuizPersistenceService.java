package com.notionquiz.generator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notionquiz.generator.domain.QuizSet;
import com.notionquiz.generator.dto.QuizItemResponse;
import com.notionquiz.generator.repository.QuizSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuizPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(QuizPersistenceService.class);

    private final QuizSetRepository quizSetRepository;
    private final ObjectMapper objectMapper;

    public QuizPersistenceService(QuizSetRepository quizSetRepository, ObjectMapper objectMapper) {
        this.quizSetRepository = quizSetRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveQuizSet(String pageId, String sourceText, List<QuizItemResponse> quizzes) {
        String quizJson = toQuizJson(pageId, quizzes);

        try {
            QuizSet saved = quizSetRepository.save(new QuizSet(pageId, sourceText, quizJson));
            log.info("[QuizPersistenceService] 퀴즈 저장 완료. pageId={}, quizSetId={}, quizCount={}",
                pageId,
                saved.getId(),
                quizzes == null ? 0 : quizzes.size());
        } catch (Exception e) {
            log.error("[QuizPersistenceService] 퀴즈 저장 실패. pageId={}, sourceLength={}, quizJsonLength={}",
                pageId,
                sourceText == null ? 0 : sourceText.length(),
                quizJson.length(),
                e);
            throw new RuntimeException("퀴즈 생성 결과 저장에 실패했습니다. pageId=" + pageId, e);
        }
    }

    private String toQuizJson(String pageId, List<QuizItemResponse> quizzes) {
        try {
            return objectMapper.writeValueAsString(quizzes);
        } catch (JsonProcessingException e) {
            log.error("[QuizPersistenceService] 퀴즈 JSON 직렬화 실패. pageId={}, quizCount={}",
                pageId,
                quizzes == null ? 0 : quizzes.size(),
                e);
            throw new RuntimeException("퀴즈 JSON 직렬화에 실패했습니다. pageId=" + pageId, e);
        }
    }
}
