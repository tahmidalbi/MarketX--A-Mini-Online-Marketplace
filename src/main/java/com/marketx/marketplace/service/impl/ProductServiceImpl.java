package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.dto.EditProductDto;
import com.marketx.marketplace.dto.ProductDto;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.repository.ProductRepository;
import com.marketx.marketplace.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void addProduct(ProductDto dto, User seller) {
        Product product = Product.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .quantity(dto.getQuantity())
                .imageUrl(dto.getImageUrl())
                .category(dto.getCategory())
                .seller(seller)
                .build();
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void updateProduct(Long id, EditProductDto dto, User seller) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new SecurityException("You do not own this product");
        }
        product.setPrice(dto.getPrice());
        product.setDescription(dto.getDescription());
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id, User seller) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new SecurityException("You do not own this product");
        }
        productRepository.delete(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findBySeller(User seller) {
        return productRepository.findBySellerOrderByCreatedAtDesc(seller);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> search(String query) {
        if (query == null || query.isBlank()) return findAll();
        return productRepository.searchByQuery(query.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> filter(String query, String category) {
        boolean hasQuery    = query    != null && !query.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        if (hasQuery && hasCategory) {
            return productRepository.searchByQuery(query.trim()).stream()
                    .filter(p -> p.getCategory().equals(category))
                    .toList();
        }
        if (hasQuery)    return productRepository.searchByQuery(query.trim());
        if (hasCategory) return productRepository.findByCategoryOrderByCreatedAtDesc(category);
        return productRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }
}
