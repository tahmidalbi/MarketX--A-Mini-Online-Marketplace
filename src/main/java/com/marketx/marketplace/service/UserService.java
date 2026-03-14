package com.marketx.marketplace.service;

import com.marketx.marketplace.dto.BuyerRegistrationDto;
import com.marketx.marketplace.dto.ProfileUpdateDto;
import com.marketx.marketplace.dto.SellerRegistrationDto;
import com.marketx.marketplace.entity.User;

import java.util.Optional;

public interface UserService {

    void registerBuyer(BuyerRegistrationDto dto);

    void registerSeller(SellerRegistrationDto dto);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    void updateProfile(Long userId, ProfileUpdateDto dto);
}
