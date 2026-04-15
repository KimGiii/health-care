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
            d.keyDecodingStrategy  = .convertFromSnakeCase
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

        let urlRequest = try buildRequest(for: endpoint)
        let (data, response) = try await session.data(for: urlRequest)

        guard let http = response as? HTTPURLResponse else { throw APIError.unknown }

        // 혹시라도 401이 오면 한 번만 재시도 (안전망)
        if http.statusCode == 401 && endpoint.requiresAuth && retryOnUnauthorized {
            try await refreshTokens()
            return try await performRequest(endpoint, retryOnUnauthorized: false)
        }

        guard (200..<300).contains(http.statusCode) else {
            let apiError = try? decoder.decode(APIErrorResponse.self, from: data)
            if http.statusCode == 401 { throw APIError.unauthorized }
            throw APIError.serverError(statusCode: http.statusCode, code: apiError?.code)
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

        let urlRequest = try buildRequest(for: endpoint)
        let (data, response) = try await session.data(for: urlRequest)

        guard let http = response as? HTTPURLResponse else { throw APIError.unknown }

        if http.statusCode == 401 && endpoint.requiresAuth && retryOnUnauthorized {
            try await refreshTokens()
            try await performRequestVoid(endpoint, retryOnUnauthorized: false)
            return
        }

        guard (200..<300).contains(http.statusCode) else {
            let apiError = try? decoder.decode(APIErrorResponse.self, from: data)
            if http.statusCode == 401 { throw APIError.unauthorized }
            throw APIError.serverError(statusCode: http.statusCode, code: apiError?.code)
        }
    }

    // MARK: - Token Refresh

    /// 토큰이 만료됐거나 30초 이내 만료 예정이면 미리 refresh
    private func refreshIfNeeded() async throws {
        guard let token = tokenStore.accessToken, isTokenExpired(token) else { return }
        try await refreshTokens()
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
            waiters.forEach { $0.resume(throwing: error) }
            throw error
        }
    }

    private func buildRequest(for endpoint: APIEndpoint) throws -> URLRequest {
        guard let url = URL(string: endpoint.path, relativeTo: baseURL) else {
            throw APIError.invalidURL
        }
        var components = URLComponents(url: url, resolvingAgainstBaseURL: true)
        if let params = endpoint.queryItems { components?.queryItems = params }

        guard let resolvedURL = components?.url else { throw APIError.invalidURL }
        var request = URLRequest(url: resolvedURL, timeoutInterval: 30)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if endpoint.requiresAuth, let token = tokenStore.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = endpoint.body
        return request
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
    let success: Bool
    let code: String?
    let message: String
}
