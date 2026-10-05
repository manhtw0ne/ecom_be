package com.manh.ecom_be.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manh.ecom_be.dtos.UpdateUserDTO;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.services.comment.CommentService;
import com.manh.ecom_be.services.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileCommentAuthorizationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired CommentRepository comments;
    @Autowired UserService userService;
    @Autowired CommentService commentService;
    User owner, stranger, admin;
    Product product;
    Comment comment;
    Role buyerRole;

    @BeforeEach void seed() {
        buyerRole = roles.save(Role.builder().name("USER").build());
        owner = account("owner", buyerRole);
        stranger = account("stranger", buyerRole);
        admin = account("admin", roles.save(Role.builder().name("ADMIN").build()));
        var category = categories.save(Category.builder().name("Privacy test").build());
        product = products.save(Product.builder().name("Test product").price(BigDecimal.TEN)
                .category(category).stockQuantity(5).build());
        comment = comments.save(Comment.builder().user(owner).product(product).content("Original").build());
    }

    private User account(String name, Role role) {
        return users.save(User.builder().email(name + System.nanoTime() + "@example.test")
                .fullName(name).phoneNumber("0901234567").address("Private address")
                .googleAccountId("verified-" + name + System.nanoTime())
                .password("hash").active(true).role(role).build());
    }

    private String commentBody(Long userId) throws Exception {
        return json.writeValueAsString(Map.of("user_id", userId, "product_id", product.getId(), "content", "Edited"));
    }

    @Test void forgedBodyOwnerCannotEditSomeoneElsesComment() throws Exception {
        mvc.perform(put("/api/v1/comments/" + comment.getId()).with(user(stranger))
                .contentType("application/json").content(commentBody(stranger.getId())))
                .andExpect(status().isForbidden());
        assertThat(comments.findById(comment.getId()).orElseThrow().getContent()).isEqualTo("Original");
    }

    @Test void ownerCanEditAndAdminCanModerate() throws Exception {
        for (User actor : new User[]{owner, admin}) {
            mvc.perform(put("/api/v1/comments/" + comment.getId()).with(user(actor))
                    .contentType("application/json").content(commentBody(stranger.getId())))
                    .andExpect(status().isOk());
        }
        var stored = comments.findById(comment.getId()).orElseThrow();
        assertThat(stored.getContent()).isEqualTo("Edited");
        assertThat(stored.getUser().getId()).isEqualTo(owner.getId());
    }

    @Test void createUsesAuthenticatedAuthorInsteadOfClientUserId() throws Exception {
        mvc.perform(post("/api/v1/comments").with(user(stranger)).contentType("application/json")
                .content(commentBody(owner.getId()))).andExpect(status().isOk());
        var written = comments.findByUserIdAndProductId(stranger.getId(), product.getId());
        assertThat(written).hasSize(1);
        assertThat(written.getFirst().getUser().getId()).isEqualTo(stranger.getId());
    }

    @Test void commentResponseDoesNotExposePrivateProfileFields() throws Exception {
        mvc.perform(get("/api/v1/comments").param("product_id", product.getId().toString()).with(user(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].user.id").value(owner.getId()))
                .andExpect(jsonPath("$.data[0].user.fullname").value("owner"))
                .andExpect(jsonPath("$.data[0].user.phone_number").doesNotExist())
                .andExpect(jsonPath("$.data[0].user.address").doesNotExist())
                .andExpect(jsonPath("$.data[0].user.role").doesNotExist())
                .andExpect(jsonPath("$.data[0].user.google_account_id").doesNotExist())
                .andExpect(jsonPath("$.data[0].user.date_of_birth").doesNotExist());
    }

    @Test void anonymousCannotWriteOrUpdateProfile() throws Exception {
        mvc.perform(post("/api/v1/comments").contentType("application/json").content(commentBody(owner.getId())))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/details/" + owner.getId()).contentType("application/json")
                .content("{\"fullname\":\"Changed\"}")).andExpect(status().isUnauthorized());
    }

    @Test void profileOwnerCanUpdateButOthersIncludingAdminCannotImpersonate() throws Exception {
        for (User actor : new User[]{stranger, admin}) {
            mvc.perform(put("/api/v1/users/details/" + owner.getId()).with(user(actor))
                    .header("Authorization", "Bearer unused-by-preauthenticated-test")
                    .contentType("application/json").content("{\"fullname\":\"Forged\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/v1/users/details/" + owner.getId()).with(user(owner))
                .header("Authorization", "Bearer unused-by-preauthenticated-test")
                .contentType("application/json").content("{\"fullname\":\"Updated\",\"role_id\":" + admin.getRole().getId() + "}"))
                .andExpect(status().isOk());
        var stored = users.findById(owner.getId()).orElseThrow();
        assertThat(stored.getFullName()).isEqualTo("Updated");
        assertThat(stored.getRole().getName()).isEqualTo("USER");
    }

    @ParameterizedTest @ValueSource(strings = {"google_account_id", "facebook_account_id"})
    void profileCannotLinkUnverifiedSocialIdentity(String field) throws Exception {
        String original = owner.getGoogleAccountId();
        mvc.perform(put("/api/v1/users/details/" + owner.getId()).with(user(owner))
                .header("Authorization", "Bearer unused-by-preauthenticated-test")
                .contentType("application/json").content(json.writeValueAsString(Map.of(field, "forged"))))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(owner.getId()).orElseThrow().getGoogleAccountId()).isEqualTo(original);
    }

    @ParameterizedTest @ValueSource(strings = {"google_account_id", "facebook_account_id", "is_social_login"})
    void registrationCannotBypassHashingOrClaimSocialIdentity(String field) throws Exception {
        long before = users.count();
        var body = new HashMap<String, Object>();
        body.put("email", "register" + System.nanoTime() + "@example.test");
        body.put("password", "a-test-password"); body.put("retype_password", "a-test-password");
        body.put("role_id", buyerRole.getId());
        body.put(field, field.equals("is_social_login") ? true : "forged");
        mvc.perform(post("/api/v1/users/register").contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(before);
    }

    @Test void missingCommentReturns404() throws Exception {
        mvc.perform(put("/api/v1/comments/" + Long.MAX_VALUE).with(user(owner))
                .contentType("application/json").content(commentBody(owner.getId())))
                .andExpect(status().isNotFound());
    }

    @Test void directServiceCallsCannotBypassOwnershipOrAdministrativeRoles() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(stranger, null, stranger.getAuthorities()));
        try {
            assertThatThrownBy(() -> userService.updateUser(owner.getId(), new UpdateUserDTO()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> userService.changeProfileImage(owner.getId(), "avatar.png"))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> userService.blockOrEnable(owner.getId(), false))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> userService.resetPassword(owner.getId(), "forged"))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> commentService.deleteComment(comment.getId()))
                    .isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }
}