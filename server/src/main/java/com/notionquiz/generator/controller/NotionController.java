package com.notionquiz.generator.controller;

import com.notionquiz.generator.dto.NotionRequest;
import com.notionquiz.generator.service.NotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 확장 프로그램에서 호출 가능하도록 설정
public class NotionController {
    private final NotionService notionService;

    @PostMapping("/page/new")
    public String sendPage(@RequestBody NotionRequest request) {
        return notionService.fetchPageData(request.getPageId());
    }
}
