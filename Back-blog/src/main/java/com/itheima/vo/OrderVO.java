package com.itheima.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表VO（含退款状态）
 */
@Data
public class OrderVO implements Serializable {
    private Integer id;
    private String orderNo;
    private String buyerNickname; // 买家昵称
    private String sellerNickname; // 卖家昵称
    // ===== 商品相关字段 =====
    private Integer goodsId;
    private String goodsName;
    private String goodsPic;
    private BigDecimal goodsPrice;
    private Integer goodsNum;
    // ===== 订单核心字段 =====
    private BigDecimal totalAmount;
    private Integer orderStatus;
    private String orderStatusName; // 订单状态名称
    private Integer payType;
    private String payTypeName; // 支付方式名称
    // ===== 退款相关字段（新增）=====
    private Integer refundStatus;
    private String refundStatusName; // 退款状态名称
    private BigDecimal refundAmount;
    // ===== 公共字段 =====
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}