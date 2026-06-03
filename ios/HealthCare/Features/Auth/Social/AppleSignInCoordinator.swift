import AuthenticationServices
import CryptoKit
import Foundation
import Security
import UIKit

/// Apple 로그인 흐름을 `SocialIdentityTokenProvider` 추상화에 맞춰 캡슐화한 코디네이터.
///
/// 주의 — `ASAuthorizationController.delegate` 는 weak 참조라, delegate 객체가 충분히 강하게
/// 잡혀 있지 않으면 다이얼로그 결과가 돌아오기 전에 dealloc 되어 continuation 이 leak 된다.
/// 따라서 본 코디네이터 인스턴스 자체가 delegate 역할을 맡고, 호출자가 `fetchIdToken()` 의
/// await 가 끝날 때까지 인스턴스를 유지하는 것으로 수명을 보장한다.
///
/// 요구사항:
///   - Xcode → Signing & Capabilities → "Sign in with Apple" 활성화
///   - Apple Developer Portal 의 App ID 에 동일 capability 등록
///   - 백엔드 `app.oauth.apple.audience` 는 본 앱 Bundle ID 와 일치
@MainActor
final class AppleSignInCoordinator: NSObject, SocialIdentityTokenProvider {

    enum AppleSignInError: Error, LocalizedError {
        case missingIdentityToken
        case unexpected(Error)

        var errorDescription: String? {
            switch self {
            case .missingIdentityToken: return String(localized: "auth.error.apple.missingIdToken")
            case .unexpected(let e):    return e.localizedDescription
            }
        }
    }

    private var continuation: CheckedContinuation<SocialIdentityToken, Error>?
    private var controller: ASAuthorizationController?
    /// 현재 인증 요청에 사용한 원본 nonce. 결과를 받을 때 SocialIdentityToken 에 함께 담는다.
    private var currentRawNonce: String?

    nonisolated func fetchIdToken() async throws -> SocialIdentityToken {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<SocialIdentityToken, Error>) in
            Task { @MainActor in
                self.start(continuation: cont)
            }
        }
    }

    private func start(continuation: CheckedContinuation<SocialIdentityToken, Error>) {
        // 동일 코디네이터에 중복 호출이 들어오면 이전 continuation 을 안전하게 종료
        if let pending = self.continuation {
            pending.resume(throwing: CancellationError())
        }
        self.continuation = continuation

        // 재생 공격 방지: 원본 nonce 를 만들고 SHA256 해시를 요청에 실어 보낸다.
        // Apple 은 ID 토큰의 nonce 클레임에 이 해시(16진수)를 그대로 넣어주며,
        // 백엔드가 SHA256(rawNonce) 와 비교해 토큰이 이 기기·이 세션에서 발급됐음을 검증한다.
        let rawNonce = Self.randomNonceString()
        self.currentRawNonce = rawNonce

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256Hex(rawNonce)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        self.controller = controller
        controller.performRequests()
    }

    private func finish(with result: Result<SocialIdentityToken, Error>) {
        let pending = continuation
        continuation = nil
        controller = nil
        currentRawNonce = nil
        switch result {
        case .success(let token): pending?.resume(returning: token)
        case .failure(let error): pending?.resume(throwing: error)
        }
    }

    // MARK: - Nonce

    /// 암호학적으로 안전한 난수 기반 nonce 문자열.
    private static func randomNonceString(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var random: UInt8 = 0
            let status = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
            guard status == errSecSuccess else {
                // SecRandom 실패는 사실상 발생하지 않지만, 발생 시 약한 nonce 를 쓰느니 중단한다.
                fatalError("Unable to generate secure nonce: \(status)")
            }
            if random < charset.count {
                result.append(charset[Int(random)])
                remaining -= 1
            }
        }
        return result
    }

    /// nonce 의 SHA256 16진수 문자열. Apple 요청에 싣고, 토큰의 nonce 클레임과 동일해야 한다.
    private static func sha256Hex(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}

extension AppleSignInCoordinator: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(controller: ASAuthorizationController,
                                             didCompleteWithAuthorization authorization: ASAuthorization) {
        Task { @MainActor in
            let result: Result<SocialIdentityToken, Error>
            if let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
               let tokenData = credential.identityToken,
               let token = String(data: tokenData, encoding: .utf8) {
                result = .success(SocialIdentityToken(idToken: token, rawNonce: self.currentRawNonce))
            } else {
                result = .failure(AppleSignInError.missingIdentityToken)
            }
            self.finish(with: result)
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController,
                                             didCompleteWithError error: Error) {
        let mapped: Error = (error as? ASAuthorizationError)?.code == .canceled
            ? CancellationError()
            : AppleSignInError.unexpected(error)
        Task { @MainActor in self.finish(with: .failure(mapped)) }
    }
}

extension AppleSignInCoordinator: ASAuthorizationControllerPresentationContextProviding {
    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            let keyWindow = scenes.flatMap { $0.windows }.first(where: { $0.isKeyWindow })
            return keyWindow ?? scenes.first?.windows.first ?? ASPresentationAnchor()
        }
    }
}
