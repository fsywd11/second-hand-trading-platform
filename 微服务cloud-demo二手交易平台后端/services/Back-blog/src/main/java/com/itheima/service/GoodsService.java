package com.itheima.service;

import org.example.goods.DTO.GoodsDTO;
import org.example.goods.DTO.GoodsQueryDTO;
import org.example.common.PageBean;
import org.example.user.VO.BuyerViewSellerVO;
import org.example.goods.VO.GoodsDetailVO;
import org.example.goods.VO.GoodsVO;

import java.util.List;

/**
 * 商品服务接口
 */
public interface GoodsService {
    void add(GoodsDTO goodsDTO);

    PageBean<GoodsVO> list(GoodsQueryDTO queryDTO);

    GoodsDetailVO findById(Integer id);

    void update(GoodsDTO goodsDTO);

    void delete(Integer id);

    void updateStatus(Integer id, Integer status);

    BuyerViewSellerVO findSellerByUserId(Integer id);

    List<GoodsVO> ragSearch(String query);

    /**
     * 全量校验并清理Milvus脏数据
     */
    void cleanMilvusDirtyData();

    /**
     * 全量查询商品
     * @param queryDTO
     * @return
     */
    PageBean<GoodsVO> alllist(GoodsQueryDTO queryDTO);
}