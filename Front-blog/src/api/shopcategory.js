import request from "@/utils/request.js";

// 1. 商品分类添加（管理员）
export const shopCategoryAddService = (categoryData) => {
    return request.post('/shopcategory/add', categoryData)
}

// 2. 【废弃原全量查询】管理员查询全部分类列表（适配后端新路径 /shopcategory/admin/list）
export const shopCategoryAdminListService = () => {
    return request.get('/shopcategory/admin/list')
}

// 3. 前端通用：查询所有一级分类（无权限限制）
export const shopCategoryParentListService = () => {
    return request.get('/shopcategory/parent/list')
}

// 4. 前端通用：根据父ID查询二级分类（无权限限制）
export const shopCategoryChildListService = (parentId) => {
    return request.get('/shopcategory/child/list', { params: { parentId: parentId } })
}

// 5. 前端通用：查询树形分类结构（一级+二级，推荐前端菜单使用）
export const shopCategoryTreeListService = () => {
    return request.get('/shopcategory/tree/list')
}

// 6. 商品分类查询-根据id查询分类详情（管理员）
export const shopCategoryDetailService = (id) => {
    return request.get('/shopcategory/detail', { params: { id: id } })
}

// 7. 商品分类修改（管理员）
export const shopCategoryUpdateService = (categoryData) => {
    return request.put('/shopcategory', categoryData)
}

// 8. 商品分类删除（管理员）
export const shopCategoryDeleteService = (id) => {
    return request.delete(`/shopcategory/${id}`)
}

// 9. 兼容保留：根据分类ID查询分类名称（可选，前端展示用）
export const shopCategoryNameByIdService = (id) => {
    return request.get('/shopcategory/name', { params: { id: id } })
}

// ====================== 商品公开相关 API ======================

// 商品获取全部分类列表
export const shopCategoryOpenListService = ()=>{
    return request.get('/shopcategory/list')
}