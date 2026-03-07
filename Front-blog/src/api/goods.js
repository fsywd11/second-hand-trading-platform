import request from "@/utils/request.js";

// 商品添加
export const goodsAddService = (goodsData)=>{
    return request.post('/goods/add', goodsData)
}

// 商品分页列表查询（多条件筛选）
export const goodsListService = (queryData)=>{
    return request.post('/goods/list',queryData)
}

// 商品详情查询
export const goodsDetailService = (id)=>{
    return request.post(`/goods/detail/${id}`)
}

// 商品修改
export const goodsUpdateService = (goodsData)=>{
    return request.post('/goods/update', goodsData)
}

// 商品删除（下架并删除）
export const goodsDeleteService = (id)=>{
    return request.post(`/goods/delete/${id}`)
}

// 商品状态更新（在售/已售出/下架等）
export const goodsUpdateStatusService = (id, status)=>{
    return request.post(`/goods/updateStatus/${id}/${status}`)
}


// 我的商品分页列表查询（多条件筛选）
export const goodsMyListService = (queryData)=>{
    return request.post('/goods/mylist',queryData)
}


//通过RAG搜索商品
export const goodsRagSearchService = (query)=>{
    return request.post('/goods/rag/search',query)
}

//清理Milvus脏数据（仅管理员可调用）
export const goodsCleanMilvusService = ()=>{
    return request.post('/goods/cleanMilvusDirtyData')
}


// ====================== 商品推荐相关 API ======================

/**
 * 按关键词推荐商品（获取前3个在售商品）
 * @param {string} keyword - 分类关键词
 * @returns {Promise} 包含商品列表的响应结果
 */
export const goodsRecommendByKeywordService = (keyword)=>{
    return request.post('/goods/recommend/byKeyword', { keyword })
}


// ====================== 商品公开相关 API ======================


// 商品分页列表查询（多条件筛选）
export const goodsOpenListService = (queryData)=>{
    return request.post('/goods/goodsopenlist',queryData)
}

//通过商品id查询卖家信息
export const goodsOpenDetailSellerService = (id)=>{
    return request.post(`/goods//findSellerByUserId/${id}`)
}

