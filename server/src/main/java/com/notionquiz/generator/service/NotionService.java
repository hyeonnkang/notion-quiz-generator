package com.notionquiz.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NotionService {

    private final String NOTION_TOKEN = System.getenv("NOTION_SECRET");
    private final String NOTION_VERSION = System.getenv("NOTION_VERSION");

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String fetchPageData(String pageId) {
        long startedAt = System.currentTimeMillis();
        System.out.println("[NotionService] 페이지 데이터 조회 시작. pageId=" + pageId);
        StringBuilder out = new StringBuilder();
        fetchChildrenRecursive(pageId, out);
        String result = out.toString().trim();
        System.out.println("[NotionService] 페이지 데이터 조회 완료. length=" + result.length() +
            ", elapsed=" + (System.currentTimeMillis() - startedAt) + "ms");
        return result;
    }

    private void fetchChildrenRecursive(String blockId, StringBuilder out) {
        String cursor = null;

        do {
            System.out.println("[NotionService] 블록 조회 요청. blockId=" + blockId +
                ", cursor=" + (cursor == null ? "null" : cursor));
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NOTION_TOKEN);
            headers.set("Notion-Version", NOTION_VERSION);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = "https://api.notion.com/v1/blocks/" + blockId + "/children?page_size=100"
                + (cursor == null ? "" : ("&start_cursor=" + cursor));

            ResponseEntity<String> res;
            try {
                res = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            } catch (Exception e) {
                System.out.println("[NotionService] Notion API 호출 실패. blockId=" + blockId +
                    ", cursor=" + (cursor == null ? "null" : cursor) + ", reason=" + e.getMessage());
                throw new RuntimeException("ID [" + blockId + "] 의 Notion API 호출에 실패했습니다.", e);
            }

            try {
                JsonNode root = objectMapper.readTree(res.getBody());
                JsonNode results = root.path("results");
                System.out.println("[NotionService] 블록 조회 응답. status=" + res.getStatusCode().value() +
                    ", resultsCount=" + results.size());

                for (JsonNode block : results) {
                    appendPlainText(block, out);

                    if (block.path("has_children").asBoolean(false)) {
                        String childBlockId = block.path("id").asText();
                        fetchChildrenRecursive(childBlockId, out);
                    }
                }

                boolean hasMore = root.path("has_more").asBoolean(false);
                cursor = hasMore ? root.path("next_cursor").asText(null) : null;
                System.out.println("[NotionService] 페이지네이션 상태. hasMore=" + hasMore +
                    ", nextCursor=" + (cursor == null ? "null" : cursor));

            } catch (Exception e) {
                System.out.println("[NotionService] 응답 파싱 실패. blockId=" + blockId +
                    ", reason=" + e.getMessage());
                throw new RuntimeException("ID [" + blockId + "] 의 데이터를 가져오는데 실패했습니다.", e);
            }

        } while (cursor != null && !cursor.isBlank());
    }

    private void appendPlainText(JsonNode block, StringBuilder out) {
        String type = block.path("type").asText();
        JsonNode richText = block.path(type).path("rich_text");

        if (richText.isArray()) {
            for (JsonNode t : richText) {
                String plain = t.path("plain_text").asText("");
                if (!plain.isBlank()) {
                    out.append(plain).append('\n');
                }
            }
        }
    }
}
