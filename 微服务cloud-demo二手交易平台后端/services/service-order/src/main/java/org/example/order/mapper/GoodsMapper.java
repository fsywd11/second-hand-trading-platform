package org.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.goods.POJO.Goods;
import org.example.goods.POJO.GoodsImage;

import java.util.List;

@Mapper
public interface GoodsMapper {

    @Select("select * from goods where id = #{id}")
    Goods findById(Integer id);

    @Select("select * from goods_image where goods_id = #{goodsId} order by id asc")
    List<GoodsImage> findGoodsImagesByGoodsId(Integer goodsId);

    void update(Goods goods);

    List<Goods> findAllOnSale();
}
