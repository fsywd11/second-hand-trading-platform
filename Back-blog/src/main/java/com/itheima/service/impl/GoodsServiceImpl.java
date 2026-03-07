package com.itheima.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.itheima.DTO.GoodsDTO;
import com.itheima.DTO.GoodsQueryDTO;
import com.itheima.mapper.*;
import com.itheima.pojo.*;
import com.itheima.service.CampusKnowledgeGraphService;
import com.itheima.service.GoodsService;
import com.itheima.service.UserService;
import com.itheima.util.MilvusCollectionUtil;
import com.itheima.util.MilvusVectorUtil;
import com.itheima.util.QwenEmbeddingUtil;
import com.itheima.util.ThreadLocalUtil;
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
import java.util.stream.Collectors;

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
    private GoodsCollectMapper goodsCollectMapper;

    @Autowired
    private QwenEmbeddingUtil qwenEmbeddingUtil;
    @Autowired
    private MilvusVectorUtil milvusVectorUtil;
    @Autowired
    private MilvusCollectionUtil milvusCollectionUtil;

    // 新增依赖
    @Autowired
    private CampusKnowledgeGraphService campusKnowledgeGraphService;
    @Autowired
    private UserService userService;

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

    /**
     * 完全替换为默认个性化推荐逻辑，修复父分类ID查询子分类商品+参数绑定问题
     */
    @Override
    @Transactional(readOnly = true)
    public PageBean<GoodsVO> list(GoodsQueryDTO queryDTO) {
        PageBean<GoodsVO> pb = new PageBean<>();
        // 默认分页参数
        Integer pageNum = queryDTO.getPageNum() == null ? 1 : queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize() == null ? 10 : queryDTO.getPageSize();

        // 优先从sellerId获取用户ID（兼容我的商品场景），无则视为未登录
        // 获取当前登录用户（创建人）
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");

        try {
            // ========== 核心修复：处理父分类ID，获取所有子分类ID ==========
            List<Integer> targetChildCategoryIds = new ArrayList<>();
            if (queryDTO.getCategoryId() != null && queryDTO.getCategoryId() != 0) {
                // 1. 查询传入的父分类ID下的所有子分类ID
                List<Integer> childIds = shopCategoryMapper.listChildCategoryIdsByParentId(queryDTO.getCategoryId());
                if (!CollectionUtils.isEmpty(childIds)) {
                    targetChildCategoryIds = childIds;
                } else {
                    // 若没有子分类，说明传入的是子分类ID，直接使用
                    targetChildCategoryIds.add(queryDTO.getCategoryId());
                }
            }

            // 2. 未登录/用户ID不合法：返回指定分类下的热门商品
            if (userId == null || userId <= 0) {
                List<GoodsVO> hotGoodsList = getHotGoodsByCategory(targetChildCategoryIds, pageSize * 2);
                // 分页处理
                List<GoodsVO> pageGoodsList = getPageData(hotGoodsList, pageNum, pageSize);
                pb.setTotal((long) hotGoodsList.size());
                pb.setItems(pageGoodsList);
                return pb;
            }

            // 3. 获取用户完整信息（含major/grade/campus_scene/tags）
            User user = userService.getById(userId);
            if (user == null) {
                // 用户不存在：返回指定分类下的热门商品
                List<GoodsVO> hotGoodsList = getHotGoodsByCategory(targetChildCategoryIds, pageSize * 2);
                List<GoodsVO> pageGoodsList = getPageData(hotGoodsList, pageNum, pageSize);
                pb.setTotal((long) hotGoodsList.size());
                pb.setItems(pageGoodsList);
                return pb;
            }

            // 4. 知识图谱层：匹配用户特征对应的商品分类（优先使用前端传入的分类）
            List<Integer> kgCategoryIds = new ArrayList<>();
            if (!CollectionUtils.isEmpty(targetChildCategoryIds)) {
                // 前端传入了分类，优先使用该分类
                kgCategoryIds = targetChildCategoryIds;
            } else {
                // 前端未传分类，使用知识图谱匹配的分类
                kgCategoryIds = campusKnowledgeGraphService.matchCategoryByUser(user);
            }

            List<GoodsVO> kgGoodsList = new ArrayList<>();
            if (!CollectionUtils.isEmpty(kgCategoryIds)) {
                kgGoodsList = listByCategoryIds(kgCategoryIds, pageSize * 2);
            }

            // 5. 协同过滤层：基于用户特征向量检索相似商品（并过滤指定分类）
            List<Double> userVector = campusKnowledgeGraphService.generateUserCompositeVector(user);
            List<Long> cfGoodsIds = milvusVectorUtil.searchSimilarGoods(userVector, pageSize * 2);
            List<GoodsVO> cfGoodsList = new ArrayList<>();
            if (!CollectionUtils.isEmpty(cfGoodsIds)) {
                // 转换为Integer并查询商品
                List<Integer> goodsIdList = cfGoodsIds.stream()
                        .map(Long::intValue)
                        .collect(Collectors.toList());
                cfGoodsList = goodsMapper.listByIds(goodsIdList);
                // 过滤仅在售商品 + 过滤指定分类
                cfGoodsList = filterGoodsByCategoryAndStatus(cfGoodsList, targetChildCategoryIds);
            }

            // 6. 结果融合：去重 + 优先知识图谱推荐
            List<GoodsVO> finalGoodsList = mergeRecommendResults(kgGoodsList, cfGoodsList, pageSize * 2);

            // 7. 分页处理（保持原有分页逻辑）
            List<GoodsVO> pageGoodsList = getPageData(finalGoodsList, pageNum, pageSize);

            // 8. 封装分页结果（和原格式完全一致）
            pb.setTotal((long) finalGoodsList.size());
            pb.setItems(pageGoodsList);

            return pb;
        } catch (Exception e) {
            log.error("个性化推荐失败，用户ID：{}", userId, e);
            // 异常降级：返回指定分类下的热门商品
            List<Integer> childIds = new ArrayList<>();
            if (queryDTO.getCategoryId() != null && queryDTO.getCategoryId() != 0) {
                childIds = shopCategoryMapper.listChildCategoryIdsByParentId(queryDTO.getCategoryId());
            }
            List<GoodsVO> hotGoodsList = getHotGoodsByCategory(childIds, pageSize * 2);
            List<GoodsVO> pageGoodsList = getPageData(hotGoodsList, pageNum, pageSize);
            pb.setTotal((long) hotGoodsList.size());
            pb.setItems(pageGoodsList);
            return pb;
        }
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

    // ========== 私有辅助方法 ==========

    /**
     * 根据分类ID（子ID列表）获取热门商品 - 修复参数传递
     */
    private List<GoodsVO> getHotGoodsByCategory(List<Integer> childCategoryIds, Integer limit) {
        GoodsQueryDTO queryDTO = new GoodsQueryDTO();
        queryDTO.setGoodsStatus(GoodsStatusEnum.ON_SALE.getCode());

        // 按Mapper接口定义的参数传递：queryDTO + categoryIds + limit
        List<GoodsVO> goodsList;
        if (!CollectionUtils.isEmpty(childCategoryIds)) {
            // 查询指定子分类下的商品
            goodsList = goodsMapper.list(queryDTO, childCategoryIds, limit);
        } else {
            // 无分类限制，查询全量热门商品
            goodsList = goodsMapper.list(queryDTO, new ArrayList<>(), limit);
        }
        return filterAndSupplementGoods(goodsList);
    }

    /**
     * 按分类ID列表查询商品
     */
    private List<GoodsVO> listByCategoryIds(List<Integer> categoryIds, Integer limit) {
        if (CollectionUtils.isEmpty(categoryIds) || limit == null || limit <= 0) {
            return new ArrayList<>();
        }
        return goodsMapper.listByCategoryIds(categoryIds, limit);
    }

    /**
     * 过滤商品：仅保留在售 + 指定分类的商品
     * @param goodsList 原始商品列表
     * @param categoryIds 目标分类ID列表（子ID）
     * @return 过滤后的商品列表
     */
    private List<GoodsVO> filterGoodsByCategoryAndStatus(List<GoodsVO> goodsList, List<Integer> categoryIds) {
        if (CollectionUtils.isEmpty(goodsList)) {
            return new ArrayList<>();
        }
        return goodsList.stream()
                .filter(goods -> goods.getGoodsStatus().equals(GoodsStatusEnum.ON_SALE.getCode()))
                .filter(goods -> {
                    // 若无分类限制，直接保留；有则过滤指定分类
                    if (CollectionUtils.isEmpty(categoryIds)) {
                        return true;
                    }
                    return categoryIds.contains(goods.getCategoryId());
                })
                .peek(goods -> {
                    goods.setIsNewName(GoodsIsNewEnum.getNameByCode(goods.getIsNew()));
                    goods.setGoodsStatusName(GoodsStatusEnum.getNameByCode(goods.getGoodsStatus()));
                    goods.setImageList(goodsMapper.findGoodsImagesByGoodsId(goods.getId()));
                    goods.setCollectCount(goodsCollectMapper.allList(goods.getId()));
                    // 补充卖家信息
                    User seller = userMapper.findById(goods.getSellerId());
                    if (seller != null) {
                        goods.setSellerNickname(seller.getNickname());
                        goods.setSellerPic(seller.getUserPic());
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 覆盖原有filterAndSupplementGoods方法，增加分类过滤
     */
    private List<GoodsVO> filterAndSupplementGoods(List<GoodsVO> goodsList) {
        return filterGoodsByCategoryAndStatus(goodsList, new ArrayList<>());
    }

    /**
     * 融合推荐结果：优先知识图谱，补充协同过滤，去重
     */
    private List<GoodsVO> mergeRecommendResults(List<GoodsVO> kgGoodsList, List<GoodsVO> cfGoodsList, Integer limit) {
        List<GoodsVO> mergedList = new ArrayList<>();
        Set<Integer> existGoodsIds = new HashSet<>();

        // 1. 先加知识图谱推荐的商品
        for (GoodsVO goods : kgGoodsList) {
            if (mergedList.size() >= limit) {
                break;
            }
            if (!existGoodsIds.contains(goods.getId())) {
                mergedList.add(goods);
                existGoodsIds.add(goods.getId());
            }
        }

        // 2. 再加协同过滤推荐的商品（去重）
        for (GoodsVO goods : cfGoodsList) {
            if (mergedList.size() >= limit) {
                break;
            }
            if (!existGoodsIds.contains(goods.getId())) {
                mergedList.add(goods);
                existGoodsIds.add(goods.getId());
            }
        }

        // 3. 不足则补充热门商品
        if (mergedList.size() < limit) {
            int needSupplement = limit - mergedList.size();
            List<GoodsVO> hotGoods = getHotGoodsByCategory(new ArrayList<>(), needSupplement);
            for (GoodsVO goods : hotGoods) {
                if (mergedList.size() >= limit) {
                    break;
                }
                if (!existGoodsIds.contains(goods.getId())) {
                    mergedList.add(goods);
                }
            }
        }

        return mergedList;
    }

    /**
     * 通用分页处理方法（保持原有分页逻辑）
     */
    private List<GoodsVO> getPageData(List<GoodsVO> allData, Integer pageNum, Integer pageSize) {
        List<GoodsVO> pageData = new ArrayList<>();
        int total = allData.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        if (start < total) {
            pageData = allData.subList(start, end);
        }
        return pageData;
    }
}