package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.dto.CheckoutDto;
import com.marketx.marketplace.entity.*;
import com.marketx.marketplace.exception.ResourceNotFoundException;
import com.marketx.marketplace.repository.CartItemRepository;
import com.marketx.marketplace.repository.OrderItemRepository;
import com.marketx.marketplace.repository.OrderRepository;
import com.marketx.marketplace.service.CartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CartService cartService;

    @InjectMocks
    private OrderServiceImpl orderService;

    // ── Helpers ─────────────────────────────────────────────────

    private User user(Long id, String email) {
        return User.builder().id(id).name("User " + id).email(email)
                .role(Role.BUYER).approvalStatus(ApprovalStatus.APPROVED).build();
    }

    private Product product(Long id, String name, BigDecimal price, int stock, User seller) {
        return Product.builder()
                .id(id).name(name).description("desc").price(price)
                .quantity(stock).category("Electronics").seller(seller).build();
    }

    private CartItem cartItem(User buyer, Product product, int qty) {
        return CartItem.builder().buyer(buyer).product(product).quantity(qty).build();
    }

    // ── checkout ─────────────────────────────────────────────────

    /**
     * A successful checkout must:
     *   1. Persist the Order with the correct totalAmount (sum of price × qty)
     *   2. Persist one OrderItem per CartItem with snapshot fields populated
     *   3. Call cartService.clearCart() exactly once to empty the cart
     *
     * The total amount calculation is critical — any rounding or accumulation
     * error here would charge the buyer the wrong amount.
     */
    @Test
    void checkout_withValidCart_createsOrderWithCorrectTotalAndClearsCart() {
        User buyer  = user(1L, "buyer@test.com");
        User seller = user(2L, "seller@test.com");

        Product p1 = product(10L, "Laptop",  new BigDecimal("800.00"), 5, seller);
        Product p2 = product(11L, "Mouse",   new BigDecimal("25.00"),  10, seller);

        List<CartItem> cart = List.of(
                cartItem(buyer, p1, 1),   // 800.00
                cartItem(buyer, p2, 2)    // 50.00
        );

        CheckoutDto dto = new CheckoutDto();
        dto.setShippingAddress("123 Main St");

        when(cartItemRepository.findByBuyerOrderByAddedAtDesc(buyer)).thenReturn(cart);

        Order savedOrder = Order.builder()
                .id(100L).buyer(buyer)
                .totalAmount(new BigDecimal("850.00"))
                .shippingAddress("123 Main St")
                .status(OrderStatus.ORDER_PLACED)
                .build();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.checkout(buyer, dto);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(orderCaptor.getValue().getShippingAddress()).isEqualTo("123 Main St");
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.ORDER_PLACED);

        // One OrderItem saved per CartItem
        verify(orderItemRepository, times(2)).save(any(OrderItem.class));

        // Cart must be cleared after order is placed
        verify(cartService, times(1)).clearCart(buyer);

        assertThat(result).isSameAs(savedOrder);
    }

    /**
     * Attempting to checkout with an empty cart must throw IllegalStateException
     * immediately. No Order, no OrderItems, and no cart-clear should occur —
     * all persistence calls must be skipped entirely.
     */
    @Test
    void checkout_whenCartIsEmpty_throwsIllegalStateException_andPersistsNothing() {
        User buyer = user(1L, "buyer@test.com");
        when(cartItemRepository.findByBuyerOrderByAddedAtDesc(buyer)).thenReturn(List.of());

        CheckoutDto dto = new CheckoutDto();
        dto.setShippingAddress("123 Main St");

        assertThatThrownBy(() -> orderService.checkout(buyer, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cart is empty");

        verify(orderRepository, never()).save(any());
        verify(orderItemRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
    }

    /**
     * checkout() must populate the OrderItem snapshot fields (productName,
     * productCategory) from the Product at time of order. This ensures order
     * history remains accurate even if the product is later deleted or renamed.
     */
    @Test
    void checkout_persistsProductNameSnapshotOnOrderItem() {
        User buyer  = user(1L, "buyer@test.com");
        User seller = user(2L, "seller@test.com");
        Product p   = product(10L, "Vintage Lamp", new BigDecimal("45.00"), 3, seller);

        when(cartItemRepository.findByBuyerOrderByAddedAtDesc(buyer))
                .thenReturn(List.of(cartItem(buyer, p, 1)));

        Order saved = Order.builder().id(1L).buyer(buyer)
                .totalAmount(new BigDecimal("45.00")).status(OrderStatus.ORDER_PLACED).build();
        when(orderRepository.save(any())).thenReturn(saved);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.checkout(buyer, new CheckoutDto());

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(captor.capture());
        OrderItem item = captor.getValue();

        assertThat(item.getProductName()).isEqualTo("Vintage Lamp");
        assertThat(item.getProductCategory()).isEqualTo("Electronics");
        assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(item.getQuantity()).isEqualTo(1);
    }

    // ── getBuyerOrder ────────────────────────────────────────────

    /**
     * A buyer requesting an order that doesn't belong to them must receive a
     * SecurityException. This is an ownership guard — the order must never be
     * returned to the wrong user even if the order ID is valid.
     */
    @Test
    void getBuyerOrder_whenCallerDoesNotOwnOrder_throwsSecurityException() {
        User owner    = user(1L, "owner@test.com");
        User attacker = user(2L, "attacker@test.com");

        Order order = Order.builder().id(50L).buyer(owner)
                .totalAmount(BigDecimal.TEN).status(OrderStatus.ORDER_PLACED).build();

        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getBuyerOrder(attacker, 50L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("do not own");
    }

    /**
     * Requesting an order with a non-existent ID must throw ResourceNotFoundException.
     */
    @Test
    void getBuyerOrder_whenOrderDoesNotExist_throwsResourceNotFoundException() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getBuyerOrder(user(1L, "b@test.com"), 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateOrderStatus ────────────────────────────────────────

    /**
     * A seller updating the status of an order that contains their items must
     * persist the new status with a single save() call and the status must change
     * to exactly what was requested.
     */
    @Test
    void updateOrderStatus_whenSellerOwnsItemsInOrder_updatesStatusAndSaves() {
        User seller = user(2L, "seller@test.com");
        User buyer  = user(1L, "buyer@test.com");

        OrderItem item = OrderItem.builder()
                .id(1L).seller(seller).quantity(1)
                .unitPrice(BigDecimal.TEN).subtotal(BigDecimal.TEN)
                .productName("Widget").productCategory("Electronics").build();

        Order order = Order.builder().id(10L).buyer(buyer)
                .totalAmount(BigDecimal.TEN).status(OrderStatus.ORDER_PLACED)
                .items(new java.util.ArrayList<>(List.of(item))).build();
        item.setOrder(order);

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.updateOrderStatus(seller, 10L, OrderStatus.CONFIRMED);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    /**
     * A seller must not be able to update the status of an order that contains
     * no items belonging to them. SecurityException must be thrown and the order
     * must not be mutated.
     */
    @Test
    void updateOrderStatus_whenSellerHasNoItemsInOrder_throwsSecurityException() {
        User actualSeller = user(2L, "real@test.com");
        User otherSeller  = user(3L, "attacker@test.com");
        User buyer        = user(1L, "buyer@test.com");

        OrderItem item = OrderItem.builder()
                .id(1L).seller(actualSeller).quantity(1)
                .unitPrice(BigDecimal.TEN).subtotal(BigDecimal.TEN)
                .productName("Widget").productCategory("Electronics").build();

        Order order = Order.builder().id(10L).buyer(buyer)
                .totalAmount(BigDecimal.TEN).status(OrderStatus.ORDER_PLACED)
                .items(new java.util.ArrayList<>(List.of(item))).build();
        item.setOrder(order);

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(otherSeller, 10L, OrderStatus.CONFIRMED))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("do not have items");

        verify(orderRepository, never()).save(any());
    }

    // ── getTotalPlatformRevenue ───────────────────────────────────

    /**
     * When the repository returns null (no orders exist yet), getTotalPlatformRevenue()
     * must return BigDecimal.ZERO rather than propagating null to callers.
     */
    @Test
    void getTotalPlatformRevenue_whenNoOrders_returnsZeroNotNull() {
        when(orderRepository.totalPlatformRevenue()).thenReturn(null);

        BigDecimal result = orderService.getTotalPlatformRevenue();

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
