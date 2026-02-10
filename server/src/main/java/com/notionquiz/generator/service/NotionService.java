package com.notionquiz.generator.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class NotionService {

    private final String NOTION_TOKEN = System.getenv("NOTION_SECRET");
    private final String NOTION_VERSION = System.getenv("NOTION_VERSION");

    public String fetchPageData(String pageId) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + NOTION_TOKEN);
        headers.set("Notion-Version", NOTION_VERSION);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = "https://api.notion.com/v1/blocks/" + pageId + "/children";
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            System.out.println("응답 결과: " + response.getBody());
            return "ID [" + pageId + "] 의 데이터를 성공적으로 가져왔습니다.";
        } catch (Exception e) {
            System.out.println("에러 발생: " + e.getMessage());
            return "ID [" + pageId + "] 의 데이터를 가져오는데 실패했습니다.";
        }
    }
}