package com.healthcare.security;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return toUserDetails(user);
    }

    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return toUserDetails(user);
    }

    private UserDetails toUserDetails(User user) {
        // 소셜로그인 전용 사용자는 passwordHash 가 null 이다.
        // Spring Security 의 User 생성자는 null/empty password 를 거부하므로 빈 문자열로 대체한다.
        // JWT 기반 인증이라 password 매칭이 일어나지 않고, 이 값은 단순 자리표시자.
        String password = user.getPasswordHash() != null ? user.getPasswordHash() : "";
        return new org.springframework.security.core.userdetails.User(
            user.getId().toString(),
            password,
            Collections.emptyList()
        );
    }
}
