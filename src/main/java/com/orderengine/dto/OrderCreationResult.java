
package com.orderengine.dto;

public record OrderCreationResult(
        OrderResponseDTO order,
        boolean created
) {
}
