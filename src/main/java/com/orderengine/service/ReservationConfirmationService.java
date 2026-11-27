package com.orderengine.service;

import com.orderengine.dto.OrderResponseDTO;
import com.orderengine.entity.CustomerOrder;
import com.orderengine.entity.InventoryReservation;
import com.orderengine.entity.OrderStatus;
import com.orderengine.entity.Product;
import com.orderengine.entity.ReservationStatus;
import com.orderengine.exception.InsufficientStockException;
import com.orderengine.exception.InvalidReservationStateException;
import com.orderengine.exception.ResourceNotFoundException;
import com.orderengine.repository.OrderRepository;
import com.orderengine.repository.ProductRepository;
import com.orderengine.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationConfirmationService {

    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public ReservationConfirmationService(
            ReservationRepository reservationRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository) {
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponseDTO confirmReservation(String reservationId) {

        // 1. Lock the reservation row.
        InventoryReservation reservation =
                reservationRepository.findByIdForUpdate(reservationId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Reservation not found: " + reservationId));

        // 2. Handle an already-confirmed retry.
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            if (reservation.getOrderId() == null) {
                throw new IllegalStateException(
                        "Confirmed reservation has no order ID");
            }

            return orderRepository.findById(reservation.getOrderId())
                    .map(OrderResponseDTO::from)
                    .orElseThrow(() -> new IllegalStateException(
                            "Order linked to reservation was not found"));
        }

        // 3. Only RESERVED reservations can be confirmed.
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new InvalidReservationStateException(
                    "Cannot confirm reservation in status: "
                            + reservation.getStatus());
        }
        if (!reservation.getExpiresAt().isAfter(java.time.Instant.now())) {
            throw new InvalidReservationStateException(
                    "Reservation has expired and cannot be confirmed");
        }

        // 4. Lock the product row.
        Product product = productRepository
                .findByIdForUpdate(reservation.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found: " + reservation.getProductId()));

        // 5. Permanently deduct PostgreSQL inventory.
        int quantity = reservation.getQuantity();

        if (product.getAvailableStock() < quantity) {
            throw new InsufficientStockException(
                    "Insufficient database inventory for product: "
                            + product.getName());
        }

        product.setAvailableStock(
                product.getAvailableStock() - quantity);

        // 6. Create the order using the current product price.
        CustomerOrder order = new CustomerOrder();
        order.setProduct(product);
        order.setQuantity(quantity);
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount(
                product.getPrice()
                        .multiply(java.math.BigDecimal.valueOf(quantity)));
        order.setStatus(OrderStatus.CONFIRMED);

        CustomerOrder savedOrder = orderRepository.save(order);

        // 7. Link the reservation to the new order.
        reservation.confirm(savedOrder.getId());

        // 8. Return the order response.
        return OrderResponseDTO.from(savedOrder);
    }
}