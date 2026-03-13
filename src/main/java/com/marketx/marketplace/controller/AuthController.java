package com.marketx.marketplace.controller;

import com.marketx.marketplace.dto.BuyerRegistrationDto;
import com.marketx.marketplace.dto.SellerRegistrationDto;
import com.marketx.marketplace.exception.PasswordMismatchException;
import com.marketx.marketplace.exception.UserAlreadyExistsException;
import com.marketx.marketplace.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String showLoginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {

        if (error != null) {
            String message = switch (error) {
                case "pending"  -> "Your seller account is pending admin approval. Please wait.";
                case "rejected" -> "Your seller account has been rejected by the admin.";
                default         -> "Invalid email or password. Please try again.";
            };
            model.addAttribute("errorMessage", message);
        }

        if (logout != null) {
            model.addAttribute("successMessage", "You have been logged out successfully.");
        }

        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegisterPage() {
        return "auth/register";
    }

    @GetMapping("/register/buyer")
    public String showBuyerRegistrationForm(Model model) {
        model.addAttribute("buyerDto", new BuyerRegistrationDto());
        return "auth/register-buyer";
    }

    @PostMapping("/register/buyer")
    public String registerBuyer(
            @Valid @ModelAttribute("buyerDto") BuyerRegistrationDto dto,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "auth/register-buyer";
        }

        try {
            userService.registerBuyer(dto);
        } catch (UserAlreadyExistsException | PasswordMismatchException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register-buyer";
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Registration successful! You can now log in.");
        return "redirect:/auth/login";
    }

    @GetMapping("/register/seller")
    public String showSellerRegistrationForm(Model model) {
        model.addAttribute("sellerDto", new SellerRegistrationDto());
        return "auth/register-seller";
    }

    @PostMapping("/register/seller")
    public String registerSeller(
            @Valid @ModelAttribute("sellerDto") SellerRegistrationDto dto,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "auth/register-seller";
        }

        try {
            userService.registerSeller(dto);
        } catch (UserAlreadyExistsException | PasswordMismatchException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register-seller";
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Seller registration submitted! Please wait for admin approval before logging in.");
        return "redirect:/auth/login";
    }

    @GetMapping("/access-denied")
    public String showAccessDenied() {
        return "auth/access-denied";
    }
}
