package com.notionquiz.generator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notionquiz.generator.dto.QuizGenerateResponse;
import com.notionquiz.generator.dto.QuizItemResponse;
import com.notionquiz.generator.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private final NotionService notionService;
    private final DocumentChunkService documentChunkService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final QuizPersistenceService quizPersistenceService;

    public QuizService(
        NotionService notionService,
        DocumentChunkService documentChunkService,
        ChatClient.Builder chatClientBuilder,
        ObjectMapper objectMapper,
        QuizPersistenceService quizPersistenceService
    ) {
        this.notionService = notionService;
        this.documentChunkService = documentChunkService;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.quizPersistenceService = quizPersistenceService;
    }

    public QuizGenerateResponse generateQuiz(String pageId) {
        long startedAt = System.currentTimeMillis();

        if (pageId == null || pageId.isBlank()) {
            throw new RuntimeException("퀴즈 생성에 실패했습니다: pageId가 비어 있습니다.");
        }

        String pageContent;
        try {
            pageContent = notionService.fetchPageData(pageId);
        } catch (Exception e) {
            throw new RuntimeException("퀴즈 생성에 실패했습니다: 노션 페이지를 불러오지 못했습니다. pageId=" + pageId, e);
        }

        if (pageContent == null || pageContent.isBlank()) {
            throw new RuntimeException("퀴즈 생성에 실패했습니다: 노션 페이지 내용이 비어 있습니다. pageId=" + pageId);
        }

        String documentHash = HashUtil.sha256(pageContent);
        Optional<List<QuizItemResponse>> cachedQuizzes = quizPersistenceService.findCachedQuizzes(pageId, documentHash);
        if (cachedQuizzes.isPresent()) {
            log.info("[QuizService] 퀴즈 캐시 반환. pageId={}, cached=true, documentHash={}, totalElapsed={}ms",
                pageId,
                documentHash,
                System.currentTimeMillis() - startedAt);
            return createResponse(pageId, cachedQuizzes.get(), true);
        }

        List<String> chunks = documentChunkService.splitText(pageContent);
        System.out.println("[QuizService] 문서 chunking 완료. originalLength=" + pageContent.length() +
            ", chunkCount=" + chunks.size());

        if (chunks.isEmpty()) {
            throw new RuntimeException("퀴즈 생성에 실패했습니다: 처리 가능한 문서 chunk가 없습니다. pageId=" + pageId);
        }

        int usedChunkCount = Math.min(2, chunks.size());
        String contentForPrompt = String.join("\n\n", chunks.subList(0, usedChunkCount));
        System.out.println("[QuizService] chunk 사용 완료. usedChunkCount=" + usedChunkCount +
            ", inputLength=" + contentForPrompt.length());

        String prompt = """
            다음 노션 문서 내용을 기반으로 객관식 퀴즈를 정확히 3개 생성하라.

            [중요 규칙]
            1) 반드시 아래 문서 내용에 있는 정보만 사용한다.
            2) 문서에 없는 사실은 절대 만들어내지 않는다.
            3) 출력은 반드시 JSON 배열(JSON Array)만 반환한다.
            4) 설명 문장, 제목, 주석, 마크다운, 코드블록(```)을 절대 포함하지 않는다.
            5) 배열 각 항목은 아래 스키마를 정확히 따른다.
               {
                 "question": "...",
                 "choices": ["...", "...", "...", "..."],
                 "answer": "..."
               }
            6) choices 는 반드시 문자열 4개여야 하며, answer 는 choices 중 하나와 정확히 일치해야 한다.

            [노션 문서 내용]
            %s
            """.formatted(contentForPrompt);

        try {
            System.out.println("[QuizService] AI 호출 시작. promptLength=" + prompt.length());
            String response = chatClient.prompt(prompt).call().content();
            if (response == null || response.isBlank()) {
                throw new RuntimeException("퀴즈 생성에 실패했습니다: AI 응답이 비어 있습니다.");
            }
            String normalizedResponse = removeMarkdownCodeBlock(response);
            List<QuizItemResponse> quizzes;
            try {
                quizzes = objectMapper.readValue(normalizedResponse, new TypeReference<List<QuizItemResponse>>() {});
            } catch (Exception parseException) {
                log.error("[QuizService] AI 응답 JSON 파싱 실패. rawResponse={}", response, parseException);
                throw new RuntimeException(
                    "퀴즈 생성에 실패했습니다: AI 응답을 JSON 배열로 파싱하지 못했습니다. " +
                        "question/choices(4개)/answer 형식을 확인하세요.",
                    parseException
                );
            }

            quizPersistenceService.saveQuizSet(pageId, documentHash, pageContent, quizzes);

            QuizGenerateResponse result = createResponse(pageId, quizzes, false);
            log.info("[QuizService] 퀴즈 생성 완료. pageId={}, cached=false, documentHash={}", pageId, documentHash);
            System.out.println("[QuizService] AI 호출 및 파싱 완료. responseLength=" + response.length() +
                ", quizCount=" + quizzes.size() +
                ", totalElapsed=" + (System.currentTimeMillis() - startedAt) + "ms");
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("퀴즈 생성에 실패했습니다: AI 호출 중 오류가 발생했습니다.", e);
        }
    }

    private String removeMarkdownCodeBlock(String rawResponse) {
        String normalized = rawResponse == null ? "" : rawResponse.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
    }

    private QuizGenerateResponse createResponse(String pageId, List<QuizItemResponse> quizzes, boolean cached) {
        QuizGenerateResponse result = new QuizGenerateResponse();
        result.setPageId(pageId);
        result.setCached(cached);
        result.setQuizzes(quizzes);
        return result;
    }
}
