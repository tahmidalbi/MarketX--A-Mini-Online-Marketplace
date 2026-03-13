package com.marketx.marketplace.controller;

import com.marketx.marketplace.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductService productService;

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Clothing", "Books", "Sports",
            "Home & Garden", "Toys", "Food", "Beauty", "Accessories", "Other"
    );

    @GetMapping("/")
    public String home(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       Authentication authentication,
                       Model model) {
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
        model.addAttribute("products", productService.filter(q, category));
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("categories", CATEGORIES);
        return "index";
    }
}
