package com.marketx.marketplace.controller;

import com.marketx.marketplace.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/seller")
public class SellerController {

    @GetMapping("/dashboard")
    public String showDashboard(Authentication authentication, Model model) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        model.addAttribute("user", userDetails.getUser());
        return "seller/dashboard";
    }
}
