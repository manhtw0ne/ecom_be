package com.manh.ecom_be.services.comment;
import com.manh.ecom_be.dtos.CommentDTO;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    @Mock CommentRepository comments;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock com.manh.ecom_be.components.SecurityUtils securityUtils;
    @InjectMocks CommentService service;
    @Test void insertLinksExistingBuyerAndProduct() {
        var user=User.builder().id(1L).build(); var product=Product.builder().id(2L).build();
        when(securityUtils.requireUser()).thenReturn(user);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(products.findById(2L)).thenReturn(Optional.of(product));
        when(comments.save(any())).thenAnswer(i->i.getArgument(0));
        var result=service.insertComment(new CommentDTO(1L,2L,"Useful product"));
        assertThat(result.getUser()).isSameAs(user); assertThat(result.getProduct()).isSameAs(product);
        assertThat(result.getContent()).isEqualTo("Useful product");
    }
    @Test void missingBuyerOrProductDoesNotSave() {
        when(securityUtils.requireUser()).thenReturn(User.builder().id(1L).build());
        assertThatThrownBy(()->service.insertComment(new CommentDTO(1L,2L,"text"))).isInstanceOf(IllegalArgumentException.class);
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        assertThatThrownBy(()->service.insertComment(new CommentDTO(1L,2L,"text"))).isInstanceOf(IllegalArgumentException.class);
        verify(comments,never()).save(any());
    }
    @Test void updateChangesTextWithoutChangingOwnership() throws Exception {
        var owner=new User(); var product=new Product();
        var comment=Comment.builder().user(owner).product(product).content("old").build();
        when(comments.findById(3L)).thenReturn(Optional.of(comment));
        service.updateComment(3L,new CommentDTO(99L,99L,"edited"));
        assertThat(comment.getContent()).isEqualTo("edited"); assertThat(comment.getUser()).isSameAs(owner);
        assertThat(comment.getProduct()).isSameAs(product); verify(comments).save(comment);
    }
    @Test void unknownCommentCannotBeUpdated() {
        assertThatThrownBy(()->service.updateComment(3L,new CommentDTO())).isInstanceOf(DataNotFoundException.class);
        verify(comments,never()).save(any());
    }
    @Test void listsExposeBuyerAndProductInformation() {
        var comment=Comment.builder().id(3L).content("review")
                .user(User.builder().id(1L).fullName("Buyer").build()).product(Product.builder().id(2L).build()).build();
        when(comments.findByProductId(2L)).thenReturn(List.of(comment));
        when(comments.findByUserIdAndProductId(1L,2L)).thenReturn(List.of(comment));
        var all=service.getCommentsByProduct(2L); var mine=service.getCommentsByUserAndProduct(1L,2L);
        assertThat(all).hasSize(1); assertThat(mine).hasSize(1);
        assertThat(all.getFirst().getProductId()).isEqualTo(2L);
        assertThat(mine.getFirst().getContent()).isEqualTo("review");
        assertThat(mine.getFirst().getUser().getFullName()).isEqualTo("Buyer");
    }
    @Test void deleteTargetsRequestedComment() throws Exception {
        var owner = User.builder().id(1L).build();
        when(comments.findById(3L)).thenReturn(Optional.of(Comment.builder().user(owner).build()));
        service.deleteComment(3L); verify(comments).deleteById(3L);
        verify(securityUtils).requireOwnerOrAdmin(1L);
    }
}