import Foundation

actor APIClient {
    private let session: URLSession
    private let baseURL: URL
    private let tokenStore: TokenStore
    private let decoder: JSONDecoder

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
    }

    func request<T: Decodable>(_ endpoint: APIEndpoint) async throws -> T {
        let urlRequest = try buildRequest(for: endpoint)
        let (data, response) = try await session.data(for: urlRequest)

        guard let http = response as? HTTPURLResponse else {
            throw APIError.unknown
        }

        guard (200..<300).contains(http.statusCode) else {
            let apiError = try? decoder.decode(APIErrorResponse.self, from: data)
            throw APIError.serverError(statusCode: http.statusCode, code: apiError?.code)
        }

        let envelope = try decoder.decode(SuccessEnvelope<T>.self, from: data)
        return envelope.data
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
