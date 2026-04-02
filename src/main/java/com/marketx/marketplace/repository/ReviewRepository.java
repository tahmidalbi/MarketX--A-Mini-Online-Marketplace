package com.marketx.marketplace.repository;

import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.Review;
import com.marketx.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductOrderByCreatedAtDesc(Product product);

    Optional<Review> findByBuyerAndProduct(User buyer, Product product);

    @Query("SELECT AVG(r.stars) FROM Review r WHERE r.product = :product")
    Double findAverageStarsByProduct(@Param("product") Product product);

    long countByProduct(Product product);
}
