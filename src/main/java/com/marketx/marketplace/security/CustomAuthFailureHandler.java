package com.marketx.marketplace.security;

import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {

    private final UserRepository userRepository;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String errorParam;
        if (exception instanceof DisabledException) {
            errorParam = "pending";
        } else if (exception instanceof LockedException) {
            // Sellers get "rejected" (approval was denied); all other roles get "disabled"
            String email = request.getParameter("email");
            boolean isSeller = userRepository.findByEmail(email)
                    .map(u -> u.getRole() == Role.SELLER)
                    .orElse(false);
            errorParam = isSeller ? "rejected" : "disabled";
        } else {
            errorParam = "invalid";
        }
        response.sendRedirect("/auth/login?error=" + errorParam);
    }
}
