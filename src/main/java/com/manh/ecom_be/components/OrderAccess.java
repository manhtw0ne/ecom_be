package com.manh.ecom_be.components;

import com.manh.ecom_be.models.Order;
import com.manh.ecom_be.models.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Shared authorization for every order service entry point. */
@Component
public class OrderAccess {
    public User requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof User user) || !user.isActive()
                || user.getId() == null
                || auth.getAuthorities().stream().noneMatch(a ->
                    a.getAuthority().equals("ROLE_USER") || a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("An active account is required");
        }
        return user;
    }

    public void requireAdmin() {
        requireUser();
        if (!isAdmin()) throw new AccessDeniedException("Administrator access is required");
    }

    public void requireOwnerOrAdmin(Long ownerId) {
        User user = requireUser();
        if (!isAdmin() && !user.getId().equals(ownerId)) {
            throw new AccessDeniedException("You cannot access another user's order");
        }
    }

    public void requireOrder(Order order) {
        requireOwnerOrAdmin(order.getUser().getId());
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
