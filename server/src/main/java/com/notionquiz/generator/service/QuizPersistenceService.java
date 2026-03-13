package com.notionquiz.generator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notionquiz.generator.domain.QuizSet;
import com.notionquiz.generator.dto.QuizItemResponse;
import com.notionquiz.generator.repository.QuizSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class QuizPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(QuizPersistenceService.class);

    private final QuizSetRepository quizSetRepository;
    private final ObjectMapper objectMapper;

    public QuizPersistenceService(QuizSetRepository quizSetRepository, ObjectMapper objectMapper) {
        this.quizSetRepository = quizSetRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<List<QuizItemResponse>> findCachedQuizzes(String pageId, String documentHash) {
        return quizSetRepository.findByPageIdAndDocumentHash(pageId, documentHash)
            .flatMap(quizSet -> parseQuizJson(pageId, documentHash, quizSet));
    }

    @Transactional
    public void saveQuizSet(String pageId, String documentHash, String sourceText, List<QuizItemResponse> quizzes) {
        String quizJson = toQuizJson(pageId, quizzes);

        try {
            QuizSet saved = quizSetRepository.save(new QuizSet(pageId, documentHash, sourceText, quizJson));
            log.info("[QuizPersistenceService] 퀴즈 저장 완료. pageId={}, documentHash={}, quizSetId={}, quizCount={}",
                pageId,
                documentHash,
                saved.getId(),
                quizzes == null ? 0 : quizzes.size());
        } catch (Exception e) {
            log.error("[QuizPersistenceService] 퀴즈 저장 실패. pageId={}, documentHash={}, sourceLength={}, quizJsonLength={}",
                pageId,
                documentHash,
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

    private Optional<List<QuizItemResponse>> parseQuizJson(String pageId, String documentHash, QuizSet quizSet) {
        try {
            List<QuizItemResponse> quizzes = objectMapper.readValue(
                quizSet.getQuizJson(),
                new TypeReference<List<QuizItemResponse>>() {}
            );
            return Optional.of(quizzes);
        } catch (Exception e) {
            log.warn("[QuizPersistenceService] 캐시 데이터 파싱 실패. pageId={}, documentHash={}, quizSetId={}",
                pageId,
                documentHash,
                quizSet.getId(),
                e);
            return Optional.empty();
        }
    }
}
