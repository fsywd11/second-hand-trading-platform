import request from "@/utils/request.js";

// 商品分类添加
export const shopCategoryAddService = (categoryData)=>{
    return request.post('/shopcategory', categoryData)
}

// 商品获取全部分类列表
export const shopCategoryListService = ()=>{
    return request.get('/shopcategory')
}

// 商品分类查询-根据id查询分类详情
export const shopCategoryDetailService = (id)=>{
    return request.get('/shopcategory/detail', {params: {id: id}})
}

// 商品分类修改
export const shopCategoryUpdateService = (categoryData)=>{
    return request.put('/shopcategory', categoryData)
}

// 商品分类删除
export const shopCategoryDeleteService = (id)=>{
    return request.delete(`/shopcategory/${id}`)
}

// ====================== 商品公开相关 API ======================

// 商品获取全部分类列表
export const shopCategoryOpenListService = ()=>{
    return request.get('/shopcategory/list')
}