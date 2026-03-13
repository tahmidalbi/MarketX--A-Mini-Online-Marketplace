package com.marketx.marketplace.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                return switch (authority.getAuthority()) {
                    case "ROLE_ADMIN"  -> "redirect:/admin/dashboard";
                    case "ROLE_SELLER" -> "redirect:/seller/dashboard";
                    case "ROLE_BUYER"  -> "redirect:/buyer/dashboard";
                    default            -> "index";
                };
            }
        }
        return "index";
    }
}
