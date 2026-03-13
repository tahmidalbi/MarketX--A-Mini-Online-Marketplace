package com.marketx.marketplace.repository;

import com.marketx.marketplace.entity.ApprovalStatus;
import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_returnsUser_whenUserExists() {
        User user = User.builder()
                .name("John Buyer")
                .email("john@test.com")
                .password("encoded_password")
                .role(Role.BUYER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("john@test.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("John Buyer");
        assertThat(found.get().getRole()).isEqualTo(Role.BUYER);
    }

    @Test
    void findByRoleAndApprovalStatus_returnsPendingSellersOnly() {
        User pendingSeller = User.builder()
                .name("Pending Seller")
                .email("seller@test.com")
                .password("encoded_password")
                .role(Role.SELLER)
                .approvalStatus(ApprovalStatus.PENDING)
                .build();
        User approvedBuyer = User.builder()
                .name("Approved Buyer")
                .email("buyer@test.com")
                .password("encoded_password")
                .role(Role.BUYER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        userRepository.save(pendingSeller);
        userRepository.save(approvedBuyer);

        List<User> result = userRepository.findByRoleAndApprovalStatus(Role.SELLER, ApprovalStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("seller@test.com");
    }

    /**
     * A seller whose account was disabled by an admin (approvalStatus set to REJECTED)
     * must NOT appear in getPendingSellerRegistrations()-style queries. This validates
     * the exclusion logic that the admin disable feature relies on at the repository level.
     */
    @Test
    void findByRoleAndApprovalStatus_excludesDisabledSeller_fromPendingResults() {
        User pendingSeller = User.builder()
                .name("New Seller")
                .email("new-seller@admin-test.com")
                .password("encoded_password")
                .role(Role.SELLER)
                .approvalStatus(ApprovalStatus.PENDING)
                .build();
        User disabledSeller = User.builder()
                .name("Banned Seller")
                .email("banned-seller@admin-test.com")
                .password("encoded_password")
                .role(Role.SELLER)
                .approvalStatus(ApprovalStatus.REJECTED)
                .build();
        userRepository.save(pendingSeller);
        userRepository.save(disabledSeller);

        List<User> pending = userRepository.findByRoleAndApprovalStatus(Role.SELLER, ApprovalStatus.PENDING);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getEmail()).isEqualTo("new-seller@admin-test.com");
    }
}
