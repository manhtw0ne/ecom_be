package com.manh.ecom_be.services.user;

import com.manh.ecom_be.components.SecurityUtils;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.repositories.UserRepository;
import com.manh.ecom_be.utils.FileUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageService {
    private final SecurityUtils securityUtils;
    private final UserRepository users;
    private final EntityManager entityManager;

    @Transactional(rollbackFor = Exception.class)
    public String replace(MultipartFile file) throws Exception {
        Long userId = securityUtils.requireUser().getId();
        var user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
        // The security filter may have loaded this entity before waiting for the lock.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!user.isActive() || user.isDeleted()) {
            throw new org.springframework.security.access.AccessDeniedException("Active account required");
        }
        String previous = user.getProfileImage();
        String created = FileUtils.storeAvatar(file, userId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                // Never delete shared defaults, legacy filenames or remote OAuth avatar URLs.
                if (FileUtils.isManagedAvatar(previous, userId)) cleanup(previous);
            }
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) cleanup(created);
            }
        });
        user.setProfileImage(created);
        users.saveAndFlush(user);
        return created;
    }

    private void cleanup(String name) {
        try { FileUtils.deleteFile(name); }
        catch (java.io.IOException ex) { log.warn("Unable to clean up managed avatar {}", name, ex); }
    }
}