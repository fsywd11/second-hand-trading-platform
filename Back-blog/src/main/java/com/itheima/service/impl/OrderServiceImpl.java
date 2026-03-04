package com.itheima.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.itheima.DTO.OrderCreateDTO;
import com.itheima.DTO.OrderQueryDTO;
import com.itheima.DTO.RefundApplyDTO;
import com.itheima.DTO.RefundHandleDTO;
import com.itheima.mapper.AddressMapper;
import com.itheima.mapper.GoodsMapper;
import com.itheima.mapper.OrderMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.*;
import com.itheima.service.OrderService;
import com.itheima.vo.OrderDetailVO;
import com.itheima.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private GoodsMapper goodsMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AddressMapper addressMapper;

    // 1. 创建订单（单商品，扣减库存）
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateDTO orderCreateDTO) {
        // 参数校验
        if (orderCreateDTO == null) {
            throw new IllegalArgumentException("订单参数不能为空");
        }
        if (orderCreateDTO.getGoodsId() == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
        if (orderCreateDTO.getGoodsNum() == null || orderCreateDTO.getGoodsNum() < 1) {
            orderCreateDTO.setGoodsNum(1); // 二手默认购买1件
        }

        // 查询商品信息（校验+获取商品数据）
        Goods goods = goodsMapper.findById(orderCreateDTO.getGoodsId());
        if (goods == null) {
            throw new RuntimeException("商品不存在：ID=" + orderCreateDTO.getGoodsId());
        }
        if (!Objects.equals(goods.getGoodsStatus(), GoodsStatusEnum.ON_SALE.getCode())) {
            throw new RuntimeException("商品[" + goods.getGoodsName() + "]非在售状态，无法下单");
        }
        if (goods.getStock() < orderCreateDTO.getGoodsNum()) {
            throw new RuntimeException("商品[" + goods.getGoodsName() + "]库存不足，当前库存：" + goods.getStock());
        }

        // 生成订单编号
        String orderNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                String.format("%06d", new Random().nextInt(999999));

        // 构建订单主表（冗余商品信息，默认无退款）
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderCreateDTO, orderInfo);
        // 商品信息冗余存储
        orderInfo.setGoodsName(goods.getGoodsName());
        orderInfo.setGoodsPic(goods.getGoodsPic());
        orderInfo.setGoodsPrice(goods.getSellPrice());
        // 计算总金额
        orderInfo.setTotalAmount(goods.getSellPrice().multiply(new BigDecimal(orderCreateDTO.getGoodsNum())));
        // 订单初始状态
        orderInfo.setOrderNo(orderNo);
        orderInfo.setOrderStatus(OrderStatusEnum.PENDING_PAY.getCode());
        orderInfo.setPayType(PayTypeEnum.UNPAID.getCode()); // 未支付
        orderInfo.setRefundStatus(RefundStatusEnum.NO_REFUND.getCode()); // 初始无退款
        orderInfo.setRefundAmount(BigDecimal.ZERO);
        orderInfo.setCreateTime(LocalDateTime.now());
        orderInfo.setUpdateTime(LocalDateTime.now());

        // 保存订单
        orderMapper.add(orderInfo);

        // 扣减商品库存+更新商品状态为已售出
        goods.setStock(goods.getStock() - orderCreateDTO.getGoodsNum());
        //库存为零时
        if (goods.getStock() == 0) {
            // 改为已售罄
            goods.setGoodsStatus(GoodsStatusEnum.SOLD_OUT.getCode());
        }
        goods.setUpdateTime(LocalDateTime.now());
        goodsMapper.update(goods);

        log.info("订单创建成功，订单号：{}，商品ID：{}", orderNo, orderCreateDTO.getGoodsId());
        return orderNo;
    }

    // 2. 分页查询订单（含退款状态）
    @Override
    @Transactional(readOnly = true)
    public PageBean<OrderVO> list(OrderQueryDTO queryDTO) {
        PageBean<OrderVO> pb = new PageBean<>();
        PageHelper.startPage(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 查询订单列表
        List<OrderVO> orderList = orderMapper.list(queryDTO);
        Page<OrderVO> p = (Page<OrderVO>) orderList;

        // 补充枚举名称（订单状态、支付方式、退款状态）
        orderList.forEach(order -> {
            order.setOrderStatusName(OrderStatusEnum.getNameByCode(order.getOrderStatus()));
            order.setPayTypeName(order.getPayType() == 1 ? "微信" : order.getPayType() == 2 ? "支付宝" : "未支付");
            order.setRefundStatusName(RefundStatusEnum.getNameByCode(order.getRefundStatus()));
        });

        pb.setTotal(p.getTotal());
        pb.setItems(orderList);
        return pb;
    }

    // 3. 查询订单详情（含退款信息）
    @Override
    @Transactional(readOnly = true)
    public OrderDetailVO findById(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("订单ID不合法");
        }

        // 查询订单主信息（含退款字段）
        OrderInfo orderInfo = orderMapper.findById(id);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在：" + id);
        }

        // 转换为详情VO
        OrderDetailVO vo = new OrderDetailVO();
        BeanUtils.copyProperties(orderInfo, vo);

        // 补充买家信息
        User buyer = userMapper.findById(orderInfo.getBuyerId());
        if (buyer != null) {
            vo.setBuyerNickname(buyer.getNickname());
            vo.setBuyerPhone(buyer.getPhone());
        }

        // 补充卖家信息
        User seller = userMapper.findById(orderInfo.getSellerId());
        if (seller != null) {
            vo.setSellerNickname(seller.getNickname());
            vo.setSellerPhone(seller.getPhone());
        }

        // 补充收货地址
        Address address = addressMapper.findById(orderInfo.getAddressId());
        if (address != null) {
            vo.setAddress(address.getProvince() + address.getCity() + address.getDistrict() + address.getDetailAddr());
        }

        // 补充枚举名称（订单、支付、退款）
        vo.setOrderStatusName(OrderStatusEnum.getNameByCode(orderInfo.getOrderStatus()));
        vo.setPayTypeName(orderInfo.getPayType() == 1 ? "微信" : orderInfo.getPayType() == 2 ? "支付宝" : "未支付");
        vo.setRefundStatusName(RefundStatusEnum.getNameByCode(orderInfo.getRefundStatus()));

        return vo;
    }

    // 4. 更新订单状态（支付/发货/完成）
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id, Integer status) {
        if (id == null || status == null) {
            throw new IllegalArgumentException("订单ID和状态不能为空");
        }
        if (!Arrays.asList(1,2,3,4,5).contains(status)) {
            throw new IllegalArgumentException("订单状态不合法");
        }

        // 校验订单是否存在
        OrderInfo orderInfo = orderMapper.findById(id);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在：" + id);
        }

        // 更新订单状态
        orderMapper.updateStatus(id, status, LocalDateTime.now());

        // 补充时间字段
        OrderInfo order = new OrderInfo();
        order.setId(id);
        order.setUpdateTime(LocalDateTime.now());
        if (status == OrderStatusEnum.PENDING_RECEIVE.getCode()) {
            order.setDeliveryTime(LocalDateTime.now()); // 发货时间
        } else if (status == OrderStatusEnum.PENDING_DELIVERY.getCode()) {
            order.setPayTime(LocalDateTime.now()); // 支付时间
        }
        orderMapper.update(order);

        log.info("订单状态更新成功，订单ID：{}，新状态：{}", id, status);
    }

    // 5. 取消订单（恢复库存）
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("订单ID不合法");
        }

        OrderInfo orderInfo = orderMapper.findById(id);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在：" + id);
        }

        // 校验订单状态（只能取消待付款/待发货，且无退款中）
        if (!Arrays.asList(1,2).contains(orderInfo.getOrderStatus())) {
            throw new RuntimeException("当前订单状态不支持取消，状态：" + orderInfo.getOrderStatus());
        }
        if (Objects.equals(orderInfo.getRefundStatus(), RefundStatusEnum.REFUNDING.getCode())) {
            throw new RuntimeException("订单正在退款中，无法取消");
        }

        // 1. 更新订单状态为已取消
        orderMapper.updateStatus(id, OrderStatusEnum.CANCELED.getCode(), LocalDateTime.now());

        // 2. 更新取消时间
        OrderInfo order = new OrderInfo();
        order.setId(id);
        order.setCancelTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.update(order);

        // 3. 恢复商品库存+状态
        Goods goods = goodsMapper.findById(orderInfo.getGoodsId());
        if (goods != null) {
            goods.setStock(goods.getStock() + orderInfo.getGoodsNum());
            goods.setGoodsStatus(GoodsStatusEnum.ON_SALE.getCode());
            goods.setUpdateTime(LocalDateTime.now());
            goodsMapper.update(goods);
            log.info("订单取消，商品[{}]库存恢复，恢复数量：{}", goods.getGoodsName(), orderInfo.getGoodsNum());
        }

        log.info("订单取消成功，订单ID：{}", id);
    }

    // 6. 确认收货
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("订单ID不合法");
        }

        OrderInfo orderInfo = orderMapper.findById(id);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在：" + id);
        }

        // 校验订单状态（只能确认待收货，且无退款中）
        if (!Objects.equals(orderInfo.getOrderStatus(), OrderStatusEnum.PENDING_RECEIVE.getCode())) {
            throw new RuntimeException("当前订单状态不支持确认收货");
        }
        if (Objects.equals(orderInfo.getRefundStatus(), RefundStatusEnum.REFUNDING.getCode())) {
            throw new RuntimeException("订单正在退款中，无法确认收货");
        }

        // 更新订单状态为已完成
        orderMapper.updateStatus(id, OrderStatusEnum.COMPLETED.getCode(), LocalDateTime.now());

        // 更新收货时间
        OrderInfo order = new OrderInfo();
        order.setId(id);
        order.setReceiveTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.update(order);

        log.info("订单确认收货成功，订单ID：{}", id);
    }

    // 7. 申请退款（核心新增）
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRefund(RefundApplyDTO refundApplyDTO) {
        if (refundApplyDTO == null) {
            throw new IllegalArgumentException("退款参数不能为空");
        }

        Integer orderId = refundApplyDTO.getOrderId();
        // 校验订单是否存在
        OrderInfo orderInfo = orderMapper.findById(orderId);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在：" + orderId);
        }

        // 校验退款状态（只能对“无退款”的订单申请）
        if (!Objects.equals(orderInfo.getRefundStatus(), RefundStatusEnum.NO_REFUND.getCode())) {
            throw new RuntimeException("当前订单已申请退款，无需重复申请");
        }

        // 校验订单状态（只能对已支付、待发货、待收货、已完成的订单申请退款）
        List<Integer> allowStatus = Arrays.asList(2,3,4); // 待发货、待收货、已完成
        if (orderInfo.getPayType() == 0 || !allowStatus.contains(orderInfo.getOrderStatus())) {
            throw new RuntimeException("当前订单状态不支持申请退款");
        }

        // 校验退款金额（不能大于订单总金额）
        if (refundApplyDTO.getRefundAmount().compareTo(orderInfo.getTotalAmount()) > 0) {
            throw new RuntimeException("退款金额不能大于订单总金额");
        }

        // 申请退款：更新退款状态为“退款中”
        orderMapper.applyRefund(
                orderId,
                RefundStatusEnum.REFUNDING.getCode(),
                refundApplyDTO.getRefundAmount(),
                refundApplyDTO.getRefundReason(),
                refundApplyDTO.getRefundRemark(),
                LocalDateTime.now()
        );

        log.info("订单退款申请成功，订单ID：{}，退款金额：{}", orderId, refundApplyDTO.getRefundAmount());
    }

    // 8. 处理退款（商家/平台操作，核心新增）
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRefund(RefundHandleDTO refundHandleDTO) {
        if (refundHandleDTO == null) {
            throw new IllegalArgumentException("退款处理参数不能为空");
        }

        Integer orderId = refundHandleDTO.getOrderId();
        Integer handleResult = refundHandleDTO.getHandleResult();
        // 校验处理结果合法性
        if (!Arrays.asList(2,3).contains(handleResult)) {
            throw new IllegalArgumentException("退款处理结果不合法（只能是2-成功/3-失败）");
        }

        // 校验订单是否存在
        OrderInfo orderInfo = orderMapper.findById(orderId);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在：" + orderId);
        }

        // 校验退款状态（只能处理“退款中”的订单）
        if (!Objects.equals(orderInfo.getRefundStatus(), RefundStatusEnum.REFUNDING.getCode())) {
            throw new RuntimeException("当前订单非退款中状态，无法处理");
        }

        LocalDateTime now = LocalDateTime.now();
        // 处理退款：更新退款状态、时间、备注
        orderMapper.handleRefund(
                orderId,
                handleResult,
                refundHandleDTO.getRefundRemark(),
                now,
                now
        );

        // 若退款成功：恢复商品库存+更新订单状态为“已取消”（二手交易退款=取消订单）
        if (Objects.equals(handleResult, RefundStatusEnum.REFUND_SUCCESS.getCode())) {
            // 更新订单状态为已取消
            orderMapper.updateStatus(orderId, OrderStatusEnum.CANCELED.getCode(), now);

            // 恢复商品库存+状态
            Goods goods = goodsMapper.findById(orderInfo.getGoodsId());
            if (goods != null) {
                goods.setStock(goods.getStock() + orderInfo.getGoodsNum());
                goods.setGoodsStatus(GoodsStatusEnum.ON_SALE.getCode());
                goods.setUpdateTime(now);
                goodsMapper.update(goods);
                log.info("订单退款成功，商品[{}]库存恢复，恢复数量：{}", goods.getGoodsName(), orderInfo.getGoodsNum());
            }

            // 更新订单取消时间
            OrderInfo order = new OrderInfo();
            order.setId(orderId);
            order.setCancelTime(now);
            order.setUpdateTime(now);
            orderMapper.update(order);
        }

        log.info("订单退款处理完成，订单ID：{}，处理结果：{}", orderId, handleResult == 2 ? "退款成功" : "退款失败");
    }
}