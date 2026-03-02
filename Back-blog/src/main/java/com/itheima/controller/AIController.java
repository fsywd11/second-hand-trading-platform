package com.itheima.controller;

import com.itheima.anno.PreAuthorize;
import com.itheima.service.AiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AIController {

    @Resource
    private AiService aiService;
    @PreAuthorize("/ai/chat")
    @PostMapping(value = "/chat",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody String message) {
        StringBuilder context = new StringBuilder();
        context.append("【博客基本信息】：本站共有文章 ");
        context.append("【近期更新/热门文章列表】：\n");

        // 3. 传入 Service 进行 AI 调用
        Flux<String> response = aiService.getAiResponse(message, context.toString());
        return response;
    }
}
