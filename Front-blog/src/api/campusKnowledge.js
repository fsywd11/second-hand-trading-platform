import request from "@/utils/request.js";

// 知识图谱规则新增
export const kgAddService = (kgData)=>{
    return request.post('/api/backAll/kg/add', kgData)
}

// 知识图谱规则查询所有
export const kgListAllService = ()=>{
    return request.get('/api/backAll/kg/list')
}

// 知识图谱规则根据ID查询
export const kgSelectService = (id)=>{
    return request.get(`/api/backAll/kg/select/${id}`)
}

// 知识图谱规则修改
export const kgUpdateService = (kgData)=>{
    return request.put('/api/backAll/kg/update', kgData)
}

// 知识图谱规则删除
export const kgDeleteService = (id)=>{
    return request.delete(`/api/backAll/kg/delete/${id}`)
}

// 同步所有规则到Milvus
export const kgSyncService = ()=>{
    return request.post('/api/backAll/kg/sync')
}