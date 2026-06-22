import Foundation
import GoogleSignIn
import UIKit

/// Google 로그인 흐름을 `SocialIdentityTokenProvider` 추상화에 맞춰 캡슐화한 코디네이터.
///
/// 백엔드 검증 규칙(중요):
///   - GoogleSignIn 7.1.0 의 iOS sign-in API 는 nonce 파라미터를 노출하지 않는다.
///   - 따라서 Google 로그인은 `SocialIdentityToken.rawNonce == nil` 로 전달하며, 백엔드는 하위호환 규칙에 따라
///     nonce 검증을 건너뛴다. nonce 지원 SDK 로 올리면 이 경계를 다시 좁힌다.
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

        GIDSignIn.sharedInstance.signIn(
            withPresenting: presenter,
            hint: nil,
            additionalScopes: nil
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
            continuation.resume(returning: SocialIdentityToken(idToken: idToken, rawNonce: nil))
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

}
