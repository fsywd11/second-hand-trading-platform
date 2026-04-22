package org.example.product.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.example.common.PageBean;
import org.example.goods.DTO.GoodsDTO;
import org.example.goods.DTO.GoodsQueryDTO;
import org.example.goods.POJO.Goods;
import org.example.goods.POJO.GoodsImage;
import org.example.goods.VO.GoodsDetailVO;
import org.example.goods.VO.GoodsVO;
import org.example.product.constant.GoodsIsNewEnum;
import org.example.product.constant.GoodsStatusEnum;
import org.example.product.mapper.GoodsCollectMapper;
import org.example.product.mapper.GoodsMapper;
import org.example.product.mapper.ShopCategoryMapper;
import org.example.product.mapper.UserMapper;
import org.example.product.service.ProductDomainService;
import org.example.trace.command.TraceRecordCommand;
import org.example.trace.constant.TraceEntityType;
import org.example.trace.model.TraceabilityVO;
import org.example.trace.support.TraceabilityChainService;
import org.example.trace.util.TraceSnapshotFactory;
import org.example.user.POJO.User;
import org.example.user.VO.BuyerViewSellerVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ProductDomainServiceImpl implements ProductDomainService {

    private static final String SOURCE_SERVICE = "service-product";
    private static final String EVENT_GOODS_PUBLISHED = "GOODS_PUBLISHED";

    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final ShopCategoryMapper shopCategoryMapper;
    private final GoodsCollectMapper goodsCollectMapper;
    private final TraceabilityChainService traceabilityChainService;

    public ProductDomainServiceImpl(GoodsMapper goodsMapper,
                                    UserMapper userMapper,
                                    ShopCategoryMapper shopCategoryMapper,
                                    GoodsCollectMapper goodsCollectMapper,
                                    TraceabilityChainService traceabilityChainService) {
        this.goodsMapper = goodsMapper;
        this.userMapper = userMapper;
        this.shopCategoryMapper = shopCategoryMapper;
        this.goodsCollectMapper = goodsCollectMapper;
        this.traceabilityChainService = traceabilityChainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(GoodsDTO goodsDTO) {
        Goods goods = new Goods();
        BeanUtils.copyProperties(goodsDTO, goods);
        LocalDateTime now = LocalDateTime.now();
        goods.setCreateTime(now);
        goods.setUpdateTime(now);
        goods.setGoodsStatus(resolveGoodsStatus(goods.getStock(), goods.getGoodsStatus()));
        goodsMapper.add(goods);
        List<GoodsImage> imageList = goodsDTO.getImageList();
        if (imageList != null && !imageList.isEmpty()) {
            goodsMapper.insertGoodsImages(goods.getId(), imageList);
        }
        recordGoodsEvent(goodsMapper.findById(goods.getId()),
                goodsMapper.findGoodsImagesByGoodsId(goods.getId()),
                EVENT_GOODS_PUBLISHED,
                "商品发布后生成唯一链上凭证");
    }

    @Override
    @Transactional(readOnly = true)
    public PageBean<GoodsVO> list(GoodsQueryDTO queryDTO) {
        queryDTO.setGoodsStatus(GoodsStatusEnum.ON_SALE.getCode());
        return alllist(queryDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public GoodsDetailVO findById(Integer id) {
        Goods goods = goodsMapper.findById(id);
        if (goods == null) {
            throw new IllegalArgumentException("商品不存在");
        }

        GoodsDetailVO detailVO = new GoodsDetailVO();
        BeanUtils.copyProperties(goods, detailVO);
        detailVO.setImageList(goodsMapper.findGoodsImagesByGoodsId(id));
        if (goods.getOriginalPrice() != null && goods.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discount = goods.getSellPrice().divide(goods.getOriginalPrice(), 2, BigDecimal.ROUND_HALF_UP);
            detailVO.setDiscount(discount.multiply(BigDecimal.TEN) + "折");
        } else {
            detailVO.setDiscount("无折扣");
        }
        if (shopCategoryMapper.findById(goods.getCategoryId()) != null) {
            detailVO.setCategoryName(shopCategoryMapper.findById(goods.getCategoryId()).getCategoryName());
        }
        detailVO.setIsNewName(GoodsIsNewEnum.getNameByCode(goods.getIsNew()));
        detailVO.setGoodsStatusName(GoodsStatusEnum.getNameByCode(goods.getGoodsStatus()));
        User seller = userMapper.findById(goods.getSellerId());
        if (seller != null) {
            detailVO.setSellerNickname(seller.getNickname());
            detailVO.setSellerAvatar(seller.getUserPic());
        }
        detailVO.setCollectCount(goodsCollectMapper.allList(id));
        detailVO.setTraceability(traceById(id));
        return detailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(GoodsDTO goodsDTO) {
        Goods goods = goodsMapper.findById(goodsDTO.getId());
        if (goods == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        BeanUtils.copyProperties(goodsDTO, goods);
        goods.setGoodsStatus(resolveGoodsStatus(goods.getStock(), goods.getGoodsStatus()));
        goods.setUpdateTime(LocalDateTime.now());
        goodsMapper.update(goods);
        goodsMapper.deleteGoodsImagesByGoodsId(goods.getId());
        if (goodsDTO.getImageList() != null && !goodsDTO.getImageList().isEmpty()) {
            goodsMapper.insertGoodsImages(goods.getId(), goodsDTO.getImageList());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        Goods goods = goodsMapper.findById(id);
        if (goods == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        goodsMapper.deleteGoodsImagesByGoodsId(id);
        goodsMapper.delete(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id, Integer status) {
        goodsMapper.updateStatus(id, status, LocalDateTime.now());
        Goods goods = goodsMapper.findById(id);
        if (goods == null) {
            throw new IllegalArgumentException("商品不存在");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BuyerViewSellerVO findSellerByUserId(Integer id) {
        return goodsMapper.findSellerByUserId(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageBean<GoodsVO> alllist(GoodsQueryDTO queryDTO) {
        PageHelper.startPage(queryDTO.getPageNum(), queryDTO.getPageSize());
        List<GoodsVO> list = goodsMapper.allList(queryDTO);
        for (GoodsVO goodsVO : list) {
            goodsVO.setImageList(goodsMapper.findGoodsImagesByGoodsId(goodsVO.getId()));
            goodsVO.setCollectCount(goodsCollectMapper.allList(goodsVO.getId()));
            goodsVO.setIsNewName(GoodsIsNewEnum.getNameByCode(goodsVO.getIsNew()));
            goodsVO.setGoodsStatusName(GoodsStatusEnum.getNameByCode(goodsVO.getGoodsStatus()));
        }
        Page<GoodsVO> page = (Page<GoodsVO>) list;
        PageBean<GoodsVO> pageBean = new PageBean<>();
        pageBean.setTotal(page.getTotal());
        pageBean.setItems(page.getResult());
        return pageBean;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoodsVO> listByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<GoodsVO> goodsVOS = goodsMapper.listByIds(ids);
        for (GoodsVO goodsVO : goodsVOS) {
            goodsVO.setImageList(goodsMapper.findGoodsImagesByGoodsId(goodsVO.getId()));
            goodsVO.setCollectCount(goodsCollectMapper.allList(goodsVO.getId()));
            goodsVO.setIsNewName(GoodsIsNewEnum.getNameByCode(goodsVO.getIsNew()));
            goodsVO.setGoodsStatusName(GoodsStatusEnum.getNameByCode(goodsVO.getGoodsStatus()));
        }
        return goodsVOS;
    }

    @Override
    @Transactional(readOnly = true)
    public TraceabilityVO traceById(Integer id) {
        Goods goods = goodsMapper.findById(id);
        if (goods == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        return traceabilityChainService.getTraceability(
                TraceEntityType.GOODS.getCode(),
                id,
                TraceSnapshotFactory.buildGoodsSnapshot(goods, goodsMapper.findGoodsImagesByGoodsId(id))
        );
    }

    private void recordGoodsEvent(Goods goods, List<GoodsImage> imageList, String eventType, String summary) {
        if (goods == null) {
            return;
        }
        TraceRecordCommand command = new TraceRecordCommand();
        command.setEntityType(TraceEntityType.GOODS.getCode());
        command.setEntityId(goods.getId());
        command.setBusinessNo("GOODS-" + goods.getId());
        command.setEventType(eventType);
        command.setOperatorId(goods.getSellerId());
        command.setSourceService(SOURCE_SERVICE);
        command.setSummary(summary);
        command.setEventTime(goods.getUpdateTime() != null ? goods.getUpdateTime() : goods.getCreateTime());
        command.setTraceIdPrefix(TraceEntityType.GOODS.getPrefix());
        command.setSnapshot(TraceSnapshotFactory.buildGoodsSnapshot(goods, imageList));
        traceabilityChainService.recordEvent(command);
    }

    private Integer resolveGoodsStatus(Integer stock, Integer currentStatus) {
        if (stock != null && stock <= 0) {
            return GoodsStatusEnum.SOLD_OUT.getCode();
        }
        if (stock != null && stock > 0 && (currentStatus == null || Objects.equals(currentStatus, GoodsStatusEnum.SOLD_OUT.getCode()))) {
            return GoodsStatusEnum.ON_SALE.getCode();
        }
        return currentStatus;
    }
}

