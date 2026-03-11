package com.notionquiz.generator.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuizService {

    private final NotionService notionService;
    private final DocumentChunkService documentChunkService;
    private final ChatClient chatClient;

    public QuizService(
        NotionService notionService,
        DocumentChunkService documentChunkService,
        ChatClient.Builder chatClientBuilder
    ) {
        this.notionService = notionService;
        this.documentChunkService = documentChunkService;
        this.chatClient = chatClientBuilder.build();
    }

    public String generateQuiz(String pageId) {
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
            다음 노션 문서 내용을 기반으로 객관식 퀴즈를 만들어라.

            [중요 규칙]
            1) 반드시 아래 문서 내용에 있는 정보만 사용한다.
            2) 문서에 없는 사실은 절대 만들어내지 않는다.
            3) 객관식 문제는 정확히 3개를 만든다.
            4) 각 문제는 아래 형식을 반드시 지킨다.
               - 문제
               - 보기 4개
               - 정답

            [출력 형식]
            문제 1:
            보기:
            1) ...
            2) ...
            3) ...
            4) ...
            정답: ...

            문제 2:
            보기:
            1) ...
            2) ...
            3) ...
            4) ...
            정답: ...

            문제 3:
            보기:
            1) ...
            2) ...
            3) ...
            4) ...
            정답: ...

            [노션 문서 내용]
            %s
            """.formatted(contentForPrompt);

        try {
            System.out.println("[QuizService] AI 호출 시작. promptLength=" + prompt.length());
            String response = chatClient.prompt(prompt).call().content();
            if (response == null || response.isBlank()) {
                throw new RuntimeException("퀴즈 생성에 실패했습니다: AI 응답이 비어 있습니다.");
            }
            System.out.println("[QuizService] AI 호출 완료. responseLength=" + response.length() +
                ", totalElapsed=" + (System.currentTimeMillis() - startedAt) + "ms");
            return response;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("퀴즈 생성에 실패했습니다: AI 호출 중 오류가 발생했습니다.", e);
        }
    }
}
