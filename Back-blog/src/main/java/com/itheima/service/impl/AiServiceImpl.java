package com.itheima.service.impl;

import com.itheima.service.AiService;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiServiceImpl implements AiService {

    @Override
    public Flux<String> getAiResponse(String question, String blogContext) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();

        String systemRole = "你现在是'我的博客'的专属AI客服。我会为你提供博客的实时数据，请据此回答用户。" +
                "\n【当前博客实时资料】：\n" + blogContext +
                "\n【规则】：1. 优先使用提供的资料回答。2. 如果资料中没有相关内容，请礼貌引导。3. 使用Markdown格式。";

        // 1. 构造参数：DashScope 兼容模式下，通常通过执行方法决定是否流式，
        // 但为了保险，可以在 params 中明确开启 stream 标志
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addSystemMessage(systemRole)
                .addUserMessage(question)
                .model("qwen-plus")
                .build();

        return Flux.create(sink -> {
            // 关键修复点：使用 try-with-resources 确保流被正确关闭
            try (StreamResponse<ChatCompletionChunk> response = client.chat().completions().createStreaming(params)) {
                // 修复方法：调用流响应对象的 stream() 或 直接迭代
                response.stream().forEach(chunk -> {
                    // 修复 choices() 报错：确保导入了正确的 ChatCompletionChunk 类
                    if (chunk.choices() != null && !chunk.choices().isEmpty()) {
                        chunk.choices().get(0).delta().content().ifPresent(sink::next);
                    }
                });
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
}