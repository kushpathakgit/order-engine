package com.orderengine;

import com.orderengine.dto.OrderRequestDTO;
import com.orderengine.entity.Product;
import com.orderengine.exception.InsufficientStockException;
import java.util.UUID;
import com.orderengine.repository.OrderRepository;
import com.orderengine.repository.ProductRepository;
import com.orderengine.service.OrderService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Long productId;

    @BeforeEach
    void setUp() {

        // Remove previous test data.
        orderRepository.deleteAll();
        productRepository.deleteAll();

        // Create a product with only 5 items.
        Product product = new Product();

        product.setName("Concurrency Test Product");
        product.setPrice(new BigDecimal("1000.00"));
        product.setAvailableStock(5);

        Product savedProduct = productRepository.save(product);

        productId = savedProduct.getId();
    }

    @Test
    void shouldNotOversellWhenMultipleRequestsArriveTogether()
            throws Exception {

        int totalRequests = 20;

        ExecutorService executor =
                Executors.newFixedThreadPool(totalRequests);

        CountDownLatch ready =
                new CountDownLatch(totalRequests);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        try {

            // Submit all order requests.
            for (int i = 0; i < totalRequests; i++) {

                Future<Boolean> future = executor.submit(() -> {

                    ready.countDown();

                    // Wait until all threads are ready.
                    start.await();

                    try {

                        OrderRequestDTO request =
                                new OrderRequestDTO(productId, 1);

                        orderService.createOrder(
                                request,
                                UUID.randomUUID().toString()
                        );

                        return true;

                    } catch (InsufficientStockException ex) {

                        return false;
                    }
                });

                futures.add(future);
            }

            // Wait until all worker threads are ready.
            assertTrue(ready.await(10, TimeUnit.SECONDS));

            // Release all worker threads together.
            start.countDown();

            int successfulOrders = 0;
            int rejectedOrders = 0;

            for (Future<Boolean> future : futures) {

                boolean success = future.get(30, TimeUnit.SECONDS);

                if (success) {
                    successfulOrders++;
                } else {
                    rejectedOrders++;
                }
            }

            // Verify the results.
            assertEquals(5, successfulOrders);
            assertEquals(15, rejectedOrders);

            Product updatedProduct =
                    productRepository.findById(productId).orElseThrow();

            assertEquals(0, updatedProduct.getAvailableStock());

            assertEquals(5, orderRepository.count());

        } finally {

            executor.shutdownNow();
        }
    }
}