package com.healthcare.domain.user.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.domain.user.dto.UpdateProfileRequest;
import com.healthcare.domain.user.dto.UserProfileResponse;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Cacheable(cacheNames = "userProfile", key = "#userId")
    public UserProfileResponse getProfile(Long userId) {
        User user = findUser(userId);
        return UserProfileResponse.from(user);
    }

    @Transactional
    @CacheEvict(cacheNames = "userProfile", key = "#userId")
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);

        User.ActivityLevel activityLevel = null;
        if (request.getActivityLevel() != null) {
            activityLevel = User.ActivityLevel.valueOf(request.getActivityLevel());
        }

        user.updateProfile(request.getDisplayName(), request.getHeightCm(), request.getWeightKg(), activityLevel);
        user.updateTargets(request.getCalorieTarget(), request.getProteinTargetG(), request.getCarbTargetG(), request.getFatTargetG());

        if (request.getFcmToken() != null) {
            user.updateFcmToken(request.getFcmToken());
        }

        return UserProfileResponse.from(user);
    }

    @Transactional
    @CacheEvict(cacheNames = "userProfile", key = "#userId")
    public void deleteAccount(Long userId) {
        User user = findUser(userId);
        user.softDelete();
    }

    private User findUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
