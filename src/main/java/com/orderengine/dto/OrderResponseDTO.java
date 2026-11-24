package com.orderengine.dto;

import com.orderengine.entity.CustomerOrder;
import com.orderengine.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponseDTO(
        Long orderId,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static OrderResponseDTO from(CustomerOrder order) {
        return new OrderResponseDTO(
                order.getId(),
                order.getProduct().getId(),
                order.getProduct().getName(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}