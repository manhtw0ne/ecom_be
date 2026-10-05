package com.manh.ecom_be.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshTokenDTO {
    @com.fasterxml.jackson.annotation.JsonAlias("refresh_token")
    @jakarta.validation.constraints.Size(max = 255)
    @NotBlank
    private String refreshToken;
}
