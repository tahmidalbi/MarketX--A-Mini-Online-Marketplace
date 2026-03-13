package com.marketx.marketplace.config;

import com.marketx.marketplace.entity.ApprovalStatus;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.repository.ProductRepository;
import com.marketx.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "albitahmid@gmail.com";
    private static final String AKASH_EMAIL = "akash@gmail.com";
    private static final String ANIK_EMAIL  = "anik@gmail.com";
    private static final String SELLER_PASS = "123456";

    @Override
    public void run(ApplicationArguments args) {
        seedAdmin();
        User akash = seedSeller("Akash", AKASH_EMAIL);
        User anik  = seedSeller("Anik",  ANIK_EMAIL);
        seedProducts(akash, anik);
    }

    /* ── Users ────────────────────────────────────────────────── */

    private void seedAdmin() {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) return;
        userRepository.save(User.builder()
                .name("Tahmid Albi")
                .email(ADMIN_EMAIL)
                .password(passwordEncoder.encode("rafiqul25"))
                .role(Role.ADMIN)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build());
        log.info("Seeded admin: {}", ADMIN_EMAIL);
    }

    private User seedSeller(String name, String email) {
        if (userRepository.existsByEmail(email)) {
            return userRepository.findByEmail(email).orElseThrow();
        }
        User seller = userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(SELLER_PASS))
                .role(Role.SELLER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build());
        log.info("Seeded seller: {}", email);
        return seller;
    }

    /* ── Products ─────────────────────────────────────────────── */

    private void seedProducts(User akash, User anik) {
        if (!productRepository.findBySellerOrderByCreatedAtDesc(akash).isEmpty()) return;

        // Akash — Electronics
        productRepository.save(product(
                "Wireless Noise-Cancelling Headphones",
                "Premium over-ear headphones with 40-hour battery, active noise cancellation, and crystal-clear audio. Foldable design with a carrying case included.",
                "89.99", 25, "Electronics", akash));

        productRepository.save(product(
                "Mechanical Gaming Keyboard",
                "Compact TKL layout with Cherry MX Red switches, per-key RGB lighting, and braided USB-C cable. N-key rollover for flawless gaming performance.",
                "54.99", 40, "Electronics", akash));

        productRepository.save(product(
                "USB-C Hub 7-in-1",
                "Expands a single USB-C port into 4K HDMI, 3× USB-A 3.0, SD/MicroSD card slots, and 100W PD pass-through charging.",
                "29.99", 60, "Electronics", akash));

        // Anik — Books & Accessories
        productRepository.save(product(
                "Clean Code",
                "A handbook of agile software craftsmanship by Robert C. Martin. Essential reading for every developer who cares about writing maintainable software.",
                "19.99", 15, "Books", anik));

        productRepository.save(product(
                "The Pragmatic Programmer",
                "David Thomas and Andrew Hunt's classic guide — covers career tips, tooling, and timeless engineering principles every developer should know.",
                "22.99", 12, "Books", anik));

        productRepository.save(product(
                "Minimalist Leather Wallet",
                "Ultra-slim genuine leather bifold wallet. Holds up to 8 cards with RFID-blocking lining. Available in black and brown.",
                "18.99", 30, "Accessories", anik));

        log.info("Seeded products for Akash and Anik.");
    }

    private Product product(String name, String description, String price,
                            int quantity, String category, User seller) {
        return Product.builder()
                .name(name)
                .description(description)
                .price(new BigDecimal(price))
                .quantity(quantity)
                .category(category)
                .seller(seller)
                .build();
    }
}
