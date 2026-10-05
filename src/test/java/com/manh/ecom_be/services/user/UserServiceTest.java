package com.manh.ecom_be.services.user;

import com.manh.ecom_be.components.JwtTokenUtils;
import com.manh.ecom_be.components.LocalizationUtils;
import com.manh.ecom_be.dtos.UserLoginDTO;
import com.manh.ecom_be.models.User;
import com.manh.ecom_be.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock TokenRepository tokens;
    @Mock PasswordEncoder encoder;
    @Mock JwtTokenUtils jwt;
    @Mock AuthenticationManager auth;
    @Mock LocalizationUtils localization;
    @Mock com.manh.ecom_be.components.SecurityUtils securityUtils;
    @InjectMocks UserService service;

    @Test
    void incorrectPasswordNeverIssuesToken() throws Exception {
        User user = User.builder().phoneNumber("0123456789").password("hashed").active(true).build();
        UserLoginDTO dto = new UserLoginDTO();
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setPassword("wrong");
        when(users.findByPhoneNumber(user.getPhoneNumber())).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);
        assertThatThrownBy(() -> service.login(dto)).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(jwt);
    }

    @Test
    void correctPasswordIssuesToken() throws Exception {
        User user = User.builder().email("buyer@example.test").password("hashed").active(true).build();
        UserLoginDTO dto = new UserLoginDTO();
        dto.setEmail(user.getEmail());
        dto.setPassword("correct");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("correct", "hashed")).thenReturn(true);
        when(jwt.generateToken(user)).thenReturn("jwt");
        assertThat(service.login(dto)).isEqualTo("jwt");
    }
}
