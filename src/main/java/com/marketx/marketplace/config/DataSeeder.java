package com.marketx.marketplace.config;

import com.marketx.marketplace.entity.ApprovalStatus;
import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "albitahmid@gmail.com";

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail(ADMIN_EMAIL)) {
            User admin = User.builder()
                    .name("Tahmid Albi")
                    .email(ADMIN_EMAIL)
                    .password(passwordEncoder.encode("rafiqul25"))
                    .role(Role.ADMIN)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .build();
            userRepository.save(admin);
            log.info("Admin user seeded: {}", ADMIN_EMAIL);
        } else {
            log.info("Admin user already exists, skipping seed.");
        }
    }
}
