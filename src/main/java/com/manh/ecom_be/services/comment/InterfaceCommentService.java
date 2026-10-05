package com.manh.ecom_be.services.comment;

import com.manh.ecom_be.dtos.CommentDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.Comment;
import com.manh.ecom_be.responses.comment.CommentResponse;

import java.util.List;

public interface InterfaceCommentService {
    Comment insertComment(CommentDTO comment);

    void deleteComment(Long commentId) throws DataNotFoundException;
    void updateComment(Long id, CommentDTO commentDTO) throws DataNotFoundException;

    List<CommentResponse> getCommentsByUserAndProduct(Long userId, Long productId);
    List<CommentResponse> getCommentsByProduct(Long productId);

    void generateFakeComments() throws Exception;
}
