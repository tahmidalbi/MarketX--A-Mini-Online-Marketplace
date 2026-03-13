package com.marketx.marketplace.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
public class CustomAuthSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        String redirectUrl = "/";
        for (GrantedAuthority authority : authorities) {
            redirectUrl = switch (authority.getAuthority()) {
                case "ROLE_ADMIN"  -> "/admin/dashboard";
                case "ROLE_SELLER" -> "/seller/dashboard";
                case "ROLE_BUYER"  -> "/buyer/dashboard";
                default            -> "/";
            };
            break;
        }

        response.sendRedirect(redirectUrl);
    }
}
