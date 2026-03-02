import request from "@/utils/request.js";

//权限分类列表查询
export const permissionListService=(params)=>{
    return request.get('/permission/list',{params:params});
}

//新增权限
export const permissionAddService = (permissionData)=>{
    return request.post('/permission/add',permissionData);
}

//编辑权限
export const permissionUpdateInfoServices = (permissionData)=>{
    return request.put('/permission/update',permissionData);
}

//删除权限
export const permissionDeleteService = (id)=>{
    return request.delete(`/permission/delete/${id}`);
}

//获取权限角色列表
export const permissionRolesListService=(params)=>{
    return request.get('/permission/permissionRolesList',{params:params});
}

//新增权限角色表
export const permissionRolesAdd = (permissionRolesData)=>{
    return request.post('/permission/permissionRolesAdd',permissionRolesData);
}

//删除权限角色表
export const permissionRolesDelete = (id)=>{
    return request.delete(`/permission/permissionRolesDelete/${id}`);
}

//获取所有权限列表
export const allPermissionList = () => {
    return request.get('/permission/allPermissionList');
}


