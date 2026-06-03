import Foundation

enum APIError: LocalizedError {
    case invalidURL
    case unknown
    case serverError(statusCode: Int, code: String?, message: String?)
    case decodingError(Error)
    case noNetwork
    case tokenExpired
    case unauthorized
    /// 백엔드가 403 + code="PREMIUM_REQUIRED"로 응답한 경우.
    case premiumRequired

    var errorDescription: String? {
        switch self {
        case .invalidURL:                  return String(localized: "api.error.invalidURL")
        case .unknown:                     return String(localized: "api.error.unknown")
        case .noNetwork:                   return String(localized: "api.error.noNetwork")
        case .tokenExpired:                return String(localized: "api.error.tokenExpired")
        case .unauthorized:                return String(localized: "api.error.unauthorized")
        case .premiumRequired:             return String(localized: "api.error.premiumRequired")
        case .decodingError:               return String(localized: "api.error.decoding")
        case .serverError(let status, _, let message):
            // 백엔드가 사용자용 한국어 메시지를 내려주면 우선 사용.
            // 그 외엔 상태코드만 노출해 디버깅 단서 제공.
            if let message, !message.isEmpty { return message }
            return String(format: String(localized: "api.error.server.format"), status)
        }
    }
}
