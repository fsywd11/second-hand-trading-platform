package org.example.trace.util;

import org.example.goods.POJO.Goods;
import org.example.goods.POJO.GoodsImage;
import org.example.order.POJO.OrderInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TraceSnapshotFactory {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private TraceSnapshotFactory() {
    }

    public static Map<String, Object> buildGoodsSnapshot(Goods goods, List<GoodsImage> imageList) {
        return buildGoodsSnapshot(goods, imageList, null, null);
    }

    public static Map<String, Object> buildGoodsSnapshot(Goods goods, List<GoodsImage> imageList, OrderInfo latestOrder) {
        return buildGoodsSnapshot(goods, imageList, latestOrder, null);
    }

    public static Map<String, Object> buildGoodsSnapshot(Goods goods, List<GoodsImage> imageList, OrderInfo latestOrder, String orderTraceId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (goods == null) {
            return snapshot;
        }
        TraceHashUtil.putIfNotNull(snapshot, "goodsId", goods.getId());
        TraceHashUtil.putIfNotNull(snapshot, "goodsName", goods.getGoodsName());
        TraceHashUtil.putIfNotNull(snapshot, "goodsDesc", goods.getGoodsDesc());
        TraceHashUtil.putIfNotNull(snapshot, "goodsPic", goods.getGoodsPic());
        TraceHashUtil.putIfNotNull(snapshot, "categoryId", goods.getCategoryId());
        TraceHashUtil.putIfNotNull(snapshot, "originalPrice", decimalToString(goods.getOriginalPrice()));
        TraceHashUtil.putIfNotNull(snapshot, "sellPrice", decimalToString(goods.getSellPrice()));
        TraceHashUtil.putIfNotNull(snapshot, "sellerId", goods.getSellerId());
        TraceHashUtil.putIfNotNull(snapshot, "goodsStatus", goods.getGoodsStatus());
        TraceHashUtil.putIfNotNull(snapshot, "isNew", goods.getIsNew());
        TraceHashUtil.putIfNotNull(snapshot, "stock", goods.getStock());
        TraceHashUtil.putIfNotNull(snapshot, "createTime", formatTime(goods.getCreateTime()));
        TraceHashUtil.putIfNotNull(snapshot, "updateTime", formatTime(goods.getUpdateTime()));
        snapshot.put("images", buildImageSnapshot(imageList));
        Map<String, Object> orderEvidence = buildGoodsOrderEvidence(latestOrder, orderTraceId);
        if (!orderEvidence.isEmpty()) {
            snapshot.put("latestOrderEvidence", orderEvidence);
        }
        Map<String, Object> ownershipEvidence = buildOwnershipSnapshot(latestOrder);
        if (!ownershipEvidence.isEmpty()) {
            snapshot.put("ownershipEvidence", ownershipEvidence);
        }
        return snapshot;
    }

    public static Map<String, Object> buildOrderSnapshot(OrderInfo orderInfo, String goodsTraceId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (orderInfo == null) {
            return snapshot;
        }
        TraceHashUtil.putIfNotNull(snapshot, "orderId", orderInfo.getId());
        TraceHashUtil.putIfNotNull(snapshot, "orderNo", orderInfo.getOrderNo());
        TraceHashUtil.putIfNotNull(snapshot, "buyerId", orderInfo.getBuyerId());
        TraceHashUtil.putIfNotNull(snapshot, "sellerId", orderInfo.getSellerId());
        TraceHashUtil.putIfNotNull(snapshot, "addressId", orderInfo.getAddressId());
        TraceHashUtil.putIfNotNull(snapshot, "goodsId", orderInfo.getGoodsId());
        TraceHashUtil.putIfNotNull(snapshot, "goodsTraceId", goodsTraceId);
        TraceHashUtil.putIfNotNull(snapshot, "goodsName", orderInfo.getGoodsName());
        TraceHashUtil.putIfNotNull(snapshot, "goodsPic", orderInfo.getGoodsPic());
        TraceHashUtil.putIfNotNull(snapshot, "goodsPrice", decimalToString(orderInfo.getGoodsPrice()));
        TraceHashUtil.putIfNotNull(snapshot, "goodsNum", orderInfo.getGoodsNum());
        TraceHashUtil.putIfNotNull(snapshot, "totalAmount", decimalToString(orderInfo.getTotalAmount()));
        TraceHashUtil.putIfNotNull(snapshot, "orderStatus", orderInfo.getOrderStatus());
        TraceHashUtil.putIfNotNull(snapshot, "payType", orderInfo.getPayType());
        TraceHashUtil.putIfNotNull(snapshot, "payTime", formatTime(orderInfo.getPayTime()));
        TraceHashUtil.putIfNotNull(snapshot, "deliveryTime", formatTime(orderInfo.getDeliveryTime()));
        TraceHashUtil.putIfNotNull(snapshot, "receiveTime", formatTime(orderInfo.getReceiveTime()));
        TraceHashUtil.putIfNotNull(snapshot, "cancelTime", formatTime(orderInfo.getCancelTime()));
        TraceHashUtil.putIfNotNull(snapshot, "remark", orderInfo.getRemark());
        TraceHashUtil.putIfNotNull(snapshot, "refundStatus", orderInfo.getRefundStatus());
        TraceHashUtil.putIfNotNull(snapshot, "refundAmount", decimalToString(orderInfo.getRefundAmount()));
        TraceHashUtil.putIfNotNull(snapshot, "refundReason", orderInfo.getRefundReason());
        TraceHashUtil.putIfNotNull(snapshot, "refundTime", formatTime(orderInfo.getRefundTime()));
        TraceHashUtil.putIfNotNull(snapshot, "refundRemark", orderInfo.getRefundRemark());
        TraceHashUtil.putIfNotNull(snapshot, "createTime", formatTime(orderInfo.getCreateTime()));
        TraceHashUtil.putIfNotNull(snapshot, "updateTime", formatTime(orderInfo.getUpdateTime()));
        Map<String, Object> ownershipEvidence = buildOwnershipSnapshot(orderInfo);
        if (!ownershipEvidence.isEmpty()) {
            snapshot.put("ownershipEvidence", ownershipEvidence);
        }
        return snapshot;
    }

    private static Map<String, Object> buildGoodsOrderEvidence(OrderInfo orderInfo, String orderTraceId) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (orderInfo == null) {
            return evidence;
        }
        TraceHashUtil.putIfNotNull(evidence, "orderTraceId", orderTraceId);
        TraceHashUtil.putIfNotNull(evidence, "orderId", orderInfo.getId());
        TraceHashUtil.putIfNotNull(evidence, "orderNo", orderInfo.getOrderNo());
        TraceHashUtil.putIfNotNull(evidence, "buyerId", orderInfo.getBuyerId());
        TraceHashUtil.putIfNotNull(evidence, "sellerId", orderInfo.getSellerId());
        TraceHashUtil.putIfNotNull(evidence, "goodsNum", orderInfo.getGoodsNum());
        TraceHashUtil.putIfNotNull(evidence, "goodsPrice", decimalToString(orderInfo.getGoodsPrice()));
        TraceHashUtil.putIfNotNull(evidence, "totalAmount", decimalToString(orderInfo.getTotalAmount()));
        TraceHashUtil.putIfNotNull(evidence, "orderStatus", orderInfo.getOrderStatus());
        TraceHashUtil.putIfNotNull(evidence, "tradePhase", resolveTradePhase(orderInfo.getOrderStatus()));
        TraceHashUtil.putIfNotNull(evidence, "payType", orderInfo.getPayType());
        TraceHashUtil.putIfNotNull(evidence, "payTime", formatTime(orderInfo.getPayTime()));
        TraceHashUtil.putIfNotNull(evidence, "deliveryTime", formatTime(orderInfo.getDeliveryTime()));
        TraceHashUtil.putIfNotNull(evidence, "receiveTime", formatTime(orderInfo.getReceiveTime()));
        TraceHashUtil.putIfNotNull(evidence, "cancelTime", formatTime(orderInfo.getCancelTime()));
        TraceHashUtil.putIfNotNull(evidence, "refundStatus", orderInfo.getRefundStatus());
        TraceHashUtil.putIfNotNull(evidence, "refundAmount", decimalToString(orderInfo.getRefundAmount()));
        TraceHashUtil.putIfNotNull(evidence, "refundReason", orderInfo.getRefundReason());
        TraceHashUtil.putIfNotNull(evidence, "refundTime", formatTime(orderInfo.getRefundTime()));
        TraceHashUtil.putIfNotNull(evidence, "updateTime", formatTime(orderInfo.getUpdateTime()));
        return evidence;
    }

    private static Map<String, Object> buildOwnershipSnapshot(OrderInfo orderInfo) {
        Map<String, Object> ownership = new LinkedHashMap<>();
        if (orderInfo == null) {
            return ownership;
        }
        boolean transferred = isOwnershipTransferred(orderInfo);
        TraceHashUtil.putIfNotNull(ownership, "ownershipStatus", transferred ? "TRANSFERRED" : "SELLER_HOLDING");
        TraceHashUtil.putIfNotNull(ownership, "previousOwnerId", orderInfo.getSellerId());
        TraceHashUtil.putIfNotNull(ownership, "currentOwnerId", transferred ? orderInfo.getBuyerId() : orderInfo.getSellerId());
        if (!transferred) {
            TraceHashUtil.putIfNotNull(ownership, "pendingOwnerId", orderInfo.getBuyerId());
        }
        TraceHashUtil.putIfNotNull(ownership, "transferOrderNo", orderInfo.getOrderNo());
        TraceHashUtil.putIfNotNull(ownership, "transferTime", transferred ? formatTime(orderInfo.getReceiveTime()) : null);
        return ownership;
    }

    private static List<Map<String, Object>> buildImageSnapshot(List<GoodsImage> imageList) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        if (imageList == null) {
            return snapshots;
        }
        for (GoodsImage image : imageList) {
            if (image == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            TraceHashUtil.putIfNotNull(item, "id", image.getId());
            TraceHashUtil.putIfNotNull(item, "imageUrl", image.getImageUrl());
            TraceHashUtil.putIfNotNull(item, "extInfo", image.getExtInfo());
            snapshots.add(item);
        }
        return snapshots;
    }

    private static String decimalToString(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static boolean isOwnershipTransferred(OrderInfo orderInfo) {
        return orderInfo != null && orderInfo.getOrderStatus() != null && orderInfo.getOrderStatus() == 4;
    }

    private static String resolveTradePhase(Integer orderStatus) {
        if (orderStatus == null) {
            return null;
        }
        return switch (orderStatus) {
            case 1 -> "CREATED";
            case 2 -> "PAID";
            case 3 -> "DELIVERING";
            case 4 -> "COMPLETED";
            case 5 -> "CANCELED";
            default -> "UNKNOWN";
        };
    }

    private static String formatTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }
}
