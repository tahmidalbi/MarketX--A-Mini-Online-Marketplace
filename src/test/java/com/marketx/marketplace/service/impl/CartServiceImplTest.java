package com.marketx.marketplace.service.impl;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marketx.marketplace.entity.CartItem;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.exception.ResourceNotFoundException;
import com.marketx.marketplace.repository.CartItemRepository;
import com.marketx.marketplace.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    // ── Helpers ─────────────────────────────────────────────────

    private User buyer(Long id) {
        return User.builder().id(id).name("Buyer " + id).email("buyer" + id + "@test.com").build();
    }

    private Product product(Long id, int stock) {
        return Product.builder()
                .id(id).name("Product " + id).description("desc")
                .price(new BigDecimal("10.00")).quantity(stock)
                .category("Electronics").build();
    }

    // ── addToCart ────────────────────────────────────────────────

    /**
     * Adding a product to an empty cart must create a new CartItem with the
     * correct buyer, product and quantity via a single save() call.
     * findByBuyerAndProduct returning empty simulates no existing cart row.
     */
    @Test
    void addToCart_whenNoExistingItem_createsNewCartItem() {
        User buyer = buyer(1L);
        Product product = product(10L, 5);

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByBuyerAndProduct(buyer, product)).thenReturn(Optional.empty());

        cartService.addToCart(buyer, 10L, 2);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        CartItem saved = captor.getValue();

        assertThat(saved.getBuyer()).isSameAs(buyer);
        assertThat(saved.getProduct()).isSameAs(product);
        assertThat(saved.getQuantity()).isEqualTo(2);
    }

    /**
     * Adding more of a product already in the cart must increment the existing
     * CartItem's quantity — not create a duplicate row. The save must be called
     * once on the updated existing item.
     */
    @Test
    void addToCart_whenItemAlreadyInCart_incrementsQuantity() {
        User buyer = buyer(1L);
        Product product = product(10L, 10);
        CartItem existing = CartItem.builder()
                .id(99L).buyer(buyer).product(product).quantity(3).build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByBuyerAndProduct(buyer, product)).thenReturn(Optional.of(existing));

        cartService.addToCart(buyer, 10L, 2);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(5); // 3 + 2
    }

    /**
     * Requesting more quantity than available stock must throw
     * IllegalArgumentException before any save() is called. This prevents
     * overselling at the service layer.
     */
    @Test
    void addToCart_whenRequestedQuantityExceedsStock_throwsIllegalArgument() {
        User buyer = buyer(1L);
        Product product = product(10L, 2); // only 2 in stock

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addToCart(buyer, 10L, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not enough stock");

        verify(cartItemRepository, never()).save(any());
    }

    /**
     * Adding a product that does not exist in the database must throw
     * ResourceNotFoundException. The cart must not be modified.
     */
    @Test
    void addToCart_whenProductDoesNotExist_throwsResourceNotFoundException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addToCart(buyer(1L), 999L, 1))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cartItemRepository, never()).save(any());
    }

    // ── updateQuantity ───────────────────────────────────────────

    /**
     * Setting quantity to 0 (or negative) must delete the cart item entirely
     * rather than saving a zero-quantity row. This cleans up the cart
     * naturally when a user decrements to zero.
     */
    @Test
    void updateQuantity_whenQuantityIsZero_deletesCartItem() {
        User buyer = buyer(1L);
        Product product = product(10L, 5);
        CartItem item = CartItem.builder()
                .id(1L).buyer(buyer).product(product).quantity(2).build();

        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        cartService.updateQuantity(buyer, 1L, 0);

        verify(cartItemRepository).delete(item);
        verify(cartItemRepository, never()).save(any());
    }

    /**
     * A buyer must not be able to update a cart item that belongs to another buyer.
     * The ownership check uses buyer ID comparison; a mismatched ID must throw
     * ResourceNotFoundException, treating the item as invisible to the caller.
     */
    @Test
    void updateQuantity_whenCallerDoesNotOwnItem_throwsResourceNotFoundException() {
        User owner    = buyer(1L);
        User attacker = buyer(2L);
        Product product = product(10L, 5);
        CartItem item = CartItem.builder()
                .id(1L).buyer(owner).product(product).quantity(2).build();

        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateQuantity(attacker, 1L, 3))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cartItemRepository, never()).save(any());
        verify(cartItemRepository, never()).delete(any());
    }

    // ── removeFromCart ───────────────────────────────────────────

    /**
     * Removing a cart item must call delete() exactly once with the correct item.
     * A buyer must only be able to remove their own items — ownership is validated
     * before deletion.
     */
    @Test
    void removeFromCart_asOwner_deletesItemExactlyOnce() {
        User buyer = buyer(1L);
        Product product = product(10L, 5);
        CartItem item = CartItem.builder()
                .id(5L).buyer(buyer).product(product).quantity(1).build();

        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        cartService.removeFromCart(buyer, 5L);

        verify(cartItemRepository, times(1)).delete(item);
    }

    /**
     * A buyer attempting to remove another buyer's cart item must receive
     * ResourceNotFoundException. The delete must never be called.
     */
    @Test
    void removeFromCart_whenCallerDoesNotOwnItem_throwsResourceNotFoundException() {
        User owner    = buyer(1L);
        User attacker = buyer(2L);
        Product product = product(10L, 5);
        CartItem item = CartItem.builder()
                .id(5L).buyer(owner).product(product).quantity(1).build();

        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.removeFromCart(attacker, 5L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cartItemRepository, never()).delete(any());
    }
}
