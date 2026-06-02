import Foundation

struct LoginRequest: Encodable {
    let email: String
    let password: String
}

struct RegisterRequest: Encodable {
    let email: String
    let password: String
    let displayName: String
}

struct SocialLoginRequest: Encodable {
    let idToken: String
}

struct SocialLoginCommitRequest: Encodable {
    let idToken: String
    let agreedToTerms: Bool
    let agreedToPrivacy: Bool
}

/// 2-step 소셜로그인 check 응답.
/// - newUser=false → tokens 즉시 사용
/// - newUser=true  → tokens=nil, 클라이언트는 약관 동의 후 commit 호출
struct SocialLoginCheckResponse: Decodable {
    let newUser: Bool
    let tokens: TokenResponse?
}

struct TokenResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let expiresIn: Int
    let onboardingCompleted: Bool
}
