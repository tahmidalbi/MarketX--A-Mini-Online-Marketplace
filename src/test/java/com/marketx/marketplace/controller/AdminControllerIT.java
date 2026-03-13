package com.marketx.marketplace.controller;

import com.marketx.marketplace.entity.User;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminControllerIT {

    @Autowired
    private WebApplicationContext context;

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
     * GET /admin/users/{id} for a seller with listed products must:
     *   1. Return HTTP 200 (admin role is authorised)
     *   2. Render the seller's name in the profile card
     *   3. Show the correct product count in the section badge
     *
     * This exercises the entire stack: security, AdminController.showUserDetail(),
     * AdminServiceImpl.getProductsByUser(), and the user-detail Thymeleaf template.
     * The seller "Akash" is pre-seeded by DataSeeder with 3 Electronics products.
     */
    @Test
    void showUserDetail_asAdmin_renders200WithSellerNameAndProductCount() throws Exception {
        User admin  = userRepository.findByEmail("albitahmid@gmail.com")
                .orElseThrow(() -> new IllegalStateException("DataSeeder must seed admin"));
        User seller = userRepository.findByEmail("akash@gmail.com")
                .orElseThrow(() -> new IllegalStateException("DataSeeder must seed akash"));

        CustomUserDetails adminDetails = new CustomUserDetails(admin);

        mockMvc.perform(get("/admin/users/{id}", seller.getId())
                        .with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(seller.getName())))
                .andExpect(content().string(containsString("3 products")));
    }
}
