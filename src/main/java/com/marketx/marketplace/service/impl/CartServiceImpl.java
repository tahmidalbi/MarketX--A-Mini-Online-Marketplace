package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.entity.CartItem;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.exception.ResourceNotFoundException;
import com.marketx.marketplace.repository.CartItemRepository;
import com.marketx.marketplace.repository.ProductRepository;
import com.marketx.marketplace.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void addToCart(User buyer, Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (product.getQuantity() < quantity) {
            throw new IllegalArgumentException("Not enough stock. Available: " + product.getQuantity());
        }

        Optional<CartItem> existing = cartItemRepository.findByBuyerAndProduct(buyer, product);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + quantity;
            if (product.getQuantity() < newQty) {
                throw new IllegalArgumentException("Not enough stock. Available: " + product.getQuantity());
            }
            item.setQuantity(newQty);
            cartItemRepository.save(item);
        } else {
            cartItemRepository.save(CartItem.builder()
                    .buyer(buyer)
                    .product(product)
                    .quantity(quantity)
                    .build());
        }
    }

    @Override
    @Transactional
    public void updateQuantity(User buyer, Long cartItemId, int quantity) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .filter(c -> c.getBuyer().getId().equals(buyer.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            if (item.getProduct().getQuantity() < quantity) {
                throw new IllegalArgumentException("Not enough stock. Available: " + item.getProduct().getQuantity());
            }
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }
    }

    @Override
    @Transactional
    public void removeFromCart(User buyer, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .filter(c -> c.getBuyer().getId().equals(buyer.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
        cartItemRepository.delete(item);
    }

    @Override
    public List<CartItem> getCartItems(User buyer) {
        return cartItemRepository.findByBuyerOrderByAddedAtDesc(buyer);
    }

    @Override
    public long getCartCount(User buyer) {
        return cartItemRepository.countByBuyer(buyer);
    }

    @Override
    @Transactional
    public void clearCart(User buyer) {
        cartItemRepository.deleteAllByBuyer(buyer);
    }
}
