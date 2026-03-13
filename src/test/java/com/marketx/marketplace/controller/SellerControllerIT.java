package com.marketx.marketplace.controller;

import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.repository.ProductRepository;
import com.marketx.marketplace.repository.UserRepository;
import com.marketx.marketplace.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional: MockMvc requests run in their own transaction boundary,
 * so save() and the post-request findById() each auto-commit, giving correct
 * cross-transaction visibility of the deletion.
 */
@SpringBootTest
@ActiveProfiles("test")
class SellerControllerIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * An authenticated, approved seller must be able to delete their own product.
     * Asserts HTTP 302 redirect to /seller/my-products AND that the row is gone
     * from the database after the request completes.
     *
     * Using CustomUserDetails directly as the MockMvc principal ensures the
     * currentUser(Authentication) cast inside SellerController succeeds,
     * identical to the real production code path.
     */
    @Test
    void deleteProduct_asAuthenticatedOwner_deletesFromDbAndRedirectsToMyProducts() throws Exception {
        User akash = userRepository.findByEmail("akash@gmail.com")
                .orElseThrow(() -> new IllegalStateException("akash@gmail.com not seeded"));

        Product toDelete = productRepository.save(Product.builder()
                .name("Delete-Me Integration Test Product")
                .description("Created for integration test only")
                .price(BigDecimal.ONE)
                .quantity(1)
                .category("Electronics")
                .seller(akash)
                .build());

        Long productId = toDelete.getId();

        mockMvc.perform(
                delete("/seller/products/{id}", productId)
                        .with(user(new CustomUserDetails(akash)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/my-products"));

        assertThat(productRepository.findById(productId)).isEmpty();
    }
}
