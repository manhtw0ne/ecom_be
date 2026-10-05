package com.manh.ecom_be.responses.product;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.manh.ecom_be.models.Comment;
import com.manh.ecom_be.models.Favorite;
import com.manh.ecom_be.models.Product;
import com.manh.ecom_be.models.ProductImage;
import com.manh.ecom_be.responses.BaseResponse;
import com.manh.ecom_be.responses.comment.CommentResponse;
import com.manh.ecom_be.responses.favorite.FavoriteResponse;
import lombok.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductResponse extends BaseResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    @JsonProperty("stock_quantity")
    private int stockQuantity;
    private String thumbnail;
    private String description;

    private int totalPages;

    @JsonProperty("product_images")
    private List<ProductImage> productImages = new ArrayList<>();

    @JsonProperty("comments")
    private List<CommentResponse> comments = new ArrayList<>();

    @JsonProperty("favorites")
    private List<FavoriteResponse> favorites = new ArrayList<>();

    @JsonProperty("category_id")
    private Long categoryId;
    public static ProductResponse fromProduct(Product product) {
        List<Comment> comments = product.getComments()
                .stream()
                .sorted(Comparator.comparing(Comment::getCreatedAt).reversed())
                .collect(Collectors.toList());

        List<Favorite> favorites = product.getFavorites();
        ProductResponse productResponse = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .thumbnail(product.getThumbnail())
                .comments(comments.stream().map(CommentResponse::fromComment).toList()) // Collect sorted comments into a list
                .favorites(favorites.stream().map(FavoriteResponse::fromFavorite).toList())
                .description(product.getDescription())
                .categoryId(product.getCategory().getId())
                .productImages(product.getProductImages())
                .totalPages(0)
                .build();
        productResponse.setCreatedAt(product.getCreatedAt());
        productResponse.setUpdatedAt(product.getUpdatedAt());

        return productResponse;
    }
}

