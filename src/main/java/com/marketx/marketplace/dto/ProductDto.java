package com.marketx.marketplace.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductDto {

    @NotBlank(message = "Product name is required")
    private String name;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be at least $0.01")
    private BigDecimal price;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer quantity;

    private String imageUrl;

    @NotBlank(message = "Category is required")
    private String category;
}
