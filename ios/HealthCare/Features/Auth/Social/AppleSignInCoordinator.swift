import AuthenticationServices
import Foundation
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
            case .missingIdentityToken: return "Apple ID 토큰을 받지 못했습니다."
            case .unexpected(let e):    return e.localizedDescription
            }
        }
    }

    private var continuation: CheckedContinuation<String, Error>?
    private var controller: ASAuthorizationController?

    nonisolated func fetchIdToken() async throws -> String {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<String, Error>) in
            Task { @MainActor in
                self.start(continuation: cont)
            }
        }
    }

    private func start(continuation: CheckedContinuation<String, Error>) {
        // 동일 코디네이터에 중복 호출이 들어오면 이전 continuation 을 안전하게 종료
        if let pending = self.continuation {
            pending.resume(throwing: CancellationError())
        }
        self.continuation = continuation

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        self.controller = controller
        controller.performRequests()
    }

    private func finish(with result: Result<String, Error>) {
        let pending = continuation
        continuation = nil
        controller = nil
        switch result {
        case .success(let token): pending?.resume(returning: token)
        case .failure(let error): pending?.resume(throwing: error)
        }
    }
}

extension AppleSignInCoordinator: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(controller: ASAuthorizationController,
                                             didCompleteWithAuthorization authorization: ASAuthorization) {
        let result: Result<String, Error>
        if let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
           let tokenData = credential.identityToken,
           let token = String(data: tokenData, encoding: .utf8) {
            result = .success(token)
        } else {
            result = .failure(AppleSignInError.missingIdentityToken)
        }
        Task { @MainActor in self.finish(with: result) }
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
