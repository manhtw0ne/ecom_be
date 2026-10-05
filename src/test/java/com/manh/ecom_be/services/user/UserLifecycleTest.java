package com.manh.ecom_be.services.user;

import com.manh.ecom_be.components.*;
import com.manh.ecom_be.dtos.*;
import com.manh.ecom_be.exceptions.*;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLifecycleTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock TokenRepository tokens;
    @Mock PasswordEncoder encoder;
    @Mock JwtTokenUtils jwt;
    @Mock AuthenticationManager authentication;
    @Mock LocalizationUtils localization;
    @Mock com.manh.ecom_be.components.SecurityUtils securityUtils;
    @InjectMocks UserService service;

    @Test void registrationStoresHashAndUserRole() throws Exception {
        var dto = UserDTO.builder().fullName("Buyer").phoneNumber("0123456789")
                .email("buyer@example.test").password("secret").roleId(1L).build();
        var role = Role.builder().id(1L).name(Role.USER).build();
        when(roles.findById(1L)).thenReturn(Optional.of(role));
        when(encoder.encode("secret")).thenReturn("hash");
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        var user = service.createUser(dto);
        assertThat(user.getPassword()).isEqualTo("hash");
        assertThat(user.getRole()).isSameAs(role);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getEmail()).isEqualTo(dto.getEmail());
    }
    @Test void duplicatePhoneRejectedBeforeSave() {
        var dto = UserDTO.builder().phoneNumber("0123456789").build();
        when(users.existsByPhoneNumber(dto.getPhoneNumber())).thenReturn(true);
        assertThatThrownBy(() -> service.createUser(dto)).isInstanceOf(DataIntegrityViolationException.class);
        verify(users, never()).save(any());
    }
    @Test void duplicateEmailRejectedBeforeSave() {
        var dto = UserDTO.builder().email("buyer@example.test").build();
        when(users.existsByEmail(dto.getEmail())).thenReturn(true);
        assertThatThrownBy(() -> service.createUser(dto)).isInstanceOf(DataIntegrityViolationException.class);
        verify(users, never()).save(any());
    }
    @Test void cannotRegisterAdmin() {
        when(roles.findById(2L)).thenReturn(Optional.of(Role.builder().name(Role.ADMIN).build()));
        assertThatThrownBy(() -> service.createUser(UserDTO.builder().roleId(2L).build()))
                .isInstanceOf(PermissionDenyException.class);
        verify(users, never()).save(any());
    }
    @Test void missingRoleRejected() {
        assertThatThrownBy(() -> service.createUser(UserDTO.builder().roleId(99L).build()))
                .isInstanceOf(DataNotFoundException.class);
        verify(users, never()).save(any());
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void socialAccountProvisioningUsesNormalRole(boolean google) throws Exception {
        var role = Role.builder().name(Role.USER).build();
        when(roles.findByName(Role.USER)).thenReturn(Optional.of(role));
        var dto = new UserLoginDTO();
        dto.setFullName("Social buyer");
        dto.setEmail("social@example.test");
        dto.setProfileImage("avatar.png");
        if (google) dto.setGoogleAccountId("google-id"); else dto.setFacebookAccountId("facebook-id");
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwt.generateToken(any())).thenReturn("social-jwt");
        assertThat(service.loginSocial(dto)).isEqualTo("social-jwt");
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isSameAs(role);
        assertThat(saved.getValue().getFullName()).isEqualTo("Social buyer");
        assertThat(saved.getValue().getPassword()).isEmpty();
        assertThat(saved.getValue().isActive()).isTrue();
    }
    @Test void blockedSocialAccountDoesNotGetToken() throws Exception {
        when(roles.findByName(Role.USER)).thenReturn(Optional.of(Role.builder().name(Role.USER).build()));
        var dto = new UserLoginDTO(); dto.setGoogleAccountId("g");
        when(users.findByGoogleAccountId("g")).thenReturn(Optional.of(User.builder().active(false).build()));
        assertThatThrownBy(() -> service.loginSocial(dto)).isInstanceOf(DataNotFoundException.class);
        verifyNoInteractions(jwt);
        verify(users, never()).save(any());
    }
    @Test void invalidSocialIdentityRejected() {
        when(roles.findByName(Role.USER)).thenReturn(Optional.of(new Role()));
        assertThatThrownBy(() -> service.loginSocial(new UserLoginDTO())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jwt);
    }
    @Test void profileUpdateHashesPasswordAndPreservesPhone() throws Exception {
        var user = User.builder().phoneNumber("original").password("old").build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(encoder.encode("new")).thenReturn("new-hash");
        var birthday = new Date(0);
        var dto = UpdateUserDTO.builder().fullname("Updated").address("Hanoi").dateOfBirth(birthday)
                .phoneNumber("replacement").password("new").retypePassword("new").build();

        var updated = service.updateUser(1L, dto);
        assertThat(updated.getFullName()).isEqualTo("Updated");
        assertThat(updated.getAddress()).isEqualTo("Hanoi");
        assertThat(updated.getDateOfBirth()).isEqualTo(birthday);
        assertThat(updated.getPassword()).isEqualTo("new-hash");
        assertThat(updated.getPhoneNumber()).isEqualTo("original");
        assertThat(updated.getGoogleAccountId()).isNull();
        assertThat(updated.getFacebookAccountId()).isNull();
    }
    @Test void mismatchingPasswordDoesNotSave() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(new User()));
        var dto = UpdateUserDTO.builder().password("new").retypePassword("different").build();
        assertThatThrownBy(() -> service.updateUser(1L,dto)).isInstanceOf(DataNotFoundException.class);
        verify(users, never()).save(any()); verifyNoInteractions(encoder);
    }
    @Test void resetPasswordDeletesAllRefreshTokens() throws Exception {
        var user = new User(); var t1 = new Token(); var t2 = new Token();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(encoder.encode("new")).thenReturn("hash");
        when(tokens.findByUser(user)).thenReturn(List.of(t1,t2));
        service.resetPassword(1L,"new");
        assertThat(user.getPassword()).isEqualTo("hash");
        verify(tokens).delete(t1); verify(tokens).delete(t2);
    }
    @Test void tokenSubjectFallsBackToEmail() throws Exception {
        var user = User.builder().email("buyer@example.test").build();
        when(jwt.getSubject("jwt")).thenReturn(user.getEmail());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThat(service.getUserDetailsFromToken("jwt")).isSameAs(user);
    }
    @Test void expiredTokenNeverLoadsUser() {
        when(jwt.isTokenExpired("expired")).thenReturn(true);
        assertThatThrownBy(() -> service.getUserDetailsFromToken("expired")).isInstanceOf(ExpiredTokenException.class);
        verifyNoInteractions(users);
    }

    @Test void accountCanBeBlockedAndAvatarChanged() throws Exception {
        var user = User.builder().active(true).build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        service.blockOrEnable(1L,false); service.changeProfileImage(1L,"avatar.png");
        assertThat(user.isActive()).isFalse(); assertThat(user.getProfileImage()).isEqualTo("avatar.png");
        verify(users,times(2)).save(user);
    }
    @Test void missingUserCannotBeEdited() {
        assertThatThrownBy(() -> service.updateUser(99L,new UpdateUserDTO())).isInstanceOf(DataNotFoundException.class);
        assertThatThrownBy(() -> service.resetPassword(99L,"x")).isInstanceOf(DataNotFoundException.class);
        assertThatThrownBy(() -> service.blockOrEnable(99L,false)).isInstanceOf(DataNotFoundException.class);
        assertThatThrownBy(() -> service.changeProfileImage(99L,"x")).isInstanceOf(DataNotFoundException.class);
        verify(users,never()).save(any());
    }
}