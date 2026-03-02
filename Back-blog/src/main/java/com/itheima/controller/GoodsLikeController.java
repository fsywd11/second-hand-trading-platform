package com.itheima.controller;

import com.itheima.anno.PreAuthorize;
import com.itheima.pojo.GoodsLike; // 替换Like
import com.itheima.pojo.Result;
import com.itheima.service.GoodsLikeService; // 替换LikeService
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/goods/like") // 路径语义化：添加goods前缀，区分商品点赞
@Validated
@Slf4j
public class GoodsLikeController { // 类名语义化修改
    @Autowired
    private GoodsLikeService goodsLikeService; // 替换LikeService

    // 添加商品点赞
    @PreAuthorize("/goods/like/add/{goodsId}") // 权限标识适配
    @PostMapping("/add/{goodsId}") // 路径参数替换为goodsId
    public Result add(@PathVariable Integer goodsId){ // 参数替换为goodsId
        goodsLikeService.add(goodsId);
        return Result.success("点赞成功");
    }

    // 取消商品点赞
    @PreAuthorize("/goods/like/delete/{goodsId}") // 权限标识适配
    @DeleteMapping("/delete/{goodsId}") // 路径参数替换为goodsId
    public Result delete(@PathVariable Integer goodsId){ // 参数替换为goodsId
        goodsLikeService.delete(goodsId);
        return Result.success("取消点赞成功");
    }

    // 查询当前用户是否点赞该商品
    @PreAuthorize("/goods/like/list/user/{goodsId}") // 权限标识适配
    @GetMapping("/list/user/{goodsId}") // 路径语义化
    public Result<List<GoodsLike>> listByUserAndGoods(@PathVariable Integer goodsId){ // 方法名适配
        List<GoodsLike> likes = goodsLikeService.listByUserAndGoods(goodsId);
        return Result.success(likes);
    }
}