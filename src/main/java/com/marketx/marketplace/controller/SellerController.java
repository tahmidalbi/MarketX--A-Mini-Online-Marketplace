package com.marketx.marketplace.controller;

import com.marketx.marketplace.dto.EditProductDto;
import com.marketx.marketplace.dto.ProductDto;
import com.marketx.marketplace.entity.Order;
import com.marketx.marketplace.entity.OrderStatus;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.security.CustomUserDetails;
import com.marketx.marketplace.service.OrderService;
import com.marketx.marketplace.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/seller")
@RequiredArgsConstructor
public class SellerController {

    private final ProductService productService;
    private final OrderService orderService;

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Clothing", "Books", "Sports",
            "Home & Garden", "Toys", "Food", "Beauty", "Accessories", "Other"
    );

    private User currentUser(Authentication auth) {
        return ((CustomUserDetails) auth.getPrincipal()).getUser();
    }

    /* ── Dashboard ──────────────────────────────────────────────── */

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String q,
                            @RequestParam(required = false) String category,
                            Authentication auth, Model model) {
        model.addAttribute("user", currentUser(auth));
        model.addAttribute("products", productService.filter(q, category));
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("activePage", "home");
        return "seller/dashboard";
    }

    /* ── My Products ──────────────────────────────────────────────── */

    @GetMapping("/my-products")
    public String myProducts(Authentication auth, Model model) {
        User user = currentUser(auth);
        model.addAttribute("user", user);
        model.addAttribute("products", productService.findBySeller(user));
        model.addAttribute("activePage", "my-products");
        return "seller/my-products";
    }

    @GetMapping("/products/add")
    public String showAddForm(Authentication auth, Model model) {
        model.addAttribute("user", currentUser(auth));
        model.addAttribute("productDto", new ProductDto());
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("activePage", "my-products");
        return "seller/add-product";
    }

    @PostMapping("/products")
    public String addProduct(@Valid @ModelAttribute ProductDto productDto,
                             BindingResult result,
                             Authentication auth,
                             Model model) {
        if (result.hasErrors()) {
            model.addAttribute("user", currentUser(auth));
            model.addAttribute("categories", CATEGORIES);
            model.addAttribute("activePage", "my-products");
            return "seller/add-product";
        }
        productService.addProduct(productDto, currentUser(auth));
        return "redirect:/seller/my-products";
    }

    @GetMapping("/products/{id}/edit")
    public String showEditForm(@PathVariable Long id, Authentication auth, Model model) {
        User user = currentUser(auth);
        Product product = productService.findById(id)
                .filter(p -> p.getSeller().getId().equals(user.getId()))
                .orElse(null);
        if (product == null) return "redirect:/seller/my-products";

        EditProductDto dto = new EditProductDto();
        dto.setPrice(product.getPrice());
        dto.setDescription(product.getDescription());

        model.addAttribute("user", user);
        model.addAttribute("product", product);
        model.addAttribute("editDto", dto);
        model.addAttribute("activePage", "my-products");
        return "seller/edit-product";
    }

    @PutMapping("/products/{id}")
    public String editProduct(@PathVariable Long id,
                              @Valid @ModelAttribute EditProductDto editDto,
                              BindingResult result,
                              Authentication auth,
                              Model model) {
        User user = currentUser(auth);
        if (result.hasErrors()) {
            Product product = productService.findById(id).orElse(null);
            model.addAttribute("user", user);
            model.addAttribute("product", product);
            model.addAttribute("activePage", "my-products");
            return "seller/edit-product";
        }
        productService.updateProduct(id, editDto, user);
        return "redirect:/seller/my-products";
    }

    @DeleteMapping("/products/{id}")
    public String deleteProduct(@PathVariable Long id, Authentication auth) {
        productService.deleteProduct(id, currentUser(auth));
        return "redirect:/seller/my-products";
    }

    /* ── My Orders ──────────────────────────────────────────────── */

    @GetMapping("/my-orders")
    public String myOrders(Authentication auth, Model model) {
        User user = currentUser(auth);
        List<Order> orders = orderService.getSellerOrders(user);
        model.addAttribute("user", user);
        model.addAttribute("orders", orders);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("activePage", "my-orders");
        return "seller/my-orders";
    }

    @PostMapping("/my-orders/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam OrderStatus status,
                               Authentication auth,
                               RedirectAttributes ra) {
        try {
            orderService.updateOrderStatus(currentUser(auth), id, status);
            ra.addFlashAttribute("successMessage", "Order #" + id + " status updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/seller/my-orders";
    }

    /* ── Analytics ──────────────────────────────────────────────── */

    @GetMapping("/analytics")
    public String analytics(Authentication auth, Model model) {
        User user = currentUser(auth);
        model.addAttribute("user", user);
        model.addAttribute("totalRevenue", orderService.getSellerTotalRevenue(user));
        model.addAttribute("revenueByCategory", orderService.getSellerRevenueByCategory(user));
        model.addAttribute("unitsByCategory", orderService.getSellerUnitsByCategory(user));
        model.addAttribute("topProducts", orderService.getSellerTopProducts(user));
        model.addAttribute("totalProducts", productService.findBySeller(user).size());
        model.addAttribute("totalOrders", orderService.getSellerOrders(user).size());
        model.addAttribute("activePage", "analytics");
        return "seller/analytics";
    }
}
