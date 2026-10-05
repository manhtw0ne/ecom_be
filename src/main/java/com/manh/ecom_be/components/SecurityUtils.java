package com.manh.ecom_be.components;

import com.manh.ecom_be.models.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {
    public User getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof User selectedUser) {
            if (!selectedUser.isActive()) {
                return null;
            }
            return (User) authentication.getPrincipal();
        }
        return null;
    }
    public User requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = getLoggedInUser();
        if (auth == null || !auth.isAuthenticated() || user == null || user.isDeleted()
                || user.getId() == null || auth.getAuthorities().stream().noneMatch(a ->
                "ROLE_USER".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException("Active account required");
        }
        return user;
    }

    public void requireSelf(Long userId) {
        if (!requireUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Only the account owner may update this profile");
        }
    }

    public void requireOwnerOrAdmin(Long ownerId) {
        User user = requireUser();
        if (!user.getId().equals(ownerId) && !isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("You cannot change another user's content");
        }
    }

    public void requireAdmin() {
        requireUser();
        if (!isAdmin()) throw new org.springframework.security.access.AccessDeniedException("Administrator required");
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }}
