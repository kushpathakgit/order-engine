
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

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    private final ProductCacheService productCacheService;

    // Constructor Injection
    public ProductService(
            ProductRepository productRepository,
            ProductCacheService productCacheService) {

        this.productRepository = productRepository;
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
    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {

        // 1. Check Redis first
        Optional<ProductResponseDTO> cachedProduct =
                productCacheService.get(id);

        if (cachedProduct.isPresent()) {
            return cachedProduct.get();
        }

        // 2. Cache miss: fetch from PostgreSQL
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));

        // 3. Convert entity into DTO
        ProductResponseDTO response = ProductResponseDTO.from(product);

        // 4. Store response in Redis
        productCacheService.put(response);

        // 5. Return response
        return response;
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
