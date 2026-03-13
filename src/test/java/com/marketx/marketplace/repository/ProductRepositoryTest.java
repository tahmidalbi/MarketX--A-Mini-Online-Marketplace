package com.marketx.marketplace.repository;

import com.marketx.marketplace.entity.ApprovalStatus;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer tests running against a real H2 in-memory database.
 * Each test runs inside a transaction that is rolled back on completion,
 * so DataSeeder data (seeded once at context startup) is never disturbed.
 *
 * Category/search values are deliberately chosen to avoid collision with
 * DataSeeder products (Electronics, Books, Accessories).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private User seller;

    @BeforeEach
    void setUp() {
        // A seller that does not exist in DataSeeder — safe from cross-test interference.
        seller = userRepository.save(User.builder()
                .name("Repo Test Seller")
                .email("reposeller@test.com")
                .password("encoded")
                .role(Role.SELLER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build());
    }

    private Product persist(String name, String category) {
        return productRepository.save(Product.builder()
                .name(name)
                .description("Description for " + name)
                .price(BigDecimal.valueOf(9.99))
                .quantity(10)
                .category(category)
                .seller(seller)
                .build());
    }

    /**
     * The JPQL searchByQuery must match against the product name case-insensitively.
     * Using the term "quasar" which does not appear in any DataSeeder product ensures
     * only our own fixture is returned — the count can be asserted exactly.
     */
    @Test
    void searchByQuery_matchesByProductNameCaseInsensitively() {
        persist("Quasar Telescope Pro", "Toys");
        persist("Mountain Bike 21-Speed", "Sports");

        List<Product> results = productRepository.searchByQuery("quasar");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Quasar Telescope Pro");
    }

    /**
     * findByCategoryOrderByCreatedAtDesc must return products exclusively from the
     * requested category. Using "Clothing" (absent from DataSeeder) lets us assert
     * the exact count and verify no cross-category products bleed through.
     */
    @Test
    void findByCategoryOrderByCreatedAtDesc_returnsOnlyProductsInRequestedCategory() {
        persist("Slim-Fit Jeans",  "Clothing");
        persist("Polo T-Shirt",    "Clothing");
        persist("Tennis Racket",   "Sports");   // must NOT appear

        List<Product> results = productRepository.findByCategoryOrderByCreatedAtDesc("Clothing");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(p -> p.getCategory().equals("Clothing"));
        assertThat(results).noneMatch(p -> p.getCategory().equals("Sports"));
    }

    /**
     * findBySellerOrderByCreatedAtDesc must only return products belonging to the
     * given seller. The DataSeeder's products belong to different sellers (Akash, Anik)
     * so they must not appear — confirming strict seller-scoped isolation.
     */
    @Test
    void findBySellerOrderByCreatedAtDesc_doesNotReturnOtherSellersProducts() {
        // Seed a second seller inside the same test transaction
        User otherSeller = userRepository.save(User.builder()
                .name("Other Seller")
                .email("other@test.com")
                .password("encoded")
                .role(Role.SELLER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build());

        persist("My Product A", "Toys");
        persist("My Product B", "Sports");
        productRepository.save(Product.builder()      // belongs to otherSeller
                .name("Other Seller Product")
                .description("Not mine")
                .price(BigDecimal.TEN)
                .quantity(3)
                .category("Clothing")
                .seller(otherSeller)
                .build());

        List<Product> results = productRepository.findBySellerOrderByCreatedAtDesc(seller);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(p -> p.getSeller().getId().equals(seller.getId()));
        assertThat(results).noneMatch(p -> p.getName().equals("Other Seller Product"));
    }
}
