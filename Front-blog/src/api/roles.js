import request from "@/utils/request.js";

//分页列表查询
export const rolesListService=(params)=>{
    return request.get('/roles/list',{params:params});
}

//新增权限
export const rolesAddService = (roleData)=>{
    return request.post('/roles/add',roleData);
}

//编辑权限
export const rolesUpdateInfoServices = (roleData)=>{
    return request.put('/roles/update',roleData);
}

//删除权限
export const rolesDeleteService = (id)=>{
    return request.delete(`/roles/delete/${id}`);
}

//角色列表
export const allRolesList = () => {
    return request.get('/roles/allRolesList');
}

//用户角色表
export const userRolesListService=(params)=>{
    return request.get('/roles/userRolesList',{params:params});
}

//删除用户角色表
export const userRolesDelete = (id)=>{
    return request.delete(`/roles/userRolesDelete/${id}`);
}

//新增用户角色表
export const userRolesAdd = (userRolesData)=>{
    return request.post('/roles/userRolesAdd',userRolesData);
}