package com.healthcare.domain.user.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.ValidationException;
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

        User.ActivityLevel activityLevel = parseActivityLevel(request.getActivityLevel());
        User.Sex sex = parseSex(request.getSex());

        user.updateProfile(
            request.getDisplayName(),
            request.getDateOfBirth(),
            request.getHeightCm(),
            request.getWeightKg(),
            activityLevel,
            sex,
            request.getLocale(),
            request.getTimezone()
        );
        user.updateTargets(request.getCalorieTarget(), request.getProteinTargetG(), request.getCarbTargetG(), request.getFatTargetG());

        if (request.getFcmToken() != null) {
            user.updateFcmToken(request.getFcmToken());
        }

        if (Boolean.TRUE.equals(request.getOnboardingCompleted())) {
            user.completeOnboarding();
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

    private User.ActivityLevel parseActivityLevel(String activityLevel) {
        if (activityLevel == null || activityLevel.isBlank()) {
            return null;
        }
        try {
            return User.ActivityLevel.valueOf(activityLevel);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("유효하지 않은 활동 수준입니다: " + activityLevel);
        }
    }

    private User.Sex parseSex(String sex) {
        if (sex == null || sex.isBlank()) {
            return null;
        }
        try {
            return User.Sex.valueOf(sex);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("유효하지 않은 성별입니다: " + sex);
        }
    }
}
