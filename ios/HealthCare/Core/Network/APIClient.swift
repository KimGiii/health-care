import Foundation

actor APIClient {
    private let session: URLSession
    private let baseURL: URL
    private let tokenStore: TokenStore
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

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
        let urlRequest = try buildRequest(for: endpoint)
        let (data, response) = try await session.data(for: urlRequest)

        guard let http = response as? HTTPURLResponse else { throw APIError.unknown }

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

    private func refreshTokens() async throws {
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
