package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.entity.ApprovalStatus;
import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.repository.UserRepository;
import com.marketx.marketplace.service.AdminService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<User> getPendingSellerRegistrations() {
        return userRepository.findByRoleAndApprovalStatus(Role.SELLER, ApprovalStatus.PENDING);
    }

    @Override
    @Transactional
    public void approveSeller(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new EntityNotFoundException("User not found with id: " + userId));
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void rejectSeller(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new EntityNotFoundException("User not found with id: " + userId));
        user.setApprovalStatus(ApprovalStatus.REJECTED);
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
