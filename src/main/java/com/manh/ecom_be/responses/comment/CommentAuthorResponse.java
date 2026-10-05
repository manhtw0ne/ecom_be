package com.manh.ecom_be.responses.comment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.manh.ecom_be.models.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Only public attribution belongs in a product comment response. */
@Getter
@AllArgsConstructor
public class CommentAuthorResponse {
    private Long id;
    @JsonProperty("fullname") private String fullName;
    @JsonProperty("profile_image") private String profileImage;

    public static CommentAuthorResponse fromUser(User user) {
        return new CommentAuthorResponse(user.getId(), user.getFullName(), user.getProfileImage());
    }
}