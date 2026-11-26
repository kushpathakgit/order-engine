package com.orderengine.repository;

import com.orderengine.entity.CustomerOrder;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    @Override
    @EntityGraph(attributePaths = "product")
    List<CustomerOrder> findAll();

    @Override
    @EntityGraph(attributePaths = "product")
    Optional<CustomerOrder> findById(Long id);

}