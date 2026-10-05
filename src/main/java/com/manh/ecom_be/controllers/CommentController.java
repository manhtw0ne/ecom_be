package com.manh.ecom_be.controllers;


import com.manh.ecom_be.components.SecurityUtils;
import com.manh.ecom_be.dtos.CommentDTO;
import com.manh.ecom_be.models.User;
import com.manh.ecom_be.responses.ApiResponse;
import com.manh.ecom_be.responses.comment.CommentResponse;
import com.manh.ecom_be.services.comment.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("${api.prefix}/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;
    private final SecurityUtils securityUtils;

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getAllComments(
            @RequestParam(value = "user_id", required = false) Long userId,
            @RequestParam("product_id") Long productId
    ) {
        List<CommentResponse> commentResponses;

        if (userId == null) {
            commentResponses = commentService.getCommentsByProduct(productId);
        } else {
            commentResponses = commentService.getCommentsByUserAndProduct(userId, productId);
        }

        return ResponseEntity.ok(ApiResponse.success(commentResponses, "Get comments successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<?>> updateComment(
            @PathVariable("id") Long commentId,
            @Valid @RequestBody CommentDTO commentDTO
    ) throws Exception {


        commentService.updateComment(commentId, commentDTO);
        return ResponseEntity.ok(ApiResponse.success(null, "Update comment successfully"));
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<?>> insertComment(
            @Valid @RequestBody CommentDTO commentDTO
    ) {

        commentService.insertComment(commentDTO);
        return ResponseEntity.ok(ApiResponse.success(null, "Insert comment successfully"));
    }

    @PostMapping("/generateFakeComments")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<?>> generateFakeComments() throws Exception {
        commentService.generateFakeComments();
        return ResponseEntity.ok(ApiResponse.success(null, "Insert fake comments successfully"));
    }
}
