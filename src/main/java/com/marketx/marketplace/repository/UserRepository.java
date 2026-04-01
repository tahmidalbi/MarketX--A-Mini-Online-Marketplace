package com.marketx.marketplace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marketx.marketplace.entity.ApprovalStatus;
import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleAndApprovalStatus(Role role, ApprovalStatus approvalStatus);

    long countByRoleAndApprovalStatus(Role role, ApprovalStatus approvalStatus);

    List<User> findByRole(Role role);
}
