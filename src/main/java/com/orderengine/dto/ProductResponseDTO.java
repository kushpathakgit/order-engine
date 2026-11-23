
package com.orderengine.dto;

import com.orderengine.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponseDTO(

        Long id,
        String name,
        BigDecimal price,
        Integer availableStock,
        LocalDateTime createdAt

) {

    public static ProductResponseDTO from(Product product) {

        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getAvailableStock(),
                product.getCreatedAt()
        );
    }
}
