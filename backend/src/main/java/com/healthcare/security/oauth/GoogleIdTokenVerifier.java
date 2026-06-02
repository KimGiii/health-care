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
public class GoogleIdTokenVerifier extends JwksIdTokenVerifier {

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of(
            "accounts.google.com",
            "https://accounts.google.com"
    );

    @Autowired
    public GoogleIdTokenVerifier(@Value("${app.oauth.google.audience}") String audience) {
        super(defaultProvider(), GOOGLE_ISSUERS, audience);
    }

    GoogleIdTokenVerifier(JwkProvider provider, String audience) {
        super(provider, GOOGLE_ISSUERS, audience);
    }

    private static JwkProvider defaultProvider() {
        try {
            return new JwkProviderBuilder(new URL(GOOGLE_JWKS_URL))
                    .cached(10, 6, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Google JWKS URL 이 잘못되었습니다.", e);
        }
    }
}
