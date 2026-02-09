package com.notionquiz.generator;

import com.notionquiz.generator.service.NotionService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GeneratorApplication {
	public static void main(String[] args) {
		SpringApplication.run(GeneratorApplication.class, args);

		String notionSecret = System.getenv("NOTION_SECRET");
		System.out.println("내 노션 키: " + notionSecret);

		NotionService notionQuizService = new NotionService();
		String notionPageId = System.getenv("NOTION_PAGE_ID");
		notionQuizService.fetchPageData(notionPageId);
	}
}
