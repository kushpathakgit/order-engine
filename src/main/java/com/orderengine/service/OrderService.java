package com.orderengine.service;

import com.orderengine.dto.OrderRequestDTO;
import com.orderengine.dto.OrderResponseDTO;
import com.orderengine.entity.CustomerOrder;
import com.orderengine.entity.OrderStatus;
import com.orderengine.entity.Product;
import com.orderengine.exception.InsufficientStockException;
import com.orderengine.exception.ResourceNotFoundException;
import com.orderengine.repository.OrderRepository;
import com.orderengine.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderService(
            ProductRepository productRepository,
            OrderRepository orderRepository
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponseDTO createOrder(OrderRequestDTO request, String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key header is required"
            );
        }

        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key cannot exceed 100 characters"
            );
        }

        var existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent()) {
            return OrderResponseDTO.from(existingOrder.get());
        }

        // 1. Find the product and obtain its current price.
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + request.productId()
                        )
                );

        // 2. Atomically deduct stock only if sufficient stock exists.
        int updatedRows = productRepository.deductStockIfAvailable(
                request.productId(),
                request.quantity()
        );

        // 3. If no row was updated, distinguish missing product from low stock.
        if (updatedRows == 0) {

            boolean productExists =
                    productRepository.existsById(request.productId());

            if (!productExists) {
                throw new ResourceNotFoundException(
                        "Product not found with id: " + request.productId()
                );
            }

            throw new InsufficientStockException(
                    "Insufficient stock for product: " + product.getName()
            );
        }

        // 4. Calculate the order total using the server-side product price.
        BigDecimal unitPrice = product.getPrice();

        BigDecimal totalAmount = unitPrice.multiply(
                BigDecimal.valueOf(request.quantity())
        );

        // 5. Create the order.
        CustomerOrder order = new CustomerOrder();

        order.setProduct(product);
        order.setQuantity(request.quantity());
        order.setUnitPrice(unitPrice);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.CONFIRMED);

        order.setIdempotencyKey(idempotencyKey);

        // 6. Save the order.
        CustomerOrder savedOrder = orderRepository.save(order);

        // 7. Return the response.
        return OrderResponseDTO.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(OrderResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderById(Long id) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Order not found with id: " + id
                        )
                );

        return OrderResponseDTO.from(order);
    }
}