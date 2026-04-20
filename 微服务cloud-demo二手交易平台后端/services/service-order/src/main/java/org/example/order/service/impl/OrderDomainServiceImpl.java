package org.example.order.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.address.POJO.Address;
import org.example.common.PageBean;
import org.example.goods.DTO.RefundApplyDTO;
import org.example.goods.DTO.RefundHandleDTO;
import org.example.goods.POJO.Goods;
import org.example.goods.POJO.GoodsImage;
import org.example.order.DTO.OrderCreateDTO;
import org.example.order.DTO.OrderQueryDTO;
import org.example.order.POJO.OrderInfo;
import org.example.order.VO.OrderDetailVO;
import org.example.order.VO.OrderVO;
import org.example.order.constant.GoodsStatusEnum;
import org.example.order.constant.OrderStatusEnum;
import org.example.order.constant.PayTypeEnum;
import org.example.order.constant.RefundStatusEnum;
import org.example.order.mapper.AddressMapper;
import org.example.order.mapper.GoodsMapper;
import org.example.order.mapper.OrderMapper;
import org.example.order.mapper.UserMapper;
import org.example.order.service.OrderDomainService;
import org.example.order.util.RedisDistributedLock;
import org.example.trace.command.TraceRecordCommand;
import org.example.trace.constant.TraceEntityType;
import org.example.trace.model.TraceAnchorInfo;
import org.example.trace.model.TraceabilityVO;
import org.example.trace.support.TraceabilityChainService;
import org.example.trace.util.TraceSnapshotFactory;
import org.example.user.POJO.User;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderDomainServiceImpl implements OrderDomainService {

    private static final String STOCK_CACHE_KEY = "goods:stock:";
    private static final String ORDER_IDEMPOTENT_KEY = "order:idempotent:";
    private static final String ORDER_SOURCE_SERVICE = "service-order";
    private static final String GOODS_SOURCE_SERVICE = "service-order";
    private static final String EVENT_ORDER_CREATED = "ORDER_CREATED";
    private static final String EVENT_ORDER_PAID = "ORDER_PAID";
    private static final String EVENT_ORDER_SENT = "ORDER_SENT";
    private static final String EVENT_ORDER_RECEIVED = "ORDER_RECEIVED";
    private static final String EVENT_ORDER_CANCELED = "ORDER_CANCELED";
    private static final String EVENT_ORDER_REFUND_APPLIED = "ORDER_REFUND_APPLIED";
    private static final String EVENT_ORDER_REFUND_HANDLED = "ORDER_REFUND_HANDLED";
    private static final String EVENT_ORDER_STATUS_ADMIN_CHANGED = "ORDER_STATUS_ADMIN_CHANGED";
    private static final String EVENT_ORDER_DELETED = "ORDER_DELETED";
    private static final String EVENT_GOODS_ORDER_LOCKED = "GOODS_ORDER_LOCKED";
    private static final String EVENT_GOODS_ORDER_PAID = "GOODS_ORDER_PAID";
    private static final String EVENT_GOODS_ORDER_SENT = "GOODS_ORDER_SENT";
    private static final String EVENT_GOODS_OWNERSHIP_TRANSFERRED = "GOODS_OWNERSHIP_TRANSFERRED";
    private static final String EVENT_GOODS_ORDER_REFUND_APPLIED = "GOODS_ORDER_REFUND_APPLIED";
    private static final String EVENT_GOODS_ORDER_REFUND_HANDLED = "GOODS_ORDER_REFUND_HANDLED";
    private static final String EVENT_GOODS_ORDER_ADMIN_CHANGED = "GOODS_ORDER_ADMIN_CHANGED";
    private static final String EVENT_GOODS_ORDER_DELETED = "GOODS_ORDER_DELETED";
    private static final String EVENT_GOODS_ORDER_RESTOCKED = "GOODS_ORDER_RESTOCKED";

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final AddressMapper addressMapper;
    private final RedisDistributedLock redisDistributedLock;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final TraceabilityChainService traceabilityChainService;

    public OrderDomainServiceImpl(OrderMapper orderMapper,
                                  GoodsMapper goodsMapper,
                                  UserMapper userMapper,
                                  AddressMapper addressMapper,
                                  RedisDistributedLock redisDistributedLock,
                                  RedisTemplate<Object, Object> redisTemplate,
                                  TraceabilityChainService traceabilityChainService) {
        this.orderMapper = orderMapper;
        this.goodsMapper = goodsMapper;
        this.userMapper = userMapper;
        this.addressMapper = addressMapper;
        this.redisDistributedLock = redisDistributedLock;
        this.redisTemplate = redisTemplate;
        this.traceabilityChainService = traceabilityChainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateDTO orderCreateDTO) {
        if (orderCreateDTO.getGoodsNum() == null || orderCreateDTO.getGoodsNum() < 1) {
            orderCreateDTO.setGoodsNum(1);
        }
        if (orderCreateDTO.getRequestId() == null || orderCreateDTO.getRequestId().isBlank()) {
            throw new IllegalArgumentException("requestId 不能为空");
        }

        String idempotentKey = ORDER_IDEMPOTENT_KEY + orderCreateDTO.getRequestId();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 10, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(success)) {
            throw new IllegalStateException("请勿重复提交订单");
        }

        Integer goodsId = orderCreateDTO.getGoodsId();
        String lockValue = null;
        try {
            lockValue = redisDistributedLock.acquireLock(goodsId.toString());
            if (lockValue == null) {
                throw new IllegalStateException("系统繁忙，请稍后再试");
            }

            Goods goods = goodsMapper.findById(goodsId);
            if (goods == null) {
                throw new IllegalArgumentException("商品不存在");
            }
            if (!Objects.equals(goods.getGoodsStatus(), GoodsStatusEnum.ON_SALE.getCode())) {
                throw new IllegalStateException("商品当前不可下单");
            }
            if (goods.getStock() < orderCreateDTO.getGoodsNum()) {
                throw new IllegalStateException("库存不足");
            }

            OrderInfo orderInfo = new OrderInfo();
            BeanUtils.copyProperties(orderCreateDTO, orderInfo);
            LocalDateTime now = LocalDateTime.now();
            orderInfo.setOrderNo(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%06d", new Random().nextInt(999999)));
            orderInfo.setGoodsName(goods.getGoodsName());
            orderInfo.setGoodsPic(goods.getGoodsPic());
            orderInfo.setGoodsPrice(goods.getSellPrice());
            orderInfo.setTotalAmount(goods.getSellPrice().multiply(BigDecimal.valueOf(orderCreateDTO.getGoodsNum())));
            orderInfo.setOrderStatus(OrderStatusEnum.PENDING_PAY.getCode());
            orderInfo.setPayType(PayTypeEnum.UNPAID.getCode());
            orderInfo.setRefundStatus(RefundStatusEnum.NO_REFUND.getCode());
            orderInfo.setRefundAmount(BigDecimal.ZERO);
            orderInfo.setCreateTime(now);
            orderInfo.setUpdateTime(now);
            orderMapper.add(orderInfo);

            goods.setStock(goods.getStock() - orderCreateDTO.getGoodsNum());
            if (goods.getStock() == 0) {
                goods.setGoodsStatus(GoodsStatusEnum.SOLD_OUT.getCode());
            }
            goods.setUpdateTime(now);
            goodsMapper.update(goods);
            redisTemplate.opsForValue().set(STOCK_CACHE_KEY + goodsId, goods.getStock(), 1, TimeUnit.HOURS);

            OrderInfo latestOrder = requireOrder(orderInfo.getId());
            recordOrderEvent(latestOrder, EVENT_ORDER_CREATED, "订单创建并完成快照上链", latestOrder.getBuyerId());
            recordGoodsEvent(goods, EVENT_GOODS_ORDER_LOCKED, "订单创建后商品库存与状态变更上链", latestOrder.getBuyerId(), latestOrder);
            return latestOrder.getOrderNo();
        } catch (Exception ex) {
            redisTemplate.delete(idempotentKey);
            throw ex;
        } finally {
            if (lockValue != null) {
                redisDistributedLock.releaseLock(goodsId.toString(), lockValue);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageBean<OrderVO> list(OrderQueryDTO queryDTO) {
        PageHelper.startPage(queryDTO.getPageNum(), queryDTO.getPageSize());
        List<OrderVO> list = orderMapper.list(queryDTO);
        for (OrderVO order : list) {
            order.setOrderStatusName(OrderStatusEnum.getNameByCode(order.getOrderStatus()));
            order.setPayTypeName(PayTypeEnum.getNameByCode(order.getPayType()));
            order.setRefundStatusName(RefundStatusEnum.getNameByCode(order.getRefundStatus()));
            Address address = addressMapper.findById(order.getAddressId());
            if (address != null) {
                order.setAddress(address.getProvince() + address.getCity() + address.getDistrict() + address.getDetailAddr());
            }
        }
        Page<OrderVO> page = (Page<OrderVO>) list;
        PageBean<OrderVO> pageBean = new PageBean<>();
        pageBean.setTotal(page.getTotal());
        pageBean.setItems(page.getResult());
        return pageBean;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailVO findById(Integer id) {
        OrderInfo orderInfo = orderMapper.findById(id);
        if (orderInfo == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        OrderDetailVO detailVO = new OrderDetailVO();
        BeanUtils.copyProperties(orderInfo, detailVO);
        User buyer = userMapper.findById(orderInfo.getBuyerId());
        if (buyer != null) {
            detailVO.setBuyerNickname(buyer.getNickname());
            detailVO.setBuyerPhone(buyer.getPhone());
        }
        User seller = userMapper.findById(orderInfo.getSellerId());
        if (seller != null) {
            detailVO.setSellerNickname(seller.getNickname());
            detailVO.setSellerPhone(seller.getPhone());
        }
        Address address = addressMapper.findById(orderInfo.getAddressId());
        if (address != null) {
            detailVO.setAddress(address.getProvince() + address.getCity() + address.getDistrict() + address.getDetailAddr());
        }
        detailVO.setOrderStatusName(OrderStatusEnum.getNameByCode(orderInfo.getOrderStatus()));
        detailVO.setPayTypeName(PayTypeEnum.getNameByCode(orderInfo.getPayType()));
        detailVO.setRefundStatusName(RefundStatusEnum.getNameByCode(orderInfo.getRefundStatus()));
        detailVO.setTraceability(traceById(id));
        return detailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id) {
        LocalDateTime now = LocalDateTime.now();
        orderMapper.updateStatus(id, OrderStatusEnum.PENDING_DELIVERY.getCode(), PayTypeEnum.WECHAT.getCode(), now);
        OrderInfo latestOrder = requireOrder(id);
        recordOrderEvent(latestOrder, EVENT_ORDER_PAID, "订单支付状态已上链", latestOrder.getBuyerId());
        recordGoodsTradeEvent(latestOrder, EVENT_GOODS_ORDER_PAID, "订单支付后交易凭证已同步上链", latestOrder.getBuyerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Integer id) {
        OrderInfo orderInfo = requireOrder(id);
        if (!Arrays.asList(OrderStatusEnum.PENDING_PAY.getCode(), OrderStatusEnum.PENDING_DELIVERY.getCode()).contains(orderInfo.getOrderStatus())) {
            throw new IllegalStateException("当前订单状态不支持取消");
        }
        restoreStockAndCancel(orderInfo, orderInfo.getBuyerId(), orderInfo.getBuyerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Integer id) {
        OrderInfo orderInfo = requireOrder(id);
        if (!Objects.equals(orderInfo.getOrderStatus(), OrderStatusEnum.PENDING_RECEIVE.getCode())) {
            throw new IllegalStateException("当前订单状态不支持确认收货");
        }
        orderMapper.updateConfirmStatus(id, OrderStatusEnum.COMPLETED.getCode());
        OrderInfo latestOrder = requireOrder(id);
        recordOrderEvent(latestOrder, EVENT_ORDER_RECEIVED, "订单确认收货已上链", latestOrder.getBuyerId());
        recordGoodsTradeEvent(latestOrder, EVENT_GOODS_OWNERSHIP_TRANSFERRED, "交易完成，商品权属变更已自动上链", latestOrder.getBuyerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRefund(RefundApplyDTO refundApplyDTO) {
        OrderInfo orderInfo = requireOrder(refundApplyDTO.getOrderId());
        orderMapper.applyRefund(orderInfo.getId(),
                RefundStatusEnum.REFUNDING.getCode(),
                refundApplyDTO.getRefundAmount(),
                refundApplyDTO.getRefundReason(),
                LocalDateTime.now(),
                refundApplyDTO.getRefundRemark(),
                LocalDateTime.now());
        OrderInfo latestOrder = requireOrder(orderInfo.getId());
        recordOrderEvent(latestOrder, EVENT_ORDER_REFUND_APPLIED, "订单退款申请已上链", latestOrder.getBuyerId());
        recordGoodsTradeEvent(latestOrder, EVENT_GOODS_ORDER_REFUND_APPLIED, "订单退款申请已同步到商品链上凭证", latestOrder.getBuyerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRefund(RefundHandleDTO refundHandleDTO) {
        OrderInfo orderInfo = requireOrder(refundHandleDTO.getOrderId());
        orderMapper.handleRefund(orderInfo.getId(),
                refundHandleDTO.getHandleResult(),
                refundHandleDTO.getRefundRemark(),
                LocalDateTime.now(),
                LocalDateTime.now());
        OrderInfo latestOrder = requireOrder(orderInfo.getId());
        recordOrderEvent(latestOrder, EVENT_ORDER_REFUND_HANDLED, "订单退款处理结果已上链", latestOrder.getSellerId());
        recordGoodsTradeEvent(latestOrder, EVENT_GOODS_ORDER_REFUND_HANDLED, "订单退款处理结果已同步到商品链上凭证", latestOrder.getSellerId());
        if (Objects.equals(refundHandleDTO.getHandleResult(), RefundStatusEnum.REFUND_SUCCESS.getCode())) {
            restoreStockAndCancel(latestOrder, latestOrder.getSellerId(), latestOrder.getSellerId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Integer id) {
        OrderInfo orderInfo = requireOrder(id);
        recordOrderEvent(orderInfo, EVENT_ORDER_DELETED, "订单删除前保留链上存证", orderInfo.getBuyerId());
        orderMapper.deleteOrder(id);
        Goods goods = goodsMapper.findById(orderInfo.getGoodsId());
        OrderInfo latestRelatedOrder = orderMapper.findLatestByGoodsId(orderInfo.getGoodsId());
        recordGoodsEvent(goods, EVENT_GOODS_ORDER_DELETED, "订单删除后商品链上关联快照已更新", orderInfo.getBuyerId(), latestRelatedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendOrder(Integer id) {
        LocalDateTime now = LocalDateTime.now();
        orderMapper.updateSendStatus(id, OrderStatusEnum.PENDING_RECEIVE.getCode(), now);
        OrderInfo latestOrder = requireOrder(id);
        recordOrderEvent(latestOrder, EVENT_ORDER_SENT, "订单发货状态已上链", latestOrder.getSellerId());
        recordGoodsTradeEvent(latestOrder, EVENT_GOODS_ORDER_SENT, "订单发货后物流状态已同步上链", latestOrder.getSellerId());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderVO findByOrderNo(String orderNo) {
        return orderMapper.findByOrderNo(orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdateStatus(Integer id, Integer orderStatus) {
        orderMapper.adminUpdateStatus(id, orderStatus);
        OrderInfo latestOrder = requireOrder(id);
        recordOrderEvent(latestOrder, EVENT_ORDER_STATUS_ADMIN_CHANGED, "后台修改订单状态已上链", null);
        recordGoodsTradeEvent(latestOrder, EVENT_GOODS_ORDER_ADMIN_CHANGED, "后台同步修改交易状态，商品链上凭证已更新", null);
    }

    @Override
    @Transactional(readOnly = true)
    public TraceabilityVO traceById(Integer id) {
        OrderInfo orderInfo = requireOrder(id);
        return traceabilityChainService.getTraceability(
                TraceEntityType.ORDER.getCode(),
                id,
                TraceSnapshotFactory.buildOrderSnapshot(orderInfo, resolveGoodsTraceId(orderInfo.getGoodsId()))
        );
    }

    @PostConstruct
    public void initStockCache() {
        List<Goods> goodsList = goodsMapper.findAllOnSale();
        for (Goods goods : goodsList) {
            redisTemplate.opsForValue().set(STOCK_CACHE_KEY + goods.getId(), goods.getStock(), 24, TimeUnit.HOURS);
        }
        log.info("init stock cache finished, size={}", goodsList.size());
    }

    private void restoreStockAndCancel(OrderInfo orderInfo, Integer orderOperatorId, Integer goodsOperatorId) {
        Integer goodsId = orderInfo.getGoodsId();
        String lockValue = redisDistributedLock.acquireLock(goodsId.toString());
        if (lockValue == null) {
            throw new IllegalStateException("系统繁忙，请稍后再试");
        }
        try {
            orderMapper.updateCancelStatus(orderInfo.getId(), OrderStatusEnum.CANCELED.getCode());
            Goods goods = goodsMapper.findById(goodsId);
            if (goods != null) {
                goods.setStock(goods.getStock() + orderInfo.getGoodsNum());
                goods.setGoodsStatus(GoodsStatusEnum.ON_SALE.getCode());
                goods.setUpdateTime(LocalDateTime.now());
                goodsMapper.update(goods);
                redisTemplate.opsForValue().set(STOCK_CACHE_KEY + goodsId, goods.getStock(), 1, TimeUnit.HOURS);
                OrderInfo canceledOrder = requireOrder(orderInfo.getId());
                recordGoodsEvent(goods, EVENT_GOODS_ORDER_RESTOCKED, "订单取消或退款后商品库存恢复已上链", goodsOperatorId, canceledOrder);
            }
            OrderInfo canceledOrder = requireOrder(orderInfo.getId());
            recordOrderEvent(canceledOrder, EVENT_ORDER_CANCELED, "订单取消状态已上链", orderOperatorId);
        } finally {
            redisDistributedLock.releaseLock(goodsId.toString(), lockValue);
        }
    }

    private OrderInfo requireOrder(Integer id) {
        OrderInfo orderInfo = orderMapper.findById(id);
        if (orderInfo == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return orderInfo;
    }

    private void recordOrderEvent(OrderInfo orderInfo, String eventType, String summary, Integer operatorId) {
        TraceRecordCommand command = new TraceRecordCommand();
        command.setEntityType(TraceEntityType.ORDER.getCode());
        command.setEntityId(orderInfo.getId());
        command.setBusinessNo(orderInfo.getOrderNo());
        command.setEventType(eventType);
        command.setOperatorId(operatorId);
        command.setSourceService(ORDER_SOURCE_SERVICE);
        command.setSummary(summary);
        command.setEventTime(resolveOrderEventTime(orderInfo));
        command.setTraceIdPrefix(TraceEntityType.ORDER.getPrefix());
        command.setSnapshot(TraceSnapshotFactory.buildOrderSnapshot(orderInfo, resolveGoodsTraceId(orderInfo.getGoodsId())));
        traceabilityChainService.recordEvent(command);
    }

    private void recordGoodsEvent(Goods goods, String eventType, String summary, Integer operatorId) {
        recordGoodsEvent(goods, eventType, summary, operatorId, null);
    }

    private void recordGoodsEvent(Goods goods, String eventType, String summary, Integer operatorId, OrderInfo relatedOrder) {
        if (goods == null) {
            return;
        }
        TraceRecordCommand command = new TraceRecordCommand();
        command.setEntityType(TraceEntityType.GOODS.getCode());
        command.setEntityId(goods.getId());
        command.setBusinessNo("GOODS-" + goods.getId());
        command.setEventType(eventType);
        command.setOperatorId(operatorId);
        command.setSourceService(GOODS_SOURCE_SERVICE);
        command.setSummary(summary);
        command.setEventTime(resolveGoodsEventTime(goods, relatedOrder));
        command.setTraceIdPrefix(TraceEntityType.GOODS.getPrefix());
        command.setSnapshot(TraceSnapshotFactory.buildGoodsSnapshot(
                goods,
                resolveGoodsImages(goods.getId()),
                relatedOrder,
                relatedOrder == null ? null : resolveOrderTraceId(relatedOrder.getId())
        ));
        traceabilityChainService.recordEvent(command);
    }

    private void recordGoodsTradeEvent(OrderInfo orderInfo, String eventType, String summary, Integer operatorId) {
        if (orderInfo == null) {
            return;
        }
        Goods goods = goodsMapper.findById(orderInfo.getGoodsId());
        recordGoodsEvent(goods, eventType, summary, operatorId, orderInfo);
    }

    private List<GoodsImage> resolveGoodsImages(Integer goodsId) {
        return goodsMapper.findGoodsImagesByGoodsId(goodsId);
    }

    private String resolveGoodsTraceId(Integer goodsId) {
        TraceAnchorInfo anchorInfo = traceabilityChainService.getAnchorInfo(TraceEntityType.GOODS.getCode(), goodsId);
        return anchorInfo == null ? null : anchorInfo.getTraceId();
    }

    private String resolveOrderTraceId(Integer orderId) {
        if (orderId == null) {
            return null;
        }
        TraceAnchorInfo anchorInfo = traceabilityChainService.getAnchorInfo(TraceEntityType.ORDER.getCode(), orderId);
        return anchorInfo == null ? null : anchorInfo.getTraceId();
    }

    private LocalDateTime resolveGoodsEventTime(Goods goods, OrderInfo relatedOrder) {
        if (relatedOrder != null) {
            if (relatedOrder.getUpdateTime() != null) {
                return relatedOrder.getUpdateTime();
            }
            if (relatedOrder.getCreateTime() != null) {
                return relatedOrder.getCreateTime();
            }
        }
        if (goods != null && goods.getUpdateTime() != null) {
            return goods.getUpdateTime();
        }
        return LocalDateTime.now();
    }

    private LocalDateTime resolveOrderEventTime(OrderInfo orderInfo) {
        if (orderInfo.getUpdateTime() != null) {
            return orderInfo.getUpdateTime();
        }
        if (orderInfo.getCreateTime() != null) {
            return orderInfo.getCreateTime();
        }
        return LocalDateTime.now();
    }
}
