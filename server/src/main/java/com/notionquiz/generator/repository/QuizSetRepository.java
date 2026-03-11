package com.notionquiz.generator.repository;

import com.notionquiz.generator.domain.QuizSet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {
}
