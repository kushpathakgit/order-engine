package com.orderengine.repository;

import com.orderengine.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products
            SET available_stock = available_stock - :quantity
            WHERE id = :productId
            AND available_stock >= :quantity
            """, nativeQuery = true)
    int deductStockIfAvailable(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity
    );

    Page<Product> findAll(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Product p
        WHERE p.id = :productId
        """)
    Optional<Product> findByIdForUpdate(
            @Param("productId") Long productId
    );
}