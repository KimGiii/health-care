import XCTest
@testable import HealthCare

final class HealthCareTests: XCTestCase {
    override func tearDown() {
        URLProtocolStub.requestHandler = nil
        TokenStore().clearTokens()
        super.tearDown()
    }

    func testTokenStoreWriteRead() throws {
        let store = TokenStore()
        store.save(accessToken: "test-access", refreshToken: "test-refresh")
        XCTAssertEqual(store.accessToken, "test-access")
        XCTAssertEqual(store.refreshToken, "test-refresh")
        store.clearTokens()
        XCTAssertNil(store.accessToken)
        XCTAssertNil(store.refreshToken)
    }

    func testAuthStatusDefaultsToUnauthenticated() async throws {
        let tokenStore = TokenStore()
        tokenStore.clearTokens()
        let authState = await AuthState(tokenStore: tokenStore)
        let status = await authState.status
        XCTAssertEqual(status, .unauthenticated)
    }

    func testAPIClientRefreshesExpiredTokenBeforeAuthenticatedRequest() async throws {
        let store = TokenStore()
        store.clearTokens()
        store.save(accessToken: Self.jwt(expiringIn: -60), refreshToken: "old-refresh")

        let refreshedAccessToken = Self.jwt(expiringIn: 3600)
        let session = Self.stubbedSession { request in
            switch request.url?.path {
            case "/api/v1/auth/token/refresh":
                XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
                let body = try XCTUnwrap(Self.bodyData(from: request))
                let payload = try JSONDecoder().decode([String: String].self, from: body)
                XCTAssertEqual(payload["refreshToken"], "old-refresh")
                return Self.jsonResponse(path: request.url?.path, body: """
                {
                  "success": true,
                  "data": {
                    "accessToken": "\(refreshedAccessToken)",
                    "refreshToken": "new-refresh",
                    "expiresIn": 3600,
                    "onboardingCompleted": true
                  },
                  "message": null
                }
                """)
            case "/api/v1/users/me":
                XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer \(refreshedAccessToken)")
                return Self.jsonResponse(path: request.url?.path, body: """
                {
                  "success": true,
                  "data": { "value": "ok" },
                  "message": null
                }
                """)
            default:
                XCTFail("예상하지 못한 요청: \(request.url?.absoluteString ?? "nil")")
                return Self.jsonResponse(statusCode: 404, path: request.url?.path, body: "{}")
            }
        }

        let client = APIClient(baseURL: Self.baseURL, tokenStore: store, session: session)
        let response: StubPayload = try await client.request(.getProfile)

        XCTAssertEqual(response.value, "ok")
        XCTAssertEqual(store.accessToken, refreshedAccessToken)
        XCTAssertEqual(store.refreshToken, "new-refresh")
    }

    func testAPIClientRefreshesAndRetriesOnceAfterUnauthorizedResponse() async throws {
        let store = TokenStore()
        store.clearTokens()
        let initialAccessToken = Self.jwt(expiringIn: 3600)
        let refreshedAccessToken = Self.jwt(expiringIn: 7200)
        store.save(accessToken: initialAccessToken, refreshToken: "old-refresh")

        let profileRequestCount = LockedCounter()
        let session = Self.stubbedSession { request in
            switch request.url?.path {
            case "/api/v1/users/me":
                let count = profileRequestCount.increment()
                if count == 1 {
                    XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer \(initialAccessToken)")
                    return Self.jsonResponse(statusCode: 401, path: request.url?.path, body: """
                    {
                      "success": false,
                      "code": "UNAUTHORIZED",
                      "message": "만료된 토큰입니다."
                    }
                    """)
                }

                XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer \(refreshedAccessToken)")
                return Self.jsonResponse(path: request.url?.path, body: """
                {
                  "success": true,
                  "data": { "value": "retried" },
                  "message": null
                }
                """)
            case "/api/v1/auth/token/refresh":
                return Self.jsonResponse(path: request.url?.path, body: """
                {
                  "success": true,
                  "data": {
                    "accessToken": "\(refreshedAccessToken)",
                    "refreshToken": "new-refresh",
                    "expiresIn": 3600,
                    "onboardingCompleted": true
                  },
                  "message": null
                }
                """)
            default:
                XCTFail("예상하지 못한 요청: \(request.url?.absoluteString ?? "nil")")
                return Self.jsonResponse(statusCode: 404, path: request.url?.path, body: "{}")
            }
        }

        let client = APIClient(baseURL: Self.baseURL, tokenStore: store, session: session)
        let response: StubPayload = try await client.request(.getProfile)

        XCTAssertEqual(response.value, "retried")
        XCTAssertEqual(profileRequestCount.value, 2)
        XCTAssertEqual(store.accessToken, refreshedAccessToken)
        XCTAssertEqual(store.refreshToken, "new-refresh")
    }

    func testAPIClientRetriesWithCurrentTokenWhenUnauthorizedRequestUsedStaleToken() async throws {
        let store = TokenStore()
        store.clearTokens()
        let initialAccessToken = Self.jwt(expiringIn: 3600)
        let refreshedAccessToken = Self.jwt(expiringIn: 7200)
        store.save(accessToken: initialAccessToken, refreshToken: "old-refresh")

        let profileRequestCount = LockedCounter()
        let refreshRequestCount = LockedCounter()
        let session = Self.stubbedSession { request in
            switch request.url?.path {
            case "/api/v1/users/me":
                let count = profileRequestCount.increment()
                if count == 1 {
                    XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer \(initialAccessToken)")
                    store.save(accessToken: refreshedAccessToken, refreshToken: "new-refresh")
                    return Self.jsonResponse(statusCode: 401, path: request.url?.path, body: """
                    {
                      "success": false,
                      "code": "UNAUTHORIZED",
                      "message": "만료된 토큰입니다."
                    }
                    """)
                }

                XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer \(refreshedAccessToken)")
                return Self.jsonResponse(path: request.url?.path, body: """
                {
                  "success": true,
                  "data": { "value": "retried-with-current-token" },
                  "message": null
                }
                """)
            case "/api/v1/auth/token/refresh":
                refreshRequestCount.increment()
                return Self.jsonResponse(statusCode: 500, path: request.url?.path, body: "{}")
            default:
                XCTFail("예상하지 못한 요청: \(request.url?.absoluteString ?? "nil")")
                return Self.jsonResponse(statusCode: 404, path: request.url?.path, body: "{}")
            }
        }

        let client = APIClient(baseURL: Self.baseURL, tokenStore: store, session: session)
        let response: StubPayload = try await client.request(.getProfile)

        XCTAssertEqual(response.value, "retried-with-current-token")
        XCTAssertEqual(profileRequestCount.value, 2)
        XCTAssertEqual(refreshRequestCount.value, 0)
    }

    func testAPIClientDoesNotRetryUnauthorizedResponseAfterRefreshAttempt() async throws {
        let store = TokenStore()
        store.clearTokens()
        let initialAccessToken = Self.jwt(expiringIn: 3600)
        let refreshedAccessToken = Self.jwt(expiringIn: 7200)
        store.save(accessToken: initialAccessToken, refreshToken: "old-refresh")

        let profileRequestCount = LockedCounter()
        let session = Self.stubbedSession { request in
            switch request.url?.path {
            case "/api/v1/users/me":
                profileRequestCount.increment()
                return Self.jsonResponse(statusCode: 401, path: request.url?.path, body: """
                {
                  "success": false,
                  "code": "UNAUTHORIZED",
                  "message": "권한이 없습니다."
                }
                """)
            case "/api/v1/auth/token/refresh":
                return Self.jsonResponse(path: request.url?.path, body: """
                {
                  "success": true,
                  "data": {
                    "accessToken": "\(refreshedAccessToken)",
                    "refreshToken": "new-refresh",
                    "expiresIn": 3600,
                    "onboardingCompleted": true
                  },
                  "message": null
                }
                """)
            default:
                XCTFail("예상하지 못한 요청: \(request.url?.absoluteString ?? "nil")")
                return Self.jsonResponse(statusCode: 404, path: request.url?.path, body: "{}")
            }
        }

        let client = APIClient(baseURL: Self.baseURL, tokenStore: store, session: session)

        do {
            let _: StubPayload = try await client.request(.getProfile)
            XCTFail("401 재시도 후에도 실패하면 unauthorized 에러를 던져야 합니다.")
        } catch APIError.unauthorized {
            XCTAssertEqual(profileRequestCount.value, 2)
        } catch {
            XCTFail("예상하지 못한 에러: \(error)")
        }
    }

    func testAPIClientMapsURLErrorToNoNetwork() async throws {
        let store = TokenStore()
        store.clearTokens()

        let session = Self.stubbedSession { _ in
            throw URLError(.notConnectedToInternet)
        }

        let client = APIClient(baseURL: Self.baseURL, tokenStore: store, session: session)

        do {
            let _: StubPayload = try await client.request(.login(body: Data()))
            XCTFail("URLSession 네트워크 실패는 noNetwork로 변환되어야 합니다.")
        } catch APIError.noNetwork {
            // Expected.
        } catch {
            XCTFail("예상하지 못한 에러: \(error)")
        }
    }

    func testAPIClientDecodesRawSuccessResponseForCompatibility() async throws {
        let store = TokenStore()
        store.clearTokens()
        store.save(accessToken: Self.jwt(expiringIn: 3600), refreshToken: "refresh")

        let session = Self.stubbedSession { request in
            XCTAssertEqual(request.url?.path, "/api/v1/users/me")
            return Self.jsonResponse(path: request.url?.path, body: """
            {
              "value": "raw-success"
            }
            """)
        }

        let client = APIClient(baseURL: Self.baseURL, tokenStore: store, session: session)
        let response: StubPayload = try await client.request(.getProfile)

        XCTAssertEqual(response.value, "raw-success")
    }

    func testAPIClientDecodesRawDietRestrictionsForCompatibility() async throws {
        let store = TokenStore()
        store.clearTokens()
        store.save(accessToken: Self.jwt(expiringIn: 3600), refreshToken: "refresh")

        let session = Self.stubbedSession { request in
            XCTAssertEqual(request.url?.path, "/api/v1/diet/restrictions")
            return Self.jsonResponse(path: request.url?.path, body: """
            [
              {
                "id": 1,
                "restrictionType": "ALLERGY",
                "targetType": "ALLERGEN_TAG",
                "foodCatalogId": null,
                "category": null,
                "keyword": null,
                "allergenTag": "MILK",
                "createdAt": "2026-06-16T15:20:00+09:00"
              }
            ]
            """)
        }

        let client = APIClient(baseURL: Self.baseURL, tokenStore: store, session: session)
        let response: [DietRestriction] = try await client.request(.listDietRestrictions)

        XCTAssertEqual(response.count, 1)
        XCTAssertEqual(response[0].id, 1)
        XCTAssertEqual(response[0].restrictionType, .ALLERGY)
        XCTAssertEqual(response[0].targetType, .ALLERGEN_TAG)
        XCTAssertEqual(response[0].allergenTag, .MILK)
    }

    func testAPIClientDecodesRawDietRecommendationForCompatibility() async throws {
        let store = TokenStore()
        store.clearTokens()
        store.save(accessToken: Self.jwt(expiringIn: 3600), refreshToken: "refresh")

        let session = Self.stubbedSession { request in
            XCTAssertEqual(request.url?.path, "/api/v1/diet/recommendations/daily")
            return Self.jsonResponse(path: request.url?.path, body: """
            {
              "date": "2026-06-16",
              "targets": {
                "calorieTarget": 2000,
                "proteinTargetG": 150,
                "carbTargetG": 230,
                "fatTargetG": 67
              },
              "appliedRestrictions": [],
              "meals": [],
              "totalNutrients": {
                "totalCalories": 0,
                "totalProteinG": 0,
                "totalCarbsG": 0,
                "totalFatG": 0
              },
              "strictAllergyMode": false,
              "disclaimer": "이 추천은 참고용입니다."
            }
            """)
        }

        let client = APIClient(baseURL: Self.baseURL, tokenStore: store, session: session)
        let response: DailyDietRecommendationResponse = try await client.request(.getDailyRecommendation(body: Data()))

        XCTAssertEqual(response.date, "2026-06-16")
        XCTAssertEqual(response.targets.calorieTarget, 2000)
        XCTAssertTrue(response.appliedRestrictions.isEmpty)
        XCTAssertTrue(response.meals.isEmpty)
        XCTAssertEqual(response.disclaimer, "이 추천은 참고용입니다.")
    }
}

private extension HealthCareTests {
    static let baseURL = URL(string: "https://unit.test")!

    struct StubPayload: Decodable, Equatable {
        let value: String
    }

    static func stubbedSession(
        handler: @escaping (URLRequest) throws -> (HTTPURLResponse, Data)
    ) -> URLSession {
        URLProtocolStub.requestHandler = handler
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [URLProtocolStub.self]
        return URLSession(configuration: configuration)
    }

    static func jsonResponse(
        statusCode: Int = 200,
        path: String?,
        body: String
    ) -> (HTTPURLResponse, Data) {
        let url = URL(string: path ?? "/", relativeTo: baseURL)!
        let response = HTTPURLResponse(
            url: url,
            statusCode: statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        return (response, Data(body.utf8))
    }

    static func bodyData(from request: URLRequest) -> Data? {
        if let httpBody = request.httpBody {
            return httpBody
        }

        guard let stream = request.httpBodyStream else {
            return nil
        }

        stream.open()
        defer { stream.close() }

        var data = Data()
        let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }

        while stream.hasBytesAvailable {
            let bytesRead = stream.read(buffer, maxLength: bufferSize)
            if bytesRead < 0 {
                return nil
            }
            if bytesRead == 0 {
                break
            }
            data.append(buffer, count: bytesRead)
        }
        return data
    }

    static func jwt(expiringIn seconds: TimeInterval) -> String {
        let header = ["alg": "none", "typ": "JWT"]
        let payload = ["exp": Date().timeIntervalSince1970 + seconds]
        return [
            base64URLEncodedJSON(header),
            base64URLEncodedJSON(payload),
            "signature"
        ].joined(separator: ".")
    }

    static func base64URLEncodedJSON(_ object: Any) -> String {
        let data = try! JSONSerialization.data(withJSONObject: object)
        return data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

private final class URLProtocolStub: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let requestHandler = Self.requestHandler else {
            client?.urlProtocol(self, didFailWithError: APIError.unknown)
            return
        }

        do {
            let (response, data) = try requestHandler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {
    }
}

private final class LockedCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0

    var value: Int {
        lock.withLock { count }
    }

    @discardableResult
    func increment() -> Int {
        lock.withLock {
            count += 1
            return count
        }
    }
}
