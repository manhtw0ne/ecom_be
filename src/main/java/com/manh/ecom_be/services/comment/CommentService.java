package com.manh.ecom_be.services.comment;

import com.github.javafaker.Faker;
import com.manh.ecom_be.dtos.CommentDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.Comment;
import com.manh.ecom_be.models.Product;
import com.manh.ecom_be.models.User;
import com.manh.ecom_be.repositories.CommentRepository;
import com.manh.ecom_be.repositories.ProductRepository;
import com.manh.ecom_be.repositories.UserRepository;
import com.manh.ecom_be.responses.comment.CommentResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class CommentService implements InterfaceCommentService {
    private static final Logger logger = LoggerFactory.getLogger(CommentService.class);
    private final com.manh.ecom_be.components.SecurityUtils securityUtils;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public Comment insertComment(CommentDTO commentDTO) {
        User actor = securityUtils.requireUser();
        User user = userRepository.findById(actor.getId()).orElse(null);
        Product product = productRepository.findById(commentDTO.getProductId()).orElse(null);
        if (user == null || product == null) {
            throw new IllegalArgumentException("User or product not found");
        }
        Comment newComment = Comment.builder()
                .user(user)
                .product(product)
                .content(commentDTO.getContent())
                .build();
        return commentRepository.save(newComment);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) throws DataNotFoundException {
        securityUtils.requireUser();
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new DataNotFoundException("Comment not found"));
        securityUtils.requireOwnerOrAdmin(comment.getUser().getId());

        commentRepository.deleteById(commentId);
    }

    @Override
    @Transactional
    public void updateComment(Long id, CommentDTO commentDTO) throws DataNotFoundException {
        securityUtils.requireUser();
        Comment existingComment = commentRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Comment not found"));
        securityUtils.requireOwnerOrAdmin(existingComment.getUser().getId());
        existingComment.setContent(commentDTO.getContent());
        commentRepository.save(existingComment);
    }

    @Override
    public List<CommentResponse> getCommentsByUserAndProduct(Long userId, Long productId) {
        List<Comment> comments = commentRepository.findByUserIdAndProductId(userId, productId);
        return comments
                .stream()
                .map(comment -> CommentResponse.fromComment(comment))
                .collect(Collectors.toList());
    }

    @Override
    public List<CommentResponse> getCommentsByProduct(Long productId) {
        List<Comment> comments = commentRepository.findByProductId(productId);
        return comments
                .stream()
                .map(comment -> CommentResponse.fromComment(comment))
                .collect(Collectors.toList());
    }

    @Override
    public void generateFakeComments() throws Exception {
        securityUtils.requireAdmin();

        Faker faker = new Faker();
        Random random = new Random();
        List<User> users = userRepository.findAll();
        List<Product> products = productRepository.findAll();
        List<Comment> comments = new ArrayList<>();

        final int totalRecords = 10_000;
        final int batchSize = 1_000;

        for (int i = 0; i < totalRecords; i++) {
            User user = users.get(random.nextInt(users.size()));
            Product product = products.get(random.nextInt(products.size()));

            Comment comment = Comment.builder()
                    .content(faker.lorem().sentence())
                    .product(product)
                    .user(user)
                    .build();

            // Random created_at từ 2015 đến hiện tại
            LocalDateTime startDate = LocalDateTime.of(2015, 1, 1, 0, 0);
            LocalDateTime endDate = LocalDateTime.now();
            long randomEpoch = ThreadLocalRandom.current()
                    .nextLong(startDate.toEpochSecond(ZoneOffset.UTC),
                            endDate.toEpochSecond(ZoneOffset.UTC));
            comment.setCreatedAt(LocalDateTime.ofEpochSecond(randomEpoch, 0, ZoneOffset.UTC));

            comments.add(comment);
            if (comments.size() >= batchSize) {
                commentRepository.saveAll(comments);
                comments.clear();
            }
        }
    }
}
