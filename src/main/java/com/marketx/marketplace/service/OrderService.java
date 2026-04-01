package com.marketx.marketplace.service;

import com.marketx.marketplace.dto.CheckoutDto;
import com.marketx.marketplace.entity.Order;
import com.marketx.marketplace.entity.OrderStatus;
import com.marketx.marketplace.entity.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface OrderService {
    // Buyer
    Order checkout(User buyer, CheckoutDto dto);
    List<Order> getBuyerOrders(User buyer);
    Order getBuyerOrder(User buyer, Long orderId);

    // Seller
    List<Order> getSellerOrders(User seller);
    Order getSellerOrder(User seller, Long orderId);
    void updateOrderStatus(User seller, Long orderId, OrderStatus newStatus);

    // Seller stats
    BigDecimal getSellerTotalRevenue(User seller);
    Map<String, BigDecimal> getSellerRevenueByCategory(User seller);
    Map<String, Long> getSellerUnitsByCategory(User seller);
    List<Object[]> getSellerTopProducts(User seller);

    // Admin
    List<Order> getAllOrders();
    Order getOrderById(Long orderId);
    List<Object[]> getRevenuePerSeller();
    Map<String, BigDecimal> getGlobalRevenueByCategory();
    BigDecimal getTotalPlatformRevenue();
}
