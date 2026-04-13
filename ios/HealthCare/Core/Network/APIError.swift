import Foundation

enum APIError: LocalizedError {
    case invalidURL
    case unknown
    case serverError(statusCode: Int, code: String?)
    case decodingError(Error)
    case noNetwork
    case tokenExpired
    case unauthorized

    var errorDescription: String? {
        switch self {
        case .invalidURL:                  return "잘못된 URL입니다."
        case .unknown:                     return "알 수 없는 오류가 발생했습니다."
        case .noNetwork:                   return "네트워크 연결을 확인해주세요."
        case .tokenExpired:                return "세션이 만료되었습니다. 다시 로그인해주세요."
        case .unauthorized:                return "인증에 실패했습니다."
        case .decodingError:               return "서버 응답을 처리할 수 없습니다."
        case .serverError(_, let code):    return code.map { "서버 오류: \($0)" } ?? "서버 오류가 발생했습니다."
        }
    }
}
