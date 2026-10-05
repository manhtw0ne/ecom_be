package com.manh.ecom_be.services.token;


import com.manh.ecom_be.components.JwtTokenUtils;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.exceptions.ExpiredTokenException;
import com.manh.ecom_be.models.Token;
import com.manh.ecom_be.models.User;
import com.manh.ecom_be.repositories.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class TokenService implements InterfaceTokenService {
    private static final int MAX_TOKENS = 3;

    @Value("${jwt.expiration}")
    private int expiration;

    @Value("${jwt.expiration-refresh-token}")
    private int expirationRefreshToken;

    private final TokenRepository tokenRepository;
    private final com.manh.ecom_be.repositories.UserRepository userRepository;
    private final JwtTokenUtils jwtTokenUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Token refreshToken(String refreshToken) throws Exception {
        if (refreshToken == null || refreshToken.isBlank()) throw invalidRefresh();
        // All session mutations lock user before token, including block/password changes.
        Long ownerId = tokenRepository.findOwnerIdByRefreshToken(refreshToken)
                .orElseThrow(this::invalidRefresh);
        User owner = userRepository.findByIdForUpdate(ownerId).orElseThrow(this::invalidRefresh);
        Token existingToken = tokenRepository.findByRefreshTokenForUpdate(refreshToken)
                .orElseThrow(this::invalidRefresh);
        LocalDateTime now = LocalDateTime.now();
        if (!owner.isActive() || owner.isDeleted() || existingToken.isRevoked() || existingToken.isExpired()
                || existingToken.getUser() == null || !ownerId.equals(existingToken.getUser().getId())
                || existingToken.getRefreshExpirationDate() == null
                || !existingToken.getRefreshExpirationDate().isAfter(now)) {
            throw invalidRefresh();
        }
        existingToken.setToken(jwtTokenUtils.generateToken(owner));
        existingToken.setExpirationDate(now.plusSeconds(expiration));
        existingToken.setRefreshToken(UUID.randomUUID().toString());
        // Absolute session lifetime: rotating does not extend the original refresh deadline.
        return tokenRepository.saveAndFlush(existingToken);
    }

    private org.springframework.security.authentication.BadCredentialsException invalidRefresh() {
        return new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token");
    }
    @Override
    @Transactional
    public Token addToken(User user, String token, boolean isMobileDevice) {
        user = userRepository.findByIdForUpdate(user.getId()).orElseThrow(this::invalidRefresh);
        if (!user.isActive() || user.isDeleted()) throw invalidRefresh();
        List<Token> userTokens = tokenRepository.findByUser(user);
        int tokenCount = userTokens.size();

        if (tokenCount >= MAX_TOKENS) {
            boolean hasNonMobileToken = !userTokens.stream().allMatch(Token::isMobile);
            Token tokenToDelete;
            if (hasNonMobileToken) {
                tokenToDelete = userTokens.stream()
                        .filter(userToken -> !userToken.isMobile())
                        .findFirst()
                        .orElse(userTokens.get(0));
            } else {
                tokenToDelete = userTokens.get(0);
            }
            tokenRepository.delete(tokenToDelete);
        }

        long expirationInSeconds = expiration;
        LocalDateTime expirationDateTime = LocalDateTime.now().plusSeconds(expirationInSeconds);

        Token newToken = Token.builder()
                .user(user)
                .token(token)
                .revoked(false)
                .expired(false)
                .tokenType("Bearer")
                .expirationDate(expirationDateTime)
                .isMobile(isMobileDevice)
                .build();

        newToken.setRefreshToken(UUID.randomUUID().toString());
        newToken.setRefreshExpirationDate(LocalDateTime.now().plusSeconds(expirationRefreshToken));
        tokenRepository.save(newToken);
        return newToken;
    }





}
