package com.itheima.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.itheima.DTO.GoodsDTO;
import com.itheima.DTO.GoodsQueryDTO;
import com.itheima.mapper.*;
import com.itheima.pojo.*;
import com.itheima.service.GoodsService;
import com.itheima.util.MilvusCollectionUtil;
import com.itheima.util.MilvusVectorUtil;
import com.itheima.util.QwenEmbeddingUtil;
import com.itheima.vo.BuyerViewSellerVO;
import com.itheima.vo.GoodsDetailVO;
import com.itheima.vo.GoodsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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

    @Autowired
    private QwenEmbeddingUtil qwenEmbeddingUtil;
    @Autowired
    private MilvusVectorUtil milvusVectorUtil;
    @Autowired
    private MilvusCollectionUtil milvusCollectionUtil;

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
            goods.setGoodsStatus(GoodsStatusEnum.ON_SALE.getCode());
        } else {
            goods.setGoodsStatus(GoodsStatusEnum.SOLD_OUT.getCode());
        }

        // 保存商品
        goodsMapper.add(goods);

        // 处理商品图片
        List<GoodsImage> imageList = goodsDTO.getImageList();
        if (imageList != null && !imageList.isEmpty()) {
            goodsMapper.insertGoodsImages(goods.getId(), imageList);
        }

        // 生成商品向量并入库（精简文本，仅保留名称+描述）
        try {
            String goodsText = String.format(
                    "二手商品：%s，描述：%s",
                    goods.getGoodsName(),
                    goods.getGoodsDesc() == null ? "" : goods.getGoodsDesc()
            );
            milvusCollectionUtil.initGoodsCollection();
            List<Double> embedding = qwenEmbeddingUtil.getEmbedding(goodsText);
            milvusVectorUtil.insertGoodsVector(Long.valueOf(goods.getId()), goodsText, embedding);
        } catch (Exception e) {
            log.error("商品向量入库失败", e);
            throw new RuntimeException("商品发布成功，但向量入库失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageBean<GoodsVO> list(GoodsQueryDTO queryDTO) {
        PageBean<GoodsVO> pb = new PageBean<>();

        // 处理分类ID逻辑
        List<Integer> targetCategoryIds = new ArrayList<>();
        if (queryDTO.getCategoryId() != null && queryDTO.getCategoryId() != 0) {
            Integer categoryId = queryDTO.getCategoryId();
            Integer parentId = shopCategoryMapper.findParentIdById(categoryId);

            if (parentId == 0) {
                List<Integer> childIds = shopCategoryMapper.listChildCategoryIdsByParentId(categoryId);
                if (!CollectionUtils.isEmpty(childIds)) {
                    targetCategoryIds = childIds;
                } else {
                    pb.setTotal(0L);
                    pb.setItems(new ArrayList<>());
                    return pb;
                }
            } else {
                targetCategoryIds.add(categoryId);
            }
        }

        // 启动分页
        PageHelper.startPage(queryDTO.getPageNum(), queryDTO.getPageSize());
        List<GoodsVO> goodsList = goodsMapper.list(queryDTO, targetCategoryIds);

        // 封装分页结果
        long total = 0;
        if (goodsList instanceof Page) {
            Page<GoodsVO> p = (Page<GoodsVO>) goodsList;
            total = p.getTotal();
        } else {
            total = goodsList.size();
        }

        // 补充商品信息
        goodsList.forEach(goods -> {
            goods.setIsNewName(GoodsIsNewEnum.getNameByCode(goods.getIsNew()));
            goods.setGoodsStatusName(GoodsStatusEnum.getNameByCode(goods.getGoodsStatus()));
            List<GoodsImage> imageList = goodsMapper.findGoodsImagesByGoodsId(goods.getId());
            goods.setImageList(imageList);
            goods.setCollectCount(goodsCollectMapper.allList(goods.getId()));
        });

        pb.setTotal(total);
        pb.setItems(goodsList);
        return pb;
    }

    @Override
    @Transactional(readOnly = true)
    public GoodsDetailVO findById(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("商品ID不合法");
        }

        Goods goods = goodsMapper.findById(id);
        if (goods == null) {
            throw new RuntimeException("商品不存在：" + id);
        }

        GoodsDetailVO vo = new GoodsDetailVO();
        BeanUtils.copyProperties(goods, vo);

        // 查询商品图片
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
        vo.setIsNewName(GoodsIsNewEnum.getNameByCode(goods.getIsNew()));
        vo.setGoodsStatus(goods.getGoodsStatus());
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

        Goods oldGoods = goodsMapper.findById(goodsDTO.getId());
        if (oldGoods == null) {
            throw new RuntimeException("商品不存在：" + goodsDTO.getId());
        }

        // 转换为实体类
        Goods goods = new Goods();
        BeanUtils.copyProperties(goodsDTO, goods);
        goods.setUpdateTime(LocalDateTime.now());

        // 修改商品
        goodsMapper.update(goods);

        // 同步商品图片（先删后加）
        goodsMapper.deleteGoodsImagesByGoodsId(goods.getId());
        List<GoodsImage> imageList = goodsDTO.getImageList();
        if (imageList != null && !imageList.isEmpty()) {
            goodsMapper.insertGoodsImages(goods.getId(), imageList);
        }

        // 更新商品向量（先删旧向量，再插新向量）
        try {
            Long goodsId = Long.valueOf(goods.getId());
            // 核心修复：先删除旧向量，避免重复
            milvusVectorUtil.deleteGoodsVector(goodsId);

            // 精简文本，提升检索精度
            String goodsText = String.format(
                    "二手商品：%s，描述：%s",
                    goods.getGoodsName(),
                    goods.getGoodsDesc() == null ? "" : goods.getGoodsDesc()
            );
            List<Double> embedding = qwenEmbeddingUtil.getEmbedding(goodsText);
            milvusVectorUtil.insertGoodsVector(goodsId, goodsText, embedding);

            log.info("商品ID:{} 向量更新成功", goodsId);
        } catch (Exception e) {
            log.error("商品向量更新失败", e);
            throw new RuntimeException("商品更新成功，但向量更新失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("商品ID不合法");
        }

        Goods goods = goodsMapper.findById(id);
        if (goods == null) {
            throw new RuntimeException("商品不存在：" + id);
        }

        // 物理删除Milvus向量
        try {
            milvusVectorUtil.deleteGoodsVector(Long.valueOf(id));
        } catch (Exception e) {
            log.error("删除商品ID:{} 向量数据失败", id, e);
        }

        // 删除商品图片
        goodsMapper.deleteGoodsImagesByGoodsId(id);

        // 删除商品
        goodsMapper.delete(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id, Integer status) {
        if (id == null || status == null) {
            throw new IllegalArgumentException("商品ID和状态不能为空");
        }

        // 校验状态合法性
        boolean statusValid = false;
        for (GoodsStatusEnum enumItem : GoodsStatusEnum.values()) {
            if (enumItem.getCode().equals(status)) {
                statusValid = true;
                break;
            }
        }
        if (!statusValid) {
            throw new IllegalArgumentException("商品状态不合法：" + status);
        }

        Goods goods = goodsMapper.findById(id);
        if (goods == null) {
            throw new RuntimeException("商品不存在：" + id);
        }

        // 状态变更处理向量（物理删除/重新插入）
        try {
            Long goodsId = Long.valueOf(id);
            // 非在售状态：物理删除向量
            if (status != GoodsStatusEnum.ON_SALE.getCode()) {
                milvusVectorUtil.deleteGoodsVector(goodsId);
            }
            // 重新上架：重新生成并插入向量
            else if (status == GoodsStatusEnum.ON_SALE.getCode()
                    && goods.getGoodsStatus() != GoodsStatusEnum.ON_SALE.getCode()) {
                String goodsText = String.format(
                        "二手商品：%s，描述：%s",
                        goods.getGoodsName(),
                        goods.getGoodsDesc() == null ? "" : goods.getGoodsDesc()
                );
                List<Double> embedding = qwenEmbeddingUtil.getEmbedding(goodsText);
                milvusVectorUtil.insertGoodsVector(goodsId, goodsText, embedding);
            }
        } catch (Exception e) {
            log.error("商品状态变更时处理向量失败，商品ID：{}", id, e);
        }

        // 更新商品状态
        goodsMapper.updateStatus(id, status, LocalDateTime.now());
    }

    @Override
    public BuyerViewSellerVO findSellerByUserId(Integer id) {
        return goodsMapper.findSellerByUserId(id);
    }

    @Override
    public List<GoodsVO> ragSearch(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return new ArrayList<>();
            }
            String cleanQuery = query.trim();

            // 生成查询向量
            List<Double> queryVector = qwenEmbeddingUtil.getEmbedding(cleanQuery);

            // 向量检索（过滤低相似度）
            List<Long> similarGoodsIds = milvusVectorUtil.searchSimilarGoods(queryVector, 20);
            if (similarGoodsIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 去重 + 数据库查询
            Set<Long> uniqueGoodsIds = new LinkedHashSet<>(similarGoodsIds);
            List<GoodsVO> goodsList = new ArrayList<>();

            for (Long goodsId : uniqueGoodsIds) {
                try {
                    Goods goods = goodsMapper.findById(Math.toIntExact(goodsId));
                    // 仅保留在售商品
                    if (goods == null || goods.getGoodsStatus() != GoodsStatusEnum.ON_SALE.getCode()) {
                        continue;
                    }
                    GoodsVO vo = new GoodsVO();
                    BeanUtils.copyProperties(goods, vo);
                    vo.setImageList(goodsMapper.findGoodsImagesByGoodsId(Math.toIntExact(goodsId)));
                    vo.setCollectCount(goodsCollectMapper.allList(Math.toIntExact(goodsId)));
                    vo.setIsNewName(GoodsIsNewEnum.getNameByCode(goods.getIsNew()));
                    vo.setGoodsStatusName(GoodsStatusEnum.getNameByCode(goods.getGoodsStatus()));
                    vo.setSellerNickname(userMapper.findById(goods.getSellerId()).getNickname());
                    vo.setSellerPic(userMapper.findById(goods.getSellerId()).getUserPic());
                    goodsList.add(vo);
                } catch (Exception e) {
                    log.error("查询商品ID:{} 详情失败", goodsId, e);
                    continue;
                }
            }
            return goodsList;
        } catch (Exception e) {
            log.error("RAG搜索异常，查询词：{}", query, e);
            throw new RuntimeException("RAG搜索异常", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanMilvusDirtyData() {
        List<Long> validGoodsIds = goodsMapper.listAllValidGoodsIds();
        if (validGoodsIds.isEmpty()) {
            log.warn("数据库中无有效商品，跳过Milvus脏数据清理");
            return;
        }
        milvusVectorUtil.cleanDirtyVectorData(validGoodsIds);
    }
}