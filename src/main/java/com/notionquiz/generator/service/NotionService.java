package com.notionquiz.generator.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class NotionService {

    private final String NOTION_TOKEN = System.getenv("NOTION_SECRET");
    private final String NOTION_VERSION = "2022-06-28";

    public void fetchPageData(String pageId) {
        RestTemplate restTemplate = new RestTemplate();

        // 1. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + NOTION_TOKEN);
        headers.set("Notion-Version", NOTION_VERSION);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. Notion API 호출 (특정 페이지의 자식 블록들 가져오기)
        String url = "https://api.notion.com/v1/blocks/" + pageId + "/children";
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            System.out.println("응답 결과: " + response.getBody());
        } catch (Exception e) {
            System.out.println("에러 발생: " + e.getMessage());
        }
    }
}