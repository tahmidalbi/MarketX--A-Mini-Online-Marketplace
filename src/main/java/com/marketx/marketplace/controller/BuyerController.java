package com.marketx.marketplace.controller;

import com.marketx.marketplace.dto.CheckoutDto;
import com.marketx.marketplace.dto.ProfileUpdateDto;
import com.marketx.marketplace.dto.ReviewDto;
import com.marketx.marketplace.entity.CartItem;
import com.marketx.marketplace.entity.Order;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.exception.ResourceNotFoundException;
import com.marketx.marketplace.exception.UserAlreadyExistsException;
import com.marketx.marketplace.security.CustomUserDetails;
import com.marketx.marketplace.service.CartService;
import com.marketx.marketplace.service.OrderService;
import com.marketx.marketplace.service.ProductService;
import com.marketx.marketplace.service.ReviewService;
import com.marketx.marketplace.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/buyer")
@RequiredArgsConstructor
public class BuyerController {

    private final ProductService productService;
    private final UserService userService;
    private final CartService cartService;
    private final OrderService orderService;
    private final ReviewService reviewService;

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Clothing", "Books", "Sports",
            "Home & Garden", "Toys", "Food", "Beauty", "Accessories", "Other");

    private User currentUser(Authentication auth) {
        return ((CustomUserDetails) auth.getPrincipal()).getUser();
    }

    /* ── Dashboard ──────────────────────────────────────────────── */

    @GetMapping("/dashboard")
    public String showDashboard(@RequestParam(required = false) String q,
                                @RequestParam(required = false) String category,
                                Authentication authentication,
                                Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("products", productService.filter(q, category));
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("selectedCategory", category != null ? category : "");
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("activePage", "home");
        model.addAttribute("cartCount", cartService.getCartCount(user));
        return "buyer/dashboard";
    }

    /* ── Profile ────────────────────────────────────────────────── */

    @GetMapping("/profile")
    public String showProfile(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        model.addAttribute("user", user);
        model.addAttribute("profileDto", dto);
        model.addAttribute("activePage", "profile");
        model.addAttribute("cartCount", cartService.getCartCount(user));
        return "buyer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileDto") ProfileUpdateDto dto,
                                BindingResult result,
                                Authentication authentication,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();
        if (result.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("activePage", "profile");
            model.addAttribute("cartCount", cartService.getCartCount(user));
            return "buyer/profile";
        }
        try {
            userService.updateProfile(user.getId(), dto);
            userDetails.getUser().setName(dto.getName());
            userDetails.getUser().setEmail(dto.getEmail());
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
        } catch (UserAlreadyExistsException e) {
            model.addAttribute("user", user);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("activePage", "profile");
            model.addAttribute("cartCount", cartService.getCartCount(user));
            return "buyer/profile";
        }
        return "redirect:/buyer/profile";
    }

    /* ── Cart ───────────────────────────────────────────────────── */

    @GetMapping("/cart")
    public String viewCart(Authentication auth, Model model) {
        User user = currentUser(auth);
        List<CartItem> items = cartService.getCartItems(user);
        BigDecimal total = items.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("user", user);
        model.addAttribute("cartItems", items);
        model.addAttribute("cartTotal", total);
        model.addAttribute("activePage", "cart");
        model.addAttribute("cartCount", items.size());
        return "buyer/cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(defaultValue = "1") int quantity,
                            Authentication auth,
                            RedirectAttributes ra) {
        try {
            cartService.addToCart(currentUser(auth), productId, quantity);
            ra.addFlashAttribute("successMessage", "Item added to cart!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/buyer/cart";
    }

    @PostMapping("/cart/{id}/update")
    public String updateCartItem(@PathVariable Long id,
                                 @RequestParam int quantity,
                                 Authentication auth,
                                 RedirectAttributes ra) {
        try {
            cartService.updateQuantity(currentUser(auth), id, quantity);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/buyer/cart";
    }

    @PostMapping("/cart/{id}/remove")
    public String removeFromCart(@PathVariable Long id,
                                 Authentication auth,
                                 RedirectAttributes ra) {
        cartService.removeFromCart(currentUser(auth), id);
        ra.addFlashAttribute("successMessage", "Item removed from cart.");
        return "redirect:/buyer/cart";
    }

    /* ── Checkout ───────────────────────────────────────────────── */

    @GetMapping("/checkout")
    public String showCheckout(Authentication auth, Model model) {
        User user = currentUser(auth);
        List<CartItem> items = cartService.getCartItems(user);
        if (items.isEmpty()) return "redirect:/buyer/cart";

        BigDecimal total = items.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("user", user);
        model.addAttribute("cartItems", items);
        model.addAttribute("cartTotal", total);
        model.addAttribute("checkoutDto", new CheckoutDto());
        model.addAttribute("activePage", "cart");
        model.addAttribute("cartCount", items.size());
        return "buyer/checkout";
    }

    @PostMapping("/checkout")
    public String placeOrder(@Valid @ModelAttribute CheckoutDto checkoutDto,
                             BindingResult result,
                             Authentication auth,
                             Model model,
                             RedirectAttributes ra) {
        User user = currentUser(auth);
        if (result.hasErrors()) {
            List<CartItem> items = cartService.getCartItems(user);
            BigDecimal total = items.stream()
                    .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("user", user);
            model.addAttribute("cartItems", items);
            model.addAttribute("cartTotal", total);
            model.addAttribute("cartCount", items.size());
            model.addAttribute("activePage", "cart");
            return "buyer/checkout";
        }
        try {
            Order order = orderService.checkout(user, checkoutDto);
            ra.addFlashAttribute("successMessage", "Order #" + order.getId() + " placed successfully!");
            return "redirect:/buyer/my-orders";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/buyer/cart";
        }
    }

    /* ── My Orders ──────────────────────────────────────────────── */

    @GetMapping("/my-orders")
    public String myOrders(Authentication auth, Model model) {
        User user = currentUser(auth);
        model.addAttribute("user", user);
        model.addAttribute("orders", orderService.getBuyerOrders(user));
        model.addAttribute("activePage", "my-orders");
        model.addAttribute("cartCount", cartService.getCartCount(user));
        return "buyer/my-orders";
    }

    @GetMapping("/my-orders/{id}")
    public String orderDetail(@PathVariable Long id, Authentication auth, Model model) {
        User user = currentUser(auth);
        try {
            Order order = orderService.getBuyerOrder(user, id);
            model.addAttribute("user", user);
            model.addAttribute("order", order);
            model.addAttribute("activePage", "my-orders");
            model.addAttribute("cartCount", cartService.getCartCount(user));
            return "buyer/order-detail";
        } catch (ResourceNotFoundException | SecurityException e) {
            return "redirect:/buyer/my-orders";
        }
    }

    /* ── Reviews ────────────────────────────────────────────────── */

    @PostMapping("/review/{productId}")
    public String submitReview(@PathVariable Long productId,
                               @Valid @ModelAttribute("reviewDto") ReviewDto dto,
                               BindingResult result,
                               Authentication auth,
                               RedirectAttributes ra) {
        if (result.hasErrors()) {
            result.getAllErrors().forEach(e -> ra.addFlashAttribute("reviewError", e.getDefaultMessage()));
            return "redirect:/products/" + productId;
        }
        try {
            reviewService.submitReview(currentUser(auth), productId, dto);
            ra.addFlashAttribute("reviewSuccess", "Your review was saved!");
        } catch (Exception e) {
            ra.addFlashAttribute("reviewError", e.getMessage());
        }
        return "redirect:/products/" + productId;
    }
}
