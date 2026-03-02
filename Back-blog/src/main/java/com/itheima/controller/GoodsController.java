package com.itheima.controller;

import com.itheima.anno.PreAuthorize;
import com.itheima.DTO.GoodsDTO;
import com.itheima.DTO.GoodsQueryDTO;
import com.itheima.pojo.GoodsStatusEnum;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.Result;
import com.itheima.service.GoodsService;
import com.itheima.util.ThreadLocalUtil;
import com.itheima.vo.BuyerViewSellerVO;
import com.itheima.vo.GoodsDetailVO;
import com.itheima.vo.GoodsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/goods")
@Slf4j
@Tag(name = "商品接口", description = "二手商品管理接口")
public class GoodsController {
    @Autowired
    private GoodsService goodsService;

    //添加商品
    @PreAuthorize("/goods/add")
    @PostMapping("/add")
    @Operation(summary = "新增商品", description = "发布二手商品")
    public Result add(@RequestBody @Validated GoodsDTO goodsDTO) {
        goodsService.add(goodsDTO);
        return Result.success();
    }

    @PreAuthorize("/goods/list")
    @PostMapping("/list")
    @Operation(summary = "分页查询商品", description = "多条件筛选二手商品")
    public Result<PageBean<GoodsVO>> list(@RequestBody GoodsQueryDTO queryDTO) {
        PageBean<GoodsVO> pb = goodsService.list(queryDTO);
        return Result.success(pb);
    }


    @PostMapping("/detail/{id}")
    @Operation(summary = "商品详情", description = "查询商品详细信息")
    public Result<GoodsDetailVO> detail(@PathVariable Integer id) {
        GoodsDetailVO vo = goodsService.findById(id);
        if (vo == null) {
            return Result.error("商品不存在");
        }
        return Result.success(vo);
    }

    @PreAuthorize("/goods/update")
    @PostMapping("/update")
    @Operation(summary = "修改商品", description = "更新商品信息")
    public Result update(@RequestBody @Validated GoodsDTO goodsDTO) {
        goodsService.update(goodsDTO);
        return Result.success();
    }

    @PreAuthorize("/goods/delete")
    @PostMapping("/delete/{id}")
    @Operation(summary = "删除商品", description = "下架并删除商品")
    public Result delete(@PathVariable Integer id) {
        goodsService.delete(id);
        return Result.success();
    }

    @PreAuthorize("/goods/updateStatus")
    @PostMapping("/updateStatus/{id}/{status}")
    @Operation(summary = "更新商品状态", description = "在售/已售出/下架等")
    public Result updateStatus(@PathVariable Integer id, @PathVariable Integer status) {
        goodsService.updateStatus(id, status);
        return Result.success();
    }

    @PostMapping("/goodsopenlist")
    @Operation(summary = "分页查询商品", description = "多条件筛选二手商品")
    public Result<PageBean<GoodsVO>> goodsOpenList(@RequestBody GoodsQueryDTO queryDTO) {
        //商品状态为在售
        queryDTO.setGoodsStatus(GoodsStatusEnum.ON_SALE.getCode());
        PageBean<GoodsVO> pb = goodsService.list(queryDTO);
        return Result.success(pb);
    }

    //我的商品
    @PreAuthorize("/goods/mylist")
    @PostMapping("/mylist")
    @Operation(summary = "分页查询商品", description = "多条件筛选二手商品")
    public Result<PageBean<GoodsVO>> myList(@RequestBody GoodsQueryDTO queryDTO) {
        Map<String, Object> map = ThreadLocalUtil.get();
        queryDTO.setSellerId((Integer) map.get("id"));
        PageBean<GoodsVO> pb = goodsService.list(queryDTO);
        return Result.success(pb);
    }



    //通过用户id查询卖家信息
    @PostMapping("/findSellerByUserId/{id}")
    @Operation(summary = "查询商品卖家信息", description = "通过用户id查询卖家信息")
    public Result<BuyerViewSellerVO> findSellerByUserId(@PathVariable Integer id) {
        BuyerViewSellerVO vo = goodsService.findSellerByUserId(id);
        return Result.success(vo);
    }


}