package com.marketx.marketplace.repository;

import com.marketx.marketplace.entity.Order;
import com.marketx.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByBuyerOrderByCreatedAtDesc(User buyer);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i WHERE i.seller = :seller ORDER BY o.createdAt DESC")
    List<Order> findOrdersBySeller(@Param("seller") User seller);

    @Query("SELECT COALESCE(SUM(i.subtotal), 0) FROM OrderItem i WHERE i.seller = :seller")
    BigDecimal sumRevenueBySeller(@Param("seller") User seller);

    @Query("SELECT i.productCategory, COALESCE(SUM(i.subtotal), 0) FROM OrderItem i WHERE i.seller = :seller GROUP BY i.productCategory")
    List<Object[]> revenueByCategory(@Param("seller") User seller);

    @Query("SELECT i.productCategory, COALESCE(SUM(i.quantity), 0) FROM OrderItem i WHERE i.seller = :seller GROUP BY i.productCategory")
    List<Object[]> unitsSoldByCategory(@Param("seller") User seller);

    @Query("SELECT i.productName, COALESCE(SUM(i.quantity), 0), COALESCE(SUM(i.subtotal), 0) FROM OrderItem i WHERE i.seller = :seller GROUP BY i.productName ORDER BY SUM(i.subtotal) DESC")
    List<Object[]> topProductsBySeller(@Param("seller") User seller);

    // Admin queries
    List<Order> findAllByOrderByCreatedAtDesc();

    @Query("SELECT u, COALESCE(SUM(i.subtotal), 0) FROM OrderItem i JOIN i.seller u GROUP BY u ORDER BY SUM(i.subtotal) DESC")
    List<Object[]> revenuePerSeller();

    @Query("SELECT i.productCategory, COALESCE(SUM(i.subtotal), 0) FROM OrderItem i GROUP BY i.productCategory ORDER BY SUM(i.subtotal) DESC")
    List<Object[]> globalRevenueByCategory();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    BigDecimal totalPlatformRevenue();
}
