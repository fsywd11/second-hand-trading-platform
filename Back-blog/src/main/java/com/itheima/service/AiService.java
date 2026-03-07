package com.itheima.service;


import reactor.core.publisher.Flux;

import java.math.BigDecimal;

public interface AiService {
    Flux<String> getAiResponse(String message, String blogContext);
    public String generateGoodsDesc(String keywords, String goodsName, Integer isNew, BigDecimal sellPrice);
}
