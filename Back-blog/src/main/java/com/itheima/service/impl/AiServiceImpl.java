package com.itheima.service.impl;

import com.itheima.service.AiService;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class AiServiceImpl implements AiService {

    // 复用客户端，避免重复创建（优化：建议抽成配置类）
    private OpenAIClient getOpenAIClient() {
        return OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();
    }

    // 原有流式对话方法（保留）
    @Override
    public Flux<String> getAiResponse(String question, String blogContext) {
        OpenAIClient client = getOpenAIClient();
        // ========== 核心优化：完善系统角色设定，适配二手交易平台实际场景 ==========
        String systemRole = """
                你的名字是吴桐，性格傲娇又热心，是「XX二手交易平台」的专属AI客服。
                【核心身份】：熟悉平台所有规则和在售商品信息，擅长解答用户关于商品、交易、售后的各类问题。
                【实时数据】：
                %s
                【回答规则】：
                1. 优先严格使用提供的实时数据回答，数据里没有的信息绝对不编造；
                2. 若数据无相关内容，傲娇但礼貌引导："哼😜，这个问题我还没查到哦～要不换个问题问问？比如在售商品、价格、库存这些我都超懂的！"；
                3. 回答风格：傲娇中带专业，口语化但不随意，比如"这都不知道？😝 我来告诉你吧..."；
                4. 格式要求：必须使用Markdown格式，商品信息用列表/加粗展示，价格/库存等关键数据突出；
                5. 业务限制：只回答二手交易相关问题，无关问题（如闲聊、敏感内容）按规则2引导；
                6. 数据解读：
                   - 商品状态：1=在售，2=已售罄，3=下架，4=审核中，5=违规；
                   - 新旧程度：0=二手，1=全新，2=9成新，3=8成新，4=7成及以下；
                   - 价格展示：保留两位小数，标注"售价/原价"。
                """.formatted(Objects.requireNonNullElse(blogContext, "暂无实时数据"));
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addSystemMessage(systemRole)
                .addUserMessage(question)
                .model("qwen-plus")
                .build();

        return Flux.create(sink -> {
            try (StreamResponse<ChatCompletionChunk> response = client.chat().completions().createStreaming(params)) {
                response.stream().forEach(chunk -> {
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

    // 新增：AI生成商品描述的方法（非流式，直接返回完整文本）
    public String generateGoodsDesc(String keywords, String goodsName, Integer isNew, BigDecimal sellPrice) {
        OpenAIClient client = getOpenAIClient();

        // 映射新旧程度为文字描述
        String newDegree = switch (isNew) {
            case 0 -> "二手";
            case 1 -> "全新";
            case 2 -> "9成新";
            case 3 -> "8成新";
            case 4 -> "7成新及以下";
            default -> "二手";
        };

        // 结构化提示词，确保生成的描述符合平台规范
        String prompt = String.format(
                "你是二手交易平台的商品描述生成助手，请根据以下信息生成专业、详细的商品描述：\n" +
                        "1. 商品名称：%s\n" +
                        "2. 新旧程度：%s\n" +
                        "3. 售卖价格：%s元\n" +
                        "4. 用户补充关键词：%s\n" +
                        "生成规则：\n" +
                        "- 语气友好，符合二手交易平台风格；\n" +
                        "- 内容详细，包含商品状态、使用情况、卖点等；\n" +
                        "- 控制在200-500字；\n" +
                        "- 仅返回描述文本，不要额外说明；\n" +
                        "- 避免使用夸张、违规词汇。",
                goodsName, newDegree, sellPrice, keywords
        );

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage(prompt)
                .model("qwen-turbo") // 轻量版，速度快
                .temperature(0.5)    // 适度随机性，保证描述自然
                .maxTokens(800)
                .build();

        try {
            ChatCompletion response = client.chat().completions().create(params);
            return response.choices().get(0).message().content().orElse("");
        } catch (Exception e) {
            throw new RuntimeException("AI生成商品描述失败：" + e.getMessage());
        }
    }
}