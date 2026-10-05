package com.manh.ecom_be.services.user;

import com.manh.ecom_be.components.JwtTokenUtils;
import com.manh.ecom_be.components.LocalizationUtils;
import com.manh.ecom_be.dtos.UpdateUserDTO;
import com.manh.ecom_be.dtos.UserDTO;
import com.manh.ecom_be.dtos.UserLoginDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.exceptions.ExpiredTokenException;
import com.manh.ecom_be.exceptions.InvalidPasswordException;
import com.manh.ecom_be.exceptions.PermissionDenyException;
import com.manh.ecom_be.models.Role;
import com.manh.ecom_be.models.Token;
import com.manh.ecom_be.models.User;
import com.manh.ecom_be.repositories.RoleRepository;
import com.manh.ecom_be.repositories.TokenRepository;
import com.manh.ecom_be.repositories.UserRepository;
import com.manh.ecom_be.utils.MessageKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.manh.ecom_be.utils.ValidationUtils.isValidEmail;

@Service
@RequiredArgsConstructor
public class UserService implements InterfaceUserService {
    private final com.manh.ecom_be.components.SecurityUtils securityUtils;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtils jwtTokenUtil;
    private final AuthenticationManager authenticationManager;
    private final LocalizationUtils localizationUtils;

    @Override
    @Transactional
    public User createUser(UserDTO userDTO) throws Exception {
        if (userDTO.isSocialLogin() || org.springframework.util.StringUtils.hasText(userDTO.getGoogleAccountId())
                || org.springframework.util.StringUtils.hasText(userDTO.getFacebookAccountId())) {
            throw new IllegalArgumentException("Use the verified social login flow to link an identity");
        }
        if (userDTO.getPhoneNumber() != null && !userDTO.getPhoneNumber().isBlank() && userRepository.existsByPhoneNumber(userDTO.getPhoneNumber())) {
            throw new DataIntegrityViolationException("Phone number already exists");
        }

        if (userDTO.getEmail() != null && !userDTO.getEmail().isBlank()
                && userRepository.existsByEmail(userDTO.getEmail())) {
            throw new DataIntegrityViolationException("Email already exists");
        }

        Role role = roleRepository.findById(userDTO.getRoleId())
                .orElseThrow(() -> new DataNotFoundException(
                        localizationUtils.getLocalizedMessage(MessageKeys.ROLE_DOES_NOT_EXIST)));

        if (!role.getName().equalsIgnoreCase(Role.USER)) {
            throw new PermissionDenyException("Only the USER role can be registered");
        }

        User newUser = User.builder()
                .fullName(userDTO.getFullName())
                .phoneNumber(userDTO.getPhoneNumber())
                .email(userDTO.getEmail())
                .password(userDTO.getPassword())
                .address(userDTO.getAddress())
                .dateOfBirth(userDTO.getDateOfBirth())


                .active(true)
                .build();

        newUser.setRole(role);

        if (!org.springframework.util.StringUtils.hasText(userDTO.getPassword())) {
            throw new IllegalArgumentException("Password is required");
        }
        newUser.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        return userRepository.save(newUser);
    }

    @Override
    public String login(UserLoginDTO userLoginDTO) throws Exception {
        Optional<User> optionalUser = Optional.empty();

        if (userLoginDTO.getPhoneNumber() != null && !userLoginDTO.getPhoneNumber().isBlank()) {
            optionalUser = userRepository.findByPhoneNumber(userLoginDTO.getPhoneNumber());
        }

        if (optionalUser.isEmpty() && userLoginDTO.getEmail() != null) {
            optionalUser = userRepository.findByEmail(userLoginDTO.getEmail());
        }

        if (optionalUser.isEmpty()) {
            throw new DataNotFoundException(localizationUtils.getLocalizedMessage(MessageKeys.WRONG_PHONE_PASSWORD));
        }

        User existingUser = optionalUser.get();

        if (userLoginDTO.getPassword() == null || existingUser.getPassword() == null
                || !passwordEncoder.matches(userLoginDTO.getPassword(), existingUser.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!existingUser.isActive()) {
            throw new DataNotFoundException(localizationUtils.getLocalizedMessage(MessageKeys.USER_IS_LOCKED));
        }
        return jwtTokenUtil.generateToken(existingUser);
    }

    @Override
    public String loginSocial(UserLoginDTO userLoginDTO) throws Exception {
        Optional<User> optionalUser = Optional.empty();

        Role roleUser = roleRepository.findByName(Role.USER)
                .orElseThrow(() -> new DataNotFoundException(
                        localizationUtils.getLocalizedMessage(MessageKeys.ROLE_DOES_NOT_EXIST)));

        if (userLoginDTO.isGoogleAccountIdValid()) {
            optionalUser = userRepository.findByGoogleAccountId(userLoginDTO.getGoogleAccountId());

            if (optionalUser.isEmpty()) {
                User newUser = User.builder()
                        .fullName(Optional.ofNullable(userLoginDTO.getFullName()).orElse(""))
                        .email(Optional.ofNullable(userLoginDTO.getEmail()).orElse(""))
                        .profileImage(Optional.ofNullable(userLoginDTO.getProfileImage()).orElse(""))
                        .role(roleUser)
                        .googleAccountId(userLoginDTO.getGoogleAccountId())
                        .password("")
                        .active(true)
                        .build();

                newUser = userRepository.save(newUser);
                optionalUser = Optional.of(newUser);
            }
        }
        else if (userLoginDTO.isFacebookAccountIdValid()) {
            optionalUser = userRepository.findByFacebookAccountId(userLoginDTO.getFacebookAccountId());

            if (optionalUser.isEmpty()) {
                User newUser = User.builder()
                        .fullName(Optional.ofNullable(userLoginDTO.getFullName()).orElse(""))
                        .email(Optional.ofNullable(userLoginDTO.getEmail()).orElse(""))
                        .profileImage(Optional.ofNullable(userLoginDTO.getProfileImage()).orElse(""))
                        .facebookAccountId(userLoginDTO.getFacebookAccountId())
                        .role(roleUser)
                        .password("")
                        .active(true)
                        .build();


                newUser = userRepository.save(newUser);
                optionalUser = Optional.of(newUser);
            }
        } else {
            throw new IllegalArgumentException("Invalid social account information");
        }

        User user = optionalUser.get();
        if (!user.isActive()) {
            throw new DataNotFoundException(localizationUtils.getLocalizedMessage(MessageKeys.USER_IS_LOCKED));
        }

        return jwtTokenUtil.generateToken(user);
    }

    @Transactional
    @Override
    public User updateUser(Long userId, UpdateUserDTO updateUserDTO) throws Exception {
        securityUtils.requireSelf(userId);
        if (org.springframework.util.StringUtils.hasText(updateUserDTO.getGoogleAccountId())
                || org.springframework.util.StringUtils.hasText(updateUserDTO.getFacebookAccountId())) {
            throw new IllegalArgumentException("Social identities cannot be edited through profile updates");
        }
        User existingUser = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new DataNotFoundException("User not found"));

        if (updateUserDTO.getFullname() != null) {
            existingUser.setFullName(updateUserDTO.getFullname());
        }

//        if (updateUserDTO.getPhoneNumber() != null) {
//            existingUser.setPhoneNumber(updateUserDTO.getPhoneNumber());
//        }

        if (updateUserDTO.getAddress() != null) {
            existingUser.setAddress(updateUserDTO.getAddress());
        }

        if (updateUserDTO.getDateOfBirth() != null) {
            existingUser.setDateOfBirth(updateUserDTO.getDateOfBirth());
        }



        if (updateUserDTO.getPassword() != null
                && !updateUserDTO.getPassword().isEmpty()) {
            if (!updateUserDTO.getPassword().equals(updateUserDTO.getRetypePassword())) {
                throw new DataNotFoundException("Password and retype password do not match");
            }
            String newPassword = updateUserDTO.getPassword();
            String encodedPassword = passwordEncoder.encode(newPassword);
            existingUser.setPassword(encodedPassword);
            tokenRepository.findByUser(existingUser).forEach(tokenRepository::delete);
        }
        return userRepository.save(existingUser);
    }



    @Override
    public User getUserDetailsFromToken(String token) throws Exception {
        if (jwtTokenUtil.isTokenExpired(token)) {
            throw new ExpiredTokenException("Token is expired");
        }
        String subject = jwtTokenUtil.getSubject(token);
        Optional<User> user;
        user = userRepository.findByPhoneNumber(subject);
        if (user.isEmpty() && isValidEmail(subject)) {
            user = userRepository.findByEmail(subject);
        }
        return user.orElseThrow(() -> new Exception("User not found"));
    }



    @Override
    public Page<User> findAll(String keyword, Pageable pageable) {
        securityUtils.requireAdmin();
        return userRepository.findAll(keyword, pageable);
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword)
            throws InvalidPasswordException, DataNotFoundException {
        securityUtils.requireAdmin();
        User existingUser = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
        String encodedPassword = passwordEncoder.encode(newPassword);
        existingUser.setPassword(encodedPassword);
        userRepository.save(existingUser);

        List<Token> tokens = tokenRepository.findByUser(existingUser);
        for (Token token : tokens) {
            tokenRepository.delete(token);
        }
    }

    @Override
    @Transactional
    public void blockOrEnable(Long userId, Boolean active) throws DataNotFoundException {
        securityUtils.requireAdmin();

        User existingUser = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
        existingUser.setActive(active);
        if (!active) tokenRepository.findByUser(existingUser).forEach(tokenRepository::delete);
        userRepository.save(existingUser);
    }

    @Override
    @Transactional
    public void changeProfileImage(Long userId, String imageName) throws Exception {
        securityUtils.requireSelf(userId);
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
        existingUser.setProfileImage(imageName);
        userRepository.save(existingUser);
    }









}
