package com.marketx.marketplace.service.impl;

import com.marketx.marketplace.dto.ReviewDto;
import com.marketx.marketplace.entity.Product;
import com.marketx.marketplace.entity.Review;
import com.marketx.marketplace.entity.User;
import com.marketx.marketplace.exception.ResourceNotFoundException;
import com.marketx.marketplace.repository.ProductRepository;
import com.marketx.marketplace.repository.ReviewRepository;
import com.marketx.marketplace.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public Review submitReview(User buyer, Long productId, ReviewDto dto) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        Review review = reviewRepository.findByBuyerAndProduct(buyer, product)
                .orElse(Review.builder().buyer(buyer).product(product).build());

        review.setStars(dto.getStars());
        review.setComment(dto.getComment().trim());
        return reviewRepository.save(review);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> getReviewsForProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return reviewRepository.findByProductOrderByCreatedAtDesc(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Double getAverageStars(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return reviewRepository.findAverageStarsByProduct(product);
    }

    @Override
    @Transactional(readOnly = true)
    public long getReviewCount(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return reviewRepository.countByProduct(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Review getExistingReview(User buyer, Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return null;
        return reviewRepository.findByBuyerAndProduct(buyer, product).orElse(null);
    }
}
