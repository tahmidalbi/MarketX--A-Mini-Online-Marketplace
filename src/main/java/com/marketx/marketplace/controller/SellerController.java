package com.marketx.marketplace.controller;

import com.marketx.marketplace.dto.EditProductDto;
import com.marketx.marketplace.dto.ProductDto;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.security.CustomUserDetails;
import com.marketx.marketplace.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/seller")
@RequiredArgsConstructor
public class SellerController {

    private final ProductService productService;

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Clothing", "Books", "Sports",
            "Home & Garden", "Toys", "Food", "Beauty", "Accessories", "Other"
    );

    private User currentUser(Authentication auth) {
        return ((CustomUserDetails) auth.getPrincipal()).getUser();
    }

    /* ── Home / Browse ────────────────────────────────────────── */

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

    /* ── My Products ──────────────────────────────────────────── */

    @GetMapping("/my-products")
    public String myProducts(Authentication auth, Model model) {
        User user = currentUser(auth);
        model.addAttribute("user", user);
        model.addAttribute("products", productService.findBySeller(user));
        model.addAttribute("activePage", "my-products");
        return "seller/my-products";
    }

    /* ── Add Product ──────────────────────────────────────────── */

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

    /* ── Edit Product ─────────────────────────────────────────── */

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

    /* ── Delete Product ───────────────────────────────────────── */

    @DeleteMapping("/products/{id}")
    public String deleteProduct(@PathVariable Long id, Authentication auth) {
        productService.deleteProduct(id, currentUser(auth));
        return "redirect:/seller/my-products";
    }

    /* ── My Orders (coming soon) ──────────────────────────────── */

    @GetMapping("/my-orders")
    public String myOrders(Authentication auth, Model model) {
        model.addAttribute("user", currentUser(auth));
        model.addAttribute("activePage", "my-orders");
        return "seller/my-orders";
    }
}
