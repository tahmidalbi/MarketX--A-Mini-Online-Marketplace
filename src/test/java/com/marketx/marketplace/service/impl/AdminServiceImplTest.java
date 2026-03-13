package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.entity.ApprovalStatus;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.repository.ProductRepository;
import com.marketx.marketplace.repository.UserRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private AdminServiceImpl adminService;

    /**
     * disableUser() must flip the user's approvalStatus to REJECTED — the mechanism
     * that blocks login — and persist it with a single save() call. No other field
     * (role, email, password, etc.) should be touched.
     */
    @Test
    void disableUser_setsApprovalStatusToRejectedAndSavesExactlyOnce() {
        User user = User.builder()
                .name("Alice Buyer")
                .email("alice@test.com")
                .password("hashed")
                .role(Role.BUYER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminService.disableUser(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        // Role and email must not have been mutated by the disable operation
        assertThat(saved.getRole()).isEqualTo(Role.BUYER);
        assertThat(saved.getEmail()).isEqualTo("alice@test.com");
    }

    /**
     * getProductsByUser() must resolve the User entity first (by id), then pass
     * that exact User object to productRepository — not just the id. This validates
     * the correct join query path is taken and the result list is returned as-is.
     */
    @Test
    void getProductsByUser_resolvesUserThenPassesEntityToProductRepository() {
        User seller = User.builder()
                .name("Bob Seller")
                .email("bob@test.com")
                .password("hashed")
                .role(Role.SELLER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();

        List<Product> expected = List.of(
                Product.builder().name("Widget A").price(new BigDecimal("9.99"))
                        .quantity(10).category("Electronics").seller(seller).build(),
                Product.builder().name("Widget B").price(new BigDecimal("14.99"))
                        .quantity(5).category("Electronics").seller(seller).build()
        );

        when(userRepository.findById(42L)).thenReturn(Optional.of(seller));
        when(productRepository.findBySellerOrderByCreatedAtDesc(seller)).thenReturn(expected);

        List<Product> result = adminService.getProductsByUser(42L);

        assertThat(result).isSameAs(expected);
        // Must query by the exact User object fetched, not any other user
        verify(productRepository).findBySellerOrderByCreatedAtDesc(seller);
        // Must never fall back to fetching all products
        verify(productRepository, never()).findAllByOrderByCreatedAtDesc();
    }
}
