import request from "@/utils/request.js";

/**
 * 创建订单
 * @param {Object} orderCreateDTO - 订单创建参数
 * @returns {Promise} 响应结果
 */
export const createOrderService = (orderCreateDTO) => {
    return request.post(`/order/create`, orderCreateDTO);
}

/**
 * 分页查询订单（多条件筛选）
 * @param {Object} params - 查询参数（OrderQueryDTO）
 * @returns {Promise} 分页结果
 */
export const getOrderListService = (params) => {
    return request.get(`/order/list`, { params });
}

/**
 * 查询订单详情
 * @param {Number} id - 订单ID
 * @returns {Promise} 订单详情
 */
export const getOrderDetailService = (id) => {
    return request.get(`/order/detail/${id}`);
}

/**
 * 更新订单状态
 * @param {Number} id - 订单ID
 * @param {Number} status - 订单状态（待付款/待发货/已完成等）
 * @returns {Promise} 响应结果
 */
export const updateOrderStatusService = (id, status) => {
    return request.put(`/order/status/${id}/${status}`);
}

/**
 * 取消订单
 * @param {Number} id - 订单ID
 * @returns {Promise} 响应结果
 */
export const cancelOrderService = (id) => {
    return request.put(`/order/cancel/${id}`);
}

/**
 * 确认收货
 * @param {Number} id - 订单ID
 * @returns {Promise} 响应结果
 */
export const confirmReceiveOrderService = (id) => {
    return request.put(`/order/confirm/${id}`);
}

/**
 * 申请退款
 * @param {Object} refundApplyDTO - 退款申请参数
 * @returns {Promise} 响应结果
 */
export const applyRefundService = (refundApplyDTO) => {
    return request.post(`/order/refund/apply`, refundApplyDTO);
}

/**
 * 处理退款（同意/驳回）
 * @param {Object} refundHandleDTO - 退款处理参数
 * @returns {Promise} 响应结果
 */
export const handleRefundService = (refundHandleDTO) => {
    return request.put(`/order/refund/handle`, refundHandleDTO);
}

/**
删除订单
*/
export const deleteOrderService = (id) => {
    return request.delete(`/order/delete/${id}`);
}