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
            // 백엔드는 현재 한국어 메시지만 내려준다(Phase 4 백엔드 i18n 미구현).
            // 영문 모드에서 서버 한국어 메시지를 그대로 노출하면 영문 UI 와 섞여 어색하므로,
            // 한국어 글자가 포함된 메시지는 무시하고 status 기반 영문 메시지로 폴백한다.
            // PREMIUM_REQUIRED 같은 영문 코드/메시지는 그대로 통과.
            if let message, !message.isEmpty, !Self.containsKoreanIfEnglishLocale(message) {
                return message
            }
            return Self.statusFallback(status)
        }
    }

    /// 영문 locale 일 때만 한국어 포함 여부를 검사. 한국어 locale 에서는 서버 메시지를 그대로 사용한다.
    private static func containsKoreanIfEnglishLocale(_ s: String) -> Bool {
        let prefersEnglish = (Locale.preferredLanguages.first ?? "").hasPrefix("en")
        guard prefersEnglish else { return false }
        // 한글 음절(가-힣) + 자모(ㄱ-ㅣ) 검출.
        return s.range(of: "[가-힣ㄱ-ㅎㅏ-ㅣ]", options: .regularExpression) != nil
    }

    /// HTTP status → locale-aware 사용자용 메시지. 가장 흔한 4xx/5xx 만 명시, 나머지는 generic.
    private static func statusFallback(_ status: Int) -> String {
        switch status {
        case 400: return String(localized: "api.error.status.400")
        case 403: return String(localized: "api.error.status.403")
        case 404: return String(localized: "api.error.status.404")
        case 409: return String(localized: "api.error.status.409")
        case 422: return String(localized: "api.error.status.422")
        case 429: return String(localized: "api.error.status.429")
        case 500...599: return String(localized: "api.error.status.5xx")
        default: return String(format: String(localized: "api.error.server.format"), status)
        }
    }
}
