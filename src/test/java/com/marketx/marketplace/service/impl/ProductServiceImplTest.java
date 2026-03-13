package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.dto.ProductDto;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.repository.ProductRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    // ── Helpers ─────────────────────────────────────────────────

    private User user(Long id) {
        return User.builder().id(id).name("User " + id).email("u" + id + "@test.com").build();
    }

    private Product product(Long id, String name, String category, User seller) {
        return Product.builder()
                .id(id).name(name).description("desc").price(BigDecimal.TEN)
                .quantity(5).category(category).seller(seller).build();
    }

    // ── Tests ────────────────────────────────────────────────────

    /**
     * When neither query nor category is supplied, all products must be fetched via the
     * dedicated findAll path. The search and category methods must never be invoked —
     * calling them unnecessarily would perform a wasteful LIKE query on every column.
     */
    @Test
    void filter_whenNoQueryAndNoCategory_callsFindAllAndNothingElse() {
        List<Product> all = List.of(product(1L, "Laptop", "Electronics", user(1L)));
        when(productRepository.findAllByOrderByCreatedAtDesc()).thenReturn(all);

        List<Product> result = productService.filter(null, null);

        assertThat(result).isSameAs(all);
        verify(productRepository).findAllByOrderByCreatedAtDesc();
        verify(productRepository, never()).searchByQuery(any());
        verify(productRepository, never()).findByCategoryOrderByCreatedAtDesc(any());
    }

    /**
     * When only a category is selected the service must delegate exclusively to
     * findByCategoryOrderByCreatedAtDesc - not to the full-text search.
     * This guards against loading every product and then filtering in Java.
     */
    @Test
    void filter_whenCategoryOnly_delegatesToFindByCategory_andNeverCallsSearch() {
        List<Product> books = List.of(product(2L, "Clean Code", "Books", user(1L)));
        when(productRepository.findByCategoryOrderByCreatedAtDesc("Books")).thenReturn(books);

        List<Product> result = productService.filter("", "Books");

        assertThat(result).isEqualTo(books);
        verify(productRepository).findByCategoryOrderByCreatedAtDesc("Books");
        verify(productRepository, never()).searchByQuery(any());
        verify(productRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    /**
     * When both a text query and a category filter are active the service searches
     * by query first and then narrows the result set to the requested category.
     * This is the most complex branch: it verifies the in-memory intersection logic,
     * ensuring no cross-category product leaks through when both filters are applied.
     */
    @Test
    void filter_whenQueryAndCategoryBothSet_returnsOnlySearchHitsThatMatchCategory() {
        User seller = user(1L);
        Product electronics = product(1L, "Gaming Laptop", "Electronics", seller);
        Product book1       = product(2L, "Clean Code",    "Books",       seller);
        Product book2       = product(3L, "Pragmatic Prog","Books",       seller);

        when(productRepository.searchByQuery("code")).thenReturn(List.of(electronics, book1, book2));

        List<Product> result = productService.filter("code", "Books");

        assertThat(result).containsExactlyInAnyOrder(book1, book2);
        assertThat(result).doesNotContain(electronics);
    }

    /**
     * Ownership guard: a seller attempting to delete a product they do not own must
     * receive a SecurityException. Critically, the repository delete must never be
     * reached — the guard must fail fast before any mutation happens.
     */
    @Test
    void deleteProduct_whenCallerIsNotOwner_throwsSecurityException_andNeverReachesDelete() {
        User owner    = user(1L);
        User attacker = user(2L);
        Product product = product(10L, "Private Item", "Electronics", owner);

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.deleteProduct(10L, attacker))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("do not own");

        verify(productRepository, never()).delete(any());
    }

    /**
     * addProduct must build and persist a Product whose fields exactly mirror the DTO.
     * This test uses ArgumentCaptor to inspect the object actually passed to save(),
     * catching any silent field drops or incorrect mappings.
     */
    @Test
    void addProduct_persistsProductWithExactDtoFieldsAndCorrectSeller() {
        User seller = user(1L);
        ProductDto dto = new ProductDto();
        dto.setName("Wireless Headphones");
        dto.setDescription("Premium audio");
        dto.setPrice(new BigDecimal("49.99"));
        dto.setQuantity(20);
        dto.setCategory("Electronics");
        dto.setImageUrl("https://img.example.com/head.jpg");

        productService.addProduct(dto, seller);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Wireless Headphones");
        assertThat(saved.getPrice()).isEqualTo(new BigDecimal("49.99"));
        assertThat(saved.getCategory()).isEqualTo("Electronics");
        assertThat(saved.getQuantity()).isEqualTo(20);
        assertThat(saved.getImageUrl()).isEqualTo("https://img.example.com/head.jpg");
        assertThat(saved.getSeller()).isSameAs(seller);
    }
}
