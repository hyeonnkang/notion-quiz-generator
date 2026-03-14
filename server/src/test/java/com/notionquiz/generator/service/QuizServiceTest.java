package com.notionquiz.generator.service;

import com.notionquiz.generator.dto.QuizGenerateResponse;
import com.notionquiz.generator.dto.QuizItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private NotionService notionService;

    @Mock
    private DocumentChunkService documentChunkService;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private QuizPersistenceService quizPersistenceService;

    private QuizService quizService;

    @BeforeEach
    void setUp() {
        given(chatClientBuilder.build()).willReturn(chatClient);
        given(chatClient.prompt(anyString())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);

        quizService = new QuizService(
            notionService,
            documentChunkService,
            chatClientBuilder,
            quizPersistenceService,
            new QuizSanitizationService()
        );
    }

    @Test
    void generateQuiz_retriesOnceWhenSanitizedQuizCountIsInsufficient() {
        String pageId = "page-1";
        String pageContent = "문서 내용";
        String firstResponse = """
            [
              {"question":"중복 질문","choices":["A","B","C","D"],"answer":"A"},
              {"question":"중복 질문","choices":["A","B","C","D"],"answer":"A"},
              {"question":"보기 부족","choices":["A","B","C"],"answer":"A"}
            ]
            """.trim();
        String secondResponse = """
            [
              {"question":"질문 1","choices":["A1","B1","C1","D1"],"answer":"A1"},
              {"question":"질문 2","choices":["A2","B2","C2","D2"],"answer":"B2"},
              {"question":"질문 3","choices":["A3","B3","C3","D3"],"answer":"C3"}
            ]
            """.trim();

        given(notionService.fetchPageData(pageId)).willReturn(pageContent);
        given(quizPersistenceService.findCachedQuizzes(anyString(), anyString())).willReturn(Optional.empty());
        given(documentChunkService.splitText(pageContent)).willReturn(List.of("chunk-1", "chunk-2"));
        given(callResponseSpec.content()).willReturn(firstResponse, secondResponse);
        given(quizPersistenceService.parseQuizItemsJson(pageId, firstResponse)).willReturn(List.of(
            quizItem("중복 질문", List.of("A", "B", "C", "D"), "A"),
            quizItem("중복 질문", List.of("A", "B", "C", "D"), "A"),
            quizItem("보기 부족", List.of("A", "B", "C"), "A")
        ));
        given(quizPersistenceService.parseQuizItemsJson(pageId, secondResponse)).willReturn(List.of(
            quizItem("질문 1", List.of("A1", "B1", "C1", "D1"), "A1"),
            quizItem("질문 2", List.of("A2", "B2", "C2", "D2"), "B2"),
            quizItem("질문 3", List.of("A3", "B3", "C3", "D3"), "C3")
        ));

        QuizGenerateResponse result = quizService.generateQuiz(pageId);

        assertThat(result.isCached()).isFalse();
        assertThat(result.getQuizzes()).hasSize(3);
        assertThat(result.getQuizzes()).extracting(QuizItemResponse::getQuestion)
            .containsExactly("질문 1", "질문 2", "질문 3");
        verify(chatClient, times(2)).prompt(anyString());
        verify(quizPersistenceService).saveQuizSet(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(
            quizzes -> quizzes != null && quizzes.size() == 3
        ));
    }

    private QuizItemResponse quizItem(String question, List<String> choices, String answer) {
        QuizItemResponse quizItem = new QuizItemResponse();
        quizItem.setQuestion(question);
        quizItem.setChoices(choices);
        quizItem.setAnswer(answer);
        return quizItem;
    }
}
