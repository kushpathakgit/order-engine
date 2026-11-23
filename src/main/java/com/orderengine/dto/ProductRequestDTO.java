
package com.orderengine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequestDTO(

        @NotBlank(message = "Product name is required")
        @Size(max = 150, message = "Product name cannot exceed 150 characters")
        String name,

        @NotNull(message = "Product price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal price,

        @NotNull(message = "Available stock is required")
        @Min(value = 0, message = "Stock cannot be negative")
        Integer availableStock

) {
}
