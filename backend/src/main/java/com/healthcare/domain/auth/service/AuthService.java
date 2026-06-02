package com.healthcare.domain.auth.service;

import com.healthcare.common.exception.DuplicateResourceException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.auth.dto.LoginRequest;
import com.healthcare.domain.auth.dto.RefreshTokenRequest;
import com.healthcare.domain.auth.dto.RegisterRequest;
import com.healthcare.domain.auth.dto.SocialLoginCheckResponse;
import com.healthcare.domain.auth.dto.SocialLoginCommitRequest;
import com.healthcare.domain.auth.dto.SocialLoginRequest;
import com.healthcare.domain.auth.dto.TokenResponse;
import com.healthcare.domain.auth.entity.RefreshToken;
import com.healthcare.domain.auth.repository.RefreshTokenRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.entity.UserIdentity;
import com.healthcare.domain.user.repository.UserIdentityRepository;
import com.healthcare.domain.user.repository.UserRepository;
import com.healthcare.security.JwtTokenProvider;
import com.healthcare.security.oauth.AppleIdTokenVerifier;
import com.healthcare.security.oauth.GoogleIdTokenVerifier;
import com.healthcare.security.oauth.OAuthIdTokenVerifier;
import com.healthcare.security.oauth.OAuthUserInfo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AppleIdTokenVerifier appleIdTokenVerifier;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    private final Counter registerCounter;
    private final Counter loginSuccessCounter;
    private final Counter loginFailCounter;
    private final Counter socialLoginSuccessCounter;

    public AuthService(UserRepository userRepository,
                       UserIdentityRepository userIdentityRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       AppleIdTokenVerifier appleIdTokenVerifier,
                       GoogleIdTokenVerifier googleIdTokenVerifier,
                       MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.appleIdTokenVerifier = appleIdTokenVerifier;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.registerCounter = Counter.builder("healthcare.auth.register")
            .description("회원가입 성공 수")
            .register(meterRegistry);
        this.loginSuccessCounter = Counter.builder("healthcare.auth.login")
            .description("로그인 시도 수")
            .tag("result", "success")
            .register(meterRegistry);
        this.loginFailCounter = Counter.builder("healthcare.auth.login")
            .description("로그인 시도 수")
            .tag("result", "fail")
            .register(meterRegistry);
        this.socialLoginSuccessCounter = Counter.builder("healthcare.auth.social_login")
            .description("소셜로그인 성공 수")
            .tag("result", "success")
            .register(meterRegistry);
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new DuplicateResourceException("이미 사용 중인 이메일입니다.");
        }

        if (request.getDateOfBirth() != null) {
            long age = ChronoUnit.YEARS.between(request.getDateOfBirth(), LocalDate.now());
            if (age < 14) {
                throw new ValidationException("만 14세 이상만 가입할 수 있습니다.");
            }
        }

        User.Sex sex = parseSex(request.getSex());
        User.ActivityLevel activityLevel = parseActivityLevel(request.getActivityLevel());

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .displayName(request.getDisplayName())
            .sex(sex)
            .dateOfBirth(request.getDateOfBirth())
            .heightCm(request.getHeightCm())
            .weightKg(request.getWeightKg())
            .activityLevel(activityLevel)
            .locale(normalizeLocale(request.getLocale()))
            .timezone(normalizeTimezone(request.getTimezone()))
            .onboardingCompleted(hasCompletedOnboardingProfile(request))
            .build();

        userRepository.save(user);
        registerCounter.increment();
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
            .orElseThrow(() -> {
                loginFailCounter.increment();
                return new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");
            });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginFailCounter.increment();
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        refreshTokenRepository.revokeAllByUserId(user.getId());
        loginSuccessCounter.increment();
        return issueTokens(user);
    }

    /**
     * 소셜 로그인. 정책:
     * <ol>
     *   <li>제공자별 JWKS 검증기로 ID 토큰 검증 → subject/email/emailVerified 획득</li>
     *   <li>(provider, subject) 매칭되는 UserIdentity 가 있으면 해당 User 사용</li>
     *   <li>없고 email_verified=true 이며 동일 이메일 LOCAL 계정이 있으면 자동 연결 (UserIdentity 행만 추가)</li>
     *   <li>그 외에는 신규 User + UserIdentity 생성 (passwordHash=null, onboardingCompleted=false)</li>
     * </ol>
     * 검증되지 않은 이메일을 가진 토큰은 자동 연결하지 않고 항상 신규 계정으로 분리한다 (계정 탈취 방지).
     *
     * @deprecated 2-step 흐름 {@link #socialLoginCheck} + {@link #socialLoginCommit} 사용을 권장.
     *             기존 클라이언트(v1.1.x) 호환을 위해 유지하되 약관 동의 시각이 기록되지 않는다.
     */
    @Deprecated
    @Transactional
    public TokenResponse socialLogin(String providerRaw, SocialLoginRequest request) {
        UserIdentity.Provider provider = parseProvider(providerRaw);
        OAuthUserInfo info = verifierFor(provider).verify(request.getIdToken());

        User user = resolveExistingUser(provider, info.subject())
            .orElseGet(() -> linkOrCreateUser(provider, info));

        refreshTokenRepository.revokeAllByUserId(user.getId());
        socialLoginSuccessCounter.increment();
        return issueTokens(user);
    }

    /**
     * 2-step 소셜로그인 1단계: ID 토큰 검증 후 기존/신규 판별.
     * <ul>
     *   <li>기존 사용자(또는 email_verified=true 자동 연결 대상): 토큰 즉시 발급</li>
     *   <li>신규 사용자: 토큰 발급 없이 {@code isNew=true} 만 반환. 클라이언트는 약관 동의 UI 노출 후
     *       {@link #socialLoginCommit} 호출.</li>
     * </ul>
     * 신규 판별 단계에서는 어떤 row 도 생성하지 않는다.
     */
    @Transactional
    public SocialLoginCheckResponse socialLoginCheck(String providerRaw, SocialLoginRequest request) {
        UserIdentity.Provider provider = parseProvider(providerRaw);
        OAuthUserInfo info = verifierFor(provider).verify(request.getIdToken());

        Optional<User> existing = resolveExistingUser(provider, info.subject());
        if (existing.isEmpty() && info.email() != null && info.emailVerified()) {
            existing = userRepository.findByEmailAndDeletedAtIsNull(info.email());
            if (existing.isPresent()) {
                // 자동 연결: identity 만 추가하고 즉시 로그인 (약관은 LOCAL 가입 시 이미 동의했다고 본다)
                userIdentityRepository.save(UserIdentity.create(existing.get(), provider, info.subject()));
            }
        }

        if (existing.isPresent()) {
            User user = existing.get();
            refreshTokenRepository.revokeAllByUserId(user.getId());
            socialLoginSuccessCounter.increment();
            return SocialLoginCheckResponse.existing(issueTokens(user));
        }
        return SocialLoginCheckResponse.pendingConsent();
    }

    /**
     * 2-step 소셜로그인 2단계: 약관 동의 확인 후 신규 사용자 생성.
     * <p>
     * 클라이언트는 {@link #socialLoginCheck} 가 {@code isNew=true} 를 반환한 경우에만 호출한다.
     * 동일 토큰을 다시 검증하며(짧은 유효시간 내라 안전), 동시성 race 로 그사이 identity 가 만들어졌다면
     * 그 사용자 토큰을 발급한다(중복 가입 방지).
     */
    @Transactional
    public TokenResponse socialLoginCommit(String providerRaw, SocialLoginCommitRequest request) {
        UserIdentity.Provider provider = parseProvider(providerRaw);
        OAuthUserInfo info = verifierFor(provider).verify(request.getIdToken());

        // race 가드: check 와 commit 사이에 다른 디바이스에서 동일 (provider, subject) 가입이 완료됐을 수 있다.
        User user = resolveExistingUser(provider, info.subject())
            .orElseGet(() -> {
                User created = linkOrCreateUser(provider, info);
                OffsetDateTime now = OffsetDateTime.now();
                created.recordConsents(now, now);
                return created;
            });

        refreshTokenRepository.revokeAllByUserId(user.getId());
        socialLoginSuccessCounter.increment();
        return issueTokens(user);
    }

    /**
     * (provider, subject) 매칭되는 identity 가 있어도 그 user 가 soft-delete 됐다면
     * 고아 identity 를 정리하고 신규 가입 흐름으로 빠진다.
     * <p>
     * 사유: 회원탈퇴(soft delete)된 사용자가 같은 Apple/Google 계정으로 재가입할 수 있어야 한다.
     * User 엔티티의 {@code @SQLRestriction("deleted_at IS NULL")} 때문에 lazy proxy 초기화 시
     * {@link jakarta.persistence.EntityNotFoundException} 이 발생하므로 사전 체크가 필요하다.
     */
    private Optional<User> resolveExistingUser(UserIdentity.Provider provider, String subject) {
        Optional<UserIdentity> identityOpt =
            userIdentityRepository.findByProviderAndProviderSubject(provider, subject);
        if (identityOpt.isEmpty()) {
            return Optional.empty();
        }
        UserIdentity identity = identityOpt.get();
        // identity.getUser() 는 lazy proxy — id 만 꺼내고 실제 User 는 별도 조회로 deleted_at 필터링
        Long userId = identity.getUser().getId();
        Optional<User> liveUser = userRepository.findByIdAndDeletedAtIsNull(userId);
        if (liveUser.isPresent()) {
            return liveUser;
        }
        // 고아 identity — 죽은 사용자에 매달려 있으므로 제거해 (provider, subject) UNIQUE 를 해제.
        // flush 를 명시해야 같은 트랜잭션 안의 후속 INSERT 가 DELETE 보다 먼저 실행되지 않는다.
        userIdentityRepository.delete(identity);
        userIdentityRepository.flush();
        return Optional.empty();
    }

    private User linkOrCreateUser(UserIdentity.Provider provider, OAuthUserInfo info) {
        // 이메일 검증된 토큰만 기존 계정과 자동 연결 (탈취 방지)
        if (info.email() != null && info.emailVerified()) {
            User existing = userRepository.findByEmailAndDeletedAtIsNull(info.email()).orElse(null);
            if (existing != null) {
                userIdentityRepository.save(UserIdentity.create(existing, provider, info.subject()));
                return existing;
            }
        }

        String email = info.email() != null && !info.email().isBlank()
            ? info.email()
            : provider.name().toLowerCase() + "_" + info.subject() + "@social.local";
        String displayName = (info.name() != null && !info.name().isBlank())
            ? info.name()
            : defaultDisplayNameFrom(email);

        User newUser = User.builder()
            .email(email)
            .passwordHash(null)
            .displayName(displayName)
            .locale("ko-KR")
            .timezone("Asia/Seoul")
            .onboardingCompleted(false)
            .build();
        userRepository.save(newUser);
        userIdentityRepository.save(UserIdentity.create(newUser, provider, info.subject()));
        return newUser;
    }

    private OAuthIdTokenVerifier verifierFor(UserIdentity.Provider provider) {
        return switch (provider) {
            case APPLE -> appleIdTokenVerifier;
            case GOOGLE -> googleIdTokenVerifier;
        };
    }

    private UserIdentity.Provider parseProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("provider 가 필요합니다.");
        }
        try {
            return UserIdentity.Provider.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("지원하지 않는 provider 입니다: " + raw);
        }
    }

    private String defaultDisplayNameFrom(String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return local.length() > 100 ? local.substring(0, 100) : local;
    }

    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = hash(request.getRefreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
            .orElseThrow(() -> new UnauthorizedException("유효하지 않은 리프레시 토큰입니다."));

        if (!refreshToken.isValid()) {
            throw new UnauthorizedException("만료된 리프레시 토큰입니다.");
        }

        refreshToken.revoke();

        User user = userRepository.findByIdAndDeletedAtIsNull(refreshToken.getUserId())
            .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));

        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshTokenRaw = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        RefreshToken refreshToken = RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(hash(refreshTokenRaw))
            .expiresAt(OffsetDateTime.now().plusDays(30))
            .build();

        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .accessToken(accessToken)
            .refreshToken(refreshTokenRaw)
            .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
            .onboardingCompleted(user.isOnboardingCompleted())
            .build();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
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

    private String normalizeLocale(String locale) {
        return locale == null || locale.isBlank() ? "ko-KR" : locale;
    }

    private String normalizeTimezone(String timezone) {
        return timezone == null || timezone.isBlank() ? "Asia/Seoul" : timezone;
    }

    private boolean hasCompletedOnboardingProfile(RegisterRequest request) {
        return request.getSex() != null
            && request.getDateOfBirth() != null
            && request.getHeightCm() != null
            && request.getWeightKg() != null
            && request.getActivityLevel() != null
            && request.getLocale() != null
            && request.getTimezone() != null;
    }
}
