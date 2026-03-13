package com.marketx.marketplace.security;

import com.marketx.marketplace.entity.ApprovalStatus;
import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * Spring Security uses this as the principal name (Authentication.getName()).
     * We use email as the login identifier.
     */
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    /**
     * Returns the user's display name.
     * Used in templates via sec:authentication="principal.user.name".
     */
    public String getName() {
        return user.getName();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * REJECTED sellers cannot log in — account is "locked".
     * Spring Security throws LockedException → failure handler maps to ?error=rejected
     */
    @Override
    public boolean isAccountNonLocked() {
        if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.REJECTED) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * PENDING sellers cannot log in — account is "disabled".
     * Spring Security throws DisabledException → failure handler maps to ?error=pending
     */
    @Override
    public boolean isEnabled() {
        if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.PENDING) {
            return false;
        }
        return true;
    }
}
