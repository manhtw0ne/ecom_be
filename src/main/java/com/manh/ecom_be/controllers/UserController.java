package com.manh.ecom_be.controllers;

import com.manh.ecom_be.components.LocalizationUtils;
import com.manh.ecom_be.components.SecurityUtils;
import com.manh.ecom_be.dtos.RefreshTokenDTO;
import com.manh.ecom_be.dtos.UpdateUserDTO;
import com.manh.ecom_be.dtos.UserDTO;
import com.manh.ecom_be.dtos.UserLoginDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.exceptions.InvalidPasswordException;
import com.manh.ecom_be.models.Token;
import com.manh.ecom_be.models.User;
import com.manh.ecom_be.responses.user.LoginResponse;
import com.manh.ecom_be.responses.ApiResponse;
import com.manh.ecom_be.responses.user.UserListResponse;
import com.manh.ecom_be.responses.user.UserResponse;
import com.manh.ecom_be.services.auth.InterfaceAuthService;
import com.manh.ecom_be.services.token.InterfaceTokenService;
import com.manh.ecom_be.services.user.InterfaceUserService;
import com.manh.ecom_be.utils.FileUtils;
import com.manh.ecom_be.utils.MessageKeys;
import com.manh.ecom_be.utils.ValidationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("${api.prefix}/users")
@RequiredArgsConstructor
public class UserController {
    private final InterfaceUserService userService;
    private final LocalizationUtils localizationUtils;
    private final InterfaceTokenService tokenService;
    private final InterfaceAuthService authService;
    private final SecurityUtils securityUtils;
    private final com.manh.ecom_be.services.user.ProfileImageService profileImageService;

    private String facebookClientSecret;

    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserListResponse>> getAllUser(
            @RequestParam(defaultValue = "", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit
    ) throws Exception {
        PageRequest pageRequest = PageRequest.of(
                page, limit,
                Sort.by("id").ascending()
        );
        Page<UserResponse> userPage = userService.findAll(keyword, pageRequest)
                .map(UserResponse::fromUser);

        int totalPages = userPage.getTotalPages();
        List<UserResponse> userResponses = userPage.getContent();
        UserListResponse userListResponse = UserListResponse
                .builder()
                .users(userResponses)
                .totalPages(totalPages)
                .build();

        return ResponseEntity.ok(ApiResponse.success(userListResponse, "Get user list successfully"));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> createUser(
            @Valid @RequestBody UserDTO userDTO
    ) throws Exception {
        if (userDTO.getEmail() == null || userDTO.getEmail().trim().isBlank()) {
            if (userDTO.getPhoneNumber() == null || userDTO.getPhoneNumber().isBlank()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error(HttpStatus.BAD_REQUEST, "At least email or phone number is required"));
            } else {
                if (!ValidationUtils.isValidPhoneNumber(userDTO.getPhoneNumber())) {
                    throw new Exception("Invalid phone number");
                }
            }
        } else {
            if (!ValidationUtils.isValidEmail(userDTO.getEmail())) {
                throw new Exception("Invalid email format");
            }
        }

        if (!userDTO.getPassword().equals(userDTO.getRetypePassword())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(HttpStatus.BAD_REQUEST,
                            localizationUtils.getLocalizedMessage(MessageKeys.PASSWORD_NOT_MATCH)));
        }

        User user = userService.createUser(userDTO);
        return ResponseEntity.ok(ApiResponse.created(UserResponse.fromUser(user), "Account registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody UserLoginDTO userLoginDTO,
            HttpServletRequest request
    ) throws Exception {
        String token = userService.login(userLoginDTO);
        String userAgent = request.getHeader("User-Agent");
        User userDetail = userService.getUserDetailsFromToken(token);
        Token jwtToken = tokenService.addToken(userDetail, token, isMobileDevice(userAgent));

        LoginResponse loginResponse = LoginResponse.builder()
                .message(localizationUtils.getLocalizedMessage(MessageKeys.LOGIN_SUCCESSFULLY))
                .token(jwtToken.getToken())
                .tokenType(jwtToken.getTokenType())
                .refreshToken(jwtToken.getRefreshToken())
                .username(userDetail.getUsername())
                .roles(userDetail.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .id(userDetail.getId())
                .build();

        return ResponseEntity.ok(ApiResponse.success(loginResponse, "Login successfully"));
    }

    // private - Ä‘Æ°á»£c gá»i ná»™i bá»™ tá»« callback()
    private ResponseEntity<ApiResponse<LoginResponse>> loginSocial(
            @Valid @RequestBody UserLoginDTO userLoginDTO,
            HttpServletRequest request
    ) throws Exception {
        String token = userService.loginSocial(userLoginDTO);
        String userAgent = request.getHeader("User-Agent");
        User userDetail = userService.getUserDetailsFromToken(token);
        Token jwtToken = tokenService.addToken(userDetail, token, isMobileDevice(userAgent));

        LoginResponse loginResponse = LoginResponse.builder()
                .message(localizationUtils.getLocalizedMessage(MessageKeys.LOGIN_SUCCESSFULLY))
                .token(jwtToken.getToken())
                .tokenType(jwtToken.getTokenType())
                .refreshToken(jwtToken.getRefreshToken())
                .username(userDetail.getUsername())
                .roles(userDetail.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .id(userDetail.getId())
                .build();

        return ResponseEntity.ok(ApiResponse.success(loginResponse, "Login successfully"));
    }

    private boolean isMobileDevice(String userAgent) {
        return userAgent != null && userAgent.toLowerCase().contains("mobile");
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenDTO refreshTokenDTO) throws Exception {
        Token jwtToken = tokenService.refreshToken(refreshTokenDTO.getRefreshToken());
        User userDetail = jwtToken.getUser();

        LoginResponse loginResponse = LoginResponse.builder()
                .token(jwtToken.getToken())
                .tokenType(jwtToken.getTokenType())
                .refreshToken(jwtToken.getRefreshToken())
                .username(userDetail.getUsername())
                .roles(userDetail.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .id(userDetail.getId())
                .build();

        return ResponseEntity.ok(ApiResponse.success(loginResponse, "Refresh token successfully"));
    }

    @PostMapping("/details")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_USER')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserDetails(
            @RequestHeader("Authorization") String authorizationHeader
    ) throws Exception {
        String extractedToken = authorizationHeader.substring(7);
        User user = userService.getUserDetailsFromToken(extractedToken);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.fromUser(user), "Get user's detail successfully"));
    }

    @PutMapping("/details/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_USER')")
    @Operation(security = { @SecurityRequirement(name = "bearer-key") })
    public ResponseEntity<ApiResponse<?>> updateUserDetails(
            @PathVariable Long userId,
            @RequestBody UpdateUserDTO updatedUserDTO,
            @RequestHeader("Authorization") String authorizationHeader
    ) throws Exception {
        securityUtils.requireSelf(userId);
        User updatedUser = userService.updateUser(userId, updatedUserDTO);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.fromUser(updatedUser), "Update user detail successfully"));
    }

    @PutMapping("/reset-password/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<?>> resetPassword(@Valid @PathVariable long userId) {
        try {
            String newPassword = UUID.randomUUID().toString().substring(0, 5);
            userService.resetPassword(userId, newPassword);
            return ResponseEntity.ok(ApiResponse.success(newPassword, "Reset password successfully"));
        } catch (InvalidPasswordException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(HttpStatus.BAD_REQUEST, "Invalid password"));
        } catch (DataNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(HttpStatus.NOT_FOUND, "User not found"));
        }
    }

    @PutMapping("/block/{userId}/{active}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<?>> blockOrEnable(
            @Valid @PathVariable long userId,
            @Valid @PathVariable int active
    ) throws Exception {
        userService.blockOrEnable(userId, active > 0);
        String message = active > 0 ? "Successfully enabled the user." : "Successfully blocked the user.";
        return ResponseEntity.ok(ApiResponse.success(null, message));
    }

    @PostMapping(value = "/upload-profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<?>> uploadProfileImage(
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        securityUtils.requireUser();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(HttpStatus.BAD_REQUEST, "Image file is required."));
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error(HttpStatus.PAYLOAD_TOO_LARGE,
                            "Image file size exceeds the allowed limit of 10MB."));
        }
        String imageName = profileImageService.replace(file);

        return ResponseEntity.ok(ApiResponse.created(imageName, "Upload profile image successfully"));
    }

    @GetMapping("/profile-images/{imageName}")
    public ResponseEntity<?> viewImage(@PathVariable String imageName) {
        return FileUtils.imageResponse(imageName);
    }
    @GetMapping("/auth/social-login")
    public ResponseEntity<String> socialAuth(
            @RequestParam("login_type") String loginType,
            HttpServletRequest request
    ) {
        loginType = loginType.trim().toLowerCase();
        String url = authService.generateAuthUrl(loginType);
        return ResponseEntity.ok(url);
    }

    @GetMapping("/auth/social/callback")
    public ResponseEntity<ApiResponse<LoginResponse>> callback(
            @RequestParam("code") String code,
            @RequestParam("login_type") String loginType,
            HttpServletRequest request
    ) throws Exception {
        Map<String, Object> userInfo = authService.authenticateAndFetchProfile(code, loginType);
        if (userInfo == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(HttpStatus.BAD_REQUEST, "Failed to authenticate"));
        }

        String accountId = "";
        String name = "";
        String picture = "";
        String email = "";

        if (loginType.trim().equals("google")) {
            accountId = (String) Objects.requireNonNullElse(userInfo.get("sub"), "");
            name = (String) Objects.requireNonNullElse(userInfo.get("name"), "");
            picture = (String) Objects.requireNonNullElse(userInfo.get("picture"), "");
            email = (String) Objects.requireNonNullElse(userInfo.get("email"), "");
        } else if (loginType.trim().equals("facebook")) {
            accountId = (String) Objects.requireNonNullElse(userInfo.get("id"), "");
            name = (String) Objects.requireNonNullElse(userInfo.get("name"), "");
            email = (String) Objects.requireNonNullElse(userInfo.get("email"), "");
            Object pictureObj = userInfo.get("picture");
            if (pictureObj instanceof Map) {
                Map<?, ?> pictureData = (Map<?, ?>) pictureObj;
                Object dataObj = pictureData.get("data");
                if (dataObj instanceof Map) {
                    Map<?, ?> dataMap = (Map<?, ?>) dataObj;
                    Object urlObj = dataMap.get("url");
                    if (urlObj instanceof String) {
                        picture = (String) urlObj;
                    }
                }
            }
        }

        UserLoginDTO userLoginDTO = UserLoginDTO.builder()
                .email(email)
                .fullName(name)
                .password("")
                .phoneNumber("")
                .profileImage(picture)
                .build();

        if (loginType.trim().equals("google")) {
            userLoginDTO.setGoogleAccountId(accountId);
        } else if (loginType.trim().equals("facebook")) {
            userLoginDTO.setFacebookAccountId(accountId);
        }

        return this.loginSocial(userLoginDTO, request);
    }
}

