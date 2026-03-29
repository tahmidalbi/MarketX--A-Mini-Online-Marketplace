package com.marketx.marketplace.service;

import com.marketx.marketplace.entity.CartItem;
import com.marketx.marketplace.entity.User;

import java.util.List;

public interface CartService {
    void addToCart(User buyer, Long productId, int quantity);
    void updateQuantity(User buyer, Long cartItemId, int quantity);
    void removeFromCart(User buyer, Long cartItemId);
    List<CartItem> getCartItems(User buyer);
    long getCartCount(User buyer);
    void clearCart(User buyer);
}
