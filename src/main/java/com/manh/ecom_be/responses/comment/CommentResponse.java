package com.manh.ecom_be.responses.comment;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.manh.ecom_be.models.Comment;
import com.manh.ecom_be.responses.BaseResponse;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentResponse extends BaseResponse {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("content")
    private String content;

    @JsonProperty("user")
    private CommentAuthorResponse user;


    @JsonProperty("product_id")
    private Long productId;


    public static CommentResponse fromComment(Comment comment) {
        CommentAuthorResponse userResponse = CommentAuthorResponse.fromUser(comment.getUser());
        CommentResponse result = CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .user(userResponse)
                .productId(comment.getProduct().getId())
                .build();

        return result;
    }
}
