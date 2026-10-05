package com.manh.ecom_be.repositories;

import com.manh.ecom_be.models.Token;
import com.manh.ecom_be.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {
    List<Token> findByUser(User user);
    Token findByToken(String token);
    @org.springframework.data.jpa.repository.Query("select t.user.id from Token t where t.refreshToken = :value")
    Optional<Long> findOwnerIdByRefreshToken(@org.springframework.data.repository.query.Param("value") String value);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Token t where t.refreshToken = :value")
    Optional<Token> findByRefreshTokenForUpdate(@org.springframework.data.repository.query.Param("value") String value);
}
