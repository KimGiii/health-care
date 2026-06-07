import Foundation

actor APIClient {
    private let session: URLSession
    private let baseURL: URL
    private let tokenStore: TokenStore
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    /// refresh 진행 중 여부 (actor-isolated)
    private var isRefreshing = false
    /// refresh 완료를 기다리는 continuation 목록
    private var refreshWaiters: [CheckedContinuation<Void, Error>] = []

    init(baseURL: URL, tokenStore: TokenStore, session: URLSession = .shared) {
        self.baseURL    = baseURL
        self.tokenStore = tokenStore
        self.session    = session
        self.decoder = {
            let d = JSONDecoder()
            d.dateDecodingStrategy = .iso8601
            return d
        }()
        self.encoder = JSONEncoder()
    }

    func request<T: Decodable>(_ endpoint: APIEndpoint) async throws -> T {
        return try await performRequest(endpoint, retryOnUnauthorized: true)
    }

    /// data 필드가 없는 응답 (DELETE 등 ApiResponse<Void>)을 처리
    func requestVoid(_ endpoint: APIEndpoint) async throws {
        try await performRequestVoid(endpoint, retryOnUnauthorized: true)
    }

    // MARK: - Private

    private func performRequest<T: Decodable>(
        _ endpoint: APIEndpoint,
        retryOnUnauthorized: Bool
    ) async throws -> T {
        // 요청 전 토큰 만료 선제 체크 — 만료됐으면 먼저 refresh
        if endpoint.requiresAuth && retryOnUnauthorized {
            try await refreshIfNeeded()
        }

        let accessTokenForRequest = endpoint.requiresAuth ? tokenStore.accessToken : nil
        let urlRequest = try buildRequest(for: endpoint, accessToken: accessTokenForRequest)
        let (data, response) = try await send(urlRequest)

        guard let http = response as? HTTPURLResponse else { throw APIError.unknown }

        // 혹시라도 401이 오면 한 번만 재시도 (안전망)
        if http.statusCode == 401 && endpoint.requiresAuth && retryOnUnauthorized {
            if tokenStore.accessToken != accessTokenForRequest {
                return try await performRequest(endpoint, retryOnUnauthorized: false)
            }
            try await refreshTokens()
            return try await performRequest(endpoint, retryOnUnauthorized: false)
        }

        guard (200..<300).contains(http.statusCode) else {
            let apiError = try? decoder.decode(APIErrorResponse.self, from: data)
            if http.statusCode == 401 { throw APIError.unauthorized }
            if http.statusCode == 403 && apiError?.code == "PREMIUM_REQUIRED" {
                throw APIError.premiumRequired
            }
            throw APIError.serverError(statusCode: http.statusCode, code: apiError?.code, message: apiError?.message)
        }

        do {
            let envelope = try decoder.decode(SuccessEnvelope<T>.self, from: data)
            return envelope.data
        } catch {
            throw APIError.decodingError(error)
        }
    }

    private func performRequestVoid(
        _ endpoint: APIEndpoint,
        retryOnUnauthorized: Bool
    ) async throws {
        if endpoint.requiresAuth && retryOnUnauthorized {
            try await refreshIfNeeded()
        }

        let accessTokenForRequest = endpoint.requiresAuth ? tokenStore.accessToken : nil
        let urlRequest = try buildRequest(for: endpoint, accessToken: accessTokenForRequest)
        let (data, response) = try await send(urlRequest)

        guard let http = response as? HTTPURLResponse else { throw APIError.unknown }

        if http.statusCode == 401 && endpoint.requiresAuth && retryOnUnauthorized {
            if tokenStore.accessToken != accessTokenForRequest {
                try await performRequestVoid(endpoint, retryOnUnauthorized: false)
                return
            }
            try await refreshTokens()
            try await performRequestVoid(endpoint, retryOnUnauthorized: false)
            return
        }

        guard (200..<300).contains(http.statusCode) else {
            let apiError = try? decoder.decode(APIErrorResponse.self, from: data)
            if http.statusCode == 401 { throw APIError.unauthorized }
            if http.statusCode == 403 && apiError?.code == "PREMIUM_REQUIRED" {
                throw APIError.premiumRequired
            }
            throw APIError.serverError(statusCode: http.statusCode, code: apiError?.code, message: apiError?.message)
        }
    }

    // MARK: - Token Refresh

    /// 토큰이 만료됐거나 30초 이내 만료 예정이면 미리 refresh
    private func refreshIfNeeded() async throws {
        if let token = tokenStore.accessToken {
            if isTokenExpired(token) {
                try await refreshTokens()
            }
            return
        }

        if tokenStore.refreshToken != nil {
            try await refreshTokens()
            return
        }

        NotificationCenter.default.post(name: .sessionDidExpire, object: nil)
        throw APIError.unauthorized
    }

    /// JWT payload의 exp 클레임으로 만료 여부 판단 (30초 버퍼 포함)
    private func isTokenExpired(_ token: String) -> Bool {
        let parts = token.split(separator: ".").map(String.init)
        guard parts.count == 3 else { return true }
        var base64 = parts[1]
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let pad = base64.count % 4
        if pad > 0 { base64 += String(repeating: "=", count: 4 - pad) }
        guard let data = Data(base64Encoded: base64),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let exp = json["exp"] as? TimeInterval else { return true }
        return Date().timeIntervalSince1970 >= exp - 30
    }

    /// 실제 refresh 수행 — 동시에 여러 호출이 와도 한 번만 실행
    private func refreshTokens() async throws {
        // 이미 refresh 진행 중이면 완료될 때까지 대기
        if isRefreshing {
            try await withCheckedThrowingContinuation { continuation in
                refreshWaiters.append(continuation)
            }
            return
        }

        isRefreshing = true

        do {
            guard let refreshToken = tokenStore.refreshToken else {
                NotificationCenter.default.post(name: .sessionDidExpire, object: nil)
                throw APIError.unauthorized
            }
            let body = try encoder.encode(["refreshToken": refreshToken])
            let tokenResponse: TokenResponse = try await performRequest(
                .refreshToken(body: body),
                retryOnUnauthorized: false
            )
            tokenStore.save(
                accessToken: tokenResponse.accessToken,
                refreshToken: tokenResponse.refreshToken
            )
            // 대기 중인 모든 요청 성공 재개
            isRefreshing = false
            let waiters = refreshWaiters
            refreshWaiters = []
            waiters.forEach { $0.resume() }
        } catch {
            isRefreshing = false
            let waiters = refreshWaiters
            refreshWaiters = []
            if case APIError.unauthorized = error {
                NotificationCenter.default.post(name: .sessionDidExpire, object: nil)
            }
            waiters.forEach { $0.resume(throwing: error) }
            throw error
        }
    }

    private func buildRequest(for endpoint: APIEndpoint, accessToken: String?) throws -> URLRequest {
        var components = URLComponents()
        components.scheme = baseURL.scheme
        components.host = baseURL.host
        components.port = baseURL.port
        components.path = endpoint.path
        components.queryItems = endpoint.queryItems

        guard let resolvedURL = components.url else {
            throw APIError.invalidURL
        }

        var request = URLRequest(url: resolvedURL, timeoutInterval: 30)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if endpoint.requiresAuth, let token = accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = endpoint.body
        return request
    }

    private func send(_ request: URLRequest) async throws -> (Data, URLResponse) {
        return try await sendWithTransientRetry(request, attempt: 0, maxAttempts: 3)
    }

    /// LAN cold-start / connection-warmup 단계에서 병렬 요청이 일시적으로 실패하는
    /// 케이스를 흡수한다. 첫 탭 진입 시 "에러 → 재클릭으로 성공" 패턴의 원인.
    /// 지수 백오프 250ms → 750ms (총 grace ~1s) 후에도 실패하면 사용자에게 보고.
    private func sendWithTransientRetry(
        _ request: URLRequest,
        attempt: Int,
        maxAttempts: Int
    ) async throws -> (Data, URLResponse) {
        do {
            return try await session.data(for: request)
        } catch let urlError as URLError {
            let nextAttempt = attempt + 1
            if nextAttempt < maxAttempts, isTransient(urlError) {
                let backoffMs: UInt64 = attempt == 0 ? 250 : 750
                try? await Task.sleep(nanoseconds: backoffMs * 1_000_000)
                return try await sendWithTransientRetry(request, attempt: nextAttempt, maxAttempts: maxAttempts)
            }
            throw APIError.noNetwork
        }
    }

    private nonisolated func isTransient(_ error: URLError) -> Bool {
        // .cancelled는 view dismiss로 인한 의도된 종료이므로 retry 대상이 아님.
        // retry해도 같은 cancellation context에서 즉시 또 cancelled가 됨.
        switch error.code {
        case .timedOut,
             .networkConnectionLost,
             .notConnectedToInternet,
             .cannotConnectToHost,
             .dnsLookupFailed:
            return true
        default:
            return false
        }
    }
}

// MARK: - Convenience encode helper (accessible outside actor)
extension APIClient {
    nonisolated func encode<T: Encodable>(_ value: T) throws -> Data {
        return try JSONEncoder().encode(value)
    }
}

// MARK: - Response envelopes
private struct SuccessEnvelope<T: Decodable>: Decodable {
    let success: Bool
    let data: T
    let message: String?
}

struct APIErrorResponse: Decodable {
    // 백엔드 ErrorResponse는 success 필드를 보내지 않으므로 옵셔널.
    // 과거 required로 잡혀 있어 모든 에러 디코드가 실패 → code=nil → 일반 메시지로 노출되던 회귀 차단.
    let success: Bool?
    let code: String?
    let message: String
}
