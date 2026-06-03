import Foundation
import GoogleSignIn
import UIKit

/// Google 로그인 흐름을 `SocialIdentityTokenProvider` 추상화에 맞춰 캡슐화한 코디네이터.
///
/// 백엔드 검증 규칙(중요):
///   - Google 은 nonce 를 `id_token` 의 `nonce` 클레임에 **원본 그대로** 담는다(Apple 처럼 SHA256 해시 X).
///   - 따라서 본 코디네이터는 SHA256 변환 없이 원본 nonce 를 그대로 SDK 에 넘기고, `SocialIdentityToken.rawNonce`
///     에도 동일 값을 담아 백엔드의 `case GOOGLE -> rawNonce` 비교 분기와 매칭시킨다.
///
/// 요구사항:
///   - `Info.plist` 의 `GIDClientID` = GCP 콘솔에서 발급한 iOS OAuth Client ID
///   - `Info.plist` 의 `CFBundleURLTypes` 에 Reversed Client ID 등록
///   - `AppDelegate.application(_:open:options:)` 에서 `GIDSignIn.sharedInstance.handle(url:)` 위임
///   - 백엔드 `app.oauth.google.audience` = 동일 Client ID
@MainActor
final class GoogleSignInCoordinator: NSObject, SocialIdentityTokenProvider {

    enum GoogleSignInError: Error, LocalizedError {
        case missingPresentingViewController
        case missingIdentityToken
        case unexpected(Error)

        var errorDescription: String? {
            switch self {
            case .missingPresentingViewController: return String(localized: "auth.error.google.missingPresenter")
            case .missingIdentityToken:            return String(localized: "auth.error.google.missingIdToken")
            case .unexpected(let e):               return e.localizedDescription
            }
        }
    }

    nonisolated func fetchIdToken() async throws -> SocialIdentityToken {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<SocialIdentityToken, Error>) in
            Task { @MainActor in
                self.start(continuation: cont)
            }
        }
    }

    private func start(continuation: CheckedContinuation<SocialIdentityToken, Error>) {
        guard let presenter = Self.topViewController() else {
            continuation.resume(throwing: GoogleSignInError.missingPresentingViewController)
            return
        }

        // 재생 공격 방지용 원본 nonce. Google 은 SHA256 해시 없이 그대로 토큰에 실어 돌려준다.
        let rawNonce = Self.randomNonceString()

        GIDSignIn.sharedInstance.signIn(
            withPresenting: presenter,
            hint: nil,
            additionalScopes: nil,
            nonce: rawNonce
        ) { result, error in
            if let error {
                // 사용자가 시트를 닫은 경우 SDK 는 GIDSignInError.canceled 를 던진다 → CancellationError 로 매핑.
                if let gidError = error as? GIDSignInError, gidError.code == .canceled {
                    continuation.resume(throwing: CancellationError())
                } else {
                    continuation.resume(throwing: GoogleSignInError.unexpected(error))
                }
                return
            }
            guard let idToken = result?.user.idToken?.tokenString else {
                continuation.resume(throwing: GoogleSignInError.missingIdentityToken)
                return
            }
            continuation.resume(returning: SocialIdentityToken(idToken: idToken, rawNonce: rawNonce))
        }
    }

    // MARK: - Helpers

    /// 현재 활성 윈도우의 최상위 ViewController. Google SDK 가 sign-in UI 를 띄울 anchor 로 사용한다.
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let keyWindow = scenes.flatMap { $0.windows }.first(where: { $0.isKeyWindow })
            ?? scenes.first?.windows.first
        var top = keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }

    /// 암호학적으로 안전한 난수 기반 nonce 문자열.
    private static func randomNonceString(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var random: UInt8 = 0
            let status = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
            guard status == errSecSuccess else {
                fatalError("Unable to generate secure nonce: \(status)")
            }
            if random < charset.count {
                result.append(charset[Int(random)])
                remaining -= 1
            }
        }
        return result
    }
}
