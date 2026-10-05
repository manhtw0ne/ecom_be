package com.manh.ecom_be.services.token;

import com.manh.ecom_be.models.Token;
import com.manh.ecom_be.models.User;

public interface InterfaceTokenService {
    Token addToken(User user, String token, boolean isMobileDevice);
    Token refreshToken(String refreshToken) throws Exception;
}
