package com.itheima.mapper;

import com.itheima.pojo.GoodsLike; // 替换Like为GoodsLike
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface GoodsLikeMapper { // 接口名语义化修改

    // 添加商品点赞
    @Insert("insert into goods_like(user_id,goods_id,create_time) values(#{userId},#{goodsId},now())")
    void add(Integer userId, Integer goodsId); // 参数替换为goodsId

    // 查询当前用户是否点赞该商品（原list方法语义修正）
    @Select("select * from goods_like where user_id=#{userId} AND goods_id=#{goodsId}")
    List<GoodsLike> listByUserAndGoods(Integer userId, Integer goodsId); // 方法名语义化

    // 删除商品点赞
    @Delete("delete from goods_like where user_id=#{userId} AND goods_id=#{goodsId}")
    void delete(Integer userId, Integer goodsId); // 参数替换为goodsId

    // 查询某商品的所有点赞记录
    @Select("select count(*) from goods_like where goods_id=#{goodsId}")
    Integer listByGoodsId(Integer goodsId); // 方法名语义化
}