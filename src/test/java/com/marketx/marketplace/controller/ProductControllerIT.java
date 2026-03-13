package com.marketx.marketplace.controller;

import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class ProductControllerIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * GET /products/{id} for a product that exists must return HTTP 200 and
     * render the product name + seller name in the HTML.
     * The seller assertion validates that Open Session in View is active during
     * Thymeleaf rendering (seller is LAZY on the Product entity). We extract the
     * expected values inside a TransactionTemplate so that the lazy proxy is
     * resolved before the repository session closes.
     */
    @Test
    void productDetail_forExistingProduct_returns200AndRendersNameAndSeller() throws Exception {
        long[]   productId   = {0};
        String[] productName = {""};
        String[] sellerName  = {""};

        transactionTemplate.executeWithoutResult(status -> {
            Product product = productRepository.findAllByOrderByCreatedAtDesc()
                    .stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No products in test DB"));
            productId[0]   = product.getId();
            productName[0] = product.getName();
            sellerName[0]  = product.getSeller().getName();
        });

        mockMvc.perform(get("/products/{id}", productId[0]))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(productName[0])))
                .andExpect(content().string(containsString(sellerName[0])));
    }
}
