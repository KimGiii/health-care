package com.healthcare.security.oauth;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class AppleIdTokenVerifier extends JwksIdTokenVerifier {

    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    @Autowired
    public AppleIdTokenVerifier(@Value("${app.oauth.apple.audience}") String audience) {
        super(defaultProvider(), Set.of(APPLE_ISSUER), audience);
    }

    // 테스트에서 JwkProvider 주입을 위한 보조 생성자
    AppleIdTokenVerifier(JwkProvider provider, String audience) {
        super(provider, Set.of(APPLE_ISSUER), audience);
    }

    private static JwkProvider defaultProvider() {
        try {
            return new JwkProviderBuilder(new URL(APPLE_JWKS_URL))
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Apple JWKS URL 이 잘못되었습니다.", e);
        }
    }
}
