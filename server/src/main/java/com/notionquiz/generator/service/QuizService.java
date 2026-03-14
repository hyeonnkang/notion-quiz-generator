package com.notionquiz.generator.service;

import com.notionquiz.generator.dto.QuizGenerateResponse;
import com.notionquiz.generator.dto.QuizItemResponse;
import com.notionquiz.generator.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);
    private static final int MAX_GENERATION_ATTEMPTS = 2;

    private final NotionService notionService;
    private final DocumentChunkService documentChunkService;
    private final ChatClient chatClient;
    private final QuizPersistenceService quizPersistenceService;
    private final QuizSanitizationService quizSanitizationService;

    public QuizService(
        NotionService notionService,
        DocumentChunkService documentChunkService,
        ChatClient.Builder chatClientBuilder,
        QuizPersistenceService quizPersistenceService,
        QuizSanitizationService quizSanitizationService
    ) {
        this.notionService = notionService;
        this.documentChunkService = documentChunkService;
        this.chatClient = chatClientBuilder.build();
        this.quizPersistenceService = quizPersistenceService;
        this.quizSanitizationService = quizSanitizationService;
    }

    public QuizGenerateResponse generateQuiz(String pageId) {
        long startedAt = System.currentTimeMillis();

        validatePageId(pageId, "퀴즈 생성");

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
            List<QuizItemResponse> sanitizedCachedQuizzes = quizSanitizationService.sanitizeQuizItems(cachedQuizzes.get());
            if (quizSanitizationService.hasRequiredQuizCount(sanitizedCachedQuizzes)) {
                log.info("[QuizService] 퀴즈 캐시 반환. pageId={}, cached=true, documentHash={}, totalElapsed={}ms",
                    pageId,
                    documentHash,
                    System.currentTimeMillis() - startedAt);
                return createResponse(pageId, sanitizedCachedQuizzes, true);
            }

            log.warn("[QuizService] 캐시 퀴즈 품질 기준 미달로 재생성합니다. pageId={}, documentHash={}, cachedQuizCount={}, sanitizedQuizCount={}",
                pageId,
                documentHash,
                cachedQuizzes.get().size(),
                sanitizedCachedQuizzes.size());
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

        try {
            List<QuizItemResponse> quizzes = generateValidQuizzes(pageId, contentForPrompt);
            quizPersistenceService.saveQuizSet(pageId, documentHash, pageContent, quizzes);

            QuizGenerateResponse result = createResponse(pageId, quizzes, false);
            log.info("[QuizService] 퀴즈 생성 완료. pageId={}, cached=false, documentHash={}, totalElapsed={}ms",
                pageId,
                documentHash,
                System.currentTimeMillis() - startedAt);
            System.out.println("[QuizService] AI 호출 및 파싱 완료. quizCount=" + quizzes.size() +
                ", totalElapsed=" + (System.currentTimeMillis() - startedAt) + "ms");
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("퀴즈 생성에 실패했습니다: AI 호출 중 오류가 발생했습니다.", e);
        }
    }

    public QuizGenerateResponse getLatestQuiz(String pageId) {
        validatePageId(pageId, "퀴즈 조회");

        List<QuizItemResponse> quizzes = quizPersistenceService.findLatestQuizzes(pageId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "저장된 퀴즈가 없습니다. pageId=" + pageId
            ));

        return createResponse(pageId, quizSanitizationService.sanitizeQuizItems(quizzes), true);
    }

    private List<QuizItemResponse> generateValidQuizzes(String pageId, String contentForPrompt) {
        String lastResponse = null;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String prompt = buildPrompt(contentForPrompt, attempt > 1);
            System.out.println("[QuizService] AI 호출 시작. attempt=" + attempt + ", promptLength=" + prompt.length());

            String response = chatClient.prompt(prompt).call().content();
            if (response == null || response.isBlank()) {
                throw new RuntimeException("퀴즈 생성에 실패했습니다: AI 응답이 비어 있습니다.");
            }

            lastResponse = response;
            String normalizedResponse = removeMarkdownCodeBlock(response);
            List<QuizItemResponse> parsedQuizzes;
            try {
                parsedQuizzes = quizPersistenceService.parseQuizItemsJson(pageId, normalizedResponse);
            } catch (Exception parseException) {
                log.error("[QuizService] AI 응답 JSON 파싱 실패. rawResponse={}", response, parseException);
                throw new RuntimeException(
                    "퀴즈 생성에 실패했습니다: AI 응답을 JSON 배열로 파싱하지 못했습니다. " +
                        "question/choices(4개)/answer 형식을 확인하세요.",
                    parseException
                );
            }

            List<QuizItemResponse> sanitizedQuizzes = quizSanitizationService.sanitizeQuizItems(parsedQuizzes);
            if (quizSanitizationService.hasRequiredQuizCount(sanitizedQuizzes)) {
                return sanitizedQuizzes;
            }

            log.warn("[QuizService] 생성된 퀴즈가 품질 기준 미달입니다. pageId={}, attempt={}, parsedQuizCount={}, sanitizedQuizCount={}",
                pageId,
                attempt,
                parsedQuizzes.size(),
                sanitizedQuizzes.size());
        }

        throw new RuntimeException(
            "퀴즈 생성에 실패했습니다: 품질 기준을 만족하는 문항 3개를 확보하지 못했습니다. lastResponseLength=" +
                (lastResponse == null ? 0 : lastResponse.length())
        );
    }

    private String buildPrompt(String contentForPrompt, boolean retry) {
        String retryInstruction = retry
            ? """
                [재시도 지시]
                이전 응답은 중복 또는 유효성 기준을 통과하지 못했다.
                이번에는 반드시 서로 다른 핵심 개념 기준의 유효한 3문항만 생성하라.

                """
            : "";

        return """
            %s다음 노션 문서 내용을 기반으로 객관식 퀴즈를 정확히 3개 생성하라.

            [중요 규칙]
            1) 반드시 아래 문서에 명시된 정보만 사용한다.
            2) 추측, 일반 지식 보완, 외부 정보 확장, 문서에 없는 사실 생성은 절대 금지한다.
            3) 문서만 읽고 정답을 확정할 수 없는 내용은 문제로 만들지 않는다.
            4) 3문항은 서로 다른 핵심 개념, 사실, 절차를 다뤄야 하며 같은 내용을 표현만 바꿔 반복하지 않는다.
            5) 지나치게 단순한 문제는 피하고, 문서 이해에 필요한 핵심 내용을 묻는다.
            6) 출력은 반드시 JSON 배열(JSON Array)만 반환한다.
            7) 설명 문장, 제목, 주석, 마크다운, 코드블록(```)을 절대 포함하지 않는다.
            8) 배열 각 항목은 아래 스키마를 정확히 따른다.
               {
                 "question": "...",
                 "choices": ["...", "...", "...", "..."],
                 "answer": "..."
               }
            9) choices 는 반드시 문자열 4개여야 하며, 보기와 정답 표현은 문서에 있는 내용만 사용한다.
            10) answer 는 choices 중 하나와 문자 단위로 정확히 일치해야 한다.

            [노션 문서 내용]
            %s
            """.formatted(retryInstruction, contentForPrompt);
    }

    private String removeMarkdownCodeBlock(String rawResponse) {
        String normalized = rawResponse == null ? "" : rawResponse.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
    }

    private void validatePageId(String pageId, String action) {
        if (pageId == null || pageId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, action + "에 실패했습니다: pageId가 비어 있습니다.");
        }
    }

    private QuizGenerateResponse createResponse(String pageId, List<QuizItemResponse> quizzes, boolean cached) {
        QuizGenerateResponse result = new QuizGenerateResponse();
        result.setPageId(pageId);
        result.setCached(cached);
        result.setQuizzes(quizzes);
        return result;
    }
}
