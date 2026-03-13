package com.notionquiz.generator.repository;

import com.notionquiz.generator.domain.QuizSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {
    Optional<QuizSet> findByPageIdAndDocumentHash(String pageId, String documentHash);
    Optional<QuizSet> findTopByPageIdOrderByCreatedAtDesc(String pageId);
}
