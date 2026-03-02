package com.itheima.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.itheima.DTO.GoodsDTO;
import com.itheima.DTO.GoodsQueryDTO;
import com.itheima.mapper.*;
import com.itheima.pojo.*;
import com.itheima.service.GoodsService;
import com.itheima.vo.BuyerViewSellerVO;
import com.itheima.vo.GoodsDetailVO;
import com.itheima.vo.GoodsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class GoodsServiceImpl implements GoodsService {
    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ShopCategoryMapper shopCategoryMapper;

    @Autowired
    private GoodsLikeMapper goodsLikeMapper;

    @Autowired
    private GoodsCollectMapper goodsCollectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(GoodsDTO goodsDTO) {
        // 参数校验
        if (goodsDTO == null) {
            throw new IllegalArgumentException("商品信息不能为空");
        }

        // 转换为实体类
        Goods goods = new Goods();
        BeanUtils.copyProperties(goodsDTO, goods);
        goods.setCreateTime(LocalDateTime.now());
        goods.setUpdateTime(LocalDateTime.now());

        // 默认状态：在售
        if (goods.getStock() > 0) {
            // 还有库存：保持在售
            goods.setGoodsStatus(GoodsStatusEnum.ON_SALE.getCode());
        } else {
            // 库存为0：改为已售罄
            goods.setGoodsStatus(GoodsStatusEnum.SOLD_OUT.getCode());
        }

        // 保存商品（移除标签关联逻辑）
        goodsMapper.add(goods);

        // ========== 新增：处理商品图片 ==========
        List<GoodsImage> imageList = goodsDTO.getImageList();
        if (imageList != null && !imageList.isEmpty()) {
            goodsMapper.insertGoodsImages(goods.getId(), imageList);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageBean<GoodsVO> list(GoodsQueryDTO queryDTO) {
        PageBean<GoodsVO> pb = new PageBean<>();
        PageHelper.startPage(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 查询商品列表
        List<GoodsVO> goodsList = goodsMapper.list(queryDTO);
        Page<GoodsVO> p = (Page<GoodsVO>) goodsList;

        // 补充枚举名称（移除标签相关逻辑）
        goodsList.forEach(goods -> {
            goods.setIsNewName(GoodsIsNewEnum.getNameByCode(goods.getIsNew()));
            goods.setGoodsStatusName(GoodsStatusEnum.getNameByCode(goods.getGoodsStatus()));
            // ========== 新增：查询商品图片列表 ==========
            List<GoodsImage> imageList = goodsMapper.findGoodsImagesByGoodsId(goods.getId());
            goods.setImageList(imageList);
            //对应商品收藏数
            goods.setCollectCount(goodsCollectMapper.allList(goods.getId()));
        });

        pb.setTotal(p.getTotal());
        pb.setItems(goodsList);
        return pb;
    }

    @Override
    @Transactional(readOnly = true)
    public GoodsDetailVO findById(Integer id) {
        // 参数校验
        if (id == null || id < 1) {
            throw new IllegalArgumentException("商品ID不合法");
        }

        // 查询商品
        Goods goods = goodsMapper.findById(id);
        if (goods == null) {
            throw new RuntimeException("商品不存在：" + id);
        }

        // 转换为详情VO
        GoodsDetailVO vo = new GoodsDetailVO();
        BeanUtils.copyProperties(goods, vo);

        // ========== 新增：查询商品多态图片列表 ==========
        List<GoodsImage> imageList = goodsMapper.findGoodsImagesByGoodsId(id);
        vo.setImageList(imageList);

        // 计算折扣
        if (goods.getOriginalPrice() != null && goods.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discount = goods.getSellPrice().divide(goods.getOriginalPrice(), 2, BigDecimal.ROUND_HALF_UP);
            vo.setDiscount(discount.multiply(new BigDecimal(10)).toString() + "折");
        } else {
            vo.setDiscount("无折扣");
        }

        vo.setCategoryName(shopCategoryMapper.findById(goods.getCategoryId()).getCategoryName());

        // 补充枚举名称（移除标签相关逻辑）
        vo.setIsNewName(GoodsIsNewEnum.getNameByCode(goods.getIsNew()));
        vo.setGoodsStatusName(GoodsStatusEnum.getNameByCode(goods.getGoodsStatus()));
        User seller = userMapper.findById(vo.getSellerId());
        vo.setSellerNickname(seller.getNickname());
        vo.setSellerAvatar(seller.getUserPic());
        vo.setCollectCount(goodsCollectMapper.allList(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(GoodsDTO goodsDTO) {
        if (goodsDTO == null || goodsDTO.getId() == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }

        // 查询商品是否存在
        Goods oldGoods = goodsMapper.findById(goodsDTO.getId());
        if (oldGoods == null) {
            throw new RuntimeException("商品不存在：" + goodsDTO.getId());
        }

        // 转换为实体类
        Goods goods = new Goods();
        BeanUtils.copyProperties(goodsDTO, goods);
        goods.setUpdateTime(LocalDateTime.now());

        // 修改商品（移除标签同步逻辑）
        goodsMapper.update(goods);

        // ========== 新增：同步商品图片（先删后加） ==========
        goodsMapper.deleteGoodsImagesByGoodsId(goods.getId()); // 删除原有图片
        List<GoodsImage> imageList = goodsDTO.getImageList();
        if (imageList != null && !imageList.isEmpty()) {
            goodsMapper.insertGoodsImages(goods.getId(), imageList); // 插入新图片
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        // 参数校验
        if (id == null || id < 1) {
            throw new IllegalArgumentException("商品ID不合法");
        }

        // 查询商品
        Goods goods = goodsMapper.findById(id);
        if (goods == null) {
            throw new RuntimeException("商品不存在：" + id);
        }

        // ========== 新增：删除商品图片 ==========
        goodsMapper.deleteGoodsImagesByGoodsId(id);

        // 删除商品（移除标签关联删除逻辑）
        goodsMapper.delete(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id, Integer status) {
        if (id == null || status == null) {
            throw new IllegalArgumentException("商品ID和状态不能为空");
        }

        // 校验状态合法性
        if (!Arrays.asList(1,2,3,4,5).contains(status)) {
            throw new IllegalArgumentException("商品状态不合法");
        }

        goodsMapper.updateStatus(id, status, LocalDateTime.now());
    }
    @Override
    public BuyerViewSellerVO findSellerByUserId(Integer id) {
        return goodsMapper.findSellerByUserId(id);
    }
}