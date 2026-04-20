import request from "@/utils/request.js";

// 原有：AI生成商品描述接口（无图片）
export const generateGoodsDescService = (params) => {
    return request.post('/api/backAll/ai/generateGoodsDesc', params)
}
