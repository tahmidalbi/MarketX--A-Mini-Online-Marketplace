package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.dto.BuyerRegistrationDto;
import com.marketx.marketplace.exception.UserAlreadyExistsException;
import com.marketx.marketplace.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void registerBuyer_throwsUserAlreadyExistsException_whenEmailAlreadyTaken() {
        BuyerRegistrationDto dto = new BuyerRegistrationDto();
        dto.setName("Alice");
        dto.setEmail("alice@test.com");
        dto.setPassword("pass123");
        dto.setConfirmPassword("pass123");

        when(userRepository.existsByEmail("alice@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerBuyer(dto))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("alice@test.com");
    }
}
