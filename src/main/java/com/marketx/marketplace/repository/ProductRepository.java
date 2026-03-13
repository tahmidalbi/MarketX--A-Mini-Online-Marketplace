package com.marketx.marketplace.repository;

import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findBySellerOrderByCreatedAtDesc(User seller);

    List<Product> findAllByOrderByCreatedAtDesc();
}
