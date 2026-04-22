package com.itheima.service;

import org.example.goods.DTO.GoodsDTO;
import org.example.goods.DTO.GoodsQueryDTO;
import org.example.common.PageBean;
import org.example.user.VO.BuyerViewSellerVO;
import org.example.goods.VO.GoodsDetailVO;
import org.example.goods.VO.GoodsVO;
import org.example.trace.model.TraceabilityVO;

import java.util.List;

/**
 * 商品服务接口（合并完整版）
 */
public interface GoodsService {

    // ==================== 基础 CURD 方法 ====================
    void add(GoodsDTO goodsDTO);

    PageBean<GoodsVO> list(GoodsQueryDTO queryDTO);

    GoodsDetailVO findById(Integer id);

    void update(GoodsDTO goodsDTO);

    void delete(Integer id);

    void updateStatus(Integer id, Integer status);

    BuyerViewSellerVO findSellerByUserId(Integer id);

    PageBean<GoodsVO> alllist(GoodsQueryDTO queryDTO);

    // ==================== 来自第一个Service，新增补充的方法 ====================
    List<GoodsVO> listByIds(List<Integer> ids);

    TraceabilityVO traceById(Integer id);

    // ==================== 原有第二个Service独有的方法 ====================
    List<GoodsVO> ragSearch(String query);

    /**
     * 全量校验并清理Milvus脏数据
     */
    void cleanMilvusDirtyData();
}