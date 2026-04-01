package com.marketx.marketplace.repository;

import com.marketx.marketplace.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer tests running against H2 in-memory database.
 * Each test runs inside a transaction that is rolled back on completion,
 * so DataSeeder data is never disturbed and tests are fully isolated.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    // ── Helpers ─────────────────────────────────────────────────

    private User seededBuyer() {
        return userRepository.findByEmail("akash@gmail.com")
                .orElseThrow(() -> new IllegalStateException("DataSeeder must seed akash"));
    }

    private User seededSeller() {
        return userRepository.findByEmail("anik@gmail.com")
                .orElseThrow(() -> new IllegalStateException("DataSeeder must seed anik"));
    }

    private Order saveOrder(User buyer, BigDecimal total) {
        return orderRepository.save(Order.builder()
                .buyer(buyer)
                .totalAmount(total)
                .shippingAddress("Test Street 1")
                .status(OrderStatus.ORDER_PLACED)
                .build());
    }

    private OrderItem saveOrderItem(Order order, Product product, User seller, int qty) {
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(qty));
        return orderItemRepository.save(OrderItem.builder()
                .order(order)
                .product(product)
                .seller(seller)
                .quantity(qty)
                .unitPrice(product.getPrice())
                .subtotal(subtotal)
                .productName(product.getName())
                .productCategory(product.getCategory())
                .build());
    }

    // ── findByBuyerOrderByCreatedAtDesc ──────────────────────────

    /**
     * findByBuyerOrderByCreatedAtDesc must return only orders belonging to the
     * given buyer, ordered newest-first. Orders from other buyers must not leak
     * into the result set.
     */
    @Test
    void findByBuyerOrderByCreatedAtDesc_returnsOnlyThatBuyersOrders() {
        User buyer1 = seededBuyer();
        User buyer2 = seededSeller(); // reuse seeded user as a second "buyer" for isolation

        saveOrder(buyer1, new BigDecimal("100.00"));
        saveOrder(buyer1, new BigDecimal("200.00"));
        saveOrder(buyer2, new BigDecimal("50.00")); // should NOT appear for buyer1

        List<Order> buyer1Orders = orderRepository.findByBuyerOrderByCreatedAtDesc(buyer1);

        assertThat(buyer1Orders).hasSizeGreaterThanOrEqualTo(2);
        assertThat(buyer1Orders).allMatch(o -> o.getBuyer().getId().equals(buyer1.getId()));
    }

    // ── findOrdersBySeller ───────────────────────────────────────

    /**
     * findOrdersBySeller must return orders that contain at least one item
     * belonging to the given seller. An order with no items for that seller
     * must not be included.
     */
    @Test
    void findOrdersBySeller_returnsOrdersContainingSellerItems_excludesOthers() {
        User buyer     = seededBuyer();
        User seller    = seededSeller();
        User otherUser = userRepository.findByEmail("albitahmid@gmail.com")
                .orElseThrow(() -> new IllegalStateException("admin not seeded"));

        Product sellerProduct = productRepository.findBySellerOrderByCreatedAtDesc(seller)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("seeded seller has no products"));

        Order orderWithSellerItem  = saveOrder(buyer, new BigDecimal("50.00"));
        Order orderWithoutSellerItem = saveOrder(buyer, new BigDecimal("30.00"));

        saveOrderItem(orderWithSellerItem, sellerProduct, seller, 1);
        // orderWithoutSellerItem intentionally has no items for 'seller'

        List<Order> result = orderRepository.findOrdersBySeller(seller);

        assertThat(result).anyMatch(o -> o.getId().equals(orderWithSellerItem.getId()));
        assertThat(result).noneMatch(o -> o.getId().equals(orderWithoutSellerItem.getId()));
    }

    // ── sumRevenueBySeller ───────────────────────────────────────

    /**
     * sumRevenueBySeller must sum subtotals of all OrderItems for a seller and
     * return the correct total. A seller with no items must return null (which
     * OrderServiceImpl safely maps to ZERO).
     */
    @Test
    void sumRevenueBySeller_sumsSubtotalsForThatSellerOnly() {
        User buyer  = seededBuyer();
        User seller = seededSeller();

        Product product = productRepository.findBySellerOrderByCreatedAtDesc(seller)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("seeded seller has no products"));

        Order order1 = saveOrder(buyer, new BigDecimal("80.00"));
        Order order2 = saveOrder(buyer, new BigDecimal("40.00"));

        saveOrderItem(order1, product, seller, 2); // 2 × price
        saveOrderItem(order2, product, seller, 1); // 1 × price

        BigDecimal expectedRevenue = product.getPrice().multiply(BigDecimal.valueOf(3));
        BigDecimal actualRevenue   = orderRepository.sumRevenueBySeller(seller);

        assertThat(actualRevenue).isEqualByComparingTo(expectedRevenue);
    }

    // ── totalPlatformRevenue ─────────────────────────────────────

    /**
     * totalPlatformRevenue must aggregate totalAmount across all orders.
     * After inserting two orders the platform total must grow by their combined amount.
     */
    @Test
    void totalPlatformRevenue_reflectsAllOrderTotals() {
        User buyer = seededBuyer();

        BigDecimal before = orderRepository.totalPlatformRevenue();
        BigDecimal baseline = before != null ? before : BigDecimal.ZERO;

        saveOrder(buyer, new BigDecimal("100.00"));
        saveOrder(buyer, new BigDecimal("55.50"));

        BigDecimal after = orderRepository.totalPlatformRevenue();
        assertThat(after).isEqualByComparingTo(baseline.add(new BigDecimal("155.50")));
    }

    // ── CartItemRepository ───────────────────────────────────────

    /**
     * findByBuyerAndProduct must return the exact CartItem for a buyer-product
     * pair, and return empty when the item does not exist.
     */
    @Test
    void cartItemRepository_findByBuyerAndProduct_returnsCorrectItem() {
        User buyer = seededBuyer();
        Product product = productRepository.findAllByOrderByCreatedAtDesc()
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No products seeded"));

        // Before adding — must be empty
        assertThat(cartItemRepository.findByBuyerAndProduct(buyer, product)).isEmpty();

        cartItemRepository.save(CartItem.builder()
                .buyer(buyer).product(product).quantity(2).build());

        // After adding — must be present with correct quantity
        assertThat(cartItemRepository.findByBuyerAndProduct(buyer, product))
                .isPresent()
                .hasValueSatisfying(item -> assertThat(item.getQuantity()).isEqualTo(2));
    }

    /**
     * deleteAllByBuyer must remove every CartItem for the given buyer and
     * leave items belonging to other buyers untouched.
     */
    @Test
    void cartItemRepository_deleteAllByBuyer_removesOnlyThatBuyersItems() {
        User buyer1 = seededBuyer();
        User buyer2 = seededSeller();

        List<Product> products = productRepository.findAllByOrderByCreatedAtDesc();
        assertThat(products).hasSizeGreaterThanOrEqualTo(2);

        Product p1 = products.get(0);
        Product p2 = products.get(1);

        cartItemRepository.save(CartItem.builder().buyer(buyer1).product(p1).quantity(1).build());
        cartItemRepository.save(CartItem.builder().buyer(buyer2).product(p2).quantity(1).build());

        cartItemRepository.deleteAllByBuyer(buyer1);

        assertThat(cartItemRepository.findByBuyerOrderByAddedAtDesc(buyer1)).isEmpty();
        assertThat(cartItemRepository.findByBuyerOrderByAddedAtDesc(buyer2)).hasSize(1);
    }

    /**
     * countByBuyer must return the exact number of cart items for a buyer,
     * updating correctly after additions and deletions.
     */
    @Test
    void cartItemRepository_countByBuyer_reflectsCurrentCartSize() {
        User buyer = seededBuyer();
        List<Product> products = productRepository.findAllByOrderByCreatedAtDesc();
        assertThat(products).hasSizeGreaterThanOrEqualTo(2);

        assertThat(cartItemRepository.countByBuyer(buyer)).isEqualTo(0);

        cartItemRepository.save(CartItem.builder()
                .buyer(buyer).product(products.get(0)).quantity(1).build());
        assertThat(cartItemRepository.countByBuyer(buyer)).isEqualTo(1);

        cartItemRepository.save(CartItem.builder()
                .buyer(buyer).product(products.get(1)).quantity(2).build());
        assertThat(cartItemRepository.countByBuyer(buyer)).isEqualTo(2);
    }
}
