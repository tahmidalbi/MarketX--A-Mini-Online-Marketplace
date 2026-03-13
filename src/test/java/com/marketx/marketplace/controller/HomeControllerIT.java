package com.marketx.marketplace.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class HomeControllerIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * GET /?category=Books must return 200, render both seeded Books products
     * ("Clean Code", "The Pragmatic Programmer"), and contain zero mention of
     * the seeded Electronics product ("Wireless Noise-Cancelling Headphones").
     * DataSeeder seeds these products so no test setup is required.
     * A failure means the category filter is broken in the service, repo query,
     * or Thymeleaf rendering pipeline.
     */
    @Test
    void categoryFilter_forBooks_rendersOnlyBooksProducts_andExcludesElectronics() throws Exception {
        mockMvc.perform(get("/").param("category", "Books"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Clean Code")))
                .andExpect(content().string(containsString("Pragmatic Programmer")))
                .andExpect(content().string(not(containsString("Wireless Noise-Cancelling"))));
    }
}
