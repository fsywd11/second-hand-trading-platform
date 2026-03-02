package com.itheima.service;


import reactor.core.publisher.Flux;

public interface AiService {
    Flux<String> getAiResponse(String message, String blogContext);
}
