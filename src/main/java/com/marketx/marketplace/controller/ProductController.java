package com.marketx.marketplace.controller;

import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.exception.ResourceNotFoundException;
import com.marketx.marketplace.service.ProductService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        Product product = productService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        model.addAttribute("product", product);
        return "product-detail";
    }
}
