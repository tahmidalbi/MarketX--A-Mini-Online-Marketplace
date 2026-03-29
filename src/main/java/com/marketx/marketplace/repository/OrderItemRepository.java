package com.marketx.marketplace.repository;

import com.marketx.marketplace.entity.OrderItem;
import com.marketx.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findBySellerOrderByIdDesc(User seller);

    @Query("SELECT i FROM OrderItem i WHERE i.seller = :seller AND i.order.id = :orderId")
    List<OrderItem> findBySellerAndOrderId(@Param("seller") User seller, @Param("orderId") Long orderId);
}
