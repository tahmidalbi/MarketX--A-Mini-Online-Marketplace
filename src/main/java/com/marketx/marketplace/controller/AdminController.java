package com.marketx.marketplace.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.marketx.marketplace.entity.Order;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.service.AdminService;
import com.marketx.marketplace.service.OrderService;
import com.marketx.marketplace.service.ProductService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ProductService productService;
    private final OrderService orderService;

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Clothing", "Books", "Sports",
            "Home & Garden", "Toys", "Food", "Beauty", "Accessories", "Other"
    );

    /* ── Dashboard ──────────────────────────────────────────────── */

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String q,
                            @RequestParam(required = false) String category,
                            Model model) {
        List<User> users = adminService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("userCount", users.size());
        model.addAttribute("productCount", adminService.getAllProducts().size());
        model.addAttribute("pendingCount", adminService.getPendingSellerCount());
        model.addAttribute("totalOrders", orderService.getAllOrders().size());
        model.addAttribute("totalRevenue", orderService.getTotalPlatformRevenue());
        model.addAttribute("products", productService.filter(q, category));
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("categories", CATEGORIES);
        return "admin/dashboard";
    }

    /* ── Users ──────────────────────────────────────────────────── */

    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", adminService.getAllUsers());
        model.addAttribute("pendingCount", adminService.getPendingSellerCount());
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, Model model) {
        User user = adminService.getUserById(id);
        model.addAttribute("user", user);
        if (user.getRole().name().equals("SELLER")) {
            model.addAttribute("sellerProducts", productService.findBySeller(user));
        }
        return "admin/user-detail";
    }

    @PostMapping("/sellers/{id}/approve")
    public String approveSeller(@PathVariable Long id, RedirectAttributes ra) {
        adminService.approveSeller(id);
        ra.addFlashAttribute("successMessage", "Seller approved.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/sellers/{id}/reject")
    public String rejectSeller(@PathVariable Long id, RedirectAttributes ra) {
        adminService.rejectSeller(id);
        ra.addFlashAttribute("successMessage", "Seller rejected.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/disable")
    public String disableUser(@PathVariable Long id, RedirectAttributes ra) {
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

    /* ── Products ───────────────────────────────────────────────── */

    @GetMapping("/products")
    public String listProducts(@RequestParam(required = false) String q,
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

    /* ── Orders ─────────────────────────────────────────────────── */

    @GetMapping("/orders")
    public String listOrders(Model model) {
        List<Order> orders = orderService.getAllOrders();
        model.addAttribute("orders", orders);
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable Long id, Model model) {
        Order order = orderService.getOrderById(id);
        model.addAttribute("order", order);
        return "admin/order-detail";
    }

    /* ── Statistics ─────────────────────────────────────────────── */

    @GetMapping("/statistics")
    public String statistics(Model model) {
        model.addAttribute("totalRevenue", orderService.getTotalPlatformRevenue());
        model.addAttribute("revenuePerSeller", orderService.getRevenuePerSeller());
        model.addAttribute("globalRevenueByCategory", orderService.getGlobalRevenueByCategory());
        model.addAttribute("totalOrders", orderService.getAllOrders().size());
        model.addAttribute("totalUsers", adminService.getAllUsers().size());
        model.addAttribute("pendingCount", adminService.getPendingSellerCount());
        return "admin/statistics";
    }
}
