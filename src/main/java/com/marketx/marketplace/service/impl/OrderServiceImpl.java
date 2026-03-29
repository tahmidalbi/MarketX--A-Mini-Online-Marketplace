package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.dto.CheckoutDto;
import com.marketx.marketplace.entity.*;
import com.marketx.marketplace.exception.ResourceNotFoundException;
import com.marketx.marketplace.repository.CartItemRepository;
import com.marketx.marketplace.repository.OrderItemRepository;
import com.marketx.marketplace.repository.OrderRepository;
import com.marketx.marketplace.service.CartService;
import com.marketx.marketplace.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final CartService cartService;

    /* ── Buyer ─────────────────────────────────────────────────── */

    @Override
    @Transactional
    public Order checkout(User buyer, CheckoutDto dto) {
        List<CartItem> cartItems = cartItemRepository.findByBuyerOrderByAddedAtDesc(buyer);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        BigDecimal total = cartItems.stream()
                .map(c -> c.getProduct().getPrice().multiply(BigDecimal.valueOf(c.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .buyer(buyer)
                .totalAmount(total)
                .shippingAddress(dto.getShippingAddress())
                .status(OrderStatus.ORDER_PLACED)
                .build();

        order = orderRepository.save(order);

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .seller(product.getSeller())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .productName(product.getName())
                    .productCategory(product.getCategory())
                    .build();

            orderItemRepository.save(item);

            // Decrement stock
            int newStock = product.getQuantity() - cartItem.getQuantity();
            product.setQuantity(Math.max(0, newStock));
        }

        cartService.clearCart(buyer);
        return order;
    }

    @Override
    public List<Order> getBuyerOrders(User buyer) {
        return orderRepository.findByBuyerOrderByCreatedAtDesc(buyer);
    }

    @Override
    public Order getBuyerOrder(User buyer, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new SecurityException("You do not own this order");
        }
        return order;
    }

    /* ── Seller ─────────────────────────────────────────────────── */

    @Override
    public List<Order> getSellerOrders(User seller) {
        return orderRepository.findOrdersBySeller(seller);
    }

    @Override
    public Order getSellerOrder(User seller, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        boolean hasItems = order.getItems().stream()
                .anyMatch(i -> i.getSeller().getId().equals(seller.getId()));
        if (!hasItems) {
            throw new SecurityException("You do not have items in this order");
        }
        return order;
    }

    @Override
    @Transactional
    public void updateOrderStatus(User seller, Long orderId, OrderStatus newStatus) {
        Order order = getSellerOrder(seller, orderId);
        order.setStatus(newStatus);
        orderRepository.save(order);
    }

    /* ── Seller Stats ───────────────────────────────────────────── */

    @Override
    public BigDecimal getSellerTotalRevenue(User seller) {
        BigDecimal rev = orderRepository.sumRevenueBySeller(seller);
        return rev != null ? rev : BigDecimal.ZERO;
    }

    @Override
    public Map<String, BigDecimal> getSellerRevenueByCategory(User seller) {
        List<Object[]> rows = orderRepository.revenueByCategory(seller);
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    @Override
    public Map<String, Long> getSellerUnitsByCategory(User seller) {
        List<Object[]> rows = orderRepository.unitsSoldByCategory(seller);
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    @Override
    public List<Object[]> getSellerTopProducts(User seller) {
        return orderRepository.topProductsBySeller(seller);
    }

    /* ── Admin ──────────────────────────────────────────────────── */

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    @Override
    public List<Object[]> getRevenuePerSeller() {
        return orderRepository.revenuePerSeller();
    }

    @Override
    public Map<String, BigDecimal> getGlobalRevenueByCategory() {
        List<Object[]> rows = orderRepository.globalRevenueByCategory();
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    @Override
    public BigDecimal getTotalPlatformRevenue() {
        BigDecimal rev = orderRepository.totalPlatformRevenue();
        return rev != null ? rev : BigDecimal.ZERO;
    }
}
