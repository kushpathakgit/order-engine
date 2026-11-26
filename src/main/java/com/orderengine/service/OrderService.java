
package com.orderengine.service;

import com.orderengine.dto.OrderCreationResult;
import com.orderengine.dto.OrderRequestDTO;
import com.orderengine.dto.OrderResponseDTO;
import com.orderengine.entity.CustomerOrder;
import com.orderengine.entity.IdempotencyRecord;
import com.orderengine.entity.OrderStatus;
import com.orderengine.entity.Product;
import com.orderengine.exception.IdempotencyKeyReuseException;
import com.orderengine.exception.InsufficientStockException;
import com.orderengine.exception.InvalidIdempotencyKeyException;
import com.orderengine.exception.ResourceNotFoundException;
import com.orderengine.repository.IdempotencyRepository;
import com.orderengine.repository.OrderRepository;
import com.orderengine.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;

    public OrderService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            IdempotencyRepository idempotencyRepository) {

        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    public OrderCreationResult createOrder(
            OrderRequestDTO request,
            String idempotencyKey) {

        // 1. Validate the idempotency key
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key header is required"
            );
        }

        if (idempotencyKey.length() > 100) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key cannot exceed 100 characters"
            );
        }

        // 2. Generate a fingerprint for this request
        String requestHash = generateRequestHash(request);

        // 3. Try to reserve the key in PostgreSQL
        int inserted = idempotencyRepository.tryReserveKey(
                idempotencyKey,
                requestHash
        );

        // 4. If the key already exists, handle the retry
        if (inserted == 0) {

            IdempotencyRecord existingRecord =
                    idempotencyRepository.findById(idempotencyKey)
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Idempotency record not found after conflict"
                                    )
                            );

            // Reject reuse of the same key for a different payload
            if (!existingRecord.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReuseException(
                        "This Idempotency-Key was already used with a different request"
                );
            }

            if (existingRecord.getOrderId() == null) {
                throw new IllegalStateException(
                        "Completed idempotency record has no order ID"
                );
            }

            CustomerOrder existingOrder =
                    orderRepository.findById(existingRecord.getOrderId())
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Order linked to idempotency key was not found"
                                    )
                            );

            return new OrderCreationResult(
                    OrderResponseDTO.from(existingOrder),
                    false
            );
        }

        // 5. This is a new request. Check the product.
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + request.productId()
                ));

        // 6. Deduct stock atomically
        int updatedRows = productRepository.deductStockIfAvailable(
                request.productId(),
                request.quantity()
        );

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

        // 7. Calculate order amount
        BigDecimal unitPrice = product.getPrice();

        BigDecimal totalAmount = unitPrice.multiply(
                BigDecimal.valueOf(request.quantity())
        );

        // 8. Create the order
        CustomerOrder order = new CustomerOrder();

        order.setProduct(product);
        order.setQuantity(request.quantity());
        order.setUnitPrice(unitPrice);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.CONFIRMED);

        CustomerOrder savedOrder = orderRepository.save(order);

        // 9. Link the reserved key to the newly created order
        IdempotencyRecord record =
                idempotencyRepository.findById(idempotencyKey)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Reserved idempotency key not found"
                                )
                        );

        record.setOrderId(savedOrder.getId());

        idempotencyRepository.save(record);

        // 10. Return the newly created order
        return new OrderCreationResult(
                OrderResponseDTO.from(savedOrder),
                true
        );
    }

    private String generateRequestHash(OrderRequestDTO request) {

        String payload = request.productId() + ":" + request.quantity();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    ex
            );
        }
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + id
                ));

        return OrderResponseDTO.from(order);
    }
}
