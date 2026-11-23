
package com.orderengine.controller;

import com.orderengine.dto.ProductRequestDTO;
import com.orderengine.dto.ProductResponseDTO;
import com.orderengine.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    // Constructor Injection
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Create Product
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestBody ProductRequestDTO request
    ) {

        ProductResponseDTO response =
                productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Get All Products
    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getAllProducts() {

        return ResponseEntity.ok(
                productService.getAllProducts()
        );
    }

    // Get Product By ID
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                productService.getProductById(id)
        );
    }

    // Update Product
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequestDTO request
    ) {

        return ResponseEntity.ok(
                productService.updateProduct(id, request)
        );
    }

    // Delete Product
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id
    ) {

        productService.deleteProduct(id);

        return ResponseEntity.noContent().build();
    }
}
