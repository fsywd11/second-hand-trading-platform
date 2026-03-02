import request from "@/utils/request.js";

// 地址新增
export const addressAddService = (addressData) => {
    return request.post('/address', addressData)
}

// 地址分页列表查询
export const addressListService = (queryData) => {
    return request.post('/address/list',null, {params: queryData})
}

// 所有用户地址列表（管理员用）
export const addressAllListService = (queryData) => {
    return request.post('/address/allList', null,{params: queryData})
}

// 地址详情查询
export const addressDetailService = (id) => {
    return request.post(`/address/detail/${id}`)
}

// 地址修改
export const addressUpdateService = (addressData) => {
    return request.post('/address/update', addressData)
}

// 地址删除
export const addressDeleteService = (id) => {
    return request.post(`/address/delete/${id}`)
}

// 设置默认地址
export const addressSetDefaultService = (addressId) => {
    return request.post(`/address/default/${addressId}`)
}

// 查询用户默认地址
export const addressGetDefaultService = () => {
    return request.post('/address/default')
}