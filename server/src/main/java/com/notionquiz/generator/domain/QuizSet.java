package com.notionquiz.generator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "quiz_set")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String pageId;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String sourceText;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String quizJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public QuizSet(String pageId, String sourceText, String quizJson) {
        this.pageId = pageId;
        this.sourceText = sourceText;
        this.quizJson = quizJson;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
