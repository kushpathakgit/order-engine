
package com.orderengine.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.orderengine.dto.ProductRequestDTO;
import com.orderengine.dto.ProductResponseDTO;
import com.orderengine.entity.Product;
import com.orderengine.exception.ResourceNotFoundException;
import com.orderengine.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    private final RedisLockService redisLockService;

    private final ProductCacheService productCacheService;

    // Constructor Injection
    public ProductService(
            ProductRepository productRepository, RedisLockService redisLockService,
            ProductCacheService productCacheService) {

        this.productRepository = productRepository;
        this.redisLockService = redisLockService;
        this.productCacheService = productCacheService;
    }
    // Create Product
    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO request) {

        Product product = new Product();

        product.setName(request.name());
        product.setPrice(request.price());
        product.setAvailableStock(request.availableStock());

        Product savedProduct = productRepository.save(product);

        return ProductResponseDTO.from(savedProduct);
    }

    // Get All Products
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getAllProducts() {

        return productRepository.findAll()
                .stream()
                .map(ProductResponseDTO::from)
                .toList();
    }

    // Get Product By ID

    public ProductResponseDTO getProductById(Long id) {

        // Step 1: Initial cache check
        Optional<ProductResponseDTO> cachedProduct =
                productCacheService.get(id);

        if (cachedProduct.isPresent()) {
            return cachedProduct.get();
        }

        String lockKey = "order-engine:lock:product:" + id;

        // Step 2: Attempt to acquire the distributed lock
        String lockToken = redisLockService.tryAcquire(
                lockKey,
                Duration.ofSeconds(10)
        );

        // Step 3: We acquired the lock
        if (lockToken != null) {

            try {
                // Another request might have populated the cache
                // before we acquired the lock.
                cachedProduct = productCacheService.get(id);

                if (cachedProduct.isPresent()) {
                    return cachedProduct.get();
                }

                // Cache is still empty. Fetch from PostgreSQL.
                Product product = productRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product not found with id: " + id));

                ProductResponseDTO response =
                        ProductResponseDTO.from(product);

                // Populate Redis
                productCacheService.put(response);

                return response;

            } finally {
                // Release only if this request still owns the lock.
                redisLockService.release(lockKey, lockToken);
            }
        }

        // Step 4: Another request owns the lock.
        // Wait briefly and repeatedly check Redis.
        for (int attempt = 0; attempt < 10; attempt++) {

            try {
                Thread.sleep(50);

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }

            cachedProduct = productCacheService.get(id);

            if (cachedProduct.isPresent()) {
                return cachedProduct.get();
            }
        }

        // Step 5: Lock unavailable or wait limit reached.
        // Fall back to PostgreSQL rather than blocking indefinitely.
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));

        return ProductResponseDTO.from(product);
    }


    // Update Product
    @Transactional
    public ProductResponseDTO updateProduct(
            Long id,
            ProductRequestDTO request
    ) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        product.setName(request.name());
        product.setPrice(request.price());
        product.setAvailableStock(request.availableStock());

        Product updatedProduct = productRepository.save(product);

        productCacheService.evictAfterCommit(id);

        return ProductResponseDTO.from(updatedProduct);
    }

    // Delete Product
    @Transactional
    public void deleteProduct(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        productCacheService.evictAfterCommit(id);

        productRepository.delete(product);
    }
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProducts(int page, int size) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("id").ascending()
        );

        return productRepository.findAll(pageable)
                .map(ProductResponseDTO::from);
    }
}
