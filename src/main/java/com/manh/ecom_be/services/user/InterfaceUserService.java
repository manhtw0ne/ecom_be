package com.manh.ecom_be.services.user;

import com.manh.ecom_be.dtos.UpdateUserDTO;
import com.manh.ecom_be.dtos.UserDTO;
import com.manh.ecom_be.dtos.UserLoginDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.exceptions.InvalidPasswordException;
import com.manh.ecom_be.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InterfaceUserService {
    User createUser(UserDTO userDTO) throws Exception;
    String login(UserLoginDTO userLoginDTO) throws Exception;
    User getUserDetailsFromToken(String token) throws Exception;

    User updateUser(Long userId, UpdateUserDTO dto) throws Exception;

    Page<User> findAll(String keyword, Pageable pageable);
    void resetPassword(Long userId, String newPassword)
            throws InvalidPasswordException, DataNotFoundException;
    void blockOrEnable(Long userId, Boolean active) throws DataNotFoundException;
    void changeProfileImage(Long userId, String imageName) throws Exception;
    String loginSocial(UserLoginDTO userLoginDTO) throws Exception;
}
