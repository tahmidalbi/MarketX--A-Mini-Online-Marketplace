package com.marketx.marketplace.controller;

import com.marketx.marketplace.entity.Role;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.security.CustomUserDetails;
import com.marketx.marketplace.service.AdminService;
import com.marketx.marketplace.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ProductService productService;

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Clothing", "Books", "Sports",
            "Home & Garden", "Toys", "Food", "Beauty", "Accessories", "Other");

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String showDashboard(@RequestParam(required = false) String q,
                                @RequestParam(required = false) String category,
                                Model model) {
        var products = productService.filter(q, category);
        model.addAttribute("products", products);
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("productCount", adminService.getAllProducts().size());
        model.addAttribute("userCount", adminService.getAllUsers().size());
        model.addAttribute("pendingCount", adminService.getPendingSellerRegistrations().size());
        return "admin/dashboard";
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public String showAllUsers(Model model) {
        model.addAttribute("users", adminService.getAllUsers());
        model.addAttribute("pendingCount", adminService.getPendingSellerRegistrations().size());
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String showUserDetail(@PathVariable Long id, Model model) {
        User user = adminService.getUserById(id);
        model.addAttribute("user", user);
        if (user.getRole() == Role.SELLER) {
            model.addAttribute("sellerProducts", adminService.getProductsByUser(id));
        }
        return "admin/user-detail";
    }

    @PostMapping("/sellers/{id}/approve")
    public String approveSeller(@PathVariable Long id, RedirectAttributes ra) {
        adminService.approveSeller(id);
        ra.addFlashAttribute("successMessage", "Seller approved successfully.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/sellers/{id}/reject")
    public String rejectSeller(@PathVariable Long id, RedirectAttributes ra) {
        adminService.rejectSeller(id);
        ra.addFlashAttribute("successMessage", "Seller rejected.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/disable")
    public String disableUser(@PathVariable Long id,
                              Authentication auth,
                              RedirectAttributes ra) {
        CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
        if (principal.getUser().getId().equals(id)) {
            ra.addFlashAttribute("errorMessage", "You cannot disable your own account.");
            return "redirect:/admin/users/" + id;
        }
        adminService.disableUser(id);
        ra.addFlashAttribute("successMessage", "Account disabled.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/enable")
    public String enableUser(@PathVariable Long id, RedirectAttributes ra) {
        adminService.enableUser(id);
        ra.addFlashAttribute("successMessage", "Account enabled.");
        return "redirect:/admin/users/" + id;
    }

    // ── Products ──────────────────────────────────────────────────────────────

    @GetMapping("/products")
    public String showAllProducts(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String category,
                                  Model model) {
        model.addAttribute("products", productService.filter(q, category));
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("categories", CATEGORIES);
        return "admin/products";
    }

    @DeleteMapping("/products/{id}")
    public String removeProduct(@PathVariable Long id, RedirectAttributes ra) {
        adminService.removeProduct(id);
        ra.addFlashAttribute("successMessage", "Product removed.");
        return "redirect:/admin/products";
    }
}
