#if DEBUG
import Foundation

/// UI 테스트(`UI_TEST_STUB_NETWORK`)에서 실제 백엔드 대신 정형화된 응답을 돌려주는 모의 네트워크.
///
/// 목적: `UI_TEST_AUTHENTICATED`로 진입한 인증 세션이 유지되도록 한다.
/// 실제 백엔드에 가짜 토큰으로 호출하면 401/refresh 실패 → `.sessionDidExpire` →
/// 로그아웃 → OnboardingView 폴백이 일어나 인증 후 화면 테스트가 모두 실패한다.
/// 여기서는 대시보드·기록 화면이 호출하는 엔드포인트에 빈 컬렉션/최소 프로필을 반환해
/// 세션을 살아 있게 유지한다.
enum UITestNetworkStub {
    static var isEnabled: Bool {
        ProcessInfo.processInfo.arguments.contains("UI_TEST_STUB_NETWORK")
    }

    /// 만료되지 않는 구조적 JWT(header.payload.signature)를 만든다.
    /// `APIClient.isTokenExpired`가 payload의 `exp`를 읽어 false를 반환하므로
    /// refresh 호출 자체가 발생하지 않는다.
    static func makeSessionToken(expiresInDays: Int = 365) -> String {
        func segment(_ object: [String: Any]) -> String {
            let data = (try? JSONSerialization.data(withJSONObject: object)) ?? Data()
            return data.base64EncodedString()
                .replacingOccurrences(of: "+", with: "-")
                .replacingOccurrences(of: "/", with: "_")
                .replacingOccurrences(of: "=", with: "")
        }
        let header = segment(["alg": "none", "typ": "JWT"])
        let exp = Int(Date().addingTimeInterval(Double(expiresInDays) * 86_400).timeIntervalSince1970)
        let payload = segment(["sub": "ui-test", "exp": exp])
        return "\(header).\(payload).uitest"
    }

    static func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubURLProtocol.self]
        return URLSession(configuration: config)
    }
}

/// 모든 요청을 가로채 경로별 고정 응답(200 + JSON 봉투)을 돌려준다.
final class StubURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let path = request.url?.path ?? ""
        let response = HTTPURLResponse(
            url: request.url ?? URL(string: "https://stub.local")!,
            statusCode: 200,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(StubURLProtocol.body(for: path).utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private static func body(for path: String) -> String {
        func envelope(_ data: String) -> String {
            "{\"success\":true,\"data\":\(data),\"message\":null}"
        }

        // DietLogListResponse(page/size) + Session/GoalListResponse(pageNumber/pageSize)
        // 양쪽 키를 모두 담아 어느 쪽으로 디코드해도 빈 페이지가 되도록 한다.
        let emptyPage = """
        {"content":[],"page":0,"size":50,"pageNumber":0,"pageSize":50,\
        "totalElements":0,"totalPages":0,"first":true,"last":true}
        """

        if path.hasSuffix("/auth/token/refresh") {
            return envelope("""
            {"accessToken":"\(UITestNetworkStub.makeSessionToken())",\
            "refreshToken":"ui-test-refresh-token","expiresIn":3600,"onboardingCompleted":true}
            """)
        }
        if path.hasSuffix("/users/me") {
            return envelope("""
            {"id":1,"email":"uitest@gainsy.app","displayName":"UI Test",\
            "onboardingCompleted":true,"isPremium":false}
            """)
        }
        if path.hasSuffix("/diet/logs")
            || path.hasSuffix("/exercise/sessions")
            || path.hasSuffix("/goals")
            || path.hasSuffix("/body-measurements") {
            return envelope(emptyPage)
        }
        // 기본: 빈 배열. 목록형 다수가 여기 해당하고, 객체형이라 디코드에 실패해도
        // 200 응답이므로 세션은 유지된다(로그아웃 안 됨).
        return envelope("[]")
    }
}
#endif
