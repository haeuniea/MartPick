package com.martpick.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

	List<Product> findByNameContainingIgnoreCaseOrderByGoodIdAsc(String keyword);
}
