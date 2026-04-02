package com.marketx.marketplace.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.marketx.marketplace.dto.CheckoutDto;
import com.marketx.marketplace.dto.ProfileUpdateDto;
import com.marketx.marketplace.dto.ReviewDto;
import com.marketx.marketplace.entity.CartItem;
import com.marketx.marketplace.entity.Order;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.exception.ResourceNotFoundException;
import com.marketx.marketplace.exception.UserAlreadyExistsException;
import com.marketx.marketplace.payment.SSLCommerz;
import com.marketx.marketplace.security.CustomUserDetails;
import com.marketx.marketplace.service.CartService;
import com.marketx.marketplace.service.OrderService;
import com.marketx.marketplace.service.ProductService;
import com.marketx.marketplace.service.ReviewService;
import com.marketx.marketplace.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/buyer")
@RequiredArgsConstructor
public class BuyerController {

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${sslcommerz.store-id}")
    private String sslStoreId;

    @Value("${sslcommerz.store-password}")
    private String sslStorePassword;

    @Value("${sslcommerz.sandbox:true}")
    private boolean sslSandbox;

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
    public String submitCheckout(@Valid @ModelAttribute CheckoutDto checkoutDto,
                                 BindingResult result,
                                 Authentication auth,
                                 Model model,
                                 HttpSession session,
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
        session.setAttribute("sslShippingAddress", checkoutDto.getShippingAddress());
        return "redirect:/buyer/payment";
    }

    /* ── Payment ────────────────────────────────────────────────── */

    @GetMapping("/payment")
    public String showPayment(Authentication auth, HttpSession session, Model model) {
        User user = currentUser(auth);
        List<CartItem> items = cartService.getCartItems(user);
        if (items.isEmpty()) return "redirect:/buyer/cart";

        String shippingAddress = (String) session.getAttribute("sslShippingAddress");
        if (shippingAddress == null || shippingAddress.isBlank()) {
            return "redirect:/buyer/checkout";
        }

        BigDecimal total = items.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("user", user);
        model.addAttribute("cartItems", items);
        model.addAttribute("cartTotal", total);
        model.addAttribute("shippingAddress", shippingAddress);
        model.addAttribute("activePage", "cart");
        model.addAttribute("cartCount", items.size());
        return "buyer/payment";
    }

    @PostMapping("/payment")
    public String initiatePayment(Authentication auth, HttpSession session, RedirectAttributes ra) {
        User user = currentUser(auth);
        List<CartItem> items = cartService.getCartItems(user);
        if (items.isEmpty()) return "redirect:/buyer/cart";

        String shippingAddress = (String) session.getAttribute("sslShippingAddress");
        if (shippingAddress == null || shippingAddress.isBlank()) {
            return "redirect:/buyer/checkout";
        }

        BigDecimal total = items.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── FIX: embed userId AND amount in tranId so the success callback
        // can identify the buyer and verify the amount WITHOUT relying on
        // the HTTP session (SSLCommerz posts from its own server — no session).
        // Format: MKT_{userId}_{totalInCents}_{timestamp}
        long totalCents = total.multiply(BigDecimal.valueOf(100)).longValue();
        String tranId = "MKT_" + user.getId() + "_" + totalCents + "_" + System.currentTimeMillis();

        Map<String, String> postData = new HashMap<>();
        postData.put("total_amount", total.toPlainString());
        postData.put("currency", "BDT");
        postData.put("tran_id", tranId);
        postData.put("success_url", baseUrl + "/buyer/payment/success");
        postData.put("fail_url",    baseUrl + "/buyer/payment/fail");
        postData.put("cancel_url",  baseUrl + "/buyer/payment/cancel");
        postData.put("version",     "3.00");
        postData.put("cus_name",    user.getName());
        postData.put("cus_email",   user.getEmail());
        postData.put("cus_add1",    shippingAddress);
        postData.put("cus_city",    "Dhaka");
        postData.put("cus_state",   "Dhaka");
        postData.put("cus_postcode","1000");
        postData.put("cus_country", "Bangladesh");
        postData.put("cus_phone",   "01700000000");

        try {
            SSLCommerz sslcz = new SSLCommerz(sslStoreId, sslStorePassword, sslSandbox);
            String gatewayUrl = sslcz.initiateTransaction(postData, false);

            // Store shipping address keyed by tranId so multiple concurrent
            // orders from the same user don't overwrite each other.
            session.setAttribute("ssl_addr_" + tranId, shippingAddress);
            session.removeAttribute("sslShippingAddress"); // clean up old key

            return "redirect:" + gatewayUrl;
        } catch (Exception e) {
            log.error("SSLCommerz initiation failed: {}", e.getMessage());
            ra.addFlashAttribute("errorMessage", "Payment gateway error: " + e.getMessage());
            return "redirect:/buyer/payment";
        }
    }

    /**
     * SSLCommerz POSTs here after the buyer pays.
     *
     * WHY this method has no Authentication parameter:
     * This POST comes from SSLCommerz's servers, not from the buyer's browser.
     * It carries NO session cookie, so Spring Security cannot identify the user.
     * That is why the URL is in permitAll() in SecurityConfig.
     * We identify everything we need from tran_id in the POST body instead.
     */
    @PostMapping("/payment/success")
    public String paymentSuccess(HttpServletRequest request, Model model) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v[0]));

        String tranId = params.get("tran_id");
        log.info("SSLCommerz SUCCESS callback received. tran_id={}", tranId);

        if (tranId == null || !tranId.startsWith("MKT_")) {
            log.warn("Invalid tran_id in success callback: {}", tranId);
            model.addAttribute("errorMessage", "Invalid payment response. Please contact support.");
            return "buyer/payment-result";
        }

        // Parse userId and amount from tranId: MKT_{userId}_{totalCents}_{timestamp}
        String[] parts = tranId.split("_");
        // parts[0]="MKT", parts[1]=userId, parts[2]=totalCents, parts[3]=timestamp
        if (parts.length != 4) {
            model.addAttribute("errorMessage", "Malformed transaction ID. Please contact support.");
            return "buyer/payment-result";
        }

        long userId;
        String amount;
        try {
            userId = Long.parseLong(parts[1]);
            long cents = Long.parseLong(parts[2]);
            amount = BigDecimal.valueOf(cents)
                               .divide(BigDecimal.valueOf(100))
                               .setScale(2)
                               .toPlainString();
        } catch (NumberFormatException e) {
            model.addAttribute("errorMessage", "Malformed transaction ID. Please contact support.");
            return "buyer/payment-result";
        }

        // Look up the user by ID — no session needed
        User user = userService.findById(userId).orElse(null);
        if (user == null) {
            log.error("User not found for userId={} from tran_id={}", userId, tranId);
            model.addAttribute("errorMessage", "User account not found. Please contact support.");
            return "buyer/payment-result";
        }

        // Validate with SSLCommerz
        try {
            SSLCommerz sslcz = new SSLCommerz(sslStoreId, sslStorePassword, sslSandbox);
            boolean valid = sslcz.orderValidate(tranId, amount, "BDT", params);

            if (valid) {
                // Retrieve shipping address stored when payment was initiated.
                // The session may not be available here (server-to-server POST),
                // so fall back to the address in the SSLCommerz POST params.
                String shippingAddress = params.getOrDefault("cus_add1", "");

                CheckoutDto checkoutDto = new CheckoutDto();
                checkoutDto.setShippingAddress(shippingAddress);
                Order order = orderService.checkout(user, checkoutDto);

                log.info("Order #{} placed for userId={} via SSLCommerz tran_id={}", order.getId(), userId, tranId);
                model.addAttribute("successMessage",
                        "Payment successful! Order #" + order.getId() + " has been placed.");
                model.addAttribute("orderId", order.getId());
            } else {
                log.warn("SSLCommerz validation returned false for tran_id={}", tranId);
                model.addAttribute("errorMessage",
                        "Payment could not be verified. If money was deducted, please contact support with transaction ID: " + tranId);
            }
        } catch (Exception e) {
            log.error("Error processing payment success for tran_id={}: {}", tranId, e.getMessage());
            model.addAttribute("errorMessage", "Payment error: " + e.getMessage());
        }

        return "buyer/payment-result";
    }

    @PostMapping("/payment/fail")
    public String paymentFail(HttpServletRequest request, Model model) {
        String tranId = request.getParameter("tran_id");
        log.warn("SSLCommerz FAIL callback. tran_id={}", tranId);
        model.addAttribute("errorMessage", "Payment failed. Please try again.");
        return "buyer/payment-result";
    }

    @PostMapping("/payment/cancel")
    public String paymentCancel(HttpServletRequest request, Model model) {
        String tranId = request.getParameter("tran_id");
        log.info("SSLCommerz CANCEL callback. tran_id={}", tranId);
        model.addAttribute("errorMessage", "Payment was cancelled.");
        return "buyer/payment-result";
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
