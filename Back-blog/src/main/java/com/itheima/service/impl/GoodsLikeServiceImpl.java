package com.itheima.service.impl;

import com.itheima.mapper.GoodsLikeMapper; // 替换LikeMapper
import com.itheima.pojo.GoodsLike; // 替换Like
import com.itheima.service.GoodsLikeService; // 替换LikeService
import com.itheima.util.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class GoodsLikeServiceImpl implements GoodsLikeService {
    @Autowired
    private GoodsLikeMapper goodsLikeMapper; // 替换LikeMapper

    // 添加商品点赞
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Integer goodsId) { // 参数替换为goodsId
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        goodsLikeMapper.add(userId, goodsId); // 调用修改后的Mapper方法
    }

    // 查询当前用户是否点赞该商品
    @Override
    @Transactional(readOnly = true)
    public List<GoodsLike> listByUserAndGoods(Integer goodsId) { // 方法名+参数适配
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return goodsLikeMapper.listByUserAndGoods(userId, goodsId); // 调用修改后的Mapper方法
    }

    // 删除商品点赞
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer goodsId) { // 参数替换为goodsId
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        goodsLikeMapper.delete(userId, goodsId); // 调用修改后的Mapper方法
    }

}