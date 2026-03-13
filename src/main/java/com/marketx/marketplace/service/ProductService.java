package com.marketx.marketplace.service;

import com.marketx.marketplace.dto.EditProductDto;
import com.marketx.marketplace.dto.ProductDto;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;

import java.util.List;
import java.util.Optional;

public interface ProductService {

    void addProduct(ProductDto dto, User seller);

    void updateProduct(Long id, EditProductDto dto, User seller);

    void deleteProduct(Long id, User seller);

    List<Product> findBySeller(User seller);

    List<Product> findAll();

    Optional<Product> findById(Long id);
}
