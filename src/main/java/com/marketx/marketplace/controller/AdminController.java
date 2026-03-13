package com.marketx.marketplace.controller;

import com.marketx.marketplace.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        model.addAttribute("pendingSellers", adminService.getPendingSellerRegistrations());
        return "admin/dashboard";
    }

    @PostMapping("/sellers/{id}/approve")
    public String approveSeller(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        adminService.approveSeller(id);
        redirectAttributes.addFlashAttribute("successMessage", "Seller account approved successfully.");
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/sellers/{id}/reject")
    public String rejectSeller(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        adminService.rejectSeller(id);
        redirectAttributes.addFlashAttribute("successMessage", "Seller account rejected.");
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/users")
    public String showAllUsers(Model model) {
        model.addAttribute("users", adminService.getAllUsers());
        return "admin/users";
    }
}
