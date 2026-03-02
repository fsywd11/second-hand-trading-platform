package com.itheima.mapper;

import com.itheima.DTO.GoodsQueryDTO;
import com.itheima.pojo.Goods;
import com.itheima.pojo.GoodsImage;
import com.itheima.vo.BuyerViewSellerVO;
import com.itheima.vo.GoodsVO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品Mapper接口
 */
public interface GoodsMapper {

    // 1. 新增：批量插入商品图片
    void insertGoodsImages(@Param("goodsId") Integer goodsId, @Param("imageList") List<GoodsImage> imageList);

    // 2. 新增：根据商品ID删除所有图片
    @Delete("DELETE FROM goods_image WHERE goods_id = #{goodsId}")
    void deleteGoodsImagesByGoodsId(Integer goodsId);

    // 3. 新增：根据商品ID查询图片列表
    @Select("SELECT * FROM goods_image WHERE goods_id = #{goodsId} ORDER BY id ASC")
    List<GoodsImage> findGoodsImagesByGoodsId(Integer goodsId);

    // 新增商品
    @Insert("INSERT INTO goods (goods_name, goods_desc, goods_pic, category_id, original_price, sell_price, seller_id, goods_status, is_new,stock, create_time, update_time) " +
            "VALUES (#{goodsName}, #{goodsDesc}, #{goodsPic}, #{categoryId}, #{originalPrice}, #{sellPrice}, #{sellerId}, #{goodsStatus}, #{isNew}, #{stock}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(Goods goods);

    // 分页查询商品
    List<GoodsVO> list(GoodsQueryDTO queryDTO);

    // 根据ID查询商品
    @Select("SELECT * FROM goods WHERE id = #{id}")
    Goods findById(Integer id);

    // 修改商品
    void update(Goods goods);

    // 删除商品
    @Delete("DELETE FROM goods WHERE id = #{id}")
    void delete(Integer id);

    // 更新商品状态
    void updateStatus(@Param("id") Integer id, @Param("status") Integer status, @Param("updateTime") LocalDateTime updateTime);

    // 删除商品标签关联
    @Delete("DELETE FROM goods_tag WHERE goods_id = #{goodsId}")
    void deleteGoodsTagsByGoodsId(Integer goodsId);

    BuyerViewSellerVO findSellerByUserId(Integer id);
}