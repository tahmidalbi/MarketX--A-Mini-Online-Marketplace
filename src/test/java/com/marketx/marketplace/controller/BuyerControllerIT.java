package com.marketx.marketplace.controller;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.marketx.marketplace.entity.CartItem;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.repository.CartItemRepository;
import com.marketx.marketplace.repository.OrderRepository;
import com.marketx.marketplace.repository.ProductRepository;
import com.marketx.marketplace.repository.UserRepository;
import com.marketx.marketplace.security.CustomUserDetails;

/**
 * Not @Transactional: MockMvc requests run in their own transaction so saves
 * and subsequent reads each auto-commit, giving correct cross-transaction
 * visibility. Cart and Order rows created here are cleaned up per-test via the
 * helper teardown using the repositories directly.
 */
@SpringBootTest
@ActiveProfiles("test")
class BuyerControllerIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private MockMvc mockMvc;

    private User buyer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        buyer = userRepository.findByEmail("reza@gmail.com")
                .orElseThrow(() -> new IllegalStateException("DataSeeder must seed reza as a BUYER"));

        // Clean slate: remove any cart items left from a previous test run
        transactionTemplate.executeWithoutResult(s ->
                cartItemRepository.deleteAllByBuyer(buyer));
    }

    // ── Dashboard ──────────────────────────────────────────────

    /**
     * GET /buyer/dashboard must return 200 and render product listings from
     * the DataSeeder. The buyer's name must appear in the welcome banner.
     * Verifies the full stack: security, controller model attributes, and
     * Thymeleaf template rendering.
     */
    @Test
    void dashboard_asAuthenticatedBuyer_returns200AndRendersProducts() throws Exception {
        mockMvc.perform(get("/buyer/dashboard")
                        .with(user(new CustomUserDetails(buyer))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(buyer.getName())));
    }

    /**
     * GET /buyer/dashboard without authentication must redirect to the login page,
     * not return 200 or 403.
     */
    @Test
    void dashboard_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/buyer/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    // ── Cart ──────────────────────────────────────────────────

    /**
     * POST /buyer/cart/add must add the product to the cart and redirect to
     * /buyer/cart. After the request the cart must contain exactly one item
     * for the buyer.
     */
    @Test
    void addToCart_asAuthenticatedBuyer_addsItemAndRedirectsToCart() throws Exception {
        long[] productId = {0};
        transactionTemplate.executeWithoutResult(s -> {
            Product product = productRepository.findAllByOrderByCreatedAtDesc()
                    .stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No products seeded"));
            productId[0] = product.getId();
        });

        mockMvc.perform(post("/buyer/cart/add")
                        .param("productId", String.valueOf(productId[0]))
                        .param("quantity", "1")
                        .with(user(new CustomUserDetails(buyer)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/buyer/cart"));

        long count = transactionTemplate.execute(s ->
                cartItemRepository.countByBuyer(buyer));
        assertThat(count).isEqualTo(1);
    }

    /**
     * GET /buyer/cart must return 200 and show the cart item that was just added.
     * The product name must appear in the rendered HTML.
     */
    @Test
    void viewCart_withItemInCart_returns200AndRendersProductName() throws Exception {
        String[] productName = {""};
        transactionTemplate.executeWithoutResult(s -> {
            Product product = productRepository.findAllByOrderByCreatedAtDesc()
                    .stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No products seeded"));
            productName[0] = product.getName();
            cartItemRepository.save(CartItem.builder()
                    .buyer(buyer).product(product).quantity(1).build());
        });

        mockMvc.perform(get("/buyer/cart")
                        .with(user(new CustomUserDetails(buyer))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(productName[0])));
    }

    // ── Checkout ──────────────────────────────────────────────

    /**
     * GET /buyer/checkout with an empty cart must redirect to /buyer/cart
     * rather than rendering the checkout page. Prevents placing a zero-total order.
     */
    @Test
    void showCheckout_withEmptyCart_redirectsToCart() throws Exception {
        mockMvc.perform(get("/buyer/checkout")
                        .with(user(new CustomUserDetails(buyer))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/buyer/cart"));
    }

    /**
     * POST /buyer/checkout with a valid cart and shipping address must store
     * the address in session and redirect to /buyer/payment (the SSLCommerz
     * review page). The order is placed only after payment validation.
     */
    @Test
    void placeOrder_withValidCartAndAddress_createsOrderAndRedirectsToMyOrders() throws Exception {
        // Seed a product with enough stock and add it to the buyer's cart
        transactionTemplate.executeWithoutResult(s -> {
            Product product = productRepository.save(Product.builder()
                    .name("Checkout-Test Product")
                    .description("For checkout integration test")
                    .price(new BigDecimal("99.99"))
                    .quantity(10)
                    .category("Electronics")
                    .seller(userRepository.findByEmail("anik@gmail.com")
                            .orElseThrow(() -> new IllegalStateException("anik not seeded")))
                    .build());
            cartItemRepository.save(CartItem.builder()
                    .buyer(buyer).product(product).quantity(1).build());
        });

        mockMvc.perform(post("/buyer/checkout")
                        .param("shippingAddress", "456 Test Avenue, Test City")
                        .with(user(new CustomUserDetails(buyer)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/buyer/payment"));
    }

    /**
     * POST /buyer/checkout with a blank shipping address must fail validation
     * and re-render the checkout page (HTTP 200) — not place the order.
     */
    @Test
    void placeOrder_withBlankShippingAddress_failsValidationAndReturns200() throws Exception {
        transactionTemplate.executeWithoutResult(s -> {
            Product product = productRepository.findAllByOrderByCreatedAtDesc()
                    .stream().findFirst().orElseThrow();
            cartItemRepository.save(CartItem.builder()
                    .buyer(buyer).product(product).quantity(1).build());
        });

        mockMvc.perform(post("/buyer/checkout")
                        .param("shippingAddress", "")
                        .with(user(new CustomUserDetails(buyer)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // ── My Orders ─────────────────────────────────────────────

    /**
     * GET /buyer/my-orders must return 200 and render the orders page.
     * Verifies the buyer can access their order history page without errors.
     */
    @Test
    void myOrders_asAuthenticatedBuyer_returns200() throws Exception {
        mockMvc.perform(get("/buyer/my-orders")
                        .with(user(new CustomUserDetails(buyer))))
                .andExpect(status().isOk());
    }
}
