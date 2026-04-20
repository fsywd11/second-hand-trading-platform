package org.example.product.service;

import org.example.common.PageBean;
import org.example.goods.DTO.GoodsDTO;
import org.example.goods.DTO.GoodsQueryDTO;
import org.example.goods.VO.GoodsDetailVO;
import org.example.goods.VO.GoodsVO;
import org.example.trace.model.TraceabilityVO;
import org.example.user.VO.BuyerViewSellerVO;

import java.util.List;

public interface ProductDomainService {
    void add(GoodsDTO goodsDTO);

    PageBean<GoodsVO> list(GoodsQueryDTO queryDTO);

    GoodsDetailVO findById(Integer id);

    void update(GoodsDTO goodsDTO);

    void delete(Integer id);

    void updateStatus(Integer id, Integer status);

    BuyerViewSellerVO findSellerByUserId(Integer id);

    PageBean<GoodsVO> alllist(GoodsQueryDTO queryDTO);

    List<GoodsVO> listByIds(List<Integer> ids);

    TraceabilityVO traceById(Integer id);
}
