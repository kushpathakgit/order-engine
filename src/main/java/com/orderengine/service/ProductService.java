
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

@Service
public class ProductService {

    private final ProductRepository productRepository;

    // Constructor Injection
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
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

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id
                        )
                );

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
