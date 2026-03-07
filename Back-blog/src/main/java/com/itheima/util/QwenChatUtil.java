package com.itheima.util;

import com.itheima.vo.GoodsVO;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QwenChatUtil {

    private final OpenAIClient openAIClient;

    public QwenChatUtil() {
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY")) // 从环境变量获取API Key
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();
    }

    /**
     * 生成RAG搜索结果的自然语言总结
     * @param query 用户查询文本
     * @param goodsList 检索到的商品列表
     * @return 人性化的搜索总结
     */
    public String generateSearchSummary(String query, List<GoodsVO> goodsList) {
        if (goodsList.isEmpty()) {
            return String.format("未找到与「%s」相关的在售二手商品", query);
        }

        // 拼接商品信息
        StringBuilder goodsInfo = new StringBuilder();
        for (int i = 0; i < Math.min(5, goodsList.size()); i++) {
            com.itheima.vo.GoodsVO goods = goodsList.get(i);
            goodsInfo.append(String.format(
                    "商品%d：名称=%s，价格=%.2f元，新旧程度=%s，库存=%d件\n",
                    i+1,
                    goods.getGoodsName(),
                    goods.getSellPrice(),
                    goods.getIsNewName(),
                    goods.getStock()
            ));
        }

        // 构造千问提示词
        String prompt = String.format(
                "你是二手交易平台的智能搜索助手，请根据用户查询和检索到的商品信息，生成友好的搜索结果总结。\n" +
                        "用户查询：%s\n" +
                        "商品信息：\n%s\n" +
                        "要求：1. 语气友好；2. 突出核心信息（价格、新旧、库存）；3. 控制在100字以内；4. 只返回总结内容，不要额外说明。",
                query, goodsInfo.toString()
        );

        // 调用千问API
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage(prompt)
                .model("qwen-turbo") // 轻量版模型，速度快
                .temperature(0.3) // 低随机性，保证结果稳定
                .maxTokens(200)
                .build();

        ChatCompletion response = openAIClient.chat().completions().create(params);
        return response.choices().get(0).message().content().orElse("抱歉，未能生成有效的回复");

    }
}