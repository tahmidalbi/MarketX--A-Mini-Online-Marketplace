package com.marketx.marketplace.repository;

import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findBySellerOrderByCreatedAtDesc(User seller);

    List<Product> findAllByOrderByCreatedAtDesc();

    List<Product> findByCategoryOrderByCreatedAtDesc(String category);

    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.category) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY p.createdAt DESC")
    List<Product> searchByQuery(@Param("q") String q);
}
