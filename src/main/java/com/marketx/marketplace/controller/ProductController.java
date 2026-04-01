package com.marketx.marketplace.controller;

import com.marketx.marketplace.dto.ReviewDto;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.exception.ResourceNotFoundException;
import com.marketx.marketplace.security.CustomUserDetails;
import com.marketx.marketplace.service.ProductService;
import com.marketx.marketplace.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ReviewService reviewService;

    @GetMapping("/{id}")
    public String productDetail(@PathVariable Long id, Authentication authentication, Model model) {
        Product product = productService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        model.addAttribute("product", product);
        model.addAttribute("reviews", reviewService.getReviewsForProduct(id));
        model.addAttribute("reviewCount", reviewService.getReviewCount(id));
        Double avg = reviewService.getAverageStars(id);
        model.addAttribute("avgStars", avg != null ? Math.round(avg * 10.0) / 10.0 : null);
        int avgFloor = 0;
        boolean avgHalf = false;
        if (avg != null) {
            int floor = (int) Math.floor(avg);
            double dec = avg - floor;
            avgHalf  = dec >= 0.25 && dec < 0.75;
            avgFloor = dec >= 0.75 ? floor + 1 : floor;
        }
        model.addAttribute("avgStarsFloor", avgFloor);
        model.addAttribute("avgHalfStar", avgHalf);
        model.addAttribute("reviewDto", new ReviewDto());

        if (authentication != null && authentication.isAuthenticated()) {
            User user = ((CustomUserDetails) authentication.getPrincipal()).getUser();
            model.addAttribute("existingReview", reviewService.getExistingReview(user, id));
        }
        return "product-detail";
    }
}
