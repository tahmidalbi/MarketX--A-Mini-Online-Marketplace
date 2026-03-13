package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.dto.BuyerRegistrationDto;
import com.marketx.marketplace.dto.SellerRegistrationDto;
import com.marketx.marketplace.entity.ApprovalStatus;
import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.exception.PasswordMismatchException;
import com.marketx.marketplace.exception.UserAlreadyExistsException;
import com.marketx.marketplace.repository.UserRepository;
import com.marketx.marketplace.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void registerBuyer(BuyerRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException(
                    "An account with email '" + dto.getEmail() + "' already exists.");
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match.");
        }

        User buyer = User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(Role.BUYER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();

        userRepository.save(buyer);
    }

    @Override
    @Transactional
    public void registerSeller(SellerRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException(
                    "An account with email '" + dto.getEmail() + "' already exists.");
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match.");
        }

        User seller = User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(Role.SELLER)
                .approvalStatus(ApprovalStatus.PENDING)
                .build();

        userRepository.save(seller);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
