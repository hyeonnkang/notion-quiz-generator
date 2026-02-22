package com.notionquiz.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class NotionService {

    private final String NOTION_TOKEN = System.getenv("NOTION_SECRET");
    private final String NOTION_VERSION = System.getenv("NOTION_VERSION");

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String fetchPageData(String pageId) {
        StringBuilder out = new StringBuilder();
        fetchChildrenRecursive(pageId, out);
        return out.toString().trim();
    }

    private void fetchChildrenRecursive(String blockId, StringBuilder out) {
        String cursor = null;

        do {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NOTION_TOKEN);
            headers.set("Notion-Version", NOTION_VERSION);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = "https://api.notion.com/v1/blocks/" + blockId + "/children?page_size=100"
                + (cursor == null ? "" : ("&start_cursor=" + cursor));

            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            try {
                JsonNode root = objectMapper.readTree(res.getBody());
                JsonNode results = root.path("results");

                for (JsonNode block : results) {
                    appendPlainText(block, out);

                    if (block.path("has_children").asBoolean(false)) {
                        String childBlockId = block.path("id").asText();
                        fetchChildrenRecursive(childBlockId, out);
                    }
                }

                boolean hasMore = root.path("has_more").asBoolean(false);
                cursor = hasMore ? root.path("next_cursor").asText(null) : null;

            } catch (Exception e) {
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