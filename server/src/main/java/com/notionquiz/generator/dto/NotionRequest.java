package com.notionquiz.generator.dto;

import lombok.Getter;
import org.springframework.stereotype.Service;

@Getter
@Service
public class NotionRequest {
    private String pageId;
}
