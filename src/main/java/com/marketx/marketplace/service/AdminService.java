package com.marketx.marketplace.service;

import com.marketx.marketplace.entity.User;

import java.util.List;

public interface AdminService {

    List<User> getPendingSellerRegistrations();

    void approveSeller(Long userId);

    void rejectSeller(Long userId);

    List<User> getAllUsers();
}
