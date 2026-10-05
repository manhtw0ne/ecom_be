package com.manh.ecom_be.services.orderdetails;

import com.manh.ecom_be.dtos.OrderDetailDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.OrderDetail;

import java.util.List;

public interface InterfaceOrderDetailService {
    OrderDetail createOrderDetail(OrderDetailDTO newOrderDetail) throws Exception;
    OrderDetail getOrderDetail(Long id) throws DataNotFoundException;
    OrderDetail updateOrderDetail(Long id, OrderDetailDTO newOrderDetailData) throws
            DataNotFoundException;
    void deleteById(Long id);
    List<OrderDetail> findByOrderId(Long orderId) throws DataNotFoundException;
}


