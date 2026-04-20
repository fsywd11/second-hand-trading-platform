package org.example.product.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.example.common.PageBean;
import org.example.common.Result;
import org.example.goods.DTO.GoodsDTO;
import org.example.goods.DTO.GoodsQueryDTO;
import org.example.goods.VO.GoodsVO;

public final class SentinelBlockHandler {

    private SentinelBlockHandler() {
    }

    public static Result<Void> addBlocked(GoodsDTO goodsDTO, BlockException exception) {
        return Result.error("商品发布已触发限流: " + exception.getClass().getSimpleName());
    }

    public static Result<PageBean<GoodsVO>> goodsListBlocked(GoodsQueryDTO queryDTO, BlockException exception) {
        return Result.error("商品查询已触发限流: " + exception.getClass().getSimpleName());
    }
}
