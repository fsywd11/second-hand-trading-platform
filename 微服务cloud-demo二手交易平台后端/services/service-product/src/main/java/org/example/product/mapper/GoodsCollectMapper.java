package org.example.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GoodsCollectMapper {

    @Select("select count(*) from goods_collect where goods_id = #{goodsId}")
    Integer allList(Integer goodsId);
}
