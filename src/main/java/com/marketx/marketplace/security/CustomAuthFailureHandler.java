package com.marketx.marketplace.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String errorParam;
        if (exception instanceof DisabledException) {
            errorParam = "pending";
        } else if (exception instanceof LockedException) {
            errorParam = "rejected";
        } else {
            errorParam = "invalid";
        }
        response.sendRedirect("/auth/login?error=" + errorParam);
    }
}
