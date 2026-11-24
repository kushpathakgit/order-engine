package com.orderengine.controller;

import com.orderengine.dto.OrderRequestDTO;
import com.orderengine.dto.OrderResponseDTO;
import com.orderengine.service.OrderService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(
            @Valid @RequestBody OrderRequestDTO request
    ) {
        OrderResponseDTO response = orderService.createOrder(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrderById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }
}