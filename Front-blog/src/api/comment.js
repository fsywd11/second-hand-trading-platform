import request from "@/utils/request.js";

export const commentAddService = (commentData)=>{
    return request.post('/comment/add',commentData)
}

//文章分页列表查询
export const commentListService = (params)=>{
    return request.get('/comment/list',{params:params})
}

//文章修改
export const commentUpdateServices=(commentData)=>{
    return request.put(`/comment/update`,commentData)
}

//文章删除
export const commentDeleteService=(id)=>{
    return request.delete(`/comment/delete/${id}`)
}

//文章详情
export const commentList=(articleId)=>{
    return request.get(`/comment/commentList/${articleId}`)
}


export const commentCountService=()=>{
    return request.get(`/comment/alllist`)
}

export const commentLikeService=(id)=>{
    return request.post(`/comment/like/${id}`)
}


//显示与某一个用户id有关的商品的全部评论
export const commentallListService=(userId)=>{
    return request.get(`/comment/commentallList/${userId}`)
}

