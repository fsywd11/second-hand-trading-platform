package org.example.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.order.POJO.OrderInfo;

@Mapper
public interface OrderTraceMapper {

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   buyer_id AS buyerId,
                   seller_id AS sellerId,
                   address_id AS addressId,
                   goods_id AS goodsId,
                   goods_name AS goodsName,
                   goods_pic AS goodsPic,
                   goods_price AS goodsPrice,
                   goods_num AS goodsNum,
                   total_amount AS totalAmount,
                   order_status AS orderStatus,
                   pay_type AS payType,
                   pay_time AS payTime,
                   delivery_time AS deliveryTime,
                   receive_time AS receiveTime,
                   cancel_time AS cancelTime,
                   remark,
                   refund_status AS refundStatus,
                   refund_amount AS refundAmount,
                   refund_reason AS refundReason,
                   refund_time AS refundTime,
                   refund_remark AS refundRemark,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM order_info
            WHERE goods_id = #{goodsId}
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """)
    OrderInfo findLatestByGoodsId(Integer goodsId);
}
