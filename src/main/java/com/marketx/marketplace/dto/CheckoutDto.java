package com.marketx.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutDto {

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;
}
