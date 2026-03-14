package com.marketx.marketplace.controller;

import com.marketx.marketplace.dto.ProfileUpdateDto;
import com.marketx.marketplace.exception.UserAlreadyExistsException;
import com.marketx.marketplace.security.CustomUserDetails;
import com.marketx.marketplace.service.ProductService;
import com.marketx.marketplace.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/buyer")
@RequiredArgsConstructor
public class BuyerController {

    private final ProductService productService;
    private final UserService userService;

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Clothing", "Books", "Sports",
            "Home & Garden", "Toys", "Food", "Beauty", "Accessories", "Other");

    @GetMapping("/dashboard")
    public String showDashboard(@RequestParam(required = false) String q,
                                @RequestParam(required = false) String category,
                                Authentication authentication,
                                Model model) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        model.addAttribute("user", userDetails.getUser());
        model.addAttribute("products", productService.filter(q, category));
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("activePage", "home");
        return "buyer/dashboard";
    }

    @GetMapping("/profile")
    public String showProfile(Authentication authentication, Model model) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName(userDetails.getUser().getName());
        dto.setEmail(userDetails.getUser().getEmail());

        model.addAttribute("user", userDetails.getUser());
        model.addAttribute("profileDto", dto);
        model.addAttribute("activePage", "profile");
        return "buyer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileDto") ProfileUpdateDto dto,
                                BindingResult result,
                                Authentication authentication,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        if (result.hasErrors()) {
            model.addAttribute("user", userDetails.getUser());
            model.addAttribute("activePage", "profile");
            return "buyer/profile";
        }

        try {
            userService.updateProfile(userDetails.getUser().getId(), dto);
            // Reflect changes in the current session principal immediately
            userDetails.getUser().setName(dto.getName());
            userDetails.getUser().setEmail(dto.getEmail());
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
        } catch (UserAlreadyExistsException e) {
            model.addAttribute("user", userDetails.getUser());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("activePage", "profile");
            return "buyer/profile";
        }

        return "redirect:/buyer/profile";
    }
}
