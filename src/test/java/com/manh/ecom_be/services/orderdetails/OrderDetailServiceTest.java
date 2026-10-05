package com.manh.ecom_be.services.orderdetails;

import java.math.BigDecimal;

import com.manh.ecom_be.components.OrderAccess;
import com.manh.ecom_be.dtos.OrderDetailDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderDetailServiceTest {
    @Mock OrderRepository orders;
    @Mock OrderDetailRepository details;
    @Mock OrderAccess access;
    @InjectMocks OrderDetailService service;

    @Test void standaloneWritesCannotBypassInventoryAndTotals() {
        var dto = new OrderDetailDTO(1L, 2L, new BigDecimal("10"), 3, new BigDecimal("30"), "blue");
        assertThatThrownBy(() -> service.createOrderDetail(dto)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.updateOrderDetail(4L, dto)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteById(4L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(orders, details);
    }

    @Test void readsCheckParentOrderBeforeReturningLines() throws Exception {
        var order = new Order();
        var line = OrderDetail.builder().id(4L).order(order).build();
        when(details.findById(4L)).thenReturn(Optional.of(line));
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        when(details.findByOrderId(1L)).thenReturn(List.of(line));
        assertThat(service.getOrderDetail(4L)).isSameAs(line);
        assertThat(service.findByOrderId(1L)).containsExactly(line);
        verify(access, times(2)).requireOrder(order);
        assertThatThrownBy(() -> service.getOrderDetail(99L)).isInstanceOf(DataNotFoundException.class);
        assertThatThrownBy(() -> service.findByOrderId(99L)).isInstanceOf(DataNotFoundException.class);
    }

    @Test void deniedParentDoesNotQueryItsLines() {
        var order = new Order();
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        doThrow(new AccessDeniedException("Forbidden")).when(access).requireOrder(order);
        assertThatThrownBy(() -> service.findByOrderId(1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(details);
    }
}