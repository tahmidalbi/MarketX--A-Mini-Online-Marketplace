package com.marketx.marketplace.service;

import java.util.List;

import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;

public interface AdminService {

    List<User> getPendingSellerRegistrations();
    void approveSeller(Long userId);
    void rejectSeller(Long userId);

    List<User> getAllUsers();
    User getUserById(Long id);

    List<Product> getProductsByUser(Long userId);
    void disableUser(Long id);
    void enableUser(Long id);

    List<Product> getAllProducts();
    void removeProduct(Long id);

    long getPendingSellerCount();
}
