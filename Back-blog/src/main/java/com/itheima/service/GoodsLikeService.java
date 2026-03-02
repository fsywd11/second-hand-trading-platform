package com.itheima.service;

import com.itheima.pojo.GoodsLike;

import java.util.List;

public interface GoodsLikeService {
    // 添加商品点赞
    void add(Integer goodsId);

    // 查询当前用户是否点赞该商品
    List<GoodsLike> listByUserAndGoods(Integer goodsId);

    // 删除商品点赞
    void delete(Integer goodsId);

}