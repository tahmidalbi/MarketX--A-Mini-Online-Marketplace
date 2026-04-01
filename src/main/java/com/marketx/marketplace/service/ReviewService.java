package com.marketx.marketplace.service;

import com.marketx.marketplace.dto.ReviewDto;
import com.marketx.marketplace.entity.Review;
import com.marketx.marketplace.entity.User;

import java.util.List;

public interface ReviewService {

    /**
     * Submit or update a review. A buyer may only review a product once;
     * submitting again replaces the previous rating/comment.
     */
    Review submitReview(User buyer, Long productId, ReviewDto dto);

    List<Review> getReviewsForProduct(Long productId);

    /** Returns null when there are no reviews yet. */
    Double getAverageStars(Long productId);

    long getReviewCount(Long productId);

    /** Returns the buyer's existing review for this product, or null. */
    Review getExistingReview(User buyer, Long productId);
}
