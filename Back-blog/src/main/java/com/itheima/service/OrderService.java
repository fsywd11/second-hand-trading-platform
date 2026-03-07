package com.itheima.service;

import com.itheima.DTO.OrderCreateDTO;
import com.itheima.DTO.OrderQueryDTO;
import com.itheima.DTO.RefundApplyDTO;
import com.itheima.DTO.RefundHandleDTO;
import com.itheima.pojo.PageBean;
import com.itheima.vo.OrderDetailVO;
import com.itheima.vo.OrderVO;

/**
 * 订单Service（含退款逻辑，无关联表）
 */
public interface OrderService {
    // 原有方法
    String createOrder(OrderCreateDTO orderCreateDTO);
    PageBean<OrderVO> list(OrderQueryDTO queryDTO);
    OrderDetailVO findById(Integer id);
    void updateStatus(Integer id, Integer status);
    void cancelOrder(Integer id);
    void confirmReceive(Integer id);

    // 新增：退款相关方法
    void applyRefund(RefundApplyDTO refundApplyDTO); // 申请退款
    void handleRefund(RefundHandleDTO refundHandleDTO); // 处理退款

    //删除订单
    void deleteOrder(Integer id);
}