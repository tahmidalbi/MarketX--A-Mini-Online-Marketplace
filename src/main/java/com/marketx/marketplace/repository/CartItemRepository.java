package com.marketx.marketplace.repository;

import com.marketx.marketplace.entity.CartItem;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByBuyerOrderByAddedAtDesc(User buyer);

    Optional<CartItem> findByBuyerAndProduct(User buyer, Product product);

    @Modifying
    @Transactional
    @Query("DELETE FROM CartItem c WHERE c.buyer = :buyer")
    void deleteAllByBuyer(@Param("buyer") User buyer);

    long countByBuyer(User buyer);
}
